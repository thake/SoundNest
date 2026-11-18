/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.library.hiddensongs

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.compose.rememberNavController
import io.github.thake.soundnest.core.di.Graph
import io.github.thake.soundnest.core.model.SongsModel
import io.github.thake.soundnest.core.ui.classic.SpotuiTheme
import io.github.thake.soundnest.feature.library.hiddensongs.settings.HiddenSongs
import io.github.thake.soundnest.feature.library.hiddensongs.ui.classic.HiddenSongsScreen
import io.github.thake.soundnest.feature.library.hiddensongs.ui.immersive.ImmersiveHiddenSongsScreen
import io.github.thake.soundnest.feature.parental.settings.FilterMode
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class HiddenSongsScreenTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val app get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        Graph::class.java.getDeclaredField("app").apply { isAccessible = true }.set(null, app)
        ParentalPolicyStore.resetForTests(app)
        HiddenSongs.songs.forEach { HiddenSongs.show(app, it.id) }
    }

    @After
    fun tearDown() {
        ParentalPolicyStore.resetForTests(app)
        HiddenSongs.songs.forEach { HiddenSongs.show(app, it.id) }
    }

    private fun addSong(id: String, title: String, singer: String) {
        HiddenSongs.hide(
            app,
            SongsModel(
                id = id.hashCode(),
                title = title,
                album = "Album",
                singer = singer,
                coverUri = "",
                url = "spotify:track:$id",
                spotifyTrackId = id,
            ),
        )
    }

    @Test
    fun classicHiddenSongsScreenFiltersBlockedTracksAndKeywords() {
        addSong("track1", "Clean Track", "Artist One")
        addSong("track2", "Explicit Title Track", "Artist Two")
        addSong("track3", "Parent Blocked Track", "Artist Three")

        ParentalPolicyStore.update(app) {
            ParentalPolicy(
                blockedTrackIds = setOf("track3"),
                blockedKeywords = setOf("Explicit Title"),
            )
        }

        rule.setContent {
            SpotuiTheme {
                val nav = rememberNavController()
                HiddenSongsScreen(navController = nav)
            }
        }
        rule.waitForIdle()

        rule.onNodeWithText("Clean Track").assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithText("Explicit Title Track").fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithText("Parent Blocked Track").fetchSemanticsNodes().size)
    }

    @Test
    fun allowedTrackOverridesKeywordBlockAndPersistsInStrictAllowlist() {
        addSong("track1", "Special Track with BadWord", "Artist One")
        addSong("track2", "Normal Hidden Track", "Artist Two")

        ParentalPolicyStore.update(app) {
            ParentalPolicy(
                filterMode = FilterMode.STRICT_ALLOWLIST,
                blockedKeywords = setOf("BadWord"),
                allowedTrackIds = setOf("track1"),
            )
        }

        rule.setContent {
            SpotuiTheme {
                val nav = rememberNavController()
                HiddenSongsScreen(navController = nav)
            }
        }
        rule.waitForIdle()

        rule.onNodeWithText("Special Track with BadWord").assertIsDisplayed()
        rule.onNodeWithText("Normal Hidden Track").assertIsDisplayed()
    }

    @Test
    fun immersiveHiddenSongsScreenFiltersBlockedTracksAndKeywords() {
        addSong("track1", "Clean Track", "Artist One")
        addSong("track2", "Parent Blocked Track", "Artist Two")

        ParentalPolicyStore.update(app) {
            ParentalPolicy(
                blockedTrackIds = setOf("track2"),
            )
        }

        rule.setContent {
            SpotuiTheme {
                val nav = rememberNavController()
                ImmersiveHiddenSongsScreen(navController = nav)
            }
        }
        rule.waitForIdle()

        rule.onNodeWithText("Clean Track").assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithText("Parent Blocked Track").fetchSemanticsNodes().size)
    }
}
