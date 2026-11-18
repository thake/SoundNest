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
    SectionCard(stringResource(R.string.parent_section_home)) {
        PolicyToggleRow(
            title = stringResource(R.string.parent_home_minimal_title),
            subtitle = stringResource(R.string.parent_home_minimal_subtitle),
            checked = home.minimalHomeMode,
        ) { v ->
            ParentalPolicyStore.update(context) { it.copy(home = it.home.copy(minimalHomeMode = v)) }
        }
        PolicyToggleRow(
            title = stringResource(R.string.parent_home_shortcuts_title),
            subtitle = stringResource(R.string.parent_home_shortcuts_subtitle),
            checked = home.showShortcutsGrid,
        ) { v ->
            ParentalPolicyStore.update(context) { it.copy(home = it.home.copy(showShortcutsGrid = v)) }
        }
        PolicyToggleRow(
            title = stringResource(R.string.parent_home_picks_title),
            subtitle = stringResource(R.string.parent_home_picks_subtitle),
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
            title = stringResource(R.string.parent_home_bell_title),
            subtitle = stringResource(R.string.parent_home_bell_subtitle),
            checked = home.showWhatsNewBell,
            enabled = !home.minimalHomeMode && home.showNewReleases,
        ) { v ->
            ParentalPolicyStore.update(context) { it.copy(home = it.home.copy(showWhatsNewBell = v)) }
        }
        PolicyToggleRow(
            title = stringResource(R.string.parent_home_chips_title),
            subtitle = stringResource(R.string.parent_home_chips_subtitle),
            checked = home.showFilterChips,
            enabled = !home.minimalHomeMode,
        ) { v ->
            ParentalPolicyStore.update(context) { it.copy(home = it.home.copy(showFilterChips = v)) }
        }
        PolicyToggleRow(
            title = stringResource(R.string.parent_home_dj_title),
            subtitle = stringResource(R.string.parent_home_dj_subtitle),
            checked = home.showDjCard,
        ) { v ->
            ParentalPolicyStore.update(context) { it.copy(home = it.home.copy(showDjCard = v)) }
        }

        HorizontalDivider(color = tone(0xFF3A3A3A))
        Text(stringResource(R.string.parent_home_category_carousels), color = AppText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)

        PolicyToggleRow(
            title = stringResource(R.string.parent_home_charts_title),
            subtitle = stringResource(R.string.parent_home_charts_subtitle),
            checked = home.showChartsAndTrending,
            enabled = !home.minimalHomeMode,
        ) { v ->
            ParentalPolicyStore.update(context) { it.copy(home = it.home.copy(showChartsAndTrending = v)) }
        }
        PolicyToggleRow(
            title = stringResource(R.string.parent_home_mixes_title),
            subtitle = stringResource(R.string.parent_home_mixes_subtitle),
            checked = home.showMixesAndMadeForYou,
            enabled = !home.minimalHomeMode,
        ) { v ->
            ParentalPolicyStore.update(context) { it.copy(home = it.home.copy(showMixesAndMadeForYou = v)) }
        }
        PolicyToggleRow(
            title = stringResource(R.string.parent_home_releases_title),
            subtitle = stringResource(R.string.parent_home_releases_subtitle),
            checked = home.showNewReleases,
            enabled = !home.minimalHomeMode,
        ) { v ->
            ParentalPolicyStore.update(context) { it.copy(home = it.home.copy(showNewReleases = v)) }
        }
        PolicyToggleRow(
            title = stringResource(R.string.parent_home_recents_title),
            subtitle = stringResource(R.string.parent_home_recents_subtitle),
            checked = home.showRecents,
            enabled = !home.minimalHomeMode,
        ) { v ->
            ParentalPolicyStore.update(context) { it.copy(home = it.home.copy(showRecents = v)) }
        }

        if (home.hiddenShelfKeys.isNotEmpty()) {
            HorizontalDivider(color = tone(0xFF3A3A3A))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.parent_home_hidden_shelves_count, home.hiddenShelfKeys.size),
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
                    Text(stringResource(R.string.parent_home_restore_all), color = AppAccent)
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
