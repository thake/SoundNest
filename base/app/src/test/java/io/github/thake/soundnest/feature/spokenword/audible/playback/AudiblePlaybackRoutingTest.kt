/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.spokenword.audible.playback

import io.github.thake.soundnest.feature.spokenword.audible.data.AudibleCatalogCache
import io.github.thake.soundnest.feature.spokenword.audible.data.AudibleProgressStore
import io.github.thake.soundnest.feature.spokenword.audible.settings.AudibleCredentialStore

import io.github.thake.soundnest.audible.*

import android.app.Application
import io.github.thake.soundnest.core.playback.CurrentSongState
import io.github.thake.soundnest.core.di.Graph
import io.github.thake.soundnest.core.playback.SongPlayer
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import android.os.Looper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AudiblePlaybackRoutingTest {

    private lateinit var app: Application
    private lateinit var fakeBackend: FakeAudibleBackend

    private class FakeAudibleBackend : AudiblePlayerBackend {
        var loadedGrant: AudibleStreamGrant? = null
        var playing: Boolean = false
        var buffering: Boolean = false
        var positionMs: Long = 0L
        var appliedSpeed: Float = 1.0f
        var appliedGain: Float = 1.0f
        var listener: AudiblePlayerBackend.Listener? = null

        override fun load(
            grant: AudibleStreamGrant,
            initialPositionMs: Long,
            speed: Float,
            playWhenReady: Boolean,
            listener: AudiblePlayerBackend.Listener,
        ) {
            loadedGrant = grant
            positionMs = initialPositionMs
            this.appliedSpeed = speed
            this.playing = playWhenReady
            this.buffering = false
            this.listener = listener
            listener.onPlaybackChanged(this.playing, false, positionMs)
        }

        override fun resume() {
            playing = true
            listener?.onPlaybackChanged(true, false, positionMs)
        }

        override fun pause() {
            playing = false
            listener?.onPlaybackChanged(false, false, positionMs)
        }

        override fun stop() {
            playing = false
        }

        override fun seekToAbsolute(positionMs: Long) {
            this.positionMs = positionMs
            listener?.onPlaybackChanged(playing, false, positionMs)
        }

        override fun setSpeed(speed: Float) {
            this.appliedSpeed = speed
        }

        override fun setGain(gain: Float) {
            this.appliedGain = gain
        }

        override fun isPlaying(): Boolean = playing
        override fun isBuffering(): Boolean = buffering
        override fun currentPositionMs(): Long = positionMs
        override fun release() {
            playing = false
        }
    }

    private val sampleGrant = AudibleStreamGrant(
        asin = "B08G9PRS1K",
        manifestUrl = "https://cdn.audible.de/B08G9PRS1K/manifest.mpd",
        drmType = "Widevine",
        chapters = listOf(
            AudibleChapter(0, "Chapter 1: Awakening", 0L, 60_000L),
            AudibleChapter(1, "Chapter 2: Questions", 60_000L, 120_000L),
            AudibleChapter(2, "Chapter 3: Experiments", 180_000L, 90_000L),
        ),
    )

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        Graph::class.java.getDeclaredField("app").apply { isAccessible = true }.set(null, app)
        SongPlayer::class.java.getDeclaredField("boundState").apply { isAccessible = true }
            .set(SongPlayer, CurrentSongState())

        ParentalPolicyStore.resetForTests(app)
        AudibleCredentialStore.clear(app)
        AudibleCatalogCache.clear(app)
        AudibleProgressStore.clearAll(app)

        fakeBackend = FakeAudibleBackend()
        AudiblePlayer.configureForTests(
            context = app,
            backend = fakeBackend,
            grantLoader = { asin -> sampleGrant.copy(asin = asin) },
        )
    }

    @After
    fun tearDown() {
        AudiblePlayer.resetForTests()
        ParentalPolicyStore.resetForTests(app)
        AudibleCredentialStore.clear(app)
        AudibleCatalogCache.clear(app)
        AudibleProgressStore.clearAll(app)
    }

    private fun approveSampleBook() {
        AudibleCredentialStore.save(
            app,
            AudibleSessionTokens(
                marketplace = AudibleMarketplace.DE,
                deviceSerial = "SERIAL123",
                adpToken = "ADP_SECRET",
                devicePrivateKeyPem = "PEM_SECRET",
                refreshToken = "REFRESH_SECRET",
            ),
        )
        AudibleCatalogCache.saveBooks(
            app,
            listOf(
                AudibleBook(
                    asin = "B08G9PRS1K",
                    title = "Project Hail Mary",
                    authors = listOf("Andy Weir"),
                    narrators = listOf("Ray Porter"),
                    runtimeLengthMs = 270_000L,
                    coverUrl = "https://m.media-amazon.com/images/I/hailmary.jpg",
                ),
            ),
        )
        ParentalPolicyStore.update(app) { it.copy(spokenWord = it.spokenWord.copy(allowAudiobooks = true)) }
        ParentalPolicyStore.setAudibleBookAllowed(app, "B08G9PRS1K", allowed = true, title = "Project Hail Mary", subtitle = "Andy Weir")
    }

    @Test
    fun `SongPlayer routes audible URIs to AudiblePlayer, exposes spoken-word controls, navigates chapters and saves local progress`() {
        approveSampleBook()
        // Pre-seed local progress at 75_000 ms (15s into Chapter 2)
        AudibleProgressStore.savePosition(app, asin = "B08G9PRS1K", positionMs = 75_000L, chapterIndex = 1)

        SongPlayer.playSong("audible:book:B08G9PRS1K")
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(AudiblePlayer.active.value)
        assertTrue(SongPlayer.isPlaying())
        assertEquals("audible:chapter:B08G9PRS1K:1", SongPlayer.nowPlaying.value.uri)
        assertTrue("Expected spoken-word controls enabled for Audible chapters", SongPlayer.isEpisode)
        assertFalse("Audible chapters must not expose Spotify Like action", SongPlayer.canLike)

        // Chapter-relative duration (120_000 ms) and position (15_000 ms into Chapter 2)
        assertEquals(120_000L, SongPlayer.getDuration())
        assertEquals(15_000L, SongPlayer.getCurrentPosition())

        // Rewind 25s (-25_000 ms) crosses smoothly back into Chapter 1 (50_000 ms absolute)
        SongPlayer.skipBy(-25_000L)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("audible:chapter:B08G9PRS1K:0", SongPlayer.nowPlaying.value.uri)
        assertEquals(60_000L, SongPlayer.getDuration())
        assertEquals(50_000L, SongPlayer.getCurrentPosition())

        // Next chapter jumps to Chapter 2 start (60_000 ms absolute, 0 ms chapter-relative)
        SongPlayer.next()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("audible:chapter:B08G9PRS1K:1", SongPlayer.nowPlaying.value.uri)
        assertEquals(0L, SongPlayer.getCurrentPosition())

        // Playback speed forwards to backend
        SongPlayer.setSpeed(1.25)
        assertEquals(1.25f, fakeBackend.appliedSpeed, 0.001f)

        // Pause persists local progress without WhisperSync
        fakeBackend.seekToAbsolute(92_000L)
        SongPlayer.pause()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(SongPlayer.isPlaying())
        val saved = AudibleProgressStore.get(app, "B08G9PRS1K")
        assertNotNull(saved)
        assertEquals(92_000L, saved!!.positionMs)
        assertEquals(1, saved.chapterIndex)
    }

    @Test
    fun `unapproved Audible book is refused at playSong and live policy revocation immediately stops playback`() {
        approveSampleBook()

        // Unapproved ASIN refused
        SongPlayer.playSong("audible:book:UNAPPROVED99")
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(AudiblePlayer.active.value)

        // Approved ASIN plays
        SongPlayer.playSong("audible:chapter:B08G9PRS1K:2")
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(AudiblePlayer.active.value)
        assertEquals("audible:chapter:B08G9PRS1K:2", SongPlayer.nowPlaying.value.uri)

        // Live parent revocation of B08G9PRS1K immediately stops AudiblePlayer!
        ParentalPolicyStore.setAudibleBookAllowed(app, "B08G9PRS1K", allowed = false)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(AudiblePlayer.active.value)
        assertFalse(fakeBackend.playing)
    }
}
