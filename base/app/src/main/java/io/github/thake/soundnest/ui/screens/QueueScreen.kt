package io.github.thake.soundnest.ui.screens

import io.github.thake.soundnest.ui.components.animatedRow
import io.github.thake.soundnest.ui.theme.AppText

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import io.github.thake.soundnest.R
import io.github.thake.soundnest.di.SongPlayer
import io.github.thake.soundnest.ui.theme.AppAccent
import io.github.thake.soundnest.ui.theme.AppMuted
import io.github.thake.soundnest.ui.theme.AppPalette

/**
 * The "play queue" — the track that's playing now plus everything coming up
 * (Spotify's Queue view). Tapping an upcoming track jumps straight to it;
 * swiping a row or tapping its remove button removes it.
 */
@OptIn(ExperimentalGlideComposeApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(navController: NavController) {
    // Spotify's real queue, pushed by the engine.
    val q by SongPlayer.engineQueue

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(io.github.thake.soundnest.ui.theme.AppBackground)
            .statusBarsPadding()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp, 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(R.string.bottom_actions_close),
                tint = AppText,
                modifier = Modifier
                    .size(28.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { navController.navigateUp() }
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(stringResource(R.string.action_queue_label), color = AppText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp),
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            if (q.current == null && q.queued.isEmpty() && q.nextUp.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.nothing_here),
                        color = AppMuted,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }
            q.current?.let {
                item(key = "now") {
                    QueueHeader(stringResource(R.string.default_notification_channel_name))
                    QueueRow(song = it, highlight = true, onClick = {})
                }
            }
            // Keyed by song and occurrence (as the Immersive queue): a row removed or played lets
            // the ones under it slide up instead of every row after it being drawn anew.
            val keys = queueKeys(q.queued + q.nextUp)
            if (q.queued.isNotEmpty()) {
                item(key = "heading-queued") {
                    QueueHeader(
                        text = stringResource(R.string.next_in_queue),
                        modifier = animatedRow(),
                        actionLabel = stringResource(R.string.clear_queue),
                        onAction = { SongPlayer.clearQueue() },
                    )
                }
                itemsIndexed(q.queued, key = { i, _ -> keys[i] }) { _, item ->
                    Box(animatedRow()) {
                        SwipeRemove(item) {
                            QueueRow(
                                song = item.song,
                                highlight = false,
                                onClick = { SongPlayer.skipTo(item) },
                                onRemove = { SongPlayer.removeFromQueue(item) },
                            )
                        }
                    }
                }
            }
            if (q.nextUp.isNotEmpty()) {
                item(key = "heading-next") { QueueHeader(stringResource(R.string.next_up), animatedRow()) }
                itemsIndexed(q.nextUp, key = { i, _ -> keys[q.queued.size + i] }) { _, item ->
                    Box(animatedRow()) {
                        SwipeRemove(item) {
                            QueueRow(
                                song = item.song,
                                highlight = false,
                                onClick = { SongPlayer.skipTo(item) },
                                onRemove = { SongPlayer.removeFromQueue(item) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One key per row: the song's uri, with its occurrence after the first ("uri~2"), never twice the same. */
private fun queueKeys(rows: List<SongPlayer.QueueItem>): List<String> {
    val seen = HashMap<String, Int>()
    return rows.map { item ->
        val count = seen.merge(item.uri, 1, Int::plus)!!
        if (count == 1) item.uri else "${item.uri}~$count"
    }
}

@Composable
private fun QueueHeader(
    text: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier.fillMaxWidth().padding(16.dp, 18.dp, 16.dp, 6.dp),
    ) {
        Text(
            text,
            color = AppText,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null && onAction != null) {
            Text(
                actionLabel,
                color = AppAccent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onAction).padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
            )
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SwipeRemove(item: SongPlayer.QueueItem, content: @Composable () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) {
                SongPlayer.removeFromQueue(item)
                true
            } else false
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                contentAlignment = Alignment.CenterEnd,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF7A1F1F))
                    .padding(horizontal = 24.dp),
            ) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.aligned_curation_first_save_default_row_remove_action_description), tint = AppText)
            }
        },
        content = { content() }
    )
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun QueueRow(
    song: io.github.thake.soundnest.data.entity.SongsModel,
    highlight: Boolean,
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null,
    isDragging: Boolean = false,
    dragOffsetY: Float = 0f,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = dragOffsetY }
            .background(if (isDragging) io.github.thake.soundnest.ui.theme.headerTint(Color(0xFF1E1E24), 0f) else io.github.thake.soundnest.ui.theme.AppBackground)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() }
            .padding(16.dp, 8.dp)
    ) {
        GlideImage(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(4.dp)),
            model = song.coverUri,
            contentScale = ContentScale.Crop,
            loading = placeholder(R.drawable.placeholder),
            failure = placeholder(R.drawable.placeholder),
            contentDescription = ""
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = if (highlight) Color(AppPalette.toArgb()) else AppText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.singer,
                color = AppMuted,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onRemove != null) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.aligned_curation_first_save_default_row_remove_action_description),
                tint = AppMuted,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(32.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onRemove)
                    .padding(6.dp),
            )
        }
    }
}
