/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.equalizer.ui

import io.github.thake.soundnest.feature.spokenword.ui.speedLabel

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.thake.soundnest.R
import io.github.thake.soundnest.feature.equalizer.dsp.EqBands
import io.github.thake.soundnest.feature.equalizer.settings.EqPreset
import java.text.NumberFormat
import java.util.Locale

/** "+3", "0", "-1.5": a gain as both looks show it. */
internal fun dbText(db: Float): String {
    val v = Math.round(db * 2) / 2f
    val s = if (v == Math.floor(v.toDouble()).toFloat()) v.toInt().toString() else String.format(Locale.getDefault(), "%.1f", v)
    return if (v > 0) "+$s" else s
}

/** The spatial width in steps of 5 %. */
internal fun widthStep(width: Float): Float = Math.round(width * 20) / 20f

/** "160 %", "160%": the spatial width as a percentage, in the phone's language. */
internal fun widthText(width: Float): String =
    NumberFormat.getPercentInstance(Locale.getDefault()).format(widthStep(width).toDouble())

/** "1×", "1,25×" in the phone's language. */
internal fun speedText(speed: Float): String = speedLabel(speed.toDouble()) + "×"

/** "31 Hz", "1 kHz", "16 kHz". */
@Composable
internal fun eqBandLabel(band: Int): String {
    val hz = EqBands.FREQUENCIES[band]
    return if (hz >= 1000f) stringResource(R.string.equalizer_khz, speedLabel(hz / 1000.0))
    else stringResource(R.string.equalizer_hz, hz.toInt().toString())
}

fun eqPresetLabel(p: EqPreset): Int = when (p) {
    EqPreset.FLAT -> R.string.eq_preset_flat
    EqPreset.BASS_BOOST -> R.string.eq_preset_bass_boost
    EqPreset.BASS_CUT -> R.string.eq_preset_bass_cut
    EqPreset.TREBLE_BOOST -> R.string.eq_preset_treble_boost
    EqPreset.TREBLE_CUT -> R.string.eq_preset_treble_cut
    EqPreset.VOCAL -> R.string.eq_preset_vocal
    EqPreset.ROCK -> R.string.eq_preset_rock
    EqPreset.POP -> R.string.eq_preset_pop
    EqPreset.JAZZ -> R.string.eq_preset_jazz
    EqPreset.CLASSICAL -> R.string.eq_preset_classical
    EqPreset.ELECTRONIC -> R.string.eq_preset_electronic
    EqPreset.HIP_HOP -> R.string.eq_preset_hip_hop
    EqPreset.ACOUSTIC -> R.string.eq_preset_acoustic
    EqPreset.SPOKEN_WORD -> R.string.eq_preset_spoken_word
    EqPreset.CUSTOM -> R.string.eq_preset_custom
}
