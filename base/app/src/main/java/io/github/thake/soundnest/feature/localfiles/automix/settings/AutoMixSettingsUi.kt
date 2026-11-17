/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.localfiles.automix.settings

import android.content.Context
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.thake.soundnest.BuildConfig
import io.github.thake.soundnest.R
import io.github.thake.soundnest.automix.segue.MixIdea
import io.github.thake.soundnest.core.playback.LocalPlayer
import io.github.thake.soundnest.core.preferences.SettingsEntry
import io.github.thake.soundnest.core.ui.classic.AppAccent
import io.github.thake.soundnest.core.ui.classic.AppMuted
import io.github.thake.soundnest.core.ui.classic.AppText
import io.github.thake.soundnest.core.ui.classic.OptionRow
import io.github.thake.soundnest.core.ui.classic.tone
import io.github.thake.soundnest.core.ui.immersive.Immersive
import io.github.thake.soundnest.core.ui.immersive.pages.ImmersiveChoiceRow
import io.github.thake.soundnest.core.ui.immersive.pages.ImmersiveGroupRow
import io.github.thake.soundnest.core.ui.immersive.pages.ImmersiveSwitchRow
import io.github.thake.soundnest.core.ui.immersive.pages.immersiveGroup
import io.github.thake.soundnest.core.ui.immersive.sheets.ImmersiveAlertDialog
import io.github.thake.soundnest.feature.localfiles.automix.engine.AutoMix
import io.github.thake.soundnest.feature.localfiles.automix.engine.AutoMixDiagnostics
import io.github.thake.soundnest.feature.localfiles.automix.ui.autoMixReasonText
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Classic & Immersive AutoMix settings rows/groups (delegated to from `PlaybackSettingsUi`
 * when `policy.localFiles.enabled`) and Settings Search entries.
 */
object AutoMixSettingsUi {
    val settingsEntries: List<SettingsEntry> = listOf(
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
            title = R.string.settings_automix_mobile_data,
            detail = R.string.settings_automix_mobile_data_detail,
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
    fun ClassicAutoMixHeaderDetails() {
        Text(
            stringResource(R.string.settings_automix_detail_soundnest),
            color = AppMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
        currentWhyNotRes()?.let {
            Text(stringResource(it), color = AppAccent, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }

    @Composable
    fun ClassicAutoMixOptions(policy: ParentalPolicy) {
        if (!policy.localFiles.enabled) return
        val ctx = LocalContext.current
        val settings = AutoMixSettings

        @Composable
        fun Chip(label: String, selected: Boolean, onClick: () -> Unit) = Text(
            label,
            color = if (selected) Color.Black else AppText,
            fontSize = 14.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(if (selected) AppAccent else tone(0xFF2A2A2A))
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )

        if (settings.style == 0) OptionRow(
            stringResource(R.string.settings_automix_segue),
            stringResource(R.string.settings_automix_segue_detail),
            checked = settings.segue,
            onChecked = { settings.changeSegue(it, ctx) },
        )
        if (!settings.segueActive) OptionRow(
            stringResource(R.string.settings_automix_session),
            stringResource(R.string.settings_automix_session_detail),
            checked = settings.sessionDj,
            onChecked = { settings.changeSessionDj(it, ctx) },
        )
        OptionRow(
            stringResource(R.string.settings_automix_orpheon),
            stringResource(R.string.settings_automix_orpheon_detail),
            checked = settings.orpheon,
            onChecked = { settings.changeOrpheon(it, ctx) },
        )
        if (BuildConfig.APPLICATION_ID == "io.github.thake.soundnest.automixlab") {
            for (idea in MixIdea.values()) OptionRow(
                idea.title,
                idea.detail,
                checked = settings.hasIdea(idea),
                enabled = settings.orpheon,
                onChecked = { settings.changeIdea(idea, it, ctx) },
            )
        }
        OptionRow(
            stringResource(R.string.settings_automix_album_order),
            stringResource(R.string.settings_automix_album_order_detail),
            checked = settings.albumOrder,
            onChecked = { settings.changeAlbumOrder(it, ctx) },
        )
        if (policy.spokenWord.allowPodcasts) {
            OptionRow(
                stringResource(R.string.settings_automix_podcasts),
                stringResource(R.string.settings_automix_podcasts_detail),
                checked = settings.mixPodcasts,
                onChecked = { settings.changeMixPodcasts(it, ctx) },
            )
        }
        OptionRow(
            stringResource(R.string.settings_automix_harmonic),
            stringResource(R.string.settings_automix_harmonic_detail),
            checked = settings.harmonic,
            onChecked = { settings.changeHarmonic(it, ctx) },
        )
        OptionRow(
            stringResource(R.string.settings_automix_eq),
            stringResource(R.string.settings_automix_eq_detail),
            checked = settings.djEq,
            onChecked = { settings.changeDjEq(it, ctx) },
        )
        OptionRow(
            stringResource(R.string.settings_automix_show),
            stringResource(R.string.settings_automix_show_detail),
            checked = settings.showInPlayer,
            onChecked = { settings.changeShowInPlayer(it, ctx) },
        )
        OptionRow(
            stringResource(R.string.settings_automix_mobile_data),
            stringResource(R.string.settings_automix_mobile_data_detail),
            checked = settings.analyzeOnMobileData,
            onChecked = { settings.changeAnalyzeOnMobileData(it, ctx) },
        )
        @OptIn(ExperimentalLayoutApi::class)
        @Composable
        fun Choices(title: Int, detail: Int?, labels: List<Int>, selected: Int, pick: (Int) -> Unit) =
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                Text(stringResource(title), color = AppText, fontSize = 16.sp)
                detail?.let { Text(stringResource(it), color = AppMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp)) }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 10.dp),
                ) {
                    labels.forEachIndexed { i, l -> Chip(stringResource(l), selected == i) { pick(i) } }
                }
            }
        Choices(
            R.string.settings_automix_on_next,
            R.string.settings_automix_on_next_detail,
            listOf(R.string.settings_automix_next_cut, R.string.settings_automix_next_quick, R.string.settings_automix_next_full),
            settings.nextStyle,
        ) { settings.changeNextStyle(it, ctx) }
        Choices(
            R.string.settings_automix_style,
            R.string.settings_automix_style_detail,
            listOf(R.string.settings_automix_style_blend, R.string.settings_automix_style_cut, R.string.settings_automix_style_fade),
            settings.style,
        ) { settings.changeStyle(it, ctx) }
        Choices(
            R.string.settings_automix_length,
            R.string.settings_automix_length_detail,
            listOf(R.string.settings_automix_length_short, R.string.settings_automix_length_auto, R.string.settings_automix_length_long),
            settings.mixLength,
        ) { settings.changeMixLength(it, ctx) }
        AutoMixDurationBoundsRow()
        OptionRow(
            stringResource(R.string.settings_automix_performance),
            stringResource(R.string.settings_automix_performance_detail),
            checked = settings.performancePriority,
            onChecked = { settings.changePerformancePriority(it, ctx) },
        )
        Choices(
            R.string.settings_automix_tempo,
            R.string.settings_automix_tempo_detail,
            listOf(R.string.settings_automix_tempo_strict, R.string.settings_automix_tempo_normal, R.string.settings_automix_tempo_wide),
            settings.tempoRange,
        ) { settings.changeTempoRange(it, ctx) }
        if (settings.showInPlayer) Choices(
            R.string.settings_automix_pulse,
            null,
            listOf(R.string.settings_crossfade_off, R.string.settings_automix_pulse_subtle, R.string.settings_automix_pulse_strong),
            settings.pulseLevel,
        ) { settings.changePulseLevel(it, ctx) }
        AutoMixDiagnosticsPanel()
        AutoMixCacheRow()
    }

    @Composable
    fun ClassicAutoMixIntroDialog(onDismiss: () -> Unit) {
        val ctx = LocalContext.current
        AlertDialog(
            onDismissRequest = { onDismiss(); AutoMixSettings.markIntroSeen(ctx) },
            containerColor = tone(0xFF282828),
            title = { Text(stringResource(R.string.settings_automix_intro_title), color = AppText) },
            text = { Text(stringResource(R.string.settings_automix_intro_text_soundnest), color = AppMuted) },
            confirmButton = {
                TextButton(onClick = { onDismiss(); AutoMixSettings.markIntroSeen(ctx) }) {
                    Text(stringResource(R.string.settings_automix_intro_ok), color = AppAccent)
                }
            },
        )
    }

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

    fun LazyListScope.immersiveAutoMixGroups(
        ctx: Context,
        policy: ParentalPolicy,
        cacheBytes: Long?,
        onClearCache: () -> Unit,
    ) {
        if (!policy.localFiles.enabled) return
        val settings = AutoMixSettings
        currentWhyNotRes()?.let { reason ->
            immersiveGroup("automix-now") {
                row { ImmersiveGroupRow(stringResource(reason)) }
            }
        }
        immersiveGroup("automix-style", headerRes = R.string.settings_automix_style, footerRes = R.string.settings_automix_style_detail) {
            listOf(R.string.settings_automix_style_blend, R.string.settings_automix_style_cut, R.string.settings_automix_style_fade).forEachIndexed { i, label ->
                row { ImmersiveChoiceRow(stringResource(label), selected = settings.style == i, onClick = { settings.changeStyle(i, ctx) }) }
            }
            if (settings.style == 0) row {
                ImmersiveSwitchRow(
                    stringResource(R.string.settings_automix_segue),
                    checked = settings.segue,
                    onChecked = { settings.changeSegue(it, ctx) },
                    detail = stringResource(R.string.settings_automix_segue_detail),
                )
            }
        }
        immersiveGroup("automix-length", headerRes = R.string.settings_automix_length, footerRes = R.string.settings_automix_length_detail) {
            listOf(R.string.settings_automix_length_short, R.string.settings_automix_length_auto, R.string.settings_automix_length_long).forEachIndexed { i, label ->
                row { ImmersiveChoiceRow(stringResource(label), selected = settings.mixLength == i, onClick = { settings.changeMixLength(i, ctx) }) }
            }
            row {
                DurationStepper(R.string.settings_automix_duration_min, settings.minDurationSeconds, zero = R.string.settings_automix_duration_auto) { v ->
                    settings.changeDurationBounds(v, if (settings.maxDurationSeconds in 1 until v) v else settings.maxDurationSeconds, ctx)
                }
            }
            row {
                DurationStepper(R.string.settings_automix_duration_max, settings.maxDurationSeconds, zero = R.string.settings_automix_duration_none) { v ->
                    settings.changeDurationBounds(if (v in 1 until settings.minDurationSeconds) v else settings.minDurationSeconds, v, ctx)
                }
            }
        }
        immersiveGroup("automix-tempo", headerRes = R.string.settings_automix_tempo, footerRes = R.string.settings_automix_tempo_detail) {
            listOf(R.string.settings_automix_tempo_strict, R.string.settings_automix_tempo_normal, R.string.settings_automix_tempo_wide).forEachIndexed { i, label ->
                row { ImmersiveChoiceRow(stringResource(label), selected = settings.tempoRange == i, onClick = { settings.changeTempoRange(i, ctx) }) }
            }
        }
        immersiveGroup("automix-next", headerRes = R.string.settings_automix_on_next, footerRes = R.string.settings_automix_on_next_detail) {
            listOf(R.string.settings_automix_next_cut, R.string.settings_automix_next_quick, R.string.settings_automix_next_full).forEachIndexed { i, label ->
                row { ImmersiveChoiceRow(stringResource(label), selected = settings.nextStyle == i, onClick = { settings.changeNextStyle(i, ctx) }) }
            }
        }
        immersiveGroup("automix", headerRes = R.string.settings_automix_options, footerRes = R.string.settings_automix_detail_soundnest) {
            row {
                ImmersiveSwitchRow(
                    stringResource(R.string.settings_automix_harmonic),
                    checked = settings.harmonic,
                    onChecked = { settings.changeHarmonic(it, ctx) },
                    detail = stringResource(R.string.settings_automix_harmonic_detail),
                )
            }
            row {
                ImmersiveSwitchRow(
                    stringResource(R.string.settings_automix_eq),
                    checked = settings.djEq,
                    onChecked = { settings.changeDjEq(it, ctx) },
                    detail = stringResource(R.string.settings_automix_eq_detail),
                )
            }
            row {
                ImmersiveSwitchRow(
                    stringResource(R.string.settings_automix_album_order),
                    checked = settings.albumOrder,
                    onChecked = { settings.changeAlbumOrder(it, ctx) },
                    detail = stringResource(R.string.settings_automix_album_order_detail),
                )
            }
            if (policy.spokenWord.allowPodcasts) {
                row {
                    ImmersiveSwitchRow(
                        stringResource(R.string.settings_automix_podcasts),
                        checked = settings.mixPodcasts,
                        onChecked = { settings.changeMixPodcasts(it, ctx) },
                        detail = stringResource(R.string.settings_automix_podcasts_detail),
                    )
                }
            }
            row {
                ImmersiveSwitchRow(
                    stringResource(R.string.settings_automix_orpheon),
                    checked = settings.orpheon,
                    onChecked = { settings.changeOrpheon(it, ctx) },
                    detail = stringResource(R.string.settings_automix_orpheon_detail),
                )
            }
            if (!settings.segueActive) row {
                ImmersiveSwitchRow(
                    stringResource(R.string.settings_automix_session),
                    checked = settings.sessionDj,
                    onChecked = { settings.changeSessionDj(it, ctx) },
                    detail = stringResource(R.string.settings_automix_session_detail),
                )
            }
        }
        immersiveGroup("automix-analysis", headerRes = R.string.settings_automix_analysis) {
            row {
                ImmersiveSwitchRow(
                    stringResource(R.string.settings_automix_mobile_data),
                    checked = settings.analyzeOnMobileData,
                    onChecked = { settings.changeAnalyzeOnMobileData(it, ctx) },
                    detail = stringResource(R.string.settings_automix_mobile_data_detail),
                )
            }
            row {
                ImmersiveSwitchRow(
                    stringResource(R.string.settings_automix_performance),
                    checked = settings.performancePriority,
                    onChecked = { settings.changePerformancePriority(it, ctx) },
                    detail = stringResource(R.string.settings_automix_performance_detail),
                )
            }
        }
        immersiveGroup("automix-display", headerRes = R.string.settings_automix_display) {
            row {
                ImmersiveSwitchRow(
                    stringResource(R.string.settings_automix_show),
                    checked = settings.showInPlayer,
                    onChecked = { settings.changeShowInPlayer(it, ctx) },
                    detail = stringResource(R.string.settings_automix_show_detail),
                )
            }
            if (settings.showInPlayer) listOf(R.string.settings_crossfade_off, R.string.settings_automix_pulse_subtle, R.string.settings_automix_pulse_strong).forEachIndexed { i, label ->
                row {
                    ImmersiveChoiceRow(
                        stringResource(R.string.settings_automix_pulse) + " · " + stringResource(label),
                        selected = settings.pulseLevel == i,
                        onClick = { settings.changePulseLevel(i, ctx) },
                    )
                }
            }
        }
        immersiveGroup("automix-cache") {
            row {
                ImmersiveGroupRow(
                    stringResource(R.string.settings_automix_cache),
                    detail = cacheBytes?.let { stringResource(R.string.settings_automix_cache_detail, Formatter.formatShortFileSize(ctx, it)) },
                    trailing = {
                        TextButton(
                            enabled = (cacheBytes ?: 0L) > 0L,
                            onClick = onClearCache,
                        ) {
                            Text(
                                stringResource(R.string.settings_automix_cache_clear),
                                color = if ((cacheBytes ?: 0L) > 0L) Immersive.Accent else Immersive.TextSecondary,
                            )
                        }
                    },
                )
            }
        }
    }

    @Composable
    fun ImmersiveAutoMixIntroDialog(onDismiss: () -> Unit) {
        val ctx = LocalContext.current
        ImmersiveAlertDialog(
            onDismissRequest = { onDismiss(); AutoMixSettings.markIntroSeen(ctx) },
            title = { Text(stringResource(R.string.settings_automix_intro_title), style = Immersive.Headline) },
            text = { Text(stringResource(R.string.settings_automix_intro_text_soundnest), style = Immersive.Subhead) },
            confirmButton = {
                TextButton(onClick = { onDismiss(); AutoMixSettings.markIntroSeen(ctx) }) {
                    Text(stringResource(R.string.settings_automix_intro_ok), color = Immersive.Accent)
                }
            },
        )
    }
}

@Composable
private fun AutoMixDurationBoundsRow() {
    val ctx = LocalContext.current
    val settings = AutoMixSettings
    var minText by remember(settings.minDurationSeconds) { mutableStateOf(settings.minDurationSeconds.toString()) }
    var maxText by remember(settings.maxDurationSeconds) { mutableStateOf(settings.maxDurationSeconds.toString()) }
    val minValue = minText.toIntOrNull()?.takeIf { it in 0..90 }
    val maxValue = maxText.toIntOrNull()?.takeIf { it in 0..90 }
    val valid = minValue != null && maxValue != null && (maxValue == 0 || minValue <= maxValue)
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Text(stringResource(R.string.settings_automix_duration), color = AppText, fontSize = 16.sp)
        Text(stringResource(R.string.settings_automix_duration_detail), color = AppMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
            OutlinedTextField(
                minText,
                { minText = it.filter(Char::isDigit).take(2) },
                label = { Text(stringResource(R.string.settings_automix_duration_min)) },
                singleLine = true,
                isError = minValue == null || (maxValue != null && maxValue > 0 && minValue > maxValue),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                maxText,
                { maxText = it.filter(Char::isDigit).take(2) },
                label = { Text(stringResource(R.string.settings_automix_duration_max)) },
                singleLine = true,
                isError = maxValue == null || (minValue != null && maxValue != 0 && minValue > maxValue),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }
        if (!valid) {
            Text(stringResource(R.string.settings_automix_duration_error), color = AppAccent, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        } else if (minValue != settings.minDurationSeconds || maxValue != settings.maxDurationSeconds) {
            TextButton(
                onClick = { settings.changeDurationBounds(minValue!!, maxValue!!, ctx) },
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.settings_automix_duration_apply))
            }
        }
    }
}

/** Event-driven status and recent AutoMix events; copied text is redacted before leaving the app. */
@Composable
private fun AutoMixDiagnosticsPanel() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val expandedState = remember { mutableStateOf(false) }
    val expanded = expandedState.value
    val events = AutoMix.journalSnapshot
    val cue by LocalPlayer.mixCue
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_automix_diagnostics), color = AppText, fontSize = 16.sp)
                Text(
                    stringResource(
                        if (cue == null) R.string.settings_automix_diagnostics_no_plan
                        else if (cue?.mixing == true) R.string.settings_automix_diagnostics_running
                        else R.string.settings_automix_diagnostics_planned,
                    ),
                    color = AppMuted,
                    fontSize = 12.sp,
                )
            }
            TextButton(onClick = {
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
            }) { Text(stringResource(R.string.settings_automix_diagnostics_copy), color = AppAccent) }
            TextButton(onClick = { expandedState.value = !expanded }) {
                Text(stringResource(if (expanded) R.string.settings_automix_diagnostics_hide else R.string.settings_automix_diagnostics_show), color = AppAccent)
            }
        }
        if (expanded) {
            cue?.let {
                Text(
                    stringResource(
                        R.string.settings_automix_diagnostics_cue,
                        stringResource(if (it.mixing) R.string.settings_automix_diagnostics_running_short else R.string.settings_automix_diagnostics_planned_short),
                        it.style.name,
                        it.exitMs,
                        it.endMs,
                        it.entryMs,
                        it.resumeMs,
                    ),
                    color = AppMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            autoMixReasonText(AutoMix.whyNot())?.let {
                Text(stringResource(it), color = AppMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
            val visible = events.takeLast(12)
            if (visible.isEmpty()) Text(stringResource(R.string.settings_automix_diagnostics_empty), color = AppMuted, fontSize = 12.sp)
            else visible.forEach { Text(it, color = AppMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp)) }
            Text(stringResource(R.string.settings_automix_diagnostics_privacy), color = AppMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** The AutoMix analyses kept on the phone: their space, and a button that drops them (analysed again when played). */
@Composable
private fun AutoMixCacheRow() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableStateOf(0) }
    val bytes by produceState<Long?>(null, refresh) {
        value = withContext(Dispatchers.IO) { AutoMix.cacheBytes() }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 10.dp)) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_automix_cache), color = AppText, fontSize = 16.sp)
            Text(
                bytes?.let { stringResource(R.string.settings_automix_cache_detail, Formatter.formatShortFileSize(ctx, it)) }.orEmpty(),
                color = AppMuted,
                fontSize = 12.sp,
            )
        }
        TextButton(
            enabled = (bytes ?: 0L) > 0L,
            onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) { AutoMix.clearCache() }
                    refresh++
                }
            },
        ) { Text(stringResource(R.string.settings_automix_cache_clear), color = AppAccent) }
    }
}

/** A whole number of seconds from 0 to 90 with − and +; 0 reads as [zero] ("Automatic", "No limit"). */
@Composable
private fun DurationStepper(title: Int, value: Int, zero: Int, onChange: (Int) -> Unit) {
    val steps = listOf(0, 2, 4, 6, 8, 10, 12, 15, 20, 25, 30, 40, 50, 60, 75, 90)
    val at = steps.indexOfFirst { it >= value }.let { if (it < 0) steps.lastIndex else it }
    ImmersiveGroupRow(
        stringResource(title),
        value = if (value == 0) stringResource(zero) else stringResource(R.string.settings_automix_seconds, value),
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(enabled = at > 0, onClick = { onChange(steps[at - 1]) }) { Text("−", style = Immersive.Headline) }
                TextButton(
                    enabled = at < steps.lastIndex,
                    onClick = { onChange(steps[(if (steps[at] == value) at + 1 else at).coerceAtMost(steps.lastIndex)]) },
                ) { Text("+", style = Immersive.Headline) }
            }
        },
    )
}
