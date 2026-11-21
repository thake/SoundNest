/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.parental.schedule

import android.content.Context
import android.os.SystemClock
import io.github.thake.soundnest.core.playback.SongPlayer
import io.github.thake.soundnest.core.ui.look.UserTouch
import io.github.thake.soundnest.feature.parental.remote.settings.DeviceRole
import io.github.thake.soundnest.feature.parental.remote.settings.DeviceRoleStore
import io.github.thake.soundnest.feature.parental.security.ParentSecurityManager
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore
import io.github.thake.soundnest.feature.player.sleeptimer.playback.SleepTimerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

data class ScheduleRuntimeState(
    val isLockedByUsageHours: Boolean = false,
    val nextAllowedWindow: NextAllowedWindow? = null,
    val temporaryExtensionUntilMs: Long = 0L,
    val bedtimeInactivityActive: Boolean = false,
    val bedtimeRemainingMs: Long? = null,
)

/**
 * Runtime coordinator enforcing:
 * 1. **Allowed Listening Hours** — pauses playback and shows `UsageHoursLockOverlay` outside
 *    parent-configured Weekday/Weekend windows (unless Parent Mode is unlocked or a temporary
 *    parent extension is active).
 * 2. **Bedtime Inactivity Sleep Timer** — after the configured evening hour, smoothly fades out
 *    and pauses playback when there has been no touch/app activity for `X` minutes while playing.
 */
object ParentalScheduleController {
    private const val TICK_INTERVAL_MS = 5_000L
    internal const val FADE_LEAD_MS = 15_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(ScheduleRuntimeState())
    val state: StateFlow<ScheduleRuntimeState> = _state.asStateFlow()

    @Volatile
    internal var nowZonedProvider: () -> ZonedDateTime = { ZonedDateTime.now() }

    @Volatile
    internal var wallClockMs: () -> Long = { System.currentTimeMillis() }

    @Volatile
    internal var monoClockMs: () -> Long = { SystemClock.uptimeMillis() }

    @Volatile
    internal var isPlayingProvider: () -> Boolean = { runCatching { SongPlayer.isPlaying() }.getOrDefault(false) }

    @Volatile
    internal var sleepTrigger: (fade: Boolean) -> Unit = { fade ->
        runCatching { SleepTimerController.triggerParentalSleep(fade) }
    }

    @Volatile
    internal var fadeCanceller: () -> Unit = {
        runCatching { SleepTimerController.cancelParentalFadeIfActive() }
    }

    @Volatile
    private var started = false

    private val activeJobs = mutableListOf<Job>()

    @Volatile
    private var lastActivityMonoMs: Long = 0L

    @Volatile
    private var wasPlaying: Boolean = false

    @Volatile
    private var bedtimeFadeTriggered: Boolean = false

    @Volatile
    private var extensionUntilWallMs: Long = 0L

    @Synchronized
    fun start(context: Context) {
        if (started) return
        val app = context.applicationContext
        started = true
        lastActivityMonoMs = maxOf(UserTouch.lastAt.value, monoClockMs())

        activeJobs += scope.launch {
            UserTouch.lastAt.collect { touchAt ->
                if (touchAt > lastActivityMonoMs) {
                    lastActivityMonoMs = touchAt
                    if (bedtimeFadeTriggered) {
                        bedtimeFadeTriggered = false
                        fadeCanceller()
                    }
                    evaluateNow(app)
                }
            }
        }
        activeJobs += scope.launch {
            ParentalPolicyStore.policyFlow.collect { evaluateNow(app) }
        }
        activeJobs += scope.launch {
            ParentSecurityManager.sessionTick.collect { evaluateNow(app) }
        }
        activeJobs += scope.launch {
            while (isActive) {
                evaluateNow(app)
                delay(TICK_INTERVAL_MS)
            }
        }
    }

    /** Records explicit user/control activity, resetting the bedtime inactivity countdown. */
    fun recordActivity(context: Context? = null) {
        lastActivityMonoMs = monoClockMs()
        if (bedtimeFadeTriggered) {
            bedtimeFadeTriggered = false
            fadeCanceller()
        }
        if (context != null) evaluateNow(context)
    }

    /**
     * Grants a temporary listening-hours override (e.g. +30 or +60 minutes).
     * Fail-closed: requires an active Parent Admin Session.
     */
    fun grantTemporaryExtension(context: Context, minutes: Int): Boolean {
        val app = context.applicationContext
        if (!ParentSecurityManager.isAdminUnlocked(app) || minutes <= 0) return false
        extensionUntilWallMs = wallClockMs() + minutes * 60_000L
        evaluateNow(app)
        return true
    }

    fun clearTemporaryExtension(context: Context) {
        extensionUntilWallMs = 0L
        evaluateNow(context.applicationContext)
    }

    @Synchronized
    fun evaluateNow(context: Context): ScheduleRuntimeState {
        val app = context.applicationContext
        // Dedicated Parent Controller devices never lock themselves out.
        if (DeviceRoleStore.currentRole(app) == DeviceRole.PARENT_CONTROLLER) {
            val open = ScheduleRuntimeState()
            _state.value = open
            return open
        }

        val schedule = ParentalPolicyStore.current(app).schedule.clamped()
        val nowZoned = nowZonedProvider()
        val nowWall = wallClockMs()
        val nowMono = monoClockMs()
        val playing = isPlayingProvider()

        // Starting playback from paused counts as fresh activity so a bedtime story gets the full X minutes.
        if (playing && !wasPlaying) {
            lastActivityMonoMs = nowMono
            bedtimeFadeTriggered = false
        }
        if (!playing) {
            bedtimeFadeTriggered = false
        }
        wasPlaying = playing

        // Clear expired temporary extension
        if (extensionUntilWallMs > 0L && nowWall >= extensionUntilWallMs) {
            extensionUntilWallMs = 0L
        }

        val withinHours = ScheduleEvaluator.isWithinAllowedUsageHours(schedule, nowZoned)
        val adminUnlocked = ParentSecurityManager.isAdminUnlocked(app)
        val hasExtension = extensionUntilWallMs > nowWall
        val locked = schedule.usageHoursEnabled && !withinHours && !adminUnlocked && !hasExtension
        val nextAllowed = if (locked) ScheduleEvaluator.nextAllowedStart(schedule, nowZoned) else null

        if (locked && playing) {
            sleepTrigger(false)
        }

        val bedtimeRemaining = if (!locked) {
            ScheduleEvaluator.bedtimeInactivityRemainingMs(
                policy = schedule,
                now = nowZoned,
                lastActivityMonoMs = lastActivityMonoMs,
                nowMonoMs = nowMono,
                isPlaying = playing,
            )
        } else {
            null
        }

        if (bedtimeRemaining != null && bedtimeRemaining <= FADE_LEAD_MS && !bedtimeFadeTriggered) {
            bedtimeFadeTriggered = true
            sleepTrigger(true)
        }

        val nextState = ScheduleRuntimeState(
            isLockedByUsageHours = locked,
            nextAllowedWindow = nextAllowed,
            temporaryExtensionUntilMs = if (hasExtension) extensionUntilWallMs else 0L,
            bedtimeInactivityActive = bedtimeRemaining != null,
            bedtimeRemainingMs = bedtimeRemaining,
        )
        _state.value = nextState
        return nextState
    }

    @Synchronized
    internal fun resetForTests() {
        activeJobs.forEach { it.cancel() }
        activeJobs.clear()
        started = false
        nowZonedProvider = { ZonedDateTime.now() }
        wallClockMs = { System.currentTimeMillis() }
        monoClockMs = { SystemClock.uptimeMillis() }
        isPlayingProvider = { runCatching { SongPlayer.isPlaying() }.getOrDefault(false) }
        sleepTrigger = { fade -> runCatching { SleepTimerController.triggerParentalSleep(fade) } }
        fadeCanceller = { runCatching { SleepTimerController.cancelParentalFadeIfActive() } }
        lastActivityMonoMs = 0L
        wasPlaying = false
        bedtimeFadeTriggered = false
        extensionUntilWallMs = 0L
        _state.value = ScheduleRuntimeState()
    }
}
