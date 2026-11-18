/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.parental.ui

import android.content.Context
import android.widget.Toast
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
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.GraphicEq
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
import io.github.thake.soundnest.feature.equalizer.settings.EqualizerSettingsUi
import io.github.thake.soundnest.feature.home.settings.m3HomeParentSettingsSections
import io.github.thake.soundnest.feature.library.settings.m3LibraryParentRow
import io.github.thake.soundnest.feature.localfiles.settings.LocalFilesSettingsUi
import io.github.thake.soundnest.feature.localfiles.settings.m3LocalFilesParentRow
import io.github.thake.soundnest.feature.lyrics.settings.m3LyricsParentRows
import io.github.thake.soundnest.feature.parental.security.AppSurface
import io.github.thake.soundnest.feature.parental.security.ParentSecurityManager
import io.github.thake.soundnest.feature.parental.settings.FilterMode
import io.github.thake.soundnest.feature.parental.settings.ParentalConfigBackup
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore
import io.github.thake.soundnest.feature.parental.settings.RuleLabelStore
import io.github.thake.soundnest.feature.player.settings.CrossfadePref
import io.github.thake.soundnest.feature.player.settings.PlaybackSettingsUi
import io.github.thake.soundnest.feature.player.settings.m3PlaybackParentRow
import io.github.thake.soundnest.feature.radio.settings.RadioSettingsUi
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

enum class ParentSettingsPage {
    Root,
    FilterRules,
    HomeSections,
    Spotify,
    Audible,
    LocalAndRadio,
    Transitions,
    SpeakersNetwork,
    SecurityBackup,
}

@Composable
fun ParentSettingsScreen(
    navController: NavController,
    listState: LazyListState = rememberLazyListState(),
    initialPage: ParentSettingsPage = ParentSettingsPage.Root,
) {
    val context = LocalContext.current
    val sessionTick by ParentSecurityManager.sessionTick.collectAsState()
    val unlocked = remember(sessionTick) { ParentSecurityManager.isAdminUnlocked(context) }
    val policy by ParentalPolicyStore.policyFlow.collectAsState()
    val audibleSummary by AudibleCredentialStore.summaryFlow.collectAsState()

    var networkRefresh by remember { mutableIntStateOf(0) }
    var rulesSheetApprovedTab by remember { mutableStateOf<Boolean?>(null) }
    var showAudiblePicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        AudibleCredentialStore.summary(context)
        ParentalPolicyStore.current(context)
        if (!ParentSecurityManager.isAdminUnlocked(context)) {
            ParentSecurityManager.runProtected(context, AppSurface.PARENT_CONTROLS) {}
        }
    }

    DisposableEffect(context, unlocked) {
        val onWifi = TrustedNetworkGate.currentSnapshot(context).onWifi
        if (unlocked && onWifi) runCatching { LanDiscovery.start(context) }
        onDispose {
            if (unlocked && onWifi) runCatching { LanDiscovery.stop() }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val jsonText = ParentalConfigBackup.exportJson(context)
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(jsonText.toByteArray(Charsets.UTF_8))
            } ?: error("Could not open file output stream")
        }.onSuccess {
            Toast.makeText(context, context.getString(R.string.parent_toast_export_success), Toast.LENGTH_SHORT).show()
        }.onFailure { e ->
            Toast.makeText(context, context.getString(R.string.parent_toast_export_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val raw = context.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            } ?: error("Could not read selected file")
            ParentalConfigBackup.importJson(context, raw).getOrThrow()
        }.onSuccess {
            networkRefresh++
            Toast.makeText(context, context.getString(R.string.parent_toast_import_success), Toast.LENGTH_SHORT).show()
        }.onFailure { e ->
            Toast.makeText(context, context.getString(R.string.parent_toast_import_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }

    if (!unlocked) {
        M3SettingsScaffold(
            title = stringResource(R.string.parent_settings_title),
            onBack = { navController.popBackStack() },
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
        ParentSecurityManager.lockNow(context)
        navController.popBackStack()
    }

    M3SubpageHost(
        initialPage = initialPage,
        rootPage = ParentSettingsPage.Root,
        valueOf = ParentSettingsPage::valueOf,
        onExit = { navController.popBackStack() },
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
                navController = navController,
                context = context,
                policy = policy,
                listState = pageListState,
                back = back,
                lockAndExit = lockAndExit,
                onOpenTransitions = { open(ParentSettingsPage.Transitions) },
            )
            ParentSettingsPage.Transitions -> PlaybackSettingsUi.M3CrossfadePage(back = back)
            ParentSettingsPage.SpeakersNetwork -> ParentSpeakersNetworkPage(
                context = context,
                policy = policy,
                sessionTick = sessionTick,
                networkRefresh = networkRefresh,
                listState = pageListState,
                back = back,
                lockAndExit = lockAndExit,
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
}

@Composable
private fun ParentLockExitClassicAction(
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
            text = stringResource(R.string.parent_action_lock_exit),
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
        title = stringResource(R.string.parent_settings_title),
        onBack = back,
        listState = listState,
        listTestTag = "parent_settings_list",
        classicActions = { ParentLockExitClassicAction(palette, lockAndExit) },
        immersiveActions = {
            ImmersiveBarAction(
                icon = Icons.Rounded.Lock,
                contentDescription = stringResource(R.string.parent_action_lock_exit),
                onClick = lockAndExit,
            )
        },
    ) { scaffoldPalette ->
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ParentFilterRulesPage(
    context: Context,
    policy: ParentalPolicy,
    listState: LazyListState,
    back: () -> Unit,
    lockAndExit: () -> Unit,
    onOpenRulesSheet: (Boolean) -> Unit,
) {
    val palette = rememberM3SettingsPalette()
    var uriInput by rememberSaveable { mutableStateOf("") }
    var keywordInput by rememberSaveable { mutableStateOf("") }
    var regexInput by rememberSaveable { mutableStateOf("") }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = palette.text,
        unfocusedTextColor = palette.text,
        focusedBorderColor = palette.accent,
        unfocusedBorderColor = palette.divider,
        focusedLabelColor = palette.accent,
        unfocusedLabelColor = palette.muted,
        cursorColor = palette.accent,
    )

    M3SettingsScaffold(
        title = stringResource(R.string.parent_section_filter_rules),
        onBack = back,
        listState = listState,
        listTestTag = "parent_settings_list",
        classicActions = { ParentLockExitClassicAction(palette, lockAndExit) },
        immersiveActions = {
            ImmersiveBarAction(
                icon = Icons.Rounded.Lock,
                contentDescription = stringResource(R.string.parent_action_lock_exit),
                onClick = lockAndExit,
            )
        },
    ) { scaffoldPalette ->

        m3SettingsSection(
            key = "parent_filter:mode",
            palette = scaffoldPalette,
            header = context.getString(R.string.parent_filter_section_mode),
        ) {
            row {
                M3SettingsChoiceRow(
                    title = stringResource(R.string.parent_mode_filtered_open_title),
                    detail = stringResource(R.string.parent_mode_filtered_open_subtitle),
                    selected = policy.filterMode == FilterMode.FILTERED_OPEN,
                    palette = scaffoldPalette,
                    onClick = {
                        ParentalPolicyStore.update(context) { it.copy(filterMode = FilterMode.FILTERED_OPEN) }
                    },
                )
            }
            row {
                M3SettingsChoiceRow(
                    title = stringResource(R.string.parent_mode_strict_allowlist_title),
                    detail = stringResource(R.string.parent_mode_strict_allowlist_subtitle),
                    selected = policy.filterMode == FilterMode.STRICT_ALLOWLIST,
                    palette = scaffoldPalette,
                    onClick = {
                        ParentalPolicyStore.update(context) { it.copy(filterMode = FilterMode.STRICT_ALLOWLIST) }
                    },
                )
            }
            row {
                M3SettingsSwitchRow(
                    title = stringResource(R.string.parent_filter_block_explicit_title),
                    detail = stringResource(R.string.parent_filter_block_explicit_subtitle),
                    checked = policy.blockExplicit,
                    palette = scaffoldPalette,
                    onCheckedChange = { checked ->
                        ParentalPolicyStore.update(context) { it.copy(blockExplicit = checked) }
                    },
                )
            }
        }

        m3SettingsSection(
            key = "parent_filter:lists",
            palette = scaffoldPalette,
            header = context.getString(R.string.parent_filter_section_lists),
            hasLeadingIconBadges = true,
        ) {
            row {
                val approvedSummary = buildRuleSummaryText(
                    context = context,
                    playlists = policy.allowedPlaylistIds.size,
                    albums = policy.allowedAlbumIds.size,
                    artists = policy.allowedArtistIds.size,
                    tracks = policy.allowedTrackIds.size,
                    shows = policy.spokenWord.podcasts.allowedShowIds.size,
                    audibleBooks = policy.spokenWord.audible.allowedAsins.size,
                )
                M3SettingsNavRow(
                    icon = Icons.Rounded.CheckCircle,
                    title = stringResource(R.string.parent_rules_approved_title),
                    detail = approvedSummary,
                    value = stringResource(R.string.parent_rules_manage_button),
                    valueColor = scaffoldPalette.accent,
                    palette = scaffoldPalette,
                    testTag = "parent_manage_approved_rules",
                    onClick = { onOpenRulesSheet(true) },
                )
            }
            row {
                val blockedSummary = buildRuleSummaryText(
                    context = context,
                    playlists = policy.blockedPlaylistIds.size,
                    albums = policy.blockedAlbumIds.size,
                    artists = policy.blockedArtistIds.size,
                    tracks = policy.blockedTrackIds.size,
                    shows = policy.spokenWord.podcasts.blockedShowIds.size,
                )
                M3SettingsNavRow(
                    icon = Icons.Rounded.Block,
                    title = stringResource(R.string.parent_rules_blocked_title),
                    detail = blockedSummary,
                    value = stringResource(R.string.parent_rules_manage_button),
                    valueColor = scaffoldPalette.destructive,
                    destructive = true,
                    palette = scaffoldPalette,
                    testTag = "parent_manage_blocked_rules",
                    onClick = { onOpenRulesSheet(false) },
                )
            }
        }

        item(key = "parent_filter:quick", contentType = "m3:block") {
            M3SectionBlock(
                palette = scaffoldPalette,
                header = context.getString(R.string.parent_filter_section_quick),
                footer = context.getString(R.string.parent_quick_tip),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.parent_quick_allow_block_title),
                        color = scaffoldPalette.text,
                        fontFamily = scaffoldPalette.fontFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    OutlinedTextField(
                        value = uriInput,
                        onValueChange = { uriInput = it },
                        label = {
                            Text(
                                text = stringResource(R.string.parent_quick_allow_block_placeholder),
                                fontFamily = scaffoldPalette.fontFamily,
                            )
                        },
                        singleLine = true,
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            enabled = uriInput.isNotBlank(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = scaffoldPalette.accent,
                                disabledContentColor = scaffoldPalette.muted.copy(alpha = 0.45f),
                            ),
                            onClick = {
                                val applied = applyQuickUriRule(context, uriInput, allow = true)
                                if (applied) {
                                    Toast.makeText(context, context.getString(R.string.parent_toast_added_allowlist), Toast.LENGTH_SHORT).show()
                                    uriInput = ""
                                } else {
                                    Toast.makeText(context, context.getString(R.string.parent_toast_unrecognized_link), Toast.LENGTH_SHORT).show()
                                }
                            },
                        ) {
                            Text(
                                text = stringResource(R.string.parent_action_allow),
                                fontFamily = scaffoldPalette.fontFamily,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        OutlinedButton(
                            enabled = uriInput.isNotBlank(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = scaffoldPalette.destructive,
                                disabledContentColor = scaffoldPalette.muted.copy(alpha = 0.45f),
                            ),
                            onClick = {
                                val applied = applyQuickUriRule(context, uriInput, allow = false)
                                if (applied) {
                                    Toast.makeText(context, context.getString(R.string.parent_toast_added_blocklist), Toast.LENGTH_SHORT).show()
                                    uriInput = ""
                                } else {
                                    Toast.makeText(context, context.getString(R.string.parent_toast_unrecognized_link), Toast.LENGTH_SHORT).show()
                                }
                            },
                        ) {
                            Text(
                                text = stringResource(R.string.parent_action_block),
                                fontFamily = scaffoldPalette.fontFamily,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }

        item(key = "parent_filter:patterns", contentType = "m3:block") {
            val buttonColors = m3FilledButtonColors(scaffoldPalette)
            M3SectionBlock(
                palette = scaffoldPalette,
                header = context.getString(R.string.parent_filter_section_patterns),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.parent_rules_keywords_title),
                        color = scaffoldPalette.text,
                        fontFamily = scaffoldPalette.fontFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = keywordInput,
                            onValueChange = { keywordInput = it },
                            label = {
                                Text(
                                    text = stringResource(R.string.parent_rules_keywords_placeholder),
                                    fontFamily = scaffoldPalette.fontFamily,
                                )
                            },
                            singleLine = true,
                            colors = fieldColors,
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            enabled = keywordInput.isNotBlank(),
                            onClick = {
                                ParentalPolicyStore.addBlockedKeyword(context, keywordInput)
                                keywordInput = ""
                            },
                            colors = buttonColors,
                        ) {
                            Text(
                                text = stringResource(R.string.parent_rules_keywords_add),
                                fontFamily = scaffoldPalette.fontFamily,
                            )
                        }
                    }
                    if (policy.blockedKeywords.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            policy.blockedKeywords.sorted().forEach { kw ->
                                M3RemovableChip(label = kw, palette = scaffoldPalette) {
                                    ParentalPolicyStore.removeBlockedKeyword(context, kw)
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(
                    color = scaffoldPalette.divider,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.parent_rules_regex_title),
                        color = scaffoldPalette.text,
                        fontFamily = scaffoldPalette.fontFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = regexInput,
                            onValueChange = { regexInput = it },
                            label = {
                                Text(
                                    text = stringResource(R.string.parent_rules_regex_placeholder),
                                    fontFamily = scaffoldPalette.fontFamily,
                                )
                            },
                            singleLine = true,
                            colors = fieldColors,
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            enabled = regexInput.isNotBlank(),
                            onClick = {
                                val valid = runCatching { Regex(regexInput.trim(), RegexOption.IGNORE_CASE) }.isSuccess
                                if (!valid) {
                                    Toast.makeText(context, context.getString(R.string.parent_rules_regex_error), Toast.LENGTH_SHORT).show()
                                } else {
                                    ParentalPolicyStore.addBlockedRegex(context, regexInput)
                                    regexInput = ""
                                }
                            },
                            colors = buttonColors,
                        ) {
                            Text(
                                text = stringResource(R.string.parent_rules_keywords_add),
                                fontFamily = scaffoldPalette.fontFamily,
                            )
                        }
                    }
                    if (policy.blockedRegexPatterns.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            policy.blockedRegexPatterns.sorted().forEach { rx ->
                                M3RemovableChip(label = "/$rx/", palette = scaffoldPalette) {
                                    ParentalPolicyStore.removeBlockedRegex(context, rx)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ParentHomeSectionsPage(
    context: Context,
    policy: ParentalPolicy,
    listState: LazyListState,
    back: () -> Unit,
    lockAndExit: () -> Unit,
) {
    val palette = rememberM3SettingsPalette()
    M3SettingsScaffold(
        title = stringResource(R.string.parent_section_home),
        onBack = back,
        listState = listState,
        listTestTag = "parent_settings_list",
        classicActions = { ParentLockExitClassicAction(palette, lockAndExit) },
        immersiveActions = {
            ImmersiveBarAction(
                icon = Icons.Rounded.Lock,
                contentDescription = stringResource(R.string.parent_action_lock_exit),
                onClick = lockAndExit,
            )
        },
    ) { scaffoldPalette ->
        m3HomeParentSettingsSections(
            context = context,
            policy = policy,
            palette = scaffoldPalette,
        )
    }
}

@Composable
private fun ParentSpotifyPage(
    context: Context,
    policy: ParentalPolicy,
    listState: LazyListState,
    back: () -> Unit,
    lockAndExit: () -> Unit,
) {
    val palette = rememberM3SettingsPalette()
    M3SettingsScaffold(
        title = stringResource(R.string.parent_section_spotify),
        onBack = back,
        listState = listState,
        listTestTag = "parent_settings_list",
        classicActions = { ParentLockExitClassicAction(palette, lockAndExit) },
        immersiveActions = {
            ImmersiveBarAction(
                icon = Icons.Rounded.Lock,
                contentDescription = stringResource(R.string.parent_action_lock_exit),
                onClick = lockAndExit,
            )
        },
    ) { scaffoldPalette ->
        m3SettingsSection(
            key = "parent_spotify:library",
            palette = scaffoldPalette,
            header = context.getString(R.string.parent_features_section_library),
        ) {
            m3LibraryParentRow(context, policy, scaffoldPalette)
            m3PlaybackParentRow(context, policy, scaffoldPalette)
        }

        m3SettingsSection(
            key = "parent_spotify:spoken",
            palette = scaffoldPalette,
            header = context.getString(R.string.parent_features_section_podcasts_audiobooks),
        ) {
            m3PodcastParentRows(context, policy, scaffoldPalette)
        }

        m3SettingsSection(
            key = "parent_spotify:social",
            palette = scaffoldPalette,
            header = context.getString(R.string.parent_features_section_social),
        ) {
            m3JamParentRow(context, policy, scaffoldPalette)
        }

        m3SettingsSection(
            key = "parent_spotify:lyrics",
            palette = scaffoldPalette,
            header = context.getString(R.string.parent_features_section_lyrics),
        ) {
            m3LyricsParentRows(context, policy, scaffoldPalette)
        }
    }
}

@Composable
private fun ParentAudiblePage(
    context: Context,
    policy: ParentalPolicy,
    listState: LazyListState,
    back: () -> Unit,
    lockAndExit: () -> Unit,
    onOpenAudiblePicker: () -> Unit,
) {
    val palette = rememberM3SettingsPalette()
    val home = policy.home
    val audible = policy.spokenWord.audible
    M3SettingsScaffold(
        title = stringResource(R.string.parent_section_audible),
        onBack = back,
        listState = listState,
        listTestTag = "parent_settings_list",
        classicActions = { ParentLockExitClassicAction(palette, lockAndExit) },
        immersiveActions = {
            ImmersiveBarAction(
                icon = Icons.Rounded.Lock,
                contentDescription = stringResource(R.string.parent_action_lock_exit),
                onClick = lockAndExit,
            )
        },
    ) { scaffoldPalette ->
        m3SettingsSection(
            key = "parent_audible:access",
            palette = scaffoldPalette,
            header = context.getString(R.string.parent_audible_section_access),
        ) {
            row {
                M3SettingsSwitchRow(
                    title = stringResource(R.string.parent_audible_audiobooks_title),
                    detail = stringResource(R.string.parent_audible_audiobooks_subtitle),
                    checked = policy.spokenWord.allowAudible,
                    palette = scaffoldPalette,
                    switchTestTag = "parent_audible_audiobooks_toggle",
                    onCheckedChange = { v ->
                        ParentalPolicyStore.update(context) { it.copy(spokenWord = it.spokenWord.copy(allowAudible = v)) }
                    },
                )
            }
            row {
                M3SettingsSwitchRow(
                    title = stringResource(R.string.audible_home_pinned_toggle_title),
                    detail = stringResource(R.string.audible_home_pinned_toggle_subtitle),
                    checked = home.showParentPicks && audible.showPinnedOnHome,
                    palette = scaffoldPalette,
                    enabled = home.showParentPicks,
                    switchTestTag = "parent_audible_home_pinned_toggle",
                    onCheckedChange = { v ->
                        ParentalPolicyStore.update(context) {
                            it.copy(
                                spokenWord = it.spokenWord.copy(
                                    audible = it.spokenWord.audible.copy(showPinnedOnHome = v),
                                ),
                            )
                        }
                    },
                )
            }
        }

        item(key = "parent_audible:account", contentType = "m3:block") {
            M3SectionBlock(
                palette = scaffoldPalette,
                header = context.getString(R.string.parent_audible_section_account),
            ) {
                Box(Modifier.padding(12.dp)) {
                    AudibleParentControlsCard(
                        audibleEnabled = policy.spokenWord.allowAudible,
                        onOpenPicker = onOpenAudiblePicker,
                    )
                }
            }
        }
    }
}

@Composable
private fun ParentLocalAndRadioPage(
    navController: NavController,
    context: Context,
    policy: ParentalPolicy,
    listState: LazyListState,
    back: () -> Unit,
    lockAndExit: () -> Unit,
    onOpenTransitions: () -> Unit,
) {
    val palette = rememberM3SettingsPalette()
    M3SettingsScaffold(
        title = stringResource(R.string.settings_section_local_radio),
        onBack = back,
        listState = listState,
        listTestTag = "parent_settings_list",
        classicActions = { ParentLockExitClassicAction(palette, lockAndExit) },
        immersiveActions = {
            ImmersiveBarAction(
                icon = Icons.Rounded.Lock,
                contentDescription = stringResource(R.string.parent_action_lock_exit),
                onClick = lockAndExit,
            )
        },
    ) { scaffoldPalette ->
        m3SettingsSection(
            key = "local_radio:sources",
            palette = scaffoldPalette,
            header = context.getString(R.string.local_radio_section_sources),
        ) {
            m3LocalFilesParentRow(context, policy, scaffoldPalette)
            m3RadioParentRow(context, policy, scaffoldPalette)
        }

        if (policy.localFiles.enabled || policy.radio.enabled) {
            m3SettingsSection(
                key = "local_radio:visibility",
                palette = scaffoldPalette,
                header = context.getString(R.string.local_radio_section_visibility),
            ) {
                with(LocalFilesSettingsUi) { m3LibraryRow(context, policy, scaffoldPalette) }
                with(RadioSettingsUi) { m3LibraryRow(context, policy, scaffoldPalette) }
            }

            m3SettingsSection(
                key = "local_radio:audio",
                palette = scaffoldPalette,
                header = context.getString(R.string.local_radio_section_audio),
                hasLeadingIconBadges = true,
            ) {
                with(EqualizerSettingsUi) {
                    m3SettingsRow(navController = navController, policy = policy, palette = scaffoldPalette)
                }
                if (policy.localFiles.enabled) {
                    row {
                        M3SettingsNavRow(
                            icon = Icons.Rounded.GraphicEq,
                            title = stringResource(R.string.settings_transitions_title),
                            detail = stringResource(R.string.settings_transitions_detail_soundnest),
                            value = PlaybackSettingsUi.transitionValue(CrossfadePref.seconds),
                            palette = scaffoldPalette,
                            testTag = "local_radio_transitions_nav",
                            onClick = onOpenTransitions,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ParentSpeakersNetworkPage(
    context: Context,
    policy: ParentalPolicy,
    sessionTick: Long,
    networkRefresh: Int,
    listState: LazyListState,
    back: () -> Unit,
    lockAndExit: () -> Unit,
) {
    val palette = rememberM3SettingsPalette()
    val speakersState = rememberSpeakersParentUiState(
        policy = policy,
        sessionTick = sessionTick,
        externalRefreshKey = networkRefresh,
    )
    M3SettingsScaffold(
        title = stringResource(R.string.parent_speakers_trusted_title),
        onBack = back,
        listState = listState,
        listTestTag = "parent_settings_list",
        classicActions = { ParentLockExitClassicAction(palette, lockAndExit) },
        immersiveActions = {
            ImmersiveBarAction(
                icon = Icons.Rounded.Lock,
                contentDescription = stringResource(R.string.parent_action_lock_exit),
                onClick = lockAndExit,
            )
        },
    ) { scaffoldPalette ->
        m3SpeakersParentSettingsSections(
            context = context,
            policy = policy,
            state = speakersState,
            palette = scaffoldPalette,
        )
    }
    SpeakersNicknameDialogHost(
        state = speakersState,
        palette = palette,
    )
}

@Composable
private fun ParentSecurityBackupPage(
    context: Context,
    listState: LazyListState,
    back: () -> Unit,
    lockAndExit: () -> Unit,
    onExportJson: () -> Unit,
    onImportJson: () -> Unit,
) {
    val palette = rememberM3SettingsPalette()
    M3SettingsScaffold(
        title = stringResource(R.string.parent_section_security_backup),
        onBack = back,
        listState = listState,
        listTestTag = "parent_settings_list",
        classicActions = { ParentLockExitClassicAction(palette, lockAndExit) },
        immersiveActions = {
            ImmersiveBarAction(
                icon = Icons.Rounded.Lock,
                contentDescription = stringResource(R.string.parent_action_lock_exit),
                onClick = lockAndExit,
            )
        },
    ) { scaffoldPalette ->
        item(key = "parent_security:session", contentType = "m3:block") {
            M3SectionBlock(
                palette = scaffoldPalette,
                header = context.getString(R.string.parent_section_lock_session),
            ) {
                Box(Modifier.padding(16.dp)) {
                    ParentSecurityControlsSection()
                }
            }
        }
        item(key = "parent_security:backup", contentType = "m3:block") {
            M3SectionBlock(
                palette = scaffoldPalette,
                header = context.getString(R.string.parent_backup_section_title),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.parent_backup_section_subtitle),
                        color = scaffoldPalette.muted,
                        fontFamily = scaffoldPalette.fontFamily,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onExportJson,
                            colors = m3FilledButtonColors(scaffoldPalette),
                        ) {
                            Text(
                                text = stringResource(R.string.parent_action_export_json),
                                fontFamily = scaffoldPalette.fontFamily,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        OutlinedButton(onClick = onImportJson) {
                            Text(
                                text = stringResource(R.string.parent_action_import_json),
                                color = scaffoldPalette.text,
                                fontFamily = scaffoldPalette.fontFamily,
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class ManagedRuleEntry(
    val kind: String,
    val id: String,
    val title: String,
    val subtitle: String,
)

private fun buildRuleSummaryText(
    context: Context,
    playlists: Int,
    albums: Int,
    artists: Int,
    tracks: Int,
    shows: Int,
    audibleBooks: Int = 0,
): String {
    val total = playlists + albums + artists + tracks + shows + audibleBooks
    if (total == 0) return context.getString(R.string.parent_rules_summary_none)
    val res = context.resources
    val parts = buildList {
        if (playlists > 0) add(res.getQuantityString(R.plurals.parent_rules_summary_playlists, playlists, playlists))
        if (albums > 0) add(res.getQuantityString(R.plurals.parent_rules_summary_albums, albums, albums))
        if (artists > 0) add(res.getQuantityString(R.plurals.parent_rules_summary_artists, artists, artists))
        if (tracks > 0) add(res.getQuantityString(R.plurals.parent_rules_summary_songs, tracks, tracks))
        if (shows > 0) add(res.getQuantityString(R.plurals.parent_rules_summary_shows, shows, shows))
        if (audibleBooks > 0) add(res.getQuantityString(R.plurals.parent_rules_summary_audible, audibleBooks, audibleBooks))
    }
    return parts.joinToString(" · ")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ManageContentRulesSheet(
    initialTabIsApproved: Boolean,
    onDismiss: () -> Unit,
) {
    val palette = rememberM3SettingsPalette()
    LookSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.elevatedCardBg,
    ) {
        ManageContentRulesSheetBody(
            initialTabIsApproved = initialTabIsApproved,
            onDismiss = onDismiss,
        )
    }
}

@Composable
internal fun ManageContentRulesSheetBody(
    initialTabIsApproved: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val palette = rememberM3SettingsPalette()
    val policy by ParentalPolicyStore.policyFlow.collectAsState()
    var showApproved by remember(initialTabIsApproved) { mutableStateOf(initialTabIsApproved) }
    var searchQuery by remember { mutableStateOf("") }

    val approvedChipColors = m3FilterChipColors(palette)
    val blockedChipColors = m3FilterChipColors(palette, selectedContainerColor = palette.destructive)

    val approvedEntries = remember(policy) {
        collectManagedRuleEntries(
            context = context,
            playlists = policy.allowedPlaylistIds,
            albums = policy.allowedAlbumIds,
            artists = policy.allowedArtistIds,
            tracks = policy.allowedTrackIds,
            shows = policy.spokenWord.podcasts.allowedShowIds,
            audibleAsins = policy.spokenWord.audible.allowedAsins,
        )
    }
    val blockedEntries = remember(policy) {
        collectManagedRuleEntries(
            context = context,
            playlists = policy.blockedPlaylistIds,
            albums = policy.blockedAlbumIds,
            artists = policy.blockedArtistIds,
            tracks = policy.blockedTrackIds,
            shows = policy.spokenWord.podcasts.blockedShowIds,
        )
    }

    val activeEntries = if (showApproved) approvedEntries else blockedEntries
    val filteredEntries = remember(activeEntries, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) activeEntries
        else activeEntries.filter { entry ->
            entry.title.lowercase().contains(q) ||
                entry.subtitle.lowercase().contains(q) ||
                entry.id.lowercase().contains(q) ||
                entry.kind.lowercase().contains(q)
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.parent_manage_rules_title),
                color = palette.text,
                fontFamily = palette.fontFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), tint = palette.muted)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = showApproved,
                onClick = { showApproved = true },
                colors = approvedChipColors,
                leadingIcon = if (showApproved) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null,
                label = {
                    Text(
                        stringResource(R.string.parent_rules_approved_tab_count, approvedEntries.size),
                        fontFamily = palette.fontFamily,
                        fontWeight = if (showApproved) FontWeight.Bold else FontWeight.Normal,
                    )
                },
                modifier = Modifier.testTag("rules_tab_approved"),
            )
            FilterChip(
                selected = !showApproved,
                onClick = { showApproved = false },
                colors = blockedChipColors,
                leadingIcon = if (!showApproved) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null,
                label = {
                    Text(
                        stringResource(R.string.parent_rules_blocked_tab_count, blockedEntries.size),
                        fontFamily = palette.fontFamily,
                        fontWeight = if (!showApproved) FontWeight.Bold else FontWeight.Normal,
                    )
                },
                modifier = Modifier.testTag("rules_tab_blocked"),
            )
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    stringResource(R.string.parent_rules_search_placeholder),
                    color = palette.muted,
                    fontFamily = palette.fontFamily,
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = palette.text,
                unfocusedTextColor = palette.text,
                focusedBorderColor = palette.accent,
                unfocusedBorderColor = palette.divider,
                cursorColor = palette.accent,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("rules_search_input"),
        )

        if (filteredEntries.isEmpty()) {
            val emptyMessage = when {
                searchQuery.isNotBlank() -> stringResource(R.string.parent_rules_no_match, searchQuery.trim())
                showApproved -> stringResource(R.string.parent_rules_empty_approved)
                else -> stringResource(R.string.parent_rules_empty_blocked)
            }
            Text(
                text = emptyMessage,
                color = palette.muted,
                fontFamily = palette.fontFamily,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 20.dp),
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
            ) {
                items(filteredEntries, key = { "${it.kind}:${it.id}" }) { entry ->
                    val badgeColor = if (showApproved) palette.accent else palette.destructive
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(palette.innerCardBg)
                            .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                    ) {
                        Text(
                            text = RuleLabelStore.kindDisplayName(entry.kind),
                            color = badgeColor,
                            fontFamily = palette.fontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(badgeColor.copy(alpha = 0.16f))
                                .padding(horizontal = 7.dp, vertical = 3.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = entry.title,
                                color = palette.text,
                                fontFamily = palette.fontFamily,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (entry.subtitle.isNotBlank()) {
                                Text(
                                    text = entry.subtitle,
                                    color = palette.muted,
                                    fontFamily = palette.fontFamily,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        IconButton(
                            onClick = { clearManagedRule(context, entry.kind, entry.id) },
                            modifier = Modifier.testTag("rule_remove_${entry.kind}_${entry.id}"),
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = stringResource(R.string.parent_action_remove_rule),
                                tint = palette.muted,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun collectManagedRuleEntries(
    context: Context,
    playlists: Set<String>,
    albums: Set<String>,
    artists: Set<String>,
    tracks: Set<String>,
    shows: Set<String>,
    audibleAsins: Set<String> = emptySet(),
): List<ManagedRuleEntry> {
    fun entriesFor(kind: String, ids: Set<String>): List<ManagedRuleEntry> =
        ids.map { id ->
            val label = RuleLabelStore.resolve(context, kind, id)
            ManagedRuleEntry(kind = kind, id = id, title = label.title, subtitle = label.subtitle)
        }.sortedBy { it.title.lowercase() }

    return buildList {
        addAll(entriesFor("playlist", playlists))
        addAll(entriesFor("album", albums))
        addAll(entriesFor("artist", artists))
        addAll(entriesFor("track", tracks))
        addAll(entriesFor("show", shows))
        addAll(entriesFor("audible", audibleAsins))
    }
}

private fun clearManagedRule(context: Context, kind: String, id: String) {
    when (kind) {
        "playlist" -> ParentalPolicyStore.clearPlaylistRule(context, id)
        "album" -> ParentalPolicyStore.clearAlbumRule(context, id)
        "artist" -> ParentalPolicyStore.clearArtistRule(context, id)
        "track" -> ParentalPolicyStore.clearTrackRule(context, id)
        "show" -> ParentalPolicyStore.clearShowRule(context, id)
        "audible" -> ParentalPolicyStore.setAudibleBookAllowed(context, id, false)
    }
}

private fun applyQuickUriRule(context: Context, raw: String, allow: Boolean): Boolean {
    val trimmed = raw.trim()
    val kind = when {
        trimmed.contains(":track:") || trimmed.contains("/track/") -> "track"
        trimmed.contains(":album:") || trimmed.contains("/album/") -> "album"
        trimmed.contains(":artist:") || trimmed.contains("/artist/") -> "artist"
        trimmed.contains(":playlist:") || trimmed.contains("/playlist/") -> "playlist"
        trimmed.contains(":show:") || trimmed.contains("/show/") -> "show"
        else -> return false
    }
    val id = ParentalPolicyStore.normalizeSpotifyId(trimmed)
    if (id.isEmpty()) return false
    when (kind) {
        "track" -> if (allow) ParentalPolicyStore.allowTrack(context, id) else ParentalPolicyStore.blockTrack(context, id)
        "album" -> if (allow) ParentalPolicyStore.allowAlbum(context, id) else ParentalPolicyStore.blockAlbum(context, id)
        "artist" -> if (allow) ParentalPolicyStore.allowArtist(context, id) else ParentalPolicyStore.blockArtist(context, id)
        "playlist" -> if (allow) ParentalPolicyStore.allowPlaylist(context, id) else ParentalPolicyStore.blockPlaylist(context, id)
        "show" -> if (allow) ParentalPolicyStore.allowShow(context, id) else ParentalPolicyStore.blockShow(context, id)
    }
    return true
}
