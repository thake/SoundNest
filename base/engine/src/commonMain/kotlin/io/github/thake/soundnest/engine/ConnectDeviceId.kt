/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.engine

/**
 * Normalizes and parses Spotify Connect device identifiers across REST (`hobs` / `/v1/cluster`)
 * and Harmony dealer representations.
 *
 * Amazon Alexa / Echo devices appear in two equivalent forms depending on which Spotify endpoint
 * last populated the cluster:
 * - Dealer push + Harmony: `<baseUuid>::a_<aliasInt>` (split on wire into `to/<baseUuid>` + `target_alias_id: <aliasInt>`)
 * - Initial `hobs` / `GET /v1/cluster`: `<baseUuid>_amzn_<aliasInt>` (sent as `to/<baseUuid>_amzn_<aliasInt>`, or retried as `to/<baseUuid>` + `target_alias_id: <aliasInt>`)
 */
object ConnectDeviceId {
    const val HARMONY_ALIAS_SEPARATOR = "::a_"
    private val AMZN_ALIAS_PATTERN = Regex("^(.+)_amzn_(\\d+)$")

    data class Parsed(
        val rawId: String,
        val baseId: String,
        val aliasId: String?,
        val wireDeviceId: String,
        val wireAliasInt: Int?,
        val wireAliasRaw: String?,
        val alternateWire: Pair<String, Int?>? = null,
    )

    fun parse(deviceId: String, explicitAliasId: String? = null): Parsed {
        val trimmed = deviceId.trim()
        val explicit = explicitAliasId?.trim()?.takeIf { it.isNotEmpty() }

        val sepIdx = trimmed.indexOf(HARMONY_ALIAS_SEPARATOR)
        if (sepIdx > 0) {
            val base = trimmed.substring(0, sepIdx)
            val alias = trimmed.substring(sepIdx + HARMONY_ALIAS_SEPARATOR.length).trim().takeIf { it.isNotEmpty() } ?: explicit
            val aliasInt = alias?.toIntOrNull()
            return Parsed(
                rawId = trimmed,
                baseId = base,
                aliasId = alias,
                wireDeviceId = base,
                wireAliasInt = aliasInt,
                wireAliasRaw = alias,
                alternateWire = aliasInt?.let { "${base}_amzn_$it" to null },
            )
        }

        val amznMatch = AMZN_ALIAS_PATTERN.matchEntire(trimmed)
        if (amznMatch != null) {
            val base = amznMatch.groupValues[1]
            val alias = amznMatch.groupValues[2]
            val aliasInt = alias.toIntOrNull()
            return Parsed(
                rawId = trimmed,
                baseId = base,
                aliasId = alias,
                wireDeviceId = trimmed,
                wireAliasInt = null,
                wireAliasRaw = null,
                alternateWire = aliasInt?.let { base to it },
            )
        }

        val aliasInt = explicit?.toIntOrNull()
        return Parsed(
            rawId = trimmed,
            baseId = trimmed,
            aliasId = explicit,
            wireDeviceId = trimmed,
            wireAliasInt = aliasInt,
            wireAliasRaw = explicit,
            alternateWire = null,
        )
    }

    /** Formats a target ID the way Harmony's `_resolveTargetDevice` expects (`<base>::a_<alias>` when alias is separate). */
    fun harmonyTargetId(deviceId: String, aliasId: String? = null): String {
        val trimmed = deviceId.trim()
        val explicit = aliasId?.trim()?.takeIf { it.isNotEmpty() }
        if (explicit == null) return trimmed
        if (HARMONY_ALIAS_SEPARATOR in trimmed || AMZN_ALIAS_PATTERN.matches(trimmed)) return trimmed
        return "$trimmed$HARMONY_ALIAS_SEPARATOR$explicit"
    }

    /** True when two device IDs refer to the same Connect device and alias (even across `_amzn_N` vs `::a_N`). */
    fun sameDevice(
        firstId: String?,
        secondId: String?,
        firstAliasId: String? = null,
        secondAliasId: String? = null,
    ): Boolean {
        val a = firstId?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        val b = secondId?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        if (a == b && (firstAliasId.isNullOrBlank() || secondAliasId.isNullOrBlank() || firstAliasId == secondAliasId)) {
            return true
        }
        val pa = parse(a, firstAliasId)
        val pb = parse(b, secondAliasId)
        return pa.baseId == pb.baseId && pa.aliasId == pb.aliasId
    }

    /**
     * True when [activeClusterDeviceId] (from `cluster.active_device_id`, which may be the bare
     * base UUID on dealer pushes) matches the target device/alias.
     */
    fun matchesActiveClusterDevice(
        activeClusterDeviceId: String?,
        targetDeviceId: String?,
        targetAliasId: String? = null,
    ): Boolean {
        val active = activeClusterDeviceId?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        val target = targetDeviceId?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        if (sameDevice(active, target, secondAliasId = targetAliasId)) return true
        val pa = parse(active)
        val pt = parse(target, targetAliasId)
        return pa.aliasId == null && pa.baseId == pt.baseId
    }
}
