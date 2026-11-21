package io.github.thake.soundnest.feature.spokenword.ui.classic

import io.github.thake.soundnest.feature.spokenword.ui.episodeClock
import io.github.thake.soundnest.feature.spokenword.ui.episodeDateLabel
import io.github.thake.soundnest.feature.spokenword.ui.episodeMinutes

import io.github.thake.soundnest.feature.home.whatsnew.ui.classic.title

import io.github.thake.soundnest.core.ui.look.wideClickable
import io.github.thake.soundnest.core.ui.classic.headerTint
import io.github.thake.soundnest.core.ui.classic.AppText

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.github.thake.soundnest.R
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import io.github.thake.soundnest.core.catalog.Response
import io.github.thake.soundnest.core.model.PodcastModel
import io.github.thake.soundnest.core.model.SongsModel
import io.github.thake.soundnest.core.playback.SongPlayer
import io.github.thake.soundnest.feature.spokenword.audiobooks.ui.ChapterOptionsSheet
import io.github.thake.soundnest.feature.spokenword.audiobooks.ui.classic.ChapterRow
import io.github.thake.soundnest.feature.spokenword.audiobooks.ui.formatBookRuntime
import io.github.thake.soundnest.feature.spokenword.ui.EpisodeOptionsSheet
import io.github.thake.soundnest.core.ui.classic.Loader
import io.github.thake.soundnest.core.ui.look.pressable
import io.github.thake.soundnest.core.ui.navigation.episodeRoute
import io.github.thake.soundnest.feature.spokenword.podcasts.settings.EpisodeFilter
import io.github.thake.soundnest.feature.spokenword.ui.EpisodeProgressBar
import io.github.thake.soundnest.feature.spokenword.ui.episodeFilterLabel
import io.github.thake.soundnest.core.ui.classic.AppAccent
import io.github.thake.soundnest.core.ui.classic.AppBackground
import io.github.thake.soundnest.feature.spokenword.ui.ShowViewModel

/**
 * A show page, laid out like the official app: cover and name, follow, the episodes (or the
 * chapters of a book) with their own play / download / "…" controls, and an About tab.
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun ShowScreen(navController: NavController, showId: String, showName: String = "") {
    val vm: ShowViewModel = viewModel()
    val context = LocalContext.current
    LaunchedEffect(showId) { vm.loadShow(showId) }

    val pageState by vm.page.collectAsState()
    val page = (pageState as? Response.Success)?.data
    val show = page?.show
    val audiobook = page?.audiobook == true
    val rawItems = page?.items.orEmpty()
    val policy = io.github.thake.soundnest.feature.parental.settings.rememberParentalPolicy()

    var tab by remember { mutableStateOf(0) }
    var oldestFirst by remember { mutableStateOf(false) }
    var menuSong by remember { mutableStateOf<SongsModel?>(null) }
    val np by SongPlayer.nowPlaying

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
        if (isAudible || io.github.thake.soundnest.feature.spokenword.audible.data.AudibleUris.isAudibleUri(song.url)) {
            SongPlayer.playSong(song.url, context, contextUri = contextUri, positionMs = positionMs)
            return
        }
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
                bookTitle = show?.name?.takeIf { it.isNotBlank() } ?: showName,
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

    Box(Modifier.fillMaxSize().background(AppBackground)) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = io.github.thake.soundnest.core.ui.look.bottomBarsSpace()),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                val primaryPlayLabel = when {
                    bookPlaying -> stringResource(R.string.notif_action_pause)
                    bookActive -> stringResource(R.string.audiobook_resume)
                    resumeIdx != null -> stringResource(R.string.audiobook_resume_chapter, resumeIdx!! + 1)
                    else -> stringResource(R.string.episode_play)
                }
                Header(
                    navController = navController,
                    showId = showId,
                    show = show,
                    fallbackName = showName,
                    cover = page?.coverUri.orEmpty(),
                    audiobook = audiobook,
                    showFollow = !isAudible,
                    following = vm.following.value,
                    onFollow = { vm.setFollow(showId, it) },
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
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TabLabel(
                        text = stringResource(if (audiobook) R.string.show_tab_chapters else R.string.show_tab_episodes),
                        selected = tab == 0,
                        modifier = Modifier.weight(1f),
                    ) { tab = 0 }
                    TabLabel(stringResource(R.string.show_tab_about), selected = tab == 1, modifier = Modifier.weight(1f)) { tab = 1 }
                }
            }
            if (tab == 0 && items.isNotEmpty()) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) {
                        if (audiobook) {
                            Text(
                                text = stringResource(R.string.show_chapters_count, items.size),
                                color = AppText.copy(alpha = 0.65f),
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f).padding(vertical = 10.dp),
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { oldestFirst = !oldestFirst }
                                    .padding(vertical = 12.dp),
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = null, tint = AppText.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = stringResource(if (oldestFirst) R.string.show_sort_oldest else R.string.show_sort_newest),
                                    color = AppText.copy(alpha = 0.8f),
                                    fontSize = 13.sp,
                                )
                            }
                        }
                        Text(
                            text = stringResource(
                                if (audiobook && filter == EpisodeFilter.ALL) R.string.show_chapters_filter_all
                                else episodeFilterLabel(filter),
                            ),
                            color = if (filter == EpisodeFilter.ALL) AppText.copy(alpha = 0.8f) else AppAccent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { vm.setFilter(showId, filter.next()) }
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                        )
                    }
                }
            }
            if (pageState is Response.Loading && page == null) item { Loader() }
            if (pageState is Response.Error && items.isEmpty()) item {
                io.github.thake.soundnest.core.ui.look.ErrorRetry(stringResource(R.string.page_load_failed)) { vm.loadShow(showId) }
            }
            if (tab == 1) item { About(show) }
            if (tab == 0 && items.isNotEmpty() && rows.isEmpty()) item {
                Text(
                    stringResource(if (audiobook) R.string.show_filter_all_chapters_played else R.string.show_filter_all_played),
                    color = AppText.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(16.dp),
                )
            }
            if (tab == 0 && page != null && items.isEmpty() && pageState !is Response.Loading) item {
                Text(
                    stringResource(R.string.show_empty),
                    color = AppText.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(16.dp),
                )
            }
            if (tab == 0) items(rows, key = { it.url }) { ep ->
                val epIsAudible = io.github.thake.soundnest.feature.spokenword.audible.data.AudibleUris.isAudibleUri(ep.url)
                val epPlayUri = if (epIsAudible) ep.url else "spotify:episode:${ep.url.removePrefix("episode:")}"
                val isCurrentRow = np.uri == epPlayUri
                val isPlayingRow = isCurrentRow && SongPlayer.isPlaying()
                if (audiobook) {
                    ChapterRow(
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
                    )
                } else {
                    EpisodeRow(
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
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun Header(
    navController: NavController,
    showId: String,
    show: PodcastModel?,
    fallbackName: String,
    cover: String,
    audiobook: Boolean,
    showFollow: Boolean = true,
    following: Boolean,
    onFollow: (Boolean) -> Unit,
    showPlayButton: Boolean = false,
    playButtonLabel: String = "",
    playButtonPlaying: Boolean = false,
    onPlayBook: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(headerTint(Color(0xFF33322F), 0f), AppBackground)))
            .statusBarsPadding(),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(12.dp)
                .wideClickable { navController.navigateUp() }
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.35f)),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.accessibility_navigate_back), tint = Color.White)
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            GlideImage(
                model = cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                failure = placeholder(R.drawable.placeholder),
                modifier = Modifier
                    .size(128.dp)
                    .clip(RoundedCornerShape(6.dp)),
            )
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(
                    text = show?.name?.takeIf { it.isNotBlank() } ?: fallbackName,
                    color = AppText,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                val authors = show?.authors.orEmpty().joinToString(", ")
                val narrators = show?.narrators.orEmpty().joinToString(", ")
                if (authors.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.show_authors, authors), color = AppText.copy(alpha = 0.8f), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (narrators.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(stringResource(R.string.show_narrators, narrators), color = AppText.copy(alpha = 0.65f), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (authors.isBlank() && show != null && show.publisher.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(show.publisher, color = AppText.copy(alpha = 0.8f), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (!show?.subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(show!!.subtitle, color = AppText.copy(alpha = 0.65f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (audiobook && show != null && show.runtimeMs > 0L) {
                    Spacer(Modifier.height(2.dp))
                    Text(formatBookRuntime(show.runtimeMs), color = AppText.copy(alpha = 0.65f), fontSize = 12.sp)
                }
            }
        }
        // Rating, as the official header shows it under the cover.
        val rating = show?.rating ?: 0.0
        if (rating > 0.0) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp),
            ) {
                Icon(Icons.Default.Star, contentDescription = null, tint = AppText.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = String.format(io.github.thake.soundnest.feature.appearance.settings.AppLanguage.currentLocale(), "%.1f", rating),
                    color = AppText.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                )
            }
        }
        if (showFollow || showPlayButton) {
            val resolvedName = show?.name?.takeIf { it.isNotBlank() } ?: fallbackName
            val showTarget = remember(showId, resolvedName, show?.publisher, cover) {
                io.github.thake.soundnest.feature.parental.ui.ParentBlockTarget(
                    io.github.thake.soundnest.feature.parental.ui.ParentRuleKind.SHOW,
                    showId,
                    resolvedName,
                    show?.publisher.orEmpty(),
                    cover,
                )
            }
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                itemVerticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                if (showPlayButton) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(AppAccent)
                            .pressable(0.96f, onClick = onPlayBook)
                            .padding(horizontal = 18.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (playButtonPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = playButtonLabel,
                                color = Color.Black,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                        }
                    }
                }
                if (showFollow) {
                    FollowPill(following) { onFollow(!following) }
                    if (!audiobook) {
                        io.github.thake.soundnest.feature.parental.ui.ParentInlineBlockPill(target = showTarget)
                    }
                }
            }
        } else {
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun FollowPill(following: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (following) AppText.copy(alpha = 0.12f) else Color.Transparent)
            .pressable(0.96f, onClick = onClick)
            .padding(horizontal = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (following) {
                Icon(Icons.Default.Check, contentDescription = null, tint = AppText, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = stringResource(if (following) R.string.show_following else R.string.show_follow),
                color = AppText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun TabLabel(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clickable(onClick = onClick).padding(vertical = 10.dp),
    ) {
        Text(
            text = text,
            color = if (selected) AppText else AppText.copy(alpha = 0.6f),
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .height(2.dp)
                .width(if (selected) 40.dp else 0.dp)
                .background(AppAccent),
        )
    }
}

@Composable
private fun About(show: PodcastModel?) {
    val authors = show?.authors.orEmpty().joinToString(", ")
    val narrators = show?.narrators.orEmpty().joinToString(", ")
    val released = formatReleaseDate(show?.releaseDate.orEmpty())
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            text = io.github.thake.soundnest.core.ui.look.rememberLinkedText(show?.description.orEmpty(), AppAccent),
            color = AppText.copy(alpha = 0.75f),
            fontSize = 14.sp,
        )
        if (authors.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.show_authors, authors), color = AppText.copy(alpha = 0.6f), fontSize = 13.sp)
        }
        if (narrators.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.show_narrators, narrators), color = AppText.copy(alpha = 0.6f), fontSize = 13.sp)
        }
        if (released.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.audiobook_released_date, released), color = AppText.copy(alpha = 0.6f), fontSize = 13.sp)
        }
    }
}

internal fun formatReleaseDate(raw: String): String {
    if (raw.isBlank()) return ""
    val locale = io.github.thake.soundnest.feature.appearance.settings.AppLanguage.effectiveLocale
    return runCatching {
        java.time.LocalDate.parse(raw.take(10)).format(
            java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withLocale(locale),
        )
    }.getOrDefault(raw)
}

/** One episode (or chapter): cover, title, snippet, date • length, then its own controls. */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun EpisodeRow(
    episode: SongsModel,
    playing: Boolean,
    finished: Boolean,
    isNew: Boolean,
    onPlay: () -> Unit,
    onOpen: () -> Unit,
    onMenu: () -> Unit,
) {
    val date = episodeDateLabel(episode.releaseDate)
    val minutes = episodeMinutes(episode.durationMs)
    Column(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row {
            GlideImage(
                model = episode.coverUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                failure = placeholder(R.drawable.placeholder),
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(4.dp)),
            )
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    text = episode.title,
                    color = if (playing) AppAccent else AppText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (episode.description.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = episode.description,
                        color = AppText.copy(alpha = 0.65f),
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isNew) {
                        Text(
                            stringResource(R.string.episode_new),
                            color = AppAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("•", color = AppText.copy(alpha = 0.4f), fontSize = 12.sp)
                        Spacer(Modifier.width(6.dp))
                    }
                    if (finished) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = AppAccent, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.episode_marked_played), color = AppText.copy(alpha = 0.6f), fontSize = 12.sp)
                        Spacer(Modifier.width(6.dp))
                        Text("•", color = AppText.copy(alpha = 0.4f), fontSize = 12.sp)
                        Spacer(Modifier.width(6.dp))
                    }
                    val started = episode.playedPositionMs > 0 && !finished
                    Text(
                        text = when {
                            date.isBlank() -> minutes
                            else -> "$date • $minutes"
                        },
                        color = AppText.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                    )
                    if (started) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.episode_remaining, episodeClock(episode.durationMs - episode.playedPositionMs)),
                            color = AppAccent,
                            fontSize = 12.sp,
                        )
                    }
                }
                if (!finished) {
                    Spacer(Modifier.height(6.dp))
                    EpisodeProgressBar(episode.playedPositionMs, episode.durationMs, AppAccent, AppText.copy(alpha = 0.15f))
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        ) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.episode_options),
                tint = AppText.copy(alpha = 0.8f),
                modifier = Modifier.size(22.dp).pressable(0.9f, onClick = onMenu),
            )
            Spacer(Modifier.weight(1f))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(40.dp).clip(CircleShape).background(AppText).pressable(0.92f, onClick = onPlay),
            ) {
                io.github.thake.soundnest.core.ui.look.PlayPauseIcon(
                    playing = playing,
                    contentDescription = stringResource(R.string.episode_play),
                    tint = io.github.thake.soundnest.core.ui.classic.AppBackground,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
