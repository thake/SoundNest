/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.parental.remote

import android.app.Application
import android.content.Context
import android.util.Base64
import io.github.thake.soundnest.feature.parental.remote.crypto.RemoteCrypto
import io.github.thake.soundnest.feature.parental.remote.net.AuthenticatedRequest
import io.github.thake.soundnest.feature.parental.remote.net.ChildControlDelegate
import io.github.thake.soundnest.feature.parental.remote.net.ChildRemoteServer
import io.github.thake.soundnest.feature.parental.remote.net.ChildStatusSnapshot
import io.github.thake.soundnest.feature.parental.remote.net.ClientWireMessage
import io.github.thake.soundnest.feature.parental.remote.net.ParentRemoteClient
import io.github.thake.soundnest.feature.parental.remote.net.RemotePlaybackAction
import io.github.thake.soundnest.feature.parental.remote.net.RemotePolicyBundle
import io.github.thake.soundnest.feature.parental.remote.net.RemoteProtocol
import io.github.thake.soundnest.feature.parental.remote.net.RemoteRequest
import io.github.thake.soundnest.feature.parental.remote.net.ServerHello
import io.github.thake.soundnest.feature.parental.remote.net.ServerWireMessage
import io.github.thake.soundnest.feature.parental.remote.settings.DeviceRoleStore
import io.github.thake.soundnest.feature.parental.remote.settings.PairedChildRecord
import io.github.thake.soundnest.feature.parental.remote.settings.PairedDevicesStore
import io.github.thake.soundnest.feature.parental.security.ParentSecurityManager
import io.github.thake.soundnest.feature.parental.settings.FilterMode
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore
import io.github.thake.soundnest.feature.parental.settings.RuleLabel
import io.github.thake.soundnest.feature.parental.settings.RuleLabelStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
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
import java.net.InetSocketAddress
import java.net.Socket

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ChildRemoteServerEndToEndTest {

    private lateinit var context: Context
    private val executedActions = mutableListOf<RemoteRequest.PlaybackAction>()
    private var playingState = true
    private var volumeState = 60
    private var sleepRemainingMs: Long? = null

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        ChildRemoteServer.resetForTests()
        DeviceRoleStore.resetForTests(context)
        PairedDevicesStore.resetForTests(context)
        ParentSecurityManager.resetForTests(context)
        ParentalPolicyStore.resetForTests(context)

        ChildRemoteServer.enableMdns = false
        executedActions.clear()
        playingState = true
        volumeState = 60
        sleepRemainingMs = null

        ChildRemoteServer.delegate = object : ChildControlDelegate {
            override fun getStatus(context: Context): ChildStatusSnapshot {
                val p = ParentalPolicyStore.current(context)
                return ChildStatusSnapshot(
                    childId = DeviceRoleStore.deviceId(context),
                    deviceName = DeviceRoleStore.deviceName(context),
                    title = "Kidz Bop Song",
                    artist = "Kidz Bop",
                    isPlaying = playingState,
                    volumePercent = volumeState,
                    sleepTimerRemainingMs = sleepRemainingMs,
                    policyRevision = ParentalPolicyStore.revision.value,
                    filterMode = p.filterMode,
                    blockExplicit = p.blockExplicit,
                )
            }

            override fun executePlayback(
                context: Context,
                action: RemoteRequest.PlaybackAction,
            ): ChildStatusSnapshot {
                executedActions += action
                when (action.action) {
                    RemotePlaybackAction.PLAY -> playingState = true
                    RemotePlaybackAction.PAUSE -> playingState = false
                    RemotePlaybackAction.SET_VOLUME -> volumeState = action.volumePercent ?: volumeState
                    RemotePlaybackAction.SET_SLEEP_TIMER -> {
                        sleepRemainingMs = when (val m = action.sleepTimerMinutes) {
                            null -> null
                            0 -> -1L
                            else -> m * 60_000L
                        }
                    }
                    else -> Unit
                }
                return getStatus(context)
            }

            override fun getPolicy(context: Context): RemotePolicyBundle = RemotePolicyBundle(
                childId = DeviceRoleStore.deviceId(context),
                deviceName = DeviceRoleStore.deviceName(context),
                policy = ParentalPolicyStore.current(context),
                ruleLabels = RuleLabelStore.all(context),
                policyRevision = ParentalPolicyStore.revision.value,
            )

            override fun updatePolicy(
                context: Context,
                policy: ParentalPolicy,
                ruleLabels: Map<String, RuleLabel>,
            ): Pair<Long, ChildStatusSnapshot> {
                val validRegexes = policy.blockedRegexPatterns.filter { pat ->
                    pat.isNotBlank() && runCatching { Regex(pat, RegexOption.IGNORE_CASE) }.isSuccess
                }.toSet()
                RuleLabelStore.mergeAll(context, ruleLabels)
                ParentalPolicyStore.replace(context, policy.copy(blockedRegexPatterns = validRegexes))
                return ParentalPolicyStore.revision.value to getStatus(context)
            }

            override fun renameDevice(context: Context, newName: String): ChildStatusSnapshot {
                DeviceRoleStore.setDeviceName(context, newName)
                return getStatus(context)
            }
        }
    }

    @After
    fun tearDown() {
        ChildRemoteServer.resetForTests()
    }

    @Test
    fun pairingRequiresUnlockedAdminSession() {
        // Admin session is locked -> startPairingSession must fail closed
        val result = ChildRemoteServer.startPairingSession(context, InetAddress.getLoopbackAddress())
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
    }

    @Test
    fun fullEndToEndPairingControlPolicySyncAndRevocation() = runBlocking {
        // 1. Unlock child Admin Session and start pairing
        ParentSecurityManager.setupInitialPin(context, "1234")
        DeviceRoleStore.setDeviceName(context, "Leo's Tablet")
        val session = ChildRemoteServer.startPairingSession(context, InetAddress.getLoopbackAddress()).getOrThrow()

        // 2. Wrong pairing code must fail
        val wrongCodeRes = ParentRemoteClient.pairWithChild(
            context = context,
            host = "127.0.0.1",
            port = session.port,
            code = if (session.code == "000000") "999999" else "000000",
        )
        assertTrue("Wrong code must be rejected", wrongCodeRes.isFailure)

        // 3. Valid pairing code succeeds
        val pairedChild = ParentRemoteClient.pairWithChild(
            context = context,
            host = "127.0.0.1",
            port = session.port,
            code = session.code,
        ).getOrThrow()
        assertEquals("Leo's Tablet", pairedChild.displayName)
        assertEquals(1, PairedDevicesStore.pairedParents(context).size)
        assertEquals(1, PairedDevicesStore.pairedChildren(context).size)

        // 4. Reusing consumed pairing session must fail
        val reuseRes = ParentRemoteClient.pairWithChild(
            context = context,
            host = "127.0.0.1",
            port = session.port,
            code = session.code,
        )
        assertTrue("Consumed pairing session must not be reusable", reuseRes.isFailure)

        // 5. Fetch live status
        val status = ParentRemoteClient.fetchStatus(context, pairedChild).getOrThrow()
        assertEquals("Kidz Bop Song", status.title)
        assertTrue(status.isPlaying)
        assertEquals(60, status.volumePercent)

        // 6. Send playback actions (Pause, Play, Next, Previous, Volume, Sleep Timer, Rename)
        val afterPause = ParentRemoteClient.sendPlaybackAction(context, pairedChild, RemotePlaybackAction.PAUSE).getOrThrow()
        assertFalse(afterPause.isPlaying)

        val afterPlay = ParentRemoteClient.sendPlaybackAction(context, pairedChild, RemotePlaybackAction.PLAY).getOrThrow()
        assertTrue(afterPlay.isPlaying)

        ParentRemoteClient.sendPlaybackAction(context, pairedChild, RemotePlaybackAction.NEXT).getOrThrow()
        ParentRemoteClient.sendPlaybackAction(context, pairedChild, RemotePlaybackAction.PREVIOUS).getOrThrow()
        assertTrue(executedActions.any { it.action == RemotePlaybackAction.NEXT })
        assertTrue(executedActions.any { it.action == RemotePlaybackAction.PREVIOUS })

        val afterVol = ParentRemoteClient.sendPlaybackAction(
            context,
            pairedChild,
            RemotePlaybackAction.SET_VOLUME,
            volumePercent = 35,
        ).getOrThrow()
        assertEquals(35, afterVol.volumePercent)

        val afterSleep = ParentRemoteClient.sendPlaybackAction(
            context,
            pairedChild,
            RemotePlaybackAction.SET_SLEEP_TIMER,
            sleepTimerMinutes = 30,
        ).getOrThrow()
        assertEquals(30 * 60_000L, afterSleep.sleepTimerRemainingMs)

        val afterRename = ParentRemoteClient.renameChildDevice(context, pairedChild, "Leo's Bedroom Tablet").getOrThrow()
        assertEquals("Leo's Bedroom Tablet", afterRename.deviceName)
        assertEquals("Leo's Bedroom Tablet", DeviceRoleStore.deviceName(context))

        // 7. Fetch & update ParentalPolicy (including invalid regex filtering)
        val bundle = ParentRemoteClient.fetchPolicy(context, pairedChild).getOrThrow()
        assertEquals(FilterMode.FILTERED_OPEN, bundle.policy.filterMode)

        val updatedPolicy = bundle.policy.copy(
            filterMode = FilterMode.STRICT_ALLOWLIST,
            blockedKeywords = setOf("scary"),
            blockedRegexPatterns = setOf("valid.*pattern", "[invalid("),
        )
        val pushRes = ParentRemoteClient.pushPolicy(
            context = context,
            child = pairedChild,
            policy = updatedPolicy,
            ruleLabels = mapOf("playlist:abc" to RuleLabel("Bedtime Mix")),
        ).getOrThrow()
        assertEquals(FilterMode.STRICT_ALLOWLIST, pushRes.snapshot.filterMode)
        val persisted = ParentalPolicyStore.current(context)
        assertEquals(FilterMode.STRICT_ALLOWLIST, persisted.filterMode)
        assertEquals(setOf("scary"), persisted.blockedKeywords)
        assertEquals(setOf("valid.*pattern"), persisted.blockedRegexPatterns)
        assertEquals("Bedtime Mix", RuleLabelStore.all(context)["playlist:abc"]?.title)

        // 8. Pause All fan-out with mixed online + offline child
        val offlineChild = PairedChildRecord(
            childId = "offline-child",
            displayName = "Offline Phone",
            lastHost = "127.0.0.1",
            lastPort = 1, // unreachable
            secretBlobB64 = pairedChild.secretBlobB64,
            wrapped = pairedChild.wrapped,
            pairedAtMs = 0L,
        )
        playingState = true
        val pauseAllRes = ParentRemoteClient.pauseAll(context, listOf(pairedChild, offlineChild))
        assertTrue(pauseAllRes[pairedChild.childId]?.isSuccess == true)
        assertTrue(pauseAllRes[offlineChild.childId]?.isFailure == true)
        assertFalse(playingState)

        // 9. Disabling remote control blocks paired parent until re-enabled
        PairedDevicesStore.setRemoteControlEnabled(context, false)
        assertTrue(ParentRemoteClient.fetchStatus(context, pairedChild).isFailure)
        PairedDevicesStore.setRemoteControlEnabled(context, true)
        assertTrue(ParentRemoteClient.fetchStatus(context, pairedChild).isSuccess)

        // 10. Revoking parent immediately blocks subsequent commands
        val parentId = DeviceRoleStore.deviceId(context)
        PairedDevicesStore.revokePairedParent(context, parentId)
        val afterRevoke = ParentRemoteClient.fetchStatus(context, pairedChild)
        assertTrue("Revoked parent must be rejected", afterRevoke.isFailure)
    }

    @Test
    fun expiredPairingSessionIsRejected() = runBlocking {
        var now = 1_000_000L
        ChildRemoteServer.clockMs = { now }
        ParentSecurityManager.setupInitialPin(context, "1234")
        val session = ChildRemoteServer.startPairingSession(context, InetAddress.getLoopbackAddress()).getOrThrow()

        // Advance past 3-minute pairing TTL
        now += ChildRemoteServer.PAIRING_TTL_MS + 1_000L

        val res = ParentRemoteClient.pairWithChild(context, "127.0.0.1", session.port, session.code)
        assertTrue("Expired pairing session must be rejected", res.isFailure)
    }

    @Test
    fun wireRejectsReplayedChallengeTamperedCiphertextAndUnknownParent() = runBlocking {
        ParentSecurityManager.setupInitialPin(context, "1234")
        val session = ChildRemoteServer.startPairingSession(context, InetAddress.getLoopbackAddress()).getOrThrow()
        val pairedChild = ParentRemoteClient.pairWithChild(context, "127.0.0.1", session.port, session.code).getOrThrow()
        val parentId = DeviceRoleStore.deviceId(context)
        val secret = PairedDevicesStore.secretForChild(context, pairedChild.childId)!!
        val aad = RemoteProtocol.aadFor(parentId, pairedChild.childId)

        // 1. Replayed / wrong challenge nonce inside valid AES-GCM encryption -> BAD_CHALLENGE
        Socket().use { s ->
            s.connect(InetSocketAddress("127.0.0.1", session.port), 2_000)
            s.soTimeout = 3_000
            RemoteProtocol.readJsonFrame<ServerHello>(s.getInputStream())
            val forgedReq = AuthenticatedRequest(
                challengeNonceB64 = Base64.encodeToString(RemoteCrypto.randomBytes(16), Base64.NO_WRAP),
                request = RemoteRequest.GetStatus,
            )
            val (iv, ct) = RemoteCrypto.encryptAesGcm(secret, RemoteProtocol.json.encodeToString(forgedReq).toByteArray(), aad)
            RemoteProtocol.writeJsonFrame<ClientWireMessage>(
                s.getOutputStream(),
                ClientWireMessage.Encrypted(parentId, Base64.encodeToString(iv, Base64.NO_WRAP), Base64.encodeToString(ct, Base64.NO_WRAP)),
            )
            val reply = RemoteProtocol.readJsonFrame<ServerWireMessage>(s.getInputStream())
            assertTrue(reply is ServerWireMessage.Error && reply.code == "BAD_CHALLENGE")
        }

        // 2. Tampered ciphertext -> DECRYPT_FAILED
        Socket().use { s ->
            s.connect(InetSocketAddress("127.0.0.1", session.port), 2_000)
            s.soTimeout = 3_000
            val hello = RemoteProtocol.readJsonFrame<ServerHello>(s.getInputStream())
            val validReq = AuthenticatedRequest(hello.challengeNonceB64, RemoteRequest.GetStatus)
            val (iv, ct) = RemoteCrypto.encryptAesGcm(secret, RemoteProtocol.json.encodeToString(validReq).toByteArray(), aad)
            ct[0] = (ct[0].toInt() xor 0xFF).toByte()
            RemoteProtocol.writeJsonFrame<ClientWireMessage>(
                s.getOutputStream(),
                ClientWireMessage.Encrypted(parentId, Base64.encodeToString(iv, Base64.NO_WRAP), Base64.encodeToString(ct, Base64.NO_WRAP)),
            )
            val reply = RemoteProtocol.readJsonFrame<ServerWireMessage>(s.getInputStream())
            assertTrue(reply is ServerWireMessage.Error && reply.code == "DECRYPT_FAILED")
        }

        // 3. Unknown parentId -> UNAUTHORIZED
        Socket().use { s ->
            s.connect(InetSocketAddress("127.0.0.1", session.port), 2_000)
            s.soTimeout = 3_000
            RemoteProtocol.readJsonFrame<ServerHello>(s.getInputStream())
            RemoteProtocol.writeJsonFrame<ClientWireMessage>(
                s.getOutputStream(),
                ClientWireMessage.Encrypted("unknown-parent", Base64.encodeToString(ByteArray(12), Base64.NO_WRAP), Base64.encodeToString(ByteArray(32), Base64.NO_WRAP)),
            )
            val reply = RemoteProtocol.readJsonFrame<ServerWireMessage>(s.getInputStream())
            assertTrue(reply is ServerWireMessage.Error && reply.code == "UNAUTHORIZED")
        }
    }
}
