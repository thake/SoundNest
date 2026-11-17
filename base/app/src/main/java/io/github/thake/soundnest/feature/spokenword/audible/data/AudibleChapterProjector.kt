/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.spokenword.audible.data

import io.github.thake.soundnest.audible.*

data class ProjectedChapterPosition(
    val chapter: AudibleChapter,
    val chapterPositionMs: Long,
    val chapterDurationMs: Long,
    val absolutePositionMs: Long,
)

class AudibleChapterProjector(
    rawChapters: List<AudibleChapter>,
    fallbackTitle: String = "Chapter 1",
    fallbackTotalDurationMs: Long = 0L,
) {
    val chapters: List<AudibleChapter> = if (rawChapters.isEmpty()) {
        listOf(
            AudibleChapter(
                index = 0,
                title = fallbackTitle.ifBlank { "Chapter 1" },
                startOffsetMs = 0L,
                lengthMs = fallbackTotalDurationMs.coerceAtLeast(0L),
            ),
        )
    } else {
        rawChapters
            .sortedBy { it.startOffsetMs }
            .mapIndexed { idx, ch ->
                ch.copy(
                    index = idx,
                    startOffsetMs = ch.startOffsetMs.coerceAtLeast(0L),
                    lengthMs = ch.lengthMs.coerceAtLeast(0L),
                )
            }
    }

    val totalDurationMs: Long = chapters.last().endOffsetMs.coerceAtLeast(fallbackTotalDurationMs.coerceAtLeast(0L))

    fun project(absoluteStreamPositionMs: Long): ProjectedChapterPosition {
        val clamped = if (totalDurationMs > 0L) {
            absoluteStreamPositionMs.coerceIn(0L, totalDurationMs)
        } else {
            absoluteStreamPositionMs.coerceAtLeast(0L)
        }

        // Find the last chapter whose startOffsetMs <= clamped (with strict < on endOffsetMs except for the final chapter)
        var selected = chapters.first()
        for (i in chapters.indices) {
            val ch = chapters[i]
            val isLast = i == chapters.lastIndex
            if (clamped >= ch.startOffsetMs && (isLast || clamped < ch.endOffsetMs)) {
                selected = ch
                break
            }
            if (clamped >= ch.startOffsetMs) {
                selected = ch
            }
        }

        val chDuration = if (selected.lengthMs > 0L) selected.lengthMs else (totalDurationMs - selected.startOffsetMs).coerceAtLeast(0L)
        val chPos = (clamped - selected.startOffsetMs).let { rel ->
            if (chDuration > 0L) rel.coerceIn(0L, chDuration) else rel.coerceAtLeast(0L)
        }

        return ProjectedChapterPosition(
            chapter = selected,
            chapterPositionMs = chPos,
            chapterDurationMs = chDuration,
            absolutePositionMs = clamped,
        )
    }

    fun absolutePositionForChapter(chapterIndex: Int, chapterOffsetMs: Long = 0L): Long {
        val ch = chapters.getOrElse(chapterIndex.coerceIn(0, chapters.lastIndex)) { chapters.first() }
        val clampedOffset = if (ch.lengthMs > 0L) {
            chapterOffsetMs.coerceIn(0L, ch.lengthMs)
        } else {
            chapterOffsetMs.coerceAtLeast(0L)
        }
        return (ch.startOffsetMs + clampedOffset).let { abs ->
            if (totalDurationMs > 0L) abs.coerceIn(0L, totalDurationMs) else abs
        }
    }

    fun seekWithinChapter(chapterIndex: Int, targetChapterOffsetMs: Long): Long =
        absolutePositionForChapter(chapterIndex, targetChapterOffsetMs)

    fun skipBy(absoluteStreamPositionMs: Long, deltaMs: Long): Long {
        val target = (absoluteStreamPositionMs + deltaMs).coerceAtLeast(0L)
        return if (totalDurationMs > 0L) target.coerceAtMost(totalDurationMs) else target
    }

    fun nextChapterPosition(absoluteStreamPositionMs: Long): Long? {
        val current = project(absoluteStreamPositionMs).chapter
        val nextIdx = current.index + 1
        return chapters.getOrNull(nextIdx)?.startOffsetMs
    }

    fun previousChapterPosition(
        absoluteStreamPositionMs: Long,
        restartThresholdMs: Long = 3_000L,
    ): Long {
        val projected = project(absoluteStreamPositionMs)
        return if (projected.chapterPositionMs > restartThresholdMs || projected.chapter.index <= 0) {
            projected.chapter.startOffsetMs
        } else {
            chapters[projected.chapter.index - 1].startOffsetMs
        }
    }

    fun resolveInitialPosition(
        requestedChapterIndex: Int?,
        requestedPositionMs: Long?,
        savedProgress: BookProgress?,
    ): Long {
        if (requestedChapterIndex != null) {
            return absolutePositionForChapter(requestedChapterIndex, requestedPositionMs ?: 0L)
        }
        if (requestedPositionMs != null) {
            return if (totalDurationMs > 0L) requestedPositionMs.coerceIn(0L, totalDurationMs) else requestedPositionMs.coerceAtLeast(0L)
        }
        if (savedProgress != null) {
            val allChaptersFinished = chapters.isNotEmpty() && chapters.all { it.index in savedProgress.finishedChapters }
            if (!allChaptersFinished && savedProgress.positionMs > 0L) {
                return if (totalDurationMs > 0L) savedProgress.positionMs.coerceIn(0L, totalDurationMs) else savedProgress.positionMs
            }
        }
        return 0L
    }
}
