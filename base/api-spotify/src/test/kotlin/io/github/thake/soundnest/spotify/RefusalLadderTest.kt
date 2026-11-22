package io.github.thake.soundnest.spotify

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RefusalLadderTest {

    @Test
    fun advancesStepsFromOneToThreeWhenTrackWasPicked() {
        val ladder = RefusalLadder()
        assertEquals(1, ladder.nextStep(hasTrack = true))
        assertEquals(2, ladder.nextStep(hasTrack = true))
        assertEquals(3, ladder.nextStep(hasTrack = true))
        assertEquals(4, ladder.nextStep(hasTrack = true))

        ladder.reset()
        assertEquals(1, ladder.nextStep(hasTrack = true))
    }

    @Test
    fun skipsStepOneWhenNoSpecificTrackWasPicked() {
        val ladder = RefusalLadder()
        assertEquals(2, ladder.nextStep(hasTrack = false))
        assertEquals(3, ladder.nextStep(hasTrack = false))
    }

    @Test
    fun detectsTwoDistinctRefusedKindsWithinWindowAndEvictsExpiredKinds() {
        var now = 1_000L
        val ladder = RefusalLadder(kindWindowMs = 10_000L)

        assertFalse(ladder.recordRefusedKind("play", now))
        // Same kind repeated does not trigger refused-everything
        now += 1_000L
        assertFalse(ladder.recordRefusedKind("play", now))

        // Distinct second kind within window triggers refused-everything
        now += 2_000L
        assertTrue(ladder.recordRefusedKind("seek", now))

        // After window expires, old kinds are evicted
        now += 11_000L
        assertFalse(ladder.recordRefusedKind("shuffle", now))
    }

    @Test
    fun clearKindsRemovesTrackedHistory() {
        val ladder = RefusalLadder(kindWindowMs = 10_000L)
        assertFalse(ladder.recordRefusedKind("play", 1_000L))
        ladder.clearKinds()
        assertFalse(ladder.recordRefusedKind("seek", 2_000L))
    }
}
