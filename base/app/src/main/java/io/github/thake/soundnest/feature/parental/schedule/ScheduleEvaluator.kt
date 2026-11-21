/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.parental.schedule

import java.time.DayOfWeek
import java.time.ZonedDateTime

data class NextAllowedWindow(
    val isToday: Boolean,
    val startMinutes: Int,
) {
    val formattedStart: String
        get() = ScheduleParentalPolicy.formatMinutesOfDay(startMinutes)
}

/**
 * Pure, deterministic evaluator for Allowed Listening Hours and Bedtime Inactivity Sleep Timer.
 */
object ScheduleEvaluator {

    fun isWeekend(dayOfWeek: DayOfWeek): Boolean =
        dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY

    fun windowForDay(policy: ScheduleParentalPolicy, dayOfWeek: DayOfWeek): DailyTimeWindow =
        if (isWeekend(dayOfWeek)) policy.weekendWindow.clamped() else policy.weekdayWindow.clamped()

    /**
     * Checks whether [minuteOfDay] (`0..1439`) falls inside `[startMinutes, endMinutes)`.
     * - If `start == end`, the window spans the full 24 hours (always allowed).
     * - If `start < end`, standard daytime window (`minute >= start && minute < end`).
     * - If `start > end`, overnight wrap-around window (`minute >= start || minute < end`).
     */
    fun isMinuteInWindow(minuteOfDay: Int, startMinutes: Int, endMinutes: Int): Boolean {
        val m = minuteOfDay.coerceIn(0, 23 * 60 + 59)
        val s = startMinutes.coerceIn(0, 23 * 60 + 59)
        val e = endMinutes.coerceIn(0, 23 * 60 + 59)
        return when {
            s == e -> true
            s < e -> m in s until e
            else -> m >= s || m < e
        }
    }

    fun isWithinAllowedUsageHours(policy: ScheduleParentalPolicy, now: ZonedDateTime): Boolean {
        if (!policy.usageHoursEnabled) return true
        val window = windowForDay(policy, now.dayOfWeek)
        val minuteOfDay = now.hour * 60 + now.minute
        return isMinuteInWindow(minuteOfDay, window.startMinutes, window.endMinutes)
    }

    /**
     * Computes when the next allowed listening window begins when currently outside allowed hours.
     * Returns `null` if listening is currently allowed.
     */
    fun nextAllowedStart(policy: ScheduleParentalPolicy, now: ZonedDateTime): NextAllowedWindow? {
        if (isWithinAllowedUsageHours(policy, now)) return null
        val todayWindow = windowForDay(policy, now.dayOfWeek)
        val minuteOfDay = now.hour * 60 + now.minute
        return if (minuteOfDay < todayWindow.startMinutes) {
            NextAllowedWindow(isToday = true, startMinutes = todayWindow.startMinutes)
        } else {
            val tomorrowWindow = windowForDay(policy, now.plusDays(1).dayOfWeek)
            NextAllowedWindow(isToday = false, startMinutes = tomorrowWindow.startMinutes)
        }
    }

    /**
     * Returns true when the Bedtime Inactivity Sleep Timer is enabled and [now] falls within
     * the configured evening-to-morning window (`[bedtimeStartMinutes, bedtimeEndMinutes)`).
     */
    fun isInBedtimeWindow(policy: ScheduleParentalPolicy, now: ZonedDateTime): Boolean {
        if (!policy.bedtimeInactivityEnabled) return false
        val p = policy.clamped()
        val minuteOfDay = now.hour * 60 + now.minute
        return isMinuteInWindow(minuteOfDay, p.bedtimeStartMinutes, p.bedtimeEndMinutes)
    }

    /**
     * Computes remaining milliseconds before Bedtime Inactivity auto-sleep triggers, or `null`
     * if bedtime inactivity is disabled, outside the bedtime window, or nothing is playing.
     */
    fun bedtimeInactivityRemainingMs(
        policy: ScheduleParentalPolicy,
        now: ZonedDateTime,
        lastActivityMonoMs: Long,
        nowMonoMs: Long,
        isPlaying: Boolean,
    ): Long? {
        if (!isPlaying || !isInBedtimeWindow(policy, now)) return null
        val timeoutMs = policy.clamped().inactivityTimeoutMinutes * 60_000L
        val elapsedMs = (nowMonoMs - lastActivityMonoMs).coerceAtLeast(0L)
        return (timeoutMs - elapsedMs).coerceAtLeast(0L)
    }
}
