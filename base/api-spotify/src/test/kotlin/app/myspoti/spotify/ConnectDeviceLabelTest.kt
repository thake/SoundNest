/* SPDX-License-Identifier: GPL-3.0-only */
package app.myspoti.spotify

import app.myspoti.spotify.bridge.Bridge
import app.myspoti.spotify.bridge.BridgeHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class ConnectDeviceLabelTest {
    private class FakeConnectHost(
        private val connectDevicesJson: String? = null,
        private val fallbackServiceJson: String = "[]",
    ) : BridgeHost {
        override val messages = MutableSharedFlow<String>(extraBufferCapacity = 64)
        val commands = mutableListOf<String>()

        override suspend fun evaluate(script: String) {
            val m = Regex("""Myspoti\.call\((\d+), "([^"]+)", (.*)\)\.catch""", RegexOption.DOT_MATCHES_ALL).find(script) ?: return
            val id = m.groupValues[1]
            val cmd = m.groupValues[2]
            commands += cmd
            when (cmd) {
                "connect.devices" -> {
                    if (connectDevicesJson != null) {
                        messages.emit("""{"type":"result","id":$id,"ok":true,"value":$connectDevicesJson}""")
                    } else {
                        messages.emit("""{"type":"result","id":$id,"ok":false,"error":"unknown command connect.devices"}""")
                    }
                }
                "service.call" -> {
                    messages.emit("""{"type":"result","id":$id,"ok":true,"value":$fallbackServiceJson}""")
                }
                else -> messages.emit("""{"type":"result","id":$id,"ok":true,"value":null}""")
            }
        }
    }

    private lateinit var scope: CoroutineScope

    @BeforeEach
    fun setUp() {
        scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Unconfined)
    }

    @AfterEach
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `player-bridge connect_devices reads prototype getters and Alexa aliases lost by Object_keys`() {
        val node = findNodeBinary()
        assumeTrue(node != null, "Node runtime not found; skipping live JS VM check")
        val bridgeJs = checkNotNull(javaClass.getResource("/bridge/player-bridge.js")) { "player-bridge.js missing" }.readText()

        val probeScript = """
            const posted = [];
            globalThis.window = globalThis;
            globalThis.Document = class {};
            globalThis.HTMLMediaElement = class {};
            globalThis.Node = class {};
            globalThis.Window = class {};
            globalThis.MutationObserver = class { observe() {} disconnect() {} };
            globalThis.PerformanceObserver = class { observe() {} };
            globalThis.MyspotiHost = { postMessage: msg => posted.push(JSON.parse(msg)) };

            class ProtoConnectDevice {
              constructor(raw) { Object.defineProperty(this, '_raw', { value: raw, enumerable: false }); this.id = raw.id; }
              get name() { return this._raw.name; }
              get displayName() { return this._raw.displayName; }
              get deviceAliases() { return this._raw.deviceAliases; }
              get brandDisplayName() { return this._raw.brandDisplayName; }
              get modelDisplayName() { return this._raw.modelDisplayName; }
              get type() { return this._raw.type; }
              get isActive() { return this._raw.isActive; }
              get isLocal() { return this._raw.isLocal; }
              get volume() { return this._raw.volume; }
            }

            const fakeMap = new Map([
              [Symbol('ConnectDevicesAPI'), {
                getDevices: async () => [
                  new ProtoConnectDevice({
                    id: 'echo-1',
                    name: '',
                    displayName: '',
                    deviceAliases: { '0': { id: 'amzn1', name: 'Kitchen Alexa' } },
                    brandDisplayName: 'Amazon',
                    modelDisplayName: 'Echo',
                    type: 'SPEAKER',
                    isActive: true,
                    isLocal: false,
                    volume: 0.5,
                  }),
                  new ProtoConnectDevice({
                    id: 'sonos-1',
                    name: 'Playroom Sonos',
                    displayName: 'Playroom Sonos',
                    brandDisplayName: 'Sonos',
                    modelDisplayName: 'One',
                    type: 'SPEAKER',
                    isActive: false,
                    isLocal: false,
                    volume: 0.3,
                  }),
                ]
              }],
              [Symbol('PlayerAPI'), { getEvents: () => ({ addListener() {} }), getState: () => null, getQueue: () => null }],
              [Symbol('PlaybackAPI'), { getVolume: () => 1 }],
              [Symbol('LibraryAPI'), { getEvents: () => ({ addListener() {} }), contains: async () => [] }],
              [Symbol('PlatformAutoplayAPI'), {}],
              [Symbol('TokenProvider'), { getToken: async () => null }],
            ]);
            const mainEl = {
              '__reactContainer${'$'}test': {
                memoizedProps: {
                  value: {
                    getRegistry: () => ({ _map: fakeMap, resolve: k => fakeMap.get(k) }),
                    getGraphQLLoader: () => async () => ({}),
                  }
                }
              },
              querySelectorAll: () => [],
            };
            globalThis.document = {
              addEventListener() {},
              getElementById: id => (id === 'main' ? mainEl : null),
              body: mainEl,
            };
            globalThis.addEventListener = () => {};

            $bridgeJs
            (async () => {
              // Prove service.call (Object.keys) loses prototype getters while connect.devices preserves them
              await window.Myspoti.call(99, 'service.call', { service: 'ConnectDevicesAPI', method: 'getDevices', args: [] });
              const broken = posted.find(p => p.id === 99);
              if (broken && broken.value && broken.value[0] && broken.value[0].name) {
                throw new Error('Expected Object.keys() serialization to miss prototype getter');
              }
              await window.Myspoti.call(1, 'connect.devices', {});
              console.log(JSON.stringify(posted.find(p => p.id === 1)));
              process.exit(0);
            })().catch(e => { console.error(e); process.exit(1); });
        """.trimIndent()

        val proc = ProcessBuilder(node, "-e", probeScript).redirectErrorStream(true).start()
        val out = proc.inputStream.bufferedReader().readText().trim()
        val exit = proc.waitFor()
        assertEquals(0, exit, "Node script failed: $out")
        val line = out.lineSequence().last { it.startsWith("{") }
        val res = Json.parseToJsonElement(line).jsonObject
        assertEquals("true", res["ok"]?.toString(), "Expected connect.devices to succeed, got: $line")
        val arr = res["value"]!!.jsonArray
        assertEquals(2, arr.size)
        assertEquals("\"Kitchen Alexa\"", arr[0].jsonObject["name"]?.toString())
        assertEquals("\"Playroom Sonos\"", arr[1].jsonObject["name"]?.toString())
    }

    @Test
    fun `SpotifyDevices refresh uses connect_devices and resolves Alexa aliases brand model and human fallback`() = runTest {
        val payload = """
            [
              {"id":"d-direct","name":"Living Room Speaker","type":"SPEAKER","isActive":false,"isLocal":false,"volume":0.6},
              {"id":"d-alexa-map","name":"","aliases":{"0":{"id":"amzn1","name":"Emma Echo Dot"}},"type":"SPEAKER","isActive":true,"isLocal":false,"volume":0.4},
              {"id":"d-alexa-arr","name":"   ","device_aliases":[{"name":"Kitchen Echo Show"}],"type":"AVR","isActive":false,"isLocal":false,"volume":0.5},
              {"id":"d-brand","name":"","brandDisplayName":"Amazon","modelDisplayName":"Echo Pop","type":"SPEAKER","isActive":false,"isLocal":false},
              {"id":"d-dedup-brand","name":"","brand":"Sonos","model":"Sonos Roam","type":"SPEAKER","isActive":false,"isLocal":false},
              {"id":"d-anon","name":"","type":"CAST_AUDIO","isActive":false,"isLocal":false}
            ]
        """.trimIndent()
        val host = FakeConnectHost(connectDevicesJson = payload)
        val bridge = Bridge(host, scope)
        val devices = SpotifyDevices(bridge)

        devices.refresh()

        assertTrue("connect.devices" in host.commands, "Expected SpotifyDevices.refresh() to call connect.devices")
        val list = devices.devices.value
        assertEquals(6, list.size)
        assertEquals("Living Room Speaker", list[0].name)
        assertEquals("Emma Echo Dot", list[1].name)
        assertTrue(list[1].active)
        assertEquals("Kitchen Echo Show", list[2].name)
        assertEquals("Amazon Echo Pop", list[3].name)
        assertEquals("Sonos Roam", list[4].name)
        assertEquals("Speaker", list[5].name)
    }

    @Test
    fun `SpotifyDevices refresh falls back to ConnectDevicesAPI getDevices when bridge is older`() = runTest {
        val fallback = """[{"id":"d-1","displayName":"Fallback Alexa","type":"SPEAKER","isActive":false,"isLocal":false}]"""
        val host = FakeConnectHost(connectDevicesJson = null, fallbackServiceJson = fallback)
        val bridge = Bridge(host, scope)
        val devices = SpotifyDevices(bridge)

        devices.refresh()

        assertEquals(listOf("connect.devices", "service.call"), host.commands)
        assertEquals("Fallback Alexa", devices.devices.value.single().name)
    }

    private fun findNodeBinary(): String? {
        val candidates = buildList {
            System.getenv("PATH")?.split(File.pathSeparator)?.forEach { dir -> add(File(dir, "node")) }
            add(File(System.getProperty("user.home"), ".local/share/mise/installs/node/lts/bin/node"))
        }
        return candidates.firstOrNull { it.isFile && it.canExecute() }?.absolutePath
    }
}
