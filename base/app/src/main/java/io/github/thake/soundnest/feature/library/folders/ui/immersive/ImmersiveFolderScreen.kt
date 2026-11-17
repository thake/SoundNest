package io.github.thake.soundnest.feature.library.folders.ui.immersive


import io.github.thake.soundnest.core.ui.immersive.pages.ImmersiveNameDialog
import io.github.thake.soundnest.core.ui.immersive.pages.ImmersivePageFrame
import io.github.thake.soundnest.core.ui.immersive.pages.ImmersiveSheet
import io.github.thake.soundnest.core.ui.immersive.pages.ImmersiveSheetRow
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
 * A Spotify library folder in the Immersive look: the folder's name over its folders and
 * playlists, in the Library's rows (long press and drag to reorder, the ⋯ menu of every row), a
 * ⋯ in the bar for a new playlist, renaming and deleting the folder. The loading, the pending
 * edits and the actions are the Classic `FolderScreen`'s.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImmersiveFolderScreen(navController: NavController, uri: String, initialName: String) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initialName) }
    // The folder as Spotify gave it; [entries] is that with the changes Spotify has not caught up with.
    var server by remember { mutableStateOf<List<LibraryEntry>?>(null) }
    val edits by LibraryEdits.version.collectAsState()
    val entries = remember(server, edits) { server?.let { LibraryEdits.applyTo(uri, it) } }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var newPlaylist by remember { mutableStateOf(false) }
    var deletingFolder by remember { mutableStateOf(false) }
    var itemMenu by remember { mutableStateOf<LibraryEntry?>(null) }
    var generation by remember { mutableStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(uri, generation) {
        withContext(Dispatchers.IO) {
            Graph.repository.provideLibraryFolder(uri).collect { r ->
                if (r is Response.Success) server = r.data else if (r is Response.Error && server == null) server = emptyList()
            }
        }
        refreshing = false
    }
    // A change just written: the folder is read again once Spotify's list has had time to catch up.
    LaunchedEffect(uri) { LibraryEdits.reloads.collect { generation++ } }

    if (menu) ImmersiveSheet(onDismiss = { menu = false }) {
        ImmersiveSheetRow(Icons.AutoMirrored.Rounded.PlaylistAdd, stringResource(R.string.add_to_playlist_new_playlist_button_text), onClick = { menu = false; newPlaylist = true })
        ImmersiveSheetRow(Icons.Rounded.Edit, stringResource(R.string.context_menu_rename_folder), onClick = { menu = false; renaming = true })
        ImmersiveSheetRow(Icons.Rounded.DeleteOutline, stringResource(R.string.folder_delete), destructive = true, onClick = { menu = false; deletingFolder = true })
    }
    itemMenu?.let { LibraryItemSheet(it, onDismiss = { itemMenu = null }, onChanged = { generation++ }) }
    if (deletingFolder) ImmersiveAlertDialog(
        onDismissRequest = { deletingFolder = false },
        text = { Text(stringResource(R.string.folder_delete_confirm, name), style = Immersive.Body) },
        confirmButton = {
            TextButton(onClick = {
                deletingFolder = false
                // The folder is gone from the library at once; a refusal brings it back.
                LibraryFolders.delete(context, uri)
                navController.navigateUp()
            }) { Text(stringResource(R.string.actions_picker_delete), style = Immersive.BodyStrong.copy(color = Immersive.Destructive)) }
        },
        dismissButton = {
            TextButton(onClick = { deletingFolder = false }) {
                Text(stringResource(R.string.cancel), style = Immersive.BodyStrong.copy(color = Immersive.Accent))
            }
        },
    )
    // A Spotify playlist created straight inside this folder.
    if (newPlaylist) ImmersiveNameDialog(
        stringResource(R.string.create_playlist_content_description), context.getString(R.string.my_playlist),
        stringResource(R.string.create_playlist_create_button), onDismiss = { newPlaylist = false },
    ) { playlistName ->
        newPlaylist = false
        SpotifySync.createPlaylistWithTrack(context, playlistName, "", folderUri = uri)
    }
    if (renaming) ImmersiveNameDialog(
        stringResource(R.string.context_menu_rename_folder), name, stringResource(R.string.local_rename),
        onDismiss = { renaming = false },
    ) { newName ->
        renaming = false
        val before = name
        name = newName
        LibraryFolders.rename(context, uri, newName) { ok -> if (!ok) name = before }
    }

    val listState = rememberLazyListState()
    val arranged = entries?.let { list ->
        LibraryOrder.version
        LibraryOrder.arrange(list, LibraryOrder.sort, LibraryOrder.custom(uri), 0)
    }.orEmpty()
    ImmersivePageFrame {
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { refreshing = true; generation++ },
            modifier = Modifier.fillMaxSize(),
        ) {
            LibraryList(
                listState = listState,
                header = {
                    ImmersiveLargeTitle(
                        title = name,
                        subtitle = entries?.size?.let { pluralStringResource(R.plurals.add_to_playlist_folder_subtitle_playlists_count, it, it.toString()) },
                    )
                },
                loading = entries == null,
                entries = arranged,
                artists = emptyList(),
                loadFailed = false,
                onRetry = { generation++ },
                navController = navController,
                reorderLevel = uri,
                pinned = 0,
                onMenu = { itemMenu = it },
            )
        }
        ImmersiveTopBar(title = name, collapse = rememberTopBarCollapse(listState), onBack = { navController.navigateUp() }) {
            ImmersiveBarAction(Icons.Rounded.MoreHoriz, stringResource(R.string.content_description_context_menu), onClick = { menu = true })
        }
    }
}
