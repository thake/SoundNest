/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.player.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.playback.NativeAudio
import io.github.thake.soundnest.core.preferences.SettingsEntry
import io.github.thake.soundnest.core.ui.classic.AppAccent
import io.github.thake.soundnest.core.ui.classic.AppMuted
import io.github.thake.soundnest.core.ui.classic.AppText
import io.github.thake.soundnest.core.ui.classic.OptionRow
import io.github.thake.soundnest.core.ui.classic.Section
import io.github.thake.soundnest.core.ui.classic.tone
import io.github.thake.soundnest.core.ui.immersive.Immersive
import io.github.thake.soundnest.core.ui.immersive.pages.ImmersiveChoiceRow
import io.github.thake.soundnest.core.ui.immersive.pages.ImmersiveGroupRow
import io.github.thake.soundnest.core.ui.immersive.pages.ImmersiveGroupScope
import io.github.thake.soundnest.core.ui.immersive.pages.ImmersivePage
import io.github.thake.soundnest.core.ui.immersive.pages.ImmersiveSwitchRow
import io.github.thake.soundnest.core.ui.immersive.pages.immersiveGroup
import io.github.thake.soundnest.core.ui.navigation.Routes
import io.github.thake.soundnest.feature.equalizer.settings.EqualizerSettingsUi
import io.github.thake.soundnest.feature.localfiles.automix.settings.AutoMixSettings
import io.github.thake.soundnest.feature.localfiles.automix.settings.AutoMixSettingsUi
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy
import io.github.thake.soundnest.feature.parental.settings.rememberParentalPolicy
import io.github.thake.soundnest.feature.parental.ui.CompactParentalControlsRow
import io.github.thake.soundnest.feature.player.settings.TransitionPref.Mode as TransitionMode
import io.github.thake.soundnest.feature.spokenword.settings.SpokenWordSettingsUi
import io.github.thake.soundnest.feature.system.settings.SystemSettingsUi
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
 * Classic (`TAB_PLAYBACK` & `SUB_TRANSITIONS`) and Immersive (`PlaybackPage`, `StreamingPage`,
 * `CrossfadePage`) playback settings surfaces, player display toggles, and Settings Search entries.
 */
object PlaybackSettingsUi {
    val settingsEntries: List<SettingsEntry> = listOf(
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
        SettingsEntry(
            title = R.string.myspoti_shuffle_title,
            detail = R.string.myspoti_shuffle_detail,
            classicPlace = "0",
            immersivePage = "Playback",
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

    @Composable
    fun ClassicPlayerDisplayRows() {
        val ctx = LocalContext.current
        OptionRow(
            label = stringResource(R.string.settings_player_source_title),
            detail = stringResource(R.string.settings_player_source_detail),
            checked = PlayerSourceLinePref.on,
            onChecked = { PlayerSourceLinePref.set(ctx, it) },
        )
    }

    internal fun ImmersiveGroupScope.immersivePlayerDisplayRows(ctx: Context, isImmersiveLook: Boolean) {
        row {
            ImmersiveSwitchRow(
                stringResource(R.string.settings_player_source_title),
                checked = PlayerSourceLinePref.on,
                onChecked = { PlayerSourceLinePref.set(ctx, it) },
                detail = stringResource(R.string.settings_player_source_detail),
            )
        }
        if (isImmersiveLook) {
            row {
                ImmersiveSwitchRow(
                    stringResource(R.string.settings_player_capsule_title),
                    checked = PlayerCapsulePref.on,
                    onChecked = { PlayerCapsulePref.set(ctx, it) },
                    detail = stringResource(R.string.settings_player_capsule_detail),
                )
            }
            row {
                ImmersiveSwitchRow(
                    stringResource(R.string.settings_player_modes_title),
                    checked = PlayerModesPref.on,
                    onChecked = { PlayerModesPref.set(ctx, it) },
                    detail = stringResource(R.string.settings_player_modes_detail_soundnest),
                )
            }
            row {
                ImmersiveSwitchRow(
                    stringResource(R.string.settings_player_volume_title),
                    checked = PlayerVolumePref.on,
                    onChecked = { PlayerVolumePref.set(ctx, it) },
                    detail = stringResource(R.string.settings_player_volume_detail),
                )
            }
        }
    }

    @Composable
    fun ClassicPlaybackTabContent(
        navController: NavController,
        policy: ParentalPolicy,
        onOpenTransitions: () -> Unit,
    ) {
        val ctx = LocalContext.current
        CompactParentalControlsRow(
            onOpenParentSettings = { navController.navigate(Routes.ParentSettings.route) },
        )
        HorizontalDivider(color = tone(0xFF2A2A2A))
        Section(R.string.settings_section_playback)
        StreamingQualityPicker(title = R.string.quality_streaming_wifi_title)
        StreamingQualityPicker(cellular = true, title = R.string.quality_streaming_cell_title)
        if (policy.effectiveAllowAutoplayAndRadio) {
            OptionRow(
                label = stringResource(R.string.settings_autoplay_title),
                detail = stringResource(R.string.settings_autoplay_detail),
                checked = AutoplayPref.on,
                onChecked = { AutoplayPref.set(ctx, it) },
            )
        }
        OptionRow(
            label = stringResource(R.string.myspoti_shuffle_title),
            detail = stringResource(R.string.myspoti_shuffle_detail),
            checked = AppShufflePref.on,
            onChecked = { AppShufflePref.set(ctx, it) },
        )
        TransitionsLink(onOpen = onOpenTransitions)
        EqualizerSettingsUi.ClassicPlaybackRow(navController, policy)
        Section(R.string.settings_section_controls)
        var nativeAudio by remember { mutableStateOf(NativeAudio.enabled) }
        OptionRow(
            label = stringResource(R.string.settings_native_audio_title),
            detail = stringResource(R.string.settings_native_audio_detail),
            checked = nativeAudio,
            onChecked = { nativeAudio = it; NativeAudio.set(ctx, it) },
        )
        var keepPlaying by remember { mutableStateOf(PlaybackService.keepsPlayingOnClose(ctx)) }
        OptionRow(
            label = stringResource(R.string.settings_keep_playing_title),
            detail = stringResource(R.string.settings_keep_playing_detail),
            checked = keepPlaying,
            onChecked = { keepPlaying = it; PlaybackService.setKeepsPlayingOnClose(ctx, it) },
        )
        SpokenWordSettingsUi.ClassicControlsRow(policy)
        SystemSettingsUi.ClassicDataAndBatterySection()
    }

    @Composable
    fun ClassicTransitionsSection(policy: ParentalPolicy) {
        val ctx = LocalContext.current
        val rawMode = TransitionPref.mode
        val mode = if (!policy.localFiles.enabled && rawMode == TransitionMode.AUTOMIX) TransitionMode.OFF else rawMode
        var intro by remember { mutableStateOf(false) }

        @Composable
        fun Chip(label: String, selected: Boolean, onClick: () -> Unit) = Text(
            label,
            color = if (selected) Color.Black else AppText,
            fontSize = 14.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(if (selected) AppAccent else tone(0xFF2A2A2A))
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )

        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
            Text(stringResource(R.string.settings_transitions_title), color = AppText, fontSize = 16.sp)
            Text(
                stringResource(R.string.settings_transitions_detail_soundnest),
                color = AppMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 10.dp),
            ) {
                Chip(stringResource(R.string.settings_crossfade_off), mode == TransitionMode.OFF) {
                    TransitionPref.set(ctx, TransitionMode.OFF)
                }
                Chip(stringResource(R.string.settings_crossfade_title), mode == TransitionMode.CROSSFADE) {
                    TransitionPref.set(ctx, TransitionMode.CROSSFADE)
                }
                if (policy.localFiles.enabled) {
                    Chip(stringResource(R.string.settings_automix_title), mode == TransitionMode.AUTOMIX) {
                        TransitionPref.set(ctx, TransitionMode.AUTOMIX)
                        if (!AutoMixSettings.introSeen) intro = true
                    }
                }
            }
            Text(
                stringResource(
                    when (mode) {
                        TransitionMode.OFF -> R.string.settings_transition_off_detail
                        TransitionMode.CROSSFADE -> R.string.settings_transition_crossfade_detail
                        TransitionMode.AUTOMIX -> R.string.settings_transition_automix_detail
                    },
                ),
                color = AppMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (policy.localFiles.enabled && mode == TransitionMode.AUTOMIX) {
                AutoMixSettingsUi.ClassicAutoMixHeaderDetails()
            }
            if (mode == TransitionMode.CROSSFADE) {
                CrossfadeSlider(AppAccent, AppText, AppMuted, Modifier.padding(top = 12.dp))
            }
        }

        if (policy.localFiles.enabled && mode == TransitionMode.AUTOMIX) {
            AutoMixSettingsUi.ClassicAutoMixOptions(policy)
        }
        if (intro) {
            AutoMixSettingsUi.ClassicAutoMixIntroDialog { intro = false }
        }
    }

    @Composable
    fun ImmersivePlaybackPage(
        navController: NavController,
        openStreamingWifi: () -> Unit,
        openStreamingCell: () -> Unit,
        openCrossfade: () -> Unit,
        back: () -> Unit,
    ) {
        val ctx = LocalContext.current
        val policy = rememberParentalPolicy()
        val wifi = getStreamingQuality(ctx, false)
        val cell = getStreamingQuality(ctx, true)
        val label = { q: StreamingQuality -> StreamingChoices.first { it.first == q }.second }
        var keepPlaying by remember { mutableStateOf(PlaybackService.keepsPlayingOnClose(ctx)) }
        val crossfade = CrossfadePref.seconds
        ImmersivePage(title = stringResource(R.string.settings_section_playback), onBack = back) {
            immersiveGroup("quality", footerRes = R.string.quality_streaming_detail) {
                row { ImmersiveGroupRow(stringResource(R.string.quality_streaming_wifi_title), value = stringResource(label(wifi)), chevron = true, onClick = openStreamingWifi) }
                row { ImmersiveGroupRow(stringResource(R.string.quality_streaming_cell_title), value = stringResource(label(cell)), chevron = true, onClick = openStreamingCell) }
            }
            immersiveGroup("playback") {
                if (policy.effectiveAllowAutoplayAndRadio) {
                    row {
                        ImmersiveSwitchRow(
                            stringResource(R.string.settings_autoplay_title),
                            checked = AutoplayPref.on,
                            onChecked = { AutoplayPref.set(ctx, it) },
                            detail = stringResource(R.string.settings_autoplay_detail),
                        )
                    }
                }
                row {
                    ImmersiveSwitchRow(
                        stringResource(R.string.myspoti_shuffle_title),
                        checked = AppShufflePref.on,
                        onChecked = { AppShufflePref.set(ctx, it) },
                        detail = stringResource(R.string.myspoti_shuffle_detail),
                    )
                }
                row {
                    ImmersiveGroupRow(
                        stringResource(R.string.settings_transitions_title),
                        detail = stringResource(R.string.settings_transitions_detail_soundnest),
                        value = transitionValue(crossfade),
                        chevron = true,
                        onClick = openCrossfade,
                    )
                }
                with(EqualizerSettingsUi) {
                    immersivePlaybackRow(navController, policy)
                }
            }
            immersiveGroup("controls", headerRes = R.string.settings_section_controls) {
                row {
                    var nativeAudio by remember { mutableStateOf(NativeAudio.enabled) }
                    ImmersiveSwitchRow(
                        stringResource(R.string.settings_native_audio_title),
                        checked = nativeAudio,
                        onChecked = { nativeAudio = it; NativeAudio.set(ctx, it) },
                        detail = stringResource(R.string.settings_native_audio_detail),
                    )
                }
                row {
                    ImmersiveSwitchRow(
                        stringResource(R.string.settings_keep_playing_title),
                        checked = keepPlaying,
                        onChecked = { keepPlaying = it; PlaybackService.setKeepsPlayingOnClose(ctx, it) },
                        detail = stringResource(R.string.settings_keep_playing_detail),
                    )
                }
                with(SpokenWordSettingsUi) {
                    immersiveControlsRow(ctx, policy)
                }
            }
        }
    }

    @Composable
    fun ImmersiveStreamingPage(cellular: Boolean, back: () -> Unit) {
        val ctx = LocalContext.current
        var current by remember(cellular) { mutableStateOf(getStreamingQuality(ctx, cellular)) }
        val premium by AccountPlan.premium.collectAsState()
        val choices = StreamingChoices.filter { it.first != StreamingQuality.VERY_HIGH || premium == true || current == StreamingQuality.VERY_HIGH }
        ImmersivePage(
            title = stringResource(if (cellular) R.string.quality_streaming_cell_title else R.string.quality_streaming_wifi_title),
            onBack = back,
        ) {
            immersiveGroup("choices", footerRes = R.string.quality_streaming_detail) {
                choices.forEach { (value, label, detail) ->
                    row {
                        ImmersiveChoiceRow(
                            stringResource(label),
                            selected = current == value,
                            detail = stringResource(detail),
                            onClick = { current = value; setStreamingQuality(ctx, value, cellular) },
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun ImmersiveCrossfadePage(back: () -> Unit) {
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
        ImmersivePage(title = stringResource(R.string.settings_transitions_title), onBack = back) {
            immersiveGroup("mode", footerRes = R.string.settings_transitions_detail_soundnest) {
                for ((m, title, detail) in modeChoices) {
                    row {
                        ImmersiveChoiceRow(
                            stringResource(title),
                            selected = mode == m,
                            detail = stringResource(detail),
                            onClick = {
                                TransitionPref.set(ctx, m)
                                if (m == TransitionMode.AUTOMIX && !AutoMixSettings.introSeen) intro = true
                            },
                        )
                    }
                }
            }
            if (mode == TransitionMode.CROSSFADE) {
                immersiveGroup("length", headerRes = R.string.settings_crossfade_length, footerRes = R.string.settings_crossfade_detail_soundnest) {
                    row {
                        CrossfadeSlider(Immersive.Accent, Immersive.Text, Immersive.TextSecondary, Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                    }
                }
            }
            if (policy.localFiles.enabled && mode == TransitionMode.AUTOMIX) {
                with(AutoMixSettingsUi) {
                    immersiveAutoMixGroups(ctx, policy, cacheBytes, clearCache)
                }
            }
        }
        if (intro) {
            AutoMixSettingsUi.ImmersiveAutoMixIntroDialog { intro = false }
        }
    }
}

/** Transitions in the Playback tab: what is on, one line; its choices and AutoMix's options on their own page. */
@Composable
private fun TransitionsLink(onOpen: () -> Unit) {
    val seconds = CrossfadePref.seconds
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_transitions_title), color = AppText, fontSize = 16.sp)
            Text(stringResource(R.string.settings_transitions_detail_soundnest), color = AppMuted, fontSize = 12.sp)
        }
        Text(
            PlaybackSettingsUi.transitionValue(seconds),
            color = AppAccent,
            fontSize = 14.sp,
            modifier = Modifier.padding(start = 12.dp),
        )
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = AppMuted)
    }
}
