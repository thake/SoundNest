/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.parental.remote.ui

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.ui.look.LookAlertDialog
import io.github.thake.soundnest.core.ui.settings.M3SettingsPalette
import io.github.thake.soundnest.core.ui.settings.m3FilledButtonColors
import io.github.thake.soundnest.core.ui.settings.m3FilterChipColors
import io.github.thake.soundnest.feature.parental.remote.net.DiscoveredChildEndpoint
import io.github.thake.soundnest.feature.parental.remote.net.PairingUri
import io.github.thake.soundnest.feature.parental.remote.net.ParentLanDiscovery
import io.github.thake.soundnest.feature.parental.remote.net.ParentRemoteClient
import io.github.thake.soundnest.feature.parental.remote.settings.PairedChildRecord
import kotlinx.coroutines.launch

@Composable
fun PairChildDialog(
    palette: M3SettingsPalette,
    preselected: DiscoveredChildEndpoint? = null,
    onDismiss: () -> Unit,
    onPaired: (PairedChildRecord) -> Unit,
    onChildrenImported: (List<PairedChildRecord>) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val discoveredMap by ParentLanDiscovery.discovered.collectAsState()
    val discoveredList = remember(discoveredMap) {
        discoveredMap.values.sortedWith(compareByDescending<DiscoveredChildEndpoint> { it.pairingOpen }.thenBy { it.deviceName })
    }

    var selectedHost by remember(preselected) { mutableStateOf(preselected?.host.orEmpty()) }
    var selectedPort by remember(preselected) { mutableStateOf(preselected?.port?.toString().orEmpty()) }
    var selectedName by remember(preselected) { mutableStateOf(preselected?.deviceName.orEmpty()) }
    var code by remember { mutableStateOf("") }
    var showManual by remember(preselected) { mutableStateOf(preselected != null) }
    var manualParentTransfer by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    fun submitPairing(host: String, port: Int, pairingCode: String) {
        if (busy) return
        busy = true
        errorMsg = null
        scope.launch {
            val res = ParentRemoteClient.pairWithChild(context, host, port, pairingCode)
            busy = false
            res.onSuccess { record ->
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        context,
                        context.getString(R.string.parent_hub_pairing_success, record.displayName),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                onPaired(record)
            }.onFailure { e ->
                errorMsg = context.getString(R.string.parent_hub_pairing_failed, e.message ?: "Error")
            }
        }
    }

    fun submitParentTransfer(host: String, port: Int, transferCode: String) {
        if (busy) return
        busy = true
        errorMsg = null
        scope.launch {
            val res = ParentRemoteClient.receiveParentTransfer(context, host, port, transferCode)
            busy = false
            res.onSuccess { result ->
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        context,
                        context.getString(
                            R.string.parent_transfer_imported_toast,
                            result.importedChildren.size,
                            result.sourceParentName,
                        ),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                onChildrenImported(result.importedChildren)
            }.onFailure { e ->
                errorMsg = context.getString(R.string.parent_hub_pairing_failed, e.message ?: "Error")
            }
        }
    }

    LookAlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = palette.dialogBg,
        title = {
            Text(
                text = stringResource(R.string.parent_hub_pair_child),
                color = palette.text,
                fontFamily = palette.fontFamily,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = {
                        errorMsg = null
                        runCatching {
                            GmsBarcodeScanning.getClient(context).startScan()
                                .addOnSuccessListener { barcode ->
                                    val raw = barcode.rawValue.orEmpty()
                                    val childInvite = PairingUri.parse(raw)
                                    val parentInvite = PairingUri.parseParentTransfer(raw)
                                    when {
                                        childInvite != null -> submitPairing(childInvite.host, childInvite.port, childInvite.code)
                                        parentInvite != null -> submitParentTransfer(parentInvite.host, parentInvite.port, parentInvite.code)
                                        else -> errorMsg = context.getString(
                                            R.string.parent_hub_pairing_failed,
                                            "Not a valid SoundNest pairing QR code",
                                        )
                                    }
                                }
                                .addOnFailureListener {
                                    errorMsg = context.getString(R.string.parent_hub_scan_unavailable)
                                    showManual = true
                                }
                        }.onFailure {
                            errorMsg = context.getString(R.string.parent_hub_scan_unavailable)
                            showManual = true
                        }
                    },
                    enabled = !busy,
                    colors = m3FilledButtonColors(palette),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pair_child_scan_qr"),
                ) {
                    Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.parent_hub_scan_qr), fontWeight = FontWeight.Bold)
                }

                if (discoveredList.isNotEmpty()) {
                    HorizontalDivider(color = palette.divider)
                    Text(
                        text = stringResource(R.string.parent_hub_discovered_pairing),
                        color = palette.muted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    discoveredList.forEach { ep ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !busy) {
                                    selectedHost = ep.host
                                    selectedPort = ep.port.toString()
                                    selectedName = ep.deviceName
                                    showManual = true
                                    errorMsg = null
                                }
                                .padding(vertical = 8.dp),
                        ) {
                            Icon(Icons.Rounded.Devices, contentDescription = null, tint = palette.accent, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(ep.deviceName, color = palette.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    text = "${ep.host}:${ep.port}" + if (ep.pairingOpen) " · Ready" else "",
                                    color = if (ep.pairingOpen) palette.accent else palette.muted,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }
                }

                if (!showManual) {
                    OutlinedButton(
                        onClick = { showManual = true },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.parent_hub_manual_address), color = palette.text)
                    }
                } else {
                    HorizontalDivider(color = palette.divider)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !manualParentTransfer,
                            onClick = { manualParentTransfer = false; errorMsg = null },
                            label = { Text(stringResource(R.string.parent_transfer_mode_child), fontSize = 12.sp) },
                            colors = m3FilterChipColors(palette),
                            modifier = Modifier.testTag("pair_mode_child"),
                        )
                        FilterChip(
                            selected = manualParentTransfer,
                            onClick = { manualParentTransfer = true; selectedName = ""; errorMsg = null },
                            label = { Text(stringResource(R.string.parent_transfer_mode_parent), fontSize = 12.sp) },
                            colors = m3FilterChipColors(palette),
                            modifier = Modifier.testTag("pair_mode_parent"),
                        )
                    }
                    if (selectedName.isNotBlank()) {
                        Text(
                            text = selectedName,
                            color = palette.accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    OutlinedTextField(
                        value = selectedHost,
                        onValueChange = { selectedHost = it.trim(); errorMsg = null },
                        label = { Text(stringResource(R.string.parent_hub_manual_host_hint)) },
                        singleLine = true,
                        enabled = !busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pair_child_host_input"),
                    )
                    OutlinedTextField(
                        value = selectedPort,
                        onValueChange = { selectedPort = it.filter { c -> c.isDigit() }.take(5); errorMsg = null },
                        label = { Text(stringResource(R.string.parent_hub_manual_port_hint)) },
                        singleLine = true,
                        enabled = !busy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pair_child_port_input"),
                    )
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.filter { c -> c.isDigit() }.take(6); errorMsg = null },
                        label = { Text(stringResource(R.string.parent_hub_enter_code_hint)) },
                        singleLine = true,
                        enabled = !busy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pair_child_code_input"),
                    )
                }

                if (busy) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = palette.accent, strokeWidth = 2.dp)
                    }
                }

                errorMsg?.let { msg ->
                    Text(
                        text = msg,
                        color = androidx.compose.ui.graphics.Color(0xFFEF5350),
                        fontSize = 13.sp,
                    )
                }
            }
        },
        confirmButton = {
            if (showManual) {
                Button(
                    onClick = {
                        val portNum = selectedPort.toIntOrNull()
                        if (selectedHost.isBlank() || portNum == null || portNum !in 1..65535 || code.length != 6) {
                            errorMsg = context.getString(R.string.parent_hub_pairing_failed, "Check IP, port, and 6-digit code")
                        } else if (manualParentTransfer) {
                            submitParentTransfer(selectedHost, portNum, code)
                        } else {
                            submitPairing(selectedHost, portNum, code)
                        }
                    },
                    enabled = !busy && selectedHost.isNotBlank() && selectedPort.isNotBlank() && code.length == 6,
                    colors = m3FilledButtonColors(palette),
                    modifier = Modifier.testTag("pair_child_confirm"),
                ) {
                    Text(stringResource(R.string.parent_hub_pair_action), fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text(stringResource(android.R.string.cancel), color = palette.muted)
            }
        },
    )
}
