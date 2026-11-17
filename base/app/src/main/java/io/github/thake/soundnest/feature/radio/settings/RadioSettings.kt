/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.radio.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.thake.soundnest.core.preferences.PolicySyncedSetting
import io.github.thake.soundnest.core.preferences.featurePrefs
import io.github.thake.soundnest.feature.radio.playback.RadioPlayer
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy

/**
 * "Live Radio" (Settings > Library): [on] adds the pinned Library row under Local Files; off hides
 * it and stops anything playing. Backed by `"feature_radio"` preferences and kept in sync with
 * [ParentalPolicy].
 */
object RadioSettings : PolicySyncedSetting {
    private const val PREF_NAME = "radio"
    private const val KEY_ON = "on"

    /** The pinned Library row's sentinel id (parallel to Api.HomeCache.LOCAL_FILES_ID). */
    const val LIBRARY_ENTRY_ID = "live radio"

    private fun prefs(context: Context) = context.featurePrefs(PREF_NAME)

    var on by mutableStateOf(false)
        private set

    fun init(context: Context) {
        on = prefs(context).getBoolean(KEY_ON, false)
    }

    fun set(context: Context, value: Boolean) {
        on = value
        prefs(context).edit().putBoolean(KEY_ON, value).apply()
        if (!value) RadioPlayer.stop()
    }

    override fun enforcePolicy(context: Context, policy: ParentalPolicy) {
        set(context, policy.radio.enabled)
    }
}
