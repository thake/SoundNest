package io.github.thake.soundnest.feature.spokenword.podcasts.ui.immersive


import io.github.thake.soundnest.core.ui.immersive.pages.ImmersivePage
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.navigation.NavController
import io.github.thake.soundnest.api.TopContent
import io.github.thake.soundnest.api.best
import io.github.thake.soundnest.R
import io.github.thake.soundnest.host.android.SoundNestRuntime
import io.github.thake.soundnest.core.di.Graph
import io.github.thake.soundnest.core.playback.SongPlayer
import io.github.thake.soundnest.core.ui.look.spotifyImageUrl
import io.github.thake.soundnest.core.ui.immersive.Immersive
import io.github.thake.soundnest.core.ui.immersive.components.ImmersiveListRow
import io.github.thake.soundnest.core.ui.immersive.components.ImmersiveMessageState
import io.github.thake.soundnest.core.ui.immersive.components.ImmersiveShelfCard
import io.github.thake.soundnest.core.ui.immersive.components.immersiveSongListSkeleton
import io.github.thake.soundnest.core.ui.immersive.components.immersiveClickable
import io.github.thake.soundnest.core.ui.immersive.motion.immersiveItemPlacement
import io.github.thake.soundnest.core.ui.immersive.components.SectionHeader
import io.github.thake.soundnest.core.ui.navigation.artistRoute
import io.github.thake.soundnest.feature.home.whatsnew.ui.classic.ReleaseTile
import io.github.thake.soundnest.feature.home.whatsnew.ui.classic.route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// The pages of the Discover group in the Immersive look: new releases, saved episodes and the
// listening recap. They read the same sources as the Classic ones in DiscoverScreens.kt and
// StatsScreens.kt (the web API's what's-new, the saved episodes, ListenHistory, TopStats).


/** "Your Episodes": saved podcast episodes, newest first, playable in place. */
@Composable
fun ImmersiveEpisodesScreen(navController: NavController) {
    val episodes by produceState<List<io.github.thake.soundnest.core.model.SongsModel>?>(null) { value = Graph.api.getSavedEpisodes() }
    var filter by remember { mutableStateOf(io.github.thake.soundnest.feature.spokenword.podcasts.settings.PodcastSettings.libraryFilter()) }
    ImmersivePage(title = stringResource(R.string.your_episodes_title), onBack = { navController.popBackStack() }) {
        val list = episodes
        when {
            list == null -> immersiveSongListSkeleton(count = 7)
            list.isEmpty() -> item(key = "empty") { ImmersiveMessageState(stringResource(R.string.your_episodes_empty)) }
            else -> {
                item(key = "filter") {
                    Text(
                        text = stringResource(io.github.thake.soundnest.feature.spokenword.ui.episodeFilterLabel(filter)),
                        style = Immersive.Subhead.copy(
                            color = if (filter == io.github.thake.soundnest.feature.spokenword.podcasts.settings.EpisodeFilter.ALL) Immersive.TextSecondary else Immersive.Accent,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                        ),
                        modifier = Modifier
                            .immersiveClickable(onClick = {
                                filter = filter.next()
                                io.github.thake.soundnest.feature.spokenword.podcasts.settings.PodcastSettings.setLibraryFilter(filter)
                            }, pressedScale = 0.96f)
                            .padding(horizontal = Immersive.Gutter, vertical = 12.dp),
                    )
                }
                val shown = filter.apply(list) { it.played }
                if (shown.isEmpty()) item(key = "all-played") { ImmersiveMessageState(stringResource(R.string.show_filter_all_played)) }
                items(shown, key = { it.url }) { e ->
                Column(immersiveItemPlacement()) {
                    ImmersiveListRow(
                        title = e.title,
                        subtitle = e.singer + " • " + episodeMinutesLabel(e.durationMs / 60_000L) +
                            if (!e.played && e.playedPositionMs > 0) " • " + stringResource(R.string.episode_remaining, io.github.thake.soundnest.feature.spokenword.ui.episodeClock(e.durationMs - e.playedPositionMs)) else "",
                        imageUrl = e.coverUri,
                        round = false,
                        trailing = if (e.played) ({
                            androidx.compose.material3.Icon(
                                androidx.compose.material.icons.Icons.Rounded.Check,
                                contentDescription = stringResource(R.string.episode_marked_played),
                                tint = Immersive.Accent,
                                modifier = Modifier.size(20.dp),
                            )
                        }) else null,
                        onClick = {
                            SongPlayer.registerContext(list.map { it.url }, "spotify:collection:your-episodes")
                            SongPlayer.playSong(e.url)
                        },
                    )
                }
                }
            }
        }
    }
}

private fun episodeMinutesLabel(min: Long) = if (min >= 60) "${min / 60} h ${min % 60} min" else "$min min"
