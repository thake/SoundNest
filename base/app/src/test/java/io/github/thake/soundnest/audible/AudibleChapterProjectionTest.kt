/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.audible

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudibleChapterProjectionTest {

    private val sampleChapters = listOf(
        AudibleChapter(index = 0, title = "Opening Credits & Chapter 1", startOffsetMs = 0L, lengthMs = 60_000L),
        AudibleChapter(index = 1, title = "Chapter 2: Launch", startOffsetMs = 60_000L, lengthMs = 120_000L),
        AudibleChapter(index = 2, title = "Chapter 3: Orbit", startOffsetMs = 180_000L, lengthMs = 90_000L),
    )

    @Test
    fun `projects continuous stream position into chapter-relative offset and duration`() {
        val projector = AudibleChapterProjector(sampleChapters)
        assertEquals(270_000L, projector.totalDurationMs)

        val start = projector.project(0L)
        assertEquals(0, start.chapter.index)
        assertEquals(0L, start.chapterPositionMs)
        assertEquals(60_000L, start.chapterDurationMs)

        val midCh2 = projector.project(85_000L)
        assertEquals(1, midCh2.chapter.index)
        assertEquals("Chapter 2: Launch", midCh2.chapter.title)
        assertEquals(25_000L, midCh2.chapterPositionMs)
        assertEquals(120_000L, midCh2.chapterDurationMs)

        val atBoundary = projector.project(180_000L)
        assertEquals(2, atBoundary.chapter.index)
        assertEquals(0L, atBoundary.chapterPositionMs)
        assertEquals(90_000L, atBoundary.chapterDurationMs)

        val pastEnd = projector.project(999_999L)
        assertEquals(2, pastEnd.chapter.index)
        assertEquals(90_000L, pastEnd.chapterPositionMs)
    }

    @Test
    fun `skipBy crosses chapter boundaries seamlessly on continuous stream`() {
        val projector = AudibleChapterProjector(sampleChapters)

        // 5 seconds into Chapter 2 (65_000 ms): skipping -15s lands at 50_000 ms (10s before end of Chapter 1)
        val rewound = projector.skipBy(65_000L, -15_000L)
        assertEquals(50_000L, rewound)
        val projectedRewound = projector.project(rewound)
        assertEquals(0, projectedRewound.chapter.index)
        assertEquals(50_000L, projectedRewound.chapterPositionMs)

        // 5 seconds before end of Chapter 2 (175_000 ms): skipping +15s lands 10s into Chapter 3 (190_000 ms)
        val forwarded = projector.skipBy(175_000L, +15_000L)
        assertEquals(190_000L, forwarded)
        val projectedForwarded = projector.project(forwarded)
        assertEquals(2, projectedForwarded.chapter.index)
        assertEquals(10_000L, projectedForwarded.chapterPositionMs)
    }

    @Test
    fun `nextChapterPosition and previousChapterPosition follow audiobook transport rules`() {
        val projector = AudibleChapterProjector(sampleChapters)

        assertEquals(60_000L, projector.nextChapterPosition(12_000L))
        assertEquals(180_000L, projector.nextChapterPosition(65_000L))
        assertNull(projector.nextChapterPosition(200_000L))

        // More than 3s into Chapter 2 -> restarts Chapter 2 (60_000L)
        assertEquals(60_000L, projector.previousChapterPosition(70_000L))
        // Within first 3s of Chapter 2 -> jumps to start of Chapter 1 (0L)
        assertEquals(0L, projector.previousChapterPosition(62_000L))
        // Within first 3s of Chapter 1 -> clamps to 0L
        assertEquals(0L, projector.previousChapterPosition(1_500L))
    }

    @Test
    fun `resolveInitialPosition honors explicit chapter, explicit offset, or saved unfinished progress`() {
        val projector = AudibleChapterProjector(sampleChapters)
        val saved = BookProgress(
            asin = "B08G9PRS1K",
            positionMs = 150_000L,
            chapterIndex = 1,
            updatedAtMs = 1000L,
        )

        // Resume from saved position when neither chapter nor position is explicitly requested
        assertEquals(150_000L, projector.resolveInitialPosition(null, null, saved))

        // Fully finished book (all chapters finished) restarts from 0 unless a specific chapter/offset is asked
        assertEquals(
            0L,
            projector.resolveInitialPosition(null, null, saved.copy(finishedChapters = setOf(0, 1, 2))),
        )

        // Explicit chapter tap starts at that chapter's startOffsetMs (+ optional chapter offset)
        assertEquals(180_000L, projector.resolveInitialPosition(requestedChapterIndex = 2, requestedPositionMs = null, savedProgress = saved))
        assertEquals(195_000L, projector.resolveInitialPosition(requestedChapterIndex = 2, requestedPositionMs = 15_000L, savedProgress = saved))
    }

    @Test
    fun `synthesizes single fallback chapter when chapter metadata is empty`() {
        val projector = AudibleChapterProjector(emptyList(), fallbackTitle = "Full Audiobook", fallbackTotalDurationMs = 300_000L)
        assertEquals(1, projector.chapters.size)
        val projected = projector.project(45_000L)
        assertEquals("Full Audiobook", projected.chapter.title)
        assertEquals(45_000L, projected.chapterPositionMs)
        assertEquals(300_000L, projected.chapterDurationMs)
    }
}
