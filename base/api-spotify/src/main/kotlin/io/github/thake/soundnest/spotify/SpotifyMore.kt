package io.github.thake.soundnest.spotify

import io.github.thake.soundnest.api.Account
import io.github.thake.soundnest.api.Audiobook
import io.github.thake.soundnest.api.BrowsePage
import io.github.thake.soundnest.api.Canvas
import io.github.thake.soundnest.api.Category
import io.github.thake.soundnest.api.CreditRole
import io.github.thake.soundnest.api.Credits
import io.github.thake.soundnest.api.Device
import io.github.thake.soundnest.api.Devices
import io.github.thake.soundnest.api.Episode
import io.github.thake.soundnest.api.FriendActivity
import io.github.thake.soundnest.api.HistoryEntry
import io.github.thake.soundnest.api.Home
import io.github.thake.soundnest.api.HomeFeed
import io.github.thake.soundnest.api.HomeFilter
import io.github.thake.soundnest.api.Image
import io.github.thake.soundnest.api.Jam
import io.github.thake.soundnest.api.JamAvailability
import io.github.thake.soundnest.api.JamMember
import io.github.thake.soundnest.api.JamSession
import io.github.thake.soundnest.api.Lyrics
import io.github.thake.soundnest.api.LyricsLine
import io.github.thake.soundnest.api.LyricsWord
import io.github.thake.soundnest.api.MediaKind
import io.github.thake.soundnest.api.Page
import io.github.thake.soundnest.api.Podcasts
import io.github.thake.soundnest.api.Shelf
import io.github.thake.soundnest.api.Show
import io.github.thake.soundnest.api.TimeRange
import io.github.thake.soundnest.api.TopContent
import io.github.thake.soundnest.api.TrackExtras
import io.github.thake.soundnest.api.UserProfile
import io.github.thake.soundnest.api.UserRef
import io.github.thake.soundnest.api.Users
import io.github.thake.soundnest.api.WhatsNewContent
import io.github.thake.soundnest.api.WhatsNewItem
import io.github.thake.soundnest.api.Uri
import io.github.thake.soundnest.api.ArtistRef
import io.github.thake.soundnest.spotify.bridge.Bridge
import io.github.thake.soundnest.spotify.bridge.service
import io.github.thake.soundnest.spotify.gql.Gql
import io.github.thake.soundnest.spotify.gql.GqlException
import io.github.thake.soundnest.spotify.gql.SpotifyHttp
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.put
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.net.URLEncoder
import java.util.TimeZone

private fun idOf(uri: Uri) = uri.substringAfterLast(':')

/** Promotions and ads (HomePromotionShelf, sponsored cards…): never shown as content. */
internal fun isPromotion(typename: String?): Boolean =
    typename != null && Regex("Promotion|Sponsor|Advert|(^|[a-z])Ad([A-Z]|$)").containsMatchIn(typename)

private fun shelf(section: JsonElement): Shelf? {
    if (isPromotion(section.at("data").typename()) || isPromotion(section.typename())) return null
    val items = section.list("sectionItems.items")
        .filterNot { isPromotion(it.at("content").typename()) || isPromotion(it.at("content.data").typename()) }
        .mapNotNull { card(it.at("content")) }
    if (items.isEmpty()) return null
    return Shelf(
        uri = section.str("uri"),
        title = section.str("data.title.transformedLabel") ?: section.str("data.title.text"),
        items = items,
        total = section.int("sectionItems.totalCount") ?: items.size,
    )
}

class SpotifyHome(private val gql: Gql) : Home {
    override suspend fun feed(filter: HomeFilter, itemsPerShelf: Int): HomeFeed = feed(filter.facet, itemsPerShelf)

    override suspend fun feed(facet: String, itemsPerShelf: Int): HomeFeed {
        val home = gql.query("home", vars(
            "timeZone" to TimeZone.getDefault().id, "sp_t" to "", "facet" to facet,
            "sectionItemsLimit" to itemsPerShelf, "homeEndUserIntegration" to "INTEGRATION_WEB_PLAYER",
            "includeEpisodeContentRatingsV2" to true, "includeHomeChapterVideoCards" to false,
        )).entity("data.home", "home")
        val filters = home.list("homeChips").mapNotNull { chip -> HomeFilter.entries.firstOrNull { it.facet == chip.str("id") } }
        return HomeFeed(
            greeting = home.str("greeting.transformedLabel") ?: "",
            filters = listOf(HomeFilter.ALL) + filters,
            shelves = home.list("sectionContainer.sections.items").mapNotNull(::shelf),
            chips = home.list("homeChips").mapNotNull(::homeChip),
        )
    }

    /** {id, label.transformedLabel, subChips[]}, as the Web Player reads it (a chip with no label is not shown there). */
    private fun homeChip(c: JsonElement): io.github.thake.soundnest.api.HomeChip? {
        val id = c.str("id")?.takeIf { it.isNotBlank() } ?: return null
        val label = c.str("label.transformedLabel")?.takeIf { it.isNotBlank() } ?: return null
        return io.github.thake.soundnest.api.HomeChip(id, label, c.list("subChips").mapNotNull(::homeChip))
    }

    override suspend fun categories(): List<Category> =
        gql.query("browseAll", vars(
            "pagePagination" to mapOf("offset" to 0, "limit" to 10),
            "sectionPagination" to mapOf("offset" to 0, "limit" to 99),
            "browseEndUserIntegration" to "INTEGRATION_WEB_PLAYER",
        )).list("data.browseStart.sections.items").flatMap { it.list("sectionItems.items") }.mapNotNull { item ->
            val c = item.obj("content.data.data.cardRepresentation") ?: item.obj("content.data.cardRepresentation") ?: return@mapNotNull null
            val uri = item.str("uri") ?: return@mapNotNull null
            Category(uri, c.str("title.transformedLabel") ?: "", images(c.list("artwork.sources")), c.str("backgroundColor.hex"))
        }

    override suspend fun whatsNew(offset: Int, limit: Int): Page<io.github.thake.soundnest.api.Album> {
        val feed = gql.query("queryWhatsNewFeed", vars("offset" to offset, "limit" to limit, "onlyUnPlayedItems" to false))
            .at("data.whatsNewFeedItems")
        return page(feed, offset) { item ->
            val d = item.at("content.data")
            album(d)?.let { a -> if (a.type == "ALBUM" && d.str("albumType") != null) a.copy(type = d.str("albumType")!!) else a }
        }
    }

    // The Web Player's own page: 50 entries, the pill as `includedContentTypes` (none: both).
    override suspend fun whatsNewFeed(offset: Int, limit: Int, content: WhatsNewContent): Page<WhatsNewItem> {
        val feed = gql.query("queryWhatsNewFeed", vars(
            "offset" to offset, "limit" to limit, "onlyUnPlayedItems" to false,
            "includedContentTypes" to when (content) {
                WhatsNewContent.ALL -> emptyList()
                WhatsNewContent.MUSIC -> listOf("ALBUM")
                WhatsNewContent.PODCASTS -> listOf("EPISODE")
            },
            "includeEpisodeContentRatingsV2" to true,
        )).at("data.whatsNewFeedItems")
        return page(feed, offset, ::whatsNewItem)
    }

    // `$items` is an input object holding the list (a bare list is refused with 400), as the Web
    // Player sends it: {items: {items: [{id, state}]}}.
    override suspend fun markWhatsNewSeen(ids: List<String>) {
        if (ids.isEmpty()) return
        gql.query("SetItemsStateInWhatsNewFeed", vars("items" to mapOf("items" to ids.map { mapOf("id" to it, "state" to "SEEN") })))
    }

    override suspend fun category(uri: Uri): BrowsePage {
        val page = gql.query("browsePage", vars(
            "uri" to uri, "pagePagination" to mapOf("offset" to 0, "limit" to 10),
            "sectionPagination" to mapOf("offset" to 0, "limit" to 20),
            "browseEndUserIntegration" to "INTEGRATION_WEB_PLAYER", "includeEpisodeContentRatingsV2" to true,
        )).entity("data.browse", "browsePage")
        return BrowsePage(uri, page.str("header.title.transformedLabel") ?: "", page.list("sections.items").mapNotNull(::shelf))
    }
}

class SpotifyPodcasts(private val gql: Gql, private val bridge: Bridge, private val http: SpotifyHttp) : Podcasts {
    private suspend fun showUnion(uri: Uri) = gql.query("queryShowMetadataV2", vars(
        "uri" to uri, "includeContentCapabilityTrait" to true, "includeEpisodeContentRatingsV2" to true,
    )).entity("data.podcastUnionV2", "queryShowMetadataV2")

    override suspend fun show(uri: Uri): Show = show(showUnion(uri)) ?: throw GqlException("queryShowMetadataV2", "bad show")

    override suspend fun episodes(uri: Uri, offset: Int, limit: Int): Page<Episode> {
        val e = gql.query("queryPodcastEpisodes", vars("uri" to uri, "offset" to offset, "limit" to limit, "includeEpisodeContentRatingsV2" to true))
            .entity("data.podcastUnionV2", "queryPodcastEpisodes").at("episodesV2")
        return page(e, offset) { episode(it.at("entity.data")) }
    }

    override suspend fun episode(uri: Uri): Episode =
        episode(gql.query("getEpisodeOrChapter", vars("uri" to uri, "includeEpisodeContentRatingsV2" to true))
            .entity("data.episodeUnionV2", "getEpisodeOrChapter")) ?: throw GqlException("getEpisodeOrChapter", "bad episode")

    override suspend fun audiobook(uri: Uri): Audiobook {
        val d = showUnion(uri)
        return Audiobook(
            uri = d.str("uri") ?: uri,
            name = d.str("name") ?: "",
            authors = d.list("authorsV2").mapNotNull { it.str("name") }.ifEmpty { d.list("authors").mapNotNull { it.str("name") } },
            narrators = d.list("narrators.items").mapNotNull { it.str("name") },
            description = d.str("description"),
            images = images(d.list("coverArt.sources")),
            chapterCount = d.int("chaptersV2.totalCount") ?: d.int("episodesV2.totalCount") ?: 0,
        )
    }

    override suspend fun chapters(uri: Uri, offset: Int, limit: Int): Page<Episode> {
        val d = gql.query("queryBookChapters", vars("uri" to uri, "offset" to offset, "limit" to limit))
            .entity("data.podcastUnionV2", "queryBookChapters")
        val list = d.at("chaptersV2") ?: d.at("episodesV2")
        return page(list, offset) { episode(it.at("entity.data") ?: it.at("data")) }
    }

    /** "Your Episodes" is the `listenlater` collection set; details are loaded in parallel. */
    override suspend fun savedEpisodes(offset: Int, limit: Int): Page<Episode> {
        // The page's own get() throws on an empty set: that means no saved episodes.
        val raw = runCatching { bridge.service("CollectionPlatformAPI", "get", JsonPrimitive("listenlater")) }.getOrNull()
        val saved = (raw as? JsonArray).orEmpty()
            .sortedByDescending { it.long("addTime") ?: 0 }
            .mapNotNull { it.str("uri") }
        val slice = saved.drop(offset).take(limit)
        val items = kotlinx.coroutines.coroutineScope {
            slice.map { uri -> async { runCatching { episode(uri) }.getOrNull() } }.map { it.await() }
        }.filterNotNull()
        return Page(items, offset, saved.size, (offset + slice.size).takeIf { it < saved.size })
    }

    /** Saves or unsaves an episode in the `listenlater` set that [savedEpisodes] reads. */
    override suspend fun setEpisodeSaved(uri: Uri, saved: Boolean) {
        bridge.service("CollectionPlatformAPI", if (saved) "add" else "remove", JsonPrimitive("listenlater"), json(listOf(uri)))
    }
}

class SpotifyUsers(private val gql: Gql, private val bridge: Bridge, private val http: SpotifyHttp) : Users {
    internal companion object {
        val BUDDYLIST_HOSTS = listOf("guc-spclient.spotify.com", "spclient.wg.spotify.com")
        const val LISTENING_FEED_URL = "https://spclient.wg.spotify.com/listening-activity/v1/feed"

        /** `/{me}/is-blocking/{user}` on the social graph; both ids are the usernames, never the uris. */
        fun blockUrl(username: String, userUri: Uri): String {
            require(username.isNotEmpty()) { "no username" }
            val me = URLEncoder.encode(username, "UTF-8").replace("+", "%20")
            val target = URLEncoder.encode(idOf(userUri), "UTF-8").replace("+", "%20")
            return "https://spclient.wg.spotify.com/socialgraph/v4/$me/is-blocking/$target"
        }

        /** The protobuf `containsResult` (field 1, varint) of the GET, read from the body text: `08 01` is true, an empty body false. */
        fun blockedAnswer(body: String): Boolean = body.length >= 2 && body[0] == '\u0008' && body[1] == '\u0001'
    }

    override suspend fun me(): Account {
        val p = gql.query("profileAttributes", vars()).at("data.me.profile")
        val a = gql.query("accountAttributes", vars()).at("data.me.account")
        return Account(
            uri = p.str("uri") ?: "",
            username = p.str("username") ?: "",
            name = p.str("name") ?: p.str("username") ?: "",
            images = images(p.list("avatar.sources")),
            premium = a.str("product")?.equals("FREE", ignoreCase = true) == false,
            country = a.str("country"),
        )
    }

    override suspend fun profile(userUri: Uri): UserProfile {
        val id = URLEncoder.encode(idOf(userUri), "UTF-8")
        val p = http.getJson("https://spclient.wg.spotify.com/user-profile-view/v3/profile/$id?playlist_limit=50&artist_limit=10&episode_limit=10&market=from_token")
            ?: throw GqlException("user-profile-view", "not found")
        return UserProfile(
            uri = p.str("uri") ?: userUri,
            name = p.str("name") ?: idOf(userUri),
            images = listOfNotNull(p.str("image_url")?.let { Image(it) }),
            followers = p.int("followers_count"),
            following = p.int("following_count"),
            publicPlaylists = p.list("public_playlists").mapNotNull { pl ->
                val uri = pl.str("uri") ?: return@mapNotNull null
                io.github.thake.soundnest.api.Playlist(uri, pl.str("name") ?: "", null, pl.str("owner_name"),
                    listOfNotNull(pl.str("image_url")?.let { Image(it) }), 0)
            },
        )
    }

    override suspend fun followers(userUri: Uri): List<UserRef> = profileList(userUri, "followers")
    override suspend fun following(userUri: Uri): List<UserRef> = profileList(userUri, "following")

    private suspend fun profileList(userUri: Uri, kind: String): List<UserRef> {
        val id = URLEncoder.encode(idOf(userUri), "UTF-8")
        val r = http.getJson("https://spclient.wg.spotify.com/user-profile-view/v3/profile/$id/$kind?market=from_token") ?: return emptyList()
        return r.list("profiles").mapNotNull { f ->
            val uri = f.str("uri") ?: return@mapNotNull null
            UserRef(uri, f.str("name") ?: idOf(uri), f.str("image_url"))
        }.distinctBy { it.uri }
    }

    override suspend fun top(range: TimeRange, limit: Int): TopContent {
        val input = mapOf("offset" to 0, "limit" to limit, "sortBy" to "AFFINITY", "timeRange" to range.gqlName)
        return topContent(gql.query("userTopContent", vars(
            "includeTopArtists" to true, "topArtistsInput" to input, "includeTopTracks" to true, "topTracksInput" to input,
        )).at("data.me.profile"))
    }

    override suspend fun follow(userUris: List<Uri>) { gql.query("followUsers", vars("usernames" to userUris.map(::idOf))) }
    override suspend fun unfollow(userUris: List<Uri>) { gql.query("unfollowUsers", vars("usernames" to userUris.map(::idOf))) }

    override suspend fun isFollowing(userUris: List<Uri>): List<Boolean> {
        val r = gql.query("isFollowingUsers", vars("uris" to userUris))
        val list = r.list("data.users")
        return userUris.map { u -> list.firstOrNull { it.str("uri") == u }?.bool("following") ?: false }
    }

    // The Web Player's "Block" in a profile's menu is a plain spclient call on the social graph, with
    // the account's own username in the path; its answer to the GET is a protobuf `{ bool contains = 1 }`.
    override suspend fun isBlocked(userUri: Uri): Boolean {
        val (code, text) = http.send("GET", blockUrl(ownUsername(), userUri), accept = "application/x-protobuf")
        return when (code) {
            200 -> blockedAnswer(text)
            204, 404 -> false
            else -> throw io.github.thake.soundnest.spotify.gql.HttpException(code, text.take(200))
        }
    }

    @Volatile private var username: String? = null
    private suspend fun ownUsername(): String = username ?: me().username.also { username = it }

    /** Another page load may be another account: the next [ownUsername] asks again. */
    internal fun forgetUsername() { username = null }

    override suspend fun blockUser(userUri: Uri) = writeBlock("POST", userUri)
    override suspend fun unblockUser(userUri: Uri) = writeBlock("DELETE", userUri)

    private suspend fun writeBlock(method: String, userUri: Uri) {
        val (code, text) = http.send(method, blockUrl(ownUsername(), userUri))
        if (code !in 200..299) throw io.github.thake.soundnest.spotify.gql.HttpException(code, text.take(200))
    }

    /**
     * The desktop client's buddylist first (below). Only when it is empty or unanswered, the mobile
     * client's feed, which the page's token reads too: the buddylist came back empty for accounts
     * whose friends show in the phone app. A buddylist that answers costs no more than before.
     */
    override suspend fun friendActivity(): List<FriendActivity> {
        var failure: Exception? = null
        try {
            buddyListActivity().let { if (it.isNotEmpty()) return it }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            failure = e
        }
        val feed = try {
            listeningFeedActivity()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            failure?.addSuppressed(e)
            null
        }
        // An empty buddylist stays an empty list when the feed does not answer.
        return feed ?: failure?.let { throw it } ?: emptyList()
    }

    /**
     * The mobile client's feed (`listening-activity/v1/feed`, protobuf), or null when it does not
     * answer 200. It sends uris only, so names, covers and avatars are looked up in two calls.
     */
    private suspend fun listeningFeedActivity(): List<FriendActivity>? {
        val (code, body) = http.sendProto("POST", LISTENING_FEED_URL, proto { varint(1, 1) })
        if (code != 200) return null
        val feed = listeningFeed(body)
        if (feed.isEmpty()) return emptyList()
        val (entities, avatars) = kotlinx.coroutines.coroutineScope {
            val names = async { lookupEntities(gql, feed.flatMap { listOfNotNull(it.userUri, it.itemUri, it.contextUri) }) }
            // Avatars are a nicety: the rows show without them.
            val pictures = async {
                try {
                    lookupAvatars(gql, feed.map { it.userUri })
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    emptyMap()
                }
            }
            names.await() to pictures.await()
        }
        return feedActivity(feed, entities, avatars)
    }

    /**
     * The Web Player's own friend feed is a stub (its BuddyFeedAPI says `isSupported: false`), so
     * this reads the endpoint the desktop client's feed uses, with the page's token: guc-spclient
     * first (the desktop's host), then the generic spclient host.
     */
    private suspend fun buddyListActivity(): List<FriendActivity> {
        var last: Exception? = null
        for (host in BUDDYLIST_HOSTS) {
            try {
                return buddyList(http.getJson("https://$host/presence-view/v1/buddylist"))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IllegalStateException("friend activity unavailable")
    }

    override suspend fun history(): List<HistoryEntry> =
        (bridge.service("RecentlyPlayedAPI", "getContexts") as? JsonArray).orEmpty().mapNotNull { c ->
            val uri = c.str("uri") ?: return@mapNotNull null
            HistoryEntry(uri, MediaKind.of(uri), c.str("name") ?: "",
                c.list("images").mapNotNull { i -> i.str("url")?.let { Image(it, i.int("width"), i.int("height")) } },
                c.long("timestampMs"))
        }

    override suspend fun setPrivateSession(on: Boolean) {
        val supported = bridge.service("PrivateSessionAPI", "getCapabilities").bool("isSupported") ?: false
        if (!supported) throw UnsupportedOperationException("private session is not available in the Web Player")
        bridge.service("PrivateSessionAPI", "setPrivateSession", JsonPrimitive(on))
    }
}

class SpotifyTrackExtras(private val gql: Gql, private val bridge: Bridge, private val http: SpotifyHttp) : TrackExtras {
    override suspend fun lyrics(trackUri: Uri): Lyrics? {
        val r = http.getJson("https://spclient.wg.spotify.com/color-lyrics/v2/track/${idOf(trackUri)}?format=json&vocalRemoval=false&market=from_token")
            ?: return null
        val l = r.obj("lyrics") ?: return null
        val synced = l.str("syncType") != "UNSYNCED"
        val lines = l.list("lines").map { line ->
            val start = line.long("startTimeMs") ?: 0
            LyricsLine(
                startMs = start,
                endMs = line.long("endTimeMs")?.takeIf { it > 0 },
                text = line.str("words") ?: "",
                words = line.list("syllables").mapNotNull { s -> s.str("words")?.let { LyricsWord(s.long("startTimeMs") ?: start, it) } }.ifEmpty { null },
            )
        }.filter { it.text.isNotBlank() || synced }
        return Lyrics(lines, synced, l.str("language"), l.str("providerDisplayName") ?: l.str("provider"),
            r.int("colors.background"), r.int("colors.text"), r.int("colors.highlightText"))
    }

    override suspend fun canvas(trackUri: Uri): Canvas? {
        val c = gql.query("canvas", vars("trackUri" to trackUri)).obj("data.trackUnion.canvas") ?: return null
        val url = c.str("url") ?: return null
        return Canvas(c.str("uri") ?: "", url, c.str("type") ?: "")
    }

    override suspend fun credits(trackUri: Uri): Credits {
        val contributors = gql.query("queryTrackCreditsGroupedModal", vars("trackUri" to trackUri))
            .list("data.trackUnion.creditsTrait.contributors.items")
        val roles = contributors.groupBy { it.str("role") ?: it.str("roleGroup.name") ?: "" }.map { (role, people) ->
            CreditRole(role, people.map { ArtistRef(it.str("uri") ?: "", it.str("name") ?: "") })
        }
        return Credits(roles)
    }

    override suspend fun radio(seedUri: Uri): Uri? =
        http.getJson("https://spclient.wg.spotify.com/inspiredby-mix/v2/seed_to_playlist/$seedUri?response-format=json")
            ?.list("mediaItems")?.firstOrNull()?.str("uri")

    override suspend fun setExcludedFromTaste(trackUri: Uri, excluded: Boolean) {
        bridge.service("CollectionPlatformAPI", if (excluded) "add" else "remove", JsonPrimitive("ignoreinrecs"), json(listOf(trackUri)))
    }

    override suspend fun isExcludedFromTaste(trackUris: List<Uri>): List<Boolean> = collectionContains("ignoreinrecs", trackUris)

    /** "Don't play this artist" is the `artistban` collection set, written like the taste exclusion. */
    override suspend fun setArtistBanned(artistUri: Uri, banned: Boolean) {
        bridge.service("CollectionPlatformAPI", if (banned) "add" else "remove", JsonPrimitive("artistban"), json(listOf(artistUri)))
    }

    override suspend fun isArtistBanned(artistUris: List<Uri>): List<Boolean> = collectionContains("artistban", artistUris)

    /** Asks the server: the page's own contains() answers from a cache that removals do not clear. */
    private suspend fun collectionContains(set: String, uris: List<Uri>): List<Boolean> {
        if (uris.isEmpty()) return emptyList()
        val username = bridge.service("UserAPI", "getUser").str("username") ?: throw GqlException("collection", "no user")
        val body = json(mapOf("username" to username, "set" to set, "items" to uris.map { mapOf("uri" to it) })).toString()
        val (code, text) = http.send("POST", "https://spclient.wg.spotify.com/collection/v2/contains?market=from_token", body, contentType = "application/json")
        if (code != 200) throw io.github.thake.soundnest.spotify.gql.HttpException(code, text.take(200))
        return Bridge.json.parseToJsonElement(text).list("found").map { (it as? JsonPrimitive)?.content == "true" }
    }
}

internal object ConnectDeviceLabels {
    suspend fun fetchDevicesJson(bridge: Bridge): JsonArray =
        (runCatching { bridge.call("connect.devices") }.getOrNull() as? JsonArray)
            ?: (bridge.service("ConnectDevicesAPI", "getDevices") as? JsonArray)
            ?: JsonArray(emptyList())

    fun resolveName(d: JsonElement, type: String): String {
        val obj = d as? kotlinx.serialization.json.JsonObject ?: return io.github.thake.soundnest.engine.Cluster.humanDeviceType(type)
        for (key in listOf("name", "displayName", "display_name")) {
            obj.str(key)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        extractAliases(obj).firstOrNull()?.let { return it }
        val brand = (obj.str("brandDisplayName") ?: obj.str("brand_display_name") ?: obj.str("brand"))?.trim().orEmpty()
        val model = (obj.str("modelDisplayName") ?: obj.str("model_display_name") ?: obj.str("model"))?.trim().orEmpty()
        io.github.thake.soundnest.engine.Cluster.combineBrandModel(brand, model)?.let { return it }
        return io.github.thake.soundnest.engine.Cluster.humanDeviceType(type)
    }

    fun extractAliases(obj: kotlinx.serialization.json.JsonObject): List<String> {
        val raw = obj["aliases"] ?: obj["deviceAliases"] ?: obj["device_aliases"] ?: return emptyList()
        val items: Iterable<JsonElement> = when (raw) {
            is JsonArray -> raw
            is kotlinx.serialization.json.JsonObject -> raw.values
            else -> emptyList()
        }
        return items.mapNotNull { entry ->
            when (entry) {
                is JsonPrimitive -> entry.content.takeIf { entry.isString }?.trim()?.takeIf { it.isNotEmpty() }
                is kotlinx.serialization.json.JsonObject -> (entry.str("name") ?: entry.str("displayName") ?: entry.str("display_name"))
                    ?.trim()?.takeIf { it.isNotEmpty() }
                else -> null
            }
        }.distinct()
    }
}

class SpotifyDevices(
    private val bridge: Bridge,
    private val http: SpotifyHttp? = null,
    private val sender: (suspend (method: String, url: String, body: String?) -> Pair<Int, String>)? = null,
) : Devices {
    /** A device id that is this app too (the native engine's, see [AccountAudio.deviceId]); null: none. */
    @Volatile var ownDeviceId: () -> String? = { null }
    /** All device ids owned by this app (active native engine + any pending-cleanup orphan ids). */
    @Volatile var ownDeviceIds: () -> Set<String> = { ownDeviceId()?.let(::setOf).orEmpty() }
    @Volatile var audio: AccountAudio? = null
    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    override val devices: StateFlow<List<Device>> = _devices

    override suspend fun refresh() {
        val ownIds = buildSet {
            ownDeviceId()?.takeIf { it.isNotBlank() }?.let(::add)
            ownDeviceIds().filter { it.isNotBlank() }.forEach(::add)
        }
        val all = ConnectDeviceLabels.fetchDevicesJson(bridge).mapNotNull { d ->
            val id = (d.str("id") ?: d.str("connectStateId") ?: d.str("deviceId"))?.trim()?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            val type = (d.str("type") ?: d.str("deviceType"))?.trim()?.takeIf { it.isNotEmpty() } ?: "unknown"
            val explicitAlias = (d.str("aliasId") ?: d.str("targetAliasId"))?.trim()?.takeIf { it.isNotEmpty() }
            val parsed = io.github.thake.soundnest.engine.ConnectDeviceId.parse(id, explicitAlias)
            Device(
                id = id,
                name = ConnectDeviceLabels.resolveName(d, type),
                type = type,
                active = d.bool("isActive") ?: false,
                volume = d.double("volume"),
                local = (d.bool("isLocal") ?: false) || id in ownIds,
                aliasId = parsed.aliasId,
            )
        }
        val anyLocalActive = all.any { it.local && it.active }
        var localSeen = false
        _devices.value = all.mapNotNull { dev ->
            if (dev.local) {
                if (localSeen) null else {
                    localSeen = true
                    if (anyLocalActive && !dev.active) dev.copy(active = true) else dev
                }
            } else dev
        }
    }

    override suspend fun transfer(deviceId: String, play: Boolean) {
        transfer(deviceId, play, null)
    }

    override suspend fun transfer(deviceId: String, play: Boolean, aliasId: String?) {
        val targetAlias = aliasId ?: _devices.value.firstOrNull {
            io.github.thake.soundnest.engine.ConnectDeviceId.sameDevice(it.id, deviceId, it.aliasId)
        }?.aliasId
        val a = audio
        val handled = if (a != null && a.holdsPlayback) {
            runCatching { a.transferTo(deviceId, play, targetAlias) }.getOrDefault(false)
        } else false

        if (!handled) {
            val harmonyTarget = io.github.thake.soundnest.engine.ConnectDeviceId.harmonyTargetId(deviceId, targetAlias)
            val args = buildMap<String, Any> {
                put("deviceId", deviceId)
                put("play", play)
                if (targetAlias != null) put("aliasId", targetAlias)
            }
            val result = try {
                bridge.call("connect.transfer", json(args) as kotlinx.serialization.json.JsonObject)
            } catch (e: io.github.thake.soundnest.spotify.bridge.BridgeException) {
                if (e.message?.contains("unknown command", ignoreCase = true) == true) {
                    bridge.service("ConnectTransferAPI", "transferPlayback", JsonPrimitive(harmonyTarget), json(emptyMap<String, Any>()))
                } else {
                    throw e
                }
            }
            if (result.bool("ok") == false) {
                throw IllegalStateException("Spotify Connect transfer rejected for $deviceId")
            }
            if (a != null && a.holdsPlayback) {
                runCatching { a.yieldPlayback() }
            }
        }
        // The transfer is done: a list that cannot be read now is not a failed transfer.
        runCatching { refresh() }
    }

    override suspend fun pullHere() {
        val a = audio
        val handled = if (a != null && a.on) {
            runCatching { a.pullHere(play = true) }.getOrDefault(false)
        } else false

        if (!handled) {
            bridge.service("ConnectTransferAPI", "pullToLocalDevice", json(mapOf<String, Any>()))
        }
        refresh()
    }

    private suspend fun sendHttp(method: String, url: String, body: String): Pair<Int, String>? {
        sender?.let { return it(method, url, body) }
        val h = http ?: return null
        return h.send(method, url, body, contentType = "application/json")
    }

    private suspend fun resolveLocalConnectId(): String? =
        ownDeviceId() ?: _devices.value.firstOrNull { it.local }?.id
            ?: runCatching { refresh(); _devices.value.firstOrNull { it.local }?.id }.getOrNull()

    private fun aliasFieldJson(aliasInt: Int?, aliasRaw: String?): String? = when {
        aliasInt != null -> aliasInt.toString()
        !aliasRaw.isNullOrBlank() -> JsonPrimitive(aliasRaw).toString()
        else -> null
    }

    /**
     * The volume of another device, as the Web Player's own slider sets it while that device plays
     * (connect-state `volume`, 0 to 65535), sent from this app's device. The page's own volume call
     * changed only the page's player: the computer kept its volume (reported on Ko-fi).
     */
    override suspend fun setVolume(deviceId: String, level: Double): Boolean {
        val from = resolveLocalConnectId() ?: return false
        val parsed = io.github.thake.soundnest.engine.ConnectDeviceId.parse(deviceId)
        if (from == parsed.wireDeviceId) return false
        val v = (level.coerceIn(0.0, 1.0) * 65535).toInt()
        suspend fun send(targetId: String, aliasInt: Int?, aliasRaw: String?): Int {
            val aliasVal = aliasFieldJson(aliasInt, aliasRaw)
            val body = if (aliasVal != null) """{"volume":$v,"command_options":{"target_alias_id":$aliasVal}}""" else """{"volume":$v}"""
            return sendHttp("PUT", "https://spclient.wg.spotify.com/connect-state/v1/connect/volume/from/$from/to/$targetId", body)?.first ?: -1
        }
        val code = send(parsed.wireDeviceId, parsed.wireAliasInt, parsed.wireAliasRaw)
        if (code in 200..299) return true
        val alt = parsed.alternateWire ?: return false
        if (code != 404 && code != 410) return false
        return send(alt.first, alt.second, alt.second?.toString()) in 200..299
    }

    override suspend fun pause(deviceId: String): Boolean {
        if (deviceId.isBlank()) return false
        val from = resolveLocalConnectId() ?: return false
        val parsed = io.github.thake.soundnest.engine.ConnectDeviceId.parse(deviceId)
        suspend fun send(targetId: String, aliasInt: Int?, aliasRaw: String?): Int {
            val aliasVal = aliasFieldJson(aliasInt, aliasRaw)
            val body = if (aliasVal != null) """{"command":{"endpoint":"pause"},"target_alias_id":$aliasVal}""" else """{"command":{"endpoint":"pause"}}"""
            return sendHttp("POST", "https://spclient.wg.spotify.com/connect-state/v1/player/command/from/$from/to/$targetId", body)?.first ?: -1
        }
        val code = send(parsed.wireDeviceId, parsed.wireAliasInt, parsed.wireAliasRaw)
        if (code in 200..299) return true
        val alt = parsed.alternateWire ?: return false
        if (code != 404 && code != 410) return false
        return send(alt.first, alt.second, alt.second?.toString()) in 200..299
    }
}

/** Jam goes through the page's SocialConnectAPI. Free accounts get RESTRICTED from Spotify. */
class SpotifyJam(private val bridge: Bridge, private val http: SpotifyHttp) : Jam {
    private companion object { const val SOCIAL = "https://spclient.wg.spotify.com/social-connect" }
    private val _session = MutableStateFlow<JamSession?>(null)
    override val session: StateFlow<JamSession?> = _session

    override suspend fun availability(): JamAvailability =
        when ((bridge.service("SocialConnectAPI", "getEnabled") as? JsonPrimitive)?.content) {
            "ENABLED", "AVAILABLE" -> JamAvailability.AVAILABLE
            "RESTRICTED", "DISABLED" -> JamAvailability.RESTRICTED
            else -> JamAvailability.UNKNOWN
        }

    private fun parse(s: JsonElement?): JamSession? {
        val id = s.str("session_id") ?: s.str("sessionId") ?: return null
        val members = (s.list("session_members").ifEmpty { s.list("sessionMembers") }).map { m ->
            JamMember(
                userUri = "spotify:user:" + (m.str("username") ?: m.str("id") ?: ""),
                name = m.str("display_name") ?: m.str("displayName") ?: m.str("username") ?: "",
                imageUrl = m.str("image_url") ?: m.str("imageUrl") ?: m.str("large_image_url"),
                // session_owner_id is the member's id (hex), not its username.
                host = m.bool("is_host") ?: m.bool("isHost")
                    ?: ((s.str("session_owner_id") ?: s.str("sessionOwnerId")).let { o -> o != null && (m.str("id") == o || m.str("username") == o) }),
            )
        }
        return JamSession(
            id = id,
            joinToken = s.str("join_session_token") ?: s.str("joinSessionToken"),
            inviteLink = null,
            members = members,
            isHost = s.bool("is_session_owner") ?: s.bool("isSessionOwner") ?: false,
            queueOnly = s.bool("queue_only_mode") ?: s.bool("queueOnlyMode") ?: false,
        )
    }

    /** The session this account is in, read from Spotify (404: none): also a Jam joined in the room, which the page does not know. */
    override suspend fun refresh() {
        val (code, body) = http.send("GET", "$SOCIAL/v2/sessions/current")
        val read = if (code in 200..299) parse(runCatching { Json.parseToJsonElement(body) }.getOrNull()) else if (code == 404) null
            else parse(bridge.service("SocialConnectAPI", "fetchCurrentSession"))
        // How this phone joined is not in the session read again: kept from the join.
        _session.value = read?.copy(listensHere = read.id == listeningIn)
    }

    /** The session this phone joined as a listener (REMOTE), null when it joined to control the host. */
    @Volatile private var listeningIn: String? = null

    override suspend fun start(): JamSession {
        if (availability() == JamAvailability.RESTRICTED) throw UnsupportedOperationException("Jam is restricted on this account")
        // GET social-connect/v2/sessions/current_or_new?activate=true, as the official apps. Its answer is
        // the short form (no members, no owner flag): the session is read again in full.
        val created = parse(bridge.service("SocialConnectAPI", "createSession")) ?: throw GqlException("jam", "no session")
        runCatching { refresh() }
        return _session.value ?: created.copy(isHost = true).also { _session.value = it }
    }

    override suspend fun join(joinToken: String): JamSession {
        // Sent to Spotify directly, as the page's own join dialog sends it (docs/api/JAM.md): the page's
        // service gives up at once on an account it marks RESTRICTED, while Spotify lets a free account
        // join as in the room. As the official apps offer it: "Continue on this device" first
        // (LISTEN_AND_CONTROL: this phone streams the Jam itself, Spotify's own playback), else "Play
        // from the host's speaker" (CONTROL: this phone controls the host's playback). Measured on a
        // free account (3 Nov 2026): the first is answered 403 "User not allowed to join session",
        // the second 200. Each try has its own attempt id.
        // The page's own Connect device joins (local_device_id): without it the session took this
        // account but not always the page's device, which then knew of no Jam, made itself the
        // active device at the first command (a transfer to itself) and so left the Jam.
        val device = localDevice()
        var last = 0 to ""
        for (control in listOf("LISTEN_AND_CONTROL", "CONTROL")) {
            val body = buildJsonObject {
                put("join_token", joinToken); put("join_type", "deeplinking"); put("playback_control", control)
                device?.let { put("local_device_id", it) }
                put("join_attempt_id", java.util.UUID.randomUUID().toString()); put("permission_mode", "FULL")
            }
            last = http.send("POST", "$SOCIAL/v3/sessions/join", body.toString())
            // What Spotify answered each way of joining: the diagnostic says how this phone is in the Jam.
            bridge.logger?.invoke("jam: join as $control (device ${device?.take(6) ?: "none"}) -> HTTP ${last.first} ${io.github.thake.soundnest.spotify.bridge.LogMask.shown(last.second, 300)}")
            if (last.first in 200..299) parse(runCatching { Json.parseToJsonElement(last.second) }.getOrNull())?.let { joined ->
                val listening = control == "LISTEN_AND_CONTROL"
                listeningIn = joined.id.takeIf { listening }
                val s = joined.copy(listensHere = listening)
                _session.value = s
                // Listening, the page is the Jam's player itself: nothing to wait for. Controlling, the
                // first command waits for the page to see the host's device (else it went to itself).
                if (!listening) awaitJamDevice()
                return s
            }
        }
        throw GqlException("jam", "join failed: HTTP ${last.first} ${last.second.take(200)}")
    }

    /** The page's own Connect device id, or null when the page does not say. */
    private suspend fun localDevice(): String? = runCatching {
        ConnectDeviceLabels.fetchDevicesJson(bridge)
            .firstOrNull { it.bool("isLocal") == true }?.let { it.str("id") ?: it.str("connectStateId") }
    }.getOrNull()

    /**
     * Until the page sees the Jam's device as the active one (its commands then go to the host),
     * 6 s at most: a command sent before that went to the page itself and took it out of the Jam.
     */
    private suspend fun awaitJamDevice() {
        val end = System.currentTimeMillis() + 6_000
        while (System.currentTimeMillis() < end) {
            val devices = runCatching { ConnectDeviceLabels.fetchDevicesJson(bridge) }.getOrNull()
            if (devices?.any { it.bool("isActive") == true && it.bool("isLocal") != true } == true) return
            kotlinx.coroutines.delay(400)
        }
    }

    /** Another account takes the page: the Jam known is that of the account that left. */
    internal fun forgetSession() { _session.value = null; listeningIn = null }

    /** In a Jam this account joined: its songs are Spotify's (the Jam's), never an extension's. */
    val member: Boolean get() = _session.value?.isHost == false

    override suspend fun leave() {
        _session.value?.let { http.send("POST", "$SOCIAL/v3/sessions/${it.id}/leave") }
        _session.value = null
        listeningIn = null
    }

    override suspend fun end() {
        _session.value?.let { http.send("DELETE", "$SOCIAL/v3/sessions/${it.id}") }
        _session.value = null
    }

    override suspend fun inviteLink(): String? {
        // The page shortens a link from the join token and the session's URI (not its id: that gave nothing).
        val token = _session.value?.joinToken ?: return null
        val r = runCatching { bridge.service("SocialConnectAPI", "getShortInviteLink", JsonPrimitive(token), JsonPrimitive("spotify:socialsession:$token")) }.getOrNull()
        return (r as? JsonPrimitive)?.content ?: r.str("shareableUrl") ?: r.str("shareUrl") ?: r.str("url")
            ?: "https://open.spotify.com/socialsession/$token"
    }

    override suspend fun removeMember(userUri: Uri) { bridge.service("SocialConnectAPI", "removeSessionMember", JsonPrimitive(idOf(userUri))); refresh() }
    override suspend fun setParticipantVolumeControl(on: Boolean) { bridge.service("SocialConnectAPI", "setParticipantVolumeControl", JsonPrimitive(on)) }
}
