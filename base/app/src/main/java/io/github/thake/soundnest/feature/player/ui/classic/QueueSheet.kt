package io.github.thake.soundnest.feature.player.ui.classic

import io.github.thake.soundnest.feature.speakers.ui.activeDeviceIcon
import io.github.thake.soundnest.feature.speakers.ui.activeDeviceLabel


import io.github.thake.soundnest.core.ui.look.LookSheet
import io.github.thake.soundnest.core.ui.classic.tone
import io.github.thake.soundnest.core.ui.classic.AppText
import io.github.thake.soundnest.core.ui.classic.AppMuted

import androidx.compose.ui.res.stringResource
import io.github.thake.soundnest.R

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Delete
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import io.github.thake.soundnest.core.ui.navigation.isImmersive
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import io.github.thake.soundnest.core.model.SongsModel
import io.github.thake.soundnest.core.di.Graph
import io.github.thake.soundnest.core.playback.SongPlayer
import io.github.thake.soundnest.core.ui.classic.ExplicitBadge
import io.github.thake.soundnest.feature.player.ui.RepeatIcons
import io.github.thake.soundnest.feature.player.sleeptimer.ui.classic.SleepTimerSheet

private val Green: Color get() = io.github.thake.soundnest.core.ui.classic.AppAccent
private val SheetColor: androidx.compose.ui.graphics.Color get() = tone(0xFF282828)


/**
 * The ground of a queue row at rest: the sheet's own colour, nothing on the Immersive glass
 * sheet (an opaque row there is a dark stripe across the glass).
 */
private val RowGround: androidx.compose.ui.graphics.Color
    @Composable get() = if (isImmersive()) androidx.compose.ui.graphics.Color.Transparent else SheetColor


// ── Queue (official layout: sheet over the now-playing screen) ──

private val QueueRowHeight = 64.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalGlideComposeApi::class)
@Composable
fun QueueSheet(contextName: String, onDismiss: () -> Unit) {
    val q by SongPlayer.engineQueue
    val state = Graph.currentSongState
    val playing = state.playingState.value
    val shuffle = state.shuffle.value
    var showTimer by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    if (showTimer) SleepTimerSheet { showTimer = false }

    // The queue rows are all ONE LazyColumn item, so a drag can only scroll the sheet
    // through this state.
    val listState = rememberLazyListState()

    io.github.thake.soundnest.core.ui.look.LookSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SheetColor,
    ) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.92f).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_queue_label), color = AppText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    if (contextName.isNotBlank()) Row(modifier = Modifier.padding(top = 4.dp)) {
                        Text(stringResource(R.string.playing_on), color = AppText.copy(alpha = 0.7f), fontSize = 14.sp)
                        Text(contextName, color = AppText, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) {
                        Icon(activeDeviceIcon(), contentDescription = null, tint = Green, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(activeDeviceLabel(), color = Green, fontSize = 13.sp)
                    }
                }
                PillButton(if (editing) "OK" else stringResource(R.string.curation_actions_edit_button_title)) { editing = !editing }
            }
            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                q.current?.let { cur ->
                    item(key = "current") {
                        QueueSheetRow(cur, current = true, trailing = {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(AppText)
                                    .clickable { if (playing) SongPlayer.pause() else SongPlayer.play() },
                            ) {
                                Icon(
                                    if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (playing) stringResource(R.string.content_description_pause_button) else stringResource(R.string.content_description_play_button),
                                    tint = io.github.thake.soundnest.core.ui.classic.AppBackground, modifier = Modifier.size(22.dp),
                                )
                            }
                        }, onClick = {})
                    }
                }
                item(key = "queue") { QueueList(q, contextName, editing, listState) }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                QueueAction(Icons.Default.Shuffle, stringResource(R.string.play_mode_item_shuffle_title), shuffle, Modifier.weight(1f)) {
                    SongPlayer.setShuffle(!shuffle); state.shuffle.value = !shuffle
                }
                val mode = SongPlayer.repeatMode.value
                QueueAction(when (mode) { 2 -> Icons.Default.RepeatOne; 3 -> RepeatIcons.once; else -> Icons.Default.Repeat }, stringResource(R.string.np_content_desc_repeat), mode != 0, Modifier.weight(1f)) {
                    SongPlayer.cycleRepeat()
                }
                QueueAction(Icons.Outlined.Timer, stringResource(R.string.np_content_desc_sleep_timer), SongPlayer.sleepEndsAt.value != null, Modifier.weight(1f)) {
                    showTimer = true
                }
            }
        }
    }
}

@Composable
private fun PillButton(label: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(tone(0xFF3E3E3E))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
    ) { Text(label, color = AppText, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
}

private val QueueHeaderHeight = 48.dp

/** Strip at each end of the list where a held row starts scrolling it. */
private val QueueEdgeZone = 48.dp

/** Scroll speed at the very edge, a second: about six rows. See [QueueDragMath.edgeSpeed] for the curve. */
private val QueueEdgeSpeed = 360.dp

/**
 * Top of the queue block in viewport pixels (negative once scrolled past the top).
 * The sheet's list holds the current track (optional) and the queue block, so
 * [queueContentTop] is that first item's height; [firstVisibleIndex] / [firstVisibleScrollOffset]
 * are [LazyListState]'s own numbers, which keeps this in step with the scroll.
 */
internal fun queueTopInViewport(
    totalItems: Int,
    firstVisibleIndex: Int,
    firstVisibleScrollOffset: Int,
    queueContentTop: Float,
): Float {
    if (totalItems == 0) return 0f
    val firstItemTop = if (firstVisibleIndex > 0 && totalItems > 1) queueContentTop else 0f
    return queueContentTop - (firstItemTop + firstVisibleScrollOffset)
}

/** The y (in the queue block) of the gap [item] would drop into from [centerY]; mirrors the drop's own placement. */
private fun dropLineY(
    entries: List<QEntry>, item: SongPlayer.QueueItem, fromQueued: Boolean, centerY: Float,
    headerPx: Float, rowPx: Float, topOf: (Int) -> Float,
): Float? {
    val rowsOf = { queuedSection: Boolean -> entries.filterIsInstance<QEntry.Row>().filter { it.queued == queuedSection }.map { it.item } }
    fun rowTop(target: SongPlayer.QueueItem) = topOf(entries.indexOfFirst { it is QEntry.Row && it.item == target })
    val nextHeaderIndex = entries.indexOfFirst { it is QEntry.Header && !it.queuedSection }
    val nextHeaderMid = if (nextHeaderIndex >= 0) topOf(nextHeaderIndex) + headerPx / 2 else Float.MAX_VALUE
    val toQueue = centerY < nextHeaderMid
    val others = rowsOf(toQueue) - item
    if (others.isEmpty()) return if (toQueue && !fromQueued) 0f else null
    val slot = if (toQueue) ((centerY - headerPx) / rowPx).toInt().coerceIn(0, others.size)
        else ((centerY - (topOf(nextHeaderIndex) + headerPx)) / rowPx).toInt().coerceIn(0, others.size)
    return if (slot < others.size) rowTop(others[slot]) else rowTop(others.last()) + rowPx
}

private sealed class QEntry {
    data class Header(val queuedSection: Boolean) : QEntry()
    data class Row(val item: SongPlayer.QueueItem, val queued: Boolean) : QEntry()
}

/**
 * The upcoming tracks as ONE draggable list: "Prochains titres dans la file d'attente"
 * then "Suivants de : …". Dragging a track above the "Suivants" header moves it into
 * the queue (official behaviour). Drag handle on every row; swipe right removes.
 */
@Composable
private fun QueueList(
    q: SongPlayer.EngineQueue,
    contextName: String,
    editing: Boolean,
    listState: LazyListState,
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val scope = rememberCoroutineScope()
    val rowPx = with(density) { QueueRowHeight.toPx() }
    val headerPx = with(density) { QueueHeaderHeight.toPx() }
    val edgePx = with(density) { QueueEdgeZone.toPx() }
    val edgeSpeedPx = with(density) { QueueEdgeSpeed.toPx() }

    var queued by remember(q.queued) { mutableStateOf(q.queued) }
    var nextUp by remember(q.nextUp) { mutableStateOf(q.nextUp) }
    var dragging by remember { mutableStateOf<SongPlayer.QueueItem?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    var lastSlot by remember { mutableStateOf(-1) }
    var autoScrollJob by remember { mutableStateOf<Job?>(null) }

    // Kept while the drag moves: dragOffset recomposes this on every pointer move.
    val entries = remember(queued, nextUp) {
        buildList {
            if (queued.isNotEmpty()) add(QEntry.Header(true))
            queued.forEach { add(QEntry.Row(it, true)) }
            if (nextUp.isNotEmpty()) add(QEntry.Header(false))
            nextUp.forEach { add(QEntry.Row(it, false)) }
        }
    }
    fun height(e: QEntry) = if (e is QEntry.Header) headerPx else rowPx
    // Prefix sums: the drag indicator asks for a row's top on every draw frame, and taking and
    // summing the rows before it each time allocated and walked the list.
    val tops = remember(entries, rowPx, headerPx) {
        FloatArray(entries.size + 1).also { tops ->
            var acc = 0f
            entries.forEachIndexed { index, e ->
                tops[index] = acc
                acc += height(e)
            }
            tops[entries.size] = acc
        }
    }
    fun topOf(index: Int) = tops[index.coerceIn(0, entries.size)]

    /** Entry under a y position (list coordinates), found in [tops]. */
    fun entryAt(y: Float): Int {
        if (entries.isEmpty()) return -1
        var low = 0
        var high = entries.lastIndex
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (tops[mid] <= y) low = mid else high = mid - 1
        }
        return low
    }

    /**
     * Top of the queue block in viewport coordinates (negative once scrolled past the
     * top). Computing it from the scroll state instead of measuring the composable
     * guarantees it matches the scroll the drag itself is doing.
     */
    fun queueBlockTop(): Float {
        val total = listState.layoutInfo.totalItemsCount
        return queueTopInViewport(
            totalItems = total,
            firstVisibleIndex = listState.firstVisibleItemIndex,
            firstVisibleScrollOffset = listState.firstVisibleItemScrollOffset,
            queueContentTop = if (total > 1) rowPx else 0f,
        )
    }

    /** Pixels a second to scroll for the held row's top at [rowTop]: negative near the top edge, positive near the bottom. */
    fun edgeScrollSpeed(rowTop: Float): Float {
        val info = listState.layoutInfo
        return io.github.thake.soundnest.feature.player.ui.immersive.QueueDragMath.edgeSpeed(
            rowTop, rowTop + rowPx,
            info.viewportStartOffset.toFloat(), info.viewportEndOffset.toFloat(),
            edgePx, edgeSpeedPx,
        )
    }

    /** Recomputes the hovered entry after the list moved; ticks only when it changes. */
    fun syncHoveredSlot(itemIndex: Int) {
        val slot = entryAt(topOf(itemIndex) + dragOffset + rowPx / 2)
        if (slot != lastSlot) {
            lastSlot = slot
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.SegmentTick)
        }
    }

    /** Scrolls the list, paced by the clock rather than by frames, while the dragged row stays in an edge zone. */
    fun startAutoScroll(itemIndex: Int) {
        autoScrollJob?.cancel()
        autoScrollJob = scope.launch {
            var previous = withFrameNanos { it }
            while (isActive) {
                val now = withFrameNanos { it }
                // A dropped frame paid back in full lands as a lurch, so it is not.
                val seconds = ((now - previous) / 1_000_000_000f).coerceAtMost(1f / 30f)
                previous = now
                val rowTop = queueBlockTop() + topOf(itemIndex) + dragOffset
                val step = edgeScrollSpeed(rowTop) * seconds
                if (step != 0f) {
                    val moved = listState.scrollBy(step)
                    if (moved != 0f) {
                        // The rows live in list CONTENT space: the content slid under a
                        // still finger, so the drag has to slide by the same amount or
                        // the row escapes the finger and the hovered slot stops matching.
                        dragOffset += moved
                        syncHoveredSlot(itemIndex)
                    }
                }
            }
        }
    }

    fun stopAutoScroll() {
        autoScrollJob?.cancel()
        autoScrollJob = null
    }

    fun drop(item: SongPlayer.QueueItem, fromQueued: Boolean, centerY: Float) {
        val nextHeaderIndex = entries.indexOfFirst { it is QEntry.Header && !it.queuedSection }
        val nextHeaderMid = if (nextHeaderIndex >= 0) topOf(nextHeaderIndex) + headerPx / 2 else Float.MAX_VALUE
        // Either way across the line: a song moved up into the queue can be taken back down.
        val toQueue = centerY < nextHeaderMid
        // A queued Spotify song cannot go back into "next from" (Spotify has no such move).
        if (!toQueue && fromQueued && item.provider != SongPlayer.LOCAL) return
        if (toQueue) {
            val others = queued - item
            if (others.isEmpty()) {
                if (!fromQueued) {
                    // Empty queue: becomes the first queued track.
                    nextUp = nextUp - item; queued = listOf(item)
                    SongPlayer.showQueueOrder(queued, nextUp)
                    SongPlayer.moveToQueueFront(item)
                }
                return
            }
            // Position among the queued rows (rows start after the queue header).
            val slot = ((centerY - headerPx) / rowPx).toInt().coerceIn(0, others.size)
            val newQueued = others.toMutableList().apply { add(slot, item) }
            if (newQueued == queued) return
            if (!fromQueued) nextUp = nextUp - item
            queued = newQueued
            SongPlayer.showQueueOrder(queued, nextUp)
            val (target, after) = if (slot < others.size) others[slot] to false else others[slot - 1] to true
            if (fromQueued) SongPlayer.reorder(item, target, after) else SongPlayer.moveIntoQueue(item, target, after)
        } else {
            val others = nextUp - item
            if (others.isEmpty()) return
            val startY = topOf(nextHeaderIndex) + headerPx
            val slot = ((centerY - startY) / rowPx).toInt().coerceIn(0, others.size)
            val newNext = others.toMutableList().apply { add(slot, item) }
            if (newNext == nextUp) return
            if (fromQueued) queued = queued - item
            nextUp = newNext
            SongPlayer.showQueueOrder(queued, nextUp)
            if (slot < others.size) SongPlayer.reorder(item, others[slot], after = false)
            else SongPlayer.reorder(item, others[slot - 1], after = true)
        }
    }

    // Where the dragged song would land if let go: a thin accent bar between two rows, worked
    // out exactly as [drop] places it.
    val accent = io.github.thake.soundnest.core.ui.classic.AppAccent
    val dropBar = Modifier.drawWithContent {
        drawContent()
        val item = dragging ?: return@drawWithContent
        val from = entries.indexOfFirst { it is QEntry.Row && it.item == item }
        if (from < 0) return@drawWithContent
        val y = dropLineY(entries, item, (entries[from] as QEntry.Row).queued, topOf(from) + dragOffset + rowPx / 2, headerPx, rowPx, ::topOf)
            ?: return@drawWithContent
        val inset = 20.dp.toPx()
        val h = 1.5.dp.toPx()
        drawRoundRect(androidx.compose.ui.graphics.lerp(accent, Color.Black, 0.35f).copy(alpha = 0.8f), topLeft = androidx.compose.ui.geometry.Offset(inset, y - h / 2),
            size = androidx.compose.ui.geometry.Size(size.width - inset * 2, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2))
    }
    Column(dropBar) {
        entries.forEachIndexed { index, e ->
            when (e) {
                is QEntry.Header -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().height(QueueHeaderHeight),
                ) {
                    Box(Modifier.weight(1f)) {
                        SheetHeader(
                            if (e.queuedSection) stringResource(R.string.next_in_queue)
                            else if (contextName.isNotBlank()) stringResource(R.string.next_from, contextName) else stringResource(R.string.next_up)
                        )
                    }
                    if (e.queuedSection) Text(
                        stringResource(R.string.clear_queue),
                        color = AppText, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(end = 20.dp)
                            .clickable {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm)
                                queued = emptyList(); SongPlayer.clearQueue()
                            },
                    )
                }
                is QEntry.Row -> {
                    val item = e.item
                    val isDragged = dragging == item
                    // The gesture below outlives this composition (keyed by the song only): it calls the
                    // latest of these, so it works on the rows as they are now, not as at its first frame.
                    val onDragStartNow by androidx.compose.runtime.rememberUpdatedState {
                        dragging = item; dragOffset = 0f; lastSlot = index
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        startAutoScroll(index)
                    }
                    val onDragEndNow by androidx.compose.runtime.rememberUpdatedState {
                        stopAutoScroll()
                        val center = topOf(index) + dragOffset + rowPx / 2
                        drop(item, e.queued, center)
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.GestureEnd)
                        dragging = null; dragOffset = 0f
                    }
                    val onDragNow by androidx.compose.runtime.rememberUpdatedState { dy: Float ->
                        dragOffset += dy
                        syncHoveredSlot(index)
                    }
                    Box(
                        modifier = Modifier
                            .graphicsLayer { translationY = if (isDragged) dragOffset else 0f }
                            .zIndex(if (isDragged) 1f else 0f)
                            .background(if (isDragged) tone(0xFF3A3A3A) else RowGround)
                    ) {
                        SwipeLeftToRemove(enabled = !editing && dragging == null, onRemove = {
                            if (e.queued) queued = queued - item else nextUp = nextUp - item
                            SongPlayer.removeFromQueue(item)
                        }) {
                            QueueSheetRow(item.song, current = false, leading = {
                                if (editing) Icon(
                                    Icons.Default.RemoveCircleOutline,
                                    contentDescription = stringResource(R.string.aligned_curation_first_save_default_row_remove_action_description),
                                    tint = AppText.copy(alpha = 0.8f),
                                    modifier = Modifier
                                        .padding(end = 12.dp)
                                        .size(24.dp)
                                        .clickable {
                                            if (e.queued) queued = queued - item else nextUp = nextUp - item
                                            SongPlayer.removeFromQueue(item)
                                        },
                                )
                            }, trailing = {
                                Icon(
                                    Icons.Default.DragHandle,
                                    contentDescription = stringResource(R.string.edit_playlist_items_move_chip),
                                    tint = AppText.copy(alpha = 0.8f),
                                    modifier = Modifier
                                        .size(28.dp)
                                        .pointerInput(item.uid + item.uri) {
                                            detectDragGestures(
                                                onDragStart = { onDragStartNow() },
                                                onDragEnd = { onDragEndNow() },
                                                onDragCancel = {
                                                    stopAutoScroll()
                                                    dragging = null; dragOffset = 0f
                                                },
                                            ) { change, amount ->
                                                change.consume()
                                                onDragNow(amount.y)
                                            }
                                        },
                                )
                            }, onClick = { if (!editing) SongPlayer.skipTo(item) })
                        }
                    }
                }
            }
        }
    }
}

/** Swipe a queue row from right to left to remove it (red strip, haptic tick at the threshold). */
@Composable
private fun SwipeLeftToRemove(enabled: Boolean, onRemove: () -> Unit, content: @Composable () -> Unit) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // 40 % of the row (at least 110 dp): a slanted scroll of the sheet took songs out of the queue.
    var rowWidth by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val minimum = with(androidx.compose.ui.platform.LocalDensity.current) { 110.dp.toPx() }
    val threshold = maxOf(minimum, rowWidth * 0.4f)
    val offset = remember { androidx.compose.animation.core.Animatable(0f) }
    var armed by remember { mutableStateOf(false) }
    // The offset moves every drag and spring frame: read it through derived state so only the
    // strip appearing and the row coming to rest, not the whole row, recompose.
    val revealed by remember(offset) { derivedStateOf { offset.value < 0f } }
    val atRest by remember(offset) { derivedStateOf { offset.value == 0f } }
    Box(modifier = Modifier.fillMaxWidth().onSizeChanged { rowWidth = it.width.toFloat() }) {
        if (revealed) Box(
            contentAlignment = Alignment.CenterEnd,
            modifier = Modifier.matchParentSize().background(if (armed) Color(0xFFE22134) else Color(0xFF7A1F1F)),
        ) {
            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.aligned_curation_first_save_default_row_remove_action_description), tint = AppText, modifier = Modifier.padding(end = 24.dp).size(24.dp))
        }
        Box(
            modifier = Modifier
                .graphicsLayer { translationX = offset.value }
                // Opaque once it slides, to cover the red strip it uncovers.
                .background(if (atRest) RowGround else SheetColor)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (armed) {
                                scope.launch { offset.animateTo(-size.width.toFloat()) ; onRemove(); offset.snapTo(0f) }
                            } else scope.launch { offset.animateTo(0f) }
                            armed = false
                        },
                        onDragCancel = { armed = false; scope.launch { offset.animateTo(0f) } },
                    ) { change, dx ->
                        change.consume()
                        val next = (offset.value + dx).coerceIn(-size.width.toFloat(), 0f)
                        scope.launch { offset.snapTo(next) }
                        val nowArmed = -next >= threshold
                        if (nowArmed && !armed) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.GestureThresholdActivate)
                        armed = nowArmed
                    }
                },
        ) { content() }
    }
}

@Composable
private fun SheetHeader(text: String) {
    Text(
        text, color = AppText, fontSize = 15.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 20.dp),
    )
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun QueueSheetRow(
    song: SongsModel,
    current: Boolean,
    leading: @Composable () -> Unit = {},
    trailing: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val rec = io.github.thake.soundnest.feature.library.ui.SmartShuffle.isRecommendation(song.spotifyTrackId)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(QueueRowHeight)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
    ) {
        leading()
        GlideImage(
            model = song.coverUri, contentDescription = "", contentScale = ContentScale.Crop,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (current) {
                    Icon(Icons.Default.Equalizer, contentDescription = null, tint = Green, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    song.title, color = if (current) Green else AppText, fontSize = 16.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                // Mixed in by smart shuffle: marked, as in the official app, not taken for one of the playlist's songs.
                if (rec) { Icon(Icons.Outlined.AutoAwesome, contentDescription = stringResource(R.string.smart_shuffle_recommended), tint = Green, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)) }
                if (song.explicit) { ExplicitBadge(); Spacer(Modifier.width(6.dp)) }
                Text(song.singer, color = AppText.copy(alpha = 0.7f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (rec) Icon(
            Icons.Outlined.AddCircleOutline,
            contentDescription = stringResource(R.string.smart_shuffle_add),
            tint = AppText,
            modifier = Modifier.padding(end = 12.dp).size(26.dp).clip(CircleShape).clickable { io.github.thake.soundnest.feature.library.ui.SmartShuffle.keep(context, song) },
        )
        trailing()
    }
}

@Composable
private fun QueueAction(icon: ImageVector, label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(tone(0xFF3E3E3E))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (active) Green else AppText, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = if (active) Green else AppText, fontSize = 13.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}
