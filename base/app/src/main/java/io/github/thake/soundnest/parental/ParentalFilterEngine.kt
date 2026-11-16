/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.parental

import io.github.thake.soundnest.api.Album
import io.github.thake.soundnest.api.Episode
import io.github.thake.soundnest.api.MediaKind
import io.github.thake.soundnest.api.RecentItem
import io.github.thake.soundnest.api.RecentSection
import io.github.thake.soundnest.api.TopContent
import io.github.thake.soundnest.api.Track
import io.github.thake.soundnest.api.WhatsNewItem
import io.github.thake.soundnest.api.canonicalUri
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
        val parts = canonicalUri(contextUri).split(':')
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
        // 0. Audible audiobook chapters (always fail-closed unless explicitly approved + connected)
        if (io.github.thake.soundnest.audible.AudibleUris.isAudibleUri(song.url) ||
            io.github.thake.soundnest.audible.AudibleUris.isAudibleUri(song.showId)
        ) {
            val ref = song.showId.ifBlank { song.url }
            return isAudibleBookAllowed(ref, policy)
        }

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
        val unprefixed = showId.removePrefix(Api.SHOW_PREFIX)
        if (io.github.thake.soundnest.audible.AudibleUris.isAudibleUri(showId) ||
            io.github.thake.soundnest.audible.AudibleUris.isAudibleUri(unprefixed)
        ) {
            return isAudibleBookAllowed(unprefixed, policy)
        }
        val gateOpen = if (isAudiobook) policy.allowAudiobooks else policy.allowPodcasts
        if (!gateOpen) return false
        val id = ParentalPolicyStore.normalizeSpotifyId(unprefixed)
        if (id.isNotEmpty() && id in policy.blockedShowIds) return false
        if (id.isNotEmpty() && id in policy.allowedShowIds) return true
        if (matchesBlockedText(policy, name, publisher, description)) return false
        return when (policy.filterMode) {
            FilterMode.FILTERED_OPEN -> true
            FilterMode.STRICT_ALLOWLIST -> false
        }
    }

    fun isAudibleBookAllowed(
        idOrUri: String,
        policy: ParentalPolicy,
        isConnected: Boolean? = null,
    ): Boolean {
        if (!policy.allowAudiobooks) return false
        val connected = isConnected ?: io.github.thake.soundnest.audible.AudibleCredentialStore.isConnectedOrLoaded()
        if (!connected) return false
        val asin = ParentalPolicyStore.normalizeAudibleAsin(idOrUri)
        return asin.isNotEmpty() && asin in policy.allowedAudibleAsins
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

    fun isApiTrackAllowed(
        track: Track,
        policy: ParentalPolicy,
        parentContextUri: String? = null,
    ): Boolean {
        val trackId = ParentalPolicyStore.normalizeSpotifyId(track.uri)
        val albumId = track.album?.uri?.let(ParentalPolicyStore::normalizeSpotifyId).orEmpty()
        val artistIds = track.artists.map { ParentalPolicyStore.normalizeSpotifyId(it.uri) }.filter { it.isNotEmpty() }
        val artistsText = track.artists.joinToString(", ") { it.name }
        val albumName = track.album?.name.orEmpty()

        if (trackId.isNotEmpty() && trackId in policy.blockedTrackIds) return false
        if (trackId.isNotEmpty() && trackId in policy.allowedTrackIds) return true

        if (artistIds.any { it in policy.blockedArtistIds }) return false
        if (albumId.isNotEmpty() && albumId in policy.blockedAlbumIds) return false
        if (policy.blockExplicit && track.explicit) return false
        if (matchesBlockedText(policy, track.name, albumName, artistsText)) return false

        return when (policy.filterMode) {
            FilterMode.FILTERED_OPEN -> true
            FilterMode.STRICT_ALLOWLIST ->
                (albumId.isNotEmpty() && albumId in policy.allowedAlbumIds) ||
                    artistIds.any { it in policy.allowedArtistIds } ||
                    isContextAllowlisted(parentContextUri, policy)
        }
    }

    fun isApiAlbumAllowed(album: Album, policy: ParentalPolicy): Boolean =
        isAlbumAllowed(
            albumId = album.uri,
            name = album.name,
            artists = album.artists.joinToString(", ") { it.name },
            artistIds = album.artists.map { it.uri },
            policy = policy,
        )

    fun isApiEpisodeAllowed(
        episode: Episode,
        policy: ParentalPolicy,
        parentContextUri: String? = null,
    ): Boolean {
        if (!policy.allowPodcasts) return false
        val episodeId = ParentalPolicyStore.normalizeSpotifyId(episode.uri)
        val showId = episode.show?.uri?.let(ParentalPolicyStore::normalizeSpotifyId).orEmpty()
        val showName = episode.show?.name.orEmpty()

        if (episodeId.isNotEmpty() && episodeId in policy.blockedTrackIds) return false
        if (showId.isNotEmpty() && showId in policy.blockedShowIds) return false
        if (episodeId.isNotEmpty() && episodeId in policy.allowedTrackIds) return true
        if (policy.blockExplicit && episode.explicit) return false
        if (matchesBlockedText(policy, episode.name, showName, episode.description.orEmpty())) return false

        return when (policy.filterMode) {
            FilterMode.FILTERED_OPEN -> true
            FilterMode.STRICT_ALLOWLIST ->
                (showId.isNotEmpty() && showId in policy.allowedShowIds) ||
                    isContextAllowlisted(parentContextUri, policy)
        }
    }

    fun isWhatsNewItemAllowed(item: WhatsNewItem, policy: ParentalPolicy): Boolean {
        item.album?.let { return isApiAlbumAllowed(it, policy) }
        item.episode?.let { return isApiEpisodeAllowed(it, policy) }
        return false
    }

    /**
     * Filters items from `home.whatsNew(offset, limit)` ("From artists you follow"), whose
     * entries are [Album] records carrying `spotify:album:*`, `spotify:show:*`, or `spotify:episode:*` URIs.
     */
    fun isFollowedReleaseAllowed(release: Album, policy: ParentalPolicy): Boolean {
        val creators = release.artists.joinToString(", ") { it.name }
        return when (MediaKind.of(release.uri)) {
            MediaKind.SHOW -> isShowAllowed(
                showId = release.uri,
                name = release.name,
                publisher = creators,
                isAudiobook = false,
                policy = policy,
            )
            MediaKind.EPISODE -> {
                if (!policy.allowPodcasts) return false
                val epId = ParentalPolicyStore.normalizeSpotifyId(release.uri)
                val showIds = release.artists.map { ParentalPolicyStore.normalizeSpotifyId(it.uri) }.filter { it.isNotEmpty() }
                if (epId.isNotEmpty() && epId in policy.blockedTrackIds) return false
                if (showIds.any { it in policy.blockedShowIds }) return false
                if (epId.isNotEmpty() && epId in policy.allowedTrackIds) return true
                if (matchesBlockedText(policy, release.name, creators)) return false
                when (policy.filterMode) {
                    FilterMode.FILTERED_OPEN -> true
                    FilterMode.STRICT_ALLOWLIST -> showIds.any { it in policy.allowedShowIds }
                }
            }
            MediaKind.AUDIOBOOK, MediaKind.CHAPTER -> isShowAllowed(
                showId = release.uri,
                name = release.name,
                publisher = creators,
                isAudiobook = true,
                policy = policy,
            )
            else -> isApiAlbumAllowed(release, policy)
        }
    }

    fun filterTopContent(top: TopContent, policy: ParentalPolicy): TopContent {
        val artists = top.artists.filter { isArtistAllowed(artistId = it.uri, name = it.name, policy = policy) }
        val tracks = top.tracks.filter { isApiTrackAllowed(it, policy) }
        return top.copy(
            artists = artists,
            tracks = tracks,
            artistTotal = if (artists.size == top.artists.size) top.artistTotal else artists.size,
            trackTotal = if (tracks.size == top.tracks.size) top.trackTotal else tracks.size,
        )
    }

    fun isRecentItemAllowed(
        item: RecentItem,
        policy: ParentalPolicy,
        parentContextUri: String? = null,
    ): Boolean {
        val uri = canonicalUri(item.uri)
        if (io.github.thake.soundnest.audible.AudibleUris.isAudibleUri(uri)) {
            return isAudibleBookAllowed(uri, policy)
        }
        val kind = if (item.kind != MediaKind.UNKNOWN) item.kind else MediaKind.of(uri)
        val id = ParentalPolicyStore.normalizeSpotifyId(uri)
        val subtitle = item.subtitle.orEmpty()
        return when (kind) {
            MediaKind.TRACK -> {
                if (id.isNotEmpty() && id in policy.blockedTrackIds) return false
                if (id.isNotEmpty() && id in policy.allowedTrackIds) return true
                if (policy.blockExplicit && item.explicit) return false
                if (matchesBlockedText(policy, item.title, subtitle)) return false
                when (policy.filterMode) {
                    FilterMode.FILTERED_OPEN -> true
                    FilterMode.STRICT_ALLOWLIST -> isContextAllowlisted(parentContextUri, policy)
                }
            }
            MediaKind.ALBUM -> isAlbumAllowed(
                albumId = id,
                name = item.title,
                artists = subtitle,
                explicit = item.explicit,
                policy = policy,
            )
            MediaKind.ARTIST -> isArtistAllowed(
                artistId = id,
                name = item.title,
                policy = policy,
            )
            MediaKind.PLAYLIST -> isPlaylistAllowed(
                playlistId = id,
                name = item.title,
                subtitleOrOwner = subtitle,
                policy = policy,
            )
            MediaKind.SHOW -> isShowAllowed(
                showId = id,
                name = item.title,
                publisher = subtitle,
                isAudiobook = false,
                policy = policy,
            )
            MediaKind.EPISODE -> {
                if (!policy.allowPodcasts) return false
                if (id.isNotEmpty() && id in policy.blockedTrackIds) return false
                if (id.isNotEmpty() && id in policy.allowedTrackIds) return true
                if (policy.blockExplicit && item.explicit) return false
                if (matchesBlockedText(policy, item.title, subtitle)) return false
                when (policy.filterMode) {
                    FilterMode.FILTERED_OPEN -> true
                    FilterMode.STRICT_ALLOWLIST -> isContextAllowlisted(parentContextUri, policy)
                }
            }
            MediaKind.AUDIOBOOK, MediaKind.CHAPTER -> {
                if (!policy.allowAudiobooks) return false
                if (id.isNotEmpty() && (id in policy.blockedShowIds || id in policy.blockedTrackIds)) return false
                if (id.isNotEmpty() && (id in policy.allowedShowIds || id in policy.allowedTrackIds)) return true
                if (policy.blockExplicit && item.explicit) return false
                if (matchesBlockedText(policy, item.title, subtitle)) return false
                when (policy.filterMode) {
                    FilterMode.FILTERED_OPEN -> true
                    FilterMode.STRICT_ALLOWLIST -> isContextAllowlisted(parentContextUri, policy)
                }
            }
            MediaKind.COLLECTION -> when {
                uri == "spotify:collection:your-episodes" || "episode" in uri.lowercase() -> policy.allowPodcasts
                uri == "spotify:collection:tracks" || ":collection" in uri -> true
                else -> !matchesBlockedText(policy, item.title, subtitle) && policy.filterMode == FilterMode.FILTERED_OPEN
            }
            MediaKind.USER -> false
            MediaKind.GENRE -> policy.effectiveAllowAutoplayAndRadio && !matchesBlockedText(policy, item.title, subtitle)
            MediaKind.FOLDER -> !matchesBlockedText(policy, item.title)
            MediaKind.UNKNOWN ->
                !matchesBlockedText(policy, item.title, subtitle) &&
                    (!policy.blockExplicit || !item.explicit) &&
                    policy.filterMode == FilterMode.FILTERED_OPEN
        }
    }

    fun filterRecentSections(sections: List<RecentSection>, policy: ParentalPolicy): List<RecentSection> =
        sections.mapNotNull { section ->
            val filteredGroups = section.groups.mapNotNull { group ->
                if (!group.music && !policy.allowPodcasts && !policy.allowAudiobooks) return@mapNotNull null
                if (!isRecentItemAllowed(group.context, policy)) return@mapNotNull null
                val contextUri = canonicalUri(group.context.uri)
                val allowedPlays = group.plays.filter { play ->
                    isRecentItemAllowed(play, policy, parentContextUri = contextUri)
                }
                if (group.plays.isNotEmpty() && allowedPlays.isEmpty()) return@mapNotNull null
                val dropped = group.plays.size - allowedPlays.size
                val adjustedCount = if (dropped > 0) {
                    (group.count - dropped).coerceAtLeast(allowedPlays.size.coerceAtLeast(1))
                } else {
                    group.count
                }
                group.copy(plays = allowedPlays, count = adjustedCount)
            }
            if (filteredGroups.isEmpty()) null else RecentSection(section.dayMs, filteredGroups)
        }

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
        if (io.github.thake.soundnest.audible.AudibleUris.isAudibleUri(entry.spotifyId) ||
            io.github.thake.soundnest.audible.AudibleUris.isAudibleUri(entry.uri)
        ) {
            val ref = if (io.github.thake.soundnest.audible.AudibleUris.isAudibleUri(entry.uri)) entry.uri else entry.spotifyId
            return entry.takeIf { isAudibleBookAllowed(ref, policy) }
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

