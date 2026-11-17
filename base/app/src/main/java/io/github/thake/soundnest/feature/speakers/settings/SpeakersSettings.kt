/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.speakers.settings

import android.content.Context
import io.github.thake.soundnest.core.preferences.PolicySyncedSetting
import io.github.thake.soundnest.core.preferences.SessionResettable
import io.github.thake.soundnest.core.preferences.featurePrefs
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy

/**
 * Runtime settings and session-scoped Spotify Connect device selection for `feature.speakers`,
 * backed by `"feature_speakers"`.
 */
object SpeakersSettings : PolicySyncedSetting, SessionResettable {
    internal const val PREF_NAME = "speakers"
    private const val KEY_CHOSEN_DEVICE = "chosen_device"
    private const val KEY_CHOSEN_AT = "chosen_at"
    private const val CHOSEN_KEEP_MS = 24 * 3600_000L

    @Volatile
    private var chosen: String? = null

    @Volatile
    private var chosenLoaded = false

    private fun prefs(context: Context) = context.featurePrefs(PREF_NAME)

    fun getChosenRemote(context: Context): String? {
        if (!chosenLoaded) {
            chosenLoaded = true
            chosen = runCatching {
                val p = prefs(context)
                p.getString(KEY_CHOSEN_DEVICE, null)
                    ?.takeIf { System.currentTimeMillis() - p.getLong(KEY_CHOSEN_AT, 0L) < CHOSEN_KEEP_MS }
            }.getOrNull()
        }
        return chosen
    }

    fun setChosenRemote(context: Context, deviceId: String?) {
        chosenLoaded = true
        chosen = deviceId
        runCatching {
            val editor = prefs(context).edit()
            if (deviceId == null) {
                editor.remove(KEY_CHOSEN_DEVICE).remove(KEY_CHOSEN_AT)
            } else {
                editor.putString(KEY_CHOSEN_DEVICE, deviceId).putLong(KEY_CHOSEN_AT, System.currentTimeMillis())
            }
            editor.apply()
        }
    }

    override fun enforcePolicy(context: Context, policy: ParentalPolicy) {
        TrustedNetworkGate.applyPolicy(context, policy.speakers)
        CustomSpeakerNames.applyPolicy(context, policy.speakers.customSpeakerNames)
    }

    override fun onLeaveAccount(context: Context, forget: Boolean) {
        setChosenRemote(context, null)
    }
}
