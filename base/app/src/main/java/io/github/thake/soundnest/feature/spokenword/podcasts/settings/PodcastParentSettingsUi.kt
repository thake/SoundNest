/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.spokenword.podcasts.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.thake.soundnest.R
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore
import io.github.thake.soundnest.feature.parental.ui.PolicyToggleRow

/**
 * Parent Controls Dashboard toggle rows for Podcasts and Spotify Audiobooks.
 */
@Composable
fun PodcastParentSettingsRows(policy: ParentalPolicy) {
    val context = LocalContext.current
    PolicyToggleRow(
        title = stringResource(R.string.parent_podcasts_title),
        subtitle = stringResource(R.string.parent_podcasts_subtitle),
        checked = policy.spokenWord.allowPodcasts,
    ) { v ->
        ParentalPolicyStore.update(context) { it.copy(spokenWord = it.spokenWord.copy(allowPodcasts = v)) }
    }
    PolicyToggleRow(
        title = stringResource(R.string.parent_spotify_audiobooks_title),
        subtitle = stringResource(R.string.parent_spotify_audiobooks_subtitle),
        checked = policy.spokenWord.allowAudiobooks,
        switchTestTag = "parent_spotify_audiobooks_toggle",
    ) { v ->
        ParentalPolicyStore.update(context) { it.copy(spokenWord = it.spokenWord.copy(allowAudiobooks = v)) }
    }
}
