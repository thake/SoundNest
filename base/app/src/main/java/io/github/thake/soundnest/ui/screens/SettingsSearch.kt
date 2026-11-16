package io.github.thake.soundnest.ui.screens

import io.github.thake.soundnest.R

/** One row Settings' search finds: its title, its description (0: none) and where it is ([place]). */
internal class SettingsEntry(val title: Int, val detail: Int, val place: String)

/**
 * What the search button of Settings looks through, in both looks: the rows of every page, by
 * their title and description in the app's language. A hit opens the page (Immersive: a
 * `SettingsPage` name) or the tab (Classic: its index, or `transitions` for that page) it is on.
 * Kept by hand with the pages: a row added to a page is added here too.
 */
internal object SettingsSearch {
    val immersive: List<SettingsEntry> = listOf(
        SettingsEntry(R.string.settings_transitions_title, R.string.settings_transitions_detail, "Crossfade"),
        SettingsEntry(R.string.accounts_title, 0, "Accounts"),
        SettingsEntry(R.string.settings_autoplay_title, R.string.settings_autoplay_detail, "Playback"),
        SettingsEntry(R.string.settings_release_notify, R.string.settings_release_notify_detail, "Library"),
        SettingsEntry(R.string.myspoti_shuffle_title, R.string.myspoti_shuffle_detail, "Playback"),
        SettingsEntry(R.string.equalizer_title, R.string.equalizer_settings_detail, "Playback"),
        SettingsEntry(R.string.settings_keep_playing_title, R.string.settings_keep_playing_detail, "Playback"),
        SettingsEntry(R.string.settings_native_audio_title, R.string.settings_native_audio_detail, "Playback"),
        SettingsEntry(R.string.settings_headset_skip_title, R.string.settings_headset_skip_detail, "Playback"),
        SettingsEntry(R.string.live_radio_settings_title, R.string.live_radio_settings_detail, "Library"),
        SettingsEntry(R.string.settings_local_files_shown, R.string.settings_local_files_shown_detail, "Library"),
        SettingsEntry(R.string.settings_section_library, 0, "Library"),
        SettingsEntry(R.string.hidden_songs_title, R.string.hidden_songs_detail, "Library"),
        SettingsEntry(R.string.lyrics_word_sync_title, 0, "Lyrics"),
        SettingsEntry(R.string.lyrics_extra_title, 0, "Lyrics"),
        SettingsEntry(R.string.settings_genius_title, R.string.settings_genius_detail, "Lyrics"),
        SettingsEntry(R.string.settings_lyrics_keep_awake_title, R.string.settings_lyrics_keep_awake_detail, "Lyrics"),
        SettingsEntry(R.string.lyrics_romanization, R.string.settings_lyrics_romanization_detail, "Lyrics"),
        SettingsEntry(R.string.lyrics_translation_title, R.string.lyrics_translation_detail, "Lyrics"),
        SettingsEntry(R.string.settings_automix_segue, R.string.settings_automix_segue_detail, "Crossfade"),
        SettingsEntry(R.string.settings_automix_session, R.string.settings_automix_session_detail, "Crossfade"),
        SettingsEntry(R.string.settings_automix_album_order, R.string.settings_automix_album_order_detail, "Crossfade"),
        SettingsEntry(R.string.settings_automix_podcasts, R.string.settings_automix_podcasts_detail, "Crossfade"),
        SettingsEntry(R.string.settings_automix_harmonic, R.string.settings_automix_harmonic_detail, "Crossfade"),
        SettingsEntry(R.string.settings_automix_eq, R.string.settings_automix_eq_detail, "Crossfade"),
        SettingsEntry(R.string.settings_automix_show, R.string.settings_automix_show_detail, "Crossfade"),
        SettingsEntry(R.string.settings_automix_mobile_data, R.string.settings_automix_mobile_data_detail, "Crossfade"),
        SettingsEntry(R.string.settings_automix_cache, 0, "Crossfade"),
        SettingsEntry(R.string.settings_storage_clear_cache, 0, "Storage"),
        SettingsEntry(R.string.energy_saver_title, R.string.energy_saver_summary, "Data"),
        SettingsEntry(R.string.data_saver_title, R.string.data_saver_summary, "Data"),
        SettingsEntry(R.string.background_playback_title, 0, "Data"),
        SettingsEntry(R.string.settings_look_title, 0, "Display"),
        SettingsEntry(R.string.settings_dynamic_color_title, R.string.settings_dynamic_color_detail, "Display"),
        SettingsEntry(R.string.settings_system_font_title, R.string.settings_system_font_detail, "Display"),
        SettingsEntry(R.string.settings_liquid_glass_title, R.string.settings_liquid_glass_detail, "Display"),
        SettingsEntry(R.string.settings_amoled_theme, R.string.settings_amoled_theme_subtitle, "Display"),
        SettingsEntry(R.string.settings_player_lyrics_card_title, R.string.settings_player_lyrics_card_detail, "Display"),
        SettingsEntry(R.string.settings_player_source_title, R.string.settings_player_source_detail, "Display"),
        SettingsEntry(R.string.settings_player_capsule_title, R.string.settings_player_capsule_detail, "Display"),
        SettingsEntry(R.string.settings_player_modes_title, R.string.settings_player_modes_detail, "Display"),
        SettingsEntry(R.string.settings_player_volume_title, R.string.settings_player_volume_detail, "Display"),
        SettingsEntry(R.string.settings_rotation_title, 0, "Display"),
        SettingsEntry(R.string.settings_item_app_language_title, 0, "Display"),
        SettingsEntry(R.string.creator_biography_section_title, R.string.about_detail, "App"),
        SettingsEntry(R.string.restart_player_title, R.string.restart_player_detail, "App"),
        SettingsEntry(R.string.diagnostic_export_title, R.string.diagnostic_export_detail, "App"),
        SettingsEntry(R.string.settings_section_playback, 0, "Playback"),
        SettingsEntry(R.string.settings_section_lyrics, 0, "Lyrics"),
        SettingsEntry(R.string.settings_section_display, 0, "Display"),
        SettingsEntry(R.string.settings_section_data, 0, "Data"),
        SettingsEntry(R.string.settings_section_storage, 0, "Storage"),
        SettingsEntry(R.string.settings_section_app, 0, "App"),
        SettingsEntry(R.string.supporter_accent, 0, "Display"),
    )

    val classic: List<SettingsEntry> = listOf(
        SettingsEntry(R.string.settings_autoplay_title, R.string.settings_autoplay_detail, "0"),
        SettingsEntry(R.string.settings_release_notify, R.string.settings_release_notify_detail, "6"),
        SettingsEntry(R.string.myspoti_shuffle_title, R.string.myspoti_shuffle_detail, "0"),
        SettingsEntry(R.string.settings_keep_playing_title, R.string.settings_keep_playing_detail, "0"),
        SettingsEntry(R.string.settings_native_audio_title, R.string.settings_native_audio_detail, "0"),
        SettingsEntry(R.string.settings_headset_skip_title, R.string.settings_headset_skip_detail, "0"),
        SettingsEntry(R.string.live_radio_settings_title, R.string.live_radio_settings_detail, "6"),
        SettingsEntry(R.string.settings_local_files_shown, R.string.settings_local_files_shown_detail, "6"),
        SettingsEntry(R.string.hidden_songs_title, R.string.hidden_songs_detail, "6"),
        SettingsEntry(R.string.settings_section_library, 0, "6"),
        SettingsEntry(R.string.energy_saver_title, R.string.energy_saver_summary, "0"),
        SettingsEntry(R.string.data_saver_title, R.string.data_saver_summary, "0"),
        SettingsEntry(R.string.background_playback_title, 0, "0"),
        SettingsEntry(R.string.lyrics_word_sync_title, 0, "1"),
        SettingsEntry(R.string.lyrics_extra_title, R.string.lyrics_extra_summary, "1"),
        SettingsEntry(R.string.settings_genius_title, R.string.settings_genius_detail, "1"),
        SettingsEntry(R.string.settings_lyrics_keep_awake_title, R.string.settings_lyrics_keep_awake_detail, "1"),
        SettingsEntry(R.string.lyrics_romanization, R.string.settings_lyrics_romanization_detail, "1"),
        SettingsEntry(R.string.lyrics_translation_title, R.string.lyrics_translation_detail, "1"),
        SettingsEntry(R.string.settings_storage_clear_cache, 0, "2"),
        SettingsEntry(R.string.settings_amoled_theme, R.string.settings_amoled_theme_subtitle, "3"),
        SettingsEntry(R.string.settings_player_lyrics_card_title, R.string.settings_player_lyrics_card_detail, "3"),
        SettingsEntry(R.string.settings_player_source_title, R.string.settings_player_source_detail, "3"),
        SettingsEntry(R.string.settings_solid_nav_title, R.string.settings_solid_nav_detail, "3"),
        SettingsEntry(R.string.settings_dynamic_color_title, R.string.settings_dynamic_color_detail, "3"),
        SettingsEntry(R.string.settings_system_font_title, R.string.settings_system_font_detail, "3"),
        SettingsEntry(R.string.settings_section_playback, 0, "0"),
        SettingsEntry(R.string.settings_section_lyrics, 0, "1"),
        SettingsEntry(R.string.settings_section_storage, 0, "2"),
        SettingsEntry(R.string.settings_tab_appearance, 0, "3"),
        SettingsEntry(R.string.settings_section_display, 0, "3"),
        SettingsEntry(R.string.supporter_accent, 0, "3"),
        SettingsEntry(R.string.settings_look_title, 0, "3"),
        SettingsEntry(R.string.settings_rotation_title, 0, "3"),
        SettingsEntry(R.string.settings_item_app_language_title, 0, "3"),
        SettingsEntry(R.string.settings_tab_about, 0, "4"),
        SettingsEntry(R.string.settings_section_app, 0, "4"),
        SettingsEntry(R.string.creator_biography_section_title, R.string.about_detail, "4"),
        SettingsEntry(R.string.restart_player_title, R.string.restart_player_detail, "4"),
        SettingsEntry(R.string.diagnostic_export_title, R.string.diagnostic_export_detail, "4"),
        SettingsEntry(R.string.accounts_title, 0, "5"),
        SettingsEntry(R.string.settings_transitions_title, 0, "transitions"),
        SettingsEntry(R.string.equalizer_title, 0, "0"),
        SettingsEntry(R.string.settings_section_data, 0, "0"),
        SettingsEntry(R.string.settings_automix_segue, R.string.settings_automix_segue_detail, "transitions"),
        SettingsEntry(R.string.settings_automix_session, R.string.settings_automix_session_detail, "transitions"),
        SettingsEntry(R.string.settings_automix_album_order, R.string.settings_automix_album_order_detail, "transitions"),
        SettingsEntry(R.string.settings_automix_podcasts, R.string.settings_automix_podcasts_detail, "transitions"),
        SettingsEntry(R.string.settings_automix_harmonic, R.string.settings_automix_harmonic_detail, "transitions"),
        SettingsEntry(R.string.settings_automix_eq, R.string.settings_automix_eq_detail, "transitions"),
        SettingsEntry(R.string.settings_automix_show, R.string.settings_automix_show_detail, "transitions"),
        SettingsEntry(R.string.settings_automix_mobile_data, R.string.settings_automix_mobile_data_detail, "transitions"),
        SettingsEntry(R.string.settings_automix_cache, 0, "transitions"),
    )

    private val lyricsTitles = setOf(
        R.string.settings_section_lyrics,
        R.string.lyrics_word_sync_title,
        R.string.lyrics_extra_title,
        R.string.settings_genius_title,
        R.string.settings_lyrics_keep_awake_title,
        R.string.lyrics_romanization,
        R.string.lyrics_translation_title,
    )

    /** Filters out Kid Settings rows whose corresponding parent FeatureGate is disabled. */
    fun filterForPolicy(
        entries: List<SettingsEntry>,
        policy: io.github.thake.soundnest.parental.ParentalPolicy,
    ): List<SettingsEntry> = entries.filter { entry ->
        when (entry.title) {
            R.string.settings_autoplay_title -> policy.effectiveAllowAutoplayAndRadio
            R.string.settings_release_notify -> io.github.thake.soundnest.parental.HomeSectionFilter.isWhatsNewAllowed(policy)
            R.string.settings_local_files_shown -> policy.allowLocalFiles
            R.string.live_radio_settings_title -> policy.allowLiveRadio
            R.string.lyrics_extra_title -> policy.allowLyrics && policy.allowExtraLyricSources
            R.string.settings_genius_title -> policy.allowLyrics && policy.allowGeniusMeanings
            R.string.settings_automix_podcasts -> policy.allowPodcasts
            R.string.settings_headset_skip_title -> policy.allowPodcasts || policy.allowAudiobooks
            in lyricsTitles -> policy.allowLyrics
            else -> true
        }
    }

    /** Lower case, without accents, so "lecture" finds "Lecture" and "ecran" finds "écran". */
    fun normalize(text: String): String =
        java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

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
