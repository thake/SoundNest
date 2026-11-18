/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.spokenword.audible.playback

import io.github.thake.soundnest.feature.spokenword.audible.data.AudibleUris

import io.github.thake.soundnest.feature.spokenword.audible.data.AudibleCatalogCache
import io.github.thake.soundnest.feature.spokenword.audible.data.AudibleChapterProjector
import io.github.thake.soundnest.feature.spokenword.audible.data.AudibleProgressStore
import io.github.thake.soundnest.feature.spokenword.audible.data.BookProgress
import io.github.thake.soundnest.feature.spokenword.audible.data.ProjectedChapterPosition
import io.github.thake.soundnest.feature.spokenword.audible.settings.AudibleCredentialStore

import io.github.thake.soundnest.audible.*

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.compose.runtime.mutableStateOf
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.drm.DummyExoMediaDrm
import androidx.media3.exoplayer.drm.ExoMediaDrm
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.UnsupportedDrmException
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import io.github.thake.soundnest.core.model.SongsModel
import io.github.thake.soundnest.core.di.Graph
import io.github.thake.soundnest.core.playback.LocalPlayer
import io.github.thake.soundnest.core.playback.SongPlayer
import io.github.thake.soundnest.feature.parental.filter.ParentalFilterEngine
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

interface AudiblePlayerBackend {
    interface Listener {
        fun onPlaybackChanged(isPlaying: Boolean, isBuffering: Boolean, absolutePositionMs: Long)
        fun onEnded(finalPositionMs: Long)
        fun onError(message: String)
    }

    fun load(
        grant: AudibleStreamGrant,
        initialPositionMs: Long,
        speed: Float,
        playWhenReady: Boolean,
        listener: Listener,
    )
    fun resume()
    fun pause()
    fun stop()
    fun seekToAbsolute(positionMs: Long)
    fun setSpeed(speed: Float)
    fun setGain(gain: Float)
    fun isPlaying(): Boolean
    fun isBuffering(): Boolean
    fun currentPositionMs(): Long
    fun release()
}

@OptIn(UnstableApi::class)
private class ExoAudiblePlayerBackend(
    private val context: Context,
    private val http: OkHttpClient,
    private val apiClient: AudibleApiClient,
) : AudiblePlayerBackend {

    private val speechAudio = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
        .build()

    private var exo: ExoPlayer? = null
    private var activeListener: AudiblePlayerBackend.Listener? = null

    private fun ensurePlayer(): ExoPlayer {
        exo?.let { return it }
        return ExoPlayer.Builder(context.applicationContext)
            .setAudioAttributes(speechAudio, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
            .also { player ->
                player.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        val listener = activeListener ?: return
                        if (playbackState == Player.STATE_ENDED) {
                            listener.onEnded(player.currentPosition.coerceAtLeast(0L))
                        } else {
                            listener.onPlaybackChanged(
                                isPlaying = player.isPlaying,
                                isBuffering = playbackState == Player.STATE_BUFFERING,
                                absolutePositionMs = player.currentPosition.coerceAtLeast(0L),
                            )
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        activeListener?.onPlaybackChanged(
                            isPlaying = isPlaying,
                            isBuffering = player.playbackState == Player.STATE_BUFFERING,
                            absolutePositionMs = player.currentPosition.coerceAtLeast(0L),
                        )
                    }

                    override fun onPositionDiscontinuity(
                        oldPosition: Player.PositionInfo,
                        newPosition: Player.PositionInfo,
                        reason: Int,
                    ) {
                        activeListener?.onPlaybackChanged(
                            isPlaying = player.isPlaying,
                            isBuffering = player.playbackState == Player.STATE_BUFFERING,
                            absolutePositionMs = player.currentPosition.coerceAtLeast(0L),
                        )
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        activeListener?.onError(error.message ?: error.errorCodeName)
                    }
                })
                exo = player
            }
    }

    override fun load(
        grant: AudibleStreamGrant,
        initialPositionMs: Long,
        speed: Float,
        playWhenReady: Boolean,
        listener: AudiblePlayerBackend.Listener,
    ) {
        activeListener = listener
        val player = ensurePlayer()
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)

        val drmProvider: DrmSessionManagerProvider? = if (grant.drmType.equals("Widevine", ignoreCase = true)) {
            val mediaDrmProvider = ExoMediaDrm.Provider { uuid ->
                try {
                    FrameworkMediaDrm.newInstance(uuid).also { cdm ->
                        grant.serverCertificate?.let { cert ->
                            runCatching { cdm.setPropertyByteArray("serviceCertificate", cert) }
                        }
                    }
                } catch (_: UnsupportedDrmException) {
                    DummyExoMediaDrm()
                }
            }
            val drmManager = DefaultDrmSessionManager.Builder()
                .setUuidAndExoMediaDrmProvider(C.WIDEVINE_UUID, mediaDrmProvider)
                .setMultiSession(true)
                .build(AudibleMediaDrmCallback(grant.asin, apiClient, http))
            DrmSessionManagerProvider { drmManager }
        } else {
            null
        }

        val uri = Uri.parse(grant.manifestUrl)
        val pathLower = uri.path.orEmpty().lowercase()
        val mediaItem = MediaItem.fromUri(uri)
        val mediaSource = when {
            pathLower.endsWith(".mpd") || grant.drmType.equals("Dash", ignoreCase = true) || grant.drmType.equals("Widevine", ignoreCase = true) && !pathLower.endsWith(".m3u8") -> {
                DashMediaSource.Factory(dataSourceFactory).apply {
                    drmProvider?.let(::setDrmSessionManagerProvider)
                }.createMediaSource(mediaItem)
            }
            pathLower.endsWith(".m3u8") || grant.drmType.equals("HlsCmaf", ignoreCase = true) -> {
                HlsMediaSource.Factory(dataSourceFactory).apply {
                    drmProvider?.let(::setDrmSessionManagerProvider)
                }.createMediaSource(mediaItem)
            }
            else -> {
                DefaultMediaSourceFactory(dataSourceFactory).apply {
                    drmProvider?.let(::setDrmSessionManagerProvider)
                }.createMediaSource(mediaItem)
            }
        }

        player.playbackParameters = PlaybackParameters(speed.coerceIn(0.5f, 3.5f))
        player.volume = 1f
        player.setMediaSource(mediaSource, initialPositionMs.coerceAtLeast(0L))
        player.playWhenReady = playWhenReady
        player.prepare()
    }

    override fun resume() {
        exo?.playWhenReady = true
    }

    override fun pause() {
        exo?.playWhenReady = false
    }

    override fun stop() {
        exo?.stop()
    }

    override fun seekToAbsolute(positionMs: Long) {
        exo?.seekTo(positionMs.coerceAtLeast(0L))
    }

    override fun setSpeed(speed: Float) {
        exo?.playbackParameters = PlaybackParameters(speed.coerceIn(0.5f, 3.5f))
    }

    override fun setGain(gain: Float) {
        exo?.volume = gain.coerceIn(0f, 1f)
    }

    override fun isPlaying(): Boolean = exo?.isPlaying == true
    override fun isBuffering(): Boolean = exo?.playbackState == Player.STATE_BUFFERING
    override fun currentPositionMs(): Long = exo?.currentPosition?.coerceAtLeast(0L) ?: 0L

    override fun release() {
        activeListener = null
        exo?.release()
        exo = null
    }
}

/**
 * Dedicated spoken-word player for parent-approved Audible audiobooks.
 *
 * Streams a book's continuous MPEG-DASH / HLS-CMAF manifest through Media3 [ExoPlayer] + Widevine CDM,
 * projects chapter-relative positions/durations onto [SongPlayer] via [AudibleChapterProjector],
 * and persists playback progress strictly locally via [AudibleProgressStore] (zero WhisperSync writes).
 */
object AudiblePlayer : io.github.thake.soundnest.core.preferences.PolicySyncedSetting {
    override fun enforcePolicy(context: Context, policy: ParentalPolicy) = enforcePolicy(policy)

    val active = mutableStateOf(false)
    val holdsFocus: Boolean get() = active.value

    private const val TICK_INTERVAL_MS = 500L
    private const val SAVE_INTERVAL_MS = 5_000L

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val defaultHttp by lazy { OkHttpClient() }

    private var appContextOverride: Context? = null
    private var testBackend: AudiblePlayerBackend? = null
    private var testGrantLoader: (suspend (String) -> AudibleStreamGrant)? = null
    private var prodBackend: AudiblePlayerBackend? = null

    private var loadJob: Job? = null
    private var currentBook: AudibleBook? = null
    private var projector: AudibleChapterProjector? = null
    private var lastProjected: ProjectedChapterPosition? = null
    private var lastPublishedChapterIndex: Int = -1
    private var playingState: Boolean = false
    private var bufferingState: Boolean = false
    private var playbackSpeed: Float = 1.0f
    private var lastDiskSaveAtMs: Long = 0L

    private val ticker = object : Runnable {
        override fun run() {
            if (!active.value) return
            val b = activeBackendOrNull() ?: return
            syncProjectionAndPublish(
                absolutePosMs = b.currentPositionMs(),
                isPlaying = b.isPlaying(),
                isBuffering = b.isBuffering(),
                forceSave = false,
            )
            if (b.isPlaying()) {
                mainHandler.postDelayed(this, TICK_INTERVAL_MS)
            }
        }
    }

    private fun resolveContext(): Context? =
        appContextOverride ?: runCatching { Graph.app }.getOrNull()

    private fun getOrCreateBackend(context: Context): AudiblePlayerBackend {
        testBackend?.let { return it }
        prodBackend?.let { return it }
        val client = AudibleApiClient(
            http = defaultHttp,
            tokensProvider = { AudibleCredentialStore.loadTokens(context) },
        )
        return ExoAudiblePlayerBackend(context, defaultHttp, client).also { prodBackend = it }
    }

    private fun activeBackendOrNull(): AudiblePlayerBackend? = testBackend ?: prodBackend

    fun currentAsin(): String? = currentBook?.asin
    fun currentChapterIndex(): Int = lastProjected?.chapter?.index ?: 0

    fun playBook(
        asin: String,
        chapterIndex: Int? = null,
        positionMs: Long? = null,
        context: Context = resolveContext() ?: Graph.app,
    ) {
        val cleanAsin = ParentalPolicyStore.normalizeAudibleAsin(asin)
        if (cleanAsin.isEmpty()) return

        val policy = ParentalPolicyStore.current(context)
        if (!ParentalFilterEngine.isAudibleBookAllowed(cleanAsin, policy)) {
            io.github.thake.soundnest.host.android.Logbook.log("AudiblePlayer: refused unapproved ASIN $cleanAsin")
            return
        }

        // Mutual exclusion: pause LocalPlayer & Spotify Web Player before starting Audible
        LocalPlayer.stop()
        SongPlayer.pauseEngineForAudible()

        val book = AudibleCatalogCache.book(context, cleanAsin) ?: AudibleBook(
            asin = cleanAsin,
            title = RuleLabelFallback.titleFor(context, cleanAsin),
        )
        val savedProgress = AudibleProgressStore.get(context, cleanAsin)
        currentBook = book
        lastPublishedChapterIndex = -1
        playbackSpeed = SongPlayer.speed.value.toFloat().takeIf { it > 0f } ?: 1.0f
        active.value = true
        bufferingState = true

        loadJob?.cancel()
        val customLoader = testGrantLoader
        if (customLoader != null) {
            // Deterministic synchronous execution on main looper when under unit tests
            val grant = kotlinx.coroutines.runBlocking { customLoader(cleanAsin) }
            startLoadedGrant(context, book, grant, chapterIndex, positionMs, savedProgress)
        } else {
            val client = AudibleApiClient(
                http = defaultHttp,
                tokensProvider = { AudibleCredentialStore.loadTokens(context) },
            )
            loadJob = scope.launch {
                val grantResult = runCatching {
                    val rawGrant = client.requestStreamManifest(cleanAsin)
                    if (rawGrant.chapters.isNotEmpty()) {
                        rawGrant
                    } else {
                        val fallbackChapters = AudibleCatalogCache.chapters(context, cleanAsin)
                            ?.takeIf { it.isNotEmpty() }
                            ?: runCatching { client.fetchChapters(cleanAsin) }.getOrNull().orEmpty()
                        if (fallbackChapters.isNotEmpty()) rawGrant.copy(chapters = fallbackChapters) else rawGrant
                    }
                }
                withContext(Dispatchers.Main) {
                    val grant = grantResult.getOrElse { err ->
                        io.github.thake.soundnest.host.android.Logbook.log("AudiblePlayer: stream grant failed for $cleanAsin: ${err.message}")
                        stop()
                        return@withContext
                    }
                    if (!active.value || currentBook?.asin != cleanAsin) return@withContext
                    startLoadedGrant(context, book, grant, chapterIndex, positionMs, savedProgress)
                }
            }
        }
    }

    private fun startLoadedGrant(
        context: Context,
        book: AudibleBook,
        grant: AudibleStreamGrant,
        requestedChapterIndex: Int?,
        requestedPositionMs: Long?,
        savedProgress: BookProgress?,
    ) {
        val cachedChapters = AudibleCatalogCache.chapters(context, book.asin).orEmpty()
        val effectiveChapters = when {
            grant.chapters.size >= cachedChapters.size && grant.chapters.isNotEmpty() -> {
                AudibleCatalogCache.saveChapters(context, book.asin, grant.chapters)
                grant.chapters
            }
            cachedChapters.isNotEmpty() -> cachedChapters
            else -> emptyList()
        }
        val proj = AudibleChapterProjector(
            rawChapters = effectiveChapters,
            fallbackTitle = book.title,
            fallbackTotalDurationMs = book.runtimeLengthMs,
        )
        projector = proj
        val initialAbsoluteMs = proj.resolveInitialPosition(requestedChapterIndex, requestedPositionMs, savedProgress)
        val backend = getOrCreateBackend(context)

        // Publish initial projected chapter immediately before/as backend starts
        syncProjectionAndPublish(
            absolutePosMs = initialAbsoluteMs,
            isPlaying = true,
            isBuffering = true,
            forceSave = false,
        )

        backend.load(
            grant = grant,
            initialPositionMs = initialAbsoluteMs,
            speed = playbackSpeed,
            playWhenReady = true,
            listener = object : AudiblePlayerBackend.Listener {
                override fun onPlaybackChanged(isPlaying: Boolean, isBuffering: Boolean, absolutePositionMs: Long) {
                    if (!active.value) return
                    syncProjectionAndPublish(
                        absolutePosMs = absolutePositionMs,
                        isPlaying = isPlaying,
                        isBuffering = isBuffering,
                        forceSave = !isPlaying,
                    )
                    mainHandler.removeCallbacks(ticker)
                    if (isPlaying) {
                        mainHandler.postDelayed(ticker, TICK_INTERVAL_MS)
                    }
                }

                override fun onEnded(finalPositionMs: Long) {
                    if (!active.value) return
                    val ctx = resolveContext()
                    val curProj = projector
                    if (ctx != null && curProj != null) {
                        curProj.chapters.forEach { ch ->
                            AudibleProgressStore.setChapterFinished(ctx, book.asin, ch.index, true)
                        }
                        AudibleProgressStore.savePosition(ctx, book.asin, curProj.totalDurationMs, curProj.chapters.lastIndex)
                    }
                    syncProjectionAndPublish(
                        absolutePosMs = finalPositionMs,
                        isPlaying = false,
                        isBuffering = false,
                        forceSave = true,
                    )
                    mainHandler.removeCallbacks(ticker)
                }

                override fun onError(message: String) {
                    io.github.thake.soundnest.host.android.Logbook.log("AudiblePlayer playback error: $message")
                    playingState = false
                    bufferingState = false
                    lastProjected?.let { projPos ->
                        syncProjectionAndPublish(projPos.absolutePositionMs, isPlaying = false, isBuffering = false, forceSave = true)
                    }
                }
            },
        )
    }

    private fun chapterSongModel(
        book: AudibleBook,
        chapter: AudibleChapter,
        played: Boolean = false,
        playedPositionMs: Long = 0L,
    ): SongsModel {
        val authorLine = book.authors.joinToString(", ").ifBlank { "Audible" }
        val uri = AudibleUris.chapterUri(book.asin, chapter.index)
        val fallbackChapterTitle = resolveContext()?.getString(io.github.thake.soundnest.R.string.audible_chapter_number, chapter.index + 1)
            ?: "Chapter ${chapter.index + 1}"
        return SongsModel(
            id = ("uri:$uri").hashCode() and 0x7fffffff,
            title = chapter.title.ifBlank { fallbackChapterTitle },
            album = book.title,
            singer = authorLine,
            coverUri = book.coverUrl,
            url = uri,
            durationMs = chapter.lengthMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            description = book.description,
            releaseDate = "",
            playedPositionMs = playedPositionMs,
            played = played,
            showId = AudibleUris.showId(book.asin),
        )
    }

    /** Full chapter list for the currently active Audible book (including earlier chapters and local progress). */
    fun currentChapters(context: Context? = resolveContext()): List<SongsModel> {
        val book = currentBook ?: return emptyList()
        val proj = projector ?: return emptyList()
        val progress = context?.let { AudibleProgressStore.get(it, book.asin) }
        val curProj = lastProjected
        return proj.chapters.map { ch ->
            val finished = progress?.finishedChapters?.contains(ch.index) == true
            val pos = when {
                finished -> ch.lengthMs
                curProj?.chapter?.index == ch.index -> curProj.chapterPositionMs
                else -> 0L
            }
            chapterSongModel(book, ch, played = finished, playedPositionMs = pos)
        }
    }

    private fun syncProjectionAndPublish(
        absolutePosMs: Long,
        isPlaying: Boolean,
        isBuffering: Boolean,
        forceSave: Boolean,
    ) {
        val book = currentBook ?: return
        val proj = projector ?: return
        val projected = proj.project(absolutePosMs)
        val prevProjected = lastProjected
        lastProjected = projected
        playingState = isPlaying
        bufferingState = isBuffering

        val ctx = resolveContext()
        val nowMs = System.currentTimeMillis()
        if (ctx != null) {
            // Mark previous chapter finished when advancing naturally across a chapter boundary
            if (prevProjected != null && projected.chapter.index == prevProjected.chapter.index + 1 &&
                prevProjected.chapterPositionMs >= (prevProjected.chapterDurationMs - 2_000L).coerceAtLeast(0L)
            ) {
                AudibleProgressStore.setChapterFinished(ctx, book.asin, prevProjected.chapter.index, true, nowMs)
            }
            if (forceSave || nowMs - lastDiskSaveAtMs >= SAVE_INTERVAL_MS) {
                lastDiskSaveAtMs = nowMs
                AudibleProgressStore.savePosition(
                    context = ctx,
                    asin = book.asin,
                    positionMs = projected.absolutePositionMs,
                    chapterIndex = projected.chapter.index,
                    nowMs = nowMs,
                    syncDisk = forceSave,
                )
            }
        }

        val currentSong = chapterSongModel(book, projected.chapter)
        val chapterUri = AudibleUris.chapterUri(book.asin, projected.chapter.index)
        SongPlayer.onAudibleState(
            chapterSong = currentSong,
            chapterUri = chapterUri,
            bookUri = AudibleUris.bookUri(book.asin),
            bookTitle = book.title,
            isPlaying = isPlaying,
            isBuffering = isBuffering,
            chapterPosMs = projected.chapterPositionMs,
            chapterDurMs = projected.chapterDurationMs,
        )

        if (lastPublishedChapterIndex != projected.chapter.index) {
            lastPublishedChapterIndex = projected.chapter.index
            val upcoming = proj.chapters
                .drop(projected.chapter.index + 1)
                .map { ch -> ch.index to chapterSongModel(book, ch) }
            SongPlayer.onAudibleQueue(currentSong, upcoming)
        }
    }

    fun resume() {
        if (!active.value) return
        activeBackendOrNull()?.resume()
    }

    fun pause() {
        if (!active.value) return
        val b = activeBackendOrNull() ?: return
        b.pause()
        syncProjectionAndPublish(
            absolutePosMs = b.currentPositionMs(),
            isPlaying = false,
            isBuffering = false,
            forceSave = true,
        )
    }

    fun stop() {
        val wasActive = active.value
        loadJob?.cancel()
        loadJob = null
        mainHandler.removeCallbacks(ticker)
        if (wasActive) {
            val b = activeBackendOrNull()
            val ctx = resolveContext()
            val book = currentBook
            val proj = lastProjected
            if (ctx != null && book != null && proj != null && b != null) {
                AudibleProgressStore.savePosition(
                    context = ctx,
                    asin = book.asin,
                    positionMs = b.currentPositionMs(),
                    chapterIndex = proj.chapter.index,
                    syncDisk = true,
                )
            }
            b?.stop()
        }
        active.value = false
        playingState = false
        bufferingState = false
        currentBook = null
        projector = null
        lastProjected = null
        lastPublishedChapterIndex = -1
        if (wasActive) {
            SongPlayer.onAudibleStopped()
        }
    }

    fun seekTo(chapterOffsetMs: Long) {
        if (!active.value) return
        val proj = projector ?: return
        val b = activeBackendOrNull() ?: return
        val currentIdx = lastProjected?.chapter?.index ?: proj.project(b.currentPositionMs()).chapter.index
        val targetAbs = proj.seekWithinChapter(currentIdx, chapterOffsetMs)
        b.seekToAbsolute(targetAbs)
        syncProjectionAndPublish(targetAbs, b.isPlaying(), b.isBuffering(), forceSave = true)
    }

    fun skipBy(deltaMs: Long) {
        if (!active.value) return
        val proj = projector ?: return
        val b = activeBackendOrNull() ?: return
        val targetAbs = proj.skipBy(b.currentPositionMs(), deltaMs)
        b.seekToAbsolute(targetAbs)
        syncProjectionAndPublish(targetAbs, b.isPlaying(), b.isBuffering(), forceSave = true)
    }

    fun next() {
        if (!active.value) return
        val proj = projector ?: return
        val b = activeBackendOrNull() ?: return
        val nextStart = proj.nextChapterPosition(b.currentPositionMs())
        if (nextStart != null) {
            b.seekToAbsolute(nextStart)
            syncProjectionAndPublish(nextStart, b.isPlaying(), b.isBuffering(), forceSave = true)
        } else {
            pause()
        }
    }

    fun previous() {
        if (!active.value) return
        val proj = projector ?: return
        val b = activeBackendOrNull() ?: return
        val prevStart = proj.previousChapterPosition(b.currentPositionMs())
        b.seekToAbsolute(prevStart)
        syncProjectionAndPublish(prevStart, b.isPlaying(), b.isBuffering(), forceSave = true)
    }

    fun skipToChapter(chapterIndex: Int) {
        if (!active.value) return
        val proj = projector ?: return
        val b = activeBackendOrNull() ?: return
        val targetAbs = proj.absolutePositionForChapter(chapterIndex, 0L)
        b.seekToAbsolute(targetAbs)
        syncProjectionAndPublish(targetAbs, b.isPlaying(), b.isBuffering(), forceSave = true)
    }

    fun setSpeed(speed: Double) {
        playbackSpeed = speed.toFloat().coerceIn(0.5f, 3.5f)
        if (active.value) {
            activeBackendOrNull()?.setSpeed(playbackSpeed)
        }
    }

    fun setGain(gain: Float) {
        if (active.value) {
            activeBackendOrNull()?.setGain(gain)
        }
    }

    fun isPlaying(): Boolean = active.value && playingState
    fun isBuffering(): Boolean = active.value && bufferingState
    fun duration(): Long = if (active.value) lastProjected?.chapterDurationMs ?: 0L else 0L
    fun position(): Long {
        if (!active.value) return 0L
        val proj = projector ?: return lastProjected?.chapterPositionMs ?: 0L
        val abs = activeBackendOrNull()?.currentPositionMs() ?: return lastProjected?.chapterPositionMs ?: 0L
        return proj.project(abs).chapterPositionMs
    }

    fun enforcePolicy(policy: ParentalPolicy) {
        if (!active.value) return
        val asin = currentBook?.asin ?: return
        if (!ParentalFilterEngine.isAudibleBookAllowed(asin, policy)) {
            io.github.thake.soundnest.host.android.Logbook.log("AudiblePlayer: stopping $asin after parental policy revocation")
            stop()
        }
    }

    internal fun configureForTests(
        context: Context,
        backend: AudiblePlayerBackend,
        grantLoader: suspend (String) -> AudibleStreamGrant,
    ) {
        stop()
        appContextOverride = context.applicationContext
        testBackend = backend
        testGrantLoader = grantLoader
    }

    internal fun resetForTests() {
        stop()
        testBackend?.release()
        prodBackend?.release()
        testBackend = null
        prodBackend = null
        testGrantLoader = null
        appContextOverride = null
        playbackSpeed = 1.0f
    }

    private object RuleLabelFallback {
        fun titleFor(context: Context, asin: String): String =
            io.github.thake.soundnest.feature.parental.settings.RuleLabelStore.resolve(context, "audible", asin).title
    }
}
