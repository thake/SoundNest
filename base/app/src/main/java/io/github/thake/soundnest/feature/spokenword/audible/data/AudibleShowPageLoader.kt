/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.spokenword.audible.data

import io.github.thake.soundnest.R

import io.github.thake.soundnest.feature.spokenword.audible.settings.AudibleCredentialStore

import io.github.thake.soundnest.audible.*

import android.content.Context
import io.github.thake.soundnest.core.model.PodcastModel
import io.github.thake.soundnest.core.model.ShowPage
import io.github.thake.soundnest.core.model.SongsModel
import io.github.thake.soundnest.feature.parental.filter.ParentalFilterEngine
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore
import io.github.thake.soundnest.spotify.stripHtml
import okhttp3.OkHttpClient

object AudibleUris {
    const val SHOW_ID_PREFIX = "audible:"
    const val BOOK_URI_PREFIX = "audible:book:"
    const val CHAPTER_URI_PREFIX = "audible:chapter:"

    fun bookUri(asin: String): String = "$BOOK_URI_PREFIX${asin.trim()}"
    fun showId(asin: String): String = "$SHOW_ID_PREFIX${asin.trim()}"
    fun chapterUri(asin: String, chapterIndex: Int): String = "$CHAPTER_URI_PREFIX${asin.trim()}:$chapterIndex"

    fun isAudibleShowId(id: String): Boolean =
        id.startsWith(SHOW_ID_PREFIX) && !id.startsWith(BOOK_URI_PREFIX) && !id.startsWith(CHAPTER_URI_PREFIX)

    fun isAudibleUri(uri: String): Boolean = uri.startsWith(SHOW_ID_PREFIX)

    fun asinFromAny(idOrUri: String): String? {
        val trimmed = idOrUri.trim()
        return when {
            trimmed.startsWith(CHAPTER_URI_PREFIX) ->
                trimmed.removePrefix(CHAPTER_URI_PREFIX).substringBefore(':').takeIf { it.isNotEmpty() }
            trimmed.startsWith(BOOK_URI_PREFIX) ->
                trimmed.removePrefix(BOOK_URI_PREFIX).substringBefore(':').takeIf { it.isNotEmpty() }
            trimmed.startsWith(SHOW_ID_PREFIX) ->
                trimmed.removePrefix(SHOW_ID_PREFIX).substringBefore(':').takeIf { it.isNotEmpty() }
            else -> null
        }
    }

    fun chapterIndexFromUri(uri: String): Int? {
        if (!uri.startsWith(CHAPTER_URI_PREFIX)) return null
        return uri.removePrefix(CHAPTER_URI_PREFIX).substringAfter(':', "").toIntOrNull()
    }
}

object AudibleShowPageLoader {
    private val defaultHttp by lazy { OkHttpClient() }

    suspend fun load(
        context: Context,
        showId: String,
        policy: ParentalPolicy = ParentalPolicyStore.current(context),
        fetchManifestChapters: suspend (String) -> List<AudibleChapter>? = { asin ->
            fetchChaptersForBook(context, asin)
        },
    ): Result<ShowPage> {
        val rawAsin = AudibleUris.asinFromAny(showId)
            ?: return Result.failure(IllegalArgumentException("Invalid Audible showId: $showId"))
        val asin = ParentalPolicyStore.normalizeAudibleAsin(rawAsin)
        if (asin.isEmpty() || !AudibleCredentialStore.isConnected(context) || !ParentalFilterEngine.isAudibleBookAllowed(asin, policy)) {
            return Result.failure(IllegalStateException(context.getString(R.string.blocked_by_parental_controls)))
        }
        val book = AudibleCatalogCache.book(context, asin)
            ?: return Result.failure(IllegalStateException("Approved Audible book not found in local catalog: $asin"))

        var chapters = AudibleCatalogCache.chapters(context, asin).orEmpty()
        if (shouldRefreshChapters(chapters, book.runtimeLengthMs)) {
            val fetched = runCatching { fetchManifestChapters(asin) }
                .onFailure { err ->
                    io.github.thake.soundnest.host.android.Logbook.log("AudibleShowPageLoader: chapter fetch failed for $asin: ${err.message}")
                }
                .getOrNull()
                .orEmpty()
            if (fetched.isNotEmpty() && (chapters.isEmpty() || fetched.size > chapters.size)) {
                AudibleCatalogCache.saveChapters(context, asin, fetched)
                chapters = fetched
            }
        }

        val projector = AudibleChapterProjector(
            rawChapters = chapters,
            fallbackTitle = book.title,
            fallbackTotalDurationMs = book.runtimeLengthMs,
        )
        val progress = AudibleProgressStore.get(context, asin)
        val activeProj = if (progress != null && progress.positionMs > 0L) {
            projector.project(progress.positionMs)
        } else {
            null
        }
        val authorLabel = book.authors.joinToString(", ").ifBlank { "Audible" }

        val rows = projector.chapters.map { ch ->
            val chapterUri = AudibleUris.chapterUri(asin, ch.index)
            val isFinished = progress?.finishedChapters?.contains(ch.index) == true
            val durationInt = ch.lengthMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val playedPosLong = when {
                isFinished -> ch.lengthMs
                activeProj != null && activeProj.chapter.index == ch.index ->
                    activeProj.chapterPositionMs
                else -> 0L
            }
            SongsModel(
                id = ("uri:$chapterUri").hashCode() and 0x7fffffff,
                title = ch.title.ifBlank { context.getString(R.string.audible_chapter_number, ch.index + 1) },
                album = book.title,
                singer = authorLabel,
                coverUri = book.coverUrl,
                url = chapterUri,
                durationMs = durationInt,
                releaseDate = "",
                playedPositionMs = playedPosLong,
                played = isFinished,
                showId = AudibleUris.showId(asin),
            )
        }

        val seriesPart = book.seriesTitle?.trim()?.takeIf { it.isNotEmpty() }?.let { series ->
            val seq = book.seriesSequence?.trim()?.takeIf { it.isNotEmpty() }
            if (seq != null) "$series #$seq" else series
        }.orEmpty()
        val rawSubtitle = book.subtitle.trim()
        val bookSubtitle = listOf(seriesPart, rawSubtitle)
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(" · ")

        val showModel = PodcastModel(
            id = AudibleUris.showId(asin),
            name = book.title,
            publisher = authorLabel,
            coverUri = book.coverUrl,
            description = stripHtml(book.description).trim(),
            rating = 0.0,
            saved = false,
            authors = book.authors,
            narrators = book.narrators,
            audiobook = true,
            subtitle = bookSubtitle,
            runtimeMs = book.runtimeLengthMs.takeIf { it > 0L } ?: projector.totalDurationMs,
            releaseDate = book.releaseDate.orEmpty(),
        )

        return Result.success(
            ShowPage(
                show = showModel,
                coverUri = book.coverUrl,
                audiobook = true,
                items = rows,
            ),
        )
    }

    private fun shouldRefreshChapters(cached: List<AudibleChapter>, runtimeLengthMs: Long): Boolean {
        if (cached.isEmpty()) return true
        // Self-heal a stale single-entry header cached from an unflattened tree response
        if (cached.size == 1 && runtimeLengthMs > 60_000L) {
            val onlyLen = cached.first().lengthMs
            if (onlyLen in 0L until (runtimeLengthMs / 2L)) return true
        }
        return false
    }

    private suspend fun fetchChaptersForBook(context: Context, asin: String): List<AudibleChapter>? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val tokens = AudibleCredentialStore.loadTokens(context) ?: return@withContext null
            val client = AudibleApiClient(
                http = defaultHttp,
                tokensProvider = { AudibleCredentialStore.loadTokens(context) ?: tokens },
            )
            val fromMetadata = runCatching { client.fetchChapters(asin) }
                .onFailure { err ->
                    io.github.thake.soundnest.host.android.Logbook.log("AudibleShowPageLoader: metadata chapters failed for $asin: ${err.message}")
                }
                .getOrNull()
                .orEmpty()
            if (fromMetadata.isNotEmpty()) return@withContext fromMetadata

            runCatching { client.requestStreamManifest(asin).chapters }
                .onFailure { err ->
                    io.github.thake.soundnest.host.android.Logbook.log("AudibleShowPageLoader: license chapters fallback failed for $asin: ${err.message}")
                }
                .getOrNull()
        }
}
