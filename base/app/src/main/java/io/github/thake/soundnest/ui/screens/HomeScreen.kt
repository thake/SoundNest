package io.github.thake.soundnest.ui.screens

import io.github.thake.soundnest.ui.theme.tone
import io.github.thake.soundnest.ui.theme.AppText
import io.github.thake.soundnest.ui.theme.AppMuted
import io.github.thake.soundnest.ui.theme.AppAccent

import io.github.thake.soundnest.ui.components.SkeletonKind
import io.github.thake.soundnest.ui.components.pressable

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.platform.LocalContext
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import io.github.thake.soundnest.R
import io.github.thake.soundnest.data.api.Response
import io.github.thake.soundnest.data.entity.HomeFeedModel
import io.github.thake.soundnest.data.entity.HomeItem
import io.github.thake.soundnest.data.entity.homeShortcuts
import io.github.thake.soundnest.data.entity.withoutDj
import io.github.thake.soundnest.data.entity.withoutPodcastShows
import io.github.thake.soundnest.data.entity.HomeSection
import io.github.thake.soundnest.ui.components.Loader
import io.github.thake.soundnest.ui.components.onHomeItemClick
import io.github.thake.soundnest.ui.components.prefetchHomeItem
import io.github.thake.soundnest.ui.components.prefetchHomeShortcuts
import io.github.thake.soundnest.ui.navigation.Routes
import io.github.thake.soundnest.ui.theme.AppBackground
import io.github.thake.soundnest.ui.theme.GridBackground
import io.github.thake.soundnest.ui.viewmodel.HomeViewModel


@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun HomeScreen(navController: NavController){

    val homeViewModel : HomeViewModel = viewModel()
    // An old feed is read again when Home shows again (see HomeViewModel.refreshIfStale).
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { homeViewModel.refreshIfStale() }
    val home by homeViewModel.home.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(Unit) { io.github.thake.soundnest.parental.ParentalPolicyStore.current(context) }
    val policy by io.github.thake.soundnest.parental.ParentalPolicyStore.policyFlow.collectAsState()

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(AppBackground.toArgb()))
    ) {
        val feed = (home as? Response.Success)?.data
        val hasFeed = feed != null && (feed.sections.isNotEmpty() || policy.homeSections.showShortcutsGrid || policy.homeSections.showParentPicks)
        // The feed fades in over the placeholder instead of replacing it in one frame.
        io.github.thake.soundnest.ui.components.LoadedFade(!hasFeed) { waiting ->
        when {
            // Preferred: the real personalized Spotify home feed.
            !waiting && feed != null -> {
                HomeFeedContent(navController, feed, homeViewModel.facet.value) { homeViewModel.selectFacet(it) }
            }

            // No feed to show (loading, empty chip, error): keep the chips on screen
            // so the user can always switch back to "Tout".
            else -> androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                HomeHeaderRow(navController, homeViewModel.facet.value) { homeViewModel.selectFacet(it) }
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    when {
                        // Fading out as the feed fades in: still the placeholder.
                        home is Response.Loading || hasFeed -> Loader(SkeletonKind.Home)
                        else -> androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (home is Response.Error) stringResource(R.string.home_error) else stringResource(R.string.home_empty),
                                color = AppText,
                                textAlign = TextAlign.Center,
                            )
                            if (home is Response.Error) androidx.compose.material3.TextButton(onClick = { homeViewModel.retry() }) {
                                Text(stringResource(R.string.home_retry), color = AppAccent)
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun HomeFeedContent(navController: NavController, feed: HomeFeedModel, facet: String = "", onFacet: (String) -> Unit = {}) {
    val context = androidx.compose.ui.platform.LocalContext.current
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

    val rawGridSection = remember(feed) { feed.sections.firstOrNull()?.takeIf { it.title.isBlank() } }
    val rawCarousels = remember(feed, rawGridSection) { if (rawGridSection != null) feed.sections.drop(1) else feed.sections }
    val carousels = remember(rawCarousels, policy, revision) {
        io.github.thake.soundnest.parental.HomeSectionFilter.filterSections(
            sections = rawCarousels,
            policy = policy,
            parentPicksCandidates = io.github.thake.soundnest.parental.HomeSectionFilter.cachedLibraryCandidates(),
        )
    }
    val carouselKeys = remember(carousels) { uniqueKeys(carousels.map { it.uri.ifBlank { it.title } }) }
    // Liked Songs leads the shortcuts when Spotify's grid does not carry it.
    val likedName = stringResource(R.string.collection_liked_songs_title)
    val shortcuts = remember(rawGridSection, feed, policy, revision, facet, likedName) {
        if (!policy.homeSections.showShortcutsGrid) null
        else {
            val filteredGrid = io.github.thake.soundnest.parental.HomeSectionFilter.filterShortcutItems(rawGridSection?.items ?: emptyList(), policy)
            val emptyFeed = feed.sections.isEmpty() && !policy.homeSections.minimalHomeMode && policy.filterMode != io.github.thake.soundnest.parental.FilterMode.STRICT_ALLOWLIST
            homeShortcuts(filteredGrid, emptyFeed, facet, likedName)
        }
    }
    // The shortcuts at the top load in the background, ready before a tap.
    androidx.compose.runtime.LaunchedEffect(shortcuts) { prefetchHomeShortcuts(shortcuts) }

    // Re-tapping the Home tab while on Home scrolls this list back to its top (official
    // behaviour): an animation over the existing items, so nothing is fetched again.
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    androidx.compose.runtime.LaunchedEffect(Unit) {
        io.github.thake.soundnest.ui.navigation.TabReselect.home.collect {
            if (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0) {
                listState.animateScrollToItem(0)
            }
        }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = io.github.thake.soundnest.ui.components.bottomBarsSpace()),
        modifier = Modifier
            .fillMaxSize()
            .background(Color(AppBackground.toArgb()))
    ) {
        // Sticky filter row: content scrolls underneath it, like the official app.
        stickyHeader {
            // Fade (not a solid band): opaque under the status bar, transparent at the bottom.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            0f to Color(AppBackground.toArgb()),
                            0.55f to Color(AppBackground.toArgb()).copy(alpha = 0.85f),
                            1f to Color.Transparent,
                        )
                    )
                    .statusBarsPadding()
                    .padding(bottom = 10.dp)
            ) {
                HomeHeaderRow(navController, facet, onFacet)
            }
        }
        shortcuts?.let { tiles ->
            item {
                HomeShortcutGrid(
                    navController = navController,
                    items = tiles,
                    adminUnlocked = adminUnlocked,
                    onQuickBlock = { quickBlockTarget = it },
                )
            }
        }
        items(carousels.size, key = { carouselKeys[it] }) { i ->
            HomeFeedSection(
                navController = navController,
                section = carousels[i],
                adminUnlocked = adminUnlocked,
                onQuickBlock = { quickBlockTarget = it },
            )
        }
    }
}

/** Spotify-style top row: profile avatar on the left (opens Settings, like the
 *  official app's profile drawer), then the filter pills — one single row. */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun HomeHeaderRow(navController: NavController, facet: String, onFacet: (String) -> Unit) {
    var showProfile by remember { androidx.compose.runtime.mutableStateOf(false) }
    if (showProfile) ProfileSheet(onOpen = { route -> showProfile = false; navController.navigate(route) }) { showProfile = false }
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(Unit) {
        io.github.thake.soundnest.data.api.ProfileCache.ensure(context)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        // The row's 10 dp above and below sit in each child instead, so the avatar's tap area
        // takes them in (and the margin up to the separator) without moving anything.
        modifier = Modifier.fillMaxWidth(),
    ) {
        val avatarUrl = io.github.thake.soundnest.data.api.ProfileCache.imageUrl
        val initial = io.github.thake.soundnest.data.api.ProfileCache.name
            ?.trim()?.firstOrNull()?.uppercase() ?: "•"
        Box(
            modifier = Modifier
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { showProfile = true }
                .padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color(0xFFE8622C)),
        ) {
            if (avatarUrl != null) {
                GlideImage(
                    model = avatarUrl,
                    contentScale = ContentScale.Crop,
                    contentDescription = stringResource(R.string.recently_played_user_profile),
                    modifier = Modifier.size(34.dp),
                )
            } else {
                Text(
                    text = initial,
                    color = Color.Black,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        }
        val policy by io.github.thake.soundnest.parental.ParentalPolicyStore.policyFlow.collectAsState()
        val showBell = io.github.thake.soundnest.parental.HomeSectionFilter.isWhatsNewAllowed(policy)
        val showChips = !policy.homeSections.minimalHomeMode && policy.homeSections.showFilterChips
        if (showBell) {
            WhatsNewBell { navController.navigate(io.github.thake.soundnest.ui.navigation.Routes.WhatsNew.route) }
        }
        if (showChips) {
            // B31: a separator between the profile and the options, reported missing from this
            // header (the sheets already carry one).
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 1.dp, height = 22.dp)
                    .background(tone(0xFF3E3E3E)),
            )
            // Scrolls sideways if the pills don't fit, like Spotify's.
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(androidx.compose.foundation.rememberScrollState())
                    .padding(start = 12.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
            ) {
                // Spotify's home chips. Once one is picked, as in Spotify: a close chip, the picked one,
                // and joined to it the chips it opens (Following: only what the user follows).
                val labels = mapOf(
                    "music-chip" to stringResource(R.string.recents_content_filter_music),
                    "podcasts-chip" to stringResource(R.string.ad_category_title_podcasts),
                    "audiobooks-chip" to stringResource(R.string.audiobooks_title),
                )
                if (facet.isEmpty()) {
                    HomePill(stringResource(R.string.filter_chip_all_label), selected = true) {}
                    HomePill(labels.getValue("music-chip")) { onFacet("music-chip") }
                    if (policy.allowPodcasts) {
                        HomePill(labels.getValue("podcasts-chip")) { onFacet("podcasts-chip") }
                    }
                    if (policy.allowAudiobooks) {
                        HomePill(labels.getValue("audiobooks-chip")) { onFacet("audiobooks-chip") }
                    }
                } else {
                    val parent = io.github.thake.soundnest.data.entity.HomeSubChips.parentOf(facet) ?: facet
                    val subs = io.github.thake.soundnest.data.entity.HomeSubChips.byParent[parent].orEmpty()
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.clip(CircleShape).background(tone(0xFF2A2A2A)).clickable { onFacet("") }.padding(8.dp),
                    ) {
                        Icon(androidx.compose.material.icons.Icons.Filled.Close, contentDescription = stringResource(R.string.filter_chip_all_label),
                            tint = AppText, modifier = Modifier.size(18.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        HomePill(labels[parent].orEmpty(), selected = true, shape = if (subs.isEmpty()) RoundedCornerShape(50) else JoinedStart) { onFacet(parent) }
                        subs.forEachIndexed { i, (id, label) ->
                            val shown = remember(id) { androidx.compose.animation.core.MutableTransitionState(false).apply { targetState = true } }
                            androidx.compose.animation.AnimatedVisibility(
                                visibleState = shown,
                                enter = androidx.compose.animation.expandHorizontally(expandFrom = Alignment.Start) + androidx.compose.animation.fadeIn(),
                            ) {
                                HomePill(label, selected = facet == id, shape = if (i == subs.lastIndex) JoinedEnd else RoundedCornerShape(4.dp)) { onFacet(id) }
                            }
                        }
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

/** The left half of two pills joined (a picked chip and the one it opened), and the right one. */
private val JoinedStart = RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50, topEndPercent = 15, bottomEndPercent = 15)
private val JoinedEnd = RoundedCornerShape(topStartPercent = 15, bottomStartPercent = 15, topEndPercent = 50, bottomEndPercent = 50)

@Composable
private fun HomePill(label: String, selected: Boolean = false, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(50), onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) io.github.thake.soundnest.ui.theme.AppAccent else tone(0xFF2A2A2A))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(text = label, color = if (selected) Color.Black else AppText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalGlideComposeApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun HomeShortcutGrid(
    navController: NavController,
    items: List<HomeItem>,
    adminUnlocked: Boolean = false,
    onQuickBlock: (io.github.thake.soundnest.parental.ParentBlockTarget) -> Unit = {},
) {
    // Large windows: more tiles per row (two on phones).
    val cols = io.github.thake.soundnest.ui.components.windowSize().columns(2, 280.dp)
    Column(modifier = Modifier.padding(8.dp, 4.dp)) {
        items.chunked(cols).forEach { rowItems ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp, 4.dp),
            ) {
                rowItems.forEach { item ->
                    val blockTarget = remember(item) { io.github.thake.soundnest.parental.ParentBlockActions.targetForHomeItem(item) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(GridBackground.toArgb()))
                            .then(
                                if (adminUnlocked && blockTarget != null) {
                                    Modifier.combinedClickable(
                                        onClick = { onHomeItemClick(navController, item) },
                                        onLongClick = { onQuickBlock(blockTarget) },
                                    )
                                } else {
                                    Modifier.pressable(0.96f, onPressHint = { prefetchHomeItem(item) }) { onHomeItemClick(navController, item) }
                                },
                            ),
                    ) {
                        GlideImage(
                            modifier = Modifier.size(48.dp),
                            contentScale = ContentScale.Crop,
                            model = item.imageUrl,
                            loading = placeholder(R.drawable.placeholder),
                            failure = placeholder(R.drawable.placeholder),
                            contentDescription = "",
                        )
                        Text(
                            text = item.name,
                            color = AppText,
                            fontSize = 12.sp,
                            lineHeight = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            // Two balanced lines ("Daily / Mix 2") rather than a lone "2" under the rest.
                            style = androidx.compose.ui.text.TextStyle(lineBreak = androidx.compose.ui.text.style.LineBreak.Heading),
                            modifier = Modifier.weight(1f).padding(8.dp, 4.dp),
                        )
                        if (adminUnlocked && blockTarget != null) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.content_description_context_menu),
                                tint = AppMuted,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .clickable { onQuickBlock(blockTarget) }
                                    .padding(7.dp),
                            )
                        }
                    }
                }
                repeat(cols - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

/** A key per entry that stays with it while the others around it change: [raw], with a counter on repeats. */
private fun uniqueKeys(raw: List<String>): List<String> {
    val seen = HashMap<String, Int>()
    return raw.map { k ->
        val n = (seen[k] ?: 0) + 1
        seen[k] = n
        if (n == 1) k else "$k#$n"
    }
}

@Composable
private fun HomeFeedSection(
    navController: NavController,
    section: HomeSection,
    adminUnlocked: Boolean = false,
    onQuickBlock: (io.github.thake.soundnest.parental.ParentBlockTarget) -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(16.dp, 20.dp, 12.dp, 4.dp),
    ) {
        Text(
            text = section.title,
            color = AppText,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (adminUnlocked && section.uri != io.github.thake.soundnest.parental.HomeSectionFilter.PARENT_PICKS_URI) {
            androidx.compose.material3.IconButton(onClick = {
                val key = io.github.thake.soundnest.parental.HomeSectionFilter.shelfKey(section)
                io.github.thake.soundnest.parental.ParentalPolicyStore.hideHomeShelf(context, key)
                android.widget.Toast.makeText(context, "Hidden section: ${section.title}", android.widget.Toast.LENGTH_SHORT).show()
            }) {
                Icon(
                    androidx.compose.material.icons.Icons.Outlined.VisibilityOff,
                    contentDescription = "Hide section",
                    tint = AppMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
    val keys = remember(section) { uniqueKeys(section.items.map { it.name + "|" + it.imageUrl }) }
    LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp, 0.dp)) {
        items(section.items.size, key = { keys[it] }) { i ->
            val item = section.items[i]
            val target = remember(item) { io.github.thake.soundnest.parental.ParentBlockActions.targetForHomeItem(item) }
            HomeFeedCard(
                item = item,
                onLongClick = if (adminUnlocked && target != null) ({ onQuickBlock(target) }) else null,
            ) { onHomeItemClick(navController, item) }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun HomeFeedCard(item: HomeItem, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    val isArtist = item is HomeItem.Artist
    val subtitle = when (item) {
        is HomeItem.Album -> item.subtitle
        is HomeItem.Playlist -> item.subtitle
        is HomeItem.Artist -> stringResource(R.string.artist_subtitle)
        is HomeItem.Show -> item.subtitle
    }
    Column(
        horizontalAlignment = if (isArtist) Alignment.CenterHorizontally else Alignment.Start,
        modifier = Modifier
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                } else {
                    Modifier.pressable(0.96f, onPressHint = { prefetchHomeItem(item) }) { onClick() }
                },
            )
            .width(150.dp)
            .padding(6.dp),
    ) {
        Box {
            GlideImage(
                modifier = Modifier
                    .size(150.dp)
                    .clip(if (isArtist) CircleShape else RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
                model = item.imageUrl,
                loading = placeholder(R.drawable.placeholder),
                failure = placeholder(R.drawable.placeholder),
                contentDescription = "",
            )
            if (item is HomeItem.Show && !item.isEpisode) {
                io.github.thake.soundnest.ui.components.NewEpisodeDot(item.id, AppAccent, Modifier.align(Alignment.TopEnd).padding(8.dp))
            }
            if (onLongClick != null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.62f))
                        .clickable { onLongClick() },
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.content_description_context_menu),
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = item.name,
            color = AppText,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            textAlign = if (isArtist) TextAlign.Center else TextAlign.Start,
        )
        // Always two lines tall: a long artist mix no longer makes one card taller than its row.
        Text(
            text = subtitle,
            color = AppMuted,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            minLines = 2,
            maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            textAlign = if (isArtist) TextAlign.Center else TextAlign.Start,
        )
    }
}

/** The bell beside the avatar: opens "What's New". */
@Composable
private fun WhatsNewBell(onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(end = 10.dp, top = 10.dp, bottom = 10.dp)
            .size(34.dp),
    ) {
        androidx.compose.material3.Icon(
            androidx.compose.material.icons.Icons.Outlined.Notifications,
            contentDescription = stringResource(R.string.whats_new_title),
            tint = AppText,
            modifier = Modifier.size(24.dp),
        )
    }
}

/** Profile sheet behind the avatar: account name and sign-out (in the Web Player). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, ExperimentalGlideComposeApi::class)
@Composable
private fun ProfileSheet(onOpen: (route: String) -> Unit = {}, onDismiss: () -> Unit) {
    val name = io.github.thake.soundnest.data.api.ProfileCache.name.orEmpty()
    val avatar = io.github.thake.soundnest.data.api.ProfileCache.imageUrl
    // Opened in full: half-open, the sheet and its list both moved under the finger.
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = tone(0xFF282828),
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.fillMaxWidth().then(Modifier.windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.navigationBars))
                .verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(bottom = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)) {
                io.github.thake.soundnest.ui.components.ProfileAvatar(url = avatar, name = name, size = 48.dp)
                Text(name.ifBlank { stringResource(R.string.spotify_account) }, color = AppText, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 14.dp))
            }
            androidx.compose.material3.HorizontalDivider(color = tone(0xFF3E3E3E), modifier = Modifier.padding(vertical = 8.dp))
            // Sound quality, data, battery, display: one page.
            androidx.compose.foundation.layout.Column(
                Modifier.fillMaxWidth().clickable { onOpen(io.github.thake.soundnest.ui.navigation.Routes.Settings.route) }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Text(stringResource(R.string.settings_title), color = AppText, fontSize = 16.sp)
                Text(stringResource(R.string.settings_detail), color = AppMuted, fontSize = 12.sp)
            }
            // Parental Controls Dashboard (Parent PIN required).
            androidx.compose.foundation.layout.Column(
                Modifier.fillMaxWidth().clickable {
                    io.github.thake.soundnest.parental.ParentSecurityManager.runProtected(
                        io.github.thake.soundnest.di.Graph.app,
                        io.github.thake.soundnest.parental.AppSurface.PARENT_CONTROLS,
                    ) { onOpen(io.github.thake.soundnest.ui.navigation.Routes.ParentSettings.route) }
                }.padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Text("Parental Controls", color = AppAccent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("Content filters, Home sections, features, speakers & backup", color = AppMuted, fontSize = 12.sp)
            }
            // In-app recap: every listen on this phone, Spotify and local files.
            androidx.compose.foundation.layout.Column(
                Modifier.fillMaxWidth().clickable { onOpen(io.github.thake.soundnest.ui.navigation.Routes.Recap.route) }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Text(stringResource(R.string.recap_title), color = AppText, fontSize = 16.sp)
                Text(stringResource(R.string.recap_detail_soundnest), color = AppMuted, fontSize = 12.sp)
            }
            // The account's own listening history, as Spotify keeps it: it belongs beside the
            // recap (the local counter) because both answer "what did I listen to".
            androidx.compose.foundation.layout.Column(
                Modifier.fillMaxWidth().clickable { onOpen(io.github.thake.soundnest.ui.navigation.Routes.Recent.route) }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Text(stringResource(R.string.recents_title), color = AppText, fontSize = 16.sp)
                Text(stringResource(R.string.recents_detail), color = AppMuted, fontSize = 12.sp)
            }
            // The Spotify account page (plan, privacy, devices), in the app's signed-in browser (Parent PIN required).
            androidx.compose.foundation.layout.Column(
                Modifier.fillMaxWidth().clickable {
                    onDismiss()
                    io.github.thake.soundnest.parental.ParentSecurityManager.runProtected(
                        io.github.thake.soundnest.di.Graph.app,
                        io.github.thake.soundnest.parental.AppSurface.ACCOUNTS_AND_LOGOUT,
                    ) {
                        io.github.thake.soundnest.ui.screens.AccountBrowser.open(io.github.thake.soundnest.di.Graph.app, io.github.thake.soundnest.ui.screens.AccountBrowser.ACCOUNT_URL)
                    }
                }.padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Text(stringResource(R.string.account_page_title), color = AppText, fontSize = 16.sp)
                Text(stringResource(R.string.account_page_detail), color = AppMuted, fontSize = 12.sp)
            }
            // About: GPL-3.0 attribution and source code.
            var about by remember { androidx.compose.runtime.mutableStateOf(false) }
            if (about) io.github.thake.soundnest.ui.components.AboutSheet(onDismiss = { about = false })
            androidx.compose.foundation.layout.Column(
                Modifier.fillMaxWidth().clickable { about = true }.padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Text(stringResource(R.string.creator_biography_section_title), color = AppText, fontSize = 16.sp)
                Text(stringResource(R.string.about_detail), color = AppMuted, fontSize = 12.sp)
            }
            // The other Spotify accounts kept on the phone: switch, add, remove (Parent PIN required).
            var accounts by remember { androidx.compose.runtime.mutableStateOf(false) }
            if (accounts) io.github.thake.soundnest.ui.components.AccountsSheet(onDismiss = { accounts = false }, onLeave = { accounts = false; onDismiss() })
            androidx.compose.foundation.layout.Column(
                Modifier.fillMaxWidth().clickable {
                    io.github.thake.soundnest.parental.ParentSecurityManager.runProtected(
                        io.github.thake.soundnest.di.Graph.app,
                        io.github.thake.soundnest.parental.AppSurface.ACCOUNTS_AND_LOGOUT,
                    ) { accounts = true }
                }.padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Text(stringResource(R.string.accounts_switch_row), color = AppText, fontSize = 16.sp)
                Text(io.github.thake.soundnest.ui.components.switchAccountDetail(), color = AppMuted, fontSize = 12.sp)
            }
            Text(
                stringResource(R.string.connect_device_menu_logout),
                color = AppText, fontSize = 16.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onDismiss()
                        io.github.thake.soundnest.parental.ParentSecurityManager.runProtected(
                            io.github.thake.soundnest.di.Graph.app,
                            io.github.thake.soundnest.parental.AppSurface.ACCOUNTS_AND_LOGOUT,
                        ) { io.github.thake.soundnest.di.SessionActions.logout(io.github.thake.soundnest.di.Graph.app) }
                    }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            )
        }
    }
}


