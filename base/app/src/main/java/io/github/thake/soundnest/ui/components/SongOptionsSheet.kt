package io.github.thake.soundnest.ui.components

import androidx.compose.material.icons.outlined.ContentCopy
import io.github.thake.soundnest.ui.theme.tone
import io.github.thake.soundnest.ui.theme.AppText
import io.github.thake.soundnest.ui.theme.AppMuted

import io.github.thake.soundnest.R

import androidx.compose.ui.res.stringResource

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.ThumbDownOffAlt
import kotlinx.coroutines.launch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import io.github.thake.soundnest.data.entity.SongsModel
import io.github.thake.soundnest.di.SongPlayer
import io.github.thake.soundnest.ui.navigation.albumRoute

private val MenuSheetColor: androidx.compose.ui.graphics.Color get() = tone(0xFF282828)
private val MenuIconColor: androidx.compose.ui.graphics.Color get() = AppMuted
private val MenuGreen: Color get() = io.github.thake.soundnest.ui.theme.AppAccent

/**
 * The track menu, one for the whole app (long press on a track, ⋮ in the player),
 * with the official app's wording and order: like, add to a playlist, play next,
 * add to queue, album, artist, share; plus queue and sleep timer for the track
 * that is playing.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalGlideComposeApi::class)
@Composable
fun SongOptionsSheet(
    song: SongsModel,
    navController: NavController,
    context: Context,
    onDismiss: () -> Unit,
    nowPlaying: Boolean = false,
    onOpenQueue: (() -> Unit)? = null,
    // Removing a track only makes sense where it is listed in a playlist: the screen that
    // owns that playlist passes this, and handles the write plus its undo (it has the list).
    onRemoveFromPlaylist: (() -> Unit)? = null,
    // The playlist the track is shown in, so the "Add to another playlist" picker can hide it.
    currentPlaylistId: String? = null,
) {
    val playerViewModel: io.github.thake.soundnest.ui.viewmodel.PlayerViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val liked = io.github.thake.soundnest.di.rememberTrackSaved(song.spotifyTrackId)
    var showSavedIn by remember { mutableStateOf(false) }
    var showAddTo by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showCredits by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var showNerd by remember { mutableStateOf(false) }
    var showQuality by remember { mutableStateOf(false) }
    if (showNerd) {
        StatsForNerdsSheet(onDismiss = { showNerd = false; onDismiss() })
        return
    }
    if (showQuality) {
        QualitySheet(onDismiss = { showQuality = false; onDismiss() })
        return
    }
    if (showCredits) {
        CreditsSheet(song = song, onDismiss = { showCredits = false; onDismiss() })
        return
    }
    if (showSpeed) {
        SpeedSheet(onDismiss = { showSpeed = false; onDismiss() })
        return
    }
    if (showSavedIn) {
        SavedInSheet(song = song, context = context, onDismiss = { showSavedIn = false; onDismiss() })
        return
    }
    if (showAddTo) {
        AddToPlaylistSheet(song = song, context = context, onDismiss = { showAddTo = false; onDismiss() }, excludePlaylistId = currentPlaylistId)
        return
    }
    if (showSleep) {
        SleepTimerSheet(onDismiss = { showSleep = false; onDismiss() })
        return
    }
    val hasId = song.spotifyTrackId.isNotBlank()
    // A Live Radio station playing right now: no queue, album or artist page of its own, but
    // (unlike a track with no Spotify id) a name and homepage worth sharing.
    val isRadio = nowPlaying && io.github.thake.soundnest.radio.RadioPlayer.current() != null
    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MenuSheetColor,
    ) {
        Column(modifier = Modifier.navigationBarsPadding().verticalScroll(rememberScrollState())) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(16.dp, 0.dp, 16.dp, 16.dp),
            ) {
                GlideImage(
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)),
                    model = song.coverUri,
                    contentScale = ContentScale.Crop,
                    contentDescription = "",
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(song.title, color = AppText, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.singer, color = MenuIconColor, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            HorizontalDivider(color = tone(0xFF3E3E3E), thickness = 0.5.dp)
            Spacer(Modifier.height(8.dp))

            val canModifyLib = io.github.thake.soundnest.parental.FeatureGates.canModifySpotifyLibrary(context)
            val canRadio = io.github.thake.soundnest.parental.FeatureGates.canUseAutoplayAndRadio(context)

            if (canModifyLib) {
                MenuRow(
                    icon = if (liked) null else Icons.Outlined.AddCircleOutline,
                    label = stringResource(if (liked) R.string.collection_liked_songs_title else R.string.aligned_curation_context_menu_item_add_to_liked_songs),
                    enabled = hasId,
                    leading = if (liked) ({ LikedBadge() }) else null,
                ) {
                    SongPlayer.setTrackSaved(song.spotifyTrackId, !liked)
                    toast(context.getString(if (liked) R.string.snackbar_removed_from_liked_songs else R.string.snackbar_added_to_liked_songs))
                    onDismiss()
                }
                MenuRow(Icons.AutoMirrored.Filled.PlaylistAdd, stringResource(R.string.context_menu_add_to_playlist), enabled = hasId) { showSavedIn = true }
                if (currentPlaylistId != null) {
                    // Copy into another playlist when viewing inside an existing playlist.
                    MenuRow(Icons.AutoMirrored.Filled.PlaylistAdd, stringResource(R.string.context_menu_add_to_another_playlist), enabled = hasId) { showAddTo = true }
                }
                if (onRemoveFromPlaylist != null) {
                    MenuRow(Icons.Outlined.Delete, stringResource(R.string.context_menu_remove_from_playlist), enabled = hasId) {
                        onDismiss()
                        onRemoveFromPlaylist()
                    }
                }
            }
            if (!nowPlaying) {
                MenuRow(Icons.AutoMirrored.Filled.PlaylistPlay, stringResource(R.string.context_menu_play_next), enabled = SongQueue.canQueue(song)) {
                    SongQueue.add(context, song, next = true)
                    onDismiss()
                }
                MenuRow(Icons.AutoMirrored.Filled.QueueMusic, stringResource(R.string.context_menu_add_to_queue), enabled = SongQueue.canQueue(song)) {
                    SongQueue.add(context, song)
                    onDismiss()
                }
            } else if (onOpenQueue != null) {
                MenuRow(Icons.AutoMirrored.Filled.QueueMusic, stringResource(R.string.context_menu_go_to_queue), enabled = !isRadio) {
                    onDismiss()
                    onOpenQueue()
                }
            }
            if (canRadio) {
                MenuRow(Icons.Outlined.Radio, stringResource(R.string.context_menu_go_to_song_radio), enabled = hasId) {
                    onDismiss()
                    playerViewModel.goToSongRadio(song.spotifyTrackId, song.title,
                        navigate = { route -> navController.navigate(route) },
                        onFailed = { toast(context.getString(R.string.song_radio_failed)) })
                }
            }
            MenuRow(Icons.Default.Album, stringResource(R.string.context_menu_browse_album), enabled = !isRadio && (song.album.isNotBlank() || song.albumId.isNotBlank())) {
                onDismiss()
                navController.navigate(albumRoute(song.album, song.singer, song.albumId))
            }
            MenuRow(Icons.Outlined.Person, stringResource(R.string.context_menu_browse_artist), enabled = !isRadio && song.singer.isNotBlank()) {
                onDismiss()
                playerViewModel.goToArtist(song.spotifyTrackId, song.singer) { route -> navController.navigate(route) }
            }
            MenuRow(Icons.Outlined.Info, stringResource(R.string.track_credits), enabled = hasId) { showCredits = true }
            // "Not interested": kept on this phone only; Parent PIN required to change hidden songs.
            if (hasId && !song.url.startsWith("episode:")) {
                val hidden = io.github.thake.soundnest.data.preferences.HiddenSongs.isHidden(song.spotifyTrackId)
                MenuRow(Icons.Outlined.ThumbDownOffAlt, stringResource(if (hidden) R.string.show_song_again else R.string.hide_song)) {
                    onDismiss()
                    io.github.thake.soundnest.parental.ParentSecurityManager.runProtected(
                        context,
                        io.github.thake.soundnest.parental.AppSurface.HIDDEN_SONGS_AND_BANS,
                    ) {
                        if (hidden) io.github.thake.soundnest.data.preferences.HiddenSongs.show(context, song.spotifyTrackId) else SongPlayer.hideSong(song)
                    }
                }
            }
            // Taste profile: shown only when the account supports taste exclusion and has loaded.
            val trackUri = "spotify:track:${song.spotifyTrackId}"
            val excluded by androidx.compose.runtime.produceState<Boolean?>(null, trackUri) {
                value = if (!hasId || !canModifyLib) null else runCatching { io.github.thake.soundnest.host.android.SoundNestRuntime.api.extras.isExcludedFromTaste(listOf(trackUri)).single() }.getOrNull()
            }
            if (canModifyLib && excluded != null) {
                MenuRow(Icons.Outlined.Block, stringResource(if (excluded == true) R.string.include_taste else R.string.exclude_taste)) {
                    val exclude = excluded != true
                    io.github.thake.soundnest.host.android.SoundNestRuntime.scope.launch {
                        runCatching { io.github.thake.soundnest.host.android.SoundNestRuntime.api.extras.setExcludedFromTaste(trackUri, exclude) }
                    }
                    io.github.thake.soundnest.di.Haptics.confirm()
                    onDismiss()
                }
            }
            // "Don't play this artist": Parent PIN required so kids cannot unban artists.
            val artistBan by androidx.compose.runtime.produceState<Pair<String, Boolean>?>(null, trackUri) {
                if (!hasId) return@produceState
                val api = io.github.thake.soundnest.host.android.SoundNestRuntime.api
                value = runCatching {
                    val artist = api.catalog.track(trackUri).artists.first().uri
                    artist to api.extras.isArtistBanned(listOf(artist)).single()
                }.getOrNull()
            }
            if (artistBan != null) {
                MenuRow(Icons.Outlined.Block, stringResource(if (artistBan?.second == true) R.string.artist_unban else R.string.artist_ban)) {
                    val (artist, banned) = artistBan ?: return@MenuRow
                    onDismiss()
                    io.github.thake.soundnest.parental.ParentSecurityManager.runProtected(
                        context,
                        io.github.thake.soundnest.parental.AppSurface.HIDDEN_SONGS_AND_BANS,
                    ) {
                        io.github.thake.soundnest.host.android.SoundNestRuntime.scope.launch {
                            runCatching { io.github.thake.soundnest.host.android.SoundNestRuntime.api.extras.setArtistBanned(artist, !banned) }
                        }
                        toast(context.getString(if (banned) R.string.artist_unbanned else R.string.artist_banned, song.singer.substringBefore(",").trim()))
                        io.github.thake.soundnest.di.Haptics.confirm()
                    }
                }
            }
            // An episode: in or out of Your Episodes.
            if (canModifyLib && song.url.startsWith("episode:")) {
                val episodeUri = "spotify:episode:" + song.url.removePrefix("episode:")
                val saved by androidx.compose.runtime.produceState<Boolean?>(null, episodeUri) {
                    value = runCatching { io.github.thake.soundnest.host.android.SoundNestRuntime.api.library.contains(listOf(episodeUri)).single() }.getOrNull()
                }
                if (saved != null) {
                    MenuRow(Icons.Outlined.Bookmark, stringResource(if (saved == true) R.string.episode_unsave else R.string.episode_save)) {
                        val save = saved != true
                        io.github.thake.soundnest.host.android.SoundNestRuntime.scope.launch {
                            runCatching { io.github.thake.soundnest.host.android.SoundNestRuntime.api.podcasts.setEpisodeSaved(episodeUri, save) }
                        }
                        io.github.thake.soundnest.di.Haptics.confirm()
                        onDismiss()
                    }
                }
            }
            if (nowPlaying && song.url.startsWith("episode:")) {
                MenuRow(Icons.Outlined.Speed, stringResource(R.string.playback_speed)) { showSpeed = true }
            }
            // The app's own playback (extensions, downloads, files): its equalizer, speed and silences.
            if (nowPlaying && io.github.thake.soundnest.di.LocalPlayer.active.value) {
                MenuRow(Icons.Outlined.GraphicEq, stringResource(R.string.equalizer_title)) {
                    onDismiss()
                    navController.navigate(io.github.thake.soundnest.ui.navigation.Routes.Equalizer.route)
                }
            }
            MenuRow(Icons.Outlined.Share, stringResource(R.string.context_menu_share), enabled = hasId || isRadio) {
                // A station has no Spotify uri: shared as plain text, never through a Spotify Code.
                if (isRadio) io.github.thake.soundnest.radio.RadioPlayer.share(context)
                else SpotifyCode.show(ShareTarget("spotify:track:${song.spotifyTrackId}", song.title, song.singer, song.coverUri))
                onDismiss()
            }
            if (hasId && !isRadio) MenuRow(Icons.Outlined.ContentCopy, stringResource(R.string.copy_link)) {
                SpotifyCode.copyLink(context, "spotify:track:${song.spotifyTrackId}"); onDismiss()
            }
            if (nowPlaying) {
                val active = SongPlayer.sleepEndsAt.value != null
                MenuRow(
                    Icons.Default.Bedtime,
                    stringResource(R.string.context_menu_sleep_timer),
                    tint = if (active) MenuGreen else MenuIconColor,
                ) { showSleep = true }
                // The source line of the player is turned off: its sheet is here instead.
                if (!io.github.thake.soundnest.data.preferences.PlayerSourceLinePref.shown) {
                    MenuRow(Icons.Outlined.GraphicEq, stringResource(R.string.quality_sheet_title)) { showQuality = true }
                }
                MenuRow(Icons.Outlined.Analytics, stringResource(R.string.nerd_title)) { showNerd = true }
            }
            val adminUnlocked = io.github.thake.soundnest.parental.rememberAdminUnlocked()
            val policy = io.github.thake.soundnest.parental.rememberParentalPolicy()
            val parentTargets = remember(song) { io.github.thake.soundnest.parental.ParentBlockActions.targetsForSong(song) }
            if (!isRadio && adminUnlocked && parentTargets.isNotEmpty()) {
                HorizontalDivider(color = tone(0xFF3E3E3E), modifier = Modifier.padding(vertical = 4.dp))
                parentTargets.forEach { target ->
                    val blocked = io.github.thake.soundnest.parental.ParentBlockActions.isBlocked(target, policy)
                    val includeTitle = target.kind != io.github.thake.soundnest.parental.ParentRuleKind.TRACK &&
                        target.kind != io.github.thake.soundnest.parental.ParentRuleKind.EPISODE
                    MenuRow(
                        icon = if (blocked) Icons.Default.Check else Icons.Outlined.Block,
                        label = io.github.thake.soundnest.parental.ParentBlockActions.actionLabel(target, blocked, includeTitle = includeTitle),
                        tint = io.github.thake.soundnest.parental.ParentBlockRed,
                    ) {
                        io.github.thake.soundnest.parental.ParentBlockActions.toggleBlock(context, target)
                        onDismiss()
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun LikedBadge() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(24.dp).clip(CircleShape).background(MenuGreen),
    ) {
        Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun MenuRow(
    icon: ImageVector?,
    label: String,
    enabled: Boolean = true,
    tint: Color = MenuIconColor,
    leading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.4f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp),
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            when {
                leading != null -> leading()
                icon != null -> Icon(icon, contentDescription = null, tint = tint.copy(alpha = alpha), modifier = Modifier.size(24.dp))
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(label, color = AppText.copy(alpha = alpha), fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}


/** The track's credits (performers, writers, producers), as Spotify lists them. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun CreditsSheet(song: SongsModel, onDismiss: () -> Unit) {
    val credits by androidx.compose.runtime.produceState<io.github.thake.soundnest.api.Credits?>(null, song.spotifyTrackId) {
        value = runCatching { io.github.thake.soundnest.host.android.SoundNestRuntime.api.extras.credits("spotify:track:${song.spotifyTrackId}") }
            .getOrDefault(io.github.thake.soundnest.api.Credits(emptyList()))
    }
    LookSheet(onDismissRequest = onDismiss, containerColor = tone(0xFF282828), sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.track_credits), color = AppText, fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                modifier = Modifier.padding(20.dp, 8.dp))
            Text(song.title, color = AppMuted, fontSize = 14.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 20.dp))
            val roles = credits?.roles
            if (roles == null) androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth().padding(20.dp))
            else roles.forEach { role ->
                Column(Modifier.padding(20.dp, 10.dp)) {
                    Text(role.artists.joinToString(", ") { it.name }, color = AppText, fontSize = 16.sp)
                    Text(role.role, color = AppMuted, fontSize = 13.sp)
                }
            }
        }
    }
}

/** Podcast playback speed (Spotify refuses speed changes on music). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun SpeedSheet(onDismiss: () -> Unit) {
    LookSheet(onDismissRequest = onDismiss, containerColor = tone(0xFF282828)) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(stringResource(R.string.playback_speed), color = AppText, fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                modifier = Modifier.padding(20.dp, 8.dp))
            listOf(0.5, 0.8, 1.0, 1.2, 1.5, 1.8, 2.0, 3.0).forEach { speed ->
                Text("${speed}×", color = AppText, fontSize = 16.sp,
                    modifier = Modifier.fillMaxWidth().clickable { SongPlayer.setSpeed(speed); onDismiss() }.padding(20.dp, 12.dp))
            }
        }
    }
}
