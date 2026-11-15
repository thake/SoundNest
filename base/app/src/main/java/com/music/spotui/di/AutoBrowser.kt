package com.music.spotui.di

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaDescriptionCompat
import app.myspoti.engine.R
import app.myspoti.host.android.MyspotiRuntime
import com.music.spotui.data.api.Response
import com.music.spotui.data.entity.ContextPageModel
import com.music.spotui.data.entity.HomeItem
import com.music.spotui.data.entity.SongsModel
import app.myspoti.api.MediaKind
import app.myspoti.api.best
import app.myspoti.api.groupRecents
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The hidden player did not come up in time (cold start, no network). The browse answer is
 * an empty folder then — MediaBrowserCompat has no error reply — and the log says why.
 */
class AudioNotReady(what: String) : IllegalStateException("player not ready: $what")

/**
 * Android Auto / media browser tree, built from the app's own data (not the web page).
 *
 * Root tabs: Home (recently played, then the home feed's shelves, each a titled group),
 * Library (Liked Songs, playlists, albums, artists), Podcasts (New episodes, then the
 * followed shows). Search answers songs, playlists, albums, artists, podcasts and episodes
 * as titled groups. Tapping a song plays it in its context, the way the album / playlist
 * screens do (see [playTrack]). "Start radio" items start Spotify's radio.
 *
 * Media ids: "home", "library", "podcasts" (tabs); "liked", "playlists", "albums", "artists",
 * "newep" (lists); "pl:<id>", "al:<id>", "ar:<id>|<name>", "sh:<show id>" (contexts);
 * "t|<context uri>|<play key>" (a song),
 * "radio|<seed uri>|<fallback song uri>" (start a radio).
 */
object AutoBrowser {
    const val ROOT = "root"
    private const val TIMEOUT_MS = 15_000L
    /**
     * The most one folder lists. A library of thousands of liked songs, each with a cover the car
     * fetches through [ArtworkProvider] (a binder thread held per image), is what froze Android
     * Auto's screen; the car shows no more than a page of a list while driving anyway.
     */
    private const val MAX_ITEMS = 300
    /** Cold start: the car binds before the hidden Web Player is up; browse waits this long. */
    private const val READY_WAIT_MS = 25_000L
    /** Home: how many recents, shelves and items per shelf (the car pages through a long tab slowly). */
    private const val HOME_RECENTS = 8
    private const val HOME_SHELVES = 6
    private const val HOME_PER_SHELF = 10

    private const val STYLE_LIST = 1
    private const val STYLE_GRID = 2
    private const val KEY_BROWSABLE = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
    private const val KEY_PLAYABLE = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
    private const val KEY_GROUP = "android.media.browse.CONTENT_STYLE_GROUP_TITLE_HINT"

    // Contexts recently listed, so a tap plays with the right queue: the last few only, in the order used.
    private val pages: MutableMap<String, ContextPageModel> = java.util.Collections.synchronizedMap(
        object : LinkedHashMap<String, ContextPageModel>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ContextPageModel>) = size > MAX_PAGES
        })
    private const val MAX_PAGES = 30
    /** How long a long list keeps coming in after its first page was shown ([contextPage]). */
    private const val FULL_LIST_MS = 120_000L
    private const val LIKED_URI = "spotify:collection:tracks"
    /** [ContextPageModel.type] of a show's episodes listed here. */
    private const val SHOW = "show"

    /** The account signs out: nothing it listed stays for the next one. */
    fun clearSession() = pages.clear()
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    /**
     * Browse for [parentId]. The tabs and the Library list answer at once (no session, no network);
     * every other node needs the session, and waits for it instead of returning an empty list,
     * which Android Auto keeps as the folder's content until it unsubscribes (issue #41, cold start).
     */
    suspend fun children(context: Context, parentId: String): List<MediaItem> {
        when {
            parentId == ROOT -> return listOf(
                tab(context, "home", R.string.aa_home, "aa_ic_home", STYLE_GRID),
                tab(context, "library", R.string.aa_library, "aa_ic_library", STYLE_LIST),
                tab(context, "podcasts", R.string.recents_filter_podcasts, "aa_ic_podcasts", STYLE_LIST),
            )
            parentId == "library" -> return libraryTab(context)
        }
        if (!awaitPlayer()) throw AudioNotReady("browse $parentId")
        return when {
            parentId == "home" -> homeTab(context)
            parentId == "playlists" || parentId == "albums" -> {
                val wantPlaylists = parentId == "playlists"
                val entries = (firstResult { Graph.api.getLibrary() } ?: emptyList())
                    .filter { it.isPlaylist == wantPlaylists && it.spotifyId != com.music.spotui.data.api.Api.HomeCache.LIKED_SONGS_ID }
                entries.take(MAX_ITEMS).map { e ->
                    browsable(if (wantPlaylists) "pl:${e.spotifyId}" else "al:${e.spotifyId}", e.name, art(e.coverUri), subtitle = e.subtitle)
                }
            }
            parentId == "artists" -> Graph.api.getFollowedArtists().filter { it.id.isNotBlank() }.take(MAX_ITEMS).map { a ->
                browsable("ar:${a.id}|${a.name}", a.name, art(a.coverUri))
            }
            parentId == "podcasts" -> podcastsTab(context)
            parentId == "newep" -> Graph.api.getNewEpisodes().take(MAX_ITEMS).map { playable("", it) }
            parentId.startsWith("sh:") -> showEpisodes(parentId.removePrefix("sh:"))
            parentId == "liked" || parentId.startsWith("pl:") || parentId.startsWith("al:") -> tracksOf(context, contextPage(parentId))
            parentId.startsWith("ar:") -> {
                val (id, name) = parentId.removePrefix("ar:").split("|", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                val overview = firstResult { Graph.api.getArtistOverview(name, id) } ?: return emptyList()
                val songs = context.getString(R.string.aa_songs)
                listOf(radioItem(context, "spotify:artist:$id", "", name)) +
                    overview.topTracks.map { playable("spotify:artist:$id", it.song, songs) } +
                    overview.popularReleases.filter { it.spotifyId.isNotBlank() }.take(12).map {
                        browsable("al:${it.spotifyId}", it.name, art(it.coverUri), subtitle = it.year, group = context.getString(R.string.aa_albums))
                    }
            }
            else -> emptyList()
        }
    }

    // ── Tabs ──

    private fun tab(context: Context, id: String, title: Int, icon: String, style: Int): MediaItem {
        val d = MediaDescriptionCompat.Builder().setMediaId(id).setTitle(context.getString(title))
            .setIconUri(Uri.parse("android.resource://${context.packageName}/drawable/$icon"))
            .setExtras(hints(style, null)).build()
        return MediaItem(d, MediaItem.FLAG_BROWSABLE)
    }

    private fun libraryTab(context: Context): List<MediaItem> = listOf(
        browsable("liked", context.getString(R.string.collection_liked_songs_title), resource(context, "aa_ic_liked"), grid = false),
        browsable("playlists", context.getString(R.string.aa_playlists), resource(context, "aa_ic_playlists"), grid = false),
        browsable("albums", context.getString(R.string.aa_albums), resource(context, "aa_ic_albums"), grid = false),
        browsable("artists", context.getString(R.string.aa_artists), resource(context, "aa_ic_artists"), grid = false),
    )

    /**
     * "New episodes" (the episodes not played yet of the followed shows), then the followed shows
     * (the phone's Library > Podcasts). Spotify not answering is an error, not an empty tab.
     */
    private suspend fun podcastsTab(context: Context): List<MediaItem> {
        val entries = Graph.api.followedShows().getOrElse { throw AudioNotReady("podcasts: ${it.message}") }
        val shows = context.getString(R.string.recents_filter_podcasts)
        return entries.take(MAX_ITEMS).mapNotNull { e ->
            val showPrefix = com.music.spotui.data.api.Api.SHOW_PREFIX
            when {
                e.spotifyId == com.music.spotui.data.api.Api.NEW_EPISODES_ID ->
                    browsable("newep", e.name, art(e.coverUri), subtitle = e.subtitle, grid = false)
                e.spotifyId.startsWith(showPrefix) ->
                    browsable("sh:" + e.spotifyId.removePrefix(showPrefix), e.name, art(e.coverUri), subtitle = e.subtitle, grid = false, group = shows)
                else -> null
            }
        }
    }

    /** A show's latest episodes (the first page, newest first); a tap plays the episode in its show, as the show screen does. */
    private suspend fun showEpisodes(showId: String): List<MediaItem> {
        val episodes = firstResult { Graph.api.getShowEpisodes(showId) }.orEmpty()
        if (episodes.isEmpty()) return emptyList()
        val page = ContextPageModel(uri = "spotify:show:$showId", type = SHOW, name = episodes.first().album,
            coverUri = episodes.first().coverUri, tracks = episodes.take(MAX_ITEMS))
        pages[page.uri] = page
        return page.tracks.map { playable(page.uri, it) }
    }

    /** Recently played, then the home feed's shelves (mixes, jump back in, …), each a titled group. */
    private suspend fun homeTab(context: Context): List<MediaItem> = kotlinx.coroutines.coroutineScope {
        val recents = async { runCatching { recentItems(context) }.getOrDefault(emptyList()) }
        val feed = async { runCatching { firstResult { Graph.api.getHomeFeed() } }.getOrNull() }
        val out = ArrayList<MediaItem>(recents.await())
        feed.await()?.sections?.take(HOME_SHELVES)?.forEach { section ->
            val title = section.title.ifBlank { context.getString(R.string.aa_home) }
            section.items.mapNotNull { homeItem(it, title) }.take(HOME_PER_SHELF).let(out::addAll)
        }
        out
    }

    private fun homeItem(item: HomeItem, group: String): MediaItem? = when (item) {
        is HomeItem.Playlist -> browsable(
            if (item.id == com.music.spotui.data.api.Api.HomeCache.LIKED_SONGS_ID) "liked" else "pl:${item.id}",
            item.name, art(item.imageUrl), group = group,
        ).takeIf { item.id.isNotBlank() }
        is HomeItem.Album -> browsable("al:${item.id}", item.name, art(item.imageUrl), subtitle = item.artists, group = group).takeIf { item.id.isNotBlank() }
        is HomeItem.Artist -> browsable("ar:${item.id}|${item.name}", item.name, art(item.imageUrl), group = group).takeIf { item.id.isNotBlank() }
        else -> null
    }

    /** The account's recents (all devices): playlists, albums, artists and Liked Songs, as a group. */
    private suspend fun recentItems(context: Context): List<MediaItem> {
        val entries = withTimeoutOrNull(TIMEOUT_MS) { MyspotiRuntime.api.recents.recentEntries(0, 50).items } ?: return emptyList()
        val title = context.getString(R.string.aa_recent)
        val seen = HashSet<String>()
        return groupRecents(entries).flatMap { it.groups }.filter { it.music }.mapNotNull { g ->
            val c = g.context
            val id = c.uri.substringAfterLast(':')
            val mediaId = when {
                c.kind == MediaKind.PLAYLIST -> "pl:$id"
                c.kind == MediaKind.ALBUM -> "al:$id"
                c.kind == MediaKind.ARTIST -> "ar:$id|${c.title}"
                c.kind == MediaKind.COLLECTION && c.uri == "spotify:collection:tracks" -> "liked"
                else -> return@mapNotNull null
            }
            if (id.isBlank() || !seen.add(mediaId)) return@mapNotNull null
            browsable(mediaId, c.title, art(c.images.best(300)?.url), subtitle = c.subtitle.orEmpty(), group = title)
        }.take(HOME_RECENTS)
    }

    // ── Search ──

    suspend fun search(query: String): List<MediaItem> {
        if (!awaitPlayer()) throw AudioNotReady("search")
        // The last answer: the music comes first, the podcasts and episodes in a second one.
        val r = lastResult { Graph.api.searchEverything(query) } ?: return emptyList()
        val context = Graph.app
        val songs = r.songs.take(10)
        val out = ArrayList<MediaItem>()
        songs.firstOrNull()?.let { out += radioItem(context, it.url, it.url, it.title) }
        val songsTitle = context.getString(R.string.aa_songs)
        songs.forEach { out += playable("", it, songsTitle) }
        val playlists = context.getString(R.string.aa_playlists)
        r.playlists.filter { it.spotifyId.isNotBlank() && !it.isFolder }.take(8).forEach {
            out += browsable("pl:${it.spotifyId}", it.name, art(it.coverUri), subtitle = it.subtitle, group = playlists)
        }
        val albums = context.getString(R.string.aa_albums)
        r.albums.filter { it.spotifyId.isNotBlank() }.take(8).forEach {
            out += browsable("al:${it.spotifyId}", it.name, art(it.coverUri), subtitle = it.artists, group = albums)
        }
        val artists = context.getString(R.string.aa_artists)
        r.artists.filter { it.id.isNotBlank() }.take(8).forEach {
            out += browsable("ar:${it.id}|${it.name}", it.name, art(it.coverUri), group = artists)
        }
        val podcasts = context.getString(R.string.recents_filter_podcasts)
        r.shows.filter { it.id.isNotBlank() }.take(8).forEach {
            out += browsable("sh:${it.id}", it.name, art(it.coverUri), subtitle = it.publisher, group = podcasts)
        }
        val episodes = context.getString(R.string.show_tab_episodes)
        r.episodes.take(8).forEach { out += playable("", it, episodes) }
        return out
    }

    /** Voice search ("play X on Myspoti"): the best matching track. */
    suspend fun playFromSearch(query: String): Boolean {
        // A cold start asks before the page is up: waiting gives the answer, returning at
        // once would tell the driver nothing matched.
        if (!awaitPlayer()) {
            android.util.Log.w("Myspoti", "voice search dropped: the player page is not ready")
            return false
        }
        val song = (firstResult { Graph.api.searchEverything(query) })?.songs?.firstOrNull() ?: return false
        play("t||${song.url}")
        return true
    }

    /** [MyspotiRuntime.api.ready], waited for (bounded) — the page loads after the car connects. */
    private suspend fun awaitPlayer(): Boolean =
        MyspotiRuntime.api.ready.value ||
            withTimeoutOrNull(READY_WAIT_MS) { MyspotiRuntime.api.ready.first { it } } != null

    // ── Playing ──

    fun play(mediaId: String) {
        val parts = mediaId.split("|", limit = 3)
        if (parts.size < 3) return
        when (parts[0]) {
            "t" -> playTrack(parts[1].ifBlank { null }, parts[2])
            "radio" -> startRadio(parts[1], parts[2])
        }
    }

    private fun onMain(block: () -> Unit) { android.os.Handler(android.os.Looper.getMainLooper()).post(block) }

    private fun playTrack(contextUri: String?, key: String, reread: Boolean = true) {
        val page = contextUri?.let { pages[it] }
        // A list the car showed before this process started (or one listed long ago): read again,
        // so the song plays on in its list and not alone, then its radio (BUG-15).
        val node = contextUri?.takeIf { page == null && reread }?.let(::nodeOf)
        if (node != null) {
            scope.launch {
                contextPage(node)?.let { pages[it.uri] = it }
                onMain { playTrack(contextUri, key, reread = false) }
            }
            return
        }
        val song = page?.tracks?.firstOrNull { it.url == key }
        if (page != null) {
            Graph.currentSongState.updateQueue(page.tracks)
            SongPlayer.registerContextName(page.uri, page.name)
            if (page.type == "liked") SongPlayer.registerLikedSongs(page.tracks.map { it.url })
        }
        SongPlayer.playSong(key, contextUri = contextUri, songs = page?.tracks?.takeIf { page.type != SHOW })
        song?.let { Graph.currentSongState.updateSongState(it.coverUri, it.title, it.singer, true, it.id, 0, it.album) }
    }

    /**
     * "Start radio": Spotify's radio playlist for the song / artist / list (the phone's "Go to song
     * radio" opens the same playlist), played from its first song. A list that has no radio of its
     * own falls back to the radio of [fallbackSong].
     */
    private fun startRadio(seed: String, fallbackSong: String) {
        scope.launch {
            val id = Graph.api.radioPlaylistId(seed)
                ?: fallbackSong.takeIf { it.startsWith("spotify:track:") }?.let { Graph.api.radioPlaylistId(it) }
                ?: return@launch
            val page = contextPage("pl:$id")?.takeIf { it.tracks.isNotEmpty() } ?: return@launch
            pages[page.uri] = page
            play("t|${page.uri}|${page.tracks.first().url}")
        }
    }

    // ── Items ──

    private fun tracksOf(context: Context, page: ContextPageModel?): List<MediaItem> {
        page ?: return emptyList()
        pages[page.uri] = page
        val first = page.tracks.firstOrNull()
        val songs = context.getString(R.string.aa_songs)
        val radio = if (first == null) emptyList() else listOf(radioItem(context, page.uri.takeIf { page.type != "liked" } ?: first.url, first.url, page.name))
        return radio + page.tracks.take(MAX_ITEMS).map { playable(page.uri, it, songs) }
    }

    /** The last answer of [load] within [TIMEOUT_MS] (a flow that answers in steps); null when none came. */
    private suspend fun <T> lastResult(load: suspend () -> kotlinx.coroutines.flow.Flow<Response<T>>): T? {
        var last: T? = null
        withTimeoutOrNull(TIMEOUT_MS) { load().collect { if (it is Response.Success) last = it.data } }
        return last
    }

    /**
     * The songs of a list node ("liked", "pl:<id>", "al:<id>"): its first answer, to show at once.
     * A long playlist comes in steps; the rest keeps coming after, and the list kept for a tap
     * ([pages]) grows with it, so the song tapped queues the whole list, not its first 100 songs.
     */
    private suspend fun contextPage(node: String): ContextPageModel? {
        val load: suspend () -> kotlinx.coroutines.flow.Flow<Response<ContextPageModel>> = when {
            node == "liked" -> { { Graph.api.getLikedPage() } }
            node.startsWith("pl:") -> { { Graph.api.getPlaylistPage(node.removePrefix("pl:")) } }
            node.startsWith("al:") -> { { Graph.api.getAlbumPage(node.removePrefix("al:")) } }
            else -> return null
        }
        val first = kotlinx.coroutines.CompletableDeferred<ContextPageModel?>()
        scope.launch {
            withTimeoutOrNull(FULL_LIST_MS) {
                load().collect { r ->
                    when {
                        r is Response.Success && !first.isCompleted -> first.complete(r.data)
                        r is Response.Success -> pages[r.data.uri] = r.data
                        r is Response.Error && !first.isCompleted -> first.complete(null)
                    }
                }
            }
            first.complete(null)
        }
        return withTimeoutOrNull(TIMEOUT_MS) { first.await() }
    }

    /** The list node of a context uri a song's media id names, to read it again ([contextPage]). */
    internal fun nodeOf(contextUri: String): String? = when {
        contextUri == LIKED_URI -> "liked"
        contextUri.startsWith("spotify:playlist:") -> "pl:" + contextUri.removePrefix("spotify:playlist:")
        contextUri.startsWith("spotify:album:") -> "al:" + contextUri.removePrefix("spotify:album:")
        else -> null
    }

    private suspend fun <T> firstResult(load: suspend () -> kotlinx.coroutines.flow.Flow<Response<T>>): T? =
        withTimeoutOrNull(TIMEOUT_MS) {
            (load().first { it !is Response.Loading } as? Response.Success)?.data
        }

    private fun art(url: String?): Uri? = ArtworkProvider.uriFor(url)

    private fun resource(context: Context, name: String): Uri = Uri.parse("android.resource://${context.packageName}/drawable/$name")

    private fun hints(style: Int, group: String?) = Bundle().apply {
        putInt(KEY_BROWSABLE, style)
        putInt(KEY_PLAYABLE, STYLE_LIST)
        if (group != null) putString(KEY_GROUP, group)
    }

    private fun browsable(id: String, title: String, icon: Uri?, subtitle: String = "", grid: Boolean = true, group: String? = null): MediaItem {
        val d = MediaDescriptionCompat.Builder().setMediaId(id).setTitle(title).setSubtitle(subtitle)
            .setIconUri(icon).setExtras(hints(if (grid) STYLE_GRID else STYLE_LIST, group)).build()
        return MediaItem(d, MediaItem.FLAG_BROWSABLE)
    }

    private fun playable(contextUri: String, s: SongsModel, group: String? = null): MediaItem {
        val d = MediaDescriptionCompat.Builder().setMediaId("t|$contextUri|${s.url}")
            .setTitle(s.title).setSubtitle(s.singer).setIconUri(art(s.coverUri)).setExtras(hints(STYLE_LIST, group)).build()
        return MediaItem(d, MediaItem.FLAG_PLAYABLE)
    }

    private fun radioItem(context: Context, seed: String, fallbackSong: String, name: String): MediaItem {
        val d = MediaDescriptionCompat.Builder().setMediaId("radio|$seed|$fallbackSong")
            .setTitle(context.getString(R.string.aa_start_radio)).setSubtitle(name)
            .setIconUri(resource(context, "aa_ic_radio")).setExtras(hints(STYLE_LIST, null)).build()
        return MediaItem(d, MediaItem.FLAG_PLAYABLE)
    }
}

/**
 * Cover art for Android Auto, which only shows local (content://) artwork: serves an
 * image from Spotify's image CDN through Glide's disk cache. Other hosts are refused.
 */
class ArtworkProvider : android.content.ContentProvider() {
    companion object {
        private val AUTHORITY = app.myspoti.engine.BuildConfig.APPLICATION_ID + ".artwork"
        private val allowedHosts = setOf("i.scdn.co", "mosaic.scdn.co", "image-cdn-ak.spotifycdn.com", "image-cdn-fa.spotifycdn.com", "thisis-images.spotifycdn.com", "seeded-session-images.scdn.co", "pickasso.spotifycdn.com")

        fun uriFor(url: String?): Uri? {
            if (url.isNullOrBlank() || !allowed(url)) return null
            return Uri.Builder().scheme("content").authority(AUTHORITY).appendPath("img").appendQueryParameter("u", url).build()
        }

        /**
         * Only https URLs on the CDN hosts: a `file:` URL whose authority happens to be
         * an allowed host (`file://i.scdn.co/data/data/...`) must never reach Glide,
         * which would open the local path and hand this app's private files to any caller
         * (the provider is exported, for Android Auto).
         */
        private fun allowed(url: String): Boolean {
            val u = Uri.parse(url)
            return u.scheme == "https" && u.host in allowedHosts
        }
    }

    override fun openFile(uri: Uri, mode: String): android.os.ParcelFileDescriptor? {
        val url = uri.getQueryParameter("u") ?: return null
        if (!allowed(url)) return null
        // Bounded: a binder thread must not wait on the network forever.
        val file = runCatching {
            com.bumptech.glide.Glide.with(context!!).asFile().load(url).submit()
                .get(15, java.util.concurrent.TimeUnit.SECONDS)
        }.getOrNull() ?: return null
        return android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri) = "image/jpeg"
    override fun onCreate() = true
    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?) = null
    override fun insert(uri: Uri, values: android.content.ContentValues?) = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: android.content.ContentValues?, s: String?, a: Array<out String>?) = 0
}
