package io.github.thake.soundnest.feature.recap.ui.classic

import io.github.thake.soundnest.core.ui.look.wideClickable
import io.github.thake.soundnest.core.ui.classic.tone
import io.github.thake.soundnest.core.ui.classic.AppText
import io.github.thake.soundnest.core.ui.classic.AppMuted

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import io.github.thake.soundnest.R
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import io.github.thake.soundnest.core.ui.classic.AppAccent
import io.github.thake.soundnest.feature.recap.data.ListenHistory
import io.github.thake.soundnest.feature.recap.ui.SpotifyStatsTab
import io.github.thake.soundnest.feature.recap.ui.StatsTabs
import io.github.thake.soundnest.feature.recap.ui.WeeklyMinutesChart

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
private fun SectionTitle(text: String) =
    Text(text, color = AppText, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp, 18.dp, 16.dp, 8.dp))

/**
 * The in-app recap: listens on this phone from Spotify and local files. Spotify's own
 * Wrapped only counts what Spotify streamed; this one counts everything, and stays on the phone.
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun RecapScreen(navController: NavController, initialTab: Int = 0) {
    val periods = listOf(R.string.recap_month to 30L, R.string.recap_year to 365L, R.string.recap_all to 36_500L)
    var period by remember { mutableStateOf(1) }
    // Off the main thread: the history file grows with every listen, and reading
    // and ranking it during composition janked the screen open.
    val loaded by produceState<ListenHistory.Recap?>(null, period) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            ListenHistory.recap(System.currentTimeMillis() - periods[period].second * 86_400_000L)
        }
    }
    val recap = loaded ?: ListenHistory.Recap(0, 0, emptyMap(), emptyList(), emptyList(), emptyList())
    // Spotify's ranking of the account first (the official "Listening stats"), then this phone's counter.
    var tab by androidx.compose.runtime.saveable.rememberSaveable(initialTab) { mutableStateOf(initialTab) }
    Column(Modifier.fillMaxSize().background(Background).statusBarsPadding()) {
        ScreenHeader(stringResource(R.string.recap_title), navController)
        StatsTabs(tab) { tab = it }
        if (tab == 0) SpotifyStatsTab(navController) else LazyColumn(contentPadding = PaddingValues(bottom = io.github.thake.soundnest.core.ui.look.bottomBarsSpace())) {
            item {
                Row(Modifier.padding(16.dp, 12.dp, 16.dp, 0.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    periods.forEachIndexed { i, (label, _) ->
                        FilterChip(selected = i == period, onClick = { period = i }, label = { Text(stringResource(label)) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AppAccent, selectedLabelColor = Color.Black,
                                labelColor = AppText, containerColor = tone(0xFF2A2A2A)))
                    }
                }
            }
            item {
                Column(Modifier.padding(16.dp, 20.dp)) {
                    Text(stringResource(R.string.recap_minutes, recap.minutes), color = AppText, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.recap_listens, recap.listens), color = Subtle, fontSize = 14.sp)
                    Spacer(Modifier.height(10.dp))
                    val src = recap.bySource
                    Text(stringResource(R.string.recap_sources_soundnest,
                        src[ListenHistory.Source.SPOTIFY] ?: 0, src[ListenHistory.Source.LOCAL] ?: 0),
                        color = Subtle, fontSize = 13.sp)
                }
            }
            item { WeeklyMinutesChart() }
            if (recap.listens == 0) item {
                Text(stringResource(R.string.recap_empty), color = Subtle, fontSize = 14.sp, modifier = Modifier.padding(16.dp))
            }
            fun ranked(title: Int, list: List<ListenHistory.Ranked>) {
                if (list.isEmpty()) return
                item { SectionTitle(stringResource(title)) }
                items(list.size) { i ->
                    val r = list[i]
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(16.dp, 6.dp)) {
                        Text("${i + 1}", color = Subtle, fontSize = 16.sp, modifier = Modifier.width(28.dp))
                        GlideImage(model = r.cover, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)))
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(r.name, color = AppText, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(listOf(r.subtitle, stringResource(R.string.recap_plays, r.count)).filter { it.isNotBlank() }.joinToString(" • "),
                                color = Subtle, fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            ranked(R.string.recap_top_tracks, recap.topTracks)
            ranked(R.string.recap_top_artists, recap.topArtists)
            ranked(R.string.recap_top_albums, recap.topAlbums)
            item {
                Text(stringResource(R.string.recap_private_note_soundnest), color = Color(0xFF7A7A7A), fontSize = 12.sp, modifier = Modifier.padding(16.dp, 24.dp))
            }
        }
    }
}
