package io.github.thake.soundnest.ui.components

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import io.github.thake.soundnest.R
import io.github.thake.soundnest.automix.AutoMix
import io.github.thake.soundnest.cast.CastPlayback
import io.github.thake.soundnest.data.preferences.CrossfadePref
import io.github.thake.soundnest.data.preferences.StreamingQuality
import io.github.thake.soundnest.di.LocalPlayer
import io.github.thake.soundnest.di.SongPlayer
import io.github.thake.soundnest.ui.theme.AppMuted
import io.github.thake.soundnest.ui.theme.AppText
import io.github.thake.soundnest.ui.theme.tone
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * "Stats for nerds": what the app knows of the sound that plays, read again every second while the
 * sheet is open (and not at all otherwise: the reading lives in the sheet's composition). For
 * the local player: the source, the format the decoder reports, the bit rate, the buffer, the
 * output and the processing. For Spotify: what the page announces; the stream itself
 * is encrypted in the page, so nothing more is claimed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsForNerdsSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sections by produceState(emptyList<NerdSection>()) {
        while (true) {
            value = NerdStats.read(context)
            delay(1_000L)
        }
    }
    LookSheet(onDismissRequest = onDismiss, containerColor = tone(0xFF282828)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            Text(stringResource(R.string.nerd_title), color = AppText, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            sections.forEach { s ->
                Text(s.title, color = AppText, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 2.dp))
                s.rows.forEach { (label, value) ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 3.dp)) {
                        Text(label, color = AppMuted, fontSize = 13.sp, modifier = Modifier.weight(0.4f).padding(end = 12.dp))
                        Text(value, color = AppText, fontSize = 14.sp, modifier = Modifier.weight(0.6f))
                    }
                }
                s.note?.let { Text(it, color = AppMuted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) }
            }
        }
    }
}

/** A block of the sheet: its [title], its (label, value) [rows] and a closing [note]. */
internal class NerdSection(val title: String, val rows: List<Pair<String, String>>, val note: String? = null)

internal object NerdStats {
    /** What is read, for both players; on the main thread (ExoPlayer's), with one call into the page. */
    suspend fun read(context: Context): List<NerdSection> = buildList {
        val local = LocalPlayer.active.value
        if (local) add(exoSection(context))
        add(spotifySection(context, local))
    }

    private fun exoSection(c: Context): NerdSection {
        val rows = ArrayList<Pair<String, String>>()
        fun row(label: Int, value: String?) { if (!value.isNullOrBlank()) rows += c.getString(label) to value }
        val t = LocalPlayer.current()
        row(R.string.nerd_source, when {
            t == null -> null
            t.isRadio -> c.getString(R.string.live_radio_title)
            else -> c.getString(R.string.nerd_source_file)
        })
        val f = LocalPlayer.audioFormat()
        if (f == null) {
            if (!CastPlayback.active) row(R.string.nerd_codec, c.getString(R.string.nerd_format_unknown))
        } else {
            val mime = f.sampleMimeType ?: f.codecs
            val codecs = f.codecs?.takeIf { it != mime }?.let { " ($it)" }.orEmpty()
            row(R.string.nerd_codec, mime?.let { codecName(it) + codecs + (if (it.contains('/')) " · $it" else "") })
            row(R.string.nerd_container, f.containerMimeType)
            row(R.string.nerd_sample_rate, f.sampleRate.takeIf { it > 0 }?.let(::khz))
            row(R.string.nerd_bit_depth, bits(c, f.pcmEncoding))
            row(R.string.nerd_channels, f.channelCount.takeIf { it > 0 }?.toString())
        }
        val kbps = (f?.bitrate?.takeIf { it > 0 } ?: f?.averageBitrate?.takeIf { it > 0 })?.let { c.getString(R.string.nerd_bitrate_value, it / 1000) }
        row(R.string.nerd_bitrate, kbps)
        row(R.string.nerd_buffer, LocalPlayer.bufferedAheadMs()?.let { c.getString(R.string.nerd_buffer_value, it / 1000.0) })
        row(R.string.nerd_state, LocalPlayer.playbackState())
        rows += outputRows(c)
        // AutoMix: the mix ahead or playing, else why the next song will not be mixed. It replaces the crossfade.
        if (AutoMix.on) row(R.string.nerd_automix, autoMixState(c))
        row(R.string.nerd_processing, listOfNotNull(
            CrossfadePref.seconds.takeIf { it > 0 && !AutoMix.on }?.let { c.getString(R.string.nerd_crossfade, it) },
            if (LocalPlayer.fadeProgress() < 1f) c.getString(R.string.nerd_fading) else null,
            io.github.thake.soundnest.data.preferences.EqualizerPref.takeIf { it.enabled }?.let {
                c.getString(R.string.nerd_equalizer, c.getString(io.github.thake.soundnest.ui.screens.eqPresetLabel(it.preset)))
            },
            io.github.thake.soundnest.data.preferences.EqualizerPref.speed.takeIf { it != 1f }?.let { c.getString(R.string.nerd_speed, speedLabel(it.toDouble())) },
            if (io.github.thake.soundnest.data.preferences.EqualizerPref.skipSilence) c.getString(R.string.nerd_skip_silence) else null,
        ).joinToString(" · ").ifBlank { c.getString(R.string.nerd_processing_none) })
        return NerdSection(c.getString(R.string.nerd_section_exo), rows)
    }

    /** AutoMix's next transition as [LocalPlayer.mixCue] has it ("Blend at 3:12, 16 s"), or the reason there is none. */
    private fun autoMixState(c: Context): String {
        val cue = LocalPlayer.mixCue.value
        if (cue != null) {
            val style = c.getString(when (cue.style) {
                io.github.thake.soundnest.automix.MixPlan.Style.BLEND -> R.string.nerd_automix_blend
                io.github.thake.soundnest.automix.MixPlan.Style.CUT -> R.string.nerd_automix_cut
                else -> R.string.nerd_automix_fade
            })
            if (cue.mixing) return c.getString(R.string.nerd_automix_mixing, style)
            val at = cue.exitMs / 1000
            return c.getString(R.string.nerd_automix_planned, style, "%d:%02d".format(Locale.US, at / 60, at % 60),
                ((cue.endMs - cue.exitMs) / 1000).toInt().coerceAtLeast(1))
        }
        return io.github.thake.soundnest.ui.components.autoMixReasonText(AutoMix.whyNot())?.let(c::getString) ?: c.getString(R.string.nerd_automix_waiting)
    }

    /** The bit depth a PCM encoding says, or null (a compressed format, or none reported). */
    private fun bits(c: Context, encoding: Int): String? = when (encoding) {
        C.ENCODING_PCM_8BIT -> c.getString(R.string.nerd_bit_depth_value, 8)
        C.ENCODING_PCM_16BIT, C.ENCODING_PCM_16BIT_BIG_ENDIAN -> c.getString(R.string.nerd_bit_depth_value, 16)
        C.ENCODING_PCM_24BIT, C.ENCODING_PCM_24BIT_BIG_ENDIAN -> c.getString(R.string.nerd_bit_depth_value, 24)
        C.ENCODING_PCM_32BIT, C.ENCODING_PCM_32BIT_BIG_ENDIAN -> c.getString(R.string.nerd_bit_depth_value, 32)
        C.ENCODING_PCM_FLOAT -> c.getString(R.string.nerd_bit_depth_float)
        else -> null
    }

    private fun khz(hz: Int): String = if (hz % 1000 == 0) "${hz / 1000} kHz" else "%.1f kHz".format(Locale.US, hz / 1000.0)

    /**
     * Where the sound goes: the speaker, Bluetooth, USB or wired headphones (with the device's name
     * when Android gives it), the highest rate a USB device says it takes, and the rate Android's
     * mixer runs at. Android does not tell an app the Bluetooth codec: it is not shown.
     */
    private fun outputRows(c: Context): List<Pair<String, String>> {
        val rows = ArrayList<Pair<String, String>>()
        val cast = CastPlayback.device.value?.takeIf { CastPlayback.active }
        if (cast != null) return listOf(c.getString(R.string.nerd_output) to c.getString(R.string.nerd_output_cast, cast.name))
        val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val outs = runCatching { am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList() }.getOrDefault(emptyList())
        val usb = outs.firstOrNull { it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY }
        val route = AudioRoute.current(c)
        val kindLabel = when {
            route.kind == AudioRoute.Kind.BLUETOOTH -> R.string.nerd_output_bluetooth
            usb != null -> R.string.nerd_output_usb
            route.kind == AudioRoute.Kind.WIRED -> R.string.nerd_output_wired
            else -> R.string.nerd_output_speaker
        }
        val device = usb.takeIf { kindLabel == R.string.nerd_output_usb }
        val name = if (device != null) runCatching { device.productName?.toString().orEmpty() }.getOrDefault("") else route.name
        val kind = c.getString(kindLabel)
        rows += c.getString(R.string.nerd_output) to (if (name.isBlank()) kind else c.getString(R.string.nerd_output_named, kind, name))
        device?.sampleRates?.maxOrNull()?.takeIf { it > 0 }?.let { rows += c.getString(R.string.nerd_output_max_rate) to khz(it) }
        am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull()?.let { rows += c.getString(R.string.nerd_mixer_rate) to khz(it) }
        return rows
    }

    private suspend fun spotifySection(c: Context, local: Boolean): NerdSection {
        val rows = ArrayList<Pair<String, String>>()
        val title = c.getString(R.string.nerd_section_spotify)
        val remote = SongPlayer.activeDevice.value
        if (remote != null) {
            rows += c.getString(R.string.nerd_device) to remote.name
            return NerdSection(title, rows, c.getString(R.string.nerd_spotify_remote))
        }
        val page = io.github.thake.soundnest.host.android.SoundNestRuntime.spotify.player
        rows += c.getString(R.string.nerd_device) to c.getString(R.string.nerd_device_this_phone)
        rows += c.getString(R.string.nerd_requested) to c.getString(streamingLabel(StreamingQuality.of(page.streamingLevel)))
        // AutoMix on while Spotify plays: why its songs are not mixed.
        if (!local && AutoMix.on) io.github.thake.soundnest.ui.components.autoMixReasonText(AutoMix.whyNot())?.let { rows += c.getString(R.string.nerd_automix) to c.getString(it) }
        if (!local) {
            val (bitrate, mime) = page.audioInfo()
            rows += c.getString(R.string.nerd_page_reports) to
                if (bitrate > 0) "${codecName(mime)} · ${bitrate / 1000} kbit/s" else c.getString(R.string.nerd_page_reports_none)
        }
        return NerdSection(title, rows, c.getString(R.string.nerd_spotify_note))
    }
}
