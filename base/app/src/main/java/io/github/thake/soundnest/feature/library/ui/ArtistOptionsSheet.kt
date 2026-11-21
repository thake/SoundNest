package io.github.thake.soundnest.feature.library.ui


import io.github.thake.soundnest.core.ui.look.LookSheet
import androidx.compose.material.icons.filled.ContentCopy
import io.github.thake.soundnest.core.ui.classic.tone
import io.github.thake.soundnest.core.ui.classic.AppText
import io.github.thake.soundnest.core.ui.classic.AppMuted

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.thake.soundnest.R
import kotlinx.coroutines.launch
import io.github.thake.soundnest.feature.player.ui.ShareTarget
import io.github.thake.soundnest.feature.player.ui.SpotifyCode

/**
 * B27: the ⋮ of an artist page's action row had no `clickable` and did nothing. It now opens
 * the artist's own menu, with the actions the app can really do for an artist: share its link,
 * and keep it out of (or back in) radios and mixes — the same taste-profile call the track
 * menu's "Don't play this artist" uses.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistOptionsSheet(artistId: String, artistName: String, artistImage: String = "", onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val uri = "spotify:artist:$artistId"
    // null = not known yet (or the call failed): the ban row stays disabled rather than lying.
    val banned by produceState<Boolean?>(null, uri) {
        if (artistId.isBlank()) return@produceState
        value = runCatching {
            io.github.thake.soundnest.host.android.SoundNestRuntime.api.extras.isArtistBanned(listOf(uri)).single()
        }.getOrNull()
    }
    LookSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = tone(0xFF282828)) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState())) {
            Text(
                artistName, color = AppText, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            )
            HorizontalDivider(color = tone(0xFF3E3E3E), thickness = 0.5.dp)
            Spacer(Modifier.height(8.dp))
            ArtistMenuRow(Icons.Default.Share, stringResource(R.string.share_link), enabled = artistId.isNotBlank()) {
                SpotifyCode.show(ShareTarget("spotify:artist:$artistId", artistName, coverUri = artistImage))
                onDismiss()
            }
            ArtistMenuRow(Icons.Default.ContentCopy, stringResource(R.string.copy_link), enabled = artistId.isNotBlank()) {
                SpotifyCode.copyLink(context, "spotify:artist:$artistId"); onDismiss()
            }
            ArtistMenuRow(
                Icons.Default.Block,
                stringResource(if (banned == true) R.string.artist_unban else R.string.artist_ban),
                enabled = banned != null,
            ) {
                // The button shows the action it will do, so the flip is derived from the state.
                val keepOut = banned != true
                onDismiss()
                io.github.thake.soundnest.feature.parental.security.ParentSecurityManager.runProtected(
                    context,
                    io.github.thake.soundnest.feature.parental.security.AppSurface.ARTIST_BANS,
                ) {
                    io.github.thake.soundnest.host.android.SoundNestRuntime.scope.launch {
                        runCatching { io.github.thake.soundnest.host.android.SoundNestRuntime.api.extras.setArtistBanned(uri, keepOut) }
                    }
                    Toast.makeText(
                        context,
                        context.getString(if (keepOut) R.string.artist_banned else R.string.artist_unbanned, artistName),
                        Toast.LENGTH_SHORT,
                    ).show()
                    io.github.thake.soundnest.core.util.Haptics.confirm()
                }
            }
            val adminUnlocked = io.github.thake.soundnest.feature.parental.ui.rememberAdminUnlocked()
            val policy = io.github.thake.soundnest.feature.parental.settings.rememberParentalPolicy()
            if (artistId.isNotBlank() && adminUnlocked) {
                val target = io.github.thake.soundnest.feature.parental.ui.ParentBlockTarget(
                    kind = io.github.thake.soundnest.feature.parental.ui.ParentRuleKind.ARTIST,
                    id = artistId,
                    title = artistName,
                    subtitle = stringResource(R.string.entity_artist),
                    coverUri = artistImage,
                )
                val picked = io.github.thake.soundnest.feature.parental.ui.ParentBlockActions.isPicked(target, policy)
                val blocked = io.github.thake.soundnest.feature.parental.ui.ParentBlockActions.isBlocked(target, policy)
                HorizontalDivider(color = tone(0xFF3E3E3E), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))
                ArtistMenuRow(
                    if (picked) androidx.compose.material.icons.Icons.Rounded.Star else androidx.compose.material.icons.Icons.Outlined.StarOutline,
                    io.github.thake.soundnest.feature.parental.ui.ParentBlockActions.pickActionLabel(context, target, picked),
                ) {
                    io.github.thake.soundnest.feature.parental.ui.ParentBlockActions.togglePick(context, target)
                    onDismiss()
                }
                ArtistMenuRow(
                    if (blocked) Icons.Default.Check else Icons.Default.Block,
                    io.github.thake.soundnest.feature.parental.ui.ParentBlockActions.actionLabel(context, target, blocked),
                ) {
                    io.github.thake.soundnest.feature.parental.ui.ParentBlockActions.toggleBlock(context, target)
                    onDismiss()
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** One row of the artist menu, laid out like the track menu's rows. */
@Composable
private fun ArtistMenuRow(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val tint = if (enabled) AppText else Color(0xFF6A6A6A)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) AppMuted else Color(0xFF6A6A6A), modifier = Modifier.size(22.dp))
        Text(label, color = tint, fontSize = 15.sp, modifier = Modifier.padding(start = 16.dp))
    }
}
