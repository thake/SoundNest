/* SPDX-License-Identifier: GPL-3.0-only */
package app.myspoti.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
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
}
