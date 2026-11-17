package io.github.thake.soundnest.feature.recap.ui.immersive

import io.github.thake.soundnest.core.ui.immersive.pages.ImmersivePage
import io.github.thake.soundnest.core.ui.immersive.pages.ImmersiveSegmentedControl
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.navigation.NavController
import io.github.thake.soundnest.api.TopContent
import io.github.thake.soundnest.api.best
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.playback.SongPlayer
import io.github.thake.soundnest.core.ui.look.spotifyImageUrl
import io.github.thake.soundnest.core.ui.immersive.Immersive
import io.github.thake.soundnest.core.ui.immersive.components.ImmersiveListRow
import io.github.thake.soundnest.core.ui.immersive.components.ImmersiveMessageState
import io.github.thake.soundnest.core.ui.immersive.components.ImmersiveShelfCard
import io.github.thake.soundnest.core.ui.immersive.components.SectionHeader
import io.github.thake.soundnest.core.ui.navigation.artistRoute
import io.github.thake.soundnest.feature.recap.data.ListenHistory
import io.github.thake.soundnest.feature.recap.data.TopStats
import io.github.thake.soundnest.feature.recap.ui.rangeLabels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// The pages of the Discover group in the Immersive look: new releases, saved episodes and the
// listening recap. They read the same sources as the Classic ones in DiscoverScreens.kt and
// StatsScreens.kt (the web API's what's-new, the saved episodes, ListenHistory, TopStats).


/**
 * The in-app recap: Spotify's ranking of the account first, then what was played on this phone
 * (Spotify and local files). Same counters as the Classic `RecapScreen`.
 */
@Composable
fun ImmersiveRecapScreen(navController: NavController, initialTab: Int = 0) {
    val periods = listOf(R.string.recap_month to 30L, R.string.recap_year to 365L, R.string.recap_all to 36_500L)
    var period by rememberSaveable { mutableIntStateOf(1) }
    var tab by rememberSaveable(initialTab) { mutableIntStateOf(initialTab) }
    // Off the main thread: the history file grows with every listen.
    val loaded by produceState<ListenHistory.Recap?>(null, period) {
        value = withContext(Dispatchers.IO) { ListenHistory.recap(System.currentTimeMillis() - periods[period].second * 86_400_000L) }
    }
    val recap = loaded ?: ListenHistory.Recap(0, 0, emptyMap(), emptyList(), emptyList(), emptyList())
    ImmersivePage(title = stringResource(R.string.recap_title), onBack = { navController.popBackStack() }) {
        item(key = "tabs") {
            ImmersiveSegmentedControl(
                options = listOf(stringResource(R.string.stats_tab_spotify), stringResource(R.string.stats_tab_phone)),
                selectedIndex = tab, onSelect = { tab = it },
                modifier = Modifier.padding(horizontal = Immersive.Gutter, vertical = 6.dp),
            )
        }
        if (tab == 0) spotifyStats(navController) else phoneStats(navController, recap, periods.map { it.first }, period) { period = it }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.spotifyStats(navController: NavController) {
    item(key = "spotify") { SpotifyStatsBlock(navController) }
}

@Composable
private fun SpotifyStatsBlock(navController: NavController) {
    val policy = io.github.thake.soundnest.feature.parental.settings.rememberParentalPolicy()
    var range by rememberSaveable { mutableIntStateOf(0) }
    var attempt by remember { mutableIntStateOf(0) }
    val timeRange = rangeLabels[range].first
    val state by produceState<Result<TopContent>?>(TopStats.cached(timeRange)?.let { Result.success(it) }, timeRange, attempt, policy) {
        value = runCatching { TopStats.get(timeRange, force = attempt > 0) }
    }
    val top = state?.getOrNull()
    Column {
        ImmersiveSegmentedControl(
            options = rangeLabels.map { stringResource(it.second) },
            selectedIndex = range, onSelect = { range = it },
            modifier = Modifier.padding(horizontal = Immersive.Gutter, vertical = 6.dp),
        )
        when {
            state == null -> repeat(5) { io.github.thake.soundnest.core.ui.immersive.components.ImmersiveSongRowSkeleton(index = it) }
            top == null -> ImmersiveMessageState(stringResource(R.string.stats_failed), actionLabel = stringResource(R.string.retry_button), onAction = { attempt++ })
            top.artists.isEmpty() && top.tracks.isEmpty() -> ImmersiveMessageState(stringResource(R.string.stats_empty))
            else -> {
                top.artistTotal?.takeIf { it > 0 }?.let { total ->
                    Text(stringResource(R.string.stats_artists_ranked, total), style = Immersive.Subhead,
                        modifier = Modifier.padding(horizontal = Immersive.Gutter, vertical = 10.dp))
                }
                if (top.artists.isNotEmpty()) {
                    SectionHeader(stringResource(R.string.recap_top_artists))
                    LazyRow(contentPadding = PaddingValues(horizontal = Immersive.Gutter), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        itemsIndexed(top.artists, key = { _, a -> a.uri }) { i, a ->
                            ImmersiveShelfCard(
                                title = "${i + 1}. ${a.name}", subtitle = null,
                                imageUrl = spotifyImageUrl(a.images.best(300)?.url), round = true,
                                onClick = { navController.navigate(artistRoute(a.name, a.uri.substringAfterLast(':'))) },
                                modifier = Modifier.width(120.dp),
                            )
                        }
                    }
                }
                if (top.tracks.isNotEmpty()) {
                    SectionHeader(stringResource(R.string.recap_top_tracks))
                    top.tracks.forEachIndexed { i, t ->
                        ImmersiveListRow(
                            title = "${i + 1}  ${t.name}",
                            subtitle = t.artists.joinToString(", ") { it.name },
                            imageUrl = spotifyImageUrl(t.album?.images?.best(160)?.url),
                            round = false,
                            onClick = { SongPlayer.playSong(t.uri) },
                        )
                    }
                }
            }
        }
        Text(stringResource(R.string.stats_spotify_note), style = Immersive.Caption.copy(color = Immersive.TextTertiary),
            modifier = Modifier.padding(Immersive.Gutter, 24.dp))
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.phoneStats(
    navController: NavController,
    recap: ListenHistory.Recap,
    periodLabels: List<Int>,
    period: Int,
    onPeriod: (Int) -> Unit,
) {
    item(key = "period") {
        ImmersiveSegmentedControl(
            options = periodLabels.map { stringResource(it) },
            selectedIndex = period, onSelect = onPeriod,
            modifier = Modifier.padding(horizontal = Immersive.Gutter, vertical = 6.dp),
        )
    }
    item(key = "totals") {
        Column(Modifier.padding(horizontal = Immersive.Gutter, vertical = 16.dp)) {
            Text(stringResource(R.string.recap_minutes, recap.minutes), style = Immersive.LargeTitle)
            Text(stringResource(R.string.recap_listens, recap.listens), style = Immersive.Subhead)
            val src = recap.bySource
            Text(
                stringResource(R.string.recap_sources_soundnest, src[ListenHistory.Source.SPOTIFY] ?: 0, src[ListenHistory.Source.LOCAL] ?: 0),
                style = Immersive.Caption, modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
    item(key = "weekly") { ImmersiveWeeklyChart() }
    if (recap.listens == 0) item(key = "empty") { ImmersiveMessageState(stringResource(R.string.recap_empty)) }
    fun ranked(key: String, title: Int, list: List<ListenHistory.Ranked>) {
        if (list.isEmpty()) return
        item(key = "$key:header") { SectionHeader(stringResource(title)) }
        itemsIndexed(list, key = { i, _ -> "$key:$i" }) { i, r ->
            ImmersiveListRow(
                title = "${i + 1}  ${r.name}",
                subtitle = listOf(r.subtitle, stringResource(R.string.recap_plays, r.count)).filter { it.isNotBlank() }.joinToString(" • "),
                imageUrl = r.cover, round = false, onClick = {},
            )
        }
    }
    ranked("tracks", R.string.recap_top_tracks, recap.topTracks)
    ranked("artists", R.string.recap_top_artists, recap.topArtists)
    ranked("albums", R.string.recap_top_albums, recap.topAlbums)
    item(key = "note") {
        Text(stringResource(R.string.recap_private_note_soundnest), style = Immersive.Caption.copy(color = Immersive.TextTertiary),
            modifier = Modifier.padding(Immersive.Gutter, 24.dp))
    }
}

/** Minutes played per calendar week on this phone, the current week last and in the accent. */
@Composable
private fun ImmersiveWeeklyChart() {
    val weeks by produceState<List<ListenHistory.Week>?>(null) { value = withContext(Dispatchers.IO) { ListenHistory.weekly(8) } }
    val w = weeks ?: return
    if (w.all { it.minutes == 0L }) return
    val max = w.maxOf { it.minutes }.coerceAtLeast(1)
    val pattern = android.text.format.DateFormat.getBestDateTimePattern(java.util.Locale.getDefault(), "dMMM")
    val format = remember(pattern) { java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault()) }
    Column(Modifier.padding(horizontal = Immersive.Gutter, vertical = 8.dp).clip(Immersive.Card).background(Immersive.Raised).padding(16.dp)) {
        Text(stringResource(R.string.stats_weekly_minutes), style = Immersive.Headline)
        Text(stringResource(R.string.stats_this_week, w.last().minutes), style = Immersive.Caption, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
        Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            w.forEachIndexed { i, week ->
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                    if (week.minutes > 0) Text("${week.minutes}", style = Immersive.Caption, maxLines = 1)
                    Box(
                        Modifier.fillMaxWidth()
                            .height((96f * week.minutes / max).coerceAtLeast(2f).dp)
                            .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                            .background(if (i == w.lastIndex) Immersive.Accent else Immersive.Text.copy(alpha = 0.22f)),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            w.forEach { week ->
                Text(format.format(java.util.Date(week.startMs)), style = Immersive.Caption.copy(color = Immersive.TextTertiary), maxLines = 1,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
    }
}
