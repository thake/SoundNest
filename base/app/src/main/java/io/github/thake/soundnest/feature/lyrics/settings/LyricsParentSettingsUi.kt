/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.lyrics.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
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
        title = "Song Lyrics",
        subtitle = "Show synchronized Spotify lyrics in the player",
        checked = policy.lyrics.enabled,
    ) { v ->
        ParentalPolicyStore.update(context) { it.copy(lyrics = it.lyrics.copy(enabled = v)) }
    }
    PolicyToggleRow(
        title = "Genius Song Explanations",
        subtitle = "Allow community-written Genius annotations (off by default)",
        checked = policy.lyrics.enabled && policy.lyrics.allowGeniusMeanings,
        enabled = policy.lyrics.enabled,
    ) { v ->
        ParentalPolicyStore.update(context) { it.copy(lyrics = it.lyrics.copy(allowGeniusMeanings = v)) }
    }
    PolicyToggleRow(
        title = "Third-Party Lyric Providers",
        subtitle = "Fallback to LRCLIB / NetEase / QQ when Spotify has no lyrics",
        checked = policy.lyrics.enabled && policy.lyrics.allowExtraSources,
        enabled = policy.lyrics.enabled,
    ) { v ->
        ParentalPolicyStore.update(context) { it.copy(lyrics = it.lyrics.copy(allowExtraSources = v)) }
    }
}
