/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.localfiles.automix.engine

import io.github.thake.soundnest.automix.*
import io.github.thake.soundnest.automix.segue.*

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.thake.soundnest.core.di.Graph
import io.github.thake.soundnest.feature.localfiles.automix.settings.AutoMixSettings
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * AutoMix: DJ-style transitions between the local player's songs (the phone's files).
 * Spotify songs play in the Web Player, whose audio the app never has.
 *
 * Each song is analysed once in the background (see [Analyzer]) and the result kept in the cache;
 * [planFor] gives the transition between two analysed songs. LocalPlayer plays it (see its AutoMix part).
 * User-configurable AutoMix preferences live in [AutoMixSettings].
 */
object AutoMix {
    /**
     * The planner's settings from [AutoMixSettings]. Mix length in bars: short 4–8, balanced 8–16 (as
     * the music says), long 12–32 (up to 90 seconds); the lower end is a preference (a shorter mix only
     * when no longer one fits the songs), the upper one a limit. A plain fade lasts 4, 8 or 14 s.
     */
    private fun configuredDurationBounds() = DurationBounds(
        minSeconds = AutoMixSettings.minDurationSeconds.toDouble(),
        maxSeconds = if (AutoMixSettings.maxDurationSeconds == 0) Double.POSITIVE_INFINITY else AutoMixSettings.maxDurationSeconds.toDouble(),
    )

    fun settings(playbackRate: Double = io.github.thake.soundnest.core.playback.LocalPlayer.rate().toDouble()): MixPlanner.Settings {
        val mixLength = AutoMixSettings.mixLength
        val tempoRange = AutoMixSettings.tempoRange
        val style = AutoMixSettings.style
        return MixPlanner.Settings(
            maxStretch = doubleArrayOf(0.04, 0.12, 0.16)[tempoRange],
            maxSec = if (mixLength == 2) 90.0 else 48.0,
            lengthBias = mixLength - 1,
            style = arrayOf(MixPlan.Style.BLEND, MixPlan.Style.CUT, MixPlan.Style.FADE)[style],
            minBars = intArrayOf(4, 8, 12)[mixLength],
            maxBars = intArrayOf(8, 16, 32)[mixLength],
            keyWeight = if (AutoMixSettings.harmonic) 2.0 else 0.0,
            smoothSec = doubleArrayOf(4.0, 8.0, 14.0)[mixLength],
            filterSec = doubleArrayOf(6.0, 10.0, 14.0)[mixLength],
            djMode = AutoMixSettings.sessionDj,
            durationBounds = configuredDurationBounds().forPlaybackRate(playbackRate),
        )
    }

    /** The tempo gap the Orpheon planner may close when the listener asks for the narrowest range. */
    private const val SOUNDNEST_NARROW_TEMPO = 0.06

    /** The policy's decision for two analysed songs. The listener's settings may only tighten its limits. */
    fun decide(
        a: TrackAnalysis,
        b: TrackAnalysis,
        earliestExit: Double,
        sameAlbum: Boolean,
        listener: Float,
        genreA: GenreFamily? = null,
        genreB: GenreFamily? = null,
    ): SegueDecision {
        val s = settings()
        val orpheon = AutoMixSettings.orpheon
        val tempoRange = AutoMixSettings.tempoRange
        val mixLength = AutoMixSettings.mixLength
        val d = SeguePolicy.decide(
            SegueRequest(
                a,
                b,
                (earliestExit * 1e6).toLong(),
                listenerRate = (listener * SegueFormat.RATE_NOMINAL).toLong(),
                // The Orpheon planner ramps both songs and keeps its own tempo limits (see SoundNestTuning): the
                // listener's range, made for one song held at a fixed speed, only narrows it at its smallest.
                maxRatePpm = if (orpheon && tempoRange > 0) null else ((if (orpheon) SOUNDNEST_NARROW_TEMPO else s.maxStretch) * 1e6).toLong(),
                maxBars = s.maxBars,
                eq = AutoMixSettings.djEq,
                harmonic = AutoMixSettings.harmonic,
                sameAlbum = sameAlbum,
                durationBounds = configuredDurationBounds(),
                genreA = genreA,
                genreB = genreB,
                minBars = s.minBars,
                fadeSec = doubleArrayOf(6.0, 10.0, 14.0)[mixLength],
                tuning = SegueTuning(
                    planner = if (orpheon) SegueTuning.PLANNER_SOUNDNEST else SegueTuning.PLANNER_COMPACT,
                    ideas = if (orpheon) AutoMixSettings.ideas else 0,
                ),
            ),
        )
        val plan = d.plan ?: return d
        if (SegueDeck.playable(plan)) return d
        return SegueDecision(
            SegueLevel.NORMAL,
            null,
            d.attempts + SegueAttempt(d.level, SegueCause.INVALID_PLAN, "the engine cannot play it"),
        )
    }

    // ── Why a song is not mixed (for the screens) ──

    /** Why the next transition is not an AutoMix one. */
    enum class Reason {
        /** AutoMix is off. */
        Off,
        /** A Spotify song: it plays in the Web Player, whose audio the app never has. */
        SpotifyStream,
        /** The songs play on a Cast device. */
        Cast,
        /** A podcast episode or an audiobook, with "Mix podcasts" off. */
        Podcast,
        /** Two songs that follow each other on their album, with "Keep albums whole" on. */
        Album,
        /** A stream not analysed on mobile data ("Analyze on mobile data" off, or the data saver). */
        MobileData,
        /** Not analysed in time while saving energy (the phone's saver, the app's, or a low battery). */
        Frugal,
        /** Not analysed in time, or the analysis failed: the songs change with a plain fade. */
        NotAnalyzed,
    }

    /** The reason LocalPlayer found for the next pair (null: it is mixed, or still being analysed). */
    internal val pairReason = mutableStateOf<Reason?>(null)

    /**
     * Why the song playing does not mix into the next one, null when it does (or will, once
     * analysed). Compose state: a screen reading it redraws when it changes.
     */
    fun whyNot(): Reason? = when {
        !AutoMixSettings.on -> Reason.Off
        io.github.thake.soundnest.feature.speakers.cast.CastPlayback.device.value != null || io.github.thake.soundnest.feature.speakers.cast.CastPlayback.active -> Reason.Cast
        !io.github.thake.soundnest.core.playback.LocalPlayer.active.value -> Reason.SpotifyStream
        else -> pairReason.value
    }

    /** Why [uri] has no analysis and will get none for now (null: ready, or under way). */
    fun missing(uri: Uri): Reason? = synchronized(memory) { refused[requestId(uri)] }

    internal fun clearMobileDataRefusals() {
        synchronized(memory) { refused.values.removeAll { it == Reason.MobileData } }
    }

    // ── Journal (last lines, for the command line and the stats sheet) ──

    private val lines = ArrayDeque<String>()
    /** Immutable event snapshot for Compose readers; updated only when a journal event arrives. */
    var journalSnapshot by mutableStateOf<List<String>>(emptyList())
        private set
    fun journal(line: String) {
        io.github.thake.soundnest.host.android.Logbook.log("automix: $line")
        synchronized(lines) {
            lines.addLast(line)
            while (lines.size > 60) lines.removeFirst()
            journalSnapshot = lines.toList()
        }
    }
    fun journalLines(): List<String> = synchronized(lines) { lines.toList() }

    // ── Analysis ──

    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "automix").apply { priority = Thread.MIN_PRIORITY; isDaemon = true } }
    private val memory = object : LinkedHashMap<String, TrackAnalysis>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TrackAnalysis>?) = size > 24
    }
    private val asked = HashSet<String>()
    private val retry = AnalysisRetry()
    private val fileVersions = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val baseKeys = java.util.concurrent.ConcurrentHashMap<String, String>()
    @Volatile private var cacheEpoch = 0L
    /** Songs without an analysis for now, and why (shown through [missing]). */
    private val refused = HashMap<String, Reason>()
    @Volatile private var wanted: Set<String> = emptySet()

    /** The analysis of [uri] if it is ready (memory or cache), else null. */
    fun analysis(uri: Uri): TrackAnalysis? {
        val key = key(uri)
        synchronized(memory) { memory[key]?.let { return it } }
        return null
    }

    /**
     * Asks for the analysis of [uris] (the song playing and the next ones), dropping the asks for
     * any other song; [ready] runs on the worker when one is done.
     */
    fun request(uris: List<Uri>, ready: () -> Unit) {
        // The song playing and the next one; the one after only while charging (skipped or
        // replaced before it plays, it was often analysed for nothing: about 10 J a song).
        // Cached answers for B/C are loaded before A's expensive analysis.
        val lookahead = AnalysisCache.lookahead(charging = charging(), frugal = frugal())
        val songs = uris.distinctBy(::requestId).take(if (AutoMixSettings.performancePriority) minOf(lookahead, 2) else lookahead)
        wanted = songs.map(::requestId).toSet()
        val epoch = cacheEpoch
        val jobs = songs.filter { uri ->
            val id = requestId(uri)
            synchronized(memory) { retry.allowed(key(uri), android.os.SystemClock.elapsedRealtime()) && asked.add(id) }
        }
        if (jobs.isEmpty()) return
        worker.execute {
            val pending = ArrayList<Uri>()
            try {
                for (uri in jobs) {
                    if (epoch != cacheEpoch || requestId(uri) !in wanted) continue
                    refreshFileVersion(uri)
                    val k = key(uri)
                    if (synchronized(memory) { memory.containsKey(k) }) continue
                    val cached = load(k)
                    if (cached != null) { synchronized(memory) { memory[k] = cached; refused -= requestId(uri) }; ready() }
                    else pending += uri
                }
                if (pending.isNotEmpty()) waitForThePage()
                for (uri in pending) {
                    val id = requestId(uri)
                    if (epoch != cacheEpoch || id !in wanted) continue
                    val result = analyse(uri, epoch)
                    if (result != null && epoch == cacheEpoch && id in wanted && result.first == key(uri)) {
                        synchronized(memory) { memory[result.first] = result.second; refused -= id; retry.succeeded(result.first) }
                        ready()
                    }
                }
            } finally { synchronized(memory) { jobs.forEach { asked -= requestId(it) } } }
        }
    }

    @Volatile private var pageWaited = false

    /**
     * A cold start: the player's page loads first. A mix is planned long before it is needed, and two
     * songs decoded at once with it took the page from about 3 s to about 20 s on the test phone.
     */
    private fun waitForThePage() {
        if (pageWaited) return
        val end = android.os.SystemClock.elapsedRealtime() + PAGE_WAIT_MS
        while (android.os.SystemClock.elapsedRealtime() < end && !runCatching { io.github.thake.soundnest.host.android.SoundNestRuntime.api.ready.value }.getOrDefault(false)) Thread.sleep(250)
        pageWaited = true
    }

    private const val PAGE_WAIT_MS = 25_000L

    private fun analyse(uri: Uri, epoch: Long): Pair<String, TrackAnalysis>? {
        val id = requestId(uri)
        val cancelled = { epoch != cacheEpoch || id !in wanted }
        val cacheKey = key(uri)
        try {
            if (cancelled()) return null
            val t0 = System.currentTimeMillis()
            val pcm = TrackDecoder.decode(uri, cancelled)
            val t1 = System.currentTimeMillis()
            // On battery 3: measured on the S24, 5 made the same song's analysis slower (7.6 s against 5.5 s),
            // the extra cores being small ones and the next song's decode running meanwhile.
            val threads = if (AutoMixSettings.performancePriority) 1 else if (frugal()) 2 else if (charging()) THREADS else minOf(THREADS, 3)
            val a = Analyzer.analyze(pcm.mid, pcm.side, model(), cancelled, threads, pcm.length)
            if (cancelled() || cacheKey != key(uri)) return null
            save(cacheKey, a)
            journal(String.format("analysed %s: %.1f BPM%s, %d bars, key %d, decode %d ms (bytes %d ms, %.1f MB, convert %d ms, %d restarts), analysis %d ms on %d threads", label(uri), a.bpm,
                if (a.steady) " steady" else (if (a.tail != null) " (tail grid)" else " (no grid)"), a.bars, a.key, t1 - t0, pcm.readMs, pcm.bytes / 1e6, pcm.convertMs, pcm.restarts,
                System.currentTimeMillis() - t1, threads) +
                (if (a.note.isNotEmpty()) " | grids refused: ${a.note}" else ""))
            return cacheKey to a
        } catch (e: Exception) {
            // MediaExtractor can wrap the data source's cancellation IOException.
            if (cancelled() || e is InterruptedException) return null
            synchronized(memory) { retry.failed(cacheKey, android.os.SystemClock.elapsedRealtime()); refused[id] = Reason.NotAnalyzed }
            journal("analysis deferred for ${label(uri)}: ${e.javaClass.simpleName} ${e.message?.take(120)}")
            return null
        } catch (e: OutOfMemoryError) {
            // The song's signals are this thread's own (dropped with this frame): the analysis
            // is given up, the app goes on. Reported: this error, uncaught here, closed the app.
            synchronized(memory) { retry.failed(cacheKey, android.os.SystemClock.elapsedRealtime()); refused[id] = Reason.NotAnalyzed }
            journal("analysis given up for ${label(uri)}: out of memory (heap ${Runtime.getRuntime().maxMemory() / 1_000_000} MB)")
            return null
        }
    }

    private fun charging(): Boolean =
        runCatching { Graph.app.getSystemService(android.os.BatteryManager::class.java).isCharging }.getOrDefault(false)

    /**
     * Saving energy: the app's own energy saver or its data saver applying, the phone's battery
     * saver, or under 20 % and unplugged.
     */
    internal fun frugal(): Boolean = io.github.thake.soundnest.feature.system.settings.EnergySettings.state.value.manualSaver || io.github.thake.soundnest.feature.system.settings.DataSaver.active || runCatching {
        val power = Graph.app.getSystemService(android.os.PowerManager::class.java)
        val battery = Graph.app.getSystemService(android.os.BatteryManager::class.java)
        power.isPowerSaveMode || (!battery.isCharging && battery.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) in 0 until 20)
    }.getOrDefault(false)

    /** Cores for one analysis, plugged in: all but two (the player and the screen keep theirs); three on battery, two when saving it. */
    private val THREADS = (Runtime.getRuntime().availableProcessors() - 2).coerceIn(2, 6)

    private var beatThis: BeatThis? = null
    private var modelFailed = false

    /** Beat This, loaded once on the worker (null if it cannot run here: the onsets then decide alone). */
    private fun model(): BeatThis? {
        if (beatThis != null || modelFailed) return beatThis
        return try {
            BeatThis(Graph.app.assets.open("automix/beat-this-small0-int8.onnx").use { it.readBytes() }, THREADS).also { beatThis = it }
        } catch (e: Throwable) {
            modelFailed = true
            journal("Beat This unavailable: ${e.javaClass.simpleName} ${e.message?.take(120)}")
            null
        }
    }

    // ── Plans ──

    fun planFor(a: TrackAnalysis, b: TrackAnalysis): MixPlan? = MixPlanner.planBounded(a, b, settings())
    fun planAfter(a: TrackAnalysis, b: TrackAnalysis, earliestExit: Double): MixPlan? = MixPlanner.planAfter(a, b, earliestExit, settings())

    // ── Cache ──

    /**
     * In the app's files, not its cache: Android empties a cache when storage runs low, and every
     * song was then decoded and analysed again. Bounded instead ([AnalysisCache.MAX_BYTES], the
     * least recently used go first); the old cache folder moves here once.
     */
    private fun dir(): File {
        val d = File(Graph.app.filesDir, "automix4")
        if (!d.exists()) {
            val old = File(Graph.app.cacheDir, "automix4")
            if (!(old.isDirectory && old.renameTo(d))) d.mkdirs()
        }
        return d
    }

    private fun load(key: String): TrackAnalysis? = runCatching {
        File(dir(), key).takeIf { it.exists() }?.let { f ->
            // Used now: the last to go when the cache is trimmed.
            f.setLastModified(System.currentTimeMillis())
            f.inputStream().buffered().use { TrackAnalysis.read(it) }
        }
    }.getOrNull()

    private fun save(key: String, a: TrackAnalysis) {
        runCatching {
            val f = File(dir(), key)
            val tmp = File(dir(), "$key.tmp")
            tmp.outputStream().buffered().use { a.write(it) }
            tmp.renameTo(f)
            dir().listFiles()?.let { files ->
                AnalysisCache.toTrim(files.map { AnalysisCache.Entry(it.name, it.length(), it.lastModified()) })
                    .forEach { File(dir(), it).delete() }
            }
        }
    }

    private fun requestId(uri: Uri): String = uri.toString()

    /** No file I/O or large manifest hashing on the player/UI thread. */
    fun key(uri: Uri): String {
        val id = requestId(uri)
        return fileVersions[id] ?: baseKeys.getOrPut(id) { digest(id) }
    }

    private fun digest(id: String): String = MessageDigest.getInstance("SHA-1").digest(id.toByteArray())
        .joinToString("") { "%02x".format(it) }.take(24)

    /** Only the worker stats files; replacing an existing file invalidates its analysis. */
    private fun refreshFileVersion(uri: Uri) {
        if (uri.scheme == "file") {
            val file = File(uri.path ?: return)
            fileVersions[requestId(uri)] = digest(AnalysisIdentity.file(uri.toString(), file.length(), file.lastModified()))
        } else if (uri.scheme == "content") {
            runCatching {
                Graph.app.contentResolver.query(uri, arrayOf(android.provider.MediaStore.MediaColumns.SIZE, android.provider.MediaStore.MediaColumns.DATE_MODIFIED), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) fileVersions[requestId(uri)] = digest(AnalysisIdentity.file(uri.toString(), cursor.getLong(0), cursor.getLong(1)))
                }
            }
        }
    }

    fun cancelRequests() { wanted = emptySet() }

    fun sourcesChanged() {
        synchronized(memory) { retry.clear(); refused.clear() }
    }

    private fun label(uri: Uri) = uri.lastPathSegment?.takeLast(48).orEmpty()

    /** Off the main thread: every song analysed is dropped (they are analysed again when played). */
    fun clearCache() {
        cacheEpoch++
        synchronized(memory) { memory.clear(); retry.clear(); refused.clear() }
        fileVersions.clear()
        dir().listFiles()?.forEach { it.delete() }
    }

    /** The space the analyses take on disk, in bytes (off the main thread). */
    fun cacheBytes(): Long = dir().listFiles()?.sumOf { it.length() } ?: 0L
}
