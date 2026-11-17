/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.spokenword.audible.settings

import kotlinx.serialization.Serializable

@Serializable
data class AudibleParentalPolicy(
    val allowedAsins: Set<String> = emptySet(),
    val pinnedAsins: Set<String> = emptySet(),
    val showPinnedOnHome: Boolean = true,
)
