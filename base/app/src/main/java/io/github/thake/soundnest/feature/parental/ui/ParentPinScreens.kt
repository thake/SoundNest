/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.parental.ui

import io.github.thake.soundnest.R
import androidx.compose.ui.res.stringResource

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import io.github.thake.soundnest.core.ui.classic.AppAccent
import io.github.thake.soundnest.core.ui.classic.AppBackground
import io.github.thake.soundnest.core.ui.classic.AppMuted
import io.github.thake.soundnest.core.ui.classic.AppText
import io.github.thake.soundnest.core.ui.classic.tone
import io.github.thake.soundnest.core.ui.immersive.Immersive
import io.github.thake.soundnest.core.ui.look.LookAlertDialog
import io.github.thake.soundnest.core.ui.navigation.isImmersive
import io.github.thake.soundnest.core.ui.settings.m3FilledButtonColors
import io.github.thake.soundnest.core.ui.settings.m3SwitchColors
import io.github.thake.soundnest.core.ui.settings.rememberM3SettingsPalette
import io.github.thake.soundnest.feature.parental.security.AppSurface
import io.github.thake.soundnest.feature.parental.security.ParentSecurityManager
import io.github.thake.soundnest.feature.parental.security.PinChangeResult
import io.github.thake.soundnest.feature.parental.security.PinSetupResult
import io.github.thake.soundnest.feature.parental.security.PinVerifyResult
import io.github.thake.soundnest.feature.parental.settings.FilterMode
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore
import io.github.thake.soundnest.feature.speakers.connect.LanDiscovery
import io.github.thake.soundnest.feature.speakers.settings.TrustedNetworkGate
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
            text = stringResource(R.string.parent_pin_setup_title),
            color = AppText,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.parent_pin_setup_description),
            color = AppMuted,
            fontSize = 14.sp,
        )

        OutlinedTextField(
            value = pin,
            onValueChange = { next ->
                pin = sanitizeSecretInput(next)
                errorMessage = null
            },
            label = { Text(stringResource(R.string.parent_pin_input_hint)) },
            singleLine = true,
            visualTransformation = transformation,
            keyboardOptions = SecretKeyboardOptions,
            trailingIcon = {
                IconButton(onClick = { showSecret = !showSecret }) {
                    Icon(
                        imageVector = if (showSecret) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showSecret) stringResource(R.string.parent_pin_hide_toggle) else stringResource(R.string.parent_pin_show_toggle),
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
            label = { Text(stringResource(R.string.parent_pin_confirm_hint)) },
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
                text = stringResource(R.string.parent_pin_setup_mode_title),
                color = AppText,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = initialFilterMode == FilterMode.FILTERED_OPEN,
                    onClick = { initialFilterMode = FilterMode.FILTERED_OPEN },
                    label = { Text(stringResource(R.string.parent_mode_filtered_open_title), fontWeight = if (initialFilterMode == FilterMode.FILTERED_OPEN) FontWeight.Bold else FontWeight.Normal) },
                    leadingIcon = if (initialFilterMode == FilterMode.FILTERED_OPEN) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else null,
                    colors = chipColors,
                    modifier = Modifier.testTag("parent_setup_mode_filtered_open"),
                )
                FilterChip(
                    selected = initialFilterMode == FilterMode.STRICT_ALLOWLIST,
                    onClick = { initialFilterMode = FilterMode.STRICT_ALLOWLIST },
                    label = { Text(stringResource(R.string.parent_mode_strict_allowlist_title), fontWeight = if (initialFilterMode == FilterMode.STRICT_ALLOWLIST) FontWeight.Bold else FontWeight.Normal) },
                    leadingIcon = if (initialFilterMode == FilterMode.STRICT_ALLOWLIST) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else null,
                    colors = chipColors,
                    modifier = Modifier.testTag("parent_setup_mode_strict_allowlist"),
                )
            }
            Text(
                text = if (initialFilterMode == FilterMode.FILTERED_OPEN) {
                    stringResource(R.string.parent_pin_setup_mode_open_desc)
                } else {
                    stringResource(R.string.parent_pin_setup_mode_strict_desc)
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
                    Text(stringResource(R.string.parent_pin_biometric_toggle_title), color = AppText, fontSize = 15.sp)
                    Text(stringResource(R.string.parent_pin_biometric_toggle_subtitle), color = AppMuted, fontSize = 12.sp)
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
                    Text(stringResource(R.string.parent_pin_trust_wifi_toggle_title), color = AppText, fontSize = 15.sp)
                    Text(stringResource(R.string.parent_pin_trust_wifi_toggle_subtitle), color = AppMuted, fontSize = 12.sp)
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
                        errorMessage = context.getString(R.string.parent_pin_error_min_length)
                    pin != confirmPin ->
                        errorMessage = context.getString(R.string.parent_pin_error_mismatch)
                    else -> {
                        when (ParentSecurityManager.setupInitialPin(context, pin)) {
                            PinSetupResult.Success, PinSetupResult.AlreadyConfigured -> {
                                ParentSecurityManager.setBiometricEnabled(context, biometricSupported && enableBiometric)
                                ParentalPolicyStore.update(context) { current ->
                                    val home = if (initialFilterMode == FilterMode.STRICT_ALLOWLIST) {
                                        current.home.copy(minimalHomeMode = true)
                                    } else {
                                        current.home
                                    }
                                    current.copy(filterMode = initialFilterMode, home = home)
                                }
                                if (trustCurrentWifi) {
                                    TrustedNetworkGate.trustCurrentNetwork(context)
                                }
                                onPinConfigured()
                            }
                            PinSetupResult.InvalidFormat ->
                                errorMessage = context.getString(R.string.parent_pin_error_min_length)
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
            Text(stringResource(R.string.parent_pin_action_save_continue), fontWeight = FontWeight.Bold)
        }

        TextButton(
            onClick = onSwitchOrLogout,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(stringResource(R.string.parent_pin_action_switch_account), color = AppMuted)
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

    var showForgotHelp by remember(req.id) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { ParentSecurityManager.dismissPrompt() },
        containerColor = tone(0xFF282828),
        title = {
            Text(stringResource(R.string.parent_pin_dialog_title), color = AppText, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = surfaceDescription(context, req.surface),
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
                    label = { Text(stringResource(R.string.parent_pin_dialog_input_hint)) },
                    singleLine = true,
                    visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = SecretKeyboardOptions,
                    trailingIcon = {
                        IconButton(onClick = { showSecret = !showSecret }) {
                            Icon(
                                imageVector = if (showSecret) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showSecret) stringResource(R.string.parent_pin_hide_toggle) else stringResource(R.string.parent_pin_show_toggle),
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
                        text = stringResource(R.string.parent_pin_lockout_seconds, secs),
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
                        Text(stringResource(R.string.parent_pin_action_biometric), color = AppText)
                    }
                }
                Text(
                    text = if (showForgotHelp) stringResource(R.string.parent_pin_hide_recovery) else stringResource(R.string.parent_pin_forgot_button),
                    color = AppMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable { showForgotHelp = !showForgotHelp }
                        .padding(vertical = 2.dp)
                        .testTag("parent_prompt_forgot_toggle"),
                )
                if (showForgotHelp) {
                    Text(
                        text = stringResource(R.string.parent_pin_recovery_instructions),
                        color = AppMuted,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(tone(0xFF1F1F1F), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                            .testTag("parent_prompt_forgot_body"),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = lockoutMs <= 0L && pin.length >= ParentSecurityManager.MIN_PIN_LENGTH,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = AppAccent,
                    disabledContentColor = AppMuted.copy(alpha = 0.45f),
                ),
                onClick = {
                    when (val res = ParentSecurityManager.submitPromptPin(context, pin)) {
                        PinVerifyResult.Verified -> Unit
                        is PinVerifyResult.LockedOut -> {
                            pin = ""
                            lockoutMs = res.remainingMs
                        }
                        is PinVerifyResult.InvalidPin -> {
                            pin = ""
                            errorText = context.getString(R.string.parent_pin_error_tries, res.remainingBeforeCooldown)
                        }
                        PinVerifyResult.NotConfigured -> ParentSecurityManager.dismissPrompt()
                    }
                },
            ) {
                Text(stringResource(R.string.parent_pin_action_unlock_button), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = { ParentSecurityManager.dismissPrompt() }) {
                Text(stringResource(R.string.cancel), color = AppMuted)
            }
        },
    )
}

private fun surfaceDescription(context: Context, surface: AppSurface): String = when (surface) {
    AppSurface.PARENT_CONTROLS -> context.getString(R.string.parent_pin_surface_parent_controls)
    AppSurface.ACCOUNTS_AND_LOGOUT -> context.getString(R.string.parent_pin_surface_accounts)
    AppSurface.ARTIST_BANS -> context.getString(R.string.parent_pin_surface_artist_bans)
    AppSurface.DIAGNOSTIC_EXPORT -> context.getString(R.string.parent_pin_surface_diagnostic)
    AppSurface.KID_APPEARANCE, AppSurface.KID_AUDIO, AppSurface.KID_SLEEP_TIMER, AppSurface.KID_HIDDEN_SONGS -> ""
}

private fun Context.findActivity(): Activity? =
    generateSequence(this) { (it as? ContextWrapper)?.baseContext }
        .filterIsInstance<Activity>()
        .firstOrNull()

private fun launchSystemBiometric(context: Context, onError: (String) -> Unit) {
    val activity = context.findActivity() ?: run {
        onError(context.getString(R.string.parent_pin_biometric_window_error))
        return
    }
    runCatching {
        val prompt = BiometricPrompt.Builder(activity)
            .setTitle(context.getString(R.string.parent_action_unlock))
            .setSubtitle(context.getString(R.string.parent_pin_biometric_prompt_subtitle))
            .setNegativeButton(context.getString(R.string.parent_pin_biometric_use_pin), activity.mainExecutor) { _, _ -> }
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
    val palette = rememberM3SettingsPalette()
    val buttonColors = m3FilledButtonColors(palette)
    val switchColors = m3SwitchColors(palette)
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
                    text = if (unlocked) stringResource(R.string.menu_parent_mode_active) else stringResource(R.string.parent_locked_title),
                    color = if (unlocked) palette.accent else palette.text,
                    fontFamily = palette.fontFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (unlocked) stringResource(R.string.parent_pin_session_unlocked_hint)
                    else stringResource(R.string.parent_pin_session_locked_hint),
                    color = palette.muted,
                    fontFamily = palette.fontFamily,
                    fontSize = 12.sp,
                )
            }
            if (!unlocked) {
                Button(
                    onClick = { ParentSecurityManager.runProtected(context, AppSurface.PARENT_CONTROLS) {} },
                    colors = buttonColors,
                ) {
                    Text(stringResource(R.string.parent_pin_unlock), fontFamily = palette.fontFamily, fontWeight = FontWeight.Bold)
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
                colors = buttonColors,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.parent_pin_dashboard_open),
                    fontFamily = palette.fontFamily,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        if (unlocked) {
            if (biometricSupported) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(stringResource(R.string.parent_pin_biometric_title), color = palette.text, fontFamily = palette.fontFamily, fontSize = 15.sp)
                        Text(stringResource(R.string.parent_pin_biometric_subtitle), color = palette.muted, fontFamily = palette.fontFamily, fontSize = 12.sp)
                    }
                    Switch(
                        checked = biometricEnabled,
                        colors = switchColors,
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
                Text(
                    text = stringResource(R.string.parent_pin_change_button),
                    color = palette.text,
                    fontFamily = palette.fontFamily,
                    fontSize = 13.sp,
                    maxLines = 1,
                )
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
    val palette = rememberM3SettingsPalette()
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var showSecrets by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val transformation = if (showSecrets) VisualTransformation.None else PasswordVisualTransformation()
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = palette.text,
        unfocusedTextColor = palette.text,
        focusedBorderColor = palette.accent,
        unfocusedBorderColor = palette.divider,
        focusedLabelColor = palette.accent,
        unfocusedLabelColor = palette.muted,
        cursorColor = palette.accent,
    )

    LookAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.dialogBg,
        title = {
            Text(
                text = stringResource(R.string.parent_pin_change_title),
                color = palette.text,
                fontFamily = palette.fontFamily,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = currentPin,
                    onValueChange = { currentPin = sanitizeSecretInput(it); errorText = null },
                    label = { Text(stringResource(R.string.parent_pin_current_label), fontFamily = palette.fontFamily) },
                    singleLine = true,
                    visualTransformation = transformation,
                    keyboardOptions = SecretKeyboardOptions,
                    colors = fieldColors,
                    trailingIcon = {
                        IconButton(onClick = { showSecrets = !showSecrets }) {
                            Icon(
                                imageVector = if (showSecrets) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showSecrets) stringResource(R.string.parent_pin_hide_toggle) else stringResource(R.string.parent_pin_show_toggle),
                                tint = palette.muted,
                            )
                        }
                    },
                )
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { newPin = sanitizeSecretInput(it); errorText = null },
                    label = { Text(stringResource(R.string.parent_pin_new_label), fontFamily = palette.fontFamily) },
                    singleLine = true,
                    visualTransformation = transformation,
                    keyboardOptions = SecretKeyboardOptions,
                    colors = fieldColors,
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { confirmPin = sanitizeSecretInput(it); errorText = null },
                    label = { Text(stringResource(R.string.parent_pin_confirm_label), fontFamily = palette.fontFamily) },
                    singleLine = true,
                    visualTransformation = transformation,
                    keyboardOptions = SecretKeyboardOptions,
                    colors = fieldColors,
                )
                errorText?.let {
                    Text(it, color = palette.destructive, fontFamily = palette.fontFamily, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = currentPin.length >= ParentSecurityManager.MIN_PIN_LENGTH &&
                    newPin.length >= ParentSecurityManager.MIN_PIN_LENGTH &&
                    confirmPin.length >= ParentSecurityManager.MIN_PIN_LENGTH,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = palette.accent,
                    disabledContentColor = palette.muted.copy(alpha = 0.45f),
                ),
                onClick = {
                    if (newPin != confirmPin) {
                        errorText = context.getString(R.string.parent_pin_error_mismatch)
                        return@TextButton
                    }
                    when (ParentSecurityManager.changePin(context, currentPin, newPin)) {
                        PinChangeResult.Success -> onDismiss()
                        PinChangeResult.InvalidCurrentPin -> errorText = context.getString(R.string.parent_pin_error_incorrect)
                        PinChangeResult.InvalidNewPinFormat -> errorText = context.getString(R.string.parent_pin_error_too_short)
                        PinChangeResult.LockedOut -> errorText = context.getString(R.string.parent_pin_error_cooldown)
                    }
                },
            ) {
                Text(stringResource(R.string.parent_pin_save), fontFamily = palette.fontFamily, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = palette.muted, fontFamily = palette.fontFamily)
            }
        },
    )
}

/**
 * Compact, single-row entry point for Kid Settings (shown only on the root/first Settings page)
 * so Parental Controls never monopolize vertical space on every Settings tab.
 */
@Composable
fun CompactParentalControlsRow(onOpenParentSettings: () -> Unit) {
    val context = LocalContext.current
    val palette = rememberM3SettingsPalette()
    val tick by ParentSecurityManager.sessionTick.collectAsState()
    val unlocked = remember(tick) { ParentSecurityManager.isAdminUnlocked(context) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                ParentSecurityManager.runProtected(context, AppSurface.PARENT_CONTROLS) {
                    onOpenParentSettings()
                }
            }
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag("compact_parental_controls_row"),
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = palette.accent,
            modifier = Modifier.padding(end = 14.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.parent_settings_title),
                color = palette.text,
                fontFamily = palette.fontFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (unlocked) stringResource(R.string.parent_pin_active_hint) else stringResource(R.string.parent_pin_manage_rules_hint),
                color = if (unlocked) palette.accent else palette.muted,
                fontFamily = palette.fontFamily,
                fontSize = 12.sp,
            )
        }
        if (unlocked) {
            TextButton(
                onClick = { ParentSecurityManager.lockNow(context) },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = stringResource(R.string.parent_pin_lock),
                    color = palette.accent,
                    fontFamily = palette.fontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = palette.muted,
        )
    }
}

/**
 * Global top status strip shown across the main app whenever the 5-minute Parent Mode session
 * is active (outside `ParentSettingsScreen`), giving parents a live countdown and 1-tap Lock
 * without overlapping screen TopAppBar titles or actions.
 */
@Composable
fun ParentAdminSessionBanner(
    onOpenParentSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val tick by ParentSecurityManager.sessionTick.collectAsState()
    var remainingMs by remember(tick) { mutableLongStateOf(ParentSecurityManager.adminRemainingMs(context)) }

    LaunchedEffect(tick, remainingMs > 0L) {
        while (remainingMs > 0L) {
            delay(1_000L)
            val next = ParentSecurityManager.adminRemainingMs(context)
            remainingMs = next
            if (next <= 0L) {
                ParentSecurityManager.lockNow(context)
                break
            }
        }
    }

    if (remainingMs <= 0L) return

    val totalSec = ((remainingMs + 999L) / 1000L).coerceAtLeast(1L)
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    val timerText = "%d:%02d".format(mins, secs)
    val accent = if (isImmersive()) Immersive.Accent else AppAccent

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF141E17))
            .statusBarsPadding()
            .testTag("global_parent_mode_banner"),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onOpenParentSettings),
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.height(15.dp),
                )
                Text(
                    text = stringResource(R.string.parent_pin_session_unlocked, timerText),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = stringResource(R.string.parent_pin_lock),
                color = Color.Black,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(accent, RoundedCornerShape(50))
                    .clickable { ParentSecurityManager.lockNow(context) }
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .testTag("global_parent_mode_lock"),
            )
        }
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(accent.copy(alpha = 0.35f)),
        )
    }
}
