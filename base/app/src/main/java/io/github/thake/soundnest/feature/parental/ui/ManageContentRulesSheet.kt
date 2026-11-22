/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.parental.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.ui.look.LookSheet
import io.github.thake.soundnest.core.ui.settings.m3FilterChipColors
import io.github.thake.soundnest.core.ui.settings.rememberM3SettingsPalette
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore
import io.github.thake.soundnest.feature.parental.settings.RuleLabelStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ManageContentRulesSheet(
    initialTabIsApproved: Boolean,
    onDismiss: () -> Unit,
) {
    val palette = rememberM3SettingsPalette()
    LookSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.elevatedCardBg,
    ) {
        ManageContentRulesSheetBody(
            initialTabIsApproved = initialTabIsApproved,
            onDismiss = onDismiss,
        )
    }
}

@Composable
internal fun ManageContentRulesSheetBody(
    initialTabIsApproved: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val palette = rememberM3SettingsPalette()
    val policy by ParentalPolicyStore.policyFlow.collectAsState()
    var showApproved by remember(initialTabIsApproved) { mutableStateOf(initialTabIsApproved) }
    var searchQuery by remember { mutableStateOf("") }

    val approvedChipColors = m3FilterChipColors(palette)
    val blockedChipColors = m3FilterChipColors(palette, selectedContainerColor = palette.destructive)

    val approvedEntries = remember(policy) {
        collectManagedRuleEntries(
            context = context,
            playlists = policy.allowedPlaylistIds,
            albums = policy.allowedAlbumIds,
            artists = policy.allowedArtistIds,
            tracks = policy.allowedTrackIds,
            shows = policy.spokenWord.podcasts.allowedShowIds,
            audibleAsins = policy.spokenWord.audible.allowedAsins,
        )
    }
    val blockedEntries = remember(policy) {
        collectManagedRuleEntries(
            context = context,
            playlists = policy.blockedPlaylistIds,
            albums = policy.blockedAlbumIds,
            artists = policy.blockedArtistIds,
            tracks = policy.blockedTrackIds,
            shows = policy.spokenWord.podcasts.blockedShowIds,
        )
    }

    val activeEntries = if (showApproved) approvedEntries else blockedEntries
    val filteredEntries = remember(activeEntries, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) activeEntries
        else activeEntries.filter { entry ->
            entry.title.lowercase().contains(q) ||
                entry.subtitle.lowercase().contains(q) ||
                entry.id.lowercase().contains(q) ||
                entry.kind.lowercase().contains(q)
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.parent_manage_rules_title),
                color = palette.text,
                fontFamily = palette.fontFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), tint = palette.muted)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = showApproved,
                onClick = { showApproved = true },
                colors = approvedChipColors,
                leadingIcon = if (showApproved) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null,
                label = {
                    Text(
                        stringResource(R.string.parent_rules_approved_tab_count, approvedEntries.size),
                        fontFamily = palette.fontFamily,
                        fontWeight = if (showApproved) FontWeight.Bold else FontWeight.Normal,
                    )
                },
                modifier = Modifier.testTag("rules_tab_approved"),
            )
            FilterChip(
                selected = !showApproved,
                onClick = { showApproved = false },
                colors = blockedChipColors,
                leadingIcon = if (!showApproved) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null,
                label = {
                    Text(
                        stringResource(R.string.parent_rules_blocked_tab_count, blockedEntries.size),
                        fontFamily = palette.fontFamily,
                        fontWeight = if (!showApproved) FontWeight.Bold else FontWeight.Normal,
                    )
                },
                modifier = Modifier.testTag("rules_tab_blocked"),
            )
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    stringResource(R.string.parent_rules_search_placeholder),
                    color = palette.muted,
                    fontFamily = palette.fontFamily,
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = palette.text,
                unfocusedTextColor = palette.text,
                focusedBorderColor = palette.accent,
                unfocusedBorderColor = palette.divider,
                cursorColor = palette.accent,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("rules_search_input"),
        )

        if (filteredEntries.isEmpty()) {
            val emptyMessage = when {
                searchQuery.isNotBlank() -> stringResource(R.string.parent_rules_no_match, searchQuery.trim())
                showApproved -> stringResource(R.string.parent_rules_empty_approved)
                else -> stringResource(R.string.parent_rules_empty_blocked)
            }
            Text(
                text = emptyMessage,
                color = palette.muted,
                fontFamily = palette.fontFamily,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 20.dp),
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
            ) {
                items(filteredEntries, key = { "${it.kind}:${it.id}" }) { entry ->
                    val badgeColor = if (showApproved) palette.accent else palette.destructive
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(palette.innerCardBg)
                            .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                    ) {
                        Text(
                            text = RuleLabelStore.kindDisplayName(entry.kind),
                            color = badgeColor,
                            fontFamily = palette.fontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(badgeColor.copy(alpha = 0.16f))
                                .padding(horizontal = 7.dp, vertical = 3.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = entry.title,
                                color = palette.text,
                                fontFamily = palette.fontFamily,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (entry.subtitle.isNotBlank()) {
                                Text(
                                    text = entry.subtitle,
                                    color = palette.muted,
                                    fontFamily = palette.fontFamily,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        IconButton(
                            onClick = { clearManagedRule(context, entry.kind, entry.id) },
                            modifier = Modifier.testTag("rule_remove_${entry.kind}_${entry.id}"),
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = stringResource(R.string.parent_action_remove_rule),
                                tint = palette.muted,
                            )
                        }
                    }
                }
            }
        }
    }
}
