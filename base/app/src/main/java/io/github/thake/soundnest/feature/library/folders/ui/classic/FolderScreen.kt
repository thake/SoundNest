package io.github.thake.soundnest.feature.library.folders.ui.classic

import io.github.thake.soundnest.core.ui.classic.AppDestructive
import io.github.thake.soundnest.core.ui.classic.tone
import io.github.thake.soundnest.core.ui.classic.AppText
import io.github.thake.soundnest.core.ui.classic.AppMuted

import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.github.thake.soundnest.R
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import io.github.thake.soundnest.core.catalog.LibraryEdits
import io.github.thake.soundnest.feature.library.folders.data.LibraryFolders
import io.github.thake.soundnest.core.catalog.Response
import io.github.thake.soundnest.core.catalog.SpotifySync
import io.github.thake.soundnest.core.model.LibraryEntry
import io.github.thake.soundnest.feature.localfiles.data.LocalItem
import io.github.thake.soundnest.feature.localfiles.data.LocalPlaylists
import io.github.thake.soundnest.feature.localfiles.data.LocalTrack
import io.github.thake.soundnest.core.di.Graph
import io.github.thake.soundnest.core.playback.LocalPlayer
import io.github.thake.soundnest.core.playback.SongPlayer
import io.github.thake.soundnest.feature.library.ui.SwipeToRemove
import io.github.thake.soundnest.core.ui.classic.Loader
import io.github.thake.soundnest.core.ui.classic.NameDialog
import io.github.thake.soundnest.core.ui.classic.SkeletonKind
import io.github.thake.soundnest.core.ui.look.pressable
import io.github.thake.soundnest.core.ui.navigation.localPlaylistRoute
import io.github.thake.soundnest.core.ui.navigation.playlistRoute
import io.github.thake.soundnest.feature.accounts.ui.ProfileAvatar
import io.github.thake.soundnest.core.ui.classic.AppBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.thake.soundnest.feature.library.ui.classic.SumUpLibraryScreen

private val Green: Color get() = io.github.thake.soundnest.core.ui.classic.AppAccent
private val SheetColor: androidx.compose.ui.graphics.Color get() = tone(0xFF282828)


@Composable
private fun SheetRow(icon: ImageVector, label: String, detail: String? = null, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Icon(icon, null, tint = AppMuted, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(label, color = AppText, fontSize = 16.sp)
            if (detail != null) Text(detail, color = AppMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun PageHeader(title: String, subtitle: String?, navController: NavController, onMenu: (() -> Unit)?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp, 8.dp, 8.dp, 4.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(44.dp).clip(CircleShape).pressable { navController.navigateUp() },
        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.accessibility_navigate_back), tint = AppText) }
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(title, color = AppText, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, color = AppMuted, fontSize = 13.sp)
        }
        if (onMenu != null) Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(44.dp).clip(CircleShape).pressable { onMenu() },
        ) { Icon(Icons.Default.MoreVert, stringResource(R.string.content_description_context_menu), tint = AppText) }
    }
}

/** A Spotify library folder: its folders and playlists, opened level by level. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderScreen(navController: NavController, uri: String, initialName: String) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initialName) }
    // The folder as Spotify gave it; [entries] is that with the changes Spotify has not caught up with.
    var server by remember { mutableStateOf<List<LibraryEntry>?>(null) }
    val edits by io.github.thake.soundnest.core.catalog.LibraryEdits.version.collectAsState()
    val entries = remember(server, edits) { server?.let { io.github.thake.soundnest.core.catalog.LibraryEdits.applyTo(uri, it) } }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var newPlaylist by remember { mutableStateOf(false) }
    var deletingFolder by remember { mutableStateOf(false) }
    var generation by remember { mutableStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(uri, generation) {
        withContext(Dispatchers.IO) {
            Graph.repository.provideLibraryFolder(uri).collect { r -> if (r is Response.Success) server = r.data else if (r is Response.Error && server == null) server = emptyList() }
        }
        refreshing = false
    }
    // A change just written: the folder is read again once Spotify's list has had time to catch up.
    LaunchedEffect(uri) { io.github.thake.soundnest.core.catalog.LibraryEdits.reloads.collect { generation++ } }
    if (menu) io.github.thake.soundnest.core.ui.look.LookSheet(onDismissRequest = { menu = false }, containerColor = SheetColor) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            SheetRow(Icons.AutoMirrored.Filled.PlaylistAdd, stringResource(R.string.add_to_playlist_new_playlist_button_text)) { menu = false; newPlaylist = true }
            SheetRow(Icons.Outlined.Edit, stringResource(R.string.context_menu_rename_folder)) { menu = false; renaming = true }
            SheetRow(Icons.Outlined.Delete, stringResource(R.string.folder_delete)) { menu = false; deletingFolder = true }
        }
    }
    if (deletingFolder) androidx.compose.material3.AlertDialog(
        onDismissRequest = { deletingFolder = false },
        containerColor = SheetColor,
        text = { Text(stringResource(R.string.folder_delete_confirm, name), color = AppText) },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                deletingFolder = false
                // The folder is gone from the library at once; a refusal brings it back.
                LibraryFolders.delete(context, uri)
                navController.navigateUp()
            }) { Text(stringResource(R.string.actions_picker_delete), color = AppDestructive) }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { deletingFolder = false }) { Text(stringResource(R.string.cancel), color = AppText) } },
    )
    // A Spotify playlist created straight inside this folder.
    if (newPlaylist) NameDialog(stringResource(R.string.create_playlist_content_description), context.getString(R.string.my_playlist),
        stringResource(R.string.create_playlist_create_button), onDismiss = { newPlaylist = false }) { playlistName ->
        newPlaylist = false
        SpotifySync.createPlaylistWithTrack(context, playlistName, "", folderUri = uri)
    }
    if (renaming) NameDialog(stringResource(R.string.context_menu_rename_folder), name, stringResource(R.string.local_rename),
        onDismiss = { renaming = false }) { newName ->
        renaming = false
        val before = name
        name = newName
        LibraryFolders.rename(context, uri, newName) { ok -> if (!ok) name = before }
    }
    Column(Modifier.fillMaxSize().background(AppBackground)) {
        val n = entries?.size
        PageHeader(name, n?.let { pluralStringResource(R.plurals.add_to_playlist_folder_subtitle_playlists_count, it, it.toString()) }, navController) { menu = true }
        when (val list = entries) {
            null -> Loader(SkeletonKind.Header)
            else -> {
                io.github.thake.soundnest.feature.library.settings.LibraryOrder.version
                val arranged = io.github.thake.soundnest.feature.library.settings.LibraryOrder.run { arrange(list, sort, custom(uri), 0) }
                androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                    isRefreshing = refreshing, onRefresh = { refreshing = true; generation++ }, modifier = Modifier.fillMaxSize(),
                ) {
                    SumUpLibraryScreen(PaddingValues(0.dp), arranged, emptyList(), navController, reorderLevel = uri, onChanged = { generation++ })
                }
            }
        }
    }
}
