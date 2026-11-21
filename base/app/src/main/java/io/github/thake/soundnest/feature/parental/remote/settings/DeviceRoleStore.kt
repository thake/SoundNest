/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.parental.remote.settings

import android.content.Context
import android.os.Build
import io.github.thake.soundnest.core.preferences.featurePrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

enum class DeviceRole {
    /** Standard SoundNest player (child/listener device, optionally paired with a parent device). */
    STANDARD,
    /** Explicit Parent Controller device: manages paired child devices on Wi-Fi without Spotify login. */
    PARENT_CONTROLLER,
}

/**
 * Persists this installation's explicit role ([DeviceRole]), stable random [deviceId],
 * and parent-editable display [deviceName] (advertised over Wi-Fi mDNS when paired).
 *
 * Backed by `"feature_parent_remote_role"`.
 */
object DeviceRoleStore {
    internal const val PREF_NAME = "parent_remote_role"
    private const val KEY_ROLE = "device_role"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_DEVICE_NAME = "device_name"

    private val _roleFlow = MutableStateFlow(DeviceRole.STANDARD)
    val roleFlow: StateFlow<DeviceRole> = _roleFlow.asStateFlow()

    private val _deviceNameFlow = MutableStateFlow(defaultDeviceName())
    val deviceNameFlow: StateFlow<String> = _deviceNameFlow.asStateFlow()

    @Volatile
    private var initialized = false

    val isParentController: Boolean
        get() = _roleFlow.value == DeviceRole.PARENT_CONTROLLER

    private fun prefs(context: Context) =
        context.applicationContext.featurePrefs(PREF_NAME)

    private fun defaultDeviceName(): String {
        val model = Build.MODEL?.trim().orEmpty()
        return model.ifEmpty { "SoundNest Device" }.take(40)
    }

    @Synchronized
    fun init(context: Context): DeviceRole {
        if (!initialized) {
            val p = prefs(context)
            val savedRole = p.getString(KEY_ROLE, null)?.let { raw ->
                runCatching { DeviceRole.valueOf(raw) }.getOrNull()
            } ?: DeviceRole.STANDARD
            _roleFlow.value = savedRole
            val savedName = p.getString(KEY_DEVICE_NAME, null)?.trim().orEmpty()
            _deviceNameFlow.value = savedName.ifEmpty { defaultDeviceName() }
            if (p.getString(KEY_DEVICE_ID, null).isNullOrBlank()) {
                p.edit().putString(KEY_DEVICE_ID, UUID.randomUUID().toString()).apply()
            }
            initialized = true
        }
        return _roleFlow.value
    }

    fun currentRole(context: Context): DeviceRole = init(context)

    @Synchronized
    fun setRole(context: Context, role: DeviceRole) {
        init(context)
        prefs(context).edit().putString(KEY_ROLE, role.name).apply()
        _roleFlow.value = role
    }

    @Synchronized
    fun deviceId(context: Context): String {
        init(context)
        val p = prefs(context)
        return p.getString(KEY_DEVICE_ID, null)?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString().also { id ->
                p.edit().putString(KEY_DEVICE_ID, id).apply()
            }
    }

    fun deviceName(context: Context): String {
        init(context)
        return _deviceNameFlow.value
    }

    @Synchronized
    fun setDeviceName(context: Context, name: String): String {
        init(context)
        val cleaned = name.trim().replace(Regex("\\s+"), " ").take(40).ifEmpty { defaultDeviceName() }
        prefs(context).edit().putString(KEY_DEVICE_NAME, cleaned).apply()
        _deviceNameFlow.value = cleaned
        return cleaned
    }

    @Synchronized
    internal fun resetForTests(context: Context) {
        prefs(context).edit().clear().commit()
        initialized = false
        _roleFlow.value = DeviceRole.STANDARD
        _deviceNameFlow.value = defaultDeviceName()
    }
}
