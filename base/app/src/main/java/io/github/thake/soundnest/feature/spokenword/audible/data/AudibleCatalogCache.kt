/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.spokenword.audible.data

import io.github.thake.soundnest.feature.spokenword.audible.settings.AudibleCredentialStore

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
private data class PersistedAudibleCatalog(
    val books: List<AudibleBook> = emptyList(),
    val chaptersByAsin: Map<String, List<AudibleChapter>> = emptyMap(),
)

object AudibleCatalogCache {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    @Volatile
    private var state: PersistedAudibleCatalog? = null

    private fun cacheFile(context: Context): File =
        File(context.applicationContext.filesDir, "audible/catalog.json")

    @Synchronized
    private fun ensureLoaded(context: Context): PersistedAudibleCatalog {
        state?.let { return it }
        val file = cacheFile(context)
        val loaded = if (file.exists()) {
            runCatching { json.decodeFromString<PersistedAudibleCatalog>(file.readText()) }
                .getOrDefault(PersistedAudibleCatalog())
        } else {
            PersistedAudibleCatalog()
        }
        state = loaded
        return loaded
    }

    @Synchronized
    private fun persist(context: Context, updated: PersistedAudibleCatalog) {
        state = updated
        runCatching { writeTextSafely(cacheFile(context), json.encodeToString(updated)) }
        _revision.value++
    }

    @Synchronized
    fun saveBooks(context: Context, books: List<AudibleBook>) {
        val current = ensureLoaded(context)
        val validAsins = books.map { it.asin }.toSet()
        val keptChapters = current.chaptersByAsin.filterKeys { it in validAsins }
        persist(context, PersistedAudibleCatalog(books = books, chaptersByAsin = keptChapters))
    }

    @Synchronized
    fun books(context: Context): List<AudibleBook> = ensureLoaded(context).books

    @Synchronized
    fun book(context: Context, asin: String): AudibleBook? {
        val clean = asin.trim()
        return ensureLoaded(context).books.firstOrNull { it.asin == clean }
    }

    @Synchronized
    fun saveChapters(context: Context, asin: String, chapters: List<AudibleChapter>) {
        val clean = asin.trim()
        if (clean.isEmpty()) return
        val current = ensureLoaded(context)
        persist(context, current.copy(chaptersByAsin = current.chaptersByAsin + (clean to chapters)))
    }

    @Synchronized
    fun chapters(context: Context, asin: String): List<AudibleChapter>? =
        ensureLoaded(context).chaptersByAsin[asin.trim()]

    @Synchronized
    fun clear(context: Context) {
        state = PersistedAudibleCatalog()
        runCatching { cacheFile(context).delete() }
        _revision.value++
    }

    fun approvedLibraryEntries(
        context: Context,
        policy: io.github.thake.soundnest.feature.parental.settings.ParentalPolicy = io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore.current(context),
    ): List<io.github.thake.soundnest.core.model.LibraryEntry> {
        if (!policy.spokenWord.allowAudible || !AudibleCredentialStore.isConnected(context)) return emptyList()
        val pinnedSet = policy.spokenWord.audible.pinnedAsins
        return books(context)
            .filter { io.github.thake.soundnest.feature.parental.filter.ParentalFilterEngine.isAudibleBookAllowed(it.asin, policy) }
            .map { book ->
                val authorsLabel = book.authors.joinToString(", ").ifBlank { "Audible" }
                io.github.thake.soundnest.core.model.LibraryEntry(
                    spotifyId = AudibleUris.showId(book.asin),
                    name = book.title,
                    subtitle = authorsLabel,
                    coverUri = book.coverUrl,
                    isPlaylist = false,
                    uri = AudibleUris.bookUri(book.asin),
                    artists = book.authors.joinToString(", "),
                    pinned = book.asin in pinnedSet,
                )
            }
    }

    fun matchingShows(
        context: Context,
        query: String,
        policy: io.github.thake.soundnest.feature.parental.settings.ParentalPolicy = io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore.current(context),
    ): List<io.github.thake.soundnest.core.model.PodcastModel> {
        val needle = query.trim()
        if (needle.isEmpty() || !policy.spokenWord.allowAudible || !AudibleCredentialStore.isConnected(context)) return emptyList()
        return books(context)
            .filter { book ->
                io.github.thake.soundnest.feature.parental.filter.ParentalFilterEngine.isAudibleBookAllowed(book.asin, policy) &&
                    (
                        book.title.contains(needle, ignoreCase = true) ||
                            book.subtitle.contains(needle, ignoreCase = true) ||
                            book.authors.any { it.contains(needle, ignoreCase = true) } ||
                            book.narrators.any { it.contains(needle, ignoreCase = true) } ||
                            (book.seriesTitle?.contains(needle, ignoreCase = true) == true)
                        )
            }
            .map { book ->
                io.github.thake.soundnest.core.model.PodcastModel(
                    id = AudibleUris.showId(book.asin),
                    name = book.title,
                    publisher = book.authors.joinToString(", ").ifBlank { "Audible" },
                    coverUri = book.coverUrl,
                    description = io.github.thake.soundnest.spotify.stripHtml(book.description).trim(),
                    authors = book.authors,
                    narrators = book.narrators,
                    audiobook = true,
                )
            }
    }

    @Synchronized
    internal fun resetMemoryForTests() {
        state = null
    }
}
