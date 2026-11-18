/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.home.settings

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.ui.settings.M3GroupCard
import io.github.thake.soundnest.core.ui.settings.M3RemovableChip
import io.github.thake.soundnest.core.ui.settings.M3SettingsPalette
import io.github.thake.soundnest.core.ui.settings.M3SettingsSwitchRow
import io.github.thake.soundnest.core.ui.settings.m3SettingsBlock
import io.github.thake.soundnest.core.ui.settings.m3SettingsSection
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore

/**
 * Material Design 3 sections for Parent Controls Front Page (Home) layout, category carousels,
 * and hidden shelves.
 */
@OptIn(ExperimentalLayoutApi::class)
fun LazyListScope.m3HomeParentSettingsSections(
    context: Context,
    policy: ParentalPolicy,
    palette: M3SettingsPalette,
) {
    val home = policy.home
    val audible = policy.spokenWord.audible

    m3SettingsSection(
        key = "parent_home:layout",
        palette = palette,
        header = context.getString(R.string.parent_home_section_layout),
    ) {
        row {
            M3SettingsSwitchRow(
                title = stringResource(R.string.parent_home_minimal_title),
                detail = stringResource(R.string.parent_home_minimal_subtitle),
                checked = home.minimalHomeMode,
                palette = palette,
                onCheckedChange = { v ->
                    ParentalPolicyStore.update(context) { it.copy(home = it.home.copy(minimalHomeMode = v)) }
                },
            )
        }
        row {
            M3SettingsSwitchRow(
                title = stringResource(R.string.parent_home_shortcuts_title),
                detail = stringResource(R.string.parent_home_shortcuts_subtitle),
                checked = home.showShortcutsGrid,
                palette = palette,
                onCheckedChange = { v ->
                    ParentalPolicyStore.update(context) { it.copy(home = it.home.copy(showShortcutsGrid = v)) }
                },
            )
        }
        row {
            M3SettingsSwitchRow(
                title = stringResource(R.string.parent_home_picks_title),
                detail = stringResource(R.string.parent_home_picks_subtitle),
                checked = home.showParentPicks,
                palette = palette,
                onCheckedChange = { v ->
                    ParentalPolicyStore.update(context) { it.copy(home = it.home.copy(showParentPicks = v)) }
                },
            )
        }
        row {
            M3SettingsSwitchRow(
                title = stringResource(R.string.audible_home_pinned_toggle_title),
                detail = stringResource(R.string.audible_home_pinned_toggle_subtitle),
                checked = home.showParentPicks && audible.showPinnedOnHome,
                palette = palette,
                enabled = home.showParentPicks,
                switchTestTag = "parent_home_pinned_audible_toggle",
                onCheckedChange = { v ->
                    ParentalPolicyStore.update(context) {
                        it.copy(
                            spokenWord = it.spokenWord.copy(
                                audible = it.spokenWord.audible.copy(showPinnedOnHome = v),
                            ),
                        )
                    }
                },
            )
        }
        row {
            M3SettingsSwitchRow(
                title = stringResource(R.string.parent_home_bell_title),
                detail = stringResource(R.string.parent_home_bell_subtitle),
                checked = home.showWhatsNewBell,
                palette = palette,
                enabled = !home.minimalHomeMode && home.showNewReleases,
                onCheckedChange = { v ->
                    ParentalPolicyStore.update(context) { it.copy(home = it.home.copy(showWhatsNewBell = v)) }
                },
            )
        }
        row {
            M3SettingsSwitchRow(
                title = stringResource(R.string.parent_home_chips_title),
                detail = stringResource(R.string.parent_home_chips_subtitle),
                checked = home.showFilterChips,
                palette = palette,
                enabled = !home.minimalHomeMode,
                onCheckedChange = { v ->
                    ParentalPolicyStore.update(context) { it.copy(home = it.home.copy(showFilterChips = v)) }
                },
            )
        }
        row {
            M3SettingsSwitchRow(
                title = stringResource(R.string.parent_home_dj_title),
                detail = stringResource(R.string.parent_home_dj_subtitle),
                checked = home.showDjCard,
                palette = palette,
                onCheckedChange = { v ->
                    ParentalPolicyStore.update(context) { it.copy(home = it.home.copy(showDjCard = v)) }
                },
            )
        }
    }

    m3SettingsSection(
        key = "parent_home:carousels",
        palette = palette,
        header = context.getString(R.string.parent_home_category_carousels),
    ) {
        row {
            M3SettingsSwitchRow(
                title = stringResource(R.string.parent_home_charts_title),
                detail = stringResource(R.string.parent_home_charts_subtitle),
                checked = home.showChartsAndTrending,
                palette = palette,
                enabled = !home.minimalHomeMode,
                onCheckedChange = { v ->
                    ParentalPolicyStore.update(context) { it.copy(home = it.home.copy(showChartsAndTrending = v)) }
                },
            )
        }
        row {
            M3SettingsSwitchRow(
                title = stringResource(R.string.parent_home_mixes_title),
                detail = stringResource(R.string.parent_home_mixes_subtitle),
                checked = home.showMixesAndMadeForYou,
                palette = palette,
                enabled = !home.minimalHomeMode,
                onCheckedChange = { v ->
                    ParentalPolicyStore.update(context) { it.copy(home = it.home.copy(showMixesAndMadeForYou = v)) }
                },
            )
        }
        row {
            M3SettingsSwitchRow(
                title = stringResource(R.string.parent_home_releases_title),
                detail = stringResource(R.string.parent_home_releases_subtitle),
                checked = home.showNewReleases,
                palette = palette,
                enabled = !home.minimalHomeMode,
                onCheckedChange = { v ->
                    ParentalPolicyStore.update(context) { it.copy(home = it.home.copy(showNewReleases = v)) }
                },
            )
        }
        row {
            M3SettingsSwitchRow(
                title = stringResource(R.string.parent_home_recents_title),
                detail = stringResource(R.string.parent_home_recents_subtitle),
                checked = home.showRecents,
                palette = palette,
                enabled = !home.minimalHomeMode,
                onCheckedChange = { v ->
                    ParentalPolicyStore.update(context) { it.copy(home = it.home.copy(showRecents = v)) }
                },
            )
        }
    }

    if (home.hiddenShelfKeys.isNotEmpty()) {
        m3SettingsBlock(key = "parent_home:hidden-shelves", palette = palette) {
            M3GroupCard(palette = palette) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.parent_home_hidden_shelves_count, home.hiddenShelfKeys.size),
                            color = palette.text,
                            fontFamily = palette.fontFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = {
                            ParentalPolicyStore.update(context) {
                                it.copy(home = it.home.copy(hiddenShelfKeys = emptySet()))
                            }
                        }) {
                            Text(
                                text = stringResource(R.string.parent_home_restore_all),
                                color = palette.accent,
                                fontFamily = palette.fontFamily,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        home.hiddenShelfKeys.sorted().forEach { key ->
                            M3RemovableChip(label = key, palette = palette) {
                                ParentalPolicyStore.unhideHomeShelf(context, key)
                            }
                        }
                    }
                }
            }
        }
    }
}
