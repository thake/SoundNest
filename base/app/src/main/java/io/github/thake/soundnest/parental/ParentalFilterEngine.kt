/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.parental

import io.github.thake.soundnest.data.api.Api
import io.github.thake.soundnest.data.entity.AlbumsModel
import io.github.thake.soundnest.data.entity.ArtistsModel
import io.github.thake.soundnest.data.entity.HomeItem
import io.github.thake.soundnest.data.entity.LibraryEntry
import io.github.thake.soundnest.data.entity.PodcastModel
import io.github.thake.soundnest.data.entity.SongsModel
import io.github.thake.soundnest.data.local.LocalFileMatch
import java.text.Normalizer

/**
 * Hybrid parental content filter supporting both:
 * - [FilterMode.FILTERED_OPEN]: full Spotify catalog minus explicit items, blocked IDs, cascading
 *   blocked artists/albums, and normalized keyword/regex rules (with explicit parent allow-overrides).
 * - [FilterMode.STRICT_ALLOWLIST]: fail-closed whitelist where only parent-approved tracks, albums,
 *   artists, playlists, and shows (and clean tracks inside allowlisted containers) are visible and playable.
 */
object ParentalFilterEngine {

    private val DIACRITICS = Regex("\\p{Mn}+")
    private val NON_WORD_SPACES = Regex("[^\\p{L}\\p{N}]+")
    private val MULTI_SPACE = Regex("\\s+")

    /** Strips accents/diacritics and lowercases while preserving digits/punctuation for regex checks. */
    fun stripDiacriticsLower(raw: String): String {
        if (raw.isBlank()) return ""
        val decomposed = Normalizer.normalize(raw, Normalizer.Form.NFKD)
        return DIACRITICS.replace(decomposed, "").lowercase()
    }

    /**
     * Normalizes text for anti-evasion keyword matching:
     * - NFKD Unicode decomposition + diacritic removal (`éxplïcît` -> `explicit`)
     * - Leetspeak / symbol folding (`@`->`a`, `$`->`s`, `0`->`o`, `1`->`i`, `!`->`i`, `3`->`e`, `4`->`a`, `5`->`s`, `7`->`t`)
     * - Punctuation collapsed to single spaces so word-boundary checks remain accurate.
     */
    fun normalizeForKeywords(raw: String): String {
        if (raw.isBlank()) return ""
        val base = stripDiacriticsLower(raw)
        val sb = StringBuilder(base.length)
        for (ch in base) {
            val mapped = when (ch) {
                '@' -> 'a'
                '$' -> 's'
                '0' -> 'o'
                '1', '!' -> 'i'
                '3' -> 'e'
                '4' -> 'a'
                '5' -> 's'
                '7' -> 't'
                else -> ch
            }
            sb.append(mapped)
        }
        return NON_WORD_SPACES.replace(sb.toString(), " ").replace(MULTI_SPACE, " ").trim()
    }

    private val CENSORED_TOKEN_SPLIT = Regex("[^\\p{L}\\p{N}@*\$!]+")
    private const val VOWELS = "aeiou"

    private fun foldLeetChar(ch: Char): Char = when (ch) {
        '@' -> 'a'
        '$' -> 's'
        '0' -> 'o'
        '1', '!' -> 'i'
        '3' -> 'e'
        '4' -> 'a'
        '5' -> 's'
        '7' -> 't'
        else -> ch
    }

    /** Matches words where `@` or `*` masks a vowel (e.g. `f@ck` -> `fuck`, `sh*t` -> `shit`). */
    private fun matchesCensoredWord(rawToken: String, normKeyword: String): Boolean {
        if (normKeyword.length < 3 || rawToken.length != normKeyword.length) return false
        if ('@' !in rawToken && '*' !in rawToken) return false
        var wildcards = 0
        for (i in rawToken.indices) {
            val rawCh = rawToken[i]
            val kwCh = normKeyword[i]
            if (foldLeetChar(rawCh) == kwCh) continue
            if ((rawCh == '@' || rawCh == '*') && kwCh in VOWELS) {
                wildcards++
                continue
            }
            return false
        }
        return wildcards > 0 && (normKeyword.length - wildcards) >= 2
    }

    /** Plain word-tokenized lowercase without digit-folding so numeric keywords like `"1989"` also match. */
    private fun normalizePlainWords(raw: String): String {
        if (raw.isBlank()) return ""
        return NON_WORD_SPACES.replace(stripDiacriticsLower(raw), " ").replace(MULTI_SPACE, " ").trim()
    }

    fun matchesBlockedText(policy: ParentalPolicy, vararg fields: String): Boolean {
        if (policy.blockedKeywords.isEmpty() && policy.blockedRegexPatterns.isEmpty()) return false
        val nonBlank = fields.filter { it.isNotBlank() }
        if (nonBlank.isEmpty()) return false

        if (policy.blockedKeywords.isNotEmpty()) {
            for (field in nonBlank) {
                val stripped = stripDiacriticsLower(field)
                val rawTokens = CENSORED_TOKEN_SPLIT.split(stripped).filter { it.isNotEmpty() }
                val foldedPadded = " ${normalizeForKeywords(field)} "
                val plainPadded = " ${normalizePlainWords(field)} "
                for (kw in policy.blockedKeywords) {
                    val normKw = normalizeForKeywords(kw)
                    val plainKw = normalizePlainWords(kw)
                    if (normKw.isNotEmpty() && foldedPadded.contains(" $normKw ")) return true
                    if (plainKw.isNotEmpty() && plainPadded.contains(" $plainKw ")) return true
                    if (normKw.length >= 3 && !normKw.contains(' ') && rawTokens.any { matchesCensoredWord(it, normKw) }) {
                        return true
                    }
                }
            }
        }

        if (policy.blockedRegexPatterns.isNotEmpty()) {
            val compiled = policy.blockedRegexPatterns.mapNotNull { pat ->
                runCatching { Regex(pat, RegexOption.IGNORE_CASE) }.getOrNull()
            }
            for (field in nonBlank) {
                val stripped = stripDiacriticsLower(field)
                val folded = normalizeForKeywords(field)
                for (rx in compiled) {
                    if (rx.containsMatchIn(field) || rx.containsMatchIn(stripped) || rx.containsMatchIn(folded)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun isContextAllowlisted(contextUri: String?, policy: ParentalPolicy): Boolean {
        if (contextUri.isNullOrBlank()) return false
        if (contextUri == "spotify:collection:tracks") return false // Liked Songs filters per-track in strict mode
        val parts = contextUri.split(':')
        if (parts.size < 3 || parts[0] != "spotify") return false
        val kind = parts[1]
        val id = parts.last()
        return when (kind) {
            "playlist" -> id in policy.allowedPlaylistIds
            "album" -> id in policy.allowedAlbumIds
            "artist" -> id in policy.allowedArtistIds
            "show" -> id in policy.allowedShowIds
            else -> false
        }
    }

    fun isTrackAllowed(
        song: SongsModel,
        policy: ParentalPolicy,
        parentContextUri: String? = null,
        isAudiobookChapter: Boolean = false,
    ): Boolean {
        // 1. Podcast episodes / audiobook chapters
        if (song.isEpisode || song.showId.isNotBlank()) {
            val gateOpen = if (isAudiobookChapter) policy.allowAudiobooks else policy.allowPodcasts
            if (!gateOpen) return false
            val episodeId = song.url.removePrefix("episode:").substringBefore('|').trim()
            val showId = song.showId.trim()
            if (episodeId.isNotEmpty() && episodeId in policy.blockedTrackIds) return false
            if (showId.isNotEmpty() && showId in policy.blockedShowIds) return false
            val explicitlyAllowedEpisode = episodeId.isNotEmpty() && episodeId in policy.allowedTrackIds
            if (explicitlyAllowedEpisode) return true
            if (policy.blockExplicit && song.explicit) return false
            if (matchesBlockedText(policy, song.title, song.album, song.singer, song.description)) return false
            return when (policy.filterMode) {
                FilterMode.FILTERED_OPEN -> true
                FilterMode.STRICT_ALLOWLIST ->
                    (showId.isNotEmpty() && showId in policy.allowedShowIds) ||
                        isContextAllowlisted(parentContextUri, policy)
            }
        }

        // 2. Local device files
        val isLocal = song.url.startsWith("localfile:") || song.url.startsWith(LocalFileMatch.SPOTIFY_PREFIX) || song.url.startsWith("content://")
        if (isLocal && song.spotifyTrackId.isBlank()) {
            if (!policy.allowLocalFiles) return false
            if (matchesBlockedText(policy, song.title, song.album, song.singer)) return false
            return policy.filterMode == FilterMode.FILTERED_OPEN
        }

        // 3. Spotify music tracks
        val trackId = ParentalPolicyStore.normalizeSpotifyId(song.spotifyTrackId)
        val albumId = ParentalPolicyStore.normalizeSpotifyId(song.albumId)
        val artistIds = song.artistIds.map(ParentalPolicyStore::normalizeSpotifyId).filter { it.isNotEmpty() }

        if (trackId.isNotEmpty() && trackId in policy.blockedTrackIds) return false
        val explicitlyAllowedTrack = trackId.isNotEmpty() && trackId in policy.allowedTrackIds
        if (explicitlyAllowedTrack) return true

        if (artistIds.any { it in policy.blockedArtistIds }) return false
        if (albumId.isNotEmpty() && albumId in policy.blockedAlbumIds) return false
        if (policy.blockExplicit && song.explicit) return false
        if (matchesBlockedText(policy, song.title, song.album, song.singer)) return false

        return when (policy.filterMode) {
            FilterMode.FILTERED_OPEN -> true
            FilterMode.STRICT_ALLOWLIST ->
                (albumId.isNotEmpty() && albumId in policy.allowedAlbumIds) ||
                    artistIds.any { it in policy.allowedArtistIds } ||
                    isContextAllowlisted(parentContextUri, policy)
        }
    }

    fun isAlbumAllowed(
        albumId: String,
        name: String,
        artists: String = "",
        artistIds: List<String> = emptyList(),
        explicit: Boolean = false,
        policy: ParentalPolicy,
    ): Boolean {
        val id = ParentalPolicyStore.normalizeSpotifyId(albumId)
        if (id.isNotEmpty() && id in policy.blockedAlbumIds) return false
        val explicitlyAllowed = id.isNotEmpty() && id in policy.allowedAlbumIds
        if (explicitlyAllowed) return true

        val normArtists = artistIds.map(ParentalPolicyStore::normalizeSpotifyId).filter { it.isNotEmpty() }
        if (normArtists.any { it in policy.blockedArtistIds }) return false
        if (policy.blockExplicit && explicit) return false
        if (matchesBlockedText(policy, name, artists)) return false

        return when (policy.filterMode) {
            FilterMode.FILTERED_OPEN -> true
            FilterMode.STRICT_ALLOWLIST -> normArtists.any { it in policy.allowedArtistIds }
        }
    }

    fun isAlbumAllowed(album: AlbumsModel, policy: ParentalPolicy): Boolean =
        isAlbumAllowed(
            albumId = album.spotifyId,
            name = album.name,
            artists = album.artists,
            policy = policy,
        )

    fun isArtistAllowed(
        artistId: String,
        name: String,
        bio: String = "",
        policy: ParentalPolicy,
    ): Boolean {
        val id = ParentalPolicyStore.normalizeSpotifyId(artistId)
        if (id.isNotEmpty() && id in policy.blockedArtistIds) return false
        if (id.isNotEmpty() && id in policy.allowedArtistIds) return true
        if (matchesBlockedText(policy, name, bio)) return false
        return when (policy.filterMode) {
            FilterMode.FILTERED_OPEN -> true
            FilterMode.STRICT_ALLOWLIST -> false
        }
    }

    fun isArtistAllowed(artist: ArtistsModel, policy: ParentalPolicy): Boolean =
        isArtistAllowed(artistId = artist.id, name = artist.name, policy = policy)

    fun isPlaylistAllowed(
        playlistId: String,
        name: String,
        subtitleOrOwner: String = "",
        description: String = "",
        policy: ParentalPolicy,
    ): Boolean {
        val id = ParentalPolicyStore.normalizeSpotifyId(playlistId)
        if (id == Api.HomeCache.LIKED_SONGS_ID || playlistId == "spotify:collection:tracks") return true
        if (id.isNotEmpty() && id in policy.blockedPlaylistIds) return false
        if (id.isNotEmpty() && id in policy.allowedPlaylistIds) return true
        if (matchesBlockedText(policy, name, subtitleOrOwner, description)) return false
        return when (policy.filterMode) {
            FilterMode.FILTERED_OPEN -> true
            FilterMode.STRICT_ALLOWLIST -> false
        }
    }

    fun isShowAllowed(
        showId: String,
        name: String,
        publisher: String = "",
        description: String = "",
        isAudiobook: Boolean = false,
        policy: ParentalPolicy,
    ): Boolean {
        val gateOpen = if (isAudiobook) policy.allowAudiobooks else policy.allowPodcasts
        if (!gateOpen) return false
        val id = ParentalPolicyStore.normalizeSpotifyId(showId.removePrefix(Api.SHOW_PREFIX))
        if (id.isNotEmpty() && id in policy.blockedShowIds) return false
        if (id.isNotEmpty() && id in policy.allowedShowIds) return true
        if (matchesBlockedText(policy, name, publisher, description)) return false
        return when (policy.filterMode) {
            FilterMode.FILTERED_OPEN -> true
            FilterMode.STRICT_ALLOWLIST -> false
        }
    }

    fun isShowAllowed(show: PodcastModel, policy: ParentalPolicy): Boolean =
        isShowAllowed(
            showId = show.id,
            name = show.name,
            publisher = show.publisher,
            description = show.description,
            isAudiobook = show.audiobook,
            policy = policy,
        )

    fun filterHomeItem(item: HomeItem, policy: ParentalPolicy): HomeItem? = when (item) {
        is HomeItem.Album -> item.takeIf { isAlbumAllowed(it.id, it.name, it.artists.ifBlank { it.subtitle }, policy = policy) }
        is HomeItem.Artist -> item.takeIf { isArtistAllowed(it.id, it.name, policy = policy) }
        is HomeItem.Playlist -> item.takeIf { isPlaylistAllowed(it.id, it.name, it.subtitle, policy = policy) }
        is HomeItem.Show -> item.takeIf { isShowAllowed(it.id, it.name, it.subtitle, policy = policy) }
    }

    fun filterLibraryEntry(entry: LibraryEntry, policy: ParentalPolicy): LibraryEntry? {
        if (entry.spotifyId == Api.HomeCache.LIKED_SONGS_ID) return entry
        if (entry.isFolder) {
            return entry.takeUnless { matchesBlockedText(policy, entry.name) }
        }
        if (entry.spotifyId == Api.EPISODES_ID || entry.spotifyId == Api.NEW_EPISODES_ID) {
            return entry.takeIf { policy.allowPodcasts }
        }
        if (entry.spotifyId.startsWith(Api.SHOW_PREFIX) || entry.uri.startsWith("spotify:show:")) {
            val showId = entry.spotifyId.removePrefix(Api.SHOW_PREFIX)
            return entry.takeIf { isShowAllowed(showId, entry.name, entry.subtitle, policy = policy) }
        }
        if (entry.uri.startsWith("spotify:episode:")) {
            return entry.takeIf { policy.allowPodcasts && !matchesBlockedText(policy, entry.name, entry.subtitle) }
        }
        if (entry.uri.startsWith("spotify:audiobook:") || entry.uri.startsWith("spotify:chapter:")) {
            return entry.takeIf { policy.allowAudiobooks && !matchesBlockedText(policy, entry.name, entry.subtitle) }
        }
        return if (entry.isPlaylist) {
            entry.takeIf { isPlaylistAllowed(entry.spotifyId, entry.name, entry.subtitle, policy = policy) }
        } else {
            entry.takeIf { isAlbumAllowed(entry.spotifyId, entry.name, entry.artists.ifBlank { entry.subtitle }, policy = policy) }
        }
    }
}
