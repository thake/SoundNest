/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.equalizer.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.ui.look.ThinSlider
import io.github.thake.soundnest.core.ui.settings.M3InfoBannerCard
import io.github.thake.soundnest.core.ui.settings.M3SegmentedRow
import io.github.thake.soundnest.core.ui.settings.M3SettingsChoiceRow
import io.github.thake.soundnest.core.ui.settings.M3SettingsNavRow
import io.github.thake.soundnest.core.ui.settings.M3SettingsPalette
import io.github.thake.soundnest.core.ui.settings.M3SettingsScaffold
import io.github.thake.soundnest.core.ui.settings.M3SettingsSwitchRow
import io.github.thake.soundnest.core.ui.settings.M3SubpageHost
import io.github.thake.soundnest.core.ui.settings.m3SettingsBlock
import io.github.thake.soundnest.core.ui.settings.m3SettingsSection
import io.github.thake.soundnest.feature.equalizer.dsp.EqBands
import io.github.thake.soundnest.feature.equalizer.dsp.Spatial
import io.github.thake.soundnest.feature.equalizer.settings.EqPreset
import io.github.thake.soundnest.feature.equalizer.settings.EqualizerSettings

private enum class EqualizerPage { Main, Presets }

/**
 * Shared Material Design 3 Equalizer, Spatial Audio, Speed, and Silence-Skipping screen
 * for local files and Live Radio across both Classic and Immersive looks.
 */
@Composable
fun MaterialEqualizerScreen(navController: NavController) {
    val ctx = LocalContext.current
    val pref = EqualizerSettings
    M3SubpageHost(
        initialPage = EqualizerPage.Main,
        rootPage = EqualizerPage.Main,
        valueOf = EqualizerPage::valueOf,
        onExit = { navController.popBackStack() },
        label = "equalizer_pages",
    ) { page, open, back, _ ->
        when (page) {
            EqualizerPage.Presets -> {
                M3SettingsScaffold(
                    title = stringResource(R.string.equalizer_preset),
                    onBack = back,
                ) { palette ->
                    m3SettingsSection(key = "eq:presets", palette = palette) {
                        val presets = EqPreset.entries.filter { it != EqPreset.CUSTOM || pref.preset == EqPreset.CUSTOM }
                        presets.forEach { preset ->
                            row {
                                M3SettingsChoiceRow(
                                    title = stringResource(eqPresetLabel(preset)),
                                    selected = pref.preset == preset,
                                    palette = palette,
                                    onClick = {
                                        pref.setPreset(ctx, preset)
                                        back()
                                    },
                                )
                            }
                        }
                    }
                }
            }

            EqualizerPage.Main -> {
                M3SettingsScaffold(
                    title = stringResource(R.string.equalizer_title),
                    onBack = back,
                ) { palette ->
                    m3SettingsBlock(key = "eq:scope", palette = palette) {
                        M3InfoBannerCard(
                            text = stringResource(R.string.equalizer_scope_soundnest),
                            palette = palette,
                            icon = Icons.Rounded.GraphicEq,
                        )
                    }

                    m3SettingsSection(
                        key = "eq:controls",
                        palette = palette,
                        header = ctx.getString(R.string.equalizer_section_equalizer),
                        footer = ctx.getString(R.string.equalizer_enable_detail),
                    ) {
                        row {
                            M3SettingsSwitchRow(
                                title = stringResource(R.string.equalizer_enable),
                                checked = pref.enabled,
                                palette = palette,
                                onCheckedChange = { pref.setEnabled(ctx, it) },
                            )
                        }
                        row {
                            M3SettingsNavRow(
                                icon = Icons.Rounded.Tune,
                                title = stringResource(R.string.equalizer_preset),
                                value = stringResource(eqPresetLabel(pref.preset)),
                                palette = palette,
                                enabled = pref.enabled,
                                onClick = { open(EqualizerPage.Presets) },
                            )
                        }
                        if (pref.preset != EqPreset.FLAT) {
                            row {
                                M3SettingsNavRow(
                                    icon = Icons.Rounded.RestartAlt,
                                    title = stringResource(R.string.equalizer_reset),
                                    palette = palette,
                                    enabled = pref.enabled,
                                    showChevron = false,
                                    onClick = { pref.setPreset(ctx, EqPreset.FLAT) },
                                )
                            }
                        }
                    }

                    m3SettingsSection(
                        key = "eq:bands",
                        palette = palette,
                        header = ctx.getString(R.string.equalizer_bands),
                        footer = ctx.getString(R.string.equalizer_preamp_detail),
                    ) {
                        row {
                            EqualizerSliderRow(
                                label = stringResource(R.string.equalizer_preamp),
                                value = pref.preamp,
                                min = EqBands.PREAMP_MIN_DB,
                                max = EqBands.PREAMP_MAX_DB,
                                valueText = stringResource(R.string.equalizer_db, dbText(pref.preamp)),
                                enabled = pref.enabled,
                                palette = palette,
                                stepValue = { (it * 2).let(Math::round) / 2f },
                                onChange = { pref.setPreamp(ctx, it, save = false) },
                                onDone = { pref.save(ctx) },
                            )
                        }
                        val gains = pref.gains
                        for (band in 0 until EqBands.COUNT) {
                            row {
                                EqualizerSliderRow(
                                    label = eqBandLabel(band),
                                    value = gains[band],
                                    min = EqBands.MIN_DB,
                                    max = EqBands.MAX_DB,
                                    valueText = stringResource(R.string.equalizer_db, dbText(gains[band])),
                                    enabled = pref.enabled,
                                    palette = palette,
                                    stepValue = { (it * 2).let(Math::round) / 2f },
                                    onChange = { pref.setGain(ctx, band, it, save = false) },
                                    onDone = { pref.save(ctx) },
                                )
                            }
                        }
                    }

                    m3SettingsSection(
                        key = "eq:spatial",
                        palette = palette,
                        header = ctx.getString(R.string.spatial_title),
                        footer = ctx.getString(R.string.spatial_detail_soundnest),
                    ) {
                        row {
                            M3SettingsSwitchRow(
                                title = stringResource(R.string.spatial_enable),
                                checked = pref.spatial,
                                palette = palette,
                                onCheckedChange = { pref.setSpatial(ctx, it) },
                            )
                        }
                        row {
                            EqualizerSliderRow(
                                label = stringResource(R.string.spatial_width),
                                value = pref.spatialWidth,
                                min = Spatial.MIN_WIDTH,
                                max = Spatial.MAX_WIDTH,
                                valueText = widthText(pref.spatialWidth),
                                enabled = pref.spatial,
                                palette = palette,
                                stepValue = ::widthStep,
                                onChange = { pref.setSpatialWidth(ctx, it, save = false) },
                                onDone = { pref.setSpatialWidth(ctx, pref.spatialWidth) },
                            )
                        }
                    }

                    m3SettingsSection(
                        key = "eq:playback",
                        palette = palette,
                        header = ctx.getString(R.string.equalizer_section_playback),
                        footer = ctx.getString(R.string.equalizer_speed_detail),
                    ) {
                        row {
                            M3SegmentedRow(
                                options = EqualizerSettings.SPEEDS.map { speedText(it) },
                                selectedIndex = EqualizerSettings.SPEEDS.indexOf(pref.speed).coerceAtLeast(0),
                                palette = palette,
                                onSelect = { pref.setSpeed(ctx, EqualizerSettings.SPEEDS[it]) },
                            )
                        }
                        row {
                            M3SettingsSwitchRow(
                                title = stringResource(R.string.equalizer_skip_silence),
                                detail = stringResource(R.string.equalizer_skip_silence_detail),
                                checked = pref.skipSilence,
                                palette = palette,
                                onCheckedChange = { pref.setSkipSilence(ctx, it) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EqualizerSliderRow(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    valueText: String,
    enabled: Boolean,
    palette: M3SettingsPalette,
    stepValue: (Float) -> Float,
    onChange: (Float) -> Unit,
    onDone: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .alpha(if (enabled) 1f else 0.55f),
    ) {
        Text(
            text = label,
            color = if (enabled) palette.text else palette.tertiary,
            fontFamily = palette.fontFamily,
            fontSize = 14.sp,
            modifier = Modifier.width(64.dp),
        )
        if (palette.immersive) {
            ThinSlider(
                value = ((value - min) / (max - min)).coerceIn(0f, 1f),
                onValueChange = { if (enabled) onChange(stepValue(min + it * (max - min))) },
                onValueChangeFinished = onDone,
                modifier = Modifier.weight(1f),
                activeColor = if (enabled) palette.accent else palette.tertiary,
            )
        } else {
            Slider(
                value = value.coerceIn(min, max),
                onValueChange = { onChange(stepValue(it)) },
                onValueChangeFinished = onDone,
                valueRange = min..max,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = palette.accent,
                    activeTrackColor = palette.accent,
                    inactiveTrackColor = palette.divider,
                ),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = valueText,
            color = palette.muted,
            fontFamily = palette.fontFamily,
            fontSize = 12.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.width(56.dp),
        )
    }
}
