/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.speakers.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.playback.SongPlayer
import io.github.thake.soundnest.core.ui.look.LookAlertDialog
import io.github.thake.soundnest.core.ui.settings.M3InfoBannerCard
import io.github.thake.soundnest.core.ui.settings.M3RemovableChip
import io.github.thake.soundnest.core.ui.settings.M3SettingsChoiceRow
import io.github.thake.soundnest.core.ui.settings.M3SettingsPalette
import io.github.thake.soundnest.core.ui.settings.M3SettingsSwitchRow
import io.github.thake.soundnest.core.ui.settings.m3FilledButtonColors
import io.github.thake.soundnest.core.ui.settings.m3SettingsBlock
import io.github.thake.soundnest.core.ui.settings.m3SettingsSection
import io.github.thake.soundnest.core.ui.settings.m3SwitchColors
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy
import io.github.thake.soundnest.feature.speakers.connect.LanDiscovery

@Stable
class SpeakersParentUiState(
    val onWifi: Boolean,
    val lanNetworkLabel: String?,
    val isTrustedNow: Boolean,
    val knownSpeakers: List<Map.Entry<String, String>>,
    val bumpRefresh: () -> Unit,
) {
    var editingSpeakerId by mutableStateOf<String?>(null)
    var editingSpeakerName by mutableStateOf("")
}

@Composable
fun rememberSpeakersParentUiState(
    policy: ParentalPolicy,
    sessionTick: Long,
    externalRefreshKey: Int = 0,
): SpeakersParentUiState {
    val context = LocalContext.current
    var localRefresh by remember { mutableIntStateOf(0) }
    val refreshKey = externalRefreshKey + localRefresh

    val lanDeviceCount = LanDiscovery.devices.size
    val currentSnap = remember(refreshKey, sessionTick, lanDeviceCount, policy.speakers) {
        TrustedNetworkGate.currentSnapshot(context)
    }
    val isTrustedNow = remember(refreshKey, sessionTick, currentSnap, policy.speakers) {
        TrustedNetworkGate.isNetworkTrusted(context, currentSnap)
    }
    val hiddenIds = policy.speakers.hiddenDeviceIds
    val allowedIds = policy.speakers.allowedDeviceIds
    val customNames = policy.speakers.customSpeakerNames
    val liveDevices by produceState(emptyList<SongPlayer.ConnectDevice>(), refreshKey, sessionTick) {
        value = runCatching { SongPlayer.devices() }.getOrDefault(emptyList())
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

    val state = remember {
        SpeakersParentUiState(
            onWifi = currentSnap.onWifi,
            lanNetworkLabel = currentSnap.lanNetworkLabel,
            isTrustedNow = isTrustedNow,
            knownSpeakers = knownSpeakers,
            bumpRefresh = { localRefresh++ },
        )
    }
    return remember(currentSnap, isTrustedNow, knownSpeakers) {
        SpeakersParentUiState(
            onWifi = currentSnap.onWifi,
            lanNetworkLabel = currentSnap.lanNetworkLabel,
            isTrustedNow = isTrustedNow,
            knownSpeakers = knownSpeakers,
            bumpRefresh = { localRefresh++ },
        ).apply {
            editingSpeakerId = state.editingSpeakerId
            editingSpeakerName = state.editingSpeakerName
        }
    }
}

/**
 * Material Design 3 sections for Parent Controls Trusted Home Network & Speakers.
 */
@OptIn(ExperimentalLayoutApi::class)
fun LazyListScope.m3SpeakersParentSettingsSections(
    context: Context,
    policy: ParentalPolicy,
    state: SpeakersParentUiState,
    palette: M3SettingsPalette,
) {
    val requireWifi = policy.speakers.requireTrustedNetwork
    val ssids = policy.speakers.trustedSsids
    val lanFingerprints = policy.speakers.trustedLanFingerprints
    val visibilityMode = policy.speakers.speakerVisibilityMode
    val hiddenIds = policy.speakers.hiddenDeviceIds
    val allowedIds = policy.speakers.allowedDeviceIds
    val customNames = policy.speakers.customSpeakerNames

    m3SettingsBlock(key = "parent_speakers:status", palette = palette) {
        val statusTint = when {
            !requireWifi -> palette.muted
            state.isTrustedNow -> palette.accent
            else -> palette.warning
        }
        val statusIcon = when {
            !requireWifi -> Icons.Rounded.Info
            state.onWifi -> Icons.Rounded.Wifi
            else -> Icons.Rounded.WifiOff
        }
        val statusTitle = when {
            !requireWifi -> stringResource(R.string.parent_speakers_untrusted_notice)
            state.isTrustedNow -> stringResource(R.string.parent_speakers_trusted_active)
            !state.onWifi -> stringResource(R.string.parent_speakers_not_wifi)
            else -> stringResource(R.string.parent_speakers_untrusted_wifi)
        }
        val statusDetail = when {
            state.onWifi && !state.lanNetworkLabel.isNullOrBlank() ->
                stringResource(R.string.parent_speakers_current_connection, state.lanNetworkLabel)
            state.onWifi ->
                stringResource(R.string.parent_speakers_wifi_zero_location)
            else ->
                stringResource(R.string.parent_speakers_connect_home_hint)
        }
        M3InfoBannerCard(
            title = statusTitle,
            text = statusDetail,
            icon = statusIcon,
            tint = statusTint,
            palette = palette,
        )
    }

    m3SettingsSection(
        key = "parent_speakers:network",
        palette = palette,
        header = context.getString(R.string.parent_speakers_section_network),
    ) {
        row {
            M3SettingsSwitchRow(
                title = stringResource(R.string.parent_speakers_require_title),
                detail = stringResource(R.string.parent_speakers_require_subtitle),
                checked = requireWifi,
                palette = palette,
                onCheckedChange = { v ->
                    TrustedNetworkGate.setRequireTrustedNetwork(context, v)
                    state.bumpRefresh()
                },
            )
        }
        row {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Button(
                    enabled = state.onWifi,
                    onClick = {
                        val ok = TrustedNetworkGate.trustCurrentNetwork(context)
                        state.bumpRefresh()
                        Toast.makeText(
                            context,
                            context.getString(
                                if (ok) R.string.parent_speakers_trusted_toast
                                else R.string.parent_speakers_connect_first_toast,
                            ),
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                    colors = m3FilledButtonColors(palette),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.parent_speakers_trust_button),
                        fontFamily = palette.fontFamily,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        if (lanFingerprints.isNotEmpty() || ssids.isNotEmpty()) {
            row {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.parent_speakers_fingerprints_header),
                        color = palette.muted,
                        fontFamily = palette.fontFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        lanFingerprints.sorted().forEach { fp ->
                            val label = TrustedNetworkGate.displayLabelForFingerprint(context, fp)
                            M3RemovableChip(label = label, palette = palette) {
                                TrustedNetworkGate.removeTrustedLanFingerprint(context, fp)
                                state.bumpRefresh()
                            }
                        }
                        ssids.sorted().forEach { ssid ->
                            val displaySsid = TrustedNetworkGate.displayLabelForSsid(context, ssid)
                            M3RemovableChip(label = "Wi-Fi: $displaySsid", palette = palette) {
                                TrustedNetworkGate.removeTrustedSsid(context, ssid)
                                state.bumpRefresh()
                            }
                        }
                    }
                }
            }
        }
    }

    m3SettingsSection(
        key = "parent_speakers:visibility-mode",
        palette = palette,
        header = context.getString(R.string.parent_speakers_mode_header),
    ) {
        row {
            M3SettingsChoiceRow(
                title = stringResource(R.string.parent_speakers_mode_all_except_hidden),
                selected = visibilityMode == SpeakerVisibilityMode.ALLOW_ALL_EXCEPT_HIDDEN,
                palette = palette,
                onClick = {
                    TrustedNetworkGate.setSpeakerVisibilityMode(context, SpeakerVisibilityMode.ALLOW_ALL_EXCEPT_HIDDEN)
                    state.bumpRefresh()
                },
            )
        }
        row {
            M3SettingsChoiceRow(
                title = stringResource(R.string.parent_speakers_mode_only_allowed),
                selected = visibilityMode == SpeakerVisibilityMode.ONLY_ALLOWED,
                palette = palette,
                onClick = {
                    TrustedNetworkGate.setSpeakerVisibilityMode(context, SpeakerVisibilityMode.ONLY_ALLOWED)
                    state.bumpRefresh()
                },
            )
        }
    }

    m3SettingsSection(
        key = "parent_speakers:devices",
        palette = palette,
        header = context.getString(R.string.parent_speakers_section_devices),
    ) {
        if (state.knownSpeakers.isEmpty()) {
            row {
                Text(
                    text = stringResource(R.string.parent_speakers_empty_hint),
                    color = palette.muted,
                    fontFamily = palette.fontFamily,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            state.knownSpeakers.forEach { (id, label) ->
                row {
                    val isHidden = id in hiddenIds
                    val isAllowed = id in allowedIds
                    val speakerUsable = if (visibilityMode == SpeakerVisibilityMode.ONLY_ALLOWED) isAllowed else !isHidden
                    val statusLabel = if (visibilityMode == SpeakerVisibilityMode.ONLY_ALLOWED) {
                        if (speakerUsable) stringResource(R.string.parent_speakers_status_allowed)
                        else stringResource(R.string.parent_speakers_status_blocked)
                    } else {
                        if (speakerUsable) stringResource(R.string.parent_speakers_status_visible)
                        else stringResource(R.string.parent_speakers_status_hidden)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = label,
                                color = palette.text,
                                fontFamily = palette.fontFamily,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = id,
                                color = palette.muted,
                                fontFamily = palette.fontFamily,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = {
                            state.editingSpeakerId = id
                            state.editingSpeakerName = customNames[id].orEmpty()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = stringResource(R.string.parent_speakers_rename_action),
                                tint = palette.muted,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Text(
                            text = statusLabel,
                            color = if (speakerUsable) palette.accent else palette.muted,
                            fontFamily = palette.fontFamily,
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
                                state.bumpRefresh()
                            },
                            colors = m3SwitchColors(palette),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SpeakersNicknameDialogHost(
    state: SpeakersParentUiState,
    palette: M3SettingsPalette,
) {
    val context = LocalContext.current
    val speakerId = state.editingSpeakerId ?: return
    LookAlertDialog(
        onDismissRequest = { state.editingSpeakerId = null },
        containerColor = palette.dialogBg,
        title = {
            Text(
                text = stringResource(R.string.parent_speaker_nickname_title),
                color = palette.text,
                fontFamily = palette.fontFamily,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            OutlinedTextField(
                value = state.editingSpeakerName,
                onValueChange = { state.editingSpeakerName = it },
                label = {
                    Text(
                        text = stringResource(R.string.parent_speaker_nickname_hint),
                        fontFamily = palette.fontFamily,
                    )
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = palette.text,
                    unfocusedTextColor = palette.text,
                    focusedBorderColor = palette.accent,
                    unfocusedBorderColor = palette.divider,
                    focusedLabelColor = palette.accent,
                    unfocusedLabelColor = palette.muted,
                    cursorColor = palette.accent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                colors = ButtonDefaults.textButtonColors(
                    contentColor = palette.accent,
                    disabledContentColor = palette.muted.copy(alpha = 0.45f),
                ),
                onClick = {
                    CustomSpeakerNames.setCustomName(context, speakerId, state.editingSpeakerName)
                    state.editingSpeakerId = null
                    state.bumpRefresh()
                },
            ) {
                Text(
                    text = stringResource(R.string.parent_pin_save),
                    fontFamily = palette.fontFamily,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { state.editingSpeakerId = null }) {
                Text(
                    text = stringResource(R.string.cancel),
                    color = palette.muted,
                    fontFamily = palette.fontFamily,
                )
            }
        },
    )
}
