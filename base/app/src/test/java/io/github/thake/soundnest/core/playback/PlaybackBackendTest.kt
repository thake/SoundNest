/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.core.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackBackendTest {

    private class FakeBackend(
        override val id: String,
        override var isActive: Boolean = false,
        override var holdsFocus: Boolean = false,
        private var playing: Boolean = false,
        private var moving: Boolean = false,
        private var duration: Long = 0L,
        private var position: Long = 0L,
        private val supportsSpeed: Boolean = false,
        private val supportsSkipBy: Boolean = false,
    ) : PlaybackBackend {
        var resumed = 0
        var paused = 0
        var nexts = 0
        var previouses = 0
        var lastSeekMs: Long? = null
        var stopped = 0
        var clearedQueued = 0
        var lastSpeed: Double? = null
        var lastSkipByMs: Long? = null

        override fun isPlaying(): Boolean = playing
        override fun isPositionMoving(): Boolean = moving
        override fun durationMs(): Long = duration
        override fun positionMs(): Long = position
        override fun resume() { resumed++ }
        override fun pause() { paused++ }
        override fun next() { nexts++ }
        override fun previous() { previouses++ }
        override fun seekTo(ms: Long) { lastSeekMs = ms }
        override fun stop() { stopped++; isActive = false }
        override fun clearQueued() { clearedQueued++ }
        override fun setSpeed(speed: Double): Boolean {
            if (!supportsSpeed) return false
            lastSpeed = speed
            return true
        }
        override fun skipBy(deltaMs: Long): Boolean {
            if (!supportsSkipBy) return false
            lastSkipByMs = deltaMs
            return true
        }
    }

    @Test
    fun `router returns null when no overlay backend is active`() {
        val audible = FakeBackend("audible", isActive = false)
        val local = FakeBackend("local", isActive = false)
        val router = OverlayBackendRouter(listOf(audible, local))

        assertNull(router.active())
        assertFalse(router.anyActive())
        assertFalse(router.anyHoldsFocus())
    }

    @Test
    fun `router respects priority order when multiple backends are active`() {
        val audible = FakeBackend("audible", isActive = true, playing = true, duration = 60_000L, position = 12_000L)
        val local = FakeBackend("local", isActive = true, playing = false, duration = 30_000L, position = 5_000L)
        val router = OverlayBackendRouter(listOf(audible, local))

        assertSame(audible, router.active())
        assertTrue(router.anyActive())
        assertEquals(60_000L, router.active()?.durationMs())
        assertEquals(12_000L, router.active()?.positionMs())
    }

    @Test
    fun `router falls through to second backend when first is inactive`() {
        val audible = FakeBackend("audible", isActive = false)
        val local = FakeBackend("local", isActive = true, holdsFocus = true, playing = true, moving = true)
        val router = OverlayBackendRouter(listOf(audible, local))

        assertSame(local, router.active())
        assertTrue(router.anyHoldsFocus())
        router.active()?.pause()
        router.active()?.seekTo(42_000L)
        router.active()?.clearQueued()

        assertEquals(1, local.paused)
        assertEquals(42_000L, local.lastSeekMs)
        assertEquals(1, local.clearedQueued)
        assertEquals(0, audible.paused)
    }

    @Test
    fun `stopAll stops every registered overlay backend`() {
        val audible = FakeBackend("audible", isActive = true)
        val local = FakeBackend("local", isActive = true)
        val router = OverlayBackendRouter(listOf(audible, local))

        router.stopAll()

        assertEquals(1, audible.stopped)
        assertEquals(1, local.stopped)
        assertFalse(router.anyActive())
    }

    @Test
    fun `optional speed and skipBy return false when unsupported`() {
        val local = FakeBackend("local", isActive = true, supportsSpeed = false, supportsSkipBy = false)
        val audible = FakeBackend("audible", isActive = true, supportsSpeed = true, supportsSkipBy = true)

        assertFalse(local.setSpeed(1.5))
        assertFalse(local.skipBy(15_000L))
        assertTrue(audible.setSpeed(1.5))
        assertTrue(audible.skipBy(15_000L))
        assertEquals(1.5, audible.lastSpeed)
        assertEquals(15_000L, audible.lastSkipByMs)
    }
}
