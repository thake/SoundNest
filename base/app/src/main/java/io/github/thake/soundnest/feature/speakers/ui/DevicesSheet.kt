package io.github.thake.soundnest.feature.speakers.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.di.Graph
import io.github.thake.soundnest.core.playback.SongPlayer
import io.github.thake.soundnest.core.ui.classic.AppAccent
import io.github.thake.soundnest.core.ui.classic.AppMuted
import io.github.thake.soundnest.core.ui.classic.AppText
import io.github.thake.soundnest.core.ui.classic.tone
import io.github.thake.soundnest.core.ui.look.LookSheet
import io.github.thake.soundnest.feature.player.settings.getStreamingQuality
import io.github.thake.soundnest.feature.parental.settings.FeatureGates
import io.github.thake.soundnest.feature.speakers.cast.CastDiscovery
import io.github.thake.soundnest.feature.speakers.cast.CastPlayback
import io.github.thake.soundnest.feature.speakers.connect.LanDiscovery
import io.github.thake.soundnest.feature.speakers.jam.ui.JamSection
import io.github.thake.soundnest.feature.speakers.settings.TrustedNetworkGate
import io.github.thake.soundnest.feature.system.settings.DataSaver
import io.github.thake.soundnest.feature.player.settings.QualitySheet
import io.github.thake.soundnest.feature.player.settings.streamingLabel

private val Green: Color get() = AppAccent
private val SheetColor: Color get() = tone(0xFF282828)

/**
 * Name of the output playing now: the Connect device, else the headphones this phone
 * plays into (their Bluetooth name), else "This phone".
 */
@Composable
fun activeDeviceLabel(): String {
    CastPlayback.device.value?.let { return it.name }
    SongPlayer.activeDevice.value?.let { return it.name }
    val route = rememberAudioRoute()
    return when (route.kind) {
        // "This phone", in the player as in the official app. Spotify Connect still lists it under
        // the device's own name (the engine registers that name); only this label says "this phone".
        AudioRoute.Kind.SPEAKER -> stringResource(R.string.connect_phone_is_self)
        AudioRoute.Kind.BLUETOOTH -> route.name.ifBlank { stringResource(R.string.label_bluetooth) }
        AudioRoute.Kind.WIRED -> stringResource(R.string.headphones)
    }
}

/** The icon of the output playing now, matching [activeDeviceLabel]: a phone when this phone plays. */
@Composable
fun activeDeviceIcon(): ImageVector {
    if (CastPlayback.device.value != null) return Icons.Default.Cast
    SongPlayer.activeDevice.value?.let { return deviceIcon(it.type) }
    return when (rememberAudioRoute().kind) {
        AudioRoute.Kind.SPEAKER -> Icons.Default.Smartphone
        AudioRoute.Kind.BLUETOOTH -> Icons.Default.Bluetooth
        AudioRoute.Kind.WIRED -> Icons.Default.Headphones
    }
}

private fun deviceIcon(type: String): ImageVector = when (type.uppercase()) {
    "SMARTPHONE", "TABLET" -> Icons.Default.Smartphone
    "SPEAKER", "AVR", "STB", "AUDIO_DONGLE", "CAST_AUDIO" -> Icons.Default.Speaker
    "TV", "CAST_VIDEO", "GAME_CONSOLE" -> Icons.Default.Tv
    else -> Icons.Default.Computer
}

// ── Connect: where the music plays ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var devices by remember { mutableStateOf<List<SongPlayer.ConnectDevice>?>(null) }
    var quality by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { devices = SongPlayer.devices() }
    val remote = SongPlayer.activeDevice.value
    val route = rememberAudioRoute()
    val headphones = remote == null && route.kind != AudioRoute.Kind.SPEAKER
    if (quality) QualitySheet(onDismiss = { quality = false })
    LookSheet(onDismissRequest = onDismiss, containerColor = SheetColor) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                stringResource(R.string.listen_on),
                color = AppText, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            // The output playing now, the song, and its quality (tap: what plays, and change it).
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp)).background(tone(0xFF333333)).padding(16.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(activeDeviceLabel(), color = AppText, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (remote == null) Text(
                            stringResource(streamingLabel(getStreamingQuality(context, DataSaver.metered))),
                            color = Color(0xFFDDDDDD), fontSize = 12.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF4A4A4A))
                                .clickable { quality = true }.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    val title = Graph.currentSongState.title.value
                    if (title.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Icon(Icons.Default.Equalizer, null, tint = Green, modifier = Modifier.size(16.dp))
                        Text("$title — ${Graph.currentSongState.singer.value}", color = Green, fontSize = 14.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.dp))
                    }
                }
                Icon(
                    when {
                        CastPlayback.device.value != null -> Icons.Default.Cast
                        remote != null -> deviceIcon(remote.type)
                        headphones -> Icons.Default.Headphones
                        else -> Icons.Default.Smartphone
                    },
                    contentDescription = null, tint = Green, modifier = Modifier.padding(start = 12.dp).size(40.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            val list = devices
            val self = list?.firstOrNull { it.isSelf }
            // This phone's speaker: from headphones (the system's output picker), or back from a Connect device.
            if (remote != null || headphones) DeviceRow(Icons.Default.Smartphone, stringResource(R.string.connect_phone_is_self), null) {
                if (remote != null) self?.let { SongPlayer.transferTo(it) } else AudioRoute.pickOutput(context)
                onDismiss()
            }
            // The headphones this phone plays into, while a Connect device has the music.
            if (remote != null && route.kind != AudioRoute.Kind.SPEAKER || headphones) DeviceRow(
                Icons.Default.Headphones,
                if (route.kind == AudioRoute.Kind.BLUETOOTH) route.name.ifBlank { stringResource(R.string.label_bluetooth) } else stringResource(R.string.headphones),
                if (route.kind == AudioRoute.Kind.BLUETOOTH) stringResource(R.string.label_bluetooth) else null,
                current = headphones,
            ) {
                if (remote != null) self?.let { SongPlayer.transferTo(it) } else AudioRoute.pickOutput(context)
                onDismiss()
            }
            val lanTick = LanDiscovery.devices.size
            val visibleList = remember(list, lanTick) {
                list?.let { TrustedNetworkGate.filterConnectDevices(context, it) }
            }
            if (visibleList == null) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Green, modifier = Modifier.size(28.dp))
                }
            } else {
                visibleList.filter { !it.isSelf && !it.isActive }.forEach { d ->
                    DeviceRow(deviceIcon(d.type), d.name, null) { SongPlayer.transferTo(d); onDismiss() }
                }
                val lanVisible = TrustedNetworkGate.canProvisionLanDevices(context) &&
                    LanDiscovery.devices.isNotEmpty()
                if (visibleList.none { !it.isSelf } && !headphones && remote == null && CastDiscovery.devices.isEmpty() &&
                    !lanVisible && CastPlayback.device.value == null)
                    Text(stringResource(R.string.no_devices), color = AppText.copy(alpha = 0.7f), modifier = Modifier.padding(20.dp))
            }
            // Spotify Connect devices of the network that are not logged in to the account yet.
            LanDeviceRows()
            // Cast devices, for Myspoti's own songs (Spotify's go through Spotify Connect above).
            CastDeviceRows(onDismiss)
            if (FeatureGates.canUseJam(context)) {
                JamSection(onDismiss)
            }
            // Bluetooth: pair or pick headphones and speakers in the system settings.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp)).background(tone(0xFF333333))
                    .clickable { AudioRoute.openBluetooth(context) }.padding(14.dp),
            ) {
                Icon(Icons.Default.Bluetooth, null, tint = Green, modifier = Modifier.size(26.dp))
                Text(stringResource(R.string.label_bluetooth), color = Green, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun DeviceRow(icon: ImageVector, name: String, detail: String?, current: Boolean = false, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (current) Green else AppText, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(name, color = if (current) Green else AppText, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            if (detail != null) Text(detail, color = AppMuted, fontSize = 13.sp)
        }
    }
}
