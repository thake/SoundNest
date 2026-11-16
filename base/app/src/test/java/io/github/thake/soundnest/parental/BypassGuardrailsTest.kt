/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.parental

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.thake.soundnest.api.Album
import io.github.thake.soundnest.api.ArtistRef
import io.github.thake.soundnest.data.NewReleaseNotifier
import io.github.thake.soundnest.data.entity.SongsModel
import io.github.thake.soundnest.di.AutoBrowser
import io.github.thake.soundnest.di.Graph
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BypassGuardrailsTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        Graph::class.java.getDeclaredField("app").apply {
            isAccessible = true
            set(null, context)
        }
        ParentalPolicyStore.resetForTests(context)
        TrustedNetworkGate.resetForTests(context)
    }

    @After
    fun tearDown() {
        ParentalPolicyStore.resetForTests(context)
        TrustedNetworkGate.resetForTests(context)
    }

    @Test
    fun `SpotifyLinkGuard delegates external http links to OS Family Link while filtering Spotify links`() {
        val blockedPlaylist = "1111111111111111111111"
        val allowedAlbum = "2222222222222222222222"
        val showId = "3333333333333333333333"
        val policy = ParentalPolicy(
            filterMode = FilterMode.FILTERED_OPEN,
            blockedPlaylistIds = setOf(blockedPlaylist),
            allowPodcasts = false,
            allowJam = false,
            readOnlySpotifyLibrary = true,
        )

        // 1. Standard external HTTP(S) links pass through untouched for Google Family Link
        val external = SpotifyLinkGuard.evaluate("https://wikipedia.org/wiki/Wolfgang_Amadeus_Mozart", policy)
        assertEquals(LinkDecision.ExternalBrowser("https://wikipedia.org/wiki/Wolfgang_Amadeus_Mozart"), external)

        // 2. User profiles and Blend links are always blocked
        assertTrue(SpotifyLinkGuard.evaluate("https://open.spotify.com/user/someuser123", policy) is LinkDecision.Blocked)
        assertTrue(SpotifyLinkGuard.evaluate("spotify:user:someuser123", policy) is LinkDecision.Blocked)
        assertTrue(SpotifyLinkGuard.evaluate("https://open.spotify.com/blend/taste-match/abc", policy) is LinkDecision.Blocked)

        // 3. Jam links blocked when allowJam == false
        assertTrue(SpotifyLinkGuard.evaluate("https://spotify.link/jam", policy.copy(allowJam = false)).let {
            it is LinkDecision.ResolveShortLink || it is LinkDecision.Blocked
        })
        assertTrue(SpotifyLinkGuard.evaluate("https://open.spotify.com/socialsession/xyz123", policy) is LinkDecision.Blocked)

        // 4. Collaborative invite links (pt=...) blocked when readOnlySpotifyLibrary == true
        assertTrue(
            SpotifyLinkGuard.evaluate(
                "https://open.spotify.com/playlist/$allowedAlbum?si=abc&pt=secretToken123",
                policy,
            ) is LinkDecision.Blocked,
        )

        // 5. Podcast show/episode links blocked when allowPodcasts == false
        assertTrue(SpotifyLinkGuard.evaluate("https://open.spotify.com/show/$showId", policy) is LinkDecision.Blocked)
        assertTrue(SpotifyLinkGuard.evaluate("spotify:episode:$showId", policy) is LinkDecision.Blocked)

        // 6. Blocked playlist link blocked; allowed album link opens in-app
        assertTrue(SpotifyLinkGuard.evaluate("https://open.spotify.com/playlist/$blockedPlaylist", policy) is LinkDecision.Blocked)
        assertEquals(
            LinkDecision.OpenInAppPage("spotify:album:$allowedAlbum"),
            SpotifyLinkGuard.evaluate("https://open.spotify.com/album/$allowedAlbum", policy),
        )
    }

    @Test
    fun `SpotifyLinkGuard enforces strict allowlist on Spotify URIs`() {
        val approvedAlbum = "4444444444444444444444"
        val unapprovedAlbum = "5555555555555555555555"
        val strictPolicy = ParentalPolicy(
            filterMode = FilterMode.STRICT_ALLOWLIST,
            allowedAlbumIds = setOf(approvedAlbum),
        )

        assertTrue(SpotifyLinkGuard.evaluate("spotify:album:$unapprovedAlbum", strictPolicy) is LinkDecision.Blocked)
        assertEquals(
            LinkDecision.OpenInAppPage("spotify:album:$approvedAlbum"),
            SpotifyLinkGuard.evaluate("spotify:album:$approvedAlbum", strictPolicy),
        )
    }

    @Test
    fun `NewReleaseNotifier filters candidate music releases against ParentalFilterEngine`() {
        val cleanRelease = Album(
            uri = "spotify:album:cleanRel111",
            name = "Kids Symphony",
            artists = listOf(ArtistRef("spotify:artist:mozartId", "Mozart Orchestra")),
            images = emptyList(),
            type = "album",
            releaseDate = "2026-01-01",
            trackCount = 10,
        )
        val blockedArtistRelease = Album(
            uri = "spotify:album:badRel222",
            name = "Midnight",
            artists = listOf(ArtistRef("spotify:artist:blockedArtistId", "Blocked Rapper")),
            images = emptyList(),
            type = "album",
            releaseDate = "2026-01-01",
            trackCount = 10,
        )
        val keywordRelease = Album(
            uri = "spotify:album:kwRel333",
            name = "Dr0g3n Nights",
            artists = listOf(ArtistRef("spotify:artist:otherId", "Other Artist")),
            images = emptyList(),
            type = "album",
            releaseDate = "2026-01-01",
            trackCount = 10,
        )

        val openPolicy = ParentalPolicy(
            filterMode = FilterMode.FILTERED_OPEN,
            blockedArtistIds = setOf("blockedArtistId"),
            blockedKeywords = setOf("drogen"),
        )
        val filteredOpen = NewReleaseNotifier.filterCandidateReleases(
            releases = listOf(cleanRelease, blockedArtistRelease, keywordRelease),
            policy = openPolicy,
            whatsNewEnabled = true,
        )
        assertEquals(listOf("spotify:album:cleanRel111"), filteredOpen.map { it.uri })

        // Strict allowlist blocks unapproved artist releases, allows approved artist releases
        val strictPolicy = ParentalPolicy(
            filterMode = FilterMode.STRICT_ALLOWLIST,
            allowedArtistIds = setOf("mozartId"),
        )
        val filteredStrict = NewReleaseNotifier.filterCandidateReleases(
            releases = listOf(cleanRelease, blockedArtistRelease),
            policy = strictPolicy,
            whatsNewEnabled = true,
        )
        assertEquals(listOf("spotify:album:cleanRel111"), filteredStrict.map { it.uri })

        // Suppressed completely when What's New / New Releases chrome is disabled
        assertTrue(
            NewReleaseNotifier.filterCandidateReleases(
                releases = listOf(cleanRelease),
                policy = openPolicy,
                whatsNewEnabled = false,
            ).isEmpty(),
        )
    }

    @Test
    fun `AutoBrowser hides podcasts tab when disabled and voice search picks first allowed track`() = runBlocking {
        // Default policy has allowPodcasts = false -> ROOT tabs contain only home and library
        val defaultTabs = AutoBrowser.children(context, AutoBrowser.ROOT).mapNotNull { it.mediaId }
        assertEquals(listOf("home", "library"), defaultTabs)

        // Enabling podcasts adds the podcasts tab
        ParentalPolicyStore.update(context) { it.copy(allowPodcasts = true) }
        val tabsWithPodcasts = AutoBrowser.children(context, AutoBrowser.ROOT).mapNotNull { it.mediaId }
        assertEquals(listOf("home", "library", "podcasts"), tabsWithPodcasts)

        // Voice search candidate picker skips explicit/blocked tracks and selects first allowed track
        val explicitCandidate = SongsModel(
            id = 1,
            title = "Bad Song",
            album = "Album",
            singer = "Artist",
            coverUri = "",
            url = "spotify:track:exp1|Bad Song",
            spotifyTrackId = "exp1",
            explicit = true,
        )
        val cleanCandidate = SongsModel(
            id = 2,
            title = "Good Song",
            album = "Album",
            singer = "Artist",
            coverUri = "",
            url = "spotify:track:clean2|Good Song",
            spotifyTrackId = "clean2",
            explicit = false,
        )
        val picked = AutoBrowser.selectVoiceSearchCandidate(
            candidates = listOf(explicitCandidate, cleanCandidate),
            policy = ParentalPolicyStore.current(context),
        )
        assertEquals("clean2", picked?.spotifyTrackId)
    }
}
