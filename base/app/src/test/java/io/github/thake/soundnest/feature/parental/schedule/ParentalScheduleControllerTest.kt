/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.parental.schedule

import android.app.Application
import android.content.Context
import io.github.thake.soundnest.feature.parental.remote.settings.DeviceRole
import io.github.thake.soundnest.feature.parental.remote.settings.DeviceRoleStore
import io.github.thake.soundnest.feature.parental.security.ParentSecurityManager
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ParentalScheduleControllerTest {

    private lateinit var context: Context
    private val zone = ZoneId.of("Europe/Berlin")
    private var playing = false
    private var wallMs = 1_000_000L
    private var monoMs = 10_000L
    private var nowZoned: ZonedDateTime = ZonedDateTime.of(2026, 11, 20, 20, 30, 0, 0, zone) // Friday 20:30
    private val sleepCalls = mutableListOf<Boolean>()
    private var fadeCancelledCount = 0

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        ParentalScheduleController.resetForTests()
        DeviceRoleStore.resetForTests(context)
        ParentSecurityManager.resetForTests(context)
        ParentalPolicyStore.resetForTests(context)

        playing = false
        wallMs = 1_000_000L
        monoMs = 10_000L
        nowZoned = ZonedDateTime.of(2026, 11, 20, 20, 30, 0, 0, zone)
        sleepCalls.clear()
        fadeCancelledCount = 0

        ParentalScheduleController.nowZonedProvider = { nowZoned }
        ParentalScheduleController.wallClockMs = { wallMs }
        ParentalScheduleController.monoClockMs = { monoMs }
        ParentalScheduleController.isPlayingProvider = { playing }
        ParentalScheduleController.sleepTrigger = { fade -> sleepCalls += fade }
        ParentalScheduleController.fadeCanceller = { fadeCancelledCount++ }
    }

    @After
    fun tearDown() {
        ParentalScheduleController.resetForTests()
    }

    @Test
    fun usageHoursLocksPausesPlaybackAndRespectsParentOverride() {
        ParentSecurityManager.setupInitialPin(context, "1234")
        ParentSecurityManager.lockNow(context)
        ParentalPolicyStore.update(context) {
            it.copy(
                schedule = ScheduleParentalPolicy(
                    usageHoursEnabled = true,
                    weekdayWindow = DailyTimeWindow(7 * 60, 20 * 60),
                    weekendWindow = DailyTimeWindow(8 * 60, 21 * 60),
                ),
            )
        }
        playing = true

        // Friday 20:30 -> locked & pauses immediately (fade = false)
        val lockedState = ParentalScheduleController.evaluateNow(context)
        assertTrue(lockedState.isLockedByUsageHours)
        assertEquals(NextAllowedWindow(isToday = false, startMinutes = 8 * 60), lockedState.nextAllowedWindow)
        assertEquals(listOf(false), sleepCalls)

        // Extension fails closed while Admin Session is locked
        assertFalse(ParentalScheduleController.grantTemporaryExtension(context, 30))

        // Unlocking Parent Admin Session lifts lock & allows granting +30 min extension
        ParentSecurityManager.verifyPin(context, "1234")
        assertFalse(ParentalScheduleController.evaluateNow(context).isLockedByUsageHours)
        assertTrue(ParentalScheduleController.grantTemporaryExtension(context, 30))

        // Even after re-locking Admin Session, +30 min extension keeps app unlocked
        ParentSecurityManager.lockNow(context)
        assertFalse(ParentalScheduleController.evaluateNow(context).isLockedByUsageHours)

        // Rewinding wall clock must not extend the monotonic temporary override
        wallMs -= 60 * 60_000L
        // After 31 monotonic minutes, extension expires and lock returns
        monoMs += 31 * 60_000L
        assertTrue(ParentalScheduleController.evaluateNow(context).isLockedByUsageHours)
    }

    @Test
    fun bedtimeInactivityTriggersFadeAfterTimeoutAndResetsOnActivity() {
        ParentalPolicyStore.update(context) {
            it.copy(
                schedule = ScheduleParentalPolicy(
                    bedtimeInactivityEnabled = true,
                    bedtimeStartMinutes = 19 * 60 + 30,
                    bedtimeEndMinutes = 6 * 60,
                    inactivityTimeoutMinutes = 20,
                ),
            )
        }

        // Before 19:30 -> does not trigger even after 30 min idle
        nowZoned = ZonedDateTime.of(2026, 11, 20, 18, 0, 0, 0, zone)
        playing = true
        ParentalScheduleController.evaluateNow(context)
        monoMs += 30 * 60_000L
        assertFalse(ParentalScheduleController.evaluateNow(context).bedtimeInactivityActive)
        assertTrue(sleepCalls.isEmpty())

        // Move into bedtime window (20:00) and start playing fresh -> full 20 min window
        playing = false
        ParentalScheduleController.evaluateNow(context)
        nowZoned = ZonedDateTime.of(2026, 11, 20, 20, 0, 0, 0, zone)
        playing = true
        val startState = ParentalScheduleController.evaluateNow(context)
        assertTrue(startState.bedtimeInactivityActive)
        assertEquals(20 * 60_000L, startState.bedtimeRemainingMs)

        // 19 min 50 sec with no activity -> within 15s fade lead -> triggers smooth fade (fade = true)
        monoMs += 19 * 60_000L + 50_000L
        ParentalScheduleController.evaluateNow(context)
        assertEquals(listOf(true), sleepCalls)

        // Child touches screen during fade -> cancels fade & resets full 20 min countdown
        ParentalScheduleController.recordActivity(context)
        assertEquals(1, fadeCancelledCount)
        assertEquals(20 * 60_000L, ParentalScheduleController.state.value.bedtimeRemainingMs)
    }

    @Test
    fun dedicatedParentControllerDeviceNeverLocksItselfOut() {
        DeviceRoleStore.setRole(context, DeviceRole.PARENT_CONTROLLER)
        ParentalPolicyStore.update(context) {
            it.copy(schedule = ScheduleParentalPolicy(usageHoursEnabled = true))
        }
        assertFalse(ParentalScheduleController.evaluateNow(context).isLockedByUsageHours)
    }
}
