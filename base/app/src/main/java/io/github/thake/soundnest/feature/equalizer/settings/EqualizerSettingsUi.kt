/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.equalizer.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.preferences.SettingsEntry
import io.github.thake.soundnest.core.ui.navigation.Routes
import io.github.thake.soundnest.core.ui.navigation.navigateSingleTop
import io.github.thake.soundnest.core.ui.settings.M3SectionScope
import io.github.thake.soundnest.core.ui.settings.M3SettingsNavRow
import io.github.thake.soundnest.core.ui.settings.M3SettingsPalette
import io.github.thake.soundnest.feature.equalizer.ui.eqPresetLabel
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy

/**
 * Settings rows and Settings Search entry for the local-player Equalizer,
 * gated on [EqualizerSettings.isAvailable].
 */
object EqualizerSettingsUi {
    val settingsEntries: List<SettingsEntry> = listOf(
        SettingsEntry(
            title = R.string.equalizer_title,
            detail = R.string.equalizer_settings_detail_soundnest,
            classicPlace = "7",
            immersivePage = "LocalAndRadio",
            isVisible = EqualizerSettings::isAvailable,
        ),
    )

    @Composable
    fun equalizerValueLabel(): String =
        if (EqualizerSettings.enabled) stringResource(eqPresetLabel(EqualizerSettings.preset))
        else stringResource(R.string.settings_equalizer_off)

    fun M3SectionScope.m3SettingsRow(
        navController: NavController,
        policy: ParentalPolicy,
        palette: M3SettingsPalette,
        showIcon: Boolean = true,
    ) {
        if (!EqualizerSettings.isAvailable(policy)) return
        row {
            M3SettingsNavRow(
                icon = if (showIcon) Icons.Rounded.Tune else null,
                title = stringResource(R.string.equalizer_title),
                detail = stringResource(R.string.equalizer_settings_detail_soundnest),
                value = equalizerValueLabel(),
                palette = palette,
                onClick = { navController.navigateSingleTop(Routes.Equalizer.route) },
            )
        }
    }
}
