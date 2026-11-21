/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.parental.remote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.ui.look.LookAlertDialog
import io.github.thake.soundnest.core.ui.settings.M3SettingsPalette
import io.github.thake.soundnest.core.ui.settings.m3FilledButtonColors
import io.github.thake.soundnest.feature.parental.remote.net.ParentTransferServer
import kotlinx.coroutines.delay
import java.net.InetAddress

/**
 * Dialog displayed on an already-paired Parent Device to onboard a second Parent Device
 * and transfer all paired children over encrypted home Wi-Fi.
 */
@Composable
fun AddSecondParentDialog(
    palette: M3SettingsPalette,
    bindAddress: InetAddress? = null,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val session by ParentTransferServer.sessionFlow.collectAsState()
    val completed by ParentTransferServer.completedFlow.collectAsState()
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    DisposableEffect(Unit) {
        val res = ParentTransferServer.startTransferSession(context, bindAddress = bindAddress)
        res.onFailure { e -> errorMsg = e.message ?: "Failed to start transfer" }
        onDispose {
            ParentTransferServer.stop()
        }
    }

    LaunchedEffect(session) {
        while (session != null) {
            nowMs = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    LookAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.dialogBg,
        title = {
            Text(
                text = stringResource(R.string.parent_transfer_title),
                color = palette.text,
                fontFamily = palette.fontFamily,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                val done = completed
                val active = session
                when {
                    done != null -> {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = palette.accent,
                            modifier = Modifier.size(48.dp),
                        )
                        Text(
                            text = stringResource(
                                R.string.parent_transfer_complete_msg,
                                done.transferredCount,
                                done.targetParentName,
                            ),
                            color = palette.text,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.testTag("parent_transfer_complete_text"),
                        )
                    }
                    errorMsg != null -> {
                        Text(
                            text = errorMsg!!,
                            color = Color(0xFFEF5350),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                    active != null -> {
                        Text(
                            text = stringResource(R.string.parent_transfer_desc, active.childCount),
                            color = palette.muted,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                        )
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(210.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White)
                                .padding(12.dp),
                        ) {
                            QrCodeCanvas(
                                content = active.inviteUri,
                                modifier = Modifier.size(186.dp),
                            )
                        }
                        val formattedCode = active.code.chunked(3).joinToString("  ")
                        Text(
                            text = formattedCode,
                            color = palette.text,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 3.sp,
                            modifier = Modifier.testTag("parent_transfer_code_text"),
                        )
                        Text(
                            text = stringResource(R.string.parent_remote_pairing_address, active.host, active.port),
                            color = palette.muted,
                            fontSize = 12.sp,
                        )
                        val remainingSec = ((active.expiresAtMs - nowMs) / 1000L).coerceAtLeast(0L)
                        val mins = remainingSec / 60L
                        val secs = remainingSec % 60L
                        Text(
                            text = stringResource(
                                R.string.parent_remote_pairing_expires,
                                "$mins:${secs.toString().padStart(2, '0')}",
                            ),
                            color = palette.accent,
                            fontSize = 12.sp,
                        )
                    }
                    else -> {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.parent_transfer_expired),
                            color = palette.muted,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                        )
                        Button(
                            onClick = {
                                errorMsg = null
                                ParentTransferServer.startTransferSession(context, bindAddress = bindAddress)
                                    .onFailure { e -> errorMsg = e.message }
                            },
                            colors = m3FilledButtonColors(palette),
                        ) {
                            Text(stringResource(R.string.parent_transfer_restart), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(if (completed != null) android.R.string.ok else android.R.string.cancel),
                    color = palette.accent,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
    )
}
