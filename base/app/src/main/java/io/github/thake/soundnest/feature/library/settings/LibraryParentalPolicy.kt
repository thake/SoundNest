/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.library.settings

import kotlinx.serialization.Serializable

/**
 * Nested parental policy configuration for Spotify Library write protection.
 */
@Serializable
data class LibraryParentalPolicy(
    val readOnlySpotifyLibrary: Boolean = false,
)
