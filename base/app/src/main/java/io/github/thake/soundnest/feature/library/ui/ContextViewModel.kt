package io.github.thake.soundnest.feature.library.ui

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.catalog.Api
import io.github.thake.soundnest.core.catalog.EditablePlaylists
import io.github.thake.soundnest.core.catalog.Response
import io.github.thake.soundnest.core.model.ContextPageModel
import io.github.thake.soundnest.core.model.SongsModel
import io.github.thake.soundnest.core.playback.CurrentSongState
import io.github.thake.soundnest.core.di.Graph
import io.github.thake.soundnest.core.playback.SongPlayer
import io.github.thake.soundnest.host.android.SoundNestRuntime
import io.github.thake.soundnest.core.ui.look.wholeList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import io.github.thake.soundnest.feature.library.settings.TrackOrder
import io.github.thake.soundnest.feature.library.settings.TrackSort

private const val RECS_COUNT = 10

/** Album / playlist page, loaded by Spotify id and laid out like the official app. */
class ContextViewModel(
    private val api: Api = Graph.api,
    private val state: CurrentSongState = Graph.currentSongState,
) : ViewModel() {
    companion object {
        @Volatile
        private var cachedPolicyRevision = -1L
        // Last pages shown, process-wide (back / forward / reopen are instant).
        private val recent = object : java.util.LinkedHashMap<String, Response<ContextPageModel>>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Response<ContextPageModel>>?) = size > 12
        }.let { java.util.Collections.synchronizedMap(it) }

        /** Signed out: the pages shown belong to the old account. */
        fun clearCache() { recent.clear(); inflight.values.forEach { it.cancel() }; inflight.clear() }

        // Look-ups started by a touch (see pressHint), joined by the page when it opens.
        private val inflight = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Deferred<Unit>>()
        private val prefetchScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)

        /** A playlist up to this long is prefetched whole, longer ones by their first page. */
        private const val WHOLE_PLAYLIST_MAX = 200

        /** Pages likely to be opened next (top of the library, home shortcuts), loaded quietly. */
        fun prefetchSoon(pages: List<Pair<String, String>>) {
            prefetchScope.launch {
                kotlinx.coroutines.delay(1_000)
                // One after the other: they must not slow down what is on screen.
                for ((type, id) in pages) {
                    prefetch(type, id)
                    inflight["$type:$id"]?.await()
                }
            }
        }

        /** Start loading a page the user is about to open (first page of tracks). */
        fun prefetch(type: String, id: String) {
            if (id.isBlank() && type != "liked") return
            val key = "$type:$id"
            if (recent[key] is Response.Success || inflight.containsKey(key)) return
            inflight[key] = prefetchScope.async {
                try {
                    val api = Graph.api
                    val flow = when (type) {
                        "album" -> api.getAlbumPage(id)
                        "liked" -> api.getLikedPage()
                        else -> api.getPlaylistPage(id)
                    }
                    // The first page of tracks; a playlist of 200 songs or fewer is waited for whole (as
                    // PagePrefetch does), so it opens complete rather than cut after its first 100.
                    var first: Response<ContextPageModel>? = null
                    val settled = flow.first { r ->
                        if (r is Response.Loading) return@first false
                        if (first == null) first = r
                        r !is Response.Success || type != "playlist" || r.data.complete || r.data.tracks.size > WHOLE_PLAYLIST_MAX
                    }
                    val shown = (settled as? Response.Success)?.takeIf { it.data.complete } ?: first
                    if (shown is Response.Success) {
                        recent[key] = shown
                        // The hearts are known before the page opens.
                        val p = shown.data
                        if (p.type != "liked" && p.type != "liked-artist") SongPlayer.libraryContains(
                            p.tracks.mapNotNull { s -> s.spotifyTrackId.takeIf { it.isNotBlank() }?.let { "spotify:track:$it" } } + p.uri)
                        // The header's cover (the big one) is on storage before the page draws it.
                        if (p.coverUri.startsWith("http") && !io.github.thake.soundnest.feature.system.settings.DataSaver.metered)
                            runCatching { com.bumptech.glide.Glide.with(Graph.app).downloadOnly().load(p.coverUri).submit() }
                    }
                } catch (t: kotlinx.coroutines.CancellationException) {
                    throw t
                } catch (_: Throwable) {
                } finally {
                    inflight.remove(key)
                }
            }
        }
    }
    private val _page = MutableStateFlow<Response<ContextPageModel>>(Response.Loading())
    val page: StateFlow<Response<ContextPageModel>> = _page
    val savedUris = mutableStateOf<Set<String>>(emptySet())
    val contextSaved = mutableStateOf(false)
    /** The page is a playlist the user may change: only there can a track be removed. */
    val editableContext = mutableStateOf(false)
    /** Who can change this playlist: more than one when it is collaborative (their faces in the header). */
    val members = mutableStateOf<List<io.github.thake.soundnest.api.PlaylistMember>>(emptyList())
    /** On a collaborative playlist: who added each row (by row id, then by song). */
    val adders = mutableStateOf<Map<String, io.github.thake.soundnest.api.PlaylistMember>>(emptyMap())
    /** Someone else's collaborative playlist this account was never invited to: it can't add to it. */
    val collabLocked = mutableStateOf(false)
    private var key: String? = null
    private var savedChecked = 0

    private var lastType = ""
    private var lastId = ""

    /** A pull-to-refresh is reading the page again. */
    val refreshing = mutableStateOf(false)

    /** "Recommended songs" under an own playlist; null until asked. */
    val recs = mutableStateOf<List<SongsModel>?>(null)
    /** Suggested already (shown, added or refreshed away): not suggested again on this page. */
    private val recsSeen = java.util.Collections.synchronizedSet(LinkedHashSet<String>())
    private var recsJob: kotlinx.coroutines.Job? = null
    private var loadJob: kotlinx.coroutines.Job? = null
    private var likedTapsJob: kotlinx.coroutines.Job? = null
    private var likedChangesJob: kotlinx.coroutines.Job? = null

    /** Asks Spotify for songs that fit [p] (its name when it is empty), [refresh] for new ones. */
    fun loadRecs(p: ContextPageModel, refresh: Boolean = false) {
        if (!io.github.thake.soundnest.feature.parental.settings.FeatureGates.canUseAutoplayAndRadio()) {
            recs.value = emptyList()
            return
        }
        if (recsJob?.isActive == true) return
        if (!refresh && recs.value != null) return
        recsJob = viewModelScope.launch(Dispatchers.IO) {
            val policy = io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore.current()
            val inList = p.tracks.mapTo(HashSet()) { it.spotifyTrackId }
            val seenSnapshot = synchronized(recsSeen) { recsSeen.toSet() }
            val skip = (inList + seenSnapshot).map { "spotify:track:$it" }
            val got = runCatching { SoundNestRuntime.api.playlists.recommended(p.uri, limit = RECS_COUNT, skip = skip) }
                .getOrDefault(emptyList()).map(api::songOf)
                .filter { it.spotifyTrackId !in inList && it.spotifyTrackId !in seenSnapshot && io.github.thake.soundnest.feature.parental.filter.ParentalFilterEngine.isTrackAllowed(it, policy) }
            synchronized(recsSeen) { got.forEach { recsSeen += it.spotifyTrackId } }
            recs.value = got
        }
    }

    /** [song] added to the playlist: it leaves the suggestions at once, and one more is asked for when few remain. */
    fun takeRec(p: ContextPageModel, song: SongsModel) {
        val left = recs.value.orEmpty().filter { it.spotifyTrackId != song.spotifyTrackId }
        recs.value = left
        if (left.size < 3) loadRecs(p, refresh = true)
    }

    fun refresh() {
        if (refreshing.value) return
        refreshing.value = true
        reload()
    }

    /** Set by [reload]: the next load skips the short-lived caches (whole Liked list, playlist songs). */
    private var fresh = false

    /** After an edit (name, cover, description): the page from the server again. */
    fun reload() {
        val k = key ?: return
        recent.remove(k)
        key = null
        fresh = true
        // The likes, the members and who added each song are read again with the new list (a
        // song just added showed no face on a collaborative playlist).
        savedChecked = 0
        load(lastType, lastId)
    }

    fun load(type: String, id: String) {
        lastType = type; lastId = id
        val rev = io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore.revision.value
        if (cachedPolicyRevision != rev) {
            recent.clear()
            cachedPolicyRevision = rev
        }
        val cacheKey = "$type:$id"
        if (key == cacheKey) return
        key = cacheKey
        loadJob?.cancel()
        likedTapsJob?.cancel()
        likedChangesJob?.cancel()
        // A page seen recently is on screen from the first frame (no blank, then pop,
        // in the middle of the slide); the network still refreshes it behind.
        recent[cacheKey]?.let { _page.value = it }
        val pending = inflight[cacheKey]
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            // Touched a moment ago: join that look-up instead of sending a second one.
            if (pending != null && _page.value !is Response.Success) {
                runCatching { pending.await() }
                recent[cacheKey]?.let { _page.value = it }
            }
            val force = fresh.also { fresh = false }
            val flow = when (type) {
                "album" -> api.getAlbumPage(id)
                "liked" -> api.getLikedPage(full = force)
                // id: "<artist id>|<artist name>"
                "liked-artist" -> api.getLikedByArtistPage(id.substringBefore('|'), id.substringAfter('|', ""))
                else -> api.getPlaylistPage(id, force = force)
            }
            // Liked Songs: a like or unlike anywhere puts the list right at once.
            if (type == "liked") {
                fun show(songs: List<SongsModel>) {
                    val current = (_page.value as? Response.Success)?.data ?: return
                    val updated = Response.Success(current.copy(
                        tracks = songs,
                        metaLine = Graph.app.resources.getQuantityString(R.plurals.liked_songs_header_metadata_text, songs.size, songs.size),
                    ))
                    _page.value = updated
                    recent[cacheKey] = updated
                }
                // An unlike leaves the list on the tap, before Spotify has answered (reported: slow).
                likedTapsJob = viewModelScope.launch(Dispatchers.IO) {
                    SongPlayer.likedTaps.collect { (id, liked) ->
                        if (liked) return@collect
                        val current = (_page.value as? Response.Success)?.data ?: return@collect
                        if (current.tracks.any { it.spotifyTrackId == id }) show(current.tracks.filterNot { it.spotifyTrackId == id })
                    }
                }
                // Once Spotify took it, the newest songs are read again: at once, then once more
                // in case the first read still had the old list.
                likedChangesJob = viewModelScope.launch(Dispatchers.IO) {
                    SongPlayer.likedChanges.drop(1).collectLatest {
                        api.mergeLikedHead()?.let(::show)
                        kotlinx.coroutines.delay(2_500)
                        api.mergeLikedHead()?.let(::show)
                    }
                }
            }
            try { flow.collect { r ->
                if (r is Response.Loading && _page.value is Response.Success) return@collect
                // The fuller list on screen (a refresh, or one opened a moment ago and kept in memory)
                // stays until the new one is complete: the first page alone cut a long playlist short
                // and lost the place in it, the list then growing back page by page.
                if (r is Response.Success && !r.data.complete &&
                    (_page.value as? Response.Success)?.data?.let { it.uri == r.data.uri && it.tracks.size > r.data.tracks.size } == true) return@collect
                _page.value = r
                if (r is Response.Success) {
                    recent[cacheKey] = r
                    val p = r.data
                    if (type == "playlist" && p.complete) io.github.thake.soundnest.core.catalog.PagePrefetch.refreshed(id, p)
                    // Check saved state on the first page and again once complete.
                    if ((savedChecked == 0 && p.tracks.isNotEmpty()) || (p.complete && savedChecked < 2)) {
                        savedChecked = if (p.complete) 2 else 1
                        refreshSaved(p)
                    }
                }
            } } finally { refreshing.value = false }
        }
    }

    private fun refreshSaved(p: ContextPageModel) = viewModelScope.launch {
        if (p.type == "liked" || p.type == "liked-artist") {
            // Everything here is liked by definition.
            savedUris.value = p.tracks.map { "spotify:track:${it.spotifyTrackId}" }.toSet()
            return@launch
        }
        val uris = p.tracks.mapNotNull { s -> s.spotifyTrackId.takeIf { it.isNotBlank() }?.let { "spotify:track:$it" } } + p.uri
        // Apart from what follows: the hearts of a 6000-song list are 120 requests one after the
        // other, and the page's own controls (add songs, members, who added what) waited behind
        // all of them.
        launch {
            val saved = SongPlayer.libraryContains(uris)
            savedUris.value = saved
            contextSaved.value = p.uri in saved
            // Reported: a playlist in the Library with an empty heart, nothing to take it out with,
            // until a pull-to-refresh of the Library. The list catches up by itself.
            if (p.type == "playlist") {
                val listed = withContext(Dispatchers.IO) { io.github.thake.soundnest.feature.library.settings.getCachedLibraryEntries(Graph.app) }
                if (io.github.thake.soundnest.core.catalog.LibraryEdits.listedButNotSaved(p.uri, saved, listed)) {
                    io.github.thake.soundnest.host.android.Logbook.log("library: ${p.uri} listed but not saved, the list is read again")
                    io.github.thake.soundnest.core.catalog.LibraryEdits.reloadSoon()
                }
            }
        }
        // A11: Spotify refuses an edit to a list the user cannot change, so the entry is only
        // offered where it would work (own and collaborative playlists, as Spotify itself).
        if (p.type == "playlist") editableContext.value = withContext(Dispatchers.IO) {
            runCatching { EditablePlaylists.load().any { it.id == p.uri.substringAfterLast(':') } }.getOrDefault(false)
        }
        if (p.type == "playlist") members.value = withContext(Dispatchers.IO) {
            runCatching { SoundNestRuntime.api.playlists.members(p.uri) }.getOrDefault(emptyList())
        }
        // Collaborative: each song shows who added it (the official app's small face on the row).
        val crew = members.value.associateBy { it.username }
        adders.value = if (crew.size < 2) emptyMap() else withContext(Dispatchers.IO) {
            runCatching {
                val out = HashMap<String, io.github.thake.soundnest.api.PlaylistMember>()
                SoundNestRuntime.api.playlists.addedBy(p.uri).forEach { r ->
                    val m = crew[r.username] ?: return@forEach
                    if (r.rowUid.isNotBlank()) out["row:" + r.rowUid] = m
                    out.putIfAbsent("song:" + r.trackUri.substringAfterLast(':'), m)
                }
                out
            }.getOrDefault(emptyMap())
        }
        // Collaborative yet not editable here: only an invitation link lets this account add songs.
        // Said on the page instead of leaving the user wondering where the + went.
        collabLocked.value = p.type == "playlist" && !editableContext.value && withContext(Dispatchers.IO) {
            runCatching { SoundNestRuntime.api.playlists.access(p.uri) == io.github.thake.soundnest.api.PlaylistAccess.COLLABORATIVE }.getOrDefault(false)
        }
    }

    /**
     * Owner, from the "Shared with" list: a member becomes a collaborator or a listener, or leaves
     * the playlist ([collaborator] null). The list answers at once; a refusal puts back what Spotify has.
     */
    fun changeMember(p: ContextPageModel, m: io.github.thake.soundnest.api.PlaylistMember, collaborator: Boolean?, onDone: (Boolean) -> Unit) {
        val before = members.value
        members.value = if (collaborator == null) before.filter { it.username != m.username }
        else before.map { if (it.username == m.username) it.copy(isCollaborator = collaborator) else it }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    if (collaborator == null) SoundNestRuntime.api.playlists.removeMember(p.uri, m.username)
                    else SoundNestRuntime.api.playlists.setMemberCollaborator(p.uri, m.username, collaborator)
                }.isSuccess
            }
            if (!ok) members.value = withContext(Dispatchers.IO) {
                runCatching { SoundNestRuntime.api.playlists.members(p.uri) }.getOrDefault(before)
            }
            onDone(ok)
        }
    }

    fun toggleContextSaved(p: ContextPageModel) {
        val on = !(SongPlayer.library[p.uri] ?: contextSaved.value)
        io.github.thake.soundnest.core.util.Haptics.toggle(on)
        contextSaved.value = on
        SongPlayer.setSaved(p.uri, on)
    }

    /**
     * The track left this page's playlist: its row goes at once, so the list answers the tap
     * without waiting for Spotify (the write reads every page of the playlist first). A
     * failure puts the page back from the server.
     */
    fun dropTrack(song: SongsModel) {
        val k = key ?: return
        val current = (_page.value as? Response.Success)?.data ?: return
        // The row, not the song: the same song twice in a playlist has the same id twice.
        val at = if (song.rowUid.isNotBlank()) current.tracks.indexOfFirst { it.rowUid == song.rowUid }
            else current.tracks.indexOfFirst { it.id == song.id }
        if (at < 0) return
        val updated = Response.Success(current.copy(tracks = current.tracks.toMutableList().apply { removeAt(at) }))
        _page.value = updated
        recent[k] = updated
    }

    // Moves are written one at a time, in the order they were made.
    private val moveWrites = kotlinx.coroutines.sync.Mutex()

    /**
     * A row of this page's playlist moved by hand: the list takes its new order at once, then
     * Spotify is told. A refusal puts the order back as it was and [onDone] gets false. Returns
     * false when the page could not take the move (its rows changed under the drag).
     */
    fun moveRow(move: PlaylistReorder.RowMove, onDone: (Boolean) -> Unit): Boolean {
        val k = key ?: return false
        val current = (_page.value as? Response.Success)?.data ?: return false
        val tracks = PlaylistReorder.applied(current.tracks, move) { it.rowUid } ?: return false
        val updated = Response.Success(current.copy(tracks = tracks))
        _page.value = updated
        recent[k] = updated
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                moveWrites.withLock {
                    runCatching { SoundNestRuntime.api.playlists.moveTracks(current.uri, listOf(move.uid), move.beforeUid) }.isSuccess
                }
            }
            val id = current.uri.substringAfterLast(':')
            if (ok) withContext(Dispatchers.IO) {
                // The songs kept for this playlist (same count, so reused as they were) are read again,
                // and its copies on storage take the new order.
                Api.invalidatePlaylist(id)
                val now = (_page.value as? Response.Success)?.data?.takeIf { it.uri == current.uri } ?: return@withContext
                io.github.thake.soundnest.core.catalog.PagePrefetch.refreshed(id, now)
            } else if (_page.value === updated) {
                _page.value = Response.Success(current)
                recent[k] = Response.Success(current)
            } else reload()
            onDone(ok)
        }
        return true
    }

    /** A name or description just saved shows in the header at once; the page is read again behind it. */
    fun showDetails(name: String, description: String) {
        val k = key ?: return
        val current = (_page.value as? Response.Success)?.data ?: return
        val updated = Response.Success(current.copy(name = name, description = description))
        _page.value = updated
        recent[k] = updated
    }

    /**
     * Smart shuffle (Spotify keeps its own for Premium): the playlist shuffled, with one of
     * Spotify's recommendations for it after every [SmartShuffle.EVERY] songs.
     */
    fun playSmart(p: ContextPageModel, first: SongsModel?) {
        viewModelScope.launch {
            // A list still loading: every song of it is mixed, not only the ones on screen yet.
            val whole = if (p.complete) p else wholeList(page) ?: p
            // Turned off while the list loaded.
            if (SmartShuffle.on.value != p.uri) return@launch
            SmartShuffle.start(whole, first)
        }
    }

    fun play(p: ContextPageModel, song: SongsModel, sort: TrackOrder = TrackOrder()) {
        // Local files of the playlist (Spotify holds their tags only): one found on this device
        // plays from there, with the other such files of the list; one not found cannot play,
        // the next song that can does.
        with(io.github.thake.soundnest.feature.localfiles.data.LocalFileMatch) {
            val sorted = sort.apply(p.tracks)
            if (song.isUnmatchedLocal) {
                val after = sorted.drop(sorted.indexOfFirst { it.id == song.id && it.rowUid == song.rowUid } + 1)
                val next = (after + sorted).firstOrNull { !it.isUnmatchedLocal } ?: return
                return play(p, next, sort)
            }
            if (song.isDeviceFile) {
                viewModelScope.launch {
                    val files = io.github.thake.soundnest.feature.localfiles.data.LocalMusic.tracks(Graph.app).associateBy { key(it) }
                    val list = sorted.mapNotNull { files[it.url] }
                    val at = list.indexOfFirst { key(it) == song.url }.coerceAtLeast(0)
                    if (list.isNotEmpty()) io.github.thake.soundnest.core.playback.LocalPlayer.play(list, at, shuffle = state.shuffle.value, context = p.name, contextUri = p.uri)
                }
                return
            }
        }
        playOnSpotify(p, song, sort)
    }

    private fun playOnSpotify(p: ContextPageModel, song: SongsModel, sort: TrackOrder) {
        state.updateQueue(p.tracks)
        // B26: keep the list's own name — the player header falls back to it when Spotify's
        // context label comes back blank (it then showed the album's name).
        SongPlayer.registerContextName(p.uri, p.name)
        if (p.type == "liked" || p.type == "liked-artist") SongPlayer.registerLikedSongs(p.tracks.map { it.url })
        // Sorted by the user: Spotify plays the list in that order, not the playlist's. An
        // artist's liked songs are a part of Liked Songs: always given, so only they play.
        val order = if (sort.isCustom && p.type != "liked-artist") null else sort.apply(p.tracks).filter { it.spotifyTrackId.isNotBlank() }.map { "spotify:track:${it.spotifyTrackId}" }
        if (order != null) state.updateQueue(sort.apply(p.tracks))
        SongPlayer.playSong(song.url, contextUri = p.uri, order = order)
        state.updateSongState(song.coverUri, song.title, song.singer, true, song.id, 0, song.album)
    }
}


/** "Sort by" for the songs of a playlist or Liked Songs; [CUSTOM] is Spotify's own order. */
/** Smart shuffle, per playlist (uri), for this session. */
object SmartShuffle {
    const val EVERY = 3
    val on = androidx.compose.runtime.mutableStateOf<String?>(null)
    /** The recommendations mixed into the playing list. */
    val recommended = androidx.compose.runtime.mutableStateOf<Set<String>>(emptySet())

    private val keepScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)

    /** [trackId] was mixed in by smart shuffle: it is not in the playlist (yet). */
    fun isRecommendation(trackId: String): Boolean = on.value != null && trackId.isNotBlank() && trackId in recommended.value

    /**
     * The + of a recommendation, as in the official app: the song joins the playlist it was mixed
     * into (Liked Songs: it is liked), and is no longer marked as a recommendation.
     */
    fun keep(context: android.content.Context, song: SongsModel) {
        if (!io.github.thake.soundnest.feature.parental.settings.FeatureGates.canModifySpotifyLibrary(context)) return
        val list = on.value ?: return
        val id = song.spotifyTrackId
        recommended.value = recommended.value - id
        if (!list.startsWith("spotify:playlist:")) {
            if (SongPlayer.currentTrackId == id) SongPlayer.setLiked(true)
            else keepScope.launch { runCatching { SoundNestRuntime.api.library.add(listOf("spotify:track:$id")) } }
            android.widget.Toast.makeText(context, context.getString(R.string.snackbar_added_to_liked_songs), android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        io.github.thake.soundnest.core.catalog.SpotifySync.addTrackToPlaylist(context, list.substringAfterLast(':'), id) { ok ->
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                if (!ok) recommended.value = recommended.value + id
                val name = SongPlayer.knownContextName(list)
                android.widget.Toast.makeText(context,
                    if (ok) context.getString(R.string.local_added_to, name.ifBlank { context.getString(R.string.smart_shuffle_title) })
                    else context.getString(R.string.playlist_change_refused),
                    android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Smart shuffle can play [contextUri]: a playlist or Liked Songs, when autoplay/radio is allowed. */
    fun canPlay(contextUri: String): Boolean =
        io.github.thake.soundnest.feature.parental.settings.FeatureGates.canUseAutoplayAndRadio() &&
            (contextUri.startsWith("spotify:playlist:") || contextUri.contains(":collection"))

    /**
     * Smart shuffle (Spotify keeps its own for Premium): the playlist shuffled, with one of
     * Spotify's recommendations for it after every [EVERY] songs.
     */
    suspend fun start(p: ContextPageModel, first: SongsModel?, fromPositionMs: Long? = null) {
        if (!io.github.thake.soundnest.feature.parental.settings.FeatureGates.canUseAutoplayAndRadio()) return
        val policy = io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore.current()
        val api = Graph.api
        val state = Graph.currentSongState
        on.value = p.uri
        val allowedPlaylistTracks = io.github.thake.soundnest.feature.parental.filter.PlaybackGuardrail.sanitizeSongs(p.tracks, policy, p.uri)
        val inList = allowedPlaylistTracks.map { it.spotifyTrackId }.toSet()
        val rawRecs = withContext(Dispatchers.IO) {
            if (p.type == "liked") {
                // Spotify's playlist extender refuses Liked Songs: the radios of a few liked songs instead.
                allowedPlaylistTracks.shuffled().take(3).flatMap { api.getRecommendations(listOf(it.spotifyTrackId)).take(8) }
            } else runCatching { SoundNestRuntime.api.playlists.recommended(p.uri, limit = 20) }.getOrDefault(emptyList()).map(api::songOf)
                // Spotify's extender gives nothing for some playlists: the radios of a few of its songs.
                .ifEmpty { allowedPlaylistTracks.shuffled().take(3).flatMap { runCatching { api.getRecommendations(listOf(it.spotifyTrackId)).take(8) }.getOrDefault(emptyList()) } }
        }.filter { it.spotifyTrackId !in inList }.distinctBy { it.spotifyTrackId }.shuffled()
        val recs = io.github.thake.soundnest.feature.parental.filter.PlaybackGuardrail.sanitizeSongs(rawRecs, policy)
        val safeFirst = first?.takeIf { io.github.thake.soundnest.feature.parental.filter.ParentalFilterEngine.isTrackAllowed(it, policy, p.uri) }
        val shuffled = allowedPlaylistTracks.shuffled().let { list ->
            safeFirst?.let { f -> listOf(f) + list.filter { it.spotifyTrackId != f.spotifyTrackId } } ?: list
        }
        if (shuffled.isEmpty()) return
        val mixed = ArrayList<SongsModel>()
        val more = recs.iterator()
        shuffled.forEachIndexed { i, s ->
            mixed += s
            if ((i + 1) % EVERY == 0 && more.hasNext()) mixed += more.next()
        }
        recommended.value = recs.map { it.spotifyTrackId }.toSet()
        // Spotify's shuffle would mix the given order again.
        SongPlayer.setShuffle(false); state.shuffle.value = false
        state.updateQueue(mixed)
        val startSong = mixed.first()
        // Turned on from the player: the song playing goes on where it is, not from its start.
        val position = fromPositionMs?.takeIf { safeFirst != null && it > 0 }
        SongPlayer.playSong(startSong.url, contextUri = p.uri, order = mixed.map { "spotify:track:${it.spotifyTrackId}" }, positionMs = position,
            songs = mixed)
        state.updateSongState(startSong.coverUri, startSong.title, startSong.singer, true, startSong.id, 0, startSong.album)
    }

    /** From the player: loads the playing list, then smart-shuffles it, the current song first. */
    suspend fun startFor(contextUri: String): Boolean {
        if (!io.github.thake.soundnest.feature.parental.settings.FeatureGates.canUseAutoplayAndRadio()) return false
        val api = Graph.api
        val flow = when {
            contextUri.contains(":collection") -> api.getLikedPage()
            contextUri.startsWith("spotify:playlist:") -> api.getPlaylistPage(contextUri.substringAfterLast(':'))
            else -> return false
        }
        // Reported (#207): only the first 100 songs of a longer playlist were mixed. Every page now.
        val page = withContext(Dispatchers.IO) { wholeList(flow) } ?: return false
        // Turned off, or another list started, while the list loaded.
        if (on.value != contextUri || SongPlayer.nowPlaying.value.contextUri != contextUri) return true
        // Read after the list: the song may have changed meanwhile.
        val currentTrackId = SongPlayer.currentTrackId
        // Reported: turning it on from the player skipped the song when it was not found in the
        // list read, and a random one started instead; the song playing now always stays first.
        val current = page.tracks.firstOrNull { it.spotifyTrackId == currentTrackId }
            ?: SongPlayer.engineQueue.value.current?.takeIf { it.spotifyTrackId == currentTrackId }
        start(page, current, SongPlayer.getCurrentPosition())
        return true
    }
}
