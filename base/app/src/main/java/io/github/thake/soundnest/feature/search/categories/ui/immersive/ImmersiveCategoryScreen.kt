/*
 * Adapted from BitChord (https://github.com/kushagrasinghx/BitChord), GPL-3.0-or-later,
 * sharedUi/src/jvmSharedMain/kotlin/com/music/bitchord/ui/screens/ExploreScreen.kt
 * (MoodGenrePlaylistsScreen) at edc1e6f. Modified for Myspoti, 2026-10.
 */
package io.github.thake.soundnest.feature.search.categories.ui.immersive

import io.github.thake.soundnest.core.ui.immersive.shell.immersiveHazeSource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.catalog.Response
import io.github.thake.soundnest.core.model.LibraryEntry
import io.github.thake.soundnest.core.ui.look.pressHint
import io.github.thake.soundnest.core.ui.immersive.Immersive
import io.github.thake.soundnest.core.ui.immersive.components.ImmersiveMessageState
import io.github.thake.soundnest.core.ui.immersive.components.ImmersiveShelfCard
import io.github.thake.soundnest.core.ui.immersive.components.ImmersiveShimmerBox
import io.github.thake.soundnest.core.ui.immersive.shell.ImmersiveLargeTitle
import io.github.thake.soundnest.core.ui.immersive.shell.ImmersiveTopBar
import io.github.thake.soundnest.core.ui.immersive.shell.immersiveTopContentPadding
import io.github.thake.soundnest.feature.library.ui.ContextViewModel
import io.github.thake.soundnest.feature.search.categories.ui.CategoryViewModel
import io.github.thake.soundnest.feature.library.ui.immersive.gridCollapse
import io.github.thake.soundnest.feature.library.ui.immersive.immersiveBleed

/**
 * A browse category's page in the Immersive look: its name as the large title (with a back
 * button in the bar) over a two-column grid of the category's playlists and albums. The data,
 * routes and prefetch are those of the Classic Category screen.
 */
@Composable
fun ImmersiveCategoryScreen(navController: NavController, genre: String, title: String) {
    val viewModel: CategoryViewModel = viewModel()
    LaunchedEffect(genre) { viewModel.load(genre) }
    val playlists by viewModel.playlists.collectAsState()
    val gridState = rememberLazyGridState()
    val collapse = gridCollapse(gridState)

    Box(Modifier.fillMaxSize().background(Immersive.Page)) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(io.github.thake.soundnest.core.ui.look.windowSize().columns(2, 200.dp)),
            contentPadding = PaddingValues(
                start = Immersive.Gutter, end = Immersive.Gutter,
                top = immersiveTopContentPadding(), bottom = Immersive.BottomChrome,
            ),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxSize().immersiveHazeSource(),
        ) {
            item(key = "category:title", span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.immersiveBleed(Immersive.Gutter)) { ImmersiveLargeTitle(title) }
            }
            when (val state = playlists) {
                is Response.Loading -> items(6, key = { "category:skeleton:$it" }) {
                    ImmersiveShimmerBox(Modifier.fillMaxWidth().aspectRatio(1f), Immersive.CoverMedium)
                }
                is Response.Success -> {
                    if (state.data.isEmpty()) {
                        item(key = "category:empty", span = { GridItemSpan(maxLineSpan) }) {
                            ImmersiveMessageState(stringResource(R.string.nothing_here))
                        }
                    } else {
                        items(state.data) { entry ->
                            CategoryCard(entry, navController)
                        }
                    }
                }
                else -> item(key = "category:error", span = { GridItemSpan(maxLineSpan) }) {
                    ImmersiveMessageState(stringResource(R.string.couldnt_load, title))
                }
            }
        }
        ImmersiveTopBar(title, collapse, onBack = { navController.popBackStack() })
    }
}

@Composable
private fun CategoryCard(entry: LibraryEntry, navController: NavController) {
    ImmersiveShelfCard(
        title = entry.name,
        subtitle = entry.subtitle,
        imageUrl = entry.coverUri,
        onClick = {
            navController.navigate(io.github.thake.soundnest.feature.search.categories.ui.classic.categoryEntryRoute(entry))
        },
        modifier = Modifier
            .fillMaxWidth()
            .pressHint { if (entry.isPlaylist) ContextViewModel.prefetch("playlist", entry.spotifyId) },
    )
}
