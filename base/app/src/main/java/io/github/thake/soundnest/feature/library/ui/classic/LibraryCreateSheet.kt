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

/** The Library's "+": a Spotify playlist, a Spotify folder, or a playlist on this phone. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryCreateSheet(navController: NavController, onDismiss: () -> Unit, onLibraryChanged: () -> Unit) {
    val context = LocalContext.current
    var asking by remember { mutableStateOf<String?>(null) } // "playlist" | "folder" | "local"
    when (asking) {
        "playlist" -> NameDialog(stringResource(R.string.create_playlist_content_description), context.getString(R.string.my_playlist),
            stringResource(R.string.create_playlist_create_button), onDismiss = onDismiss) { name ->
            // The row shows in the library at once; SpotifySync takes it back if Spotify refuses.
            SpotifySync.createPlaylistWithTrack(context, name, "")
            onDismiss()
        }
        "folder" -> NameDialog(stringResource(R.string.create_menu_item_folder_title), context.getString(R.string.create_folder_name_suggested_folder_name),
            stringResource(R.string.create_playlist_create_button), onDismiss = onDismiss) { name ->
            LibraryFolders.create(context, name)
            onDismiss()
        }
        "local" -> NameDialog(stringResource(R.string.local_playlist_new), context.getString(R.string.my_playlist),
            stringResource(R.string.create_playlist_create_button), onDismiss = onDismiss) { name ->
            val p = LocalPlaylists.create(name)
            onDismiss()
            navController.navigate(localPlaylistRoute(p.id))
        }
        else -> io.github.thake.soundnest.core.ui.look.LookSheet(onDismissRequest = onDismiss, containerColor = SheetColor) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
                SheetRow(Icons.AutoMirrored.Filled.PlaylistAdd, stringResource(R.string.assisted_curation_search_playlist)) { asking = "playlist" }
                SheetRow(Icons.Outlined.CreateNewFolder, stringResource(R.string.create_menu_item_folder_title)) { asking = "folder" }
                SheetRow(Icons.Outlined.PhoneAndroid, stringResource(R.string.local_playlist_new), stringResource(R.string.local_add_note)) { asking = "local" }
            }
        }
    }
}
