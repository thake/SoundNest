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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.thake.soundnest.connect.LanDiscovery
import io.github.thake.soundnest.ui.theme.AppAccent
import io.github.thake.soundnest.ui.theme.AppBackground
import io.github.thake.soundnest.ui.theme.AppMuted
import io.github.thake.soundnest.ui.theme.AppText
import io.github.thake.soundnest.ui.theme.tone
import kotlinx.coroutines.delay

private fun sanitizeSecretInput(raw: String): String =
    raw.replace("\n", "").replace("\r", "").take(ParentSecurityManager.MAX_PIN_LENGTH)

private val SecretKeyboardOptions = KeyboardOptions(
    keyboardType = KeyboardType.Password,
    autoCorrectEnabled = false,
)

/**
 * Mandatory fail-closed onboarding step (`Entry.PARENT_SETUP`): blocks access to the app until
 * a parent configures a 4+ character PIN or password.
 */
@Composable
fun ParentPinSetupScreen(
    onPinConfigured: () -> Unit,
    onSwitchOrLogout: () -> Unit,
) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var showSecret by remember { mutableStateOf(false) }
    var initialFilterMode by remember(context) { mutableStateOf(ParentalPolicyStore.current(context).filterMode) }
    val biometricSupported = remember(context) { ParentSecurityManager.isDeviceBiometricAvailable(context) }
    var enableBiometric by remember { mutableStateOf(biometricSupported) }
    val snap = remember(context) { TrustedNetworkGate.currentSnapshot(context) }
    val onWifi = snap.onWifi
    var trustCurrentWifi by remember { mutableStateOf(onWifi) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    DisposableEffect(context, onWifi) {
        if (onWifi) runCatching { LanDiscovery.start(context) }
        onDispose {
            if (onWifi) runCatching { LanDiscovery.stop() }
        }
    }

    val transformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation()
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = AppAccent,
        selectedLabelColor = Color.Black,
        selectedLeadingIconColor = Color.Black,
    )

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
            text = "Set Up Parent PIN or Password",
            color = AppText,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "SoundNest requires a Parent PIN or password (4+ characters) before handing this phone to your child. " +
                "Content filters, Home page sections, Home Wi-Fi speakers, and account changes stay locked behind it.",
            color = AppMuted,
            fontSize = 14.sp,
        )

        OutlinedTextField(
            value = pin,
            onValueChange = { next ->
                pin = sanitizeSecretInput(next)
                errorMessage = null
            },
            label = { Text("Parent PIN or password (4+ chars)") },
            singleLine = true,
            visualTransformation = transformation,
            keyboardOptions = SecretKeyboardOptions,
            trailingIcon = {
                IconButton(onClick = { showSecret = !showSecret }) {
                    Icon(
                        imageVector = if (showSecret) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showSecret) "Hide PIN or password" else "Show PIN or password",
                        tint = AppMuted,
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("parent_setup_secret"),
        )

        OutlinedTextField(
            value = confirmPin,
            onValueChange = { next ->
                confirmPin = sanitizeSecretInput(next)
                errorMessage = null
            },
            label = { Text("Confirm PIN or password") },
            singleLine = true,
            visualTransformation = transformation,
            keyboardOptions = SecretKeyboardOptions,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("parent_setup_confirm"),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(tone(0xFF1E1E1E), RoundedCornerShape(14.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Initial Filtering Mode",
                color = AppText,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = initialFilterMode == FilterMode.FILTERED_OPEN,
                    onClick = { initialFilterMode = FilterMode.FILTERED_OPEN },
                    label = { Text("Filtered Open", fontWeight = if (initialFilterMode == FilterMode.FILTERED_OPEN) FontWeight.Bold else FontWeight.Normal) },
                    leadingIcon = if (initialFilterMode == FilterMode.FILTERED_OPEN) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else null,
                    colors = chipColors,
                    modifier = Modifier.testTag("parent_setup_mode_filtered_open"),
                )
                FilterChip(
                    selected = initialFilterMode == FilterMode.STRICT_ALLOWLIST,
                    onClick = { initialFilterMode = FilterMode.STRICT_ALLOWLIST },
                    label = { Text("Strict Allowlist", fontWeight = if (initialFilterMode == FilterMode.STRICT_ALLOWLIST) FontWeight.Bold else FontWeight.Normal) },
                    leadingIcon = if (initialFilterMode == FilterMode.STRICT_ALLOWLIST) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else null,
                    colors = chipColors,
                    modifier = Modifier.testTag("parent_setup_mode_strict_allowlist"),
                )
            }
            Text(
                text = if (initialFilterMode == FilterMode.FILTERED_OPEN) {
                    "Recommended for ages 9–16: open music catalog with explicit songs, podcasts, and blocked keywords hidden."
                } else {
                    "Recommended for younger kids: hides unapproved shelves and plays only playlists, albums, artists, and tracks you approve."
                },
                color = AppMuted,
                fontSize = 12.sp,
            )
        }

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
                    Text("Fingerprints this Home LAN & speakers (zero location permissions)", color = AppMuted, fontSize = 12.sp)
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
                        errorMessage = "Enter a PIN or password with at least 4 characters."
                    pin != confirmPin ->
                        errorMessage = "Entries do not match."
                    else -> {
                        when (ParentSecurityManager.setupInitialPin(context, pin)) {
                            PinSetupResult.Success, PinSetupResult.AlreadyConfigured -> {
                                ParentSecurityManager.setBiometricEnabled(context, biometricSupported && enableBiometric)
                                ParentalPolicyStore.update(context) { current ->
                                    val home = if (initialFilterMode == FilterMode.STRICT_ALLOWLIST) {
                                        current.homeSections.copy(minimalHomeMode = true)
                                    } else {
                                        current.homeSections
                                    }
                                    current.copy(filterMode = initialFilterMode, homeSections = home)
                                }
                                if (trustCurrentWifi) {
                                    TrustedNetworkGate.trustCurrentNetwork(context)
                                }
                                onPinConfigured()
                            }
                            PinSetupResult.InvalidFormat ->
                                errorMessage = "Enter a PIN or password with at least 4 characters."
                        }
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = AppAccent, contentColor = Color.Black),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("parent_setup_save"),
        ) {
            Text("Save & Continue", fontWeight = FontWeight.Bold)
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
 * Global Compose host that renders a Parent PIN / Password / Biometric prompt whenever a protected
 * surface invokes [ParentSecurityManager.runProtected] while the 5-minute Admin Session is locked.
 */
@Composable
fun ParentPinPromptHost() {
    val context = LocalContext.current
    val request by ParentSecurityManager.pendingPrompt.collectAsState()
    val req = request ?: return

    var pin by remember(req.id) { mutableStateOf("") }
    var showSecret by remember(req.id) { mutableStateOf(false) }
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
            Text("Parent PIN / Password Required", color = AppText, fontWeight = FontWeight.Bold)
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
                        pin = sanitizeSecretInput(next)
                        errorText = null
                    },
                    enabled = lockoutMs <= 0L,
                    label = { Text("Parent PIN or password") },
                    singleLine = true,
                    visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = SecretKeyboardOptions,
                    trailingIcon = {
                        IconButton(onClick = { showSecret = !showSecret }) {
                            Icon(
                                imageVector = if (showSecret) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showSecret) "Hide PIN or password" else "Show PIN or password",
                                tint = AppMuted,
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("parent_prompt_secret"),
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
                            errorText = "Incorrect PIN or password (${res.remainingBeforeCooldown} tries before cooldown)."
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
    AppSurface.PARENT_CONTROLS -> "Enter your Parent PIN or password to open Parental Controls (unlocked for 5 minutes)."
    AppSurface.ACCOUNTS_AND_LOGOUT -> "Enter your Parent PIN or password to manage Spotify accounts or sign out."
    AppSurface.HIDDEN_SONGS_AND_BANS -> "Enter your Parent PIN or password to change hidden songs or artist bans."
    AppSurface.DIAGNOSTIC_EXPORT -> "Enter your Parent PIN or password to export diagnostic logs."
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
            .setNegativeButton("Use PIN / Password", activity.mainExecutor) { _, _ -> }
            .build()
        prompt.authenticate(
            CancellationSignal(),
            activity.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                    if (!ParentSecurityManager.onBiometricSucceeded(context)) {
                        onError("Please enter your Parent PIN or password.")
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                    if (!errString.isNullOrBlank()) onError(errString.toString())
                }
            },
        )
    }.onFailure {
        onError("Biometric authentication unavailable; please use your PIN or password.")
    }
}

/**
 * Reusable Parent Security session & PIN controls block (embedded in Settings and `ParentSettingsScreen`).
 */
@Composable
fun ParentSecurityControlsSection(onOpenParentSettings: (() -> Unit)? = null) {
    val context = LocalContext.current
    val tick by ParentSecurityManager.sessionTick.collectAsState()
    val unlocked = remember(tick) { ParentSecurityManager.isAdminUnlocked(context) }
    var biometricEnabled by remember(tick) { mutableStateOf(ParentSecurityManager.isBiometricEnabled(context)) }
    val biometricSupported = remember(context) { ParentSecurityManager.isDeviceBiometricAvailable(context) }
    var showChangePin by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
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

            OutlinedButton(
                onClick = { showChangePin = true },
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Change Parent PIN / Password", color = AppText, fontSize = 13.sp, maxLines = 1)
            }
        }
    }

    if (showChangePin) {
        ChangeParentPinDialog(onDismiss = { showChangePin = false })
    }
}

@Composable
internal fun ChangeParentPinDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var showSecrets by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val transformation = if (showSecrets) VisualTransformation.None else PasswordVisualTransformation()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = tone(0xFF282828),
        title = { Text("Change Parent PIN / Password", color = AppText, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = currentPin,
                    onValueChange = { currentPin = sanitizeSecretInput(it); errorText = null },
                    label = { Text("Current PIN or password") },
                    singleLine = true,
                    visualTransformation = transformation,
                    keyboardOptions = SecretKeyboardOptions,
                    trailingIcon = {
                        IconButton(onClick = { showSecrets = !showSecrets }) {
                            Icon(
                                imageVector = if (showSecrets) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showSecrets) "Hide PIN or password" else "Show PIN or password",
                                tint = AppMuted,
                            )
                        }
                    },
                )
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { newPin = sanitizeSecretInput(it); errorText = null },
                    label = { Text("New PIN or password (4+ chars)") },
                    singleLine = true,
                    visualTransformation = transformation,
                    keyboardOptions = SecretKeyboardOptions,
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { confirmPin = sanitizeSecretInput(it); errorText = null },
                    label = { Text("Confirm new PIN or password") },
                    singleLine = true,
                    visualTransformation = transformation,
                    keyboardOptions = SecretKeyboardOptions,
                )
                errorText?.let { Text(it, color = Color(0xFFFF6B6B), fontSize = 13.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (newPin != confirmPin) {
                    errorText = "New entries do not match."
                    return@TextButton
                }
                when (ParentSecurityManager.changePin(context, currentPin, newPin)) {
                    PinChangeResult.Success -> onDismiss()
                    PinChangeResult.InvalidCurrentPin -> errorText = "Current PIN or password is incorrect."
                    PinChangeResult.InvalidNewPinFormat -> errorText = "New PIN or password must be at least 4 characters."
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
