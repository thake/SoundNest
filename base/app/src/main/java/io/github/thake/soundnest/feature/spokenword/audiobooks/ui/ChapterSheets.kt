/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.spokenword.audiobooks.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.model.SongsModel
import io.github.thake.soundnest.core.ui.classic.AppAccent
import io.github.thake.soundnest.core.ui.classic.AppText
import io.github.thake.soundnest.core.ui.classic.tone
import io.github.thake.soundnest.core.ui.look.LookSheet
import io.github.thake.soundnest.feature.spokenword.ui.SpokenWordSheetAction
import io.github.thake.soundnest.feature.spokenword.ui.episodeClock

private val SheetColor: Color get() = tone(0xFF1F1F1F)

/** Formats an audiobook's total runtime as "16 h 10 min" or "42 min". */
@Composable
fun formatBookRuntime(runtimeMs: Long): String {
    val totalMinutes = ((runtimeMs + 30_000L) / 60_000L).coerceAtLeast(1L)
    val hours = (totalMinutes / 60L).toInt()
    val minutes = (totalMinutes % 60L).toInt()
    return if (hours > 0) {
        stringResource(R.string.duration_hours_minutes, hours, minutes)
    } else {
        stringResource(R.string.duration_minutes, minutes)
    }
}

/** Builds "Book Title • Author" without duplicating identical strings. */
fun audiobookSubtitle(bookTitle: String, author: String): String {
    val book = bookTitle.trim()
    val by = author.trim()
    return when {
        book.isNotEmpty() && by.isNotEmpty() && !book.equals(by, ignoreCase = true) -> "$book • $by"
        book.isNotEmpty() -> book
        else -> by
    }
}

/** Context menu for an audiobook chapter (chapter playback, restart, played mark, go to book). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalGlideComposeApi::class)
@Composable
fun ChapterOptionsSheet(
    chapter: SongsModel,
    bookTitle: String,
    finished: Boolean,
    started: Boolean,
    onPlay: () -> Unit,
    onRestart: (() -> Unit)? = null,
    onMarkFinished: (Boolean) -> Unit,
    onOpenBook: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val subtitle = audiobookSubtitle(bookTitle.ifBlank { chapter.album }, chapter.singer)
    LookSheet(onDismissRequest = onDismiss, containerColor = SheetColor) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(20.dp, 4.dp, 20.dp, 16.dp),
            ) {
                GlideImage(
                    model = chapter.coverUri,
                    contentDescription = "",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(4.dp)),
                )
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        chapter.title,
                        color = AppText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle.isNotBlank()) {
                        Text(
                            subtitle,
                            color = AppText.copy(alpha = 0.65f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            SpokenWordSheetAction(Icons.Default.PlayArrow, stringResource(R.string.episode_play)) {
                onDismiss()
                onPlay()
            }
            if (started && onRestart != null) {
                SpokenWordSheetAction(Icons.Outlined.Replay, stringResource(R.string.chapter_restart)) {
                    onDismiss()
                    onRestart()
                }
            }
            if (finished) {
                SpokenWordSheetAction(Icons.Outlined.Undo, stringResource(R.string.episode_mark_unplayed)) {
                    onMarkFinished(false)
                    Toast.makeText(context, context.getString(R.string.episode_marked_unplayed), Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            } else {
                SpokenWordSheetAction(Icons.Outlined.Done, stringResource(R.string.episode_mark_played)) {
                    onMarkFinished(true)
                    Toast.makeText(context, context.getString(R.string.episode_marked_played), Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            }
            if (onOpenBook != null) {
                SpokenWordSheetAction(Icons.Outlined.MenuBook, stringResource(R.string.audiobook_open_book)) {
                    onDismiss()
                    onOpenBook()
                }
            }
        }
    }
}

/** Table-of-contents sheet opened from the audiobook player, showing every chapter 1..N. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudiobookChaptersSheet(
    bookTitle: String,
    chapters: List<SongsModel>,
    currentUri: String,
    playing: Boolean,
    isFinished: (SongsModel) -> Boolean,
    onSelectChapter: (Int, SongsModel) -> Unit,
    onDismiss: () -> Unit,
) {
    val activeIdx = chapters.indexOfFirst { it.url == currentUri || "spotify:${it.url}" == currentUri }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (activeIdx - 1).coerceAtLeast(0))
    LookSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = tone(0xFF282828),
    ) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f).navigationBarsPadding()) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                Text(
                    stringResource(R.string.audiobook_chapters_sheet_title),
                    color = AppText,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                val sub = when {
                    bookTitle.isNotBlank() && activeIdx >= 0 && chapters.size > 1 ->
                        "$bookTitle • ${stringResource(R.string.audiobook_chapter_of, activeIdx + 1, chapters.size)}"
                    bookTitle.isNotBlank() -> bookTitle
                    activeIdx >= 0 && chapters.size > 1 ->
                        stringResource(R.string.audiobook_chapter_of, activeIdx + 1, chapters.size)
                    else -> ""
                }
                if (sub.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        sub,
                        color = AppText.copy(alpha = 0.65f),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            HorizontalDivider(
                color = AppText.copy(alpha = 0.1f),
                thickness = 0.5.dp,
                modifier = Modifier.padding(top = 6.dp),
            )
            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                itemsIndexed(chapters, key = { idx, ch -> "${idx}_${ch.url}" }) { idx, ch ->
                    val current = idx == activeIdx
                    val finished = isFinished(ch)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (current) AppAccent.copy(alpha = 0.08f) else Color.Transparent)
                            .clickable {
                                onSelectChapter(idx, ch)
                                onDismiss()
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                    ) {
                        Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
                            when {
                                current -> Icon(
                                    imageVector = if (playing) Icons.Default.GraphicEq else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = AppAccent,
                                    modifier = Modifier.size(18.dp),
                                )
                                finished -> Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = AppAccent,
                                    modifier = Modifier.size(16.dp),
                                )
                                else -> Text(
                                    text = "${idx + 1}",
                                    color = AppText.copy(alpha = 0.55f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = ch.title,
                            color = when {
                                current -> AppAccent
                                finished -> AppText.copy(alpha = 0.6f)
                                else -> AppText
                            },
                            fontSize = 15.sp,
                            fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (ch.durationMs > 0) {
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = episodeClock(ch.durationMs.toLong()),
                                color = if (current) AppAccent else AppText.copy(alpha = 0.55f),
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}
