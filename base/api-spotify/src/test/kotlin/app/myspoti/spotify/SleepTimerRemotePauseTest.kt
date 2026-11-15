/* SPDX-License-Identifier: GPL-3.0-only */
package app.myspoti.spotify

import app.myspoti.engine.NativeEngine
import app.myspoti.spotify.bridge.Bridge
import app.myspoti.spotify.bridge.BridgeHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

class SleepTimerRemotePauseTest {
    private class RemoteConnectHost : BridgeHost {
        override val messages = MutableSharedFlow<String>(extraBufferCapacity = 64)
        val commands = CopyOnWriteArrayList<String>()
        @Volatile var localActive = false

        override suspend fun evaluate(script: String) {
            val m = Regex("""Myspoti\.call\((\d+), "([^"]+)", (.*)\)\.catch""", RegexOption.DOT_MATCHES_ALL).find(script) ?: return
            val id = m.groupValues[1]
            val cmd = m.groupValues[2]
            commands += cmd
            val value = when (cmd) {
                "media.state" -> """{"paused":${!localActive},"muted":false,"volume":1,"level":1,"own":1,"time":0,"readyState":4,"ended":false,"playerVolume":0.5,"active":$localActive}"""
                else -> "null"
            }
            messages.emit("""{"type":"result","id":$id,"ok":true,"value":$value}""")
        }

        suspend fun post(json: String) = messages.emit(json)
    }

    private class IdleNativeAudio : AccountAudio {
        override var on = true
        override val deviceId: String? = null
        override val now = MutableStateFlow<NativeEngine.Now?>(null)
        override val holdsPlayback: Boolean get() = now.value != null
        val calls = CopyOnWriteArrayList<String>()

        override suspend fun play(contextUri: String, trackUri: String?, index: Int?, positionMs: Long?, order: List<String>?, shuffle: Boolean?) {
            calls += "play"
        }
        override suspend fun pause() { calls += "pause" }
        override suspend fun resume() { calls += "resume" }
        override suspend fun seek(positionMs: Long) { calls += "seek:$positionMs" }
        override suspend fun next() { calls += "next" }
        override suspend fun previous() { calls += "previous" }
    }

    private val time = VirtualTime()
    private lateinit var scope: CoroutineScope
    private lateinit var host: RemoteConnectHost
    private lateinit var bridge: Bridge
    private lateinit var player: SpotifyPlayer
    private lateinit var nativeAudio: IdleNativeAudio

    @BeforeEach
    fun setUp() {
        scope = time.scope
        host = RemoteConnectHost()
        bridge = Bridge(host, scope, time::now)
        player = SpotifyPlayer(bridge, scope)
        nativeAudio = IdleNativeAudio()
        player.audio = nativeAudio
    }

    @AfterEach
    fun tearDown() {
        scope.cancel()
    }

    private suspend fun remoteAlexaPlaying() {
        host.localActive = false
        host.post("""{"type":"ready"}""")
        time.waitFor("ready", 4_000) { bridge.ready.value }
        val item = """{"kind":"track","uri":"spotify:track:Lullaby","uid":"u1","name":"Lullaby","durationMs":180000}"""
        host.post(
            """{"type":"state","state":{"track":$item,"contextUri":"spotify:playlist:Bedtime","next":null,"isPaused":false,"isBuffering":false,"positionMs":30000,"timestamp":${time.now()},"restrictions":{}}}"""
        )
        time.waitFor("remote state", 10_000) { player.state.value?.item?.name == "Lullaby" && player.remoteActive }
    }

    @Test
    fun `pauseSlowly pauses remote Alexa speaker via Web Player even when NativeAudio is enabled`() = time.test {
        remoteAlexaPlaying()

        val fadeJob = scope.launch { player.pauseSlowly(5_000) }
        delay(2_000)
        assertFalse("player.pause" in host.commands, "Remote speaker should not pause before sleep timer fade window ends")
        assertEquals(emptyList<String>(), nativeAudio.calls.toList(), "Idle NativeAudio must not swallow remote pause")

        delay(3_500)
        fadeJob.join()
        assertTrue("player.pause" in host.commands, "Expected player.pause sent to remote Alexa speaker after fade window")
        assertEquals(emptyList<String>(), nativeAudio.calls.toList())
    }

    @Test
    fun `manual pause resume and seek route to remote speaker when NativeAudio does not hold playback`() = time.test {
        remoteAlexaPlaying()

        player.pause()
        assertTrue("player.pause" in host.commands, "Expected remote pause on Web Player")
        assertEquals(emptyList<String>(), nativeAudio.calls.toList())

        player.seek(60_000)
        assertTrue("player.seek" in host.commands, "Expected remote seek on Web Player")
        assertEquals(emptyList<String>(), nativeAudio.calls.toList())
    }

    @Test
    fun `pauseSlowly waits fade duration before pausing local NativeAudio and cancels cleanly`() = time.test {
        nativeAudio.now.value = NativeEngine.Now(
            uri = "spotify:track:LocalSong",
            name = "LocalSong",
            artists = "Artist",
            album = "Album",
            imageUrl = null,
            durationMs = 180_000,
            playing = true,
            positionMs = 10_000,
            atMs = time.now(),
            contextUri = "spotify:playlist:Bedtime",
        )
        time.waitFor("local engine state", 4_000) { player.state.value?.item?.uri == "spotify:track:LocalSong" }

        val cancelled = scope.launch { player.pauseSlowly(5_000) }
        delay(2_000)
        cancelled.cancel()
        assertEquals(emptyList<String>(), nativeAudio.calls.toList(), "Cancelled sleep fade must keep local engine playing")

        val completed = scope.launch { player.pauseSlowly(5_000) }
        delay(2_000)
        assertEquals(emptyList<String>(), nativeAudio.calls.toList())
        delay(3_500)
        completed.join()
        assertEquals(listOf("pause"), nativeAudio.calls.toList(), "Completed sleep fade must pause local engine")
    }

    @Test
    fun `SpotifyDevices pause sends direct connect-state pause command from local device to remote speaker`() = time.test {
        val sent = mutableListOf<Pair<String, String>>()
        val devices = SpotifyDevices(
            bridge = bridge,
            sender = { method, url, body ->
                sent += "$method $url" to (body ?: "")
                200 to "{}"
            },
        ).apply { ownDeviceId = { "phone-dev-1" } }

        val ok = devices.pause("echo-bedroom-9")

        assertTrue(ok)
        assertEquals(1, sent.size)
        assertEquals(
            "POST https://spclient.wg.spotify.com/connect-state/v1/player/command/from/phone-dev-1/to/echo-bedroom-9",
            sent.single().first,
        )
        assertTrue(sent.single().second.contains(""""endpoint":"pause""""), "Expected pause command payload, got: ${sent.single().second}")
    }
}
