/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.core.playback

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Caches Spotify library saved/liked state with TTL freshness, single-flight request
 * deduplication, and brief optimistic intent windows so rapid taps aren't overwritten by
 * in-flight responses.
 */
class LibrarySavedStore(
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val postMain: (() -> Unit) -> Unit,
    private val fetcher: suspend (List<String>) -> List<Boolean>,
    private val freshMs: Long = DEFAULT_FRESH_MS,
    private val intentHoldMs: Long = DEFAULT_INTENT_HOLD_MS,
) {
    /** Result of [check]: [saved] URIs, [asked] URIs fetched remotely, and [failed] if remote lookup failed. */
    data class CheckResult(
        val saved: Set<String>,
        val asked: Set<String>,
        val failed: Boolean,
    )

    val library: SnapshotStateMap<String, Boolean> = mutableStateMapOf()

    private val savedAt = ConcurrentHashMap<String, Long>()
    private val savedFlights = ConcurrentHashMap<String, Deferred<Set<String>?>>()
    private val savedFlightLock = Mutex()

    @Volatile var lastError: String = ""
        private set

    @Volatile private var intentUri: String = ""
    @Volatile private var intentValue: Boolean = false
    @Volatile private var intentUntil: Long = 0L

    fun isFresh(uri: String): Boolean =
        library.containsKey(uri) && clock() - (savedAt[uri] ?: 0L) < freshMs

    fun recordIntent(uri: String, saved: Boolean) {
        intentUri = uri
        intentValue = saved
        intentUntil = clock() + intentHoldMs
        applyItem(uri, saved)
    }

    fun clearIntent() {
        intentUntil = 0L
    }

    fun likedFor(uri: String, reported: Boolean): Boolean =
        if (uri == intentUri && clock() < intentUntil) intentValue else reported

    /**
     * Writes [saved] for [uri] unless an active optimistic intent holds the opposite value.
     * Returns true if the cache was updated.
     */
    fun applyItem(uri: String, saved: Boolean): Boolean {
        if (uri == intentUri && clock() < intentUntil && saved != intentValue) return false
        library[uri] = saved
        savedAt[uri] = clock()
        return true
    }

    suspend fun check(uris: List<String>): CheckResult {
        if (uris.isEmpty()) return CheckResult(emptySet(), emptySet(), false)
        val now = clock()
        val (known, unknown) = uris.partition { library.containsKey(it) && now - (savedAt[it] ?: 0L) < freshMs }
        val out = known.filterTo(mutableSetOf()) { library[it] == true }
        if (unknown.isEmpty()) return CheckResult(out, emptySet(), false)
        val saved = fetchSavedSet(unknown) ?: return CheckResult(out, emptySet(), true)
        postMain {
            for (u in unknown) {
                if (u == intentUri && clock() < intentUntil) continue
                library[u] = u in saved
                savedAt[u] = now
            }
        }
        return CheckResult(out + saved, unknown.toSet(), false)
    }

    private suspend fun fetchSavedSet(uris: List<String>): Set<String>? {
        val canonical = uris.distinct().sorted()
        val key = canonical.joinToString("\n")
        val flight = savedFlightLock.withLock {
            savedFlights[key] ?: scope.async {
                try {
                    val flags = fetcher(canonical)
                    canonical.filterIndexed { i, _ -> flags.getOrNull(i) == true }.toSet()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError = e.message ?: e.javaClass.simpleName
                    null
                }
            }.also { d ->
                savedFlights[key] = d
                d.invokeOnCompletion { savedFlights.remove(key, d) }
            }
        }
        return try {
            flight.await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    fun clear() {
        intentUri = ""
        intentUntil = 0L
        library.clear()
        savedAt.clear()
    }

    companion object {
        const val DEFAULT_FRESH_MS: Long = 60_000L
        const val DEFAULT_INTENT_HOLD_MS: Long = 3_000L
    }
}
