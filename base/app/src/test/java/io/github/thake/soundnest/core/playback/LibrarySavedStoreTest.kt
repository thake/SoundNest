/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.core.playback

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySavedStoreTest {

    @Test
    fun `check caches fresh entries and skips redundant fetches until TTL expires`() = runTest {
        var now = 1_000L
        var fetchCalls = 0
        val store = LibrarySavedStore(
            scope = this,
            clock = { now },
            postMain = { it() },
            freshMs = 60_000L,
            fetcher = { uris ->
                fetchCalls++
                uris.map { it == "spotify:track:1" }
            },
        )

        val first = store.check(listOf("spotify:track:1", "spotify:track:2"))
        assertFalse(first.failed)
        assertEquals(setOf("spotify:track:1"), first.saved)
        assertEquals(setOf("spotify:track:1", "spotify:track:2"), first.asked)
        assertEquals(1, fetchCalls)

        // Within TTL: served from memory
        now += 30_000L
        val second = store.check(listOf("spotify:track:1", "spotify:track:2"))
        assertFalse(second.failed)
        assertEquals(setOf("spotify:track:1"), second.saved)
        assertTrue(second.asked.isEmpty())
        assertEquals(1, fetchCalls)

        // Past TTL: re-fetched
        now += 40_000L
        val third = store.check(listOf("spotify:track:1"))
        assertEquals(setOf("spotify:track:1"), third.asked)
        assertEquals(2, fetchCalls)
    }

    @Test
    fun `concurrent checks for the same uris share a single in-flight request`() = runTest {
        val gate = CompletableDeferred<List<Boolean>>()
        var fetchCalls = 0
        val store = LibrarySavedStore(
            scope = this,
            clock = { 1_000L },
            postMain = { it() },
            fetcher = {
                fetchCalls++
                gate.await()
            },
        )

        val a = async { store.check(listOf("spotify:track:b", "spotify:track:a")) }
        val b = async { store.check(listOf("spotify:track:a", "spotify:track:b")) }
        testScheduler.runCurrent()
        assertEquals(1, fetchCalls)

        gate.complete(listOf(true, false))
        assertEquals(a.await().saved, b.await().saved)
        assertEquals(1, fetchCalls)
    }

    @Test
    fun `optimistic intent overrides stale responses until window expires`() = runTest {
        var now = 5_000L
        val store = LibrarySavedStore(
            scope = this,
            clock = { now },
            postMain = { it() },
            intentHoldMs = 3_000L,
            fetcher = { listOf(false) },
        )

        store.recordIntent("spotify:track:1", saved = true)
        assertTrue(store.likedFor("spotify:track:1", reported = false))
        assertFalse(store.applyItem("spotify:track:1", saved = false))
        assertEquals(true, store.library["spotify:track:1"])

        // After intent window expires, server value is accepted
        now += 3_500L
        assertFalse(store.likedFor("spotify:track:1", reported = false))
        assertTrue(store.applyItem("spotify:track:1", saved = false))
        assertEquals(false, store.library["spotify:track:1"])
    }

    @Test
    fun `fetcher failure marks check failed and records lastError`() = runTest {
        val store = LibrarySavedStore(
            scope = this,
            clock = { 1_000L },
            postMain = { it() },
            fetcher = { throw IllegalStateException("offline") },
        )

        val result = store.check(listOf("spotify:track:1"))
        assertTrue(result.failed)
        assertEquals("offline", store.lastError)
    }
}
