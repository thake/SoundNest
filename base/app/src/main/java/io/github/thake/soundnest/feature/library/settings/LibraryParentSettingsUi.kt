/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.library.settings

import android.content.Context
import androidx.compose.ui.res.stringResource
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.ui.settings.M3SectionScope
import io.github.thake.soundnest.core.ui.settings.M3SettingsPalette
import io.github.thake.soundnest.core.ui.settings.M3SettingsSwitchRow
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore

/**
 * Parent Controls Dashboard toggle row for Read-Only Spotify Library protection.
 */
fun M3SectionScope.m3LibraryParentRow(
    context: Context,
    policy: ParentalPolicy,
    palette: M3SettingsPalette,
) {
    row {
        M3SettingsSwitchRow(
            title = stringResource(R.string.parent_library_readonly_title),
            detail = stringResource(R.string.parent_library_readonly_subtitle),
            checked = policy.library.readOnlySpotifyLibrary,
            palette = palette,
            onCheckedChange = { readOnly ->
                ParentalPolicyStore.update(context) {
                    it.copy(library = it.library.copy(readOnlySpotifyLibrary = readOnly))
                }
            },
        )
    }
}
