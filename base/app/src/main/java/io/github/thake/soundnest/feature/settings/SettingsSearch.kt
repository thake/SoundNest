/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.settings

import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.preferences.SettingsEntry
import io.github.thake.soundnest.feature.accounts.settings.AccountsSettingsUi
import io.github.thake.soundnest.feature.appearance.settings.AppearanceSettingsUi
import io.github.thake.soundnest.feature.library.settings.LibrarySettingsUi
import io.github.thake.soundnest.feature.lyrics.settings.LyricsSettingsUi
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy
import io.github.thake.soundnest.feature.player.settings.PlaybackSettingsUi
import io.github.thake.soundnest.feature.system.settings.SystemSettingsUi
import java.text.Normalizer

/**
 * Search index for Settings in both Classic and Immersive looks. Each hit opens its target
 * `SettingsPage` (or Parental Controls for the parent management entry).
 */
internal object SettingsSearch {
    private val parentalEntries: List<SettingsEntry> = listOf(
        SettingsEntry(
            title = R.string.parent_settings_title,
            detail = R.string.parental_controls_detail_locked,
            classicPlace = "0",
            immersivePage = "Root",
        ),
    )

    val allEntries: List<SettingsEntry> = listOf(
        *PlaybackSettingsUi.settingsEntries.toTypedArray(),
        *LibrarySettingsUi.settingsEntries.toTypedArray(),
        *LyricsSettingsUi.settingsEntries.toTypedArray(),
        *SystemSettingsUi.settingsEntries.toTypedArray(),
        SettingsEntry(
            title = R.string.settings_section_local_radio,
            detail = R.string.settings_local_radio_row_detail,
            classicPlace = "7",
            immersivePage = "LocalAndRadio",
            isVisible = { it.localFiles.enabled || it.radio.enabled },
        ),
        *AppearanceSettingsUi.settingsEntries.toTypedArray(),
        *AccountsSettingsUi.settingsEntries.toTypedArray(),
        *parentalEntries.toTypedArray(),
    )

    val immersive: List<SettingsEntry>
        get() = allEntries.filter { it.immersivePage != null }

    val classic: List<SettingsEntry>
        get() = allEntries.filter { it.classicPlace != null }

    /** Filters out Kid Settings rows whose self-gated [SettingsEntry.isVisible] predicate is false. */
    fun filterForPolicy(
        entries: List<SettingsEntry>,
        policy: ParentalPolicy,
    ): List<SettingsEntry> = entries.filter { it.isVisible(policy) }

    /** Lower case, without accents, so "lecture" finds "Lecture" and "ecran" finds "écran". */
    fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    /** Every word of [query] is in the title or the description. */
    fun matches(query: String, title: String, detail: String): Boolean {
        val words = normalize(query).split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return false
        val text = normalize("$title $detail")
        return words.all { it in text }
    }

    /** The entries [query] finds, titles first, each title once; [text] reads a string resource. */
    fun search(entries: List<SettingsEntry>, query: String, text: (Int) -> String): List<SettingsEntry> {
        val hits = entries.filter { matches(query, text(it.title), if (it.detail != 0) text(it.detail) else "") }
        val q = normalize(query.trim())
        return hits.sortedBy { if (normalize(text(it.title)).contains(q)) 0 else 1 }.distinctBy { text(it.title) }
    }
}
