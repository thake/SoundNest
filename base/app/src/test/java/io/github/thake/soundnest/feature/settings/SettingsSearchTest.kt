package io.github.thake.soundnest.feature.settings

import io.github.thake.soundnest.feature.localfiles.settings.LocalFilesParentalPolicy
import io.github.thake.soundnest.feature.lyrics.settings.LyricsParentalPolicy
import io.github.thake.soundnest.feature.radio.settings.RadioParentalPolicy
import io.github.thake.soundnest.feature.spokenword.settings.SpokenWordParentalPolicy

import io.github.thake.soundnest.core.preferences.SettingsEntry
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
        val entries = listOf(
            SettingsEntry(3, 4, immersivePage = "Display"),
            SettingsEntry(1, 2, immersivePage = "Lyrics"),
            SettingsEntry(5, 0, immersivePage = "Root"),
        )
        val hits = SettingsSearch.search(entries, "lyrics") { text.getValue(it) }
        assertEquals(listOf(1, 3), hits.map { it.title })
    }

    @Test fun everyPlaceIsReachable() {
        val pages = setOf("Root", "Playback", "Lyrics", "StreamingWifi", "StreamingCell", "Crossfade",
            "Storage", "Data", "Display", "Look", "Rotation", "Language", "LyricsLanguage", "App", "Accounts", "Library")
        assertTrue(SettingsSearch.immersive.all { it.immersivePage in pages })
        assertTrue(SettingsSearch.classic.all { it.classicPlace == "transitions" || it.classicPlace?.toIntOrNull() in 0..6 })
    }

    /** Accounts, storage, and library rows sit on their own pages/tabs. */
    @Test fun rowsSitWithWhatTheyAreAbout() {
        fun immersivePlace(title: Int) = SettingsSearch.immersive.first { it.title == title }.immersivePage
        fun classicPlace(title: Int) = SettingsSearch.classic.first { it.title == title }.classicPlace
        assertEquals("Accounts", immersivePlace(io.github.thake.soundnest.R.string.accounts_title))
        assertEquals("Storage", immersivePlace(io.github.thake.soundnest.R.string.settings_storage_clear_cache))
        assertEquals("Library", immersivePlace(io.github.thake.soundnest.R.string.hidden_songs_title))
        assertEquals("5", classicPlace(io.github.thake.soundnest.R.string.accounts_title))
        assertEquals("2", classicPlace(io.github.thake.soundnest.R.string.settings_storage_clear_cache))
        assertEquals("6", classicPlace(io.github.thake.soundnest.R.string.live_radio_settings_title))
    }

    @Test fun filterForPolicyHidesRowsDisabledByParentalPolicy() {
        val R = io.github.thake.soundnest.R::class.java
        val defaultPolicy = io.github.thake.soundnest.feature.parental.settings.ParentalPolicy()
        val defaultTitles = SettingsSearch.filterForPolicy(SettingsSearch.classic, defaultPolicy).map { it.title }.toSet()
        // Defaults: Autoplay & Lyrics & WhatsNew on; Podcasts, Audiobooks, LocalFiles, LiveRadio, Genius, ExtraLyrics off.
        assertTrue(io.github.thake.soundnest.R.string.settings_autoplay_title in defaultTitles)
        assertTrue(io.github.thake.soundnest.R.string.lyrics_word_sync_title in defaultTitles)
        assertFalse(io.github.thake.soundnest.R.string.settings_local_files_shown in defaultTitles)
        assertFalse(io.github.thake.soundnest.R.string.live_radio_settings_title in defaultTitles)
        assertFalse(io.github.thake.soundnest.R.string.equalizer_title in defaultTitles)
        assertFalse(io.github.thake.soundnest.R.string.settings_genius_title in defaultTitles)
        assertFalse(io.github.thake.soundnest.R.string.lyrics_extra_title in defaultTitles)
        assertFalse(io.github.thake.soundnest.R.string.settings_automix_segue in defaultTitles)
        assertFalse(io.github.thake.soundnest.R.string.settings_automix_harmonic in defaultTitles)
        assertFalse(io.github.thake.soundnest.R.string.settings_automix_cache in defaultTitles)
        assertFalse(io.github.thake.soundnest.R.string.settings_automix_podcasts in defaultTitles)
        assertFalse(io.github.thake.soundnest.R.string.settings_headset_skip_title in defaultTitles)

        // Podcasts alone without Local Files still hides AutoMix podcast mixing.
        val podcastsWithoutLocalTitles = SettingsSearch.filterForPolicy(
            SettingsSearch.classic,
            defaultPolicy.copy(spokenWord = SpokenWordParentalPolicy(allowPodcasts = true), localFiles = LocalFilesParentalPolicy(enabled = false)),
        ).map { it.title }.toSet()
        assertFalse(io.github.thake.soundnest.R.string.settings_automix_segue in podcastsWithoutLocalTitles)
        assertFalse(io.github.thake.soundnest.R.string.settings_automix_podcasts in podcastsWithoutLocalTitles)
        assertTrue(io.github.thake.soundnest.R.string.settings_headset_skip_title in podcastsWithoutLocalTitles)

        // Live Radio alone also enables Equalizer in Settings Search.
        val radioOnlyTitles = SettingsSearch.filterForPolicy(
            SettingsSearch.classic,
            defaultPolicy.copy(radio = RadioParentalPolicy(enabled = true)),
        ).map { it.title }.toSet()
        assertTrue(io.github.thake.soundnest.R.string.equalizer_title in radioOnlyTitles)
        assertTrue(io.github.thake.soundnest.R.string.live_radio_settings_title in radioOnlyTitles)

        // Strict Allowlist + Lyrics disabled hides Autoplay, New release notify, and all Lyrics rows,
        // while LocalFiles + Podcasts enable AutoMix and spoken-word rows.
        val strictNoLyrics = defaultPolicy.copy(
            filterMode = io.github.thake.soundnest.feature.parental.settings.FilterMode.STRICT_ALLOWLIST,
            lyrics = LyricsParentalPolicy(enabled = false),
            localFiles = LocalFilesParentalPolicy(enabled = true),
            spokenWord = SpokenWordParentalPolicy(allowPodcasts = true),
            home = defaultPolicy.home.copy(showNewReleases = false),
        )
        val strictTitles = SettingsSearch.filterForPolicy(SettingsSearch.immersive, strictNoLyrics).map { it.title }.toSet()
        assertFalse(io.github.thake.soundnest.R.string.settings_autoplay_title in strictTitles)
        assertFalse(io.github.thake.soundnest.R.string.settings_release_notify in strictTitles)
        assertFalse(io.github.thake.soundnest.R.string.settings_section_lyrics in strictTitles)
        assertFalse(io.github.thake.soundnest.R.string.lyrics_word_sync_title in strictTitles)
        assertTrue(io.github.thake.soundnest.R.string.settings_local_files_shown in strictTitles)
        assertTrue(io.github.thake.soundnest.R.string.equalizer_title in strictTitles)
        assertTrue(io.github.thake.soundnest.R.string.settings_automix_segue in strictTitles)
        assertTrue(io.github.thake.soundnest.R.string.settings_automix_harmonic in strictTitles)
        assertTrue(io.github.thake.soundnest.R.string.settings_automix_cache in strictTitles)
        assertTrue(io.github.thake.soundnest.R.string.settings_automix_podcasts in strictTitles)
        assertTrue(io.github.thake.soundnest.R.string.settings_headset_skip_title in strictTitles)
    }
}
