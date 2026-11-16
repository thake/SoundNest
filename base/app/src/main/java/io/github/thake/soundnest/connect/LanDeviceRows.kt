package io.github.thake.soundnest.connect

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.thake.soundnest.R
import io.github.thake.soundnest.host.android.SoundNestRuntime
import io.github.thake.soundnest.ui.theme.AppMuted
import io.github.thake.soundnest.ui.theme.AppText

/**
 * The Spotify Connect devices of the network that are not among the account's yet, in the device
 * picker: a tap logs the device in ([LanLogin]) and moves the song to it. Devices that do not
 * announce the ids the login needs are counted in one line, so the picker says why they are not listed.
 */
@Composable
fun LanDeviceRows() {
    val context = LocalContext.current
    val canProvision = io.github.thake.soundnest.parental.TrustedNetworkGate.canProvisionLanDevices(context)
    val shouldProbe = canProvision || io.github.thake.soundnest.parental.TrustedNetworkGate.shouldProbeLanForFingerprint(context)
    DisposableEffect(shouldProbe) {
        if (shouldProbe) LanDiscovery.start(context)
        onDispose { if (shouldProbe) LanDiscovery.stop() }
    }
    val lanSnapshot = LanDiscovery.devices.toList()
    val trustedNow = io.github.thake.soundnest.parental.TrustedNetworkGate.canProvisionLanDevices(context)
    if (!trustedNow) return
    // Spotify's consent page, when a Sonos needs it (asked by the login, over the picker).
    DesktopConsentHost()
    val listed = SoundNestRuntime.spotify.devices.devices.collectAsState().value.filter { !it.local }
    val ids = listed.map { it.id }.toSet()
    val names = listed.map { it.name }.toSet()
    val unlisted = lanSnapshot
        .filter { !Zeroconf.inConnectList(it.info, ids, names) }
        .filter { d ->
            d.info.deviceId.isBlank() ||
                io.github.thake.soundnest.parental.TrustedNetworkGate.isDeviceAllowed(context, d.info.deviceId, isSelf = false)
        }
    val busy = LanLogin.busy.value
    unlisted.filter { it.info.loginable }.forEach { d ->
        val rawLabel = d.info.name.ifBlank { d.service }
        val label = CustomSpeakerNames.resolve(context, d.info.deviceId, rawLabel)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable(enabled = busy == null) { LanLogin.start(d) }
                .alpha(if (busy == null) 1f else 0.5f).padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Icon(Icons.Default.Speaker, contentDescription = null, tint = AppText, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text(label, color = AppText, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(stringResource(if (busy?.service == d.service) R.string.lan_logging_in else R.string.lan_device_detail), color = AppMuted, fontSize = 13.sp)
            }
        }
    }
    val others = unlisted.count { !it.info.loginable }
    if (others > 0) Text(stringResource(R.string.lan_others, others), color = AppMuted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
}
