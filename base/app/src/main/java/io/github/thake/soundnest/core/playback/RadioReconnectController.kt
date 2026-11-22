package io.github.thake.soundnest.core.playback

import androidx.media3.common.PlaybackException

internal data class ReconnectAttempt(
    val attemptNumber: Int,
    val delayMs: Long,
)

/**
 * Backoff and retry-classification state machine for live radio stream drops,
 * extracted from [LocalPlayer] so reconnection rules are pure and independently testable.
 */
internal class RadioReconnectController(
    val maxTries: Int = DEFAULT_MAX_TRIES,
) {
    var retries: Int = 0
        private set

    fun reset() {
        retries = 0
    }

    /**
     * Advances the retry counter and returns the next [ReconnectAttempt] with exponential backoff,
     * or `null` if [maxTries] has been exhausted (resetting the counter to 0).
     */
    fun nextAttempt(): ReconnectAttempt? {
        if (retries >= maxTries) {
            retries = 0
            return null
        }
        retries++
        val delayMs = (1_000L shl (retries - 1)).coerceAtMost(MAX_DELAY_MS)
        return ReconnectAttempt(attemptNumber = retries, delayMs = delayMs)
    }

    companion object {
        const val DEFAULT_MAX_TRIES = 5
        const val STEADY_MS = 30_000L
        private const val MAX_DELAY_MS = 16_000L

        /** Only transient network / 5xx server failures warrant automatic stream reconnection. */
        fun isRetryableNetworkError(errorCode: Int, httpStatus: Int?): Boolean = when (errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            PlaybackException.ERROR_CODE_TIMEOUT -> true
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> httpStatus != null && httpStatus >= 500
            else -> false
        }
    }
}
