package io.github.thake.soundnest.feature.localfiles.ui.immersive


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
                    if (!io.github.thake.soundnest.feature.parental.settings.FeatureGates.shouldRequestAudioStoragePermission(context)) return@LocalEmpty
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
                val shown = tracks.orEmpty().filter { io.github.thake.soundnest.core.ui.look.matchesFind(find, it.title, it.artist, it.album) }
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
