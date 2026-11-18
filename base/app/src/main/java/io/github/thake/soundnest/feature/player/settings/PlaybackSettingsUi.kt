/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.player.settings

import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.playback.NativeAudio
import io.github.thake.soundnest.core.preferences.SettingsEntry
import io.github.thake.soundnest.core.ui.settings.M3SectionScope
import io.github.thake.soundnest.core.ui.settings.M3SettingsChoiceRow
import io.github.thake.soundnest.core.ui.settings.M3SettingsNavRow
import io.github.thake.soundnest.core.ui.settings.M3SettingsPalette
import io.github.thake.soundnest.core.ui.settings.M3SettingsScaffold
import io.github.thake.soundnest.core.ui.settings.M3SettingsSwitchRow
import io.github.thake.soundnest.core.ui.settings.m3SettingsSection
import io.github.thake.soundnest.feature.appearance.settings.Look
import io.github.thake.soundnest.feature.equalizer.settings.EqualizerSettingsUi
import io.github.thake.soundnest.feature.localfiles.automix.settings.AutoMixSettings
import io.github.thake.soundnest.feature.localfiles.automix.settings.AutoMixSettingsUi
import io.github.thake.soundnest.feature.parental.settings.rememberParentalPolicy
import io.github.thake.soundnest.feature.player.settings.TransitionPref.Mode as TransitionMode
import io.github.thake.soundnest.feature.spokenword.settings.SpokenWordSettingsUi
import io.github.thake.soundnest.host.android.PlaybackService
import io.github.thake.soundnest.spotify.AccountPlan

private val StreamingChoices = listOf(
    Triple(StreamingQuality.AUTO, R.string.quality_streaming_auto, R.string.quality_streaming_auto_detail),
    Triple(StreamingQuality.LOW, R.string.quality_streaming_low, R.string.quality_streaming_low_detail),
    Triple(StreamingQuality.NORMAL, R.string.quality_streaming_normal, R.string.quality_streaming_normal_detail),
    Triple(StreamingQuality.HIGH, R.string.quality_streaming_high, R.string.quality_streaming_high_detail),
    Triple(StreamingQuality.VERY_HIGH, R.string.quality_streaming_very_high, R.string.quality_streaming_very_high_detail),
)

/**
 * Unified Material Design 3 playback settings surfaces (Playback, Streaming Quality,
 * Transitions & AutoMix), player display toggles, and Settings Search entries.
 */
object PlaybackSettingsUi {
    val settingsEntries: List<SettingsEntry> = listOf(
        SettingsEntry(
            title = R.string.quality_streaming_wifi_title,
            detail = R.string.quality_streaming_detail,
            classicPlace = "0",
            immersivePage = "StreamingWifi",
        ),
        SettingsEntry(
            title = R.string.quality_streaming_cell_title,
            detail = R.string.quality_streaming_detail,
            classicPlace = "0",
            immersivePage = "StreamingCell",
        ),
        SettingsEntry(
            title = R.string.settings_transitions_title,
            detail = R.string.settings_transitions_detail_soundnest,
            classicPlace = "transitions",
            immersivePage = "Crossfade",
        ),
        SettingsEntry(
            title = R.string.settings_autoplay_title,
            detail = R.string.settings_autoplay_detail,
            classicPlace = "0",
            immersivePage = "Playback",
            isVisible = { it.effectiveAllowAutoplayAndRadio },
        ),
        *EqualizerSettingsUi.settingsEntries.toTypedArray(),
        SettingsEntry(
            title = R.string.settings_keep_playing_title,
            detail = R.string.settings_keep_playing_detail,
            classicPlace = "0",
            immersivePage = "Playback",
        ),
        SettingsEntry(
            title = R.string.settings_native_audio_title,
            detail = R.string.settings_native_audio_detail,
            classicPlace = "0",
            immersivePage = "Playback",
        ),
        *SpokenWordSettingsUi.settingsEntries.toTypedArray(),
        SettingsEntry(
            title = R.string.settings_section_playback,
            classicPlace = "0",
            immersivePage = "Playback",
        ),
        *AutoMixSettingsUi.settingsEntries.toTypedArray(),
    )

    val displaySettingsEntries: List<SettingsEntry> = listOf(
        SettingsEntry(
            title = R.string.settings_player_source_title,
            detail = R.string.settings_player_source_detail,
            classicPlace = "3",
            immersivePage = "Display",
        ),
        SettingsEntry(
            title = R.string.settings_player_capsule_title,
            detail = R.string.settings_player_capsule_detail,
            immersivePage = "Display",
        ),
        SettingsEntry(
            title = R.string.settings_player_modes_title,
            detail = R.string.settings_player_modes_detail_soundnest,
            immersivePage = "Display",
        ),
        SettingsEntry(
            title = R.string.settings_player_volume_title,
            detail = R.string.settings_player_volume_detail,
            immersivePage = "Display",
        ),
    )

    /** Off, a crossfade of N s or AutoMix: the value of the Transitions rows. */
    @Composable
    fun transitionValue(crossfade: Int): String = when (TransitionPref.mode) {
        TransitionMode.AUTOMIX -> stringResource(R.string.settings_automix_title)
        TransitionMode.CROSSFADE -> stringResource(R.string.settings_transition_crossfade_value, crossfade)
        TransitionMode.OFF -> stringResource(R.string.settings_crossfade_off)
    }

    fun M3SectionScope.m3PlayerDisplayRows(
        ctx: Context,
        look: Look,
        palette: M3SettingsPalette,
    ) {
        row {
            M3SettingsSwitchRow(
                title = stringResource(R.string.settings_player_source_title),
                detail = stringResource(R.string.settings_player_source_detail),
                checked = PlayerSourceLinePref.on,
                palette = palette,
                onCheckedChange = { PlayerSourceLinePref.set(ctx, it) },
            )
        }
        if (look == Look.IMMERSIVE) {
            row {
                M3SettingsSwitchRow(
                    title = stringResource(R.string.settings_player_capsule_title),
                    detail = stringResource(R.string.settings_player_capsule_detail),
                    checked = PlayerCapsulePref.on,
                    palette = palette,
                    onCheckedChange = { PlayerCapsulePref.set(ctx, it) },
                )
            }
            row {
                M3SettingsSwitchRow(
                    title = stringResource(R.string.settings_player_modes_title),
                    detail = stringResource(R.string.settings_player_modes_detail_soundnest),
                    checked = PlayerModesPref.on,
                    palette = palette,
                    onCheckedChange = { PlayerModesPref.set(ctx, it) },
                )
            }
            row {
                M3SettingsSwitchRow(
                    title = stringResource(R.string.settings_player_volume_title),
                    detail = stringResource(R.string.settings_player_volume_detail),
                    checked = PlayerVolumePref.on,
                    palette = palette,
                    onCheckedChange = { PlayerVolumePref.set(ctx, it) },
                )
            }
        }
    }

    @Composable
    fun M3PlaybackPage(
        navController: NavController,
        openStreamingWifi: () -> Unit,
        openStreamingCell: () -> Unit,
        back: () -> Unit,
    ) {
        val ctx = LocalContext.current
        val policy = rememberParentalPolicy()
        val wifi = getStreamingQuality(ctx, false)
        val cell = getStreamingQuality(ctx, true)
        val label = { q: StreamingQuality -> StreamingChoices.first { it.first == q }.second }
        var keepPlaying by remember { mutableStateOf(PlaybackService.keepsPlayingOnClose(ctx)) }
        var nativeAudio by remember { mutableStateOf(NativeAudio.enabled) }

        M3SettingsScaffold(
            title = stringResource(R.string.settings_section_playback),
            onBack = back,
        ) { palette ->
            m3SettingsSection(
                key = "playback:quality",
                palette = palette,
                header = ctx.getString(R.string.settings_playback_section_quality),
                footer = ctx.getString(R.string.quality_streaming_detail),
            ) {
                row {
                    M3SettingsNavRow(
                        title = stringResource(R.string.quality_streaming_wifi_title),
                        value = stringResource(label(wifi)),
                        palette = palette,
                        onClick = openStreamingWifi,
                    )
                }
                row {
                    M3SettingsNavRow(
                        title = stringResource(R.string.quality_streaming_cell_title),
                        value = stringResource(label(cell)),
                        palette = palette,
                        onClick = openStreamingCell,
                    )
                }
            }

            if (policy.effectiveAllowAutoplayAndRadio) {
                m3SettingsSection(
                    key = "playback:queue",
                    palette = palette,
                    header = ctx.getString(R.string.settings_playback_section_queue),
                ) {
                    row {
                        M3SettingsSwitchRow(
                            title = stringResource(R.string.settings_autoplay_title),
                            detail = stringResource(R.string.settings_autoplay_detail),
                            checked = AutoplayPref.on,
                            palette = palette,
                            onCheckedChange = { AutoplayPref.set(ctx, it) },
                        )
                    }
                }
            }

            m3SettingsSection(
                key = "playback:controls",
                palette = palette,
                header = ctx.getString(R.string.settings_section_controls),
            ) {
                row {
                    M3SettingsSwitchRow(
                        title = stringResource(R.string.settings_native_audio_title),
                        detail = stringResource(R.string.settings_native_audio_detail),
                        checked = nativeAudio,
                        palette = palette,
                        onCheckedChange = {
                            nativeAudio = it
                            NativeAudio.set(ctx, it)
                        },
                    )
                }
                row {
                    M3SettingsSwitchRow(
                        title = stringResource(R.string.settings_keep_playing_title),
                        detail = stringResource(R.string.settings_keep_playing_detail),
                        checked = keepPlaying,
                        palette = palette,
                        onCheckedChange = {
                            keepPlaying = it
                            PlaybackService.setKeepsPlayingOnClose(ctx, it)
                        },
                    )
                }
                with(SpokenWordSettingsUi) {
                    m3ControlsRow(ctx, policy, palette)
                }
            }
        }
    }

    @Composable
    fun M3StreamingPage(cellular: Boolean, back: () -> Unit) {
        val ctx = LocalContext.current
        var current by remember(cellular) { mutableStateOf(getStreamingQuality(ctx, cellular)) }
        val premium by AccountPlan.premium.collectAsState()
        val choices = StreamingChoices.filter {
            it.first != StreamingQuality.VERY_HIGH || premium == true || current == StreamingQuality.VERY_HIGH
        }

        M3SettingsScaffold(
            title = stringResource(if (cellular) R.string.quality_streaming_cell_title else R.string.quality_streaming_wifi_title),
            onBack = back,
        ) { palette ->
            m3SettingsSection(
                key = "streaming:choices",
                palette = palette,
                footer = ctx.getString(R.string.quality_streaming_detail),
            ) {
                choices.forEach { (value, labelRes, detailRes) ->
                    row {
                        M3SettingsChoiceRow(
                            title = stringResource(labelRes),
                            detail = stringResource(detailRes),
                            selected = current == value,
                            palette = palette,
                            onClick = {
                                current = value
                                setStreamingQuality(ctx, value, cellular)
                            },
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun M3CrossfadePage(back: () -> Unit) {
        val ctx = LocalContext.current
        val policy = rememberParentalPolicy()
        val rawMode = TransitionPref.mode
        val mode = if (!policy.localFiles.enabled && rawMode == TransitionMode.AUTOMIX) TransitionMode.OFF else rawMode
        var intro by remember { mutableStateOf(false) }
        val (cacheBytes, clearCache) = AutoMixSettingsUi.rememberCacheState()
        val modeChoices = buildList {
            add(Triple(TransitionMode.OFF, R.string.settings_crossfade_off, R.string.settings_transition_off_detail))
            add(Triple(TransitionMode.CROSSFADE, R.string.settings_crossfade_title, R.string.settings_transition_crossfade_detail))
            if (policy.localFiles.enabled) {
                add(Triple(TransitionMode.AUTOMIX, R.string.settings_automix_title, R.string.settings_transition_automix_detail))
            }
        }

        M3SettingsScaffold(
            title = stringResource(R.string.settings_transitions_title),
            onBack = back,
        ) { palette ->
            m3SettingsSection(
                key = "transitions:mode",
                palette = palette,
                header = ctx.getString(R.string.settings_transitions_section_mode),
                footer = ctx.getString(R.string.settings_transitions_detail_soundnest),
            ) {
                for ((m, titleRes, detailRes) in modeChoices) {
                    row {
                        M3SettingsChoiceRow(
                            title = stringResource(titleRes),
                            detail = stringResource(detailRes),
                            selected = mode == m,
                            palette = palette,
                            onClick = {
                                TransitionPref.set(ctx, m)
                                if (m == TransitionMode.AUTOMIX && !AutoMixSettings.introSeen) intro = true
                            },
                        )
                    }
                }
            }

            if (mode == TransitionMode.CROSSFADE) {
                m3SettingsSection(
                    key = "transitions:crossfade-length",
                    palette = palette,
                    footer = ctx.getString(R.string.settings_crossfade_detail_soundnest),
                ) {
                    row {
                        CrossfadeSlider(
                            accent = palette.accent,
                            text = palette.text,
                            muted = palette.muted,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        )
                    }
                }
            }

            if (policy.localFiles.enabled && mode == TransitionMode.AUTOMIX) {
                with(AutoMixSettingsUi) {
                    m3AutoMixSections(
                        ctx = ctx,
                        policy = policy,
                        palette = palette,
                        cacheBytes = cacheBytes,
                        onClearCache = clearCache,
                    )
                }
            }

            if (intro) {
                // Dialog hosted outside lazy items below
            }
        }
        if (intro) {
            val dialogPalette = io.github.thake.soundnest.core.ui.settings.rememberM3SettingsPalette()
            AutoMixSettingsUi.M3AutoMixIntroDialog(palette = dialogPalette) { intro = false }
        }
    }
}
