/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.spokenword.audiobooks.ui.classic

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.model.SongsModel
import io.github.thake.soundnest.core.ui.classic.AppAccent
import io.github.thake.soundnest.core.ui.classic.AppText
import io.github.thake.soundnest.core.ui.look.MinTouch
import io.github.thake.soundnest.core.ui.look.pressable
import io.github.thake.soundnest.feature.spokenword.ui.EpisodeProgressBar
import io.github.thake.soundnest.feature.spokenword.ui.episodeClock

/**
 * Compact single-line audiobook chapter row for Classic look: leading index / check / waveform,
 * chapter title + progress bar only when active, inline clock duration, and inline ⋮ options.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChapterRow(
    chapterNumber: Int,
    chapter: SongsModel,
    current: Boolean,
    playing: Boolean,
    finished: Boolean,
    livePositionMs: Long,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val started = livePositionMs > 0L && !finished && chapter.durationMs > 0
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onMenu)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
            when {
                current -> Icon(
                    imageVector = if (playing) Icons.Default.GraphicEq else Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.immersive_shell_now_playing),
                    tint = AppAccent,
                    modifier = Modifier.size(20.dp),
                )
                finished -> Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.chapter_played_short),
                    tint = AppAccent,
                    modifier = Modifier.size(18.dp),
                )
                else -> Text(
                    text = "$chapterNumber",
                    color = AppText.copy(alpha = 0.55f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = chapter.title,
                color = when {
                    current -> AppAccent
                    finished -> AppText.copy(alpha = 0.65f)
                    else -> AppText
                },
                fontSize = 15.sp,
                fontWeight = if (current) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (chapter.description.isNotBlank()) {
                Text(
                    text = chapter.description,
                    color = AppText.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (started) {
                Text(
                    text = stringResource(R.string.episode_remaining, episodeClock((chapter.durationMs - livePositionMs).coerceAtLeast(0L))),
                    color = AppAccent,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 3.dp),
                )
                Spacer(Modifier.height(4.dp))
                EpisodeProgressBar(livePositionMs, chapter.durationMs, AppAccent, AppText.copy(alpha = 0.15f))
            } else if (finished) {
                Text(
                    text = stringResource(R.string.chapter_played_short),
                    color = AppText.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (chapter.durationMs > 0) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = episodeClock(chapter.durationMs.toLong()),
                color = if (current) AppAccent else AppText.copy(alpha = 0.6f),
                fontSize = 12.sp,
            )
        }
        Icon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = stringResource(R.string.chapter_options),
            tint = AppText.copy(alpha = 0.7f),
            modifier = Modifier
                .size(MinTouch)
                .pressable(0.92f, onClick = onMenu)
                .wrapContentSize()
                .size(20.dp),
        )
    }
}
