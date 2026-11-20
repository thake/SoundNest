/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class ClusterAliasesTest {
    @Test
    fun `Cluster parse resolves Alexa device_aliases map and preserves aliases list`() {
        val raw = """
            {
              "cluster": {
                "active_device_id": "amzn-echo-1",
                "devices": {
                  "amzn-echo-1": {
                    "can_play": true,
                    "volume": 32768,
                    "name": "",
                    "device_type": "SPEAKER",
                    "brand": "Amazon",
                    "model": "Echo Dot",
                    "device_aliases": {
                      "0": {
                        "id": "amzn1.alexa.bedroom",
                        "name": "Emma's Echo Dot",
                        "is_group": false
                      }
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val cluster = Cluster.parse(Json.parseToJsonElement(raw).jsonObject)
        assertNotNull(cluster)
        val dev = cluster!!.devices.single()
        assertEquals("amzn-echo-1", dev.id)
        assertEquals("Emma's Echo Dot", dev.name)
        assertEquals(listOf("Emma's Echo Dot"), dev.aliases)
        assertEquals("SPEAKER", dev.type)
        assertEquals(listOf(DeviceAlias("amzn1.alexa.bedroom", "Emma's Echo Dot", false)), dev.deviceAliases)
    }

    @Test
    fun `Cluster parse resolves multi-alias device groups with is_group flag`() {
        val raw = """
            {
              "devices": {
                "amzn-echo-multi": {
                  "name": "",
                  "device_type": "SPEAKER",
                  "device_aliases": [
                    { "id": "amzn1.echo", "name": "Living Room Echo", "is_group": false },
                    { "id": "amzn1.mrm", "name": "Everywhere", "is_group": true }
                  ]
                }
              }
            }
        """.trimIndent()

        val cluster = Cluster.parse(Json.parseToJsonElement(raw).jsonObject)
        assertNotNull(cluster)
        val dev = cluster!!.devices.single()
        assertEquals("amzn-echo-multi", dev.id)
        assertEquals("Living Room Echo", dev.name)
        assertEquals(listOf("Living Room Echo", "Everywhere"), dev.aliases)
        assertEquals(
            listOf(
                DeviceAlias("amzn1.echo", "Living Room Echo", false),
                DeviceAlias("amzn1.mrm", "Everywhere", true),
            ),
            dev.deviceAliases,
        )
    }

    @Test
    fun `NativeEngine transferTo sends Protobuf-JSON transfer payload to remote device`() = kotlinx.coroutines.test.runTest {
        val rig = Rig(backgroundScope)
        val status = rig.engine.transferTo("remote-echo-1", restorePaused = "restore")
        assertEquals(200, status)

        val lastReq = rig.http.requests.lastOrNull { it.url.contains("/connect/transfer/") }
        assertNotNull(lastReq)
        val url = lastReq!!.url
        assertEquals("POST", lastReq.method)
        org.junit.jupiter.api.Assertions.assertTrue(url.endsWith("/connect/transfer/from/${rig.engine.deviceId}/to/remote-echo-1"))

        val body = Json.parseToJsonElement(lastReq.body!!.decodeToString()).jsonObject
        assertEquals("restore", body["transfer_options"]!!.jsonObject["restore_paused"]!!.jsonPrimitive.content)
        org.junit.jupiter.api.Assertions.assertTrue(body.containsKey("command_id"))
    }

    @Test
    fun `Cluster parse resolves aliases array brand-model fallback and human type fallback`() {
        val raw = """
            {
              "devices": {
                "alexa-group": {
                  "name": "   ",
                  "device_type": "SPEAKER",
                  "aliases": [
                    { "id": "g1", "name": "Downstairs Everywhere" }
                  ]
                },
                "echo-show": {
                  "name": "",
                  "device_type": "AVR",
                  "brand": "Amazon",
                  "model": "Echo Show 5"
                },
                "sonos-dedup": {
                  "device_type": "SPEAKER",
                  "brand": "Sonos",
                  "model": "Sonos One SL"
                },
                "unnamed-tv": {
                  "device_type": "TV"
                }
              }
            }
        """.trimIndent()

        val cluster = Cluster.parse(Json.parseToJsonElement(raw).jsonObject)
        assertNotNull(cluster)
        val byId = cluster!!.devices.associateBy { it.id }
        assertEquals("Downstairs Everywhere", byId.getValue("alexa-group").name)
        assertEquals(listOf("Downstairs Everywhere"), byId.getValue("alexa-group").aliases)
        assertEquals("Amazon Echo Show 5", byId.getValue("echo-show").name)
        assertEquals("Sonos One SL", byId.getValue("sonos-dedup").name)
        assertEquals("TV", byId.getValue("unnamed-tv").name)
    }

    @Test
    fun `Cluster parse handles live Amazon Echo dealer payload with numeric alias id and UUID name`() {
        val raw = """
            {
              "cluster": {
                "active_device_id": "28fa3b85-4e54-4429-9383-0bad54fa70e8",
                "devices": {
                  "28fa3b85-4e54-4429-9383-0bad54fa70e8": {
                    "can_play": true,
                    "volume": 5242,
                    "name": "28fa3b85-4e54-4429-9383-0bad54fa70e8",
                    "device_id": "28fa3b85-4e54-4429-9383-0bad54fa70e8",
                    "device_type": "SPEAKER",
                    "brand": "Amazon",
                    "model": "Echo",
                    "selected_alias_id": 1,
                    "device_aliases": {
                      "1": {
                        "id": 1,
                        "display_name": "Thorstens Echo"
                      }
                    },
                    "is_offline": true,
                    "is_dynamic_device": true
                  }
                }
              }
            }
        """.trimIndent()

        val cluster = Cluster.parse(Json.parseToJsonElement(raw).jsonObject)
        assertNotNull(cluster)
        val dev = cluster!!.devices.single()
        assertEquals("28fa3b85-4e54-4429-9383-0bad54fa70e8", dev.id)
        assertEquals("Thorstens Echo", dev.name)
        assertEquals("1", dev.selectedAliasId)
        assertEquals(true, dev.offline)
        assertEquals(listOf(DeviceAlias("1", "Thorstens Echo", false)), dev.deviceAliases)
    }

    @Test
    fun `ConnectDeviceId parses Harmony composite aliases and matches _amzn_1 with a_1`() {
        val harmony = ConnectDeviceId.parse("28fa3b85-4e54-4429-9383-0bad54fa70e8::a_1")
        assertEquals("28fa3b85-4e54-4429-9383-0bad54fa70e8", harmony.baseId)
        assertEquals("1", harmony.aliasId)
        assertEquals("28fa3b85-4e54-4429-9383-0bad54fa70e8", harmony.wireDeviceId)
        assertEquals(1, harmony.wireAliasInt)

        val hobs = ConnectDeviceId.parse("28fa3b85-4e54-4429-9383-0bad54fa70e8_amzn_1")
        assertEquals("28fa3b85-4e54-4429-9383-0bad54fa70e8", hobs.baseId)
        assertEquals("1", hobs.aliasId)
        assertEquals("28fa3b85-4e54-4429-9383-0bad54fa70e8_amzn_1", hobs.wireDeviceId)
        assertEquals(null, hobs.wireAliasInt)

        org.junit.jupiter.api.Assertions.assertTrue(
            ConnectDeviceId.sameDevice("28fa3b85-4e54-4429-9383-0bad54fa70e8_amzn_1", "28fa3b85-4e54-4429-9383-0bad54fa70e8::a_1")
        )
        org.junit.jupiter.api.Assertions.assertFalse(
            ConnectDeviceId.sameDevice("041525f1-3974-44e9-95f9-ad6b258e3ac4_amzn_1", "041525f1-3974-44e9-95f9-ad6b258e3ac4::a_2")
        )
        org.junit.jupiter.api.Assertions.assertTrue(
            ConnectDeviceId.matchesActiveClusterDevice("28fa3b85-4e54-4429-9383-0bad54fa70e8", "28fa3b85-4e54-4429-9383-0bad54fa70e8::a_1")
        )
        assertEquals("28fa3b85-4e54-4429-9383-0bad54fa70e8::a_1", ConnectDeviceId.harmonyTargetId("28fa3b85-4e54-4429-9383-0bad54fa70e8", "1"))
    }

    @Test
    fun `NativeEngine transferTo strips composite alias suffix from URL and sends integer target_alias_id`() = kotlinx.coroutines.test.runTest {
        val rig = Rig(backgroundScope)
        val status = rig.engine.transferTo("28fa3b85-4e54-4429-9383-0bad54fa70e8::a_1", restorePaused = "restore")
        assertEquals(200, status)

        val lastReq = rig.http.requests.lastOrNull { it.url.contains("/connect/transfer/") }
        assertNotNull(lastReq)
        val url = lastReq!!.url
        org.junit.jupiter.api.Assertions.assertTrue(
            url.endsWith("/connect/transfer/from/${rig.engine.deviceId}/to/28fa3b85-4e54-4429-9383-0bad54fa70e8"),
            "Expected base device ID in transfer URL, got: $url",
        )
        val body = Json.parseToJsonElement(lastReq.body!!.decodeToString()).jsonObject
        val alias = body["target_alias_id"]?.jsonPrimitive
        assertNotNull(alias, "Expected target_alias_id in transfer body: $body")
        assertEquals("1", alias!!.content)
        org.junit.jupiter.api.Assertions.assertFalse(alias.isString, "Expected numeric target_alias_id, got string: $body")
    }

    @Test
    fun `NativeEngine transferTo retries _amzn_N target with base UUID and target_alias_id on 410`() = kotlinx.coroutines.test.runTest {
        val rig = Rig(backgroundScope)
        rig.http.failNext("/to/28fa3b85-4e54-4429-9383-0bad54fa70e8_amzn_1", 410)
        val status = rig.engine.transferTo("28fa3b85-4e54-4429-9383-0bad54fa70e8_amzn_1", restorePaused = "restore")
        assertEquals(200, status)

        val transferReqs = rig.http.requests.filter { it.url.contains("/connect/transfer/") }
        assertEquals(2, transferReqs.size)
        org.junit.jupiter.api.Assertions.assertTrue(transferReqs[0].url.endsWith("/to/28fa3b85-4e54-4429-9383-0bad54fa70e8_amzn_1"))
        org.junit.jupiter.api.Assertions.assertTrue(transferReqs[1].url.endsWith("/to/28fa3b85-4e54-4429-9383-0bad54fa70e8"))
        val retryBody = Json.parseToJsonElement(transferReqs[1].body!!.decodeToString()).jsonObject
        assertEquals("1", retryBody["target_alias_id"]?.jsonPrimitive?.content)
        org.junit.jupiter.api.Assertions.assertFalse(retryBody["target_alias_id"]!!.jsonPrimitive.isString)
    }
}
