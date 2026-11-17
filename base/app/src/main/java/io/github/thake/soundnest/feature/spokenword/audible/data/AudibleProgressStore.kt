/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.spokenword.audible.data

import io.github.thake.soundnest.audible.*

import android.content.Context
import io.github.thake.soundnest.core.util.writeTextSafely
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class BookProgress(
    val asin: String,
    val positionMs: Long = 0L,
    val chapterIndex: Int = 0,
    val updatedAtMs: Long = 0L,
    val finishedChapters: Set<Int> = emptySet(),
)

@Serializable
private data class PersistedProgressMap(
    val entries: Map<String, BookProgress> = emptyMap(),
)

/**
 * Strictly local-only playback progress store for Audible audiobooks.
 * Never syncs back to Amazon/Audible WhisperSync so the parent's own bookmarks stay untouched.
 */
object AudibleProgressStore {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    @Volatile
    private var entries: MutableMap<String, BookProgress>? = null

    private fun progressFile(context: Context): File =
        File(context.applicationContext.filesDir, "audible/progress.json")

    @Synchronized
    private fun ensureLoaded(context: Context): MutableMap<String, BookProgress> {
        entries?.let { return it }
        val file = progressFile(context)
        val loaded = if (file.exists()) {
            runCatching { json.decodeFromString<PersistedProgressMap>(file.readText()).entries }
                .getOrDefault(emptyMap())
        } else {
            emptyMap()
        }
        return LinkedHashMap(loaded).also { entries = it }
    }

    @Synchronized
    private fun flush(context: Context, map: Map<String, BookProgress>, syncDisk: Boolean = true) {
        val text = json.encodeToString(PersistedProgressMap(entries = map))
        runCatching { writeTextSafely(progressFile(context), text, sync = syncDisk) }
        _revision.value++
    }

    @Synchronized
    fun get(context: Context, asin: String): BookProgress? =
        ensureLoaded(context)[asin.trim()]

    @Synchronized
    fun savePosition(
        context: Context,
        asin: String,
        positionMs: Long,
        chapterIndex: Int,
        nowMs: Long = System.currentTimeMillis(),
        syncDisk: Boolean = true,
    ): BookProgress {
        val clean = asin.trim()
        val map = ensureLoaded(context)
        val existing = map[clean] ?: BookProgress(asin = clean)
        val updated = existing.copy(
            positionMs = positionMs.coerceAtLeast(0L),
            chapterIndex = chapterIndex.coerceAtLeast(0),
            updatedAtMs = nowMs,
        )
        map[clean] = updated
        flush(context, map, syncDisk = syncDisk)
        return updated
    }

    @Synchronized
    fun setChapterFinished(
        context: Context,
        asin: String,
        chapterIndex: Int,
        finished: Boolean,
        nowMs: Long = System.currentTimeMillis(),
    ): BookProgress {
        val clean = asin.trim()
        val map = ensureLoaded(context)
        val existing = map[clean] ?: BookProgress(asin = clean)
        val updatedSet = if (finished) existing.finishedChapters + chapterIndex else existing.finishedChapters - chapterIndex
        val updated = existing.copy(
            finishedChapters = updatedSet,
            updatedAtMs = nowMs,
        )
        map[clean] = updated
        flush(context, map, syncDisk = true)
        return updated
    }

    @Synchronized
    fun resetBook(context: Context, asin: String) {
        val map = ensureLoaded(context)
        if (map.remove(asin.trim()) != null) {
            flush(context, map, syncDisk = true)
        }
    }

    @Synchronized
    fun clearAll(context: Context) {
        entries = LinkedHashMap()
        runCatching { progressFile(context).delete() }
        _revision.value++
    }

    @Synchronized
    internal fun resetMemoryForTests() {
        entries = null
    }
}
