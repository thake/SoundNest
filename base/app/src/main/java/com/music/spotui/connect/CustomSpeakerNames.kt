/* SPDX-License-Identifier: GPL-3.0-only */
package com.music.spotui.connect

import android.content.Context

/**
 * Parent-assigned custom speaker nicknames (e.g. renaming `"Echo Dot-49P"` to `"Emma's Bedroom"`).
 * Stored locally by Spotify Connect `deviceId`; blank nicknames clear the override.
 */
object CustomSpeakerNames {
    private const val PREFS = "parental_speaker_names"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun setCustomName(context: Context, deviceId: String, customName: String?) {
        if (deviceId.isBlank()) return
        val trimmed = customName?.trim().orEmpty()
        prefs(context).edit().apply {
            if (trimmed.isEmpty()) remove(deviceId) else putString(deviceId, trimmed)
        }.apply()
    }

    fun getCustomName(context: Context, deviceId: String): String? =
        if (deviceId.isBlank()) null else prefs(context).getString(deviceId, null)?.trim()?.takeIf { it.isNotEmpty() }

    fun resolve(context: Context, deviceId: String, fallbackName: String): String =
        getCustomName(context, deviceId) ?: fallbackName

    fun all(context: Context): Map<String, String> =
        prefs(context).all.mapNotNull { (k, v) ->
            val name = (v as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            k to name
        }.toMap()

    fun clearAll(context: Context) {
        prefs(context).edit().clear().apply()
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
