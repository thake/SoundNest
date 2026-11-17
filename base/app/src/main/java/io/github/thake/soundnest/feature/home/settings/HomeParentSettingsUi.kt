/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.home.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.ui.classic.AppAccent
import io.github.thake.soundnest.core.ui.classic.AppText
import io.github.thake.soundnest.core.ui.classic.tone
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore
import io.github.thake.soundnest.feature.parental.ui.PolicyToggleRow
import io.github.thake.soundnest.feature.parental.ui.RemovableChip
import io.github.thake.soundnest.feature.parental.ui.SectionCard

/**
 * Parent Controls Dashboard section for Front Page (Home) layout, carousels, and hidden shelves.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeParentSettingsSection(policy: ParentalPolicy) {
    val context = LocalContext.current
    val home = policy.home
    val audible = policy.spokenWord.audible
    SectionCard("Front Page (Home) Sections") {
        PolicyToggleRow(
            title = "Minimal Home Mode",
            subtitle = "Hide all algorithmic Spotify carousels; show only Shortcuts & Parent Picks",
            checked = home.minimalHomeMode,
        ) { v ->
            ParentalPolicyStore.update(context) { it.copy(home = it.home.copy(minimalHomeMode = v)) }
        }
        PolicyToggleRow(
            title = "Show Shortcuts Grid",
            subtitle = "Two-column quick-access tiles at the top of Home (including Liked Songs)",
            checked = home.showShortcutsGrid,
        ) { v ->
            ParentalPolicyStore.update(context) { it.copy(home = it.home.copy(showShortcutsGrid = v)) }
        }
        PolicyToggleRow(
            title = "Show Parent Picks Shelf",
            subtitle = "Pin a dedicated shelf of parent-approved playlists, albums, and artists",
            checked = home.showParentPicks,
        ) { v ->
            ParentalPolicyStore.update(context) { it.copy(home = it.home.copy(showParentPicks = v)) }
        }
        PolicyToggleRow(
            title = stringResource(R.string.audible_home_pinned_toggle_title),
            subtitle = stringResource(R.string.audible_home_pinned_toggle_subtitle),
            checked = home.showParentPicks && audible.showPinnedOnHome,
            enabled = home.showParentPicks,
            switchTestTag = "parent_home_pinned_audible_toggle",
        ) { v ->
            ParentalPolicyStore.update(context) {
                it.copy(
                    spokenWord = it.spokenWord.copy(
                        audible = it.spokenWord.audible.copy(showPinnedOnHome = v),
                    ),
                )
            }
        }
        PolicyToggleRow(
            title = "Show What's New Bell & Notifications",
            subtitle = "Bell icon on Home and background release notifications",
            checked = home.showWhatsNewBell,
            enabled = !home.minimalHomeMode && home.showNewReleases,
        ) { v ->
            ParentalPolicyStore.update(context) { it.copy(home = it.home.copy(showWhatsNewBell = v)) }
        }
        PolicyToggleRow(
            title = "Show Filter Chips",
            subtitle = "All / Music / Podcasts / Audiobooks chips at the top of Home",
            checked = home.showFilterChips,
            enabled = !home.minimalHomeMode,
        ) { v ->
            ParentalPolicyStore.update(context) { it.copy(home = it.home.copy(showFilterChips = v)) }
        }
        PolicyToggleRow(
            title = "Show Spotify AI DJ",
            subtitle = "Allow Spotify's AI DJ card on Home (off by default)",
            checked = home.showDjCard,
        ) { v ->
            ParentalPolicyStore.update(context) { it.copy(home = it.home.copy(showDjCard = v)) }
        }

        HorizontalDivider(color = tone(0xFF3A3A3A))
        Text("Category Carousels", color = AppText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)

        PolicyToggleRow(
            title = "Charts, Trending & Popular",
            subtitle = "Top 50, Viral Hits, Trending now, Popular albums",
            checked = home.showChartsAndTrending,
            enabled = !home.minimalHomeMode,
        ) { v ->
            ParentalPolicyStore.update(context) { it.copy(home = it.home.copy(showChartsAndTrending = v)) }
        }
        PolicyToggleRow(
            title = "Mixes, Made For You & Discover",
            subtitle = "Daily Mixes, Discover Weekly, Recommended for today",
            checked = home.showMixesAndMadeForYou,
            enabled = !home.minimalHomeMode,
        ) { v ->
            ParentalPolicyStore.update(context) { it.copy(home = it.home.copy(showMixesAndMadeForYou = v)) }
        }
        PolicyToggleRow(
            title = "New Releases",
            subtitle = "New releases for you, Release Radar, Fresh new music",
            checked = home.showNewReleases,
            enabled = !home.minimalHomeMode,
        ) { v ->
            ParentalPolicyStore.update(context) { it.copy(home = it.home.copy(showNewReleases = v)) }
        }
        PolicyToggleRow(
            title = "Recently Played & Jump Back In",
            subtitle = "Recently played and Heavy rotation carousels",
            checked = home.showRecents,
            enabled = !home.minimalHomeMode,
        ) { v ->
            ParentalPolicyStore.update(context) { it.copy(home = it.home.copy(showRecents = v)) }
        }

        if (home.hiddenShelfKeys.isNotEmpty()) {
            HorizontalDivider(color = tone(0xFF3A3A3A))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Individually Hidden Shelves (${home.hiddenShelfKeys.size})",
                    color = AppText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    ParentalPolicyStore.update(context) {
                        it.copy(home = it.home.copy(hiddenShelfKeys = emptySet()))
                    }
                }) {
                    Text("Restore All", color = AppAccent)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                home.hiddenShelfKeys.sorted().forEach { key ->
                    RemovableChip(label = key) { ParentalPolicyStore.unhideHomeShelf(context, key) }
                }
            }
        }
    }
}
