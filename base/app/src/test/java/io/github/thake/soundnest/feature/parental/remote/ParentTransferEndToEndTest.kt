/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.parental.remote

import android.app.Application
import android.content.Context
import io.github.thake.soundnest.feature.parental.remote.crypto.RemoteCrypto
import io.github.thake.soundnest.feature.parental.remote.net.ChildControlDelegate
import io.github.thake.soundnest.feature.parental.remote.net.ChildRemoteServer
import io.github.thake.soundnest.feature.parental.remote.net.ChildStatusSnapshot
import io.github.thake.soundnest.feature.parental.remote.net.ParentRemoteClient
import io.github.thake.soundnest.feature.parental.remote.net.ParentTransferServer
import io.github.thake.soundnest.feature.parental.remote.net.RemotePolicyBundle
import io.github.thake.soundnest.feature.parental.remote.net.RemoteRequest
import io.github.thake.soundnest.feature.parental.remote.settings.DeviceRoleStore
import io.github.thake.soundnest.feature.parental.remote.settings.PairedDevicesStore
import io.github.thake.soundnest.feature.parental.security.ParentSecurityManager
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore
import io.github.thake.soundnest.feature.parental.settings.RuleLabel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
class ParentTransferEndToEndTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        ChildRemoteServer.resetForTests()
        ParentTransferServer.resetForTests()
        DeviceRoleStore.resetForTests(context)
        PairedDevicesStore.resetForTests(context)
        ParentSecurityManager.resetForTests(context)
        ParentalPolicyStore.resetForTests(context)
        ChildRemoteServer.enableMdns = false
        installFakeDelegate()
    }

    @After
    fun tearDown() {
        ParentTransferServer.resetForTests()
        ChildRemoteServer.resetForTests()
    }

    private fun installFakeDelegate() {
        ChildRemoteServer.delegate = object : ChildControlDelegate {
            override fun getStatus(context: Context) = ChildStatusSnapshot(
                childId = DeviceRoleStore.deviceId(context),
                deviceName = "Joris",
                title = "Bibi Blocksberg",
                artist = "Kiddinx",
                isPlaying = true,
            )
            override fun executePlayback(context: Context, action: RemoteRequest.PlaybackAction) = getStatus(context)
            override fun getPolicy(context: Context) = RemotePolicyBundle(
                childId = DeviceRoleStore.deviceId(context),
                deviceName = "Joris",
                policy = ParentalPolicy(),
                ruleLabels = emptyMap(),
                policyRevision = 1L,
            )
            override fun updatePolicy(context: Context, policy: ParentalPolicy, ruleLabels: Map<String, RuleLabel>) =
                2L to getStatus(context)
            override fun renameDevice(context: Context, newName: String) = getStatus(context)
        }
    }

    @Test
    fun transferOnlineChildRegistersIndependentCoParentAndSurvivesSourceRevocation() = runBlocking {
        ParentSecurityManager.setupInitialPin(context, "1234")
        val childPairSession = ChildRemoteServer.startPairingSession(context, InetAddress.getLoopbackAddress()).getOrThrow()
        val parentAId = "parent-papa"
        val parentBId = "parent-mama"

        // Parent A pairs with Child
        val childRec = ParentRemoteClient.pairWithChild(
            context = context,
            host = "127.0.0.1",
            port = childPairSession.port,
            code = childPairSession.code,
            parentIdOverride = parentAId,
            parentNameOverride = "Papa's Phone",
        ).getOrThrow()

        assertEquals(1, PairedDevicesStore.pairedParents(context).size)
        assertEquals(parentAId, PairedDevicesStore.pairedParents(context).single().parentId)

        // Parent A starts transfer session
        val transferSession = ParentTransferServer.startTransferSession(
            context = context,
            bindAddress = InetAddress.getLoopbackAddress(),
            sourceParentIdOverride = parentAId,
            sourceParentNameOverride = "Papa's Phone",
        ).getOrThrow()
        assertEquals(1, transferSession.childCount)

        // Parent B receives transfer
        val importResult = ParentRemoteClient.receiveParentTransfer(
            context = context,
            host = "127.0.0.1",
            port = transferSession.port,
            code = transferSession.code,
            newParentIdOverride = parentBId,
            newParentNameOverride = "Mama's Phone",
        ).getOrThrow()

        assertEquals(1, importResult.importedChildren.size)
        assertEquals(1, importResult.upgradedCount)
        val importedChild = importResult.importedChildren.single()
        assertEquals(childRec.childId, importedChild.childId)
        assertNull("Online child should have own independent key (authParentId == null)", importedChild.authParentId)

        // Child now lists BOTH Papa's Phone and Mama's Phone independently
        val parentsOnChild = PairedDevicesStore.pairedParents(context)
        assertEquals(2, parentsOnChild.size)
        assertTrue(parentsOnChild.any { it.parentId == parentAId })
        assertTrue(parentsOnChild.any { it.parentId == parentBId && it.parentName == "Mama's Phone" })

        // Revoking Parent A must NOT break Parent B
        PairedDevicesStore.revokePairedParent(context, parentAId)
        val statusForB = ParentRemoteClient.fetchStatus(
            context = context,
            child = importedChild,
            parentIdOverride = parentBId,
        ).getOrThrow()
        assertEquals("Joris", statusForB.deviceName)
    }

    @Test
    fun transferOfflineChildUsesDelegatedFallbackAndAutoUpgradesWhenChildComesOnline() = runBlocking {
        ParentSecurityManager.setupInitialPin(context, "1234")
        val childPairSession = ChildRemoteServer.startPairingSession(context, InetAddress.getLoopbackAddress()).getOrThrow()
        val childPort = childPairSession.port
        val parentAId = "parent-papa"
        val parentBId = "parent-mama"

        val childRec = ParentRemoteClient.pairWithChild(
            context = context,
            host = "127.0.0.1",
            port = childPort,
            code = childPairSession.code,
            parentIdOverride = parentAId,
            parentNameOverride = "Papa's Phone",
        ).getOrThrow()

        // Child goes OFFLINE before Parent A transfers to Parent B
        ChildRemoteServer.stop()

        val transferSession = ParentTransferServer.startTransferSession(
            context = context,
            bindAddress = InetAddress.getLoopbackAddress(),
            sourceParentIdOverride = parentAId,
            sourceParentNameOverride = "Papa's Phone",
        ).getOrThrow()

        val importResult = ParentRemoteClient.receiveParentTransfer(
            context = context,
            host = "127.0.0.1",
            port = transferSession.port,
            code = transferSession.code,
            newParentIdOverride = parentBId,
            newParentNameOverride = "Mama's Phone",
        ).getOrThrow()

        assertEquals(1, importResult.importedChildren.size)
        assertEquals(0, importResult.upgradedCount)
        val delegatedChild = importResult.importedChildren.single()
        assertEquals(parentAId, delegatedChild.authParentId)

        // Child comes back ONLINE on a new port
        val newPort = ChildRemoteServer.startPairingSession(context, InetAddress.getLoopbackAddress()).getOrThrow().port
        ChildRemoteServer.cancelPairingSession(context)
        assertTrue(newPort > 0)
        PairedDevicesStore.updateChildEndpoint(context, delegatedChild.childId, "127.0.0.1", newPort)
        val updatedDelegated = PairedDevicesStore.pairedChildren(context).single { it.childId == delegatedChild.childId }

        // Parent B polls the child -> automatically upgrades delegated credential to Parent B's own independent key!
        val states = ParentRemoteClient.pollAll(
            context = context,
            children = listOf(updatedDelegated),
            parentIdOverride = parentBId,
            parentNameOverride = "Mama's Phone",
        )
        assertTrue(states[delegatedChild.childId]?.online == true)

        val upgradedRecord = PairedDevicesStore.pairedChildren(context).single { it.childId == delegatedChild.childId }
        assertNull("Delegated child should auto-upgrade and clear authParentId", upgradedRecord.authParentId)
        assertTrue(PairedDevicesStore.pairedParents(context).any { it.parentId == parentBId })

        // Revoking Parent A must NOT break Parent B after auto-upgrade
        PairedDevicesStore.revokePairedParent(context, parentAId)
        val statusAfterRevoke = ParentRemoteClient.fetchStatus(
            context = context,
            child = upgradedRecord,
            parentIdOverride = parentBId,
        ).getOrThrow()
        assertEquals("Joris", statusAfterRevoke.deviceName)
    }

    @Test
    fun transferRejectsWrongCodeAndIsSingleUse() = runBlocking {
        PairedDevicesStore.upsertPairedChild(
            context = context,
            childId = "child-offline-1",
            displayName = "Bennet",
            host = "127.0.0.1",
            port = 1,
            rawSecret = RemoteCrypto.randomBytes(32),
        )
        val session = ParentTransferServer.startTransferSession(
            context = context,
            bindAddress = InetAddress.getLoopbackAddress(),
            sourceParentIdOverride = "parent-a",
            sourceParentNameOverride = "Papa",
        ).getOrThrow()

        val wrongCode = if (session.code == "000000") "999999" else "000000"
        val badAttempt = ParentRemoteClient.receiveParentTransfer(
            context = context,
            host = "127.0.0.1",
            port = session.port,
            code = wrongCode,
            newParentIdOverride = "parent-evil",
            newParentNameOverride = "Evil",
        )
        assertTrue(badAttempt.isFailure)

        // Legitimate transfer succeeds once
        val okAttempt = ParentRemoteClient.receiveParentTransfer(
            context = context,
            host = "127.0.0.1",
            port = session.port,
            code = session.code,
            newParentIdOverride = "parent-b",
            newParentNameOverride = "Mama",
        )
        assertTrue(okAttempt.isSuccess)
        assertNotNull(okAttempt.getOrNull())

        // Replay attempt with same code fails (single-use)
        val replay = ParentRemoteClient.receiveParentTransfer(
            context = context,
            host = "127.0.0.1",
            port = session.port,
            code = session.code,
            newParentIdOverride = "parent-c",
            newParentNameOverride = "Replay",
        )
        assertFalse(replay.isSuccess)
    }
}
