package io.github.thake.soundnest.di

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonObject

/**
 * Debug builds only: the playback test (tools/playback_test.py) sends the app's own
 * commands, as a tap would, with
 * `adb shell am broadcast -a io.github.thake.soundnest.TEST -p app.orpheon.beta.dev --es cmd <cmd> ...`.
 * cmd: play (--es track <uri> --es context <uri>), resume, pause, next, previous, seek (--el ms <n>).
 * cmd: screenon (--ez on false to release) holds the screen on for a long run: One UI does not
 * honour the huge `screen_off_timeout` set over adb, the window flag does.
 * cmd: uitrace (--ei secs <n>, default 30) logs, 5 times a second under the tag MspTrace, what the
 * app shows (position, duration, playing, title, cover) next to what the page plays, so a run
 * can tell a jumping bar or a stale title/cover from the engine's own state (tools/qa.py --ui).
 * cmd: crossfade (--ei secs <0..12>) sets the crossfade between songs, as the Settings slider does.
 * cmd: gql (--es op <operation> --es vars <json>) runs a persisted operation through the app's own
 * Gql layer and logs the raw response: learning a server shape from the phone's real account beats
 * guessing one from a bundle.
 * cmd: native-play (--es arg <spotify uri>, --ez l3 true), native-pause, native-resume, native-seek
 * (--es arg <ms>), native-next (--es arg [uri]), native-prev, native-transfer, native-stop,
 * native-status, native-log (--es arg [lines]): the native engine headless, see NativeAudio.
 */
class TestControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val cmd = intent.getStringExtra("cmd").orEmpty()
        Log.i("MspPlayer", "test command $cmd")
        // The native engine (debug only): the reply goes back as the broadcast's result data.
        if (cmd.startsWith("native-")) {
            resultCode = 1
            resultData = NativeAudio.command(context, cmd, intent)
            return
        }
        when (cmd) {
            "gql" -> {
                val op = intent.getStringExtra("op").orEmpty()
                val variables = intent.getStringExtra("vars").orEmpty().ifBlank { "{}" }
                io.github.thake.soundnest.host.android.SoundNestRuntime.scope.launch {
                    runCatching {
                        io.github.thake.soundnest.host.android.SoundNestRuntime.spotify.raw.gql(
                            op,
                            io.github.thake.soundnest.spotify.bridge.Bridge.json.parseToJsonElement(variables).jsonObject,
                        )
                    }.onSuccess { Log.i("MspPlayer", "gql $op vars=$variables -> ${it.toString().take(4000)}") }
                        .onFailure { Log.i("MspPlayer", "gql $op vars=$variables failed: $it") }
                }
            }
            "play" -> SongPlayer.playSong(intent.getStringExtra("track").orEmpty(), contextUri = intent.getStringExtra("context"))
            "resume" -> SongPlayer.play()
            "pause" -> SongPlayer.pause()
            "next" -> SongPlayer.next()
            // The player's own "next" button and the mini player's swipe.
            "swipenext" -> SongPlayer.swipeNext()
            "previous" -> SongPlayer.previous()
            "crossfade" -> io.github.thake.soundnest.data.preferences.CrossfadePref.set(context, intent.getIntExtra("secs", 0))
            "uitrace" -> {
                val secs = intent.getIntExtra("secs", 30).coerceIn(1, 600)
                io.github.thake.soundnest.host.android.SoundNestRuntime.scope.launch(kotlinx.coroutines.Dispatchers.Main) {
                    val song = Graph.currentSongState
                    val end = android.os.SystemClock.elapsedRealtime() + secs * 1000L
                    while (android.os.SystemClock.elapsedRealtime() < end) {
                        val now = System.currentTimeMillis()
                        val e = io.github.thake.soundnest.host.android.SoundNestRuntime.spotify.player.state.value
                        Log.i("MspTrace", "t=${android.os.SystemClock.elapsedRealtime()} ui_pos=${SongPlayer.getCurrentPosition()} " +
                            "ui_dur=${SongPlayer.getDuration()} ui_play=${SongPlayer.isPlaying()} ui_uri=${SongPlayer.nowPlaying.value.uri} " +
                            "ui_cover=${song.coverUri.value.substringAfterLast('/')} eng_uri=${e?.item?.uri} " +
                            "eng_pos=${e?.positionAt(now)} eng_paused=${e?.isPaused} eng_buf=${e?.isBuffering} " +
                            "ui_title=${song.title.value} | eng_title=${e?.item?.name}")
                        kotlinx.coroutines.delay(200)
                    }
                    Log.i("MspTrace", "end")
                }
            }
            "seek" -> SongPlayer.seekTo(intent.getLongExtra("ms", 0L))
            "screenon" -> context.getSharedPreferences("myspoti_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("keep_screen_on", intent.getBooleanExtra("on", true)).apply()
            // Debug builds: the same report the Settings row writes, so an unattended run can check
            // that the export really produces a readable file (and where it lands).
            "diagnose" -> runCatching {
                val file = io.github.thake.soundnest.host.android.Logbook.export(context)
                Log.i("MspPlayer", "diagnostic report: ${file.absolutePath} (${file.length()} bytes)")
            }.onFailure { Log.i("MspPlayer", "diagnostic report failed: $it") }
            "top" -> io.github.thake.soundnest.host.android.SoundNestRuntime.scope.launch {
                io.github.thake.soundnest.api.TimeRange.entries.forEach { r ->
                    runCatching { io.github.thake.soundnest.host.android.SoundNestRuntime.api.users.top(r, 50) }
                        .onSuccess { Log.i("MspPlayer", "top $r -> artists=${it.artists.size}/${it.artistTotal} tracks=${it.tracks.size}/${it.trackTotal}") }
                        .onFailure { Log.i("MspPlayer", "top $r failed: $it") }
                }
            }
            // Debug: how many local audio files the app would list (Local Files check without the UI).
            "localfiles" -> Thread {
                val n = runCatching { kotlinx.coroutines.runBlocking { io.github.thake.soundnest.data.local.LocalMusic.tracks(context, refresh = true).size } }
                Log.i("MspPlayer", "local files: ${n.getOrElse { "failed: $it" }} track(s)")
            }.start()
        }
    }
}
