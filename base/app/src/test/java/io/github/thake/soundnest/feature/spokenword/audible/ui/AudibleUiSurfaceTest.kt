/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.spokenword.audible.ui

import io.github.thake.soundnest.feature.spokenword.audible.settings.AudibleParentalPolicy
import io.github.thake.soundnest.feature.spokenword.settings.SpokenWordParentalPolicy

import io.github.thake.soundnest.feature.spokenword.audible.data.AudibleCatalogCache
import io.github.thake.soundnest.feature.spokenword.audible.data.AudibleProgressStore
import io.github.thake.soundnest.feature.spokenword.audible.settings.AudibleCredentialStore

import io.github.thake.soundnest.audible.*

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.compose.rememberNavController
import io.github.thake.soundnest.core.model.HomeSection
import io.github.thake.soundnest.core.model.withoutPodcastShows
import io.github.thake.soundnest.core.di.Graph
import io.github.thake.soundnest.feature.home.settings.HomeParentalPolicy
import io.github.thake.soundnest.feature.parental.filter.HomeSectionFilter
import io.github.thake.soundnest.feature.parental.security.ParentSecurityManager
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore
import io.github.thake.soundnest.feature.speakers.settings.TrustedNetworkGate
import io.github.thake.soundnest.feature.spokenword.ui.classic.ShowScreen
import io.github.thake.soundnest.core.ui.classic.SpotuiTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp", application = Application::class)
class AudibleUiSurfaceTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        Graph::class.java.getDeclaredField("app").apply { isAccessible = true }.set(null, app)
        ParentSecurityManager.resetForTests(app)
        ParentalPolicyStore.resetForTests(app)
        TrustedNetworkGate.resetForTests(app)
        AudibleCredentialStore.clear(app)
        AudibleCredentialStore.resetMemoryForTests()
        AudibleCatalogCache.clear(app)
        AudibleCatalogCache.resetMemoryForTests()
        AudibleProgressStore.clearAll(app)
        AudibleProgressStore.resetMemoryForTests()
    }

    @After
    fun tearDown() {
        AudibleCredentialStore.clear(app)
        AudibleCatalogCache.clear(app)
        AudibleProgressStore.clearAll(app)
        ParentalPolicyStore.resetForTests(app)
    }

    private fun connectAudible() {
        AudibleCredentialStore.save(
            app,
            AudibleSessionTokens(
                marketplace = AudibleMarketplace.DE,
                deviceSerial = "SERIAL123",
                adpToken = "ADP_TOKEN",
                devicePrivateKeyPem = "PEM",
                refreshToken = "REFRESH",
                accessToken = "ACCESS",
                customerName = "Parent",
            ),
        )
    }

    private val hailMary = AudibleBook(
        asin = "B08G9PRS1K",
        title = "Project Hail Mary",
        authors = listOf("Andy Weir"),
        narrators = listOf("Ray Porter"),
        seriesTitle = "Standalone Sci-Fi",
        runtimeLengthMs = 60_000L,
        coverUrl = "https://example.com/phm.jpg",
        description = "Science adventure.",
    )

    private val momo = AudibleBook(
        asin = "B004V0725Y",
        title = "Momo",
        authors = listOf("Michael Ende"),
        narrators = listOf("Rufus Beck"),
        runtimeLengthMs = 45_000L,
        coverUrl = "https://example.com/momo.jpg",
    )

    @Test
    fun `approvedLibraryEntries returns only approved Audible books with pinned flag`() {
        connectAudible()
        AudibleCatalogCache.saveBooks(app, listOf(hailMary, momo))

        val policy = ParentalPolicy(
            spokenWord = SpokenWordParentalPolicy(
                allowAudiobooks = false,
                allowAudible = true,
                audible = AudibleParentalPolicy(
                    allowedAsins = setOf("B08G9PRS1K", "B004V0725Y"),
                    pinnedAsins = setOf("B004V0725Y"),
                ),
            ),
        )
        val entries = AudibleCatalogCache.approvedLibraryEntries(app, policy)
        assertEquals(listOf("audible:B08G9PRS1K", "audible:B004V0725Y"), entries.map { it.spotifyId })
        assertEquals(listOf(false, true), entries.map { it.pinned })

        val disabledPolicy = policy.copy(spokenWord = policy.spokenWord.copy(allowAudible = false))
        assertTrue(AudibleCatalogCache.approvedLibraryEntries(app, disabledPolicy).isEmpty())
    }

    @Test
    fun `Home Parent Picks surfaces pinned Audible books even when podcasts are disabled`() {
        connectAudible()
        AudibleCatalogCache.saveBooks(app, listOf(hailMary, momo))

        ParentalPolicyStore.update(app) {
            it.copy(
                spokenWord = SpokenWordParentalPolicy(
                    allowPodcasts = false,
                    allowAudiobooks = false,
                    allowAudible = true,
                    audible = AudibleParentalPolicy(
                        allowedAsins = setOf("B08G9PRS1K", "B004V0725Y"),
                        pinnedAsins = setOf("B08G9PRS1K"),
                        showPinnedOnHome = true,
                    ),
                ),
                home = HomeParentalPolicy(
                    showParentPicks = true,
                ),
            )
        }
        val policy = ParentalPolicyStore.current(app)
        val candidates = HomeSectionFilter.cachedLibraryCandidates()
        val sections = HomeSectionFilter.filterSections(
            sections = emptyList<HomeSection>(),
            policy = policy,
            parentPicksCandidates = candidates,
            parentPicksTitle = "Parent Picks",
        ).withoutPodcastShows()

        assertEquals(1, sections.size)
        assertEquals("Parent Picks", sections.first().title)
        assertEquals(listOf("Project Hail Mary"), sections.first().items.map { it.name })

        // Turning off showPinnedAudibleOnHome hides pinned Audible books from Home while keeping them approved
        val hiddenOnHomePolicy = policy.copy(
            spokenWord = policy.spokenWord.copy(
                audible = policy.spokenWord.audible.copy(showPinnedOnHome = false),
            ),
        )
        val hiddenSections = HomeSectionFilter.filterSections(
            sections = emptyList(),
            policy = hiddenOnHomePolicy,
            parentPicksCandidates = candidates,
            parentPicksTitle = "Parent Picks",
        ).withoutPodcastShows()
        assertTrue(hiddenSections.isEmpty())
    }

    @Test
    fun `matchingShows searches approved Audible books by title, author, and series`() {
        connectAudible()
        AudibleCatalogCache.saveBooks(app, listOf(hailMary, momo))

        val policy = ParentalPolicy(
            spokenWord = SpokenWordParentalPolicy(
                allowAudiobooks = false,
                allowAudible = true,
                audible = AudibleParentalPolicy(allowedAsins = setOf("B08G9PRS1K")), // momo NOT approved
            ),
        )

        assertEquals(listOf("audible:B08G9PRS1K"), AudibleCatalogCache.matchingShows(app, "Hail Mary", policy).map { it.id })
        assertEquals(listOf("audible:B08G9PRS1K"), AudibleCatalogCache.matchingShows(app, "Andy Weir", policy).map { it.id })
        assertEquals(listOf("audible:B08G9PRS1K"), AudibleCatalogCache.matchingShows(app, "Standalone", policy).map { it.id })
        // Unapproved book never appears in search
        assertTrue(AudibleCatalogCache.matchingShows(app, "Momo", policy).isEmpty())
        // Disabled Audible gate never returns Audible matches
        assertTrue(AudibleCatalogCache.matchingShows(app, "Hail", policy.copy(spokenWord = policy.spokenWord.copy(allowAudible = false))).isEmpty())
    }

    @Test
    fun `ShowScreen renders approved Audible chapters and hides Spotify Follow button`() {
        connectAudible()
        AudibleCatalogCache.saveBooks(app, listOf(hailMary))
        AudibleCatalogCache.saveChapters(
            app,
            "B08G9PRS1K",
            listOf(
                AudibleChapter(index = 0, title = "Opening Wakeup", startOffsetMs = 0L, lengthMs = 30_000L),
                AudibleChapter(index = 1, title = "Astrophage", startOffsetMs = 30_000L, lengthMs = 30_000L),
            ),
        )
        ParentalPolicyStore.update(app) {
            it.copy(
                spokenWord = SpokenWordParentalPolicy(
                    allowPodcasts = false,
                    allowAudiobooks = false,
                    allowAudible = true,
                    audible = AudibleParentalPolicy(allowedAsins = setOf("B08G9PRS1K")),
                ),
            )
        }

        rule.setContent {
            SpotuiTheme {
                val nav = rememberNavController()
                ShowScreen(navController = nav, showId = "audible:B08G9PRS1K", showName = "Project Hail Mary")
            }
        }
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithText("Opening Wakeup").fetchSemanticsNodes().isNotEmpty()
        }

        rule.onNodeWithText("Project Hail Mary").assertIsDisplayed()
        rule.onNodeWithText("Opening Wakeup").assertIsDisplayed()
        rule.onNodeWithText("Astrophage").assertIsDisplayed()
        // Spotify Follow pill is hidden for Audible books
        rule.onAllNodesWithText("Follow").assertCountEquals(0)
    }
}
