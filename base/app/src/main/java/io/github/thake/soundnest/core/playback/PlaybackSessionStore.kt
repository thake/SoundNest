package io.github.thake.soundnest.core.playback

import android.content.Context
import android.content.SharedPreferences
import io.github.thake.soundnest.core.di.Graph
import io.github.thake.soundnest.core.model.SongsModel

internal data class RestoredPlaybackSession(
    val uri: String,
    val title: String,
    val singer: String,
    val album: String,
    val coverUri: String,
    val contextUri: String,
    val contextName: String,
    val durationMs: Long,
    val positionMs: Long,
    val liked: Boolean,
    val source: String,
    val savedAtMs: Long,
    val queue: List<SongsModel>,
)

/**
 * Persists and restores the last played track, position, queue snapshot, and repeat mode
 * across app restarts, extracted from [SongPlayer] for focused responsibility and testability.
 */
internal class PlaybackSessionStore(
    private val prefsProvider: () -> SharedPreferences = {
        Graph.app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    },
    private val clockMs: () -> Long = { System.currentTimeMillis() },
) {
    private var savedLastKey: String = ""

    @Volatile var savedSource: String = LastSession.SOURCE_SPOTIFY
        private set

    @Volatile var savedAtMs: Long = 0L
        private set

    fun loadTrack(): RestoredPlaybackSession? {
        val p = prefsProvider()
        val uri = p.getString(KEY_URI, "").orEmpty()
        val title = p.getString(KEY_TITLE, "").orEmpty()
        if (uri.isBlank() || title.isBlank()) return null
        val source = p.getString(KEY_SOURCE, null) ?: LastSession.SOURCE_SPOTIFY
        val savedAt = p.getLong(KEY_SAVED_AT, 0L)
        savedSource = source
        savedAtMs = savedAt
        return RestoredPlaybackSession(
            uri = uri,
            title = title,
            singer = p.getString(KEY_SINGER, "").orEmpty(),
            album = p.getString(KEY_ALBUM, "").orEmpty(),
            coverUri = p.getString(KEY_COVER, "").orEmpty(),
            contextUri = p.getString(KEY_CONTEXT_URI, "").orEmpty(),
            contextName = p.getString(KEY_CONTEXT_NAME, "").orEmpty(),
            durationMs = p.getLong(KEY_DURATION, 0L),
            positionMs = p.getLong(KEY_POSITION, 0L),
            liked = p.getBoolean(KEY_LIKED, false),
            source = source,
            savedAtMs = savedAt,
            queue = LastSession.decodeQueue(p.getString(KEY_QUEUE, null)),
        )
    }

    /**
     * Persists the current track snapshot on track change or play/pause/like change.
     * Returns `true` if a write occurred, `false` if skipped as blank or unchanged.
     */
    fun saveTrack(
        song: SongsModel,
        uri: String,
        playing: Boolean,
        liked: Boolean,
        contextUri: String,
        contextName: String,
        durationMs: Long,
        positionMs: Long,
        source: String = LastSession.SOURCE_SPOTIFY,
        queue: List<SongsModel>? = null,
    ): Boolean {
        val key = "$uri|$playing|$liked|$source|$contextUri"
        if (uri.isBlank() || key == savedLastKey) return false
        savedLastKey = key
        savedSource = source
        savedAtMs = clockMs()
        val p = prefsProvider()
        // No queue known yet for this song: the one saved with it stays (a pause, a like), another song's goes.
        val kept = if (queue.isNullOrEmpty() && p.getString(KEY_URI, "") == uri) {
            p.getString(KEY_QUEUE, "").orEmpty()
        } else {
            null
        }
        p.edit()
            .putString(KEY_SOURCE, source)
            .putLong(KEY_SAVED_AT, savedAtMs)
            .putString(KEY_QUEUE, kept ?: queue?.let(LastSession::encodeQueue).orEmpty())
            .putString(KEY_CONTEXT_URI, contextUri)
            .putString(KEY_CONTEXT_NAME, contextName)
            .putString(KEY_URI, uri)
            .putString(KEY_TITLE, song.title)
            .putString(KEY_SINGER, song.singer)
            .putString(KEY_ALBUM, song.album)
            .putString(KEY_COVER, song.coverUri)
            .putLong(KEY_DURATION, durationMs)
            .putLong(KEY_POSITION, positionMs)
            .putBoolean(KEY_LIKED, liked)
            .apply()
        return true
    }

    fun updatePositionIfCurrent(currentUri: String, positionMs: Long) {
        if (currentUri.isBlank()) return
        val p = prefsProvider()
        val uri = p.getString(KEY_URI, "").orEmpty()
        if (uri.isBlank() || uri != currentUri) return
        p.edit().putLong(KEY_POSITION, positionMs).apply()
    }

    fun updateSpotifyQueueIfCurrent(uri: String, queue: List<SongsModel>) {
        if (savedSource != LastSession.SOURCE_SPOTIFY) return
        val p = prefsProvider()
        if (p.getString(KEY_URI, "") != uri) return
        val encoded = LastSession.encodeQueue(queue)
        if (encoded.isNotEmpty()) p.edit().putString(KEY_QUEUE, encoded).apply()
    }

    fun loadRepeatMode(): Int = prefsProvider().getInt(KEY_REPEAT, 0).coerceIn(0, 3)

    fun saveRepeatMode(mode: Int) {
        prefsProvider().edit().putInt(KEY_REPEAT, mode.coerceIn(0, 3)).apply()
    }

    fun clear() {
        prefsProvider().edit().clear().apply()
        savedLastKey = ""
        savedSource = LastSession.SOURCE_SPOTIFY
        savedAtMs = 0L
    }

    companion object {
        const val PREFS_NAME = "core_playback_session"
        private const val KEY_URI = "uri"
        private const val KEY_TITLE = "title"
        private const val KEY_SINGER = "singer"
        private const val KEY_ALBUM = "album"
        private const val KEY_COVER = "cover"
        private const val KEY_CONTEXT_URI = "contextUri"
        private const val KEY_CONTEXT_NAME = "contextName"
        private const val KEY_DURATION = "duration"
        private const val KEY_POSITION = "position"
        private const val KEY_LIKED = "liked"
        private const val KEY_SOURCE = "source"
        private const val KEY_SAVED_AT = "savedAt"
        private const val KEY_QUEUE = "queue"
        private const val KEY_REPEAT = "repeat"
    }
}
