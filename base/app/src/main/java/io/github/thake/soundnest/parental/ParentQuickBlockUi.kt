/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.parental

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import io.github.thake.soundnest.R
import io.github.thake.soundnest.data.api.Api
import io.github.thake.soundnest.data.entity.HomeItem
import io.github.thake.soundnest.data.entity.LibraryEntry
import io.github.thake.soundnest.data.entity.SongsModel
import io.github.thake.soundnest.di.SongPlayer
import io.github.thake.soundnest.ui.components.LookSheet
import io.github.thake.soundnest.ui.immersive.sheets.ImmersiveSheet
import io.github.thake.soundnest.ui.immersive.sheets.ImmersiveSheetDivider
import io.github.thake.soundnest.ui.immersive.sheets.ImmersiveSheetHeader
import io.github.thake.soundnest.ui.immersive.sheets.ImmersiveSheetRow
import io.github.thake.soundnest.ui.navigation.isImmersive
import io.github.thake.soundnest.ui.theme.AppMuted
import io.github.thake.soundnest.ui.theme.AppText
import io.github.thake.soundnest.ui.theme.tone

enum class ParentRuleKind {
    TRACK,
    EPISODE,
    ARTIST,
    PLAYLIST,
    ALBUM,
    SHOW,
}

data class ParentBlockTarget(
    val kind: ParentRuleKind,
    val id: String,
    val title: String,
    val subtitle: String = "",
    val coverUri: String = "",
) {
    val normalizedId: String
        get() = ParentalPolicyStore.normalizeSpotifyId(id.removePrefix(Api.SHOW_PREFIX).removePrefix("episode:"))
}

val ParentBlockRed = Color(0xFFFF6B6B)
private val ParentBlockSoftText = Color(0xFFFF8A8A)
private val ParentBlockFill = Color(0x24FF6B6B)
private val ParentBlockBorder = Color(0x66FF6B6B)

/**
 * Reactive Composable helper that updates immediately whenever Parent Mode unlocks, extends, or locks.
 */
@Composable
fun rememberAdminUnlocked(): Boolean {
    val context = LocalContext.current
    val tick by ParentSecurityManager.sessionTick.collectAsState()
    return remember(tick, context) { ParentSecurityManager.isAdminUnlocked(context) }
}

object ParentBlockActions {

    fun kindNoun(kind: ParentRuleKind): String = when (kind) {
        ParentRuleKind.TRACK -> "Song"
        ParentRuleKind.EPISODE -> "Episode"
        ParentRuleKind.ARTIST -> "Artist"
        ParentRuleKind.PLAYLIST -> "Playlist"
        ParentRuleKind.ALBUM -> "Album"
        ParentRuleKind.SHOW -> "Podcast Show"
    }

    fun isBlocked(target: ParentBlockTarget, policy: ParentalPolicy): Boolean {
        val norm = target.normalizedId
        if (norm.isEmpty()) return false
        return when (target.kind) {
            ParentRuleKind.TRACK, ParentRuleKind.EPISODE -> norm in policy.blockedTrackIds
            ParentRuleKind.ARTIST -> norm in policy.blockedArtistIds
            ParentRuleKind.PLAYLIST -> norm in policy.blockedPlaylistIds
            ParentRuleKind.ALBUM -> norm in policy.blockedAlbumIds
            ParentRuleKind.SHOW -> norm in policy.blockedShowIds
        }
    }

    fun actionLabel(target: ParentBlockTarget, blocked: Boolean, includeTitle: Boolean = false): String {
        val noun = kindNoun(target.kind)
        val suffix = if (includeTitle && target.title.isNotBlank()) " (${target.title})" else ""
        return if (blocked) "Unblock $noun$suffix" else "Block $noun$suffix"
    }

    fun toggleBlock(context: Context, target: ParentBlockTarget, showToast: Boolean = true): Boolean {
        val policy = ParentalPolicyStore.current(context)
        val currentlyBlocked = isBlocked(target, policy)
        if (currentlyBlocked) {
            unblock(context, target, showToast)
            return false
        } else {
            block(context, target, showToast)
            return true
        }
    }

    fun block(context: Context, target: ParentBlockTarget, showToast: Boolean = true) {
        val norm = target.normalizedId
        if (norm.isEmpty()) return
        val title = target.title.takeIf { it.isNotBlank() }
        val subtitle = target.subtitle.takeIf { it.isNotBlank() }
        when (target.kind) {
            ParentRuleKind.TRACK, ParentRuleKind.EPISODE ->
                ParentalPolicyStore.blockTrack(context, norm, title = title, subtitle = subtitle)
            ParentRuleKind.ARTIST ->
                ParentalPolicyStore.blockArtist(context, norm, title = title, subtitle = subtitle)
            ParentRuleKind.PLAYLIST ->
                ParentalPolicyStore.blockPlaylist(context, norm, title = title, subtitle = subtitle)
            ParentRuleKind.ALBUM ->
                ParentalPolicyStore.blockAlbum(context, norm, title = title, subtitle = subtitle)
            ParentRuleKind.SHOW ->
                ParentalPolicyStore.blockShow(context, norm, title = title, subtitle = subtitle)
        }
        runCatching { SongPlayer.enforceParentalPolicyNow() }
        if (showToast) {
            val label = target.title.ifBlank { kindNoun(target.kind) }
            Toast.makeText(context, "Blocked ${kindNoun(target.kind).lowercase()}: $label", Toast.LENGTH_SHORT).show()
        }
    }

    fun unblock(context: Context, target: ParentBlockTarget, showToast: Boolean = true) {
        val norm = target.normalizedId
        if (norm.isEmpty()) return
        when (target.kind) {
            ParentRuleKind.TRACK, ParentRuleKind.EPISODE -> ParentalPolicyStore.clearTrackRule(context, norm)
            ParentRuleKind.ARTIST -> ParentalPolicyStore.clearArtistRule(context, norm)
            ParentRuleKind.PLAYLIST -> ParentalPolicyStore.clearPlaylistRule(context, norm)
            ParentRuleKind.ALBUM -> ParentalPolicyStore.clearAlbumRule(context, norm)
            ParentRuleKind.SHOW -> ParentalPolicyStore.clearShowRule(context, norm)
        }
        runCatching { SongPlayer.enforceParentalPolicyNow() }
        if (showToast) {
            val label = target.title.ifBlank { kindNoun(target.kind) }
            Toast.makeText(context, "Unblocked ${kindNoun(target.kind).lowercase()}: $label", Toast.LENGTH_SHORT).show()
        }
    }

    /** Extracts one [ParentBlockTarget] per artist ID on [song], pairing names from `song.singer` when possible. */
    fun artistTargetsForSong(song: SongsModel): List<ParentBlockTarget> {
        val rawIds = song.artistIds.map(ParentalPolicyStore::normalizeSpotifyId).filter { it.isNotEmpty() }.distinct()
        if (rawIds.isEmpty()) return emptyList()
        val splitNames = song.singer.split(", ").map { it.trim() }.filter { it.isNotEmpty() }
        return rawIds.mapIndexed { idx, id ->
            val name = when {
                rawIds.size == 1 && song.singer.isNotBlank() -> song.singer.trim()
                splitNames.size == rawIds.size -> splitNames[idx]
                idx < splitNames.size -> splitNames[idx]
                else -> ""
            }
            ParentBlockTarget(
                kind = ParentRuleKind.ARTIST,
                id = id,
                title = name,
                subtitle = "Artist",
                coverUri = song.coverUri,
            )
        }
    }

    fun targetForHomeItem(item: HomeItem): ParentBlockTarget? = when (item) {
        is HomeItem.Playlist -> {
            val norm = ParentalPolicyStore.normalizeSpotifyId(item.id)
            if (norm.isEmpty() || norm == Api.HomeCache.LIKED_SONGS_ID || norm == Api.HomeCache.LOCAL_FILES_ID || norm == Api.HomeCache.EPISODES_ID) null
            else ParentBlockTarget(ParentRuleKind.PLAYLIST, norm, item.name, item.subtitle, item.imageUrl)
        }
        is HomeItem.Album -> {
            val norm = ParentalPolicyStore.normalizeSpotifyId(item.id)
            if (norm.isEmpty()) null
            else ParentBlockTarget(ParentRuleKind.ALBUM, norm, item.name, item.artists.ifBlank { item.subtitle }, item.imageUrl)
        }
        is HomeItem.Artist -> {
            val norm = ParentalPolicyStore.normalizeSpotifyId(item.id)
            if (norm.isEmpty()) null
            else ParentBlockTarget(ParentRuleKind.ARTIST, norm, item.name, "Artist", item.imageUrl)
        }
        is HomeItem.Show -> {
            val norm = ParentalPolicyStore.normalizeSpotifyId(item.id)
            if (norm.isEmpty()) null
            else ParentBlockTarget(
                kind = if (item.isEpisode) ParentRuleKind.EPISODE else ParentRuleKind.SHOW,
                id = norm,
                title = item.name,
                subtitle = item.subtitle,
                coverUri = item.imageUrl,
            )
        }
    }

    fun targetForLibraryEntry(entry: LibraryEntry): ParentBlockTarget? {
        if (entry.isFolder) return null
        val uri = entry.uri
        return when {
            uri.startsWith("spotify:playlist:") -> {
                val id = ParentalPolicyStore.normalizeSpotifyId(uri)
                if (id.isEmpty() || id == Api.HomeCache.LIKED_SONGS_ID) null
                else ParentBlockTarget(ParentRuleKind.PLAYLIST, id, entry.name, entry.subtitle, entry.coverUri)
            }
            uri.startsWith("spotify:album:") -> {
                val id = ParentalPolicyStore.normalizeSpotifyId(uri)
                if (id.isEmpty()) null
                else ParentBlockTarget(ParentRuleKind.ALBUM, id, entry.name, entry.subtitle, entry.coverUri)
            }
            uri.startsWith("spotify:artist:") -> {
                val id = ParentalPolicyStore.normalizeSpotifyId(uri)
                if (id.isEmpty()) null
                else ParentBlockTarget(ParentRuleKind.ARTIST, id, entry.name, "Artist", entry.coverUri)
            }
            uri.startsWith("spotify:show:") || entry.spotifyId.startsWith(Api.SHOW_PREFIX) -> {
                val id = ParentalPolicyStore.normalizeSpotifyId(entry.spotifyId.removePrefix(Api.SHOW_PREFIX))
                if (id.isEmpty()) null
                else ParentBlockTarget(ParentRuleKind.SHOW, id, entry.name, entry.subtitle, entry.coverUri)
            }
            else -> null
        }
    }
}

/**
 * Compact 1-tap inline pill shown on Playlist, Album, Artist, and Show detail headers while Parent Mode
 * is unlocked. Flips in-place to `Blocked · Tap to Unblock` so accidental taps on an open detail page
 * can be undone immediately without trapping the parent on an error screen.
 */
@Composable
fun ParentInlineBlockPill(
    target: ParentBlockTarget,
    modifier: Modifier = Modifier,
) {
    if (!rememberAdminUnlocked()) return
    if (target.normalizedId.isEmpty()) return

    val context = LocalContext.current
    val policy = rememberParentalPolicy()
    val blocked = ParentBlockActions.isBlocked(target, policy)
    val noun = ParentBlockActions.kindNoun(target.kind)

    val bg = if (blocked) ParentBlockRed else ParentBlockFill
    val border = if (blocked) ParentBlockRed else ParentBlockBorder
    val fg = if (blocked) Color.Black else ParentBlockSoftText
    val label = if (blocked) "Blocked · Tap to Unblock" else "Block $noun"

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(50))
            .clickable { ParentBlockActions.toggleBlock(context, target) }
            .padding(horizontal = 14.dp, vertical = 7.dp)
            .testTag("parent_inline_block_${target.kind.name.lowercase()}"),
    ) {
        Icon(
            imageVector = if (isImmersive()) Icons.Rounded.Block else Icons.Outlined.Block,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label,
            color = fg,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Lightweight, look-aware (`Classic` + `Immersive`) bottom sheet for blocking a Playlist, Album,
 * Artist, or Podcast Show directly from cards and search/library rows without needing Spotify URIs.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalGlideComposeApi::class)
@Composable
fun ParentQuickBlockSheet(
    target: ParentBlockTarget,
    extraTargets: List<ParentBlockTarget> = emptyList(),
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val policy = rememberParentalPolicy()
    val allTargets = remember(target, extraTargets) {
        (listOf(target) + extraTargets).filter { it.normalizedId.isNotEmpty() }
    }
    if (allTargets.isEmpty()) {
        onDismiss()
        return
    }
    val roundCover = target.kind == ParentRuleKind.ARTIST
    val headerSubtitle = target.subtitle.ifBlank { ParentBlockActions.kindNoun(target.kind) }

    if (isImmersive()) {
        ImmersiveSheet(
            onDismiss = onDismiss,
            imageUrl = target.coverUri.ifBlank { null },
        ) {
            ImmersiveSheetHeader(
                title = target.title,
                subtitle = headerSubtitle,
                coverUrl = target.coverUri.ifBlank { null },
                round = roundCover,
            )
            ImmersiveSheetDivider(Modifier.padding(vertical = 4.dp))
            allTargets.forEachIndexed { index, item ->
                val blocked = ParentBlockActions.isBlocked(item, policy)
                val includeName = index > 0 || allTargets.size > 1
                ImmersiveSheetRow(
                    icon = if (blocked) Icons.Rounded.CheckCircle else Icons.Rounded.Block,
                    label = ParentBlockActions.actionLabel(item, blocked, includeTitle = includeName),
                    tint = ParentBlockRed,
                    modifier = Modifier.testTag("parent_sheet_block_${item.kind.name.lowercase()}"),
                ) {
                    ParentBlockActions.toggleBlock(context, item)
                    onDismiss()
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    } else {
        LookSheet(
            onDismissRequest = onDismiss,
            containerColor = tone(0xFF282828),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    if (target.coverUri.isNotBlank()) {
                        GlideImage(
                            model = target.coverUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            loading = placeholder(R.drawable.placeholder),
                            failure = placeholder(R.drawable.placeholder),
                            modifier = Modifier
                                .size(48.dp)
                                .clip(if (roundCover) CircleShape else RoundedCornerShape(6.dp)),
                        )
                        Spacer(Modifier.width(14.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = target.title,
                            color = AppText,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (headerSubtitle.isNotBlank()) {
                            Text(
                                text = headerSubtitle,
                                color = AppMuted,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                HorizontalDivider(color = tone(0xFF3E3E3E), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))
                allTargets.forEachIndexed { index, item ->
                    val blocked = ParentBlockActions.isBlocked(item, policy)
                    val includeName = index > 0 || allTargets.size > 1
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                ParentBlockActions.toggleBlock(context, item)
                                onDismiss()
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                            .testTag("parent_sheet_block_${item.kind.name.lowercase()}"),
                    ) {
                        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (blocked) Icons.Outlined.CheckCircle else Icons.Outlined.Block,
                                contentDescription = null,
                                tint = ParentBlockRed,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = ParentBlockActions.actionLabel(item, blocked, includeTitle = includeName),
                            color = ParentBlockRed,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
