/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.parental

import io.github.thake.soundnest.feature.parental.filter.*
import io.github.thake.soundnest.feature.parental.settings.*
import io.github.thake.soundnest.feature.home.settings.HomeParentalPolicy

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.thake.soundnest.core.model.HomeItem
import io.github.thake.soundnest.core.model.HomeSection
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
class HomeSectionFilterTest {
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

    private fun sampleAlbum(id: String, name: String) = HomeItem.Album(
        id = id,
        name = name,
        imageUrl = "https://example.com/$id.jpg",
        subtitle = "Album",
        artists = "Artist",
    )

    private fun samplePlaylist(id: String, name: String) = HomeItem.Playlist(
        id = id,
        name = name,
        imageUrl = "https://example.com/$id.jpg",
        subtitle = "Playlist",
    )

    @Test
    fun `PARENT_PICKS_URI uses soundnest namespace`() {
        assertEquals("soundnest:section:parent-picks", HomeSectionFilter.PARENT_PICKS_URI)
    }

    @Test
    fun `HomeSection preserves section uri with backwards-compatible default`() {
        val legacy = HomeSection(title = "Made For You", items = listOf(samplePlaylist("pl1", "Mix 1")))
        assertEquals("", legacy.uri)

        val withUri = HomeSection(
            title = "Charts",
            items = listOf(samplePlaylist("pl2", "Top 50")),
            uri = "spotify:section:0JQ5DAob0KOew1FBAMSmBz",
        )
        assertEquals("spotify:section:0JQ5DAob0KOew1FBAMSmBz", withUri.uri)
    }

    @Test
    fun `HomeSectionConfig has kid-safe defaults including hiding Spotify DJ`() {
        val cfg = ParentalPolicy().home
        assertFalse(cfg.minimalHomeMode)
        assertTrue(cfg.showShortcutsGrid)
        assertTrue(cfg.showWhatsNewBell)
        assertTrue(cfg.showFilterChips)
        assertFalse(cfg.showDjCard)
        assertTrue(cfg.showParentPicks)
        assertTrue(cfg.showChartsAndTrending)
        assertTrue(cfg.showMixesAndMadeForYou)
        assertTrue(cfg.showNewReleases)
        assertTrue(cfg.showRecents)
        assertTrue(cfg.hiddenShelfKeys.isEmpty())
    }

    @Test
    fun `DJ card is stripped by default and kept when showDjCard is true`() {
        val djCard = samplePlaylist("37i9dQZF1EYkqdzj48dyYq", "DJ")
        val normalCard = samplePlaylist("pl_clean", "Kids Favorites")
        val djOnlyShelf = HomeSection("DJ Shelf", listOf(djCard))
        val mixedShelf = HomeSection("Jump back in", listOf(djCard, normalCard))

        val defaultFiltered = HomeSectionFilter.filterSections(
            sections = listOf(djOnlyShelf, mixedShelf),
            policy = ParentalPolicy(),
        )
        // djOnlyShelf collapses when empty; mixedShelf keeps only normalCard
        assertEquals(1, defaultFiltered.size)
        assertEquals("Jump back in", defaultFiltered[0].title)
        assertEquals(listOf(normalCard), defaultFiltered[0].items)

        val djAllowedPolicy = ParentalPolicy(home = HomeParentalPolicy(showDjCard = true))
        val withDj = HomeSectionFilter.filterSections(
            sections = listOf(djOnlyShelf, mixedShelf),
            policy = djAllowedPolicy,
        )
        assertEquals(2, withDj.size)
        assertEquals(2, withDj[1].items.size)
    }

    @Test
    fun `category toggles hide Charts, Mixes, New Releases, and Recents independently`() {
        val charts = HomeSection("Trending now", listOf(samplePlaylist("c1", "Top 50")), uri = "spotify:section:charts")
        val popular = HomeSection("Popular albums", listOf(sampleAlbum("c2", "Hit Album")))
        val mixes = HomeSection("Made For You", listOf(samplePlaylist("m1", "Daily Mix 1")))
        val discover = HomeSection("Recommended for today", listOf(sampleAlbum("m2", "Discovery")))
        val releases = HomeSection("New releases for you", listOf(sampleAlbum("n1", "Fresh Drop")))
        val recents = HomeSection("Recently played", listOf(sampleAlbum("r1", "Yesterday")))
        val generic = HomeSection("90s Acoustic", listOf(samplePlaylist("g1", "Unplugged")))
        val all = listOf(charts, popular, mixes, discover, releases, recents, generic)

        val noCharts = HomeSectionFilter.filterSections(
            all,
            ParentalPolicy(home = HomeParentalPolicy(showChartsAndTrending = false)),
        )
        assertEquals(listOf("Made For You", "Recommended for today", "New releases for you", "Recently played", "90s Acoustic"), noCharts.map { it.title })

        val noMixes = HomeSectionFilter.filterSections(
            all,
            ParentalPolicy(home = HomeParentalPolicy(showMixesAndMadeForYou = false)),
        )
        assertEquals(listOf("Trending now", "Popular albums", "New releases for you", "Recently played", "90s Acoustic"), noMixes.map { it.title })

        val noReleases = HomeSectionFilter.filterSections(
            all,
            ParentalPolicy(home = HomeParentalPolicy(showNewReleases = false)),
        )
        assertEquals(listOf("Trending now", "Popular albums", "Made For You", "Recommended for today", "Recently played", "90s Acoustic"), noReleases.map { it.title })

        val noRecents = HomeSectionFilter.filterSections(
            all,
            ParentalPolicy(home = HomeParentalPolicy(showRecents = false)),
        )
        assertEquals(listOf("Trending now", "Popular albums", "Made For You", "Recommended for today", "New releases for you", "90s Acoustic"), noRecents.map { it.title })
    }

    @Test
    fun `per-shelf hiddenShelfKeys matches both section uri and normalized title and supports unhide`() {
        val byUri = HomeSection(
            title = "Viral Hits",
            items = listOf(samplePlaylist("v1", "Viral")),
            uri = "spotify:section:0JQ5DAob0KOew1FBAMSmBz",
        )
        val byTitle = HomeSection(
            title = "  Throwback   Thursday! ",
            items = listOf(samplePlaylist("t1", "Throwback")),
        )
        val kept = HomeSection(
            title = "Classical Focus",
            items = listOf(samplePlaylist("k1", "Piano")),
        )

        ParentalPolicyStore.hideHomeShelf(context, HomeSectionFilter.shelfKey(byUri))
        ParentalPolicyStore.hideHomeShelf(context, HomeSectionFilter.shelfKey(byTitle))

        val afterHide = HomeSectionFilter.filterSections(
            listOf(byUri, byTitle, kept),
            ParentalPolicyStore.current(context),
        )
        assertEquals(listOf("Classical Focus"), afterHide.map { it.title })

        ParentalPolicyStore.unhideHomeShelf(context, HomeSectionFilter.shelfKey(byTitle))
        val afterUnhide = HomeSectionFilter.filterSections(
            listOf(byUri, byTitle, kept),
            ParentalPolicyStore.current(context),
        )
        assertEquals(listOf("  Throwback   Thursday! ", "Classical Focus"), afterUnhide.map { it.title })
    }

    @Test
    fun `minimalHomeMode strips algorithmic shelves while surfacing Parent Picks from approved items`() {
        val approvedPlaylist = samplePlaylist("pl_approved", "Disney Classics")
        val approvedAlbum = sampleAlbum("alb_approved", "Moana Soundtrack")
        val unapprovedPlaylist = samplePlaylist("pl_other", "Random Viral")

        val feed = listOf(
            HomeSection("Trending now", listOf(unapprovedPlaylist)),
            HomeSection("Jump back in", listOf(approvedPlaylist, unapprovedPlaylist)),
        )

        val policy = ParentalPolicy(
            allowedPlaylistIds = setOf("pl_approved"),
            allowedAlbumIds = setOf("alb_approved"),
            home = HomeParentalPolicy(
                minimalHomeMode = true,
                showParentPicks = true,
            ),
        )

        val result = HomeSectionFilter.filterSections(
            sections = feed,
            policy = policy,
            parentPicksCandidates = listOf(approvedAlbum, unapprovedPlaylist),
            parentPicksTitle = "Parent Picks",
        )

        assertEquals(1, result.size)
        assertEquals("Parent Picks", result[0].title)
        assertEquals(setOf("Disney Classics", "Moana Soundtrack"), result[0].items.map { it.name }.toSet())
    }

    @Test
    fun `isWhatsNewAllowed requires bell enabled, new releases enabled, and minimalHomeMode off`() {
        assertTrue(HomeSectionFilter.isWhatsNewAllowed(ParentalPolicy()))
        assertFalse(
            HomeSectionFilter.isWhatsNewAllowed(
                ParentalPolicy(home = HomeParentalPolicy(showWhatsNewBell = false)),
            ),
        )
        assertFalse(
            HomeSectionFilter.isWhatsNewAllowed(
                ParentalPolicy(home = HomeParentalPolicy(showNewReleases = false)),
            ),
        )
        assertFalse(
            HomeSectionFilter.isWhatsNewAllowed(
                ParentalPolicy(home = HomeParentalPolicy(minimalHomeMode = true)),
            ),
        )
    }
}
