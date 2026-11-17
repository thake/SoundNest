package io.github.thake.soundnest.feature.home.whatsnew.ui.classic

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

@Composable
private fun SectionTitle(text: String) =
    Text(text, color = AppText, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp, 18.dp, 16.dp, 8.dp))

/** [kind] is the Spotify uri type ("album", "show" or "episode"): the what's-new feed also carries followed podcasts. */
internal data class ReleaseTile(val id: String, val name: String, val artists: String, val cover: String, val detail: String, val kind: String = "album")

/** The page a tile opens: a show and an episode have their own pages, an album tile opens the album. */
internal fun ReleaseTile.route(): String = when (kind) {
    "show" -> showRoute(id, name)
    "episode" -> episodeRoute(id, name)
    else -> albumRoute(name, artists, id)
}

/** "New releases": what the followed artists just released, then the latest albums Spotify features. */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun NewReleasesScreen(navController: NavController) {
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
    Column(Modifier.fillMaxSize().background(Background).statusBarsPadding()) {
        ScreenHeader(stringResource(R.string.browse_new_releases), navController)
        LazyVerticalGrid(
            columns = GridCells.Fixed(io.github.thake.soundnest.core.ui.look.windowSize().columns(2, 200.dp)),
            contentPadding = PaddingValues(12.dp, 0.dp, 12.dp, io.github.thake.soundnest.core.ui.look.bottomBarsSpace()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            fun section(title: Int, list: List<ReleaseTile>?) {
                item(span = { GridItemSpan(2) }) { SectionTitle(stringResource(title)) }
                when {
                    list == null -> item(span = { GridItemSpan(2) }) { Loading() }
                    list.isEmpty() -> item(span = { GridItemSpan(2) }) {
                        Text(stringResource(R.string.nothing_here), color = Subtle, fontSize = 14.sp, modifier = Modifier.padding(16.dp, 4.dp))
                    }
                    else -> items(list, key = { "$title:${it.id}" }) { t ->
                        Column(Modifier.clickable { navController.navigate(t.route()) }) {
                            GlideImage(model = t.cover, contentDescription = null, contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(6.dp)))
                            Text(t.name, color = AppText, fontSize = 13.sp, fontWeight = FontWeight.Bold, lineHeight = 16.sp, minLines = 2, maxLines = 2,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                            Text(t.detail.ifBlank { t.artists }, color = AppMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            section(R.string.new_releases_followed, followed)
            section(R.string.new_releases_latest, latest)
        }
    }
}
