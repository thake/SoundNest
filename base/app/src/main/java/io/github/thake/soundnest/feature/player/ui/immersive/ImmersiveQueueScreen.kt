package io.github.thake.soundnest.feature.player.ui.immersive


import io.github.thake.soundnest.core.ui.immersive.pages.ImmersivePage
import io.github.thake.soundnest.core.ui.immersive.pages.ImmersiveSwipeToRemove
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
