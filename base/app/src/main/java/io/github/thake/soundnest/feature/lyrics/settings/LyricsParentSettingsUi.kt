/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.lyrics.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.thake.soundnest.R
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore
import io.github.thake.soundnest.feature.parental.ui.PolicyToggleRow

/**
 * Parent Controls Dashboard toggle rows for Song Lyrics, Genius Song Explanations,
 * and Third-Party Lyric Providers.
 */
@Composable
fun LyricsParentSettingsRows(policy: ParentalPolicy) {
    val context = LocalContext.current
    PolicyToggleRow(
        title = stringResource(R.string.parent_lyrics_title),
        subtitle = stringResource(R.string.parent_lyrics_subtitle),
        checked = policy.lyrics.enabled,
    ) { v ->
        ParentalPolicyStore.update(context) { it.copy(lyrics = it.lyrics.copy(enabled = v)) }
    }
    PolicyToggleRow(
        title = stringResource(R.string.parent_lyrics_genius_title),
        subtitle = stringResource(R.string.parent_lyrics_genius_subtitle),
        checked = policy.lyrics.enabled && policy.lyrics.allowGeniusMeanings,
        enabled = policy.lyrics.enabled,
    ) { v ->
        ParentalPolicyStore.update(context) { it.copy(lyrics = it.lyrics.copy(allowGeniusMeanings = v)) }
    }
    PolicyToggleRow(
        title = stringResource(R.string.parent_lyrics_third_party_title),
        subtitle = stringResource(R.string.parent_lyrics_third_party_subtitle),
        checked = policy.lyrics.enabled && policy.lyrics.allowExtraSources,
        enabled = policy.lyrics.enabled,
    ) { v ->
        ParentalPolicyStore.update(context) { it.copy(lyrics = it.lyrics.copy(allowExtraSources = v)) }
    }
}
