/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.core.playback

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import io.github.thake.soundnest.feature.accounts.settings.OnboardingSettings
import io.github.thake.soundnest.feature.parental.remote.settings.DeviceRole
import io.github.thake.soundnest.feature.parental.remote.settings.DeviceRoleStore
import io.github.thake.soundnest.feature.system.settings.DataSaver
import io.github.thake.soundnest.host.android.Logbook
import io.github.thake.soundnest.host.android.SoundNestRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Automatic self-healing for a wedged hidden Spotify Web Player session (for example after Android's
 * `APP_BACKGROUND` network policy tears down WebView sockets and Spotify's internal transport/Connect
 * state stops responding even after returning to the foreground).
 *
 * Guardrails:
 * - Never reloads while offline, while audio is actively playing, in Data Saver mode, or in Parent Controller mode.
 * - Enforces a strict 60s cooldown and a cap of 3 automatic reloads per 10-minute window (reset once healthy).
 */
object WebPlayerSelfHealer {
    internal const val MIN_BACKGROUND_FOR_PROBE_MS = 30_000L
    internal const val RELOAD_COOLDOWN_MS = 60_000L
    internal const val WINDOW_MS = 10 * 60_000L
    internal const val MAX_RELOADS_PER_WINDOW = 3
    internal const val AUDIO_INFO_PROBE_TIMEOUT_MS = 4_000L
    internal const val FOREGROUND_SETTLE_MS = 1_500L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile internal var clockMs: () -> Long = { SystemClock.elapsedRealtime() }
    @Volatile internal var isNetworkValidated: (Context) -> Boolean = ::defaultNetworkValidated
    @Volatile internal var isSignedIn: (Context) -> Boolean = { OnboardingSettings.isLoggedIn(it) }
    @Volatile internal var isDataSaverActive: () -> Boolean = { DataSaver.active }
    @Volatile internal var isParentController: (Context) -> Boolean = {
        DeviceRoleStore.currentRole(it) == DeviceRole.PARENT_CONTROLLER
    }
    @Volatile internal var isPlayingNow: () -> Boolean = {
        runCatching { SongPlayer.isPlaying() || LocalPlayer.active.value }.getOrDefault(false)
    }
    @Volatile internal var isApiReady: () -> Boolean = {
        runCatching { SoundNestRuntime.isStarted && SoundNestRuntime.api.ready.value }.getOrDefault(false)
    }
    @Volatile internal var hasPlayerState: () -> Boolean = {
        runCatching { SoundNestRuntime.api.player.state.value != null }.getOrDefault(false)
    }
    @Volatile internal var listDevicesCount: suspend () -> Int = {
        runCatching {
            withTimeoutOrNull(AUDIO_INFO_PROBE_TIMEOUT_MS) {
                SoundNestRuntime.api.devices.refresh()
                SoundNestRuntime.api.devices.devices.value.size
            } ?: -1
        }.getOrDefault(-1)
    }
    @Volatile internal var probeAudioInfoOk: suspend () -> Boolean = {
        runCatching {
            withTimeoutOrNull(AUDIO_INFO_PROBE_TIMEOUT_MS) {
                SoundNestRuntime.api.player.volume()
                true
            } ?: false
        }.getOrDefault(false)
    }
    @Volatile internal var triggerReload: () -> Unit = {
        runCatching { SoundNestRuntime.host.reload() }
    }
    @Volatile internal var triggerRetryFailedLoad: () -> Boolean = {
        runCatching { SoundNestRuntime.isStarted && SoundNestRuntime.host.retryIfLoadFailed() }.getOrDefault(false)
    }

    @Volatile private var backgroundedAtMs: Long = 0L
    @Volatile private var lastReloadAtMs: Long = 0L
    private val reloadTimestamps = ArrayDeque<Long>()
    private var foregroundProbeJob: Job? = null

    fun onBackground() {
        backgroundedAtMs = clockMs()
        foregroundProbeJob?.cancel()
        foregroundProbeJob = null
    }

    fun onForeground(context: Context) {
        val app = context.applicationContext
        // If the WebView failed to load while Doze/background blocked DNS (backing off up to 600 s),
        // retry immediately when the user returns to the foreground with a validated network.
        if (isSignedIn(app) && !isParentController(app) && !isDataSaverActive() && !isApiReady() && isNetworkValidated(app)) {
            triggerRetryFailedLoad()
        }
        val bgAt = backgroundedAtMs
        backgroundedAtMs = 0L
        if (bgAt <= 0L) return
        val bgDuration = clockMs() - bgAt
        if (bgDuration < MIN_BACKGROUND_FOR_PROBE_MS) return

        foregroundProbeJob?.cancel()
        foregroundProbeJob = scope.launch {
            delay(FOREGROUND_SETTLE_MS)
            checkAndHealIfWedged(app, reason = "foreground_resume")
        }
    }

    /** Clears recent reload budget once normal playback or device discovery succeeds. */
    @Synchronized
    fun recordHealthy() {
        reloadTimestamps.clear()
    }

    /**
     * Reactive self-heal called when a transport command times out or a play command is ignored
     * twice by the page. Returns `true` if a Web Player reload was triggered.
     */
    suspend fun healIfWedged(context: Context, reason: String): Boolean =
        checkAndHealIfWedged(context.applicationContext, reason)

    internal suspend fun checkAndHealIfWedged(context: Context, reason: String): Boolean {
        if (!isSignedIn(context)) return false
        if (isParentController(context)) return false
        if (isDataSaverActive()) return false
        if (isPlayingNow()) return false
        if (!isApiReady()) {
            return isNetworkValidated(context) && triggerRetryFailedLoad()
        }
        if (!isNetworkValidated(context)) return false
        if (hasPlayerState()) {
            recordHealthy()
            return false
        }

        val devicesCount = listDevicesCount()
        if (devicesCount > 0) {
            recordHealthy()
            return false
        }

        // Confirm wedged: devices=[] (or timed out) AND player.state==null AND audioInfo hangs/fails
        val audioOk = probeAudioInfoOk()
        if (audioOk && devicesCount == 0 && reason == "foreground_resume") {
            // AudioInfo still responds quickly -> page is alive, don't reload speculatively on resume.
            return false
        }

        return requestReloadIfAllowed(reason)
    }

    @Synchronized
    internal fun requestReloadIfAllowed(reason: String): Boolean {
        val now = clockMs()
        if (lastReloadAtMs > 0L && now - lastReloadAtMs < RELOAD_COOLDOWN_MS) {
            return false
        }
        while (reloadTimestamps.isNotEmpty() && now - reloadTimestamps.first() > WINDOW_MS) {
            reloadTimestamps.removeFirst()
        }
        if (reloadTimestamps.size >= MAX_RELOADS_PER_WINDOW) {
            Logbook.log("self-heal: reload budget exhausted ($MAX_RELOADS_PER_WINDOW in ${WINDOW_MS / 60_000}m), skipping ($reason)")
            return false
        }
        lastReloadAtMs = now
        reloadTimestamps.addLast(now)
        Logbook.log("self-heal: wedged Web Player detected ($reason), reloading")
        triggerReload()
        return true
    }

    private fun defaultNetworkValidated(context: Context): Boolean = runCatching {
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }.getOrDefault(false)

    @Synchronized
    internal fun resetForTests() {
        foregroundProbeJob?.cancel()
        foregroundProbeJob = null
        backgroundedAtMs = 0L
        lastReloadAtMs = 0L
        reloadTimestamps.clear()
        clockMs = { SystemClock.elapsedRealtime() }
        isNetworkValidated = ::defaultNetworkValidated
        isSignedIn = { OnboardingSettings.isLoggedIn(it) }
        isDataSaverActive = { DataSaver.active }
        isParentController = { DeviceRoleStore.currentRole(it) == DeviceRole.PARENT_CONTROLLER }
        isPlayingNow = { runCatching { SongPlayer.isPlaying() || LocalPlayer.active.value }.getOrDefault(false) }
        isApiReady = { runCatching { SoundNestRuntime.isStarted && SoundNestRuntime.api.ready.value }.getOrDefault(false) }
        hasPlayerState = { runCatching { SoundNestRuntime.api.player.state.value != null }.getOrDefault(false) }
        listDevicesCount = {
            runCatching {
                withTimeoutOrNull(AUDIO_INFO_PROBE_TIMEOUT_MS) {
                    SoundNestRuntime.api.devices.refresh()
                    SoundNestRuntime.api.devices.devices.value.size
                } ?: -1
            }.getOrDefault(-1)
        }
        probeAudioInfoOk = {
            runCatching {
                withTimeoutOrNull(AUDIO_INFO_PROBE_TIMEOUT_MS) {
                    SoundNestRuntime.api.player.volume()
                    true
                } ?: false
            }.getOrDefault(false)
        }
        triggerReload = { runCatching { SoundNestRuntime.host.reload() } }
        triggerRetryFailedLoad = {
            runCatching { SoundNestRuntime.isStarted && SoundNestRuntime.host.retryIfLoadFailed() }.getOrDefault(false)
        }
    }
}
