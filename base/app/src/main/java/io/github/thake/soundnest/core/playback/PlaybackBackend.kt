/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.core.playback

import io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayer

/**
 * Uniform contract for overlay playback backends (such as [AudiblePlayer] and [LocalPlayer])
 * that take precedence over Spotify's Web Player inside [SongPlayer].
 *
 * Adding a new audio source only requires implementing [PlaybackBackend] and registering it
 * with [OverlayBackendRouter], without growing conditional chains in [SongPlayer].
 */
interface PlaybackBackend {
    val id: String
    val isActive: Boolean
    val holdsFocus: Boolean

    fun isPlaying(): Boolean
    fun isPositionMoving(): Boolean = isPlaying()
    fun durationMs(): Long
    fun positionMs(): Long

    fun resume()
    fun pause()
    fun next()
    fun previous()
    fun seekTo(ms: Long)
    fun stop()
    fun clearQueued() {}

    /** Optional playback speed control; returns true if handled by this backend. */
    fun setSpeed(speed: Double): Boolean = false

    /** Optional relative skip; returns true if handled by this backend. */
    fun skipBy(deltaMs: Long): Boolean = false

    /** Diagnostic label for logs. */
    fun describe(): String = id
}

/**
 * Routes transport and state queries to the highest-priority active [PlaybackBackend].
 */
class OverlayBackendRouter(private val backends: List<PlaybackBackend>) {
    fun active(): PlaybackBackend? = backends.firstOrNull { it.isActive }
    fun anyActive(): Boolean = active() != null
    fun anyHoldsFocus(): Boolean = backends.any { it.holdsFocus }
    fun stopAll() = backends.forEach { it.stop() }
}

object AudiblePlaybackBackend : PlaybackBackend {
    override val id: String = "audible"
    override val isActive: Boolean get() = AudiblePlayer.active.value
    override val holdsFocus: Boolean get() = AudiblePlayer.holdsFocus

    override fun isPlaying(): Boolean = AudiblePlayer.isPlaying()
    override fun isPositionMoving(): Boolean = AudiblePlayer.isPlaying() && !AudiblePlayer.isBuffering()
    override fun durationMs(): Long = AudiblePlayer.duration()
    override fun positionMs(): Long = AudiblePlayer.position()

    override fun resume() = AudiblePlayer.resume()
    override fun pause() = AudiblePlayer.pause()
    override fun next() = AudiblePlayer.next()
    override fun previous() = AudiblePlayer.previous()
    override fun seekTo(ms: Long) = AudiblePlayer.seekTo(ms)
    override fun stop() = AudiblePlayer.stop()
    override fun setSpeed(speed: Double): Boolean {
        AudiblePlayer.setSpeed(speed)
        return true
    }
    override fun skipBy(deltaMs: Long): Boolean {
        AudiblePlayer.skipBy(deltaMs)
        return true
    }
}

object LocalPlaybackBackend : PlaybackBackend {
    override val id: String = "local"
    override val isActive: Boolean get() = LocalPlayer.active.value
    override val holdsFocus: Boolean get() = LocalPlayer.holdsFocus

    override fun isPlaying(): Boolean = LocalPlayer.isPlaying()
    override fun durationMs(): Long = LocalPlayer.duration()
    override fun positionMs(): Long = LocalPlayer.position()

    override fun resume() = LocalPlayer.resume()
    override fun pause() = LocalPlayer.pause()
    override fun next() = LocalPlayer.next()
    override fun previous() = LocalPlayer.previous()
    override fun seekTo(ms: Long) = LocalPlayer.seekTo(ms)
    override fun stop() = LocalPlayer.stop()
    override fun clearQueued() = LocalPlayer.clearQueued()
    override fun describe(): String = "local, ${LocalPlayer.describe()}"
}
