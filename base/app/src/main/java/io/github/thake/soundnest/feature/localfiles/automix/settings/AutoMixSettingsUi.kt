/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.localfiles.automix.settings

import android.content.Context
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.thake.soundnest.BuildConfig
import io.github.thake.soundnest.R
import io.github.thake.soundnest.automix.segue.MixIdea
import io.github.thake.soundnest.core.playback.LocalPlayer
import io.github.thake.soundnest.core.preferences.SettingsEntry
import io.github.thake.soundnest.core.ui.look.LookAlertDialog
import io.github.thake.soundnest.core.ui.settings.M3InfoBannerCard
import io.github.thake.soundnest.core.ui.settings.M3SettingsChoiceRow
import io.github.thake.soundnest.core.ui.settings.M3SettingsNavRow
import io.github.thake.soundnest.core.ui.settings.M3SettingsPalette
import io.github.thake.soundnest.core.ui.settings.M3SettingsSwitchRow
import io.github.thake.soundnest.core.ui.settings.m3SettingsBlock
import io.github.thake.soundnest.core.ui.settings.m3SettingsSection
import io.github.thake.soundnest.feature.localfiles.automix.engine.AutoMix
import io.github.thake.soundnest.feature.localfiles.automix.engine.AutoMixDiagnostics
import io.github.thake.soundnest.feature.localfiles.automix.ui.autoMixReasonText
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Unified Material Design 3 AutoMix settings sections (delegated to from `PlaybackSettingsUi`
 * when `policy.localFiles.enabled`) and Settings Search entries.
 */
object AutoMixSettingsUi {
    val settingsEntries: List<SettingsEntry> = listOf(
        SettingsEntry(
            title = R.string.settings_automix_style,
            detail = R.string.settings_automix_style_detail,
            classicPlace = "transitions",
            immersivePage = "Crossfade",
            isVisible = { it.localFiles.enabled },
        ),
        SettingsEntry(
            title = R.string.settings_automix_segue,
            detail = R.string.settings_automix_segue_detail,
            classicPlace = "transitions",
            immersivePage = "Crossfade",
            isVisible = { it.localFiles.enabled },
        ),
        SettingsEntry(
            title = R.string.settings_automix_session,
            detail = R.string.settings_automix_session_detail,
            classicPlace = "transitions",
            immersivePage = "Crossfade",
            isVisible = { it.localFiles.enabled },
        ),
        SettingsEntry(
            title = R.string.settings_automix_orpheon,
            detail = R.string.settings_automix_orpheon_detail,
            classicPlace = "transitions",
            immersivePage = "Crossfade",
            isVisible = { it.localFiles.enabled },
        ),
        SettingsEntry(
            title = R.string.settings_automix_album_order,
            detail = R.string.settings_automix_album_order_detail,
            classicPlace = "transitions",
            immersivePage = "Crossfade",
            isVisible = { it.localFiles.enabled },
        ),
        SettingsEntry(
            title = R.string.settings_automix_podcasts,
            detail = R.string.settings_automix_podcasts_detail,
            classicPlace = "transitions",
            immersivePage = "Crossfade",
            isVisible = { it.localFiles.enabled && it.spokenWord.allowPodcasts },
        ),
        SettingsEntry(
            title = R.string.settings_automix_harmonic,
            detail = R.string.settings_automix_harmonic_detail,
            classicPlace = "transitions",
            immersivePage = "Crossfade",
            isVisible = { it.localFiles.enabled },
        ),
        SettingsEntry(
            title = R.string.settings_automix_eq,
            detail = R.string.settings_automix_eq_detail,
            classicPlace = "transitions",
            immersivePage = "Crossfade",
            isVisible = { it.localFiles.enabled },
        ),
        SettingsEntry(
            title = R.string.settings_automix_show,
            detail = R.string.settings_automix_show_detail,
            classicPlace = "transitions",
            immersivePage = "Crossfade",
            isVisible = { it.localFiles.enabled },
        ),
        SettingsEntry(
            title = R.string.settings_automix_pulse,
            classicPlace = "transitions",
            immersivePage = "Crossfade",
            isVisible = { it.localFiles.enabled },
        ),
        SettingsEntry(
            title = R.string.settings_automix_on_next,
            detail = R.string.settings_automix_on_next_detail,
            classicPlace = "transitions",
            immersivePage = "Crossfade",
            isVisible = { it.localFiles.enabled },
        ),
        SettingsEntry(
            title = R.string.settings_automix_length,
            detail = R.string.settings_automix_length_detail,
            classicPlace = "transitions",
            immersivePage = "Crossfade",
            isVisible = { it.localFiles.enabled },
        ),
        SettingsEntry(
            title = R.string.settings_automix_duration,
            detail = R.string.settings_automix_duration_detail,
            classicPlace = "transitions",
            immersivePage = "Crossfade",
            isVisible = { it.localFiles.enabled },
        ),
        SettingsEntry(
            title = R.string.settings_automix_mobile_data,
            detail = R.string.settings_automix_mobile_data_detail,
            classicPlace = "transitions",
            immersivePage = "Crossfade",
            isVisible = { it.localFiles.enabled },
        ),
        SettingsEntry(
            title = R.string.settings_automix_performance,
            detail = R.string.settings_automix_performance_detail,
            classicPlace = "transitions",
            immersivePage = "Crossfade",
            isVisible = { it.localFiles.enabled },
        ),
        SettingsEntry(
            title = R.string.settings_automix_tempo,
            detail = R.string.settings_automix_tempo_detail,
            classicPlace = "transitions",
            immersivePage = "Crossfade",
            isVisible = { it.localFiles.enabled },
        ),
        SettingsEntry(
            title = R.string.settings_automix_diagnostics,
            classicPlace = "transitions",
            immersivePage = "Crossfade",
            isVisible = { it.localFiles.enabled },
        ),
        SettingsEntry(
            title = R.string.settings_automix_cache,
            classicPlace = "transitions",
            immersivePage = "Crossfade",
            isVisible = { it.localFiles.enabled },
        ),
    )

    fun currentWhyNotRes(): Int? = autoMixReasonText(AutoMix.whyNot())

    @Composable
    fun rememberCacheState(): Pair<Long?, () -> Unit> {
        val scope = rememberCoroutineScope()
        var cacheRefresh by remember { mutableStateOf(0) }
        val cacheBytes by produceState<Long?>(null, cacheRefresh) {
            value = withContext(Dispatchers.IO) { AutoMix.cacheBytes() }
        }
        val clear: () -> Unit = {
            scope.launch {
                withContext(Dispatchers.IO) { AutoMix.clearCache() }
                cacheRefresh++
            }
        }
        return cacheBytes to clear
    }

    fun LazyListScope.m3AutoMixSections(
        ctx: Context,
        policy: ParentalPolicy,
        palette: M3SettingsPalette,
        cacheBytes: Long?,
        onClearCache: () -> Unit,
    ) {
        if (!policy.localFiles.enabled) return
        val settings = AutoMixSettings

        currentWhyNotRes()?.let { reason ->
            m3SettingsBlock(key = "automix:status", palette = palette) {
                M3InfoBannerCard(
                    text = stringResource(reason),
                    palette = palette,
                    icon = Icons.Rounded.GraphicEq,
                )
            }
        }

        // 1. Transition Style & Planner
        m3SettingsSection(
            key = "automix:style",
            palette = palette,
            header = ctx.getString(R.string.settings_automix_style),
            footer = ctx.getString(R.string.settings_automix_style_detail),
        ) {
            listOf(
                R.string.settings_automix_style_blend,
                R.string.settings_automix_style_cut,
                R.string.settings_automix_style_fade,
            ).forEachIndexed { index, labelRes ->
                row {
                    M3SettingsChoiceRow(
                        title = stringResource(labelRes),
                        selected = settings.style == index,
                        palette = palette,
                        onClick = { settings.changeStyle(index, ctx) },
                    )
                }
            }
            if (settings.style == 0) {
                row {
                    M3SettingsSwitchRow(
                        title = stringResource(R.string.settings_automix_segue),
                        detail = stringResource(R.string.settings_automix_segue_detail),
                        checked = settings.segue,
                        palette = palette,
                        onCheckedChange = { settings.changeSegue(it, ctx) },
                    )
                }
            }
            if (!settings.segueActive) {
                row {
                    M3SettingsSwitchRow(
                        title = stringResource(R.string.settings_automix_session),
                        detail = stringResource(R.string.settings_automix_session_detail),
                        checked = settings.sessionDj,
                        palette = palette,
                        onCheckedChange = { settings.changeSessionDj(it, ctx) },
                    )
                }
            }
            row {
                M3SettingsSwitchRow(
                    title = stringResource(R.string.settings_automix_orpheon),
                    detail = stringResource(R.string.settings_automix_orpheon_detail),
                    checked = settings.orpheon,
                    palette = palette,
                    onCheckedChange = { settings.changeOrpheon(it, ctx) },
                )
            }
            if (BuildConfig.APPLICATION_ID == "io.github.thake.soundnest.automixlab") {
                for (idea in MixIdea.values()) {
                    row {
                        M3SettingsSwitchRow(
                            title = idea.title,
                            detail = idea.detail,
                            checked = settings.hasIdea(idea),
                            enabled = settings.orpheon,
                            palette = palette,
                            onCheckedChange = { settings.changeIdea(idea, it, ctx) },
                        )
                    }
                }
            }
        }

        // 2. Mix Rules
        m3SettingsSection(
            key = "automix:rules",
            palette = palette,
            header = ctx.getString(R.string.settings_automix_options),
            footer = ctx.getString(R.string.settings_automix_detail_soundnest),
        ) {
            row {
                M3SettingsSwitchRow(
                    title = stringResource(R.string.settings_automix_album_order),
                    detail = stringResource(R.string.settings_automix_album_order_detail),
                    checked = settings.albumOrder,
                    palette = palette,
                    onCheckedChange = { settings.changeAlbumOrder(it, ctx) },
                )
            }
            if (policy.spokenWord.allowPodcasts) {
                row {
                    M3SettingsSwitchRow(
                        title = stringResource(R.string.settings_automix_podcasts),
                        detail = stringResource(R.string.settings_automix_podcasts_detail),
                        checked = settings.mixPodcasts,
                        palette = palette,
                        onCheckedChange = { settings.changeMixPodcasts(it, ctx) },
                    )
                }
            }
            row {
                M3SettingsSwitchRow(
                    title = stringResource(R.string.settings_automix_harmonic),
                    detail = stringResource(R.string.settings_automix_harmonic_detail),
                    checked = settings.harmonic,
                    palette = palette,
                    onCheckedChange = { settings.changeHarmonic(it, ctx) },
                )
            }
            row {
                M3SettingsSwitchRow(
                    title = stringResource(R.string.settings_automix_eq),
                    detail = stringResource(R.string.settings_automix_eq_detail),
                    checked = settings.djEq,
                    palette = palette,
                    onCheckedChange = { settings.changeDjEq(it, ctx) },
                )
            }
        }

        // 3. Player Visuals
        m3SettingsSection(
            key = "automix:display",
            palette = palette,
            header = ctx.getString(R.string.settings_automix_display),
        ) {
            row {
                M3SettingsSwitchRow(
                    title = stringResource(R.string.settings_automix_show),
                    detail = stringResource(R.string.settings_automix_show_detail),
                    checked = settings.showInPlayer,
                    palette = palette,
                    onCheckedChange = { settings.changeShowInPlayer(it, ctx) },
                )
            }
            if (settings.showInPlayer) {
                listOf(
                    R.string.settings_crossfade_off,
                    R.string.settings_automix_pulse_subtle,
                    R.string.settings_automix_pulse_strong,
                ).forEachIndexed { index, labelRes ->
                    row {
                        M3SettingsChoiceRow(
                            title = "${stringResource(R.string.settings_automix_pulse)} · ${stringResource(labelRes)}",
                            selected = settings.pulseLevel == index,
                            palette = palette,
                            onClick = { settings.changePulseLevel(index, ctx) },
                        )
                    }
                }
            }
        }

        // 4. Timing & Bounds (On Next + Mix Length + Duration Bounds)
        m3SettingsSection(
            key = "automix:on-next",
            palette = palette,
            header = ctx.getString(R.string.settings_automix_on_next),
            footer = ctx.getString(R.string.settings_automix_on_next_detail),
        ) {
            listOf(
                R.string.settings_automix_next_cut,
                R.string.settings_automix_next_quick,
                R.string.settings_automix_next_full,
            ).forEachIndexed { index, labelRes ->
                row {
                    M3SettingsChoiceRow(
                        title = stringResource(labelRes),
                        selected = settings.nextStyle == index,
                        palette = palette,
                        onClick = { settings.changeNextStyle(index, ctx) },
                    )
                }
            }
        }

        m3SettingsSection(
            key = "automix:length",
            palette = palette,
            header = ctx.getString(R.string.settings_automix_length),
            footer = ctx.getString(R.string.settings_automix_length_detail),
        ) {
            listOf(
                R.string.settings_automix_length_short,
                R.string.settings_automix_length_auto,
                R.string.settings_automix_length_long,
            ).forEachIndexed { index, labelRes ->
                row {
                    M3SettingsChoiceRow(
                        title = stringResource(labelRes),
                        selected = settings.mixLength == index,
                        palette = palette,
                        onClick = { settings.changeMixLength(index, ctx) },
                    )
                }
            }
            row { M3AutoMixDurationBoundsRow(palette) }
        }

        // 5. Performance & Tempo
        m3SettingsSection(
            key = "automix:tempo",
            palette = palette,
            header = ctx.getString(R.string.settings_automix_tempo),
            footer = ctx.getString(R.string.settings_automix_tempo_detail),
        ) {
            listOf(
                R.string.settings_automix_tempo_strict,
                R.string.settings_automix_tempo_normal,
                R.string.settings_automix_tempo_wide,
            ).forEachIndexed { index, labelRes ->
                row {
                    M3SettingsChoiceRow(
                        title = stringResource(labelRes),
                        selected = settings.tempoRange == index,
                        palette = palette,
                        onClick = { settings.changeTempoRange(index, ctx) },
                    )
                }
            }
        }

        m3SettingsSection(
            key = "automix:analysis",
            palette = palette,
            header = ctx.getString(R.string.settings_automix_analysis),
        ) {
            row {
                M3SettingsSwitchRow(
                    title = stringResource(R.string.settings_automix_mobile_data),
                    detail = stringResource(R.string.settings_automix_mobile_data_detail),
                    checked = settings.analyzeOnMobileData,
                    palette = palette,
                    onCheckedChange = { settings.changeAnalyzeOnMobileData(it, ctx) },
                )
            }
            row {
                M3SettingsSwitchRow(
                    title = stringResource(R.string.settings_automix_performance),
                    detail = stringResource(R.string.settings_automix_performance_detail),
                    checked = settings.performancePriority,
                    palette = palette,
                    onCheckedChange = { settings.changePerformancePriority(it, ctx) },
                )
            }
        }

        // 6. Diagnostics & Cache (both looks!)
        m3SettingsSection(
            key = "automix:diagnostics-cache",
            palette = palette,
            header = ctx.getString(R.string.settings_automix_diagnostics),
        ) {
            row { M3AutoMixDiagnosticsRow(palette) }
            row {
                M3SettingsNavRow(
                    title = stringResource(R.string.settings_automix_cache),
                    detail = cacheBytes?.let {
                        stringResource(R.string.settings_automix_cache_detail, Formatter.formatShortFileSize(ctx, it))
                    },
                    palette = palette,
                    trailingContent = {
                        TextButton(
                            enabled = (cacheBytes ?: 0L) > 0L,
                            onClick = onClearCache,
                        ) {
                            Text(
                                text = stringResource(R.string.settings_automix_cache_clear),
                                color = if ((cacheBytes ?: 0L) > 0L) palette.accent else palette.muted,
                                fontFamily = palette.fontFamily,
                            )
                        }
                    },
                )
            }
        }
    }

    @Composable
    fun M3AutoMixIntroDialog(palette: M3SettingsPalette, onDismiss: () -> Unit) {
        val ctx = LocalContext.current
        LookAlertDialog(
            onDismissRequest = {
                onDismiss()
                AutoMixSettings.markIntroSeen(ctx)
            },
            containerColor = palette.dialogBg,
            title = {
                Text(
                    stringResource(R.string.settings_automix_intro_title),
                    color = palette.text,
                    fontFamily = palette.fontFamily,
                )
            },
            text = {
                Text(
                    stringResource(R.string.settings_automix_intro_text_soundnest),
                    color = palette.muted,
                    fontFamily = palette.fontFamily,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDismiss()
                        AutoMixSettings.markIntroSeen(ctx)
                    },
                ) {
                    Text(
                        stringResource(R.string.settings_automix_intro_ok),
                        color = palette.accent,
                        fontFamily = palette.fontFamily,
                    )
                }
            },
        )
    }
}

@Composable
private fun M3AutoMixDurationBoundsRow(palette: M3SettingsPalette) {
    val ctx = LocalContext.current
    val settings = AutoMixSettings
    var minText by remember(settings.minDurationSeconds) { mutableStateOf(settings.minDurationSeconds.toString()) }
    var maxText by remember(settings.maxDurationSeconds) { mutableStateOf(settings.maxDurationSeconds.toString()) }
    val minValue = minText.toIntOrNull()?.takeIf { it in 0..90 }
    val maxValue = maxText.toIntOrNull()?.takeIf { it in 0..90 }
    val valid = minValue != null && maxValue != null && (maxValue == 0 || minValue <= maxValue)
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = palette.text,
        unfocusedTextColor = palette.text,
        focusedBorderColor = palette.accent,
        unfocusedBorderColor = palette.divider,
        focusedLabelColor = palette.accent,
        unfocusedLabelColor = palette.muted,
        cursorColor = palette.accent,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_automix_duration),
            color = palette.text,
            fontFamily = palette.fontFamily,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = stringResource(R.string.settings_automix_duration_detail),
            color = palette.muted,
            fontFamily = palette.fontFamily,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(top = 10.dp),
        ) {
            OutlinedTextField(
                value = minText,
                onValueChange = { minText = it.filter(Char::isDigit).take(2) },
                label = { Text(stringResource(R.string.settings_automix_duration_min)) },
                singleLine = true,
                isError = minValue == null || (maxValue != null && maxValue > 0 && minValue > maxValue),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = fieldColors,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = maxText,
                onValueChange = { maxText = it.filter(Char::isDigit).take(2) },
                label = { Text(stringResource(R.string.settings_automix_duration_max)) },
                singleLine = true,
                isError = maxValue == null || (minValue != null && maxValue != 0 && minValue > maxValue),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = fieldColors,
                modifier = Modifier.weight(1f),
            )
        }
        if (!valid) {
            Text(
                text = stringResource(R.string.settings_automix_duration_error),
                color = palette.destructive,
                fontFamily = palette.fontFamily,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        } else if (minValue != settings.minDurationSeconds || maxValue != settings.maxDurationSeconds) {
            TextButton(
                onClick = { settings.changeDurationBounds(minValue!!, maxValue!!, ctx) },
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(
                    stringResource(R.string.settings_automix_duration_apply),
                    color = palette.accent,
                    fontFamily = palette.fontFamily,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** Event-driven status and recent AutoMix events; copied text is redacted before leaving the app. */
@Composable
private fun M3AutoMixDiagnosticsRow(palette: M3SettingsPalette) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val expandedState = remember { mutableStateOf(false) }
    val expanded = expandedState.value
    val events = AutoMix.journalSnapshot
    val cue by LocalPlayer.mixCue

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_automix_diagnostics),
                    color = palette.text,
                    fontFamily = palette.fontFamily,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = stringResource(
                        if (cue == null) R.string.settings_automix_diagnostics_no_plan
                        else if (cue?.mixing == true) R.string.settings_automix_diagnostics_running
                        else R.string.settings_automix_diagnostics_planned,
                    ),
                    color = palette.muted,
                    fontFamily = palette.fontFamily,
                    fontSize = 12.sp,
                )
            }
            TextButton(
                onClick = {
                    val reason = autoMixReasonText(AutoMix.whyNot())?.let(context.resources::getString).orEmpty()
                    val cueLine = cue?.let {
                        "${if (it.mixing) "running" else "planned"} ${it.style}, A ${it.exitMs}-${it.endMs} ms, B ${it.entryMs}-${it.resumeMs} ms"
                    } ?: "no transition planned"
                    val safeEvents = AutoMixDiagnostics.safeJournal(events.takeLast(20))
                    val report = buildString {
                        appendLine("AutoMix diagnostics")
                        appendLine("Enabled: ${AutoMixSettings.on}")
                        appendLine("Transition: $cueLine")
                        if (reason.isNotBlank()) appendLine("Reason: $reason")
                        appendLine("Recent events:")
                        safeEvents.forEach { appendLine(it) }
                    }
                    clipboard.setText(AnnotatedString(report))
                },
            ) {
                Text(
                    stringResource(R.string.settings_automix_diagnostics_copy),
                    color = palette.accent,
                    fontFamily = palette.fontFamily,
                )
            }
            TextButton(onClick = { expandedState.value = !expanded }) {
                Text(
                    stringResource(if (expanded) R.string.settings_automix_diagnostics_hide else R.string.settings_automix_diagnostics_show),
                    color = palette.accent,
                    fontFamily = palette.fontFamily,
                )
            }
        }
        if (expanded) {
            cue?.let {
                Text(
                    text = stringResource(
                        R.string.settings_automix_diagnostics_cue,
                        stringResource(if (it.mixing) R.string.settings_automix_diagnostics_running_short else R.string.settings_automix_diagnostics_planned_short),
                        it.style.name,
                        it.exitMs,
                        it.endMs,
                        it.entryMs,
                        it.resumeMs,
                    ),
                    color = palette.muted,
                    fontFamily = palette.fontFamily,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            autoMixReasonText(AutoMix.whyNot())?.let {
                Text(
                    stringResource(it),
                    color = palette.muted,
                    fontFamily = palette.fontFamily,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            val visible = events.takeLast(12)
            if (visible.isEmpty()) {
                Text(
                    stringResource(R.string.settings_automix_diagnostics_empty),
                    color = palette.muted,
                    fontFamily = palette.fontFamily,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else {
                visible.forEach {
                    Text(
                        it,
                        color = palette.muted,
                        fontFamily = palette.fontFamily,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
            Text(
                stringResource(R.string.settings_automix_diagnostics_privacy),
                color = palette.tertiary,
                fontFamily = palette.fontFamily,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
