/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.parental.remote.net

import android.content.Context
import android.util.Base64
import io.github.thake.soundnest.feature.parental.remote.crypto.RemoteCrypto
import io.github.thake.soundnest.feature.parental.remote.settings.DeviceRole
import io.github.thake.soundnest.feature.parental.remote.settings.DeviceRoleStore
import io.github.thake.soundnest.feature.parental.remote.settings.PairedDevicesStore
import io.github.thake.soundnest.feature.parental.security.ParentSecurityManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.encodeToString
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

data class ActiveParentTransferSession(
    val code: String,
    val expiresAtMs: Long,
    val host: String,
    val port: Int,
    val inviteUri: String,
    val childCount: Int,
)

data class CompletedParentTransfer(
    val targetParentName: String,
    val transferredCount: Int,
    val upgradedCount: Int,
)

/**
 * Ephemeral single-use encrypted LAN server run by an already-paired Parent Device (Parent A)
 * to onboard a second Parent Device (Parent B) and transfer all paired children.
 *
 * Online children are immediately registered with a fresh, independent 256-bit key for Parent B
 * via `RemoteRequest.RegisterParent`. Offline children are transferred with a temporary delegated
 * fallback (`authParentId`) that Parent B automatically upgrades on first contact.
 */
object ParentTransferServer {
    private const val TRANSFER_TTL_MS = 3 * 60 * 1_000L
    private const val SOCKET_TIMEOUT_MS = 10_000
    private const val MAX_TRANSFER_ATTEMPTS = 5
    private const val MAX_CONCURRENT_CLIENTS = 8

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val clientSemaphore = Semaphore(MAX_CONCURRENT_CLIENTS)
    private val failedAttempts = AtomicInteger(0)

    @Volatile
    internal var clockMs: () -> Long = { System.currentTimeMillis() }

    private val activeSession = AtomicReference<ActiveParentTransferSession?>(null)
    private val _sessionFlow = MutableStateFlow<ActiveParentTransferSession?>(null)
    val sessionFlow: StateFlow<ActiveParentTransferSession?> = _sessionFlow.asStateFlow()

    private val _completedFlow = MutableStateFlow<CompletedParentTransfer?>(null)
    val completedFlow: StateFlow<CompletedParentTransfer?> = _completedFlow.asStateFlow()

    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var acceptJob: Job? = null
    @Volatile private var expiryJob: Job? = null
    @Volatile private var sourceParentIdOverride: String? = null
    @Volatile private var sourceParentNameOverride: String? = null

    @Synchronized
    fun startTransferSession(
        context: Context,
        bindAddress: InetAddress? = null,
        sourceParentIdOverride: String? = null,
        sourceParentNameOverride: String? = null,
    ): Result<ActiveParentTransferSession> = runCatching {
        stop()
        val app = context.applicationContext
        if (DeviceRoleStore.currentRole(app) == DeviceRole.STANDARD &&
            ParentSecurityManager.isPinConfigured(app) &&
            !ParentSecurityManager.isAdminUnlocked(app)
        ) {
            throw SecurityException("Parent PIN unlock required to add a second parent device")
        }
        val children = PairedDevicesStore.pairedChildren(app)
        require(children.isNotEmpty()) { "No paired children to transfer" }

        this.sourceParentIdOverride = sourceParentIdOverride
        this.sourceParentNameOverride = sourceParentNameOverride
        _completedFlow.value = null

        val host = bindAddress?.hostAddress
            ?: ChildRemoteServer.resolveLocalLanIpv4(app)
            ?: error("Connect to Wi-Fi to add a second parent device")
        val effectiveBind = bindAddress ?: runCatching { InetAddress.getByName(host) }.getOrNull()
        val ss = if (effectiveBind != null) ServerSocket(0, 8, effectiveBind) else ServerSocket(0, 8)
        serverSocket = ss
        val port = ss.localPort
        val code = RemoteCrypto.generateSixDigitCode()
        val expiresAt = clockMs() + TRANSFER_TTL_MS
        val sourceId = sourceParentIdOverride ?: DeviceRoleStore.deviceId(app)
        val sourceName = sourceParentNameOverride ?: DeviceRoleStore.deviceName(app)
        val uri = PairingUri.encodeParentTransfer(
            ParentTransferInvite(
                sourceParentId = sourceId,
                host = host,
                port = port,
                code = code,
                deviceName = sourceName,
            ),
        )
        val session = ActiveParentTransferSession(
            code = code,
            expiresAtMs = expiresAt,
            host = host,
            port = port,
            inviteUri = uri,
            childCount = children.size,
        )
        activeSession.set(session)
        failedAttempts.set(0)
        _sessionFlow.value = session

        acceptJob = scope.launch {
            while (isActive && !ss.isClosed) {
                val client = runCatching { ss.accept() }.getOrNull() ?: break
                launch {
                    clientSemaphore.withPermit {
                        handleClient(app, client)
                    }
                }
            }
        }
        expiryJob = scope.launch {
            delay(TRANSFER_TTL_MS)
            if (activeSession.get() === session) {
                stop()
            }
        }
        session
    }

    @Synchronized
    fun stop() {
        expiryJob?.cancel()
        expiryJob = null
        acceptJob?.cancel()
        acceptJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        activeSession.set(null)
        failedAttempts.set(0)
        _sessionFlow.value = null
        sourceParentIdOverride = null
        sourceParentNameOverride = null
    }

    private fun currentValidSession(): ActiveParentTransferSession? {
        val s = activeSession.get() ?: return null
        if (clockMs() > s.expiresAtMs) {
            if (activeSession.compareAndSet(s, null)) {
                _sessionFlow.value = null
            }
            return null
        }
        return s
    }

    private fun handleClient(context: Context, socket: Socket) {
        socket.use { s ->
            runCatching {
                s.soTimeout = SOCKET_TIMEOUT_MS
                val input = s.getInputStream()
                val output = s.getOutputStream()

                val session = currentValidSession()
                if (session == null) {
                    RemoteProtocol.writeJsonFrame<ParentTransferReply>(
                        output,
                        ParentTransferReply.Error("TRANSFER_CLOSED", "Transfer session expired or already used"),
                    )
                    return
                }

                val sourceId = sourceParentIdOverride ?: DeviceRoleStore.deviceId(context)
                val sourceName = sourceParentNameOverride ?: DeviceRoleStore.deviceName(context)
                val challengeNonce = RemoteCrypto.randomBytes(16)
                val challengeB64 = Base64.encodeToString(challengeNonce, Base64.NO_WRAP)

                RemoteProtocol.writeJsonFrame(
                    output,
                    ParentTransferHello(
                        version = RemoteProtocol.PROTOCOL_VERSION,
                        sourceParentId = sourceId,
                        sourceParentName = sourceName,
                        childCount = PairedDevicesStore.pairedChildren(context).size,
                        challengeNonceB64 = challengeB64,
                    ),
                )

                val init = RemoteProtocol.readJsonFrame<ParentTransferInit>(input)
                val newParentPubBytes = runCatching { Base64.decode(init.newParentEcdhPubB64, Base64.NO_WRAP) }.getOrNull()
                val proofBytes = runCatching { Base64.decode(init.proofHmacB64, Base64.NO_WRAP) }.getOrNull()
                if (newParentPubBytes == null || proofBytes == null || init.newParentId.isBlank()) {
                    RemoteProtocol.writeJsonFrame<ParentTransferReply>(
                        output,
                        ParentTransferReply.Error("BAD_REQUEST", "Malformed transfer payload"),
                    )
                    return
                }

                val transferAuthKey = RemoteCrypto.derivePairingKey(session.code, challengeNonce, sourceId)
                val expectedProofInput =
                    "parent-transfer-init|${init.newParentId}|$sourceId|".toByteArray(Charsets.UTF_8) + newParentPubBytes
                if (!RemoteCrypto.verifyHmacSha256(transferAuthKey, expectedProofInput, proofBytes)) {
                    if (failedAttempts.incrementAndGet() >= MAX_TRANSFER_ATTEMPTS) {
                        if (activeSession.compareAndSet(session, null)) {
                            _sessionFlow.value = null
                            scope.launch { stop() }
                        }
                    }
                    RemoteProtocol.writeJsonFrame<ParentTransferReply>(
                        output,
                        ParentTransferReply.Error("INVALID_CODE", "Transfer code verification failed"),
                    )
                    return
                }

                // Single-use: atomically consume session before generating/registering child keys
                if (!activeSession.compareAndSet(session, null)) {
                    RemoteProtocol.writeJsonFrame<ParentTransferReply>(
                        output,
                        ParentTransferReply.Error("TRANSFER_USED", "Transfer session already consumed"),
                    )
                    return
                }
                _sessionFlow.value = null

                val children = PairedDevicesStore.pairedChildren(context)
                val transferredEntries: List<TransferredChildEntry> = runBlocking {
                    children.map { child ->
                        async(Dispatchers.IO) {
                            val freshSecret = RemoteCrypto.randomBytes(32)
                            val registered = ParentRemoteClient.registerCoParent(
                                context = context,
                                child = child,
                                newParentId = init.newParentId,
                                newParentName = init.newParentName,
                                newSecret = freshSecret,
                                parentIdOverride = sourceId,
                            ).isSuccess
                            if (registered) {
                                TransferredChildEntry(
                                    childId = child.childId,
                                    displayName = child.displayName,
                                    lastHost = child.lastHost,
                                    lastPort = child.lastPort,
                                    secretB64 = Base64.encodeToString(freshSecret, Base64.NO_WRAP),
                                    authParentId = null,
                                    upgraded = true,
                                )
                            } else {
                                val existingSecret = PairedDevicesStore.secretForChild(context, child.childId)
                                    ?: return@async null
                                TransferredChildEntry(
                                    childId = child.childId,
                                    displayName = child.displayName,
                                    lastHost = child.lastHost,
                                    lastPort = child.lastPort,
                                    secretB64 = Base64.encodeToString(existingSecret, Base64.NO_WRAP),
                                    authParentId = child.authParentId ?: sourceId,
                                    upgraded = false,
                                )
                            }
                        }
                    }.awaitAll().filterNotNull()
                }

                val sourceKp = RemoteCrypto.generateEcdhKeyPair()
                val sourcePubBytes = sourceKp.public.encoded
                val ecdhShared = RemoteCrypto.deriveEcdhSharedSecret(sourceKp.private, newParentPubBytes)
                val encKey = RemoteCrypto.derivePermanentSecret(ecdhShared, challengeNonce, init.newParentId, sourceId)
                val aad = "soundnest-parent-transfer-v1|${init.newParentId}|$sourceId".toByteArray(Charsets.UTF_8)

                val payload = ParentTransferPayload(
                    challengeNonceB64 = challengeB64,
                    sourceParentName = sourceName,
                    children = transferredEntries,
                )
                val plainBytes = RemoteProtocol.json.encodeToString(payload).toByteArray(Charsets.UTF_8)
                val (iv, ct) = RemoteCrypto.encryptAesGcm(encKey, plainBytes, aad)

                val respMacInput =
                    "parent-transfer-complete|${init.newParentId}|$sourceId|".toByteArray(Charsets.UTF_8) +
                        newParentPubBytes + sourcePubBytes
                val respMac = RemoteCrypto.hmacSha256(transferAuthKey, respMacInput)

                RemoteProtocol.writeJsonFrame<ParentTransferReply>(
                    output,
                    ParentTransferReply.Complete(
                        sourceEcdhPubB64 = Base64.encodeToString(sourcePubBytes, Base64.NO_WRAP),
                        proofHmacB64 = Base64.encodeToString(respMac, Base64.NO_WRAP),
                        ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP),
                        ciphertextB64 = Base64.encodeToString(ct, Base64.NO_WRAP),
                    ),
                )

                _completedFlow.value = CompletedParentTransfer(
                    targetParentName = init.newParentName.ifBlank { "Parent Device" },
                    transferredCount = transferredEntries.size,
                    upgradedCount = transferredEntries.count { it.upgraded },
                )
                stop()
            }
        }
    }

    @Synchronized
    internal fun resetForTests() {
        stop()
        _completedFlow.value = null
        clockMs = { System.currentTimeMillis() }
    }
}
