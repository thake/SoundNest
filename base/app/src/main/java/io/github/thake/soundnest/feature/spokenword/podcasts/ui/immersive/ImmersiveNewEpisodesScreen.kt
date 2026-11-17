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


/** "New episodes": the episodes not played yet of the followed shows, newest first; a tap opens the episode. */
@Composable
fun ImmersiveNewEpisodesScreen(navController: NavController) {
    val episodes by produceState<List<io.github.thake.soundnest.core.model.SongsModel>?>(null) { value = Graph.api.getNewEpisodes() }
    ImmersivePage(title = stringResource(R.string.new_episodes_title), onBack = { navController.popBackStack() }) {
        val list = episodes
        when {
            list == null -> immersiveSongListSkeleton(count = 7)
            list.isEmpty() -> item(key = "empty") { ImmersiveMessageState(stringResource(R.string.new_episodes_empty)) }
            else -> items(list, key = { it.url }) { e ->
                Column(immersiveItemPlacement()) {
                    val dateLabel = io.github.thake.soundnest.feature.spokenword.ui.episodeDateLabel(e.releaseDate).ifBlank { e.releaseDate.take(10) }
                    ImmersiveListRow(
                        title = e.title,
                        subtitle = e.singer + " • " + dateLabel,
                        imageUrl = e.coverUri,
                        round = false,
                        onClick = { navController.navigate(io.github.thake.soundnest.core.ui.navigation.episodeRoute(e.url.removePrefix("episode:"), e.title)) },
                    )
                }
            }
        }
    }
}
