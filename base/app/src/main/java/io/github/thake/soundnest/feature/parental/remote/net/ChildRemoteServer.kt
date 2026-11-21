/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.parental.remote.net

import android.content.Context
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Base64
import io.github.thake.soundnest.BuildConfig
import io.github.thake.soundnest.core.catalog.Api
import io.github.thake.soundnest.core.di.Graph
import io.github.thake.soundnest.core.model.HomeItem
import io.github.thake.soundnest.core.playback.SongPlayer
import io.github.thake.soundnest.feature.parental.remote.crypto.RemoteCrypto
import io.github.thake.soundnest.feature.parental.remote.settings.DeviceRole
import io.github.thake.soundnest.feature.parental.remote.settings.DeviceRoleStore
import io.github.thake.soundnest.feature.parental.remote.settings.PairedDevicesStore
import io.github.thake.soundnest.feature.parental.security.ParentSecurityManager
import io.github.thake.soundnest.feature.parental.settings.ParentalConfigBackup
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore
import io.github.thake.soundnest.feature.parental.settings.RuleLabel
import io.github.thake.soundnest.feature.parental.settings.RuleLabelStore
import io.github.thake.soundnest.feature.player.sleeptimer.playback.SleepTimerController
import io.github.thake.soundnest.feature.speakers.settings.TrustedNetworkGate
import io.github.thake.soundnest.feature.spokenword.audible.data.AudibleCatalogCache
import io.github.thake.soundnest.feature.spokenword.audible.settings.AudibleCredentialStore
import io.github.thake.soundnest.host.android.SoundNestRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicReference

data class ActivePairingSession(
    val code: String,
    val expiresAtMs: Long,
    val host: String,
    val port: Int,
    val inviteUri: String,
)

/**
 * Abstraction over local child playback & policy operations so [ChildRemoteServer]
 * can be tested cleanly on JVM/Robolectric without a live Spotify WebView.
 */
interface ChildControlDelegate {
    fun getStatus(context: Context): ChildStatusSnapshot
    fun executePlayback(context: Context, action: RemoteRequest.PlaybackAction): ChildStatusSnapshot
    fun getPolicy(context: Context): RemotePolicyBundle
    fun updatePolicy(context: Context, policy: ParentalPolicy, ruleLabels: Map<String, RuleLabel>): Pair<Long, ChildStatusSnapshot>
    fun renameDevice(context: Context, newName: String): ChildStatusSnapshot
    fun searchCatalog(context: Context, query: String): List<RemoteCatalogItem> = emptyList()
    fun getChildCatalog(context: Context): RemoteResponse.ChildCatalog = RemoteResponse.ChildCatalog()
}

object DefaultChildControlDelegate : ChildControlDelegate {
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    override fun getStatus(context: Context): ChildStatusSnapshot {
        val state = runCatching { Graph.currentSongState }.getOrNull()
        val policy = ParentalPolicyStore.current(context)
        val revision = ParentalPolicyStore.revision.value
        val audio = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val volumePercent = if (audio != null) {
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val cur = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            ((cur * 100f) / max).toInt().coerceIn(0, 100)
        } else {
            50
        }
        val sleepRemaining: Long? = when {
            SleepTimerController.sleepTimerEndOfTrack -> -1L
            else -> SleepTimerController.sleepTimerEndsAtMs?.let { endsAt ->
                (endsAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            }
        }
        val provider = runCatching { SongPlayer.nowPlaying.value.provider }.getOrDefault("")
        val isPlaying = runCatching { SongPlayer.isPlaying() }.getOrElse { state?.playingState?.value ?: false }
        val posMs = runCatching { SongPlayer.getCurrentPosition() }.getOrDefault(0L)
        val durMs = runCatching { SongPlayer.getDuration() }.getOrDefault(0L)
        return ChildStatusSnapshot(
            childId = DeviceRoleStore.deviceId(context),
            deviceName = DeviceRoleStore.deviceName(context),
            appVersion = BuildConfig.VERSION_NAME,
            title = state?.title?.value.orEmpty(),
            artist = state?.singer?.value.orEmpty(),
            album = state?.album?.value.orEmpty(),
            coverUrl = state?.coverUri?.value.orEmpty(),
            isPlaying = isPlaying,
            positionMs = posMs,
            durationMs = durMs,
            playbackSource = provider,
            volumePercent = volumePercent,
            sleepTimerRemainingMs = sleepRemaining,
            policyRevision = revision,
            filterMode = policy.filterMode,
            blockExplicit = policy.blockExplicit,
        )
    }

    override fun executePlayback(context: Context, action: RemoteRequest.PlaybackAction): ChildStatusSnapshot {
        when (action.action) {
            RemotePlaybackAction.PLAY -> mainHandler.post { runCatching { SongPlayer.play(by = "parent_remote") } }
            RemotePlaybackAction.PAUSE -> mainHandler.post { runCatching { SongPlayer.pause(by = "parent_remote") } }
            RemotePlaybackAction.NEXT -> mainHandler.post { runCatching { SongPlayer.next(by = "parent_remote") } }
            RemotePlaybackAction.PREVIOUS -> mainHandler.post { runCatching { SongPlayer.previous(by = "parent_remote") } }
            RemotePlaybackAction.SET_VOLUME -> {
                val pct = (action.volumePercent ?: 50).coerceIn(0, 100)
                val audio = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                if (audio != null) {
                    val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                    val target = ((pct / 100f) * max).toInt().coerceIn(0, max)
                    runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0) }
                }
            }
            RemotePlaybackAction.SET_SLEEP_TIMER -> {
                mainHandler.post { runCatching { SleepTimerController.setSleepTimer(action.sleepTimerMinutes) } }
            }
        }
        return getStatus(context)
    }

    override fun getPolicy(context: Context): RemotePolicyBundle {
        val payload = ParentalConfigBackup.exportPayload(context)
        return RemotePolicyBundle(
            childId = DeviceRoleStore.deviceId(context),
            deviceName = DeviceRoleStore.deviceName(context),
            policy = payload.policy,
            ruleLabels = payload.ruleLabels,
            policyRevision = ParentalPolicyStore.revision.value,
        )
    }

    override fun updatePolicy(
        context: Context,
        policy: ParentalPolicy,
        ruleLabels: Map<String, RuleLabel>,
    ): Pair<Long, ChildStatusSnapshot> {
        val validRegexes = policy.blockedRegexPatterns.filter { pattern ->
            pattern.isNotBlank() && runCatching { Regex(pattern, RegexOption.IGNORE_CASE) }.isSuccess
        }.toSet()
        val sanitized = policy.copy(blockedRegexPatterns = validRegexes)
        RuleLabelStore.mergeAll(context, ruleLabels)
        ParentalPolicyStore.replace(context, sanitized)
        return ParentalPolicyStore.revision.value to getStatus(context)
    }

    override fun renameDevice(context: Context, newName: String): ChildStatusSnapshot {
        DeviceRoleStore.setDeviceName(context, newName)
        return getStatus(context)
    }

    override fun searchCatalog(context: Context, query: String): List<RemoteCatalogItem> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val out = mutableListOf<RemoteCatalogItem>()
        // Include matching Audible books from local cache first
        runCatching {
            AudibleCatalogCache.books(context)
                .filter { b ->
                    val authors = b.authors.joinToString(", ")
                    b.title.contains(q, ignoreCase = true) || authors.contains(q, ignoreCase = true)
                }
                .take(10)
                .forEach { b ->
                    out += RemoteCatalogItem(
                        kind = "audible",
                        id = b.asin,
                        title = b.title,
                        subtitle = b.authors.joinToString(", "),
                        coverUrl = b.coverUrl,
                    )
                }
        }
        // Unfiltered Spotify catalog search via child's active session (no credentials leave the child)
        if (runCatching { SoundNestRuntime.isStarted && SoundNestRuntime.api.ready.value }.getOrDefault(false)) {
            runCatching {
                runBlocking {
                    val api = SoundNestRuntime.api
                    val music = runCatching { api.search.all(q, limit = 12) }.getOrNull()
                    if (music != null) {
                        music.tracks.items.forEach { t ->
                            out += RemoteCatalogItem(
                                kind = "track",
                                id = ParentalPolicyStore.normalizeSpotifyId(t.uri),
                                title = t.name,
                                subtitle = t.artists.joinToString(", ") { it.name },
                                coverUrl = t.album?.images?.firstOrNull()?.url.orEmpty(),
                                explicit = t.explicit,
                            )
                        }
                        music.artists.items.forEach { a ->
                            out += RemoteCatalogItem(
                                kind = "artist",
                                id = ParentalPolicyStore.normalizeSpotifyId(a.uri),
                                title = a.name,
                                subtitle = "",
                                coverUrl = a.images.firstOrNull()?.url.orEmpty(),
                            )
                        }
                        music.albums.items.forEach { al ->
                            out += RemoteCatalogItem(
                                kind = "album",
                                id = ParentalPolicyStore.normalizeSpotifyId(al.uri),
                                title = al.name,
                                subtitle = al.artists.joinToString(", ") { it.name },
                                coverUrl = al.images.firstOrNull()?.url.orEmpty(),
                            )
                        }
                        music.playlists.items.forEach { pl ->
                            out += RemoteCatalogItem(
                                kind = "playlist",
                                id = ParentalPolicyStore.normalizeSpotifyId(pl.uri),
                                title = pl.name,
                                subtitle = pl.owner.orEmpty(),
                                coverUrl = pl.images.firstOrNull()?.url.orEmpty(),
                            )
                        }
                    }
                    val shows = runCatching { api.search.shows(q, limit = 8).items }.getOrDefault(emptyList())
                    shows.forEach { sh ->
                        out += RemoteCatalogItem(
                            kind = "show",
                            id = ParentalPolicyStore.normalizeSpotifyId(sh.uri),
                            title = sh.title,
                            subtitle = sh.subtitle.orEmpty(),
                            coverUrl = sh.images.firstOrNull()?.url.orEmpty(),
                        )
                    }
                }
            }
        }
        return out.filter { it.id.isNotBlank() && it.title.isNotBlank() }.distinctBy { "${it.kind}:${it.id}" }
    }

    override fun getChildCatalog(context: Context): RemoteResponse.ChildCatalog {
        val libraryItems = mutableListOf<RemoteCatalogItem>()
        runCatching {
            Api.HomeCache.library.orEmpty().forEach { entry ->
                val rawId = entry.spotifyId
                val kind = when {
                    rawId.startsWith(Api.SHOW_PREFIX) -> "show"
                    entry.isPlaylist -> "playlist"
                    else -> "album"
                }
                val id = ParentalPolicyStore.normalizeSpotifyId(rawId.removePrefix(Api.SHOW_PREFIX))
                if (id.isNotBlank()) {
                    libraryItems += RemoteCatalogItem(
                        kind = kind,
                        id = id,
                        title = entry.name,
                        subtitle = entry.subtitle.ifBlank { entry.artists },
                        coverUrl = entry.coverUri,
                    )
                }
            }
            Api.HomeCache.artists.orEmpty().forEach { a ->
                val id = ParentalPolicyStore.normalizeSpotifyId(a.id)
                if (id.isNotBlank()) {
                    libraryItems += RemoteCatalogItem(
                        kind = "artist",
                        id = id,
                        title = a.name,
                        coverUrl = a.coverUri,
                    )
                }
            }
        }

        val homeItems = mutableListOf<RemoteCatalogItem>()
        runCatching {
            Api.HomeCache.home?.sections.orEmpty().flatMap { it.items }.forEach { item ->
                val mapped = when (item) {
                    is HomeItem.Playlist -> RemoteCatalogItem("playlist", ParentalPolicyStore.normalizeSpotifyId(item.id), item.name, item.subtitle, item.imageUrl)
                    is HomeItem.Album -> RemoteCatalogItem("album", ParentalPolicyStore.normalizeSpotifyId(item.id), item.name, item.artists.ifBlank { item.subtitle }, item.imageUrl)
                    is HomeItem.Artist -> RemoteCatalogItem("artist", ParentalPolicyStore.normalizeSpotifyId(item.id), item.name, "", item.imageUrl)
                    is HomeItem.Show -> RemoteCatalogItem("show", ParentalPolicyStore.normalizeSpotifyId(item.id), item.name, item.subtitle, item.imageUrl)
                    else -> null
                }
                if (mapped != null && mapped.id.isNotBlank()) homeItems += mapped
            }
        }

        val audibleConnected = runCatching { AudibleCredentialStore.summary(context) != null }.getOrDefault(false)
        val audibleBooks = runCatching {
            AudibleCatalogCache.books(context).map { b ->
                RemoteCatalogItem(
                    kind = "audible",
                    id = b.asin,
                    title = b.title,
                    subtitle = b.authors.joinToString(", "),
                    coverUrl = b.coverUrl,
                )
            }
        }.getOrDefault(emptyList())

        return RemoteResponse.ChildCatalog(
            libraryItems = libraryItems.distinctBy { "${it.kind}:${it.id}" },
            homeItems = homeItems.distinctBy { "${it.kind}:${it.id}" },
            audibleBooks = audibleBooks,
            audibleConnected = audibleConnected,
        )
    }
}

/**
 * Local Wi-Fi TCP server + mDNS advertiser running on a Child Device.
 *
 * Security invariants:
 * - Pairing can ONLY be opened when [ParentSecurityManager.isAdminUnlocked] is true.
 * - Pairing uses ephemeral ECDH (P-256) authenticated with the 6-digit code + connection challenge nonce.
 * - Post-pairing commands require AES-256-GCM authentication with the paired parent's secret and fresh challenge nonce.
 * - Never exposes Spotify cookies, Audible tokens, or Parent PIN hashes.
 */
object ChildRemoteServer {
    const val PAIRING_TTL_MS = 3 * 60_000L
    private const val SOCKET_TIMEOUT_MS = 8_000

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    internal var delegate: ChildControlDelegate = DefaultChildControlDelegate

    @Volatile
    internal var clockMs: () -> Long = { System.currentTimeMillis() }

    @Volatile
    internal var enableMdns: Boolean = true

    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private var nsdManager: NsdManager? = null
    private var regListener: NsdManager.RegistrationListener? = null
    private var appContext: Context? = null

    private val activePairing = AtomicReference<ActivePairingSession?>(null)

    private val _pairingSessionFlow = MutableStateFlow<ActivePairingSession?>(null)
    val pairingSessionFlow: StateFlow<ActivePairingSession?> = _pairingSessionFlow.asStateFlow()

    private val _runningFlow = MutableStateFlow(false)
    val runningFlow: StateFlow<Boolean> = _runningFlow.asStateFlow()

    val listeningPort: Int
        get() = serverSocket?.localPort ?: -1

    /**
     * Synchronizes server running state with device role, paired parents, remote-control toggle, and Wi-Fi.
     */
    @Synchronized
    fun syncLifecycle(context: Context) {
        val app = context.applicationContext
        appContext = app
        DeviceRoleStore.init(app)
        PairedDevicesStore.init(app)

        val role = DeviceRoleStore.currentRole(app)
        val hasParents = PairedDevicesStore.pairedParents(app).isNotEmpty()
        val enabled = PairedDevicesStore.isRemoteControlEnabled(app)
        val pairingOpen = currentValidPairing() != null
        val onWifi = runCatching { TrustedNetworkGate.currentSnapshot(app).onWifi }.getOrDefault(false)

        val shouldRun = role == DeviceRole.STANDARD && onWifi && (pairingOpen || (hasParents && enabled))
        if (shouldRun) {
            ensureStarted(app)
        } else {
            stop()
        }
    }

    /**
     * Opens a single-use 3-minute pairing session. Fail-closed: requires an active Parent Admin Session.
     */
    @Synchronized
    fun startPairingSession(context: Context, bindAddress: InetAddress? = null): Result<ActivePairingSession> {
        val app = context.applicationContext
        if (!ParentSecurityManager.isAdminUnlocked(app)) {
            return Result.failure(SecurityException("Parent PIN unlock required to pair a Parent Device"))
        }
        appContext = app
        ensureStarted(app, bindAddress)
        val port = listeningPort
        if (port <= 0) {
            return Result.failure(IllegalStateException("Could not start local Wi-Fi server"))
        }
        val host = bindAddress?.hostAddress
            ?: resolveLocalLanIpv4(app)
            ?: "127.0.0.1"
        val code = RemoteCrypto.generateSixDigitCode()
        val childId = DeviceRoleStore.deviceId(app)
        val deviceName = DeviceRoleStore.deviceName(app)
        val invite = PairingInvite(
            childId = childId,
            host = host,
            port = port,
            code = code,
            deviceName = deviceName,
        )
        val session = ActivePairingSession(
            code = code,
            expiresAtMs = clockMs() + PAIRING_TTL_MS,
            host = host,
            port = port,
            inviteUri = PairingUri.encode(invite),
        )
        activePairing.set(session)
        _pairingSessionFlow.value = session
        reRegisterMdns(app, port)
        return Result.success(session)
    }

    @Synchronized
    fun cancelPairingSession(context: Context) {
        activePairing.set(null)
        _pairingSessionFlow.value = null
        val app = context.applicationContext
        val port = listeningPort
        if (port > 0 && PairedDevicesStore.pairedParents(app).isNotEmpty() && PairedDevicesStore.isRemoteControlEnabled(app)) {
            reRegisterMdns(app, port)
        } else {
            syncLifecycle(app)
        }
    }

    @Synchronized
    internal fun ensureStarted(context: Context, bindAddress: InetAddress? = null) {
        val app = context.applicationContext
        appContext = app
        if (serverSocket != null && serverSocket?.isClosed == false) return
        val ss = runCatching {
            if (bindAddress != null) ServerSocket(0, 16, bindAddress) else ServerSocket(0)
        }.getOrNull() ?: return
        serverSocket = ss
        _runningFlow.value = true
        val port = ss.localPort
        reRegisterMdns(app, port)
        acceptJob = scope.launch {
            while (isActive && !ss.isClosed) {
                val client = runCatching { ss.accept() }.getOrNull() ?: break
                launch { handleClient(app, client) }
            }
        }
    }

    @Synchronized
    fun stop() {
        unregisterMdns()
        acceptJob?.cancel()
        acceptJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        _runningFlow.value = false
    }

    private fun currentValidPairing(): ActivePairingSession? {
        val s = activePairing.get() ?: return null
        if (clockMs() > s.expiresAtMs) {
            activePairing.compareAndSet(s, null)
            _pairingSessionFlow.value = null
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

                val childId = DeviceRoleStore.deviceId(context)
                val deviceName = DeviceRoleStore.deviceName(context)
                val challengeNonce = RemoteCrypto.randomBytes(16)
                val challengeB64 = Base64.encodeToString(challengeNonce, Base64.NO_WRAP)

                val hello = ServerHello(
                    version = RemoteProtocol.PROTOCOL_VERSION,
                    childId = childId,
                    deviceName = deviceName,
                    challengeNonceB64 = challengeB64,
                    pairingOpen = currentValidPairing() != null,
                )
                RemoteProtocol.writeJsonFrame(output, hello)

                val msg = RemoteProtocol.readJsonFrame<ClientWireMessage>(input)
                when (msg) {
                    is ClientWireMessage.PairInit -> handlePairInit(context, output, childId, deviceName, challengeNonce, msg)
                    is ClientWireMessage.Encrypted -> handleEncryptedRequest(context, output, childId, challengeB64, msg)
                }
            }
        }
    }

    private fun handlePairInit(
        context: Context,
        output: java.io.OutputStream,
        childId: String,
        deviceName: String,
        challengeNonce: ByteArray,
        msg: ClientWireMessage.PairInit,
    ) {
        val session = currentValidPairing()
        if (session == null) {
            RemoteProtocol.writeJsonFrame<ServerWireMessage>(output, ServerWireMessage.Error("PAIRING_CLOSED", "Pairing session is not open"))
            return
        }
        val parentPubBytes = runCatching { Base64.decode(msg.parentEcdhPubB64, Base64.NO_WRAP) }.getOrNull()
        val proofBytes = runCatching { Base64.decode(msg.proofHmacB64, Base64.NO_WRAP) }.getOrNull()
        if (parentPubBytes == null || proofBytes == null || msg.parentId.isBlank()) {
            RemoteProtocol.writeJsonFrame<ServerWireMessage>(output, ServerWireMessage.Error("BAD_REQUEST", "Malformed pairing payload"))
            return
        }

        val pairingKey = RemoteCrypto.derivePairingKey(session.code, challengeNonce, childId)
        val expectedInput = "pair-init|${msg.parentId}|$childId|".toByteArray(Charsets.UTF_8) + parentPubBytes
        if (!RemoteCrypto.verifyHmacSha256(pairingKey, expectedInput, proofBytes)) {
            RemoteProtocol.writeJsonFrame<ServerWireMessage>(output, ServerWireMessage.Error("INVALID_CODE", "Pairing code verification failed"))
            return
        }

        // Single-use session: atomically consume so no second device can reuse it
        if (!activePairing.compareAndSet(session, null)) {
            RemoteProtocol.writeJsonFrame<ServerWireMessage>(output, ServerWireMessage.Error("PAIRING_USED", "Pairing session already consumed"))
            return
        }
        _pairingSessionFlow.value = null

        val childKp = RemoteCrypto.generateEcdhKeyPair()
        val childPubBytes = childKp.public.encoded
        val ecdhShared = RemoteCrypto.deriveEcdhSharedSecret(childKp.private, parentPubBytes)
        val permanentSecret = RemoteCrypto.derivePermanentSecret(ecdhShared, challengeNonce, msg.parentId, childId)

        PairedDevicesStore.upsertPairedParent(
            context = context,
            parentId = msg.parentId,
            parentName = msg.parentName,
            rawSecret = permanentSecret,
            pairedAtMs = clockMs(),
        )

        val respMacInput = "pair-complete|${msg.parentId}|$childId|".toByteArray(Charsets.UTF_8) + parentPubBytes + childPubBytes
        val respMac = RemoteCrypto.hmacSha256(pairingKey, respMacInput)
        RemoteProtocol.writeJsonFrame<ServerWireMessage>(
            output,
            ServerWireMessage.PairComplete(
                childId = childId,
                childName = deviceName,
                childEcdhPubB64 = Base64.encodeToString(childPubBytes, Base64.NO_WRAP),
                proofHmacB64 = Base64.encodeToString(respMac, Base64.NO_WRAP),
            ),
        )
        // Update mDNS TXT record to reflect pair=0
        val port = listeningPort
        if (port > 0) reRegisterMdns(context, port)
    }

    private fun handleEncryptedRequest(
        context: Context,
        output: java.io.OutputStream,
        childId: String,
        challengeB64: String,
        msg: ClientWireMessage.Encrypted,
    ) {
        if (!PairedDevicesStore.isRemoteControlEnabled(context)) {
            RemoteProtocol.writeJsonFrame<ServerWireMessage>(output, ServerWireMessage.Error("REMOTE_DISABLED", "Remote control is disabled"))
            return
        }
        val secret = PairedDevicesStore.secretForParent(context, msg.parentId)
        if (secret == null) {
            RemoteProtocol.writeJsonFrame<ServerWireMessage>(output, ServerWireMessage.Error("UNAUTHORIZED", "Parent device is not paired"))
            return
        }
        val iv = runCatching { Base64.decode(msg.ivB64, Base64.NO_WRAP) }.getOrNull()
        val ct = runCatching { Base64.decode(msg.ciphertextB64, Base64.NO_WRAP) }.getOrNull()
        if (iv == null || ct == null) {
            RemoteProtocol.writeJsonFrame<ServerWireMessage>(output, ServerWireMessage.Error("BAD_FRAME", "Invalid encrypted envelope"))
            return
        }
        val aad = RemoteProtocol.aadFor(msg.parentId, childId)
        val plaintext = runCatching { RemoteCrypto.decryptAesGcm(secret, iv, ct, aad) }.getOrNull()
        if (plaintext == null) {
            RemoteProtocol.writeJsonFrame<ServerWireMessage>(output, ServerWireMessage.Error("DECRYPT_FAILED", "Authentication failed"))
            return
        }
        val authReq = runCatching {
            RemoteProtocol.json.decodeFromString<AuthenticatedRequest>(plaintext.toString(Charsets.UTF_8))
        }.getOrNull()
        if (authReq == null || authReq.challengeNonceB64 != challengeB64) {
            RemoteProtocol.writeJsonFrame<ServerWireMessage>(output, ServerWireMessage.Error("BAD_CHALLENGE", "Challenge nonce mismatch"))
            return
        }

        val response: RemoteResponse = runCatching {
            when (val req = authReq.request) {
                RemoteRequest.GetStatus -> RemoteResponse.Status(delegate.getStatus(context))
                is RemoteRequest.PlaybackAction -> RemoteResponse.Status(delegate.executePlayback(context, req))
                RemoteRequest.GetPolicy -> RemoteResponse.Policy(delegate.getPolicy(context))
                is RemoteRequest.UpdatePolicy -> {
                    val (rev, snap) = delegate.updatePolicy(context, req.policy, req.ruleLabels)
                    RemoteResponse.PolicyUpdated(policyRevision = rev, snapshot = snap)
                }
                is RemoteRequest.RenameDevice -> {
                    val snap = delegate.renameDevice(context, req.displayName)
                    val port = listeningPort
                    if (port > 0) reRegisterMdns(context, port)
                    RemoteResponse.Status(snap)
                }
                is RemoteRequest.SearchCatalog -> RemoteResponse.CatalogSearch(delegate.searchCatalog(context, req.query))
                RemoteRequest.GetChildCatalog -> delegate.getChildCatalog(context)
                is RemoteRequest.RegisterParent -> {
                    val cleanParentId = req.parentId.trim()
                    require(cleanParentId.isNotEmpty()) { "Parent ID cannot be empty" }
                    val newSecret = Base64.decode(req.secretB64, Base64.NO_WRAP)
                    require(newSecret.size == 32) { "Expected 32-byte parent secret" }
                    val rec = PairedDevicesStore.upsertPairedParent(
                        context = context,
                        parentId = cleanParentId,
                        parentName = req.parentName,
                        rawSecret = newSecret,
                        pairedAtMs = clockMs(),
                    )
                    RemoteResponse.ParentRegistered(parentId = rec.parentId, parentName = rec.parentName)
                }
            }
        }.getOrElse { e ->
            RemoteResponse.Error("EXEC_ERROR", e.message ?: "Command failed")
        }

        val authResp = AuthenticatedResponse(challengeNonceB64 = challengeB64, response = response)
        val respPlain = RemoteProtocol.json.encodeToString(authResp).toByteArray(Charsets.UTF_8)
        val (respIv, respCt) = RemoteCrypto.encryptAesGcm(secret, respPlain, aad)
        RemoteProtocol.writeJsonFrame<ServerWireMessage>(
            output,
            ServerWireMessage.Encrypted(
                ivB64 = Base64.encodeToString(respIv, Base64.NO_WRAP),
                ciphertextB64 = Base64.encodeToString(respCt, Base64.NO_WRAP),
            ),
        )
    }

    @Synchronized
    private fun reRegisterMdns(context: Context, port: Int) {
        if (!enableMdns) return
        unregisterMdns()
        val mgr = runCatching { context.getSystemService(Context.NSD_SERVICE) as? NsdManager }.getOrNull() ?: return
        nsdManager = mgr
        val childId = DeviceRoleStore.deviceId(context)
        val info = NsdServiceInfo().apply {
            serviceName = "SoundNest-${childId.take(8)}"
            serviceType = RemoteProtocol.MDNS_SERVICE_TYPE
            this.port = port
            setAttribute("v", RemoteProtocol.PROTOCOL_VERSION.toString())
            setAttribute("id", childId)
            setAttribute("name", DeviceRoleStore.deviceName(context))
            setAttribute("pair", if (currentValidPairing() != null) "1" else "0")
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
        }
        regListener = listener
        runCatching { mgr.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }
    }

    @Synchronized
    private fun unregisterMdns() {
        val listener = regListener ?: return
        regListener = null
        runCatching { nsdManager?.unregisterService(listener) }
    }

    fun resolveLocalLanIpv4(context: Context): String? {
        runCatching {
            val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val lp = cm?.getLinkProperties(cm.activeNetwork)
            val fromLp = lp?.linkAddresses?.mapNotNull { it.address as? Inet4Address }
                ?.firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress && it.isSiteLocalAddress }
                ?.hostAddress
            if (!fromLp.isNullOrBlank()) return fromLp
        }
        return runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress && it.isSiteLocalAddress }
                ?.hostAddress
        }.getOrNull()
    }

    @Synchronized
    internal fun resetForTests() {
        stop()
        activePairing.set(null)
        _pairingSessionFlow.value = null
        delegate = DefaultChildControlDelegate
        clockMs = { System.currentTimeMillis() }
        enableMdns = true
    }
}
