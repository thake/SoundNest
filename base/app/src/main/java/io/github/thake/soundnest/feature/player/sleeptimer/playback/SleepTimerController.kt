/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.player.sleeptimer.playback

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import io.github.thake.soundnest.core.playback.LocalPlayer
import io.github.thake.soundnest.core.playback.SongPlayer
import io.github.thake.soundnest.feature.speakers.cast.CastPlayback
import io.github.thake.soundnest.feature.speakers.settings.RemoteSleepPauseCoordinator
import io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayer
import io.github.thake.soundnest.host.android.SoundNestRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Ephemeral sleep timer controller with volume fade-out (for timed sleep) and end-of-track watch,
 * supporting Spotify Web Player, Connect speakers, Google Cast, LocalPlayer, and AudiblePlayer.
 */
object SleepTimerController {
    /** A timed sleep timer lowers the volume over its last seconds. */
    private const val SLEEP_FADE_MS = 15_000L
    /** The end-of-track timer pauses this close to the end (before the next item starts). */
    private const val END_OF_TRACK_MARGIN_MS = 600L

    private val main by lazy { Handler(Looper.getMainLooper()) }
    private val api get() = SoundNestRuntime.api
    private val scope get() = SoundNestRuntime.scope

    private val _sleepEndsAt = mutableStateOf<Long?>(null) // elapsedRealtime, or -1 = end of track
    val sleepEndsAt: State<Long?> get() = _sleepEndsAt

    val sleepTimerEndsAtMs: Long?
        get() = _sleepEndsAt.value?.takeIf { it > 0L }

    val sleepTimerEndOfTrack: Boolean
        get() = _sleepEndsAt.value == -1L

    private var sleepFade: Job? = null
    private var sleepTrackId: String? = null

    private val sleepRunnable = Runnable {
        _sleepEndsAt.value = null
        if (sleepFade?.isActive != true) pauseForSleepTimer()
    }

    private val sleepFadeRunnable = Runnable { startSleepFade() }

    /** "End of track": pauses just before the item ends, or as soon as another one plays. */
    private val endOfTrackWatch = object : Runnable {
        override fun run() {
            val t = sleepTrackId ?: return
            val uri = SongPlayer.nowPlaying.value.uri.ifBlank { SongPlayer.currentTrackId }
            val duration = SongPlayer.getDuration()
            val moved = uri.isNotBlank() && uri != t
            val atEnd = duration > 0 && SongPlayer.isPlaying() && SongPlayer.getCurrentPosition() >= duration - END_OF_TRACK_MARGIN_MS
            if (moved || atEnd) {
                sleepTrackId = null
                _sleepEndsAt.value = null
                pauseForSleepTimer()
                return
            }
            main.postDelayed(this, 250)
        }
    }

    /** Clears any active sleep timer and cancels any running fade (e.g. on account logout/switch). */
    fun clear() {
        runCatching {
            main.removeCallbacks(sleepRunnable)
            main.removeCallbacks(sleepFadeRunnable)
            main.removeCallbacks(endOfTrackWatch)
        }
        sleepFade?.cancel()
        sleepFade = null
        sleepTrackId = null
        _sleepEndsAt.value = null
    }

    /** [minutes] null = off, 0 = at the end of the current track. */
    fun setSleepTimer(minutes: Int?) {
        runCatching {
            main.removeCallbacks(sleepRunnable)
            main.removeCallbacks(sleepFadeRunnable)
            main.removeCallbacks(endOfTrackWatch)
        }
        sleepFade?.cancel()
        sleepFade = null
        sleepTrackId = null
        when {
            minutes == null -> _sleepEndsAt.value = null
            minutes == 0 -> {
                sleepTrackId = SongPlayer.nowPlaying.value.uri.ifBlank { SongPlayer.currentTrackId }
                _sleepEndsAt.value = -1L
                runCatching { main.removeCallbacks(endOfTrackWatch); main.post(endOfTrackWatch) }
            }
            else -> {
                _sleepEndsAt.value = SystemClock.elapsedRealtime() + minutes * 60_000L
                runCatching {
                    main.postDelayed(sleepRunnable, minutes * 60_000L)
                    main.postDelayed(sleepFadeRunnable, (minutes * 60_000L - SLEEP_FADE_MS).coerceAtLeast(0L))
                }
            }
        }
    }

    private fun pauseForSleepTimer() {
        val remoteId = SongPlayer.activeDevice.value?.id
        SongPlayer.pauseForSleep(send = true)
        if (!remoteId.isNullOrBlank()) {
            scope.launch { runCatching { api.devices.pause(remoteId) } }
        }
    }

    private fun startSleepFade() {
        if (!SongPlayer.isPlaying()) return
        sleepFade?.cancel()
        sleepFade = scope.launch {
            if (LocalPlayer.active.value || AudiblePlayer.active.value) {
                fadeLocalOut(SLEEP_FADE_MS)
                return@launch
            }
            val remoteId = SongPlayer.activeDevice.value?.id
            val ok = runCatching { api.player.pauseSlowly(SLEEP_FADE_MS) }
                .onFailure { if (it is CancellationException) throw it }
                .isSuccess
            if (SongPlayer.isPlaying()) {
                _sleepEndsAt.value = null
                var sentFallback = false
                RemoteSleepPauseCoordinator.finishRemoteSleepFade(
                    remoteDeviceId = remoteId,
                    pauseSlowlySucceeded = ok,
                    remoteStillPlaying = { api.player.state.value?.isPaused == false },
                    sendPlayerPause = { sentFallback = true; SongPlayer.pauseForSleep(send = true) },
                    sendDirectDevicePause = { id -> api.devices.pause(id) },
                )
                if (!sentFallback) SongPlayer.pauseForSleep(send = false)
            }
        }
    }

    private suspend fun fadeLocalOut(ms: Long) {
        val cast = CastPlayback.active
        val base = if (cast) CastPlayback.volume.floatValue else 1f
        fun set(gain: Float) = when {
            cast -> CastPlayback.setVolume(base * gain)
            AudiblePlayer.active.value -> AudiblePlayer.setGain(gain)
            else -> LocalPlayer.setGain(gain)
        }
        val startedAt = SystemClock.elapsedRealtime()
        try {
            while (true) {
                if (!SongPlayer.isPlaying()) return
                val t = ((SystemClock.elapsedRealtime() - startedAt).toFloat() / ms).coerceIn(0f, 1f)
                if (t >= 1f) break
                set(Math.pow(10.0, -2.0 * t).toFloat())
                delay(if (cast) 1_000L else 250L)
            }
            _sleepEndsAt.value = null
            SongPlayer.pauseForSleep(send = true)
            delay(300)
        } finally {
            withContext(NonCancellable) { set(1f) }
        }
    }
}
