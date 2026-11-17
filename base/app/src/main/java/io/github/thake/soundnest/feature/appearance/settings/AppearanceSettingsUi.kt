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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import io.github.thake.soundnest.core.ui.classic.AppAccent
import io.github.thake.soundnest.core.ui.classic.AppMuted
import io.github.thake.soundnest.core.ui.classic.AppText
import io.github.thake.soundnest.core.ui.classic.OptionRow
import io.github.thake.soundnest.core.ui.classic.Section
import io.github.thake.soundnest.core.ui.classic.tone
import io.github.thake.soundnest.core.ui.immersive.Immersive
import io.github.thake.soundnest.core.ui.immersive.pages.ImmersiveChoiceRow
import io.github.thake.soundnest.core.ui.immersive.pages.ImmersiveGroupRow
import io.github.thake.soundnest.core.ui.immersive.pages.ImmersivePage
import io.github.thake.soundnest.core.ui.immersive.pages.ImmersiveSwitchRow
import io.github.thake.soundnest.core.ui.immersive.pages.immersiveGroup
import io.github.thake.soundnest.core.ui.immersive.shell.LiquidGlassAvailability
import io.github.thake.soundnest.core.ui.immersive.shell.liquidGlassAvailability
import io.github.thake.soundnest.feature.lyrics.settings.LyricsSettingsUi
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy
import io.github.thake.soundnest.feature.parental.settings.rememberParentalPolicy
import io.github.thake.soundnest.feature.player.settings.PlaybackSettingsUi
import io.github.thake.soundnest.feature.system.settings.EnergySettings

/**
 * Classic & Immersive Settings UI surfaces and Settings Search entries for Display, Look,
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
    fun ClassicAppearanceTabContent(policy: ParentalPolicy) {
        val ctx = LocalContext.current
        val energy by EnergySettings.state.collectAsStateWithLifecycle()
        Section(R.string.settings_section_display)
        OptionRow(
            label = stringResource(R.string.settings_amoled_theme),
            detail = stringResource(R.string.settings_amoled_theme_subtitle),
            checked = energy.blackPreference,
            onChecked = EnergySettings::setBlackBackground,
        )
        LyricsSettingsUi.ClassicPlayerLyricsCardRow(policy)
        PlaybackSettingsUi.ClassicPlayerDisplayRows()
        OptionRow(
            label = stringResource(R.string.settings_solid_nav_title),
            detail = stringResource(R.string.settings_solid_nav_detail),
            checked = SolidNavBar.on,
            onChecked = { SolidNavBar.set(ctx, it) },
        )
        if (DynamicColorPref.available) {
            OptionRow(
                label = stringResource(R.string.settings_dynamic_color_title),
                detail = stringResource(R.string.settings_dynamic_color_detail),
                checked = DynamicColorPref.on,
                onChecked = { DynamicColorPref.set(ctx, it) },
            )
        }
        if (!DynamicColorPref.active) ClassicAccentRow(Look.CLASSIC)
        OptionRow(
            label = stringResource(R.string.settings_system_font_title),
            detail = stringResource(R.string.settings_system_font_detail),
            checked = AppFontPref.system,
            onChecked = { AppFontPref.set(ctx, it) },
        )
        ClassicLookRow()
        ClassicOrientationRow()
        ClassicLanguageRow()
    }

    @Composable
    fun ImmersiveDisplayPage(
        openLook: () -> Unit,
        openRotation: () -> Unit,
        openLanguage: () -> Unit,
        back: () -> Unit,
    ) {
        val ctx = LocalContext.current
        val policy = rememberParentalPolicy()
        val energy by EnergySettings.state.collectAsStateWithLifecycle()
        val look = LookPref.look
        val rotation = OrientationPref.mode
        val language = remember { AppLanguage.current(ctx) }
        val auto = stringResource(R.string.language_auto)
        ImmersivePage(title = stringResource(R.string.settings_section_display), onBack = back) {
            immersiveGroup("look") {
                row {
                    ImmersiveGroupRow(
                        stringResource(R.string.settings_look_title),
                        value = stringResource(if (look == Look.CLASSIC) R.string.look_classic else R.string.look_immersive),
                        chevron = true,
                        onClick = openLook,
                    )
                }
                row { ImmersiveAccentRow(Look.IMMERSIVE) }
                if (look == Look.CLASSIC && DynamicColorPref.available) row {
                    ImmersiveSwitchRow(
                        stringResource(R.string.settings_dynamic_color_title),
                        checked = DynamicColorPref.on,
                        onChecked = { DynamicColorPref.set(ctx, it) },
                        detail = stringResource(R.string.settings_dynamic_color_detail),
                    )
                }
                row {
                    ImmersiveSwitchRow(
                        stringResource(R.string.settings_system_font_title),
                        checked = AppFontPref.system,
                        onChecked = { AppFontPref.set(ctx, it) },
                        detail = stringResource(R.string.settings_system_font_detail),
                    )
                }
            }
            immersiveGroup("display") {
                if (look == Look.IMMERSIVE) row {
                    val glass = liquidGlassAvailability()
                    val unsupported = glass == LiquidGlassAvailability.UNSUPPORTED
                    ImmersiveSwitchRow(
                        stringResource(R.string.settings_liquid_glass_title),
                        checked = LiquidGlassPref.on && !unsupported,
                        enabled = !unsupported,
                        onChecked = { LiquidGlassPref.set(ctx, it) },
                        detail = stringResource(
                            when (glass) {
                                LiquidGlassAvailability.UNSUPPORTED -> R.string.settings_liquid_glass_unsupported
                                LiquidGlassAvailability.REDUCED -> R.string.settings_liquid_glass_reduced
                                LiquidGlassAvailability.AVAILABLE -> R.string.settings_liquid_glass_detail
                            },
                        ),
                    )
                }
                row {
                    ImmersiveSwitchRow(
                        stringResource(R.string.settings_amoled_theme),
                        checked = energy.blackPreference,
                        onChecked = EnergySettings::setBlackBackground,
                        detail = stringResource(R.string.settings_amoled_theme_subtitle),
                    )
                }
                with(LyricsSettingsUi) {
                    immersivePlayerLyricsCardRow(ctx, look, policy)
                }
                with(PlaybackSettingsUi) {
                    immersivePlayerDisplayRows(ctx, look == Look.IMMERSIVE)
                }
            }
            immersiveGroup("layout") {
                row {
                    ImmersiveGroupRow(
                        stringResource(R.string.settings_rotation_title),
                        value = stringResource(rotationLabel(rotation)),
                        chevron = true,
                        onClick = openRotation,
                    )
                }
                row {
                    ImmersiveGroupRow(
                        stringResource(R.string.settings_item_app_language_title),
                        value = if (language == AppLanguage.SYSTEM) auto else AppLanguage.nativeName(language),
                        chevron = true,
                        onClick = openLanguage,
                    )
                }
            }
        }
    }

    @Composable
    fun ImmersiveLookPage(back: () -> Unit) {
        val ctx = LocalContext.current
        val current = LookPref.look
        ImmersivePage(title = stringResource(R.string.settings_look_title), onBack = back) {
            immersiveGroup("choices") {
                for (l in Look.entries) {
                    val classic = l == Look.CLASSIC
                    row {
                        ImmersiveChoiceRow(
                            stringResource(if (classic) R.string.look_classic else R.string.look_immersive),
                            selected = current == l,
                            detail = stringResource(if (classic) R.string.look_classic_detail else R.string.look_immersive_detail),
                            onClick = { LookPref.set(ctx, l) },
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun ImmersiveRotationPage(back: () -> Unit) {
        val ctx = LocalContext.current
        val pref = OrientationPref
        ImmersivePage(title = stringResource(R.string.settings_rotation_title), onBack = back) {
            immersiveGroup("choices") {
                for ((value, label) in listOf(
                    pref.PORTRAIT to R.string.settings_rotation_portrait,
                    pref.AUTO to R.string.settings_rotation_auto,
                    pref.LANDSCAPE to R.string.settings_rotation_landscape,
                )) {
                    row {
                        ImmersiveChoiceRow(
                            stringResource(label),
                            selected = pref.mode == value,
                            onClick = { pref.set(ctx, value) },
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun ImmersiveLanguagePage(back: () -> Unit) {
        val ctx = LocalContext.current
        val current = remember { AppLanguage.current(ctx) }
        val auto = stringResource(R.string.language_auto)
        fun label(tag: String) = if (tag == AppLanguage.SYSTEM) auto else AppLanguage.nativeName(tag)
        ImmersivePage(title = stringResource(R.string.settings_item_app_language_title), onBack = back) {
            immersiveGroup("choices", footerRes = R.string.language_partial_note) {
                AppLanguage.AVAILABLE.forEach { tag ->
                    row {
                        ImmersiveChoiceRow(
                            label(tag),
                            selected = tag == current,
                            detail = if (tag == AppLanguage.SYSTEM) stringResource(R.string.language_auto_detail) else null,
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
private fun ClassicLookRow() {
    val ctx = LocalContext.current
    val current = LookPref.look
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Text(stringResource(R.string.settings_look_title), color = AppText, fontSize = 16.sp)
        for (l in Look.entries) {
            val classic = l == Look.CLASSIC
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { role = Role.RadioButton }
                    .clickable { LookPref.set(ctx, l) }
                    .padding(vertical = 8.dp),
            ) {
                RadioButton(selected = current == l, onClick = null)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(stringResource(if (classic) R.string.look_classic else R.string.look_immersive), color = AppText, fontSize = 15.sp)
                    Text(stringResource(if (classic) R.string.look_classic_detail else R.string.look_immersive_detail), color = AppMuted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun ClassicAccentRow(look: Look) {
    val ctx = LocalContext.current
    val pref = AccentPref
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Text(stringResource(R.string.supporter_accent), color = AppText, fontSize = 16.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 10.dp)) {
            for (a in AccentPref.Accent.entries) {
                val selected = pref.accentOf(look) == a
                val name = stringResource(accentNameRes(a))
                Box(
                    Modifier
                        .size(40.dp)
                        .semantics { contentDescription = name; role = Role.RadioButton }
                        .clip(CircleShape)
                        .background(a.color)
                        .then(if (selected) Modifier.border(3.dp, AppText, CircleShape) else Modifier)
                        .clickable { pref.setAccent(ctx, look, a) },
                )
            }
        }
    }
}

@Composable
private fun ImmersiveAccentRow(look: Look) {
    val ctx = LocalContext.current
    val accentPref = AccentPref
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(stringResource(R.string.supporter_accent), style = Immersive.Body)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 10.dp)) {
            for (a in AccentPref.Accent.entries) {
                val selected = accentPref.accentOf(look) == a
                val name = stringResource(accentNameRes(a))
                Box(
                    Modifier
                        .size(38.dp)
                        .semantics { contentDescription = name; role = Role.RadioButton }
                        .clip(CircleShape)
                        .background(a.color)
                        .then(if (selected) Modifier.border(3.dp, Immersive.Text, CircleShape) else Modifier)
                        .clickable { accentPref.setAccent(ctx, look, a) },
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

@Composable
private fun ClassicOrientationRow() {
    val ctx = LocalContext.current
    val pref = OrientationPref
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Text(stringResource(R.string.settings_rotation_title), color = AppText, fontSize = 16.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
            for ((value, label) in listOf(
                pref.PORTRAIT to R.string.settings_rotation_portrait,
                pref.AUTO to R.string.settings_rotation_auto,
                pref.LANDSCAPE to R.string.settings_rotation_landscape,
            )) {
                val selected = pref.mode == value
                Text(
                    stringResource(label),
                    color = if (selected) Color.Black else AppText,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) AppAccent else tone(0xFF2A2A2A))
                        .clickable { pref.set(ctx, value) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClassicLanguageRow() {
    val ctx = LocalContext.current
    val current = remember { AppLanguage.current(ctx) }
    var picking by remember { mutableStateOf(false) }
    val auto = stringResource(R.string.language_auto)
    fun label(tag: String) = if (tag == AppLanguage.SYSTEM) auto else AppLanguage.nativeName(tag)
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { picking = true }
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(stringResource(R.string.settings_item_app_language_title), color = AppText, fontSize = 16.sp)
        Text(label(current), color = AppMuted, fontSize = 12.sp)
    }
    if (picking) ModalBottomSheet(
        onDismissRequest = { picking = false },
        containerColor = tone(0xFF282828),
    ) {
        LazyColumn(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            item {
                Text(
                    stringResource(R.string.settings_item_app_language_title),
                    color = AppText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                Text(
                    stringResource(R.string.language_partial_note),
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clickable {
                            picking = false
                            if (!selected) {
                                AppLanguage.apply(ctx, tag)
                                generateSequence(ctx) { (it as? ContextWrapper)?.baseContext }
                                    .filterIsInstance<Activity>().firstOrNull()?.recreate()
                            }
                        }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(label(tag), color = if (selected) AppAccent else AppText, fontSize = 16.sp)
                        if (tag == AppLanguage.SYSTEM) {
                            Text(stringResource(R.string.language_auto_detail), color = AppMuted, fontSize = 12.sp)
                        }
                    }
                    if (selected) Icon(Icons.Default.Check, null, tint = AppAccent)
                }
            }
        }
    }
}
