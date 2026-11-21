/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.parental.remote.net

import android.content.Context
import android.util.Base64
import io.github.thake.soundnest.feature.parental.remote.crypto.RemoteCrypto
import io.github.thake.soundnest.feature.parental.remote.settings.DeviceRoleStore
import io.github.thake.soundnest.feature.parental.remote.settings.PairedChildRecord
import io.github.thake.soundnest.feature.parental.remote.settings.PairedDevicesStore
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy
import io.github.thake.soundnest.feature.parental.settings.RuleLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

data class ChildLiveState(
    val child: PairedChildRecord,
    val online: Boolean,
    val snapshot: ChildStatusSnapshot? = null,
    val lastError: String? = null,
    val updatedAtMs: Long = 0L,
)

/**
 * Encrypted TCP client used by a Parent Device to pair with and control paired Child Devices
 * on the same Wi-Fi network.
 */
object ParentRemoteClient {
    private const val CONNECT_TIMEOUT_MS = 4_000
    private const val READ_TIMEOUT_MS = 5_000

    suspend fun pairWithChild(
        context: Context,
        host: String,
        port: Int,
        code: String,
    ): Result<PairedChildRecord> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanCode = code.filter { it.isDigit() }
            require(cleanCode.length == 6) { "Pairing code must be 6 digits" }
            val parentId = DeviceRoleStore.deviceId(context)
            val parentName = DeviceRoleStore.deviceName(context)

            Socket().use { s ->
                s.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                s.soTimeout = READ_TIMEOUT_MS
                val input = s.getInputStream()
                val output = s.getOutputStream()

                val hello = RemoteProtocol.readJsonFrame<ServerHello>(input)
                require(hello.version == RemoteProtocol.PROTOCOL_VERSION) { "Unsupported protocol version: ${hello.version}" }
                require(hello.pairingOpen) { "Child device is not currently in pairing mode" }

                val challengeNonce = Base64.decode(hello.challengeNonceB64, Base64.NO_WRAP)
                val parentKp = RemoteCrypto.generateEcdhKeyPair()
                val parentPubBytes = parentKp.public.encoded
                val pairingKey = RemoteCrypto.derivePairingKey(cleanCode, challengeNonce, hello.childId)
                val proofInput = "pair-init|$parentId|${hello.childId}|".toByteArray(Charsets.UTF_8) + parentPubBytes
                val proofMac = RemoteCrypto.hmacSha256(pairingKey, proofInput)

                RemoteProtocol.writeJsonFrame<ClientWireMessage>(
                    output,
                    ClientWireMessage.PairInit(
                        parentId = parentId,
                        parentName = parentName,
                        parentEcdhPubB64 = Base64.encodeToString(parentPubBytes, Base64.NO_WRAP),
                        proofHmacB64 = Base64.encodeToString(proofMac, Base64.NO_WRAP),
                    ),
                )

                when (val reply = RemoteProtocol.readJsonFrame<ServerWireMessage>(input)) {
                    is ServerWireMessage.Error -> error(reply.message)
                    is ServerWireMessage.PairComplete -> {
                        require(reply.childId == hello.childId) { "Child ID mismatch" }
                        val childPubBytes = Base64.decode(reply.childEcdhPubB64, Base64.NO_WRAP)
                        val respMac = Base64.decode(reply.proofHmacB64, Base64.NO_WRAP)
                        val expectedRespInput =
                            "pair-complete|$parentId|${hello.childId}|".toByteArray(Charsets.UTF_8) + parentPubBytes + childPubBytes
                        require(RemoteCrypto.verifyHmacSha256(pairingKey, expectedRespInput, respMac)) {
                            "Child device authentication failed"
                        }
                        val ecdhShared = RemoteCrypto.deriveEcdhSharedSecret(parentKp.private, childPubBytes)
                        val permanentSecret = RemoteCrypto.derivePermanentSecret(
                            ecdhSharedSecret = ecdhShared,
                            nonce = challengeNonce,
                            parentId = parentId,
                            childId = reply.childId,
                        )
                        PairedDevicesStore.upsertPairedChild(
                            context = context,
                            childId = reply.childId,
                            displayName = reply.childName.ifBlank { hello.deviceName },
                            host = host,
                            port = port,
                            rawSecret = permanentSecret,
                        )
                    }
                    else -> error("Unexpected server reply during pairing")
                }
            }
        }
    }

    suspend fun fetchStatus(context: Context, child: PairedChildRecord): Result<ChildStatusSnapshot> =
        sendEncryptedRequest(context, child, RemoteRequest.GetStatus).mapCatching { resp ->
            when (resp) {
                is RemoteResponse.Status -> resp.snapshot
                is RemoteResponse.Error -> error(resp.message)
                else -> error("Unexpected status response")
            }
        }

    suspend fun sendPlaybackAction(
        context: Context,
        child: PairedChildRecord,
        action: RemotePlaybackAction,
        volumePercent: Int? = null,
        sleepTimerMinutes: Int? = null,
    ): Result<ChildStatusSnapshot> =
        sendEncryptedRequest(
            context,
            child,
            RemoteRequest.PlaybackAction(action, volumePercent, sleepTimerMinutes),
        ).mapCatching { resp ->
            when (resp) {
                is RemoteResponse.Status -> resp.snapshot
                is RemoteResponse.Error -> error(resp.message)
                else -> error("Unexpected playback response")
            }
        }

    suspend fun fetchPolicy(context: Context, child: PairedChildRecord): Result<RemotePolicyBundle> =
        sendEncryptedRequest(context, child, RemoteRequest.GetPolicy).mapCatching { resp ->
            when (resp) {
                is RemoteResponse.Policy -> resp.bundle
                is RemoteResponse.Error -> error(resp.message)
                else -> error("Unexpected policy response")
            }
        }

    suspend fun pushPolicy(
        context: Context,
        child: PairedChildRecord,
        policy: ParentalPolicy,
        ruleLabels: Map<String, RuleLabel>,
        expectedRevision: Long? = null,
    ): Result<RemoteResponse.PolicyUpdated> =
        sendEncryptedRequest(
            context,
            child,
            RemoteRequest.UpdatePolicy(policy, ruleLabels, expectedRevision),
        ).mapCatching { resp ->
            when (resp) {
                is RemoteResponse.PolicyUpdated -> resp
                is RemoteResponse.Error -> error(resp.message)
                else -> error("Unexpected policy update response")
            }
        }

    suspend fun renameChildDevice(
        context: Context,
        child: PairedChildRecord,
        newName: String,
    ): Result<ChildStatusSnapshot> =
        sendEncryptedRequest(context, child, RemoteRequest.RenameDevice(newName)).mapCatching { resp ->
            when (resp) {
                is RemoteResponse.Status -> {
                    PairedDevicesStore.renameChild(context, child.childId, resp.snapshot.deviceName)
                    resp.snapshot
                }
                is RemoteResponse.Error -> error(resp.message)
                else -> error("Unexpected rename response")
            }
        }

    suspend fun pauseAll(context: Context, children: List<PairedChildRecord>): Map<String, Result<ChildStatusSnapshot>> =
        coroutineScope {
            children.map { child ->
                async { child.childId to sendPlaybackAction(context, child, RemotePlaybackAction.PAUSE) }
            }.awaitAll().toMap()
        }

    suspend fun pollAll(context: Context, children: List<PairedChildRecord>): Map<String, ChildLiveState> =
        coroutineScope {
            children.map { child ->
                async {
                    val res = fetchStatus(context, child)
                    val now = System.currentTimeMillis()
                    child.childId to res.fold(
                        onSuccess = { snap ->
                            if (snap.deviceName.isNotBlank() && snap.deviceName != child.displayName) {
                                PairedDevicesStore.renameChild(context, child.childId, snap.deviceName)
                            }
                            ChildLiveState(child = child, online = true, snapshot = snap, updatedAtMs = now)
                        },
                        onFailure = { e ->
                            ChildLiveState(child = child, online = false, lastError = e.message, updatedAtMs = now)
                        },
                    )
                }
            }.awaitAll().toMap()
        }

    private suspend fun sendEncryptedRequest(
        context: Context,
        child: PairedChildRecord,
        request: RemoteRequest,
    ): Result<RemoteResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val secret = PairedDevicesStore.secretForChild(context, child.childId)
                ?: error("Missing pairing key for ${child.displayName}")
            val parentId = DeviceRoleStore.deviceId(context)
            val discovered = ParentLanDiscovery.discovered.value[child.childId]
            val host = discovered?.host ?: child.lastHost
            val port = discovered?.port ?: child.lastPort
            require(host.isNotBlank() && port in 1..65535) { "No known Wi-Fi address for ${child.displayName}" }

            Socket().use { s ->
                s.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                s.soTimeout = READ_TIMEOUT_MS
                val input = s.getInputStream()
                val output = s.getOutputStream()

                val hello = RemoteProtocol.readJsonFrame<ServerHello>(input)
                require(hello.childId == child.childId) { "Connected to unexpected device" }

                val aad = RemoteProtocol.aadFor(parentId, child.childId)
                val authReq = AuthenticatedRequest(
                    challengeNonceB64 = hello.challengeNonceB64,
                    request = request,
                )
                val plainBytes = RemoteProtocol.json.encodeToString(authReq).toByteArray(Charsets.UTF_8)
                val (iv, ct) = RemoteCrypto.encryptAesGcm(secret, plainBytes, aad)

                RemoteProtocol.writeJsonFrame<ClientWireMessage>(
                    output,
                    ClientWireMessage.Encrypted(
                        parentId = parentId,
                        ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP),
                        ciphertextB64 = Base64.encodeToString(ct, Base64.NO_WRAP),
                    ),
                )

                when (val reply = RemoteProtocol.readJsonFrame<ServerWireMessage>(input)) {
                    is ServerWireMessage.Error -> error(reply.message)
                    is ServerWireMessage.Encrypted -> {
                        val rIv = Base64.decode(reply.ivB64, Base64.NO_WRAP)
                        val rCt = Base64.decode(reply.ciphertextB64, Base64.NO_WRAP)
                        val rPlain = RemoteCrypto.decryptAesGcm(secret, rIv, rCt, aad)
                        val authResp = RemoteProtocol.json.decodeFromString<AuthenticatedResponse>(
                            rPlain.toString(Charsets.UTF_8),
                        )
                        require(authResp.challengeNonceB64 == hello.challengeNonceB64) { "Response challenge mismatch" }
                        authResp.response
                    }
                    else -> error("Unexpected wire message")
                }
            }
        }
    }
}
