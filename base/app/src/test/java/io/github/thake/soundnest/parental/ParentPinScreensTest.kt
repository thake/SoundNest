/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.parental

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
import io.github.thake.soundnest.ui.theme.SpotuiTheme
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
        io.github.thake.soundnest.di.Graph::class.java.getDeclaredField("app").apply { isAccessible = true }.set(null, app)
        ParentSecurityManager.resetForTests(app)
        TrustedNetworkGate.resetForTests(app)
    }

    @After
    fun tearDown() {
        val app = RuntimeEnvironment.getApplication()
        ParentSecurityManager.resetForTests(app)
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
        ParentSecurityManager.lockNow(app)
        assertEquals(PinVerifyResult.Verified, ParentSecurityManager.verifyPin(app, "P@ss!"))
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
}
