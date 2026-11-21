/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.parental.remote.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.ui.immersive.shell.ImmersiveBarAction
import io.github.thake.soundnest.core.ui.settings.M3GroupCard
import io.github.thake.soundnest.core.ui.settings.M3SettingsNavRow
import io.github.thake.soundnest.core.ui.settings.M3SettingsScaffold
import io.github.thake.soundnest.core.ui.settings.M3SettingsSwitchRow
import io.github.thake.soundnest.core.ui.settings.m3FilledButtonColors
import io.github.thake.soundnest.core.ui.settings.m3SettingsBlock
import io.github.thake.soundnest.core.ui.settings.m3SettingsSection
import io.github.thake.soundnest.feature.parental.remote.net.ChildRemoteServer
import io.github.thake.soundnest.feature.parental.remote.settings.DeviceRoleStore
import io.github.thake.soundnest.feature.parental.remote.settings.PairedDevicesStore
import kotlinx.coroutines.delay

/**
 * Child-side Family Remote Control management page inside Parental Controls Dashboard
 * (requires Parent PIN / Admin Session).
 */
@Composable
fun ChildFamilyRemotePage(
    context: Context,
    listState: LazyListState,
    back: () -> Unit,
    lockAndExit: () -> Unit,
    onOpenParentHub: () -> Unit,
) {
    LaunchedEffect(Unit) {
        DeviceRoleStore.init(context)
        PairedDevicesStore.init(context)
    }

    val pairedParents by PairedDevicesStore.pairedParentsFlow.collectAsState()
    val remoteEnabled by PairedDevicesStore.remoteControlEnabledFlow.collectAsState()
    val pairingSession by ChildRemoteServer.pairingSessionFlow.collectAsState()
    val savedName by DeviceRoleStore.deviceNameFlow.collectAsState()

    var nameInput by remember(savedName) { mutableStateOf(savedName) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(pairingSession) {
        while (pairingSession != null) {
            nowMs = System.currentTimeMillis()
            if (nowMs >= pairingSession!!.expiresAtMs) {
                ChildRemoteServer.cancelPairingSession(context)
                break
            }
            delay(1_000L)
        }
    }

    M3SettingsScaffold(
        title = stringResource(R.string.parent_remote_section_title),
        onBack = back,
        listState = listState,
        listTestTag = "parent_family_remote_list",
        immersiveActions = {
            ImmersiveBarAction(
                icon = Icons.Rounded.Lock,
                contentDescription = stringResource(R.string.parent_pin_lock),
                onClick = lockAndExit,
            )
        },
    ) { scaffoldPalette ->
        // 1. Remote Control Toggle & Device Name
        m3SettingsBlock(key = "family:settings", palette = scaffoldPalette) {
            M3GroupCard(palette = scaffoldPalette) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    M3SettingsSwitchRow(
                        title = stringResource(R.string.parent_remote_enable_title),
                        detail = stringResource(R.string.parent_remote_enable_desc),
                        checked = remoteEnabled,
                        palette = scaffoldPalette,
                        onCheckedChange = { enabled ->
                            PairedDevicesStore.setRemoteControlEnabled(context, enabled)
                            ChildRemoteServer.syncLifecycle(context)
                        },
                    )
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it.take(40) },
                        label = { Text(stringResource(R.string.parent_remote_device_name_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (nameInput.trim() != savedName && nameInput.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                DeviceRoleStore.setDeviceName(context, nameInput)
                                ChildRemoteServer.syncLifecycle(context)
                            },
                        ) {
                            Text(stringResource(R.string.parent_remote_save_name), color = scaffoldPalette.text)
                        }
                    }
                }
            }
        }

        // 2. Pair a Parent Device (QR + 6-digit code)
        m3SettingsBlock(key = "family:pairing", palette = scaffoldPalette) {
            M3GroupCard(palette = scaffoldPalette) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    val active = pairingSession
                    if (active == null) {
                        Button(
                            onClick = {
                                ChildRemoteServer.startPairingSession(context).onFailure { e ->
                                    Toast.makeText(context, e.message ?: "Could not start pairing", Toast.LENGTH_LONG).show()
                                }
                            },
                            colors = m3FilledButtonColors(scaffoldPalette),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("child_start_pairing_btn"),
                        ) {
                            Icon(Icons.Rounded.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.parent_remote_start_pairing), fontWeight = FontWeight.Bold)
                        }
                    } else {
                        QrCodeCanvas(
                            content = active.inviteUri,
                            modifier = Modifier
                                .size(200.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .padding(12.dp),
                        )
                        val formattedCode = active.code.chunked(3).joinToString(" ")
                        Text(
                            text = formattedCode,
                            color = scaffoldPalette.text,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 4.sp,
                            modifier = Modifier.testTag("child_pairing_code_text"),
                        )
                        Text(
                            text = stringResource(R.string.parent_remote_pairing_address, active.host, active.port),
                            color = scaffoldPalette.muted,
                            fontSize = 12.sp,
                        )
                        val remainingSec = ((active.expiresAtMs - nowMs) / 1_000L).coerceAtLeast(0L)
                        val countdown = "%d:%02d".format(remainingSec / 60, remainingSec % 60)
                        Text(
                            text = stringResource(R.string.parent_remote_pairing_expires, countdown),
                            color = scaffoldPalette.accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        OutlinedButton(
                            onClick = { ChildRemoteServer.cancelPairingSession(context) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.parent_remote_cancel_pairing), color = scaffoldPalette.muted)
                        }
                    }
                }
            }
        }

        // 3. Paired Parent Devices List
        m3SettingsBlock(
            key = "family:paired-parents",
            palette = scaffoldPalette,
        ) {
            Text(
                text = stringResource(R.string.parent_remote_paired_parents),
                color = scaffoldPalette.muted,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            )
            M3GroupCard(palette = scaffoldPalette) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (pairedParents.isEmpty()) {
                        Text(
                            text = stringResource(R.string.parent_remote_no_parents),
                            color = scaffoldPalette.muted,
                            fontSize = 13.sp,
                        )
                    } else {
                        pairedParents.forEach { parent ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Rounded.Devices, contentDescription = null, tint = scaffoldPalette.accent)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = parent.parentName,
                                    color = scaffoldPalette.text,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(
                                    onClick = {
                                        PairedDevicesStore.revokePairedParent(context, parent.parentId)
                                        ChildRemoteServer.syncLifecycle(context)
                                    },
                                    modifier = Modifier.testTag("revoke_parent_${parent.parentId}"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = stringResource(R.string.parent_remote_revoke),
                                        tint = Color(0xFFEF5350),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Dual-use: Open Parent Controller Hub from a signed-in SoundNest install
        m3SettingsSection(
            key = "family:controller-hub",
            palette = scaffoldPalette,
            hasLeadingIconBadges = true,
        ) {
            row {
                M3SettingsNavRow(
                    icon = Icons.Rounded.Devices,
                    title = stringResource(R.string.parent_remote_open_hub_title),
                    detail = stringResource(R.string.parent_remote_open_hub_desc),
                    palette = scaffoldPalette,
                    testTag = "parent_open_controller_hub",
                    onClick = onOpenParentHub,
                )
            }
        }
    }
}

@Composable
internal fun QrCodeCanvas(
    content: String,
    modifier: Modifier = Modifier,
) {
    val matrix = remember(content) {
        runCatching {
            val hints = mapOf(EncodeHintType.MARGIN to 0)
            QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, hints)
        }.getOrNull()
    }
    Canvas(modifier = modifier) {
        val m = matrix ?: return@Canvas
        val w = m.width
        val h = m.height
        if (w <= 0 || h <= 0) return@Canvas
        val cellW = size.width / w
        val cellH = size.height / h
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (m.get(x, y)) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(x * cellW, y * cellH),
                        size = Size(cellW + 0.5f, cellH + 0.5f),
                    )
                }
            }
        }
    }
}
