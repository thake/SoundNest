package io.github.thake.soundnest.feature.spokenword.podcasts.ui.immersive

import io.github.thake.soundnest.feature.spokenword.ui.episodeClock
import io.github.thake.soundnest.feature.spokenword.ui.episodeDateLabel
import io.github.thake.soundnest.feature.spokenword.ui.episodeMinutes


import io.github.thake.soundnest.core.ui.immersive.pages.ImmersivePage
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
import io.github.thake.soundnest.feature.localfiles.data.LocalMusic
import io.github.thake.soundnest.feature.localfiles.data.LocalTrack
import io.github.thake.soundnest.core.playback.LocalPlayer
import io.github.thake.soundnest.core.playback.SongPlayer
import io.github.thake.soundnest.feature.spokenword.ui.EpisodeOptionsSheet
import io.github.thake.soundnest.feature.spokenword.ui.shareEpisode
import io.github.thake.soundnest.feature.spokenword.ui.spotifyUri
import io.github.thake.soundnest.core.ui.immersive.Immersive
import io.github.thake.soundnest.core.ui.immersive.components.ImmersiveButton
import io.github.thake.soundnest.core.ui.immersive.components.ImmersiveCircleButton
import io.github.thake.soundnest.core.ui.immersive.components.ImmersiveCover
import io.github.thake.soundnest.core.ui.immersive.components.ImmersiveListRow
import io.github.thake.soundnest.core.ui.immersive.components.ImmersiveMessageState
import io.github.thake.soundnest.core.ui.immersive.components.ImmersivePlayShuffleButtons
import io.github.thake.soundnest.core.ui.immersive.components.ImmersiveSearchField
import io.github.thake.soundnest.core.ui.immersive.components.ImmersiveSongRow
import io.github.thake.soundnest.core.ui.immersive.components.SectionHeader
import io.github.thake.soundnest.core.ui.immersive.components.immersiveClickable
import io.github.thake.soundnest.core.ui.immersive.components.immersiveSongListSkeleton
import io.github.thake.soundnest.core.ui.immersive.motion.immersiveItemPlacement
import io.github.thake.soundnest.core.ui.immersive.shell.ImmersiveBarAction
import io.github.thake.soundnest.core.ui.navigation.showRoute
import io.github.thake.soundnest.feature.localfiles.ui.classic.AddLocalToPlaylistSheet
import io.github.thake.soundnest.feature.localfiles.ui.classic.LocalFoldersSheet
import io.github.thake.soundnest.feature.spokenword.podcasts.ui.EpisodeViewModel
import kotlinx.coroutines.launch


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
        io.github.thake.soundnest.core.di.Graph.currentSongState.updateSongState(ep.coverUri, ep.title, ep.singer, true, ep.id, 0, ep.album)
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
                        io.github.thake.soundnest.core.ui.look.rememberLinkedText(ep.description, Immersive.Accent), style = Immersive.Body.copy(color = Immersive.Text.copy(alpha = 0.8f)),
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
