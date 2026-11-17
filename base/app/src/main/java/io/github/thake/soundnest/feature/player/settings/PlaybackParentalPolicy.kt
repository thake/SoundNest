/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.player.settings

import kotlinx.serialization.Serializable

/**
 * Nested parental policy configuration for Autoplay, Smart Shuffle, and Song/Artist Radio.
 */
@Serializable
data class PlaybackParentalPolicy(
    val allowAutoplayAndRadio: Boolean = true,
)
