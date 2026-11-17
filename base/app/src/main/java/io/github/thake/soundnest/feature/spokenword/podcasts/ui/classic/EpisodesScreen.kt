package io.github.thake.soundnest.feature.spokenword.podcasts.ui.classic

import io.github.thake.soundnest.feature.home.whatsnew.ui.classic.title

import io.github.thake.soundnest.core.ui.look.wideClickable
import io.github.thake.soundnest.core.ui.classic.tone
import io.github.thake.soundnest.core.ui.classic.AppText
import io.github.thake.soundnest.core.ui.classic.AppMuted

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.github.thake.soundnest.api.best
import io.github.thake.soundnest.R
import io.github.thake.soundnest.host.android.SoundNestRuntime
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import io.github.thake.soundnest.core.di.Graph
import io.github.thake.soundnest.core.playback.SongPlayer
import io.github.thake.soundnest.core.ui.navigation.albumRoute
import io.github.thake.soundnest.core.ui.navigation.episodeRoute
import io.github.thake.soundnest.core.ui.navigation.showRoute
import io.github.thake.soundnest.core.ui.classic.AppAccent

private val Background: Color get() = io.github.thake.soundnest.core.ui.classic.AppBackground
private val Subtle: androidx.compose.ui.graphics.Color get() = AppMuted


@Composable
private fun ScreenHeader(title: String, navController: NavController) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(8.dp, 12.dp, 16.dp, 8.dp),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.accessibility_navigate_back),
            tint = AppText,
            modifier = Modifier.wideClickable(horizontal = 8.dp, vertical = 10.dp) { navController.popBackStack() }.size(40.dp).padding(8.dp),
        )
        Text(title, fontWeight = FontWeight.Bold, color = AppText, fontSize = 22.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun Loading() = io.github.thake.soundnest.core.ui.classic.SkeletonList(count = 7)

/** "Your Episodes": saved podcast episodes, newest first, playable in place. */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun EpisodesScreen(navController: NavController) {
    val episodes by produceState<List<io.github.thake.soundnest.core.model.SongsModel>?>(null) { value = Graph.api.getSavedEpisodes() }
    var filter by remember { mutableStateOf(io.github.thake.soundnest.feature.spokenword.podcasts.settings.PodcastSettings.libraryFilter()) }
    Column(Modifier.fillMaxSize().background(Background).statusBarsPadding()) {
        ScreenHeader(stringResource(R.string.your_episodes_title), navController)
        when (val list = episodes) {
            null -> Loading()
            else -> if (list.isEmpty()) Text(stringResource(R.string.your_episodes_empty), color = Subtle, fontSize = 14.sp, modifier = Modifier.padding(20.dp))
            else LazyColumn(contentPadding = PaddingValues(bottom = io.github.thake.soundnest.core.ui.look.bottomBarsSpace())) {
                item(key = "filter") {
                    Text(
                        text = stringResource(io.github.thake.soundnest.feature.spokenword.ui.episodeFilterLabel(filter)),
                        color = if (filter == io.github.thake.soundnest.feature.spokenword.podcasts.settings.EpisodeFilter.ALL) AppText.copy(alpha = 0.8f) else AppAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clickable {
                                filter = filter.next()
                                io.github.thake.soundnest.feature.spokenword.podcasts.settings.PodcastSettings.setLibraryFilter(filter)
                            }
                            .padding(16.dp, 12.dp),
                    )
                }
                val shown = filter.apply(list) { it.played }
                if (shown.isEmpty()) item(key = "all-played") {
                    Text(stringResource(R.string.show_filter_all_played), color = Subtle, fontSize = 14.sp, modifier = Modifier.padding(20.dp))
                }
                items(shown, key = { it.url }) { e ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable {
                            SongPlayer.registerContext(list.map { it.url }, "spotify:collection:your-episodes")
                            SongPlayer.playSong(e.url)
                        }.padding(16.dp, 8.dp),
                    ) {
                        GlideImage(model = e.coverUri, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)))
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(e.title, color = AppText, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(e.singer + " • " + formatMinutes(e.durationMs / 60_000L), color = Subtle, fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            if (!e.played) {
                                Spacer(Modifier.height(4.dp))
                                io.github.thake.soundnest.feature.spokenword.ui.EpisodeProgressBar(e.playedPositionMs, e.durationMs, AppAccent, AppText.copy(alpha = 0.15f))
                            }
                        }
                        if (e.played) Icon(Icons.Default.Check, contentDescription = stringResource(R.string.episode_marked_played), tint = AppAccent, modifier = Modifier.padding(start = 8.dp).size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun formatMinutes(min: Long) = if (min >= 60) "${min / 60} h ${min % 60} min" else "$min min"
