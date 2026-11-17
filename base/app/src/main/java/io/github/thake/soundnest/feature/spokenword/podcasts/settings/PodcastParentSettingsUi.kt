/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.spokenword.podcasts.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore
import io.github.thake.soundnest.feature.parental.ui.PolicyToggleRow

/**
 * Parent Controls Dashboard toggle rows for Podcasts and Audiobooks.
 */
@Composable
fun PodcastParentSettingsRows(policy: ParentalPolicy) {
    val context = LocalContext.current
    PolicyToggleRow(
        title = "Podcasts",
        subtitle = "Allow podcast shows and episodes (unmoderated spoken-word audio)",
        checked = policy.spokenWord.allowPodcasts,
    ) { v ->
        ParentalPolicyStore.update(context) { it.copy(spokenWord = it.spokenWord.copy(allowPodcasts = v)) }
    }
    PolicyToggleRow(
        title = "Audiobooks",
        subtitle = "Allow Spotify audiobooks & parent-approved Audible audiobooks",
        checked = policy.spokenWord.allowAudiobooks,
    ) { v ->
        ParentalPolicyStore.update(context) { it.copy(spokenWord = it.spokenWord.copy(allowAudiobooks = v)) }
    }
}
