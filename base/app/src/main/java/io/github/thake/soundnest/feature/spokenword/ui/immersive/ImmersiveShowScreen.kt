package io.github.thake.soundnest.feature.spokenword.ui.immersive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.catalog.Response
import io.github.thake.soundnest.core.model.SongsModel
import io.github.thake.soundnest.core.util.Haptics
import io.github.thake.soundnest.core.playback.SongPlayer
import io.github.thake.soundnest.feature.spokenword.podcasts.settings.EpisodeFilter
import io.github.thake.soundnest.feature.spokenword.audiobooks.ui.ChapterOptionsSheet
import io.github.thake.soundnest.feature.spokenword.audiobooks.ui.formatBookRuntime
import io.github.thake.soundnest.feature.spokenword.audiobooks.ui.immersive.ImmersiveChapterRow
import io.github.thake.soundnest.feature.spokenword.ui.EpisodeOptionsSheet
import io.github.thake.soundnest.feature.spokenword.ui.classic.formatReleaseDate
import io.github.thake.soundnest.feature.spokenword.ui.episodeFilterLabel
import io.github.thake.soundnest.core.ui.immersive.Immersive
import io.github.thake.soundnest.core.ui.immersive.components.ImmersiveButton
import io.github.thake.soundnest.core.ui.immersive.components.ImmersiveCover
import io.github.thake.soundnest.core.ui.immersive.components.ImmersiveMessageState
import io.github.thake.soundnest.core.ui.immersive.components.immersiveClickable
import io.github.thake.soundnest.core.ui.immersive.components.immersiveSongListSkeleton
import io.github.thake.soundnest.feature.library.ui.immersive.ImmersiveAboutText
import io.github.thake.soundnest.feature.home.ui.immersive.ImmersiveChip
import io.github.thake.soundnest.feature.home.ui.immersive.ImmersiveChipRow
import io.github.thake.soundnest.core.ui.immersive.shell.ImmersiveTopBar
import io.github.thake.soundnest.core.ui.immersive.shell.immersiveHazeSource
import io.github.thake.soundnest.core.ui.immersive.shell.immersiveTopContentPadding
import io.github.thake.soundnest.core.ui.navigation.episodeRoute
import io.github.thake.soundnest.feature.spokenword.ui.ShowViewModel

private val ShowCover = 208.dp
private val AudiobookCover = 176.dp

/**
 * A show (or audiobook) page in the Immersive look: the cover on a wash of its own colour, the
 * name, who makes it, the rating and Follow; then Episodes (or Chapters) and About as pills, the
 * sort order, and the episodes with their own play, download and menu controls. Same
 * [ShowViewModel] and actions as the Classic show page.
 */
@Composable
fun ImmersiveShowScreen(navController: NavController, showId: String, showName: String = "") {
    val vm: ShowViewModel = viewModel()
    val context = LocalContext.current
    LaunchedEffect(showId) { vm.loadShow(showId) }

    val pageState by vm.page.collectAsState()
    val page = (pageState as? Response.Success)?.data
    val show = page?.show
    val audiobook = page?.audiobook == true
    val rawItems = page?.items.orEmpty()
    val policy = io.github.thake.soundnest.feature.parental.settings.rememberParentalPolicy()
    val displayName = show?.name?.takeIf { it.isNotBlank() } ?: showName
    val cover = page?.coverUri.orEmpty()

    var tab by remember { mutableStateOf(0) }
    var oldestFirst by remember { mutableStateOf(false) }
    var menuSong by remember { mutableStateOf<SongsModel?>(null) }
    val np by SongPlayer.nowPlaying
    val listState = rememberLazyListState()

    val isAudible = io.github.thake.soundnest.feature.spokenword.audible.data.AudibleUris.isAudibleUri(showId)
    val contextUri = if (isAudible) {
        io.github.thake.soundnest.feature.spokenword.audible.data.AudibleUris.asinFromAny(showId)?.let(io.github.thake.soundnest.feature.spokenword.audible.data.AudibleUris::bookUri).orEmpty()
    } else {
        "spotify:show:$showId"
    }
    LaunchedEffect(contextUri, audiobook) {
        if (audiobook && contextUri.isNotBlank()) SongPlayer.markAudiobookContext(contextUri)
    }
    val items = remember(rawItems, policy, showId, audiobook, isAudible) {
        if (isAudible) rawItems else rawItems.filter {
            io.github.thake.soundnest.feature.parental.filter.ParentalFilterEngine.isTrackAllowed(
                it,
                policy,
                parentContextUri = contextUri,
                isAudiobookChapter = audiobook,
            )
        }
    }

    fun playAt(index: Int, positionMs: Long? = null) {
        val song = items.getOrNull(index) ?: return
        vm.updateQueue(items)
        SongPlayer.playSong(song.url, context, contextUri = contextUri, positionMs = positionMs)
        vm.updateSongState(song.coverUri, song.title, song.singer, true, song.id, index, song.album)
    }

    val filter = vm.filter.value
    val rows by remember(items, oldestFirst, filter, audiobook) {
        derivedStateOf {
            val base = if (!audiobook && oldestFirst) items.reversed() else items
            filter.apply(base) { vm.isFinished(it) }
        }
    }
    val chapterNumberOf = remember(items) {
        items.mapIndexed { idx, s -> s.url to (idx + 1) }.toMap()
    }

    val bookActive = audiobook && contextUri.isNotBlank() && np.contextUri == contextUri && np.uri.isNotBlank()
    val bookPlaying = bookActive && SongPlayer.isPlaying()
    val resumeIdx by remember(items, audiobook) {
        derivedStateOf {
            if (!audiobook) null
            else items.indexOfFirst { !vm.isFinished(it) && it.playedPositionMs > 0L }.takeIf { it >= 0 }
                ?: items.indexOfFirst { !vm.isFinished(it) }.takeIf { it > 0 }
        }
    }
    var livePosMs by remember { mutableLongStateOf(SongPlayer.getCurrentPosition()) }
    LaunchedEffect(audiobook, bookPlaying, np.uri) {
        if (!audiobook) return@LaunchedEffect
        livePosMs = SongPlayer.getCurrentPosition()
        while (bookPlaying) {
            kotlinx.coroutines.delay(1_000L)
            livePosMs = SongPlayer.getCurrentPosition()
        }
    }

    menuSong?.let { sel ->
        val selIsAudible = isAudible || io.github.thake.soundnest.feature.spokenword.audible.data.AudibleUris.isAudibleUri(sel.url)
        val selPlayUri = if (selIsAudible) sel.url else "spotify:episode:${sel.url.removePrefix("episode:")}"
        val selLivePos = if (np.uri == selPlayUri) livePosMs.coerceAtLeast(sel.playedPositionMs) else sel.playedPositionMs
        if (audiobook) {
            ChapterOptionsSheet(
                chapter = sel,
                bookTitle = displayName,
                finished = vm.isFinished(sel),
                started = selLivePos > 0L,
                onPlay = {
                    val index = items.indexOfFirst { it.url == sel.url }
                    if (index >= 0) playAt(index)
                },
                onRestart = {
                    val index = items.indexOfFirst { it.url == sel.url }
                    if (index >= 0) playAt(index, positionMs = 0L)
                },
                onMarkFinished = { vm.setFinished(sel, it) },
                onOpenBook = null,
                onDismiss = { menuSong = null },
            )
        } else {
            EpisodeOptionsSheet(
                song = sel,
                finished = vm.isFinished(sel),
                onPlay = {
                    val index = items.indexOfFirst { it.url == sel.url }
                    if (index >= 0) playAt(index)
                },
                onMarkFinished = { vm.setFinished(sel, it) },
                onOpen = { navController.navigate(episodeRoute(sel.url.removePrefix("episode:"), sel.title)) },
                onDismiss = { menuSong = null },
            )
        }
    }

    ImmersiveOverscrollHost {
        Box(Modifier.fillMaxSize().background(Immersive.Page)) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = Immersive.BottomChrome),
                modifier = Modifier.fillMaxSize().immersiveHazeSource(),
            ) {
                item(key = "header") {
                    val primaryPlayLabel = when {
                        bookPlaying -> stringResource(R.string.notif_action_pause)
                        bookActive -> stringResource(R.string.audiobook_resume)
                        resumeIdx != null -> stringResource(R.string.audiobook_resume_chapter, resumeIdx!! + 1)
                        else -> stringResource(R.string.episode_play)
                    }
                    ShowHeader(
                        showId = showId,
                        show = show,
                        name = displayName,
                        cover = cover,
                        audiobook = audiobook,
                        showFollow = !isAudible,
                        following = vm.following.value,
                        onFollow = { on ->
                            Haptics.toggle(on)
                            vm.setFollow(showId, on)
                        },
                        showPlayButton = audiobook && items.isNotEmpty(),
                        playButtonLabel = primaryPlayLabel,
                        playButtonPlaying = bookPlaying,
                        onPlayBook = {
                            when {
                                bookActive -> if (bookPlaying) SongPlayer.pause() else SongPlayer.play()
                                isAudible -> SongPlayer.playSong(contextUri, context, contextUri = contextUri)
                                else -> playAt(resumeIdx ?: 0)
                            }
                        },
                    )
                }
                item(key = "tabs") {
                    ImmersiveChipRow {
                        ImmersiveChip(
                            label = stringResource(if (audiobook) R.string.show_tab_chapters else R.string.show_tab_episodes),
                            selected = tab == 0,
                            onClick = { tab = 0 },
                        )
                        ImmersiveChip(stringResource(R.string.show_tab_about), selected = tab == 1, onClick = { tab = 1 })
                    }
                }
                if (tab == 0 && items.isNotEmpty()) item(key = "sort") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Immersive.Gutter),
                    ) {
                        if (audiobook) {
                            Text(
                                text = stringResource(R.string.show_chapters_count, items.size),
                                style = Immersive.Subhead.copy(color = Immersive.TextSecondary),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(vertical = 10.dp),
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .immersiveClickable(onClick = { oldestFirst = !oldestFirst }, pressedScale = 0.96f)
                                    .padding(vertical = 12.dp),
                            ) {
                                Icon(Icons.Rounded.SwapVert, contentDescription = null, tint = Immersive.Accent, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(if (oldestFirst) R.string.show_sort_oldest else R.string.show_sort_newest),
                                    style = Immersive.Subhead.copy(color = Immersive.Accent, fontWeight = FontWeight.Medium),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Text(
                            text = stringResource(
                                if (audiobook && filter == EpisodeFilter.ALL) R.string.show_chapters_filter_all
                                else episodeFilterLabel(filter),
                            ),
                            style = Immersive.Subhead.copy(
                                color = if (filter == EpisodeFilter.ALL) Immersive.TextSecondary else Immersive.Accent,
                                fontWeight = FontWeight.Medium,
                            ),
                            maxLines = 1,
                            modifier = Modifier
                                .immersiveClickable(onClick = { vm.setFilter(showId, filter.next()) }, pressedScale = 0.96f)
                                .padding(start = 12.dp, top = 10.dp, bottom = 10.dp),
                        )
                    }
                }
                if (tab == 0 && items.isNotEmpty() && rows.isEmpty()) item(key = "all-played") {
                    ImmersiveMessageState(stringResource(if (audiobook) R.string.show_filter_all_chapters_played else R.string.show_filter_all_played))
                }
                if (pageState is Response.Loading && page == null) immersiveSongListSkeleton(count = 5, keyPrefix = "skeleton:show", alpha = 0.5f)
                if (pageState is Response.Error && items.isEmpty()) item(key = "error") {
                    ImmersiveMessageState((pageState as Response.Error).error)
                }
                if (tab == 1) item(key = "about") { About(show) }
                if (tab == 0 && page != null && items.isEmpty() && pageState !is Response.Loading) item(key = "empty") {
                    ImmersiveMessageState(stringResource(R.string.show_empty))
                }
                if (tab == 0) items(rows, key = { it.url }) { ep ->
                    val epIsAudible = io.github.thake.soundnest.feature.spokenword.audible.data.AudibleUris.isAudibleUri(ep.url)
                    val epPlayUri = if (epIsAudible) ep.url else "spotify:episode:${ep.url.removePrefix("episode:")}"
                    val isCurrentRow = np.uri == epPlayUri
                    val isPlayingRow = isCurrentRow && SongPlayer.isPlaying()
                    if (audiobook) {
                        ImmersiveChapterRow(
                            chapterNumber = chapterNumberOf[ep.url] ?: 1,
                            chapter = ep,
                            current = isCurrentRow,
                            playing = isPlayingRow,
                            finished = vm.isFinished(ep),
                            livePositionMs = if (isCurrentRow) livePosMs.coerceAtLeast(ep.playedPositionMs) else ep.playedPositionMs,
                            onClick = {
                                if (isCurrentRow) {
                                    if (isPlayingRow) SongPlayer.pause() else SongPlayer.play()
                                } else {
                                    val index = items.indexOfFirst { it.url == ep.url }
                                    if (index >= 0) playAt(index)
                                }
                            },
                            onMenu = { menuSong = ep },
                            modifier = rowPlacement(),
                        )
                    } else {
                        ImmersiveEpisodeRow(
                            episode = ep,
                            playing = isPlayingRow,
                            finished = vm.isFinished(ep),
                            isNew = vm.isNew(ep),
                            onPlay = {
                                val index = items.indexOfFirst { it.url == ep.url }
                                if (index >= 0) playAt(index)
                            },
                            onOpen = {
                                navController.navigate(episodeRoute(ep.url.removePrefix("episode:"), ep.title))
                            },
                            onMenu = { menuSong = ep },
                            modifier = rowPlacement(),
                        )
                    }
                }
            }
            ImmersiveTopBar(
                title = displayName,
                collapse = rememberHeaderCollapse(listState),
                onBack = { navController.navigateUp() },
            )
        }
    }
}

@Composable
private fun ShowHeader(
    showId: String,
    show: io.github.thake.soundnest.core.model.PodcastModel?,
    name: String,
    cover: String,
    audiobook: Boolean = false,
    showFollow: Boolean = true,
    following: Boolean,
    onFollow: (Boolean) -> Unit,
    showPlayButton: Boolean = false,
    playButtonLabel: String = "",
    playButtonPlaying: Boolean = false,
    onPlayBook: () -> Unit = {},
) {
    Box(Modifier.fillMaxWidth()) {
        CoverGlow(cover, height = if (audiobook) 380.dp else 420.dp)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(top = immersiveTopContentPadding() + 8.dp, bottom = 8.dp),
        ) {
            ImmersiveCover(cover, Modifier.size(if (audiobook) AudiobookCover else ShowCover), Immersive.CoverLarge, placeholderIcon = Icons.Rounded.Podcasts)
            Spacer(Modifier.height(if (audiobook) 14.dp else 20.dp))
            Text(
                text = name,
                style = Immersive.LargeTitle,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = Immersive.Gutter),
            )
            val authors = show?.authors.orEmpty().joinToString(", ")
            val narrators = show?.narrators.orEmpty().joinToString(", ")
            val byline = when {
                authors.isNotBlank() -> stringResource(R.string.show_authors, authors)
                show != null && show.publisher.isNotBlank() -> show.publisher
                else -> ""
            }
            if (byline.isNotBlank()) {
                Text(
                    text = byline,
                    style = Immersive.Subhead.copy(color = Immersive.Accent),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = Immersive.Gutter).padding(top = 4.dp),
                )
            }
            if (narrators.isNotBlank()) {
                Text(
                    text = stringResource(R.string.show_narrators, narrators),
                    style = Immersive.Caption,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = Immersive.Gutter).padding(top = 2.dp),
                )
            }
            val metaLine = buildList {
                show?.subtitle?.takeIf { it.isNotBlank() }?.let { add(it) }
                if (audiobook && show != null && show.runtimeMs > 0L) add(formatBookRuntime(show.runtimeMs))
            }.joinToString(" · ")
            if (metaLine.isNotBlank()) {
                Text(
                    text = metaLine,
                    style = Immersive.Caption,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = Immersive.Gutter).padding(top = 2.dp),
                )
            }
            val rating = show?.rating ?: 0.0
            if (rating > 0.0) {
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Star, contentDescription = null, tint = Immersive.TextSecondary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(String.format(io.github.thake.soundnest.feature.appearance.settings.AppLanguage.currentLocale(), "%.1f", rating), style = Immersive.Subhead)
                }
            }
            if (showPlayButton || showFollow) {
                Spacer(Modifier.height(14.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showPlayButton) {
                        ImmersiveButton(
                            text = playButtonLabel,
                            icon = if (playButtonPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            filled = true,
                            modifier = Modifier.widthIn(min = 160.dp),
                            onClick = onPlayBook,
                        )
                    }
                    if (showFollow) {
                        ImmersiveButton(
                            text = stringResource(if (following) R.string.show_following else R.string.show_follow),
                            icon = if (following) Icons.Rounded.Check else Icons.Rounded.Add,
                            filled = !showPlayButton && !following,
                            modifier = Modifier.widthIn(min = if (showPlayButton) 120.dp else 170.dp),
                            onClick = { onFollow(!following) },
                        )
                    }
                }
                if (showFollow && !audiobook) {
                    val showTarget = remember(showId, name, show?.publisher, cover) {
                        io.github.thake.soundnest.feature.parental.ui.ParentBlockTarget(
                            io.github.thake.soundnest.feature.parental.ui.ParentRuleKind.SHOW,
                            showId,
                            name,
                            show?.publisher.orEmpty(),
                            cover,
                        )
                    }
                    io.github.thake.soundnest.feature.parental.ui.ParentInlineBlockPill(
                        target = showTarget,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun About(show: io.github.thake.soundnest.core.model.PodcastModel?) {
    val authors = show?.authors.orEmpty().joinToString(", ")
    val narrators = show?.narrators.orEmpty().joinToString(", ")
    val released = formatReleaseDate(show?.releaseDate.orEmpty())
    Column(Modifier.fillMaxWidth().padding(horizontal = Immersive.Gutter, vertical = 8.dp)) {
        if (!show?.description.isNullOrBlank()) ImmersiveAboutText(show?.description.orEmpty(), maxLines = 8)
        if (authors.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.show_authors, authors), style = Immersive.Subhead)
        }
        if (narrators.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.show_narrators, narrators), style = Immersive.Subhead)
        }
        if (released.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.audiobook_released_date, released), style = Immersive.Subhead)
        }
    }
}
