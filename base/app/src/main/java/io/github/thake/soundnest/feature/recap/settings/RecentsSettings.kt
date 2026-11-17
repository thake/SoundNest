/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.recap.settings

import android.content.Context
import io.github.thake.soundnest.core.model.SongsModel
import io.github.thake.soundnest.core.playback.SongPlayer
import io.github.thake.soundnest.core.preferences.SessionResettable
import io.github.thake.soundnest.core.preferences.featurePrefs
import org.json.JSONArray
import org.json.JSONObject

/**
 * One "Recent searches" entry — Spotify-style: not the typed query but the
 * actual item the user opened from the results (song / artist / album / show).
 * Carries enough payload to re-launch the item on tap.
 */
data class RecentItem(
    val type: String,            // "song" | "artist" | "album" | "show"
    val key: String,             // stable id for dedupe (spotify id, or name)
    val name: String,
    val singer: String = "",     // artist line (songs/albums)
    val image: String = "",
    // Song-only payload so a tap can replay it.
    val songId: Int = -1,
    val songAlbum: String = "",
    val songUrl: String = "",
    val spotifyTrackId: String = "",
    val explicit: Boolean = false,
    val durationMs: Int = 0,
)

/** [this], a search's song the user played, as the recent search to keep. */
fun SongsModel.toRecentItem() = RecentItem(
    type = "song",
    key = spotifyTrackId.ifBlank { url },
    name = title,
    singer = singer,
    image = coverUri,
    songId = id,
    songAlbum = album,
    songUrl = url,
    spotifyTrackId = spotifyTrackId,
    explicit = explicit,
    durationMs = durationMs,
)

/**
 * A recent song as the song to play or queue again; null for any other kind of item. A song kept
 * with its Spotify id is played by that id, whatever play key was saved with it.
 */
fun RecentItem.toSong(): SongsModel? {
    if (type != "song") return null
    val byId = { SongPlayer.buildSpotifyPlayQuery(spotifyTrackId, name, singer) }
    val url = when {
        songUrl.isBlank() -> byId()
        spotifyTrackId.isNotBlank() && !songUrl.startsWith("spotify:track:") -> byId()
        else -> songUrl
    }
    return SongsModel(
        songId, name, songAlbum, singer, image, url, spotifyTrackId,
        explicit = explicit, durationMs = durationMs,
    )
}

/**
 * Stores recent items in `"feature_recap"` preferences and clears them on account switch/sign-out.
 */
object RecentsSettings : SessionResettable {
    private const val PREF_NAME = "recap"
    private const val KEY = "items"
    private const val MAX_ITEMS = 20

    private fun prefs(context: Context) = context.featurePrefs(PREF_NAME)

    private fun RecentItem.toJson(): JSONObject = JSONObject().apply {
        put("type", type); put("key", key); put("name", name); put("singer", singer)
        put("image", image); put("songId", songId); put("songAlbum", songAlbum)
        put("songUrl", songUrl); put("spotifyTrackId", spotifyTrackId)
        put("explicit", explicit); put("durationMs", durationMs)
    }

    private fun JSONObject.toRecentItem(): RecentItem = RecentItem(
        type = optString("type"), key = optString("key"), name = optString("name"),
        singer = optString("singer"), image = optString("image"),
        songId = optInt("songId", -1), songAlbum = optString("songAlbum"),
        songUrl = optString("songUrl"), spotifyTrackId = optString("spotifyTrackId"),
        explicit = optBoolean("explicit", false), durationMs = optInt("durationMs", 0),
    )

    fun get(context: Context): List<RecentItem> {
        val raw = prefs(context).getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getJSONObject(it).toRecentItem() }
        }.getOrDefault(emptyList())
    }

    private fun save(context: Context, items: List<RecentItem>) {
        val arr = JSONArray().also { a -> items.forEach { a.put(it.toJson()) } }
        prefs(context).edit().putString(KEY, arr.toString()).apply()
    }

    fun add(context: Context, item: RecentItem) {
        if (item.name.isBlank()) return
        val rest = get(context).filterNot { it.type == item.type && it.key == item.key }
        save(context, (listOf(item) + rest).take(MAX_ITEMS))
    }

    fun remove(context: Context, item: RecentItem) {
        save(context, get(context).filterNot { it.type == item.type && it.key == item.key })
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY).apply()
    }

    override fun onLeaveAccount(context: Context, forget: Boolean) {
        clear(context)
    }
}

fun getRecentItems(context: Context): List<RecentItem> = RecentsSettings.get(context)

fun addRecentItem(context: Context, item: RecentItem) = RecentsSettings.add(context, item)

fun removeRecentItem(context: Context, item: RecentItem) = RecentsSettings.remove(context, item)

fun clearRecentItems(context: Context) = RecentsSettings.clear(context)
