/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.library.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.thake.soundnest.R
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore
import io.github.thake.soundnest.feature.parental.ui.PolicyToggleRow

/**
 * Parent Controls Dashboard toggle row for Read-Only Spotify Library protection.
 */
@Composable
fun LibraryParentSettingsRow(policy: ParentalPolicy) {
    val context = LocalContext.current
    PolicyToggleRow(
        title = stringResource(R.string.parent_library_readonly_title),
        subtitle = stringResource(R.string.parent_library_readonly_subtitle),
        checked = policy.library.readOnlySpotifyLibrary,
    ) { readOnly ->
        ParentalPolicyStore.update(context) {
            it.copy(library = it.library.copy(readOnlySpotifyLibrary = readOnly))
        }
    }
}
