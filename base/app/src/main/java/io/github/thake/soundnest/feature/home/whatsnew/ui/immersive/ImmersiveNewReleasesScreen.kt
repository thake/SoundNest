package io.github.thake.soundnest.feature.home.whatsnew.ui.immersive


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


/** "New releases": what the followed artists just released, then the latest albums Spotify features. */
@Composable
fun ImmersiveNewReleasesScreen(navController: NavController) {
    val policy = io.github.thake.soundnest.feature.parental.settings.rememberParentalPolicy()
    val followed by produceState<List<ReleaseTile>?>(null, policy) {
        value = runCatching {
            SoundNestRuntime.api.home.whatsNew(0, 50).items
                .filter { io.github.thake.soundnest.feature.parental.filter.ParentalFilterEngine.isFollowedReleaseAllowed(it, policy) }
                .map { a ->
                    val formattedDate = a.releaseDate?.let { d -> io.github.thake.soundnest.feature.spokenword.ui.episodeDateLabel(d).ifBlank { d.take(10) } }
                    ReleaseTile(a.uri.substringAfterLast(':'), a.name, a.artists.joinToString(", ") { it.name },
                        a.images.best(300)?.url.orEmpty(), listOfNotNull(a.type.lowercase().replaceFirstChar { it.uppercase() }, formattedDate).joinToString(" • "),
                        a.uri.split(':').getOrElse(1) { "album" })
                }
        }.getOrDefault(emptyList())
    }
    val latest by produceState<List<ReleaseTile>?>(null, policy) {
        value = runCatching {
            Graph.api.newReleases().map { ReleaseTile(it.spotifyId, it.name, it.artists, it.coverUri, it.artists) }
        }.getOrDefault(emptyList())
    }
    // Large windows: more releases per row (two on phones).
    val cols = io.github.thake.soundnest.core.ui.look.windowSize().columns(2, 220.dp)
    ImmersivePage(title = stringResource(R.string.browse_new_releases), onBack = { navController.popBackStack() }) {
        fun section(key: String, title: Int, list: List<ReleaseTile>?) {
            item(key = "$key:header") { SectionHeader(stringResource(title)) }
            when {
                list == null -> immersiveSongListSkeleton(count = 4, keyPrefix = "$key:skeleton")
                list.isEmpty() -> item(key = "$key:empty") {
                    Text(stringResource(R.string.nothing_here), style = Immersive.Subhead, modifier = Modifier.padding(horizontal = Immersive.Gutter, vertical = 4.dp))
                }
                else -> items(list.chunked(cols), key = { row -> "$key:" + row.first().id }) { pair ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Immersive.Gutter).padding(bottom = 18.dp),
                    ) {
                        pair.forEach { t ->
                            ImmersiveShelfCard(
                                title = t.name, subtitle = t.detail.ifBlank { t.artists }, imageUrl = t.cover,
                                onClick = { navController.navigate(t.route()) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(cols - pair.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        section("followed", R.string.new_releases_followed, followed)
        section("latest", R.string.new_releases_latest, latest)
    }
}
