package io.github.thake.soundnest.feature.localfiles.ui.immersive


import io.github.thake.soundnest.core.ui.immersive.pages.ImmersiveNameDialog
import io.github.thake.soundnest.core.ui.immersive.pages.ImmersivePage
import io.github.thake.soundnest.core.ui.immersive.pages.ImmersiveSheet
import io.github.thake.soundnest.core.ui.immersive.pages.ImmersiveSheetRow
import io.github.thake.soundnest.core.ui.immersive.pages.ImmersiveSwipeToRemove
import io.github.thake.soundnest.core.ui.immersive.sheets.ImmersiveAlertDialog
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.catalog.LibraryEdits
import io.github.thake.soundnest.feature.library.folders.data.LibraryFolders
import io.github.thake.soundnest.core.catalog.Response
import io.github.thake.soundnest.core.catalog.SpotifySync
import io.github.thake.soundnest.core.model.LibraryEntry
import io.github.thake.soundnest.feature.library.settings.LibraryOrder
import io.github.thake.soundnest.feature.localfiles.data.LocalPlaylists
import io.github.thake.soundnest.core.di.Graph
import io.github.thake.soundnest.core.playback.LocalPlayer
import io.github.thake.soundnest.core.playback.SongPlayer
import io.github.thake.soundnest.core.ui.immersive.Immersive
import io.github.thake.soundnest.core.ui.immersive.components.ImmersiveListRow
import io.github.thake.soundnest.core.ui.immersive.components.ImmersiveMessageState
import io.github.thake.soundnest.core.ui.immersive.components.ImmersivePlayShuffleButtons
import io.github.thake.soundnest.feature.library.ui.immersive.LibraryList
import io.github.thake.soundnest.core.ui.immersive.shell.ImmersiveBarAction
import io.github.thake.soundnest.core.ui.immersive.shell.ImmersiveLargeTitle
import io.github.thake.soundnest.core.ui.immersive.shell.ImmersiveTopBar
import io.github.thake.soundnest.core.ui.immersive.shell.rememberTopBarCollapse
import io.github.thake.soundnest.feature.library.ui.classic.LibraryItemSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


/**
 * A playlist kept on this phone, in the Immersive look: Play and Shuffle over the phone's files,
 * each row swiping away to leave the playlist, a ⋯ in the bar to rename or delete it. The data
 * and the player are the Classic `LocalPlaylistScreen`'s ([LocalPlaylists], [LocalPlayer]).
 */
@Composable
fun ImmersiveLocalPlaylistScreen(navController: NavController, id: String) {
    val playlist = LocalPlaylists.playlists[id]
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    if (playlist == null) {
        LaunchedEffect(Unit) { navController.navigateUp() }
        return
    }
    if (menu) ImmersiveSheet(onDismiss = { menu = false }) {
        ImmersiveSheetRow(Icons.Rounded.Edit, stringResource(R.string.local_rename), onClick = { menu = false; renaming = true })
        ImmersiveSheetRow(Icons.Rounded.DeleteOutline, stringResource(R.string.local_delete_playlist), destructive = true, onClick = {
            menu = false
            navController.navigateUp()
            LocalPlaylists.delete(id)
        })
    }
    if (renaming) ImmersiveNameDialog(
        stringResource(R.string.local_rename), playlist.name, stringResource(R.string.local_rename),
        onDismiss = { renaming = false },
    ) { renaming = false; LocalPlaylists.rename(id, it) }

    val np by SongPlayer.nowPlaying
    val playingHere = LocalPlayer.active.value && LocalPlayer.contextName == playlist.name
    val tracks = playlist.items.map { it.toTrack() }
    ImmersivePage(
        title = playlist.name,
        subtitle = stringResource(R.string.local_playlist_subtitle) + " • " +
            pluralStringResource(R.plurals.add_to_playlist_subtitle_songs_count, playlist.items.size, playlist.items.size.toString()),
        onBack = { navController.navigateUp() },
        actions = {
            ImmersiveBarAction(Icons.Rounded.MoreHoriz, stringResource(R.string.content_description_context_menu), onClick = { menu = true })
        },
    ) {
        if (playlist.items.isEmpty()) {
            item(key = "empty") { ImmersiveMessageState(stringResource(R.string.local_playlist_empty)) }
            return@ImmersivePage
        }
        item(key = "buttons") {
            ImmersivePlayShuffleButtons(
                onPlay = {
                    when {
                        playingHere && SongPlayer.isPlaying() -> SongPlayer.pause()
                        playingHere -> SongPlayer.play()
                        else -> LocalPlayer.play(tracks, 0, context = playlist.name)
                    }
                },
                onShuffle = { LocalPlayer.play(tracks, tracks.indices.random(), shuffle = true, context = playlist.name) },
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        itemsIndexed(playlist.items, key = { _, item -> item.key }) { index, item ->
            val current = LocalPlayer.active.value && np.uri == "local:${tracks[index].id}"
            Column(Modifier.animateItem()) {
                ImmersiveSwipeToRemove(item.key, onRemove = { LocalPlaylists.remove(id, item.key) }) {
                    ImmersiveListRow(
                        title = item.title,
                        subtitle = item.artist.ifBlank { null },
                        imageUrl = item.artUri,
                        round = false,
                        playing = current && SongPlayer.isPlaying(),
                        onClick = { LocalPlayer.play(tracks, index, context = playlist.name) },
                    )
                }
            }
        }
    }
}
