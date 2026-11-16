/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.parental

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.thake.soundnest.data.api.Api
import io.github.thake.soundnest.data.entity.HomeItem
import io.github.thake.soundnest.data.entity.LibraryEntry
import io.github.thake.soundnest.data.entity.SongsModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ParentalFilterEngineTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ParentalPolicyStore.resetForTests(context)
    }

    @After
    fun tearDown() {
        ParentalPolicyStore.resetForTests(context)
    }

    @Test
    fun `filtered open mode blocks explicit tracks by default and allows clean tracks`() {
        val policy = ParentalPolicyStore.current(context)
        assertEquals(FilterMode.FILTERED_OPEN, policy.filterMode)
        assertTrue(policy.blockExplicit)

        val cleanSong = song(id = "trackClean111111111111", title = "Here Comes The Sun", explicit = false)
        val explicitSong = song(id = "trackExplicit222222222", title = "Explicit Song", explicit = true)

        assertTrue(ParentalFilterEngine.isTrackAllowed(cleanSong, policy))
        assertFalse(ParentalFilterEngine.isTrackAllowed(explicitSong, policy))

        val relaxed = policy.copy(blockExplicit = false)
        assertTrue(ParentalFilterEngine.isTrackAllowed(explicitSong, relaxed))
    }

    @Test
    fun `text normalization catches diacritics punctuation and leetspeak without false positives on substrings`() {
        val policy = ParentalPolicy(
            filterMode = FilterMode.FILTERED_OPEN,
            blockedKeywords = setOf("shit", "fuck", "drogen", "ass"),
            blockedRegexPatterns = setOf("""18\+"""),
        )

        // Exact & obfuscated matches are blocked
        assertFalse(ParentalFilterEngine.isTrackAllowed(song("t1", title = "Bull \$h1t Anthem"), policy))
        assertFalse(ParentalFilterEngine.isTrackAllowed(song("t2", title = "F@ck Everything"), policy))
        assertFalse(ParentalFilterEngine.isTrackAllowed(song("t3", album = "Dr0g3n Party"), policy))
        assertFalse(ParentalFilterEngine.isTrackAllowed(song("t4", singer = "B@d @\$\$ Crew"), policy))
        assertFalse(ParentalFilterEngine.isTrackAllowed(song("t5", title = "Éxplïcît 18+ Club"), policy))

        // Word-boundary safety: "classic" and "bass" must NOT falsely trigger on "ass"
        assertTrue(ParentalFilterEngine.isTrackAllowed(song("t6", title = "Classical Piano Sonata"), policy))
        assertTrue(ParentalFilterEngine.isTrackAllowed(song("t7", title = "Acoustic Bass Groove", album = "Passionate Strings"), policy))
    }

    @Test
    fun `cascading blocklists block artist and album tracks while explicit track allow overrides`() {
        val blockedArtist = "artistBlocked9999999999"
        val blockedAlbum = "albumBlocked88888888888"
        val whitelistedTrack = "trackAllowedOverride777"

        val policy = ParentalPolicy(
            filterMode = FilterMode.FILTERED_OPEN,
            blockExplicit = true,
            blockedArtistIds = setOf(blockedArtist),
            blockedAlbumIds = setOf(blockedAlbum),
            blockedTrackIds = setOf("trackDirectlyBlocked666"),
            allowedTrackIds = setOf(whitelistedTrack),
        )

        // Track by blocked artist -> blocked
        assertFalse(
            ParentalFilterEngine.isTrackAllowed(
                song("tArtist", artistIds = listOf("cleanArtist", blockedArtist)),
                policy,
            ),
        )
        // Track on blocked album -> blocked
        assertFalse(
            ParentalFilterEngine.isTrackAllowed(
                song("tAlbum", albumId = blockedAlbum),
                policy,
            ),
        )
        // Directly blocked track -> blocked
        assertFalse(
            ParentalFilterEngine.isTrackAllowed(
                song("trackDirectlyBlocked666"),
                policy,
            ),
        )
        // Parent explicitly whitelisted a specific track -> allowed even if on blocked album or marked explicit
        assertTrue(
            ParentalFilterEngine.isTrackAllowed(
                song(whitelistedTrack, albumId = blockedAlbum, explicit = true),
                policy,
            ),
        )
    }

    @Test
    fun `strict allowlist mode fails closed and only permits allowlisted entities or contexts`() {
        val allowedArtist = "artistAllowed1111111111"
        val allowedAlbum = "albumAllowed22222222222"
        val allowedPlaylist = "playlistAllowed33333333"
        val allowedSingleTrack = "trackAllowed44444444444"

        val policy = ParentalPolicy(
            filterMode = FilterMode.STRICT_ALLOWLIST,
            blockExplicit = true,
            allowedArtistIds = setOf(allowedArtist),
            allowedAlbumIds = setOf(allowedAlbum),
            allowedPlaylistIds = setOf(allowedPlaylist),
            allowedTrackIds = setOf(allowedSingleTrack),
        )

        // Unapproved clean track fails closed
        val randomClean = song("randomCleanTrack0000000", title = "Random Song")
        assertFalse(ParentalFilterEngine.isTrackAllowed(randomClean, policy))

        // Approved via track ID, album ID, artist ID, or allowlisted playlist context
        assertTrue(ParentalFilterEngine.isTrackAllowed(song(allowedSingleTrack), policy))
        assertTrue(ParentalFilterEngine.isTrackAllowed(song("tInAlbum", albumId = allowedAlbum), policy))
        assertTrue(ParentalFilterEngine.isTrackAllowed(song("tByArtist", artistIds = listOf(allowedArtist)), policy))
        assertTrue(
            ParentalFilterEngine.isTrackAllowed(
                randomClean,
                policy,
                parentContextUri = "spotify:playlist:$allowedPlaylist",
            ),
        )

        // Even inside an allowlisted playlist, explicit songs remain blocked unless track itself is allowlisted
        val explicitInPlaylist = song("expInPl", explicit = true)
        assertFalse(
            ParentalFilterEngine.isTrackAllowed(
                explicitInPlaylist,
                policy,
                parentContextUri = "spotify:playlist:$allowedPlaylist",
            ),
        )

        // Home items & Library entries filter strictly to allowlist (Liked Songs container stays accessible)
        assertNull(
            ParentalFilterEngine.filterHomeItem(
                HomeItem.Playlist(name = "Top Hits", imageUrl = "", subtitle = "", id = "unapprovedPl"),
                policy,
            ),
        )
        assertNotNull(
            ParentalFilterEngine.filterHomeItem(
                HomeItem.Playlist(name = "Kid Favorites", imageUrl = "", subtitle = "", id = allowedPlaylist),
                policy,
            ),
        )
        val likedEntry = LibraryEntry(
            spotifyId = Api.HomeCache.LIKED_SONGS_ID,
            name = "Liked Songs",
            subtitle = "Playlist",
            coverUri = "",
            isPlaylist = true,
        )
        assertNotNull(ParentalFilterEngine.filterLibraryEntry(likedEntry, policy))
    }

    @Test
    fun `policy store mutations persist across reads and maintain disjoint allow and block sets`() {
        ParentalPolicyStore.blockTrack(context, "spotify:track:abc123")
        assertTrue("abc123" in ParentalPolicyStore.current(context).blockedTrackIds)

        // Allowing the same track removes it from blockedTrackIds and adds to allowedTrackIds
        ParentalPolicyStore.allowTrack(context, "abc123")
        val updated = ParentalPolicyStore.current(context)
        assertTrue("abc123" in updated.allowedTrackIds)
        assertFalse("abc123" in updated.blockedTrackIds)
    }

    private fun song(
        id: String,
        title: String = "Clean Title",
        album: String = "Clean Album",
        singer: String = "Clean Artist",
        explicit: Boolean = false,
        albumId: String = "cleanAlbumId",
        artistIds: List<String> = listOf("cleanArtistId"),
    ) = SongsModel(
        id = id.hashCode(),
        title = title,
        album = album,
        singer = singer,
        coverUri = "",
        url = "spotify:track:$id|$title $singer",
        spotifyTrackId = id,
        explicit = explicit,
        albumId = albumId,
        artistIds = artistIds,
    )
}
