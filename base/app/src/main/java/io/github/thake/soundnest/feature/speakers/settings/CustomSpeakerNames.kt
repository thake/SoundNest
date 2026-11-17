/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.speakers.settings

import android.content.Context
import io.github.thake.soundnest.core.preferences.featurePrefs
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore

/**
 * Parent-assigned custom speaker nicknames (e.g. renaming `"Echo Dot-49P"` to `"Emma's Bedroom"`).
 * Stored locally in `"feature_speakers"` by Spotify Connect `deviceId` and synced with
 * [SpeakersParentalPolicy.customSpeakerNames]; blank nicknames clear the override.
 */
object CustomSpeakerNames {
    private const val KEY_PREFIX = "speaker_name:"

    @Volatile
    private var applyingFromPolicy = false

    private fun prefs(context: Context) = context.featurePrefs(SpeakersSettings.PREF_NAME)

    fun setCustomName(context: Context, deviceId: String, customName: String?) {
        if (deviceId.isBlank()) return
        val id = deviceId.trim()
        val trimmed = customName?.trim().orEmpty()
        prefs(context).edit().apply {
            if (trimmed.isEmpty()) remove("$KEY_PREFIX$id") else putString("$KEY_PREFIX$id", trimmed)
        }.apply()
        if (!applyingFromPolicy) {
            ParentalPolicyStore.syncCustomSpeakerNames(context, all(context))
        }
    }

    fun getCustomName(context: Context, deviceId: String): String? =
        if (deviceId.isBlank()) null
        else prefs(context).getString("$KEY_PREFIX${deviceId.trim()}", null)?.trim()?.takeIf { it.isNotEmpty() }

    fun resolve(context: Context, deviceId: String, fallbackName: String): String =
        getCustomName(context, deviceId) ?: fallbackName

    fun all(context: Context): Map<String, String> =
        prefs(context).all.mapNotNull { (k, v) ->
            if (!k.startsWith(KEY_PREFIX)) return@mapNotNull null
            val id = k.removePrefix(KEY_PREFIX).trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val name = (v as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            id to name
        }.toMap()

    fun clearAll(context: Context) {
        val p = prefs(context)
        val keys = p.all.keys.filter { it.startsWith(KEY_PREFIX) }
        if (keys.isNotEmpty()) {
            val editor = p.edit()
            keys.forEach(editor::remove)
            editor.apply()
        }
        if (!applyingFromPolicy) {
            ParentalPolicyStore.syncCustomSpeakerNames(context, emptyMap())
        }
    }

    internal fun applyPolicy(context: Context, names: Map<String, String>) {
        applyingFromPolicy = true
        try {
            val p = prefs(context)
            val editor = p.edit()
            p.all.keys.filter { it.startsWith(KEY_PREFIX) }.forEach(editor::remove)
            names.forEach { (k, v) ->
                val id = k.trim()
                val cleaned = v.trim()
                if (id.isNotEmpty() && cleaned.isNotEmpty()) {
                    editor.putString("$KEY_PREFIX$id", cleaned)
                }
            }
            editor.apply()
        } finally {
            applyingFromPolicy = false
        }
    }
}

/**
 * Ensures sleep timer expiration reliably pauses active remote Spotify Connect / Alexa speakers
 * even if the Web Player's `pauseSlowly` failed or left the remote cluster playing.
 */
object RemoteSleepPauseCoordinator {
    suspend fun finishRemoteSleepFade(
        remoteDeviceId: String?,
        pauseSlowlySucceeded: Boolean,
        remoteStillPlaying: () -> Boolean,
        sendPlayerPause: () -> Unit,
        sendDirectDevicePause: suspend (String) -> Boolean,
    ) {
        val needsFallback = !pauseSlowlySucceeded || (remoteDeviceId != null && remoteStillPlaying())
        if (!needsFallback) return
        sendPlayerPause()
        if (!remoteDeviceId.isNullOrBlank()) {
            runCatching { sendDirectDevicePause(remoteDeviceId) }
        }
    }
}
