package io.github.thake.soundnest.data.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import android.util.Log
import io.github.thake.soundnest.api.Album
import io.github.thake.soundnest.api.Artist
import io.github.thake.soundnest.api.Image
import io.github.thake.soundnest.api.LibraryFilter
import io.github.thake.soundnest.api.LocalFile
import io.github.thake.soundnest.api.MediaCard
import io.github.thake.soundnest.api.MediaKind
import io.github.thake.soundnest.api.SoundNestApi
import io.github.thake.soundnest.api.Playlist
import io.github.thake.soundnest.api.Track
import io.github.thake.soundnest.api.best
import io.github.thake.soundnest.R
import io.github.thake.soundnest.host.android.SoundNestRuntime
import io.github.thake.soundnest.data.entity.AlbumsModel
import io.github.thake.soundnest.data.entity.ArtistOverviewModel
import io.github.thake.soundnest.data.entity.ArtistTrackUi
import io.github.thake.soundnest.data.entity.ArtistsModel
import io.github.thake.soundnest.data.entity.ContextPageModel
import io.github.thake.soundnest.data.entity.HomeFeedModel
import io.github.thake.soundnest.data.entity.HomeItem
import io.github.thake.soundnest.data.entity.HomeSection
import io.github.thake.soundnest.data.entity.LibraryEntry
import io.github.thake.soundnest.data.entity.PodcastModel
import io.github.thake.soundnest.data.entity.SearchResults
import io.github.thake.soundnest.data.entity.ShowPage
import io.github.thake.soundnest.data.entity.SongsModel
import io.github.thake.soundnest.data.local.LocalFileMatch
import io.github.thake.soundnest.data.local.LocalMusic
import io.github.thake.soundnest.di.Graph
import io.github.thake.soundnest.di.SongPlayer
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.contentOrNull

/**
 * The UI's data source, over [SoundNestApi]. Keeps the `Flow<Response<...>>` contract the
 * ViewModels use; every call maps the API's models to the UI's ones.
 */
class Api constructor(
    private val context: android.content.Context = Graph.app,
) {
    private val api: SoundNestApi get() = SoundNestRuntime.api

    private fun stableId(key: String): Int = key.hashCode() and 0x7fffffff

    /** Process-level caches: screens show the last result at once, then refresh. */
    companion object HomeCache {
        /** Releases loaded for an artist page, at most. */
        const val DISCOGRAPHY_MAX = 300
        /** Sentinel id for the pinned "Liked Songs" library entry. */
        const val LIKED_SONGS_ID = "liked-songs"
        /** The Liked Songs artwork, as the library and the home shortcuts draw it. */
        const val LIKED_COVER = "https://misc.scdn.co/liked-songs/liked-songs-640.png"
        /** The "Local Files" folder pinned under Liked Songs in the library (not a Spotify item). */
        const val LOCAL_FILES_ID = "local-files"
        /** "Your Episodes" (saved podcast episodes). */
        const val EPISODES_ID = "your-episodes"
        /** Prefix of the library id of a followed show in the Podcasts chip (a plain show id otherwise). */
        const val SHOW_PREFIX = "show:"
        /** "New episodes" (unplayed episodes of the followed shows), first of the Podcasts chip's shows. */
        const val NEW_EPISODES_ID = "new-episodes"
        /** Episodes asked for each followed show by the new episodes list, and the most it keeps. */
        private const val NEW_PER_SHOW = 3
        private const val NEW_EPISODES_MAX = 60
        /** The new episodes list is asked for again only after this long (the shows are many queries). */
        private const val NEW_EPISODES_TTL_MS = 30 * 60_000L
        @Volatile var newEpisodes: Pair<Long, List<SongsModel>>? = null
        /** Pages of 50 asked for a show's episodes / chapters, at most (500 rows). */
        const val MAX_EPISODE_PAGES = 10
        /** The page's collection set behind its "Mark as finished" menu. */
        private const val MARKED_FINISHED = "markedasfinished"
        @Volatile var albums: List<AlbumsModel>? = null
        @Volatile var artists: List<ArtistsModel>? = null
        @Volatile var home: HomeFeedModel? = null
        val facets = java.util.concurrent.ConcurrentHashMap<String, HomeFeedModel>()
        @Volatile var library: List<LibraryEntry>? = null
        /** Library as shown by the Library screen (root level, folders closed). */
        @Volatile var libraryRoot: List<LibraryEntry>? = null
        /** Contents of the library folders opened so far, by folder uri. */
        val folders = java.util.concurrent.ConcurrentHashMap<String, List<LibraryEntry>>()

        /** A playlist's songs were changed from the app: the next page of it reads them again. */
        fun invalidatePlaylist(playlistId: String) = PlaylistFresh.drop("spotify:playlist:$playlistId")

        /** Drop all cached feeds (e.g. on logout / account switch). */
        fun clear() {
            PlaylistFresh.drop(null)
            albums = null; artists = null; home = null; library = null
            libraryRoot = null; folders.clear(); newEpisodes = null
            facets.clear()
            LikedCache.tracks = null
            ArtistCache.map.clear()
            ScreenCache.clear()
        }
    }

    object LikedCache {
        @Volatile var tracks: List<SongsModel>? = null
        private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
        @Volatile private var loading: kotlinx.coroutines.Deferred<List<SongsModel>?>? = null

        /**
         * All liked songs, loaded once for the whole app (not again by every screen that needs
         * them, nor started over when that screen closes before the end).
         */
        @Synchronized
        fun loadOnce(): kotlinx.coroutines.Deferred<List<SongsModel>?> {
            tracks?.let { return kotlinx.coroutines.CompletableDeferred(it) }
            loading?.takeIf { it.isActive }?.let { return it }
            return scope.async {
                runCatching {
                    Graph.api.getLikedPage().first { it is Response.Success && it.data.complete || it is Response.Error }
                        .let { (it as? Response.Success)?.data?.tracks }
                }.getOrNull()?.also { if (tracks == null) tracks = it }
            }.also { loading = it }
        }

        /**
         * [head] (the newest liked songs, from Spotify) then the rest of [known] after them.
         * A song unliked inside the head's span is gone from it, so it goes from the list too.
         */
        /** True when [head] ends inside [known] (or is the whole list), so [merge] loses nothing. */
        fun reaches(head: List<SongsModel>, known: List<SongsModel>): Boolean {
            if (head.size < 50) return true
            val last = head.last().spotifyTrackId
            return known.any { it.spotifyTrackId == last }
        }

        fun merge(head: List<SongsModel>, known: List<SongsModel>): List<SongsModel> {
            val ids = head.mapTo(HashSet()) { it.spotifyTrackId }
            val lastHead = head.lastOrNull()?.spotifyTrackId
            // What came after the head's last song in the old list is older: kept as it was.
            val tailStart = known.indexOfFirst { it.spotifyTrackId == lastHead }.let { if (it < 0) known.size else it + 1 }
            val tail = if (head.size < 50) emptyList() else known.drop(tailStart).filterNot { it.spotifyTrackId in ids }
            return head + tail
        }
    }

    /** True once the Web Player is signed in and ready (waits up to 30 s at startup). */
    private suspend fun ready(): Boolean =
        withTimeoutOrNull(30_000) { api.ready.first { it } } != null

    /**
     * For reads that go straight to Spotify over HTTP (search): ready as soon as a token is in hand,
     * the one kept from the last launch included, without waiting for the page to load.
     */
    private suspend fun readyToRead(): Boolean =
        api.ready.value || runCatching { io.github.thake.soundnest.host.android.SoundNestRuntime.spotify.http.hasToken() }.getOrDefault(false) || ready()

    private suspend fun diskHome(): HomeFeedModel? =
        withContext(Dispatchers.IO) { ScreenCache.home.read() }?.also { HomeCache.home = it }

    private fun notSignedIn() = context.getString(R.string.not_signed_in)

    // ── Mapping ──

    private fun cover(images: List<Image>) = images.best(300)?.url.orEmpty()
    private fun big(images: List<Image>) = images.best(640)?.url.orEmpty()

    fun songOf(t: Track): SongsModel {
        val id = t.uri.substringAfterLast(':')
        val singer = t.artists.joinToString(", ") { it.name }
        if (t.uri.startsWith("spotify:episode:")) return SongsModel(
            id = stableId("episode:$id"),
            title = t.name.take(128),
            album = t.album?.name.orEmpty(),
            singer = singer,
            coverUri = cover(t.album?.images.orEmpty()),
            url = "episode:$id",
            explicit = t.explicit,
            durationMs = t.durationMs.toInt(),
            addedAt = t.addedAt.orEmpty(),
            playable = t.playable,
            rowUid = t.uid.orEmpty(),
        )
        // A local file of the user's computer: the uri holds its tags, nothing else. Greyed out
        // until [LocalFileMatch] finds the same song on this device.
        if (LocalFile.isLocal(t.uri)) return SongsModel(
            id = stableId("local:${t.uri}"),
            title = t.name.take(128),
            album = t.album?.name.orEmpty(),
            singer = singer,
            coverUri = "",
            url = t.uri,
            durationMs = t.durationMs.toInt(),
            addedAt = t.addedAt.orEmpty(),
            playable = false,
            rowUid = t.uid.orEmpty(),
        )
        return SongsModel(
            id = stableId("track:$id"),
            title = t.name.take(128),
            album = t.album?.name.orEmpty(),
            singer = singer,
            coverUri = cover(t.album?.images.orEmpty()),
            url = SongPlayer.buildSpotifyPlayQuery(id, t.name, singer),
            spotifyTrackId = id,
            explicit = t.explicit,
            durationMs = t.durationMs.toInt(),
            albumId = t.album?.uri?.substringAfterLast(':').orEmpty(),
            addedAt = t.addedAt.orEmpty(),
            playable = t.playable,
            artistIds = t.artists.map { it.uri.substringAfterLast(':') },
            rowUid = t.uid.orEmpty(),
        )
    }

    /** [songs] with the playlist's local files played from this device's own copy, when there is one. */
    private suspend fun withDeviceFiles(songs: List<SongsModel>): List<SongsModel> {
        if (songs.none { it.url.startsWith(LocalFileMatch.SPOTIFY_PREFIX) }) return songs
        return LocalFileMatch.resolve(songs, LocalMusic.tracks(context))
    }

    private fun episodeSong(e: io.github.thake.soundnest.api.Episode): SongsModel {
        val id = e.uri.substringAfterLast(':')
        val show = e.show?.name ?: "Podcast"
        return SongsModel(
            id = stableId("episode:$id"),
            title = e.name,
            album = show,
            singer = show,
            coverUri = cover(e.images).ifBlank { cover(e.show?.images.orEmpty()) },
            url = "episode:$id",
            durationMs = e.durationMs.toInt(),
            // No spotifyTrackId on purpose: an episode is keyed by its "episode:<id>" url
            // everywhere (download, playback). "playable" is what the downloader refuses on.
            playable = e.playable,
            description = e.description.orEmpty(),
            releaseDate = e.releaseDate.orEmpty(),
            played = e.played,
            playedPositionMs = e.playedPositionMs,
            showId = e.show?.uri?.substringAfterLast(':').orEmpty(),
        )
    }

    /** The id the UI keys an item by: Spotify's bare id, or the whole uri of an item of another provider. */
    private fun idOf(uri: String) = if (uri.startsWith("spotify:")) uri.substringAfterLast(':') else uri

    private fun albumOf(a: Album) = AlbumsModel(
        spotifyId = idOf(a.uri),
        id = stableId("album:${a.uri}"),
        artists = a.artists.joinToString(", ") { it.name },
        coverUri = cover(a.images),
        name = a.name,
        time = a.releaseDate?.take(10).orEmpty(),
        type = a.type,
    )

    private fun artistOf(a: Artist) = ArtistsModel(name = a.name, coverUri = cover(a.images), id = idOf(a.uri))

    private fun playlistEntry(p: Playlist) = LibraryEntry(
        spotifyId = p.uri.substringAfterLast(':'),
        name = p.name,
        subtitle = context.getString(R.string.assisted_curation_search_playlist) + (p.owner?.let { " • $it" } ?: ""),
        coverUri = cover(p.images),
        isPlaylist = true,
    )

    private fun policy() = io.github.thake.soundnest.parental.ParentalPolicyStore.current(context)

    private fun filterFeed(feed: HomeFeedModel, p: io.github.thake.soundnest.parental.ParentalPolicy = policy()): HomeFeedModel =
        feed.copy(
            sections = feed.sections.mapNotNull { sec ->
                val allowed = sec.items.mapNotNull { io.github.thake.soundnest.parental.ParentalFilterEngine.filterHomeItem(it, p) }
                if (allowed.isEmpty()) null else sec.copy(items = allowed)
            },
        )

    private fun homeItem(c: MediaCard, p: io.github.thake.soundnest.parental.ParentalPolicy = policy()): HomeItem? {
        val id = c.uri.substringAfterLast(':')
        val image = cover(c.images)
        if (c.kind == MediaKind.AUDIOBOOK || c.kind == MediaKind.CHAPTER) {
            if (!io.github.thake.soundnest.parental.ParentalFilterEngine.isShowAllowed(id, c.title, c.subtitle.orEmpty(), isAudiobook = true, policy = p)) return null
            return HomeItem.Show(id = id, name = c.title, imageUrl = image, subtitle = c.subtitle.orEmpty(), isEpisode = c.kind == MediaKind.CHAPTER)
        }
        val mapped = when (c.kind) {
            MediaKind.ALBUM -> HomeItem.Album(id = id, name = c.title, imageUrl = image, subtitle = c.subtitle ?: context.getString(R.string.album_type_album), artists = c.subtitle.orEmpty())
            MediaKind.ARTIST -> HomeItem.Artist(name = c.title, imageUrl = image, id = id)
            MediaKind.PLAYLIST -> HomeItem.Playlist(name = c.title, imageUrl = image, subtitle = c.subtitle?.let { context.getString(R.string.assisted_curation_search_playlist) + " • $it" } ?: context.getString(R.string.assisted_curation_search_playlist), id = id)
            MediaKind.SHOW -> HomeItem.Show(id = id, name = c.title, imageUrl = image, subtitle = c.subtitle.orEmpty(), isEpisode = false)
            MediaKind.EPISODE -> HomeItem.Show(id = id, name = c.title, imageUrl = image, subtitle = c.subtitle.orEmpty(), isEpisode = true)
            MediaKind.COLLECTION -> HomeItem.Playlist(name = c.title, imageUrl = image.ifBlank { LIKED_COVER }, subtitle = context.getString(R.string.assisted_curation_search_playlist), id = LIKED_SONGS_ID)
            else -> null
        } ?: return null
        return io.github.thake.soundnest.parental.ParentalFilterEngine.filterHomeItem(mapped, p)
    }

    private fun likedEntry() = LibraryEntry(
        spotifyId = LIKED_SONGS_ID,
        name = context.getString(R.string.collection_liked_songs_title),
        subtitle = context.getString(R.string.assisted_curation_search_playlist),
        coverUri = LIKED_COVER,
        isPlaylist = true,
    )

    // ── Home ──

    /**
     * New releases for the "New releases" screen: albums from artists the user follows
     * (home shelves), then Spotify's new-release editorial playlists' albums.
     */
    suspend fun newReleases(): List<AlbumsModel> {
        val p = policy()
        val feed = api.home.feed(io.github.thake.soundnest.api.HomeFilter.MUSIC, itemsPerShelf = 30)
        val albums = feed.shelves.flatMap { it.items }.filter { it.kind == MediaKind.ALBUM }.distinctBy { it.uri }
        return albums.map { c ->
            AlbumsModel(spotifyId = c.uri.substringAfterLast(':'), id = stableId("album:${c.uri}"), artists = c.subtitle.orEmpty(),
                coverUri = cover(c.images), name = c.title, time = "", type = "ALBUM")
        }.filter { io.github.thake.soundnest.parental.ParentalFilterEngine.isAlbumAllowed(it, p) }
    }

    /** Artists the user follows (library "Artists" filter). */
    suspend fun getFollowedArtists(): List<ArtistsModel> {
        if (!ready()) return emptyList()
        val p = policy()
        val out = ArrayList<ArtistsModel>()
        var offset = 0
        while (true) {
            val page = runCatching { api.library.items(filter = LibraryFilter.ARTISTS, offset = offset, limit = 50) }.getOrNull() ?: break
            page.items.filter { it.kind == MediaKind.ARTIST }.forEach { out += ArtistsModel(it.name, cover(it.images), it.uri.substringAfterLast(':'), it.addedAt.orEmpty()) }
            offset = page.nextOffset ?: break
        }
        return out.filter { io.github.thake.soundnest.parental.ParentalFilterEngine.isArtistAllowed(it, p) }
    }

    /**
     * Every show the user follows (library "Podcasts" filter), as library rows. The list is read
     * page after page to its end: the Home podcasts shelf holds only the first 20. Empty when it
     * could not be read; [followedShows] tells that apart from no show followed.
     */
    suspend fun getFollowedShows(): List<LibraryEntry> = followedShows().getOrDefault(emptyList())

    /**
     * [getFollowedShows], failing when Spotify did not answer its first page (no session, an error):
     * the Podcasts chip said "nothing followed" for an error and logged nothing (#136). A later
     * page that fails ends the list with the shows already read.
     *
     * The "Podcasts" filter id was never captured (only Playlists, Artists and Albums were), and an
     * id Spotify does not know answers an empty list, which the chip showed as "nothing followed"
     * for accounts that follow shows (#136). So when the filter finds no show, the whole library
     * is read and its shows kept: the unfiltered list carries them whatever the filters are called.
     */
    suspend fun followedShows(): Result<List<LibraryEntry>> {
        val p = policy()
        if (!p.allowPodcasts) return Result.success(emptyList())
        if (!ready()) return Result.failure(IllegalStateException(notSignedIn()))
        val filtered = readShows(LibraryFilter.PODCASTS)
        val shows = if (filtered.getOrNull().isNullOrEmpty()) readShows(LibraryFilter.ALL).takeIf { it.isSuccess || filtered.isFailure } ?: filtered else filtered
        val out = shows.getOrElse { return Result.failure(it) }.mapNotNull {
            val entry = LibraryEntry(SHOW_PREFIX + it.uri.substringAfterLast(':'), it.name,
                context.getString(R.string.album_type_podcast) + (it.subtitle?.let { s -> " • $s" } ?: ""), cover(it.images), isPlaylist = false)
            io.github.thake.soundnest.parental.ParentalFilterEngine.filterLibraryEntry(entry, p)
        }
        if (out.isEmpty()) return Result.success(out)
        val news = LibraryEntry(NEW_EPISODES_ID, context.getString(R.string.new_episodes_title), context.getString(R.string.new_episodes_subtitle),
            out.first().coverUri, isPlaylist = true)
        return Result.success(listOf(news) + out)
    }

    /**
     * The shows of the library list read with [filter], page after page, and of the folders in it
     * (a show filed in a folder is only listed inside it), three levels down at most; failing on
     * a first page that fails.
     */
    private suspend fun readShows(filter: LibraryFilter, folderUri: String? = null, depth: Int = 0): Result<List<io.github.thake.soundnest.api.LibraryItem>> {
        val out = ArrayList<io.github.thake.soundnest.api.LibraryItem>()
        val folders = ArrayList<String>()
        var offset = 0
        while (true) {
            val page = runCatching { api.library.items(filter = filter, folderUri = folderUri, offset = offset, limit = 50) }.getOrElse {
                if (it is CancellationException) throw it
                Log.e("Api", "getFollowedShows ($filter${folderUri?.let { f -> " in $f" }.orEmpty()}) failed at $offset", it)
                if (offset == 0) return Result.failure(it)
                null
            } ?: break
            out += page.items.filter { it.kind == MediaKind.SHOW }
            folders += page.items.filter { it.kind == MediaKind.FOLDER }.map { it.uri }
            offset = page.nextOffset ?: break
        }
        if (depth < 3) folders.distinct().forEach { f -> readShows(filter, f, depth + 1).getOrNull()?.let { out += it } }
        return Result.success(out.distinctBy { it.uri })
    }

    /**
     * The episodes not played yet of the shows the user follows, newest first: the show page's own
     * episodes query, asked for the latest few of each show, four shows at a time. Kept for half
     * an hour, so opening the screen again asks nothing.
     */
    suspend fun getNewEpisodes(): List<SongsModel> {
        val p = policy()
        if (!p.allowPodcasts || !ready()) return emptyList()
        newEpisodes?.let { (at, list) ->
            if (System.currentTimeMillis() - at < NEW_EPISODES_TTL_MS) {
                return list.filter { io.github.thake.soundnest.parental.ParentalFilterEngine.isTrackAllowed(it, p) }
            }
        }
        val shows = getFollowedShows().filter { it.spotifyId.startsWith(SHOW_PREFIX) }.map { it.spotifyId.removePrefix(SHOW_PREFIX) }
        val gate = kotlinx.coroutines.sync.Semaphore(PLAYLIST_PARALLEL)
        val found = coroutineScope {
            shows.map { id ->
                async(Dispatchers.IO) { gate.withPermit { runCatching { api.podcasts.episodes("spotify:show:$id", 0, NEW_PER_SHOW).items }.getOrDefault(emptyList()) } }
            }.map { it.await() }.flatten()
        }
        val list = found.map(::episodeSong)
            .filter { !it.played && it.releaseDate.isNotBlank() && io.github.thake.soundnest.parental.ParentalFilterEngine.isTrackAllowed(it, p) }
            .sortedByDescending { it.releaseDate }.take(NEW_EPISODES_MAX)
        if (list.isNotEmpty()) newEpisodes = System.currentTimeMillis() to list
        return list
    }

    /** [facet]: "" (all), "music-chip", "podcasts-chip", "audiobooks-chip", or a sub-chip Spotify sent (HomeSubChips). */
    suspend fun getHomeFeed(facet: String = ""): Flow<Response<HomeFeedModel>> = flow {
        val p = policy()
        if (facet.contains("podcast", ignoreCase = true) && !p.allowPodcasts) {
            emit(Response.Success(HomeFeedModel())); return@flow
        }
        if (facet.contains("audiobook", ignoreCase = true) && !p.allowAudiobooks) {
            emit(Response.Success(HomeFeedModel())); return@flow
        }
        val cached = if (facet.isEmpty()) HomeCache.home ?: diskHome() else HomeCache.facets[facet]
        cached?.let { emit(Response.Success(filterFeed(it, p))) } ?: emit(Response.Loading())
        if (!ready()) { if (cached == null) emit(Response.Error(notSignedIn())); return@flow }
        // Any facet Spotify offers: a top chip, or one opened beside it (Music > Following).
        runCatching { api.home.feed(facet, itemsPerShelf = 20) }.fold(
            onSuccess = { feed ->
                io.github.thake.soundnest.data.entity.HomeSubChips.learn(feed.chips)
                val sections = feed.shelves.mapNotNull { shelf ->
                    val items = shelf.items.distinctBy { it.uri }.mapNotNull { homeItem(it, p) }
                        .distinctBy { it::class.simpleName + "|" + it.name.lowercase() }
                    if (items.isEmpty()) null else HomeSection(title = shelf.title.orEmpty(), items = items, uri = shelf.uri.orEmpty())
                }.distinctBy { it.uri.ifBlank { it.title.lowercase().ifBlank { it.hashCode().toString() } } }
                val model = HomeFeedModel(greeting = feed.greeting, sections = sections)
                if (facet.isEmpty()) { HomeCache.home = model; ScreenCache.home.write(model) } else HomeCache.facets[facet] = model
                emit(Response.Success(model))
            },
            onFailure = { Log.e("Api", "getHomeFeed failed", it); if (cached == null) emit(Response.Error(it.message ?: "error")) },
        )
    }

    // ── Search ──

    suspend fun searchTracks(query: String): Flow<Response<List<SongsModel>>> = flow {
        emit(Response.Loading())
        if (query.isBlank()) { emit(Response.Success(emptyList())); return@flow }
        if (!ready()) { emit(Response.Error(notSignedIn())); return@flow }
        val p = policy()
        runCatching { api.search.tracks(query, limit = 30).items.map(::songOf).filter { io.github.thake.soundnest.parental.ParentalFilterEngine.isTrackAllowed(it, p) } }.fold(
            onSuccess = { emit(Response.Success(it)) },
            onFailure = { Log.e("Api", "searchTracks failed", it); emit(Response.Error(it.message ?: "error")) },
        )
    }

    /** Tracks, albums, artists and playlists in parallel, then podcasts and episodes. */
    suspend fun searchEverything(query: String): Flow<Response<SearchResults>> = flow {
        emit(Response.Loading())
        if (query.isBlank()) { emit(Response.Success(SearchResults())); return@flow }
        if (!readyToRead()) { emit(Response.Error(notSignedIn())); return@flow }
        val p = policy()
        val music = runCatching { api.search.all(query, limit = 20) }.getOrElse {
            Log.e("Api", "searchEverything failed", it); emit(Response.Error(it.message ?: "error")); return@flow
        }
        val base = SearchResults(
            songs = music.tracks.items.map(::songOf).filter { io.github.thake.soundnest.parental.ParentalFilterEngine.isTrackAllowed(it, p) },
            albums = music.albums.items.map { a ->
                val m = albumOf(a)
                m.takeIf {
                    io.github.thake.soundnest.parental.ParentalFilterEngine.isAlbumAllowed(
                        albumId = m.spotifyId,
                        name = m.name,
                        artists = m.artists,
                        artistIds = a.artists.map { ar -> ar.uri.substringAfterLast(':') },
                        policy = p,
                    )
                }
            }.filterNotNull(),
            artists = music.artists.items.map(::artistOf).filter { io.github.thake.soundnest.parental.ParentalFilterEngine.isArtistAllowed(it, p) },
            playlists = music.playlists.items.map(::playlistEntry).mapNotNull { io.github.thake.soundnest.parental.ParentalFilterEngine.filterLibraryEntry(it, p) },
        )
        emit(Response.Success(base))
        if (!p.allowPodcasts) return@flow
        val (shows, episodes) = coroutineScope {
            val s = async { runCatching { api.search.shows(query, limit = 12).items }.getOrDefault(emptyList()) }
            val e = async { runCatching { api.search.episodes(query, limit = 12).items }.getOrDefault(emptyList()) }
            s.await() to e.await()
        }
        val filteredShows = shows.map { PodcastModel(it.uri.substringAfterLast(':'), it.title, it.subtitle.orEmpty(), cover(it.images)) }
            .filter { io.github.thake.soundnest.parental.ParentalFilterEngine.isShowAllowed(it, p) }
        val filteredEpisodes = episodes.map(::episodeSong)
            .filter { io.github.thake.soundnest.parental.ParentalFilterEngine.isTrackAllowed(it, p) }
        if (filteredShows.isNotEmpty() || filteredEpisodes.isNotEmpty()) emit(Response.Success(base.copy(
            shows = filteredShows,
            episodes = filteredEpisodes,
        )))
    }

    // ── Podcasts ──

    /** A podcast's episodes, newest first, as playable songs (url = "episode:<id>"). */
    suspend fun getShowEpisodes(showId: String): Flow<Response<List<SongsModel>>> = flow {
        emit(Response.Loading())
        val p = policy()
        if (!p.allowPodcasts) { emit(Response.Success(emptyList())); return@flow }
        if (!ready()) { emit(Response.Error(notSignedIn())); return@flow }
        val uri = "spotify:show:$showId"
        val episodes = runCatching {
            val out = ArrayList<SongsModel>()
            var offset = 0
            repeat(10) {
                val page = api.podcasts.episodes(uri, offset, 50)
                out += page.items.map(::episodeSong).filter { io.github.thake.soundnest.parental.ParentalFilterEngine.isTrackAllowed(it, p, parentContextUri = uri) }
                emit(Response.Success(out.toList()))
                offset = page.nextOffset ?: return@runCatching out
            }
            out
        }
        if (episodes.exceptionOrNull() is CancellationException) throw episodes.exceptionOrNull()!!
        episodes.fold(
            onSuccess = { songs -> SongPlayer.registerContext(songs.map { it.url }, uri); emit(Response.Success(songs)) },
            onFailure = { Log.e("Api", "getShowEpisodes failed", it); emit(Response.Error(it.message ?: "error")) },
        )
    }

    /**
     * The show page: header and rows in one flow, so the screen has a single loading state.
     *
     * A podcast answers [io.github.thake.soundnest.api.Podcasts.episodes]; a book answers nothing there and
     * only has chapters, which is why its page used to show a cover and a title and nothing
     * else (B13).
     */
    suspend fun getShowPage(showId: String): Flow<Response<ShowPage>> = flow {
        emit(Response.Loading())
        val pol = policy()
        if (!pol.allowPodcasts && !pol.allowAudiobooks) { emit(Response.Error(context.getString(R.string.page_load_failed))); return@flow }
        if (!ready()) { emit(Response.Error(notSignedIn())); return@flow }
        val uri = "spotify:show:$showId"
        val head = if (pol.allowPodcasts) runCatching { api.podcasts.show(uri) }.getOrNull() else null
        val rows = ArrayList<SongsModel>()
        var failure: Throwable? = null
        var audiobook = false
        var sawPodcastEpisodes = false
        var book: io.github.thake.soundnest.api.Audiobook? = null

        if (pol.allowPodcasts) {
            try {
                var offset = 0
                loop@ for (page in 0 until MAX_EPISODE_PAGES) {
                    val p = api.podcasts.episodes(uri, offset, 50)
                    if (p.items.isEmpty()) break@loop
                    sawPodcastEpisodes = true
                    rows += p.items.map(::episodeSong).filter {
                        io.github.thake.soundnest.parental.ParentalFilterEngine.isTrackAllowed(it, pol, parentContextUri = uri, isAudiobookChapter = false)
                    }
                    val built = pageOf(showId, head, book, rows, audiobook = false)
                    if (!io.github.thake.soundnest.parental.ParentalFilterEngine.isShowAllowed(built.show, pol)) {
                        emit(Response.Error(context.getString(R.string.page_load_failed))); return@flow
                    }
                    emit(Response.Success(built))
                    offset = p.nextOffset ?: break@loop
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure = e
            }
        }

        if (!sawPodcastEpisodes && head == null && rows.isEmpty() && pol.allowAudiobooks) {
            // A book: its chapters come from queryBookChapters, not from the episodes query.
            audiobook = true
            book = runCatching { api.podcasts.audiobook(uri) }.getOrNull()
            try {
                var offset = 0
                loop@ for (page in 0 until MAX_EPISODE_PAGES) {
                    val p = api.podcasts.chapters(uri, offset, 50)
                    if (p.items.isEmpty()) break@loop
                    rows += p.items.map(::episodeSong).filter {
                        io.github.thake.soundnest.parental.ParentalFilterEngine.isTrackAllowed(it, pol, parentContextUri = uri, isAudiobookChapter = true)
                    }
                    val built = pageOf(showId, head, book, rows, audiobook = true)
                    if (!io.github.thake.soundnest.parental.ParentalFilterEngine.isShowAllowed(built.show, pol)) {
                        emit(Response.Error(context.getString(R.string.page_load_failed))); return@flow
                    }
                    emit(Response.Success(built))
                    offset = p.nextOffset ?: break@loop
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (rows.isEmpty()) failure = e
            }
        }

        if (rows.isEmpty() && failure != null) {
            Log.e("Api", "getShowPage failed", failure)
            emit(Response.Error(failure.message ?: "error"))
            return@flow
        }
        if ((audiobook && book == null && rows.isEmpty()) || (!audiobook && head == null && rows.isEmpty())) {
            emit(Response.Error(context.getString(R.string.page_load_failed)))
            return@flow
        }
        val finalPage = pageOf(showId, head, book, rows, audiobook)
        if (!io.github.thake.soundnest.parental.ParentalFilterEngine.isShowAllowed(finalPage.show, pol)) {
            emit(Response.Error(context.getString(R.string.page_load_failed)))
            return@flow
        }
        SongPlayer.registerContext(rows.map { it.url }, uri)
        emit(Response.Success(finalPage))
    }

    private fun pageOf(
        showId: String,
        head: io.github.thake.soundnest.api.Show?,
        book: io.github.thake.soundnest.api.Audiobook?,
        rows: List<SongsModel>,
        audiobook: Boolean,
    ): ShowPage {
        val cover = head?.let { big(it.images) }.orEmpty()
            .ifBlank { book?.images?.let(::big).orEmpty() }
            .ifBlank { rows.firstOrNull()?.coverUri.orEmpty() }
        val model = PodcastModel(
            id = showId,
            name = head?.name ?: book?.name ?: rows.firstOrNull()?.album.orEmpty(),
            publisher = head?.publisher?.takeIf { it.isNotBlank() }
                ?: book?.authors?.joinToString(", ").orEmpty(),
            coverUri = cover,
            description = head?.description ?: book?.description?.let { io.github.thake.soundnest.spotify.stripHtml(it) }.orEmpty(),
            rating = head?.rating ?: 0.0,
            saved = head?.saved == true,
            authors = book?.authors.orEmpty(),
            narrators = book?.narrators.orEmpty(),
            audiobook = audiobook,
        )
        return ShowPage(model, rows, audiobook, cover)
    }

    /** One episode (or chapter) by raw Spotify id, for the episode page. */
    suspend fun getEpisode(episodeId: String): SongsModel? {
        val pol = policy()
        if (!pol.allowPodcasts || !ready()) return null
        return runCatching { episodeSong(api.podcasts.episode("spotify:episode:$episodeId")) }
            .getOrNull()
            ?.takeIf { io.github.thake.soundnest.parental.ParentalFilterEngine.isTrackAllowed(it, pol) }
    }

    /**
     * Marks an episode finished, or not started. The page keeps this in its own
     * "markedasfinished" collection — the set behind its "Mark as finished" menu — so the
     * change follows the account like Spotify's own menu does.
     */
    suspend fun setEpisodePlayed(episodeUri: String, played: Boolean) {
        if (episodeUri.isBlank() || !io.github.thake.soundnest.parental.FeatureGates.canModifySpotifyLibrary(context)) return
        SoundNestRuntime.spotify.raw.service(
            "CollectionPlatformAPI",
            if (played) "add" else "remove",
            kotlinx.serialization.json.JsonPrimitive(MARKED_FINISHED),
            kotlinx.serialization.json.JsonArray(listOf(kotlinx.serialization.json.JsonPrimitive(episodeUri))),
        )
    }

    /** Raw uris the page has in its "markedasfinished" set (empty when it refuses). */
    suspend fun finishedEpisodes(): Set<String> {
        if (!ready()) return emptySet()
        // The page's get() throws on an empty set: that simply means nothing is marked.
        val raw = runCatching {
            SoundNestRuntime.spotify.raw.service(
                "CollectionPlatformAPI", "get", kotlinx.serialization.json.JsonPrimitive(MARKED_FINISHED),
            )
        }.getOrNull()
        return (raw as? kotlinx.serialization.json.JsonArray).orEmpty()
            .mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull }
            .toSet()
    }

    /** "Your Episodes": saved podcast episodes. */
    suspend fun getSavedEpisodes(): List<SongsModel> {
        val pol = policy()
        if (!pol.allowPodcasts || !ready()) return emptyList()
        return runCatching {
            api.podcasts.savedEpisodes(limit = 100).items.map(::episodeSong)
                .filter { io.github.thake.soundnest.parental.ParentalFilterEngine.isTrackAllowed(it, pol) }
        }.getOrDefault(emptyList())
    }

    /** Release date of a show's newest episode (one episode asked for), "" when unknown. */
    suspend fun latestEpisodeDate(showId: String): String {
        if (!policy().allowPodcasts || !ready()) return ""
        return runCatching { api.podcasts.episodes("spotify:show:$showId", 0, 1).items.firstOrNull()?.releaseDate.orEmpty() }.getOrDefault("")
    }

    // ── Radio and categories ──

    /** The song radio after [seedTrackIds]' last track: Spotify's own radio playlist. */
    suspend fun getRecommendations(seedTrackIds: List<String>): List<SongsModel> {
        val pol = policy()
        if (!pol.effectiveAllowAutoplayAndRadio) return emptyList()
        val seed = seedTrackIds.lastOrNull { it.isNotBlank() } ?: return emptyList()
        if (!ready()) return emptyList()
        val radio = runCatching { api.extras.radio("spotify:track:$seed") }.getOrNull() ?: return emptyList()
        return runCatching {
            api.playlists.tracks(radio, 0, 50).items.map(::songOf)
                .filter { it.spotifyTrackId != seed && io.github.thake.soundnest.parental.ParentalFilterEngine.isTrackAllowed(it, pol) }
        }.getOrDefault(emptyList())
    }

    /** The radio playlist id for a track or artist uri (for "Go to song radio"). */
    suspend fun radioPlaylistId(seedUri: String): String? =
        if (!policy().effectiveAllowAutoplayAndRadio || !ready()) null
        else runCatching { api.extras.radio(seedUri) }.getOrNull()?.substringAfterLast(':')

    /**
     * A browse category: Spotify's own category page when [genre] is a page/genre uri or id,
     * else playlists matching the name.
     */
    suspend fun getCategoryPlaylists(genre: String): Flow<Response<List<LibraryEntry>>> = flow {
        emit(Response.Loading())
        val pol = policy()
        if (genre.isBlank() || !pol.effectiveAllowAutoplayAndRadio) { emit(Response.Success(emptyList())); return@flow }
        if (!ready()) { emit(Response.Error(notSignedIn())); return@flow }
        val pageUri = when {
            genre.startsWith("spotify:page:") || genre.startsWith("spotify:genre:") -> genre
            else -> runCatching { api.home.categories() }.getOrNull()?.firstOrNull { it.title.equals(genre, ignoreCase = true) }?.uri
        }
        val entries = runCatching {
            val raw = if (pageUri != null) {
                // Shows, episodes and books too: the Podcasts and Documentaries pages hold little else
                // and showed empty (reported). Each opens by the kind its uri says (categoryEntryRoute).
                api.home.category(pageUri).shelves.flatMap { it.items }.distinctBy { it.uri }.mapNotNull { c ->
                    if (c.kind !in CATEGORY_KINDS) return@mapNotNull null
                    LibraryEntry(c.uri.substringAfterLast(':'), c.title, c.subtitle.orEmpty(), cover(c.images), isPlaylist = c.kind == MediaKind.PLAYLIST, uri = c.uri)
                }
            } else api.search.playlists(genre, limit = 24).items.map(::playlistEntry)
            raw.mapNotNull { io.github.thake.soundnest.parental.ParentalFilterEngine.filterLibraryEntry(it, pol) }
        }
        entries.fold(
            onSuccess = { emit(Response.Success(it)) },
            onFailure = { Log.e("Api", "getCategoryPlaylists failed", it); emit(Response.Error(it.message ?: "error")) },
        )
    }

    /** What a browse category page lists: the cards a tap can open. */
    private val CATEGORY_KINDS = setOf(MediaKind.PLAYLIST, MediaKind.ALBUM, MediaKind.SHOW, MediaKind.EPISODE, MediaKind.AUDIOBOOK, MediaKind.CHAPTER)

    // ── Artists ──

    private suspend fun findArtist(name: String): Artist? {
        val hits = api.search.artists(name, limit = 5).items
        return hits.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: hits.firstOrNull()
    }

    private fun filterArtistOverview(model: ArtistOverviewModel, pol: io.github.thake.soundnest.parental.ParentalPolicy, uri: String): ArtistOverviewModel? {
        if (!io.github.thake.soundnest.parental.ParentalFilterEngine.isArtistAllowed(model.id, model.name, model.biography.orEmpty(), pol)) return null
        return model.copy(
            topTracks = model.topTracks.filter { io.github.thake.soundnest.parental.ParentalFilterEngine.isTrackAllowed(it.song, pol, parentContextUri = uri) },
            popularReleases = model.popularReleases.filter { io.github.thake.soundnest.parental.ParentalFilterEngine.isAlbumAllowed(it, pol) },
            appearsOn = model.appearsOn.filter { io.github.thake.soundnest.parental.ParentalFilterEngine.isAlbumAllowed(it, pol) },
            relatedArtists = model.relatedArtists.filter { io.github.thake.soundnest.parental.ParentalFilterEngine.isArtistAllowed(it, pol) },
        )
    }

    /** The full artist page; [knownArtistId] avoids the fuzzy name lookup. */
    suspend fun getArtistOverview(artistName: String, knownArtistId: String = ""): Flow<Response<ArtistOverviewModel>> = flow {
        emit(Response.Loading())
        val pol = policy()
        if (artistName.isBlank() && knownArtistId.isBlank()) { emit(Response.Success(ArtistOverviewModel(name = artistName))); return@flow }
        if (!ready()) { emit(Response.Error(notSignedIn())); return@flow }
        val uri = when {
            knownArtistId.contains(':') -> knownArtistId
            knownArtistId.isNotBlank() -> "spotify:artist:$knownArtistId"
            else -> runCatching { findArtist(artistName) }.getOrNull()?.uri
        }
        if (uri == null) { emit(Response.Error(context.getString(R.string.artist_not_found))); return@flow }
        // Shown again within a few minutes (back, the releases page, another tap): from memory,
        // without asking Spotify for the page and the whole discography once more.
        ArtistCache.map[uri]?.let { (at, cached) ->
            val filtered = filterArtistOverview(cached, pol, uri)
            if (filtered == null) { emit(Response.Error(context.getString(R.string.artist_not_found))); return@flow }
            emit(Response.Success(filtered))
            if (System.currentTimeMillis() - at < ArtistCache.FRESH_MS) return@flow
        }
        val firstReleases = kotlinx.coroutines.coroutineScope {
            // The discography's first page goes out with the page itself, not after it.
            val releases = async { runCatching { api.catalog.discography(uri, 0, 50) }.getOrNull() }
            val page = runCatching { api.catalog.artistPage(uri) }.getOrElse {
                releases.cancel()
                Log.e("Api", "getArtistOverview failed", it)
                return@coroutineScope null
            }
            page to releases
        } ?: run {
            if (ArtistCache.map[uri] == null) emit(Response.Error(context.getString(R.string.page_load_failed)))
            return@flow
        }
        val page = firstReleases.first
        val avatar = big(page.artist.images)
        val top = page.topTracks.map { t -> songOf(t.track).let { if (it.coverUri.isBlank()) it.copy(coverUri = avatar) else it } to t.playcount }
        val rawModel = ArtistOverviewModel(
            id = idOf(uri),
            name = page.artist.name.ifBlank { artistName },
            verified = page.artist.verified,
            monthlyListeners = page.artist.monthlyListeners,
            biography = page.biography,
            headerImage = big(page.headerImages).ifBlank { avatar },
            avatarImage = avatar,
            topTracks = top.map { (song, plays) -> ArtistTrackUi(song, plays) },
            // Newest first, by year at this point (a popular release may carry its year alone).
            popularReleases = io.github.thake.soundnest.api.Releases.newestFirst(page.popularReleases, emptyList()).map(::albumOf),
            appearsOn = page.appearsOn.map(::albumOf),
            relatedArtists = page.related.map(::artistOf),
        )
        ArtistCache.map[uri] = System.currentTimeMillis() to rawModel
        val filtered = filterArtistOverview(rawModel, pol, uri) ?: run {
            emit(Response.Error(context.getString(R.string.artist_not_found)))
            return@flow
        }
        SongPlayer.registerContext(filtered.topTracks.map { it.song.url }, uri)
        emit(Response.Success(filtered))
        // The overview carries only part of the discography: the whole list follows.
        // Every page of it (big artists have hundreds of singles), up to a sane cap.
        val all = runCatching {
            val out = ArrayList<io.github.thake.soundnest.api.Album>()
            val first = firstReleases.second.await()
            var offset: Int? = if (first != null) { out += first.items; first.nextOffset.takeIf { first.items.isNotEmpty() } } else 0
            while (offset != null && out.size < DISCOGRAPHY_MAX) {
                val next = api.catalog.discography(uri, offset, 50)
                if (next.items.isEmpty()) break
                out += next.items
                offset = next.nextOffset
            }
            out
        }.getOrNull().orEmpty()
        // Shown again when the discography adds releases or puts them in their real order (its dates are full ones).
        val merged = io.github.thake.soundnest.api.Releases.newestFirst(page.popularReleases, all).map(::albumOf)
        if (merged != rawModel.popularReleases) {
            val fullRaw = rawModel.copy(popularReleases = merged)
            ArtistCache.map[uri] = System.currentTimeMillis() to fullRaw
            filterArtistOverview(fullRaw, pol, uri)?.let { emit(Response.Success(it)) }
        }
    }

    /**
     * The songs of playlists read in full lately, by playlist uri (a few, small ones: a long list
     * stays on the network). Dropped by an edit made from the app, see [invalidatePlaylist].
     */
    private object PlaylistFresh {
        const val FRESH_MS = 5 * 60_000L
        const val MAX_TRACKS = 1_000
        private const val KEEP = 6
        private val map = object : LinkedHashMap<String, Pair<Long, List<SongsModel>>>(8, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, List<SongsModel>>>) = size > KEEP
        }

        @Synchronized fun get(uri: String): List<SongsModel>? =
            map[uri]?.takeIf { System.currentTimeMillis() - it.first < FRESH_MS }?.second

        @Synchronized fun put(uri: String, songs: List<SongsModel>) {
            if (songs.isEmpty() || songs.size > MAX_TRACKS) map.remove(uri) else map[uri] = System.currentTimeMillis() to songs
        }

        @Synchronized fun drop(uri: String?) { if (uri == null) map.clear() else map.remove(uri) }
    }

    /** Artist pages shown lately, by artist uri, with when they were loaded. */
    private object ArtistCache {
        const val FRESH_MS = 5 * 60_000L
        val map = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, ArtistOverviewModel>>()
    }

    // ── Albums and playlists ──

    private fun dateLabel(iso: String?): String {
        if (iso.isNullOrBlank()) return ""
        return runCatching {
            val d = java.time.LocalDate.parse(iso.take(10))
            d.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.getDefault()))
        }.getOrDefault(iso.take(4))
    }

    private fun durationLabel(totalMs: Long): String {
        val min = totalMs / 60000
        return if (min >= 60) "${min / 60} h ${min % 60} min" else "$min min"
    }

    /** Album id for a name (+ artist) when the caller only knows the name. */
    suspend fun resolveAlbumId(albumName: String, artist: String = ""): String? {
        if (albumName.isBlank() || !ready()) return null
        val hits = runCatching { api.search.albums(if (artist.isBlank()) albumName else "$albumName $artist", limit = 10).items }.getOrNull().orEmpty()
        return pickAlbum(hits, albumName, artist)?.uri?.let(::idOf)
    }

    /**
     * The user's liked songs by one artist, as their own page (Spotify's "Liked Songs" row on an
     * artist page). They play inside Liked Songs, in this list's order, so only they play.
     */
    suspend fun getLikedByArtistPage(artistId: String, artistName: String): Flow<Response<ContextPageModel>> = flow {
        emit(Response.Loading())
        val pol = policy()
        if (!io.github.thake.soundnest.parental.ParentalFilterEngine.isArtistAllowed(artistId, artistName, policy = pol)) {
            emit(Response.Error(context.getString(R.string.artist_not_found))); return@flow
        }
        val all = LikedCache.tracks ?: LikedCache.loadOnce().await()
            ?: run { emit(Response.Error(context.getString(R.string.page_load_failed))); return@flow }
        val songs = all.filter { s ->
            val byArtist = if (s.artistIds.isNotEmpty()) artistId in s.artistIds
            else artistName.isNotBlank() && s.singer.split(", ").any { it.equals(artistName, ignoreCase = true) }
            byArtist && io.github.thake.soundnest.parental.ParentalFilterEngine.isTrackAllowed(s, pol)
        }
        emit(Response.Success(ContextPageModel(
            uri = "spotify:collection:tracks",
            type = "liked-artist",
            name = context.getString(R.string.artist_liked_songs),
            coverUri = "",
            ownerName = ProfileCache.name.orEmpty(),
            ownerAvatar = ProfileCache.imageUrl.orEmpty(),
            metaLine = context.resources.getQuantityString(R.plurals.artist_liked_songs_count, songs.size, songs.size, artistName),
            tracks = songs,
            complete = true,
        )))
    }

    /**
     * Liked Songs as a page: cached list shown instantly, then refreshed. A list read in full less
     * than [LIKED_FULL_MS] ago only has its newest page read again (the hearts of this account
     * and a like or unlike anywhere else land there); after that, with no list on storage, or
     * with [full] (a pull-to-refresh), every page is read.
     */
    suspend fun getLikedPage(full: Boolean = false): Flow<Response<ContextPageModel>> = flow {
        val pol = policy()
        fun page(tracks: List<SongsModel>, complete: Boolean): ContextPageModel {
            val allowed = tracks.filter { io.github.thake.soundnest.parental.ParentalFilterEngine.isTrackAllowed(it, pol) }
            SongPlayer.registerLikedSongs(allowed.map { it.url })
            return ContextPageModel(
                uri = "spotify:collection:tracks",
                type = "liked",
                name = context.getString(R.string.collection_liked_songs_title),
                coverUri = "",
                ownerName = ProfileCache.name.orEmpty(),
                ownerAvatar = ProfileCache.imageUrl.orEmpty(),
                metaLine = context.resources.getQuantityString(R.plurals.liked_songs_header_metadata_text, allowed.size, allowed.size),
                tracks = allowed,
                complete = complete,
            )
        }
        if (LikedCache.tracks == null) LikedCache.tracks = withContext(Dispatchers.IO) { ScreenCache.liked.read() }
        LikedCache.tracks?.let { emit(Response.Success(page(it, true))) } ?: emit(Response.Loading())
        if (!ready()) { if (LikedCache.tracks == null) emit(Response.Error(notSignedIn())); return@flow }
        val known = LikedCache.tracks
        if (!full && known != null) {
            val at = withContext(Dispatchers.IO) { ScreenCache.likedFullAt() }
            if (at != null && System.currentTimeMillis() - at < LIKED_FULL_MS) {
                val head = runCatching { likedHead() }.getOrElse { if (it is CancellationException) throw it; null }
                // The list shown stays when Spotify cannot be reached.
                if (head == null) return@flow
                // A head that does not reach the list known (more than a page of new likes) cannot be merged.
                if (LikedCache.reaches(head, known)) {
                    val merged = LikedCache.merge(head, known)
                    LikedCache.tracks = merged
                    ScreenCache.liked.write(merged)
                    emit(Response.Success(page(merged, true)))
                    return@flow
                }
            }
        }
        val first = LikedCache.tracks == null
        // Cancellation must escape: emit() throws AbortFlowException (a CancellationException)
        // when the collector stops early (first { … }); catching it and emitting again
        // violates flow exception transparency and breaks Android Auto's firstResult.
        val tracks = runCatching { allLiked { if (first) emit(Response.Success(page(it, false))) } }.getOrElse {
            if (it is CancellationException) throw it
            Log.e("Api", "getLikedPage failed", it)
            if (LikedCache.tracks == null) emit(Response.Error(context.getString(R.string.couldnt_load, context.getString(R.string.collection_liked_songs_title))))
            return@flow
        }
        LikedCache.tracks = tracks
        ScreenCache.liked.write(tracks)
        ScreenCache.markLikedFull()
        emit(Response.Success(page(tracks, true)))
    }

    /**
     * After a like / unlike: the newest Liked Songs (one page) put on top of what is
     * known, so the list is right at once without loading every page again.
     */
    suspend fun mergeLikedHead(): List<SongsModel>? = runCatching {
        if (!ready()) return null
        val pol = policy()
        val head = likedHead()
        val merged = LikedCache.merge(head, LikedCache.tracks.orEmpty())
        LikedCache.tracks = merged
        withContext(Dispatchers.IO) { ScreenCache.liked.write(merged) }
        val allowed = merged.filter { io.github.thake.soundnest.parental.ParentalFilterEngine.isTrackAllowed(it, pol) }
        SongPlayer.registerLikedSongs(allowed.map { it.url })
        allowed
    }.getOrNull()

    private val LIKED_PAGE = 50

    /** The newest Liked Songs: one page of 50. */
    private suspend fun likedHead(): List<SongsModel> =
        api.library.likedTracks(0, 50).items.map { songOf(it.track).copy(addedAt = it.addedAt.orEmpty()) }

    private suspend fun allLiked(onPage: suspend (List<SongsModel>) -> Unit): List<SongsModel> {
        fun songs(p: io.github.thake.soundnest.api.Page<io.github.thake.soundnest.api.SavedTrack>) = p.items.map { songOf(it.track).copy(addedAt = it.addedAt.orEmpty()) }
        val first = api.library.likedTracks(0, LIKED_PAGE)
        return PagedFetch.inOrder(songs(first), first.total, LIKED_PAGE, fetch = { o -> songs(api.library.likedTracks(o, LIKED_PAGE)) }, onBatch = onPage)
    }

    /**
     * An album as a page: the copy on storage (opened before) at once, then the album and its
     * songs from Spotify, and the artist's avatar a moment after in a second emit. Without
     * [stored] the copy on storage is left out (a sync that must see the album as it is now).
     */
    /** The release date in full, then the label when no copyright line names it, then the copyright lines. */
    private fun albumFooter(album: Album): List<String> {
        val date = album.releaseDate?.takeIf { it.length >= 10 }?.let { iso ->
            runCatching {
                java.time.LocalDate.parse(iso.take(10)).format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.LONG))
            }.getOrNull()
        }
        val label = album.label?.trim()?.takeIf { l -> l.isNotEmpty() && album.copyrights.none { it.contains(l, ignoreCase = true) } }
        return listOfNotNull(date, label?.let { context.getString(R.string.album_label, it) }) + album.copyrights
    }

    private fun filterAlbumPage(raw: ContextPageModel, pol: io.github.thake.soundnest.parental.ParentalPolicy): ContextPageModel? {
        val albumId = raw.uri.substringAfterLast(':')
        val artistIds = listOfNotNull(raw.ownerUri.takeIf { it.isNotBlank() }?.substringAfterLast(':'))
        if (!io.github.thake.soundnest.parental.ParentalFilterEngine.isAlbumAllowed(
                albumId = albumId,
                name = raw.name,
                artists = raw.ownerName,
                artistIds = artistIds,
                policy = pol,
            )
        ) return null
        val allowedTracks = raw.tracks.filter { io.github.thake.soundnest.parental.ParentalFilterEngine.isTrackAllowed(it, pol, parentContextUri = raw.uri) }
        return raw.copy(tracks = allowedTracks, explicit = allowedTracks.any { it.explicit })
    }

    suspend fun getAlbumPage(asked: String, stored: Boolean = true): Flow<Response<ContextPageModel>> = flow {
        emit(Response.Loading())
        val pol = policy()
        val albumId = asked.substringAfterLast(':')
        val uri = "spotify:album:$albumId"
        val saved = if (stored) withContext(Dispatchers.IO) { ScreenCache.albumPage(albumId).read() } else null
        if (saved != null) {
            val filteredSaved = filterAlbumPage(saved, pol)
            if (filteredSaved != null) {
                SongPlayer.registerContext(filteredSaved.tracks.map { it.url }, uri)
                emit(Response.Success(filteredSaved))
            }
        }
        if (!ready()) { if (saved == null) emit(Response.Error(notSignedIn())); return@flow }
        val (album, tracks) = runCatching {
            coroutineScope {
                val a = async { api.catalog.album(uri) }
                val t = async {
                    val out = ArrayList<Track>()
                    var offset = 0
                    while (true) {
                        val p = api.catalog.albumTracks(uri, offset, 50)
                        out += p.items
                        offset = p.nextOffset ?: break
                    }
                    out
                }
                a.await() to t.await()
            }
        }.getOrElse { if (saved == null) emit(Response.Error(context.getString(R.string.album_not_found))); return@flow }
        val artistIds = album.artists.map { it.uri.substringAfterLast(':') }
        if (!io.github.thake.soundnest.parental.ParentalFilterEngine.isAlbumAllowed(
                albumId = albumId,
                name = album.name,
                artists = album.artists.joinToString(", ") { it.name },
                artistIds = artistIds,
                policy = pol,
            )
        ) {
            emit(Response.Error(context.getString(R.string.album_not_found)))
            return@flow
        }
        val rawSongs = tracks.map { songOf(it).copy(album = album.name, coverUri = cover(album.images), albumId = albumId) }
        val allowedSongs = rawSongs.filter { io.github.thake.soundnest.parental.ParentalFilterEngine.isTrackAllowed(it, pol, parentContextUri = uri) }
        SongPlayer.registerContext(allowedSongs.map { it.url }, uri)
        val type = context.getString(when (album.type) {
            "SINGLE" -> R.string.album_type_single; "EP" -> R.string.album_type_ep
            "COMPILATION" -> R.string.album_type_compilation; else -> R.string.album_type_album
        })
        val firstArtist = album.artists.firstOrNull()
        val rawPage = ContextPageModel(
            uri = uri,
            type = "album",
            name = album.name,
            coverUri = big(album.images),
            ownerName = album.artists.joinToString(", ") { it.name },
            // The avatar of the copy on storage (same artist) until the new one is known.
            ownerAvatar = saved?.takeIf { it.ownerUri == firstArtist?.uri.orEmpty() }?.ownerAvatar.orEmpty(),
            ownerUri = firstArtist?.uri.orEmpty(),
            metaLine = listOf(type, dateLabel(album.releaseDate)).filter { it.isNotBlank() }.joinToString(" • "),
            explicit = rawSongs.any { it.explicit },
            tracks = rawSongs,
            footer = albumFooter(album),
        )
        // Written before the first emit: a collector that only wants the first page stops the flow there.
        ScreenCache.albumPage(albumId).write(rawPage)
        ScreenCache.pruneAlbumPages(ALBUM_PAGES_KEPT)
        val page = rawPage.copy(tracks = allowedSongs, explicit = allowedSongs.any { it.explicit })
        emit(Response.Success(page))
        val avatar = firstArtist?.let { runCatching { api.catalog.artist(it.uri) }.getOrNull() }?.images?.let(::cover).orEmpty()
        if (avatar.isNotBlank() && avatar != page.ownerAvatar) {
            ScreenCache.albumPage(albumId).write(rawPage.copy(ownerAvatar = avatar))
            emit(Response.Success(page.copy(ownerAvatar = avatar)))
        }
    }

    /**
     * A playlist as a page. Its details are always asked for; its songs are kept in memory for
     * [PlaylistFresh.FRESH_MS] after a full read and reused when the playlist still has as many
     * songs, so reopening it does not read every page again. An edit made here drops them
     * ([invalidatePlaylist]); [force] (a reload) reads everything.
     */
    suspend fun getPlaylistPage(playlistId: String, force: Boolean = false): Flow<Response<ContextPageModel>> = flow {
        emit(Response.Loading())
        val pol = policy()
        if (!ready()) { emit(Response.Error(notSignedIn())); return@flow }
        val uri = "spotify:playlist:$playlistId"
        val fresh = if (force) null else PlaylistFresh.get(uri)
        // The first songs are asked for together with the playlist's details, not after them.
        val flowScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.currentCoroutineContext())
        fun startFirstPage() = flowScope.async(Dispatchers.IO) {
            runCatching { api.playlists.tracks(uri, 0, 100) }
        }
        var firstPage = if (fresh == null) startFirstPage() else null
        val pl = runCatching { api.playlists.playlist(uri) }.getOrElse {
            firstPage?.cancel()
            emit(Response.Error(context.getString(R.string.playlist_not_found))); return@flow
        }
        val strippedDesc = stripHtml(pl.description)
        if (!io.github.thake.soundnest.parental.ParentalFilterEngine.isPlaylistAllowed(
                playlistId = playlistId,
                name = pl.name,
                subtitleOrOwner = pl.owner.orEmpty(),
                description = strippedDesc,
                policy = pol,
            )
        ) {
            firstPage?.cancel()
            emit(Response.Error(context.getString(R.string.playlist_not_found)))
            return@flow
        }
        if (fresh != null && fresh.size != pl.trackCount) firstPage = startFirstPage()
        val saves = pl.followers?.takeIf { it > 0 }?.let {
            context.resources.getQuantityString(R.plurals.profile_list_item_saves_count, it.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), it)
        }.orEmpty()
        val header = ContextPageModel(
            uri = uri,
            type = "playlist",
            name = pl.name,
            coverUri = big(pl.images),
            description = strippedDesc,
            ownerName = pl.owner.orEmpty(),
            ownerAvatar = cover(pl.ownerImages),
            ownerUri = pl.ownerUri.orEmpty(),
            metaLine = saves,
            canLeave = pl.canLeave && io.github.thake.soundnest.parental.FeatureGates.canModifySpotifyLibrary(context),
        )
        // The first page gives the total: the rest is asked for PLAYLIST_PARALLEL pages at a time
        // and shown in order as it lands, at most every PLAYLIST_EMIT_MS. One page after the
        // other, each followed by the whole list shown and registered again, a 6000-song
        // playlist took about two minutes (tester report).
        val songs = ArrayList<SongsModel>()
        var failed = false
        fun publish(done: Boolean): Response<ContextPageModel> {
            // Kept before the emit: a collector that stops at the complete page ends the flow there.
            if (done && !failed) PlaylistFresh.put(uri, songs.toList())
            val allowedSongs = songs.filter { io.github.thake.soundnest.parental.ParentalFilterEngine.isTrackAllowed(it, pol, parentContextUri = uri) }
            SongPlayer.registerContext(allowedSongs.map { it.url }, uri)
            val total = allowedSongs.sumOf { it.durationMs.toLong() }
            // The song count rides along with the duration ("1 h 2 min / 12 songs") so the two
            // numbers that grow together as pages arrive stay next to each other.
            val durationAndCount = if (allowedSongs.isNotEmpty()) {
                val count = context.resources.getQuantityString(R.plurals.liked_songs_header_metadata_text, allowedSongs.size, allowedSongs.size)
                if (total > 0) "${durationLabel(total)} / $count" else count
            } else ""
            val meta = listOf(saves, durationAndCount).filter { it.isNotBlank() }.joinToString(" • ")
            // A playlist without a cover of its own shows the mosaic of its songs' albums, as in the official app.
            val cover = header.coverUri.ifBlank { io.github.thake.soundnest.ui.components.mosaicCoverFor(allowedSongs.take(200).map { it.coverUri }) }
            return Response.Success(header.copy(coverUri = cover, metaLine = meta, tracks = allowedSongs, complete = done))
        }
        if (firstPage == null) {
            songs += fresh!!
            emit(publish(done = true))
            return@flow
        }
        val first = firstPage.await().getOrElse { failed = true; null }
        if (first != null) {
            songs += withDeviceFiles(first.items.map(::songOf))
            val offsets = generateSequence(first.nextOffset) { (it + PLAYLIST_PAGE).takeIf { n -> n < first.total } }.toList()
            emit(publish(done = offsets.isEmpty()))
            if (offsets.isNotEmpty()) {
                val gate = kotlinx.coroutines.sync.Semaphore(PLAYLIST_PARALLEL)
                coroutineScope {
                    val jobs = offsets.map { o -> async(Dispatchers.IO) { gate.withPermit { runCatching { api.playlists.tracks(uri, o, PLAYLIST_PAGE) }.getOrNull() } } }
                    var lastEmit = System.currentTimeMillis()
                    for ((i, job) in jobs.withIndex()) {
                        val page = job.await()
                        if (page == null) { failed = true; jobs.drop(i + 1).forEach { it.cancel() }; break }
                        songs += withDeviceFiles(page.items.map(::songOf))
                        val last = i == jobs.lastIndex
                        if (last || System.currentTimeMillis() - lastEmit >= PLAYLIST_EMIT_MS) {
                            emit(publish(done = last)); lastEmit = System.currentTimeMillis()
                        }
                    }
                }
                // A page that failed: what came before it stays, marked complete so nothing waits.
                if (failed && songs.isNotEmpty()) emit(publish(done = true))
            }
        }
        if (failed && songs.isEmpty()) emit(Response.Error(context.getString(R.string.couldnt_load, pl.name)))
    }

    // ── Library ──

    private fun entryOf(item: io.github.thake.soundnest.api.LibraryItem): LibraryEntry? = when (item.kind) {
        MediaKind.FOLDER -> LibraryEntry(
            spotifyId = item.uri, name = item.name,
            subtitle = context.getString(R.string.create_menu_item_folder_title) + " • " +
                context.resources.getQuantityString(R.plurals.add_to_playlist_folder_subtitle_playlists_count, item.count ?: 0, (item.count ?: 0).toString()),
            coverUri = "", isPlaylist = false, folderUri = item.uri, childCount = item.count ?: 0,
            uri = item.uri, pinned = item.pinned, addedAt = item.addedAt.orEmpty(),
        )
        MediaKind.PLAYLIST -> LibraryEntry(item.uri.substringAfterLast(':'), item.name,
            context.getString(R.string.assisted_curation_search_playlist) + (item.subtitle?.let { " • $it" } ?: ""), cover(item.images), isPlaylist = true,
            uri = item.uri, pinned = item.pinned, addedAt = item.addedAt.orEmpty(), ownerUri = item.ownerUri.orEmpty())
        MediaKind.ALBUM -> LibraryEntry(item.uri.substringAfterLast(':'), item.name,
            context.getString(R.string.album_type_album) + (item.subtitle?.let { " • $it" } ?: ""), cover(item.images), isPlaylist = false, artists = item.subtitle.orEmpty(),
            uri = item.uri, pinned = item.pinned, addedAt = item.addedAt.orEmpty())
        MediaKind.COLLECTION -> if (item.uri == "spotify:collection:tracks") null
            else LibraryEntry(EPISODES_ID, item.name, "Podcast", cover(item.images), isPlaylist = true)
        else -> null
    }

    private suspend fun libraryLevel(folderUri: String?, filter: LibraryFilter = LibraryFilter.ALL): List<io.github.thake.soundnest.api.LibraryItem> {
        val first = api.library.items(filter = filter, folderUri = folderUri, offset = 0, limit = 50)
        // 40 pages at most, as before (2,000 items); the rest four at a time instead of one by one.
        return PagedFetch.inOrder(first.items, minOf(first.total, 40 * 50), 50,
            fetch = { o -> api.library.items(filter = filter, folderUri = folderUri, offset = o, limit = 50).items })
    }

    private fun filterLibraryEntries(entries: List<LibraryEntry>, pol: io.github.thake.soundnest.parental.ParentalPolicy = policy()): List<LibraryEntry> =
        entries.mapNotNull { io.github.thake.soundnest.parental.ParentalFilterEngine.filterLibraryEntry(it, pol) }

    /** Flat library (Android Auto, pickers): Liked Songs, playlists, then albums. */
    suspend fun getLibrary(): Flow<Response<List<LibraryEntry>>> = flow {
        val pol = policy()
        HomeCache.library?.let { emit(Response.Success(filterLibraryEntries(it, pol))) } ?: emit(Response.Loading())
        if (!ready()) { if (HomeCache.library == null) emit(Response.Error(notSignedIn())); return@flow }
        runCatching {
            val playlists = api.playlists.mine().map(::playlistEntry)
            val albums = libraryLevel(null, LibraryFilter.ALBUMS).mapNotNull(::entryOf).filter { !it.isPlaylist }
            listOf(likedEntry()) + playlists + albums
        }.fold(
            onSuccess = { HomeCache.library = it; emit(Response.Success(filterLibraryEntries(it, pol))) },
            onFailure = { Log.e("Api", "getLibrary failed", it); if (HomeCache.library == null) emit(Response.Error(it.message ?: "error")) },
        )
    }

    /** "Your Library" as Spotify shows it: Liked Songs, then folders, playlists and albums in the user's order. */
    suspend fun getLibraryRoot(): Flow<Response<List<LibraryEntry>>> = flow {
        val pol = policy()
        if (HomeCache.libraryRoot == null) HomeCache.libraryRoot = withContext(Dispatchers.IO) { ScreenCache.libraryRoot.read() }
        HomeCache.libraryRoot?.let { emit(Response.Success(filterLibraryEntries(it, pol))) } ?: emit(Response.Loading())
        if (!ready()) { if (HomeCache.libraryRoot == null) emit(Response.Error(notSignedIn())); return@flow }
        runCatching { listOf(likedEntry()) + libraryLevel(null).mapNotNull(::entryOf) }.fold(
            onSuccess = {
                HomeCache.libraryRoot = it
                ScreenCache.libraryRoot.write(it)
                val filtered = filterLibraryEntries(it, pol)
                PagePrefetch.schedule(filtered)
                emit(Response.Success(filtered))
            },
            onFailure = { Log.e("Api", "getLibraryRoot failed", it); if (HomeCache.libraryRoot == null) emit(Response.Error(it.message ?: "error")) },
        )
    }

    /** The folders and playlists inside one library folder. */
    suspend fun getLibraryFolder(folderUri: String): Flow<Response<List<LibraryEntry>>> = flow {
        val pol = policy()
        HomeCache.folders[folderUri]?.let { emit(Response.Success(filterLibraryEntries(it, pol))) } ?: emit(Response.Loading())
        if (!ready()) { if (HomeCache.folders[folderUri] == null) emit(Response.Error(notSignedIn())); return@flow }
        runCatching { libraryLevel(folderUri).mapNotNull(::entryOf) }.fold(
            onSuccess = { HomeCache.folders[folderUri] = it; emit(Response.Success(filterLibraryEntries(it, pol))) },
            onFailure = { Log.e("Api", "getLibraryFolder failed", it); if (HomeCache.folders[folderUri] == null) emit(Response.Error(it.message ?: "error")) },
        )
    }

    /** The signed-in account (name, avatar, plan). Spotify does not give the e-mail to the Web Player. */
    suspend fun getAccount(): Flow<Response<io.github.thake.soundnest.data.entity.AccountModel>> = flow {
        emit(Response.Loading())
        if (!ready()) { emit(Response.Error(notSignedIn())); return@flow }
        runCatching { api.users.me() }.fold(
            onSuccess = { u ->
                emit(Response.Success(io.github.thake.soundnest.data.entity.AccountModel(
                    name = u.name, email = "", imageUrl = cover(u.images), plan = if (u.premium) "Premium" else "Free",
                )))
            },
            onFailure = { Log.e("Api", "getAccount failed", it); emit(Response.Error(it.message ?: "error")) },
        )
    }

    /** Playlist descriptions come as HTML: keep the text and link labels. */
    private fun stripHtml(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return androidx.core.text.HtmlCompat.fromHtml(raw, androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()
    }

    /** Among same-named albums, prefer the exact name by the requested artist. */
    private fun pickAlbum(candidates: List<Album>, albumName: String, artist: String): Album? {
        if (candidates.isEmpty()) return null
        if (artist.isBlank()) return candidates.firstOrNull { it.name.equals(albumName, ignoreCase = true) } ?: candidates.first()
        val want = artist.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }
        fun artistMatches(a: Album) = a.artists.joinToString(" ") { it.name }.lowercase().let { names -> want.any { names.contains(it) } }
        val nameMatches = candidates.filter { it.name.equals(albumName, ignoreCase = true) }
        return nameMatches.firstOrNull(::artistMatches) ?: candidates.firstOrNull(::artistMatches) ?: nameMatches.firstOrNull() ?: candidates.first()
    }
}

/** Songs per playlist page, pages asked for at once, and the most often a growing list is shown again. */
private const val PLAYLIST_PAGE = 100
private const val PLAYLIST_PARALLEL = 4
private const val PLAYLIST_EMIT_MS = 350L
/** A Liked Songs list read in full is trusted this long: only its newest page is read again. */
private const val LIKED_FULL_MS = 24 * 3_600_000L
/** Album pages kept on storage. */
private const val ALBUM_PAGES_KEPT = 30
