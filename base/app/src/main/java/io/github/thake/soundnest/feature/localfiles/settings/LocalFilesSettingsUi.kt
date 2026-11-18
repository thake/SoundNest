/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.localfiles.settings

import android.content.Context
import androidx.compose.ui.res.stringResource
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.preferences.SettingsEntry
import io.github.thake.soundnest.core.ui.settings.M3SectionScope
import io.github.thake.soundnest.core.ui.settings.M3SettingsPalette
import io.github.thake.soundnest.core.ui.settings.M3SettingsSwitchRow
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy

/**
 * Settings row and Settings Search entry for Local Files visibility,
 * gated on `policy.localFiles.enabled`.
 */
object LocalFilesSettingsUi {
    val settingsEntries: List<SettingsEntry> = listOf(
        SettingsEntry(
            title = R.string.settings_local_files_shown,
            detail = R.string.settings_local_files_shown_detail,
            classicPlace = "7",
            immersivePage = "LocalAndRadio",
            isVisible = { it.localFiles.enabled },
        ),
    )

    fun M3SectionScope.m3LibraryRow(
        context: Context,
        policy: ParentalPolicy,
        palette: M3SettingsPalette,
    ) {
        if (!policy.localFiles.enabled) return
        row {
            M3SettingsSwitchRow(
                title = stringResource(R.string.settings_local_files_shown),
                detail = stringResource(R.string.settings_local_files_shown_detail),
                checked = LocalFilesSettings.shown,
                palette = palette,
                onCheckedChange = { LocalFilesSettings.set(context, it) },
            )
        }
    }
}
