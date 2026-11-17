/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.speakers.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.thake.soundnest.core.playback.SongPlayer
import io.github.thake.soundnest.core.ui.classic.AppAccent
import io.github.thake.soundnest.core.ui.classic.AppMuted
import io.github.thake.soundnest.core.ui.classic.AppText
import io.github.thake.soundnest.core.ui.classic.tone
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy
import io.github.thake.soundnest.feature.parental.ui.PolicyToggleRow
import io.github.thake.soundnest.feature.parental.ui.RemovableChip
import io.github.thake.soundnest.feature.parental.ui.SectionCard
import io.github.thake.soundnest.feature.speakers.connect.LanDiscovery

/**
 * Parent Controls Dashboard section for Trusted Home Network & Speakers,
 * including the custom speaker nickname dialog.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpeakersParentSettingsSection(
    policy: ParentalPolicy,
    sessionTick: Long,
    externalRefreshKey: Int = 0,
) {
    val context = LocalContext.current
    var localRefresh by remember { mutableIntStateOf(0) }
    val refreshKey = externalRefreshKey + localRefresh
    var editingSpeakerId by remember { mutableStateOf<String?>(null) }
    var editingSpeakerName by remember { mutableStateOf("") }

    val selectedChipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = AppAccent,
        selectedLabelColor = Color.Black,
        selectedLeadingIconColor = Color.Black,
    )
    val accentFilledButtonColors = ButtonDefaults.buttonColors(
        containerColor = AppAccent,
        contentColor = Color.Black,
        disabledContainerColor = tone(0xFF2E2E2E),
        disabledContentColor = AppMuted.copy(alpha = 0.5f),
    )

    val lanDeviceCount = LanDiscovery.devices.size
    val requireWifi = policy.speakers.requireTrustedNetwork
    val ssids = policy.speakers.trustedSsids
    val lanFingerprints = policy.speakers.trustedLanFingerprints
    val currentSnap = remember(refreshKey, sessionTick, lanDeviceCount, policy.speakers) {
        TrustedNetworkGate.currentSnapshot(context)
    }
    val isTrustedNow = remember(refreshKey, sessionTick, currentSnap, policy.speakers) {
        TrustedNetworkGate.isNetworkTrusted(context, currentSnap)
    }
    val visibilityMode = policy.speakers.speakerVisibilityMode
    val hiddenIds = policy.speakers.hiddenDeviceIds
    val allowedIds = policy.speakers.allowedDeviceIds
    val customNames = policy.speakers.customSpeakerNames
    val liveDevices by produceState(emptyList<SongPlayer.ConnectDevice>(), refreshKey, sessionTick) {
        value = runCatching { SongPlayer.devices() }.getOrDefault(emptyList())
    }

    SectionCard("Trusted Home Network & Speakers") {
        val statusBg = when {
            !requireWifi -> tone(0xFF2C2C2C)
            isTrustedNow -> AppAccent.copy(alpha = 0.16f)
            else -> Color(0xFFFFB74D).copy(alpha = 0.14f)
        }
        val statusTint = when {
            !requireWifi -> AppMuted
            isTrustedNow -> AppAccent
            else -> Color(0xFFFFB74D)
        }
        val statusTitle = when {
            !requireWifi -> "Network boundary disabled — speakers allowed everywhere"
            isTrustedNow -> "✓ Trusted Home LAN active — speakers enabled"
            !currentSnap.onWifi -> "Not connected to Wi-Fi — remote speakers hidden"
            else -> "Untrusted Wi-Fi — remote speakers hidden"
        }
        val statusDetail = when {
            currentSnap.onWifi && !currentSnap.lanNetworkLabel.isNullOrBlank() ->
                "Current connection: ${currentSnap.lanNetworkLabel} (zero location permissions)"
            currentSnap.onWifi ->
                "Connected to Wi-Fi · Zero location permissions used"
            else ->
                "Connect to your Home Wi-Fi to trust its router & speaker fingerprint."
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(statusBg)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(statusTitle, color = statusTint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(statusDetail, color = AppMuted, fontSize = 11.sp)
        }

        PolicyToggleRow(
            title = "Require Trusted Home Network",
            subtitle = "Hide cloud Spotify Connect & Alexa speakers and block LAN speaker setup when away from Home Wi-Fi",
            checked = requireWifi,
        ) { v ->
            TrustedNetworkGate.setRequireTrustedNetwork(context, v)
            localRefresh++
        }

        Button(
            enabled = currentSnap.onWifi,
            onClick = {
                val ok = TrustedNetworkGate.trustCurrentNetwork(context)
                localRefresh++
                Toast.makeText(
                    context,
                    if (ok) "Current Home LAN & visible speakers trusted." else "Connect to your Home Wi-Fi first.",
                    Toast.LENGTH_SHORT,
                ).show()
            },
            colors = accentFilledButtonColors,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Trust Current Home LAN & Visible Speakers", fontWeight = FontWeight.Bold)
        }

        if (lanFingerprints.isNotEmpty() || ssids.isNotEmpty()) {
            Text(
                "Trusted Networks & Speaker Fingerprints",
                color = AppMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                lanFingerprints.sorted().forEach { fp ->
                    val label = TrustedNetworkGate.displayLabelForFingerprint(context, fp)
                    RemovableChip(label = label) {
                        TrustedNetworkGate.removeTrustedLanFingerprint(context, fp)
                        localRefresh++
                    }
                }
                ssids.sorted().forEach { ssid ->
                    val displaySsid = TrustedNetworkGate.displayLabelForSsid(context, ssid)
                    RemovableChip(label = "Wi-Fi: $displaySsid") {
                        TrustedNetworkGate.removeTrustedSsid(context, ssid)
                        localRefresh++
                    }
                }
            }
        }

        HorizontalDivider(color = tone(0xFF3A3A3A))
        Text("Speaker Visibility Mode", color = AppText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val allExceptHiddenSelected = visibilityMode == SpeakerVisibilityMode.ALLOW_ALL_EXCEPT_HIDDEN
            FilterChip(
                selected = allExceptHiddenSelected,
                onClick = {
                    TrustedNetworkGate.setSpeakerVisibilityMode(context, SpeakerVisibilityMode.ALLOW_ALL_EXCEPT_HIDDEN)
                    localRefresh++
                },
                colors = selectedChipColors,
                leadingIcon = if (allExceptHiddenSelected) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null,
                label = { Text("All Except Hidden", fontWeight = if (allExceptHiddenSelected) FontWeight.Bold else FontWeight.Normal) },
            )
            val onlyAllowedSelected = visibilityMode == SpeakerVisibilityMode.ONLY_ALLOWED
            FilterChip(
                selected = onlyAllowedSelected,
                onClick = {
                    TrustedNetworkGate.setSpeakerVisibilityMode(context, SpeakerVisibilityMode.ONLY_ALLOWED)
                    localRefresh++
                },
                colors = selectedChipColors,
                leadingIcon = if (onlyAllowedSelected) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null,
                label = { Text("Only Allowed", fontWeight = if (onlyAllowedSelected) FontWeight.Bold else FontWeight.Normal) },
            )
        }

        val knownSpeakers = remember(liveDevices, hiddenIds, allowedIds, customNames, lanDeviceCount) {
            val map = linkedMapOf<String, String>()
            liveDevices.filter { !it.isSelf && it.id.isNotBlank() }.forEach { d ->
                map[d.id] = customNames[d.id] ?: d.name
            }
            runCatching {
                LanDiscovery.devices.toList().forEach { lan ->
                    val id = lan.info.deviceId
                    if (id.isNotBlank() && id !in map) {
                        map[id] = customNames[id] ?: lan.info.name.ifBlank { id }
                    }
                }
            }
            (hiddenIds + allowedIds + customNames.keys).forEach { id ->
                if (id.isNotBlank() && id !in map) map[id] = customNames[id] ?: id
            }
            map.entries.toList()
        }

        if (knownSpeakers.isEmpty()) {
            Text(
                "Open the Connect device sheet while on Home Wi-Fi to discover speakers you can rename, hide, or allow.",
                color = AppMuted,
                fontSize = 12.sp,
            )
        } else {
            knownSpeakers.forEach { (id, label) ->
                val isHidden = id in hiddenIds
                val isAllowed = id in allowedIds
                val speakerUsable = if (visibilityMode == SpeakerVisibilityMode.ONLY_ALLOWED) isAllowed else !isHidden
                val statusLabel = if (visibilityMode == SpeakerVisibilityMode.ONLY_ALLOWED) {
                    if (speakerUsable) "Allowed" else "Blocked"
                } else {
                    if (speakerUsable) "Visible" else "Hidden"
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(label, color = AppText, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(id, color = AppMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = {
                        editingSpeakerId = id
                        editingSpeakerName = customNames[id].orEmpty()
                    }) {
                        Icon(Icons.Default.Edit, contentDescription = "Rename speaker", tint = AppMuted, modifier = Modifier.size(18.dp))
                    }
                    Text(
                        text = statusLabel,
                        color = if (speakerUsable) AppAccent else AppMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Switch(
                        checked = speakerUsable,
                        onCheckedChange = { usable ->
                            if (visibilityMode == SpeakerVisibilityMode.ONLY_ALLOWED) {
                                TrustedNetworkGate.setDeviceAllowedInStrictMode(context, id, usable)
                            } else {
                                TrustedNetworkGate.setDeviceHidden(context, id, !usable)
                            }
                            localRefresh++
                        },
                    )
                }
            }
        }
    }

    editingSpeakerId?.let { speakerId ->
        AlertDialog(
            onDismissRequest = { editingSpeakerId = null },
            containerColor = tone(0xFF282828),
            title = { Text("Custom Speaker Nickname", color = AppText, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = editingSpeakerName,
                    onValueChange = { editingSpeakerName = it },
                    label = { Text("Nickname (e.g. Emma's Bedroom)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = AppAccent,
                        disabledContentColor = AppMuted.copy(alpha = 0.45f),
                    ),
                    onClick = {
                        CustomSpeakerNames.setCustomName(context, speakerId, editingSpeakerName)
                        editingSpeakerId = null
                        localRefresh++
                    },
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingSpeakerId = null }) {
                    Text("Cancel", color = AppMuted)
                }
            },
        )
    }
}
