package io.github.thake.soundnest.core.playback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.thake.soundnest.core.model.SongsModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaybackSessionStoreTest {

    private lateinit var store: PlaybackSessionStore
    private var fakeNow = 100_000L

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = ctx.getSharedPreferences("test_playback_session", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        store = PlaybackSessionStore(prefsProvider = { prefs }, clockMs = { fakeNow })
    }

    private fun song(id: String, title: String) = SongsModel(
        id = id.hashCode() and 0x7fffffff,
        title = title,
        album = "Album $id",
        singer = "Artist $id",
        coverUri = "https://i.scdn.co/image/$id",
        url = "spotify:track:$id",
        spotifyTrackId = id,
    )

    @Test
    fun savesAndRestoresTrackSessionAndQueue() {
        val s1 = song("t1", "Song One")
        val s2 = song("t2", "Song Two")

        val wrote = store.saveTrack(
            song = s1,
            uri = "spotify:track:t1",
            playing = true,
            liked = true,
            contextUri = "spotify:playlist:pl1",
            contextName = "Kids Mix",
            durationMs = 180_000L,
            positionMs = 45_000L,
            source = LastSession.SOURCE_SPOTIFY,
            queue = listOf(s1, s2),
        )
        assertTrue(wrote)

        val restored = store.loadTrack()
        assertEquals("spotify:track:t1", restored?.uri)
        assertEquals("Song One", restored?.title)
        assertEquals("Artist t1", restored?.singer)
        assertEquals("Album t1", restored?.album)
        assertEquals("spotify:playlist:pl1", restored?.contextUri)
        assertEquals("Kids Mix", restored?.contextName)
        assertEquals(180_000L, restored?.durationMs)
        assertEquals(45_000L, restored?.positionMs)
        assertTrue(restored?.liked == true)
        assertEquals(LastSession.SOURCE_SPOTIFY, restored?.source)
        assertEquals(100_000L, restored?.savedAtMs)
        assertEquals(2, restored?.queue?.size)
        assertEquals("t2", restored?.queue?.get(1)?.spotifyTrackId)
    }

    @Test
    fun deduplicatesIdenticalSaveAndPreservesQueueWhenSameUriHasNoNewQueue() {
        val s1 = song("t1", "Song One")
        val s2 = song("t2", "Song Two")

        assertTrue(
            store.saveTrack(
                song = s1, uri = "spotify:track:t1", playing = true, liked = false,
                contextUri = "", contextName = "", durationMs = 100_000L, positionMs = 10_000L,
                queue = listOf(s1, s2),
            ),
        )
        // Exact same state key -> skipped
        assertFalse(
            store.saveTrack(
                song = s1, uri = "spotify:track:t1", playing = true, liked = false,
                contextUri = "", contextName = "", durationMs = 100_000L, positionMs = 15_000L,
                queue = null,
            ),
        )
        // Pause on same URI with null queue -> writes and preserves existing queue
        assertTrue(
            store.saveTrack(
                song = s1, uri = "spotify:track:t1", playing = false, liked = false,
                contextUri = "", contextName = "", durationMs = 100_000L, positionMs = 20_000L,
                queue = null,
            ),
        )
        val restored = store.loadTrack()
        assertEquals(20_000L, restored?.positionMs)
        assertEquals(2, restored?.queue?.size)
    }

    @Test
    fun updatesPositionAndQueueOnlyWhenUriMatches() {
        val s1 = song("t1", "Song One")
        val s2 = song("t2", "Song Two")
        store.saveTrack(
            song = s1, uri = "spotify:track:t1", playing = true, liked = false,
            contextUri = "", contextName = "", durationMs = 100_000L, positionMs = 5_000L,
        )

        store.updatePositionIfCurrent("spotify:track:other", 99_000L)
        assertEquals(5_000L, store.loadTrack()?.positionMs)

        store.updatePositionIfCurrent("spotify:track:t1", 33_000L)
        assertEquals(33_000L, store.loadTrack()?.positionMs)

        store.updateSpotifyQueueIfCurrent("spotify:track:other", listOf(s1, s2))
        assertEquals(0, store.loadTrack()?.queue?.size)

        store.updateSpotifyQueueIfCurrent("spotify:track:t1", listOf(s1, s2))
        assertEquals(2, store.loadTrack()?.queue?.size)
    }

    @Test
    fun clampsRepeatModeAndClearsAllState() {
        assertEquals(0, store.loadRepeatMode())
        store.saveRepeatMode(2)
        assertEquals(2, store.loadRepeatMode())
        store.saveRepeatMode(99)
        assertEquals(3, store.loadRepeatMode())

        store.saveTrack(
            song = song("t1", "Song One"), uri = "spotify:track:t1", playing = true, liked = false,
            contextUri = "", contextName = "", durationMs = 100_000L, positionMs = 5_000L,
        )
        store.clear()
        assertNull(store.loadTrack())
        assertEquals(0, store.loadRepeatMode())
        assertEquals(0L, store.savedAtMs)
        assertEquals(LastSession.SOURCE_SPOTIFY, store.savedSource)
    }
}
