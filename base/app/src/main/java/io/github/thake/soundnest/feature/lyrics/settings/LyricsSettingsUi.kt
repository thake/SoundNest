/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.lyrics.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.preferences.SettingsEntry
import io.github.thake.soundnest.core.ui.settings.M3SectionScope
import io.github.thake.soundnest.core.ui.settings.M3SettingsChoiceRow
import io.github.thake.soundnest.core.ui.settings.M3SettingsNavRow
import io.github.thake.soundnest.core.ui.settings.M3SettingsPalette
import io.github.thake.soundnest.core.ui.settings.M3SettingsScaffold
import io.github.thake.soundnest.core.ui.settings.M3SettingsSwitchRow
import io.github.thake.soundnest.core.ui.settings.m3SettingsSection
import io.github.thake.soundnest.feature.appearance.settings.AppLanguage
import io.github.thake.soundnest.feature.appearance.settings.Look
import io.github.thake.soundnest.feature.lyrics.data.LyricsApi
import io.github.thake.soundnest.feature.lyrics.genius.settings.GeniusSettings
import io.github.thake.soundnest.feature.lyrics.translation.settings.LyricsTranslationSettings
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy
import io.github.thake.soundnest.feature.parental.settings.rememberParentalPolicy

/**
 * Unified Material Design 3 Settings UI surfaces and Settings Search entries for Lyrics,
 * Genius Song Meanings, and Lyric Translation / Romanization.
 */
object LyricsSettingsUi {
    val settingsEntries: List<SettingsEntry> = listOf(
        SettingsEntry(
            title = R.string.lyrics_word_sync_title,
            classicPlace = "1",
            immersivePage = "Lyrics",
            isVisible = { it.lyrics.enabled },
        ),
        SettingsEntry(
            title = R.string.lyrics_extra_title,
            detail = R.string.lyrics_extra_summary,
            classicPlace = "1",
            immersivePage = "Lyrics",
            isVisible = { it.lyrics.enabled && it.lyrics.allowExtraSources },
        ),
        SettingsEntry(
            title = R.string.settings_genius_title,
            detail = R.string.settings_genius_detail,
            classicPlace = "1",
            immersivePage = "Lyrics",
            isVisible = { it.lyrics.enabled && it.lyrics.allowGeniusMeanings },
        ),
        SettingsEntry(
            title = R.string.settings_lyrics_keep_awake_title,
            detail = R.string.settings_lyrics_keep_awake_detail,
            classicPlace = "1",
            immersivePage = "Lyrics",
            isVisible = { it.lyrics.enabled },
        ),
        SettingsEntry(
            title = R.string.lyrics_romanization,
            detail = R.string.settings_lyrics_romanization_detail,
            classicPlace = "1",
            immersivePage = "Lyrics",
            isVisible = { it.lyrics.enabled },
        ),
        SettingsEntry(
            title = R.string.lyrics_translation_title,
            detail = R.string.lyrics_translation_detail,
            classicPlace = "1",
            immersivePage = "Lyrics",
            isVisible = { it.lyrics.enabled },
        ),
        SettingsEntry(
            title = R.string.settings_section_lyrics,
            classicPlace = "1",
            immersivePage = "Lyrics",
            isVisible = { it.lyrics.enabled },
        ),
    )

    val displaySettingsEntries: List<SettingsEntry> = listOf(
        SettingsEntry(
            title = R.string.settings_player_lyrics_card_title,
            detail = R.string.settings_player_lyrics_card_detail,
            classicPlace = "3",
            immersivePage = "Display",
            isVisible = { it.lyrics.enabled },
        ),
    )

    fun M3SectionScope.m3PlayerLyricsCardRow(
        context: Context,
        look: Look,
        policy: ParentalPolicy,
        palette: M3SettingsPalette,
    ) {
        if (look == Look.CLASSIC && policy.lyrics.enabled) {
            row {
                M3SettingsSwitchRow(
                    title = stringResource(R.string.settings_player_lyrics_card_title),
                    detail = stringResource(R.string.settings_player_lyrics_card_detail),
                    checked = LyricsSettings.playerLyricsCard,
                    palette = palette,
                    onCheckedChange = { LyricsSettings.setPlayerLyricsCard(context, it) },
                )
            }
        }
    }

    @Composable
    fun M3LyricsPage(openLyricsLanguage: () -> Unit, back: () -> Unit) {
        val ctx = LocalContext.current
        val policy = rememberParentalPolicy()
        LaunchedEffect(policy.lyrics.enabled) {
            if (!policy.lyrics.enabled) back()
        }
        var wordSync by remember { mutableStateOf(LyricsSettings.isWordSync(ctx)) }
        var lyricsExtra by remember { mutableStateOf(LyricsTranslationSettings.isExtraSources(ctx)) }
        val translateTo = LyricsTranslationSettings.getTranslateLang(ctx)
        val translateLabel = if (translateTo.isEmpty()) stringResource(R.string.language_auto)
        else AppLanguage.nativeName(translateTo)

        M3SettingsScaffold(
            title = stringResource(R.string.settings_section_lyrics),
            onBack = back,
        ) { palette ->
            m3SettingsSection(
                key = "lyrics:sync",
                palette = palette,
                header = ctx.getString(R.string.settings_lyrics_section_sync),
            ) {
                row {
                    M3SettingsSwitchRow(
                        title = stringResource(R.string.lyrics_word_sync_title),
                        detail = stringResource(if (wordSync) R.string.lyrics_word_sync_on else R.string.lyrics_word_sync_off),
                        checked = wordSync,
                        palette = palette,
                        onCheckedChange = {
                            wordSync = it
                            LyricsSettings.setWordSync(ctx, it)
                        },
                    )
                }
                if (policy.lyrics.allowExtraSources) {
                    row {
                        M3SettingsSwitchRow(
                            title = stringResource(R.string.lyrics_extra_title),
                            detail = stringResource(R.string.lyrics_extra_summary),
                            checked = lyricsExtra,
                            palette = palette,
                            onCheckedChange = {
                                lyricsExtra = it
                                LyricsTranslationSettings.setExtraSources(ctx, it)
                                LyricsApi.forgetAll()
                            },
                        )
                    }
                }
                if (policy.lyrics.allowGeniusMeanings) {
                    row {
                        M3SettingsSwitchRow(
                            title = stringResource(R.string.settings_genius_title),
                            detail = stringResource(R.string.settings_genius_detail),
                            checked = GeniusSettings.on,
                            palette = palette,
                            onCheckedChange = { GeniusSettings.set(ctx, it) },
                        )
                    }
                }
            }

            m3SettingsSection(
                key = "lyrics:reading",
                palette = palette,
                header = ctx.getString(R.string.settings_lyrics_section_reading),
            ) {
                row {
                    M3SettingsSwitchRow(
                        title = stringResource(R.string.settings_lyrics_keep_awake_title),
                        detail = stringResource(R.string.settings_lyrics_keep_awake_detail),
                        checked = LyricsSettings.keepAwake,
                        palette = palette,
                        onCheckedChange = { LyricsSettings.setKeepAwake(ctx, it) },
                    )
                }
                row {
                    M3SettingsSwitchRow(
                        title = stringResource(R.string.lyrics_romanization),
                        detail = stringResource(R.string.settings_lyrics_romanization_detail),
                        checked = LyricsTranslationSettings.romanization,
                        palette = palette,
                        onCheckedChange = { LyricsTranslationSettings.setRomanization(ctx, it) },
                    )
                }
                row {
                    M3SettingsNavRow(
                        title = stringResource(R.string.lyrics_translation_title),
                        detail = stringResource(R.string.lyrics_translation_detail),
                        value = translateLabel,
                        palette = palette,
                        onClick = openLyricsLanguage,
                    )
                }
            }
        }
    }

    @Composable
    fun M3LyricsLanguagePage(back: () -> Unit) {
        val ctx = LocalContext.current
        var current by remember { mutableStateOf(LyricsTranslationSettings.getTranslateLang(ctx)) }
        val auto = stringResource(R.string.language_auto)
        fun label(tag: String) = if (tag == AppLanguage.SYSTEM) auto else AppLanguage.nativeName(tag)

        M3SettingsScaffold(
            title = stringResource(R.string.lyrics_translation_title),
            onBack = back,
        ) { palette ->
            m3SettingsSection(
                key = "lyrics:lang-choices",
                palette = palette,
                footer = ctx.getString(R.string.lyrics_translation_detail),
            ) {
                AppLanguage.AVAILABLE.forEach { tag ->
                    row {
                        M3SettingsChoiceRow(
                            title = label(tag),
                            selected = tag == current,
                            palette = palette,
                            onClick = {
                                current = tag
                                LyricsTranslationSettings.setTranslateLang(ctx, tag)
                            },
                        )
                    }
                }
            }
        }
    }
}
