/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.spokenword.podcasts.settings

import android.content.Context
import androidx.compose.ui.res.stringResource
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.ui.settings.M3SectionScope
import io.github.thake.soundnest.core.ui.settings.M3SettingsPalette
import io.github.thake.soundnest.core.ui.settings.M3SettingsSwitchRow
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore

/**
 * Parent Controls Dashboard toggle rows for Podcasts and Spotify Audiobooks.
 */
fun M3SectionScope.m3PodcastParentRows(
    context: Context,
    policy: ParentalPolicy,
    palette: M3SettingsPalette,
) {
    row {
        M3SettingsSwitchRow(
            title = stringResource(R.string.parent_podcasts_title),
            detail = stringResource(R.string.parent_podcasts_subtitle),
            checked = policy.spokenWord.allowPodcasts,
            palette = palette,
            onCheckedChange = { v ->
                ParentalPolicyStore.update(context) { it.copy(spokenWord = it.spokenWord.copy(allowPodcasts = v)) }
            },
        )
    }
    row {
        M3SettingsSwitchRow(
            title = stringResource(R.string.parent_spotify_audiobooks_title),
            detail = stringResource(R.string.parent_spotify_audiobooks_subtitle),
            checked = policy.spokenWord.allowAudiobooks,
            palette = palette,
            switchTestTag = "parent_spotify_audiobooks_toggle",
            onCheckedChange = { v ->
                ParentalPolicyStore.update(context) { it.copy(spokenWord = it.spokenWord.copy(allowAudiobooks = v)) }
            },
        )
    }
}
