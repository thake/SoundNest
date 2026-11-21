/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.parental.schedule

import android.app.Application
import io.github.thake.soundnest.feature.parental.settings.ParentalConfigBackup
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ScheduleEvaluatorTest {

    private val zone = ZoneId.of("Europe/Berlin")

    // 2026-11-20 is Friday, 2026-11-21 is Saturday, 2026-11-22 is Sunday, 2026-11-23 is Monday
    private fun time(dayOfMonth: Int, hour: Int, minute: Int): ZonedDateTime =
        ZonedDateTime.of(2026, 11, dayOfMonth, hour, minute, 0, 0, zone)

    @Test
    fun weekdayAndWeekendUsageHoursBoundaries() {
        val policy = ScheduleParentalPolicy(
            usageHoursEnabled = true,
            weekdayWindow = DailyTimeWindow(startMinutes = 7 * 60, endMinutes = 20 * 60),
            weekendWindow = DailyTimeWindow(startMinutes = 8 * 60, endMinutes = 21 * 60),
        )

        // Friday (weekday 07:00..20:00)
        assertFalse(ScheduleEvaluator.isWithinAllowedUsageHours(policy, time(20, 6, 59)))
        assertTrue(ScheduleEvaluator.isWithinAllowedUsageHours(policy, time(20, 7, 0)))
        assertTrue(ScheduleEvaluator.isWithinAllowedUsageHours(policy, time(20, 19, 59)))
        assertFalse(ScheduleEvaluator.isWithinAllowedUsageHours(policy, time(20, 20, 0)))

        // Saturday (weekend 08:00..21:00)
        assertFalse(ScheduleEvaluator.isWithinAllowedUsageHours(policy, time(21, 7, 59)))
        assertTrue(ScheduleEvaluator.isWithinAllowedUsageHours(policy, time(21, 8, 0)))
        assertTrue(ScheduleEvaluator.isWithinAllowedUsageHours(policy, time(21, 20, 59)))
        assertFalse(ScheduleEvaluator.isWithinAllowedUsageHours(policy, time(21, 21, 0)))
    }

    @Test
    fun nextAllowedWindowHandlesSameDayAndWeekendWeekdayTransitions() {
        val policy = ScheduleParentalPolicy(
            usageHoursEnabled = true,
            weekdayWindow = DailyTimeWindow(startMinutes = 7 * 60, endMinutes = 20 * 60),
            weekendWindow = DailyTimeWindow(startMinutes = 8 * 60, endMinutes = 21 * 60),
        )

        // Friday 06:15 -> today at 07:00
        val friMorning = ScheduleEvaluator.nextAllowedStart(policy, time(20, 6, 15))
        assertEquals(NextAllowedWindow(isToday = true, startMinutes = 7 * 60), friMorning)

        // Friday 20:30 -> tomorrow (Saturday) at 08:00
        val friNight = ScheduleEvaluator.nextAllowedStart(policy, time(20, 20, 30))
        assertEquals(NextAllowedWindow(isToday = false, startMinutes = 8 * 60), friNight)

        // Sunday 21:15 -> tomorrow (Monday) at 07:00
        val sunNight = ScheduleEvaluator.nextAllowedStart(policy, time(22, 21, 15))
        assertEquals(NextAllowedWindow(isToday = false, startMinutes = 7 * 60), sunNight)

        // During allowed hours -> null
        assertNull(ScheduleEvaluator.nextAllowedStart(policy, time(20, 12, 0)))
    }

    @Test
    fun bedtimeWindowWrapsAcrossMidnight() {
        val policy = ScheduleParentalPolicy(
            bedtimeInactivityEnabled = true,
            bedtimeStartMinutes = 19 * 60 + 30,
            bedtimeEndMinutes = 6 * 60,
            inactivityTimeoutMinutes = 20,
        )

        assertFalse(ScheduleEvaluator.isInBedtimeWindow(policy, time(20, 19, 29)))
        assertTrue(ScheduleEvaluator.isInBedtimeWindow(policy, time(20, 19, 30)))
        assertTrue(ScheduleEvaluator.isInBedtimeWindow(policy, time(20, 23, 59)))
        assertTrue(ScheduleEvaluator.isInBedtimeWindow(policy, time(21, 0, 30)))
        assertTrue(ScheduleEvaluator.isInBedtimeWindow(policy, time(21, 5, 59)))
        assertFalse(ScheduleEvaluator.isInBedtimeWindow(policy, time(21, 6, 0)))
        assertFalse(ScheduleEvaluator.isInBedtimeWindow(policy, time(21, 14, 0)))
    }

    @Test
    fun bedtimeInactivityRemainingCountdown() {
        val policy = ScheduleParentalPolicy(
            bedtimeInactivityEnabled = true,
            bedtimeStartMinutes = 19 * 60 + 30,
            bedtimeEndMinutes = 6 * 60,
            inactivityTimeoutMinutes = 20,
        )
        val night = time(20, 20, 0)

        // Not playing -> null
        assertNull(ScheduleEvaluator.bedtimeInactivityRemainingMs(policy, night, 100_000L, 200_000L, isPlaying = false))

        // Playing, 5 min elapsed -> 15 min remaining
        val rem = ScheduleEvaluator.bedtimeInactivityRemainingMs(
            policy = policy,
            now = night,
            lastActivityMonoMs = 100_000L,
            nowMonoMs = 100_000L + 5 * 60_000L,
            isPlaying = true,
        )
        assertEquals(15 * 60_000L, rem)

        // Playing, 25 min elapsed -> 0 ms remaining
        val expired = ScheduleEvaluator.bedtimeInactivityRemainingMs(
            policy = policy,
            now = night,
            lastActivityMonoMs = 100_000L,
            nowMonoMs = 100_000L + 25 * 60_000L,
            isPlaying = true,
        )
        assertEquals(0L, expired)
    }

    @Test
    fun backupJsonRoundTripPreservesSchedulePolicy() {
        val context = RuntimeEnvironment.getApplication()
        ParentalPolicyStore.resetForTests(context)

        val custom = ScheduleParentalPolicy(
            usageHoursEnabled = true,
            weekdayWindow = DailyTimeWindow(6 * 60 + 30, 19 * 60 + 45),
            weekendWindow = DailyTimeWindow(8 * 60 + 15, 21 * 60 + 30),
            bedtimeInactivityEnabled = true,
            bedtimeStartMinutes = 19 * 60,
            bedtimeEndMinutes = 5 * 60 + 30,
            inactivityTimeoutMinutes = 30,
        )
        ParentalPolicyStore.update(context) { it.copy(schedule = custom) }

        val exported = ParentalConfigBackup.exportJson(context)
        ParentalPolicyStore.resetForTests(context)
        assertFalse(ParentalPolicyStore.current(context).schedule.usageHoursEnabled)

        ParentalConfigBackup.importJson(context, exported).getOrThrow()
        assertEquals(custom, ParentalPolicyStore.current(context).schedule)
    }
}
