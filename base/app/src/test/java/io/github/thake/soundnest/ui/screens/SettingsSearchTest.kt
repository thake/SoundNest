package io.github.thake.soundnest.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchTest {
    @Test fun wordsMatchWithoutCaseOrAccents() {
        assertTrue(SettingsSearch.matches("ecran", "Garder l'écran allumé", ""))
        assertTrue(SettingsSearch.matches("image  IMAGE", "Image dans l'image", "Quitter l'app"))
        assertTrue(SettingsSearch.matches("pip window", "Picture-in-picture", "keeps it in a small window, pip"))
        assertFalse(SettingsSearch.matches("lyrics video", "Music videos", "Clips in the player"))
        assertFalse(SettingsSearch.matches("   ", "Anything", ""))
    }

    @Test fun titlesComeFirstAndOnce() {
        val text = mapOf(1 to "Lyrics", 2 to "Shows the words", 3 to "Video", 4 to "No lyrics while a video plays", 5 to "Lyrics")
        val entries = listOf(SettingsEntry(3, 4, "Display"), SettingsEntry(1, 2, "Lyrics"), SettingsEntry(5, 0, "Root"))
        val hits = SettingsSearch.search(entries, "lyrics") { text.getValue(it) }
        assertEquals(listOf(1, 3), hits.map { it.title })
    }

    @Test fun everyPlaceIsReachable() {
        val pages = setOf("Root", "Playback", "Lyrics", "StreamingWifi", "StreamingCell", "Crossfade",
            "Storage", "Data", "Display", "Look", "Rotation", "Language", "LyricsLanguage", "App", "Accounts", "Library")
        assertTrue(SettingsSearch.immersive.all { it.place in pages })
        assertTrue(SettingsSearch.classic.all { it.place == "transitions" || it.place.toIntOrNull() in 0..6 })
    }

    /** Accounts, storage, and library rows sit on their own pages/tabs. */
    @Test fun rowsSitWithWhatTheyAreAbout() {
        fun place(list: List<SettingsEntry>, title: Int) = list.first { it.title == title }.place
        assertEquals("Accounts", place(SettingsSearch.immersive, io.github.thake.soundnest.R.string.accounts_title))
        assertEquals("Storage", place(SettingsSearch.immersive, io.github.thake.soundnest.R.string.settings_storage_clear_cache))
        assertEquals("Library", place(SettingsSearch.immersive, io.github.thake.soundnest.R.string.hidden_songs_title))
        assertEquals("5", place(SettingsSearch.classic, io.github.thake.soundnest.R.string.accounts_title))
        assertEquals("2", place(SettingsSearch.classic, io.github.thake.soundnest.R.string.settings_storage_clear_cache))
        assertEquals("6", place(SettingsSearch.classic, io.github.thake.soundnest.R.string.live_radio_settings_title))
    }

    @Test fun filterForPolicyHidesRowsDisabledByParentalPolicy() {
        val R = io.github.thake.soundnest.R::class.java
        val defaultPolicy = io.github.thake.soundnest.parental.ParentalPolicy()
        val defaultTitles = SettingsSearch.filterForPolicy(SettingsSearch.classic, defaultPolicy).map { it.title }.toSet()
        // Defaults: Autoplay & Lyrics & WhatsNew on; Podcasts, Audiobooks, LocalFiles, LiveRadio, Genius, ExtraLyrics off.
        assertTrue(io.github.thake.soundnest.R.string.settings_autoplay_title in defaultTitles)
        assertTrue(io.github.thake.soundnest.R.string.lyrics_word_sync_title in defaultTitles)
        assertFalse(io.github.thake.soundnest.R.string.settings_local_files_shown in defaultTitles)
        assertFalse(io.github.thake.soundnest.R.string.live_radio_settings_title in defaultTitles)
        assertFalse(io.github.thake.soundnest.R.string.settings_genius_title in defaultTitles)
        assertFalse(io.github.thake.soundnest.R.string.lyrics_extra_title in defaultTitles)
        assertFalse(io.github.thake.soundnest.R.string.settings_automix_podcasts in defaultTitles)
        assertFalse(io.github.thake.soundnest.R.string.settings_headset_skip_title in defaultTitles)

        // Strict Allowlist + Lyrics disabled hides Autoplay, New release notify, and all Lyrics rows.
        val strictNoLyrics = defaultPolicy.copy(
            filterMode = io.github.thake.soundnest.parental.FilterMode.STRICT_ALLOWLIST,
            allowLyrics = false,
            allowLocalFiles = true,
            allowPodcasts = true,
            homeSections = defaultPolicy.homeSections.copy(showNewReleases = false),
        )
        val strictTitles = SettingsSearch.filterForPolicy(SettingsSearch.immersive, strictNoLyrics).map { it.title }.toSet()
        assertFalse(io.github.thake.soundnest.R.string.settings_autoplay_title in strictTitles)
        assertFalse(io.github.thake.soundnest.R.string.settings_release_notify in strictTitles)
        assertFalse(io.github.thake.soundnest.R.string.settings_section_lyrics in strictTitles)
        assertFalse(io.github.thake.soundnest.R.string.lyrics_word_sync_title in strictTitles)
        assertTrue(io.github.thake.soundnest.R.string.settings_local_files_shown in strictTitles)
        assertTrue(io.github.thake.soundnest.R.string.settings_automix_podcasts in strictTitles)
        assertTrue(io.github.thake.soundnest.R.string.settings_headset_skip_title in strictTitles)
    }
}
