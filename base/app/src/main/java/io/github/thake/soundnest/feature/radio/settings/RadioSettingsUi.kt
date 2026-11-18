/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.radio.settings

import android.content.Context
import androidx.compose.ui.res.stringResource
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.preferences.SettingsEntry
import io.github.thake.soundnest.core.ui.settings.M3SectionScope
import io.github.thake.soundnest.core.ui.settings.M3SettingsPalette
import io.github.thake.soundnest.core.ui.settings.M3SettingsSwitchRow
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy

/**
 * Settings row and Settings Search entry for Live Radio visibility.
 */
object RadioSettingsUi {
    val settingsEntries: List<SettingsEntry> = listOf(
        SettingsEntry(
            title = R.string.live_radio_settings_title,
            detail = R.string.live_radio_settings_detail,
            classicPlace = "7",
            immersivePage = "LocalAndRadio",
            isVisible = { it.radio.enabled },
        ),
    )

    fun M3SectionScope.m3LibraryRow(
        context: Context,
        policy: ParentalPolicy,
        palette: M3SettingsPalette,
    ) {
        if (!policy.radio.enabled) return
        row {
            M3SettingsSwitchRow(
                title = stringResource(R.string.live_radio_settings_title),
                detail = stringResource(R.string.live_radio_settings_detail),
                checked = RadioSettings.on,
                palette = palette,
                onCheckedChange = { RadioSettings.set(context, it) },
            )
        }
    }
}
