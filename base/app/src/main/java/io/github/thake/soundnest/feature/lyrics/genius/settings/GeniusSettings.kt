/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.lyrics.genius.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.thake.soundnest.core.di.Graph
import io.github.thake.soundnest.core.preferences.PolicySyncedSetting
import io.github.thake.soundnest.core.preferences.featurePrefs
import io.github.thake.soundnest.feature.lyrics.data.LyricsApi
import io.github.thake.soundnest.feature.lyrics.genius.data.GeniusMeaning
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy

/**
 * Genius Song Meanings & plain-text fallback setting backed by `"feature_lyrics"`.
 * Off: nothing is asked of genius.com and all Genius UI surfaces (light bulb, underlined lines,
 * "Meaning" row, Genius among lyrics sources) are hidden.
 */
object GeniusSettings : PolicySyncedSetting {
    private const val KEY = "genius"

    private fun prefs(c: Context) = c.featurePrefs("lyrics")

    var on by mutableStateOf(
        runCatching { prefs(Graph.app).getBoolean(KEY, true) }.getOrDefault(true),
    )
        private set

    fun init(c: Context) {
        on = prefs(c).getBoolean(KEY, true)
    }

    fun set(c: Context, value: Boolean) {
        on = value
        prefs(c).edit().putBoolean(KEY, value).apply()
        if (!value) GeniusMeaning.forget()
        // Lyrics read from Genius before are not kept: the song is read again from the others.
        LyricsApi.forgetAll()
    }

    override fun enforcePolicy(context: Context, policy: ParentalPolicy) {
        set(context, policy.lyrics.enabled && policy.lyrics.allowGeniusMeanings)
    }
}
