package io.github.thake.soundnest.ui.immersive.pages

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.github.thake.soundnest.R
import io.github.thake.soundnest.data.local.LocalMusic
import io.github.thake.soundnest.data.local.LocalTrack
import io.github.thake.soundnest.di.LocalPlayer
import io.github.thake.soundnest.di.SongPlayer
import io.github.thake.soundnest.ui.components.EpisodeOptionsSheet
import io.github.thake.soundnest.ui.components.episodeClock
import io.github.thake.soundnest.ui.components.episodeDateLabel
import io.github.thake.soundnest.ui.components.episodeMinutes
import io.github.thake.soundnest.ui.components.shareEpisode
import io.github.thake.soundnest.ui.components.spotifyUri
import io.github.thake.soundnest.ui.immersive.Immersive
import io.github.thake.soundnest.ui.immersive.components.ImmersiveButton
import io.github.thake.soundnest.ui.immersive.components.ImmersiveCircleButton
import io.github.thake.soundnest.ui.immersive.components.ImmersiveCover
import io.github.thake.soundnest.ui.immersive.components.ImmersiveListRow
import io.github.thake.soundnest.ui.immersive.components.ImmersiveMessageState
import io.github.thake.soundnest.ui.immersive.components.ImmersivePlayShuffleButtons
import io.github.thake.soundnest.ui.immersive.components.ImmersiveSearchField
import io.github.thake.soundnest.ui.immersive.components.ImmersiveSongRow
import io.github.thake.soundnest.ui.immersive.components.SectionHeader
import io.github.thake.soundnest.ui.immersive.components.immersiveClickable
import io.github.thake.soundnest.ui.immersive.components.immersiveSongListSkeleton
import io.github.thake.soundnest.ui.immersive.motion.immersiveItemPlacement
import io.github.thake.soundnest.ui.immersive.shell.ImmersiveBarAction
import io.github.thake.soundnest.ui.navigation.showRoute
import io.github.thake.soundnest.ui.screens.AddLocalToPlaylistSheet
import io.github.thake.soundnest.ui.screens.LocalFoldersSheet
import io.github.thake.soundnest.ui.viewmodel.EpisodeViewModel
import kotlinx.coroutines.launch

// ---------------------------------------------------------------- Local files

/** "Local Files" of the Immersive look: the device's own audio, played by [LocalPlayer]. */
@Composable
fun ImmersiveLocalFilesScreen(navController: NavController) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(LocalMusic.hasPermission(context)) }
    var askedOnce by remember { mutableStateOf(false) }
    var tracks by remember { mutableStateOf<List<LocalTrack>?>(null) }
    var find by remember { mutableStateOf("") }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> granted = ok; askedOnce = true }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = LocalMusic.hasPermission(context) }
    LaunchedEffect(granted) { if (granted) tracks = LocalMusic.tracks(context, refresh = true) }

    val np by SongPlayer.nowPlaying
    val playingLocal = LocalPlayer.active.value
    var adding by remember { mutableStateOf<LocalTrack?>(null) }
    adding?.let { t -> AddLocalToPlaylistSheet(t, onDismiss = { adding = null }) }
    var choosingFolders by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    if (choosingFolders) LocalFoldersSheet(
        onChanged = { scope.launch { tracks = LocalMusic.tracks(context) } },
        onDismiss = { choosingFolders = false },
    )

    val title = stringResource(R.string.local_files_title)
    val n = tracks?.size ?: 0
    ImmersivePage(
        title = title,
        subtitle = if (granted && tracks != null) pluralStringResource(R.plurals.local_files_count, n, n) else null,
        onBack = { navController.popBackStack() },
        actions = if (granted) {
            { ImmersiveBarAction(Icons.Rounded.Folder, stringResource(R.string.local_folders_button), onClick = { choosingFolders = true }) }
        } else null,
    ) {
        when {
            !granted -> item(key = "permission") {
                LocalEmpty(
                    stringResource(R.string.local_files_empty_section_title),
                    stringResource(R.string.local_files_empty_section_subtitle),
                    stringResource(if (askedOnce) R.string.local_files_empty_section_button_go_to_settings else R.string.local_files_empty_section_dialog_permission_rationale_accept),
                ) {
                    if (!io.github.thake.soundnest.parental.FeatureGates.shouldRequestAudioStoragePermission(context)) return@LocalEmpty
                    if (askedOnce) context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                    else launcher.launch(LocalMusic.permission)
                }
            }
            tracks == null -> immersiveSongListSkeleton(count = 8)
            tracks?.isEmpty() == true -> item(key = "none") {
                LocalEmpty(stringResource(R.string.local_files_empty_section_no_files_title), stringResource(R.string.local_files_none_subtitle), null) {}
            }
            else -> {
                item(key = "buttons") {
                    ImmersivePlayShuffleButtons(
                        onPlay = {
                            when {
                                playingLocal && SongPlayer.isPlaying() -> SongPlayer.pause()
                                playingLocal -> SongPlayer.play()
                                else -> tracks?.let { LocalPlayer.play(it, 0) }
                            }
                        },
                        onShuffle = { tracks?.let { if (it.isNotEmpty()) LocalPlayer.play(it, it.indices.random(), shuffle = true) } },
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                item(key = "find") {
                    ImmersiveSearchField(
                        query = find, onQueryChange = { find = it }, onSubmit = {},
                        placeholder = stringResource(R.string.local_files_find_hint),
                        modifier = Modifier.padding(horizontal = Immersive.Gutter, vertical = 6.dp),
                    )
                }
                val shown = tracks.orEmpty().filter { io.github.thake.soundnest.ui.components.matchesFind(find, it.title, it.artist, it.album) }
                if (shown.isEmpty() && find.isNotBlank()) item(key = "find-empty") {
                    ImmersiveMessageState(stringResource(R.string.assisted_curation_search_empty_view_no_result_title, find.trim()))
                }
                items(shown, key = { it.id }) { t ->
                    val current = playingLocal && np.uri == "local:${t.id}"
                    Column(immersiveItemPlacement()) {
                        ImmersiveListRow(
                            title = t.title, subtitle = t.artist.ifBlank { null }, imageUrl = t.artUri.toString(), round = false,
                            playing = current,
                            // The whole folder plays from this song, even when the list is filtered.
                            onClick = { tracks?.let { all -> LocalPlayer.play(all, all.indexOf(t).coerceAtLeast(0)) } },
                            onMore = { adding = t },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalEmpty(title: String, subtitle: String, button: String?, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
    ) {
        Image(painterResource(R.drawable.local_files_cover), null, modifier = Modifier.size(120.dp).clip(Immersive.CoverMedium))
        Text(title, style = Immersive.Title, modifier = Modifier.padding(top = 24.dp))
        Text(subtitle, style = Immersive.Subhead, modifier = Modifier.padding(top = 8.dp))
        if (button != null) ImmersiveButton(button, onClick, filled = true, modifier = Modifier.padding(top = 24.dp))
    }
}

// ---------------------------------------------------------------- Episode

/** One podcast episode (or book chapter) of the Immersive look; the state and actions are the Classic page's [EpisodeViewModel]. */
@Composable
fun ImmersiveEpisodeScreen(navController: NavController, episodeId: String, fallbackName: String = "") {
    val vm: EpisodeViewModel = viewModel()
    val context = LocalContext.current
    LaunchedEffect(episodeId) { vm.load(episodeId) }

    val episode by vm.episode.collectAsState()
    val finished = vm.finished.value
    var menuOpen by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    val np by SongPlayer.nowPlaying
    val uri = "spotify:episode:$episodeId"
    val playing = np.uri == uri && SongPlayer.isPlaying()
    val saved = SongPlayer.library[uri] == true

    fun play() {
        val ep = episode ?: return
        SongPlayer.playSong(ep.url, context)
        // The mini player and the notification show it before Spotify reports it.
        io.github.thake.soundnest.di.Graph.currentSongState.updateSongState(ep.coverUri, ep.title, ep.singer, true, ep.id, 0, ep.album)
    }
    episode?.let { ep ->
        if (menuOpen) EpisodeOptionsSheet(song = ep, finished = finished, onPlay = ::play, onMarkFinished = { vm.setFinished(ep, it) }, onDismiss = { menuOpen = false })
    }

    val title = episode?.title?.takeIf { it.isNotBlank() } ?: fallbackName
    ImmersivePage(title = title, onBack = { navController.popBackStack() }) {
        item(key = "hero") {
            Column(Modifier.fillMaxWidth().padding(horizontal = Immersive.Gutter), horizontalAlignment = Alignment.CenterHorizontally) {
                ImmersiveCover(episode?.coverUri, Modifier.fillMaxWidth(0.62f).aspectRatio(1f), Immersive.CoverLarge)
                val showId = episode?.showId.orEmpty()
                if (showId.isNotBlank()) {
                    Text(
                        episode?.singer.orEmpty(), style = Immersive.BodyStrong.copy(color = Immersive.Accent), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 18.dp)
                            .immersiveClickable(onClick = { navController.navigate(showRoute(showId, episode?.singer.orEmpty())) }),
                    )
                }
                val date = episodeDateLabel(episode?.releaseDate.orEmpty())
                val minutes = episodeMinutes(episode?.durationMs ?: 0)
                Text(if (date.isBlank()) minutes else "$date • $minutes", style = Immersive.Subhead, modifier = Modifier.padding(top = 4.dp))
                // Action row: save, share, "...", then Play.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                ) {
                    ImmersiveCircleButton(
                        if (saved) Icons.Rounded.Check else Icons.Rounded.Add,
                        stringResource(if (saved) R.string.episode_unsave else R.string.episode_save),
                        onClick = { episode?.let { SongPlayer.setSaved(it.spotifyUri, !saved) } },
                    )
                    ImmersiveCircleButton(Icons.Rounded.IosShare, stringResource(R.string.a11y_share), onClick = { episode?.let { shareEpisode(context, it) } })
                    ImmersiveCircleButton(Icons.Rounded.MoreHoriz, stringResource(R.string.episode_options), onClick = { menuOpen = true })
                    Spacer(Modifier.weight(1f))
                    ImmersiveButton(
                        stringResource(if (playing) R.string.notif_action_pause else R.string.episode_play),
                        onClick = { if (playing) SongPlayer.pause() else play() },
                        icon = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        filled = true,
                    )
                }
            }
        }
        if (vm.loading.value) immersiveSongListSkeleton(count = 2, keyPrefix = "episode:loading")
        if (vm.failed.value) item(key = "failed") { ImmersiveMessageState(stringResource(R.string.episode_not_found)) }
        episode?.let { ep ->
            if (ep.playedPositionMs > 0 && !finished) item(key = "remaining") {
                Text(
                    stringResource(R.string.episode_remaining, episodeClock(ep.durationMs - ep.playedPositionMs)),
                    style = Immersive.Caption.copy(color = Immersive.Accent),
                    modifier = Modifier.padding(horizontal = Immersive.Gutter, vertical = 4.dp),
                )
            }
            if (ep.description.isNotBlank()) item(key = "description") {
                Column(
                    Modifier.fillMaxWidth().animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)).padding(Immersive.Gutter),
                ) {
                    Text(
                        io.github.thake.soundnest.ui.components.rememberLinkedText(ep.description, Immersive.Accent), style = Immersive.Body.copy(color = Immersive.Text.copy(alpha = 0.8f)),
                        maxLines = if (expanded) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(if (expanded) R.string.episode_show_less else R.string.episode_show_more),
                        style = Immersive.Subhead.copy(color = Immersive.Accent),
                        modifier = Modifier.padding(top = 8.dp).immersiveClickable(onClick = { expanded = !expanded }),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Queue

/** The play queue route of the Immersive look: the song playing, then what is queued and what comes next. Swipe a row to remove it. */
@Composable
fun ImmersiveQueueScreen(navController: NavController) {
    // Spotify's real queue, pushed by the engine.
    val q by SongPlayer.engineQueue
    ImmersivePage(title = stringResource(R.string.action_queue_label), onBack = { navController.popBackStack() }) {
        if (q.current == null && q.queued.isEmpty() && q.nextUp.isEmpty()) {
            item(key = "empty") { ImmersiveMessageState(stringResource(R.string.nothing_here)) }
        }
        q.current?.let { cur ->
            item(key = "now:header") { SectionHeader(stringResource(R.string.default_notification_channel_name)) }
            item(key = "now") { ImmersiveSongRow(song = cur, playing = true, onClick = {}, onMore = null) }
        }
        if (q.queued.isNotEmpty()) {
            item(key = "queued:header") {
                SectionHeader(
                    title = stringResource(R.string.next_in_queue),
                    trailing = {
                        Text(
                            text = stringResource(R.string.clear_queue),
                            style = Immersive.Subhead.copy(color = Immersive.Accent),
                            modifier = Modifier
                                .immersiveClickable(onClick = { SongPlayer.clearQueue() })
                                .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                        )
                    },
                )
            }
            itemsIndexed(q.queued, key = { i, it -> "q" + i + it.uid + it.uri }) { _, item ->
                Column(immersiveItemPlacement()) {
                    ImmersiveSwipeToRemove(item.uid + item.uri, onRemove = { SongPlayer.removeFromQueue(item) }) {
                        Box(Modifier.background(Immersive.Page)) {
                            ImmersiveSongRow(
                                song = item.song,
                                playing = false,
                                onClick = { SongPlayer.skipTo(item) },
                                onMore = null,
                                trailing = {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.aligned_curation_first_save_default_row_remove_action_description),
                                        tint = Immersive.TextTertiary,
                                        modifier = Modifier
                                            .size(32.dp)
                                            .immersiveClickable(onClick = { SongPlayer.removeFromQueue(item) })
                                            .padding(6.dp),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
        if (q.nextUp.isNotEmpty()) {
            item(key = "next:header") { SectionHeader(stringResource(R.string.next_up)) }
            itemsIndexed(q.nextUp, key = { i, it -> "n" + i + it.uid + it.uri }) { _, item ->
                Column(immersiveItemPlacement()) {
                    ImmersiveSwipeToRemove(item.uid + item.uri, onRemove = { SongPlayer.removeFromQueue(item) }) {
                        Box(Modifier.background(Immersive.Page)) {
                            ImmersiveSongRow(
                                song = item.song,
                                playing = false,
                                onClick = { SongPlayer.skipTo(item) },
                                onMore = null,
                                trailing = {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.aligned_curation_first_save_default_row_remove_action_description),
                                        tint = Immersive.TextTertiary,
                                        modifier = Modifier
                                            .size(32.dp)
                                            .immersiveClickable(onClick = { SongPlayer.removeFromQueue(item) })
                                            .padding(6.dp),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
