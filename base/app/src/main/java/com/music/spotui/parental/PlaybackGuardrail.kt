/* SPDX-License-Identifier: GPL-3.0-only */
package com.music.spotui.parental

import com.music.spotui.data.entity.SongsModel
import com.music.spotui.di.SongPlayer

enum class LivePlaybackDecision {
    ALLOW,
    SUPPRESS_REMOTE_MIRROR,
    BLOCK_AND_SKIP_OR_PAUSE,
}

/**
 * Real-time playback & queue guardrails enforced by [SongPlayer]:
 * 1. Strips disallowed items from `EngineQueue` (`current`, `queued`, `nextUp`).
 * 2. Skips or pauses disallowed tracks if they ever start playing on this device.
 * 3. Suppresses remote Connect mirroring into MiniPlayer / MediaSession when another family
 *    member on a shared Spotify account plays an explicit/blocked song on another speaker, or
 *    when the active remote speaker is outside the Trusted Home Network.
 */
object PlaybackGuardrail {

    fun sanitizeSongs(
        songs: List<SongsModel>,
        policy: ParentalPolicy,
        contextUri: String? = null,
    ): List<SongsModel> =
        songs.filter { ParentalFilterEngine.isTrackAllowed(it, policy, contextUri) }

    fun sanitizeQueue(
        queue: SongPlayer.EngineQueue,
        policy: ParentalPolicy,
        contextUri: String? = null,
    ): SongPlayer.EngineQueue = SongPlayer.EngineQueue(
        current = queue.current?.takeIf { ParentalFilterEngine.isTrackAllowed(it, policy, contextUri) },
        queued = queue.queued.filter { ParentalFilterEngine.isTrackAllowed(it.song, policy, contextUri) },
        nextUp = queue.nextUp.filter { ParentalFilterEngine.isTrackAllowed(it.song, policy, contextUri) },
    )

    fun evaluateLivePlayback(
        item: SongsModel?,
        isRemoteActive: Boolean,
        isRemoteDeviceAllowed: Boolean,
        policy: ParentalPolicy,
        contextUri: String? = null,
        isAd: Boolean = false,
    ): LivePlaybackDecision {
        if (isRemoteActive) {
            if (!isRemoteDeviceAllowed) return LivePlaybackDecision.SUPPRESS_REMOTE_MIRROR
            if (isAd) return LivePlaybackDecision.ALLOW
            if (item != null && !ParentalFilterEngine.isTrackAllowed(item, policy, contextUri)) {
                return LivePlaybackDecision.SUPPRESS_REMOTE_MIRROR
            }
            return LivePlaybackDecision.ALLOW
        }
        // Never block, skip, or pause Spotify ads on Free accounts, even in strict allowlist mode.
        if (isAd) return LivePlaybackDecision.ALLOW
        if (item != null && !ParentalFilterEngine.isTrackAllowed(item, policy, contextUri)) {
            return LivePlaybackDecision.BLOCK_AND_SKIP_OR_PAUSE
        }
        return LivePlaybackDecision.ALLOW
    }
}
