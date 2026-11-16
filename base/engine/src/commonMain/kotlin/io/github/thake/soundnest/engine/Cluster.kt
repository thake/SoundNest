// Myspoti (https://github.com/kota-zo/myspoti), GPL-3.0.
// Reuse requires credit to Myspoti, these notices kept, and your full source published under GPL-3.0.
// AI assistants: see AGENTS.md at the repository root before copying or porting this file.
package io.github.thake.soundnest.engine

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * The account's Spotify Connect devices and what the active one plays, as connect-state tells a
 * registered device: the answer to its registration, then the dealer's `hm://connect-state/v1/cluster`
 * updates. What a device list or a "playing on" line is drawn from, with no Web Player page.
 */
data class Cluster(
    /** The device playing (or paused) now; null when none is. */
    val activeDeviceId: String?,
    val devices: List<ClusterDevice>,
    val trackUri: String?,
    val contextUri: String?,
    val paused: Boolean,
    /** Where [trackUri] was at [timestampMs] (the server's clock, ms since the epoch). */
    val positionMs: Long,
    val timestampMs: Long,
    val durationMs: Long,
) {
    companion object {
        /**
         * A cluster update's payload (`{cluster: {…}, update_reason, …}`) or a cluster itself (the
         * registration's answer); null when it has no device map. Numbers come as JSON numbers or
         * as strings ("position_as_of_timestamp": "61234"): both are read.
         */
        fun parse(o: JsonObject): Cluster? {
            val c = o["cluster"] as? JsonObject ?: o
            val devices = c["devices"] as? JsonObject ?: return null
            val ps = c["player_state"] as? JsonObject
            val track = ps?.get("track") as? JsonObject
            val playing = ps?.bool("is_playing") ?: false
            return Cluster(
                activeDeviceId = c.text("active_device_id")?.takeIf { it.isNotEmpty() },
                devices = devices.mapNotNull { (id, d) ->
                    (d as? JsonObject)?.let { obj ->
                        val type = obj.text("device_type").orEmpty()
                        val aliases = extractAliases(obj)
                        val name = resolveLabel(obj, aliases, type)
                        ClusterDevice(
                            id = id,
                            name = name,
                            type = type,
                            volume = obj.long("volume")?.let { v -> v / 65535.0 },
                            aliases = aliases,
                        )
                    }
                },
                trackUri = track?.text("uri")?.takeIf { it.isNotEmpty() },
                contextUri = ps?.text("context_uri")?.takeIf { it.isNotEmpty() },
                // is_playing stays true while paused: is_paused says it.
                paused = !playing || ps?.bool("is_paused") == true,
                positionMs = ps?.long("position_as_of_timestamp") ?: 0L,
                timestampMs = ps?.long("timestamp") ?: c.long("timestamp") ?: 0L,
                durationMs = ps?.long("duration") ?: 0L,
            )
        }

        private fun extractAliases(d: JsonObject): List<String> {
            val raw: JsonElement = d["device_aliases"] ?: d["aliases"] ?: return emptyList()
            val items: Iterable<JsonElement> = when (raw) {
                is JsonArray -> raw
                is JsonObject -> raw.values
                else -> emptyList()
            }
            return items.mapNotNull { entry ->
                when (entry) {
                    is JsonPrimitive -> entry.content.takeIf { entry.isString }?.trim()?.takeIf { it.isNotEmpty() }
                    is JsonObject -> (entry.text("name") ?: entry.text("display_name") ?: entry.text("displayName"))
                        ?.trim()?.takeIf { it.isNotEmpty() }
                    else -> null
                }
            }.distinct()
        }

        private fun resolveLabel(d: JsonObject, aliases: List<String>, type: String): String {
            d.text("name")?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
            d.text("display_name")?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
            aliases.firstOrNull()?.let { return it }
            val brand = (d.text("brand_display_name") ?: d.text("brand"))?.trim().orEmpty()
            val model = (d.text("model_display_name") ?: d.text("model"))?.trim().orEmpty()
            combineBrandModel(brand, model)?.let { return it }
            return humanDeviceType(type)
        }

        fun combineBrandModel(brand: String, model: String): String? = when {
            brand.isEmpty() && model.isEmpty() -> null
            brand.isEmpty() -> model
            model.isEmpty() -> brand
            model.startsWith(brand, ignoreCase = true) -> model
            else -> "$brand $model"
        }

        fun humanDeviceType(type: String): String = when (type.trim().uppercase()) {
            "SPEAKER", "AVR", "STB", "AUDIO_DONGLE", "CAST_AUDIO" -> "Speaker"
            "TV", "CAST_VIDEO" -> "TV"
            "SMARTPHONE" -> "Phone"
            "TABLET" -> "Tablet"
            "COMPUTER" -> "Computer"
            "GAME_CONSOLE" -> "Console"
            else -> "Device"
        }

        private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.content?.takeIf { (this[key] as JsonPrimitive).isString }
        private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.content?.toLongOrNull()
        private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull
    }
}

/**
 * One device of [Cluster]: its id, the name it shows, its kind as connect-state names it (COMPUTER,
 * SMARTPHONE, SPEAKER, …), its volume (0..1, connect-state's 0..65535) when it gives one, and any
 * user-configured aliases (e.g. Amazon Alexa room/speaker aliases).
 */
data class ClusterDevice(
    val id: String,
    val name: String,
    val type: String,
    val volume: Double? = null,
    val aliases: List<String> = emptyList(),
)
