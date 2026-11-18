package io.github.thake.soundnest.feature.spokenword.ui.immersive

import io.github.thake.soundnest.feature.spokenword.ui.PodcastSpeedSheet
import io.github.thake.soundnest.feature.spokenword.ui.speedLabel

import io.github.thake.soundnest.feature.player.ui.immersive.CircleGlyph
import io.github.thake.soundnest.feature.player.ui.immersive.LocalPlayerTint
import io.github.thake.soundnest.feature.player.ui.immersive.PLAYER_GUTTER
import io.github.thake.soundnest.feature.player.ui.immersive.PLAYER_MAX_WIDTH
import io.github.thake.soundnest.feature.player.ui.immersive.PlayerDismissStrip
import io.github.thake.soundnest.feature.player.ui.immersive.PlayerScrubber
import io.github.thake.soundnest.feature.player.ui.immersive.dragToClose
import io.github.thake.soundnest.feature.player.ui.immersive.playerTintOf
import io.github.thake.soundnest.feature.player.ui.immersive.rememberImmersivePlayer

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RotateLeft
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.github.thake.soundnest.R
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import io.github.thake.soundnest.core.model.SongsModel
import io.github.thake.soundnest.core.di.Graph
import io.github.thake.soundnest.core.util.Haptics
import io.github.thake.soundnest.core.playback.SongPlayer
import io.github.thake.soundnest.feature.speakers.ui.DevicesSheet
import io.github.thake.soundnest.feature.spokenword.ui.EpisodeOptionsSheet
import io.github.thake.soundnest.feature.player.settings.QualitySheet
import io.github.thake.soundnest.feature.player.ui.classic.QueueSheet
import io.github.thake.soundnest.feature.player.sleeptimer.ui.classic.SleepTimerSheet
import io.github.thake.soundnest.core.ui.look.overflowRightFade
import io.github.thake.soundnest.feature.player.settings.playingSourceLabel
import io.github.thake.soundnest.core.ui.look.titleMarquee
import io.github.thake.soundnest.core.ui.immersive.Immersive
import io.github.thake.soundnest.core.ui.look.MeshGradientBackground
import io.github.thake.soundnest.core.ui.look.rememberArtworkColors
import io.github.thake.soundnest.core.ui.look.rememberPauseScale
import io.github.thake.soundnest.core.ui.look.rememberReduceMotion
import io.github.thake.soundnest.core.ui.navigation.episodeRoute
import io.github.thake.soundnest.core.ui.navigation.showRoute
import io.github.thake.soundnest.feature.spokenword.ui.classic.episodeShowId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The height of the line holding the title, the show and the round glyphs. */
internal val PODCAST_TITLE_HEIGHT = 68.dp
internal val PODCAST_ART_TITLE_GAP = 20.dp
internal val PODCAST_ART_TOP_PAD = 8.dp
internal val PodcastArtCorner = RoundedCornerShape(8.dp)

/** The cover is always decoded at the size of the largest cover, whatever the box measures. */
internal const val PODCAST_COVER_DECODE_PX = 640

/** A podcast skip, in milliseconds. */
internal const val PODCAST_SKIP_MS = 15_000L

/**
 * The player of a podcast episode in the Immersive look, in the music player's frame: the
 * cover's colours drifting behind a large cover, the episode and its show, the scrubber, then
 * the podcast transport (15 s back, play/pause, 15 s forward) and a row of speed, sleep timer,
 * devices and queue. The state and every action are the Classic `PodcastPlayerScreen`'s: the
 * same playback, saved state, sheets and links to the show and the episode.
 */
@OptIn(ExperimentalGlideComposeApi::class, ExperimentalFoundationApi::class)
@Composable
fun ImmersivePodcastPlayer(navController: NavController) {
    val bridge = rememberImmersivePlayer(navController)
    val state = bridge.state
    val actions = bridge.actions
    val np by SongPlayer.nowPlaying
    val title = state.title
    val showName = state.artist.ifBlank { np.contextName }
    val cover = Graph.currentSongState.coverUri.value
    val episodeId = np.uri.removePrefix("spotify:episode:")
    val uri = "spotify:episode:$episodeId"
    val saved = SongPlayer.library[uri] == true
    LaunchedEffect(uri) {
        SongPlayer.requestSaved(uri)
    }

    var showSpeed by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showDevices by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showQuality by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val palette = rememberArtworkColors(state.coverUrl.takeIf { it.isNotBlank() })
    val tint by animateColorAsState(playerTintOf(palette), tween(io.github.thake.soundnest.core.ui.immersive.motion.ImmersiveMotion.TINT_MS), label = "podcastTint")
    val reduce = rememberReduceMotion()
    val pauseScale = rememberPauseScale(state.playing)

    fun openShow() {
        io.github.thake.soundnest.feature.spokenword.ui.classic.openEpisodeShow(navController, np.contextUri, np.uri, showName)
    }

    val coverArt: @Composable (Dp) -> Unit = { size ->
        Box(
            Modifier
                .size(size)
                .graphicsLayer {
                    // The pause step back, as on the music player.
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
    // The episode and its show, the star of the saved episodes and the episode's menu.
    val credits: @Composable RowScope.() -> Unit = {
        // The source line's room is kept from the start (it is known a moment after the
        // episode starts): it fades in below the title and the show instead of moving them.
        val sourceLabel = playingSourceLabel()
        var titleOverflows by remember(title) { mutableStateOf(false) }
        var showOverflows by remember(showName) { mutableStateOf(false) }
        Column(Modifier.weight(1f).wrapContentHeight(unbounded = true)) {
            Text(
                text = title,
                style = Immersive.Title.copy(fontSize = 20.sp, lineHeight = 22.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { if (it.hasVisualOverflow) titleOverflows = true },
                modifier = Modifier
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        navController.navigate(episodeRoute(episodeId, title))
                    }
                    .overflowRightFade(titleOverflows)
                    .titleMarquee(iterations = Int.MAX_VALUE),
            )
            Text(
                text = showName,
                style = Immersive.Title.copy(
                    fontSize = 20.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.W500,
                ),
                color = tint.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { if (it.hasVisualOverflow) showOverflows = true },
                modifier = Modifier
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { openShow() }
                    .overflowRightFade(showOverflows)
                    .titleMarquee(iterations = Int.MAX_VALUE),
            )
            // What the sound is when SoundNest's own player plays it; a tap opens its quality.
            if (sourceLabel.isNotBlank()) Text(
                text = sourceLabel,
                style = Immersive.Caption.copy(lineHeight = 16.sp),
                color = tint.copy(alpha = 0.45f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { showQuality = true },
            )
        }
        Spacer(Modifier.width(10.dp))
        CircleGlyph(
            icon = if (saved) Icons.Rounded.Star else Icons.Rounded.StarBorder,
            contentDescription = stringResource(if (saved) R.string.immersive_player_unlike else R.string.immersive_player_like),
            onClick = { SongPlayer.setSaved(uri, !saved) },
            active = saved,
        )
        Spacer(Modifier.width(8.dp))
        CircleGlyph(
            icon = Icons.Rounded.MoreHoriz,
            contentDescription = stringResource(R.string.episode_options),
            onClick = { showMenu = true },
        )
    }
    // Scrubber, podcast transport and the row of speed, sleep timer, devices and queue.
    val controls: @Composable () -> Unit = {
        PlayerScrubber(state = state, onSeek = actions.seekTo)
        Spacer(Modifier.height(8.dp))
        PodcastTransportRow(
            isPlaying = state.playing,
            isLoading = state.buffering,
            onBack = { SongPlayer.skipBy(-PODCAST_SKIP_MS) },
            onPlayPause = actions.playPause,
            onForward = { SongPlayer.skipBy(PODCAST_SKIP_MS) },
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
                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                contentDescription = stringResource(R.string.immersive_player_queue),
                onClick = { showQueue = true },
            )
        }
        Spacer(Modifier.height(18.dp))
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        // Wider than tall (a phone on its side, a tablet): the cover beside the controls.
        val landscape = maxWidth > maxHeight || io.github.thake.soundnest.core.ui.look.windowSize().twoPane
        MeshGradientBackground(palette = palette, trackKey = state.coverUrl)
        CompositionLocalProvider(LocalPlayerTint provides tint) {
            if (landscape) Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                // The handle: always hands its drags to the sheet.
                PlayerDismissStrip(tint) { actions.close() }
                Row(
                    Modifier.weight(1f).fillMaxWidth().padding(start = PLAYER_GUTTER, end = PLAYER_GUTTER, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(PLAYER_GUTTER),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BoxWithConstraints(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        coverArt(minOf(maxWidth, maxHeight))
                    }
                    // Scrolls if a small window cannot hold the right-hand side: no control is out of reach.
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        Column(Modifier.widthIn(max = PLAYER_MAX_WIDTH).verticalScroll(rememberScrollState())) {
                            // Where the episode plays from: a tap opens the show.
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { openShow() },
                            ) {
                                Text(stringResource(R.string.podcast_playing_from), style = Immersive.Caption, color = tint.copy(alpha = 0.55f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    showName,
                                    style = Immersive.BodyStrong.copy(fontSize = 14.sp, lineHeight = 18.sp),
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.titleMarquee(),
                                )
                            }
                            Row(Modifier.fillMaxWidth().height(PODCAST_TITLE_HEIGHT), verticalAlignment = Alignment.CenterVertically) { credits() }
                            Spacer(Modifier.height(8.dp))
                            controls()
                        }
                    }
                }
            } else Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                // The handle: always hands its drags to the sheet.
                PlayerDismissStrip(tint) { actions.close() }

                // Where the episode plays from: a tap opens the show.
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PLAYER_GUTTER)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { openShow() },
                ) {
                    Text(
                        stringResource(R.string.podcast_playing_from),
                        style = Immersive.Caption,
                        color = tint.copy(alpha = 0.55f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        showName,
                        style = Immersive.BodyStrong.copy(fontSize = 14.sp, lineHeight = 18.sp),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.titleMarquee(),
                    )
                }

                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val contentWidth = minOf(maxWidth - PLAYER_GUTTER * 2, PLAYER_MAX_WIDTH)
                    val art = minOf(contentWidth, maxHeight - PODCAST_ART_TOP_PAD - PODCAST_ART_TITLE_GAP - PODCAST_TITLE_HEIGHT - 16.dp).coerceAtLeast(96.dp)
                    Column(
                        modifier = Modifier.fillMaxSize().padding(top = PODCAST_ART_TOP_PAD),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        coverArt(art)
                        Spacer(Modifier.height(PODCAST_ART_TITLE_GAP))
                        // The episode and its show, the star of the saved episodes and the episode's menu.
                        Row(
                            modifier = Modifier.width(contentWidth).height(PODCAST_TITLE_HEIGHT).dragToClose(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            credits()
                        }
                    }
                }

                // ---- Bottom: scrubber, podcast transport, speed / sleep / devices / queue ----
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
    if (showSleep) SleepTimerSheet { showSleep = false }
    if (showDevices) DevicesSheet { showDevices = false }
    if (showQueue) QueueSheet(contextName = showName) { showQueue = false }
    if (showQuality) QualitySheet { showQuality = false }
    if (showMenu) {
        val song = SongsModel(
            id = ("uri:$uri").hashCode() and 0x7fffffff,
            title = title,
            album = showName,
            singer = showName,
            coverUri = cover,
            url = "episode:$episodeId",
            durationMs = state.durationMs.toInt(),
        )
        EpisodeOptionsSheet(
            song = song,
            finished = false,
            onPlay = { SongPlayer.play() },
            onMarkFinished = { played ->
                scope.launch {
                    withContext(Dispatchers.IO) { runCatching { Graph.api.setEpisodePlayed(uri, played) } }
                }
            },
            onOpen = { navController.navigate(episodeRoute(episodeId, title)) },
            onDismiss = { showMenu = false },
        )
    }
}

/** 15 s back, play/pause, 15 s forward: bare glyphs, no backgrounds, as the music player's transport. */
@Composable
private fun PodcastTransportRow(
    isPlaying: Boolean,
    isLoading: Boolean,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onForward: () -> Unit,
) {
    val playTouch = 92.dp
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkipButton(back = true, onClick = onBack)
        if (isLoading) {
            // Same footprint as the play/pause target: a smaller box would shunt everything below it.
            Box(Modifier.size(playTouch), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(38.dp))
            }
        } else {
            TransportButton(
                icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = stringResource(if (isPlaying) R.string.immersive_player_pause else R.string.immersive_player_play),
                size = 88.dp,
                touchSize = playTouch,
                onClick = onPlayPause,
            )
        }
        SkipButton(back = false, onClick = onForward)
    }
}

/** The circular arrow with the number inside, as the official player draws a 15 s skip. */
@Composable
internal fun SkipButton(
    back: Boolean,
    onClick: () -> Unit,
    iconSize: Dp = 56.dp,
    touchSize: Dp = 72.dp,
) {
    Box(contentAlignment = Alignment.Center) {
        TransportButton(
            icon = if (back) Icons.Rounded.RotateLeft else Icons.Rounded.RotateRight,
            contentDescription = stringResource(if (back) R.string.player_back_15 else R.string.player_forward_15),
            size = iconSize,
            touchSize = touchSize,
            onClick = onClick,
        )
        Text(
            text = "15",
            style = Immersive.Caption.copy(fontSize = 12.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold),
            color = Color.White,
            textAlign = TextAlign.Center,
        )
    }
}

/** A transport glyph: the touch target takes the press, the icon is never clipped. */
@Composable
internal fun TransportButton(icon: ImageVector, contentDescription: String, size: Dp, touchSize: Dp, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(touchSize)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                Haptics.click()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        // Play and pause swap as in the music player.
        io.github.thake.soundnest.core.ui.look.GlyphSwap(icon) { Icon(it, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(size)) }
    }
}

/** The speed, in a capsule: a tap opens the speeds. */
@Composable
internal fun SpeedChip(label: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(BOTTOM_GLYPH_SIZE)
            .clip(CircleShape)
            .background(LocalPlayerTint.current.copy(alpha = 0.12f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                Haptics.click()
                onClick()
            }
            .padding(horizontal = 16.dp),
    ) {
        Text(label, style = Immersive.Subhead.copy(fontWeight = FontWeight.Bold), color = LocalPlayerTint.current, maxLines = 1)
    }
}

private val BOTTOM_GLYPH_SIZE = 44.dp

/** One of the bottom row's round glyphs; lit while what it controls is on (a sleep timer, another device). */
@Composable
internal fun BottomGlyph(icon: ImageVector, contentDescription: String, onClick: () -> Unit, highlighted: Boolean = false) {
    Box(
        modifier = Modifier
            .size(BOTTOM_GLYPH_SIZE)
            .clip(CircleShape)
            .background(if (highlighted) LocalPlayerTint.current.copy(alpha = 0.20f) else Color.Transparent)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                Haptics.click()
                onClick()
            }
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = LocalPlayerTint.current.copy(alpha = if (highlighted) 1f else 0.75f),
            modifier = Modifier.size(26.dp),
        )
    }
}
