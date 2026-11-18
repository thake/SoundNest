/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.parental.settings

import android.content.Context
import io.github.thake.soundnest.feature.speakers.settings.TrustedNetworkGate
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ParentalBackupPayload(
    val schemaVersion: Int = ParentalConfigBackup.CURRENT_SCHEMA_VERSION,
    val policy: ParentalPolicy = ParentalPolicy(),
    val ruleLabels: Map<String, RuleLabel> = emptyMap(),
)

/**
 * Exports and imports parent configuration (content filter rules, Home section settings,
 * feature gates, Trusted Home Wi-Fi & speaker visibility rules, and custom speaker nicknames —
 * all nested inside [ParentalPolicy]) as portable JSON for configuring multiple siblings' devices.
 *
 * Security invariant: NEVER exports or modifies `ParentSecurityManager` PIN hashes, salts, or lockouts,
 * nor Audible OAuth session credentials.
 */
object ParentalConfigBackup {
    const val CURRENT_SCHEMA_VERSION = 1

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun exportPayload(context: Context): ParentalBackupPayload {
        val currentPolicy = ParentalPolicyStore.current(context).let {
            it.copy(speakers = TrustedNetworkGate.toSpeakersPolicy(context))
        }
        return ParentalBackupPayload(
            schemaVersion = CURRENT_SCHEMA_VERSION,
            policy = currentPolicy,
            ruleLabels = RuleLabelStore.all(context),
        )
    }

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
        val migratedPolicy = ParentalPolicyStore.migrateLegacyAudiblePolicy(rawJson, payload.policy)
        val sanitizedPolicy = migratedPolicy.copy(blockedRegexPatterns = validRegexes)

        RuleLabelStore.mergeAll(context, payload.ruleLabels)
        ParentalPolicyStore.replace(context, sanitizedPolicy)
    }
}
