/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.parental.remote.settings

import android.content.Context
import android.util.Base64
import io.github.thake.soundnest.core.preferences.featurePrefs
import io.github.thake.soundnest.feature.parental.remote.crypto.RemoteCrypto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class PairedParentRecord(
    val parentId: String,
    val parentName: String,
    val secretBlobB64: String,
    val wrapped: Boolean,
    val pairedAtMs: Long,
)

@Serializable
data class PairedChildRecord(
    val childId: String,
    val displayName: String,
    val lastHost: String,
    val lastPort: Int,
    val secretBlobB64: String,
    val wrapped: Boolean,
    val pairedAtMs: Long,
    /**
     * Optional delegated parent ID used temporarily when this child was offline during a
     * Parent-to-Parent transfer. Automatically cleared to null once upgraded to this device's own key.
     */
    val authParentId: String? = null,
)

/**
 * Persists paired Parent Devices (on a Child Device) and paired Child Devices (on a Parent Device).
 * Per-pair 256-bit shared secrets are envelope-wrapped with Android Keystore AES-GCM.
 *
 * Backed by `"feature_parent_remote_pairs"`.
 */
object PairedDevicesStore {
    internal const val PREF_NAME = "parent_remote_pairs"
    private const val KEY_PARENTS_JSON = "paired_parents_json"
    private const val KEY_CHILDREN_JSON = "paired_children_json"
    private const val KEY_REMOTE_ENABLED = "child_remote_enabled"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _pairedParentsFlow = MutableStateFlow<List<PairedParentRecord>>(emptyList())
    val pairedParentsFlow: StateFlow<List<PairedParentRecord>> = _pairedParentsFlow.asStateFlow()

    private val _pairedChildrenFlow = MutableStateFlow<List<PairedChildRecord>>(emptyList())
    val pairedChildrenFlow: StateFlow<List<PairedChildRecord>> = _pairedChildrenFlow.asStateFlow()

    private val _remoteControlEnabledFlow = MutableStateFlow(true)
    val remoteControlEnabledFlow: StateFlow<Boolean> = _remoteControlEnabledFlow.asStateFlow()

    @Volatile
    private var initialized = false

    private fun prefs(context: Context) =
        context.applicationContext.featurePrefs(PREF_NAME)

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        val p = prefs(context)
        _pairedParentsFlow.value = p.getString(KEY_PARENTS_JSON, null)?.let { raw ->
            runCatching { json.decodeFromString<List<PairedParentRecord>>(raw) }.getOrNull()
        }.orEmpty()
        _pairedChildrenFlow.value = p.getString(KEY_CHILDREN_JSON, null)?.let { raw ->
            runCatching { json.decodeFromString<List<PairedChildRecord>>(raw) }.getOrNull()
        }.orEmpty()
        _remoteControlEnabledFlow.value = p.getBoolean(KEY_REMOTE_ENABLED, true)
        initialized = true
    }

    fun isRemoteControlEnabled(context: Context): Boolean {
        init(context)
        return _remoteControlEnabledFlow.value
    }

    @Synchronized
    fun setRemoteControlEnabled(context: Context, enabled: Boolean) {
        init(context)
        prefs(context).edit().putBoolean(KEY_REMOTE_ENABLED, enabled).apply()
        _remoteControlEnabledFlow.value = enabled
    }

    // ── Child-side: paired Parent Devices ──

    fun pairedParents(context: Context): List<PairedParentRecord> {
        init(context)
        return _pairedParentsFlow.value
    }

    @Synchronized
    fun upsertPairedParent(
        context: Context,
        parentId: String,
        parentName: String,
        rawSecret: ByteArray,
        pairedAtMs: Long = System.currentTimeMillis(),
    ): PairedParentRecord {
        init(context)
        val wrapped = RemoteCrypto.wrapWithKeystoreOrNull(rawSecret)
        val blob = wrapped ?: rawSecret
        val record = PairedParentRecord(
            parentId = parentId,
            parentName = parentName.trim().ifEmpty { "Parent Device" }.take(40),
            secretBlobB64 = Base64.encodeToString(blob, Base64.NO_WRAP),
            wrapped = wrapped != null,
            pairedAtMs = pairedAtMs,
        )
        val updated = _pairedParentsFlow.value.filterNot { it.parentId == parentId } + record
        prefs(context).edit()
            .putString(KEY_PARENTS_JSON, json.encodeToString(updated))
            .putBoolean(KEY_REMOTE_ENABLED, true)
            .apply()
        _pairedParentsFlow.value = updated
        _remoteControlEnabledFlow.value = true
        return record
    }

    @Synchronized
    fun revokePairedParent(context: Context, parentId: String) {
        init(context)
        val updated = _pairedParentsFlow.value.filterNot { it.parentId == parentId }
        prefs(context).edit().putString(KEY_PARENTS_JSON, json.encodeToString(updated)).apply()
        _pairedParentsFlow.value = updated
    }

    fun secretForParent(context: Context, parentId: String): ByteArray? {
        val record = pairedParents(context).firstOrNull { it.parentId == parentId } ?: return null
        return unwrapSecret(record.secretBlobB64, record.wrapped)
    }

    // ── Parent-side: paired Child Devices ──

    fun pairedChildren(context: Context): List<PairedChildRecord> {
        init(context)
        return _pairedChildrenFlow.value
    }

    @Synchronized
    fun upsertPairedChild(
        context: Context,
        childId: String,
        displayName: String,
        host: String,
        port: Int,
        rawSecret: ByteArray,
        pairedAtMs: Long = System.currentTimeMillis(),
        authParentId: String? = null,
    ): PairedChildRecord {
        init(context)
        val wrapped = RemoteCrypto.wrapWithKeystoreOrNull(rawSecret)
        val blob = wrapped ?: rawSecret
        val record = PairedChildRecord(
            childId = childId,
            displayName = displayName.trim().ifEmpty { "Child Device" }.take(40),
            lastHost = host.trim(),
            lastPort = port,
            secretBlobB64 = Base64.encodeToString(blob, Base64.NO_WRAP),
            wrapped = wrapped != null,
            pairedAtMs = pairedAtMs,
            authParentId = authParentId?.trim()?.takeIf { it.isNotEmpty() },
        )
        val updated = _pairedChildrenFlow.value.filterNot { it.childId == childId } + record
        prefs(context).edit().putString(KEY_CHILDREN_JSON, json.encodeToString(updated)).apply()
        _pairedChildrenFlow.value = updated
        return record
    }

    @Synchronized
    fun updateChildEndpoint(context: Context, childId: String, host: String, port: Int, displayName: String? = null) {
        init(context)
        val existing = _pairedChildrenFlow.value
        var changed = false
        val updated = existing.map { rec ->
            if (rec.childId == childId) {
                val nextName = displayName?.trim()?.takeIf { it.isNotEmpty() }?.take(40) ?: rec.displayName
                if (rec.lastHost != host || rec.lastPort != port || rec.displayName != nextName) {
                    changed = true
                    rec.copy(lastHost = host, lastPort = port, displayName = nextName)
                } else {
                    rec
                }
            } else {
                rec
            }
        }
        if (changed) {
            prefs(context).edit().putString(KEY_CHILDREN_JSON, json.encodeToString(updated)).apply()
            _pairedChildrenFlow.value = updated
        }
    }

    @Synchronized
    fun renameChild(context: Context, childId: String, newName: String) {
        val cleaned = newName.trim().take(40)
        if (cleaned.isEmpty()) return
        init(context)
        val updated = _pairedChildrenFlow.value.map {
            if (it.childId == childId) it.copy(displayName = cleaned) else it
        }
        prefs(context).edit().putString(KEY_CHILDREN_JSON, json.encodeToString(updated)).apply()
        _pairedChildrenFlow.value = updated
    }

    @Synchronized
    fun removePairedChild(context: Context, childId: String) {
        init(context)
        val updated = _pairedChildrenFlow.value.filterNot { it.childId == childId }
        prefs(context).edit().putString(KEY_CHILDREN_JSON, json.encodeToString(updated)).apply()
        _pairedChildrenFlow.value = updated
    }

    fun secretForChild(context: Context, childId: String): ByteArray? {
        val record = pairedChildren(context).firstOrNull { it.childId == childId } ?: return null
        return unwrapSecret(record.secretBlobB64, record.wrapped)
    }

    private fun unwrapSecret(blobB64: String, wrapped: Boolean): ByteArray? {
        val bytes = runCatching { Base64.decode(blobB64, Base64.NO_WRAP) }.getOrNull() ?: return null
        return if (wrapped) RemoteCrypto.unwrapFromKeystore(bytes) else bytes
    }

    @Synchronized
    internal fun resetForTests(context: Context) {
        prefs(context).edit().clear().commit()
        initialized = false
        _pairedParentsFlow.value = emptyList()
        _pairedChildrenFlow.value = emptyList()
        _remoteControlEnabledFlow.value = true
    }
}
