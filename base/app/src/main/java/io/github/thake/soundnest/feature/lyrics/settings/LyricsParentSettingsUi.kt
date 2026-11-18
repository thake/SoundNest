/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.lyrics.settings

import android.content.Context
import androidx.compose.ui.res.stringResource
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.ui.settings.M3SectionScope
import io.github.thake.soundnest.core.ui.settings.M3SettingsPalette
import io.github.thake.soundnest.core.ui.settings.M3SettingsSwitchRow
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore

/**
 * Parent Controls Dashboard toggle rows for Song Lyrics, Genius Song Explanations,
 * and Third-Party Lyric Providers.
 */
fun M3SectionScope.m3LyricsParentRows(
    context: Context,
    policy: ParentalPolicy,
    palette: M3SettingsPalette,
) {
    row {
        M3SettingsSwitchRow(
            title = stringResource(R.string.parent_lyrics_title),
            detail = stringResource(R.string.parent_lyrics_subtitle),
            checked = policy.lyrics.enabled,
            palette = palette,
            onCheckedChange = { v ->
                ParentalPolicyStore.update(context) { it.copy(lyrics = it.lyrics.copy(enabled = v)) }
            },
        )
    }
    row {
        M3SettingsSwitchRow(
            title = stringResource(R.string.parent_lyrics_genius_title),
            detail = stringResource(R.string.parent_lyrics_genius_subtitle),
            checked = policy.lyrics.enabled && policy.lyrics.allowGeniusMeanings,
            enabled = policy.lyrics.enabled,
            palette = palette,
            onCheckedChange = { v ->
                ParentalPolicyStore.update(context) { it.copy(lyrics = it.lyrics.copy(allowGeniusMeanings = v)) }
            },
        )
    }
    row {
        M3SettingsSwitchRow(
            title = stringResource(R.string.parent_lyrics_third_party_title),
            detail = stringResource(R.string.parent_lyrics_third_party_subtitle),
            checked = policy.lyrics.enabled && policy.lyrics.allowExtraSources,
            enabled = policy.lyrics.enabled,
            palette = palette,
            onCheckedChange = { v ->
                ParentalPolicyStore.update(context) { it.copy(lyrics = it.lyrics.copy(allowExtraSources = v)) }
            },
        )
    }
}
