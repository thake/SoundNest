package io.github.thake.soundnest.feature.library.ui.classic

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
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.rounded.Star
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

/**
 * Whether the row's ⋯ menu offers "Remove from Your Library": a playlist of someone else (its
 * owner known), an album, an artist or a show. The user's own playlists are deleted, not removed.
 */
internal fun removable(entry: LibraryEntry, me: String? = io.github.thake.soundnest.feature.accounts.session.ProfileCache.id): Boolean {
    val uri = entry.uri
    if (entry.isFolder) return false
    if (uri.startsWith("spotify:playlist:")) return me != null && entry.ownerUri.isNotBlank() && entry.ownerUri != "spotify:user:$me"
    return uri.startsWith("spotify:album:") || uri.startsWith("spotify:artist:") || uri.startsWith("spotify:show:")
}

/**
 * A library row's ⋯ menu: pin it at the top, move a playlist or folder into another folder
 * (or back to the top level), delete a folder, remove what is not the user's own ([removable]). Each shows in the list at once ([LibraryEdits]),
 * which reads itself again when Spotify has had time to catch up; [onChanged] is kept for callers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryItemSheet(entry: LibraryEntry, onDismiss: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    var moving by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var merging by remember { mutableStateOf(false) }
    // The change shows in the list at once and is written in the app's own scope (the sheet is
    // closed by then); a refusal puts the list back and says so.
    fun act(edit: LibraryEdits.Edit, block: suspend () -> Unit) {
        LibraryEdits.run(context, edit, write = { block(); LibraryFolders.changed() })
        onDismiss()
    }
    val api = io.github.thake.soundnest.host.android.SoundNestRuntime.api
    io.github.thake.soundnest.core.ui.look.LookSheet(onDismissRequest = onDismiss, containerColor = SheetColor) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Text(entry.name, color = AppText, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(20.dp, 0.dp, 20.dp, 8.dp))
            if (!moving) {
                // A saved playlist or album: all its songs into the queue (read to the end first).
                if (entry.uri.startsWith("spotify:playlist:") || entry.uri.startsWith("spotify:album:")) {
                    SheetRow(Icons.AutoMirrored.Outlined.PlaylistPlay, stringResource(R.string.context_menu_play_next)) {
                        io.github.thake.soundnest.core.playback.CollectionQueue.queue(entry.uri, next = true); onDismiss()
                    }
                    SheetRow(Icons.AutoMirrored.Outlined.QueueMusic, stringResource(R.string.context_menu_add_to_queue)) {
                        io.github.thake.soundnest.core.playback.CollectionQueue.queue(entry.uri, next = false); onDismiss()
                    }
                }
                SheetRow(Icons.Outlined.PushPin, stringResource(if (entry.pinned) R.string.library_unpin else R.string.library_pin)) {
                    act(LibraryEdits.Pin(entry.uri, !entry.pinned)) { if (entry.pinned) api.library.unpin(entry.uri) else api.library.pin(entry.uri) }
                }
                if (entry.isPlaylist || entry.isFolder) SheetRow(Icons.Outlined.DriveFileMove, stringResource(R.string.library_move_to_folder)) { moving = true }
                // A saved playlist or album: all its songs into another playlist of yours.
                if (entry.uri.startsWith("spotify:playlist:") || entry.uri.startsWith("spotify:album:"))
                    SheetRow(Icons.AutoMirrored.Filled.PlaylistAdd, stringResource(R.string.context_menu_add_to_playlist)) { merging = true }
                if (entry.isFolder) SheetRow(Icons.Outlined.Delete, stringResource(R.string.folder_delete)) { deleting = true }
                // Someone else's playlist, an album, an artist or a show: out of the library from here,
                // without opening it and relying on its heart (reported: a playlist listed with an
                // empty heart could not be taken out).
                if (removable(entry)) SheetRow(Icons.Outlined.RemoveCircleOutline, stringResource(R.string.whats_new_remove_library)) {
                    io.github.thake.soundnest.core.playback.SongPlayer.setSaved(entry.uri, false); onDismiss()
                }
                val adminUnlocked = io.github.thake.soundnest.feature.parental.ui.rememberAdminUnlocked()
                val policy = io.github.thake.soundnest.feature.parental.settings.rememberParentalPolicy()
                val parentTarget = remember(entry) { io.github.thake.soundnest.feature.parental.ui.ParentBlockActions.targetForLibraryEntry(entry) }
                if (adminUnlocked && parentTarget != null) {
                    val picked = io.github.thake.soundnest.feature.parental.ui.ParentBlockActions.isPicked(parentTarget, policy)
                    val blocked = io.github.thake.soundnest.feature.parental.ui.ParentBlockActions.isBlocked(parentTarget, policy)
                    SheetRow(
                        if (picked) androidx.compose.material.icons.Icons.Rounded.Star else androidx.compose.material.icons.Icons.Outlined.StarOutline,
                        io.github.thake.soundnest.feature.parental.ui.ParentBlockActions.pickActionLabel(context, parentTarget, picked),
                    ) {
                        io.github.thake.soundnest.feature.parental.ui.ParentBlockActions.togglePick(context, parentTarget)
                        onChanged()
                        onDismiss()
                    }
                    SheetRow(
                        if (blocked) androidx.compose.material.icons.Icons.Outlined.CheckCircle else androidx.compose.material.icons.Icons.Outlined.Block,
                        io.github.thake.soundnest.feature.parental.ui.ParentBlockActions.actionLabel(context, parentTarget, blocked),
                    ) {
                        io.github.thake.soundnest.feature.parental.ui.ParentBlockActions.toggleBlock(context, parentTarget)
                        onChanged()
                        onDismiss()
                    }
                }
            } else {
                val folders by androidx.compose.runtime.produceState<List<io.github.thake.soundnest.api.LibraryItem>?>(null) {
                    value = runCatching { api.library.items(folderUri = null, limit = 200).items.filter { it.kind == io.github.thake.soundnest.api.MediaKind.FOLDER && it.uri != entry.uri } }
                        .getOrDefault(emptyList())
                }
                SheetRow(Icons.Outlined.Home, stringResource(R.string.library_top_level)) { act(LibraryEdits.Move(entry, null)) { api.library.moveToFolder(entry.uri, null) } }
                folders?.forEach { f ->
                    SheetRow(Icons.Outlined.Folder, f.name) { act(LibraryEdits.Move(entry, f.uri)) { api.library.moveToFolder(entry.uri, f.uri) } }
                }
            }
        }
    }
    if (merging) io.github.thake.soundnest.feature.library.ui.AddCollectionToPlaylistSheet(entry.uri, entry.name) { merging = false; onDismiss() }
    if (deleting) androidx.compose.material3.AlertDialog(
        onDismissRequest = { deleting = false },
        containerColor = SheetColor,
        text = { Text(stringResource(R.string.folder_delete_confirm, entry.name), color = AppText) },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { deleting = false; act(LibraryEdits.Remove(entry.uri)) { api.library.deleteFolder(entry.uri) } }) {
                Text(stringResource(R.string.actions_picker_delete), color = AppDestructive)
            }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.cancel), color = AppText) } },
    )
}
