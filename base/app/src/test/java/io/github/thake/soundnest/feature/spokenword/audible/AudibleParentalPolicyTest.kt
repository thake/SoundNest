/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.spokenword.audible

import io.github.thake.soundnest.feature.parental.filter.*
import io.github.thake.soundnest.feature.parental.settings.*
import io.github.thake.soundnest.feature.home.settings.HomeParentalPolicy

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.thake.soundnest.feature.spokenword.audible.data.AudibleUris
import io.github.thake.soundnest.feature.spokenword.audible.settings.AudibleCredentialStore
import io.github.thake.soundnest.audible.AudibleMarketplace
import io.github.thake.soundnest.audible.AudibleSessionTokens
import io.github.thake.soundnest.core.model.HomeItem
import io.github.thake.soundnest.core.model.LibraryEntry
import io.github.thake.soundnest.core.model.SongsModel
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
            it.copy(filterMode = FilterMode.FILTERED_OPEN, spokenWord = it.spokenWord.copy(allowAudiobooks = false, allowAudible = true))
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
            ParentalPolicyStore.update(context) { it.copy(filterMode = mode, spokenWord = it.spokenWord.copy(allowAudiobooks = false, allowAudible = true)) }
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

        // Turning off allowAudible gate immediately blocks the book
        ParentalPolicyStore.update(context) { it.copy(spokenWord = it.spokenWord.copy(allowAudible = false)) }
        assertFalse(ParentalFilterEngine.isShowAllowed("audible:B08G9PRS1K", "Project Hail Mary", isAudiobook = true, policy = ParentalPolicyStore.current(context)))

        // Re-enable gate, then disconnect Audible -> immediately blocked
        ParentalPolicyStore.update(context) { it.copy(spokenWord = it.spokenWord.copy(allowAudible = true)) }
        AudibleCredentialStore.clear(context)
        assertFalse(ParentalFilterEngine.isShowAllowed("audible:B08G9PRS1K", "Project Hail Mary", isAudiobook = true, policy = ParentalPolicyStore.current(context)))
    }

    @Test
    fun `pinnedAudibleAsins is maintained as a strict subset of allowedAudibleAsins`() {
        // Pinning an unallowed book does not pin it unless allowed
        ParentalPolicyStore.setAudibleBookPinned(context, "B08G9PRS1K", pinned = true)
        assertTrue(ParentalPolicyStore.current(context).spokenWord.audible.pinnedAsins.isEmpty())

        // Allow then pin -> both sets contain ASIN
        ParentalPolicyStore.setAudibleBookAllowed(context, "B08G9PRS1K", allowed = true)
        ParentalPolicyStore.setAudibleBookPinned(context, "B08G9PRS1K", pinned = true)
        assertEquals(setOf("B08G9PRS1K"), ParentalPolicyStore.current(context).spokenWord.audible.allowedAsins)
        assertEquals(setOf("B08G9PRS1K"), ParentalPolicyStore.current(context).spokenWord.audible.pinnedAsins)

        // Revoking allow automatically unpins from Home
        ParentalPolicyStore.setAudibleBookAllowed(context, "B08G9PRS1K", allowed = false)
        assertTrue(ParentalPolicyStore.current(context).spokenWord.audible.allowedAsins.isEmpty())
        assertTrue(ParentalPolicyStore.current(context).spokenWord.audible.pinnedAsins.isEmpty())
    }

    @Test
    fun `Home Parent Picks shows only pinned Audible books and respects showPinnedAudibleOnHome toggle`() {
        connectAudible()
        ParentalPolicyStore.update(context) {
            it.copy(
                spokenWord = it.spokenWord.copy(
                    allowAudiobooks = false,
                    allowAudible = true,
                    audible = it.spokenWord.audible.copy(showPinnedOnHome = true),
                ),
                home = it.home.copy(
                    minimalHomeMode = true,
                    showParentPicks = true,
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
            it.copy(spokenWord = it.spokenWord.copy(audible = it.spokenWord.audible.copy(showPinnedOnHome = false)))
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
        ParentalPolicyStore.update(context) { it.copy(spokenWord = it.spokenWord.copy(allowAudiobooks = false, allowAudible = true)) }
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

    @Test
    fun `independent toggles guarantee complete Spotify audiobooks isolation when Audible is enabled`() {
        connectAudible()
        ParentalPolicyStore.update(context) {
            it.copy(
                filterMode = FilterMode.FILTERED_OPEN,
                spokenWord = it.spokenWord.copy(
                    allowAudiobooks = false,
                    allowAudible = true,
                ),
            )
        }
        ParentalPolicyStore.setAudibleBookAllowed(context, "B08G9PRS1K", allowed = true)
        val policy = ParentalPolicyStore.current(context)

        // Audible is allowed
        assertTrue(ParentalFilterEngine.isShowAllowed("audible:B08G9PRS1K", "Project Hail Mary", isAudiobook = true, policy = policy))
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
                policy,
                isAudiobookChapter = true,
            ),
        )

        // Spotify audiobook show is blocked
        assertFalse(ParentalFilterEngine.isShowAllowed("spotify:show:7vK70cstG7i980MaoZ8z74", "Spotify Audiobook", isAudiobook = true, policy = policy))

        // Spotify chapter track is blocked
        assertFalse(
            ParentalFilterEngine.isTrackAllowed(
                SongsModel(
                    id = 2,
                    title = "Chapter 1",
                    album = "Spotify Audiobook",
                    singer = "Author",
                    coverUri = "",
                    url = "spotify:chapter:6qU9d7M7Q24b80k4J79o01",
                    showId = "7vK70cstG7i980MaoZ8z74",
                ),
                policy,
                isAudiobookChapter = true,
            ),
        )

        // Spotify audiobook LibraryEntry is blocked
        val spotifyLibEntry = LibraryEntry(
            spotifyId = "7vK70cstG7i980MaoZ8z74",
            name = "Spotify Audiobook",
            subtitle = "Author",
            coverUri = "",
            isPlaylist = false,
            uri = "spotify:audiobook:7vK70cstG7i980MaoZ8z74",
        )
        assertNull(ParentalFilterEngine.filterLibraryEntry(spotifyLibEntry, policy))

        // Spotify chapter LibraryEntry is blocked
        val spotifyChapterEntry = LibraryEntry(
            spotifyId = "6qU9d7M7Q24b80k4J79o01",
            name = "Chapter 1",
            subtitle = "Author",
            coverUri = "",
            isPlaylist = false,
            uri = "spotify:chapter:6qU9d7M7Q24b80k4J79o01",
        )
        assertNull(ParentalFilterEngine.filterLibraryEntry(spotifyChapterEntry, policy))

        // Spotify audiobook link is blocked
        assertTrue(SpotifyLinkGuard.evaluate("spotify:audiobook:7vK70cstG7i980MaoZ8z74", policy) is LinkDecision.Blocked)
        assertTrue(SpotifyLinkGuard.evaluate("spotify:chapter:6qU9d7M7Q24b80k4J79o01", policy) is LinkDecision.Blocked)

        // Disabling allowAudible immediately revokes Audible without affecting Spotify state
        ParentalPolicyStore.update(context) { it.copy(spokenWord = it.spokenWord.copy(allowAudible = false)) }
        val disabledPolicy = ParentalPolicyStore.current(context)
        assertFalse(ParentalFilterEngine.isAudibleBookAllowed("B08G9PRS1K", disabledPolicy))
        assertFalse(ParentalFilterEngine.isShowAllowed("audible:B08G9PRS1K", "Project Hail Mary", isAudiobook = true, policy = disabledPolicy))
    }
}
