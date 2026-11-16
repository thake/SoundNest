package io.github.thake.soundnest.radio.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.thake.soundnest.R
import io.github.thake.soundnest.radio.RadioFavoritesStore
import io.github.thake.soundnest.radio.RadioPlayer
import io.github.thake.soundnest.radio.RadioStation
import io.github.thake.soundnest.ui.immersive.Immersive
import io.github.thake.soundnest.ui.immersive.components.ImmersiveListRow
import io.github.thake.soundnest.ui.immersive.components.ImmersiveMessageState
import io.github.thake.soundnest.ui.immersive.components.ImmersiveSearchField
import io.github.thake.soundnest.ui.immersive.components.SectionHeader
import io.github.thake.soundnest.ui.immersive.components.immersiveClickable
import io.github.thake.soundnest.ui.immersive.components.immersiveSongListSkeleton
import io.github.thake.soundnest.ui.immersive.pages.ImmersivePage
import io.github.thake.soundnest.ui.immersive.screens.ImmersiveChip
import io.github.thake.soundnest.ui.immersive.screens.ImmersiveChipRow

/**
 * The Live Radio screen of the Immersive look, on the same [LiveRadioState] as the Classic
 * [LiveRadioScreen]: the page scaffold with its large title, the search capsule, the genre pills
 * and the stations as list rows, the favorites first while nothing is searched.
 */
@Composable
fun ImmersiveLiveRadioScreen(navController: NavController) {
    val state = rememberLiveRadioState()
    val favorites = RadioFavoritesStore.list()
    ImmersivePage(
        title = stringResource(R.string.live_radio_title),
        subtitle = stringResource(R.string.live_radio_subtitle),
        onBack = { navController.popBackStack() },
    ) {
        item(key = "radio:search") {
            ImmersiveSearchField(
                query = state.query, onQueryChange = { state.query = it }, onSubmit = {},
                placeholder = stringResource(R.string.live_radio_search_hint),
                modifier = Modifier.padding(horizontal = Immersive.Gutter, vertical = 6.dp),
            )
        }
        if (state.genres.isNotEmpty()) item(key = "radio:genres") {
            ImmersiveChipRow {
                state.genres.forEach { g -> ImmersiveChip(g, selected = g == state.genre, onClick = { state.pick(g) }) }
            }
        }
        if (favorites.isNotEmpty() && state.browsing) {
            item(key = "radio:favorites") { SectionHeader(stringResource(R.string.live_radio_favorites)) }
            items(favorites, key = { "fav:" + it.stationUuid }) { StationRow(it) }
        }
        item(key = "radio:section") {
            SectionHeader(
                when {
                    state.query.isNotBlank() -> stringResource(R.string.live_radio_search_results)
                    state.genre.isNotBlank() -> state.genre.replaceFirstChar { it.uppercase() }
                    else -> stringResource(R.string.live_radio_popular)
                },
            )
        }
        // Said even over the curated picks: what follows them is missing.
        if (state.error) item(key = "radio:error") { ImmersiveMessageState(stringResource(R.string.live_radio_error)) }
        val results = state.results
        when {
            state.loading && results == null -> immersiveSongListSkeleton(keyPrefix = "radio:skeleton")
            results?.isEmpty() == true && !state.error && state.unplayable == 0 -> item(key = "radio:empty") {
                ImmersiveMessageState(stringResource(R.string.live_radio_no_results))
            }
            else -> items(results.orEmpty(), key = { it.stationUuid }) { StationRow(it) }
        }
        if (state.unplayable > 0) item(key = "radio:unplayable") {
            ImmersiveMessageState(pluralStringResource(R.plurals.lib_radio_unplayable, state.unplayable, state.unplayable))
        }
    }
}

@Composable
private fun StationRow(station: RadioStation) {
    // The station LocalPlayer is on, by its synthetic song id, as the mini player shows it.
    val currentId by io.github.thake.soundnest.di.Graph.currentSongState.songId
    val current = currentId == RadioPlayer.songIdFor(station)
    val fav = RadioFavoritesStore.isFavorite(station.stationUuid)
    ImmersiveListRow(
        title = station.name.trim(),
        subtitle = stationSubtitle(station),
        imageUrl = station.favicon.ifBlank { null },
        round = false,
        onClick = { RadioPlayer.play(station) },
        playing = current && io.github.thake.soundnest.di.Graph.currentSongState.playingState.value,
        trailing = {
            Icon(
                if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = stringResource(if (fav) R.string.live_radio_unfavorite else R.string.live_radio_favorite),
                tint = if (fav) Immersive.Accent else Immersive.TextSecondary,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .immersiveClickable(onClick = { RadioFavoritesStore.toggle(station) }, pressedScale = 0.9f)
                    .padding(7.dp),
            )
        },
    )
}
