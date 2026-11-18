/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.spokenword.audiobooks.ui.classic

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.di.Graph
import io.github.thake.soundnest.core.model.SongsModel
import io.github.thake.soundnest.core.playback.SongPlayer
import io.github.thake.soundnest.core.ui.classic.AppAccent
import io.github.thake.soundnest.core.ui.classic.AppBackground
import io.github.thake.soundnest.core.ui.classic.AppMuted
import io.github.thake.soundnest.core.ui.look.MinTouch
import io.github.thake.soundnest.core.ui.look.PlayPauseIcon
import io.github.thake.soundnest.core.ui.look.pressable
import io.github.thake.soundnest.core.ui.look.pullToDismiss
import io.github.thake.soundnest.core.ui.look.rememberPullToDismiss
import io.github.thake.soundnest.core.ui.look.titleMarquee
import io.github.thake.soundnest.core.ui.navigation.PlayerSheet
import io.github.thake.soundnest.core.util.Palette
import io.github.thake.soundnest.feature.player.settings.PlayerSourceLinePref
import io.github.thake.soundnest.feature.player.settings.QualitySheet
import io.github.thake.soundnest.feature.player.settings.playingSourceLabel
import io.github.thake.soundnest.feature.player.sleeptimer.ui.classic.SleepTimerSheet
import io.github.thake.soundnest.feature.speakers.ui.DevicesSheet
import io.github.thake.soundnest.feature.speakers.ui.activeDeviceIcon
import io.github.thake.soundnest.feature.speakers.ui.activeDeviceLabel
import io.github.thake.soundnest.feature.spokenword.audible.data.AudibleProgressStore
import io.github.thake.soundnest.feature.spokenword.audible.data.AudibleUris
import io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayer
import io.github.thake.soundnest.feature.spokenword.audiobooks.ui.AudiobookChaptersSheet
import io.github.thake.soundnest.feature.spokenword.audiobooks.ui.ChapterOptionsSheet
import io.github.thake.soundnest.feature.spokenword.audiobooks.ui.audiobookSubtitle
import io.github.thake.soundnest.feature.spokenword.ui.PodcastSpeedSheet
import io.github.thake.soundnest.feature.spokenword.ui.classic.PodcastProgress
import io.github.thake.soundnest.feature.spokenword.ui.classic.SkipIcon
import io.github.thake.soundnest.feature.spokenword.ui.classic.openEpisodeShow
import io.github.thake.soundnest.feature.spokenword.ui.speedLabel
import io.github.thake.soundnest.feature.system.ui.StatsForNerdsSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dedicated Classic audiobook player: displays book title in the top bar, "Chapter X of Y",
 * "Book • Author" subtitle, 5-button transport (prev chapter, -15s, play/pause, +15s, next chapter),
 * speed, end-of-chapter sleep timer, and a Chapters table-of-contents sheet.
 */
@OptIn(ExperimentalGlideComposeApi::class, ExperimentalFoundationApi::class)
@Composable
fun AudiobookPlayerScreen(navController: NavController) {
    val context = LocalContext.current
    val state = Graph.currentSongState
    val np by SongPlayer.nowPlaying
    val title = state.title.value
    val author = state.singer.value
    val bookTitle = np.contextName.ifBlank { state.album.value }.ifBlank { author }
    val subtitle = audiobookSubtitle(bookTitle, author)
    val cover = state.coverUri.value
    val playing = state.playingState.value
    val isAudible = AudibleUris.isAudibleUri(np.uri) || AudiblePlayer.active.value
    val progressRevision by AudibleProgressStore.revision.collectAsState()

    val chapters = remember(isAudible, np.uri, np.contextUri, state.queue.value, progressRevision) {
        if (isAudible) {
            AudiblePlayer.currentChapters(context).ifEmpty { state.queue.value }
        } else {
            state.queue.value.ifEmpty {
                val q = SongPlayer.engineQueue.value
                listOfNotNull(q.current) + q.nextUp.map { it.song }
            }
        }
    }
    val currentChapterIdx = remember(isAudible, np.uri, chapters) {
        val byUri = AudibleUris.chapterIndexFromUri(np.uri)
        if (byUri != null) byUri else chapters.indexOfFirst { it.url == np.uri || "spotify:${it.url}" == np.uri }.takeIf { it >= 0 }
    }

    var showSpeed by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showDevices by remember { mutableStateOf(false) }
    var showChapters by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val duration = SongPlayer.getDuration()

    fun openBook() {
        openEpisodeShow(navController, np.contextUri, np.uri, bookTitle)
    }

    fun isChapterFinished(ch: SongsModel): Boolean {
        if (!isAudible) return ch.played
        val asin = AudibleUris.asinFromAny(ch.url) ?: return ch.played
        val idx = AudibleUris.chapterIndexFromUri(ch.url) ?: return ch.played
        return AudibleProgressStore.get(context, asin)?.finishedChapters?.contains(idx) ?: ch.played
    }

    fun markChapterFinished(ch: SongsModel, finished: Boolean) {
        if (isAudible) {
            val asin = AudibleUris.asinFromAny(ch.url)
            val idx = AudibleUris.chapterIndexFromUri(ch.url)
            if (asin != null && idx != null) {
                AudibleProgressStore.setChapterFinished(context, asin, idx, finished)
            }
        } else {
            val epUri = if (ch.url.startsWith("spotify:")) ch.url else "spotify:episode:${ch.url.removePrefix("episode:")}"
            scope.launch {
                withContext(Dispatchers.IO) { runCatching { Graph.api.setEpisodePlayed(epUri, finished) } }
            }
        }
    }

    if (showSpeed) PodcastSpeedSheet { showSpeed = false }
    if (showSleep) SleepTimerSheet { showSleep = false }
    if (showDevices) DevicesSheet { showDevices = false }
    if (showChapters) {
        AudiobookChaptersSheet(
            bookTitle = bookTitle,
            chapters = chapters,
            currentUri = np.uri,
            playing = playing,
            isFinished = ::isChapterFinished,
            onSelectChapter = { idx, ch ->
                if (isAudible) AudiblePlayer.skipToChapter(idx)
                else SongPlayer.playSong(ch.url, context, contextUri = np.contextUri)
            },
            onDismiss = { showChapters = false },
        )
    }
    if (showMenu) {
        val currentSong = SongsModel(
            id = ("uri:${np.uri}").hashCode() and 0x7fffffff,
            title = title,
            album = bookTitle,
            singer = author,
            coverUri = cover,
            url = np.uri,
            durationMs = duration.toInt(),
        )
        ChapterOptionsSheet(
            chapter = currentSong,
            bookTitle = bookTitle,
            finished = isChapterFinished(currentSong),
            started = SongPlayer.getCurrentPosition() > 0L,
            onPlay = { SongPlayer.play() },
            onRestart = {
                SongPlayer.seekTo(0L)
                SongPlayer.play()
            },
            onMarkFinished = { played -> markChapterFinished(currentSong, played) },
            onOpenBook = { openBook() },
            onDismiss = { showMenu = false },
        )
    }

    var coverColor by remember { mutableStateOf(Palette.cachedSecond(cover) ?: Color(0xFF3A3630)) }
    LaunchedEffect(cover) {
        if (cover.isNotBlank()) Palette().extractSecondColorFromCoverUrl(context, cover) { coverColor = it }
    }
    val tint by animateColorAsState(coverColor, tween(450), label = "audiobookCoverTint")

    val pullToClose = rememberPullToDismiss(PlayerSheet.open.value) { PlayerSheet.close() }
    Box(
        Modifier
            .fillMaxSize()
            .pullToDismiss(pullToClose)
            .graphicsLayer { translationY = pullToClose.offset }
            .background(Brush.verticalGradient(listOf(tint, AppBackground))),
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // Top bar: down chevron, "Playing from the audiobook" + book title, chapter options.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_down),
                    contentDescription = stringResource(R.string.a11y_close_player),
                    tint = Color.White,
                    modifier = Modifier.size(26.dp).clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { PlayerSheet.close() },
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { openBook() },
                ) {
                    Text(
                        stringResource(R.string.audiobook_playing_from),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        bookTitle,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.titleMarquee(iterations = 3),
                    )
                }
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.chapter_options),
                    tint = Color.White,
                    modifier = Modifier.size(24.dp).pressable(0.9f) { showMenu = true },
                )
            }

            Spacer(Modifier.weight(1f))
            GlideImage(
                model = cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth(0.80f)
                    .aspectRatio(1f)
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Spacer(Modifier.weight(1f))

            // Chapter indicator, chapter title, and "Book • Author".
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
                if (currentChapterIdx != null && chapters.size > 1) {
                    Text(
                        text = stringResource(R.string.audiobook_chapter_of, currentChapterIdx + 1, chapters.size),
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(2.dp))
                }
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { openBook() }
                            .titleMarquee(iterations = 3),
                    )
                }
            }

            PodcastProgress(playing = playing, uri = np.uri, duration = duration)

            // 5-button audiobook transport: Prev chapter, -15s, Play/Pause, +15s, Next chapter.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp).height(64.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = stringResource(R.string.audiobook_previous_chapter),
                    tint = Color.White,
                    modifier = Modifier.size(32.dp).pressable(0.9f) { SongPlayer.previous() },
                )
                SkipIcon(back = true)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .pressable(0.92f) {
                            if (playing) SongPlayer.pause() else SongPlayer.play()
                        },
                ) {
                    PlayPauseIcon(
                        playing = playing,
                        contentDescription = stringResource(if (playing) R.string.notif_action_pause else R.string.notif_action_play),
                        tint = Color.Black,
                        modifier = Modifier.size(30.dp),
                    )
                }
                SkipIcon(back = false)
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = stringResource(R.string.audiobook_next_chapter),
                    tint = Color.White,
                    modifier = Modifier.size(32.dp).pressable(0.9f) { SongPlayer.next() },
                )
            }

            // Bottom utility row: active device + source, speed, sleep timer, and Chapters ToC.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = MinTouch)
                    .padding(start = 20.dp, end = 12.dp, top = 4.dp, bottom = 12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = MinTouch)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { showDevices = true },
                ) {
                    Icon(
                        imageVector = activeDeviceIcon(),
                        tint = AppAccent,
                        modifier = Modifier.size(22.dp),
                        contentDescription = stringResource(R.string.mr_user_route_category_name),
                    )
                    Column(modifier = Modifier.padding(start = 8.dp).widthIn(max = 180.dp)) {
                        Text(
                            text = activeDeviceLabel(),
                            color = AppAccent,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val source = playingSourceLabel()
                        var quality by remember { mutableStateOf(false) }
                        var nerd by remember { mutableStateOf(false) }
                        if (quality) QualitySheet { quality = false }
                        if (nerd) StatsForNerdsSheet { nerd = false }
                        if (source.isNotBlank() && PlayerSourceLinePref.shown) {
                            Text(
                                text = source,
                                color = AppMuted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.combinedClickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onLongClick = { nerd = true },
                                    onClick = { if (!isAudible) quality = true },
                                ),
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(width = 44.dp, height = 28.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                            .pressable(0.92f) { showSpeed = true },
                    ) {
                        Text(
                            stringResource(R.string.player_speed_chip, speedLabel(SongPlayer.speed.value)),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Icon(
                        Icons.Outlined.Timer,
                        contentDescription = stringResource(R.string.player_sleep_timer),
                        tint = if (SongPlayer.sleepEndsAt.value != null) AppAccent else Color.White,
                        modifier = Modifier.size(MinTouch).pressable(0.9f) { showSleep = true }.wrapContentSize().size(24.dp),
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.FormatListBulleted,
                        contentDescription = stringResource(R.string.audiobook_chapters_sheet_title),
                        tint = Color.White,
                        modifier = Modifier.size(MinTouch).pressable(0.9f) { showChapters = true }.wrapContentSize().size(24.dp),
                    )
                }
            }
        }
    }
}
