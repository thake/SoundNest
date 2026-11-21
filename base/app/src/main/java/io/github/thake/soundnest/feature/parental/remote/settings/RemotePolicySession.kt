/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.parental.remote.settings

import android.content.Context
import io.github.thake.soundnest.feature.parental.remote.net.ParentRemoteClient
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore
import io.github.thake.soundnest.feature.parental.settings.RuleLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed class RemoteSyncState {
    data object Idle : RemoteSyncState()
    data object Loading : RemoteSyncState()
    data object Syncing : RemoteSyncState()
    data object Synced : RemoteSyncState()
    data class Error(val message: String) : RemoteSyncState()
}

/**
 * Active remote editing session overlaying [ParentalPolicyStore] and [RuleLabelStore] when a
 * Parent Device is managing a paired Child Device.
 *
 * All standard children's-app parental screens (`ParentSettingsScreen`, `ManageContentRulesSheet`,
 * `ParentScheduleSettingsPage`, `ParentQuickBlockUi`, etc.) automatically read and mutate the
 * attached child's policy in memory (zero writes to the parent device's own local prefs) and
 * debounced-push every change over AES-256-GCM to the child device.
 */
object RemotePolicySession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pushMutex = Mutex()

    @Volatile
    internal var pushDebounceMs: Long = 250L

    private val _activeChildFlow = MutableStateFlow<PairedChildRecord?>(null)
    val activeChildFlow: StateFlow<PairedChildRecord?> = _activeChildFlow.asStateFlow()

    private val _syncStateFlow = MutableStateFlow<RemoteSyncState>(RemoteSyncState.Idle)
    val syncStateFlow: StateFlow<RemoteSyncState> = _syncStateFlow.asStateFlow()

    @Volatile private var remotePolicy: ParentalPolicy? = null
    @Volatile private var remoteRevision: Long? = null
    private val remoteLabels = LinkedHashMap<String, RuleLabel>()
    @Volatile private var pendingPushJob: Job? = null
    @Volatile private var dirty: Boolean = false
    @Volatile private var attachedContext: Context? = null

    val isActive: Boolean
        get() = _activeChildFlow.value != null && remotePolicy != null

    val activeChild: PairedChildRecord?
        get() = _activeChildFlow.value

    fun currentPolicyOrNull(): ParentalPolicy? = if (_activeChildFlow.value != null) remotePolicy else null

    @Synchronized
    fun currentLabelsOrNull(): Map<String, RuleLabel>? =
        if (_activeChildFlow.value != null) remoteLabels.toMap() else null

    @Synchronized
    fun recordLabel(key: String, label: RuleLabel) {
        if (_activeChildFlow.value == null) return
        remoteLabels[key] = label
        dirty = true
        schedulePush()
    }

    @Synchronized
    fun mergeLabels(entries: Map<String, RuleLabel>) {
        if (_activeChildFlow.value == null || entries.isEmpty()) return
        remoteLabels.putAll(entries)
        dirty = true
        schedulePush()
    }

    @Synchronized
    fun replaceLabels(entries: Map<String, RuleLabel>) {
        if (_activeChildFlow.value == null) return
        remoteLabels.clear()
        remoteLabels.putAll(entries)
        dirty = true
        schedulePush()
    }

    @Synchronized
    internal fun onPolicyMutated(newPolicy: ParentalPolicy) {
        if (_activeChildFlow.value == null) return
        remotePolicy = newPolicy
        dirty = true
        schedulePush()
    }

    /**
     * Fetches [child]'s current policy & rule labels over encrypted Wi-Fi and activates the
     * remote overlay on [ParentalPolicyStore] and [RuleLabelStore].
     */
    suspend fun attach(context: Context, child: PairedChildRecord): Result<Unit> {
        val app = context.applicationContext
        attachedContext = app
        _activeChildFlow.value = child
        _syncStateFlow.value = RemoteSyncState.Loading

        return ParentRemoteClient.fetchPolicy(app, child).map { bundle ->
            synchronized(this) {
                remotePolicy = bundle.policy
                remoteRevision = bundle.policyRevision
                remoteLabels.clear()
                remoteLabels.putAll(bundle.ruleLabels)
                dirty = false
            }
            ParentalPolicyStore.enterRemoteSession(bundle.policy)
            _syncStateFlow.value = RemoteSyncState.Synced
        }.onFailure { e ->
            _syncStateFlow.value = RemoteSyncState.Error(e.message ?: "Offline")
        }
    }

    /** Immediately pushes any pending remote edits to the attached child device. */
    suspend fun flushPendingPush(context: Context? = null): Result<Unit> {
        pendingPushJob?.cancel()
        pendingPushJob = null
        return pushNow(context)
    }

    private suspend fun pushNow(context: Context?): Result<Unit> = pushMutex.withLock {
        val app = (context ?: attachedContext)?.applicationContext ?: return Result.success(Unit)
        val child = _activeChildFlow.value ?: return Result.success(Unit)
        val (policy, labels, rev) = synchronized(this) {
            val p = remotePolicy ?: return Result.success(Unit)
            dirty = false
            Triple(p, remoteLabels.toMap(), remoteRevision)
        }
        _syncStateFlow.value = RemoteSyncState.Syncing
        ParentRemoteClient.pushPolicy(app, child, policy, labels, rev).map { updated ->
            synchronized(this) {
                remoteRevision = updated.policyRevision
            }
            if (!dirty) {
                _syncStateFlow.value = RemoteSyncState.Synced
            }
        }.onFailure { e ->
            synchronized(this) { dirty = true }
            _syncStateFlow.value = RemoteSyncState.Error(e.message ?: "Sync failed")
        }
    }

    fun retrySync(context: Context) {
        val app = context.applicationContext
        val child = _activeChildFlow.value ?: return
        scope.launch {
            if (remotePolicy == null) attach(app, child)
            else flushPendingPush(app)
        }
    }

    /** Pushes the current remote draft policy & rule labels to multiple selected paired children. */
    suspend fun pushToOtherChildren(context: Context, targets: List<PairedChildRecord>): Int {
        val app = context.applicationContext
        val (policy, labels) = synchronized(this) {
            val p = remotePolicy ?: ParentalPolicyStore.current(app)
            p to remoteLabels.toMap()
        }
        if (targets.isEmpty()) return 0
        val results = coroutineScope {
            targets.map { t ->
                async { ParentRemoteClient.pushPolicy(app, t, policy, labels) }
            }.awaitAll()
        }
        return results.count { it.isSuccess }
    }

    /** Detaches the remote session and restores [ParentalPolicyStore] to the local device policy. */
    fun detach(context: Context) {
        val app = context.applicationContext
        val pendingChild = _activeChildFlow.value
        val needsFlush = dirty && pendingChild != null && remotePolicy != null
        val snapshotPolicy = remotePolicy
        val snapshotLabels = synchronized(this) { remoteLabels.toMap() }
        val snapshotRev = remoteRevision

        pendingPushJob?.cancel()
        pendingPushJob = null
        synchronized(this) {
            remotePolicy = null
            remoteRevision = null
            remoteLabels.clear()
            dirty = false
        }
        _activeChildFlow.value = null
        _syncStateFlow.value = RemoteSyncState.Idle
        ParentalPolicyStore.exitRemoteSession(app)

        if (needsFlush && pendingChild != null && snapshotPolicy != null) {
            scope.launch {
                runCatching { ParentRemoteClient.pushPolicy(app, pendingChild, snapshotPolicy, snapshotLabels, snapshotRev) }
            }
        }
    }

    private fun schedulePush() {
        val app = attachedContext ?: return
        pendingPushJob?.cancel()
        _syncStateFlow.value = RemoteSyncState.Syncing
        pendingPushJob = scope.launch {
            if (pushDebounceMs > 0L) delay(pushDebounceMs)
            pushNow(app)
        }
    }

    @Synchronized
    internal fun resetForTests(context: Context) {
        pendingPushJob?.cancel()
        pendingPushJob = null
        remotePolicy = null
        remoteRevision = null
        remoteLabels.clear()
        dirty = false
        attachedContext = null
        pushDebounceMs = 250L
        _activeChildFlow.value = null
        _syncStateFlow.value = RemoteSyncState.Idle
        ParentalPolicyStore.exitRemoteSession(context.applicationContext)
    }
}
