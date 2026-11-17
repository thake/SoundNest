/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.lyrics.settings

import kotlinx.serialization.Serializable

/**
 * Parental policy slice for Lyrics, Genius Song Meanings, and Extra Community Lyric Providers.
 * Wired into [io.github.thake.soundnest.feature.parental.settings.ParentalPolicy] in Phase 4.
 */
@Serializable
data class LyricsParentalPolicy(
    val enabled: Boolean = true,
    val allowGeniusMeanings: Boolean = false,
    val allowExtraSources: Boolean = false,
)
