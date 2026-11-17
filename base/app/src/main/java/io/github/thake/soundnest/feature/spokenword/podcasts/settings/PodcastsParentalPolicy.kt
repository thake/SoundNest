/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.spokenword.podcasts.settings

import kotlinx.serialization.Serializable

@Serializable
data class PodcastsParentalPolicy(
    val allowedShowIds: Set<String> = emptySet(),
    val blockedShowIds: Set<String> = emptySet(),
)
