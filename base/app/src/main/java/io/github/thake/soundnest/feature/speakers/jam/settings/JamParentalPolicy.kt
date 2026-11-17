/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.speakers.jam.settings

import kotlinx.serialization.Serializable

/**
 * Nested parental policy configuration for Spotify Jam shared listening sessions.
 * Fail-closed (`enabled = false`) by default for children's safety.
 */
@Serializable
data class JamParentalPolicy(
    val enabled: Boolean = false,
)
