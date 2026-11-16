package io.github.thake.soundnest.cli

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.thake.soundnest.host.android.SoundNestRuntime
import io.github.thake.soundnest.data.api.Response
import io.github.thake.soundnest.data.entity.ContextPageModel
import io.github.thake.soundnest.di.Graph
import io.github.thake.soundnest.di.SongPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

/**
 * The command line's door (`adb shell am broadcast -a <package>.CLI -p <package> --es cmd … --es args64 …
 * --es req <id>`), see docs/cli.md and tools/myspoti-cli. Only the adb shell may send it (DUMP), the
 * manifest turns it on in debug builds only, and it answers nothing elsewhere. The reply goes
 * to `<external files>/cli/<req>.json` and, cut, to logcat under [TAG].
 */
class CliReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!enabled) return
        val cmd = intent.getStringExtra("cmd").orEmpty()
        val req = CliReply.safeRequestId(intent.getStringExtra("req")) ?: "last"
        val app = context.applicationContext
        val pending = goAsync()
        scope.launch {
            val reply = try {
                val command = CliParser.parse(cmd, CliParser.args(intent.getStringExtra("args"), intent.getStringExtra("args64")))
                CliReply.ok(cmd, run(app, command))
            } catch (e: CliError) {
                CliReply.error(cmd, e.message.orEmpty())
            } catch (t: Throwable) {
                CliReply.error(cmd, CliMask.text(t.message ?: t.toString()))
            }
            runCatching {
                val dir = File(app.getExternalFilesDir(null), "cli").apply { mkdirs() }
                val tmp = File(dir, "$req.json.tmp")
                tmp.writeText(reply)
                tmp.renameTo(File(dir, "$req.json"))
            }.onFailure { Log.w(TAG, "$req: reply not written: $it") }
            Log.i(TAG, "$req $cmd ${reply.take(3500)}")
            // 1: the receiver answered (an absent or disabled one leaves 0), how tools/myspoti-cli tells them apart.
            pending.resultCode = 1
            pending.resultData = if (reply.length < 1000) reply else null
            pending.finish()
        }
    }

    private suspend fun run(ctx: Context, c: CliCommand): JsonElement = when (c) {
        CliCommand.Status -> {
          val audio = withContext(Dispatchers.Main) { if (SoundNestRuntime.isStarted) SoundNestRuntime.spotify.player.audioInfo() else 0 to "" }
          onMain {
            val np = SongPlayer.nowPlaying.value
            buildJsonObject {
                put("package", ctx.packageName)
                put("version", io.github.thake.soundnest.BuildConfig.VERSION_NAME)
                put("versionCode", io.github.thake.soundnest.BuildConfig.VERSION_CODE)
                put("debug", io.github.thake.soundnest.BuildConfig.DEBUG)
                put("signedIn", ctx.getSharedPreferences("myspoti_prefs", Context.MODE_PRIVATE).getBoolean("LoggedIn", false))
                put("playing", SongPlayer.isPlaying())
                put("uri", np.uri)
                put("context", np.contextUri)
                put("positionMs", SongPlayer.getCurrentPosition())
                put("durationMs", SongPlayer.getDuration())
                val local = io.github.thake.soundnest.di.LocalPlayer.active.value
                put("player", if (local) "local" else "account")
                if (local) {
                    put("playWhenReady", io.github.thake.soundnest.di.LocalPlayer.playWhenReady())
                    put("playbackState", io.github.thake.soundnest.di.LocalPlayer.playbackState())
                    io.github.thake.soundnest.di.LocalPlayer.current()?.let { t ->
                        put("source", if (t.isRadio) "radio" else "file")
                    }
                }
                put("ui", nav.subscriptionCount.value > 0)
                if (SoundNestRuntime.isStarted) {
                    val page = SoundNestRuntime.spotify.player
                    val s = page.state.value
                    put("pageUri", s?.item?.uri ?: "")
                    put("pagePaused", s?.isPaused ?: true)
                    put("pagePositionMs", s?.positionAt(System.currentTimeMillis()) ?: 0L)
                    put("quality", page.streamingLevel)
                    put("bitrate", audio.first); put("codec", audio.second)
                    put("playsElsewhere", page.playsElsewhere.value)
                }
                SongPlayer.activeDevice.value?.let { put("activeDevice", it.name) }
            }
          }
        }
        is CliCommand.Play -> {
            val page = c.context?.let { pageOf(it) }
            val songs = page?.tracks?.takeIf { it.isNotEmpty() }
            onMain {
                page?.let { SongPlayer.registerContextName(it.uri, it.name) }
                SongPlayer.playSong(c.uri, contextUri = c.context, songs = songs)
            }
            val id = c.uri.substringAfterLast(':')
            if (c.context == null) JsonNull else buildJsonObject { put("songs", songs?.takeIf { l -> l.any { it.spotifyTrackId == id } }?.size ?: 0) }
        }
        is CliCommand.AutoMixSet -> onMain {
            c.on?.let { io.github.thake.soundnest.automix.AutoMix.set(it); io.github.thake.soundnest.di.LocalPlayer.autoMixChanged(it) }
            JsonPrimitive(io.github.thake.soundnest.di.LocalPlayer.autoMixState())
        }
        is CliCommand.AutoMixSegue -> onMain {
            c.on?.let { io.github.thake.soundnest.automix.AutoMix.changeSegue(it) }
            JsonPrimitive(io.github.thake.soundnest.automix.AutoMix.segue)
        }
        is CliCommand.JamJoin -> jamJson(SoundNestRuntime.api.jam.join(c.token))
        CliCommand.JamStatus -> { SoundNestRuntime.api.jam.refresh(); jamJson(SoundNestRuntime.api.jam.session.value) }
        CliCommand.JamLeave -> { SoundNestRuntime.api.jam.leave(); JsonNull }
        is CliCommand.Search -> {
            val t0 = android.os.SystemClock.elapsedRealtime()
            val pageReady = SoundNestRuntime.isStarted && SoundNestRuntime.spotify.ready.value
            val token = SoundNestRuntime.isStarted && SoundNestRuntime.spotify.http.hasToken()
            val steps = ArrayList<String>()
            var first = -1L
            io.github.thake.soundnest.data.api.Api(ctx).searchEverything(c.query).collect { r ->
                val ms = android.os.SystemClock.elapsedRealtime() - t0
                when (r) {
                    is io.github.thake.soundnest.data.api.Response.Success -> { if (first < 0) first = ms; steps += "${ms} ms: ${r.data.songs.size} songs, ${r.data.artists.size} artists, ${r.data.albums.size} albums, ${r.data.playlists.size} playlists" }
                    is io.github.thake.soundnest.data.api.Response.Error -> steps += "${ms} ms: error ${r.error}"
                    else -> Unit
                }
            }
            buildJsonObject {
                put("pageReadyAtStart", pageReady); put("tokenAtStart", token)
                put("firstResultsMs", first); put("totalMs", android.os.SystemClock.elapsedRealtime() - t0)
                put("steps", kotlinx.serialization.json.JsonArray(steps.map { kotlinx.serialization.json.JsonPrimitive(it) }))
                put("httpFellBack", SoundNestRuntime.spotify.httpGql.fellBack.get())
            }
        }
        CliCommand.AutoMixStatus -> onMain {
            buildJsonObject {
                put("state", io.github.thake.soundnest.di.LocalPlayer.autoMixState())
                put("journal", JsonArray(io.github.thake.soundnest.automix.AutoMix.journalLines().takeLast(30).map(::JsonPrimitive)))
            }
        }
        is CliCommand.AutoMixSettings -> onMain {
            val a = io.github.thake.soundnest.automix.AutoMix
            when (c.name) {
                null -> Unit
                "enabled" -> { val on = c.value == "on"; a.set(on); io.github.thake.soundnest.di.LocalPlayer.autoMixChanged(on) }
                "segue" -> a.changeSegue(c.value == "on")
                "orpheon" -> a.changeOrpheon(c.value == "on")
                "ideas" -> io.github.thake.soundnest.automix.segue.MixIdea.values().forEach { a.changeIdea(it, c.value == "on") }
                "session-dj" -> a.changeSessionDj(c.value == "on")
                "album-order" -> a.changeAlbumOrder(c.value == "on")
                "podcasts" -> a.changeMixPodcasts(c.value == "on")
                "harmonic" -> a.changeHarmonic(c.value == "on")
                "eq" -> a.changeDjEq(c.value == "on")
                "show" -> a.changeShowInPlayer(c.value == "on")
                "mobile-data" -> a.changeAnalyzeOnMobileData(c.value == "on")
                "on-next" -> a.changeNextStyle(listOf("cut", "quick", "full").indexOf(c.value))
                "style" -> a.changeStyle(listOf("blend", "cut", "fade").indexOf(c.value))
                "length" -> a.changeMixLength(listOf("short", "balanced", "long").indexOf(c.value))
                "tempo" -> a.changeTempoRange(listOf("strict", "normal", "wide").indexOf(c.value))
                "pulse" -> a.changePulseLevel(listOf("off", "subtle", "strong").indexOf(c.value))
                "duration-min" -> if (!a.changeDurationBounds(c.value!!.toInt(), a.maxDurationSeconds))
                    throw CliError("automix.settings: min duration must be 0..90 seconds and not exceed a configured max")
                "duration-max" -> if (!a.changeDurationBounds(a.minDurationSeconds, c.value!!.toInt()))
                    throw CliError("automix.settings: max duration must be 0..90 seconds and at least the configured min; 0 means unlimited")
                "performance-priority" -> a.changePerformancePriority(c.value == "on")
                else -> io.github.thake.soundnest.automix.segue.MixIdea.parse(c.name)?.let { a.changeIdea(it, c.value == "on") }
            }
            AutoMixSettingsSnapshot(
                enabled = a.on, segue = a.segue, sessionDj = a.sessionDj, albumOrder = a.albumOrder,
                podcasts = a.mixPodcasts, harmonic = a.harmonic, eq = a.djEq, show = a.showInPlayer,
                mobileData = a.analyzeOnMobileData,
                onNext = listOf("cut", "quick", "full").getOrElse(a.nextStyle) { "cut" },
                style = listOf("blend", "cut", "fade").getOrElse(a.style) { "blend" },
                length = listOf("short", "balanced", "long").getOrElse(a.mixLength) { "balanced" },
                tempo = listOf("strict", "normal", "wide").getOrElse(a.tempoRange) { "normal" },
                pulse = listOf("off", "subtle", "strong").getOrElse(a.pulseLevel) { "off" },
                durationMin = a.minDurationSeconds,
                durationMax = a.maxDurationSeconds,
                performancePriority = a.performancePriority,
                orpheon = a.orpheon,
                ideas = io.github.thake.soundnest.automix.segue.MixIdea.values().associate { it.wire to a.hasIdea(it) },
            ).json()
        }
        CliCommand.AutoMixNext -> onMain {
            if (!io.github.thake.soundnest.di.LocalPlayer.active.value) throw CliError("automix.next: the local player is not active")
            if (!io.github.thake.soundnest.automix.AutoMix.on) throw CliError("automix.next: AutoMix is off")
            if (!io.github.thake.soundnest.automix.AutoMix.mixOnNext) throw CliError("automix.next: on-next is set to cut")
            // The same route as the player control: SongPlayer delegates local playback to LocalPlayer.next(),
            // which calls mixOnSkip() and prepares the next deck when a transition can be made.
            SongPlayer.next(by = "CLI AutoMix")
            JsonPrimitive(io.github.thake.soundnest.di.LocalPlayer.autoMixState())
        }
        is CliCommand.AutoMixFiles -> {
            val all = io.github.thake.soundnest.data.local.LocalMusic.tracks(ctx, refresh = true)
            // Each name: a piece of "folder + title" (e.g. "Spotiflaccc/24").
            val picked = c.names.map { n -> all.firstOrNull { (it.folder + it.title).contains(n, ignoreCase = true) }
                ?: throw CliError("automix.files: no phone song matches $n") }
            onMain { io.github.thake.soundnest.di.LocalPlayer.play(picked, 0, context = "AutoMix test"); JsonArray(picked.map { JsonPrimitive(it.title) }) }
        }
        is CliCommand.AutoMixSeek -> onMain { io.github.thake.soundnest.di.LocalPlayer.seekTo(c.ms); JsonNull }
        CliCommand.Pause -> onMain { SongPlayer.pause(by = "cli"); JsonNull }
        CliCommand.Devices -> {
            val d = SoundNestRuntime.spotify.devices
            runCatching { d.refresh() }
            buildJsonArray {
                for (x in d.devices.value) add(buildJsonObject {
                    put("id", x.id); put("name", x.name); put("type", x.type); put("active", x.active); put("local", x.local)
                    x.volume?.let { put("volume", (it * 100).toInt()) }
                })
            }
        }
        is CliCommand.Transfer -> { SoundNestRuntime.spotify.devices.transfer(c.deviceId); JsonNull }
        is CliCommand.Volume -> onMain { SongPlayer.setRemoteVolume(c.level); JsonPrimitive(c.level) }
        CliCommand.Resume -> onMain { SongPlayer.play(by = "cli"); JsonNull }
        CliCommand.Next -> onMain { SongPlayer.next(); JsonNull }
        CliCommand.Previous -> onMain { SongPlayer.previous(); JsonNull }
        is CliCommand.Nav -> {
            if (nav.subscriptionCount.value == 0) throw CliError("nav: the app's screen is not open (adb shell monkey -p ${ctx.packageName} 1)")
            nav.emit(c.route)
            JsonPrimitive(c.route)
        }
    }

    /**
     * The playlist, album or Liked Songs [uri], read to its last song (or what came within
     * [READ_MS]); null when it cannot be read. Seen in review: stopped at its first answer, a big
     * playlist gave its first 100 songs and registered only those as the list (SongPlayer.rowIndex).
     */
    private suspend fun pageOf(uri: String): ContextPageModel? {
        val api = Graph.api
        val id = uri.substringAfterLast(':')
        val flow = when {
            uri.startsWith("spotify:album:") -> api.getAlbumPage(id)
            uri == "spotify:collection:tracks" -> api.getLikedPage()
            uri.startsWith("spotify:playlist:") -> api.getPlaylistPage(id)
            else -> return null
        }
        var read: ContextPageModel? = null
        runCatching { withTimeoutOrNull(READ_MS) { flow.first { it is Response.Error || (it as? Response.Success)?.data?.also { p -> read = p }?.complete == true } } }
        return read
    }

    private fun jamJson(s: io.github.thake.soundnest.api.JamSession?): JsonElement = if (s == null) JsonNull else buildJsonObject {
        put("id", s.id); put("token", s.joinToken ?: ""); put("host", s.isHost); put("queueOnly", s.queueOnly); put("listensHere", s.listensHere)
        put("members", JsonArray(s.members.map { m -> JsonPrimitive(m.name + if (m.host) " (host)" else "") }))
    }

    private suspend fun <T> onMain(block: () -> T): T = withContext(Dispatchers.Main) { block() }

    companion object {
        const val TAG = "SoundNestCli"

        /** The error of a command this build does not have (tools/myspoti-cli exits with 5 on it). */
        const val DISABLED = "disabled in this build"

        /** How long `play` waits for its context's songs (Android Auto's own wait), within the CLI's 40 s. */
        private const val READ_MS = 15_000L

        /** Debug builds only; the manifest leaves the receiver off in the others too. */
        val enabled: Boolean get() = io.github.thake.soundnest.BuildConfig.DEBUG

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** `nav <route>`: the app's screen navigates there (App collects it). */
        val nav = MutableSharedFlow<String>(extraBufferCapacity = 1)
    }
}
