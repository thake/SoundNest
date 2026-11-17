/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.localfiles.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.preferences.SettingsEntry
import io.github.thake.soundnest.core.ui.immersive.pages.ImmersiveGroupScope
import io.github.thake.soundnest.core.ui.immersive.pages.ImmersiveSwitchRow
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy
import io.github.thake.soundnest.core.ui.classic.OptionRow

/**
 * Kid Settings rows (Classic & Immersive) and Settings Search entry for Local Files,
 * gated on `policy.localFiles.enabled`.
 */
object LocalFilesSettingsUi {
    val settingsEntries: List<SettingsEntry> = listOf(
        SettingsEntry(
            title = R.string.settings_local_files_shown,
            detail = R.string.settings_local_files_shown_detail,
            classicPlace = "6",
            immersivePage = "Library",
            isVisible = { it.localFiles.enabled },
        ),
    )

    @Composable
    fun ClassicLibraryRow(policy: ParentalPolicy) {
        if (!policy.localFiles.enabled) return
        val context = LocalContext.current
        OptionRow(
            label = stringResource(R.string.settings_local_files_shown),
            detail = stringResource(R.string.settings_local_files_shown_detail),
            checked = LocalFilesSettings.shown,
            onChecked = { LocalFilesSettings.set(context, it) },
        )
    }

    internal fun ImmersiveGroupScope.immersiveLibraryRow(context: Context, policy: ParentalPolicy) {
        if (!policy.localFiles.enabled) return
        row {
            ImmersiveSwitchRow(
                stringResource(R.string.settings_local_files_shown),
                checked = LocalFilesSettings.shown,
                onChecked = { LocalFilesSettings.set(context, it) },
                detail = stringResource(R.string.settings_local_files_shown_detail),
            )
        }
    }
}
