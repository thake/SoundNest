package io.github.thake.soundnest.feature.equalizer.ui.classic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.ui.classic.AppAccent
import io.github.thake.soundnest.core.ui.classic.AppBackground
import io.github.thake.soundnest.core.ui.classic.AppMuted
import io.github.thake.soundnest.core.ui.classic.AppText
import io.github.thake.soundnest.core.ui.classic.tone
import io.github.thake.soundnest.feature.equalizer.dsp.EqBands
import io.github.thake.soundnest.feature.equalizer.dsp.Spatial
import io.github.thake.soundnest.feature.equalizer.settings.EqPreset
import io.github.thake.soundnest.feature.equalizer.settings.EqualizerSettings
import io.github.thake.soundnest.feature.equalizer.ui.dbText
import io.github.thake.soundnest.feature.equalizer.ui.eqBandLabel
import io.github.thake.soundnest.feature.equalizer.ui.eqPresetLabel
import io.github.thake.soundnest.feature.equalizer.ui.speedText
import io.github.thake.soundnest.feature.equalizer.ui.widthStep
import io.github.thake.soundnest.feature.equalizer.ui.widthText
import io.github.thake.soundnest.core.ui.classic.OptionRow
import io.github.thake.soundnest.core.ui.classic.Section

/**
 * Settings > Playback > Equalizer (Classic): the local player's equalizer (on/off, a preset, ten
 * bands and the preamp), its spatial audio, speed and silence skipping (see [EqualizerSettings]). Says
 * first what it applies to: never Spotify's own songs, whose sound the app does not get.
 */
@Composable
fun EqualizerScreen(navController: NavController) {
    val ctx = LocalContext.current
    val pref = EqualizerSettings
    Column(Modifier.fillMaxSize().background(AppBackground).statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(4.dp)) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.accessibility_navigate_back), tint = AppText)
            }
            Text(stringResource(R.string.equalizer_title), color = AppText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Text(stringResource(R.string.equalizer_scope_soundnest), color = AppMuted, fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            OptionRow(
                label = stringResource(R.string.equalizer_enable),
                detail = stringResource(R.string.equalizer_enable_detail),
                checked = pref.enabled,
                onChecked = { pref.setEnabled(ctx, it) },
            )
            Section(R.string.equalizer_preset)
            Chips(EqPreset.entries.filter { it != EqPreset.CUSTOM || pref.preset == EqPreset.CUSTOM }, { it == pref.preset },
                { stringResource(eqPresetLabel(it)) }, enabled = pref.enabled) { pref.setPreset(ctx, it) }
            Section(R.string.equalizer_bands)
            val gains = pref.gains
            for (band in 0 until EqBands.COUNT) {
                DbSlider(eqBandLabel(band), gains[band], EqBands.MIN_DB..EqBands.MAX_DB, pref.enabled,
                    onChange = { pref.setGain(ctx, band, it, save = false) }, onDone = { pref.save(ctx) })
            }
            DbSlider(stringResource(R.string.equalizer_preamp), pref.preamp, EqBands.PREAMP_MIN_DB..EqBands.PREAMP_MAX_DB, pref.enabled,
                onChange = { pref.setPreamp(ctx, it, save = false) }, onDone = { pref.save(ctx) })
            Text(stringResource(R.string.equalizer_preamp_detail), color = AppMuted, fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 20.dp))
            Text(stringResource(R.string.equalizer_reset), color = if (pref.enabled) AppAccent else AppMuted, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp).clip(RoundedCornerShape(50))
                    .clickable(enabled = pref.enabled) { pref.setPreset(ctx, EqPreset.FLAT) }.padding(horizontal = 12.dp, vertical = 8.dp))

            Section(R.string.spatial_title)
            OptionRow(
                label = stringResource(R.string.spatial_enable),
                detail = stringResource(R.string.spatial_detail_soundnest),
                checked = pref.spatial,
                onChecked = { pref.setSpatial(ctx, it) },
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Text(stringResource(R.string.spatial_width), color = if (pref.spatial) AppText else AppMuted, fontSize = 13.sp, modifier = Modifier.width(64.dp))
                Slider(
                    value = pref.spatialWidth, onValueChange = { pref.setSpatialWidth(ctx, widthStep(it), save = false) },
                    onValueChangeFinished = { pref.setSpatialWidth(ctx, pref.spatialWidth) },
                    valueRange = Spatial.MIN_WIDTH..Spatial.MAX_WIDTH, enabled = pref.spatial, modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(thumbColor = AppAccent, activeTrackColor = AppAccent, inactiveTrackColor = tone(0xFF3A3A3A)),
                )
                Text(widthText(pref.spatialWidth), color = AppMuted, fontSize = 12.sp,
                    modifier = Modifier.width(64.dp).padding(start = 8.dp))
            }

            Section(R.string.equalizer_section_playback)
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                Text(stringResource(R.string.equalizer_speed), color = AppText, fontSize = 16.sp)
                Text(stringResource(R.string.equalizer_speed_detail), color = AppMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            }
            Chips(EqualizerSettings.SPEEDS, { it == pref.speed }, { speedText(it) }) { pref.setSpeed(ctx, it) }
            OptionRow(
                label = stringResource(R.string.equalizer_skip_silence),
                detail = stringResource(R.string.equalizer_skip_silence_detail),
                checked = pref.skipSilence,
                onChecked = { pref.setSkipSilence(ctx, it) },
            )
        }
    }
}

/** A row of choices as the crossfade's, scrolling sideways when they do not fit. */
@Composable
private fun <T> Chips(items: List<T>, selected: (T) -> Boolean, label: @Composable (T) -> String, enabled: Boolean = true, onPick: (T) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        for (item in items) {
            val on = selected(item)
            Text(
                label(item),
                color = if (on) Color.Black else if (enabled) AppText else AppMuted, fontSize = 14.sp,
                modifier = Modifier.clip(RoundedCornerShape(50))
                    .background(if (on && enabled) AppAccent else if (on) AppMuted else tone(0xFF2A2A2A))
                    .clickable(enabled = enabled) { onPick(item) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

/** One band (or the preamp): its name, a slider in 0.5 dB steps and its gain. */
@Composable
private fun DbSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, enabled: Boolean,
    onChange: (Float) -> Unit, onDone: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Text(label, color = if (enabled) AppText else AppMuted, fontSize = 13.sp, modifier = Modifier.width(64.dp))
        Slider(
            value = value, onValueChange = { onChange((it * 2).let(Math::round) / 2f) }, onValueChangeFinished = onDone,
            valueRange = range, enabled = enabled, modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = AppAccent, activeTrackColor = AppAccent, inactiveTrackColor = tone(0xFF3A3A3A)),
        )
        Text(stringResource(R.string.equalizer_db, dbText(value)), color = AppMuted, fontSize = 12.sp,
            modifier = Modifier.width(64.dp).padding(start = 8.dp))
    }
}
