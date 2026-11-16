/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.parental

import android.content.Context
import io.github.thake.soundnest.connect.CustomSpeakerNames
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class TrustedNetworkBackup(
    val requireTrustedNetwork: Boolean = true,
    val trustedSsids: Set<String> = emptySet(),
    val trustedBssids: Set<String> = emptySet(),
    val trustedLanFingerprints: Set<String> = emptySet(),
    val speakerVisibilityMode: SpeakerVisibilityMode = SpeakerVisibilityMode.ALLOW_ALL_EXCEPT_HIDDEN,
    val hiddenDeviceIds: Set<String> = emptySet(),
    val allowedDeviceIds: Set<String> = emptySet(),
)

@Serializable
data class ParentalBackupPayload(
    val schemaVersion: Int = ParentalConfigBackup.CURRENT_SCHEMA_VERSION,
    val policy: ParentalPolicy = ParentalPolicy(),
    val trustedNetwork: TrustedNetworkBackup = TrustedNetworkBackup(),
    val customSpeakerNames: Map<String, String> = emptyMap(),
    val ruleLabels: Map<String, RuleLabel> = emptyMap(),
)

/**
 * Exports and imports parent configuration (content filter rules, Home section settings,
 * Trusted Home Wi-Fi & speaker visibility rules, and custom speaker nicknames) as portable JSON
 * for configuring multiple siblings' devices.
 *
 * Security invariant: NEVER exports or modifies [ParentSecurityManager] PIN hashes, salts, or lockouts.
 */
object ParentalConfigBackup {
    const val CURRENT_SCHEMA_VERSION = 1

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun exportPayload(context: Context): ParentalBackupPayload = ParentalBackupPayload(
        schemaVersion = CURRENT_SCHEMA_VERSION,
        policy = ParentalPolicyStore.current(context),
        trustedNetwork = TrustedNetworkBackup(
            requireTrustedNetwork = TrustedNetworkGate.isRequireTrustedNetwork(context),
            trustedSsids = TrustedNetworkGate.trustedSsids(context),
            trustedBssids = TrustedNetworkGate.trustedBssids(context),
            trustedLanFingerprints = TrustedNetworkGate.trustedLanFingerprints(context),
            speakerVisibilityMode = TrustedNetworkGate.speakerVisibilityMode(context),
            hiddenDeviceIds = TrustedNetworkGate.hiddenDeviceIds(context),
            allowedDeviceIds = TrustedNetworkGate.allowedDeviceIds(context),
        ),
        customSpeakerNames = CustomSpeakerNames.all(context),
        ruleLabels = RuleLabelStore.all(context),
    )

    fun exportJson(context: Context): String =
        json.encodeToString(exportPayload(context))

    fun importJson(context: Context, rawJson: String): Result<Unit> = runCatching {
        val payload = json.decodeFromString<ParentalBackupPayload>(rawJson)
        require(payload.schemaVersion in 1..CURRENT_SCHEMA_VERSION) {
            "Unsupported backup schema version: ${payload.schemaVersion}"
        }
        // Validate regex patterns before mutating state
        val validRegexes = payload.policy.blockedRegexPatterns.filter { pattern ->
            pattern.isNotBlank() && runCatching { Regex(pattern, RegexOption.IGNORE_CASE) }.isSuccess
        }.toSet()
        val sanitizedPolicy = payload.policy.copy(blockedRegexPatterns = validRegexes)

        val net = payload.trustedNetwork
        TrustedNetworkGate.replaceConfiguration(
            context = context,
            requireTrustedNetwork = net.requireTrustedNetwork,
            ssids = net.trustedSsids,
            bssids = net.trustedBssids,
            lanFingerprints = net.trustedLanFingerprints,
            visibilityMode = net.speakerVisibilityMode,
            hiddenDevices = net.hiddenDeviceIds,
            allowedDevices = net.allowedDeviceIds,
            allowJam = sanitizedPolicy.allowJam,
        )

        CustomSpeakerNames.clearAll(context)
        payload.customSpeakerNames.forEach { (deviceId, nickname) ->
            CustomSpeakerNames.setCustomName(context, deviceId, nickname)
        }

        RuleLabelStore.mergeAll(context, payload.ruleLabels)
        ParentalPolicyStore.replace(context, sanitizedPolicy)
    }
}
