/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.parental.remote

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.thake.soundnest.R
import io.github.thake.soundnest.feature.accounts.ui.Entry
import io.github.thake.soundnest.feature.accounts.ui.Onboarding
import io.github.thake.soundnest.feature.accounts.ui.WelcomeScreen
import io.github.thake.soundnest.feature.parental.remote.crypto.RemoteCrypto
import io.github.thake.soundnest.feature.parental.remote.settings.DeviceRole
import io.github.thake.soundnest.feature.parental.remote.settings.DeviceRoleStore
import io.github.thake.soundnest.feature.parental.remote.settings.PairedDevicesStore
import io.github.thake.soundnest.feature.parental.remote.ui.ParentHubScreen
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
@Config(sdk = [35], application = Application::class)
class DeviceRoleAndParentHubTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        DeviceRoleStore.resetForTests(context)
        PairedDevicesStore.resetForTests(context)
    }

    @Test
    fun onboardingRoutesDedicatedParentControllerWithoutSpotifyLogin() {
        assertEquals(
            Entry.PARENT_HUB,
            Onboarding.entry(
                spotifySignedIn = false,
                setupNeeded = true,
                connecting = false,
                parentPinConfigured = false,
                isParentController = true,
            ),
        )
        // Normal child onboarding remains fail-closed when not a parent controller
        assertEquals(
            Entry.WELCOME,
            Onboarding.entry(
                spotifySignedIn = false,
                setupNeeded = true,
                connecting = false,
                parentPinConfigured = false,
                isParentController = false,
            ),
        )
        assertEquals(
            Entry.PARENT_SETUP,
            Onboarding.entry(
                spotifySignedIn = true,
                setupNeeded = true,
                connecting = false,
                parentPinConfigured = false,
                isParentController = false,
            ),
        )
    }

    @Test
    fun welcomeScreenTriggersParentModeCallback() {
        var parentTapped = false
        composeRule.setContent {
            WelcomeScreen(
                onLogin = {},
                onParentMode = { parentTapped = true },
            )
        }
        composeRule.onNodeWithText(context.getString(R.string.welcome_parent_mode))
            .assertIsDisplayed()
            .performClick()
        assertTrue(parentTapped)
    }

    @Test
    fun parentHubShowsEmptyStateAndSwitchBackButtonInDedicatedMode() {
        var switchedBack = false
        composeRule.setContent {
            ParentHubScreen(
                isDedicatedMode = true,
                onBack = null,
                onSwitchToStandardMode = { switchedBack = true },
            )
        }
        composeRule.onNodeWithTag("parent_hub_empty_state").assertIsDisplayed()
        composeRule.onNodeWithTag("parent_hub_switch_to_player")
            .assertIsDisplayed()
            .performClick()
        assertTrue(switchedBack)
    }

    @Test
    fun parentHubRendersPairedChildCard() {
        PairedDevicesStore.upsertPairedChild(
            context = context,
            childId = "child-1",
            displayName = "Maya's Phone",
            host = "192.168.1.99",
            port = 55000,
            rawSecret = RemoteCrypto.randomBytes(32),
        )
        composeRule.setContent {
            ParentHubScreen(isDedicatedMode = true)
        }
        composeRule.onNodeWithTag("parent_hub_child_card_child-1").assertIsDisplayed()
        composeRule.onNodeWithText("Maya's Phone").assertIsDisplayed()
    }

    @Test
    fun deviceRolePersistsAcrossInit() {
        DeviceRoleStore.setRole(context, DeviceRole.PARENT_CONTROLLER)
        assertEquals(DeviceRole.PARENT_CONTROLLER, DeviceRoleStore.currentRole(context))
        DeviceRoleStore.setRole(context, DeviceRole.STANDARD)
        assertEquals(DeviceRole.STANDARD, DeviceRoleStore.currentRole(context))
    }
}
