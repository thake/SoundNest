/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.parental.filter

import android.content.Context
import io.github.thake.soundnest.core.catalog.Api
import io.github.thake.soundnest.core.di.Graph
import io.github.thake.soundnest.core.model.HomeItem
import io.github.thake.soundnest.core.model.HomeSection
import io.github.thake.soundnest.core.model.withoutDj
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore
import io.github.thake.soundnest.feature.spokenword.audible.data.AudibleCatalogCache
import io.github.thake.soundnest.feature.spokenword.audible.data.AudibleUris
import io.github.thake.soundnest.feature.spokenword.audible.settings.AudibleCredentialStore

/**
 * Evaluates and filters front-page (`Home`) shelves, DJ cards, category carousels,
 * per-shelf parent hides, Minimal Home Mode, and the "Parent Picks" shelf.
 */
object HomeSectionFilter {
    const val PARENT_PICKS_URI = "soundnest:section:parent-picks"

    private val NON_ALNUM_REGEX = Regex("[^\\p{L}\\p{N}\\s]")
    private val MULTI_SPACE_REGEX = Regex("\\s+")

    fun normalizeTitle(raw: String): String =
        raw.lowercase()
            .replace(NON_ALNUM_REGEX, " ")
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()

    /**
     * Produces a stable identifier for hiding an individual shelf.
     * Prefers Spotify's `section.uri` when present, falling back to normalized shelf title.
     */
    fun shelfKey(section: HomeSection): String {
        val uri = section.uri.trim()
        return if (uri.isNotEmpty()) "uri:$uri" else "title:${normalizeTitle(section.title)}"
    }

    fun isShelfHiddenByKey(section: HomeSection, hiddenKeys: Set<String>): Boolean {
        if (hiddenKeys.isEmpty()) return false
        val uri = section.uri.trim()
        if (uri.isNotEmpty() && (uri in hiddenKeys || "uri:$uri" in hiddenKeys)) return true
        val normTitle = normalizeTitle(section.title)
        return normTitle.isNotEmpty() && (normTitle in hiddenKeys || "title:$normTitle" in hiddenKeys)
    }

    fun isNewReleasesShelf(section: HomeSection): Boolean {
        val t = normalizeTitle(section.title)
        val u = section.uri.lowercase()
        if ("new-releases" in u || "release-radar" in u || "new_releases" in u) return true
        return t.contains("new release") ||
            t.contains("release radar") ||
            t.contains("fresh new") ||
            t.contains("new music") ||
            t.contains("latest release") ||
            t.contains("just dropped") ||
            t.contains("neuerscheinung") ||
            t.contains("neue musik") ||
            t.contains("neu veröffentlich") ||
            t.contains("frisch veröffentlich") ||
            t.contains("brandneu")
    }

    fun isChartsOrTrendingShelf(section: HomeSection): Boolean {
        val t = normalizeTitle(section.title)
        val u = section.uri.lowercase()
        if ("chart" in u || "trending" in u || "viral" in u || "popular" in u) return true
        val words = t.split(' ').filter { it.isNotEmpty() }
        return "chart" in words ||
            "charts" in words ||
            "trending" in words ||
            "popular" in words ||
            "viral" in words ||
            "hits" in words ||
            "angesagt" in words ||
            "beliebt" in words ||
            "beliebte" in words ||
            "meistgehört" in words ||
            t.contains("top 50") ||
            t.contains("top songs") ||
            t.contains("top tracks") ||
            t.contains("top titel") ||
            t.contains("biggest hits") ||
            t.contains("angesagt")
    }

    fun isMixesOrMadeForYouShelf(section: HomeSection): Boolean {
        if (isNewReleasesShelf(section)) return false
        val t = normalizeTitle(section.title)
        val u = section.uri.lowercase()
        if ("made-for-you" in u || "daily-mix" in u || "discover" in u) return true
        val words = t.split(' ').filter { it.isNotEmpty() }
        return t.contains("made for") ||
            "mix" in words ||
            "mixes" in words ||
            "discover" in words ||
            "recommended" in words ||
            "tagesmix" in words ||
            t.contains("daily mix") ||
            t.contains("discover weekly") ||
            t.contains("uniquely yours") ||
            t.contains("picked just for you") ||
            t.contains("für dich") ||
            t.contains("dein mix") ||
            t.contains("tagesmix") ||
            t.contains("mix der woche") ||
            t.contains("entdecken") ||
            t.contains("empfohlen") ||
            t.contains("nur für dich") ||
            t.contains("deine top mixe")
    }

    fun isRecentsShelf(section: HomeSection): Boolean {
        val t = normalizeTitle(section.title)
        val u = section.uri.lowercase()
        if ("recently-played" in u || "jump-back-in" in u || "recents" in u) return true
        return t.contains("recently played") ||
            t.contains("jump back in") ||
            t.contains("heavy rotation") ||
            t == "recents" ||
            t == "recent" ||
            t.contains("kürzlich gehört") ||
            t.contains("zuletzt gehört") ||
            t.contains("wieder reinspringen") ||
            t.contains("dauerbrenner")
    }

    fun isWhatsNewAllowed(policy: ParentalPolicy): Boolean {
        val cfg = policy.home
        return !cfg.minimalHomeMode && cfg.showWhatsNewBell && cfg.showNewReleases
    }

    fun isItemExplicitlyApproved(item: HomeItem, policy: ParentalPolicy): Boolean {
        return when (item) {
            is HomeItem.Playlist -> {
                val id = ParentalPolicyStore.normalizeSpotifyId(item.id)
                id.isNotEmpty() && id in policy.allowedPlaylistIds && id !in policy.blockedPlaylistIds
            }
            is HomeItem.Album -> {
                val id = ParentalPolicyStore.normalizeSpotifyId(item.id)
                id.isNotEmpty() && id in policy.allowedAlbumIds && id !in policy.blockedAlbumIds
            }
            is HomeItem.Artist -> {
                val id = ParentalPolicyStore.normalizeSpotifyId(item.id)
                id.isNotEmpty() && id in policy.allowedArtistIds && id !in policy.blockedArtistIds
            }
            is HomeItem.Show -> {
                if (AudibleUris.isAudibleUri(item.id)) {
                    val asin = AudibleUris.asinFromAny(item.id).orEmpty()
                    val audible = policy.spokenWord.audible
                    audible.showPinnedOnHome &&
                        asin.isNotEmpty() &&
                        asin in audible.pinnedAsins &&
                        ParentalFilterEngine.isAudibleBookAllowed(asin, policy)
                } else {
                    val showsAllowed = policy.spokenWord.allowPodcasts || policy.spokenWord.allowAudiobooks
                    val pods = policy.spokenWord.podcasts
                    val id = ParentalPolicyStore.normalizeSpotifyId(item.id)
                    showsAllowed && id.isNotEmpty() && id in pods.allowedShowIds && id !in pods.blockedShowIds
                }
            }
        }
    }

    fun buildParentPicksSection(
        candidateItems: List<HomeItem>,
        policy: ParentalPolicy,
        title: String = "Parent Picks",
    ): HomeSection? {
        if (!policy.home.showParentPicks) return null
        val approved = candidateItems
            .filter { isItemExplicitlyApproved(it, policy) }
            .distinctBy { itemKey(it) }
        if (approved.isEmpty()) return null
        return HomeSection(
            title = title,
            items = approved,
            uri = PARENT_PICKS_URI,
        )
    }

    /**
     * Filters shortcut-grid items (removing DJ if disabled, removing podcasts/audiobooks if disabled,
     * and restricting to explicitly approved items in `minimalHomeMode`).
     */
    fun filterShortcutItems(items: List<HomeItem>?, policy: ParentalPolicy): List<HomeItem>? {
        if (!policy.home.showShortcutsGrid) return null
        val base = items ?: return null
        val withoutShows = if (policy.spokenWord.allowPodcasts || policy.spokenWord.allowAnyAudiobooks) {
            base
        } else {
            base.filterNot { it is HomeItem.Show }
        }
        val withoutDjList = if (policy.home.showDjCard) {
            withoutShows
        } else {
            HomeSection("grid", withoutShows).let { listOf(it).withoutDj().firstOrNull()?.items.orEmpty() }
        }
        return if (policy.home.minimalHomeMode) {
            withoutDjList.filter { isItemExplicitlyApproved(it, policy) }
        } else {
            withoutDjList
        }
    }

    fun filterSections(
        sections: List<HomeSection>,
        policy: ParentalPolicy,
        parentPicksCandidates: List<HomeItem> = emptyList(),
        parentPicksTitle: String = "Parent Picks",
    ): List<HomeSection> {
        val cfg = policy.home
        val allCandidates = buildList {
            sections.forEach { addAll(it.items) }
            addAll(parentPicksCandidates)
        }
        val parentPicks = buildParentPicksSection(allCandidates, policy, parentPicksTitle)

        if (cfg.minimalHomeMode) {
            return listOfNotNull(parentPicks)
        }

        val showsAllowed = policy.spokenWord.allowPodcasts || policy.spokenWord.allowAnyAudiobooks
        val step1 = if (showsAllowed) {
            sections
        } else {
            sections.map { s -> s.copy(items = s.items.filterNot { it is HomeItem.Show }) }
        }

        val step2 = if (cfg.showDjCard) step1 else step1.withoutDj()

        val filteredShelves = step2.filter { section ->
            section.items.isNotEmpty() &&
                !isShelfHiddenByKey(section, cfg.hiddenShelfKeys) &&
                (cfg.showChartsAndTrending || !isChartsOrTrendingShelf(section)) &&
                (cfg.showMixesAndMadeForYou || !isMixesOrMadeForYouShelf(section)) &&
                (cfg.showNewReleases || !isNewReleasesShelf(section)) &&
                (cfg.showRecents || !isRecentsShelf(section))
        }

        return if (parentPicks != null && !isShelfHiddenByKey(parentPicks, cfg.hiddenShelfKeys)) {
            listOf(parentPicks) + filteredShelves
        } else {
            filteredShelves
        }
    }

    fun cachedLibraryCandidates(
        context: Context? = runCatching { Graph.app }.getOrNull(),
    ): List<HomeItem> = buildList {
        val lib = Api.HomeCache.library.orEmpty()
        for (entry in lib) {
            if (entry.isFolder || entry.spotifyId.isBlank()) continue
            if (entry.isPlaylist) {
                add(HomeItem.Playlist(name = entry.name, imageUrl = entry.coverUri, subtitle = entry.subtitle, id = entry.spotifyId))
            } else if (AudibleUris.isAudibleUri(entry.spotifyId)) {
                add(HomeItem.Show(id = entry.spotifyId, name = entry.name, imageUrl = entry.coverUri, subtitle = entry.subtitle, isEpisode = false))
            } else if (entry.spotifyId.startsWith(Api.HomeCache.SHOW_PREFIX)) {
                val showId = entry.spotifyId.removePrefix(Api.HomeCache.SHOW_PREFIX)
                add(HomeItem.Show(id = showId, name = entry.name, imageUrl = entry.coverUri, subtitle = entry.subtitle, isEpisode = false))
            } else {
                add(HomeItem.Album(id = entry.spotifyId, name = entry.name, imageUrl = entry.coverUri, subtitle = entry.subtitle, artists = entry.artists))
            }
        }
        if (context != null && AudibleCredentialStore.isConnected(context)) {
            AudibleCatalogCache.books(context).forEach { book ->
                add(
                    HomeItem.Show(
                        id = AudibleUris.showId(book.asin),
                        name = book.title,
                        imageUrl = book.coverUrl,
                        subtitle = book.authors.joinToString(", ").ifBlank { "Audible" },
                        isEpisode = false,
                    ),
                )
            }
        }
        Api.HomeCache.albums.orEmpty().forEach { album ->
            if (album.spotifyId.isNotBlank()) {
                add(HomeItem.Album(id = album.spotifyId, name = album.name, imageUrl = album.coverUri, subtitle = album.artists, artists = album.artists))
            }
        }
        Api.HomeCache.artists.orEmpty().forEach { artist ->
            if (artist.id.isNotBlank()) {
                add(HomeItem.Artist(name = artist.name, imageUrl = artist.coverUri, id = artist.id))
            }
        }
    }

    private fun itemKey(item: HomeItem): String = when (item) {
        is HomeItem.Playlist -> "playlist:${ParentalPolicyStore.normalizeSpotifyId(item.id).ifEmpty { item.name.lowercase() }}"
        is HomeItem.Album -> "album:${ParentalPolicyStore.normalizeSpotifyId(item.id).ifEmpty { item.name.lowercase() }}"
        is HomeItem.Artist -> "artist:${ParentalPolicyStore.normalizeSpotifyId(item.id).ifEmpty { item.name.lowercase() }}"
        is HomeItem.Show -> if (AudibleUris.isAudibleUri(item.id)) {
            "audible:${AudibleUris.asinFromAny(item.id).orEmpty().ifEmpty { item.name.lowercase() }}"
        } else {
            "show:${ParentalPolicyStore.normalizeSpotifyId(item.id).ifEmpty { item.name.lowercase() }}"
        }
    }
}
