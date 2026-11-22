/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.parental.schedule.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.ui.classic.AppAccent
import io.github.thake.soundnest.core.ui.classic.AppBackground
import io.github.thake.soundnest.core.ui.classic.AppMuted
import io.github.thake.soundnest.core.ui.classic.AppText
import io.github.thake.soundnest.feature.parental.schedule.ParentalScheduleController
import io.github.thake.soundnest.feature.parental.security.AppSurface
import io.github.thake.soundnest.feature.parental.security.ParentSecurityManager

/**
 * Calm full-screen overlay shown when the current clock is outside parent-configured
 * Allowed Listening Hours and neither Parent Admin Session nor a temporary extension is active.
 */
@Composable
fun UsageHoursLockOverlay() {
    val context = LocalContext.current
    val state by ParentalScheduleController.state.collectAsState()
    if (!state.isLockedByUsageHours) return

    BackHandler(enabled = true) {}

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground.copy(alpha = 0.98f))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    }
                }
            }
            .systemBarsPadding()
            .padding(horizontal = 32.dp)
            .testTag("usage_hours_lock_overlay"),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Bedtime,
                contentDescription = null,
                tint = AppAccent,
                modifier = Modifier.size(64.dp),
            )
            Text(
                text = stringResource(R.string.parent_schedule_lock_title),
                color = AppText,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            state.nextAllowedWindow?.let { next ->
                val subtitle = if (next.isToday) {
                    stringResource(R.string.parent_schedule_lock_next_today, next.formattedStart)
                } else {
                    stringResource(R.string.parent_schedule_lock_next_tomorrow, next.formattedStart)
                }
                Text(
                    text = subtitle,
                    color = AppMuted,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("usage_hours_lock_next_time"),
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    ParentSecurityManager.runProtected(context, AppSurface.PARENT_CONTROLS) {
                        ParentalScheduleController.evaluateNow(context)
                    }
                },
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = AppAccent, contentColor = Color.Black),
                modifier = Modifier.testTag("usage_hours_parent_unlock_btn"),
            ) {
                Text(
                    text = stringResource(R.string.parent_schedule_lock_parent_unlock),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }
    }
}
