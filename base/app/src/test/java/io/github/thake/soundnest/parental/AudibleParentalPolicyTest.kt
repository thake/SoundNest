/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.parental

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.thake.soundnest.audible.AudibleCredentialStore
import io.github.thake.soundnest.audible.AudibleMarketplace
import io.github.thake.soundnest.audible.AudibleSessionTokens
import io.github.thake.soundnest.audible.AudibleUris
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
class AudibleParentalPolicyTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ParentalPolicyStore.resetForTests(context)
        AudibleCredentialStore.clear(context)
    }

    @After
    fun tearDown() {
        ParentalPolicyStore.resetForTests(context)
        AudibleCredentialStore.clear(context)
    }

    private fun connectAudible() {
        AudibleCredentialStore.save(
            context,
            AudibleSessionTokens(
                marketplace = AudibleMarketplace.DE,
                deviceSerial = "SERIAL123",
                adpToken = "ADP_SECRET",
                devicePrivateKeyPem = "PEM_SECRET",
                refreshToken = "REFRESH_SECRET",
            ),
        )
    }

    @Test
    fun `audible books are fail-closed in BOTH Filtered Open and Strict Allowlist unless explicitly allowed and connected`() {
        connectAudible()
        ParentalPolicyStore.update(context) {
            it.copy(filterMode = FilterMode.FILTERED_OPEN, allowAudiobooks = true)
        }

        // Unapproved ASIN must NOT pass even in FILTERED_OPEN
        val openPolicy = ParentalPolicyStore.current(context)
        assertFalse(ParentalFilterEngine.isShowAllowed("audible:B08G9PRS1K", "Project Hail Mary", isAudiobook = true, policy = openPolicy))
        assertFalse(
            ParentalFilterEngine.isTrackAllowed(
                SongsModel(
                    id = 1,
                    title = "Chapter 1",
                    album = "Project Hail Mary",
                    singer = "Andy Weir",
                    coverUri = "",
                    url = "audible:chapter:B08G9PRS1K:0",
                    showId = "audible:B08G9PRS1K",
                ),
                openPolicy,
                isAudiobookChapter = true,
            ),
        )

        // Explicitly allow B08G9PRS1K -> allowed in both FILTERED_OPEN and STRICT_ALLOWLIST
        ParentalPolicyStore.setAudibleBookAllowed(context, "B08G9PRS1K", allowed = true, title = "Project Hail Mary", subtitle = "Andy Weir")

        for (mode in listOf(FilterMode.FILTERED_OPEN, FilterMode.STRICT_ALLOWLIST)) {
            ParentalPolicyStore.update(context) { it.copy(filterMode = mode, allowAudiobooks = true) }
            val pol = ParentalPolicyStore.current(context)
            assertTrue(ParentalFilterEngine.isShowAllowed("audible:B08G9PRS1K", "Project Hail Mary", isAudiobook = true, policy = pol))
            assertTrue(
                ParentalFilterEngine.isTrackAllowed(
                    SongsModel(
                        id = 1,
                        title = "Chapter 1",
                        album = "Project Hail Mary",
                        singer = "Andy Weir",
                        coverUri = "",
                        url = "audible:chapter:B08G9PRS1K:0",
                        showId = "audible:B08G9PRS1K",
                    ),
                    pol,
                    isAudiobookChapter = true,
                ),
            )
            val libEntry = LibraryEntry(
                spotifyId = AudibleUris.showId("B08G9PRS1K"),
                name = "Project Hail Mary",
                subtitle = "Audible · Andy Weir",
                coverUri = "",
                isPlaylist = false,
                uri = AudibleUris.bookUri("B08G9PRS1K"),
            )
            assertNotNull(ParentalFilterEngine.filterLibraryEntry(libEntry, pol))
        }

        // Turning off allowAudiobooks gate immediately blocks the book
        ParentalPolicyStore.update(context) { it.copy(allowAudiobooks = false) }
        assertFalse(ParentalFilterEngine.isShowAllowed("audible:B08G9PRS1K", "Project Hail Mary", isAudiobook = true, policy = ParentalPolicyStore.current(context)))

        // Re-enable gate, then disconnect Audible -> immediately blocked
        ParentalPolicyStore.update(context) { it.copy(allowAudiobooks = true) }
        AudibleCredentialStore.clear(context)
        assertFalse(ParentalFilterEngine.isShowAllowed("audible:B08G9PRS1K", "Project Hail Mary", isAudiobook = true, policy = ParentalPolicyStore.current(context)))
    }

    @Test
    fun `pinnedAudibleAsins is maintained as a strict subset of allowedAudibleAsins`() {
        // Pinning an unallowed book does not pin it unless allowed
        ParentalPolicyStore.setAudibleBookPinned(context, "B08G9PRS1K", pinned = true)
        assertTrue(ParentalPolicyStore.current(context).pinnedAudibleAsins.isEmpty())

        // Allow then pin -> both sets contain ASIN
        ParentalPolicyStore.setAudibleBookAllowed(context, "B08G9PRS1K", allowed = true)
        ParentalPolicyStore.setAudibleBookPinned(context, "B08G9PRS1K", pinned = true)
        assertEquals(setOf("B08G9PRS1K"), ParentalPolicyStore.current(context).allowedAudibleAsins)
        assertEquals(setOf("B08G9PRS1K"), ParentalPolicyStore.current(context).pinnedAudibleAsins)

        // Revoking allow automatically unpins from Home
        ParentalPolicyStore.setAudibleBookAllowed(context, "B08G9PRS1K", allowed = false)
        assertTrue(ParentalPolicyStore.current(context).allowedAudibleAsins.isEmpty())
        assertTrue(ParentalPolicyStore.current(context).pinnedAudibleAsins.isEmpty())
    }

    @Test
    fun `Home Parent Picks shows only pinned Audible books and respects showPinnedAudibleOnHome toggle`() {
        connectAudible()
        ParentalPolicyStore.update(context) {
            it.copy(
                allowAudiobooks = true,
                homeSections = it.homeSections.copy(
                    minimalHomeMode = true,
                    showParentPicks = true,
                    showPinnedAudibleOnHome = true,
                ),
            )
        }
        ParentalPolicyStore.setAudibleBookAllowed(context, "B001ALLOWONLY", allowed = true)
        ParentalPolicyStore.setAudibleBookAllowed(context, "B002PINNED", allowed = true)
        ParentalPolicyStore.setAudibleBookPinned(context, "B002PINNED", pinned = true)

        val candidates = listOf(
            HomeItem.Show(id = "audible:B001ALLOWONLY", name = "Library Only Book", imageUrl = "", subtitle = "Author A", isEpisode = false),
            HomeItem.Show(id = "audible:B002PINNED", name = "Pinned Home Book", imageUrl = "", subtitle = "Author B", isEpisode = false),
        )

        val shelvesWhenEnabled = HomeSectionFilter.filterSections(
            sections = emptyList(),
            policy = ParentalPolicyStore.current(context),
            parentPicksCandidates = candidates,
        )
        assertEquals(1, shelvesWhenEnabled.size)
        assertEquals(listOf("audible:B002PINNED"), shelvesWhenEnabled.single().items.map { (it as HomeItem.Show).id })

        // Turning off showPinnedAudibleOnHome hides Audible books from Home while keeping them allowed in Library
        ParentalPolicyStore.update(context) {
            it.copy(homeSections = it.homeSections.copy(showPinnedAudibleOnHome = false))
        }
        val shelvesWhenHomeToggleOff = HomeSectionFilter.filterSections(
            sections = emptyList(),
            policy = ParentalPolicyStore.current(context),
            parentPicksCandidates = candidates,
        )
        assertTrue(shelvesWhenHomeToggleOff.isEmpty())
        assertTrue(ParentalFilterEngine.isShowAllowed("audible:B002PINNED", "Pinned Home Book", isAudiobook = true, policy = ParentalPolicyStore.current(context)))
    }

    @Test
    fun `SpotifyLinkGuard enforces Audible policy on internal audible URIs and delegates audible web links externally`() {
        connectAudible()
        ParentalPolicyStore.update(context) { it.copy(allowAudiobooks = true) }
        ParentalPolicyStore.setAudibleBookAllowed(context, "B08G9PRS1K", allowed = true)

        val pol = ParentalPolicyStore.current(context)
        assertEquals(
            LinkDecision.OpenInAppPage("audible:book:B08G9PRS1K"),
            SpotifyLinkGuard.evaluate("audible:book:B08G9PRS1K", pol),
        )
        assertTrue(SpotifyLinkGuard.evaluate("audible:book:UNAPPROVED99", pol) is LinkDecision.Blocked)
        assertEquals(
            LinkDecision.ExternalBrowser("https://www.audible.de/pd/B08G9PRS1K"),
            SpotifyLinkGuard.evaluate("https://www.audible.de/pd/B08G9PRS1K", pol),
        )
    }
}
