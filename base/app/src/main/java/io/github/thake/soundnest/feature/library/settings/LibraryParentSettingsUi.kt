/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.library.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
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
        title = "Read-Only Spotify Library",
        subtitle = "Prevent unliking songs or deleting/editing Spotify playlists (local device playlists remain usable)",
        checked = policy.library.readOnlySpotifyLibrary,
    ) { readOnly ->
        ParentalPolicyStore.update(context) {
            it.copy(library = it.library.copy(readOnlySpotifyLibrary = readOnly))
        }
    }
}
