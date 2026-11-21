/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.parental

import io.github.thake.soundnest.feature.parental.filter.*
import io.github.thake.soundnest.feature.parental.security.*
import io.github.thake.soundnest.feature.parental.settings.*
import io.github.thake.soundnest.feature.parental.ui.*

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import io.github.thake.soundnest.core.catalog.Api
import io.github.thake.soundnest.core.model.AlbumsModel
import io.github.thake.soundnest.core.model.ArtistsModel
import io.github.thake.soundnest.core.model.LibraryEntry
import io.github.thake.soundnest.core.model.PodcastModel
import io.github.thake.soundnest.core.model.SearchResults
import io.github.thake.soundnest.core.model.SongsModel
import io.github.thake.soundnest.feature.recap.settings.RecentItem
import io.github.thake.soundnest.core.ui.classic.SpotuiTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class ParentalQuickBlockTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        ParentalPolicyStore.resetForTests(app)
        ParentSecurityManager.resetForTests(app)
    }

    @After
    fun tearDown() {
        ParentSecurityManager.resetForTests(app)
    }

    @Test
    fun `block and unblock across song, artist, playlist, album and show records labels and strips allowlist`() {
        // Seed allowlist entries first to verify blocking in Strict Allowlist mode removes them cleanly.
        ParentalPolicyStore.allowPlaylist(app, "pl1", title = "Old Playlist")
        ParentalPolicyStore.allowArtist(app, "ar1", title = "Old Artist")
        ParentalPolicyStore.allowAlbum(app, "al1", title = "Loud Album")
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
        assertFalse("sh1" in policy.spokenWord.podcasts.allowedShowIds)

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

    @Test
    fun `filterSearchResults, isLibraryEntryAllowed and isRecentItemAllowed immediately exclude newly blocked items`() {
        val results = SearchResults(
            songs = listOf(
                SongsModel(1, "Good Song", "Good Album", "Good Artist", "", "spotify:track:good_tr", "good_tr", artistIds = listOf("good_ar")),
                SongsModel(2, "Bad Song", "Bad Album", "Bad Artist", "", "spotify:track:bad_tr", "bad_tr", artistIds = listOf("bad_ar")),
            ),
            artists = listOf(
                ArtistsModel(name = "Good Artist", coverUri = "", id = "good_ar"),
                ArtistsModel(name = "Bad Artist", coverUri = "", id = "bad_ar"),
            ),
            albums = listOf(
                AlbumsModel(id = 1, name = "Good Album", artists = "Good Artist", coverUri = "", time = "2024", spotifyId = "good_al"),
                AlbumsModel(id = 2, name = "Bad Album", artists = "Bad Artist", coverUri = "", time = "2024", spotifyId = "bad_al"),
            ),
            playlists = listOf(
                LibraryEntry(spotifyId = "good_pl", name = "Good Playlist", subtitle = "Spotify", coverUri = "", isPlaylist = true, uri = "spotify:playlist:good_pl"),
                LibraryEntry(spotifyId = "bad_pl", name = "Bad Playlist", subtitle = "Spotify", coverUri = "", isPlaylist = true, uri = "spotify:playlist:bad_pl"),
            ),
            shows = listOf(
                PodcastModel(id = "good_sh", name = "Good Show", publisher = "Kids FM", coverUri = ""),
                PodcastModel(id = "bad_sh", name = "Bad Show", publisher = "Late FM", coverUri = ""),
            ),
        )
        ParentalPolicyStore.update(app) { it.copy(spokenWord = it.spokenWord.copy(allowPodcasts = true)) }
        ParentalPolicyStore.blockTrack(app, "bad_tr", title = "Bad Song")
        ParentalPolicyStore.blockArtist(app, "bad_ar", title = "Bad Artist")
        ParentalPolicyStore.blockAlbum(app, "bad_al", title = "Bad Album")
        ParentalPolicyStore.blockPlaylist(app, "bad_pl", title = "Bad Playlist")
        ParentalPolicyStore.blockShow(app, "bad_sh", title = "Bad Show")

        val policy = ParentalPolicyStore.current(app)
        val filtered = ParentBlockActions.filterSearchResults(results, policy)
        assertEquals(listOf("good_tr"), filtered.songs.map { it.spotifyTrackId })
        assertEquals(listOf("good_ar"), filtered.artists.map { it.id })
        assertEquals(listOf("good_al"), filtered.albums.map { it.spotifyId })
        assertEquals(listOf("good_pl"), filtered.playlists.map { it.spotifyId })
        assertEquals(listOf("good_sh"), filtered.shows.map { it.id })

        // Library entries: pinned system entries stay allowed; blocked playlist/show are disallowed
        val likedEntry = LibraryEntry(spotifyId = Api.HomeCache.LIKED_SONGS_ID, name = "Liked Songs", subtitle = "", coverUri = "", isPlaylist = true)
        val blockedShowEntry = LibraryEntry(spotifyId = "${Api.SHOW_PREFIX}bad_sh", name = "Bad Show", subtitle = "Late FM", coverUri = "", isPlaylist = false, uri = "spotify:show:bad_sh")
        assertTrue(ParentBlockActions.isLibraryEntryAllowed(likedEntry, policy))
        assertTrue(ParentBlockActions.isLibraryEntryAllowed(results.playlists[0], policy))
        assertFalse(ParentBlockActions.isLibraryEntryAllowed(results.playlists[1], policy))
        assertFalse(ParentBlockActions.isLibraryEntryAllowed(blockedShowEntry, policy))

        // Recent search items: blocked artist/playlist vanish immediately
        assertTrue(ParentBlockActions.isRecentItemAllowed(RecentItem(type = "artist", key = "good_ar", name = "Good Artist"), policy))
        assertFalse(ParentBlockActions.isRecentItemAllowed(RecentItem(type = "artist", key = "bad_ar", name = "Bad Artist"), policy))
        assertFalse(ParentBlockActions.isRecentItemAllowed(RecentItem(type = "playlist", key = "bad_pl", name = "Bad Playlist"), policy))
    }

    @Test
    fun `ParentInlineBlockPill is hidden when locked and toggles block and unblock in place when unlocked`() {
        val target = ParentBlockTarget(
            kind = ParentRuleKind.PLAYLIST,
            id = "spotify:playlist:ghibli",
            title = "Studio Ghibli Lullabies",
            subtitle = "SoundNest",
        )
        composeRule.setContent {
            SpotuiTheme {
                ParentInlineBlockPill(target = target)
            }
        }
        // Locked by default -> invisible to kids
        assertEquals(0, composeRule.onAllNodesWithText("Block Playlist").fetchSemanticsNodes().size)

        // Unlock Parent Mode -> pill appears immediately
        ParentSecurityManager.setupInitialPin(app, "1234")
        assertTrue(ParentSecurityManager.isAdminUnlocked(app))
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Block Playlist").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertTrue(ParentBlockActions.isBlocked(target, ParentalPolicyStore.current(app)))

        // Flips in-place so accidental taps can be undone without leaving the page
        composeRule.onNodeWithText("Blocked · Tap to Unblock").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertFalse(ParentBlockActions.isBlocked(target, ParentalPolicyStore.current(app)))
        composeRule.onNodeWithText("Block Playlist").assertIsDisplayed()
    }

    @Test
    fun `ParentQuickBlockSheet blocks target and dismisses on tap`() {
        ParentSecurityManager.setupInitialPin(app, "1234")
        assertTrue(ParentSecurityManager.isAdminUnlocked(app))

        val target = ParentBlockTarget(
            kind = ParentRuleKind.ARTIST,
            id = "spotify:artist:holst",
            title = "Gustav Holst",
            subtitle = "Artist",
        )
        var dismissed = false
        composeRule.setContent {
            SpotuiTheme {
                ParentQuickBlockSheet(
                    target = target,
                    onDismiss = { dismissed = true },
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("parent_sheet_block_artist")
            .assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertTrue(dismissed)
        assertTrue(ParentBlockActions.isBlocked(target, ParentalPolicyStore.current(app)))
    }

    @Test
    fun `pick and unpick across song, artist, playlist, album and show records labels and strips blocklist`() {
        // Seed blocklist entries first to verify picking removes them cleanly (disjoint sets).
        ParentalPolicyStore.blockPlaylist(app, "pl2", title = "Blocked Playlist")
        ParentalPolicyStore.blockArtist(app, "ar2", title = "Blocked Artist")
        ParentalPolicyStore.blockAlbum(app, "al2", title = "Blocked Album")
        ParentalPolicyStore.blockTrack(app, "tr2", title = "Blocked Song")
        ParentalPolicyStore.blockShow(app, "sh2", title = "Blocked Show")

        val playlistTarget = ParentBlockTarget(ParentRuleKind.PLAYLIST, "spotify:playlist:pl2", "Bedtime Stories", "Spotify")
        val artistTarget = ParentBlockTarget(ParentRuleKind.ARTIST, "spotify:artist:ar2", "Joe Hisaishi", "Artist")
        val albumTarget = ParentBlockTarget(ParentRuleKind.ALBUM, "spotify:album:al2", "Totoro OST", "Joe Hisaishi")
        val songTarget = ParentBlockTarget(ParentRuleKind.TRACK, "spotify:track:tr2", "Path of the Wind", "Joe Hisaishi")
        val showTarget = ParentBlockTarget(ParentRuleKind.SHOW, "spotify:show:sh2", "Brains On!", "APM")

        ParentBlockActions.pick(app, playlistTarget, showToast = false)
        ParentBlockActions.pick(app, artistTarget, showToast = false)
        ParentBlockActions.pick(app, albumTarget, showToast = false)
        ParentBlockActions.pick(app, songTarget, showToast = false)
        ParentBlockActions.pick(app, showTarget, showToast = false)

        val policy = ParentalPolicyStore.current(app)
        assertTrue(ParentBlockActions.isPicked(playlistTarget, policy))
        assertTrue(ParentBlockActions.isPicked(artistTarget, policy))
        assertTrue(ParentBlockActions.isPicked(albumTarget, policy))
        assertTrue(ParentBlockActions.isPicked(songTarget, policy))
        assertTrue(ParentBlockActions.isPicked(showTarget, policy))

        assertFalse(ParentBlockActions.isBlocked(playlistTarget, policy))
        assertFalse(ParentBlockActions.isBlocked(artistTarget, policy))
        assertFalse(ParentBlockActions.isBlocked(albumTarget, policy))
        assertFalse(ParentBlockActions.isBlocked(songTarget, policy))
        assertFalse(ParentBlockActions.isBlocked(showTarget, policy))

        assertEquals("Bedtime Stories", RuleLabelStore.resolve(app, "playlist", "pl2").title)
        assertEquals("Joe Hisaishi", RuleLabelStore.resolve(app, "artist", "ar2").title)
        assertEquals("Totoro OST", RuleLabelStore.resolve(app, "album", "al2").title)
        assertEquals("Path of the Wind", RuleLabelStore.resolve(app, "track", "tr2").title)
        assertEquals("Brains On!", RuleLabelStore.resolve(app, "show", "sh2").title)

        assertEquals("Pick Song", ParentBlockActions.pickActionLabel(songTarget, picked = false, includeTitle = false))
        assertEquals("Unpick Artist (Joe Hisaishi)", ParentBlockActions.pickActionLabel(artistTarget, picked = true, includeTitle = true))

        // Unpicking clears the pick rule
        ParentBlockActions.unpick(app, playlistTarget, showToast = false)
        ParentBlockActions.unpick(app, artistTarget, showToast = false)
        val afterUnpick = ParentalPolicyStore.current(app)
        assertFalse(ParentBlockActions.isPicked(playlistTarget, afterUnpick))
        assertFalse(ParentBlockActions.isPicked(artistTarget, afterUnpick))
    }

    @Test
    fun `ParentInlineBlockPill toggles Pick and Block pills side by side with mutual exclusion`() {
        val target = ParentBlockTarget(
            kind = ParentRuleKind.ALBUM,
            id = "spotify:album:totoro",
            title = "My Neighbor Totoro",
            subtitle = "Joe Hisaishi",
        )
        composeRule.setContent {
            SpotuiTheme {
                ParentInlineBlockPill(target = target)
            }
        }
        // Locked by default -> both pills hidden
        assertEquals(0, composeRule.onAllNodesWithText("Pick Album").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("Block Album").fetchSemanticsNodes().size)

        // Unlock Parent Mode -> both pills appear
        ParentSecurityManager.setupInitialPin(app, "1234")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("parent_inline_pick_album").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertTrue(ParentBlockActions.isPicked(target, ParentalPolicyStore.current(app)))
        assertFalse(ParentBlockActions.isBlocked(target, ParentalPolicyStore.current(app)))
        composeRule.onNodeWithText("Picked · Tap to Remove").assertIsDisplayed()

        // Tapping Block flips from Picked -> Blocked
        composeRule.onNodeWithTag("parent_inline_block_album").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertFalse(ParentBlockActions.isPicked(target, ParentalPolicyStore.current(app)))
        assertTrue(ParentBlockActions.isBlocked(target, ParentalPolicyStore.current(app)))
        composeRule.onNodeWithText("Pick Album").assertIsDisplayed()
        composeRule.onNodeWithText("Blocked · Tap to Unblock").assertIsDisplayed()
    }

    @Test
    fun `ParentQuickBlockSheet picks target and dismisses on tap`() {
        ParentSecurityManager.setupInitialPin(app, "1234")

        val target = ParentBlockTarget(
            kind = ParentRuleKind.PLAYLIST,
            id = "spotify:playlist:bedtime",
            title = "Bedtime Classics",
            subtitle = "Spotify",
        )
        var dismissed = false
        composeRule.setContent {
            SpotuiTheme {
                ParentQuickBlockSheet(
                    target = target,
                    onDismiss = { dismissed = true },
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("parent_sheet_pick_playlist")
            .assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertTrue(dismissed)
        assertTrue(ParentBlockActions.isPicked(target, ParentalPolicyStore.current(app)))
    }

    @Test
    fun `ParentRuleTargetsSection toggles Pick and Block independently across song, artist, and album`() {
        ParentSecurityManager.setupInitialPin(app, "1234")
        val song = SongsModel(
            id = 42,
            title = "Clair de Lune",
            album = "Suite bergamasque",
            singer = "Claude Debussy",
            coverUri = "",
            url = "spotify:track:tr_clair",
            spotifyTrackId = "tr_clair",
            albumId = "al_suite",
            artistIds = listOf("ar_debussy"),
        )
        val targets = ParentBlockActions.targetsForSong(song)
        composeRule.setContent {
            SpotuiTheme {
                ParentRuleTargetsSection(targets = targets)
            }
        }
        composeRule.waitForIdle()

        // Pick the song and block the album independently
        composeRule.onNodeWithTag("parent_sheet_pick_track").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("parent_sheet_block_album").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        val policy = ParentalPolicyStore.current(app)
        assertTrue(ParentBlockActions.isPicked(targets[0], policy))
        assertFalse(ParentBlockActions.isPicked(targets[1], policy))
        assertTrue(ParentBlockActions.isBlocked(targets[2], policy))

        // Flip album from Blocked -> Picked
        composeRule.onNodeWithTag("parent_sheet_pick_album").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        val updated = ParentalPolicyStore.current(app)
        assertTrue(ParentBlockActions.isPicked(targets[2], updated))
        assertFalse(ParentBlockActions.isBlocked(targets[2], updated))
    }
}
