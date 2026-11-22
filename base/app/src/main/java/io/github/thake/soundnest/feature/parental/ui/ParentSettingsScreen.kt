/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.parental.ui

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.ui.immersive.shell.ImmersiveBarAction
import io.github.thake.soundnest.core.ui.look.LookSheet
import io.github.thake.soundnest.core.ui.settings.M3GroupCard
import io.github.thake.soundnest.core.ui.settings.M3RemovableChip
import io.github.thake.soundnest.core.ui.settings.M3SectionBlock
import io.github.thake.soundnest.core.ui.settings.M3SettingsChoiceRow
import io.github.thake.soundnest.core.ui.settings.M3SettingsNavRow
import io.github.thake.soundnest.core.ui.settings.M3SettingsPalette
import io.github.thake.soundnest.core.ui.settings.M3SettingsScaffold
import io.github.thake.soundnest.core.ui.settings.M3SettingsSwitchRow
import io.github.thake.soundnest.core.ui.settings.M3SubpageHost
import io.github.thake.soundnest.core.ui.settings.m3FilledButtonColors
import io.github.thake.soundnest.core.ui.settings.m3FilterChipColors
import io.github.thake.soundnest.core.ui.settings.m3SettingsBlock
import io.github.thake.soundnest.core.ui.settings.m3SettingsSection
import io.github.thake.soundnest.core.ui.settings.rememberM3SettingsPalette
import io.github.thake.soundnest.feature.home.settings.m3HomeParentSettingsSections
import io.github.thake.soundnest.feature.library.settings.m3LibraryParentRow
import io.github.thake.soundnest.feature.localfiles.settings.m3LocalFilesParentRow
import io.github.thake.soundnest.feature.lyrics.settings.m3LyricsParentRows
import io.github.thake.soundnest.feature.parental.security.AppSurface
import io.github.thake.soundnest.feature.parental.security.ParentSecurityManager
import io.github.thake.soundnest.feature.parental.settings.FilterMode
import io.github.thake.soundnest.feature.parental.settings.ParentalConfigBackup
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore
import io.github.thake.soundnest.feature.parental.settings.RuleLabelStore
import io.github.thake.soundnest.feature.player.settings.m3PlaybackParentRow
import io.github.thake.soundnest.feature.radio.settings.m3RadioParentRow
import io.github.thake.soundnest.feature.speakers.connect.LanDiscovery
import io.github.thake.soundnest.feature.speakers.jam.settings.m3JamParentRow
import io.github.thake.soundnest.feature.speakers.settings.SpeakersNicknameDialogHost
import io.github.thake.soundnest.feature.speakers.settings.TrustedNetworkGate
import io.github.thake.soundnest.feature.speakers.settings.m3SpeakersParentSettingsSections
import io.github.thake.soundnest.feature.speakers.settings.rememberSpeakersParentUiState
import io.github.thake.soundnest.feature.spokenword.audible.settings.AudibleCredentialStore
import io.github.thake.soundnest.feature.spokenword.audible.settings.AudibleParentControlsCard
import io.github.thake.soundnest.feature.spokenword.audible.ui.AudiblePickerSheet
import io.github.thake.soundnest.feature.spokenword.podcasts.settings.m3PodcastParentRows
import kotlinx.coroutines.launch

enum class ParentSettingsPage {
    Root,
    FilterRules,
    HomeSections,
    Spotify,
    Audible,
    LocalAndRadio,
    SpeakersNetwork,
    ScheduleAndSleep,
    FamilyRemote,
    SecurityBackup,
}

@Composable
fun ParentSettingsScreen(
    navController: NavController? = null,
    listState: LazyListState = rememberLazyListState(),
    initialPage: ParentSettingsPage = ParentSettingsPage.Root,
    remoteChild: io.github.thake.soundnest.feature.parental.remote.settings.PairedChildRecord? = null,
    otherRemoteChildren: List<io.github.thake.soundnest.feature.parental.remote.settings.PairedChildRecord> = emptyList(),
    onClose: (() -> Unit)? = null,
    onOpenRemoteCatalog: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val isRemoteMode = remoteChild != null
    val sessionTick by ParentSecurityManager.sessionTick.collectAsState()
    val unlocked = if (isRemoteMode) true else remember(sessionTick) { ParentSecurityManager.isAdminUnlocked(context) }
    val policy by ParentalPolicyStore.policyFlow.collectAsState()
    val remoteSyncState by io.github.thake.soundnest.feature.parental.remote.settings.RemotePolicySession.syncStateFlow.collectAsState()
    val audibleSummary by AudibleCredentialStore.summaryFlow.collectAsState()
    val exitAction: () -> Unit = onClose ?: { navController?.popBackStack() }

    if (isRemoteMode) {
        BackHandler(onBack = exitAction)
    }

    val vm: ParentSettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val networkRefresh by vm.networkRefresh.collectAsState()
    var rulesSheetApprovedTab by remember { mutableStateOf<Boolean?>(null) }
    var showAudiblePicker by remember { mutableStateOf(false) }
    var showSyncOthersDialog by remember { mutableStateOf(false) }

    LaunchedEffect(remoteChild?.childId) {
        if (remoteChild != null) {
            io.github.thake.soundnest.feature.parental.remote.settings.RemotePolicySession.attach(context, remoteChild)
        } else {
            AudibleCredentialStore.summary(context)
            ParentalPolicyStore.current(context)
            if (!ParentSecurityManager.isAdminUnlocked(context)) {
                ParentSecurityManager.runProtected(context, AppSurface.PARENT_CONTROLS) {}
            }
        }
    }

    DisposableEffect(remoteChild?.childId) {
        onDispose {
            if (remoteChild != null &&
                io.github.thake.soundnest.feature.parental.remote.settings.RemotePolicySession.activeChild?.childId == remoteChild.childId
            ) {
                io.github.thake.soundnest.feature.parental.remote.settings.RemotePolicySession.detach(context)
            }
        }
    }

    DisposableEffect(context, unlocked, isRemoteMode) {
        val onWifi = TrustedNetworkGate.currentSnapshot(context).onWifi
        if (!isRemoteMode && unlocked && onWifi) runCatching { LanDiscovery.start(context) }
        onDispose {
            if (!isRemoteMode && unlocked && onWifi) runCatching { LanDiscovery.stop() }
        }
    }

    LaunchedEffect(vm) {
        vm.backupEvents.collect { event ->
            when (event) {
                ParentBackupEvent.ExportSuccess ->
                    Toast.makeText(context, context.getString(R.string.parent_toast_export_success), Toast.LENGTH_SHORT).show()
                is ParentBackupEvent.ExportFailed ->
                    Toast.makeText(context, context.getString(R.string.parent_toast_export_failed, event.message), Toast.LENGTH_LONG).show()
                ParentBackupEvent.ImportSuccess ->
                    Toast.makeText(context, context.getString(R.string.parent_toast_import_success), Toast.LENGTH_SHORT).show()
                is ParentBackupEvent.ImportFailed ->
                    Toast.makeText(context, context.getString(R.string.parent_toast_import_failed, event.message), Toast.LENGTH_LONG).show()
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        vm.exportBackup(
            produceJson = { ParentalConfigBackup.exportJson(context) },
            writeJson = { jsonText ->
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(jsonText.toByteArray(Charsets.UTF_8))
                } ?: error("Could not open file output stream")
            },
        )
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        vm.importBackup(
            readJson = {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    input.readBytes().toString(Charsets.UTF_8)
                } ?: error("Could not read selected file")
            },
            applyJson = { raw -> ParentalConfigBackup.importJson(context, raw) },
        )
    }

    val remoteAttached = isRemoteMode &&
        remoteSyncState != io.github.thake.soundnest.feature.parental.remote.settings.RemoteSyncState.Loading &&
        remoteSyncState != io.github.thake.soundnest.feature.parental.remote.settings.RemoteSyncState.Idle &&
        io.github.thake.soundnest.feature.parental.remote.settings.RemotePolicySession.isActive

    if (isRemoteMode && !remoteAttached) {
        M3SettingsScaffold(
            title = stringResource(R.string.remote_policy_title, remoteChild.displayName),
            onBack = exitAction,
            listState = listState,
            listTestTag = "parent_settings_list",
        ) { palette ->
            m3SettingsBlock(key = "remote:loading", palette = palette) {
                M3GroupCard(palette = palette) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                    ) {
                        when (val st = remoteSyncState) {
                            is io.github.thake.soundnest.feature.parental.remote.settings.RemoteSyncState.Error -> {
                                Text(
                                    text = stringResource(R.string.remote_policy_load_failed, remoteChild.displayName, st.message),
                                    color = palette.text,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center,
                                )
                                Button(
                                    onClick = { io.github.thake.soundnest.feature.parental.remote.settings.RemotePolicySession.retrySync(context) },
                                    colors = m3FilledButtonColors(palette),
                                ) {
                                    Text(stringResource(R.string.remote_policy_retry), fontWeight = FontWeight.Bold)
                                }
                            }
                            else -> {
                                androidx.compose.material3.CircularProgressIndicator(color = palette.accent)
                                Text(
                                    text = stringResource(R.string.remote_policy_loading, remoteChild.displayName),
                                    color = palette.muted,
                                    fontSize = 14.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
        return
    }

    if (!unlocked) {
        M3SettingsScaffold(
            title = stringResource(R.string.parent_settings_title),
            onBack = exitAction,
            listState = listState,
            listTestTag = "parent_settings_list",
        ) { palette ->
            m3SettingsBlock(key = "parent:locked", palette = palette) {
                M3GroupCard(palette = palette) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = palette.accent,
                            modifier = Modifier.size(48.dp),
                        )
                        Text(
                            text = stringResource(R.string.parent_locked_title),
                            color = palette.text,
                            fontFamily = palette.fontFamily,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = stringResource(R.string.parent_locked_subtitle),
                            color = palette.muted,
                            fontFamily = palette.fontFamily,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                        )
                        Button(
                            onClick = { ParentSecurityManager.runProtected(context, AppSurface.PARENT_CONTROLS) {} },
                            colors = m3FilledButtonColors(palette),
                        ) {
                            Text(
                                text = stringResource(R.string.parent_action_unlock),
                                fontFamily = palette.fontFamily,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
        return
    }

    val lockAndExit: () -> Unit = {
        if (!isRemoteMode) ParentSecurityManager.lockNow(context)
        exitAction()
    }

    M3SubpageHost(
        initialPage = initialPage,
        rootPage = ParentSettingsPage.Root,
        valueOf = ParentSettingsPage::valueOf,
        onExit = exitAction,
        label = "parent_settings_pages",
    ) { page, open, back, _ ->
        val pageListState = if (page == initialPage) listState else rememberLazyListState()
        when (page) {
            ParentSettingsPage.Root -> ParentRootHubPage(
                context = context,
                policy = policy,
                audibleConnected = audibleSummary != null,
                sessionTick = sessionTick,
                networkRefresh = networkRefresh,
                listState = pageListState,
                open = open,
                back = back,
                lockAndExit = lockAndExit,
                remoteChild = remoteChild,
                remoteSyncState = remoteSyncState,
                hasOtherRemoteChildren = otherRemoteChildren.isNotEmpty(),
                onOpenRemoteCatalog = onOpenRemoteCatalog,
                onOpenSyncOthers = { showSyncOthersDialog = true },
            )
            ParentSettingsPage.FilterRules -> ParentFilterRulesPage(
                context = context,
                policy = policy,
                listState = pageListState,
                back = back,
                lockAndExit = lockAndExit,
                onOpenRulesSheet = { approved -> rulesSheetApprovedTab = approved },
            )
            ParentSettingsPage.HomeSections -> ParentHomeSectionsPage(
                context = context,
                policy = policy,
                listState = pageListState,
                back = back,
                lockAndExit = lockAndExit,
            )
            ParentSettingsPage.Spotify -> ParentSpotifyPage(
                context = context,
                policy = policy,
                listState = pageListState,
                back = back,
                lockAndExit = lockAndExit,
            )
            ParentSettingsPage.Audible -> ParentAudiblePage(
                context = context,
                policy = policy,
                listState = pageListState,
                back = back,
                lockAndExit = lockAndExit,
                onOpenAudiblePicker = { showAudiblePicker = true },
            )
            ParentSettingsPage.LocalAndRadio -> ParentLocalAndRadioPage(
                context = context,
                policy = policy,
                listState = pageListState,
                back = back,
                lockAndExit = lockAndExit,
            )
            ParentSettingsPage.SpeakersNetwork -> ParentSpeakersNetworkPage(
                context = context,
                policy = policy,
                sessionTick = sessionTick,
                networkRefresh = networkRefresh,
                listState = pageListState,
                back = back,
                lockAndExit = lockAndExit,
            )
            ParentSettingsPage.ScheduleAndSleep -> io.github.thake.soundnest.feature.parental.schedule.ui.ParentScheduleSettingsPage(
                context = context,
                policy = policy,
                listState = pageListState,
                back = back,
                lockAndExit = lockAndExit,
            )
            ParentSettingsPage.FamilyRemote -> io.github.thake.soundnest.feature.parental.remote.ui.ChildFamilyRemotePage(
                context = context,
                listState = pageListState,
                back = back,
                lockAndExit = lockAndExit,
                onOpenParentHub = {
                    navController?.navigate(io.github.thake.soundnest.core.ui.navigation.Routes.ParentHub.route)
                },
            )
            ParentSettingsPage.SecurityBackup -> ParentSecurityBackupPage(
                context = context,
                listState = pageListState,
                back = back,
                lockAndExit = lockAndExit,
                onExportJson = { exportLauncher.launch("soundnest-policy.json") },
                onImportJson = { importLauncher.launch(arrayOf("application/json", "*/*")) },
            )
        }
    }

    rulesSheetApprovedTab?.let { approvedTab ->
        ManageContentRulesSheet(
            initialTabIsApproved = approvedTab,
            onDismiss = { rulesSheetApprovedTab = null },
        )
    }

    if (showAudiblePicker) {
        AudiblePickerSheet(
            onDismiss = { showAudiblePicker = false },
        )
    }

    if (showSyncOthersDialog && otherRemoteChildren.isNotEmpty()) {
        val syncPalette = rememberM3SettingsPalette()
        val syncScope = androidx.compose.runtime.rememberCoroutineScope()
        var selectedIds by remember(otherRemoteChildren) { mutableStateOf(otherRemoteChildren.map { it.childId }.toSet()) }
        var pushing by remember { mutableStateOf(false) }

        io.github.thake.soundnest.core.ui.look.LookAlertDialog(
            onDismissRequest = { if (!pushing) showSyncOthersDialog = false },
            containerColor = syncPalette.dialogBg,
            title = { Text(stringResource(R.string.remote_policy_sync_others), color = syncPalette.text, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    otherRemoteChildren.forEach { target ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            androidx.compose.material3.Checkbox(
                                checked = target.childId in selectedIds,
                                onCheckedChange = { checked ->
                                    selectedIds = if (checked) selectedIds + target.childId else selectedIds - target.childId
                                },
                                enabled = !pushing,
                            )
                            Text(target.displayName, color = syncPalette.text, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targets = otherRemoteChildren.filter { it.childId in selectedIds }
                        if (targets.isEmpty() || pushing) return@Button
                        pushing = true
                        syncScope.launch {
                            val ok = io.github.thake.soundnest.feature.parental.remote.settings.RemotePolicySession
                                .pushToOtherChildren(context, targets)
                            pushing = false
                            showSyncOthersDialog = false
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.remote_policy_sync_result, ok, targets.size),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    },
                    enabled = !pushing && selectedIds.isNotEmpty(),
                    colors = m3FilledButtonColors(syncPalette),
                ) {
                    Text(stringResource(R.string.remote_policy_sync_others), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showSyncOthersDialog = false }, enabled = !pushing) {
                    Text(stringResource(android.R.string.cancel), color = syncPalette.muted)
                }
            },
        )
    }
}

@Composable
internal fun ParentLockExitClassicAction(
    palette: M3SettingsPalette,
    onLockExit: () -> Unit,
) {
    OutlinedButton(
        onClick = onLockExit,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        modifier = Modifier.padding(end = 6.dp),
    ) {
        Icon(Icons.Rounded.Lock, contentDescription = null, tint = palette.accent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.parent_pin_lock),
            color = palette.text,
            fontFamily = palette.fontFamily,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ParentRootHubPage(
    context: Context,
    policy: ParentalPolicy,
    audibleConnected: Boolean,
    sessionTick: Long,
    networkRefresh: Int,
    listState: LazyListState,
    open: (ParentSettingsPage) -> Unit,
    back: () -> Unit,
    lockAndExit: () -> Unit,
    remoteChild: io.github.thake.soundnest.feature.parental.remote.settings.PairedChildRecord? = null,
    remoteSyncState: io.github.thake.soundnest.feature.parental.remote.settings.RemoteSyncState =
        io.github.thake.soundnest.feature.parental.remote.settings.RemoteSyncState.Idle,
    hasOtherRemoteChildren: Boolean = false,
    onOpenRemoteCatalog: (() -> Unit)? = null,
    onOpenSyncOthers: (() -> Unit)? = null,
) {
    val palette = rememberM3SettingsPalette()
    val modeName = stringResource(
        if (policy.filterMode == FilterMode.STRICT_ALLOWLIST) R.string.parent_mode_strict_allowlist_title
        else R.string.parent_mode_filtered_open_title,
    )
    val approvedTotal = policy.allowedPlaylistIds.size +
        policy.allowedAlbumIds.size +
        policy.allowedArtistIds.size +
        policy.allowedTrackIds.size +
        policy.spokenWord.podcasts.allowedShowIds.size +
        policy.spokenWord.audible.allowedAsins.size
    val blockedTotal = policy.blockedPlaylistIds.size +
        policy.blockedAlbumIds.size +
        policy.blockedArtistIds.size +
        policy.blockedTrackIds.size +
        policy.spokenWord.podcasts.blockedShowIds.size +
        policy.blockedKeywords.size +
        policy.blockedRegexPatterns.size

    val visibleCarousels = listOf(
        policy.home.showChartsAndTrending,
        policy.home.showMixesAndMadeForYou,
        policy.home.showNewReleases,
        policy.home.showRecents,
    ).count { it }
    val homeSummary = if (policy.home.minimalHomeMode) {
        stringResource(R.string.parent_nav_home_minimal_summary)
    } else {
        stringResource(R.string.parent_nav_home_open_summary, visibleCarousels)
    }

    val enabledSpotifyCount = listOf(
        policy.effectiveAllowAutoplayAndRadio,
        policy.spokenWord.allowPodcasts,
        policy.spokenWord.allowAudiobooks,
        policy.speakers.jam.enabled,
        policy.lyrics.enabled,
    ).count { it }
    val spotifySummary = stringResource(
        if (policy.library.readOnlySpotifyLibrary) R.string.parent_nav_spotify_summary_readonly
        else R.string.parent_nav_spotify_summary_editable,
        enabledSpotifyCount,
    )

    val approvedAudible = policy.spokenWord.audible.allowedAsins.size
    val audibleDetail = when {
        policy.spokenWord.allowAudible ->
            stringResource(R.string.parent_nav_audible_summary_enabled, approvedAudible)
        audibleConnected || approvedAudible > 0 ->
            stringResource(R.string.parent_nav_audible_summary_paused, approvedAudible)
        else ->
            stringResource(R.string.parent_nav_audible_summary_disabled)
    }

    val localRadioDetail = when {
        policy.localFiles.enabled && policy.radio.enabled ->
            stringResource(R.string.parent_nav_local_radio_both)
        policy.localFiles.enabled ->
            stringResource(R.string.parent_nav_local_radio_local_only)
        policy.radio.enabled ->
            stringResource(R.string.parent_nav_local_radio_radio_only)
        else ->
            stringResource(R.string.parent_nav_local_radio_disabled)
    }

    val snap = remember(sessionTick, networkRefresh, policy.speakers) {
        TrustedNetworkGate.currentSnapshot(context)
    }
    val isTrustedNow = remember(sessionTick, networkRefresh, snap, policy.speakers) {
        TrustedNetworkGate.isNetworkTrusted(context, snap)
    }
    val speakersSummary = when {
        !policy.speakers.requireTrustedNetwork -> stringResource(R.string.parent_nav_speakers_summary_unrestricted)
        isTrustedNow -> stringResource(R.string.parent_nav_speakers_summary_trusted)
        else -> stringResource(R.string.parent_nav_speakers_summary_blocked)
    }

    M3SettingsScaffold(
        title = if (remoteChild != null) {
            stringResource(R.string.remote_policy_title, remoteChild.displayName)
        } else {
            stringResource(R.string.parent_settings_title)
        },
        onBack = back,
        listState = listState,
        listTestTag = "parent_settings_list",
        classicActions = {
            if (remoteChild == null) ParentLockExitClassicAction(palette, lockAndExit)
        },
        immersiveActions = {
            if (remoteChild == null) {
                ImmersiveBarAction(
                    icon = Icons.Rounded.Lock,
                    contentDescription = stringResource(R.string.parent_pin_lock),
                    onClick = lockAndExit,
                )
            }
        },
    ) { scaffoldPalette ->
        if (remoteChild != null) {
            m3SettingsBlock(key = "parent:remote-banner", palette = scaffoldPalette) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    io.github.thake.soundnest.feature.parental.remote.ui.RemoteSyncBannerRow(
                        childName = remoteChild.displayName,
                        syncState = remoteSyncState,
                        palette = scaffoldPalette,
                        onRetry = { io.github.thake.soundnest.feature.parental.remote.settings.RemotePolicySession.retrySync(context) },
                    )
                    if (onOpenRemoteCatalog != null) {
                        M3GroupCard(palette = scaffoldPalette) {
                            M3SettingsNavRow(
                                icon = Icons.Rounded.CheckCircle,
                                title = stringResource(R.string.parent_hub_pick_block),
                                detail = stringResource(R.string.remote_browse_pick_block_desc, remoteChild.displayName),
                                palette = scaffoldPalette,
                                testTag = "parent_remote_open_catalog",
                                onClick = onOpenRemoteCatalog,
                            )
                        }
                    }
                    if (hasOtherRemoteChildren && onOpenSyncOthers != null) {
                        OutlinedButton(
                            onClick = onOpenSyncOthers,
                            modifier = Modifier.fillMaxWidth().testTag("parent_remote_sync_others"),
                        ) {
                            Text(stringResource(R.string.remote_policy_sync_others), color = scaffoldPalette.text)
                        }
                    }
                }
            }
        } else {
            item(key = "parent:session-block", contentType = "m3:block") {
                M3SectionBlock(
                    palette = scaffoldPalette,
                    header = stringResource(R.string.parent_section_lock_session),
                ) {
                    Box(Modifier.padding(16.dp)) {
                        ParentSecurityControlsSection()
                    }
                }
            }
        }

        m3SettingsSection(
            key = "parent:content-group",
            palette = scaffoldPalette,
            header = context.getString(R.string.parent_group_content),
            hasLeadingIconBadges = true,
        ) {
            row {
                M3SettingsNavRow(
                    icon = Icons.Rounded.FilterList,
                    title = stringResource(R.string.parent_section_filter_rules),
                    detail = stringResource(R.string.parent_nav_filter_rules_detail, modeName, approvedTotal, blockedTotal),
                    palette = scaffoldPalette,
                    testTag = "parent_nav_filter_rules",
                    onClick = { open(ParentSettingsPage.FilterRules) },
                )
            }
            row {
                M3SettingsNavRow(
                    icon = Icons.Rounded.Home,
                    title = stringResource(R.string.parent_section_home),
                    detail = homeSummary,
                    palette = scaffoldPalette,
                    testTag = "parent_nav_home_sections",
                    onClick = { open(ParentSettingsPage.HomeSections) },
                )
            }
            row {
                M3SettingsNavRow(
                    icon = Icons.Rounded.LibraryMusic,
                    title = stringResource(R.string.parent_section_spotify),
                    detail = spotifySummary,
                    palette = scaffoldPalette,
                    testTag = "parent_nav_spotify",
                    onClick = { open(ParentSettingsPage.Spotify) },
                )
            }
            row {
                M3SettingsNavRow(
                    icon = Icons.Rounded.Headphones,
                    title = stringResource(R.string.parent_section_audible),
                    detail = audibleDetail,
                    palette = scaffoldPalette,
                    testTag = "parent_nav_audible",
                    onClick = { open(ParentSettingsPage.Audible) },
                )
            }
            row {
                M3SettingsNavRow(
                    icon = Icons.Rounded.Radio,
                    title = stringResource(R.string.settings_section_local_radio),
                    detail = localRadioDetail,
                    palette = scaffoldPalette,
                    testTag = "parent_nav_local_radio",
                    onClick = { open(ParentSettingsPage.LocalAndRadio) },
                )
            }
        }

        m3SettingsSection(
            key = "parent:system-group",
            palette = scaffoldPalette,
            header = context.getString(R.string.parent_group_system),
            hasLeadingIconBadges = true,
        ) {
            row {
                M3SettingsNavRow(
                    icon = Icons.Rounded.Wifi,
                    title = stringResource(R.string.parent_speakers_trusted_title),
                    detail = speakersSummary,
                    palette = scaffoldPalette,
                    testTag = "parent_nav_speakers_network",
                    onClick = { open(ParentSettingsPage.SpeakersNetwork) },
                )
            }
            row {
                val sched = policy.schedule.clamped()
                val parts = buildList {
                    if (sched.usageHoursEnabled) {
                        add(
                            stringResource(
                                R.string.parent_schedule_nav_detail_hours,
                                io.github.thake.soundnest.feature.parental.schedule.ScheduleParentalPolicy.formatMinutesOfDay(sched.weekdayWindow.startMinutes),
                                io.github.thake.soundnest.feature.parental.schedule.ScheduleParentalPolicy.formatMinutesOfDay(sched.weekdayWindow.endMinutes),
                            ),
                        )
                    }
                    if (sched.bedtimeInactivityEnabled) {
                        add(stringResource(R.string.parent_schedule_nav_detail_bedtime, sched.inactivityTimeoutMinutes))
                    }
                }
                M3SettingsNavRow(
                    icon = Icons.Rounded.Bedtime,
                    title = stringResource(R.string.parent_schedule_section_title),
                    detail = if (parts.isEmpty()) stringResource(R.string.parent_schedule_nav_detail_off) else parts.joinToString(" · "),
                    palette = scaffoldPalette,
                    testTag = "parent_nav_schedule_sleep",
                    onClick = { open(ParentSettingsPage.ScheduleAndSleep) },
                )
            }
            if (remoteChild == null) {
                row {
                    val pairedParentsCount = io.github.thake.soundnest.feature.parental.remote.settings.PairedDevicesStore
                        .pairedParentsFlow.collectAsState().value.size
                    M3SettingsNavRow(
                        icon = Icons.Rounded.Devices,
                        title = stringResource(R.string.parent_remote_section_title),
                        detail = if (pairedParentsCount > 0) {
                            stringResource(R.string.parent_remote_nav_detail_paired, pairedParentsCount)
                        } else {
                            stringResource(R.string.parent_remote_nav_detail_none)
                        },
                        palette = scaffoldPalette,
                        testTag = "parent_nav_family_remote",
                        onClick = { open(ParentSettingsPage.FamilyRemote) },
                    )
                }
            }
            row {
                M3SettingsNavRow(
                    icon = Icons.Rounded.Security,
                    title = stringResource(R.string.parent_section_security_backup),
                    detail = stringResource(R.string.parent_nav_security_backup_detail),
                    palette = scaffoldPalette,
                    testTag = "parent_nav_security_backup",
                    onClick = { open(ParentSettingsPage.SecurityBackup) },
                )
            }
        }
    }
}

