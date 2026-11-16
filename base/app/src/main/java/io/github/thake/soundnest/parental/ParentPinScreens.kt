/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.parental

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.thake.soundnest.ui.theme.AppAccent
import io.github.thake.soundnest.ui.theme.AppBackground
import io.github.thake.soundnest.ui.theme.AppMuted
import io.github.thake.soundnest.ui.theme.AppText
import io.github.thake.soundnest.ui.theme.tone
import kotlinx.coroutines.delay

/**
 * Mandatory fail-closed onboarding step (`Entry.PARENT_SETUP`): blocks access to the app until
 * a parent configures a 6+ digit PIN.
 */
@Composable
fun ParentPinSetupScreen(
    onPinConfigured: () -> Unit,
    onSwitchOrLogout: () -> Unit,
) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    val biometricSupported = remember(context) { ParentSecurityManager.isDeviceBiometricAvailable(context) }
    var enableBiometric by remember { mutableStateOf(biometricSupported) }
    val onWifi = remember(context) { TrustedNetworkGate.currentSnapshot(context).onWifi }
    var trustCurrentWifi by remember { mutableStateOf(onWifi) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = AppAccent,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = "Set Up Parent PIN",
            color = AppText,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "SoundNest requires a 6+ digit Parent PIN before handing this phone to your child. " +
                "Content filters, Home page sections, Home Wi-Fi speakers, and account changes stay locked behind your PIN.",
            color = AppMuted,
            fontSize = 14.sp,
        )

        OutlinedTextField(
            value = pin,
            onValueChange = { next ->
                pin = next.filter { it in '0'..'9' }.take(16)
                errorMessage = null
            },
            label = { Text("Parent PIN (6+ digits)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = confirmPin,
            onValueChange = { next ->
                confirmPin = next.filter { it in '0'..'9' }.take(16)
                errorMessage = null
            },
            label = { Text("Confirm Parent PIN") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )

        if (biometricSupported) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { enableBiometric = !enableBiometric }
                    .padding(vertical = 4.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Allow Fingerprint / Face Unlock", color = AppText, fontSize = 15.sp)
                    Text("Quickly unlock Parent Mode for 5 minutes", color = AppMuted, fontSize = 12.sp)
                }
                Switch(checked = enableBiometric, onCheckedChange = { enableBiometric = it })
            }
        }

        if (onWifi) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { trustCurrentWifi = !trustCurrentWifi }
                    .padding(vertical = 4.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Trust Current Wi-Fi as Home Network", color = AppText, fontSize = 15.sp)
                    Text("Allows Alexa & Spotify Connect speakers on this Wi-Fi", color = AppMuted, fontSize = 12.sp)
                }
                Switch(checked = trustCurrentWifi, onCheckedChange = { trustCurrentWifi = it })
            }
        }

        errorMessage?.let { msg ->
            Text(text = msg, color = Color(0xFFFF6B6B), fontSize = 13.sp)
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                when {
                    !ParentSecurityManager.isValidPinFormat(pin) ->
                        errorMessage = "Enter a numeric PIN with at least 6 digits."
                    pin != confirmPin ->
                        errorMessage = "PINs do not match."
                    else -> {
                        when (ParentSecurityManager.setupInitialPin(context, pin)) {
                            PinSetupResult.Success, PinSetupResult.AlreadyConfigured -> {
                                ParentSecurityManager.setBiometricEnabled(context, biometricSupported && enableBiometric)
                                if (trustCurrentWifi) {
                                    TrustedNetworkGate.trustCurrentNetwork(context)
                                }
                                onPinConfigured()
                            }
                            PinSetupResult.InvalidFormat ->
                                errorMessage = "Enter a numeric PIN with at least 6 digits."
                        }
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = AppAccent, contentColor = Color.Black),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) {
            Text("Save Parent PIN & Continue", fontWeight = FontWeight.Bold)
        }

        TextButton(
            onClick = onSwitchOrLogout,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text("Sign out / Use a different Spotify account", color = AppMuted)
        }
    }
}

/**
 * Global Compose host that renders a Parent PIN / Biometric prompt whenever a protected surface
 * invokes [ParentSecurityManager.runProtected] while the 5-minute Admin Session is locked.
 */
@Composable
fun ParentPinPromptHost() {
    val context = LocalContext.current
    val request by ParentSecurityManager.pendingPrompt.collectAsState()
    val req = request ?: return

    var pin by remember(req.id) { mutableStateOf("") }
    var errorText by remember(req.id) { mutableStateOf<String?>(null) }
    var lockoutMs by remember(req.id) { mutableLongStateOf(ParentSecurityManager.lockoutRemainingMs(context)) }
    val canBiometric = remember(req.id, lockoutMs) {
        lockoutMs <= 0L &&
            ParentSecurityManager.isBiometricEnabled(context) &&
            ParentSecurityManager.isDeviceBiometricAvailable(context)
    }

    LaunchedEffect(req.id, lockoutMs) {
        if (lockoutMs > 0L) {
            delay(500L)
            lockoutMs = ParentSecurityManager.lockoutRemainingMs(context)
        }
    }

    AlertDialog(
        onDismissRequest = { ParentSecurityManager.dismissPrompt() },
        containerColor = tone(0xFF282828),
        title = {
            Text("Parent PIN Required", color = AppText, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = surfaceDescription(req.surface),
                    color = AppMuted,
                    fontSize = 13.sp,
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { next ->
                        pin = next.filter { it in '0'..'9' }.take(16)
                        errorText = null
                    },
                    enabled = lockoutMs <= 0L,
                    label = { Text("6+ digit Parent PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (lockoutMs > 0L) {
                    val secs = ((lockoutMs + 999L) / 1000L).coerceAtLeast(1L)
                    Text(
                        text = "Too many wrong attempts. Try again in ${secs}s.",
                        color = Color(0xFFFF6B6B),
                        fontSize = 13.sp,
                    )
                } else {
                    errorText?.let { msg ->
                        Text(text = msg, color = Color(0xFFFF6B6B), fontSize = 13.sp)
                    }
                }
                if (canBiometric) {
                    OutlinedButton(
                        onClick = { launchSystemBiometric(context) { msg -> errorText = msg } },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null, tint = AppAccent)
                        Text("  Unlock with Biometrics", color = AppText)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = lockoutMs <= 0L && pin.length >= ParentSecurityManager.MIN_PIN_LENGTH,
                onClick = {
                    when (val res = ParentSecurityManager.submitPromptPin(context, pin)) {
                        PinVerifyResult.Verified -> Unit
                        is PinVerifyResult.LockedOut -> {
                            pin = ""
                            lockoutMs = res.remainingMs
                        }
                        is PinVerifyResult.InvalidPin -> {
                            pin = ""
                            errorText = "Incorrect PIN (${res.remainingBeforeCooldown} tries before cooldown)."
                        }
                        PinVerifyResult.NotConfigured -> ParentSecurityManager.dismissPrompt()
                    }
                },
            ) {
                Text("Unlock", color = AppAccent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = { ParentSecurityManager.dismissPrompt() }) {
                Text("Cancel", color = AppMuted)
            }
        },
    )
}

private fun surfaceDescription(surface: AppSurface): String = when (surface) {
    AppSurface.PARENT_CONTROLS -> "Enter your Parent PIN to open Parental Controls (unlocked for 5 minutes)."
    AppSurface.ACCOUNTS_AND_LOGOUT -> "Enter your Parent PIN to manage Spotify accounts or sign out."
    AppSurface.HIDDEN_SONGS_AND_BANS -> "Enter your Parent PIN to change hidden songs or artist bans."
    AppSurface.DIAGNOSTIC_EXPORT -> "Enter your Parent PIN to export diagnostic logs."
    AppSurface.KID_APPEARANCE, AppSurface.KID_AUDIO, AppSurface.KID_SLEEP_TIMER -> ""
}

private fun Context.findActivity(): Activity? =
    generateSequence(this) { (it as? ContextWrapper)?.baseContext }
        .filterIsInstance<Activity>()
        .firstOrNull()

private fun launchSystemBiometric(context: Context, onError: (String) -> Unit) {
    val activity = context.findActivity() ?: run {
        onError("Biometric prompt requires an active window.")
        return
    }
    runCatching {
        val prompt = BiometricPrompt.Builder(activity)
            .setTitle("Unlock Parent Mode")
            .setSubtitle("Authenticate to unlock Parental Controls for 5 minutes")
            .setNegativeButton("Use PIN", activity.mainExecutor) { _, _ -> }
            .build()
        prompt.authenticate(
            CancellationSignal(),
            activity.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                    if (!ParentSecurityManager.onBiometricSucceeded(context)) {
                        onError("Please enter your Parent PIN.")
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                    if (!errString.isNullOrBlank()) onError(errString.toString())
                }
            },
        )
    }.onFailure {
        onError("Biometric authentication unavailable; please use your PIN.")
    }
}

/**
 * Reusable Parent Security & Trusted Network controls block (embedded in Settings and `ParentSettingsScreen`).
 */
@Composable
fun ParentSecurityControlsSection(onOpenParentSettings: (() -> Unit)? = null) {
    val context = LocalContext.current
    val tick by ParentSecurityManager.sessionTick.collectAsState()
    val unlocked = remember(tick) { ParentSecurityManager.isAdminUnlocked(context) }
    var biometricEnabled by remember(tick) { mutableStateOf(ParentSecurityManager.isBiometricEnabled(context)) }
    val biometricSupported = remember(context) { ParentSecurityManager.isDeviceBiometricAvailable(context) }
    var requireTrustedWifi by remember(tick) { mutableStateOf(TrustedNetworkGate.isRequireTrustedNetwork(context)) }
    var trustedSsids by remember(tick) { mutableStateOf(TrustedNetworkGate.trustedSsids(context)) }
    var showChangePin by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (unlocked) "Parent Mode Active (5-min auto-lock)" else "Parent Mode Locked",
                    color = if (unlocked) AppAccent else AppText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (unlocked) "Tap Lock Now before handing the phone back to your child."
                    else "Tap Unlock to edit filters, Home sections, speakers, or accounts.",
                    color = AppMuted,
                    fontSize = 12.sp,
                )
            }
            if (unlocked) {
                OutlinedButton(onClick = { ParentSecurityManager.lockNow(context) }) {
                    Text("Lock Now", color = AppText)
                }
            } else {
                Button(
                    onClick = { ParentSecurityManager.runProtected(context, AppSurface.PARENT_CONTROLS) {} },
                    colors = ButtonDefaults.buttonColors(containerColor = AppAccent, contentColor = Color.Black),
                ) {
                    Text("Unlock")
                }
            }
        }

        if (onOpenParentSettings != null) {
            Button(
                onClick = {
                    ParentSecurityManager.runProtected(context, AppSurface.PARENT_CONTROLS) {
                        onOpenParentSettings()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AppAccent, contentColor = Color.Black),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Open Parental Controls Dashboard", fontWeight = FontWeight.Bold)
            }
        }

        if (unlocked) {
            if (biometricSupported) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("Biometric Unlock", color = AppText, fontSize = 15.sp)
                        Text("Allow fingerprint or face unlock for Parent Mode", color = AppMuted, fontSize = 12.sp)
                    }
                    Switch(
                        checked = biometricEnabled,
                        onCheckedChange = {
                            biometricEnabled = it
                            ParentSecurityManager.setBiometricEnabled(context, it)
                        },
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Require Trusted Home Wi-Fi for Speakers", color = AppText, fontSize = 15.sp)
                    val summary = if (trustedSsids.isEmpty()) "No Home Wi-Fi saved yet (remote speakers hidden)"
                    else "Trusted Wi-Fi: ${trustedSsids.joinToString(", ")}"
                    Text(summary, color = AppMuted, fontSize = 12.sp)
                }
                Switch(
                    checked = requireTrustedWifi,
                    onCheckedChange = {
                        requireTrustedWifi = it
                        TrustedNetworkGate.setRequireTrustedNetwork(context, it)
                    },
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    TrustedNetworkGate.trustCurrentNetwork(context)
                    trustedSsids = TrustedNetworkGate.trustedSsids(context)
                }) {
                    Text("Trust Current Wi-Fi", color = AppText)
                }
                OutlinedButton(onClick = { showChangePin = true }) {
                    Text("Change Parent PIN", color = AppText)
                }
            }
        }
    }

    if (showChangePin) {
        ChangeParentPinDialog(onDismiss = { showChangePin = false })
    }
}

@Composable
private fun ChangeParentPinDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = tone(0xFF282828),
        title = { Text("Change Parent PIN", color = AppText, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = currentPin,
                    onValueChange = { currentPin = it.filter { c -> c in '0'..'9' }.take(16); errorText = null },
                    label = { Text("Current PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { newPin = it.filter { c -> c in '0'..'9' }.take(16); errorText = null },
                    label = { Text("New PIN (6+ digits)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { confirmPin = it.filter { c -> c in '0'..'9' }.take(16); errorText = null },
                    label = { Text("Confirm New PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                errorText?.let { Text(it, color = Color(0xFFFF6B6B), fontSize = 13.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (newPin != confirmPin) {
                    errorText = "New PINs do not match."
                    return@TextButton
                }
                when (ParentSecurityManager.changePin(context, currentPin, newPin)) {
                    PinChangeResult.Success -> onDismiss()
                    PinChangeResult.InvalidCurrentPin -> errorText = "Current PIN is incorrect."
                    PinChangeResult.InvalidNewPinFormat -> errorText = "New PIN must be at least 6 digits."
                    PinChangeResult.LockedOut -> errorText = "Too many wrong attempts; please wait."
                }
            }) {
                Text("Save", color = AppAccent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = AppMuted) }
        },
    )
}
