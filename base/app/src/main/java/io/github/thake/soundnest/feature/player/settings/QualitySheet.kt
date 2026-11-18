package io.github.thake.soundnest.feature.player.settings

import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.playback.LocalPlayer
import io.github.thake.soundnest.core.playback.SongPlayer
import io.github.thake.soundnest.core.ui.classic.AppAccent
import io.github.thake.soundnest.core.ui.classic.AppText
import io.github.thake.soundnest.core.ui.classic.tone
import io.github.thake.soundnest.core.ui.look.LookSheet
import io.github.thake.soundnest.feature.system.settings.DataSaver
import io.github.thake.soundnest.host.android.SoundNestRuntime
import kotlinx.coroutines.delay

private val Green: Color get() = AppAccent
private val SheetColor: Color get() = tone(0xFF282828)

/**
 * Where the sound comes from (Spotify's codec and bitrate, a device local file, or Live Radio),
 * shown under the output device; "" while a song is being asked for or on another device.
 */
@Composable
fun playingSourceLabel(): String {
    val uri = SongPlayer.nowPlaying.value.uri
    if (io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayer.active.value ||
        io.github.thake.soundnest.feature.spokenword.audible.data.AudibleUris.isAudibleUri(uri)
    ) {
        return stringResource(R.string.player_source_audible)
    }
    if (SongPlayer.routing) return ""
    if (!LocalPlayer.active.value) return spotifyQualityLabel(uri)
    val t = LocalPlayer.current() ?: return ""
    return stringResource(if (t.isRadio) R.string.live_radio_title else R.string.nerd_source_file)
}

/** How many times (every 2 s, about half a minute) the page is asked for the stream it plays. */
private const val QUALITY_TRIES = 15

@Composable
private fun spotifyQualityLabel(uri: String): String {
    if (SongPlayer.activeDevice.value != null || uri.isBlank()) return ""
    val label by produceState("", uri) {
        value = ""
        repeat(QUALITY_TRIES) { i ->
            delay(if (i == 0) 1500L else 2000L)
            val (bitrate, mime) = SoundNestRuntime.spotify.player.audioInfo()
            if (bitrate > 0) { value = "${codecName(mime)} · ${bitrate / 1000} kbit/s"; return@produceState }
        }
    }
    return if (label.isBlank()) "" else stringResource(R.string.player_source_spotify, label)
}

internal fun streamingLabel(q: StreamingQuality): Int = when (q) {
    StreamingQuality.AUTO -> R.string.quality_streaming_auto
    StreamingQuality.LOW -> R.string.quality_streaming_low
    StreamingQuality.NORMAL -> R.string.quality_streaming_normal
    StreamingQuality.HIGH -> R.string.quality_streaming_high
    StreamingQuality.VERY_HIGH -> R.string.quality_streaming_very_high
}

/** The sound quality page: what plays now (Spotify's codec and bitrate, a device local file, or Live Radio), and the setting. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualitySheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val local = LocalPlayer.active.value
    val now by produceState<String?>(null, local) {
        value = if (local) {
            val t = LocalPlayer.current()
            val src = if (t != null) context.getString(if (t.isRadio) R.string.live_radio_title else R.string.nerd_source_file) else ""
            val fmt = LocalPlayer.audioFormat()
            val codec = fmt?.sampleMimeType?.let(::codecName).orEmpty()
            val kbps = fmt?.bitrate?.takeIf { it > 0 }?.let { "${it / 1000} kbit/s" }.orEmpty()
            listOf(src, codec, kbps).filter { it.isNotBlank() }.joinToString(" · ")
        } else {
            val (bitrate, mime) = SoundNestRuntime.spotify.player.audioInfo()
            if (bitrate <= 0) "" else "${codecName(mime)} · ${bitrate / 1000} kbit/s"
        }
    }
    LookSheet(onDismissRequest = onDismiss, containerColor = SheetColor) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(stringResource(R.string.quality_sheet_title), color = AppText, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            val text = now
            Text(
                when {
                    text == null -> "…"
                    text.isBlank() -> stringResource(R.string.quality_now_unknown)
                    else -> stringResource(R.string.quality_now, text)
                },
                color = Green, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            if (!local) StreamingQualityPicker(
                Modifier.padding(top = 12.dp),
                cellular = DataSaver.metered,
                title = if (DataSaver.metered) R.string.quality_streaming_cell_title else R.string.quality_streaming_wifi_title,
            )
        }
    }
}

fun codecName(mime: String): String = when {
    "mp4a" in mime -> "AAC"
    "flac" in mime -> "FLAC"
    "opus" in mime -> "Opus"
    "vorbis" in mime -> "Ogg Vorbis"
    "mpeg" in mime -> "MP3"
    else -> mime.substringBefore(';').substringAfter('/').uppercase()
}
