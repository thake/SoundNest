package io.github.thake.soundnest.feature.localfiles.ui.classic

import io.github.thake.soundnest.feature.home.whatsnew.ui.classic.title

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

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun LocalItemRow(item: LocalItem, current: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().pressable(0.97f, onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        GlideImage(
            model = item.artUri, contentDescription = null, contentScale = ContentScale.Crop,
            loading = placeholder(R.drawable.placeholder), failure = placeholder(R.drawable.local_files_cover),
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)),
        )
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(item.title, color = if (current) Green else AppText, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.artist.isNotBlank()) Text(item.artist, color = AppMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Phone files in a list ([items]), played by the local player; swipe right to left to remove. */
fun androidx.compose.foundation.lazy.LazyListScope.localItems(items: List<LocalItem>, context: String, onRemove: (LocalItem) -> Unit) {
    itemsIndexed(items, key = { _, i -> i.key }) { index, item ->
        val np = SongPlayer.nowPlaying.value
        val track = item.toTrack()
        SwipeToRemove(item.key, onRemove = { onRemove(item) }) {
            LocalItemRow(item, LocalPlayer.active.value && np.uri == "local:${track.id}") {
                LocalPlayer.play(items.map { it.toTrack() }, index, context = context)
            }
        }
    }
}

/** A playlist kept on this phone. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalPlaylistScreen(navController: NavController, id: String) {
    val playlist = LocalPlaylists.playlists[id]
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    if (playlist == null) {
        LaunchedEffect(Unit) { navController.navigateUp() }
        return
    }
    if (menu) io.github.thake.soundnest.core.ui.look.LookSheet(onDismissRequest = { menu = false }, containerColor = SheetColor) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            SheetRow(Icons.Outlined.Edit, stringResource(R.string.local_rename)) { menu = false; renaming = true }
            SheetRow(Icons.Outlined.Delete, stringResource(R.string.local_delete_playlist)) {
                menu = false
                navController.navigateUp()
                LocalPlaylists.delete(id)
            }
        }
    }
    if (renaming) NameDialog(stringResource(R.string.local_rename), playlist.name, stringResource(R.string.local_rename),
        onDismiss = { renaming = false }) { renaming = false; LocalPlaylists.rename(id, it) }
    Column(Modifier.fillMaxSize().background(AppBackground)) {
        PageHeader(playlist.name, stringResource(R.string.local_playlist_subtitle) + " • " +
            pluralStringResource(R.plurals.add_to_playlist_subtitle_songs_count, playlist.items.size, playlist.items.size.toString()),
            navController) { menu = true }
        if (playlist.items.isEmpty()) Text(stringResource(R.string.local_playlist_empty), color = AppMuted, fontSize = 14.sp,
            modifier = Modifier.padding(20.dp))
        else LazyColumn(contentPadding = PaddingValues(bottom = io.github.thake.soundnest.core.ui.look.bottomBarsSpace()), modifier = Modifier.fillMaxSize()) {
            item {
                Row(Modifier.fillMaxWidth().padding(16.dp, 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(52.dp).pressable(0.92f) {
                            LocalPlayer.play(playlist.items.map { it.toTrack() }, 0, context = playlist.name)
                        }.clip(CircleShape).background(Green),
                    ) {
                        Icon(painterResource(R.drawable.play_svgrepo_com), stringResource(R.string.content_description_play_button), tint = Color.Black, modifier = Modifier.size(24.dp))
                    }
                }
            }
            localItems(playlist.items, playlist.name) { LocalPlaylists.remove(id, it.key) }
        }
    }
}
