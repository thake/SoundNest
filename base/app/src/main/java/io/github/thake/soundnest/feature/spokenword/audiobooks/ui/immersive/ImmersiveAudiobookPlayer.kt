/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.spokenword.audiobooks.ui.immersive

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.di.Graph
import io.github.thake.soundnest.core.model.SongsModel
import io.github.thake.soundnest.core.playback.SongPlayer
import io.github.thake.soundnest.core.ui.immersive.Immersive
import io.github.thake.soundnest.core.ui.immersive.motion.ImmersiveMotion
import io.github.thake.soundnest.core.ui.look.MeshGradientBackground
import io.github.thake.soundnest.core.ui.look.overflowRightFade
import io.github.thake.soundnest.core.ui.look.rememberArtworkColors
import io.github.thake.soundnest.core.ui.look.rememberPauseScale
import io.github.thake.soundnest.core.ui.look.rememberReduceMotion
import io.github.thake.soundnest.core.ui.look.titleMarquee
import io.github.thake.soundnest.core.ui.look.windowSize
import io.github.thake.soundnest.feature.player.settings.QualitySheet
import io.github.thake.soundnest.feature.player.settings.playingSourceLabel
import io.github.thake.soundnest.feature.player.sleeptimer.ui.immersive.ImmersiveSleepTimerSheet
import io.github.thake.soundnest.feature.player.ui.immersive.CircleGlyph
import io.github.thake.soundnest.feature.player.ui.immersive.LocalPlayerTint
import io.github.thake.soundnest.feature.player.ui.immersive.PLAYER_GUTTER
import io.github.thake.soundnest.feature.player.ui.immersive.PLAYER_MAX_WIDTH
import io.github.thake.soundnest.feature.player.ui.immersive.PlayerDismissStrip
import io.github.thake.soundnest.feature.player.ui.immersive.PlayerScrubber
import io.github.thake.soundnest.feature.player.ui.immersive.dragToClose
import io.github.thake.soundnest.feature.player.ui.immersive.playerTintOf
import io.github.thake.soundnest.feature.player.ui.immersive.rememberImmersivePlayer
import io.github.thake.soundnest.feature.speakers.ui.DevicesSheet
import io.github.thake.soundnest.feature.spokenword.audible.data.AudibleProgressStore
import io.github.thake.soundnest.feature.spokenword.audible.data.AudibleUris
import io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayer
import io.github.thake.soundnest.feature.spokenword.audiobooks.ui.AudiobookChaptersSheet
import io.github.thake.soundnest.feature.spokenword.audiobooks.ui.ChapterOptionsSheet
import io.github.thake.soundnest.feature.spokenword.audiobooks.ui.audiobookSubtitle
import io.github.thake.soundnest.feature.spokenword.ui.PodcastSpeedSheet
import io.github.thake.soundnest.feature.spokenword.ui.classic.openEpisodeShow
import io.github.thake.soundnest.feature.spokenword.ui.immersive.BottomGlyph
import io.github.thake.soundnest.feature.spokenword.ui.immersive.PODCAST_ART_TITLE_GAP
import io.github.thake.soundnest.feature.spokenword.ui.immersive.PODCAST_ART_TOP_PAD
import io.github.thake.soundnest.feature.spokenword.ui.immersive.PODCAST_COVER_DECODE_PX
import io.github.thake.soundnest.feature.spokenword.ui.immersive.PODCAST_SKIP_MS
import io.github.thake.soundnest.feature.spokenword.ui.immersive.PODCAST_TITLE_HEIGHT
import io.github.thake.soundnest.feature.spokenword.ui.immersive.PodcastArtCorner
import io.github.thake.soundnest.feature.spokenword.ui.immersive.SkipButton
import io.github.thake.soundnest.feature.spokenword.ui.immersive.SpeedChip
import io.github.thake.soundnest.feature.spokenword.ui.immersive.TransportButton
import io.github.thake.soundnest.feature.spokenword.ui.speedLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val AudiobookTitleHeight = 80.dp

/**
 * Dedicated Immersive audiobook player: book title at top, "Book • Author" subtitle,
 * 5-button chapter + ±15s transport, speed, end-of-chapter sleep timer, and Chapters ToC sheet.
 */
@OptIn(ExperimentalGlideComposeApi::class, ExperimentalFoundationApi::class)
@Composable
fun ImmersiveAudiobookPlayer(navController: NavController) {
    val context = LocalContext.current
    val bridge = rememberImmersivePlayer(navController)
    val state = bridge.state
    val actions = bridge.actions
    val np by SongPlayer.nowPlaying
    val title = state.title
    val author = state.artist
    val bookTitle = np.contextName.ifBlank { state.album }.ifBlank { author }
    val subtitle = audiobookSubtitle(bookTitle, author)
    val cover = Graph.currentSongState.coverUri.value
    val isAudible = AudibleUris.isAudibleUri(np.uri) || AudiblePlayer.active.value
    val progressRevision by AudibleProgressStore.revision.collectAsState()

    val chapters = remember(isAudible, np.uri, np.contextUri, Graph.currentSongState.queue.value, progressRevision) {
        if (isAudible) {
            AudiblePlayer.currentChapters(context).ifEmpty { Graph.currentSongState.queue.value }
        } else {
            Graph.currentSongState.queue.value.ifEmpty {
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
    var showQuality by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val palette = rememberArtworkColors(state.coverUrl.takeIf { it.isNotBlank() })
    val tint by animateColorAsState(playerTintOf(palette), tween(ImmersiveMotion.TINT_MS), label = "audiobookTint")
    val reduce = rememberReduceMotion()
    val pauseScale = rememberPauseScale(state.playing)

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

    val coverArt: @Composable (Dp) -> Unit = { size ->
        Box(
            Modifier
                .size(size)
                .graphicsLayer {
                    val scale = if (reduce) 1f else pauseScale.value
                    scaleX = scale
                    scaleY = scale
                    shadowElevation = 10.dp.toPx()
                    shape = PodcastArtCorner
                    clip = true
                }
                .background(Color.Black.copy(alpha = 0.18f))
                .dragToClose(),
        ) {
            GlideImage(
                model = state.coverUrl.takeIf { it.isNotBlank() },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                loading = placeholder(R.drawable.placeholder),
                failure = placeholder(R.drawable.placeholder),
                modifier = Modifier.fillMaxSize(),
                requestBuilderTransform = { it.override(PODCAST_COVER_DECODE_PX) },
            )
        }
    }

    val credits: @Composable RowScope.() -> Unit = {
        val sourceLabel = playingSourceLabel()
        var titleOverflows by remember(title) { mutableStateOf(false) }
        var subOverflows by remember(subtitle) { mutableStateOf(false) }
        Column(Modifier.weight(1f).wrapContentHeight(unbounded = true)) {
            if (currentChapterIdx != null && chapters.size > 1) {
                Text(
                    text = stringResource(R.string.audiobook_chapter_of, currentChapterIdx + 1, chapters.size),
                    style = Immersive.Caption.copy(lineHeight = 14.sp),
                    color = tint.copy(alpha = 0.55f),
                    maxLines = 1,
                )
            }
            Text(
                text = title,
                style = Immersive.Title.copy(fontSize = 20.sp, lineHeight = 22.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { if (it.hasVisualOverflow) titleOverflows = true },
                modifier = Modifier
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { openBook() }
                    .overflowRightFade(titleOverflows)
                    .titleMarquee(iterations = Int.MAX_VALUE),
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = Immersive.Title.copy(
                        fontSize = 18.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.W500,
                    ),
                    color = tint.copy(alpha = 0.60f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { if (it.hasVisualOverflow) subOverflows = true },
                    modifier = Modifier
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { openBook() }
                        .overflowRightFade(subOverflows)
                        .titleMarquee(iterations = Int.MAX_VALUE),
                )
            }
            if (sourceLabel.isNotBlank()) {
                Text(
                    text = sourceLabel,
                    style = Immersive.Caption.copy(lineHeight = 16.sp),
                    color = tint.copy(alpha = 0.45f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (isAudible) Modifier else Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { showQuality = true },
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        CircleGlyph(
            icon = Icons.Rounded.MoreHoriz,
            contentDescription = stringResource(R.string.chapter_options),
            onClick = { showMenu = true },
        )
    }

    val controls: @Composable () -> Unit = {
        PlayerScrubber(state = state, onSeek = actions.seekTo)
        Spacer(Modifier.height(8.dp))
        AudiobookTransportRow(
            isPlaying = state.playing,
            isLoading = state.buffering,
            onPrevChapter = { SongPlayer.previous() },
            onBack15 = { SongPlayer.skipBy(-PODCAST_SKIP_MS) },
            onPlayPause = actions.playPause,
            onForward15 = { SongPlayer.skipBy(PODCAST_SKIP_MS) },
            onNextChapter = { SongPlayer.next() },
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SpeedChip(label = stringResource(R.string.player_speed_chip, speedLabel(SongPlayer.speed.value)), onClick = { showSpeed = true })
            BottomGlyph(
                icon = Icons.Rounded.Timer,
                contentDescription = stringResource(R.string.player_sleep_timer),
                onClick = { showSleep = true },
                highlighted = SongPlayer.sleepEndsAt.value != null,
            )
            BottomGlyph(
                icon = Icons.Rounded.Devices,
                contentDescription = stringResource(R.string.immersive_player_devices),
                onClick = { showDevices = true },
                highlighted = state.deviceName != null,
            )
            BottomGlyph(
                icon = Icons.AutoMirrored.Rounded.FormatListBulleted,
                contentDescription = stringResource(R.string.audiobook_chapters_sheet_title),
                onClick = { showChapters = true },
            )
        }
        Spacer(Modifier.height(18.dp))
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val landscape = maxWidth > maxHeight || windowSize().twoPane
        MeshGradientBackground(palette = palette, trackKey = state.coverUrl)
        CompositionLocalProvider(LocalPlayerTint provides tint) {
            if (landscape) Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                PlayerDismissStrip(tint) { actions.close() }
                Row(
                    Modifier.weight(1f).fillMaxWidth().padding(start = PLAYER_GUTTER, end = PLAYER_GUTTER, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(PLAYER_GUTTER),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BoxWithConstraints(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        coverArt(minOf(maxWidth, maxHeight))
                    }
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        Column(Modifier.widthIn(max = PLAYER_MAX_WIDTH).verticalScroll(rememberScrollState())) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { openBook() },
                            ) {
                                Text(stringResource(R.string.audiobook_playing_from), style = Immersive.Caption, color = tint.copy(alpha = 0.55f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    bookTitle,
                                    style = Immersive.BodyStrong.copy(fontSize = 14.sp, lineHeight = 18.sp),
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.titleMarquee(),
                                )
                            }
                            Row(Modifier.fillMaxWidth().height(AudiobookTitleHeight), verticalAlignment = Alignment.CenterVertically) { credits() }
                            Spacer(Modifier.height(8.dp))
                            controls()
                        }
                    }
                }
            } else Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                PlayerDismissStrip(tint) { actions.close() }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PLAYER_GUTTER)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { openBook() },
                ) {
                    Text(
                        stringResource(R.string.audiobook_playing_from),
                        style = Immersive.Caption,
                        color = tint.copy(alpha = 0.55f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        bookTitle,
                        style = Immersive.BodyStrong.copy(fontSize = 14.sp, lineHeight = 18.sp),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.titleMarquee(),
                    )
                }

                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val contentWidth = minOf(maxWidth - PLAYER_GUTTER * 2, PLAYER_MAX_WIDTH)
                    val art = minOf(contentWidth, maxHeight - PODCAST_ART_TOP_PAD - PODCAST_ART_TITLE_GAP - AudiobookTitleHeight - 16.dp).coerceAtLeast(96.dp)
                    Column(
                        modifier = Modifier.fillMaxSize().padding(top = PODCAST_ART_TOP_PAD),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        coverArt(art)
                        Spacer(Modifier.height(PODCAST_ART_TITLE_GAP))
                        Row(
                            modifier = Modifier.width(contentWidth).height(AudiobookTitleHeight).dragToClose(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            credits()
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .widthIn(max = PLAYER_MAX_WIDTH + PLAYER_GUTTER * 2)
                        .fillMaxWidth()
                        .padding(horizontal = PLAYER_GUTTER)
                        .align(Alignment.CenterHorizontally),
                ) {
                    controls()
                }
            }
        }
    }

    if (showSpeed) PodcastSpeedSheet { showSpeed = false }
    if (showSleep) ImmersiveSleepTimerSheet { showSleep = false }
    if (showDevices) DevicesSheet { showDevices = false }
    if (showQuality) QualitySheet { showQuality = false }
    if (showChapters) {
        AudiobookChaptersSheet(
            bookTitle = bookTitle,
            chapters = chapters,
            currentUri = np.uri,
            playing = state.playing,
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
            durationMs = state.durationMs.toInt(),
        )
        ChapterOptionsSheet(
            chapter = currentSong,
            bookTitle = bookTitle,
            finished = isChapterFinished(currentSong),
            started = state.positionMs() > 0L,
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
}

@Composable
private fun AudiobookTransportRow(
    isPlaying: Boolean,
    isLoading: Boolean,
    onPrevChapter: () -> Unit,
    onBack15: () -> Unit,
    onPlayPause: () -> Unit,
    onForward15: () -> Unit,
    onNextChapter: () -> Unit,
) {
    val playTouch = 88.dp
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TransportButton(
            icon = Icons.Rounded.SkipPrevious,
            contentDescription = stringResource(R.string.audiobook_previous_chapter),
            size = 40.dp,
            touchSize = 52.dp,
            onClick = onPrevChapter,
        )
        SkipButton(back = true, onClick = onBack15, iconSize = 48.dp, touchSize = 60.dp)
        if (isLoading) {
            Box(Modifier.size(playTouch), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(38.dp))
            }
        } else {
            TransportButton(
                icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = stringResource(if (isPlaying) R.string.immersive_player_pause else R.string.immersive_player_play),
                size = 80.dp,
                touchSize = playTouch,
                onClick = onPlayPause,
            )
        }
        SkipButton(back = false, onClick = onForward15, iconSize = 48.dp, touchSize = 60.dp)
        TransportButton(
            icon = Icons.Rounded.SkipNext,
            contentDescription = stringResource(R.string.audiobook_next_chapter),
            size = 40.dp,
            touchSize = 52.dp,
            onClick = onNextChapter,
        )
    }
}
