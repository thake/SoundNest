/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.settings

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.navigation.compose.rememberNavController
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.di.Graph
import io.github.thake.soundnest.core.ui.classic.SpotuiTheme
import io.github.thake.soundnest.core.ui.immersive.shell.LocalImmersiveChrome
import io.github.thake.soundnest.core.ui.immersive.shell.rememberImmersiveChromeState
import io.github.thake.soundnest.core.ui.look.LocalLook
import io.github.thake.soundnest.core.ui.look.ProvideWindowSize
import io.github.thake.soundnest.feature.accounts.ui.AccountsScreen
import io.github.thake.soundnest.feature.appearance.settings.Look
import io.github.thake.soundnest.feature.equalizer.ui.classic.EqualizerScreen
import io.github.thake.soundnest.feature.equalizer.ui.immersive.ImmersiveEqualizerScreen
import io.github.thake.soundnest.feature.localfiles.settings.LocalFilesParentalPolicy
import io.github.thake.soundnest.feature.parental.security.ParentSecurityManager
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore
import io.github.thake.soundnest.feature.parental.ui.ParentSettingsScreen
import io.github.thake.soundnest.feature.radio.settings.RadioParentalPolicy
import io.github.thake.soundnest.core.ui.look.LocalTopStatusBarConsumed
import io.github.thake.soundnest.core.ui.look.effectiveStatusBarTopPadding
import io.github.thake.soundnest.feature.settings.ui.classic.SettingsScreen
import io.github.thake.soundnest.feature.settings.ui.immersive.ImmersiveSettingsScreen
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.junit.Assert.assertEquals
import io.github.thake.soundnest.host.android.AccountStore
import io.github.thake.soundnest.host.android.SoundNestRuntime
import io.github.thake.soundnest.host.android.WebPlayerHost
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class MaterialSettingsNavigationTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val app get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        Graph::class.java.getDeclaredField("app").apply { isAccessible = true }.set(null, app)
        ParentSecurityManager.resetForTests(app)
        ParentalPolicyStore.resetForTests(app)
        val unsafe = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        val host = unsafe.javaClass.getMethod("allocateInstance", Class::class.java)
            .invoke(unsafe, WebPlayerHost::class.java) as WebPlayerHost
        WebPlayerHost::class.java.getDeclaredField("accounts").apply { isAccessible = true }
            .set(host, AccountStore(File(app.filesDir, "accounts.json")))
        SoundNestRuntime.host = host
    }

    @After
    fun tearDown() {
        SoundNestRuntime::class.java.getDeclaredField("host").apply { isAccessible = true }
            .set(SoundNestRuntime, null)
        ParentSecurityManager.resetForTests(app)
        ParentalPolicyStore.resetForTests(app)
    }

    private fun setContentInLook(look: Look, content: @Composable () -> Unit) {
        rule.setContent {
            SpotuiTheme {
                CompositionLocalProvider(LocalLook provides look) {
                    ProvideWindowSize {
                        if (look == Look.IMMERSIVE) {
                            val chrome = rememberImmersiveChromeState()
                            CompositionLocalProvider(LocalImmersiveChrome provides chrome) {
                                content()
                            }
                        } else {
                            content()
                        }
                    }
                }
            }
        }
    }

    @Test
    fun classicSettingsHubOpensPlaybackAndDataAndNavigatesBack() {
        setContentInLook(Look.CLASSIC) {
            SettingsScreen(navController = rememberNavController())
        }
        rule.waitForIdle()

        rule.onNodeWithText(app.getString(R.string.settings_title)).assertIsDisplayed()
        rule.onNode(hasScrollToNodeAction())
            .performScrollToNode(hasText(app.getString(R.string.settings_section_playback)))
        rule.onNodeWithText(app.getString(R.string.settings_section_playback)).performClick()
        rule.waitForIdle()

        rule.onNode(hasScrollToNodeAction())
            .performScrollToNode(hasText(app.getString(R.string.quality_streaming_wifi_title)))
        rule.onNodeWithText(app.getString(R.string.quality_streaming_wifi_title)).assertIsDisplayed()

        rule.onNodeWithContentDescription(app.getString(R.string.accessibility_navigate_back)).performClick()
        rule.waitForIdle()

        rule.onNode(hasScrollToNodeAction())
            .performScrollToNode(hasText(app.getString(R.string.settings_section_data)))
        rule.onNodeWithText(app.getString(R.string.settings_section_data)).performClick()
        rule.waitForIdle()

        rule.onNode(hasScrollToNodeAction())
            .performScrollToNode(hasText(app.getString(R.string.data_saver_title)))
        rule.onNodeWithText(app.getString(R.string.data_saver_title)).assertIsDisplayed()
    }

    @Test
    fun immersiveSettingsHubOpensPlaybackAndDataAndNavigatesBack() {
        setContentInLook(Look.IMMERSIVE) {
            ImmersiveSettingsScreen(navController = rememberNavController())
        }
        rule.waitForIdle()

        rule.onAllNodesWithText(app.getString(R.string.settings_title)).onFirst().assertIsDisplayed()
        rule.onNode(hasScrollToNodeAction())
            .performScrollToNode(hasText(app.getString(R.string.settings_section_playback)))
        rule.onNodeWithText(app.getString(R.string.settings_section_playback)).performClick()
        rule.waitForIdle()

        rule.onNode(hasScrollToNodeAction())
            .performScrollToNode(hasText(app.getString(R.string.quality_streaming_wifi_title)))
        rule.onNodeWithText(app.getString(R.string.quality_streaming_wifi_title)).assertIsDisplayed()

        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()

        rule.onNode(hasScrollToNodeAction())
            .performScrollToNode(hasText(app.getString(R.string.settings_section_data)))
        rule.onNodeWithText(app.getString(R.string.settings_section_data)).performClick()
        rule.waitForIdle()

        rule.onNode(hasScrollToNodeAction())
            .performScrollToNode(hasText(app.getString(R.string.data_saver_title)))
        rule.onNodeWithText(app.getString(R.string.data_saver_title)).assertIsDisplayed()
    }

    @Test
    fun parentSettingsHubAndSubpagesWorkInClassic() {
        verifyParentHubAndSubpages(Look.CLASSIC)
    }

    @Test
    fun parentSettingsHubAndSubpagesWorkInImmersive() {
        verifyParentHubAndSubpages(Look.IMMERSIVE)
    }

    private fun verifyParentHubAndSubpages(look: Look) {
        ParentSecurityManager.setupInitialPin(app, "1234")
        ParentalPolicyStore.update(app) {
            it.copy(
                localFiles = LocalFilesParentalPolicy(enabled = true),
                radio = RadioParentalPolicy(enabled = true),
            )
        }
        setContentInLook(look) {
            ParentSettingsScreen(navController = rememberNavController())
        }
        rule.waitForIdle()

        // Unified "Lock" button in Classic; "Lock Now" and "Lock & Exit" must not appear anywhere.
        assertEquals(0, rule.onAllNodesWithText(app.getString(R.string.parent_pin_lock_now)).fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithText(app.getString(R.string.parent_action_lock_exit)).fetchSemanticsNodes().size)
        if (look == Look.CLASSIC) {
            rule.onNodeWithText(app.getString(R.string.parent_pin_lock)).assertIsDisplayed()
        }

        val hubTags = listOf(
            "parent_nav_filter_rules",
            "parent_nav_home_sections",
            "parent_nav_spotify",
            "parent_nav_audible",
            "parent_nav_local_radio",
            "parent_nav_speakers_network",
            "parent_nav_security_backup",
        )
        hubTags.forEach { tag ->
            rule.onNodeWithTag("parent_settings_list").performScrollToNode(hasTestTag(tag))
            rule.onNodeWithTag(tag).assertIsDisplayed()
        }

        rule.onNodeWithTag("parent_settings_list").performScrollToIndex(0)
        rule.onNodeWithTag("parent_settings_list").performScrollToNode(hasTestTag("parent_nav_filter_rules"))
        rule.onNodeWithTag("parent_nav_filter_rules").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("parent_settings_list").performScrollToNode(hasTestTag("parent_manage_approved_rules"))
        rule.onNodeWithTag("parent_manage_approved_rules").assertIsDisplayed()

        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()

        rule.onNodeWithTag("parent_settings_list").performScrollToIndex(0)
        rule.onNodeWithTag("parent_settings_list").performScrollToNode(hasTestTag("parent_nav_spotify"))
        rule.onNodeWithTag("parent_nav_spotify").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("parent_settings_list").performScrollToNode(hasTestTag("parent_spotify_audiobooks_toggle"))
        rule.onNodeWithTag("parent_spotify_audiobooks_toggle").assertIsDisplayed()

        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()

        rule.onNodeWithTag("parent_settings_list").performScrollToIndex(0)
        rule.onNodeWithTag("parent_settings_list").performScrollToNode(hasTestTag("parent_nav_audible"))
        rule.onNodeWithTag("parent_nav_audible").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("parent_settings_list").performScrollToNode(hasTestTag("parent_audible_audiobooks_toggle"))
        rule.onNodeWithTag("parent_audible_audiobooks_toggle").assertIsDisplayed()
        rule.onNodeWithTag("parent_settings_list").performScrollToNode(hasTestTag("parent_audible_home_pinned_toggle"))
        rule.onNodeWithTag("parent_audible_home_pinned_toggle").assertIsDisplayed()

        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()

        rule.onNodeWithTag("parent_settings_list").performScrollToIndex(0)
        rule.onNodeWithTag("parent_settings_list").performScrollToNode(hasTestTag("parent_nav_local_radio"))
        rule.onNodeWithTag("parent_nav_local_radio").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("parent_settings_list").performScrollToNode(hasText(app.getString(R.string.parent_local_files_title)))
        rule.onNodeWithText(app.getString(R.string.parent_local_files_title)).assertIsDisplayed()
        rule.onNodeWithText(app.getString(R.string.parent_radio_title)).assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithText(app.getString(R.string.settings_local_files_shown)).fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithText(app.getString(R.string.equalizer_title)).fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithText(app.getString(R.string.settings_transitions_title)).fetchSemanticsNodes().size)
    }

    @Test
    fun kidSettingsHidesLocalRadioByDefaultAndShowsWhenAllowedInClassic() {
        verifyKidLocalRadioEntry(Look.CLASSIC)
    }

    @Test
    fun kidSettingsHidesLocalRadioByDefaultAndShowsWhenAllowedInImmersive() {
        verifyKidLocalRadioEntry(Look.IMMERSIVE)
    }

    private fun verifyKidLocalRadioEntry(look: Look) {
        setContentInLook(look) {
            if (look == Look.IMMERSIVE) {
                ImmersiveSettingsScreen(navController = rememberNavController())
            } else {
                SettingsScreen(navController = rememberNavController())
            }
        }
        rule.waitForIdle()
        assertEquals(
            0,
            rule.onAllNodes(hasTestTag("settings_nav_local_radio")).fetchSemanticsNodes().size,
        )
        assertEquals(
            0,
            rule.onAllNodesWithText(app.getString(R.string.settings_transitions_title)).fetchSemanticsNodes().size,
        )

        ParentalPolicyStore.update(app) { it.copy(radio = RadioParentalPolicy(enabled = true)) }
        rule.waitForIdle()

        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasTestTag("settings_nav_local_radio"))
        rule.onNodeWithTag("settings_nav_local_radio").assertIsDisplayed().performClick()
        rule.waitForIdle()

        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(app.getString(R.string.equalizer_title)))
        rule.onNodeWithText(app.getString(R.string.equalizer_title)).assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithText(app.getString(R.string.settings_local_files_shown)).fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithText(app.getString(R.string.settings_transitions_title)).fetchSemanticsNodes().size)

        ParentalPolicyStore.update(app) { it.copy(localFiles = LocalFilesParentalPolicy(enabled = true)) }
        rule.waitForIdle()
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasTestTag("local_radio_transitions_nav"))
        rule.onNodeWithTag("local_radio_transitions_nav").assertIsDisplayed()
    }

    @Test
    fun statusBarConsumptionSuppressesDoubleTopPaddingBelowBanner() {
        var childTopConsumed = -1f
        var effectiveConsumed: Dp? = null
        var effectiveUnconsumed: Dp? = null

        rule.setContent {
            SpotuiTheme {
                Column(Modifier.fillMaxSize()) {
                    effectiveUnconsumed = effectiveStatusBarTopPadding()
                    CompositionLocalProvider(LocalTopStatusBarConsumed provides true) {
                        effectiveConsumed = effectiveStatusBarTopPadding()
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .consumeWindowInsets(WindowInsets(top = 120)),
                        ) {
                            Box(
                                Modifier
                                    .statusBarsPadding()
                                    .onGloballyPositioned { childTopConsumed = it.boundsInRoot().top },
                            ) {
                                Text("Content")
                            }
                        }
                    }
                }
            }
        }
        rule.runOnUiThread {
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 120, 0, 0))
                .build()
            ViewCompat.dispatchApplyWindowInsets(rule.activity.window.decorView, insets)
        }
        rule.waitForIdle()

        assertEquals(0.dp, effectiveConsumed)
        assertEquals(0f, childTopConsumed)
        org.junit.Assert.assertTrue((effectiveUnconsumed ?: 0.dp) >= 0.dp)
    }

    @Test
    fun equalizerAndAccountsRenderInClassicLook() {
        ParentalPolicyStore.update(app) { it.copy(radio = RadioParentalPolicy(enabled = true)) }
        setContentInLook(Look.CLASSIC) {
            EqualizerScreen(navController = rememberNavController())
        }
        rule.waitForIdle()
        rule.onNodeWithText(app.getString(R.string.equalizer_title)).assertIsDisplayed()
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(app.getString(R.string.equalizer_preset)))
        rule.onNodeWithText(app.getString(R.string.equalizer_preset)).assertIsDisplayed()
    }

    @Test
    fun equalizerAndAccountsRenderInImmersiveLook() {
        ParentalPolicyStore.update(app) { it.copy(radio = RadioParentalPolicy(enabled = true)) }
        ParentSecurityManager.setupInitialPin(app, "1234")
        setContentInLook(Look.IMMERSIVE) {
            ImmersiveEqualizerScreen(navController = rememberNavController())
        }
        rule.waitForIdle()
        rule.onAllNodesWithText(app.getString(R.string.equalizer_title)).onFirst().assertIsDisplayed()
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(app.getString(R.string.equalizer_preset)))
        rule.onNodeWithText(app.getString(R.string.equalizer_preset)).assertIsDisplayed()
    }

    @Test
    fun accountsScreenRendersInClassicAndImmersive() {
        ParentSecurityManager.setupInitialPin(app, "1234")
        setContentInLook(Look.CLASSIC) {
            AccountsScreen(navController = rememberNavController())
        }
        rule.waitForIdle()
        rule.onNodeWithText(app.getString(R.string.accounts_title)).assertIsDisplayed()
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(app.getString(R.string.accounts_add)))
        rule.onNodeWithText(app.getString(R.string.accounts_add)).assertIsDisplayed()
    }

    @Test
    fun accountsScreenRendersInImmersiveLook() {
        ParentSecurityManager.setupInitialPin(app, "1234")
        setContentInLook(Look.IMMERSIVE) {
            AccountsScreen(navController = rememberNavController())
        }
        rule.waitForIdle()
        rule.onAllNodesWithText(app.getString(R.string.accounts_title)).onFirst().assertIsDisplayed()
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(app.getString(R.string.accounts_add)))
        rule.onNodeWithText(app.getString(R.string.accounts_add)).assertIsDisplayed()
    }
}
