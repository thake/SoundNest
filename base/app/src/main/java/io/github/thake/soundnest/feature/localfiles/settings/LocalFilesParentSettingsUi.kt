/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.localfiles.settings

import android.content.Context
import androidx.compose.ui.res.stringResource
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.ui.settings.M3SectionScope
import io.github.thake.soundnest.core.ui.settings.M3SettingsPalette
import io.github.thake.soundnest.core.ui.settings.M3SettingsSwitchRow
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore

/**
 * Parent Controls Dashboard toggle row for Local Audio Files.
 */
fun M3SectionScope.m3LocalFilesParentRow(
    context: Context,
    policy: ParentalPolicy,
    palette: M3SettingsPalette,
) {
    row {
        M3SettingsSwitchRow(
            title = stringResource(R.string.parent_local_files_title),
            detail = stringResource(R.string.parent_local_files_subtitle),
            checked = policy.localFiles.enabled,
            palette = palette,
            onCheckedChange = { enabled ->
                ParentalPolicyStore.update(context) { it.copy(localFiles = it.localFiles.copy(enabled = enabled)) }
            },
        )
    }
}
