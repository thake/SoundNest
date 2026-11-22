/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.parental.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.ui.immersive.shell.ImmersiveBarAction
import io.github.thake.soundnest.core.ui.settings.M3RemovableChip
import io.github.thake.soundnest.core.ui.settings.M3SectionBlock
import io.github.thake.soundnest.core.ui.settings.M3SettingsChoiceRow
import io.github.thake.soundnest.core.ui.settings.M3SettingsNavRow
import io.github.thake.soundnest.core.ui.settings.M3SettingsScaffold
import io.github.thake.soundnest.core.ui.settings.M3SettingsSwitchRow
import io.github.thake.soundnest.core.ui.settings.m3FilledButtonColors
import io.github.thake.soundnest.core.ui.settings.m3SettingsSection
import io.github.thake.soundnest.core.ui.settings.rememberM3SettingsPalette
import io.github.thake.soundnest.feature.parental.settings.FilterMode
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ParentFilterRulesPage(
    context: Context,
    policy: ParentalPolicy,
    listState: LazyListState,
    back: () -> Unit,
    lockAndExit: () -> Unit,
    onOpenRulesSheet: (Boolean) -> Unit,
) {
    val palette = rememberM3SettingsPalette()
    var uriInput by rememberSaveable { mutableStateOf("") }
    var keywordInput by rememberSaveable { mutableStateOf("") }
    var regexInput by rememberSaveable { mutableStateOf("") }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = palette.text,
        unfocusedTextColor = palette.text,
        focusedBorderColor = palette.accent,
        unfocusedBorderColor = palette.divider,
        focusedLabelColor = palette.accent,
        unfocusedLabelColor = palette.muted,
        cursorColor = palette.accent,
    )

    M3SettingsScaffold(
        title = stringResource(R.string.parent_section_filter_rules),
        onBack = back,
        listState = listState,
        listTestTag = "parent_settings_list",
        classicActions = { ParentLockExitClassicAction(palette, lockAndExit) },
        immersiveActions = {
            ImmersiveBarAction(
                icon = Icons.Rounded.Lock,
                contentDescription = stringResource(R.string.parent_pin_lock),
                onClick = lockAndExit,
            )
        },
    ) { scaffoldPalette ->

        m3SettingsSection(
            key = "parent_filter:mode",
            palette = scaffoldPalette,
            header = context.getString(R.string.parent_filter_section_mode),
        ) {
            row {
                M3SettingsChoiceRow(
                    title = stringResource(R.string.parent_mode_filtered_open_title),
                    detail = stringResource(R.string.parent_mode_filtered_open_subtitle),
                    selected = policy.filterMode == FilterMode.FILTERED_OPEN,
                    palette = scaffoldPalette,
                    onClick = {
                        ParentalPolicyStore.update(context) { it.copy(filterMode = FilterMode.FILTERED_OPEN) }
                    },
                )
            }
            row {
                M3SettingsChoiceRow(
                    title = stringResource(R.string.parent_mode_strict_allowlist_title),
                    detail = stringResource(R.string.parent_mode_strict_allowlist_subtitle),
                    selected = policy.filterMode == FilterMode.STRICT_ALLOWLIST,
                    palette = scaffoldPalette,
                    onClick = {
                        ParentalPolicyStore.update(context) { it.copy(filterMode = FilterMode.STRICT_ALLOWLIST) }
                    },
                )
            }
            row {
                M3SettingsSwitchRow(
                    title = stringResource(R.string.parent_filter_block_explicit_title),
                    detail = stringResource(R.string.parent_filter_block_explicit_subtitle),
                    checked = policy.blockExplicit,
                    palette = scaffoldPalette,
                    onCheckedChange = { checked ->
                        ParentalPolicyStore.update(context) { it.copy(blockExplicit = checked) }
                    },
                )
            }
        }

        m3SettingsSection(
            key = "parent_filter:lists",
            palette = scaffoldPalette,
            header = context.getString(R.string.parent_filter_section_lists),
            hasLeadingIconBadges = true,
        ) {
            row {
                val approvedSummary = buildRuleSummaryText(
                    context = context,
                    playlists = policy.allowedPlaylistIds.size,
                    albums = policy.allowedAlbumIds.size,
                    artists = policy.allowedArtistIds.size,
                    tracks = policy.allowedTrackIds.size,
                    shows = policy.spokenWord.podcasts.allowedShowIds.size,
                    audibleBooks = policy.spokenWord.audible.allowedAsins.size,
                )
                M3SettingsNavRow(
                    icon = Icons.Rounded.CheckCircle,
                    title = stringResource(R.string.parent_rules_approved_title),
                    detail = approvedSummary,
                    value = stringResource(R.string.parent_rules_manage_button),
                    valueColor = scaffoldPalette.accent,
                    palette = scaffoldPalette,
                    testTag = "parent_manage_approved_rules",
                    onClick = { onOpenRulesSheet(true) },
                )
            }
            row {
                val blockedSummary = buildRuleSummaryText(
                    context = context,
                    playlists = policy.blockedPlaylistIds.size,
                    albums = policy.blockedAlbumIds.size,
                    artists = policy.blockedArtistIds.size,
                    tracks = policy.blockedTrackIds.size,
                    shows = policy.spokenWord.podcasts.blockedShowIds.size,
                )
                M3SettingsNavRow(
                    icon = Icons.Rounded.Block,
                    title = stringResource(R.string.parent_rules_blocked_title),
                    detail = blockedSummary,
                    value = stringResource(R.string.parent_rules_manage_button),
                    valueColor = scaffoldPalette.destructive,
                    destructive = true,
                    palette = scaffoldPalette,
                    testTag = "parent_manage_blocked_rules",
                    onClick = { onOpenRulesSheet(false) },
                )
            }
        }

        item(key = "parent_filter:quick", contentType = "m3:block") {
            M3SectionBlock(
                palette = scaffoldPalette,
                header = context.getString(R.string.parent_filter_section_quick),
                footer = context.getString(R.string.parent_quick_tip),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.parent_quick_allow_block_title),
                        color = scaffoldPalette.text,
                        fontFamily = scaffoldPalette.fontFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    OutlinedTextField(
                        value = uriInput,
                        onValueChange = { uriInput = it },
                        label = {
                            Text(
                                text = stringResource(R.string.parent_quick_allow_block_placeholder),
                                fontFamily = scaffoldPalette.fontFamily,
                            )
                        },
                        singleLine = true,
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            enabled = uriInput.isNotBlank(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = scaffoldPalette.accent,
                                disabledContentColor = scaffoldPalette.muted.copy(alpha = 0.45f),
                            ),
                            onClick = {
                                val applied = applyQuickUriRule(context, uriInput, allow = true)
                                if (applied) {
                                    Toast.makeText(context, context.getString(R.string.parent_toast_added_allowlist), Toast.LENGTH_SHORT).show()
                                    uriInput = ""
                                } else {
                                    Toast.makeText(context, context.getString(R.string.parent_toast_unrecognized_link), Toast.LENGTH_SHORT).show()
                                }
                            },
                        ) {
                            Text(
                                text = stringResource(R.string.parent_action_allow),
                                fontFamily = scaffoldPalette.fontFamily,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        OutlinedButton(
                            enabled = uriInput.isNotBlank(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = scaffoldPalette.destructive,
                                disabledContentColor = scaffoldPalette.muted.copy(alpha = 0.45f),
                            ),
                            onClick = {
                                val applied = applyQuickUriRule(context, uriInput, allow = false)
                                if (applied) {
                                    Toast.makeText(context, context.getString(R.string.parent_toast_added_blocklist), Toast.LENGTH_SHORT).show()
                                    uriInput = ""
                                } else {
                                    Toast.makeText(context, context.getString(R.string.parent_toast_unrecognized_link), Toast.LENGTH_SHORT).show()
                                }
                            },
                        ) {
                            Text(
                                text = stringResource(R.string.parent_action_block),
                                fontFamily = scaffoldPalette.fontFamily,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }

        item(key = "parent_filter:patterns", contentType = "m3:block") {
            val buttonColors = m3FilledButtonColors(scaffoldPalette)
            M3SectionBlock(
                palette = scaffoldPalette,
                header = context.getString(R.string.parent_filter_section_patterns),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.parent_rules_keywords_title),
                        color = scaffoldPalette.text,
                        fontFamily = scaffoldPalette.fontFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = keywordInput,
                            onValueChange = { keywordInput = it },
                            label = {
                                Text(
                                    text = stringResource(R.string.parent_rules_keywords_placeholder),
                                    fontFamily = scaffoldPalette.fontFamily,
                                )
                            },
                            singleLine = true,
                            colors = fieldColors,
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            enabled = keywordInput.isNotBlank(),
                            onClick = {
                                ParentalPolicyStore.addBlockedKeyword(context, keywordInput)
                                keywordInput = ""
                            },
                            colors = buttonColors,
                        ) {
                            Text(
                                text = stringResource(R.string.parent_rules_keywords_add),
                                fontFamily = scaffoldPalette.fontFamily,
                            )
                        }
                    }
                    if (policy.blockedKeywords.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            policy.blockedKeywords.sorted().forEach { kw ->
                                M3RemovableChip(label = kw, palette = scaffoldPalette) {
                                    ParentalPolicyStore.removeBlockedKeyword(context, kw)
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(
                    color = scaffoldPalette.divider,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.parent_rules_regex_title),
                        color = scaffoldPalette.text,
                        fontFamily = scaffoldPalette.fontFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = regexInput,
                            onValueChange = { regexInput = it },
                            label = {
                                Text(
                                    text = stringResource(R.string.parent_rules_regex_placeholder),
                                    fontFamily = scaffoldPalette.fontFamily,
                                )
                            },
                            singleLine = true,
                            colors = fieldColors,
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            enabled = regexInput.isNotBlank(),
                            onClick = {
                                val valid = runCatching { Regex(regexInput.trim(), RegexOption.IGNORE_CASE) }.isSuccess
                                if (!valid) {
                                    Toast.makeText(context, context.getString(R.string.parent_rules_regex_error), Toast.LENGTH_SHORT).show()
                                } else {
                                    ParentalPolicyStore.addBlockedRegex(context, regexInput)
                                    regexInput = ""
                                }
                            },
                            colors = buttonColors,
                        ) {
                            Text(
                                text = stringResource(R.string.parent_rules_keywords_add),
                                fontFamily = scaffoldPalette.fontFamily,
                            )
                        }
                    }
                    if (policy.blockedRegexPatterns.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            policy.blockedRegexPatterns.sorted().forEach { rx ->
                                M3RemovableChip(label = "/$rx/", palette = scaffoldPalette) {
                                    ParentalPolicyStore.removeBlockedRegex(context, rx)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
