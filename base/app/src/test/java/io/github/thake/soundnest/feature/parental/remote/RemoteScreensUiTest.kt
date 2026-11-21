/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.parental.remote

import android.app.Application
import android.content.Context
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.ui.settings.rememberM3SettingsPalette
import io.github.thake.soundnest.feature.parental.remote.crypto.RemoteCrypto
import io.github.thake.soundnest.feature.parental.remote.net.ChildControlDelegate
import io.github.thake.soundnest.feature.parental.remote.net.ChildRemoteServer
import io.github.thake.soundnest.feature.parental.remote.net.ChildStatusSnapshot
import io.github.thake.soundnest.feature.parental.remote.net.ParentRemoteClient
import io.github.thake.soundnest.feature.parental.remote.net.RemotePolicyBundle
import io.github.thake.soundnest.feature.parental.remote.net.RemoteRequest
import io.github.thake.soundnest.feature.parental.remote.settings.DeviceRoleStore
import io.github.thake.soundnest.feature.parental.remote.settings.PairedDevicesStore
import io.github.thake.soundnest.feature.parental.remote.ui.ChildFamilyRemotePage
import io.github.thake.soundnest.feature.parental.remote.ui.PairChildDialog
import io.github.thake.soundnest.feature.parental.remote.ui.RemotePolicyEditorScreen
import io.github.thake.soundnest.feature.parental.security.ParentSecurityManager
import io.github.thake.soundnest.feature.parental.settings.FilterMode
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore
import io.github.thake.soundnest.feature.parental.settings.RuleLabel
import io.github.thake.soundnest.feature.parental.settings.RuleLabelStore
import kotlinx.coroutines.runBlocking
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
import java.net.InetAddress

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class RemoteScreensUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        ChildRemoteServer.resetForTests()
        DeviceRoleStore.resetForTests(context)
        PairedDevicesStore.resetForTests(context)
        ParentSecurityManager.resetForTests(context)
        ParentalPolicyStore.resetForTests(context)
        ChildRemoteServer.enableMdns = false
    }

    @After
    fun tearDown() {
        ChildRemoteServer.resetForTests()
    }

    @Test
    fun childFamilyRemotePageStartsPairingAndRevokesPairedParent() {
        ParentSecurityManager.setupInitialPin(context, "1234")
        PairedDevicesStore.upsertPairedParent(
            context = context,
            parentId = "parent-mom",
            parentName = "Mom's Phone",
            rawSecret = RemoteCrypto.randomBytes(32),
        )
        var hubOpened = false
        composeRule.setContent {
            ChildFamilyRemotePage(
                context = context,
                listState = rememberLazyListState(),
                back = {},
                lockAndExit = {},
                onOpenParentHub = { hubOpened = true },
            )
        }

        // Start pairing -> displays 6-digit code
        composeRule.onNodeWithTag("parent_family_remote_list")
            .performScrollToNode(hasTestTag("child_start_pairing_btn"))
        composeRule.onNodeWithTag("child_start_pairing_btn").performClick()
        composeRule.onNodeWithTag("parent_family_remote_list")
            .performScrollToNode(hasTestTag("child_pairing_code_text"))
        composeRule.onNodeWithTag("child_pairing_code_text").assertIsDisplayed()

        // Scroll to paired parents & revoke
        composeRule.onNodeWithTag("parent_family_remote_list")
            .performScrollToNode(hasTestTag("revoke_parent_parent-mom"))
        composeRule.onNodeWithText("Mom's Phone").assertIsDisplayed()
        composeRule.onNodeWithTag("revoke_parent_parent-mom").performClick()
        assertTrue(PairedDevicesStore.pairedParents(context).isEmpty())

        // Scroll to Parent Controller Hub shortcut
        composeRule.onNodeWithTag("parent_family_remote_list")
            .performScrollToNode(hasTestTag("parent_open_controller_hub"))
        composeRule.onNodeWithTag("parent_open_controller_hub").performClick()
        assertTrue(hubOpened)
    }

    @Test
    fun pairChildDialogShowsQrAndManualFallbackInputs() {
        composeRule.setContent {
            val palette = rememberM3SettingsPalette()
            PairChildDialog(
                palette = palette,
                onDismiss = {},
                onPaired = {},
            )
        }
        composeRule.onNodeWithTag("pair_child_scan_qr").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.parent_hub_manual_address)).performClick()
        composeRule.onNodeWithTag("pair_child_host_input").assertIsDisplayed()
        composeRule.onNodeWithTag("pair_child_port_input").assertIsDisplayed()
        composeRule.onNodeWithTag("pair_child_code_input").assertIsDisplayed()
    }

    @Test
    fun remotePolicyEditorLoadsTogglesAndPushesPolicyOverLoopback() {
        ChildRemoteServer.delegate = object : ChildControlDelegate {
            override fun getStatus(context: Context) = ChildStatusSnapshot(
                childId = DeviceRoleStore.deviceId(context),
                deviceName = DeviceRoleStore.deviceName(context),
                policyRevision = ParentalPolicyStore.revision.value,
                filterMode = ParentalPolicyStore.current(context).filterMode,
            )
            override fun executePlayback(context: Context, action: RemoteRequest.PlaybackAction) = getStatus(context)
            override fun getPolicy(context: Context) = RemotePolicyBundle(
                childId = DeviceRoleStore.deviceId(context),
                deviceName = DeviceRoleStore.deviceName(context),
                policy = ParentalPolicyStore.current(context),
                ruleLabels = RuleLabelStore.all(context),
                policyRevision = ParentalPolicyStore.revision.value,
            )
            override fun updatePolicy(context: Context, policy: ParentalPolicy, ruleLabels: Map<String, RuleLabel>): Pair<Long, ChildStatusSnapshot> {
                ParentalPolicyStore.replace(context, policy)
                return ParentalPolicyStore.revision.value to getStatus(context)
            }
            override fun renameDevice(context: Context, newName: String) = getStatus(context)
        }

        ParentSecurityManager.setupInitialPin(context, "1234")
        val session = ChildRemoteServer.startPairingSession(context, InetAddress.getLoopbackAddress()).getOrThrow()
        val child = runBlocking {
            ParentRemoteClient.pairWithChild(context, "127.0.0.1", session.port, session.code).getOrThrow()
        }

        var savedCalled = false
        composeRule.setContent {
            RemotePolicyEditorScreen(
                child = child,
                otherChildren = emptyList(),
                onBack = {},
                onSaved = { savedCalled = true },
            )
        }

        // Wait for loopback fetchPolicy to populate the editor
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithTag("remote_policy_mode_strict").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("remote_policy_mode_strict").performClick()
        composeRule.onNodeWithTag("remote_policy_editor_list")
            .performScrollToNode(hasTestTag("remote_policy_save_btn"))
        composeRule.onNodeWithTag("remote_policy_save_btn").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000L) { savedCalled }
        assertEquals(FilterMode.STRICT_ALLOWLIST, ParentalPolicyStore.current(context).filterMode)
    }
}
