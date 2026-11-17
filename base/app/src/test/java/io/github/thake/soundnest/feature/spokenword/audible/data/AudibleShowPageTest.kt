/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.spokenword.audible.data

import io.github.thake.soundnest.feature.spokenword.audible.settings.AudibleParentalPolicy
import io.github.thake.soundnest.feature.spokenword.settings.SpokenWordParentalPolicy

import io.github.thake.soundnest.feature.spokenword.audible.settings.AudibleCredentialStore

import io.github.thake.soundnest.audible.*

import android.app.Application
import io.github.thake.soundnest.core.catalog.Api
import io.github.thake.soundnest.core.catalog.Response
import io.github.thake.soundnest.core.playback.CurrentSongState
import io.github.thake.soundnest.core.di.Graph
import io.github.thake.soundnest.feature.parental.security.ParentSecurityManager
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore
import io.github.thake.soundnest.feature.speakers.settings.TrustedNetworkGate
import io.github.thake.soundnest.core.catalog.AppRepository
import io.github.thake.soundnest.feature.spokenword.ui.ShowViewModel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AudibleShowPageTest {

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

    private fun sampleBook(asin: String = "B08G9PRS1K") = AudibleBook(
        asin = asin,
        title = "Project Hail Mary",
        subtitle = "A Novel",
        authors = listOf("Andy Weir"),
        narrators = listOf("Ray Porter"),
        runtimeLengthMs = 90_000L,
        coverUrl = "https://example.com/phm.jpg",
        description = "<p>Ryland Grace wakes up alone.</p>",
    )

    private fun sampleChapters() = listOf(
        AudibleChapter(index = 0, title = "Chapter 1", startOffsetMs = 0L, lengthMs = 30_000L),
        AudibleChapter(index = 1, title = "Chapter 2", startOffsetMs = 30_000L, lengthMs = 30_000L),
        AudibleChapter(index = 2, title = "Chapter 3", startOffsetMs = 60_000L, lengthMs = 30_000L),
    )

    @Test
    fun `load fails closed when unapproved or audiobooks disabled or disconnected`() = runBlocking {
        AudibleCatalogCache.saveBooks(app, listOf(sampleBook()))
        AudibleCatalogCache.saveChapters(app, "B08G9PRS1K", sampleChapters())

        // 1. Disconnected
        val disconnectedPolicy = ParentalPolicy(
            spokenWord = SpokenWordParentalPolicy(
                allowAudiobooks = true,
                audible = AudibleParentalPolicy(allowedAsins = setOf("B08G9PRS1K")),
            ),
        )
        assertTrue(AudibleShowPageLoader.load(app, "audible:B08G9PRS1K", disconnectedPolicy).isFailure)

        // 2. Connected, but ASIN not in allowedAudibleAsins
        connectAudible()
        val unapprovedPolicy = ParentalPolicy(
            spokenWord = SpokenWordParentalPolicy(
                allowAudiobooks = true,
                audible = AudibleParentalPolicy(allowedAsins = emptySet()),
            ),
        )
        assertTrue(AudibleShowPageLoader.load(app, "audible:B08G9PRS1K", unapprovedPolicy).isFailure)

        // 3. Connected and approved, but allowAudiobooks == false
        val disabledGatePolicy = ParentalPolicy(
            spokenWord = SpokenWordParentalPolicy(
                allowAudiobooks = false,
                audible = AudibleParentalPolicy(allowedAsins = setOf("B08G9PRS1K")),
            ),
        )
        assertTrue(AudibleShowPageLoader.load(app, "audible:B08G9PRS1K", disabledGatePolicy).isFailure)
    }

    @Test
    fun `load builds audiobook ShowPage with chapters, stripped HTML description, and local progress`() = runBlocking {
        connectAudible()
        AudibleCatalogCache.saveBooks(app, listOf(sampleBook()))
        AudibleProgressStore.setChapterFinished(app, "B08G9PRS1K", 0, true)
        AudibleProgressStore.savePosition(app, "B08G9PRS1K", positionMs = 42_000L, chapterIndex = 1)

        var manifestFetches = 0
        val policy = ParentalPolicy(
            spokenWord = SpokenWordParentalPolicy(
                allowAudiobooks = true,
                audible = AudibleParentalPolicy(allowedAsins = setOf("B08G9PRS1K")),
            ),
        )

        val page = AudibleShowPageLoader.load(
            context = app,
            showId = "audible:B08G9PRS1K",
            policy = policy,
            fetchManifestChapters = { asin ->
                manifestFetches++
                assertEquals("B08G9PRS1K", asin)
                sampleChapters()
            },
        ).getOrThrow()

        assertEquals(1, manifestFetches)
        // Cached chapters persisted after lazy load
        assertEquals(3, AudibleCatalogCache.chapters(app, "B08G9PRS1K")?.size)

        assertTrue(page.audiobook)
        assertEquals("audible:B08G9PRS1K", page.show.id)
        assertEquals("Project Hail Mary", page.show.name)
        assertEquals(listOf("Andy Weir"), page.show.authors)
        assertEquals(listOf("Ray Porter"), page.show.narrators)
        assertEquals("Ryland Grace wakes up alone.", page.show.description)
        assertFalse(page.show.saved)

        assertEquals(3, page.items.size)
        // Chapter 0: marked finished
        assertEquals("audible:chapter:B08G9PRS1K:0", page.items[0].url)
        assertTrue(page.items[0].played)
        assertEquals(30_000, page.items[0].durationMs)

        // Chapter 1: active chapter at 42s absolute -> 12s chapter progress
        assertEquals("audible:chapter:B08G9PRS1K:1", page.items[1].url)
        assertFalse(page.items[1].played)
        assertEquals(12_000, page.items[1].playedPositionMs)

        // Chapter 2: unstarted
        assertEquals("audible:chapter:B08G9PRS1K:2", page.items[2].url)
        assertFalse(page.items[2].played)
        assertEquals(0, page.items[2].playedPositionMs)
    }

    @Test
    fun `Api getShowPage serves approved Audible book without requiring Spotify sign-in`() = runBlocking {
        connectAudible()
        AudibleCatalogCache.saveBooks(app, listOf(sampleBook()))
        AudibleCatalogCache.saveChapters(app, "B08G9PRS1K", sampleChapters())
        ParentalPolicyStore.update(app) {
            it.copy(
                spokenWord = SpokenWordParentalPolicy(
                    allowPodcasts = false,
                    allowAudiobooks = true,
                    audible = AudibleParentalPolicy(allowedAsins = setOf("B08G9PRS1K")),
                ),
            )
        }

        val api = Api(app)
        val responses = api.getShowPage("audible:B08G9PRS1K").toList()
        val success = responses.filterIsInstance<Response.Success<io.github.thake.soundnest.core.model.ShowPage>>().lastOrNull()
        assertTrue("Expected Api.getShowPage to succeed for approved Audible book, got $responses", success != null)
        assertEquals("Project Hail Mary", success!!.data.show.name)
        assertEquals(3, success.data.items.size)
    }

    @Test
    fun `ShowViewModel setFinished updates local AudibleProgressStore without touching Spotify API`() {
        connectAudible()
        AudibleCatalogCache.saveBooks(app, listOf(sampleBook()))
        AudibleCatalogCache.saveChapters(app, "B08G9PRS1K", sampleChapters())
        ParentalPolicyStore.update(app) {
            it.copy(
                spokenWord = SpokenWordParentalPolicy(
                    allowAudiobooks = true,
                    audible = AudibleParentalPolicy(allowedAsins = setOf("B08G9PRS1K")),
                ),
            )
        }

        val api = Api(app)
        val vm = ShowViewModel(
            repository = AppRepository(api),
            api = api,
            currentSongState = CurrentSongState(),
        )
        val chapterRow = io.github.thake.soundnest.core.model.SongsModel(
            id = 1,
            title = "Chapter 2",
            album = "Project Hail Mary",
            singer = "Andy Weir",
            coverUri = "",
            url = "audible:chapter:B08G9PRS1K:1",
            durationMs = 30_000,
        )

        vm.setFinished(chapterRow, true)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        Thread.sleep(50)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        assertTrue(vm.isFinished(chapterRow))
        assertEquals(setOf(1), AudibleProgressStore.get(app, "B08G9PRS1K")?.finishedChapters)

        vm.setFinished(chapterRow, false)
        Thread.sleep(50)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        assertFalse(vm.isFinished(chapterRow))
        assertEquals(emptySet<Int>(), AudibleProgressStore.get(app, "B08G9PRS1K")?.finishedChapters)
    }
}
