/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.parental

import io.github.thake.soundnest.feature.speakers.settings.TrustedNetworkGate

import io.github.thake.soundnest.feature.parental.filter.*
import io.github.thake.soundnest.feature.parental.security.*
import io.github.thake.soundnest.feature.parental.settings.*
import io.github.thake.soundnest.feature.parental.ui.*
import io.github.thake.soundnest.feature.home.settings.HomeParentalPolicy

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import io.github.thake.soundnest.core.ui.classic.SpotuiTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ParentPinScreensTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        io.github.thake.soundnest.core.di.Graph::class.java.getDeclaredField("app").apply { isAccessible = true }.set(null, app)
        ParentSecurityManager.resetForTests(app)
        ParentalPolicyStore.resetForTests(app)
        TrustedNetworkGate.resetForTests(app)
    }

    @After
    fun tearDown() {
        val app = RuntimeEnvironment.getApplication()
        ParentSecurityManager.resetForTests(app)
        ParentalPolicyStore.resetForTests(app)
        TrustedNetworkGate.resetForTests(app)
    }

    @Test
    fun `setup screen rejects 3-char input and accepts 4+ char password with letters and symbols`() {
        val app = RuntimeEnvironment.getApplication()
        var configuredCallbackCount = 0

        rule.setContent {
            SpotuiTheme {
                ParentPinSetupScreen(
                    onPinConfigured = { configuredCallbackCount++ },
                    onSwitchOrLogout = {},
                )
            }
        }

        // 3-char password must be rejected
        rule.onNodeWithTag("parent_setup_secret").performTextInput("ab!")
        rule.onNodeWithTag("parent_setup_confirm").performTextInput("ab!")
        rule.onNodeWithTag("parent_setup_save").performScrollTo().performClick()

        assertEquals(0, configuredCallbackCount)
        assertFalse(ParentSecurityManager.isPinConfigured(app))

        // 4+ char alphanumeric + symbol password succeeds without stripping non-digits
        rule.onNodeWithTag("parent_setup_secret").performScrollTo().performTextClearance()
        rule.onNodeWithTag("parent_setup_confirm").performScrollTo().performTextClearance()
        rule.onNodeWithTag("parent_setup_secret").performTextInput("P@ss!")
        rule.onNodeWithTag("parent_setup_confirm").performTextInput("P@ss!")
        rule.onNodeWithTag("parent_setup_save").performScrollTo().performClick()

        assertEquals(1, configuredCallbackCount)
        assertTrue(ParentSecurityManager.isPinConfigured(app))
        assertEquals(FilterMode.FILTERED_OPEN, ParentalPolicyStore.current(app).filterMode)
        ParentSecurityManager.lockNow(app)
        assertEquals(PinVerifyResult.Verified, ParentSecurityManager.verifyPin(app, "P@ss!"))
    }

    @Test
    fun `setup screen persists Strict Allowlist and Minimal Home when chosen during onboarding`() {
        val app = RuntimeEnvironment.getApplication()
        var configured = false

        rule.setContent {
            SpotuiTheme {
                ParentPinSetupScreen(
                    onPinConfigured = { configured = true },
                    onSwitchOrLogout = {},
                )
            }
        }

        rule.onNodeWithTag("parent_setup_mode_strict_allowlist").performScrollTo().performClick()
        rule.onNodeWithTag("parent_setup_secret").performScrollTo().performTextInput("safe2026")
        rule.onNodeWithTag("parent_setup_confirm").performScrollTo().performTextInput("safe2026")
        rule.onNodeWithTag("parent_setup_save").performScrollTo().performClick()

        assertTrue(configured)
        val savedPolicy = ParentalPolicyStore.current(app)
        assertEquals(FilterMode.STRICT_ALLOWLIST, savedPolicy.filterMode)
        assertTrue(savedPolicy.home.minimalHomeMode)
    }

    @Test
    fun `prompt dialog unlocks protected surface using a 4-char password`() {
        val app = RuntimeEnvironment.getApplication()
        ParentSecurityManager.setupInitialPin(app, "k3y!")
        ParentSecurityManager.lockNow(app)

        var authorized = false
        ParentSecurityManager.runProtected(app, AppSurface.PARENT_CONTROLS) {
            authorized = true
        }

        rule.setContent {
            SpotuiTheme {
                ParentPinPromptHost()
            }
        }

        rule.onNodeWithTag("parent_prompt_secret").performTextInput("k3y!")
        rule.onNodeWithText("Unlock").assertIsEnabled().performClick()

        assertTrue(authorized)
        assertTrue(ParentSecurityManager.isAdminUnlocked(app))
    }

    @Test
    fun `prompt dialog expands Forgot PIN recovery instructions and ManageContentRulesSheetBody removes items`() {
        val app = RuntimeEnvironment.getApplication()
        ParentSecurityManager.setupInitialPin(app, "k3y!")
        ParentSecurityManager.lockNow(app)
        ParentalPolicyStore.allowPlaylist(app, "pl_kids", "Kids Favorites", "Curated")
        ParentalPolicyStore.blockArtist(app, "art_bad", "Explicit Band")

        ParentSecurityManager.runProtected(app, AppSurface.PARENT_CONTROLS) {}

        rule.setContent {
            SpotuiTheme {
                ParentPinPromptHost()
                ManageContentRulesSheetBody(initialTabIsApproved = true, onDismiss = {})
            }
        }

        rule.onNodeWithTag("parent_prompt_forgot_toggle").performClick()
        rule.onNodeWithTag("parent_prompt_forgot_body").assertExists()

        // Approved playlist is displayed with its human-readable title and can be removed in one tap
        rule.onNodeWithText("Kids Favorites").assertExists()
        rule.onNodeWithTag("rule_remove_playlist_pl_kids").performClick()
        assertFalse("pl_kids" in ParentalPolicyStore.current(app).allowedPlaylistIds)

        // Switching to Blocked tab shows blocked artist and allows removal
        ParentSecurityManager.dismissPrompt()
        rule.onNodeWithTag("rules_tab_blocked").performClick()
        rule.onNodeWithText("Explicit Band").assertExists()
        rule.onNodeWithTag("rule_remove_artist_art_bad").performClick()
        assertFalse("art_bad" in ParentalPolicyStore.current(app).blockedArtistIds)
    }
}
