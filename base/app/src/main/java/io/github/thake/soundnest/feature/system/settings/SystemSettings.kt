/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.system.settings

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import io.github.thake.soundnest.core.preferences.featurePrefs
import io.github.thake.soundnest.feature.player.settings.applyStreamingQuality
import io.github.thake.soundnest.feature.system.data.EnergyState
import io.github.thake.soundnest.host.android.SoundNestRuntime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Interceptor
import java.io.IOException

internal const val SYSTEM_PREFS_NAME = "system"

/**
 * System-level app flags (`keep_screen_on`) backed by `"feature_system"`.
 */
object SystemSettings {
    private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"

    fun isKeepScreenOn(context: Context): Boolean =
        context.featurePrefs(SYSTEM_PREFS_NAME).getBoolean(KEY_KEEP_SCREEN_ON, false)

    fun setKeepScreenOn(context: Context, keepOn: Boolean) {
        context.featurePrefs(SYSTEM_PREFS_NAME).edit().putBoolean(KEY_KEEP_SCREEN_ON, keepOn).apply()
    }
}

/**
 * Application-owned energy preferences (the app's saver is independent of Android's),
 * backed by `"feature_system"`.
 */
object EnergySettings {
    private val mutableState = MutableStateFlow(EnergyState())
    val state = mutableState.asStateFlow()
    var oledBlack by mutableStateOf(false)
        private set
    private var app: Application? = null
    private var preferences: SharedPreferences? = null
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refresh() }

    fun init(context: Context) {
        if (app != null) return
        val application = context.applicationContext as Application
        app = application
        preferences = application.featurePrefs(SYSTEM_PREFS_NAME)
        preferences?.registerOnSharedPreferenceChangeListener(preferenceListener)
        refresh()
    }

    fun setSaver(enabled: Boolean) {
        preferences?.edit { putBoolean("manual_saver", enabled) }
        refresh()
    }

    fun setBlackBackground(enabled: Boolean) {
        preferences?.edit { putBoolean("black_background", enabled) }
        refresh()
    }

    private fun refresh() {
        if (app == null) return
        val next = EnergyState(
            manualSaver = preferences?.getBoolean("manual_saver", false) == true,
            blackPreference = preferences?.getBoolean("black_background", false) == true,
        )
        mutableState.value = next
        oledBlack = next.policy.oledBlack
    }
}

/**
 * Data saver: on mobile data or another metered network (never on Wi-Fi) the app uses no data at all,
 * backed by `"feature_system"`.
 */
object DataSaver {
    private const val KEY = "data_saver"

    /** The user's switch. */
    var enabled by mutableStateOf(false)
        private set

    /** The current network costs data (mobile data, a hotspot). */
    var metered by mutableStateOf(false)
        private set
    private var context: Context? = null
    private val main by lazy { Handler(Looper.getMainLooper()) }

    @Volatile
    private var wasActive = false

    /** The saver is on and applies right now. */
    val active: Boolean get() = enabled && metered

    /** A connection refused by the data saver. */
    class Blocked : IOException("Data saver: no mobile data")

    /** Throws [Blocked] while the saver applies: call before opening any connection. */
    fun check() {
        if (active) throw Blocked()
    }

    /** For OkHttp clients. */
    val interceptor = Interceptor { chain -> check(); chain.proceed(chain.request()) }

    /** Shuts the hidden Spotify player down when the saver starts applying, rebuilds it when it stops. */
    private fun changed() {
        val now = active
        if (now == wasActive) return
        wasActive = now
        main.post {
            if (now) SoundNestRuntime.suspendPlayer()
            else SoundNestRuntime.resume()
        }
    }

    fun init(c: Context) {
        if (context != null) return
        val app = c.applicationContext
        context = app
        enabled = app.featurePrefs(SYSTEM_PREFS_NAME).getBoolean(KEY, false)
        val cm = app.getSystemService(ConnectivityManager::class.java) ?: return
        metered = cm.isActiveNetworkMetered
        wasActive = active
        runCatching {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    val was = metered
                    metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                    if (was != metered) applyStreamingQuality(app)
                    changed()
                }

                override fun onLost(network: Network) {
                    val was = metered
                    metered = cm.isActiveNetworkMetered
                    if (was != metered) applyStreamingQuality(app)
                    changed()
                }
            })
        }
    }

    fun set(on: Boolean) {
        enabled = on
        changed()
        context?.featurePrefs(SYSTEM_PREFS_NAME)?.edit()?.putBoolean(KEY, on)?.apply()
    }
}
