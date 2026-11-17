/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.home.settings

import kotlinx.serialization.Serializable

/**
 * Nested parental policy configuration for Home (Front Page) sections and shelves.
 * Note: pinned Audible audiobooks visibility lives in `AudibleParentalPolicy.showPinnedOnHome`.
 */
@Serializable
data class HomeParentalPolicy(
    val minimalHomeMode: Boolean = false,
    val showShortcutsGrid: Boolean = true,
    val showWhatsNewBell: Boolean = true,
    val showFilterChips: Boolean = true,
    val showDjCard: Boolean = false,
    val showParentPicks: Boolean = true,
    val showChartsAndTrending: Boolean = true,
    val showMixesAndMadeForYou: Boolean = true,
    val showNewReleases: Boolean = true,
    val showRecents: Boolean = true,
    val hiddenShelfKeys: Set<String> = emptySet(),
)
