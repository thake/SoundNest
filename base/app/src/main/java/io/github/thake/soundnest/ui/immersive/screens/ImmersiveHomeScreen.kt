/*
 * Adapted from BitChord (https://github.com/kushagrasinghx/BitChord), GPL-3.0-or-later,
 * sharedUi/src/jvmSharedMain/kotlin/com/music/bitchord/ui/screens/HomeScreen.kt at edc1e6f.
 * Modified for Myspoti, 2026-10.
 */
package io.github.thake.soundnest.ui.immersive.screens

import io.github.thake.soundnest.ui.immersive.shell.immersiveHazeSource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import io.github.thake.soundnest.ui.components.arrivingRow
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.github.thake.soundnest.R
import io.github.thake.soundnest.data.api.Features
import io.github.thake.soundnest.data.api.ProfileCache
import io.github.thake.soundnest.data.api.Response
import io.github.thake.soundnest.data.entity.HomeItem
import io.github.thake.soundnest.data.entity.HomeSection
import io.github.thake.soundnest.data.entity.homeShortcuts
import io.github.thake.soundnest.data.entity.withoutDj
import io.github.thake.soundnest.data.entity.withoutPodcastShows
import io.github.thake.soundnest.di.Graph
import io.github.thake.soundnest.di.SongPlayer
import io.github.thake.soundnest.ui.components.onHomeItemClick
import io.github.thake.soundnest.ui.components.prefetchHomeItem
import io.github.thake.soundnest.ui.components.prefetchHomeShortcuts
import io.github.thake.soundnest.ui.components.pressHint
import io.github.thake.soundnest.ui.immersive.Immersive
import io.github.thake.soundnest.ui.immersive.components.ImmersiveCover
import io.github.thake.soundnest.ui.immersive.components.ImmersiveLargeAlbumCard
import io.github.thake.soundnest.ui.immersive.components.ImmersiveMessageState
import io.github.thake.soundnest.ui.immersive.components.ImmersiveShelf
import io.github.thake.soundnest.ui.immersive.components.ImmersiveShelfCard
import io.github.thake.soundnest.ui.immersive.components.ImmersiveShelfRow
import io.github.thake.soundnest.ui.immersive.components.SectionHeader
import io.github.thake.soundnest.ui.immersive.components.immersiveClickable
import io.github.thake.soundnest.ui.immersive.components.immersiveFeedSkeleton
import io.github.thake.soundnest.ui.immersive.components.immersiveHeroCardWidth
import io.github.thake.soundnest.ui.immersive.shell.ImmersiveFloatingHeader
import io.github.thake.soundnest.ui.immersive.shell.ImmersiveHeaderContentHeight
import io.github.thake.soundnest.ui.immersive.shell.LocalBarControlSize
import io.github.thake.soundnest.ui.immersive.shell.immersiveChromeScroll
import io.github.thake.soundnest.ui.immersive.shell.immersiveHeaderContentPadding
import io.github.thake.soundnest.ui.immersive.shell.rememberTopBarCollapse
import io.github.thake.soundnest.ui.navigation.Routes
import io.github.thake.soundnest.ui.navigation.TabReselect
import io.github.thake.soundnest.ui.viewmodel.HomeViewModel

/**
 * Home in the Immersive look: the greeting as the page's large title with the profile disc at its
 * end, the facet pills, then the feed — the first carousel as big cards, the rest as shelves. The
 * data, clicks, prefetch and profile actions are those of the Classic Home screen.
 */
@Composable
fun ImmersiveHomeScreen(navController: NavController) {
    val homeViewModel: HomeViewModel = viewModel()
    val home by homeViewModel.home.collectAsState()
    val feed = (home as? Response.Success)?.data
    val facet = homeViewModel.facet.value
    val context = LocalContext.current
    LaunchedEffect(Unit) { ProfileCache.ensure(context) }
    // An old feed is read again when Home shows again (see HomeViewModel.refreshIfStale).
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { homeViewModel.refreshIfStale() }

    // Re-tapping the Home tab while on Home scrolls back to the top, as in the Classic screen.
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        TabReselect.home.collect {
            if (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0) {
                listState.animateScrollToItem(0)
            }
        }
    }

    val homeTitle = stringResource(R.string.navigationbar_musicappitems_home_title)
    val title = feed?.greeting?.takeIf { it.isNotBlank() } ?: homeTitle

    // The context playing right now (empty while paused), derived once for every card: a card
    // only recomposes when its own answer flips, not on every change of the player's state.
    val playingContext = remember {
        derivedStateOf { if (Graph.currentSongState.playingState.value) SongPlayer.nowPlaying.value.contextUri else "" }
    }

    LaunchedEffect(Unit) { io.github.thake.soundnest.parental.ParentalPolicyStore.current(context) }
    val policy by io.github.thake.soundnest.parental.ParentalPolicyStore.policyFlow.collectAsState()
    val revision by io.github.thake.soundnest.parental.ParentalPolicyStore.revision.collectAsState()
    val sessionTick by io.github.thake.soundnest.parental.ParentSecurityManager.sessionTick.collectAsState()
    val adminUnlocked = remember(sessionTick) { io.github.thake.soundnest.parental.ParentSecurityManager.isAdminUnlocked(context) }
    var quickBlockTarget by remember { mutableStateOf<io.github.thake.soundnest.parental.ParentBlockTarget?>(null) }
    quickBlockTarget?.let { target ->
        io.github.thake.soundnest.parental.ParentQuickBlockSheet(
            target = target,
            onDismiss = { quickBlockTarget = null },
        )
    }

    val rawGridSection = remember(feed) { feed?.sections.orEmpty().firstOrNull()?.takeIf { it.title.isBlank() } }
    val rawCarousels = remember(feed, rawGridSection) {
        val all = feed?.sections.orEmpty()
        if (rawGridSection != null) all.drop(1) else all
    }
    val carousels = remember(rawCarousels, policy, revision) {
        io.github.thake.soundnest.parental.HomeSectionFilter.filterSections(
            sections = rawCarousels,
            policy = policy,
            parentPicksCandidates = io.github.thake.soundnest.parental.HomeSectionFilter.cachedLibraryCandidates(),
        )
    }
    // Liked Songs leads the shortcuts when Spotify's grid does not carry it.
    val likedName = stringResource(R.string.collection_liked_songs_title)
    val shortcuts = remember(rawGridSection, feed, policy, revision, facet, likedName) {
        if (!policy.homeSections.showShortcutsGrid || feed == null) null
        else {
            val filteredGrid = io.github.thake.soundnest.parental.HomeSectionFilter.filterShortcutItems(rawGridSection?.items ?: emptyList(), policy)
            val emptyFeed = feed.sections.isEmpty() && !policy.homeSections.minimalHomeMode && policy.filterMode != io.github.thake.soundnest.parental.FilterMode.STRICT_ALLOWLIST
            homeShortcuts(filteredGrid, emptyFeed, facet, likedName)
        }
    }
    // The shortcuts at the top load in the background, ready before a tap.
    LaunchedEffect(shortcuts) { prefetchHomeShortcuts(shortcuts) }

    val showChips = !policy.homeSections.minimalHomeMode && policy.homeSections.showFilterChips
    val showBell = io.github.thake.soundnest.parental.HomeSectionFilter.isWhatsNewAllowed(policy)

    Box(Modifier.fillMaxSize().background(Immersive.Page)) {
        LazyColumn(
            state = listState,
            // The greeting is the floating header's own title, so the feed starts right under it
            // (it used to be a large title under an empty bar row: the gap at the top of Home).
            contentPadding = PaddingValues(top = immersiveHeaderContentPadding(), bottom = Immersive.BottomChrome),
            modifier = Modifier.fillMaxSize().immersiveHazeSource().immersiveChromeScroll(),
        ) {
            if (showChips) {
                item(key = "home:facets") {
                    ImmersiveHomeFacets(
                        facet,
                        labels = mapOf(
                            "" to stringResource(R.string.filter_chip_all_label),
                            "music-chip" to stringResource(R.string.recents_content_filter_music),
                            "podcasts-chip" to stringResource(R.string.ad_category_title_podcasts),
                            "audiobooks-chip" to stringResource(R.string.audiobooks_title),
                        ),
                        podcasts = policy.allowPodcasts,
                        audiobooks = policy.allowAudiobooks,
                        select = homeViewModel::selectFacet,
                    )
                }
            }
            when {
                feed != null && (shortcuts != null || carousels.isNotEmpty()) -> {
                    shortcuts?.let { tiles ->
                        item(key = "home:shortcuts") {
                            // The feed fades in over its placeholder (see immersiveFeedSkeleton).
                            Box(arrivingRow()) {
                                ShortcutGrid(
                                    navController = navController,
                                    items = tiles,
                                    playingContext = playingContext,
                                    adminUnlocked = adminUnlocked,
                                    onQuickBlock = { quickBlockTarget = it },
                                )
                            }
                        }
                    }
                    carousels.forEachIndexed { index, section ->
                        item(key = "home:section:$index:${section.uri.ifBlank { section.title }}") {
                            // The lead carousel gets the big cards, unless it is a row of artists.
                            val hero = index == 0 && section.items.none { it is HomeItem.Artist }
                            Box(arrivingRow()) {
                                if (hero) HeroSection(
                                    navController = navController,
                                    section = section,
                                    playingContext = playingContext,
                                    adminUnlocked = adminUnlocked,
                                    onQuickBlock = { quickBlockTarget = it },
                                )
                                else ShelfSection(
                                    navController = navController,
                                    section = section,
                                    playingContext = playingContext,
                                    adminUnlocked = adminUnlocked,
                                    onQuickBlock = { quickBlockTarget = it },
                                )
                            }
                        }
                    }
                }
                home is Response.Loading -> immersiveFeedSkeleton(firstIsHero = true)
                else -> item(key = "home:message") {
                    ImmersiveMessageState(
                        message = stringResource(if (home is Response.Error) R.string.home_error else R.string.home_empty),
                        actionLabel = if (home is Response.Error) stringResource(R.string.home_retry) else null,
                        onAction = if (home is Response.Error) ({ homeViewModel.retry() }) else null,
                    )
                }
            }
        }
        // The top-left menu avatar, greeting, and What's New bell on floating glass; the feed scrolls under it.
        ImmersiveFloatingHeader(
            title = title,
            collapse = rememberTopBarCollapse(listState, 24.dp),
            leading = { io.github.thake.soundnest.ui.screens.ImmersiveMenuAvatarButton() },
        ) {
            val size = LocalBarControlSize.current
            if (showBell) {
                WhatsNewBell(size) { navController.navigate(Routes.WhatsNew.route) }
            }
        }
    }
}

/** The bell left of the profile disc: opens "What's New". Its tap area is the header's full height, as the disc's is. */
@Composable
private fun WhatsNewBell(size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Box(
        Modifier
            .height(ImmersiveHeaderContentHeight)
            .width(size)
            .immersiveClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            androidx.compose.material.icons.Icons.Rounded.NotificationsNone,
            contentDescription = stringResource(R.string.whats_new_title),
            tint = Immersive.Text,
            modifier = Modifier.size(size * 0.55f),
        )
    }
}

/** The uri of the thing this card opens, to tell whether it is what is playing. */
private fun HomeItem.contextUri(): String = when (this) {
    is HomeItem.Album -> if (id.isNotBlank()) "spotify:album:$id" else ""
    is HomeItem.Playlist -> if (id.isNotBlank()) "spotify:playlist:$id" else ""
    is HomeItem.Artist -> if (id.isNotBlank()) "spotify:artist:$id" else ""
    is HomeItem.Show -> when {
        id.isBlank() || isEpisode -> ""
        io.github.thake.soundnest.audible.AudibleUris.isAudibleUri(id) ->
            io.github.thake.soundnest.audible.AudibleUris.asinFromAny(id)?.let(io.github.thake.soundnest.audible.AudibleUris::bookUri).orEmpty()
        else -> "spotify:show:$id"
    }
}

/** True while this card's album, playlist or artist is the playing context, [playingContext]. */
private fun HomeItem.isPlayingIn(playingContext: String): Boolean {
    val uri = contextUri()
    return uri.isNotEmpty() && playingContext == uri
}

/**
 * Keys for the cards of a shelf: the uri they open, or their kind and name for those with none
 * (an episode). A repeat within one shelf gets a suffix, as a lazy list refuses two equal keys.
 */
private fun homeItemKeys(items: List<HomeItem>): List<String> {
    val seen = HashMap<String, Int>()
    return items.map { item ->
        val base = item.contextUri().ifEmpty { "${item::class.simpleName}:${item.name}:${item.imageUrl}" }
        val count = seen.merge(base, 1, Int::plus) ?: 1
        if (count == 1) base else "$base#$count"
    }
}

@Composable
private fun HomeItem.cardSubtitle(): String = when (this) {
    is HomeItem.Album -> subtitle
    is HomeItem.Playlist -> subtitle
    is HomeItem.Artist -> stringResource(R.string.artist_subtitle)
    is HomeItem.Show -> subtitle
}

@Composable
private fun HideShelfButton(section: HomeSection) {
    val context = LocalContext.current
    androidx.compose.material3.IconButton(onClick = {
        val key = io.github.thake.soundnest.parental.HomeSectionFilter.shelfKey(section)
        io.github.thake.soundnest.parental.ParentalPolicyStore.hideHomeShelf(context, key)
        android.widget.Toast.makeText(context, "Hidden section: ${section.title}", android.widget.Toast.LENGTH_SHORT).show()
    }) {
        androidx.compose.material3.Icon(
            androidx.compose.material.icons.Icons.Rounded.VisibilityOff,
            contentDescription = "Hide section",
            tint = Immersive.TextSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** The lead carousel: big artwork cards with the caption over a scrim, the next one peeking in. */
@Composable
private fun HeroSection(
    navController: NavController,
    section: HomeSection,
    playingContext: State<String>,
    adminUnlocked: Boolean = false,
    onQuickBlock: (io.github.thake.soundnest.parental.ParentBlockTarget) -> Unit = {},
) {
    val canHide = adminUnlocked && section.uri != io.github.thake.soundnest.parental.HomeSectionFilter.PARENT_PICKS_URI
    Column(Modifier.padding(bottom = 26.dp)) {
        SectionHeader(
            title = section.title,
            trailing = if (canHide) ({ HideShelfButton(section) }) else null,
        )
        BoxWithConstraints {
            // Measured: the card has a ceiling as well as a fraction, and the skeleton agrees.
            val cardWidth = immersiveHeroCardWidth(maxWidth)
            val keys = remember(section.items) { homeItemKeys(section.items) }
            ImmersiveShelfRow {
                items(section.items.size, key = { keys[it] }) { i ->
                    val item = section.items[i]
                    val target = remember(item) { io.github.thake.soundnest.parental.ParentBlockActions.targetForHomeItem(item) }
                    // One lambda per card, so the press hint's pointer input is not restarted by a recomposition.
                    val hint = remember(item) { { prefetchHomeItem(item) } }
                    Box {
                        ImmersiveLargeAlbumCard(
                            title = item.name,
                            subtitle = item.cardSubtitle(),
                            imageUrl = item.imageUrl,
                            onClick = { onHomeItemClick(navController, item) },
                            onLongClick = if (adminUnlocked && target != null) ({ onQuickBlock(target) }) else null,
                            modifier = Modifier.width(cardWidth).pressHint(hint),
                            playing = item.isPlayingIn(playingContext.value),
                        )
                        if (adminUnlocked && target != null) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(10.dp)
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.62f))
                                    .clickable { onQuickBlock(target) },
                            ) {
                                androidx.compose.material3.Icon(
                                    imageVector = Icons.Rounded.MoreHoriz,
                                    contentDescription = stringResource(R.string.content_description_context_menu),
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A titled row of square cards (round for artists). */
@Composable
private fun ShelfSection(
    navController: NavController,
    section: HomeSection,
    playingContext: State<String>,
    adminUnlocked: Boolean = false,
    onQuickBlock: (io.github.thake.soundnest.parental.ParentBlockTarget) -> Unit = {},
) {
    val canHide = adminUnlocked && section.uri != io.github.thake.soundnest.parental.HomeSectionFilter.PARENT_PICKS_URI
    val keys = remember(section.items) { homeItemKeys(section.items) }
    ImmersiveShelf(
        title = section.title,
        trailing = if (canHide) ({ HideShelfButton(section) }) else null,
    ) {
        items(section.items.size, key = { keys[it] }) { i ->
            val item = section.items[i]
            val target = remember(item) { io.github.thake.soundnest.parental.ParentBlockActions.targetForHomeItem(item) }
            val hint = remember(item) { { prefetchHomeItem(item) } }
            val showDot = item is HomeItem.Show && !item.isEpisode
            val blockClick: (() -> Unit)? = if (adminUnlocked && target != null) ({ onQuickBlock(target) }) else null
            ImmersiveShelfCard(
                title = item.name,
                subtitle = item.cardSubtitle(),
                imageUrl = item.imageUrl,
                onClick = { onHomeItemClick(navController, item) },
                onLongClick = blockClick,
                modifier = Modifier.width(io.github.thake.soundnest.ui.immersive.components.ImmersiveShelfCardWidth)
                    .pressHint(hint),
                round = item is HomeItem.Artist,
                playing = item.isPlayingIn(playingContext.value),
                badge = if (showDot || blockClick != null) ({
                    if (showDot) {
                        io.github.thake.soundnest.ui.components.NewEpisodeDot(item.id, Immersive.Accent, Modifier.align(Alignment.TopStart).padding(8.dp))
                    }
                    if (blockClick != null) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.62f))
                                .clickable(onClick = blockClick),
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Rounded.MoreHoriz,
                                contentDescription = stringResource(R.string.content_description_context_menu),
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }) else null,
            )
        }
    }
}

/** The two-column shortcuts the web home starts with: a small cover and a name on a raised tile. */
@Composable
private fun ShortcutGrid(
    navController: NavController,
    items: List<HomeItem>,
    playingContext: State<String>,
    adminUnlocked: Boolean = false,
    onQuickBlock: (io.github.thake.soundnest.parental.ParentBlockTarget) -> Unit = {},
) {
    Column(
        modifier = Modifier.padding(horizontal = Immersive.Gutter).padding(bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Large windows: more tiles per row (two on phones).
        val cols = io.github.thake.soundnest.ui.components.windowSize().columns(2, 280.dp)
        items.chunked(cols).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                rowItems.forEach { item ->
                    val playing = item.isPlayingIn(playingContext.value)
                    val target = remember(item) { io.github.thake.soundnest.parental.ParentBlockActions.targetForHomeItem(item) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .pressHint { prefetchHomeItem(item) }
                            .immersiveClickable(
                                onClick = { onHomeItemClick(navController, item) },
                                onLongClick = if (adminUnlocked && target != null) ({ onQuickBlock(target) }) else null,
                            )
                            .clip(Immersive.CoverMedium)
                            .background(Immersive.Raised),
                    ) {
                        ImmersiveCover(
                            url = item.imageUrl,
                            modifier = Modifier.size(56.dp),
                            shape = Immersive.CoverMedium,
                            border = false,
                        )
                        Text(
                            text = item.name,
                            style = Immersive.Subhead.copy(
                                color = if (playing) Immersive.Accent else Immersive.Text,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 17.sp,
                                // Two balanced lines rather than a lone word under the rest.
                                lineBreak = androidx.compose.ui.text.style.LineBreak.Heading,
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 10.dp).weight(1f),
                        )
                        if (adminUnlocked && target != null) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .clickable { onQuickBlock(target) },
                            ) {
                                androidx.compose.material3.Icon(
                                    imageVector = Icons.Rounded.MoreHoriz,
                                    contentDescription = stringResource(R.string.content_description_context_menu),
                                    tint = Immersive.TextSecondary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
                repeat(cols - rowItems.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}
