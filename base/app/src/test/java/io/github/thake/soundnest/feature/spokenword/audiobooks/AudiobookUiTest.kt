/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.spokenword.audiobooks

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.rememberNavController
import io.github.thake.soundnest.audible.AudibleBook
import io.github.thake.soundnest.audible.AudibleChapter
import io.github.thake.soundnest.audible.AudibleMarketplace
import io.github.thake.soundnest.audible.AudibleSessionTokens
import io.github.thake.soundnest.audible.AudibleStreamGrant
import io.github.thake.soundnest.core.di.Graph
import io.github.thake.soundnest.core.playback.SongPlayer
import io.github.thake.soundnest.core.ui.classic.SpotuiTheme
import io.github.thake.soundnest.core.ui.look.LocalLook
import io.github.thake.soundnest.core.ui.look.ProvideWindowSize
import io.github.thake.soundnest.feature.appearance.settings.Look
import io.github.thake.soundnest.feature.appearance.settings.LookPref
import io.github.thake.soundnest.feature.parental.security.ParentSecurityManager
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore
import io.github.thake.soundnest.feature.player.settings.PlayerSourceLinePref
import io.github.thake.soundnest.feature.speakers.settings.TrustedNetworkGate
import io.github.thake.soundnest.feature.spokenword.audible.data.AudibleCatalogCache
import io.github.thake.soundnest.feature.spokenword.audible.data.AudibleProgressStore
import io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayer
import io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayerBackend
import io.github.thake.soundnest.feature.spokenword.audible.settings.AudibleCredentialStore
import io.github.thake.soundnest.feature.spokenword.audible.settings.AudibleParentalPolicy
import io.github.thake.soundnest.feature.spokenword.audiobooks.ui.classic.AudiobookPlayerScreen
import io.github.thake.soundnest.feature.spokenword.audiobooks.ui.immersive.ImmersiveAudiobookPlayer
import io.github.thake.soundnest.feature.spokenword.settings.SpokenWordParentalPolicy
import io.github.thake.soundnest.feature.spokenword.ui.classic.ShowScreen
import io.github.thake.soundnest.feature.spokenword.ui.immersive.ImmersiveShowScreen
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp", application = Application::class)
class AudiobookUiTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var app: Application

    private class FakeAudibleBackend : AudiblePlayerBackend {
        var playing: Boolean = false
        var positionMs: Long = 0L
        var listener: AudiblePlayerBackend.Listener? = null

        override fun load(
            grant: AudibleStreamGrant,
            initialPositionMs: Long,
            speed: Float,
            playWhenReady: Boolean,
            listener: AudiblePlayerBackend.Listener,
        ) {
            positionMs = initialPositionMs
            playing = playWhenReady
            this.listener = listener
            listener.onPlaybackChanged(playing, false, positionMs)
        }

        override fun resume() {
            playing = true
            listener?.onPlaybackChanged(true, false, positionMs)
        }

        override fun pause() {
            playing = false
            listener?.onPlaybackChanged(false, false, positionMs)
        }

        override fun stop() {
            playing = false
        }

        override fun seekToAbsolute(positionMs: Long) {
            this.positionMs = positionMs
            listener?.onPlaybackChanged(playing, false, positionMs)
        }

        override fun setSpeed(speed: Float) {}
        override fun setGain(gain: Float) {}
        override fun isPlaying(): Boolean = playing
        override fun isBuffering(): Boolean = false
        override fun currentPositionMs(): Long = positionMs
        override fun release() {
            playing = false
        }
    }

    private val chapters = listOf(
        AudibleChapter(index = 0, title = "Opening Credits", startOffsetMs = 0L, lengthMs = 2_000L),
        AudibleChapter(index = 1, title = "Introduction", startOffsetMs = 2_000L, lengthMs = 9_000L),
        AudibleChapter(index = 2, title = "Chapter 1", startOffsetMs = 11_000L, lengthMs = 2_258_000L),
        AudibleChapter(index = 3, title = "Chapter 2", startOffsetMs = 2_269_000L, lengthMs = 1_800_000L),
        AudibleChapter(index = 4, title = "Chapter 3", startOffsetMs = 4_069_000L, lengthMs = 1_500_000L),
    )

    private val hailMary = AudibleBook(
        asin = "B08G9PRS1K",
        title = "Project Hail Mary",
        authors = listOf("Andy Weir"),
        narrators = listOf("Ray Porter"),
        releaseDate = "2026-05-04",
        runtimeLengthMs = 58_200_000L,
        description = "Ryland Grace wakes up alone.",
    )

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        Graph::class.java.getDeclaredField("app").apply { isAccessible = true }.set(null, app)
        SongPlayer::class.java.getDeclaredField("boundState").apply { isAccessible = true }
            .set(SongPlayer, Graph.currentSongState)

        ParentSecurityManager.resetForTests(app)
        ParentalPolicyStore.resetForTests(app)
        TrustedNetworkGate.resetForTests(app)
        AudiblePlayer.resetForTests()
        SongPlayer.clearAudiobookContextsForTests()
        AudibleCredentialStore.clear(app)
        AudibleCredentialStore.resetMemoryForTests()
        AudibleCatalogCache.clear(app)
        AudibleCatalogCache.resetMemoryForTests()
        AudibleProgressStore.clearAll(app)
        AudibleProgressStore.resetMemoryForTests()
        PlayerSourceLinePref.set(app, true)

        AudibleCredentialStore.save(
            app,
            AudibleSessionTokens(
                marketplace = AudibleMarketplace.DE,
                deviceSerial = "TEST_SERIAL",
                adpToken = "ADP",
                devicePrivateKeyPem = "PEM",
                refreshToken = "REFRESH",
                accessToken = "ACCESS",
                customerName = "Parent",
            ),
        )
        AudibleCatalogCache.saveBooks(app, listOf(hailMary))
        AudibleCatalogCache.saveChapters(app, "B08G9PRS1K", chapters)
        ParentalPolicyStore.update(app) {
            it.copy(
                spokenWord = SpokenWordParentalPolicy(
                    allowAudiobooks = true,
                    allowAudible = true,
                    audible = AudibleParentalPolicy(allowedAsins = setOf("B08G9PRS1K")),
                ),
            )
        }
        AudibleProgressStore.setChapterFinished(app, "B08G9PRS1K", chapterIndex = 0, finished = true)
        AudibleProgressStore.savePosition(
            app,
            asin = "B08G9PRS1K",
            positionMs = 11_000L + 918_000L,
            chapterIndex = 2,
        )
        AudiblePlayer.configureForTests(
            context = app,
            backend = FakeAudibleBackend(),
            grantLoader = { asin ->
                AudibleStreamGrant(
                    asin = asin,
                    manifestUrl = "https://cdn.audible.de/$asin/manifest.mpd",
                    drmType = "Widevine",
                    chapters = chapters,
                )
            },
        )
    }

    @After
    fun tearDown() {
        AudiblePlayer.resetForTests()
        SongPlayer.clearAudiobookContextsForTests()
        LookPref.set(app, Look.CLASSIC)
        AudibleCredentialStore.clear(app)
        AudibleCatalogCache.clear(app)
        AudibleProgressStore.clearAll(app)
        ParentalPolicyStore.resetForTests(app)
    }

    private fun pump(frames: Int = 6, stepMs: Long = 80L) {
        repeat(frames) {
            ShadowLooper.idleMainLooper()
            rule.mainClock.advanceTimeBy(stepMs)
        }
        ShadowLooper.idleMainLooper()
    }

    private fun pumpUntil(timeoutMs: Long = 15_000L, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            pump(frames = 2, stepMs = 50L)
            if (predicate()) return
            Thread.sleep(20)
        }
        check(predicate()) { "Timed out waiting for Compose UI state" }
    }

    @Test
    fun `Classic ShowScreen renders compact chapter rows without reverse sort or chapter release dates`() {
        rule.mainClock.autoAdvance = false
        LookPref.set(app, Look.CLASSIC)

        rule.setContent {
            SpotuiTheme {
                CompositionLocalProvider(LocalLook provides Look.CLASSIC) {
                    ProvideWindowSize {
                        ShowScreen(
                            navController = rememberNavController(),
                            showId = "audible:B08G9PRS1K",
                            showName = "Project Hail Mary",
                        )
                    }
                }
            }
        }

        pumpUntil { rule.onAllNodesWithText("Opening Credits").fetchSemanticsNodes().isNotEmpty() }

        rule.onNodeWithText("5 chapters").assertIsDisplayed()
        rule.onNodeWithText("All chapters").assertIsDisplayed()
        rule.onNodeWithText("0:02").assertIsDisplayed()
        rule.onNodeWithText("0:09").assertIsDisplayed()
        rule.onNodeWithText("37:38").assertIsDisplayed()
        rule.onNodeWithText("22:20 left").assertIsDisplayed()
        rule.onNodeWithText("All chapters • Newest").assertDoesNotExist()
        rule.onNodeWithText("4 May").assertDoesNotExist()
    }

    @Test
    fun `Immersive ShowScreen renders compact chapter rows without reverse sort or chapter release dates`() {
        rule.mainClock.autoAdvance = false
        LookPref.set(app, Look.IMMERSIVE)

        rule.setContent {
            SpotuiTheme {
                CompositionLocalProvider(LocalLook provides Look.IMMERSIVE) {
                    ProvideWindowSize {
                        ImmersiveShowScreen(
                            navController = rememberNavController(),
                            showId = "audible:B08G9PRS1K",
                            showName = "Project Hail Mary",
                        )
                    }
                }
            }
        }

        pumpUntil { rule.onAllNodesWithText("Opening Credits").fetchSemanticsNodes().isNotEmpty() }

        rule.onNodeWithText("5 chapters").assertIsDisplayed()
        rule.onNodeWithText("All chapters").assertIsDisplayed()
        rule.onNodeWithText("0:02").assertIsDisplayed()
        rule.onNodeWithText("0:09").assertIsDisplayed()
        rule.onNodeWithText("37:38").assertIsDisplayed()
        rule.onNodeWithText("22:20 left").assertIsDisplayed()
        rule.onNodeWithText("All chapters • Newest").assertDoesNotExist()
        rule.onNodeWithText("4 May").assertDoesNotExist()
    }

    @Test
    fun `Classic AudiobookPlayerScreen renders book chrome and opens Chapters ToC sheet`() {
        rule.mainClock.autoAdvance = false
        LookPref.set(app, Look.CLASSIC)
        SongPlayer.playSong("audible:book:B08G9PRS1K", app)
        ShadowLooper.idleMainLooper()

        rule.setContent {
            SpotuiTheme {
                CompositionLocalProvider(LocalLook provides Look.CLASSIC) {
                    ProvideWindowSize {
                        AudiobookPlayerScreen(rememberNavController())
                    }
                }
            }
        }

        pump()

        rule.onNodeWithText("Playing from the audiobook").assertIsDisplayed()
        rule.onNodeWithText("Project Hail Mary").assertIsDisplayed()
        rule.onNodeWithText("Project Hail Mary • Andy Weir").assertIsDisplayed()
        rule.onNodeWithText("Audible · Audiobook").assertIsDisplayed()
        rule.onNodeWithText("Chapter 3 of 5").assertIsDisplayed()
        rule.onNodeWithContentDescription("Previous chapter").assertIsDisplayed()
        rule.onNodeWithContentDescription("Next chapter").assertIsDisplayed()

        rule.onNodeWithContentDescription("Chapters").performClick()
        pump(frames = 8, stepMs = 80L)

        rule.onNodeWithText("Opening Credits").assertIsDisplayed()
        rule.onNodeWithText("Introduction").assertIsDisplayed()
        assertTrue(rule.onAllNodesWithText("Chapter 1").fetchSemanticsNodes().size >= 2)
        rule.onNodeWithText("Chapter 2").assertIsDisplayed()
        rule.onNodeWithText("Chapter 3").assertIsDisplayed()
    }

    @Test
    fun `Immersive AudiobookPlayerScreen renders book chrome and opens Chapters ToC sheet`() {
        rule.mainClock.autoAdvance = false
        LookPref.set(app, Look.IMMERSIVE)
        SongPlayer.playSong("audible:book:B08G9PRS1K", app)
        ShadowLooper.idleMainLooper()

        rule.setContent {
            SpotuiTheme {
                CompositionLocalProvider(LocalLook provides Look.IMMERSIVE) {
                    ProvideWindowSize {
                        ImmersiveAudiobookPlayer(rememberNavController())
                    }
                }
            }
        }

        pump()

        rule.onNodeWithText("Playing from the audiobook").assertIsDisplayed()
        rule.onNodeWithText("Project Hail Mary").assertIsDisplayed()
        rule.onNodeWithText("Project Hail Mary • Andy Weir").assertIsDisplayed()
        rule.onNodeWithText("Audible · Audiobook").assertIsDisplayed()
        rule.onNodeWithText("Chapter 3 of 5").assertIsDisplayed()
        rule.onNodeWithContentDescription("Previous chapter").assertIsDisplayed()
        rule.onNodeWithContentDescription("Next chapter").assertIsDisplayed()

        rule.onNodeWithContentDescription("Chapters").performClick()
        pump(frames = 8, stepMs = 80L)

        rule.onNodeWithText("Opening Credits").assertIsDisplayed()
        rule.onNodeWithText("Introduction").assertIsDisplayed()
        assertTrue(rule.onAllNodesWithText("Chapter 1").fetchSemanticsNodes().size >= 2)
        rule.onNodeWithText("Chapter 2").assertIsDisplayed()
        rule.onNodeWithText("Chapter 3").assertIsDisplayed()
    }
}
