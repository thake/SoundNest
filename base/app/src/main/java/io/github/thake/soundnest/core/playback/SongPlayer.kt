package io.github.thake.soundnest.core.playback

import io.github.thake.soundnest.feature.androidauto.ArtworkProvider

import io.github.thake.soundnest.feature.lyrics.settings.LyricsSettings
import io.github.thake.soundnest.feature.lyrics.translation.settings.LyricsTranslationSettings


import io.github.thake.soundnest.core.di.Graph
import io.github.thake.soundnest.core.util.AppForeground
import io.github.thake.soundnest.core.util.Haptics
import io.github.thake.soundnest.feature.androidauto.AutoBrowser
import io.github.thake.soundnest.feature.recap.data.ListenHistory
import io.github.thake.soundnest.feature.widget.NowPlayingWidget
import io.github.thake.soundnest.feature.speakers.connect.SpeakerKeepAlive
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import io.github.thake.soundnest.api.ItemKind
import io.github.thake.soundnest.api.SoundNestApi
import io.github.thake.soundnest.api.PlayerState
import io.github.thake.soundnest.api.Repeat
import io.github.thake.soundnest.host.android.SoundNestRuntime
import io.github.thake.soundnest.host.android.NowPlayingInfo
import io.github.thake.soundnest.host.android.PlaybackService
import io.github.thake.soundnest.core.model.SongsModel
import io.github.thake.soundnest.feature.localfiles.data.LocalFileMatch.isDeviceFile
import io.github.thake.soundnest.feature.localfiles.data.LocalFileMatch.isUnmatchedLocal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * The UI's player facade over two players: [LocalPlayer] (the AutoMix engine) plays the phone's
 * local files and live radio, while the account's player ([SoundNestApi.player], Spotify's Web
 * Player) plays Spotify music and episodes here or on another device through Spotify Connect.
 *
 * Screens call playSong / play / pause / seekTo / getCurrentPosition…; the controls go to the
 * player heard. The account's player keeps its own queue, so next/previous/shuffle/repeat are its
 * own while it plays.
 */
object SongPlayer {
    private const val SPOTIFY_TRACK_PREFIX = "spotify:track:"
    private const val PENDING_MS = 6000L
    /** How often the state is read again while another device plays and the app is on screen. */
    private const val REMOTE_REFRESH_MS = 3_000L
    /**
     * The same with the app in the background or the screen off: the notification's clock runs on
     * by itself and Spotify's own messages bring a change; the song's end is read at once (overdue).
     */
    private const val REMOTE_BACKGROUND_REFRESH_MS = 30_000L
    /** How often the state is read again while this phone plays (the page pushes its changes). */
    private const val LOCAL_REFRESH_MS = 15_000L
    /** Nothing to read (this phone is idle): the refresh loop sleeps this long, or until a state or a Connect device wakes it. */
    private const val IDLE_WAKE_MS = 60_000L
    /** A song this far past its end with no new state from the page: the state is read at once. */
    private const val SONG_END_GRACE_MS = 1_500L
    /** A transport command that has not answered by then is given up (the next one runs). */
    private const val TRANSPORT_TIMEOUT_MS = 15_000L
    /**
     * A transport command still running this long when a newer one comes is stuck (a dead
     * connection, a page that does not answer): it is given up at once rather than holding the
     * newer one for the rest of [TRANSPORT_TIMEOUT_MS]. A skip normally answers well within it.
     */
    private const val TRANSPORT_STUCK_MS = 3_000L
    /**
     * The longest a skip is shown ahead of the page: its burst gap, its turn behind a stuck command
     * and its own timeout. Once it is answered, the page has [SKIP_ANSWERED_MS] (see [skipDone]).
     */
    private const val SKIP_PENDING_MAX_MS = TRANSPORT_TIMEOUT_MS + 5_000L
    /**
     * An answered skip whose song has not come by then: the old song is heard again (the engine
     * brings its sound back 2.5 s after the skip, SpotifyPlayer.FADE_BACK_MAX_MS), so the screen
     * shows it again too. Reported: the next song's cover on screen, the old song playing on.
     */
    private const val SKIP_ANSWERED_MS = 3_000L
    /** How long a play has to show up in the page's state before it is sent again. */
    private const val PLAY_CHECK_MS = 4_000L
    /** A resume of an episode that the page did not take is asked for again after that. */
    private const val EPISODE_RESUME_CHECK_MS = 2_500L
    /** "Next" taps closer than this are sent as one jump. */
    private const val SKIP_BURST_MS = 350L
    /** Skip commands are at least this far apart; taps in between add up (see [next]). */
    private const val MIN_SKIP_GAP_MS = 2_000L
    /** An ad break shorter than this is never shown (see the adPlaying collector). */
    private const val AD_SHOW_AFTER_MS = 2_000L
    /** After a local file starts, engine states this recent still predate the pause. */
    private const val LOCAL_GRACE_MS = 2500L
    /** An answered command's state reaches the app within this (see [reconcile]). */
    private const val COMMAND_SETTLE_MS = 1_500L
    /** A hold's end is checked this long after it, so the hold is over by then (see [reconcile]). */
    private const val RECONCILE_MARGIN_MS = 100L
    /** The page's state is read again that long at most before the screen is put right. */
    private const val RECONCILE_READ_MS = 3_000L
    /** A seek is sent within this, or given up for this try (see [seekTo]). */
    private const val SEEK_SEND_MS = 5_000L
    /** How long a seek has to show up in the page's state before it is sent again. */
    private const val SEEK_CHECK_MS = 3_000L
    /** The longest a seek holds the clock against the page (both tries and their sends). */
    private const val SEEK_HOLD_MS = 2 * (SEEK_SEND_MS + SEEK_CHECK_MS)
    /** A page position this close to a seek's target (or past it by the time played) is the seek. */
    private const val SEEK_NEAR_MS = 2_000L
    private const val TAG = "MspPlayer"
    private const val LIKED_CONTEXT = "spotify:collection:tracks"
    /** A picked song from this row on is also sent by its place in the list (see [rowIndex]). */
    private const val INDEXED_ROW_FROM = 100
    /** The car's queue shows the current song and at most this many after it (see [publishCarQueue]). */
    private const val CAR_QUEUE_MAX = 50
    /** A burst of queue changes (a skip, a list loading) reaches the car once, this long after its last one. */
    private const val CAR_QUEUE_SETTLE_MS = 300L

    private val api: SoundNestApi get() = SoundNestRuntime.api
    private val scope get() = SoundNestRuntime.scope

    // ── Player state ──
    @Volatile private var playing = false
    private val bufferingState = mutableStateOf(false)
    /** Observable startup state: visual lookups wait for the audio transport. */
    val isBuffering: Boolean get() = bufferingState.value

    /**
     * An ad break is on (T4). The page publishes no track while one plays — the song before it
     * stays in the state, buffering — so the screens show this instead of that stale song.
     */
    private val adState = mutableStateOf(false)
    val adPlaying: Boolean get() = adState.value
    @Volatile private var durationMs = 0L
    @Volatile private var positionMs = 0L
    @Volatile private var positionAt = 0L
    /** The page says it plays, but nothing is heard (see [onSoundStalled]): the clock waits. */
    @Volatile private var soundStalled = false
    @Volatile var currentTrackId: String = ""
        private set
    @Volatile var currentContextUri: String = ""
        private set
    @Volatile private var stateSeen = false

    // A track the user just picked: the player reports the previous one for a moment,
    // which must not overwrite the optimistic now-playing state.
    @Volatile private var pendingTrackId: String? = null
    @Volatile private var pendingItemUri: String? = null
    @Volatile private var pendingUntil = 0L

    // After play / pause the player reports the old state for a moment; keep the requested one.
    @Volatile private var wantPlayingUntil = 0L
    @Volatile private var wantPausedUntil = 0L

    private fun intendPlaying() { wantPlayingUntil = SystemClock.elapsedRealtime() + 4000; wantPausedUntil = 0; reconcileAfter(intentEnded, 4000) }
    private fun intendPaused() { wantPausedUntil = SystemClock.elapsedRealtime() + 1500; wantPlayingUntil = 0; reconcileAfter(intentEnded, 1500) }

    /**
     * Puts the screen back on what the page last said once a hold is over (a play or pause
     * wanted, a song shown before it plays). The page pushes changes only, and a state read again
     * that did not change is no news either: a play the page never took left the clock counting
     * over silence, and a skip that never landed left the next song on screen while the old one
     * played on (reported).
     */
    private fun reconcile() {
        // A command still on its way: its own end puts the screen right (see [transport]).
        fun leftAlone() = !acceptsSessionUpdates() || LocalPlayer.active.value || io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayer.active.value || transportsInFlight.get() > 0
        if (leftAlone()) return
        scope.launch {
            // Read again first: the page's last push can be lost on the way (its events buffer
            // drops the oldest when its main thread stalls), and the screen must not go back to
            // "paused" over a song that plays.
            kotlinx.coroutines.withTimeoutOrNull(RECONCILE_READ_MS) { runCatching { SoundNestRuntime.spotify.player.refresh() } }
            if (leftAlone()) return@launch
            val s = api.player.state.value
            if (s != null) { applyState(s); return@launch }
            // The page never reported anything (it did not load): nothing plays, whatever was tapped.
            if (playing) {
                playing = false
                bufferingState.value = false
                boundState?.updatePlayingState(false)
                pushNotification(null, boundState?.likeState?.value ?: false)
            }
        }
    }
    private val intentEnded = Runnable { reconcile() }
    private val pendingEnded = Runnable { reconcile() }
    private val commandSettled = Runnable { reconcile() }
    private fun reconcileAfter(check: Runnable, ms: Long) {
        main.removeCallbacks(check)
        main.postDelayed(check, ms.coerceAtLeast(0L) + RECONCILE_MARGIN_MS)
    }

    /**
     * Not heard yet: the page buffers, its sound stands still, or a play is wanted that the page
     * has not started. The clock waits and the screens show the song loading.
     */
    private fun waiting(s: PlayerState) = s.isBuffering || soundStalled || (playing && s.isPaused)

    private fun resolvePlaying(paused: Boolean): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now < wantPlayingUntil) {
            if (!paused) wantPlayingUntil = 0
            return true
        }
        if (now < wantPausedUntil) {
            if (paused) wantPausedUntil = 0
            return false
        }
        return !paused
    }

    // Context (album/playlist/artist/liked) a track was listed in, so playing it keeps
    // Spotify's own queue and autoplay for that context.
    private val contextRegistry = ConcurrentHashMap<String, String>()

    /** Queue items of the local player (Local Files). */
    const val LOCAL = "local"
    /** Queue items of Audible audiobook chapters. */
    const val AUDIBLE = "audible"

    /** Spotify's real queue. */
    data class QueueItem(val song: SongsModel, val uri: String, val uid: String, val provider: String)
    data class EngineQueue(
        val current: SongsModel? = null,
        val queued: List<QueueItem> = emptyList(),
        val nextUp: List<QueueItem> = emptyList(),
    )
    private val _queue = mutableStateOf(EngineQueue())
    private var apiQueue: io.github.thake.soundnest.api.Queue? = null

    /** What the now-playing screen needs beyond the track itself. */
    data class NowPlaying(
        val contextUri: String = "",
        val contextName: String = "",
        val provider: String = "",
        val uri: String = "",
        val explicit: Boolean = false,
        val albumUri: String = "",
        val prev: SongsModel? = null,
        val next: SongsModel? = null,
    )
    private val _nowPlaying = mutableStateOf(NowPlaying())
    val nowPlaying: State<NowPlaying> get() = _nowPlaying
    val engineQueue: State<EngineQueue> get() = _queue
    /** The rows the car's queue was last given, after the current song: the row with id n is carQueue[n - 1]. */
    private var carQueue: List<QueueItem> = emptyList()

    private val main = Handler(Looper.getMainLooper())
    private var boundState: CurrentSongState? = null

    /** Wires the API's flows into the UI's now-playing state. Call once, on the main thread. */
    fun attachEngine(state: CurrentSongState) {
        boundState = state
        io.github.thake.soundnest.host.android.PhoneCall.listener = { busy -> main.post { phoneCall(busy) } }
        io.github.thake.soundnest.feature.lyrics.data.LyricsApi.extraSources =
            LyricsTranslationSettings.isExtraSources(Graph.app)
        io.github.thake.soundnest.feature.lyrics.data.LyricsApi.extraSourcesOff =
            LyricsTranslationSettings.isExtraSourcesOff(Graph.app)
        io.github.thake.soundnest.feature.lyrics.data.LyricsApi.wordSync =
            LyricsSettings.isWordSync(Graph.app)
        restoreLastTrack(state)
        restoreRepeat(state)
        scope.launch { api.player.state.collect { s -> if (acceptsSessionUpdates()) applyState(s) } }
        scope.launch {
            io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore.policyFlow.collect {
                main.post { enforceParentalPolicyNow() }
            }
        }
        NativeAudio.attach()
        // Another device plays (the phone as a remote): the state is read again every few seconds
        // so the notification, the mini player and the slider keep up. Reported: the notification
        // stopped refreshing in the background — this loop waited for _activeDevice, which only a
        // pull-back sets, so an app started in the background never ran it.
        // This phone plays: the page pushes every change itself (it is not throttled while it plays, see
        // AwakeWebView), so reading the whole state and queue every 3 s only kept both processes
        // awake, screen off. It is read every LOCAL_REFRESH_MS as a safety net, and at once when
        // the song should have ended without the page saying what came next.
        // With nothing playing and no Connect device, the loop below only found it had nothing to do,
        // every 3 s, waking the main thread: it sleeps then, and a pushed state or a Connect device
        // wakes it (the long timeout is only a net against a missed wake).
        SoundNestRuntime.spotify.player.remoteSelected = { _activeDevice.value != null || chosenRemote != null }
        scope.launch { androidx.compose.runtime.snapshotFlow { _activeDevice.value }.collect { refreshWake.trySend(Unit) } }
        scope.launch {
            var lastRead = 0L
            while (true) {
                if (_activeDevice.value == null && !PlaybackService.isRunning && !playing && !isBuffering) {
                    kotlinx.coroutines.withTimeoutOrNull(IDLE_WAKE_MS) { refreshWake.receive() }
                    continue
                }
                kotlinx.coroutines.delay(REMOTE_REFRESH_MS)
                val remote = _activeDevice.value != null
                if (!remote && !PlaybackService.isRunning) continue
                if (!SoundNestRuntime.spotify.ready.value) continue
                val now = SystemClock.elapsedRealtime()
                val overdue = playing && !isBuffering && durationMs > 0 &&
                    positionMs + (now - positionAt) > durationMs + SONG_END_GRACE_MS
                val every = when {
                    !remote -> LOCAL_REFRESH_MS
                    AppForeground.inForeground -> REMOTE_REFRESH_MS
                    else -> REMOTE_BACKGROUND_REFRESH_MS
                }
                if (!overdue && now - lastRead < every) continue
                lastRead = now
                // The queue only with a new song: it is tens of KB of JSON, and it moves with the song.
                runCatching { SoundNestRuntime.spotify.player.refreshState() }
            }
        }
        scope.launch { api.player.queue.collect { q -> if (acceptsSessionUpdates()) applyQueue(q) } }
        // Android Auto's queue follows the one on screen (Spotify's or the local player's), sent
        // only when it changes: the position reports never reach it.
        scope.launch {
            androidx.compose.runtime.snapshotFlow { _queue.value to _nowPlaying.value.contextName }.collectLatest { (q, name) ->
                kotlinx.coroutines.delay(CAR_QUEUE_SETTLE_MS)
                publishCarQueue(q, name)
            }
        }
        scope.launch { SoundNestRuntime.spotify.player.stalled.collect { on -> onSoundStalled(on) } }
        // Smart shuffle plays a mixed order as the list and leaves Spotify's own shuffle off, so
        // turning it off sends no shuffle command at all (the button finds shuffle already off):
        // the list's own order comes back here.
        scope.launch {
            var was = io.github.thake.soundnest.feature.library.ui.SmartShuffle.on.value
            androidx.compose.runtime.snapshotFlow { io.github.thake.soundnest.feature.library.ui.SmartShuffle.on.value }.collect { now ->
                val ended = was.takeIf { now == null }
                was = now
                if (ended != null) endSmartShuffle(ended)
            }
        }
        scope.launch { api.library.changes.collect { refreshCurrentLiked() } }
        // T4: the player keeps the ad out of [api.player.state] on purpose (the last song stays
        // there, buffering); its own flag is collected here so the UI can say an ad is on.
        scope.launch {
            kotlinx.coroutines.flow.combine(
                SoundNestRuntime.spotify.player.adPlaying,
                androidx.compose.runtime.snapshotFlow { LocalPlayer.active.value },
            ) { ad, local -> ad && !local }.distinctUntilChanged().collectLatest { on ->
                if (on) {
                    kotlinx.coroutines.delay(AD_SHOW_AFTER_MS)
                    // Spotify limiting playback holds every repair, the ad's too: the limit is what
                    // the user is told (its toast), not an ad for as long as it lasts (50 s seen).
                    // The same when it refuses the account its licences (#134): the ad it is stuck on
                    // stays out of the screens, the toast said why nothing plays.
                    while (SoundNestRuntime.host.licenseLimited() || SoundNestRuntime.spotify.player.licencesHeld) kotlinx.coroutines.delay(1_000)
                }
                onAdPlaying(on)
            }
        }
        scope.launch {
            SoundNestRuntime.spotify.player.refused.collect {
                // No Widevine in this WebView: the refusal is the WebView's, not the account's.
                val message = if (SoundNestRuntime.spotify.player.drmMissing.value) io.github.thake.soundnest.R.string.drm_missing else io.github.thake.soundnest.R.string.play_refused
                android.widget.Toast.makeText(Graph.app, message, android.widget.Toast.LENGTH_LONG).show()
            }
        }
        scope.launch {
            SoundNestRuntime.spotify.player.refusedAll.collect {
                android.widget.Toast.makeText(Graph.app, io.github.thake.soundnest.R.string.play_refused_all, android.widget.Toast.LENGTH_LONG).show()
            }
        }
        scope.launch {
            SoundNestRuntime.spotify.player.skipChained.collect {
                android.widget.Toast.makeText(Graph.app, io.github.thake.soundnest.R.string.songs_skipped_by_spotify, android.widget.Toast.LENGTH_LONG).show()
            }
        }
        scope.launch {
            SoundNestRuntime.spotify.player.licencesRefused.collect {
                val drm = io.github.thake.soundnest.host.android.Widevine.version
                val text = if (io.github.thake.soundnest.host.android.Widevine.tooOld(drm))
                    Graph.app.getString(io.github.thake.soundnest.R.string.licences_refused_old_widevine, drm?.substringBefore('@'))
                else Graph.app.getString(io.github.thake.soundnest.R.string.licences_refused_by_spotify)
                android.widget.Toast.makeText(Graph.app, text, android.widget.Toast.LENGTH_LONG).show()
            }
        }
        // Said once the page has asked its WebView: a device that cannot decrypt Spotify's streams
        // plays nothing (no sound, 0:00), and nothing else in the app says why.
        scope.launch {
            SoundNestRuntime.spotify.player.drmMissing.first { it }
            android.widget.Toast.makeText(Graph.app, io.github.thake.soundnest.R.string.drm_missing, android.widget.Toast.LENGTH_LONG).show()
        }
        scope.launch {
            SoundNestRuntime.host.licenseLimitedUntil.collect { until ->
                if (until > SystemClock.elapsedRealtime()) main.post {
                    playing = false
                    boundState?.updatePlayingState(false)
                    android.widget.Toast.makeText(Graph.app, io.github.thake.soundnest.R.string.spotify_limited, android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
        PlaybackService.controls = Controls
    }

    /** System controls (notification, headset, car) land here. */
    private object Controls : io.github.thake.soundnest.host.android.PlaybackControls {
        // The session says the new state at once, not when the page answers: a Bluetooth headset or
        // stereo reads it (AVRCP play status) to choose between play and pause, and kept sending
        // "pause" to a paused player while the session still said playing (#108).
        override fun play() { SongPlayer.play(by = "media session"); pushNotification(null, boundState?.likeState?.value ?: false) }
        override fun pause() { SongPlayer.pause(by = "media session"); pushNotification(null, boundState?.likeState?.value ?: false) }
        override fun next() = SongPlayer.next(by = "media session")
        override fun previous() = SongPlayer.previous(by = "media session")
        override fun seekTo(ms: Long) = SongPlayer.seekTo(ms)
        override fun setLiked(liked: Boolean) = SongPlayer.setLiked(liked)
        override fun playFromMediaId(mediaId: String) = AutoBrowser.play(mediaId)
        override fun playFromSearch(query: String) { scope.launch { AutoBrowser.playFromSearch(query) } }
        override fun setRemoteVolume(volume: Int) = SongPlayer.setRemoteVolume(volume)
        override fun setShuffle(on: Boolean) = SongPlayer.setShuffle(on)
        override val isEpisode: Boolean get() = SongPlayer.isEpisode
        override fun skipBy(deltaMs: Long) {
            // Past the end of the episode a +15 s is the normal end: on to the next item.
            val duration = getDuration()
            if (deltaMs > 0 && duration > 0 && getCurrentPosition() + deltaMs >= duration) SongPlayer.next(by = "media session")
            else SongPlayer.skipBy(deltaMs)
        }
        // Row 0 is the song playing: a tap on it changes nothing.
        override fun skipToQueueItem(id: Long) { carQueue.getOrNull(id.toInt() - 1)?.let(SongPlayer::skipTo) }
    }

    // ── Last track: shown at launch (mini player) before the Web Player reports ──

    private fun lastTrackPrefs() = Graph.app.getSharedPreferences("core_playback_session", Context.MODE_PRIVATE)
    private var savedLast = ""
    // Who played the saved session and when it was saved: Spotify's state at launch is older than a
    // local-player session saved after it, and must not replace it.
    private var savedSource = io.github.thake.soundnest.core.playback.LastSession.SOURCE_SPOTIFY
    private var savedAtMs = 0L
    @Volatile private var resumeWhenReady = false

    private fun restoreLastTrack(state: CurrentSongState) {
        if (state.title.value.isNotBlank() || !acceptsSessionUpdates()) return
        val policy = io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore.current(Graph.app)
        val p = lastTrackPrefs()
        val uri = p.getString("uri", "").orEmpty()
        val title = p.getString("title", "").orEmpty()
        if (uri.isBlank() || title.isBlank()) return
        val id = trackIdOf(uri)
        val contextUri = p.getString("contextUri", "").orEmpty()
        val restoredSong = SongsModel(
            id = songIdFor(id, uri),
            title = title,
            album = p.getString("album", "").orEmpty(),
            singer = p.getString("singer", "").orEmpty(),
            coverUri = p.getString("cover", "").orEmpty(),
            url = if (uri.startsWith("spotify:episode:")) "episode:" + uri.substringAfterLast(':') else buildSpotifyPlayQuery(id, title, p.getString("singer", "").orEmpty()),
            spotifyTrackId = id,
        )
        if (!io.github.thake.soundnest.feature.parental.filter.ParentalFilterEngine.isTrackAllowed(restoredSong, policy, contextUri)) return
        currentTrackId = id
        savedSource = p.getString("source", null) ?: io.github.thake.soundnest.core.playback.LastSession.SOURCE_SPOTIFY
        savedAtMs = p.getLong("savedAt", 0L)
        durationMs = p.getLong("duration", 0L)
        positionMs = p.getLong("position", 0L)
        positionAt = SystemClock.elapsedRealtime()
        playing = false
        val contextName = p.getString("contextName", "").orEmpty()
        registerContextName(contextUri, contextName)
        currentContextUri = contextUri
        _nowPlaying.value = NowPlaying(uri = uri, contextUri = contextUri, contextName = contextName)
        state.updateSongState(
            coverUri = p.getString("cover", "").orEmpty(),
            title = title,
            singer = p.getString("singer", "").orEmpty(),
            playingState = false,
            songId = songIdFor(id, uri),
            songIndex = 0,
            album = p.getString("album", "").orEmpty(),
        )
        state.likeState.value = p.getBoolean("liked", false)
        // The media session has the last song, paused, before anything plays: a car or a Bluetooth
        // stereo that binds first (after the app was killed too) shows it and can resume it (#142).
        PlaybackService.update(Graph.app, NowPlayingInfo(
            id = uri, title = title, artist = p.getString("singer", "").orEmpty(), album = p.getString("album", "").orEmpty(),
            coverUrl = p.getString("cover", "").orEmpty().ifBlank { null }, durationMs = durationMs, positionMs = positionMs,
            playing = false, liked = state.likeState.value, shuffle = state.shuffle.value == true,
            episode = uri.startsWith("spotify:episode:"),
        ))
        // Spotify's own queue too: the page's replaces it once it reports.
        val rest = io.github.thake.soundnest.core.playback.LastSession.decodeQueue(p.getString("queue", null))
        if (rest.isNotEmpty()) {
            val rawQueue = EngineQueue(
                current = rest.first(),
                nextUp = rest.drop(1).map { QueueItem(it, SPOTIFY_TRACK_PREFIX + it.spotifyTrackId, "", "") },
            )
            val sanitized = io.github.thake.soundnest.feature.parental.filter.PlaybackGuardrail.sanitizeQueue(rawQueue, policy, contextUri)
            if (sanitized.current != null || sanitized.nextUp.isNotEmpty()) {
                restoredQueuePending = true
                _queue.value = sanitized
            }
        }
    }

    /** Saved on track change and on play/pause (not on every state). */
    private fun saveLastTrack(
        song: SongsModel, uri: String, liked: Boolean, contextUri: String, contextName: String,
        source: String = io.github.thake.soundnest.core.playback.LastSession.SOURCE_SPOTIFY, queue: List<SongsModel>? = null,
    ) {
        val key = "$uri|$playing|$liked|$source|$contextUri"
        if (uri.isBlank() || key == savedLast) return
        savedLast = key
        savedSource = source; savedAtMs = System.currentTimeMillis()
        val p = lastTrackPrefs()
        // No queue known yet for this song: the one saved with it stays (a pause, a like), another song's goes.
        val kept = if (queue.isNullOrEmpty() && p.getString("uri", "") == uri) p.getString("queue", "").orEmpty() else null
        p.edit()
            .putString("source", source).putLong("savedAt", savedAtMs)
            .putString("queue", kept ?: queue?.let(io.github.thake.soundnest.core.playback.LastSession::encodeQueue).orEmpty())
            .putString("contextUri", contextUri).putString("contextName", contextName)
            .putString("uri", uri).putString("title", song.title).putString("singer", song.singer)
            .putString("album", song.album).putString("cover", song.coverUri)
            .putLong("duration", durationMs).putLong("position", getCurrentPosition())
            .putBoolean("liked", liked)
            .apply()
    }

    /**
     * The saved session's position kept current while a song plays (every [POSITION_SAVE_MS]): saved on
     * track changes and play/pause only, an app closed mid-song resumed it from its start.
     */
    private val savePosition = object : Runnable {
        override fun run() {
            // Nothing plays: a look now and then for a song that started, not a wake every ten seconds.
            val on = playing || LocalPlayer.isPlaying()
            main.postDelayed(this, if (on) POSITION_SAVE_MS else POSITION_IDLE_MS)
            if (!on) return
            val p = lastTrackPrefs()
            val uri = p.getString("uri", "").orEmpty()
            val now = if (LocalPlayer.active.value) localSpotifyUri(LocalPlayer.current()) else _nowPlaying.value.uri
            if (uri.isBlank() || uri != now) return
            p.edit().putLong("position", getCurrentPosition()).apply()
        }
    }
    init { main.postDelayed(savePosition, POSITION_SAVE_MS) }

    private fun acceptsSessionUpdates() = io.github.thake.soundnest.feature.accounts.settings.OnboardingSettings.isLoggedIn(Graph.app)

    /** Call on the main thread before navigating to logout. */
    fun clearSession() {
        main.removeCallbacksAndMessages(null)
        // A play, pause or skip still waiting (a slow page, a page still loading) belongs to the
        // session that ends: it is dropped, not sent once the next account's page is ready.
        sessionGen.incrementAndGet()
        transportGen.incrementAndGet()
        runningTransport?.cancel()
        pendingTrackId = null
        pendingItemUri = null
        pendingUntil = 0L
        intentUri = ""; intentUntil = 0L
        wantPlayingUntil = 0L; wantPausedUntil = 0L
        // Its own end puts nothing back on screen: the seq it ends with is no longer the last.
        seekSeq++; seekJob?.cancel(); seekUntil = 0L; seekUri = ""
        stateSeen = false
        playing = false; bufferingState.value = false; adState.value = false; durationMs = 0L; positionMs = 0L
        currentTrackId = ""; currentContextUri = ""
        contextRegistry.clear(); contextRows.clear(); contextNames.clear(); library.clear()
        synchronized(wantedSaved) { wantedSaved.clear() }
        _queue.value = EngineQueue(); apiQueue = null; restoredQueuePending = false; _nowPlaying.value = NowPlaying()
        lastTrackPrefs().edit().clear().apply(); savedLast = ""; resumeWhenReady = false
        savedSource = io.github.thake.soundnest.core.playback.LastSession.SOURCE_SPOTIFY; savedAtMs = 0L
        _activeDevice.value = null; io.github.thake.soundnest.feature.player.sleeptimer.playback.SleepTimerController.clear()
        // The Connect device picked was the account's that leaves: the next one starts on this phone.
        chosenRemote = null
        boundState?.let { state ->
            state.updateQueue(emptyList())
            state.updateSongState("", "", "", false, 0, 0, "")
            state.likeState.value = false
        }
    }

    private fun trackIdOf(uri: String) = uri.removePrefix(SPOTIFY_TRACK_PREFIX).takeIf { uri.startsWith(SPOTIFY_TRACK_PREFIX) }.orEmpty()

    private fun songIdFor(trackId: String, fallback: String) =
        (if (trackId.isNotBlank()) "track:$trackId" else "uri:$fallback").hashCode() and 0x7fffffff

    internal fun songOf(n: io.github.thake.soundnest.api.NowPlaying): SongsModel {
        val id = trackIdOf(n.uri)
        val artists = n.artists.joinToString(", ") { it.name }
        return SongsModel(
            id = songIdFor(id, n.uri),
            title = n.name,
            album = n.album?.name.orEmpty(),
            singer = artists,
            coverUri = n.imageUrl.orEmpty(),
            url = if (n.kind == ItemKind.EPISODE) "episode:" + n.uri.substringAfterLast(':') else buildSpotifyPlayQuery(id, n.name, artists),
            spotifyTrackId = id,
            explicit = n.explicit,
            durationMs = n.durationMs.toInt(),
            albumId = n.album?.uri?.substringAfterLast(':').orEmpty(),
            artistIds = n.artists.map { it.uri.substringAfterLast(':') },
        )
    }

    /**
     * The page says it plays but its sound stands still (a slow network), or plays again. The page's
     * own clock goes on over the silence (reported: "the time was counting, the song not playing"):
     * the screen's clock stops where it is and goes on from there once the sound does.
     */
    private fun onSoundStalled(on: Boolean) {
        if (on == soundStalled) return
        // Read before the flag changes: the running clock as it stands, or the held one.
        positionMs = getCurrentPosition()
        positionAt = SystemClock.elapsedRealtime()
        soundStalled = on
        // A song shown before the page plays it is waiting already (see [markPending]).
        if (LocalPlayer.active.value || pendingTrackId != null) return
        val s = api.player.state.value ?: return
        bufferingState.value = waiting(s)
        pushNotification(null, boundState?.likeState?.value ?: false)
    }

    /** An ad break starts or ends: the notification, the widget and the screens follow (T4). */
    private fun onAdPlaying(on: Boolean) {
        if (adState.value == on) return
        adState.value = on
        pushNotification(null, boundState?.likeState?.value ?: false)
    }

    /**
     * What plays comes over the network as it plays (the service's Wi-Fi lock is for that): the
     * account's page or a radio; not a file of the phone.
     */
    private fun streamedNow(): Boolean {
        if (!LocalPlayer.active.value) return true
        val scheme = LocalPlayer.current()?.uri?.scheme ?: return false
        return scheme == "http" || scheme == "https"
    }

    /** Wakes the refresh loop of [attachEngine] when it sleeps: a state was pushed, or a Connect device took over. */
    private val refreshWake = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)

    /** Notification, lock screen, Bluetooth and Android Auto. */
    private fun pushNotification(song: SongsModel?, liked: Boolean) {
        refreshWake.trySend(Unit)
        val st = boundState
        // T4: during an ad break the page reports no track (the song before it would sit there,
        // buffering), so the notification and the widget say an ad is on instead of that song.
        val ad = adState.value
        val title = if (ad) Graph.app.getString(io.github.thake.soundnest.R.string.ad_playing) else song?.title ?: st?.title?.value.orEmpty()
        val artist = if (ad) "" else song?.singer ?: st?.singer?.value.orEmpty()
        val cover = if (ad) null else song?.coverUri ?: st?.coverUri?.value
        PlaybackService.update(Graph.app, NowPlayingInfo(
            id = if (ad) "spotify:ad" else _nowPlaying.value.uri,
            title = title,
            artist = artist,
            album = if (ad) "" else song?.album ?: st?.album?.value.orEmpty(),
            coverUrl = cover,
            durationMs = if (ad) 0L else getDuration(),
            positionMs = if (ad) 0L else getCurrentPosition(),
            playing = isPlaying() && !ad,
            buffering = isBuffering && !ad,
            liked = !ad && liked,
            shuffle = isShuffleOn(),
            episode = !ad && isEpisode,
            streamed = streamedNow(),
        ))
        NowPlayingWidget.show(Graph.app,
            title = title,
            artist = artist,
            coverUrl = cover,
            playing = isPlaying() && !ad)
    }

    /** The hidden songs ("Not interested") skipped in a row; one met twice means the list holds nothing else. */
    private val hiddenSkipped = mutableSetOf<String>()
    /** The hidden song the user asked for by hand: it plays, it is not skipped. */
    private var playedByHand = ""

    /**
     * A hidden song the player moved on to by itself (the list, the radio, the queue, a skip) is
     * skipped. Songs picked by hand play; a run of skips ends at the first song met twice, or at
     * [HIDDEN_SKIPS_MAX], and that song then plays.
     */
    private fun skipHidden(id: String, uri: String) {
        val byHand = uri == playedByHand
        if (!byHand) playedByHand = ""
        if (byHand || !io.github.thake.soundnest.feature.library.hiddensongs.settings.HiddenSongs.isHidden(id)) { hiddenSkipped.clear(); return }
        if (!hiddenSkipped.add(id) || hiddenSkipped.size > HIDDEN_SKIPS_MAX) {
            io.github.thake.soundnest.host.android.Logbook.log("hidden: only hidden songs left, $id plays")
            hiddenSkipped.clear(); playedByHand = uri
            return
        }
        io.github.thake.soundnest.host.android.Logbook.log("hidden: $id skipped (not interested)")
        main.post { if (_nowPlaying.value.uri == uri && !LocalPlayer.active.value) next(by = "hidden song") }
    }

    private const val HIDDEN_SKIPS_MAX = 30

    /**
     * "Not interested": the song is kept in a list on this phone (nothing goes to Spotify), taken
     * out of the queue, and skipped if it plays now. From then on lists skip it ([skipHidden]).
     */
    fun hideSong(song: SongsModel) {
        val id = song.spotifyTrackId
        if (id.isBlank()) return
        io.github.thake.soundnest.feature.library.hiddensongs.settings.HiddenSongs.hide(Graph.app, song)
        Haptics.confirm()
        _queue.value.let { it.queued + it.nextUp }.filter { it.provider != LOCAL && it.uri == SPOTIFY_TRACK_PREFIX + id }.forEach(::removeFromQueue)
        LocalPlayer.upcoming().filter { LocalPlayer.spotifyIdOf(it.second) == id }.map { it.first }.sortedDescending().forEach(LocalPlayer::remove)
        val playingNow = if (LocalPlayer.active.value) LocalPlayer.current()?.let(LocalPlayer::spotifyIdOf) == id else trackIdOf(_nowPlaying.value.uri) == id
        if (playingNow) next(by = "not interested")
    }

    /** The autoplay item already paused on, so it is paused once, not on every state. */
    private var autoplayHandledUri = ""
    /** Consecutive parental filter skips, capped so an empty/all-blocked list pauses instead of looping forever. */
    private val parentalSkipped = mutableSetOf<String>()
    private const val PARENTAL_SKIPS_MAX = 10

    /**
     * Immediately re-sanitizes the queue and re-evaluates the currently playing item against
     * the latest [io.github.thake.soundnest.feature.parental.settings.ParentalPolicy] so blocking a song, artist,
     * album, playlist, or show mid-playback takes effect on the spot.
     */
    fun enforceParentalPolicyNow() {
        val ctx = runCatching { Graph.app }.getOrNull() ?: return
        val policy = io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore.current(ctx)
        val q = apiQueue
        if (q != null && acceptsSessionUpdates()) {
            applyQueue(q)
        } else {
            _queue.value = io.github.thake.soundnest.feature.parental.filter.PlaybackGuardrail.sanitizeQueue(
                _queue.value,
                policy,
                currentContextUri,
            )
        }
        runCatching {
            val latestState = api.player.state.value
            if (latestState != null && acceptsSessionUpdates()) {
                applyState(latestState)
            }
        }
    }

    private fun applyState(s: PlayerState?) {
        if (s == null) return
        stateSeen = true
        if (LocalPlayer.active.value && engineWhileLocal(s)) return
        if (io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayer.active.value && engineWhileAudible(s)) return
        // Spotify's server still shows the song played before the local-player session saved at exit.
        if (io.github.thake.soundnest.core.playback.LastSession.engineStateIsStale(savedSource, savedAtMs, s.timestampMs, s.isPaused)) return
        val policy = io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore.current(Graph.app)
        val item = s.item
        val uri = item?.uri.orEmpty()
        val liveSong = item?.let(::songOf)
        val activeDev = api.devices.devices.value.firstOrNull { it.active }
        val isRemoteActive = (activeDev != null && !activeDev.local) || _activeDevice.value != null
        val remoteId = activeDev?.id ?: _activeDevice.value?.id.orEmpty()
        val isRemoteAllowed = isRemoteActive && io.github.thake.soundnest.feature.speakers.settings.TrustedNetworkGate.isDeviceAllowed(Graph.app, remoteId, isSelf = false)
        val isAdItem = adState.value || item?.kind == io.github.thake.soundnest.api.ItemKind.AD || uri.startsWith("spotify:ad:")
        when (io.github.thake.soundnest.feature.parental.filter.PlaybackGuardrail.evaluateLivePlayback(
            item = liveSong,
            isRemoteActive = isRemoteActive,
            isRemoteDeviceAllowed = isRemoteAllowed,
            policy = policy,
            contextUri = s.contextUri,
            isAd = isAdItem,
        )) {
            io.github.thake.soundnest.feature.parental.filter.LivePlaybackDecision.SUPPRESS_REMOTE_MIRROR -> {
                _activeDevice.value = null
                PlaybackService.setPlaybackDevice(false, remoteVolume)
                return
            }
            io.github.thake.soundnest.feature.parental.filter.LivePlaybackDecision.BLOCK_AND_SKIP_OR_PAUSE -> {
                if (uri.isNotEmpty()) lastPageUri = uri
                if (!s.isPaused) {
                    val contextBlocked = io.github.thake.soundnest.feature.parental.filter.ParentalFilterEngine.isContextBlocked(s.contextUri, policy)
                    if (!contextBlocked && uri.isNotEmpty() && parentalSkipped.add(uri) && parentalSkipped.size <= PARENTAL_SKIPS_MAX) {
                        io.github.thake.soundnest.host.android.Logbook.log("parental: $uri blocked, skipping")
                        main.post { next(by = "parental filter") }
                    } else {
                        io.github.thake.soundnest.host.android.Logbook.log("parental: $uri blocked, pausing")
                        parentalSkipped.clear()
                        pauseSilently()
                    }
                }
                return
            }
            io.github.thake.soundnest.feature.parental.filter.LivePlaybackDecision.ALLOW -> {
                if (item != null) parentalSkipped.clear()
            }
        }
        if (s.item != null && !s.isPaused) restoredQueuePending = false
        if (resumeWhenReady) {
            resumeWhenReady = false
            // The Web Player restored the last session paused: play what was tapped.
            if (s.isPaused) transport { api.player.resume() }
        }
        if (uri.isNotEmpty()) lastPageUri = uri
        val id = trackIdOf(uri)
        val pending = pendingTrackId
        if (pending != null) {
            // Rapid A -> B -> C picks can report B while C is pending: keep C until it shows up.
            if (id == pending || pendingItemUri == uri || SystemClock.elapsedRealtime() > pendingUntil) {
                pendingTrackId = null
                pendingItemUri = null
            } else return
        }
        val wasPlaying = playing
        val wasBuffering = isBuffering
        val predicted = getCurrentPosition()
        playing = resolvePlaying(s.isPaused)
        bufferingState.value = waiting(s)
        SpeakerKeepAlive.set(playing && _activeDevice.value == null)
        val lengthBefore = durationMs
        durationMs = item?.durationMs ?: 0L
        // The engine's own position when it is what plays: the account's player lays it over the page's (EngineStates).
        val reported = s.positionAt(System.currentTimeMillis())
        // A seek on its way: the page's old position does not take the clock back (see [seekTo]).
        if (seekHeld(uri) && seekLanded(s)) seekUntil = 0L
        val seeking = seekHeld(uri)
        // Keep the running clock when the player only nudges it (< 1 s).
        if (!seeking && !(wasPlaying && playing && !wasBuffering && !isBuffering && kotlin.math.abs(reported - predicted) < 1000)) {
            positionMs = reported
            positionAt = SystemClock.elapsedRealtime()
        }
        val trackChanged = uri != _nowPlaying.value.uri
        // Repeat once on a Spotify song: the page's repeat-track loops for good. The page seen
        // starting the song again is its one replay: repeat goes off there, and that replay ends
        // into the next song. Another song under repeat-track (a skip, a pick) cancels it.
        if (item != null && s.repeat == Repeat.TRACK && _repeatMode.value == LocalRepeat.ONCE) {
            if (LocalRepeat.pageRestarted(!trackChanged, predicted, lengthBefore, reported)) {
                io.github.thake.soundnest.host.android.Logbook.log("repeat once: ${item.name} replayed, back to off")
                setRepeatMode(LocalRepeat.OFF)
            } else if (trackChanged && _nowPlaying.value.uri.isNotBlank() && !seeking) {
                io.github.thake.soundnest.host.android.Logbook.log("repeat once: cancelled (${item.name} plays now), back to off")
                setRepeatMode(LocalRepeat.OFF)
            }
        }
        // Autoplay off (or disabled by parental policy / strict allowlist): stop once when the player moves on to a track it picked itself.
        val autoplayAllowed = io.github.thake.soundnest.feature.player.settings.AutoplayPref.on &&
            io.github.thake.soundnest.feature.parental.settings.FeatureGates.canUseAutoplayAndRadio(Graph.app)
        if (item != null && item.provider == "autoplay" && uri.startsWith(SPOTIFY_TRACK_PREFIX) &&
            uri != autoplayHandledUri && !autoplayAllowed) {
            autoplayHandledUri = uri
            if (!s.isPaused) pauseSilently()
        }
        if (item != null && trackChanged && playing && !adState.value && uri.startsWith(SPOTIFY_TRACK_PREFIX)) skipHidden(id, uri)
        currentContextUri = s.contextUri.orEmpty()
        if (item != null) { currentTrackId = id }
        _nowPlaying.value = NowPlaying(
            contextUri = s.contextUri.orEmpty(),
            contextName = s.contextName.orEmpty(),
            provider = item?.provider.orEmpty(),
            uri = uri,
            explicit = item?.explicit == true,
            albumUri = item?.album?.uri.orEmpty(),
            prev = s.previous?.let(::songOf)?.takeIf { io.github.thake.soundnest.feature.parental.filter.ParentalFilterEngine.isTrackAllowed(it, policy, s.contextUri) },
            next = s.next?.let(::songOf)?.takeIf { io.github.thake.soundnest.feature.parental.filter.ParentalFilterEngine.isTrackAllowed(it, policy, s.contextUri) },
        )
        val state = boundState ?: return
        val holding = SystemClock.elapsedRealtime() < shuffleWantedUntil
        if (holding && s.shuffle == shuffleWanted) shuffleWantedUntil = 0L
        if (!holding || s.shuffle == shuffleWanted) state.shuffle.value = s.shuffle
        // The page reports a speed other than 1 only for episodes: music stays at 1.
        _speed.value = s.speed.takeIf { it > 0.0 } ?: 1.0
        // A tap on a Spotify song's repeat is held until the page shows it.
        val repeatHeld = SystemClock.elapsedRealtime() < repeatWantedUntil
        if (repeatHeld && s.repeat.ordinal == repeatWanted) repeatWantedUntil = 0L
        if (!LocalPlayer.active.value && (!repeatHeld || s.repeat.ordinal == repeatWanted)) {
            // Repeat once is the page's repeat-track too: the page reporting it does not turn once into for good.
            if (!(s.repeat == Repeat.TRACK && _repeatMode.value == LocalRepeat.ONCE)) _repeatMode.value = s.repeat.ordinal
            state.repeat.value = s.repeat != Repeat.OFF
        }
        if (item == null) {
            state.updatePlayingState(playing)
            pushNotification(null, state.likeState.value)
            return
        }
        val liked = likedFor(uri, library[uri] ?: false)
        state.likeState.value = liked
        val song = songOf(item)
        state.updateSongState(
            coverUri = song.coverUri,
            title = song.title,
            singer = song.singer,
            playingState = playing,
            songId = song.id,
            songIndex = state.queue.value.indexOfFirst { it.id == song.id }.coerceAtLeast(0),
            album = song.album,
        )
        pushNotification(song, liked)
        saveLastTrack(song, uri, liked, s.contextUri.orEmpty(), s.contextName.orEmpty(), queue = spotifyQueueOf(id))
        trackListen(uri, song, ListenHistory.Source.SPOTIFY, playing)
        // A song never asked about (the one restored at launch is not a change): its state is asked
        // once, not read as "not liked" until the next song. A failed ask is not repeated by every state.
        if (!trackChanged && library[uri] == null) refreshCurrentLiked(fromState = true)
        if (trackChanged) {
            refreshCurrentLiked()
            refreshDevice()
        }
        val lookups = io.github.thake.soundnest.feature.system.settings.EnergySettings.state.value.policy.allowBackgroundLookups
        // Lyrics are warmed in the background too (the next song's are there when it starts), but not on a metered network.
        if (!isBuffering && playing && lookups && (AppForeground.inForeground || !io.github.thake.soundnest.feature.system.settings.DataSaver.metered)) {
            io.github.thake.soundnest.feature.lyrics.data.LyricsApi.prefetch(lyricsTrack(song), then = _nowPlaying.value.next?.let { lyricsTrack(it) })
        }
    }

    /** Songs whose saved state is being asked for by [refreshCurrentLiked] (one check in flight per song). */
    private val likeChecking = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    /** When a check of each song got no answer (even after its retry): states do not start another for a while. */
    private val likeCheckFailedAt = java.util.concurrent.ConcurrentHashMap<String, Long>()
    /** The last line [refreshCurrentLiked] logged, so the same answer for the same song is logged once. */
    @Volatile private var lastLikeLog = ""

    /**
     * Asks Spotify whether the song shown is liked (a state it confirmed less than [SAVED_FRESH_MS]
     * ago is not asked for again). [fromState]: called because the page state shows a song with no
     * answer yet, so a song whose check just failed waits [LIKE_RETRY_AFTER_MS] before the next one.
     * An answer that never comes leaves the heart as it is: no "not liked" is written for it.
     */
    private fun refreshCurrentLiked(fromState: Boolean = false) {
        val uri = currentLikeUri().takeIf { it.startsWith(SPOTIFY_TRACK_PREFIX) || it.startsWith("spotify:episode:") } ?: return
        if (fromState && SystemClock.elapsedRealtime() - (likeCheckFailedAt[uri] ?: -LIKE_RETRY_AFTER_MS) < LIKE_RETRY_AFTER_MS) return
        if (!likeChecking.add(uri)) return
        scope.launch {
            try {
                // Through the shared, cached request path: a state Spotify confirmed less than
                // [SAVED_FRESH_MS] ago is not asked for again (its row asked for it already).
                var check = checkLibrary(listOf(uri))
                if (check.failed) {
                    // The page was not ready, or the request was lost: once more, for this song only.
                    kotlinx.coroutines.delay(LIKE_RETRY_MS)
                    if (currentLikeUri() == uri) check = checkLibrary(listOf(uri))
                }
                val answer = when {
                    check.failed -> "failed, from Spotify, song $uri, reason ${savedError.ifBlank { "no answer" }}"
                    uri in check.asked -> "${if (uri in check.saved) "liked" else "not liked"}, from Spotify, song $uri"
                    else -> "${if (uri in check.saved) "liked" else "not liked"}, from memory, song $uri"
                }
                val line = "liked check: answer $answer"
                if (line != lastLikeLog) { lastLikeLog = line; io.github.thake.soundnest.host.android.Logbook.log(line) }
                if (check.failed) { likeCheckFailedAt[uri] = SystemClock.elapsedRealtime(); return@launch }
                likeCheckFailedAt.remove(uri)
                if (uri in check.asked) onLibraryItem(uri, uri in check.saved)
            } finally {
                likeChecking.remove(uri)
            }
        }
    }

    /** The app is on screen again, or a player opens: the song shown is asked for if its state is old (see [SAVED_FRESH_MS]). */
    fun refreshLikedIfStale() = refreshCurrentLiked()

    /** Back from the local player: the Spotify queue again. */
    fun restoreEngineQueue() = main.post { _queue.value = EngineQueue(); applyQueue(apiQueue) }

    /**
     * The queue saved at the last exit is on screen and nothing has played since: an empty queue
     * from Spotify (its session ended while the app was away, after a while) does not wipe it
     * (#240: "when the app is opened after a while the entire queue is reset").
     */
    @Volatile private var restoredQueuePending = false

    private fun applyQueue(q: io.github.thake.soundnest.api.Queue?) {
        apiQueue = q
        if (restoredQueuePending && q != null && q.current == null && q.queued.isEmpty() && q.nextUp.isEmpty()) return
        // While a local file or Audible audiobook plays, the queue shown is that player's.
        if (q == null || LocalPlayer.active.value || io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayer.active.value) return
        // A move is under way: its steps are not shown, the order dropped stays (see [queueMove]).
        if (queueMovesInFlight.get() > 0) return
        if (SystemClock.elapsedRealtime() < staleUntil && order(q) in staleOrders) return
        fun items(list: List<io.github.thake.soundnest.api.NowPlaying>) = list
            .filter { it.uri.startsWith("spotify:track:") || it.uri.startsWith("spotify:episode:") }
            .map { QueueItem(songOf(it), it.uri, it.uid.orEmpty(), "") }
        val raw = EngineQueue(
            current = q.current?.let(::songOf),
            queued = items(q.queued),
            nextUp = items(q.nextUp),
        )
        _queue.value = io.github.thake.soundnest.feature.parental.filter.PlaybackGuardrail.sanitizeQueue(
            raw,
            io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore.current(Graph.app),
            currentContextUri,
        )
        saveSpotifyQueue()
    }

    /** The page's queue from the song [id] on, for the saved session (see [saveLastTrack]). */
    private fun spotifyQueueOf(id: String): List<SongsModel> {
        val q = _queue.value
        if (LocalPlayer.active.value || io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayer.active.value || id.isBlank()) return emptyList()
        return io.github.thake.soundnest.core.playback.LastSession.queueFor(id, listOfNotNull(q.current) + (q.queued + q.nextUp).map { it.song })
    }

    /** The queue arrives after the song's state: it is saved with the song once it is there. */
    private fun saveSpotifyQueue() {
        val uri = _nowPlaying.value.uri
        val queue = spotifyQueueOf(trackIdOf(uri)).ifEmpty { return }
        val p = lastTrackPrefs()
        if (savedSource != io.github.thake.soundnest.core.playback.LastSession.SOURCE_SPOTIFY || p.getString("uri", "") != uri) return
        val encoded = io.github.thake.soundnest.core.playback.LastSession.encodeQueue(queue)
        if (p.getString("queue", "") != encoded) p.edit().putString("queue", encoded).apply()
    }

    /**
     * The car's queue: the current song as row 0, then the queued and next songs, capped at
     * [CAR_QUEUE_MAX]. A row's id is its place, which [Controls.skipToQueueItem] maps back through
     * [carQueue] (a uid is empty for some rows, and a local row's is an index that moves).
     */
    private fun publishCarQueue(q: EngineQueue, contextName: String) {
        val rows = (q.queued + q.nextUp).take(CAR_QUEUE_MAX)
        carQueue = rows
        fun entry(id: Long, s: SongsModel) = io.github.thake.soundnest.host.android.QueueEntry(id, s.title, s.singer, ArtworkProvider.uriFor(s.coverUri)?.toString())
        val entries = listOfNotNull(q.current?.let { entry(0L, it) }) + rows.mapIndexed { i, item -> entry(i + 1L, item.song) }
        PlaybackService.updateQueue(
            title = contextName.ifBlank { Graph.app.getString(io.github.thake.soundnest.R.string.next_up) },
            items = entries,
            activeId = if (q.current != null) 0L else null,
        )
    }

    // ── Listen history (in-app recap) ──
    private var listenKey = ""
    private var listenSong: SongsModel? = null
    private var listenSource = ListenHistory.Source.SPOTIFY
    private var listenPlayedMs = 0L
    private var listenTickAt = 0L
    private var listenWasPlaying = false

    /** Adds the time played since the last call; a new [key] closes the previous listen. */
    private fun trackListen(key: String, song: SongsModel?, source: ListenHistory.Source, isPlaying: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (listenWasPlaying && listenTickAt > 0) listenPlayedMs += now - listenTickAt
        if (key != listenKey) {
            listenSong?.let { s ->
                ListenHistory.record(ListenHistory.Listen(
                    System.currentTimeMillis(), listenKey, s.title, s.singer, s.album, s.coverUri, listenPlayedMs, listenSource,
                    ListenHistory.artistsOf(s.singer, s.artistIds),
                ))
            }
            listenKey = key; listenSong = song; listenSource = source; listenPlayedMs = 0
        }
        listenTickAt = now
        listenWasPlaying = isPlaying
    }

    // ── Commands ──

    /** Runs an API call off the caller, logging failures instead of crashing the UI. */
    private fun command(onFailure: (Throwable) -> Unit = {}, block: suspend () -> Unit) {
        scope.launch {
            runCatching { block() }.onFailure { Log.w(TAG, "command failed: ${it.message}"); onFailure(it) }
        }
    }

    private val transportLock = kotlinx.coroutines.sync.Mutex()
    private val transportGen = java.util.concurrent.atomic.AtomicInteger()

    /** Moved by [clearSession]: a transport started for an account that signed out is never sent. */
    private val sessionGen = java.util.concurrent.atomic.AtomicInteger()

    /**
     * Play, pause, skip: sent one at a time and only the newest one counts. Taps sent all
     * at once raced in the Web Player (a skip and a play crossing, a player stuck on one
     * song until restarted); one still waiting when a newer one comes is dropped.
     */
    /** [pullBack]: playback taken by another device comes back here first (not for a pause). */
    /**
     * [waitReady]: wait for the page when it is loading. Skips do not once a state was seen:
     * the player keeps them for after the reload (the song the taps point at plays then).
     */
    /**
     * [checkAfter]: when this phone was the one playing, the device check runs after the command
     * instead of before it, so the tap does not wait on a server round trip. A takeover it finds
     * then is pulled back as before; with another device known to be active, the check still
     * comes first, since the pull-back has to happen before the command.
     */
    /**
     * [done]: told once, on the main thread, whether the command was answered (true) or not
     * (failed, timed out, dropped for a newer one or for a page that never loaded).
     */
    private fun transport(
        pullBack: Boolean = true,
        waitReady: Boolean = true,
        checkAfter: Boolean = false,
        done: ((ok: Boolean) -> Unit)? = null,
        block: suspend (isLatest: () -> Boolean) -> Unit,
    ) {
        val gen = transportGen.incrementAndGet()
        val session = sessionGen.get()
        runningTransport?.let { job ->
            val held = SystemClock.elapsedRealtime() - runningSince
            if (job.isActive && held >= TRANSPORT_STUCK_MS) {
                Log.w(TAG, "transport stuck for $held ms: given up for the newer one")
                job.cancel()
            }
        }
        scope.launch {
            val self = coroutineContext[kotlinx.coroutines.Job]
            var told = false
            fun tell(ok: Boolean) {
                if (told) return
                told = true
                done?.invoke(ok)
            }
            var answered = false
            // Counted from inside: a coroutine cancelled before it starts never runs its finally.
            transportsInFlight.incrementAndGet()
            try {
                transportLock.withLock {
                    if (gen != transportGen.get()) return@withLock
                    runningTransport = self
                    runningSince = SystemClock.elapsedRealtime()
                    // A tap while the page loads (cold start, a reload): sent once it is there,
                    // instead of failing at once with "not ready".
                    if (waitReady && !api.ready.value && kotlinx.coroutines.withTimeoutOrNull(READY_WAIT_MS) { api.ready.first { it } } == null) {
                        Log.w(TAG, "transport dropped: the player page is not ready")
                        return@withLock
                    }
                    if (gen != transportGen.get()) return@withLock
                    val checkLater = pullBack && checkAfter && _activeDevice.value == null
                    if (pullBack && !checkLater && api.ready.value) takeBackPlayback()
                    // A device check may wait: the session may have ended meanwhile.
                    if (session != sessionGen.get()) return@withLock
                    // A cancellation of the caller is never swallowed (it must unwind); only the
                    // transport's own timeout and a real failure are logged here.
                    try {
                        kotlinx.coroutines.withTimeout(TRANSPORT_TIMEOUT_MS) { block { gen == transportGen.get() } }
                        answered = true
                        tell(true)
                    } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                        Log.w(TAG, "transport timed out after ${TRANSPORT_TIMEOUT_MS} ms")
                        scope.launch { runCatching { WebPlayerSelfHealer.healIfWedged(Graph.app, "transport_timeout") } }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "transport failed: ${e.message}")
                    }
                    if (checkLater && gen == transportGen.get() && api.ready.value) takeBackPlayback()
                }
            } finally {
                // A newer transport may hold the lock already: only this one's own mark is cleared.
                if (runningTransport === self) runningTransport = null
                tell(false)
                transportsInFlight.decrementAndGet()
                // An answered play or pause: the page gets a moment to say so before the screen
                // is put back on what it says (a slow network answers late, its state later).
                // One that failed is put right then too, whatever was wanted.
                if (answered) {
                    val until = SystemClock.elapsedRealtime() + COMMAND_SETTLE_MS
                    if (wantPlayingUntil != 0L) wantPlayingUntil = maxOf(wantPlayingUntil, until)
                    if (wantPausedUntil != 0L) wantPausedUntil = maxOf(wantPausedUntil, until)
                }
                reconcileAfter(commandSettled, COMMAND_SETTLE_MS)
            }
        }
    }

    /** Transports started and not over yet: [reconcile] leaves the screen to them meanwhile. */
    private val transportsInFlight = java.util.concurrent.atomic.AtomicInteger()

    /** The transport holding [transportLock], and since when (see [TRANSPORT_STUCK_MS]). */
    @Volatile private var runningTransport: kotlinx.coroutines.Job? = null
    @Volatile private var runningSince = 0L

    /**
     * Plays [itemUri], then checks that the page really moved to it; a play the page lost
     * (busy, reloading) is sent once more instead of leaving the old song playing.
     */
    private suspend fun playChecked(itemUri: String, isLatest: () -> Boolean, play: suspend () -> Unit) {
        play()
        repeat(2) { attempt ->
            val moved = kotlinx.coroutines.withTimeoutOrNull(PLAY_CHECK_MS) {
                api.player.state.first { it?.item?.uri == itemUri || !isLatest() }
            }
            if (moved != null || !isLatest()) {
                if (moved != null) WebPlayerSelfHealer.recordHealthy()
                return
            }
            if (attempt == 0) { Log.w(TAG, "play of $itemUri not taken: sent again"); play() }
        }
        if (!isLatest()) return
        val healed = runCatching { WebPlayerSelfHealer.healIfWedged(Graph.app, "play_not_taken") }.getOrDefault(false)
        if (healed && isLatest()) {
            // Wait for the reloaded Web Player page to become ready, then replay the child's tap once.
            kotlinx.coroutines.delay(1_000L)
            val readyAgain = kotlinx.coroutines.withTimeoutOrNull(READY_WAIT_MS) { api.ready.first { it } } != null
            if (readyAgain && isLatest()) {
                Log.i(TAG, "re-sending play of $itemUri after Web Player self-heal")
                play()
            }
        }
    }

    /** Remember the Spotify context the given play keys belong to. */
    fun registerContext(urls: List<String>, contextUri: String) {
        urls.forEach { contextRegistry[it] = contextUri }
        contextRows[contextUri] = urls
    }

    /**
     * Each list's play keys in its own order, as last registered: where a picked song sits in it
     * (see [rowIndex]). The app's rows map one to one onto the list's items, in Spotify's order.
     */
    private val contextRows = ConcurrentHashMap<String, List<String>>()

    /**
     * The place of [song] in the list [ctx], sent with its uri as the official player sends a row's
     * index with its uri. Reported (900+ songs, Liked Songs): a song picked far down a big list
     * started another song. The uri alone is looked up in the list's first pages only (lists of a
     * hundred songs played the song picked); the index says where it is. Only past that first
     * stretch, so the plays that worked go out exactly as before. A given [order] is played as one
     * page of those songs (see player-bridge.js), where the uri is always found: no index then.
     */
    private fun rowIndex(song: String, ctx: String, order: List<String>?): Int? {
        if (order != null) return null
        val at = contextRows[ctx]?.indexOf(song) ?: return null
        return at.takeIf { it >= INDEXED_ROW_FROM }
    }

    // Names of the lists playback started from, as the app's own pages name them. Spotify's
    // state carries a context label, but it comes back blank for some lists, and the player
    // then showed the album name instead of the playlist's (B26). A context the page does not
    // name is shown with the name the app played it from.
    private val contextNames = ConcurrentHashMap<String, String>()

    /** [name] of the list at [contextUri], for the player header and the queue sheet. */
    fun registerContextName(contextUri: String, name: String) {
        if (contextUri.isNotBlank() && name.isNotBlank()) contextNames[contextUri] = name
    }

    /** The app's name for [contextUri], "" when the app never played from it (B26). */
    fun knownContextName(contextUri: String): String = contextNames[contextUri].orEmpty()

    /** Liked Songs plays as its own context: Spotify maps it to the account's liked list. */
    fun registerLikedSongs(urls: List<String>) = registerContext(urls, LIKED_CONTEXT)

    /** [holdMs]: how long states of another song are ignored, at most, while this one comes. */
    private fun markPending(
        trackId: String,
        itemUri: String? = trackId.takeIf { it.isNotBlank() }?.let { SPOTIFY_TRACK_PREFIX + it },
        holdMs: Long = PENDING_MS,
    ) {
        pendingSkip = 0
        pendingItemUri = itemUri
        bufferingState.value = true
        itemUri?.let { boundState?.likeState?.value = library[it] ?: false }
        intendPlaying()
        pendingTrackId = trackId
        pendingUntil = SystemClock.elapsedRealtime() + holdMs
        reconcileAfter(pendingEnded, holdMs)
        playing = true
        positionMs = 0
        positionAt = SystemClock.elapsedRealtime()
    }

    /**
     * Play [song] (a play key "spotify:track:<id>|<text>" or "episode:<id>"). [contextUri] is
     * the list it was picked from; when null, the last list that registered this track is used.
     * [order]: the context's track uris in the order to play them (a sorted playlist).
     * [alone]: no list at all, not even the one that registered the track (a search hit), so
     * Spotify goes on with its own autoplay of related songs instead of, say, Liked Songs.
     */
    fun playSong(
        song: String, @Suppress("UNUSED_PARAMETER") context: Context? = null, contextUri: String? = null,
        order: List<String>? = null, positionMs: Long? = null,
        songs: List<SongsModel>? = null, alone: Boolean = false,
    ) {
        if (io.github.thake.soundnest.feature.spokenword.audible.data.AudibleUris.isAudibleUri(song)) {
            val asin = io.github.thake.soundnest.feature.spokenword.audible.data.AudibleUris.asinFromAny(song) ?: return
            val chapterIdx = io.github.thake.soundnest.feature.spokenword.audible.data.AudibleUris.chapterIndexFromUri(song)
            io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayer.playBook(
                asin = asin,
                chapterIndex = chapterIdx,
                positionMs = positionMs,
            )
            return
        }
        val policy = io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore.current(Graph.app)
        val episodeId = song.removePrefix("episode:").takeIf { song.startsWith("episode:") }
        if (episodeId != null && !policy.spokenWord.allowPodcasts && !policy.spokenWord.allowAudiobooks) {
            io.github.thake.soundnest.host.android.Logbook.log("playSong refused: podcasts/audiobooks disabled by parental policy")
            return
        }
        val trackId = episodeId ?: spotifyTrackIdForPlayback(song) ?: return
        if (trackId in policy.blockedTrackIds) {
            io.github.thake.soundnest.host.android.Logbook.log("playSong refused: $trackId blocked by parental policy")
            return
        }
        val candidateModel = songs?.firstOrNull { it.url == song || (trackId.isNotBlank() && it.spotifyTrackId == trackId) }
        if (candidateModel != null && !io.github.thake.soundnest.feature.parental.filter.ParentalFilterEngine.isTrackAllowed(candidateModel, policy, contextUri)) {
            io.github.thake.soundnest.host.android.Logbook.log("playSong refused: ${candidateModel.title} disallowed by parental policy")
            return
        }
        // Data saver on mobile data: Spotify can't stream, only Local Files play.
        if (io.github.thake.soundnest.feature.system.settings.DataSaver.active) {
            main.post {
                android.widget.Toast.makeText(Graph.app, io.github.thake.soundnest.R.string.data_saver_blocked_soundnest, android.widget.Toast.LENGTH_SHORT).show()
            }
            return
        }
        LocalPlayer.stop()
        io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayer.stop()
        cancelBurst()
        val itemUri = if (episodeId != null) "spotify:episode:$episodeId" else SPOTIFY_TRACK_PREFIX + trackId
        val ctx = if (alone) null else contextUri ?: contextRegistry[song]
        // What a smart shuffle ending brings back: the order of the last play that was not one.
        if (ctx != null) {
            val sent = order?.takeIf { ctx == contextUri }
            playedOrder = ctx to sent
            if (io.github.thake.soundnest.feature.library.ui.SmartShuffle.on.value != ctx) { plainOrder = ctx to sent; smartPlayed = null }
            else if (sent != null) smartPlayed = ctx
        }
        playedByHand = itemUri
        markPending(if (episodeId != null) "" else trackId, itemUri)
        transport { isLatest ->
            playChecked(itemUri, isLatest) {
                Log.d(TAG, "playing $itemUri in ${ctx ?: "no context"}: ${queueTag()}")
                if (ctx != null) {
                    val sent = order?.takeIf { ctx == contextUri }
                    api.player.play(ctx, trackUri = itemUri, index = rowIndex(song, ctx, sent), positionMs = positionMs, order = sent)
                }
                else api.player.play(itemUri)
            }
        }
    }

    val routing: Boolean get() = false

    private const val POSITION_SAVE_MS = 10_000L
    /** How often the position save looks for a song playing while none does. */
    private const val POSITION_IDLE_MS = 30_000L

    /** [by]: who asked, for the diagnostic (the app's screens, the media session...). */
    fun play(by: String = "user") {
        io.github.thake.soundnest.host.android.Logbook.log("play asked by $by")
        if (by != BY_CALL) callPause.userActed()
        if (io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayer.active.value) { Haptics.click(); io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayer.resume(); return }
        if (LocalPlayer.active.value) { Haptics.click(); LocalPlayer.resume(); return }
        // Smart shuffle was turned off while paused: the list's order comes back as it plays on.
        plainOrderDue?.let { if (restorePlainOrder(it)) return }
        Haptics.click(); intendPlaying(); playing = true; positionAt = SystemClock.elapsedRealtime()
        // Loading until the page plays: the clock ran from the tap before, over silence when the
        // resume came late or never (reported on a slow network).
        if (api.player.state.value?.isPaused != false) bufferingState.value = true
        // Tapped on the restored mini player while the Web Player is still loading.
        if (!stateSeen) resumeWhenReady = true
        val uri = _nowPlaying.value.uri
        transport(checkAfter = true) { api.player.resume() }
        jamFollowUp(wantPaused = false)
        // An episode ignored by a plain resume (the page's element stays paused, which is what
        // "the podcast stopped and the play button does nothing" was — B11) is asked for again
        // by its uri, where it was. Music keeps the plain resume: it never showed that.
        if (!uri.startsWith("spotify:episode:")) return
        scope.launch {
            kotlinx.coroutines.delay(EPISODE_RESUME_CHECK_MS)
            if (!playing || LocalPlayer.active.value || io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayer.active.value) return@launch
            val s = api.player.state.value
            if (s?.item?.uri == uri && s.isPaused) {
                Log.i(TAG, "episode resume not taken: playing $uri again (${queueTag()})")
                // In its show: played alone, Spotify goes on with its own autoplay after the episode.
                val ctx = _nowPlaying.value.contextUri.ifBlank { uri }
                runCatching { api.player.play(ctx, trackUri = uri.takeIf { ctx != it }, positionMs = getCurrentPosition()) }
            }
        }
    }

    private const val BY_CALL = "phone call"
    private val callPause = CallPause()

    /** The phone rings or a call ends, on the main thread. ExoPlayer deals with its own audio focus. */
    private fun phoneCall(busy: Boolean) {
        if (LocalPlayer.holdsFocus || io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayer.holdsFocus || SoundNestRuntime.spotify.player.playsElsewhere.value) return
        when (callPause.modeChanged(busy, isPlaying(), SystemClock.elapsedRealtime())) {
            CallPause.Action.PAUSE -> pause(by = BY_CALL)
            CallPause.Action.RESUME -> play(by = BY_CALL)
            CallPause.Action.NONE -> Unit
        }
    }

    fun pause(by: String = "user") {
        if (by != BY_CALL) callPause.userActed()
        io.github.thake.soundnest.host.android.Logbook.log("pause asked by $by")
        Haptics.click(); cancelBurst(); pauseSilently()
        io.github.thake.soundnest.host.android.PlaybackService.pausedOnPurpose()
    }

    /** [send] false: the engine has paused already (the sleep timer's fade). */
    internal fun pauseForSleep(send: Boolean = true) = pauseSilently(send)

    private fun pauseSilently(send: Boolean = true) {
        if (io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayer.active.value) { io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayer.pause(); return }
        if (LocalPlayer.active.value) { LocalPlayer.pause(); return }
        intendPaused()
        positionMs = getCurrentPosition(); positionAt = SystemClock.elapsedRealtime(); playing = false
        if (send) { transport(pullBack = false) { api.player.pause() }; jamFollowUp(wantPaused = true) }
    }

    /**
     * In a Jam this phone controls (the music on the host's device): a play or pause the host has
     * not followed [JAM_FOLLOW_MS] later is sent once more. Reported: "starting the sound from the
     * phone does not work": the first play after joining was answered 202 by Spotify and never
     * reached the host; the next one did.
     */
    private fun jamFollowUp(wantPaused: Boolean) {
        val jam = api.jam.session.value ?: return
        if (jam.isHost || jam.listensHere) return
        scope.launch {
            kotlinx.coroutines.delay(JAM_FOLLOW_MS)
            if (playing == wantPaused) return@launch // asked otherwise since
            kotlinx.coroutines.withTimeoutOrNull(RECONCILE_READ_MS) { runCatching { SoundNestRuntime.spotify.player.refresh() } }
            val s = api.player.state.value ?: return@launch
            if (s.isPaused == wantPaused || playing == wantPaused) return@launch
            io.github.thake.soundnest.host.android.Logbook.log("jam: the host did not ${if (wantPaused) "pause" else "play"} in ${JAM_FOLLOW_MS / 1000} s, sent again")
            transport(pullBack = false) { if (wantPaused) api.player.pause() else api.player.resume() }
        }
    }
    private val JAM_FOLLOW_MS = 3_000L

    fun stop() = pause()

    /**
     * Next, from any control: the player, the mini player, the notification, headphones, the car,
     * the widget. The song it lands on is shown at once ([showSkipTarget]) and stays shown until the
     * page plays it or the skip fails. Only the in-app buttons showed it before, for [PENDING_MS] at
     * most: on a slow network the skip took longer and the screen went back to the old song
     * (reported: "Next does not move").
     */
    fun next(by: String = "user") {
        Log.d(TAG, "next tapped")
        io.github.thake.soundnest.host.android.Logbook.log("next asked by $by")
        Haptics.click()
        if (io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayer.active.value) { io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayer.next(); return }
        if (LocalPlayer.active.value) { LocalPlayer.next(); return }
        cancelRepeatOnce("next")
        // Spotify is refusing licences: every skip asks for one more and makes the limit last
        // (a tester's log: 183 refusals in 8 min of fast skipping, the page then skipping on its
        // own through songs it could not play). Nothing is sent until the limit is over.
        if (SoundNestRuntime.host.licenseLimited()) {
            android.widget.Toast.makeText(Graph.app, io.github.thake.soundnest.R.string.spotify_limited, android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        intendPlaying()
        tapSeq++
        // The first tap skips at once. Taps that follow it within SKIP_BURST_MS, or within
        // MIN_SKIP_GAP_MS of the last skip sent, become one jump to the song they add up to: a
        // burst of "next" commands left the Web Player out of step (state conflicts, then a
        // player stuck until reloaded), and each song skipped through asks for a licence.
        val now = SystemClock.elapsedRealtime()
        val sinceSent = now - skipSentAt
        if (now - burstAt > SKIP_BURST_MS && sinceSent >= MIN_SKIP_GAP_MS) {
            burstAt = now
            burstUpcoming = apiQueue?.let { it.queued + it.nextUp }.orEmpty()
            burstTaps = 1
            pendingSkips = 0
            skipSentAt = now
            // The next song starts at 0: a previous tapped before Spotify reports must not read
            // the old song's position and restart instead of going back.
            positionMs = 0L
            positionAt = now
            val shown = showSkipTarget()
            // Reported: one skip went two songs on. A song that ends while the skip waits its turn
            // (the device check, a command still running, a slow page) is left by the page on its
            // own, and the skip then left the next song too. The skip is for the song it was
            // tapped on: gone already, it is not sent.
            val tappedOn = api.player.state.value?.item?.uri
            // The device check after the skip, as for a resume: before it, a slow network held
            // every skip for the device list's round trip (up to DEVICES_WAIT_MS).
            transport(waitReady = !stateSeen, checkAfter = true, pullBack = false, done = { ok -> skipDone(shown, ok) }) {
                val now = api.player.state.value?.item?.uri
                if (tappedOn != null && now != null && now != tappedOn) {
                    io.github.thake.soundnest.host.android.Logbook.log("next not sent: the song it was tapped on had already ended")
                    return@transport
                }
                api.player.next()
            }
            return
        }
        burstAt = now
        // A burst whose first tap was held (it came too soon after the last skip sent, e.g. a
        // previous): its songs are counted from the one playing now, none of its taps sent yet.
        if (burstTaps == 0 || burstUpcoming.isEmpty()) {
            burstUpcoming = apiQueue?.let { it.queued + it.nextUp }.orEmpty()
            burstTaps = 0
        }
        burstTaps++
        pendingSkips++
        showSkipTarget()
        main.removeCallbacks(sendSkips)
        main.postDelayed(sendSkips, maxOf(SKIP_BURST_MS, MIN_SKIP_GAP_MS - sinceSent))
    }

    /** Hands out the tokens of [showSkipTarget]; 0 is never one. */
    private var skipShownSeq = 0
    /** The skip whose song is pending on screen; 0 when the pending song is not a skip's (a pick). */
    private var pendingSkip = 0

    /**
     * Shows the song the taps so far land on, before the page plays it: the song after the one
     * playing at the first tap, or [burstTaps] songs after it. -> the token [skipDone] is told
     * with, 0 when the target is unknown (the page's own states then show where it lands).
     */
    private fun showSkipTarget(): Int {
        val token = ++skipShownSeq
        // Reported (beta.9): each fast tap showed `nowPlaying.next` — the same second song every
        // time, held as pending, so the screen stayed on it for seconds while the page went on to
        // the fifth. A burst shows the song its taps add up to.
        val n = if (burstTaps <= 1) _nowPlaying.value.next
            else burstUpcoming.getOrNull(burstTaps - 1)?.let(::songOf)
        if (n == null) {
            pendingTrackId = null
            pendingItemUri = null
            pendingSkip = 0
            return 0
        }
        markPending(n.spotifyTrackId, holdMs = SKIP_PENDING_MAX_MS)
        pendingSkip = token
        boundState?.updateSongState(n.coverUri, n.title, n.singer, true, n.id, 0, n.album)
        durationMs = n.durationMs.toLong()
        pushNotification(n, false)
        return token
    }

    /**
     * The skip shown as [token] was answered ([ok]): the page now has [SKIP_ANSWERED_MS] to report the
     * song. Not answered (failed, timed out, dropped): the screen goes back at once to what the
     * page really plays, instead of showing a song nothing is going to play. A newer skip or a
     * pick shown since owns the screen, and is left alone.
     */
    private fun skipDone(token: Int, ok: Boolean) {
        if (token == 0 || token != pendingSkip || pendingTrackId == null) return
        if (ok) {
            val now = SystemClock.elapsedRealtime()
            pendingUntil = minOf(pendingUntil, now + SKIP_ANSWERED_MS)
            reconcileAfter(pendingEnded, pendingUntil - now)
            return
        }
        Log.w(TAG, "skip not taken by the page: the screen shows what it plays")
        pendingSkip = 0
        pendingTrackId = null
        pendingItemUri = null
        applyState(api.player.state.value)
    }

    /** When the last skip command left (see [next]). */
    private var skipSentAt = 0L

    /** When the last "next" tap came, the queue as it was at the first one, and the taps since. */
    private var burstAt = 0L
    private var burstUpcoming: List<io.github.thake.soundnest.api.NowPlaying> = emptyList()
    private var burstTaps = 0
    private var pendingSkips = 0
    private val sendSkips = Runnable {
        if (pendingSkips <= 0) return@Runnable
        pendingSkips = 0
        skipSentAt = SystemClock.elapsedRealtime()
        // The song [burstTaps] after the one playing at the first tap.
        val target = burstUpcoming.getOrNull(burstTaps - 1)
        io.github.thake.soundnest.host.android.Logbook.log("next taps merged: $burstTaps skips${if (target != null) " in one jump to ${target.name}" else ", sent as a plain next"}")
        val shown = pendingSkip
        transport(waitReady = !stateSeen, checkAfter = true, pullBack = false, done = { ok -> skipDone(shown, ok) }) {
            if (target == null) api.player.next() else api.player.skipTo(target)
        }
        if (target != null) {
            burstTarget = target
            burstContext = _nowPlaying.value.contextUri
            burstSeq++
            verifyTap = tapSeq
            main.removeCallbacks(verifyBurst)
            main.postDelayed(verifyBurst, BURST_VERIFY_MS)
        }
    }

    /*
     * Measured on the test phone: the burst's first "next" is acknowledged by the page, applied
     * a second late, after the jump to the target — the page then lands one song past it (the
     * screen showed the target, the speaker the song after). Where the burst landed is checked,
     * and the target played by name when it is not there.
     */
    private var burstTarget: io.github.thake.soundnest.api.NowPlaying? = null
    private var burstContext = ""
    private var burstSeq = 0
    private var lastPageUri = ""

    /** Bumped by every user transport action (tap or pick); [verifyBurst] only runs if it did not move since it was scheduled. */
    private var tapSeq = 0
    private var verifyTap = 0
    private const val BURST_VERIFY_MS = 2_500L
    private val verifyBurst = Runnable {
        val t = burstTarget ?: return@Runnable
        burstTarget = null
        val seq = burstSeq
        val tap = verifyTap
        // A newer tap, pick or pause: the user moved on (a reported log: a previous pressed
        // 1 s after the burst was undone by this check, which played the burst's song again).
        if (tap != tapSeq || SystemClock.elapsedRealtime() - burstAt < BURST_VERIFY_MS || !playing) return@Runnable
        if (lastPageUri == t.uri || burstContext.isBlank()) return@Runnable
        Log.d(TAG, "skips landed on $lastPageUri, not ${t.name}: playing it (${queueTag()})")
        io.github.thake.soundnest.host.android.Logbook.log("skips landed on another song than ${t.name}: it is played by name")
        // In the order it was played in (a sorted list, a smart shuffle), and a song the user
        // queued is not in the list: asking for it there would restart the list.
        if (t.provider == "queue") return@Runnable
        val order = playedOrder?.takeIf { it.first == burstContext }?.second
        transport { if (seq == burstSeq && tap == tapSeq) api.player.play(burstContext, t.uri, order = order) }
    }

    /**
     * The user does something else than "next" (a pick, a pause, a seek, a previous): whatever
     * the burst still had to send or check is dropped, or it would undo that action (reported:
     * a previous after two nexts was overridden by the burst's jump, then by its check).
     */
    private fun cancelBurst() {
        tapSeq++
        pendingSkips = 0
        burstTarget = null
        burstTaps = 0
        burstAt = 0L
        burstUpcoming = emptyList()
        main.removeCallbacks(sendSkips)
        main.removeCallbacks(verifyBurst)
    }

    /** "shuffle=…, next=[…]": the page's shuffle flag and the first upcoming songs, for the play logs. */
    private fun queueTag(): String {
        val up = apiQueue?.let { it.queued + it.nextUp }.orEmpty().take(5).joinToString { it.name.take(30) }
        return "shuffle=${api.player.state.value?.shuffle} next=[$up]"
    }

    fun previous(by: String = "user") {
        io.github.thake.soundnest.host.android.Logbook.log("previous asked by $by")
        Haptics.click()
        if (io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayer.active.value) { io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayer.previous(); return }
        if (LocalPlayer.active.value) { LocalPlayer.previous(); return }
        cancelRepeatOnce("previous")
        intendPlaying()
        // A previous counts as -1 in the burst. Skips still waiting to be sent (nexts tapped
        // within SKIP_BURST_MS) just lose one: next, next, previous is one next, and nothing
        // is sent for the previous. Once the burst has left (or there is none), previous is a
        // plain one, and the burst is forgotten so it cannot send or verify anything later.
        if (pendingSkips > 0) {
            pendingSkips--
            burstTaps--
            tapSeq++
            burstAt = SystemClock.elapsedRealtime()
            if (pendingSkips == 0) main.removeCallbacks(sendSkips)
            return
        }
        cancelBurst()
        skipSentAt = SystemClock.elapsedRealtime()
        // Either way the song now starts at 0 (a restart or the one before): without this the old
        // position stays until Spotify reports, and a quick second previous would restart again.
        positionMs = 0L
        positionAt = SystemClock.elapsedRealtime()
        transport(waitReady = !stateSeen, checkAfter = true, pullBack = false) { api.player.previous() }
    }

    /** Tracks shufflePlay plays without a context: the first one, plus up to 49 queued. */
    private const val SHUFFLE_QUEUE_MAX = 50
    /** After pulling playback back, the page needs a moment before it takes commands. */
    private const val PULL_BACK_SETTLE_MS = 600L
    private const val PULL_BACK_WAIT_MS = 6_000L
    private const val DEVICES_WAIT_MS = 2_000L
    private const val DISCOVERY_WAIT_MS = 800L

    /**
     * How long the device list read by [takeBackPlayback] is trusted. Long enough that a burst of
     * taps pays for it once, short enough that a device taking playback in between is noticed.
     */
    private const val DEVICES_FRESH_MS = 10_000L
    /** A transport command waits that long for the page to load before it is dropped. */
    private const val READY_WAIT_MS = 30_000L

    /** Shuffle-play a list: Spotify picks the first track of its own shuffled context. */
    fun shufflePlay(songs: List<SongsModel>) {
        if (songs.isEmpty()) return
        LocalPlayer.stop()
        io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayer.stop()
        val ctx = contextRegistry[songs.first().url]
        cancelBurst()
        intendPlaying()
        transport {
            api.player.setShuffle(true)
            Log.d(TAG, "shuffle-playing ${ctx ?: "a shuffled list"}: ${queueTag()}")
            if (ctx == null) {
                // No registered context: play the list in a shuffled order (first
                // track now, the rest queued), not one random song alone. Capped at
                // SHUFFLE_QUEUE_MAX tracks: the queue is the user's Spotify queue,
                // which outlives this list and follows them to every device, so
                // queuing all of Liked Songs would bury whatever they play next.
                val order = songs.shuffled().take(SHUFFLE_QUEUE_MAX)
                api.player.play(order.first().url)
                if (order.size > 1) api.player.addToQueue(order.drop(1).map { it.url })
            } else api.player.play(ctx)
            // A play command may reset the options: make sure shuffle sticks.
            kotlinx.coroutines.delay(1500)
            api.player.setShuffle(true)
        }
    }

    /** The list and the order of the last play that was not a smart shuffle's (null: the list's own). */
    @Volatile private var plainOrder: Pair<String, List<String>?>? = null
    /** The list a smart shuffle is playing as its own mixed order. */
    @Volatile private var smartPlayed: String? = null
    /** Smart shuffle ended on this list while paused: its order comes back at the next play. */
    @Volatile private var plainOrderDue: String? = null

    /** The list and order of the last play, kept for the replays that name a song only. */
    @Volatile private var playedOrder: Pair<String, List<String>?>? = null

    private fun endSmartShuffle(ctx: String) {
        if (smartPlayed != ctx) return
        smartPlayed = null
        if (_nowPlaying.value.contextUri != ctx) return
        if (playing) restorePlainOrder(ctx) else plainOrderDue = ctx
    }

    /** Plays the song now playing again in [ctx] as it was before the smart shuffle, where it is. */
    private fun restorePlainOrder(ctx: String): Boolean {
        plainOrderDue = null
        val uri = _nowPlaying.value.uri
        if (_nowPlaying.value.contextUri != ctx || uri.isBlank() || LocalPlayer.active.value) return false
        val song = if (uri.startsWith("spotify:episode:")) "episode:" + uri.substringAfterLast(':') else uri
        Log.d(TAG, "smart shuffle off: $ctx back in the list's order from ${_nowPlaying.value.uri}")
        playSong(song, contextUri = ctx, order = plainOrder?.takeIf { it.first == ctx }?.second, positionMs = getCurrentPosition())
        return true
    }

    /** Shuffle as the player shows it (Spotify's, or the local queue's). */
    fun isShuffleOn(): Boolean = if (LocalPlayer.active.value) LocalPlayer.shuffled else boundState?.shuffle?.value == true

    fun setShuffle(on: Boolean) {
        Haptics.toggle(on)
        if (LocalPlayer.active.value) {
            LocalPlayer.setShuffle(on)
            // Local playback has no page state to bring the news to the media session.
            pushNotification(null, boundState?.likeState?.value ?: false)
            return
        }
        shuffleWanted = on
        shuffleWantedUntil = SystemClock.elapsedRealtime() + SHUFFLE_HOLD_MS
        command { api.player.setShuffle(on) }
    }

    // Reported: shuffle out of step between the list and the player. A state the page sent
    // before it took the tap flipped the button back, and the two screens then disagreed; the
    // value asked for holds until the page reports it (or for SHUFFLE_HOLD_MS).
    @Volatile private var shuffleWanted = false
    /** The repeat mode last tapped, held against the page's older reports like [shuffleWanted]. */
    @Volatile private var repeatWanted = 0
    @Volatile private var repeatWantedUntil = 0L
    @Volatile private var shuffleWantedUntil = 0L
    private const val SHUFFLE_HOLD_MS = 2_500L

    /** 0 = off, 1 = context, 2 = track for good, 3 = track once (see [LocalRepeat]). */
    private val _repeatMode = mutableStateOf(0)
    val repeatMode: State<Int> get() = _repeatMode

    fun setRepeat(on: Boolean) = setRepeatMode(if (on) 1 else 0)

    fun setRepeatMode(mode: Int) {
        if (LocalPlayer.active.value) { _repeatMode.value = mode; boundState?.repeat?.value = mode != 0; LocalPlayer.setRepeatMode(mode); lastTrackPrefs().edit().putInt("repeat", mode.coerceIn(0, 3)).apply(); return }
        Haptics.toggle(mode != 0)
        _repeatMode.value = mode
        repeatWanted = LocalRepeat.pageMode(mode)
        repeatWantedUntil = SystemClock.elapsedRealtime() + SHUFFLE_HOLD_MS
        boundState?.repeat?.value = mode != 0
        lastTrackPrefs().edit().putInt("repeat", mode.coerceIn(0, 3)).apply()
        command { api.player.setRepeat(Repeat.entries[LocalRepeat.pageMode(mode)]) }
    }

    /**
     * Reported: the repeat button forgets what it was. The mode is the user's choice, kept from
     * one run of the app to the next (the page always starts with repeat off): shown at once and
     * set again on the first song the page reports.
     */
    private fun restoreRepeat(state: CurrentSongState) {
        val p = lastTrackPrefs()
        if (!p.contains("repeat")) return
        val mode = p.getInt("repeat", 0).coerceIn(0, 3)
        _repeatMode.value = mode
        state.repeat.value = mode != 0
        SoundNestRuntime.spotify.player.restoreRepeat(Repeat.entries[LocalRepeat.pageMode(mode)])
    }

    /** Off, the queue, the song for good, the song once, off. */
    fun cycleRepeat() = setRepeatMode((_repeatMode.value + 1) % 4)

    /** A manual move on a Spotify song cancels repeat once: it was for the song left. */
    private fun cancelRepeatOnce(why: String) {
        if (_repeatMode.value != LocalRepeat.ONCE) return
        io.github.thake.soundnest.host.android.Logbook.log("repeat once: cancelled ($why), back to off")
        setRepeatMode(LocalRepeat.OFF)
    }

    /** Podcast / Audible audiobook playback speed (Spotify refuses it on music). */
    fun setSpeed(speed: Double) {
        _speed.value = speed
        if (io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayer.active.value) {
            io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayer.setSpeed(speed)
            return
        }
        command { api.player.setSpeed(speed) }
    }

    private val _speed = mutableStateOf(1.0)
    /** The speed Spotify reports for what plays (podcasts only); 1.0 for music. */
    val speed: State<Double> get() = _speed

    /** Lands within the episode when skipping: Spotify refuses a position past the end; Audible crosses chapters smoothly. */
    fun skipBy(deltaMs: Long) {
        if (io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayer.active.value) {
            io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayer.skipBy(deltaMs)
            return
        }
        val duration = getDuration()
        val target = (getCurrentPosition() + deltaMs).coerceAtLeast(0L)
        seekTo(if (duration > 0) target.coerceAtMost(duration - 1_000L) else target)
    }

    /** "Add to queue" for a podcast episode (its uri, not a track id). */
    fun addEpisodeToQueue(url: String) {
        val id = url.removePrefix("episode:").takeIf { url.startsWith("episode:") } ?: return
        Haptics.confirm()
        command { api.player.addToQueue(listOf("spotify:episode:$id")) }
    }

    private val audiobookContextUris = ConcurrentHashMap.newKeySet<String>()

    /** Records [contextUri] as an audiobook context so [isAudiobook] routes its player UI cleanly. */
    fun markAudiobookContext(contextUri: String) {
        if (contextUri.isNotBlank()) audiobookContextUris.add(contextUri)
    }

    internal fun clearAudiobookContextsForTests() {
        audiobookContextUris.clear()
    }

    /**
     * What plays is a podcast episode or Audible audiobook chapter: the notification keeps spoken-word
     * controls (±15s skips), while [isAudiobook] routes the in-app player sheet to the dedicated
     * audiobook player.
     */
    val isEpisode: Boolean
        get() = _nowPlaying.value.uri.startsWith("spotify:episode:") ||
            _nowPlaying.value.uri.startsWith("audible:chapter:")

    /** True when the playing item is an Audible or Spotify audiobook chapter. */
    val isAudiobook: Boolean
        get() {
            val np = _nowPlaying.value
            return io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayer.active.value ||
                np.uri.startsWith("audible:") ||
                np.contextUri.startsWith("audible:") ||
                np.uri.startsWith("spotify:chapter:") ||
                np.contextUri.startsWith("spotify:audiobook:") ||
                (np.contextUri.isNotBlank() && np.contextUri in audiobookContextUris)
        }

    fun lyricsTrack(s: SongsModel) = io.github.thake.soundnest.feature.lyrics.data.LyricsApi.Track(
        trackId = s.spotifyTrackId, title = s.title, artist = s.singer, album = s.album,
        durationMs = s.durationMs.toLong().takeIf { it > 0 } ?: durationMs, coverUrl = s.coverUri,
    )

    /** The lyrics request for what is playing now. */
    fun currentLyricsTrack(): io.github.thake.soundnest.feature.lyrics.data.LyricsApi.Track? {
        if (io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayer.active.value) return null
        val st = boundState ?: return null
        val title = st.title.value
        if (title.isBlank()) return null
        return io.github.thake.soundnest.feature.lyrics.data.LyricsApi.Track(
            trackId = currentTrackId, title = title, artist = st.singer.value, album = st.album.value,
            durationMs = durationMs, coverUrl = st.coverUri.value,
        )
    }

    /** Save / remove the current track in Liked Songs. */
    fun setLiked(on: Boolean) {
        if (io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayer.active.value) return
        // An unmatched device file is not in the Spotify library.
        if (LocalPlayer.active.value && localSpotifyUri(LocalPlayer.current()) == null) {
            io.github.thake.soundnest.host.android.Logbook.log("library: like not sent, the local song has no Spotify id (${LocalPlayer.current()?.uri?.scheme})")
            return
        }
        Haptics.toggle(on)
        val uri = currentLikeUri().ifBlank { if (currentTrackId.isNotBlank()) SPOTIFY_TRACK_PREFIX + currentTrackId else "" }
        if (uri.isBlank()) { boundState?.likeState?.value = on; io.github.thake.soundnest.host.android.Logbook.log("library: like not sent, no song id"); return }
        setSaved(uri, on)
    }

    // ── Library state (Liked Songs, saved albums / playlists, followed artists) ──
    /** What Spotify's library says about each uri seen so far; every screen reads this. */
    val library: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Boolean> =
        androidx.compose.runtime.mutableStateMapOf()

    /** Confirmed by Spotify less than that long ago: not asked for again (see [libraryContains]). */
    private const val SAVED_FRESH_MS = 60_000L

    /** A check of the song shown that got no answer is made once more after this long. */
    private const val LIKE_RETRY_MS = 2_500L

    /** After a check that failed twice, page states do not start another before this long. */
    private const val LIKE_RETRY_AFTER_MS = 30_000L

    /** When Spotify (or the user's own tap) last settled each uri. */
    private val savedAt = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /** One request in flight per uri list, so two callers asking share it. */
    private val savedFlights =
        java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Deferred<List<Boolean>?>>()
    private val savedFlightLock = kotlinx.coroutines.sync.Mutex()
    /** Why the last request for saved states got no answer (logged by [refreshCurrentLiked]). */
    @Volatile private var savedError = ""

    // The user's last like/unlike wins briefly over states that were already in flight.
    @Volatile private var intentUri = ""
    @Volatile private var intentValue = false
    @Volatile private var intentUntil = 0L

    private fun likedFor(uri: String, reported: Boolean): Boolean =
        if (uri == intentUri && SystemClock.elapsedRealtime() < intentUntil) intentValue else reported

    private fun onLibraryItem(uri: String, saved: Boolean) {
        if (uri == intentUri && SystemClock.elapsedRealtime() < intentUntil && saved != intentValue) return
        library[uri] = saved
        savedAt[uri] = SystemClock.elapsedRealtime()
        if (uri == currentLikeUri()) {
            boundState?.likeState?.value = saved
            pushNotification(null, saved)
        }
        if (!saved && uri.startsWith(SPOTIFY_TRACK_PREFIX)) {
            val id = trackIdOf(uri)
            io.github.thake.soundnest.core.catalog.Api.LikedCache.tracks =
                io.github.thake.soundnest.core.catalog.Api.LikedCache.tracks?.filterNot { it.spotifyTrackId == id }
        }
    }

    // Rows ask for their saved state one by one; ask Spotify in one batch per frame.
    private val wantedSaved = java.util.Collections.synchronizedSet(LinkedHashSet<String>())
    private val flushSaved = Runnable {
        val uris = synchronized(wantedSaved) { wantedSaved.toList().also { wantedSaved.clear() } }
        if (uris.isNotEmpty()) scope.launch { uris.chunked(50).forEach { libraryContains(it) } }
    }

    /** Make sure [uri]'s saved state ends up in [library]. */
    fun requestSaved(uri: String) {
        // An entry older than [SAVED_FRESH_MS] is asked again (it may have changed in another app); [libraryContains] skips the fresh ones.
        if (uri.isBlank() || (library.containsKey(uri) && SystemClock.elapsedRealtime() - (savedAt[uri] ?: 0L) < SAVED_FRESH_MS)) return
        if (wantedSaved.add(uri)) { main.removeCallbacks(flushSaved); main.postDelayed(flushSaved, 32) }
    }

    /** The in-app swipe and button: [next], which shows the song it lands on at once. */
    fun swipeNext() = next()

    /**
     * A seek the page has not shown yet: the song, the position asked for, when it was asked and
     * until when it holds. States of that song from before the seek (the old position) do not move
     * the clock back meanwhile. Reported: the bar dragged to a new place jumped back, the song
     * playing on from where it was — a state still on the old position, taken over the seek.
     */
    @Volatile private var seekUri = ""
    @Volatile private var seekTarget = 0L
    @Volatile private var seekAt = 0L
    @Volatile private var seekUntil = 0L
    private var seekJob: kotlinx.coroutines.Job? = null
    private var seekSeq = 0
    private val seekEnded = Runnable { reconcile() }

    /** [s] is the song a seek was asked on, somewhere it can have got to since. */
    private fun seekLanded(s: PlayerState): Boolean {
        if (s.item?.uri != seekUri) return false
        val at = s.positionAt(System.currentTimeMillis())
        val played = SystemClock.elapsedRealtime() - seekAt
        return at >= seekTarget - SEEK_NEAR_MS && at <= seekTarget + played + SEEK_NEAR_MS
    }

    private fun seekHeld(uri: String) = uri == seekUri && SystemClock.elapsedRealtime() < seekUntil

    fun seekTo(ms: Long) {
        if (io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayer.active.value) { io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayer.seekTo(ms); return }
        if (LocalPlayer.active.value) { LocalPlayer.seekTo(ms); return }
        cancelBurst()
        val now = SystemClock.elapsedRealtime()
        positionMs = ms
        positionAt = now
        seekUri = _nowPlaying.value.uri
        seekTarget = ms
        seekAt = now
        seekUntil = now + SEEK_HOLD_MS
        reconcileAfter(seekEnded, SEEK_HOLD_MS)
        // The newest seek only: a drag that ends while the last one is still on its way replaces it.
        val seq = ++seekSeq
        seekJob?.cancel()
        seekJob = scope.launch {
            try {
                // Sent once more when the page does not show it (busy, a slow network); after that
                // the page's word stands, wherever it is.
                repeat(2) { attempt ->
                    runCatching { kotlinx.coroutines.withTimeoutOrNull(SEEK_SEND_MS) { api.player.seek(ms) } }
                        .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; Log.w(TAG, "seek failed: ${it.message}") }
                    val landed = kotlinx.coroutines.withTimeoutOrNull(SEEK_CHECK_MS) {
                        api.player.state.first { it != null && (it.item?.uri != seekUri || seekLanded(it)) }
                    }
                    if (landed != null) return@launch
                    Log.w(TAG, "seek to $ms not taken by the page${if (attempt == 0) ": sent again" else ""}")
                }
            } finally {
                if (seq == seekSeq) {
                    seekUntil = 0L
                    main.removeCallbacks(seekEnded)
                    main.post { reconcile() }
                }
            }
        }
    }

    /** What [checkLibrary] found: [saved] of the uris asked about, [asked] the ones that went to Spotify, [failed] when that got no answer. */
    private class LibraryCheck(val saved: Set<String>, val asked: Set<String>, val failed: Boolean)

    /** Which of [uris] are saved in the user's library (Liked Songs / saved albums…). */
    suspend fun libraryContains(uris: List<String>): Set<String> = checkLibrary(uris).saved

    private suspend fun checkLibrary(uris: List<String>): LibraryCheck {
        if (uris.isEmpty()) return LibraryCheck(emptySet(), emptySet(), false)
        val now = SystemClock.elapsedRealtime()
        // A page prefetch and the page opening ask for the same list within a second, and the
        // rows of a list ask again for what the list just confirmed: only what is unknown (or
        // old enough to have changed elsewhere) goes to Spotify.
        val (known, unknown) = uris.partition { library.containsKey(it) && now - (savedAt[it] ?: 0L) < SAVED_FRESH_MS }
        val out = known.filterTo(mutableSetOf()) { library[it] == true }
        if (unknown.isEmpty()) return LibraryCheck(out, emptySet(), false)
        val flags = savedFlags(unknown) ?: return LibraryCheck(out, emptySet(), true)
        val saved = unknown.filterIndexed { i, _ -> flags.getOrNull(i) == true }.toSet()
        main.post {
            for (u in unknown) {
                if (u == intentUri && SystemClock.elapsedRealtime() < intentUntil) continue
                library[u] = u in saved
                savedAt[u] = now
            }
        }
        return LibraryCheck(out + saved, unknown.toSet(), false)
    }

    /** [uris] in one request; two callers asking the same list share that one request. */
    private suspend fun savedFlags(uris: List<String>): List<Boolean>? {
        val key = uris.sorted().joinToString("\n")
        val flight = savedFlightLock.withLock {
            savedFlights[key] ?: scope.async {
                try {
                    api.library.contains(uris)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    savedError = e.message ?: e.javaClass.simpleName
                    null
                }
            }.also { d ->
                savedFlights[key] = d
                d.invokeOnCompletion { savedFlights.remove(key, d) }
            }
        }
        return try {
            flight.await()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** Bumped at every like / unlike of a track Spotify took: the Liked Songs page refreshes its top. */
    val likedChanges = kotlinx.coroutines.flow.MutableStateFlow(0)

    /** Every like / unlike of a track the moment it is tapped (and its undo): track id, liked. */
    val likedTaps = kotlinx.coroutines.flow.MutableSharedFlow<Pair<String, Boolean>>(extraBufferCapacity = 16)

    /** Like / unlike a track by id. */
    fun setTrackSaved(trackId: String, on: Boolean) {
        if (trackId.isBlank()) return
        Haptics.toggle(on)
        setSaved(SPOTIFY_TRACK_PREFIX + trackId, on)
    }

    private fun isOnline(context: Context): Boolean {
        if (io.github.thake.soundnest.feature.system.settings.DataSaver.active) return false
        val cm = context.getSystemService(android.net.ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** Save / remove any item (album, playlist, track, artist, show, episode) in the user's library. */
    fun setSaved(uri: String, on: Boolean) {
        if (!io.github.thake.soundnest.feature.parental.settings.FeatureGates.canModifySpotifyLibrary(Graph.app)) {
            io.github.thake.soundnest.host.android.Logbook.log("library: $uri refused (read-only Spotify library)")
            return
        }
        intentUri = uri
        intentValue = on
        intentUntil = SystemClock.elapsedRealtime() + 3000
        onLibraryItem(uri, on)
        val track = uri.startsWith(SPOTIFY_TRACK_PREFIX)
        if (track) likedTaps.tryEmit(trackIdOf(uri) to on)
        val write = libraryWrites.incrementAndGet()
        // No connection: the change stays on screen and is sent when the network is back.
        if (!isOnline(Graph.app)) {
            io.github.thake.soundnest.core.catalog.LibraryOutbox.put(uri, on)
            io.github.thake.soundnest.host.android.Logbook.log("library: $uri ${if (on) "saved" else "removed"} kept for later (offline${if (io.github.thake.soundnest.feature.system.settings.DataSaver.active) ", data saver" else ""})")
            // Sent as soon as Spotify answers (put asks for it), not only when Android reports a
            // network coming up: a network already there (or the data saver turned off) never reports one.
            return
        }
        // An artist unfollowed, an album or a show removed: out of the Library's list at once.
        // Spotify's list (libraryV3) trails the write by seconds, so the artist stayed there and a
        // reload brought it back, as if the button did nothing (reported); playlists already did this.
        val listEdit = if (!track && !on) io.github.thake.soundnest.core.catalog.LibraryEdits.Remove(uri).also(io.github.thake.soundnest.core.catalog.LibraryEdits::begin) else null
        if (!track && on) io.github.thake.soundnest.core.catalog.LibraryEdits.dropRemovals(uri)
        scope.launch {
            // One write at a time; a refused one is undone on screen if no newer tap replaced it.
            libraryLock.withLock {
                runCatching { writeLibrary(uri, on) }.onSuccess {
                    io.github.thake.soundnest.host.android.Logbook.log("library: $uri ${if (on) "saved" else "removed"} on Spotify")
                    if (track) likedChanges.value++
                    listEdit?.let(io.github.thake.soundnest.core.catalog.LibraryEdits::confirm)
                    // Followed or saved: the Library reads its list again once Spotify has it.
                    if (!track && on) io.github.thake.soundnest.core.catalog.LibraryEdits.reloadSoon()
                }.onFailure {
                    listEdit?.let(io.github.thake.soundnest.core.catalog.LibraryEdits::cancel)
                    Log.w(TAG, "library change failed: ${it.message}")
                    io.github.thake.soundnest.host.android.Logbook.log("library: $uri change failed: ${it.javaClass.simpleName} ${it.message}")
                    // The connection went while it was sent: kept for later, like an offline tap.
                    if (it is java.io.IOException && !isOnline(Graph.app)) {
                        io.github.thake.soundnest.core.catalog.LibraryOutbox.put(uri, on)
                        return@withLock
                    }
                    if (write == libraryWrites.get()) {
                        intentUntil = 0
                        onLibraryItem(uri, !on)
                        if (track) likedTaps.tryEmit(trackIdOf(uri) to !on)
                        android.widget.Toast.makeText(Graph.app, io.github.thake.soundnest.R.string.library_change_failed, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
    private val libraryLock = kotlinx.coroutines.sync.Mutex()
    private val libraryWrites = java.util.concurrent.atomic.AtomicInteger()

    private suspend fun writeLibrary(uri: String, on: Boolean) {
        if (uri.startsWith("spotify:playlist:")) {
            if (on) api.playlists.follow(uri) else api.playlists.unfollow(uri)
        } else {
            if (on) api.library.add(listOf(uri)) else api.library.remove(listOf(uri))
        }
    }

    /** The library changes made offline ([io.github.thake.soundnest.core.catalog.LibraryOutbox]), sent once Spotify is reachable. */
    fun startLibraryOutbox(context: Context) {
        io.github.thake.soundnest.core.catalog.LibraryOutbox.init(context, scope,
            sender = { uri, on -> libraryLock.withLock { writeLibrary(uri, on) }; if (uri.startsWith(SPOTIFY_TRACK_PREFIX)) likedChanges.value++ },
            // The engine is started after the app's graph: wait for it, then for Spotify to answer,
            // then for the session's account (unknown after a session lost: it may be another one).
            ready = { awaitEngine(); api.ready.first { it }; io.github.thake.soundnest.feature.accounts.session.ProfileCache.awaitId(context, ACCOUNT_WAIT_MS) },
            account = { io.github.thake.soundnest.feature.accounts.session.ProfileCache.id },
            onAvailable = { scope.launch { kotlinx.coroutines.delay(3_000); io.github.thake.soundnest.core.catalog.LibraryOutbox.request() } },
        )
        // The Web Player ready again (a reload, a new sign-in): what waits is sent (nothing is asked when nothing waits),
        // once the session's account is read: after a session lost, what waits for it is told from what is not its own.
        scope.launch {
            awaitEngine()
            api.ready.collect { if (it) { io.github.thake.soundnest.feature.accounts.session.ProfileCache.awaitId(context, ACCOUNT_WAIT_MS); io.github.thake.soundnest.core.catalog.LibraryOutbox.request() } }
        }
    }

    private suspend fun awaitEngine() { while (!SoundNestRuntime.isStarted) kotlinx.coroutines.delay(1_000) }

    /** The library changes kept for later wait that long for the account's id (see ProfileCache.awaitId). */
    private const val ACCOUNT_WAIT_MS = 10_000L

    // ── Spotify Connect ──
    data class ConnectDevice(
        val id: String,
        val name: String,
        val type: String,
        val isSelf: Boolean,
        val isActive: Boolean,
        val rawName: String = name,
        val aliasId: String? = null,
    )

    private fun toConnectDevice(d: io.github.thake.soundnest.api.Device, forceRemoteActive: Boolean = false): ConnectDevice {
        val display = if (d.local) d.name else io.github.thake.soundnest.feature.speakers.settings.CustomSpeakerNames.resolve(Graph.app, d.id, d.name)
        return ConnectDevice(
            id = d.id,
            name = display,
            type = d.type,
            isSelf = if (forceRemoteActive) false else d.local,
            isActive = if (forceRemoteActive) true else d.active,
            rawName = d.name,
            aliasId = d.aliasId,
        )
    }

    private val _activeDevice = mutableStateOf<ConnectDevice?>(null)
    /** The device currently playing, when known and not this one. */
    val activeDevice: State<ConnectDevice?> get() = _activeDevice

    private fun refreshDevice() {
        scope.launch {
            runCatching { api.devices.refresh() }
            val active = api.devices.devices.value.firstOrNull { it.active }
            val allowedRemote = active?.takeIf {
                !it.local && io.github.thake.soundnest.feature.speakers.settings.TrustedNetworkGate.isDeviceAllowed(Graph.app, it.id, isSelf = false)
            }
            _activeDevice.value = allowedRemote?.let { toConnectDevice(it, forceRemoteActive = true) }
            // A negative volume is a device with a fixed one (Web Player: disable_volume): not 0 %.
            allowedRemote?.volume?.takeIf { it >= 0 }?.let { if (SystemClock.elapsedRealtime() > volumeHoldUntil) { _remoteVolume.intValue = (it * 100).toInt().coerceIn(0, 100); remoteVolumeOf = allowedRemote.id } }
            PlaybackService.setPlaybackDevice(_activeDevice.value != null, remoteVolume)
        }
    }

    /** Devices of the account, this phone first. */
    suspend fun devices(): List<ConnectDevice> {
        // What the Web Player's own picker does when it opens: it asks Spotify to look for devices
        // again. The answer is not known (no device test yet); bounded, and a page without it is fine.
        kotlinx.coroutines.withTimeoutOrNull(DISCOVERY_WAIT_MS) { runCatching { SoundNestRuntime.spotify.raw.service("ConnectDiscoveryAPI", "startActiveDiscovery") } }
        runCatching { api.devices.refresh() }
        val rawList = api.devices.devices.value.map { toConnectDevice(it) }
            .sortedWith(compareByDescending<ConnectDevice> { it.isSelf }.thenBy { it.name })
            .distinctBy { io.github.thake.soundnest.feature.speakers.connect.ConnectTransferMatcher.dedupeKey(it) }
        val list = io.github.thake.soundnest.feature.speakers.settings.TrustedNetworkGate.filterConnectDevices(Graph.app, rawList)
        _activeDevice.value = list.firstOrNull { it.isActive && !it.isSelf }
        // Kinds only (no names or ids): a report that says "no speaker" shows whether Spotify listed one.
        io.github.thake.soundnest.host.android.Logbook.log("connect: picker lists ${list.size}/${rawList.size} devices (${list.joinToString { it.type.lowercase() }})")
        return list
    }

    /**
     * The other device picked in Myspoti to play on; null: this phone. Kept over a restart of the
     * app (a day at most): forgotten, the first skip after the app came back took the device for one
     * that took playback by itself and pulled the music back to the phone (B12, reported: "I skip a
     * song and the audio starts playing through the phone").
     */
    private var chosenRemote: String?
        get() = io.github.thake.soundnest.feature.speakers.settings.SpeakersSettings.getChosenRemote(Graph.app)
        set(value) {
            io.github.thake.soundnest.feature.speakers.settings.SpeakersSettings.setChosenRemote(Graph.app, value)
        }

    /**
     * Another device took playback without being picked here (the Spotify app opened on the
     * same account, for instance): Myspoti's play, skip and previous then drove that device,
     * where a free account's commands are refused. The music comes back to this phone first.
     */
    /** When [takeBackPlayback] last read the device list; 0 = never read (read it now). */
    @Volatile private var devicesFreshUntil = 0L

    private suspend fun takeBackPlayback() {
        // The device list as Spotify has it now: the one kept here is only refreshed on a song
        // change, and after a cold start it does not know a device that took playback before.
        // But this used to run on EVERY tap, i.e. a server round trip before the song was even
        // asked for: measured 963 ms from tap to sound against 554 ms for a skip, which takes the
        // same playback path without this call. The list is read again only once the entry is
        // older than [DEVICES_FRESH_MS] — a takeover within those seconds is still caught by the
        // play check (the page does not move, the play is sent again) and by the next read.
        // Measured (15/10 run): this refresh answers from the page's cache in 8-36 ms, so it is not
        // what makes a tap slower than a skip (963 vs 554 ms then) — keeping it unconditional keeps
        // "a device that took playback is pulled back at once" (beta.7 behaviour). The timing stays
        // in the log because the day it becomes slow, that line says so.
        devicesFreshUntil = 0L
        val started = System.currentTimeMillis()
        kotlinx.coroutines.withTimeoutOrNull(DEVICES_WAIT_MS) { runCatching { api.devices.refresh() } }
        Log.i(TAG, "device list read in ${System.currentTimeMillis() - started} ms")
        val active = api.devices.devices.value.firstOrNull { it.active }
        val allowedRemote = active?.takeIf {
            !it.local && io.github.thake.soundnest.feature.speakers.settings.TrustedNetworkGate.isDeviceAllowed(Graph.app, it.id, isSelf = false)
        }
        _activeDevice.value = allowedRemote?.let { toConnectDevice(it, forceRemoteActive = true) }
        val remote = active?.takeIf { !it.local }?.let { toConnectDevice(it, forceRemoteActive = true) } ?: return
        if (io.github.thake.soundnest.feature.speakers.connect.ConnectTransferMatcher.matchesChosenRemote(remote.id, chosenRemote, remote.aliasId)) {
            if (allowedRemote != null) return
            // Child left the trusted Home Wi-Fi (or parent hid this speaker): forget chosenRemote and pull here.
            chosenRemote = null
        }
        // A Jam joined here: the music plays on the host's device and the commands go to it (Connect,
        // to the session's device): pulling it back took the host's playback away and broke the Jam.
        // (The Jam is read again first when none is known: after a restart the app has not read it yet.)
        if (io.github.thake.soundnest.feature.speakers.settings.TrustedNetworkGate.isJamAllowed(Graph.app)) {
            if (api.jam.session.value == null) kotlinx.coroutines.withTimeoutOrNull(1_500) { runCatching { api.jam.refresh() } }
            if (remote.id.startsWith("social-connect") || api.jam.session.value?.isHost == false) {
                Log.i(TAG, "playback on ${remote.name} in a Jam: left there")
                return
            }
        }
        Log.i(TAG, "playback taken by ${remote.name}: pulled back here")
        // A pull-back can land on this phone at 0:00 (reported: "sometimes on Connect the sound
        // starts over"). The position is remembered across the pull and restored only when the same
        // item really rolled back — a new play right after this call asks for its own position.
        val before = api.player.state.value?.let { s -> s.item?.uri?.let { uri -> uri to s.positionAt(System.currentTimeMillis()) } }
        // Bounded: a transfer from a device that went away can leave the page's promise
        // pending for ever, and every later command waiting behind it.
        val pulled = kotlinx.coroutines.withTimeoutOrNull(PULL_BACK_WAIT_MS) { runCatching { api.devices.pullHere() } }
        // Whatever happened, the list just changed under us: read it again next time.
        devicesFreshUntil = 0L
        if (pulled?.isSuccess != true) Log.w(TAG, "pull here failed: ${pulled?.exceptionOrNull()?.message ?: "no answer"}")
        // No answer: the page's Connect layer waits on that device and would hold every later
        // command. A new page claims playback for itself at the first command.
        if (pulled == null) {
            Log.w(TAG, "pull here hangs: player page reloaded")
            io.github.thake.soundnest.host.android.Logbook.log("pull here hangs (no answer in ${PULL_BACK_WAIT_MS / 1000} s): player page reloaded")
            SoundNestRuntime.spotify.player.reloadPage()
        }
        _activeDevice.value = null
        PlaybackService.setPlaybackDevice(false, remoteVolume)
        kotlinx.coroutines.delay(PULL_BACK_SETTLE_MS)
        // Same item but an earlier position: the page restarted it. Put the listener back where they
        // were. Anything else (a different item, a position that did not move back) is left alone.
        before?.let { (uri, pos) ->
            val now = api.player.state.value
            val at = now?.positionAt(System.currentTimeMillis()) ?: return@let
            if (now.item?.uri == uri && pos > 3_000 && at < pos - 3_000) {
                Log.i(TAG, "playback came back at $at ms on the same item: back to $pos ms")
                runCatching { api.player.seek(pos) }
            } else {
                Log.i(TAG, "playback came back at $at ms (item ${now.item?.uri})")
            }
        }
    }

    /** Move playback to [device] (Spotify Connect transfer). */
    fun transferTo(device: ConnectDevice) {
        if (io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayer.active.value) {
            main.post {
                android.widget.Toast.makeText(Graph.app, Graph.app.getString(io.github.thake.soundnest.R.string.cast_failed, device.name), android.widget.Toast.LENGTH_LONG).show()
            }
            return
        }
        if (!io.github.thake.soundnest.feature.speakers.settings.TrustedNetworkGate.isDeviceAllowed(Graph.app, device.id, isSelf = device.isSelf)) {
            io.github.thake.soundnest.host.android.Logbook.log("connect: transfer refused by TrustedNetworkGate")
            main.post {
                android.widget.Toast.makeText(Graph.app, Graph.app.getString(io.github.thake.soundnest.R.string.cast_failed, device.name), android.widget.Toast.LENGTH_LONG).show()
            }
            return
        }
        Haptics.confirm()
        transferNow(device)
    }

    private fun transferNow(device: ConnectDevice) {
        chosenRemote = if (device.isSelf) null else device.id
        _activeDevice.value = if (device.isSelf) null else device
        val trackId = currentTrackId
        if (!device.isSelf && LocalPlayer.active.value && trackId.isNotBlank()) {
            val track = SPOTIFY_TRACK_PREFIX + trackId
            val context = LocalPlayer.contextUri.ifBlank { track }
            val at = LocalPlayer.position()
            val wasPlaying = LocalPlayer.isPlaying()
            Log.i(TAG, "local song to ${device.name}: $track in $context at $at ms")
            LocalPlayer.stop()
            command(onFailure = { transferFailed(device) }) {
                api.devices.transfer(device.id, play = wasPlaying, aliasId = device.aliasId)
                api.player.play(context, trackUri = track.takeIf { it != context }, positionMs = at)
                if (!wasPlaying) api.player.pause()
                else io.github.thake.soundnest.feature.speakers.connect.SonosLocal.afterTransfer(device.id, device.rawName, true)
                verifyTransfer(device, wanted = wasPlaying)
            }
            return
        }
        val wanted = isPlaying()
        command(onFailure = { transferFailed(device) }) {
            if (device.isSelf) {
                api.devices.pullHere()
            } else {
                if (api.player.state.value?.item == null && trackId.isNotBlank()) {
                    val track = SPOTIFY_TRACK_PREFIX + trackId
                    val context = currentContextUri.ifBlank { track }
                    val at = getCurrentPosition()
                    Log.i(TAG, "seeding dormant session to ${device.name}: $track in $context at $at ms")
                    api.devices.transfer(device.id, play = wanted, aliasId = device.aliasId)
                    api.player.play(context, trackUri = track.takeIf { it != context }, positionMs = at)
                    if (!wanted) api.player.pause()
                } else {
                    api.devices.transfer(device.id, play = wanted, aliasId = device.aliasId)
                }
                // A Sonos may take the song and stay paused: its own play is pressed then.
                io.github.thake.soundnest.feature.speakers.connect.SonosLocal.afterTransfer(device.id, device.rawName, wanted)
                verifyTransfer(device, wanted = wanted)
            }
        }
    }

    private suspend fun verifyTransfer(device: ConnectDevice, wanted: Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10_000L
        var confirmedActive = false
        while (SystemClock.elapsedRealtime() < deadline) {
            kotlinx.coroutines.delay(500)
            runCatching { api.devices.refresh() }
            val match = api.devices.devices.value.firstOrNull {
                io.github.thake.soundnest.feature.speakers.connect.ConnectTransferMatcher.matchesDevice(device, it)
            }
            if (match?.active == true) {
                if (NativeAudio.holdsPlayback) {
                    runCatching { NativeAudio.yieldPlayback() }
                }
                if (!NativeAudio.holdsPlayback) {
                    confirmedActive = true
                    break
                }
            }
        }
        if (!confirmedActive) {
            Log.w(TAG, "transfer to ${device.name} (${device.id}) was not confirmed active within 10s; rolling back")
            withContext(Dispatchers.Main) {
                transferFailed(device)
            }
        } else {
            if (wanted) {
                val state = api.player.state.value
                val isPaused = state?.isPaused ?: false
                if (isPaused) {
                    Log.i(TAG, "target device ${device.name} active but paused; nudging resume")
                    runCatching { api.player.resume() }
                }
            }
            refreshDevice()
        }
    }

    /**
     * A transfer Spotify did not take (no such device any more, a speaker that went to sleep, a
     * refusal): the picker had already shown the device as playing, and the app went on driving a
     * device that plays nothing. Back on this phone, and the person is told.
     */
    private fun transferFailed(device: ConnectDevice) {
        if (device.isSelf || !io.github.thake.soundnest.feature.speakers.connect.ConnectTransferMatcher.matchesChosenRemote(device.id, chosenRemote, device.aliasId)) return
        chosenRemote = null
        _activeDevice.value = null
        main.post {
            android.widget.Toast.makeText(Graph.app, Graph.app.getString(io.github.thake.soundnest.R.string.cast_failed, device.name), android.widget.Toast.LENGTH_LONG).show()
        }
    }

    /** Volume (0..100) of the device that plays, as last reported. State: the player's volume bar follows it. */
    private val _remoteVolume = androidx.compose.runtime.mutableIntStateOf(100)
    val remoteVolume: Int get() = _remoteVolume.intValue
    @Volatile private var volumeHoldUntil = 0L
    /**
     * The device [remoteVolume] is the level of (reported by it, or set by the user while it played);
     * null: the first 100 %, never any device's.
     */
    @Volatile private var remoteVolumeOf: String? = null

    /**
     * The newest volume asked for, sent one at a time by [volumeSender]. A drag on the player's
     * volume bar asks for dozens of levels a second: sent each in its own command they could
     * reach the device out of order and leave it on an older level.
     */
    private val volumeToSend = kotlinx.coroutines.flow.MutableStateFlow<Int?>(null)
    private val volumeSender by lazy {
        scope.launch {
            volumeToSend.collect { v ->
                if (v != null) runCatching {
                    // Another device plays: its own volume, as Spotify's apps set it; else (or refused) the page's.
                    val target = _activeDevice.value?.id
                    val sent = target != null && runCatching { SoundNestRuntime.spotify.devices.setVolume(target, v / 100.0) }
                        .onFailure { Log.w(TAG, "device volume failed: ${it.message}") }.getOrDefault(false)
                    if (!sent) api.player.setVolume(v / 100.0)
                }.onFailure { Log.w(TAG, "volume failed: ${it.message}") }
            }
        }
    }

    /** Set the volume of the device that plays (volume keys, system slider, the player's volume bar). */
    fun setRemoteVolume(volume: Int) {
        _remoteVolume.intValue = volume.coerceIn(0, 100)
        remoteVolumeOf = _activeDevice.value?.id
        volumeHoldUntil = SystemClock.elapsedRealtime() + 1500
        volumeSender
        volumeToSend.value = remoteVolume
        PlaybackService.setPlaybackDevice(_activeDevice.value != null, remoteVolume)
    }

    // ── Sleep timer (delegated to SleepTimerController) ──
    val sleepEndsAt: State<Long?>
        get() = io.github.thake.soundnest.feature.player.sleeptimer.playback.SleepTimerController.sleepEndsAt

    /** [minutes] null = off, 0 = at the end of the current track. */
    fun setSleepTimer(minutes: Int?) =
        io.github.thake.soundnest.feature.player.sleeptimer.playback.SleepTimerController.setSleepTimer(minutes)


    // ── Queue (the API pushes every change) ──

    /**
     * The queue row [item] stands for in Spotify's latest queue: by its row id, else by its song
     * (a row moved a moment ago got a new id, and the list on screen may still show the old one).
     */
    private fun apiItem(item: QueueItem): io.github.thake.soundnest.api.NowPlaying? =
        apiQueue?.let { q ->
            val all = q.queued + q.nextUp
            all.firstOrNull { it.uri == item.uri && (item.uid.isBlank() || it.uid == item.uid) } ?: all.firstOrNull { it.uri == item.uri }
        }

    /**
     * [item] in the page's queue, waited for when the page does not list it yet: an edit made while
     * the page is rebuilt (just after an install or a "player never started" reload, log 04/11
     * 19:10) found no queue and was dropped without a word, the row then going back.
     */
    private suspend fun liveItem(item: QueueItem): io.github.thake.soundnest.api.NowPlaying? {
        apiItem(item)?.let { return it }
        val q = kotlinx.coroutines.withTimeoutOrNull(QUEUE_READY_MS) {
            api.player.queue.first { q -> q != null && (q.queued + q.nextUp).any { it.uri == item.uri } }
        } ?: return null.also { io.github.thake.soundnest.host.android.Logbook.log("queue edit: ${item.uri} not in the page's queue, edit dropped") }
        val all = q.queued + q.nextUp
        return all.firstOrNull { it.uri == item.uri && it.uid == item.uid } ?: all.firstOrNull { it.uri == item.uri }
    }
    private const val QUEUE_READY_MS = 12_000L

    /**
     * Queue edits, one at a time and each after Spotify has shown the one before. Sent together
     * they raced in the Web Player: "move up into the queue" (add + remove) lost its remove and
     * left the song twice, and that copy could then not be moved or taken out (reported).
     */
    private val queueLock = kotlinx.coroutines.sync.Mutex()
    private fun queueCommand(block: suspend () -> Unit) {
        scope.launch { queueLock.withLock { runCatching { block() }.onFailure { Log.w(TAG, "queue edit failed: ${it.message}") } } }
    }

    private const val QUEUE_ECHO_MS = 2_500L

    /** Waits (at most [QUEUE_ECHO_MS]) for Spotify's queue to show [done]; the queue then, or null. */
    private suspend fun awaitQueue(done: (io.github.thake.soundnest.api.Queue) -> Boolean): io.github.thake.soundnest.api.Queue? =
        kotlinx.coroutines.withTimeoutOrNull(QUEUE_ECHO_MS) { api.player.queue.first { it != null && done(it) } }

    /** "Play next": first in the queue, right after the current track. */
    fun playNext(trackId: String) {
        if (trackId.isBlank()) return
        val policy = io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore.current(Graph.app)
        if (trackId in policy.blockedTrackIds) return
        Haptics.confirm()
        command { api.player.playNext(listOf(SPOTIFY_TRACK_PREFIX + trackId)) }
    }

    fun addToQueue(trackId: String) {
        if (trackId.isBlank()) return
        val policy = io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore.current(Graph.app)
        if (trackId in policy.blockedTrackIds) return
        Haptics.confirm()
        command { api.player.addToQueue(listOf(SPOTIFY_TRACK_PREFIX + trackId)) }
    }

    /** Songs per call of Spotify's queue: a whole playlist in one call is a long wait for the page. */
    private const val QUEUE_BATCH = 100

    /**
     * "Play next" / "Add to queue" for a whole list, in the list's order; [done] is told how many
     * songs joined a queue (on the main thread). While the local player plays, a device file joins
     * the local queue and the others Spotify's; otherwise Spotify's queue takes everything it can
     * stream (tracks and episodes). A device file cannot be streamed by Spotify: it is left out then,
     * as a Spotify local file nothing matched and a greyed song are.
     */
    fun queueSongs(songs: List<SongsModel>, next: Boolean, done: (Int) -> Unit) {
        Haptics.confirm()
        scope.launch {
            val policy = io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore.current(Graph.app)
            val local = LocalPlayer.active.value
            val rows = io.github.thake.soundnest.feature.parental.filter.PlaybackGuardrail.sanitizeSongs(
                songs.filter { it.playable && !it.isUnmatchedLocal },
                policy,
                currentContextUri,
            )
            val files = if (local && rows.any { it.isDeviceFile })
                runCatching { io.github.thake.soundnest.feature.localfiles.data.LocalMusic.tracks(Graph.app) }.getOrDefault(emptyList()).associateBy { io.github.thake.soundnest.feature.localfiles.data.LocalFileMatch.key(it) }
            else emptyMap()
            val toLocal = ArrayList<io.github.thake.soundnest.feature.localfiles.data.LocalTrack>()
            val toSpotify = ArrayList<String>()
            for (s in rows) {
                val uri = when {
                    s.isDeviceFile -> { if (local) files[s.url]?.let { toLocal += it }; continue }
                    s.url.startsWith("episode:") -> "spotify:episode:" + s.url.removePrefix("episode:")
                    s.spotifyTrackId.isNotBlank() -> SPOTIFY_TRACK_PREFIX + s.spotifyTrackId
                    else -> continue
                }
                toSpotify += uri
            }
            if (toLocal.isNotEmpty()) withContext(Dispatchers.Main) { LocalPlayer.enqueueAll(toLocal, next) }
            if (toSpotify.isNotEmpty()) {
                val batches = toSpotify.chunked(QUEUE_BATCH)
                queueCommand {
                    if (!next) batches.forEach { api.player.addToQueue(it) }
                    // Each batch goes in front of the one sent before it, so the last is sent first;
                    // Spotify's queue is shown again between two (playNext reads its first row).
                    else batches.asReversed().forEachIndexed { i, batch ->
                        val before = api.player.queue.value
                        api.player.playNext(batch)
                        if (i < batches.lastIndex) awaitQueue { it !== before }
                    }
                }
            }
            val count = toLocal.size + toSpotify.size
            withContext(Dispatchers.Main) { done(count) }
        }
    }

    fun skipTo(item: QueueItem) {
        if (item.provider == AUDIBLE) { item.uid.toIntOrNull()?.let(io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayer::skipToChapter); return }
        // Its index found now: a row shown in a just-dragged order carries the index it had before (see localIndex).
        if (item.provider == LOCAL) { localIndex(item)?.let(LocalPlayer::skipTo); return }
        cancelBurst()
        playedByHand = item.uri
        val id = trackIdOf(item.uri)
        if (id.isNotBlank()) markPending(id)
        boundState?.updateSongState(item.song.coverUri, item.song.title, item.song.singer, true, item.song.id, 0, item.song.album)
        durationMs = item.song.durationMs.toLong()
        pushNotification(item.song, false)
        val target = apiItem(item) ?: return
        transport(waitReady = !stateSeen) { api.player.skipTo(target) }
    }

    /** "Clear queue": drops the tracks the user queued (not the context's next ones). */
    fun clearQueue() {
        if (io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayer.active.value) return
        if (LocalPlayer.active.value) { LocalPlayer.clearQueued(); return }
        _queue.value = _queue.value.copy(queued = emptyList())
        command { api.player.clearQueue() }
    }

    /**
     * The local player's list index of a queue row, found now: the row's own uid is the index it had
     * when the queue was reported, and a row shown in a just-dragged order carries an older one.
     */
    private fun localIndex(item: QueueItem): Int? {
        val up = LocalPlayer.upcoming().filter { "local:${it.second.id}" == item.uri }
        return (up.firstOrNull { it.first.toString() == item.uid } ?: up.firstOrNull())?.first
    }

    /**
     * Queue moves sent to Spotify and not done yet. Spotify shows a move late and in steps (the song
     * out of one part, queued at the end, then placed), and showing them took the song back to where
     * it was before it reached the place it was dropped at. So while a move is under way its reports
     * are held and the order the user dropped ([showQueueOrder]) stays; the last move to finish shows
     * the queue as Spotify has it then, and for [STALE_MS] after, a late report of an order from
     * before the moves is ignored.
     */
    private val queueMovesInFlight = java.util.concurrent.atomic.AtomicInteger()
    private val staleOrders = java.util.Collections.synchronizedSet(HashSet<Pair<List<String>, List<String>>>())
    @Volatile private var staleUntil = 0L
    private const val STALE_MS = 1_500L

    private fun order(q: io.github.thake.soundnest.api.Queue) = q.queued.map { it.uri } to q.nextUp.map { it.uri }

    /** Shows [queued] / [nextUp] at once: the order a drop made, kept while the move that makes it so runs. */
    fun showQueueOrder(queued: List<QueueItem>, nextUp: List<QueueItem>) {
        if (LocalPlayer.active.value || io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayer.active.value) return
        val shown = _queue.value
        if (shown.queued == queued && shown.nextUp == nextUp) return
        if (queueMovesInFlight.get() == 0) staleOrders.clear()
        staleOrders += shown.queued.map { it.uri } to shown.nextUp.map { it.uri }
        _queue.value = shown.copy(queued = queued, nextUp = nextUp)
    }

    /** A move of a Spotify queue row, run as [queueCommand]; see [queueMovesInFlight]. */
    private fun queueMove(block: suspend () -> Unit) {
        queueMovesInFlight.incrementAndGet()
        queueCommand {
            try {
                block()
            } finally {
                if (queueMovesInFlight.decrementAndGet() == 0) {
                    staleUntil = SystemClock.elapsedRealtime() + STALE_MS
                    api.player.queue.value?.let { staleOrders -= order(it) }
                    main.post { if (acceptsSessionUpdates()) applyQueue(api.player.queue.value ?: apiQueue) }
                }
            }
        }
    }

    /** Whether [q] shows [uri] right before / after [target], in either part. */
    private fun placed(q: io.github.thake.soundnest.api.Queue, uri: String, target: String, after: Boolean): Boolean =
        listOf(q.queued, q.nextUp).any { part ->
            val uris = part.map { it.uri }
            val i = uris.indexOf(uri)
            val t = uris.indexOf(target)
            i >= 0 && t >= 0 && i == (if (after) t + 1 else t - 1)
        }

    /**
     * Debug builds: a queue drawn with no player behind it (QueueProbeActivity, to drag rows on an
     * emulator); its moves are applied to it at once, as the local player reports them.
     */
    @Volatile private var probeQueue = false
    internal fun showProbeQueue(q: EngineQueue) {
        if (!io.github.thake.soundnest.BuildConfig.DEBUG) return
        probeQueue = true
        _queue.value = q
    }
    private fun probeMove(item: QueueItem, target: QueueItem?, after: Boolean, intoQueue: Boolean) = main.postDelayed({
        val q = _queue.value
        val queued = q.queued.filter { it.uri != item.uri }.toMutableList()
        val next = q.nextUp.filter { it.uri != item.uri }.toMutableList()
        val part = when {
            intoQueue -> queued
            target != null && queued.any { it.uri == target.uri } -> queued
            else -> next
        }
        val at = target?.let { t -> part.indexOfFirst { it.uri == t.uri }.takeIf { it >= 0 }?.let { if (after) it + 1 else it } } ?: 0
        part.add(at.coerceIn(0, part.size), item)
        _queue.value = q.copy(queued = queued, nextUp = next)
    }, 300)

    /** Move [item] just before / after [target]; a Spotify row stays in its section (see [moveIntoQueue]), a local one goes into [target]'s. */
    fun reorder(item: QueueItem, target: QueueItem, after: Boolean) {
        if (probeQueue) { probeMove(item, target, after, intoQueue = false); return }
        if (item.provider == AUDIBLE || target.provider == AUDIBLE) return
        if (item.provider == LOCAL) {
            val from = localIndex(item) ?: return
            val t = localIndex(target) ?: return
            LocalPlayer.moveBeside(from, t, after)
            return
        }
        // Read when the edit runs, after the ones before it: never a row id that has gone stale.
        queueMove {
            val a = liveItem(item) ?: return@queueMove
            val b = liveItem(target) ?: return@queueMove
            api.player.moveInQueue(a, b, after)
            // Done once Spotify shows it in place, not at its first report: that one may still be of the old order.
            awaitQueue { placed(it, a.uri, b.uri, after) }
        }
    }

    /** Move a "next up" track into the queue, right after the current one. */
    fun moveToQueueFront(item: QueueItem) = moveIntoQueue(item, null, after = false)

    /**
     * A "next up" track moved up into the queue, before / after [target] (a queued row; null: first).
     * As the official app: out of "next up" first, then queued — in that order and one after the
     * other (both at once left it in both places), then placed where it was dropped.
     */
    fun moveIntoQueue(item: QueueItem, target: QueueItem?, after: Boolean) {
        if (probeQueue) { probeMove(item, target, after, intoQueue = true); return }
        if (item.provider == AUDIBLE || target?.provider == AUDIBLE) return
        if (item.provider == LOCAL) {
            val from = localIndex(item) ?: return
            LocalPlayer.moveIntoQueue(from, target?.let { localIndex(it) ?: return }, after)
            return
        }
        queueMove {
            val a = liveItem(item) ?: return@queueMove
            val first = apiQueue?.queued?.firstOrNull()
            api.player.removeFromQueue(listOf(a))
            awaitQueue { q -> q.nextUp.none { it.uid == a.uid } }
            api.player.addToQueue(listOf(a.uri))
            val added = awaitQueue { q -> q.queued.any { it.uri == a.uri } }?.queued?.lastOrNull { it.uri == a.uri } ?: return@queueMove
            // Queued at the end: moved to the place it was dropped (the front when no target).
            val t = target?.let(::apiItem) ?: first?.takeIf { it.uid != added.uid } ?: return@queueMove
            val placeAfter = if (target == null) false else after
            // Already there (dropped last in the queue): nothing to move.
            if (api.player.queue.value?.let { placed(it, added.uri, t.uri, placeAfter) } == true) return@queueMove
            api.player.moveInQueue(added, t, placeAfter)
            awaitQueue { placed(it, added.uri, t.uri, placeAfter) }
        }
    }

    fun removeFromQueue(item: QueueItem) {
        if (item.provider == AUDIBLE) return
        // Found now, as for skipTo: with the index the row was reported with, a swipe right after a
        // drag removed whichever song had taken that place.
        if (item.provider == LOCAL) { localIndex(item)?.let(LocalPlayer::remove); return }
        _queue.value = _queue.value.let { q -> q.copy(queued = q.queued - item, nextUp = q.nextUp - item) }
        queueCommand {
            val a = liveItem(item) ?: return@queueCommand
            api.player.removeFromQueue(listOf(a))
            awaitQueue { q -> (q.queued + q.nextUp).none { it.uid == a.uid && it.uri == a.uri } }
        }
    }

    fun isPlaying(): Boolean = when {
        io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayer.active.value -> io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayer.isPlaying()
        LocalPlayer.active.value -> LocalPlayer.isPlaying()
        else -> playing
    }

    fun getDuration(): Long = when {
        io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayer.active.value -> io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayer.duration()
        LocalPlayer.active.value -> LocalPlayer.duration()
        else -> durationMs
    }

    fun getCurrentPosition(): Long {
        if (io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayer.active.value) return io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayer.position()
        if (LocalPlayer.active.value) return LocalPlayer.position()
        val base = positionMs
        if (!playing || isBuffering) return base
        val now = base + (SystemClock.elapsedRealtime() - positionAt)
        return if (durationMs > 0) now.coerceAtMost(durationMs) else now
    }

    /**
     * Whether [getCurrentPosition] moves on now. Not only paused: a song that is to play but is
     * loading (its start, a seek into what is not loaded yet, a stream that does not keep up)
     * stands still too; the lyrics hold meanwhile.
     */
    fun positionMoving(): Boolean = when {
        io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayer.active.value ->
            io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayer.isPlaying() && !io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayer.isBuffering()
        LocalPlayer.active.value -> LocalPlayer.isPlaying()
        else -> playing && !isBuffering
    }

    /**
     * A tap on a synced lyric line, for the diagnostic: where it jumps, which player plays (and
     * whether its source can seek), then where the song is 300 ms and 2 s later.
     */
    fun logLyricJump(targetMs: Long) {
        val log = io.github.thake.soundnest.host.android.Logbook::log
        val player = when {
            io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayer.active.value -> "audible"
            LocalPlayer.active.value -> "local, ${LocalPlayer.describe()}"
            _activeDevice.value != null -> "spotify connect"
            else -> "spotify"
        }
        log("lyrics: tap on the line at $targetMs ms, from ${getCurrentPosition()} ms ($player)")
        for (afterMs in longArrayOf(300L, 2_000L)) main.postDelayed({
            log("lyrics: $afterMs ms after the tap to $targetMs ms: at ${getCurrentPosition()} ms" +
                if (positionMoving()) "" else if (isPlaying() || LocalPlayer.active.value && LocalPlayer.playWhenReady()) ", loading" else ", paused")
        }, afterMs)
    }

    /** A local file starts: Spotify and Audible pause. */
    fun pauseEngineForLocal() {
        io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayer.stop()
        localStartedAt = SystemClock.elapsedRealtime()
        // What the page reported last (the screens may already show the song asked for).
        engineUriBeforeLocal = lastPageUri
        engineRepaused = false
        if (playing) command { api.player.pause() }
        playing = false
        // The local player keeps the output open itself; the engine's states, which
        // would close the silent stream, are ignored while it plays.
        SpeakerKeepAlive.set(false)
    }

    /** An Audible audiobook starts: Spotify pauses (wherever it plays). */
    fun pauseEngineForAudible() {
        audibleStartedAt = SystemClock.elapsedRealtime()
        engineUriBeforeAudible = lastPageUri
        engineRepausedForAudible = false
        if (playing) command { api.player.pause() }
        playing = false
        SpeakerKeepAlive.set(false)
    }

    private var localStartedAt = 0L
    /** What the engine played when the local file started (to tell a pause that did not take from a takeover). */
    private var engineUriBeforeLocal = ""
    private var engineRepaused = false

    private var audibleStartedAt = 0L
    private var engineUriBeforeAudible = ""
    private var engineRepausedForAudible = false

    /**
     * The engine reports playing while a local file plays. Two streams must never play
     * at once: a pause that did not take (the engine was not ready) is sent once more;
     * anything else is Spotify taking over (a Connect "play on this phone", a resume from
     * another device), and the local player yields. True when the state is to be ignored.
     */
    private fun engineWhileLocal(s: PlayerState): Boolean {
        if (s.isPaused) return true
        // The page's last states from before the pause reached it.
        if (SystemClock.elapsedRealtime() - localStartedAt < LOCAL_GRACE_MS) return true
        if (s.item?.uri == engineUriBeforeLocal && !engineRepaused) {
            engineRepaused = true
            Log.i(TAG, "engine still playing under a local file: pausing it again")
            command { api.player.pause() }
            return true
        }
        Log.i(TAG, "engine started playing: the local player yields")
        LocalPlayer.stop()
        return false
    }

    private fun engineWhileAudible(s: PlayerState): Boolean {
        if (s.isPaused) return true
        if (SystemClock.elapsedRealtime() - audibleStartedAt < LOCAL_GRACE_MS) return true
        if (s.item?.uri == engineUriBeforeAudible && !engineRepausedForAudible) {
            engineRepausedForAudible = true
            Log.i(TAG, "engine still playing under Audible: pausing it again")
            command { api.player.pause() }
            return true
        }
        Log.i(TAG, "engine started playing: Audible player yields")
        io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayer.stop()
        return false
    }

    private inline fun runOnMainImmediate(crossinline block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post { block() }
    }

    /** An Audible audiobook's projected chapter / state, shown in PodcastPlayerScreen, mini player, and notification. */
    fun onAudibleState(
        chapterSong: SongsModel,
        chapterUri: String,
        bookUri: String,
        bookTitle: String,
        isPlaying: Boolean,
        isBuffering: Boolean,
        chapterPosMs: Long,
        chapterDurMs: Long,
    ) {
        runOnMainImmediate {
            if (!io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayer.active.value) return@runOnMainImmediate
            currentTrackId = ""
            currentContextUri = bookUri
            playing = isPlaying
            positionMs = chapterPosMs
            positionAt = SystemClock.elapsedRealtime()
            durationMs = chapterDurMs
            bufferingState.value = isBuffering
            val shown = NowPlaying(
                uri = chapterUri,
                contextUri = bookUri,
                contextName = bookTitle,
                provider = AUDIBLE,
            )
            if (_nowPlaying.value != shown) {
                _nowPlaying.value = shown
            }
            val cover = io.github.thake.soundnest.host.android.Covers.large(chapterSong.coverUri)
            boundState?.let { st ->
                st.likeState.value = false
                st.updateSongState(cover, chapterSong.title, chapterSong.singer, isPlaying, chapterSong.id, 0, chapterSong.album)
            }
            runCatching { pushNotification(chapterSong.copy(coverUri = cover), false) }
        }
    }

    fun onAudibleQueue(current: SongsModel, upcoming: List<Pair<Int, SongsModel>>) {
        runOnMainImmediate {
            if (!io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayer.active.value) return@runOnMainImmediate
            val items = upcoming.map { (chapterIdx, song) ->
                QueueItem(song = song, uri = song.url, uid = chapterIdx.toString(), provider = AUDIBLE)
            }
            _queue.value = EngineQueue(current = current, queued = emptyList(), nextUp = items)
        }
    }

    fun onAudibleStopped() {
        runOnMainImmediate {
            playing = false
            bufferingState.value = false
            val wasAudible = _nowPlaying.value.uri.startsWith("audible:")
            if (wasAudible) {
                _nowPlaying.value = NowPlaying()
                boundState?.updateSongState("", "", "", false, 0, 0, "")
            } else {
                boundState?.updatePlayingState(false)
            }
            if (_queue.value.current?.url?.startsWith("audible:") == true ||
                _queue.value.nextUp.any { it.provider == AUDIBLE }
            ) {
                _queue.value = EngineQueue()
                runCatching { applyQueue(apiQueue) }
            }
            runCatching { pushNotification(null, false) }
        }
    }

    /** The local player's track / state, shown like any other (player, mini player, notification). */
    fun onLocalState(t: io.github.thake.soundnest.feature.localfiles.data.LocalTrack, isPlaying: Boolean, pos: Long, dur: Long) {
        main.post {
            if (LocalPlayer.current() !== t) return@post
            currentTrackId = localSpotifyUri(t)?.let(::trackIdOf).orEmpty()
            playing = isPlaying; positionMs = pos; positionAt = SystemClock.elapsedRealtime(); durationMs = dur
            bufferingState.value = false
            val uri = "local:${t.id}"
            val songChanged = _nowPlaying.value.uri != uri
            val shown = NowPlaying(uri = uri, contextUri = LocalPlayer.contextUri, contextName = LocalPlayer.contextName,
                provider = if (LocalPlayer.fromQueue()) "queue" else "")
            if (_nowPlaying.value.let { it.uri != uri || it.contextUri != shown.contextUri || it.contextName != shown.contextName || it.provider != shown.provider })
                _nowPlaying.value = shown
            val song = localSong(t, dur)
            val likeUri = localSpotifyUri(t)
            val liked = likeUri?.let { likedFor(it, library[it] ?: false) } ?: false
            // Its large cover, as a song Spotify plays shows (the queue keeps the small one).
            val cover = io.github.thake.soundnest.host.android.Covers.large(song.coverUri)
            boundState?.let { st ->
                st.likeState.value = liked
                st.updateSongState(cover, song.title, song.singer, isPlaying, song.id, 0, song.album)
            }
            pushNotification(song.copy(coverUri = cover), liked)
            if (songChanged) refreshCurrentLiked()
            trackListen("local:${t.id}", song, ListenHistory.Source.LOCAL, isPlaying)
        }
    }

    /** The Spotify uri of what the local player plays (null for a file of the device). */
    private fun localSpotifyUri(t: io.github.thake.soundnest.feature.localfiles.data.LocalTrack?): String? {
        t ?: return null
        return LocalPlayer.spotifyIdOf(t).takeIf { it.isNotBlank() }?.let { SPOTIFY_TRACK_PREFIX + it }
    }

    /** The uri a like of the song shown saves ("" for a file of the device or Audible audiobook). */
    private fun currentLikeUri(): String = when {
        io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayer.active.value -> ""
        LocalPlayer.active.value -> localSpotifyUri(LocalPlayer.current()).orEmpty()
        else -> pendingItemUri ?: _nowPlaying.value.uri
    }

    /**
     * The song shown can be liked: anything with a Spotify id; not a file of the device or Audible audiobook.
     * Read by the screens (state: recomposes as it changes).
     */
    val canLike: Boolean
        get() = (!LocalPlayer.active.value || !_nowPlaying.value.uri.startsWith("local:")) &&
            !io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayer.active.value &&
            !_nowPlaying.value.uri.startsWith("audible:")

    private fun localSong(t: io.github.thake.soundnest.feature.localfiles.data.LocalTrack, dur: Long = t.durationMs): SongsModel =
        SongsModel(id = ("local:" + t.id).hashCode() and 0x7fffffff, title = t.title, album = t.album,
            singer = t.artist, coverUri = t.artUri.toString(), url = "local:${t.uri}", durationMs = dur.toInt(), spotifyTrackId = "")

    /**
     * The local player's queue: [upcoming] are the songs after [current], with their list index as
     * uid; its first [queuedCount] are the ones the user queued ("Next in queue"), the rest the list's.
     */
    fun onLocalQueue(current: io.github.thake.soundnest.feature.localfiles.data.LocalTrack, upcoming: List<Pair<Int, io.github.thake.soundnest.feature.localfiles.data.LocalTrack>>, queuedCount: Int = 0) {
        main.post {
            if (!LocalPlayer.active.value) return@post
            val items = upcoming.map { (i, t) -> QueueItem(localSong(t), "local:${t.id}", i.toString(), LOCAL) }
            _queue.value = EngineQueue(current = localSong(current), queued = items.take(queuedCount), nextUp = items.drop(queuedCount))
        }
    }

    /** The song a real crossfade fades out, as the screens showed it when the fade began. */
    data class FadingSong(val title: String, val artist: String, val coverUri: String, val durationMs: Long)

    private val fadingOutState = mutableStateOf<FadingSong?>(null)
    /**
     * Set while the local player's crossfade overlaps two songs (none for Spotify's own fades,
     * which do not overlap): the players blend this one into the current song.
     */
    val fadingOut: State<FadingSong?> get() = fadingOutState

    /** [LocalPlayer]'s fade starts (just after its switch to the next song) or ends. */
    fun onLocalFade(on: Boolean) {
        // The new song's state is posted: what the screens hold is still the song going out.
        fadingOutState.value = if (!on) null else boundState?.let {
            FadingSong(it.title.value, it.singer.value, it.coverUri.value, durationMs)
        }
    }
    fun release() {}

    // ── Play-key helpers (url = "spotify:track:<id>|<search text>") ──

    private val featSearchPattern = Regex("""\s*[\(\[]\s*(feat|ft)\..*?[\)\]]""", RegexOption.IGNORE_CASE)

    fun buildSpotifyPlayQuery(spotifyTrackId: String, title: String, artist: String): String {
        val searchText = listOf(title.replace(featSearchPattern, "").trim(), artist)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        return if (spotifyTrackId.isBlank()) searchText else "$SPOTIFY_TRACK_PREFIX$spotifyTrackId|$searchText"
    }

    private fun spotifyTrackIdForPlayback(song: String): String? =
        if (song.startsWith(SPOTIFY_TRACK_PREFIX)) {
            song.removePrefix(SPOTIFY_TRACK_PREFIX).substringBefore('|').takeIf { it.isNotBlank() }
        } else {
            null
        }
}
