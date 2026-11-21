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
import androidx.compose.ui.test.performTextInput
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.ui.settings.rememberM3SettingsPalette
import io.github.thake.soundnest.feature.parental.remote.crypto.RemoteCrypto
import io.github.thake.soundnest.feature.parental.remote.net.ChildControlDelegate
import io.github.thake.soundnest.feature.parental.remote.net.ChildRemoteServer
import io.github.thake.soundnest.feature.parental.remote.net.ChildStatusSnapshot
import io.github.thake.soundnest.feature.parental.remote.net.ParentRemoteClient
import io.github.thake.soundnest.feature.parental.remote.net.RemoteCatalogItem
import io.github.thake.soundnest.feature.parental.remote.net.RemotePolicyBundle
import io.github.thake.soundnest.feature.parental.remote.net.RemoteRequest
import io.github.thake.soundnest.feature.parental.remote.net.RemoteResponse
import io.github.thake.soundnest.feature.parental.remote.settings.DeviceRoleStore
import io.github.thake.soundnest.feature.parental.remote.settings.PairedDevicesStore
import io.github.thake.soundnest.feature.parental.remote.settings.RemotePolicySession
import io.github.thake.soundnest.feature.parental.remote.ui.ChildFamilyRemotePage
import io.github.thake.soundnest.feature.parental.remote.ui.PairChildDialog
import io.github.thake.soundnest.feature.parental.remote.ui.RemoteCatalogBrowserScreen
import io.github.thake.soundnest.feature.parental.security.ParentSecurityManager
import io.github.thake.soundnest.feature.parental.settings.FilterMode
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore
import io.github.thake.soundnest.feature.parental.settings.RuleLabel
import io.github.thake.soundnest.feature.parental.ui.ParentSettingsScreen
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
    private var childServerPolicy = ParentalPolicy()
    private val childServerLabels = mutableMapOf<String, RuleLabel>()
    private var childServerRev = 1L

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        RemotePolicySession.resetForTests(context)
        ChildRemoteServer.resetForTests()
        DeviceRoleStore.resetForTests(context)
        PairedDevicesStore.resetForTests(context)
        ParentSecurityManager.resetForTests(context)
        ParentalPolicyStore.resetForTests(context)
        ChildRemoteServer.enableMdns = false
        RemotePolicySession.pushDebounceMs = 0L
        childServerPolicy = ParentalPolicy()
        childServerLabels.clear()
        childServerRev = 1L
    }

    @After
    fun tearDown() {
        RemotePolicySession.resetForTests(context)
        ChildRemoteServer.resetForTests()
    }

    private fun installLoopbackDelegate() {
        ChildRemoteServer.delegate = object : ChildControlDelegate {
            override fun getStatus(context: Context) = ChildStatusSnapshot(
                childId = DeviceRoleStore.deviceId(context),
                deviceName = "Joris",
                policyRevision = childServerRev,
                filterMode = childServerPolicy.filterMode,
            )
            override fun executePlayback(context: Context, action: RemoteRequest.PlaybackAction) = getStatus(context)
            override fun getPolicy(context: Context) = RemotePolicyBundle(
                childId = DeviceRoleStore.deviceId(context),
                deviceName = "Joris",
                policy = childServerPolicy,
                ruleLabels = childServerLabels.toMap(),
                policyRevision = childServerRev,
            )
            override fun updatePolicy(context: Context, policy: ParentalPolicy, ruleLabels: Map<String, RuleLabel>): Pair<Long, ChildStatusSnapshot> {
                childServerPolicy = policy
                childServerLabels.putAll(ruleLabels)
                childServerRev++
                return childServerRev to getStatus(context)
            }
            override fun renameDevice(context: Context, newName: String) = getStatus(context)
            override fun searchCatalog(context: Context, query: String): List<RemoteCatalogItem> = listOf(
                RemoteCatalogItem("track", "t1", "Bibi Blocksberg", "Kiddinx"),
                RemoteCatalogItem("artist", "a1", "Bibi & Tina"),
            )
            override fun getChildCatalog(context: Context) = RemoteResponse.ChildCatalog(
                libraryItems = listOf(RemoteCatalogItem("playlist", "p1", "Bedtime Mix", "By Parent")),
                audibleBooks = listOf(RemoteCatalogItem("audible", "B001", "Die drei ???", "Author")),
                audibleConnected = true,
            )
        }
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

        composeRule.onNodeWithTag("parent_family_remote_list")
            .performScrollToNode(hasTestTag("child_start_pairing_btn"))
        composeRule.onNodeWithTag("child_start_pairing_btn").performClick()
        composeRule.onNodeWithTag("parent_family_remote_list")
            .performScrollToNode(hasTestTag("child_pairing_code_text"))
        composeRule.onNodeWithTag("child_pairing_code_text").assertIsDisplayed()

        composeRule.onNodeWithTag("parent_family_remote_list")
            .performScrollToNode(hasTestTag("revoke_parent_parent-mom"))
        composeRule.onNodeWithText("Mom's Phone").assertIsDisplayed()
        composeRule.onNodeWithTag("revoke_parent_parent-mom").performClick()
        assertTrue(PairedDevicesStore.pairedParents(context).isEmpty())

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
    fun parentSettingsScreenInRemoteModeUsesChildScreensAndSyncsOverLoopback() {
        installLoopbackDelegate()
        ParentSecurityManager.setupInitialPin(context, "1234")
        val session = ChildRemoteServer.startPairingSession(context, InetAddress.getLoopbackAddress()).getOrThrow()
        val child = runBlocking {
            ParentRemoteClient.pairWithChild(context, "127.0.0.1", session.port, session.code).getOrThrow()
        }
        // Lock local PIN to prove dedicated Parent Device mode does NOT require local child PIN!
        ParentSecurityManager.lockNow(context)

        composeRule.setContent {
            ParentSettingsScreen(
                navController = null,
                remoteChild = child,
                otherRemoteChildren = emptyList(),
                onClose = {},
                onOpenRemoteCatalog = {},
            )
        }

        // Wait for RemotePolicySession.attach to load child's policy and render the real child dashboard
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithTag("parent_remote_open_catalog").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("parent_settings_list")
            .performScrollToNode(hasTestTag("parent_nav_filter_rules"))
        composeRule.onNodeWithTag("parent_nav_filter_rules").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithTag("parent_settings_list").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(context.getString(R.string.parent_mode_strict_allowlist_title)).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            childServerPolicy.filterMode == FilterMode.STRICT_ALLOWLIST
        }
        assertEquals(FilterMode.STRICT_ALLOWLIST, childServerPolicy.filterMode)
    }

    @Test
    fun remoteCatalogBrowserPicksAndBlocksItemsOverLoopback() {
        installLoopbackDelegate()
        ParentSecurityManager.setupInitialPin(context, "1234")
        val session = ChildRemoteServer.startPairingSession(context, InetAddress.getLoopbackAddress()).getOrThrow()
        val child = runBlocking {
            ParentRemoteClient.pairWithChild(context, "127.0.0.1", session.port, session.code).getOrThrow()
        }
        ParentSecurityManager.lockNow(context)

        composeRule.setContent {
            RemoteCatalogBrowserScreen(
                child = child,
                onBack = {},
            )
        }

        // Wait for initial attach + catalog load
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithTag("remote_catalog_search_input").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("remote_catalog_search_input").performTextInput("bibi")
        composeRule.mainClock.advanceTimeBy(400L)

        // Wait for search results and scroll to Pick / Block pills
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            runCatching {
                composeRule.onNodeWithTag("remote_catalog_list")
                    .performScrollToNode(hasTestTag("remote_pick_track_t1"))
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("remote_pick_track_t1").performClick()

        composeRule.onNodeWithTag("remote_catalog_list")
            .performScrollToNode(hasTestTag("remote_block_artist_a1"))
        composeRule.onNodeWithTag("remote_block_artist_a1").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            "t1" in childServerPolicy.allowedTrackIds && "a1" in childServerPolicy.blockedArtistIds
        }
        assertTrue("t1" in childServerPolicy.allowedTrackIds)
        assertTrue("a1" in childServerPolicy.blockedArtistIds)
        assertEquals("Bibi Blocksberg", childServerLabels["track:t1"]?.title)
        assertEquals("Bibi & Tina", childServerLabels["artist:a1"]?.title)
    }
}
