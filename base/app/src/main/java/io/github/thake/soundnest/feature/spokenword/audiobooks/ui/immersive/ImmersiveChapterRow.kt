/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.spokenword.audiobooks.ui.immersive

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.model.SongsModel
import io.github.thake.soundnest.core.ui.immersive.Immersive
import io.github.thake.soundnest.core.ui.immersive.components.ImmersiveMoreButton
import io.github.thake.soundnest.core.ui.immersive.components.immersiveClickable
import io.github.thake.soundnest.feature.library.ui.immersive.ImmersiveRowDivider
import io.github.thake.soundnest.feature.spokenword.ui.EpisodeProgressBar
import io.github.thake.soundnest.feature.spokenword.ui.episodeClock

private val ChapterLeadWidth = 26.dp
private val ChapterLeadGap = 14.dp

/**
 * Compact single-line audiobook chapter row for the Immersive look: index / check / waveform,
 * title, active progress bar only when started, inline clock duration, and inline ⋮ menu.
 */
@Composable
internal fun ImmersiveChapterRow(
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
    val titleColor by animateColorAsState(
        targetValue = when {
            current -> Immersive.Accent
            finished -> Immersive.TextSecondary
            else -> Immersive.Text
        },
        label = "chapter row title",
    )
    Column(modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .immersiveClickable(onClick = onClick, onLongClick = onMenu, pressedScale = 0.985f)
                .padding(horizontal = Immersive.Gutter, vertical = 6.dp),
        ) {
            Box(Modifier.width(ChapterLeadWidth), contentAlignment = Alignment.Center) {
                when {
                    current -> Icon(
                        imageVector = if (playing) Icons.Rounded.GraphicEq else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(R.string.immersive_shell_now_playing),
                        tint = Immersive.Accent,
                        modifier = Modifier.size(20.dp),
                    )
                    finished -> Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = stringResource(R.string.chapter_played_short),
                        tint = Immersive.Accent,
                        modifier = Modifier.size(18.dp),
                    )
                    else -> Text(
                        text = "$chapterNumber",
                        style = Immersive.Subhead.copy(color = Immersive.TextSecondary),
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.width(ChapterLeadGap))
            Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                Text(
                    text = chapter.title,
                    style = Immersive.Body.copy(
                        fontWeight = if (current) FontWeight.SemiBold else FontWeight.Medium,
                        color = titleColor,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (chapter.description.isNotBlank()) {
                    Text(
                        text = chapter.description,
                        style = Immersive.Caption,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                if (started) {
                    Text(
                        text = stringResource(R.string.episode_remaining, episodeClock((chapter.durationMs - livePositionMs).coerceAtLeast(0L))),
                        style = Immersive.Caption.copy(color = Immersive.Accent),
                        maxLines = 1,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    EpisodeProgressBar(livePositionMs, chapter.durationMs, Immersive.Accent, Immersive.TextSecondary.copy(alpha = 0.2f))
                } else if (finished) {
                    Text(
                        text = stringResource(R.string.chapter_played_short),
                        style = Immersive.Caption,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (chapter.durationMs > 0) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = episodeClock(chapter.durationMs.toLong()),
                    style = Immersive.Caption.copy(color = if (current) Immersive.Accent else Immersive.TextSecondary),
                    maxLines = 1,
                )
            }
            ImmersiveMoreButton(onClick = onMenu)
        }
        ImmersiveRowDivider(inset = Immersive.Gutter + ChapterLeadWidth + ChapterLeadGap)
    }
}
