package io.github.thake.soundnest.host.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidevineLimitGuardTest {

    @Test
    fun triggersHoldOnlyWhenThresholdReachedInsideWindow() {
        var now = 1_000L
        val logs = mutableListOf<String>()
        val guard = WidevineLimitGuard(clock = { now }, log = { logs.add(it) })

        repeat(WidevineLimitGuard.LIMIT_COUNT - 1) {
            assertFalse(guard.onLicenseLimited())
            assertFalse(guard.isLimited())
            now += 1_000L
        }
        assertTrue(guard.onLicenseLimited())
        assertTrue(guard.isLimited())
        assertEquals(now + WidevineLimitGuard.LIMIT_HOLD_MS, guard.limitedUntil.value)
        assertEquals(1, logs.size)

        // Hold expires once clock passes limitedUntil
        now += WidevineLimitGuard.LIMIT_HOLD_MS
        assertFalse(guard.isLimited())
    }

    @Test
    fun evictsOld429TimestampsOutsideWindow() {
        var now = 1_000L
        val guard = WidevineLimitGuard(clock = { now })

        repeat(WidevineLimitGuard.LIMIT_COUNT - 1) {
            guard.onLicenseLimited()
            now += 1_000L
        }
        // Advance past the 10s window so earlier events expire
        now += WidevineLimitGuard.LIMIT_WINDOW_MS + 1L
        assertFalse(guard.onLicenseLimited())
        assertFalse(guard.isLimited())
    }

    @Test
    fun escalatesHoldDurationUpToMaxAndResetsAfterForgetWindow() {
        var now = 1_000L
        val guard = WidevineLimitGuard(clock = { now })

        fun triggerStorm(): Long {
            repeat(WidevineLimitGuard.LIMIT_COUNT) { guard.onLicenseLimited() }
            return guard.limitedUntil.value - now
        }

        // Streak 0 -> 60s
        assertEquals(60_000L, triggerStorm())
        now += 61_000L
        // Streak 1 -> 120s
        assertEquals(120_000L, triggerStorm())
        now += 121_000L
        // Streak 2 -> 240s
        assertEquals(240_000L, triggerStorm())
        now += 241_000L
        // Streak 3 -> 480s
        assertEquals(480_000L, triggerStorm())
        now += 481_000L
        // Streak 4 -> capped at 600s
        assertEquals(WidevineLimitGuard.LIMIT_HOLD_MAX_MS, triggerStorm())

        // Quiet period > LIMIT_FORGET_MS resets streak back to 60s
        now += WidevineLimitGuard.LIMIT_FORGET_MS + 1_000L
        assertEquals(60_000L, triggerStorm())
    }

    @Test
    fun tracksHeldRequestsAndRetryAfterSeconds() {
        var now = 5_000L
        val guard = WidevineLimitGuard(clock = { now })
        repeat(WidevineLimitGuard.LIMIT_COUNT) { guard.onLicenseLimited() }

        assertTrue(guard.recordHeldIsFirst())
        assertFalse(guard.recordHeldIsFirst())
        assertEquals(60L, guard.retryAfterSeconds())

        now += 59_800L
        assertEquals(1L, guard.retryAfterSeconds())
    }

    @Test
    fun tracksRecentNon429RefusalWindow() {
        var now = 10_000L
        val guard = WidevineLimitGuard(clock = { now })

        assertFalse(guard.refusedWithin(5_000L))
        guard.recordRefused()
        assertTrue(guard.refusedWithin(5_000L))

        now += 5_000L
        assertFalse(guard.refusedWithin(5_000L))
    }
}
