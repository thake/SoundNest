package io.github.thake.soundnest.host.android

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tracks Spotify's Widevine audio-licence rate limits (HTTP 429 storms) and recent non-429
 * licence refusals, extracted from [WebPlayerHost] so the sliding-window & exponential hold
 * state machine is thread-safe and independently unit-testable.
 */
internal class WidevineLimitGuard(
    private val clock: () -> Long = { android.os.SystemClock.elapsedRealtime() },
    private val log: (String) -> Unit = {},
) {
    private val heldLicenses = AtomicInteger(0)
    private val license429 = ArrayDeque<Long>()
    private var limitStreak = 0
    private var lastLimitAt = 0L

    private val _limitedUntil = MutableStateFlow(0L)
    /** Elapsed-realtime end of the current hold, 0 or past when none. */
    val limitedUntil: StateFlow<Long> = _limitedUntil

    @Volatile private var licenceRefusedAt = 0L

    fun isLimited(): Boolean = clock() < _limitedUntil.value

    fun recordRefused() {
        licenceRefusedAt = clock()
    }

    fun refusedWithin(ms: Long): Boolean =
        licenceRefusedAt != 0L && clock() - licenceRefusedAt < ms

    /**
     * Records a 429 licence response. Returns `true` if this call crossed [LIMIT_COUNT]
     * inside [LIMIT_WINDOW_MS] and opened a new playback hold.
     */
    fun onLicenseLimited(): Boolean = synchronized(license429) {
        val now = clock()
        license429.addLast(now)
        while (license429.isNotEmpty() && now - license429.first() > LIMIT_WINDOW_MS) {
            license429.removeFirst()
        }
        if (license429.size < LIMIT_COUNT || isLimited()) return@synchronized false
        if (now - lastLimitAt > LIMIT_FORGET_MS) limitStreak = 0
        lastLimitAt = now
        val hold = (LIMIT_HOLD_MS shl limitStreak.coerceAtMost(4)).coerceAtMost(LIMIT_HOLD_MAX_MS)
        limitStreak++
        license429.clear()
        log("Spotify is limiting audio licences (429 x$LIMIT_COUNT in ${LIMIT_WINDOW_MS / 1000} s): playback held ${hold / 1000} s")
        heldLicenses.set(0)
        _limitedUntil.value = now + hold
        true
    }

    /** Records a licence request intercepted locally during a hold; returns `true` for the first one. */
    fun recordHeldIsFirst(): Boolean = heldLicenses.getAndIncrement() == 0

    /** Seconds remaining in the active hold, clamped to at least 1 for the `Retry-After` header. */
    fun retryAfterSeconds(): Long = ((_limitedUntil.value - clock()) / 1000L).coerceAtLeast(1L)

    companion object {
        const val LIMIT_COUNT = 6
        const val LIMIT_WINDOW_MS = 10_000L
        const val LIMIT_HOLD_MS = 60_000L
        const val LIMIT_HOLD_MAX_MS = 600_000L
        const val LIMIT_FORGET_MS = 1_800_000L
    }
}
