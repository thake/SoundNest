/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.appearance.settings

import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.preferences.SettingsEntry
import io.github.thake.soundnest.core.ui.immersive.shell.LiquidGlassAvailability
import io.github.thake.soundnest.core.ui.immersive.shell.liquidGlassAvailability
import io.github.thake.soundnest.core.ui.settings.M3SettingsChoiceRow
import io.github.thake.soundnest.core.ui.settings.M3SettingsNavRow
import io.github.thake.soundnest.core.ui.settings.M3SettingsPalette
import io.github.thake.soundnest.core.ui.settings.M3SettingsScaffold
import io.github.thake.soundnest.core.ui.settings.M3SettingsSwitchRow
import io.github.thake.soundnest.core.ui.settings.m3SettingsSection
import io.github.thake.soundnest.feature.lyrics.settings.LyricsSettingsUi
import io.github.thake.soundnest.feature.parental.settings.rememberParentalPolicy
import io.github.thake.soundnest.feature.player.settings.PlaybackSettingsUi
import io.github.thake.soundnest.feature.system.settings.EnergySettings

/**
 * Unified Material Design 3 Settings UI surfaces and Settings Search entries for Display, Look,
 * Accent, Font, Orientation, and App Language.
 */
object AppearanceSettingsUi {
    val settingsEntries: List<SettingsEntry> = listOf(
        SettingsEntry(
            title = R.string.settings_look_title,
            classicPlace = "3",
            immersivePage = "Display",
        ),
        SettingsEntry(
            title = R.string.settings_dynamic_color_title,
            detail = R.string.settings_dynamic_color_detail,
            classicPlace = "3",
            immersivePage = "Display",
        ),
        SettingsEntry(
            title = R.string.settings_system_font_title,
            detail = R.string.settings_system_font_detail,
            classicPlace = "3",
            immersivePage = "Display",
        ),
        SettingsEntry(
            title = R.string.settings_liquid_glass_title,
            detail = R.string.settings_liquid_glass_detail,
            immersivePage = "Display",
        ),
        SettingsEntry(
            title = R.string.settings_solid_nav_title,
            detail = R.string.settings_solid_nav_detail,
            classicPlace = "3",
        ),
        SettingsEntry(
            title = R.string.settings_amoled_theme,
            detail = R.string.settings_amoled_theme_subtitle,
            classicPlace = "3",
            immersivePage = "Display",
        ),
        *LyricsSettingsUi.displaySettingsEntries.toTypedArray(),
        *PlaybackSettingsUi.displaySettingsEntries.toTypedArray(),
        SettingsEntry(
            title = R.string.settings_rotation_title,
            classicPlace = "3",
            immersivePage = "Display",
        ),
        SettingsEntry(
            title = R.string.settings_item_app_language_title,
            classicPlace = "3",
            immersivePage = "Display",
        ),
        SettingsEntry(
            title = R.string.supporter_accent,
            classicPlace = "3",
            immersivePage = "Display",
        ),
        SettingsEntry(
            title = R.string.settings_tab_appearance,
            classicPlace = "3",
        ),
        SettingsEntry(
            title = R.string.settings_section_display,
            classicPlace = "3",
            immersivePage = "Display",
        ),
    )

    @Composable
    fun M3DisplayPage(
        openLook: () -> Unit,
        openRotation: () -> Unit,
        openLanguage: () -> Unit,
        back: () -> Unit,
    ) {
        val ctx = LocalContext.current
        val policy = rememberParentalPolicy()
        val energy by EnergySettings.state.collectAsStateWithLifecycle()
        val look = LookPref.look
        val isClassic = look == Look.CLASSIC
        val isImmersiveLook = look == Look.IMMERSIVE
        val rotation = OrientationPref.mode
        val language = remember { AppLanguage.current(ctx) }
        val auto = stringResource(R.string.language_auto)

        M3SettingsScaffold(
            title = stringResource(R.string.settings_section_display),
            onBack = back,
        ) { palette ->
            m3SettingsSection(
                key = "display:theme",
                palette = palette,
                header = ctx.getString(R.string.settings_display_section_theme),
            ) {
                row {
                    M3SettingsNavRow(
                        title = stringResource(R.string.settings_look_title),
                        value = stringResource(if (isClassic) R.string.look_classic else R.string.look_immersive),
                        palette = palette,
                        onClick = openLook,
                    )
                }
                if (!isClassic || !DynamicColorPref.active) {
                    row {
                        M3AccentRow(
                            look = if (isImmersiveLook) Look.IMMERSIVE else Look.CLASSIC,
                            palette = palette,
                        )
                    }
                }
                if (isClassic && DynamicColorPref.available) {
                    row {
                        M3SettingsSwitchRow(
                            title = stringResource(R.string.settings_dynamic_color_title),
                            detail = stringResource(R.string.settings_dynamic_color_detail),
                            checked = DynamicColorPref.on,
                            palette = palette,
                            onCheckedChange = { DynamicColorPref.set(ctx, it) },
                        )
                    }
                }
                row {
                    M3SettingsSwitchRow(
                        title = stringResource(R.string.settings_system_font_title),
                        detail = stringResource(R.string.settings_system_font_detail),
                        checked = AppFontPref.system,
                        palette = palette,
                        onCheckedChange = { AppFontPref.set(ctx, it) },
                    )
                }
                if (isImmersiveLook) {
                    row {
                        val glass = liquidGlassAvailability()
                        val unsupported = glass == LiquidGlassAvailability.UNSUPPORTED
                        M3SettingsSwitchRow(
                            title = stringResource(R.string.settings_liquid_glass_title),
                            detail = stringResource(
                                when (glass) {
                                    LiquidGlassAvailability.UNSUPPORTED -> R.string.settings_liquid_glass_unsupported
                                    LiquidGlassAvailability.REDUCED -> R.string.settings_liquid_glass_reduced
                                    LiquidGlassAvailability.AVAILABLE -> R.string.settings_liquid_glass_detail
                                },
                            ),
                            checked = LiquidGlassPref.on && !unsupported,
                            enabled = !unsupported,
                            palette = palette,
                            onCheckedChange = { LiquidGlassPref.set(ctx, it) },
                        )
                    }
                }
                row {
                    M3SettingsSwitchRow(
                        title = stringResource(R.string.settings_amoled_theme),
                        detail = stringResource(R.string.settings_amoled_theme_subtitle),
                        checked = energy.blackPreference,
                        palette = palette,
                        onCheckedChange = EnergySettings::setBlackBackground,
                    )
                }
            }

            m3SettingsSection(
                key = "display:player",
                palette = palette,
                header = ctx.getString(R.string.settings_display_section_player),
            ) {
                with(LyricsSettingsUi) {
                    m3PlayerLyricsCardRow(ctx, look, policy, palette)
                }
                with(PlaybackSettingsUi) {
                    m3PlayerDisplayRows(ctx, look, palette)
                }
                if (isClassic) {
                    row {
                        M3SettingsSwitchRow(
                            title = stringResource(R.string.settings_solid_nav_title),
                            detail = stringResource(R.string.settings_solid_nav_detail),
                            checked = SolidNavBar.on,
                            palette = palette,
                            onCheckedChange = { SolidNavBar.set(ctx, it) },
                        )
                    }
                }
            }

            m3SettingsSection(
                key = "display:layout",
                palette = palette,
                header = ctx.getString(R.string.settings_display_section_layout),
            ) {
                row {
                    M3SettingsNavRow(
                        title = stringResource(R.string.settings_rotation_title),
                        value = stringResource(rotationLabel(rotation)),
                        palette = palette,
                        onClick = openRotation,
                    )
                }
                row {
                    M3SettingsNavRow(
                        title = stringResource(R.string.settings_item_app_language_title),
                        value = if (language == AppLanguage.SYSTEM) auto else AppLanguage.nativeName(language),
                        palette = palette,
                        onClick = openLanguage,
                    )
                }
            }
        }
    }

    @Composable
    fun M3LookPage(back: () -> Unit) {
        val ctx = LocalContext.current
        val current = LookPref.look
        M3SettingsScaffold(
            title = stringResource(R.string.settings_look_title),
            onBack = back,
        ) { palette ->
            m3SettingsSection(key = "look:choices", palette = palette) {
                for (l in Look.entries) {
                    val classic = l == Look.CLASSIC
                    row {
                        M3SettingsChoiceRow(
                            title = stringResource(if (classic) R.string.look_classic else R.string.look_immersive),
                            detail = stringResource(if (classic) R.string.look_classic_detail else R.string.look_immersive_detail),
                            selected = current == l,
                            palette = palette,
                            onClick = { LookPref.set(ctx, l) },
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun M3RotationPage(back: () -> Unit) {
        val ctx = LocalContext.current
        val pref = OrientationPref
        M3SettingsScaffold(
            title = stringResource(R.string.settings_rotation_title),
            onBack = back,
        ) { palette ->
            m3SettingsSection(key = "rotation:choices", palette = palette) {
                for ((value, labelRes) in listOf(
                    pref.PORTRAIT to R.string.settings_rotation_portrait,
                    pref.AUTO to R.string.settings_rotation_auto,
                    pref.LANDSCAPE to R.string.settings_rotation_landscape,
                )) {
                    row {
                        M3SettingsChoiceRow(
                            title = stringResource(labelRes),
                            selected = pref.mode == value,
                            palette = palette,
                            onClick = { pref.set(ctx, value) },
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun M3LanguagePage(back: () -> Unit) {
        val ctx = LocalContext.current
        val current = remember { AppLanguage.current(ctx) }
        val auto = stringResource(R.string.language_auto)
        fun label(tag: String) = if (tag == AppLanguage.SYSTEM) auto else AppLanguage.nativeName(tag)

        M3SettingsScaffold(
            title = stringResource(R.string.settings_item_app_language_title),
            onBack = back,
        ) { palette ->
            m3SettingsSection(
                key = "language:choices",
                palette = palette,
                footer = ctx.getString(R.string.language_partial_note),
            ) {
                AppLanguage.AVAILABLE.forEach { tag ->
                    row {
                        M3SettingsChoiceRow(
                            title = label(tag),
                            detail = if (tag == AppLanguage.SYSTEM) stringResource(R.string.language_auto_detail) else null,
                            selected = tag == current,
                            palette = palette,
                            onClick = {
                                if (tag != current) {
                                    AppLanguage.apply(ctx, tag)
                                    generateSequence(ctx) { (it as? ContextWrapper)?.baseContext }
                                        .filterIsInstance<Activity>().firstOrNull()?.recreate()
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun rotationLabel(mode: String): Int = when (mode) {
    OrientationPref.AUTO -> R.string.settings_rotation_auto
    OrientationPref.LANDSCAPE -> R.string.settings_rotation_landscape
    else -> R.string.settings_rotation_portrait
}

@Composable
private fun M3AccentRow(look: Look, palette: M3SettingsPalette) {
    val ctx = LocalContext.current
    val pref = AccentPref
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = stringResource(R.string.supporter_accent),
            color = palette.text,
            fontFamily = palette.fontFamily,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(top = 12.dp),
        ) {
            for (a in AccentPref.Accent.entries) {
                val selected = pref.accentOf(look) == a
                val name = stringResource(accentNameRes(a))
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .semantics { contentDescription = name; role = Role.RadioButton }
                        .clip(CircleShape)
                        .background(a.color)
                        .then(if (selected) Modifier.border(3.dp, palette.text, CircleShape) else Modifier)
                        .clickable { pref.setAccent(ctx, look, a) },
                )
            }
        }
    }
}

private fun accentNameRes(accent: AccentPref.Accent): Int = when (accent) {
    AccentPref.Accent.GREEN -> R.string.color_green
    AccentPref.Accent.BLUE -> R.string.color_blue
    AccentPref.Accent.PURPLE -> R.string.color_purple
    AccentPref.Accent.PINK -> R.string.color_pink
    AccentPref.Accent.ORANGE -> R.string.color_orange
}
