/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.parental.remote.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.ui.settings.M3GroupCard
import io.github.thake.soundnest.core.ui.settings.M3SettingsPalette
import io.github.thake.soundnest.core.ui.settings.M3SettingsScaffold
import io.github.thake.soundnest.core.ui.settings.m3FilterChipColors
import io.github.thake.soundnest.core.ui.settings.m3SettingsBlock
import io.github.thake.soundnest.feature.parental.remote.net.ParentRemoteClient
import io.github.thake.soundnest.feature.parental.remote.net.RemoteCatalogItem
import io.github.thake.soundnest.feature.parental.remote.net.RemoteResponse
import io.github.thake.soundnest.feature.parental.remote.settings.PairedChildRecord
import io.github.thake.soundnest.feature.parental.remote.settings.RemotePolicySession
import io.github.thake.soundnest.feature.parental.remote.settings.RemoteSyncState
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore
import io.github.thake.soundnest.feature.parental.ui.ParentBlockActions
import io.github.thake.soundnest.feature.parental.ui.ParentBlockRed
import io.github.thake.soundnest.feature.parental.ui.ParentBlockTarget
import io.github.thake.soundnest.feature.parental.ui.ParentPickGreen
import io.github.thake.soundnest.feature.parental.ui.ParentQuickBlockSheet
import io.github.thake.soundnest.feature.parental.ui.ParentRuleKind
import kotlinx.coroutines.delay

/**
 * Remote catalog browser allowing a Parent Device to search Spotify and browse a paired Child Device's
 * Library & Audible catalog, picking and blocking items with the exact same actions as the children's app.
 */
@Composable
fun RemoteCatalogBrowserScreen(
    child: PairedChildRecord,
    onBack: () -> Unit,
    initialTab: Int = 0,
) {
    val context = LocalContext.current
    val policy by ParentalPolicyStore.policyFlow.collectAsState()
    val syncState by RemotePolicySession.syncStateFlow.collectAsState()

    var selectedTab by remember { mutableIntStateOf(initialTab) } // 0 = Search, 1 = Library, 2 = Audible
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<RemoteCatalogItem>>(emptyList()) }
    var childCatalog by remember { mutableStateOf<RemoteResponse.ChildCatalog?>(null) }
    var loadingCatalog by remember { mutableStateOf(true) }
    var sheetTarget by remember { mutableStateOf<ParentBlockTarget?>(null) }

    val ownSession = remember(child.childId) { RemotePolicySession.activeChild?.childId != child.childId }

    BackHandler(onBack = onBack)

    LaunchedEffect(child.childId) {
        if (RemotePolicySession.activeChild?.childId != child.childId || !RemotePolicySession.isActive) {
            RemotePolicySession.attach(context, child)
        }
        loadingCatalog = true
        ParentRemoteClient.fetchChildCatalog(context, child)
            .onSuccess { childCatalog = it }
        loadingCatalog = false
    }

    DisposableEffect(child.childId, ownSession) {
        onDispose {
            if (ownSession && RemotePolicySession.activeChild?.childId == child.childId) {
                RemotePolicySession.detach(context)
            }
        }
    }

    LaunchedEffect(query, child.childId) {
        val q = query.trim()
        if (q.length < 2) {
            searchResults = emptyList()
            searching = false
            return@LaunchedEffect
        }
        delay(250L)
        searching = true
        ParentRemoteClient.searchChildCatalog(context, child, q)
            .onSuccess { searchResults = it }
            .onFailure { searchResults = emptyList() }
        searching = false
    }

    M3SettingsScaffold(
        title = stringResource(R.string.remote_catalog_title, child.displayName),
        onBack = onBack,
        listTestTag = "remote_catalog_list",
    ) { scaffoldPalette ->
        // Sync status pill + Tab switcher
        m3SettingsBlock(key = "catalog:header", palette = scaffoldPalette) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RemoteSyncBannerRow(childName = child.displayName, syncState = syncState, palette = scaffoldPalette) {
                    RemotePolicySession.retrySync(context)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        label = { Text(stringResource(R.string.remote_catalog_tab_search)) },
                        colors = m3FilterChipColors(scaffoldPalette),
                        modifier = Modifier.testTag("remote_catalog_tab_search"),
                    )
                    FilterChip(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        label = { Text(stringResource(R.string.remote_catalog_tab_library)) },
                        colors = m3FilterChipColors(scaffoldPalette),
                        modifier = Modifier.testTag("remote_catalog_tab_library"),
                    )
                    val hasAudible = childCatalog?.audibleConnected == true || childCatalog?.audibleBooks?.isNotEmpty() == true
                    if (hasAudible) {
                        FilterChip(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            label = { Text(stringResource(R.string.remote_catalog_tab_audible)) },
                            colors = m3FilterChipColors(scaffoldPalette),
                            modifier = Modifier.testTag("remote_catalog_tab_audible"),
                        )
                    }
                }
                if (selectedTab == 0) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = scaffoldPalette.muted) },
                        label = { Text(stringResource(R.string.remote_catalog_search_hint)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("remote_catalog_search_input"),
                    )
                }
            }
        }

        val itemsToShow: List<RemoteCatalogItem> = when (selectedTab) {
            0 -> searchResults
            1 -> (childCatalog?.libraryItems.orEmpty() + childCatalog?.homeItems.orEmpty()).distinctBy { "${it.kind}:${it.id}" }
            else -> childCatalog?.audibleBooks.orEmpty()
        }

        val isLoading = (selectedTab == 0 && searching) || (selectedTab != 0 && loadingCatalog)

        if (isLoading) {
            m3SettingsBlock(key = "catalog:loading", palette = scaffoldPalette) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = scaffoldPalette.accent, modifier = Modifier.size(28.dp))
                }
            }
        } else if (itemsToShow.isEmpty()) {
            m3SettingsBlock(key = "catalog:empty", palette = scaffoldPalette) {
                M3GroupCard(palette = scaffoldPalette) {
                    Text(
                        text = if (selectedTab == 0 && query.trim().length < 2) {
                            stringResource(R.string.remote_catalog_search_prompt, child.displayName)
                        } else {
                            stringResource(R.string.remote_catalog_empty)
                        },
                        color = scaffoldPalette.muted,
                        fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                    )
                }
            }
        } else {
            itemsToShow.forEach { item ->
                m3SettingsBlock(key = "catalog:item:${item.kind}:${item.id}", palette = scaffoldPalette) {
                    RemoteCatalogItemRow(
                        item = item,
                        policy = policy,
                        palette = scaffoldPalette,
                        onOpenSheet = { target -> sheetTarget = target },
                    )
                }
            }
        }
    }

    sheetTarget?.let { target ->
        ParentQuickBlockSheet(
            target = target,
            onDismiss = { sheetTarget = null },
        )
    }
}

@Composable
internal fun RemoteSyncBannerRow(
    childName: String,
    syncState: RemoteSyncState,
    palette: M3SettingsPalette,
    onRetry: () -> Unit,
) {
    val (text, color) = when (syncState) {
        RemoteSyncState.Synced -> stringResource(R.string.remote_sync_synced, childName) to ParentPickGreen
        RemoteSyncState.Syncing, RemoteSyncState.Loading -> stringResource(R.string.remote_sync_syncing, childName) to palette.accent
        is RemoteSyncState.Error -> stringResource(R.string.remote_sync_error, syncState.message) to ParentBlockRed
        RemoteSyncState.Idle -> return
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.14f))
            .clickable(enabled = syncState is RemoteSyncState.Error, onClick = onRetry)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        if (syncState == RemoteSyncState.Syncing || syncState == RemoteSyncState.Loading) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = color)
        } else {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        }
        Text(text = text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (syncState is RemoteSyncState.Error) {
            Text(stringResource(R.string.remote_policy_retry), color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun RemoteCatalogItemRow(
    item: RemoteCatalogItem,
    policy: ParentalPolicy,
    palette: M3SettingsPalette,
    onOpenSheet: (ParentBlockTarget) -> Unit,
) {
    val context = LocalContext.current
    val ruleKind = when (item.kind.lowercase()) {
        "track" -> ParentRuleKind.TRACK
        "album" -> ParentRuleKind.ALBUM
        "artist" -> ParentRuleKind.ARTIST
        "playlist" -> ParentRuleKind.PLAYLIST
        "show" -> ParentRuleKind.SHOW
        else -> null
    }
    val target = ruleKind?.let {
        ParentBlockTarget(kind = it, id = item.id, title = item.title, subtitle = item.subtitle, coverUri = item.coverUrl)
    }

    M3GroupCard(palette = palette) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = target != null) { target?.let(onOpenSheet) }
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (item.coverUrl.isNotBlank()) {
                    GlideImage(
                        model = item.coverUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                    )
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(palette.divider),
                    ) {
                        Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = palette.muted)
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        color = palette.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val meta = listOf(item.kind.replaceFirstChar { it.uppercase() }, item.subtitle)
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (item.explicit) {
                            Text(
                                text = "E",
                                color = Color.Black,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(palette.muted)
                                    .padding(horizontal = 4.dp, vertical = 1.dp),
                            )
                        }
                        Text(
                            text = meta,
                            color = palette.muted,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // Pick / Block pills (Spotify) OR Approve / Pin Home pills (Audible)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (item.kind == "audible") {
                    val approved = item.id in policy.spokenWord.audible.allowedAsins
                    val pinned = item.id in policy.spokenWord.audible.pinnedAsins
                    ActionPill(
                        label = stringResource(if (approved) R.string.remote_catalog_audible_approved else R.string.remote_catalog_audible_approve),
                        icon = if (approved) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                        active = approved,
                        activeColor = ParentPickGreen,
                        palette = palette,
                        testTag = "remote_pick_audible_${item.id}",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            ParentalPolicyStore.setAudibleBookAllowed(context, item.id, !approved, item.title, item.subtitle)
                        },
                    )
                    if (approved) {
                        ActionPill(
                            label = stringResource(if (pinned) R.string.remote_catalog_audible_pinned else R.string.remote_catalog_audible_pin),
                            icon = Icons.Rounded.PushPin,
                            active = pinned,
                            activeColor = palette.accent,
                            palette = palette,
                            testTag = "remote_pin_audible_${item.id}",
                            modifier = Modifier.weight(1f),
                            onClick = { ParentalPolicyStore.setAudibleBookPinned(context, item.id, !pinned) },
                        )
                    }
                } else if (target != null) {
                    val picked = ParentBlockActions.isPicked(target, policy)
                    val blocked = ParentBlockActions.isBlocked(target, policy)
                    ActionPill(
                        label = stringResource(if (picked) R.string.remote_catalog_picked else R.string.remote_catalog_pick),
                        icon = if (picked) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                        active = picked,
                        activeColor = ParentPickGreen,
                        palette = palette,
                        testTag = "remote_pick_${item.kind}_${item.id}",
                        modifier = Modifier.weight(1f),
                        onClick = { ParentBlockActions.togglePick(context, target, showToast = false) },
                    )
                    ActionPill(
                        label = stringResource(if (blocked) R.string.remote_catalog_blocked else R.string.remote_catalog_block),
                        icon = Icons.Rounded.Block,
                        active = blocked,
                        activeColor = ParentBlockRed,
                        palette = palette,
                        testTag = "remote_block_${item.kind}_${item.id}",
                        modifier = Modifier.weight(1f),
                        onClick = { ParentBlockActions.toggleBlock(context, target, showToast = false) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    activeColor: Color,
    palette: M3SettingsPalette,
    testTag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val bg = if (active) activeColor.copy(alpha = 0.22f) else Color.Transparent
    val border = if (active) activeColor.copy(alpha = 0.7f) else palette.divider
    val fg = if (active) activeColor else palette.text
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag(testTag),
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = fg, fontSize = 13.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
    }
}
