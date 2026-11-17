/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.lyrics.settings

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
 * Core Lyrics settings backed by `"feature_lyrics"` (`context.featurePrefs("lyrics")`).
 * Consolidates word-sync, keep-awake, Classic player lyrics preview card, and the Genius hold hint.
 */
object LyricsSettings : PolicySyncedSetting {
    private const val KEY_WORD_SYNC = "lyrics_word_sync"
    private const val KEY_KEEP_AWAKE = "lyrics_keep_screen_on"
    private const val KEY_PLAYER_LYRICS_CARD = "player_lyrics_card"
    private const val KEY_GENIUS_HOLD_HINT_SEEN = "genius_hold_hint_seen"

    private fun prefs(c: Context) = c.featurePrefs("lyrics")

    /** Lyrics timed word by word first (karaoke); off: line by line, Spotify's first. */
    fun isWordSync(c: Context): Boolean = prefs(c).getBoolean(KEY_WORD_SYNC, true)

    fun setWordSync(c: Context, value: Boolean) {
        prefs(c).edit().putBoolean(KEY_WORD_SYNC, value).apply()
        LyricsApi.wordSync = value
        LyricsApi.forgetAll()
    }

    /**
     * "Keep the screen on with lyrics" (Settings > Lyrics, both looks): while the lyrics are open
     * the screen does not go dark for 10 minutes after the last touch (no limit while charging).
     */
    var keepAwake by mutableStateOf(
        runCatching { prefs(Graph.app).getBoolean(KEY_KEEP_AWAKE, true) }.getOrDefault(true),
    )
        private set

    fun setKeepAwake(c: Context, value: Boolean) {
        keepAwake = value
        prefs(c).edit().putBoolean(KEY_KEEP_AWAKE, value).apply()
    }

    /**
     * "Lyrics preview in the player" (Settings > Display, Classic look): the lyrics card under the
     * player. On by default; off, the lyrics still open with a flick up on the cover.
     */
    var playerLyricsCard by mutableStateOf(
        runCatching { prefs(Graph.app).getBoolean(KEY_PLAYER_LYRICS_CARD, true) }.getOrDefault(true),
    )
        private set

    val playerLyricsCardShown: Boolean get() = playerLyricsCard

    fun setPlayerLyricsCard(c: Context, value: Boolean) {
        playerLyricsCard = value
        prefs(c).edit().putBoolean(KEY_PLAYER_LYRICS_CARD, value).apply()
    }

    /** One-shot hint shown in Classic lyrics when Genius line notes are present. */
    fun isGeniusHoldHintSeen(c: Context): Boolean =
        prefs(c).getBoolean(KEY_GENIUS_HOLD_HINT_SEEN, false)

    fun markGeniusHoldHintSeen(c: Context) {
        prefs(c).edit().putBoolean(KEY_GENIUS_HOLD_HINT_SEEN, true).apply()
    }

    fun init(c: Context) {
        val p = prefs(c)
        keepAwake = p.getBoolean(KEY_KEEP_AWAKE, true)
        playerLyricsCard = p.getBoolean(KEY_PLAYER_LYRICS_CARD, true)
        LyricsApi.wordSync = p.getBoolean(KEY_WORD_SYNC, true)
    }

    override fun enforcePolicy(context: Context, policy: ParentalPolicy) {
        if (!policy.lyrics.enabled) {
            LyricsApi.forgetAll()
        }
    }
}
