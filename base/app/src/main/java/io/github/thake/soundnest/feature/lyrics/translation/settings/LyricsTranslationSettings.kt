/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.lyrics.translation.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.thake.soundnest.core.di.Graph
import io.github.thake.soundnest.core.preferences.PolicySyncedSetting
import io.github.thake.soundnest.core.preferences.featurePrefs
import io.github.thake.soundnest.feature.lyrics.data.LyricsApi
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy

/**
 * Extra community lyric sources (NetEase, QQ), romanization, and lyric translation language settings,
 * backed by `"feature_lyrics"` (`context.featurePrefs("lyrics")`).
 */
object LyricsTranslationSettings : PolicySyncedSetting {
    private const val KEY_EXTRA_SOURCES = "lyrics_extra_sources"
    private const val KEY_ROMANIZATION = "lyrics_romanization"
    private const val KEY_TRANSLATE_LANG = "lyric_translate_lang"

    private fun prefs(c: Context) = c.featurePrefs("lyrics")

    /** Extra lyrics sources (NetEase, QQ, Genius) when Spotify and LRCLIB have none. */
    fun isExtraSources(c: Context): Boolean = prefs(c).getBoolean(KEY_EXTRA_SOURCES, true)

    fun setExtraSources(c: Context, value: Boolean) {
        prefs(c).edit().putBoolean(KEY_EXTRA_SOURCES, value).apply()
        LyricsApi.extraSources = value
        LyricsApi.extraSourcesOff = !value
    }

    /** Whether extra sources were explicitly turned off (never touched: Genius is still the last resort). */
    fun isExtraSourcesOff(c: Context): Boolean =
        prefs(c).let { it.contains(KEY_EXTRA_SOURCES) && !it.getBoolean(KEY_EXTRA_SOURCES, false) }

    /**
     * "Show romanization" under the lyrics' lines (the song in Latin letters), offered when the lyrics
     * carry one. Off by default; read as state by both looks, so the menu and the lines agree.
     */
    var romanization by mutableStateOf(
        runCatching { prefs(Graph.app).getBoolean(KEY_ROMANIZATION, false) }.getOrDefault(false),
    )
        private set

    fun setRomanization(c: Context, value: Boolean) {
        romanization = value
        prefs(c).edit().putBoolean(KEY_ROMANIZATION, value).apply()
    }

    fun getTranslateLang(c: Context): String =
        prefs(c).getString(KEY_TRANSLATE_LANG, "").orEmpty()

    fun setTranslateLang(c: Context, code: String) {
        prefs(c).edit().putString(KEY_TRANSLATE_LANG, code.trim()).apply()
    }

    fun init(c: Context) {
        val p = prefs(c)
        romanization = p.getBoolean(KEY_ROMANIZATION, false)
        LyricsApi.extraSources = p.getBoolean(KEY_EXTRA_SOURCES, true)
        LyricsApi.extraSourcesOff = isExtraSourcesOff(c)
    }

    override fun enforcePolicy(context: Context, policy: ParentalPolicy) {
        val extraAllowed = policy.lyrics.enabled && policy.lyrics.allowExtraSources
        setExtraSources(context, extraAllowed)
        if (!policy.lyrics.enabled || !extraAllowed) {
            LyricsApi.forgetAll()
        }
    }
}
