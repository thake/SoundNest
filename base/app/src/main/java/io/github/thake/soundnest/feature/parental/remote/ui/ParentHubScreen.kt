/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.parental.remote.ui

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.ui.immersive.shell.ImmersiveBarAction
import io.github.thake.soundnest.core.ui.look.LookAlertDialog
import io.github.thake.soundnest.core.ui.settings.M3GroupCard
import io.github.thake.soundnest.core.ui.settings.M3SettingsPalette
import io.github.thake.soundnest.core.ui.settings.M3SettingsScaffold
import io.github.thake.soundnest.core.ui.settings.m3FilledButtonColors
import io.github.thake.soundnest.core.ui.settings.m3SettingsBlock
import io.github.thake.soundnest.core.ui.settings.rememberM3SettingsPalette
import io.github.thake.soundnest.feature.parental.remote.net.ChildLiveState
import io.github.thake.soundnest.feature.parental.remote.net.DiscoveredChildEndpoint
import io.github.thake.soundnest.feature.parental.remote.net.ParentLanDiscovery
import io.github.thake.soundnest.feature.parental.remote.net.ParentRemoteClient
import io.github.thake.soundnest.feature.parental.remote.net.RemotePlaybackAction
import io.github.thake.soundnest.feature.parental.remote.settings.PairedChildRecord
import io.github.thake.soundnest.feature.parental.remote.settings.PairedDevicesStore
import io.github.thake.soundnest.feature.parental.settings.FilterMode
import io.github.thake.soundnest.feature.speakers.settings.TrustedNetworkGate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Multi-child Wi-Fi Parent Controller Hub.
 * Works both in dedicated Parent Device mode (entered from Welcome without Spotify login)
 * and when opened from Parental Controls on a signed-in SoundNest install.
 */
@Composable
fun ParentHubScreen(
    isDedicatedMode: Boolean = false,
    onBack: (() -> Unit)? = null,
    onSwitchToStandardMode: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val palette = rememberM3SettingsPalette()

    LaunchedEffect(Unit) { PairedDevicesStore.init(context) }

    val pairedChildren by PairedDevicesStore.pairedChildrenFlow.collectAsState()
    val discoveredMap by ParentLanDiscovery.discovered.collectAsState()
    val onWifi = remember(context) { TrustedNetworkGate.currentSnapshot(context).onWifi }

    var liveStates by remember { mutableStateOf<Map<String, ChildLiveState>>(emptyMap()) }
    var showPairDialog by remember { mutableStateOf(false) }
    var preselectedEndpoint by remember { mutableStateOf<DiscoveredChildEndpoint?>(null) }
    var editingPolicyChild by remember { mutableStateOf<PairedChildRecord?>(null) }
    var sleepTimerTarget by remember { mutableStateOf<PairedChildRecord?>(null) }
    var renameTarget by remember { mutableStateOf<PairedChildRecord?>(null) }

    DisposableEffect(context) {
        ParentLanDiscovery.start(context)
        onDispose { ParentLanDiscovery.stop() }
    }

    // Poll paired children every 3 seconds while Parent Hub is active
    LaunchedEffect(pairedChildren) {
        if (pairedChildren.isEmpty()) {
            liveStates = emptyMap()
            return@LaunchedEffect
        }
        while (true) {
            liveStates = ParentRemoteClient.pollAll(context, pairedChildren)
            delay(3_000L)
        }
    }

    editingPolicyChild?.let { target ->
        RemotePolicyEditorScreen(
            child = target,
            otherChildren = pairedChildren.filter { it.childId != target.childId },
            onBack = { editingPolicyChild = null },
            onSaved = {
                editingPolicyChild = null
                scope.launch { liveStates = ParentRemoteClient.pollAll(context, pairedChildren) }
            },
        )
        return
    }

    val unpairedPairingReady = remember(discoveredMap, pairedChildren) {
        val pairedIds = pairedChildren.map { it.childId }.toSet()
        discoveredMap.values.filter { it.pairingOpen && it.childId !in pairedIds }
    }

    val anyPlaying = liveStates.values.any { it.online && it.snapshot?.isPlaying == true }

    fun pauseAllKids() {
        val onlineChildren = pairedChildren.filter { liveStates[it.childId]?.online == true }
        if (onlineChildren.isEmpty()) return
        scope.launch {
            val results = ParentRemoteClient.pauseAll(context, onlineChildren)
            val ok = results.values.count { it.isSuccess }
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, context.getString(R.string.parent_hub_paused_all_toast, ok), Toast.LENGTH_SHORT).show()
            }
            liveStates = ParentRemoteClient.pollAll(context, pairedChildren)
        }
    }

    M3SettingsScaffold(
        title = stringResource(R.string.parent_hub_title),
        onBack = onBack ?: {},
        listTestTag = "parent_hub_list",
        classicActions = {
            if (anyPlaying) {
                OutlinedButton(
                    onClick = ::pauseAllKids,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.padding(end = 6.dp).testTag("parent_hub_pause_all"),
                ) {
                    Icon(Icons.Rounded.PauseCircle, contentDescription = null, modifier = Modifier.size(16.dp), tint = palette.accent)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.parent_hub_pause_all), color = palette.text, fontSize = 12.sp)
                }
            }
            IconButton(
                onClick = { preselectedEndpoint = null; showPairDialog = true },
                modifier = Modifier.testTag("parent_hub_add_child"),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.parent_hub_pair_child), tint = palette.text)
            }
        },
        immersiveActions = {
            if (anyPlaying) {
                ImmersiveBarAction(
                    icon = Icons.Rounded.PauseCircle,
                    contentDescription = stringResource(R.string.parent_hub_pause_all),
                    onClick = ::pauseAllKids,
                )
            }
            ImmersiveBarAction(
                icon = Icons.Rounded.Add,
                contentDescription = stringResource(R.string.parent_hub_pair_child),
                onClick = { preselectedEndpoint = null; showPairDialog = true },
            )
        },
    ) { scaffoldPalette ->
        if (!onWifi) {
            m3SettingsBlock(key = "hub:no-wifi", palette = scaffoldPalette) {
                M3GroupCard(palette = scaffoldPalette) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    ) {
                        Icon(Icons.Rounded.WifiOff, contentDescription = null, tint = Color(0xFFFFB74D))
                        Text(
                            text = stringResource(R.string.parent_hub_not_on_wifi),
                            color = scaffoldPalette.text,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }

        if (unpairedPairingReady.isNotEmpty()) {
            m3SettingsBlock(
                key = "hub:discovered-pairing",
                palette = scaffoldPalette,
            ) {
                Text(
                    text = stringResource(R.string.parent_hub_discovered_pairing),
                    color = scaffoldPalette.muted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                )
                M3GroupCard(palette = scaffoldPalette) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        unpairedPairingReady.forEach { ep ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Rounded.Devices, contentDescription = null, tint = scaffoldPalette.accent)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(ep.deviceName, color = scaffoldPalette.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                    Text("${ep.host}:${ep.port}", color = scaffoldPalette.muted, fontSize = 12.sp)
                                }
                                Button(
                                    onClick = { preselectedEndpoint = ep; showPairDialog = true },
                                    colors = m3FilledButtonColors(scaffoldPalette),
                                ) {
                                    Text(stringResource(R.string.parent_hub_pair_action), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (pairedChildren.isEmpty()) {
            m3SettingsBlock(key = "hub:empty", palette = scaffoldPalette) {
                M3GroupCard(palette = scaffoldPalette) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 28.dp)
                            .testTag("parent_hub_empty_state"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Devices,
                            contentDescription = null,
                            tint = scaffoldPalette.accent,
                            modifier = Modifier.size(48.dp),
                        )
                        Text(
                            text = stringResource(R.string.parent_hub_empty_title),
                            color = scaffoldPalette.text,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = stringResource(R.string.parent_hub_empty_desc),
                            color = scaffoldPalette.muted,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                        )
                        Button(
                            onClick = { preselectedEndpoint = null; showPairDialog = true },
                            colors = m3FilledButtonColors(scaffoldPalette),
                            modifier = Modifier.testTag("parent_hub_pair_cta"),
                        ) {
                            Text(stringResource(R.string.parent_hub_pair_child), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            pairedChildren.forEach { child ->
                val state = liveStates[child.childId] ?: ChildLiveState(child = child, online = false)
                m3SettingsBlock(key = "hub:child:${child.childId}", palette = scaffoldPalette) {
                    ChildControlCard(
                        state = state,
                        palette = scaffoldPalette,
                        onAction = { action, vol, sleep ->
                            scope.launch {
                                val res = ParentRemoteClient.sendPlaybackAction(context, child, action, vol, sleep)
                                res.onSuccess { snap ->
                                    liveStates = liveStates + (child.childId to state.copy(online = true, snapshot = snap))
                                }
                            }
                        },
                        onOpenSleepTimer = { sleepTimerTarget = child },
                        onManageRules = { editingPolicyChild = child },
                        onRename = { renameTarget = child },
                        onUnpair = { PairedDevicesStore.removePairedChild(context, child.childId) },
                    )
                }
            }
        }

        if (isDedicatedMode && onSwitchToStandardMode != null) {
            m3SettingsBlock(key = "hub:switch-mode", palette = scaffoldPalette) {
                OutlinedButton(
                    onClick = onSwitchToStandardMode,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("parent_hub_switch_to_player"),
                ) {
                    Text(stringResource(R.string.parent_hub_switch_to_player), color = scaffoldPalette.muted)
                }
            }
        }
    }

    if (showPairDialog) {
        PairChildDialog(
            palette = palette,
            preselected = preselectedEndpoint,
            onDismiss = { showPairDialog = false; preselectedEndpoint = null },
            onPaired = {
                showPairDialog = false
                preselectedEndpoint = null
                scope.launch { liveStates = ParentRemoteClient.pollAll(context, PairedDevicesStore.pairedChildren(context)) }
            },
        )
    }

    sleepTimerTarget?.let { target ->
        LookAlertDialog(
            onDismissRequest = { sleepTimerTarget = null },
            containerColor = palette.dialogBg,
            title = { Text(stringResource(R.string.parent_hub_sleep_timer), color = palette.text, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val options = listOf<Pair<Int?, String>>(
                        null to stringResource(R.string.parent_hub_sleep_timer_off),
                        0 to stringResource(R.string.parent_hub_sleep_timer_eot),
                        15 to stringResource(R.string.parent_hub_sleep_timer_min, 15),
                        30 to stringResource(R.string.parent_hub_sleep_timer_min, 30),
                        45 to stringResource(R.string.parent_hub_sleep_timer_min, 45),
                        60 to stringResource(R.string.parent_hub_sleep_timer_min, 60),
                    )
                    options.forEach { (minutes, label) ->
                        TextButton(
                            onClick = {
                                sleepTimerTarget = null
                                scope.launch {
                                    ParentRemoteClient.sendPlaybackAction(
                                        context,
                                        target,
                                        RemotePlaybackAction.SET_SLEEP_TIMER,
                                        sleepTimerMinutes = minutes,
                                    ).onSuccess { snap ->
                                        liveStates = liveStates + (target.childId to ChildLiveState(target, true, snap))
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(label, color = palette.text, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { sleepTimerTarget = null }) {
                    Text(stringResource(android.R.string.cancel), color = palette.muted)
                }
            },
        )
    }

    renameTarget?.let { target ->
        var newName by remember(target) { mutableStateOf(target.displayName) }
        LookAlertDialog(
            onDismissRequest = { renameTarget = null },
            containerColor = palette.dialogBg,
            title = { Text(stringResource(R.string.parent_hub_rename), color = palette.text, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it.take(40) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleaned = newName.trim()
                        if (cleaned.isNotEmpty()) {
                            PairedDevicesStore.renameChild(context, target.childId, cleaned)
                            scope.launch { ParentRemoteClient.renameChildDevice(context, target, cleaned) }
                        }
                        renameTarget = null
                    },
                    colors = m3FilledButtonColors(palette),
                ) {
                    Text(stringResource(R.string.parent_remote_save_name), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text(stringResource(android.R.string.cancel), color = palette.muted)
                }
            },
        )
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun ChildControlCard(
    state: ChildLiveState,
    palette: M3SettingsPalette,
    onAction: (RemotePlaybackAction, Int?, Int?) -> Unit,
    onOpenSleepTimer: () -> Unit,
    onManageRules: () -> Unit,
    onRename: () -> Unit,
    onUnpair: () -> Unit,
) {
    val snap = state.snapshot
    var menuExpanded by remember { mutableStateOf(false) }
    var sliderVolume by remember(snap?.volumePercent) {
        mutableFloatStateOf((snap?.volumePercent ?: 50).toFloat())
    }

    M3GroupCard(palette = palette) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("parent_hub_child_card_${state.child.childId}"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header: child name + online dot + filter badge + overflow menu
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (state.online) Color(0xFF1DB954) else palette.muted.copy(alpha = 0.45f)),
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = state.child.displayName,
                        color = palette.text,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val modeLabel = when (snap?.filterMode) {
                        FilterMode.STRICT_ALLOWLIST -> stringResource(R.string.parent_mode_strict_allowlist_title)
                        FilterMode.FILTERED_OPEN -> stringResource(R.string.parent_mode_filtered_open_title)
                        null -> ""
                    }
                    val statusText = if (state.online) {
                        listOf(stringResource(R.string.parent_hub_status_online), modeLabel)
                            .filter { it.isNotBlank() }
                            .joinToString(" · ")
                    } else {
                        stringResource(R.string.parent_hub_status_offline)
                    }
                    Text(text = statusText, color = palette.muted, fontSize = 12.sp)
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = null, tint = palette.muted)
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.parent_hub_rename)) },
                            onClick = { menuExpanded = false; onRename() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.parent_hub_unpair)) },
                            onClick = { menuExpanded = false; onUnpair() },
                        )
                    }
                }
            }

            if (state.online && snap != null) {
                // Now Playing + Transport Controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (snap.coverUrl.isNotBlank()) {
                        GlideImage(
                            model = snap.coverUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp)),
                        )
                    } else {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(palette.divider),
                        ) {
                            Icon(Icons.Rounded.Devices, contentDescription = null, tint = palette.muted)
                        }
                    }

                    Column(Modifier.weight(1f)) {
                        Text(
                            text = snap.title.ifBlank { stringResource(R.string.parent_hub_nothing_playing) },
                            color = palette.text,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (snap.artist.isNotBlank()) {
                            Text(
                                text = snap.artist,
                                color = palette.muted,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onAction(RemotePlaybackAction.PREVIOUS, null, null) }) {
                            Icon(Icons.Rounded.SkipPrevious, contentDescription = null, tint = palette.text)
                        }
                        IconButton(
                            onClick = {
                                onAction(
                                    if (snap.isPlaying) RemotePlaybackAction.PAUSE else RemotePlaybackAction.PLAY,
                                    null,
                                    null,
                                )
                            },
                            modifier = Modifier.testTag("parent_hub_play_pause_${state.child.childId}"),
                        ) {
                            Icon(
                                imageVector = if (snap.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = palette.accent,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                        IconButton(onClick = { onAction(RemotePlaybackAction.NEXT, null, null) }) {
                            Icon(Icons.Rounded.SkipNext, contentDescription = null, tint = palette.text)
                        }
                    }
                }

                // Volume slider
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.VolumeUp, contentDescription = null, tint = palette.muted, modifier = Modifier.size(18.dp))
                    Slider(
                        value = sliderVolume,
                        onValueChange = { sliderVolume = it },
                        onValueChangeFinished = {
                            onAction(RemotePlaybackAction.SET_VOLUME, sliderVolume.toInt(), null)
                        },
                        valueRange = 0f..100f,
                        colors = SliderDefaults.colors(thumbColor = palette.accent, activeTrackColor = palette.accent),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${sliderVolume.toInt()}%",
                        color = palette.muted,
                        fontSize = 12.sp,
                        modifier = Modifier.width(36.dp),
                        textAlign = TextAlign.End,
                    )
                }

                // Quick action chips: Sleep Timer + Manage Rules
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val sleepLabel = when (val rem = snap.sleepTimerRemainingMs) {
                        null -> stringResource(R.string.parent_hub_sleep_timer)
                        -1L -> stringResource(R.string.parent_hub_sleep_timer_eot)
                        else -> stringResource(R.string.parent_hub_sleep_timer_min, ((rem + 59_999L) / 60_000L).toInt().coerceAtLeast(1))
                    }
                    OutlinedButton(
                        onClick = onOpenSleepTimer,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.Bedtime, contentDescription = null, modifier = Modifier.size(16.dp), tint = palette.accent)
                        Spacer(Modifier.width(6.dp))
                        Text(sleepLabel, color = palette.text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Button(
                        onClick = onManageRules,
                        colors = m3FilledButtonColors(palette),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("parent_hub_manage_rules_${state.child.childId}"),
                    ) {
                        Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.parent_hub_manage_rules), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
    }
}
