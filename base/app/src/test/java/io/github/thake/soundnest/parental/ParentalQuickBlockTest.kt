/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.parental

import android.app.Application
import io.github.thake.soundnest.data.entity.SongsModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ParentalQuickBlockTest {

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        ParentalPolicyStore.resetForTests(app)
    }

    @Test
    fun `block and unblock across song, artist, playlist, album and show records labels and strips allowlist`() {
        // Seed allowlist entries first to verify blocking in Strict Allowlist mode removes them cleanly.
        ParentalPolicyStore.allowPlaylist(app, "pl1", title = "Old Playlist")
        ParentalPolicyStore.allowArtist(app, "ar1", title = "Old Artist")
        ParentalPolicyStore.allowAlbum(app, "al1", title = "Old Album")
        ParentalPolicyStore.allowTrack(app, "tr1", title = "Old Song")
        ParentalPolicyStore.allowShow(app, "sh1", title = "Old Show")

        val playlistTarget = ParentBlockTarget(ParentRuleKind.PLAYLIST, "spotify:playlist:pl1", "Kids Party Mix", "Spotify")
        val artistTarget = ParentBlockTarget(ParentRuleKind.ARTIST, "spotify:artist:ar1", "Noisy Band", "Artist")
        val albumTarget = ParentBlockTarget(ParentRuleKind.ALBUM, "spotify:album:al1", "Loud Album", "Noisy Band")
        val songTarget = ParentBlockTarget(ParentRuleKind.TRACK, "spotify:track:tr1", "Bad Song", "Noisy Band")
        val showTarget = ParentBlockTarget(ParentRuleKind.SHOW, "spotify:show:sh1", "Late Night Talk", "Podcast Studio")

        ParentBlockActions.block(app, playlistTarget, showToast = false)
        ParentBlockActions.block(app, artistTarget, showToast = false)
        ParentBlockActions.block(app, albumTarget, showToast = false)
        ParentBlockActions.block(app, songTarget, showToast = false)
        ParentBlockActions.block(app, showTarget, showToast = false)

        val policy = ParentalPolicyStore.current(app)
        assertTrue(ParentBlockActions.isBlocked(playlistTarget, policy))
        assertTrue(ParentBlockActions.isBlocked(artistTarget, policy))
        assertTrue(ParentBlockActions.isBlocked(albumTarget, policy))
        assertTrue(ParentBlockActions.isBlocked(songTarget, policy))
        assertTrue(ParentBlockActions.isBlocked(showTarget, policy))

        assertFalse("pl1" in policy.allowedPlaylistIds)
        assertFalse("ar1" in policy.allowedArtistIds)
        assertFalse("al1" in policy.allowedAlbumIds)
        assertFalse("tr1" in policy.allowedTrackIds)
        assertFalse("sh1" in policy.allowedShowIds)

        assertEquals("Kids Party Mix", RuleLabelStore.resolve(app, "playlist", "pl1").title)
        assertEquals("Noisy Band", RuleLabelStore.resolve(app, "artist", "ar1").title)
        assertEquals("Loud Album", RuleLabelStore.resolve(app, "album", "al1").title)
        assertEquals("Bad Song", RuleLabelStore.resolve(app, "track", "tr1").title)
        assertEquals("Late Night Talk", RuleLabelStore.resolve(app, "show", "sh1").title)

        // Unblocking clears the block rule
        ParentBlockActions.unblock(app, playlistTarget, showToast = false)
        ParentBlockActions.unblock(app, artistTarget, showToast = false)
        val afterUnblock = ParentalPolicyStore.current(app)
        assertFalse(ParentBlockActions.isBlocked(playlistTarget, afterUnblock))
        assertFalse(ParentBlockActions.isBlocked(artistTarget, afterUnblock))
    }

    @Test
    fun `artistTargetsForSong pairs single and multiple artist IDs with singer names`() {
        val soloSong = SongsModel(
            id = 1,
            title = "Clair de Lune",
            album = "Suite bergamasque",
            singer = "Claude Debussy",
            coverUri = "https://example.com/c.jpg",
            url = "spotify:track:t1",
            spotifyTrackId = "t1",
            artistIds = listOf("debussy1"),
        )
        val soloTargets = ParentBlockActions.artistTargetsForSong(soloSong)
        assertEquals(1, soloTargets.size)
        assertEquals("debussy1", soloTargets[0].normalizedId)
        assertEquals("Claude Debussy", soloTargets[0].title)

        val collabSong = SongsModel(
            id = 2,
            title = "Jupiter",
            album = "The Planets",
            singer = "Gustav Holst, London Symphony Orchestra",
            coverUri = "https://example.com/p.jpg",
            url = "spotify:track:t2",
            spotifyTrackId = "t2",
            artistIds = listOf("holst1", "lso2"),
        )
        val collabTargets = ParentBlockActions.artistTargetsForSong(collabSong)
        assertEquals(2, collabTargets.size)
        assertEquals("Gustav Holst", collabTargets[0].title)
        assertEquals("London Symphony Orchestra", collabTargets[1].title)
    }

    @Test
    fun `blocking a playlist or show context immediately marks tracks in that context disallowed`() {
        val cleanTrack = SongsModel(
            id = 10,
            title = "Clean Song",
            album = "Clean Album",
            singer = "Clean Artist",
            coverUri = "",
            url = "spotify:track:clean1",
            spotifyTrackId = "clean1",
            albumId = "cleanAlbum",
            artistIds = listOf("cleanArtist"),
        )
        val initial = ParentalPolicyStore.current(app)
        assertTrue(ParentalFilterEngine.isTrackAllowed(cleanTrack, initial, parentContextUri = "spotify:playlist:party99"))

        ParentalPolicyStore.blockPlaylist(app, "party99", title = "Blocked Party Playlist")
        val updated = ParentalPolicyStore.current(app)
        assertTrue(ParentalFilterEngine.isContextBlocked("spotify:playlist:party99", updated))
        assertFalse(ParentalFilterEngine.isTrackAllowed(cleanTrack, updated, parentContextUri = "spotify:playlist:party99"))
        // Track itself remains playable outside the blocked playlist context
        assertTrue(ParentalFilterEngine.isTrackAllowed(cleanTrack, updated, parentContextUri = "spotify:album:cleanAlbum"))
        assertNotNull(RuleLabelStore.resolve(app, "playlist", "party99"))
    }

    @Test
    fun `targetsForSong builds song, artist and album targets for music and episode plus show targets for podcasts`() {
        val musicSong = SongsModel(
            id = 20,
            title = "Clair de Lune",
            album = "Suite bergamasque",
            singer = "Claude Debussy",
            coverUri = "https://example.com/c.jpg",
            url = "spotify:track:tr_clair",
            spotifyTrackId = "tr_clair",
            albumId = "al_suite",
            artistIds = listOf("ar_debussy"),
        )
        val musicTargets = ParentBlockActions.targetsForSong(musicSong)
        assertEquals(
            listOf(ParentRuleKind.TRACK, ParentRuleKind.ARTIST, ParentRuleKind.ALBUM),
            musicTargets.map { it.kind },
        )
        assertEquals("Block Song", ParentBlockActions.actionLabel(musicTargets[0], blocked = false, includeTitle = false))
        assertEquals("Block Artist (Claude Debussy)", ParentBlockActions.actionLabel(musicTargets[1], blocked = false, includeTitle = true))
        assertEquals("Block Album (Suite bergamasque)", ParentBlockActions.actionLabel(musicTargets[2], blocked = false, includeTitle = true))

        val episodeSong = SongsModel(
            id = 21,
            title = "Why Is the Sky Blue?",
            album = "Brains On! Science Podcast",
            singer = "American Public Media",
            coverUri = "https://example.com/e.jpg",
            url = "episode:ep_sky|https://cdn.example.com/audio.mp3",
            spotifyTrackId = "ep_sky",
            showId = "__show__:sh_brains",
        )
        val episodeTargets = ParentBlockActions.targetsForSong(episodeSong)
        assertEquals(
            listOf(ParentRuleKind.EPISODE, ParentRuleKind.SHOW),
            episodeTargets.map { it.kind },
        )
        assertEquals("ep_sky", episodeTargets[0].normalizedId)
        assertEquals("sh_brains", episodeTargets[1].normalizedId)
        assertEquals("Block Episode", ParentBlockActions.actionLabel(episodeTargets[0], blocked = false, includeTitle = false))
        assertEquals("Block Podcast Show (Brains On! Science Podcast)", ParentBlockActions.actionLabel(episodeTargets[1], blocked = false, includeTitle = true))
    }
}
