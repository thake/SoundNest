package io.github.thake.soundnest.feature.localfiles.ui.classic

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

/**
 * "Add to playlist" for a phone file: a playlist on this phone (or a new one), or one of
 * the user's Spotify playlists, where it is shown under "Added on this device" (Spotify
 * cannot hold phone files, so it stays on this phone).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalGlideComposeApi::class)
@Composable
fun AddLocalToPlaylistSheet(track: LocalTrack, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var spotifyLists by remember { mutableStateOf(io.github.thake.soundnest.core.catalog.EditablePlaylists.cachedWithCovers()) }
    var creating by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        spotifyLists = io.github.thake.soundnest.core.catalog.EditablePlaylists.loadWithCovers()
    }
    fun added(name: String) {
        Toast.makeText(context, context.getString(R.string.local_added_to, name), Toast.LENGTH_SHORT).show()
        onDismiss()
    }
    if (creating) {
        NameDialog(stringResource(R.string.local_playlist_new), context.getString(R.string.my_playlist),
            stringResource(R.string.create_playlist_create_button), onDismiss = { creating = false }) { name ->
            val p = LocalPlaylists.create(name)
            LocalPlaylists.add(p.id, track)
            added(p.name)
        }
        return
    }
    io.github.thake.soundnest.core.ui.look.LookSheet(onDismissRequest = onDismiss, containerColor = SheetColor) {
        LazyColumn(Modifier.navigationBarsPadding().heightIn(max = 560.dp)) {
            item {
                Text(stringResource(R.string.context_menu_add_to_playlist), color = AppText, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(20.dp, 4.dp, 20.dp, 4.dp))
                Text(stringResource(R.string.local_add_note), color = AppMuted, fontSize = 12.sp,
                    modifier = Modifier.padding(20.dp, 0.dp, 20.dp, 8.dp))
                SheetRow(Icons.AutoMirrored.Filled.PlaylistAdd, stringResource(R.string.add_to_playlist_new_playlist_button_text),
                    stringResource(R.string.local_playlist_subtitle)) { creating = true }
            }
            items(LocalPlaylists.list(), key = { "l:" + it.id }) { p ->
                PickRow(name = p.name, detail = stringResource(R.string.local_playlist_subtitle), cover = null) {
                    LocalPlaylists.add(p.id, track); added(p.name)
                }
            }
            when (val lists = spotifyLists) {
                null -> item { Text(stringResource(R.string.loading_playlists), color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(20.dp, 12.dp)) }
                else -> items(lists, key = { "s:" + it.id }) { p ->
                    PickRow(name = p.name, detail = stringResource(R.string.local_added_section), cover = p.imageUrl) {
                        LocalPlaylists.addToSpotify(p.id, track); added(p.name)
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun PickRow(name: String, detail: String, cover: String?, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        if (cover == null) Image(painterResource(R.drawable.local_files_cover), null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)))
        else GlideImage(model = cover, contentDescription = null, contentScale = ContentScale.Crop,
            loading = placeholder(R.drawable.placeholder), failure = placeholder(R.drawable.placeholder),
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)))
        Column(Modifier.padding(start = 12.dp)) {
            Text(name, color = AppText, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, color = AppMuted, fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}
