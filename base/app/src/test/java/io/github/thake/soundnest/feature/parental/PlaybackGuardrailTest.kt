/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.parental

import io.github.thake.soundnest.feature.parental.filter.*
import io.github.thake.soundnest.feature.parental.settings.*

import io.github.thake.soundnest.core.model.SongsModel
import io.github.thake.soundnest.core.playback.SongPlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlaybackGuardrailTest {

    @Test
    fun `sanitizeQueue strips explicit and blocked items from current queued and nextUp`() {
        val policy = ParentalPolicy(
            filterMode = FilterMode.FILTERED_OPEN,
            blockExplicit = true,
            blockedArtistIds = setOf("badArtist"),
        )
        val clean1 = song("clean1", "Sunny Day")
        val explicit = song("exp1", "Explicit Track", explicit = true)
        val blockedArtistSong = song("bad1", "Sneaky Song", artistIds = listOf("badArtist"))
        val clean2 = song("clean2", "Ocean Waves")

        val rawQueue = SongPlayer.EngineQueue(
            current = explicit,
            queued = listOf(
                SongPlayer.QueueItem(clean1, "spotify:track:clean1", "u1", ""),
                SongPlayer.QueueItem(blockedArtistSong, "spotify:track:bad1", "u2", ""),
            ),
            nextUp = listOf(
                SongPlayer.QueueItem(explicit, "spotify:track:exp1", "u3", ""),
                SongPlayer.QueueItem(clean2, "spotify:track:clean2", "u4", ""),
            ),
        )

        val sanitized = PlaybackGuardrail.sanitizeQueue(rawQueue, policy)
        assertNull(sanitized.current)
        assertEquals(listOf("clean1"), sanitized.queued.map { it.song.spotifyTrackId })
        assertEquals(listOf("clean2"), sanitized.nextUp.map { it.song.spotifyTrackId })
    }

    @Test
    fun `local playback guardrail skips or pauses when disallowed track starts on phone`() {
        val policy = ParentalPolicy(
            filterMode = FilterMode.FILTERED_OPEN,
            blockExplicit = true,
        )
        val explicit = song("expLocal", "Explicit Song", explicit = true)
        val clean = song("cleanLocal", "Clean Song", explicit = false)

        assertEquals(
            LivePlaybackDecision.BLOCK_AND_SKIP_OR_PAUSE,
            PlaybackGuardrail.evaluateLivePlayback(
                item = explicit,
                isRemoteActive = false,
                isRemoteDeviceAllowed = true,
                policy = policy,
            ),
        )

        assertEquals(
            LivePlaybackDecision.ALLOW,
            PlaybackGuardrail.evaluateLivePlayback(
                item = clean,
                isRemoteActive = false,
                isRemoteDeviceAllowed = true,
                policy = policy,
            ),
        )
    }

    @Test
    fun `remote Connect mirroring is suppressed when shared account plays disallowed song or untrusted speaker`() {
        val policy = ParentalPolicy(
            filterMode = FilterMode.FILTERED_OPEN,
            blockExplicit = true,
        )
        val explicit = song("expRemote", "Adult Track", explicit = true)
        val clean = song("cleanRemote", "Family Track", explicit = false)

        // Parent playing explicit song on another device on the shared Spotify account:
        // must NOT mirror into child's MiniPlayer / MediaSession notification, and must NOT interfere with parent's speaker!
        assertEquals(
            LivePlaybackDecision.SUPPRESS_REMOTE_MIRROR,
            PlaybackGuardrail.evaluateLivePlayback(
                item = explicit,
                isRemoteActive = true,
                isRemoteDeviceAllowed = true,
                policy = policy,
            ),
        )

        // Remote speaker not allowed on current network (e.g. child on cellular while home speaker plays):
        // suppress remote mirroring even for clean tracks!
        assertEquals(
            LivePlaybackDecision.SUPPRESS_REMOTE_MIRROR,
            PlaybackGuardrail.evaluateLivePlayback(
                item = clean,
                isRemoteActive = true,
                isRemoteDeviceAllowed = false,
                policy = policy,
            ),
        )

        // Allowed speaker on Trusted Home Wi-Fi playing an allowed track: mirror normally
        assertEquals(
            LivePlaybackDecision.ALLOW,
            PlaybackGuardrail.evaluateLivePlayback(
                item = clean,
                isRemoteActive = true,
                isRemoteDeviceAllowed = true,
                policy = policy,
            ),
        )
    }

    @Test
    fun `ads on Spotify Free accounts are never blocked or skipped even under strict allowlist`() {
        val strictPolicy = ParentalPolicy(
            filterMode = FilterMode.STRICT_ALLOWLIST,
            blockExplicit = true,
            allowedTrackIds = emptySet(),
        )
        val adPlaceholder = SongsModel(
            id = "ad1".hashCode(),
            title = "Advertisement",
            album = "",
            singer = "Spotify",
            coverUri = "",
            url = "spotify:ad:ad1|Advertisement",
            spotifyTrackId = "",
        )

        assertEquals(
            LivePlaybackDecision.ALLOW,
            PlaybackGuardrail.evaluateLivePlayback(
                item = adPlaceholder,
                isRemoteActive = false,
                isRemoteDeviceAllowed = true,
                policy = strictPolicy,
                isAd = true,
            ),
        )
    }

    @Test
    fun `NativeAudio only activates for confirmed Premium accounts so Free accounts play ads via WebPlayerHost`() {
        val now = 100_000L
        // Free account -> false (WebPlayerHost handles playback + ads)
        org.junit.Assert.assertFalse(
            io.github.thake.soundnest.core.playback.NativeAudio.shouldUseNativeEngine(
                enabled = true,
                premium = false,
                nowMs = now,
                asideUntilMs = 0L,
            ),
        )
        // Unknown plan -> false until confirmed Premium
        org.junit.Assert.assertFalse(
            io.github.thake.soundnest.core.playback.NativeAudio.shouldUseNativeEngine(
                enabled = true,
                premium = null,
                nowMs = now,
                asideUntilMs = 0L,
            ),
        )
        // Confirmed Premium -> true
        org.junit.Assert.assertTrue(
            io.github.thake.soundnest.core.playback.NativeAudio.shouldUseNativeEngine(
                enabled = true,
                premium = true,
                nowMs = now,
                asideUntilMs = 0L,
            ),
        )
        // Confirmed Premium but NativeAudio disabled or set aside -> false
        org.junit.Assert.assertFalse(
            io.github.thake.soundnest.core.playback.NativeAudio.shouldUseNativeEngine(
                enabled = false,
                premium = true,
                nowMs = now,
                asideUntilMs = 0L,
            ),
        )
        org.junit.Assert.assertFalse(
            io.github.thake.soundnest.core.playback.NativeAudio.shouldUseNativeEngine(
                enabled = true,
                premium = true,
                nowMs = now,
                asideUntilMs = now + 60_000L,
            ),
        )
    }

    private fun song(
        id: String,
        title: String,
        explicit: Boolean = false,
        artistIds: List<String> = listOf("goodArtist"),
    ) = SongsModel(
        id = id.hashCode(),
        title = title,
        album = "Album",
        singer = "Artist",
        coverUri = "",
        url = "spotify:track:$id|$title",
        spotifyTrackId = id,
        explicit = explicit,
        artistIds = artistIds,
    )
}
