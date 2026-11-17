/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.home.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.thake.soundnest.core.di.Graph
import io.github.thake.soundnest.core.preferences.PolicySyncedSetting
import io.github.thake.soundnest.core.preferences.featurePrefs
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy

private const val FEATURE_HOME = "home"

private fun homePrefs(context: Context) = context.featurePrefs(FEATURE_HOME)

/**
 * "Hide the DJ" on Home: Spotify's DJ, which comes in the home feed as a playlist card,
 * leaves the shelves when hidden. Synced with [HomeParentalPolicy.showDjCard].
 */
object HideDjPref : PolicySyncedSetting {
    private const val KEY = "hide_dj"

    var on by mutableStateOf(
        runCatching { homePrefs(Graph.app).getBoolean(KEY, true) }.getOrDefault(true),
    )
        private set

    val hidden: Boolean get() = on

    fun init(c: Context) {
        on = homePrefs(c).getBoolean(KEY, true)
    }

    fun set(c: Context, value: Boolean) {
        on = value
        homePrefs(c).edit().putBoolean(KEY, value).apply()
    }

    override fun enforcePolicy(context: Context, policy: ParentalPolicy) {
        set(context, !policy.home.showDjCard)
    }
}
