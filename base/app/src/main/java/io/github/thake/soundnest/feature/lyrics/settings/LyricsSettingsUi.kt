/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.lyrics.settings

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.preferences.SettingsEntry
import io.github.thake.soundnest.core.ui.classic.AppAccent
import io.github.thake.soundnest.core.ui.classic.AppMuted
import io.github.thake.soundnest.core.ui.classic.AppText
import io.github.thake.soundnest.core.ui.classic.tone
import io.github.thake.soundnest.core.ui.immersive.pages.ImmersiveChoiceRow
import io.github.thake.soundnest.core.ui.immersive.pages.ImmersiveGroupRow
import io.github.thake.soundnest.core.ui.immersive.pages.ImmersiveGroupScope
import io.github.thake.soundnest.core.ui.immersive.pages.ImmersivePage
import io.github.thake.soundnest.core.ui.immersive.pages.ImmersiveSwitchRow
import io.github.thake.soundnest.core.ui.immersive.pages.immersiveGroup
import io.github.thake.soundnest.feature.appearance.settings.AppLanguage
import io.github.thake.soundnest.feature.appearance.settings.Look
import io.github.thake.soundnest.feature.lyrics.data.LyricsApi
import io.github.thake.soundnest.feature.lyrics.genius.settings.GeniusSettings
import io.github.thake.soundnest.feature.lyrics.translation.settings.LyricsTranslationSettings
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy
import io.github.thake.soundnest.feature.parental.settings.rememberParentalPolicy
import io.github.thake.soundnest.core.ui.classic.OptionRow

/**
 * Classic & Immersive Kid Settings UI surfaces and Settings Search entries for Lyrics,
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

    @Composable
    fun ClassicLyricsTabContent(policy: ParentalPolicy) {
        if (!policy.lyrics.enabled) return
        val ctx = LocalContext.current
        var wordSync by remember { mutableStateOf(LyricsSettings.isWordSync(ctx)) }
        OptionRow(
            label = stringResource(R.string.lyrics_word_sync_title),
            detail = stringResource(if (wordSync) R.string.lyrics_word_sync_on else R.string.lyrics_word_sync_off),
            checked = wordSync,
            onChecked = {
                wordSync = it
                LyricsSettings.setWordSync(ctx, it)
            },
        )
        if (policy.lyrics.allowExtraSources) {
            var lyricsExtra by remember { mutableStateOf(LyricsTranslationSettings.isExtraSources(ctx)) }
            OptionRow(
                label = stringResource(R.string.lyrics_extra_title),
                detail = stringResource(R.string.lyrics_extra_summary),
                checked = lyricsExtra,
                onChecked = {
                    lyricsExtra = it
                    LyricsTranslationSettings.setExtraSources(ctx, it)
                    LyricsApi.forgetAll()
                },
            )
        }
        if (policy.lyrics.allowGeniusMeanings) {
            OptionRow(
                label = stringResource(R.string.settings_genius_title),
                detail = stringResource(R.string.settings_genius_detail),
                checked = GeniusSettings.on,
                onChecked = { GeniusSettings.set(ctx, it) },
            )
        }
        OptionRow(
            label = stringResource(R.string.settings_lyrics_keep_awake_title),
            detail = stringResource(R.string.settings_lyrics_keep_awake_detail),
            checked = LyricsSettings.keepAwake,
            onChecked = { LyricsSettings.setKeepAwake(ctx, it) },
        )
        OptionRow(
            label = stringResource(R.string.lyrics_romanization),
            detail = stringResource(R.string.settings_lyrics_romanization_detail),
            checked = LyricsTranslationSettings.romanization,
            onChecked = { LyricsTranslationSettings.setRomanization(ctx, it) },
        )
        ClassicLyricsTranslationRow()
    }

    @Composable
    fun ClassicPlayerLyricsCardRow(policy: ParentalPolicy) {
        if (!policy.lyrics.enabled) return
        val ctx = LocalContext.current
        OptionRow(
            label = stringResource(R.string.settings_player_lyrics_card_title),
            detail = stringResource(R.string.settings_player_lyrics_card_detail),
            checked = LyricsSettings.playerLyricsCard,
            onChecked = { LyricsSettings.setPlayerLyricsCard(ctx, it) },
        )
    }

    internal fun ImmersiveGroupScope.immersivePlayerLyricsCardRow(
        context: Context,
        look: Look,
        policy: ParentalPolicy,
    ) {
        if (look == Look.CLASSIC && policy.lyrics.enabled) {
            row {
                ImmersiveSwitchRow(
                    stringResource(R.string.settings_player_lyrics_card_title),
                    checked = LyricsSettings.playerLyricsCard,
                    onChecked = { LyricsSettings.setPlayerLyricsCard(context, it) },
                    detail = stringResource(R.string.settings_player_lyrics_card_detail),
                )
            }
        }
    }

    @Composable
    fun ImmersiveLyricsPage(openLyricsLanguage: () -> Unit, back: () -> Unit) {
        val ctx = LocalContext.current
        val policy = rememberParentalPolicy()
        LaunchedEffect(policy.lyrics.enabled) {
            if (!policy.lyrics.enabled) back()
        }
        var wordSync by remember { mutableStateOf(LyricsSettings.isWordSync(ctx)) }
        var lyricsExtra by remember { mutableStateOf(LyricsTranslationSettings.isExtraSources(ctx)) }
        ImmersivePage(title = stringResource(R.string.settings_section_lyrics), onBack = back) {
            immersiveGroup("lyrics") {
                row {
                    ImmersiveSwitchRow(
                        stringResource(R.string.lyrics_word_sync_title),
                        checked = wordSync,
                        onChecked = {
                            wordSync = it
                            LyricsSettings.setWordSync(ctx, it)
                        },
                        detail = stringResource(if (wordSync) R.string.lyrics_word_sync_on else R.string.lyrics_word_sync_off),
                    )
                }
                if (policy.lyrics.allowExtraSources) {
                    row {
                        ImmersiveSwitchRow(
                            stringResource(R.string.lyrics_extra_title),
                            checked = lyricsExtra,
                            onChecked = {
                                lyricsExtra = it
                                LyricsTranslationSettings.setExtraSources(ctx, it)
                                LyricsApi.forgetAll()
                            },
                            detail = stringResource(R.string.lyrics_extra_summary),
                        )
                    }
                }
                if (policy.lyrics.allowGeniusMeanings) {
                    row {
                        ImmersiveSwitchRow(
                            stringResource(R.string.settings_genius_title),
                            checked = GeniusSettings.on,
                            onChecked = { GeniusSettings.set(ctx, it) },
                            detail = stringResource(R.string.settings_genius_detail),
                        )
                    }
                }
                row {
                    ImmersiveSwitchRow(
                        stringResource(R.string.settings_lyrics_keep_awake_title),
                        checked = LyricsSettings.keepAwake,
                        onChecked = { LyricsSettings.setKeepAwake(ctx, it) },
                        detail = stringResource(R.string.settings_lyrics_keep_awake_detail),
                    )
                }
                row {
                    ImmersiveSwitchRow(
                        stringResource(R.string.lyrics_romanization),
                        checked = LyricsTranslationSettings.romanization,
                        onChecked = { LyricsTranslationSettings.setRomanization(ctx, it) },
                        detail = stringResource(R.string.settings_lyrics_romanization_detail),
                    )
                }
                row {
                    val translateTo = LyricsTranslationSettings.getTranslateLang(ctx)
                    ImmersiveGroupRow(
                        stringResource(R.string.lyrics_translation_title),
                        detail = stringResource(R.string.lyrics_translation_detail),
                        value = if (translateTo.isEmpty()) stringResource(R.string.language_auto)
                        else AppLanguage.nativeName(translateTo),
                        chevron = true,
                        onClick = openLyricsLanguage,
                    )
                }
            }
        }
    }

    @Composable
    fun ImmersiveLyricsLanguagePage(back: () -> Unit) {
        val ctx = LocalContext.current
        var current by remember { mutableStateOf(LyricsTranslationSettings.getTranslateLang(ctx)) }
        val auto = stringResource(R.string.language_auto)
        fun label(tag: String) = if (tag == AppLanguage.SYSTEM) auto else AppLanguage.nativeName(tag)
        ImmersivePage(title = stringResource(R.string.lyrics_translation_title), onBack = back) {
            immersiveGroup("choices", footerRes = R.string.lyrics_translation_detail) {
                AppLanguage.AVAILABLE.forEach { tag ->
                    row {
                        ImmersiveChoiceRow(
                            label(tag),
                            selected = tag == current,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClassicLyricsTranslationRow() {
    val ctx = LocalContext.current
    var current by remember { mutableStateOf(LyricsTranslationSettings.getTranslateLang(ctx)) }
    var picking by remember { mutableStateOf(false) }
    val auto = stringResource(R.string.language_auto)
    fun label(tag: String) = if (tag == AppLanguage.SYSTEM) auto else AppLanguage.nativeName(tag)
    Column(
        Modifier.fillMaxWidth().clickable { picking = true }.padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(stringResource(R.string.lyrics_translation_title), color = AppText, fontSize = 16.sp)
        Text(label(current), color = AppMuted, fontSize = 12.sp)
    }
    if (picking) ModalBottomSheet(
        onDismissRequest = { picking = false },
        containerColor = tone(0xFF282828),
    ) {
        LazyColumn(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            item {
                Text(
                    stringResource(R.string.lyrics_translation_title),
                    color = AppText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                Text(
                    stringResource(R.string.lyrics_translation_detail),
                    color = AppMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
                )
            }
            items(AppLanguage.AVAILABLE.size) { i ->
                val tag = AppLanguage.AVAILABLE[i]
                val selected = tag == current
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable {
                        picking = false
                        if (!selected) {
                            current = tag
                            LyricsTranslationSettings.setTranslateLang(ctx, tag)
                        }
                    }.padding(horizontal = 20.dp, vertical = 8.dp),
                ) {
                    Text(
                        label(tag),
                        color = if (selected) AppAccent else AppText,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f),
                    )
                    if (selected) Icon(Icons.Default.Check, null, tint = AppAccent)
                }
            }
        }
    }
}
