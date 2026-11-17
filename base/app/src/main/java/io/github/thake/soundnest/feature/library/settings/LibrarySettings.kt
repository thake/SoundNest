/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.library.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.catalog.Api
import io.github.thake.soundnest.core.model.ArtistsModel
import io.github.thake.soundnest.core.model.LibraryEntry
import io.github.thake.soundnest.core.model.SongsModel
import io.github.thake.soundnest.core.preferences.SessionResettable
import io.github.thake.soundnest.core.preferences.featurePrefs
import io.github.thake.soundnest.feature.spokenword.audible.data.AudibleUris
import org.json.JSONArray
import org.json.JSONObject
import java.text.Collator

private const val FEATURE_LIBRARY = "library"
private const val FEATURE_LIBRARY_CACHE = "library_cache"
private const val KEY_GRID_VIEW = "grid_view"
private const val KEY_TRACK_ORDER_PREFIX = "context_track_order:"

private fun libraryPrefs(c: Context) = c.featurePrefs(FEATURE_LIBRARY)
private fun libraryCachePrefs(c: Context) = c.featurePrefs(FEATURE_LIBRARY_CACHE)

/** Library layout: false = rows (default), true = Spotify-style 3-column grid. */
fun isLibraryGridView(c: Context): Boolean = libraryPrefs(c).getBoolean(KEY_GRID_VIEW, false)
fun setLibraryGridView(c: Context, v: Boolean) = libraryPrefs(c).edit().putBoolean(KEY_GRID_VIEW, v).apply()

/**
 * Offline cache of library entries and followed artists, backed by `"feature_library_cache"`
 * and cleared automatically when leaving an account via [SessionResettable].
 */
object LibraryCache : SessionResettable {
    override fun onLeaveAccount(context: Context, forget: Boolean) {
        libraryCachePrefs(context).edit().clear().apply()
    }
}

fun cacheLibraryEntries(context: Context, entries: List<LibraryEntry>) {
    val json = JSONArray().apply {
        entries.forEach { e ->
            put(JSONObject().apply {
                put("spotifyId", e.spotifyId)
                put("name", e.name)
                put("subtitle", e.subtitle)
                put("coverUri", e.coverUri)
                put("isPlaylist", e.isPlaylist)
                put("artists", e.artists)
                put("folderUri", e.folderUri)
                put("childCount", e.childCount)
                put("ownerUri", e.ownerUri)
            })
        }
    }.toString()
    libraryCachePrefs(context).edit().putString("entries", json).apply()
}

fun getCachedLibraryEntries(context: Context): List<LibraryEntry> = runCatching {
    val raw = libraryCachePrefs(context).getString("entries", null) ?: return emptyList()
    val arr = JSONArray(raw)
    (0 until arr.length()).map { i ->
        arr.getJSONObject(i).let { o ->
            LibraryEntry(
                spotifyId = o.getString("spotifyId"),
                name = o.getString("name"),
                subtitle = o.getString("subtitle"),
                coverUri = o.getString("coverUri"),
                isPlaylist = o.getBoolean("isPlaylist"),
                artists = o.optString("artists", ""),
                folderUri = o.optString("folderUri", ""),
                childCount = o.optInt("childCount", 0),
                ownerUri = o.optString("ownerUri", ""),
            )
        }
    }
}.getOrDefault(emptyList())

fun cacheFollowedArtists(context: Context, artists: List<ArtistsModel>) {
    val json = JSONArray().apply {
        artists.forEach { a ->
            put(JSONObject().apply {
                put("name", a.name)
                put("coverUri", a.coverUri)
                put("id", a.id)
            })
        }
    }.toString()
    libraryCachePrefs(context).edit().putString("followedArtists", json).apply()
}

fun getCachedFollowedArtists(context: Context): List<ArtistsModel> = runCatching {
    val raw = libraryCachePrefs(context).getString("followedArtists", null) ?: return emptyList()
    val arr = JSONArray(raw)
    (0 until arr.length()).map { i ->
        arr.getJSONObject(i).let { o ->
            ArtistsModel(
                name = o.getString("name"),
                coverUri = o.getString("coverUri"),
                id = o.optString("id", ""),
            )
        }
    }
}.getOrDefault(emptyList())

/**
 * The chips under "Your Library" (Playlists, By me, Artists, Albums, Podcasts, Audiobooks, Songs),
 * like the official app's filter row: one at a time, tapping the chosen one again shows everything.
 */
enum class LibraryChip { ALL, PLAYLISTS, BY_ME, ARTISTS, ALBUMS, PODCASTS, AUDIOBOOKS, SONGS }

object LibraryChips {
    fun isAudiobook(entry: LibraryEntry): Boolean =
        AudibleUris.isAudibleUri(entry.spotifyId) ||
            AudibleUris.isAudibleUri(entry.uri) ||
            entry.uri.startsWith("spotify:audiobook:")

    fun apply(
        chip: LibraryChip,
        entries: List<LibraryEntry>,
        artists: List<ArtistsModel>,
        songIds: Set<String>,
        myId: String? = null,
        shows: List<LibraryEntry> = emptyList(),
        podcastIds: Set<String> = emptySet(),
    ): Pair<List<LibraryEntry>, List<ArtistsModel>> = when (chip) {
        LibraryChip.ALL -> entries to artists
        LibraryChip.ARTISTS -> emptyList<LibraryEntry>() to artists
        LibraryChip.SONGS -> entries.filter { it.spotifyId in songIds } to emptyList()
        LibraryChip.PLAYLISTS -> entries.filter { it.spotifyId !in songIds && (it.isPlaylist || it.isFolder) } to emptyList()
        LibraryChip.BY_ME -> entries.filter { myId != null && it.ownerUri == "spotify:user:$myId" } to emptyList()
        LibraryChip.PODCASTS -> (entries.filter { it.spotifyId in podcastIds } + shows) to emptyList()
        LibraryChip.AUDIOBOOKS -> entries.filter(::isAudiobook) to emptyList()
        LibraryChip.ALBUMS -> entries.filter {
            it.spotifyId !in songIds &&
                !it.isPlaylist &&
                !it.isFolder &&
                !isAudiobook(it) &&
                !it.spotifyId.startsWith(Api.HomeCache.SHOW_PREFIX)
        } to emptyList()
    }

    /** A tap on [tapped] while [current] is chosen. */
    fun toggle(current: LibraryChip, tapped: LibraryChip): LibraryChip = if (current == tapped) LibraryChip.ALL else tapped
}

/**
 * How "Your Library" is sorted: the user's own order (Spotify's until they move
 * something), alphabetical, or by creator. The custom order is kept on the phone, one
 * list per level (the root, or a folder's uri). Pinned entries (Liked Songs, Local
 * Files, Downloaded) always stay on top, as in the official app.
 */
object LibraryOrder {
    enum class Sort { CUSTOM, RECENTS, RECENTLY_ADDED, ALPHABETICAL, CREATOR }

    const val ROOT = "root"
    private const val KEY_SORT = "sort"
    private const val SEP = "\u001F"

    var sort by mutableStateOf(Sort.CUSTOM)
        private set
    /** Bumped on every change, so screens re-arrange. */
    var version by mutableStateOf(0)
        private set
    private var context: Context? = null

    fun init(c: Context) {
        context = c.applicationContext
        sort = runCatching { Sort.valueOf(prefs()?.getString(KEY_SORT, null) ?: "") }.getOrDefault(Sort.CUSTOM)
    }

    private fun prefs() = context?.featurePrefs(FEATURE_LIBRARY)

    fun choose(s: Sort) {
        sort = s
        prefs()?.edit()?.putString(KEY_SORT, s.name)?.apply()
        version++
    }

    fun custom(level: String): List<String> =
        prefs()?.getString("order:$level", null)?.split(SEP)?.filter { it.isNotEmpty() } ?: emptyList()

    /** The user moved something: [keys] is the level's new order, and the sort becomes custom. */
    fun saveCustom(level: String, keys: List<String>) {
        prefs()?.edit()?.putString("order:$level", keys.joinToString(SEP))?.apply()
        if (sort != Sort.CUSTOM) choose(Sort.CUSTOM) else version++
    }

    /** Back to Spotify's order, everywhere. */
    fun reset() {
        val p = prefs() ?: return
        p.edit().apply { p.all.keys.filter { it.startsWith("order:") }.forEach { remove(it) } }.apply()
        choose(Sort.CUSTOM)
    }

    fun key(e: LibraryEntry): String = e.folderUri.ifBlank { e.spotifyId }

    /** Who made it: the owner of a playlist, the artists of an album; folders have none. */
    fun creator(e: LibraryEntry): String = when {
        e.isFolder -> ""
        !e.isPlaylist -> e.artists.ifBlank { e.subtitle.substringAfter(" • ", "") }
        else -> e.subtitle.substringAfter(" • ", "")
    }

    fun arrange(entries: List<LibraryEntry>, sort: Sort, saved: List<String>, pinned: Int): List<LibraryEntry> {
        val head = entries.take(pinned)
        val rest = entries.drop(pinned)
        val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
        val sorted = when (sort) {
            Sort.CUSTOM -> {
                if (saved.isEmpty()) rest
                else {
                    val at = saved.withIndex().associate { it.value to it.index }
                    rest.withIndex().sortedWith(compareBy({ at[key(it.value)] ?: -1 }, { it.index })).map { it.value }
                }
            }
            Sort.RECENTS -> rest
            Sort.RECENTLY_ADDED -> rest.sortedByDescending { it.addedAt }
            Sort.ALPHABETICAL -> rest.sortedWith { a, b -> collator.compare(a.name, b.name) }
            Sort.CREATOR -> rest.sortedWith(
                compareBy<LibraryEntry> { !it.isFolder }
                    .then { a, b -> collator.compare(creator(a), creator(b)) }
                    .then { a, b -> collator.compare(a.name, b.name) },
            )
        }
        return head + sorted.sortedBy { !it.pinned }
    }

    fun arrangeArtists(artists: List<ArtistsModel>, sort: Sort): List<ArtistsModel> = when (sort) {
        Sort.ALPHABETICAL, Sort.CREATOR -> {
            val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
            artists.sortedWith { a, b -> collator.compare(a.name, b.name) }
        }
        Sort.RECENTLY_ADDED -> artists.sortedWith(compareBy<ArtistsModel> { it.addedAt.isBlank() }.thenByDescending { it.addedAt })
        else -> artists
    }
}

/** Text order for sorting (accents ignored): one collator per thread, none is thread-safe. */
private val textOrder = ThreadLocal.withInitial {
    Collator.getInstance().apply { strength = Collator.PRIMARY }
}

enum class TrackSort(val label: Int) {
    CUSTOM(R.string.your_library_sort_order_custom),
    TITLE(R.string.track_sort_title),
    ARTIST(R.string.track_sort_artist),
    ALBUM(R.string.track_sort_album),
    DURATION(R.string.track_sort_duration),
    ADDED(R.string.track_sort_added);

    internal fun natural(songs: List<SongsModel>): List<SongsModel> {
        val text = textOrder.get()
        return when (this) {
            CUSTOM -> songs
            TITLE -> songs.sortedWith { a, b -> text.compare(a.title, b.title) }
            ARTIST -> songs.sortedWith { a, b -> text.compare(a.singer, b.singer).takeIf { it != 0 } ?: text.compare(a.album, b.album) }
            ALBUM -> songs.sortedWith { a, b -> text.compare(a.album, b.album).takeIf { it != 0 } ?: text.compare(a.title, b.title) }
            DURATION -> songs.sortedBy { it.durationMs }
            ADDED -> songs.sortedByDescending { it.addedAt }
        }
    }
}

/**
 * A sort and its direction. [reversed] flips the sort's natural order (A to Z, shortest
 * first, newest first for date added); for [TrackSort.CUSTOM], the playlist from its end.
 */
data class TrackOrder(val sort: TrackSort = TrackSort.CUSTOM, val reversed: Boolean = false) {
    val isCustom get() = sort == TrackSort.CUSTOM && !reversed

    fun apply(songs: List<SongsModel>): List<SongsModel> = sort.natural(songs).let { if (reversed) it.asReversed() else it }

    companion object {
        fun load(context: Context, uri: String): TrackOrder {
            val v = libraryPrefs(context).getString("$KEY_TRACK_ORDER_PREFIX$uri", null) ?: return TrackOrder()
            val sort = TrackSort.entries.firstOrNull { it.name == v.substringBefore(':') } ?: return TrackOrder()
            return TrackOrder(sort, v.endsWith(":reversed"))
        }

        fun save(context: Context, uri: String, order: TrackOrder) {
            val key = "$KEY_TRACK_ORDER_PREFIX$uri"
            libraryPrefs(context).edit().apply {
                if (order == TrackOrder()) remove(key) else putString(key, order.sort.name + if (order.reversed) ":reversed" else "")
            }.apply()
        }
    }
}
