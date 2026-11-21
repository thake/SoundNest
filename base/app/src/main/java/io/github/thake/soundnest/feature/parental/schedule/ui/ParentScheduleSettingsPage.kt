/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.parental.schedule.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.ui.immersive.shell.ImmersiveBarAction
import io.github.thake.soundnest.core.ui.look.LookAlertDialog
import io.github.thake.soundnest.core.ui.settings.M3GroupCard
import io.github.thake.soundnest.core.ui.settings.M3SettingsPalette
import io.github.thake.soundnest.core.ui.settings.M3SettingsScaffold
import io.github.thake.soundnest.core.ui.settings.M3SettingsSwitchRow
import io.github.thake.soundnest.core.ui.settings.m3FilledButtonColors
import io.github.thake.soundnest.core.ui.settings.m3FilterChipColors
import io.github.thake.soundnest.core.ui.settings.m3SettingsBlock
import io.github.thake.soundnest.core.ui.settings.rememberM3SettingsPalette
import io.github.thake.soundnest.feature.parental.schedule.ParentalScheduleController
import io.github.thake.soundnest.feature.parental.schedule.ScheduleParentalPolicy
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore

private data class TimePickerTarget(
    val title: String,
    val initialMinutes: Int,
    val onPicked: (Int) -> Unit,
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ParentScheduleSettingsPage(
    context: Context,
    policy: ParentalPolicy,
    listState: LazyListState,
    back: () -> Unit,
    lockAndExit: () -> Unit,
) {
    val palette = rememberM3SettingsPalette()
    val schedule = policy.schedule.clamped()
    val runtimeState by ParentalScheduleController.state.collectAsState()
    var pickerTarget by remember { mutableStateOf<TimePickerTarget?>(null) }

    fun updateSchedule(transform: (ScheduleParentalPolicy) -> ScheduleParentalPolicy) {
        ParentalPolicyStore.update(context) { it.copy(schedule = transform(it.schedule).clamped()) }
    }

    M3SettingsScaffold(
        title = stringResource(R.string.parent_schedule_section_title),
        onBack = back,
        listState = listState,
        listTestTag = "parent_schedule_list",
        immersiveActions = {
            ImmersiveBarAction(
                icon = Icons.Rounded.Lock,
                contentDescription = stringResource(R.string.parent_pin_lock),
                onClick = lockAndExit,
            )
        },
    ) { scaffoldPalette ->
        // 1. Allowed Listening Hours
        m3SettingsBlock(key = "schedule:hours", palette = scaffoldPalette) {
            Text(
                text = stringResource(R.string.parent_schedule_hours_header),
                color = scaffoldPalette.muted,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            )
            M3GroupCard(palette = scaffoldPalette) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    M3SettingsSwitchRow(
                        title = stringResource(R.string.parent_schedule_hours_enable_title),
                        detail = stringResource(R.string.parent_schedule_hours_enable_desc),
                        checked = schedule.usageHoursEnabled,
                        palette = scaffoldPalette,
                        onCheckedChange = { enabled ->
                            updateSchedule { it.copy(usageHoursEnabled = enabled) }
                        },
                        modifier = Modifier.testTag("schedule_usage_hours_toggle"),
                    )

                    if (schedule.usageHoursEnabled) {
                        val weekdayLabel = stringResource(R.string.parent_schedule_weekdays)
                        val weekendLabel = stringResource(R.string.parent_schedule_weekends)
                        val startLabel = stringResource(R.string.parent_schedule_start)
                        val endLabel = stringResource(R.string.parent_schedule_end)

                        TimeWindowRow(
                            label = weekdayLabel,
                            startMinutes = schedule.weekdayWindow.startMinutes,
                            endMinutes = schedule.weekdayWindow.endMinutes,
                            palette = scaffoldPalette,
                            onPickStart = {
                                pickerTarget = TimePickerTarget("$weekdayLabel · $startLabel", schedule.weekdayWindow.startMinutes) { m ->
                                    updateSchedule { it.copy(weekdayWindow = it.weekdayWindow.copy(startMinutes = m)) }
                                }
                            },
                            onPickEnd = {
                                pickerTarget = TimePickerTarget("$weekdayLabel · $endLabel", schedule.weekdayWindow.endMinutes) { m ->
                                    updateSchedule { it.copy(weekdayWindow = it.weekdayWindow.copy(endMinutes = m)) }
                                }
                            },
                        )

                        TimeWindowRow(
                            label = weekendLabel,
                            startMinutes = schedule.weekendWindow.startMinutes,
                            endMinutes = schedule.weekendWindow.endMinutes,
                            palette = scaffoldPalette,
                            onPickStart = {
                                pickerTarget = TimePickerTarget("$weekendLabel · $startLabel", schedule.weekendWindow.startMinutes) { m ->
                                    updateSchedule { it.copy(weekendWindow = it.weekendWindow.copy(startMinutes = m)) }
                                }
                            },
                            onPickEnd = {
                                pickerTarget = TimePickerTarget("$weekendLabel · $endLabel", schedule.weekendWindow.endMinutes) { m ->
                                    updateSchedule { it.copy(weekendWindow = it.weekendWindow.copy(endMinutes = m)) }
                                }
                            },
                        )

                        // Temporary parent override (+30 / +60 min)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = if (runtimeState.temporaryExtensionUntilMs > 0L) {
                                    stringResource(R.string.parent_schedule_extension_active)
                                } else {
                                    stringResource(R.string.parent_schedule_extension_title)
                                },
                                color = scaffoldPalette.muted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { ParentalScheduleController.grantTemporaryExtension(context, 30) },
                                    modifier = Modifier.testTag("schedule_extend_30_btn"),
                                ) {
                                    Text(stringResource(R.string.parent_schedule_extension_plus_30), color = scaffoldPalette.text)
                                }
                                OutlinedButton(
                                    onClick = { ParentalScheduleController.grantTemporaryExtension(context, 60) },
                                    modifier = Modifier.testTag("schedule_extend_60_btn"),
                                ) {
                                    Text(stringResource(R.string.parent_schedule_extension_plus_60), color = scaffoldPalette.text)
                                }
                                if (runtimeState.temporaryExtensionUntilMs > 0L) {
                                    TextButton(
                                        onClick = { ParentalScheduleController.clearTemporaryExtension(context) },
                                    ) {
                                        Text(stringResource(R.string.parent_schedule_extension_clear), color = scaffoldPalette.muted)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Bedtime Inactivity Sleep Timer
        m3SettingsBlock(key = "schedule:bedtime", palette = scaffoldPalette) {
            Text(
                text = stringResource(R.string.parent_schedule_bedtime_header),
                color = scaffoldPalette.muted,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            )
            M3GroupCard(palette = scaffoldPalette) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    M3SettingsSwitchRow(
                        title = stringResource(R.string.parent_schedule_bedtime_enable_title),
                        detail = stringResource(R.string.parent_schedule_bedtime_enable_desc),
                        checked = schedule.bedtimeInactivityEnabled,
                        palette = scaffoldPalette,
                        onCheckedChange = { enabled ->
                            updateSchedule { it.copy(bedtimeInactivityEnabled = enabled) }
                        },
                        modifier = Modifier.testTag("schedule_bedtime_toggle"),
                    )

                    if (schedule.bedtimeInactivityEnabled) {
                        val afterLabel = stringResource(R.string.parent_schedule_bedtime_after)
                        val untilLabel = stringResource(R.string.parent_schedule_bedtime_until)

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    pickerTarget = TimePickerTarget(afterLabel, schedule.bedtimeStartMinutes) { m ->
                                        updateSchedule { it.copy(bedtimeStartMinutes = m) }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = "$afterLabel: ${ScheduleParentalPolicy.formatMinutesOfDay(schedule.bedtimeStartMinutes)}",
                                    color = scaffoldPalette.text,
                                    fontSize = 13.sp,
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    pickerTarget = TimePickerTarget(untilLabel, schedule.bedtimeEndMinutes) { m ->
                                        updateSchedule { it.copy(bedtimeEndMinutes = m) }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = "$untilLabel: ${ScheduleParentalPolicy.formatMinutesOfDay(schedule.bedtimeEndMinutes)}",
                                    color = scaffoldPalette.text,
                                    fontSize = 13.sp,
                                )
                            }
                        }

                        Text(
                            text = stringResource(R.string.parent_schedule_inactivity_label),
                            color = scaffoldPalette.muted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            ScheduleParentalPolicy.INACTIVITY_PRESETS.forEach { minutes ->
                                val selected = schedule.inactivityTimeoutMinutes == minutes
                                FilterChip(
                                    selected = selected,
                                    onClick = { updateSchedule { it.copy(inactivityTimeoutMinutes = minutes) } },
                                    label = { Text(stringResource(R.string.parent_schedule_inactivity_min, minutes)) },
                                    leadingIcon = if (selected) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null,
                                    colors = m3FilterChipColors(scaffoldPalette),
                                    modifier = Modifier.testTag("schedule_inactivity_${minutes}m"),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    pickerTarget?.let { target ->
        val state = rememberTimePickerState(
            initialHour = target.initialMinutes / 60,
            initialMinute = target.initialMinutes % 60,
            is24Hour = true,
        )
        LookAlertDialog(
            onDismissRequest = { pickerTarget = null },
            containerColor = palette.dialogBg,
            title = {
                Text(target.title, color = palette.text, fontWeight = FontWeight.Bold)
            },
            text = {
                TimePicker(state = state)
            },
            confirmButton = {
                Button(
                    onClick = {
                        target.onPicked(state.hour * 60 + state.minute)
                        pickerTarget = null
                    },
                    colors = m3FilledButtonColors(palette),
                ) {
                    Text(stringResource(android.R.string.ok), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pickerTarget = null }) {
                    Text(stringResource(android.R.string.cancel), color = palette.muted)
                }
            },
        )
    }
}

@Composable
internal fun TimeWindowRow(
    label: String,
    startMinutes: Int,
    endMinutes: Int,
    palette: M3SettingsPalette,
    onPickStart: () -> Unit,
    onPickEnd: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = palette.text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedButton(onClick = onPickStart, modifier = Modifier.weight(1f)) {
                Text(
                    text = "${stringResource(R.string.parent_schedule_start)}: ${ScheduleParentalPolicy.formatMinutesOfDay(startMinutes)}",
                    color = palette.text,
                    fontSize = 13.sp,
                )
            }
            Spacer(Modifier.width(2.dp))
            OutlinedButton(onClick = onPickEnd, modifier = Modifier.weight(1f)) {
                Text(
                    text = "${stringResource(R.string.parent_schedule_end)}: ${ScheduleParentalPolicy.formatMinutesOfDay(endMinutes)}",
                    color = palette.text,
                    fontSize = 13.sp,
                )
            }
        }
    }
}
