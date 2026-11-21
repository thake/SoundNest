/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.parental.remote

import android.app.Application
import android.content.Context
import io.github.thake.soundnest.feature.parental.remote.net.ChildControlDelegate
import io.github.thake.soundnest.feature.parental.remote.net.ChildRemoteServer
import io.github.thake.soundnest.feature.parental.remote.net.ChildStatusSnapshot
import io.github.thake.soundnest.feature.parental.remote.net.ParentRemoteClient
import io.github.thake.soundnest.feature.parental.remote.net.RemotePolicyBundle
import io.github.thake.soundnest.feature.parental.remote.net.RemoteRequest
import io.github.thake.soundnest.feature.parental.remote.settings.DeviceRoleStore
import io.github.thake.soundnest.feature.parental.remote.settings.PairedDevicesStore
import io.github.thake.soundnest.feature.parental.remote.settings.RemotePolicySession
import io.github.thake.soundnest.feature.parental.remote.settings.RemoteSyncState
import io.github.thake.soundnest.feature.parental.security.ParentSecurityManager
import io.github.thake.soundnest.feature.parental.settings.FilterMode
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore
import io.github.thake.soundnest.feature.parental.settings.RuleLabel
import io.github.thake.soundnest.feature.parental.settings.RuleLabelStore
import io.github.thake.soundnest.feature.parental.ui.ParentBlockActions
import io.github.thake.soundnest.feature.parental.ui.ParentBlockTarget
import io.github.thake.soundnest.feature.parental.ui.ParentRuleKind
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
import java.net.InetAddress

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class RemotePolicySessionTest {

    private lateinit var context: Context
    private var childServerPolicy = ParentalPolicy(
        filterMode = FilterMode.STRICT_ALLOWLIST,
        allowedPlaylistIds = setOf("existing-pl"),
    )
    private var childServerLabels = mutableMapOf(
        "playlist:existing-pl" to RuleLabel("Child Existing Playlist"),
    )
    private var childServerRevision = 1L

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

        childServerPolicy = ParentalPolicy(
            filterMode = FilterMode.STRICT_ALLOWLIST,
            allowedPlaylistIds = setOf("existing-pl"),
        )
        childServerLabels = mutableMapOf("playlist:existing-pl" to RuleLabel("Child Existing Playlist"))
        childServerRevision = 1L

        ChildRemoteServer.delegate = object : ChildControlDelegate {
            override fun getStatus(context: Context) = ChildStatusSnapshot(
                childId = DeviceRoleStore.deviceId(context),
                deviceName = "Joris",
                policyRevision = childServerRevision,
                filterMode = childServerPolicy.filterMode,
            )
            override fun executePlayback(context: Context, action: RemoteRequest.PlaybackAction) = getStatus(context)
            override fun getPolicy(context: Context) = RemotePolicyBundle(
                childId = DeviceRoleStore.deviceId(context),
                deviceName = "Joris",
                policy = childServerPolicy,
                ruleLabels = childServerLabels.toMap(),
                policyRevision = childServerRevision,
            )
            override fun updatePolicy(context: Context, policy: ParentalPolicy, ruleLabels: Map<String, RuleLabel>): Pair<Long, ChildStatusSnapshot> {
                childServerPolicy = policy
                childServerLabels.putAll(ruleLabels)
                childServerRevision++
                return childServerRevision to getStatus(context)
            }
            override fun renameDevice(context: Context, newName: String) = getStatus(context)
        }
    }

    @After
    fun tearDown() {
        RemotePolicySession.resetForTests(context)
        ChildRemoteServer.resetForTests()
    }

    @Test
    fun attachOverlaysStoresPushesPickAndBlockMutationsAndRestoresLocalOnDetach() = runBlocking {
        // Local device starts with FILTERED_OPEN and a local-only keyword
        ParentalPolicyStore.addBlockedKeyword(context, "local-only-word")
        assertEquals(FilterMode.FILTERED_OPEN, ParentalPolicyStore.current(context).filterMode)

        ParentSecurityManager.setupInitialPin(context, "1234")
        val session = ChildRemoteServer.startPairingSession(context, InetAddress.getLoopbackAddress()).getOrThrow()
        val pairedChild = ParentRemoteClient.pairWithChild(context, "127.0.0.1", session.port, session.code).getOrThrow()

        // 1. Attach -> ParentalPolicyStore & RuleLabelStore reflect the child's policy
        val attached = RemotePolicySession.attach(context, pairedChild)
        assertTrue(attached.isSuccess)
        assertEquals(FilterMode.STRICT_ALLOWLIST, ParentalPolicyStore.current(context).filterMode)
        assertEquals(setOf("existing-pl"), ParentalPolicyStore.current(context).allowedPlaylistIds)
        assertEquals("Child Existing Playlist", RuleLabelStore.all(context)["playlist:existing-pl"]?.title)

        // 2. Pick & Block via exact children's app ParentBlockActions -> pushes to child server!
        ParentBlockActions.pick(
            context,
            ParentBlockTarget(ParentRuleKind.TRACK, "track-42", "Happy Song", "Kidz Band"),
            showToast = false,
        )
        ParentBlockActions.block(
            context,
            ParentBlockTarget(ParentRuleKind.ARTIST, "artist-99", "Explicit Artist"),
            showToast = false,
        )
        RemotePolicySession.flushPendingPush(context)

        assertTrue("track-42" in childServerPolicy.allowedTrackIds)
        assertTrue("artist-99" in childServerPolicy.blockedArtistIds)
        assertEquals("Happy Song", childServerLabels["track:track-42"]?.title)
        assertEquals("Explicit Artist", childServerLabels["artist:artist-99"]?.title)
        assertEquals(RemoteSyncState.Synced, RemotePolicySession.syncStateFlow.value)

        // 3. Detach -> local device policy is untouched and restored!
        RemotePolicySession.detach(context)
        assertFalse(RemotePolicySession.isActive)
        val localRestored = ParentalPolicyStore.current(context)
        assertEquals(FilterMode.FILTERED_OPEN, localRestored.filterMode)
        assertEquals(setOf("local-only-word"), localRestored.blockedKeywords)
        assertFalse("track-42" in localRestored.allowedTrackIds)
    }
}
