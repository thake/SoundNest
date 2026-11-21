/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.parental.schedule

import kotlinx.serialization.Serializable

/**
 * A daily time window represented as minutes since midnight (`0..1439`).
 * Supports both normal daytime windows (e.g. `07:00..20:00`) and overnight wrap-around windows.
 */
@Serializable
data class DailyTimeWindow(
    val startMinutes: Int = 7 * 60,
    val endMinutes: Int = 20 * 60,
) {
    fun clamped(): DailyTimeWindow = copy(
        startMinutes = startMinutes.coerceIn(0, 23 * 60 + 59),
        endMinutes = endMinutes.coerceIn(0, 23 * 60 + 59),
    )
}

/**
 * Nested parental policy configuration for:
 * 1. **Allowed Listening Hours** (separate Weekday Mon–Fri and Weekend Sat–Sun windows).
 * 2. **Bedtime Inactivity Sleep Timer** ("no activity for X minutes after a certain evening hour").
 */
@Serializable
data class ScheduleParentalPolicy(
    val usageHoursEnabled: Boolean = false,
    val weekdayWindow: DailyTimeWindow = DailyTimeWindow(startMinutes = 7 * 60, endMinutes = 20 * 60),
    val weekendWindow: DailyTimeWindow = DailyTimeWindow(startMinutes = 8 * 60, endMinutes = 21 * 60),
    val bedtimeInactivityEnabled: Boolean = false,
    /** Evening start time in minutes since midnight (default 19:30 = 1170). */
    val bedtimeStartMinutes: Int = 19 * 60 + 30,
    /** Morning end time in minutes since midnight (default 06:00 = 360) for overnight coverage. */
    val bedtimeEndMinutes: Int = 6 * 60,
    /** Minutes of no touch/control activity while playing before auto fade-out & pause (5..120, default 20). */
    val inactivityTimeoutMinutes: Int = 20,
) {
    fun clamped(): ScheduleParentalPolicy = copy(
        weekdayWindow = weekdayWindow.clamped(),
        weekendWindow = weekendWindow.clamped(),
        bedtimeStartMinutes = bedtimeStartMinutes.coerceIn(0, 23 * 60 + 59),
        bedtimeEndMinutes = bedtimeEndMinutes.coerceIn(0, 23 * 60 + 59),
        inactivityTimeoutMinutes = inactivityTimeoutMinutes.coerceIn(MIN_INACTIVITY_MINUTES, MAX_INACTIVITY_MINUTES),
    )

    companion object {
        const val MIN_INACTIVITY_MINUTES = 5
        const val MAX_INACTIVITY_MINUTES = 120
        val INACTIVITY_PRESETS = listOf(10, 15, 20, 30, 45, 60)

        fun formatMinutesOfDay(minutes: Int): String {
            val m = minutes.coerceIn(0, 23 * 60 + 59)
            return "%02d:%02d".format(m / 60, m % 60)
        }
    }
}
