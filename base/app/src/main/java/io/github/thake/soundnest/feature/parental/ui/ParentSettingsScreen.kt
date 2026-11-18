/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.parental.ui

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.ui.classic.AppAccent
import io.github.thake.soundnest.core.ui.classic.AppBackground
import io.github.thake.soundnest.core.ui.classic.AppMuted
import io.github.thake.soundnest.core.ui.classic.AppText
import io.github.thake.soundnest.core.ui.classic.tone
import io.github.thake.soundnest.core.ui.look.LookSheet
import io.github.thake.soundnest.core.ui.look.bottomBarsSpace
import io.github.thake.soundnest.feature.home.settings.HomeParentSettingsSection
import io.github.thake.soundnest.feature.library.settings.LibraryParentSettingsRow
import io.github.thake.soundnest.feature.localfiles.settings.LocalFilesParentSettingsRow
import io.github.thake.soundnest.feature.lyrics.settings.LyricsParentSettingsRows
import io.github.thake.soundnest.feature.parental.security.AppSurface
import io.github.thake.soundnest.feature.parental.security.ParentSecurityManager
import io.github.thake.soundnest.feature.parental.settings.FilterMode
import io.github.thake.soundnest.feature.parental.settings.ParentalConfigBackup
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore
import io.github.thake.soundnest.feature.parental.settings.RuleLabelStore
import io.github.thake.soundnest.feature.player.settings.PlaybackParentSettingsRow
import io.github.thake.soundnest.feature.radio.settings.RadioParentSettingsRow
import io.github.thake.soundnest.feature.speakers.connect.LanDiscovery
import io.github.thake.soundnest.feature.speakers.jam.settings.JamParentSettingsRow
import io.github.thake.soundnest.feature.speakers.settings.SpeakersParentSettingsSection
import io.github.thake.soundnest.feature.speakers.settings.TrustedNetworkGate
import io.github.thake.soundnest.feature.spokenword.audible.settings.AudibleParentControlsCard
import io.github.thake.soundnest.feature.spokenword.audible.ui.AudiblePickerSheet
import io.github.thake.soundnest.feature.spokenword.podcasts.settings.PodcastParentSettingsRows

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ParentSettingsScreen(
    navController: NavController,
    listState: LazyListState = rememberLazyListState(),
) {
    val context = LocalContext.current
    val sessionTick by ParentSecurityManager.sessionTick.collectAsState()
    val unlocked = remember(sessionTick) { ParentSecurityManager.isAdminUnlocked(context) }
    val policy by ParentalPolicyStore.policyFlow.collectAsState()

    var networkRefresh by remember { mutableIntStateOf(0) }
    var rulesSheetApprovedTab by remember { mutableStateOf<Boolean?>(null) }
    var showAudiblePicker by remember { mutableStateOf(false) }

    val selectedChipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = AppAccent,
        selectedLabelColor = Color.Black,
        selectedLeadingIconColor = Color.Black,
    )
    val accentFilledButtonColors = ButtonDefaults.buttonColors(
        containerColor = AppAccent,
        contentColor = Color.Black,
        disabledContainerColor = tone(0xFF2E2E2E),
        disabledContentColor = AppMuted.copy(alpha = 0.5f),
    )

    LaunchedEffect(Unit) {
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
            Toast.makeText(context, "Parental configuration exported.", Toast.LENGTH_SHORT).show()
        }.onFailure { e ->
            Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
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
            Toast.makeText(context, "Parental configuration imported (PIN preserved).", Toast.LENGTH_SHORT).show()
        }.onFailure { e ->
            Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(AppBackground.toArgb()))
            .statusBarsPadding(),
    ) {
        // Header bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AppText)
            }
            Text(
                text = "Parental Controls",
                color = AppText,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (unlocked) {
                OutlinedButton(
                    onClick = {
                        ParentSecurityManager.lockNow(context)
                        navController.popBackStack()
                    },
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = AppAccent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Lock & Exit", color = AppText, fontSize = 13.sp)
                }
            }
        }

        if (!unlocked) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = AppAccent, modifier = Modifier.size(48.dp))
                    Text("Parent Mode Locked", color = AppText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Enter your Parent PIN or password to configure content filters, Home sections, features, speakers, or backups.",
                        color = AppMuted,
                        fontSize = 14.sp,
                    )
                    Button(
                        onClick = { ParentSecurityManager.runProtected(context, AppSurface.PARENT_CONTROLS) {} },
                        colors = accentFilledButtonColors,
                    ) {
                        Text("Unlock Parent Mode", fontWeight = FontWeight.Bold)
                    }
                }
            }
            return@Column
        }

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomBarsSpace() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize().navigationBarsPadding().testTag("parent_settings_list"),
        ) {
            // 1. Session & PIN Security
            item(key = "security") {
                SectionCard("Parent Lock & Session") {
                    ParentSecurityControlsSection()
                }
            }

            // 2. Content Filter Mode & Rules
            item(key = "filter_rules") {
                SectionCard("Content Filter & Rules") {
                    Text("Filter Mode", color = AppText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val isOpen = policy.filterMode == FilterMode.FILTERED_OPEN
                        val isStrict = policy.filterMode == FilterMode.STRICT_ALLOWLIST
                        FilterChip(
                            selected = isOpen,
                            onClick = {
                                ParentalPolicyStore.update(context) { it.copy(filterMode = FilterMode.FILTERED_OPEN) }
                            },
                            label = { Text("Filtered Open", fontWeight = if (isOpen) FontWeight.Bold else FontWeight.Normal) },
                            leadingIcon = if (isOpen) { { Icon(Icons.Default.Check, contentDescription = null) } } else null,
                            colors = selectedChipColors,
                        )
                        FilterChip(
                            selected = isStrict,
                            onClick = {
                                ParentalPolicyStore.update(context) { it.copy(filterMode = FilterMode.STRICT_ALLOWLIST) }
                            },
                            label = { Text("Strict Allowlist", fontWeight = if (isStrict) FontWeight.Bold else FontWeight.Normal) },
                            leadingIcon = if (isStrict) { { Icon(Icons.Default.Check, contentDescription = null) } } else null,
                            colors = selectedChipColors,
                        )
                    }
                    Text(
                        text = if (policy.filterMode == FilterMode.FILTERED_OPEN) {
                            "Allows general catalog while blocking Spotify Explicit items, blocked artists/albums/tracks, keywords, and regexes."
                        } else {
                            "Blocks everything unless its track, album, artist, or playlist is explicitly approved. Autoplay & Radio are disabled."
                        },
                        color = AppMuted,
                        fontSize = 12.sp,
                    )

                    PolicyToggleRow(
                        title = "Block Explicit Content",
                        subtitle = "Hide and block playback of tracks and episodes marked Explicit by Spotify",
                        checked = policy.blockExplicit,
                    ) { checked ->
                        ParentalPolicyStore.update(context) { it.copy(blockExplicit = checked) }
                    }

                    HorizontalDivider(color = tone(0xFF3A3A3A))

                    // Quick Spotify URI / Link rule adder
                    var uriInput by remember { mutableStateOf("") }
                    Text("Quick Allow / Block by Spotify Link or URI", color = AppText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = uriInput,
                        onValueChange = { uriInput = it },
                        label = { Text("Paste spotify:... or open.spotify.com/... link") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            enabled = uriInput.isNotBlank(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = AppAccent,
                                disabledContentColor = AppMuted.copy(alpha = 0.45f),
                            ),
                            onClick = {
                                val applied = applyQuickUriRule(context, uriInput, allow = true)
                                if (applied) {
                                    Toast.makeText(context, "Added to Allowlist.", Toast.LENGTH_SHORT).show()
                                    uriInput = ""
                                } else {
                                    Toast.makeText(context, "Unrecognized Spotify link/URI.", Toast.LENGTH_SHORT).show()
                                }
                            },
                        ) { Text("Allow", fontWeight = FontWeight.SemiBold) }
                        OutlinedButton(
                            enabled = uriInput.isNotBlank(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFFFF6B6B),
                                disabledContentColor = AppMuted.copy(alpha = 0.45f),
                            ),
                            onClick = {
                                val applied = applyQuickUriRule(context, uriInput, allow = false)
                                if (applied) {
                                    Toast.makeText(context, "Added to Blocklist.", Toast.LENGTH_SHORT).show()
                                    uriInput = ""
                                } else {
                                    Toast.makeText(context, "Unrecognized Spotify link/URI.", Toast.LENGTH_SHORT).show()
                                }
                            },
                        ) { Text("Block", fontWeight = FontWeight.SemiBold) }
                    }

                    Text(
                        text = "Tip: While Parent Mode is unlocked, you can also block any song, artist, playlist, album, or show directly from Home, Search, Library, or detail pages.",
                        color = AppMuted,
                        fontSize = 12.sp,
                    )

                    // Interactive Rule Manager summary cards
                    RuleSummaryCard(
                        title = "Approved Items",
                        summary = buildRuleSummaryText(
                            playlists = policy.allowedPlaylistIds.size,
                            albums = policy.allowedAlbumIds.size,
                            artists = policy.allowedArtistIds.size,
                            tracks = policy.allowedTrackIds.size,
                            shows = policy.spokenWord.podcasts.allowedShowIds.size,
                            audibleBooks = policy.spokenWord.audible.allowedAsins.size,
                        ),
                        accentColor = AppAccent,
                        testTag = "parent_manage_approved_rules",
                        onClick = { rulesSheetApprovedTab = true },
                    )
                    RuleSummaryCard(
                        title = "Blocked Items",
                        summary = buildRuleSummaryText(
                            playlists = policy.blockedPlaylistIds.size,
                            albums = policy.blockedAlbumIds.size,
                            artists = policy.blockedArtistIds.size,
                            tracks = policy.blockedTrackIds.size,
                            shows = policy.spokenWord.podcasts.blockedShowIds.size,
                        ),
                        accentColor = Color(0xFFFF6B6B),
                        testTag = "parent_manage_blocked_rules",
                        onClick = { rulesSheetApprovedTab = false },
                    )

                    HorizontalDivider(color = tone(0xFF3A3A3A))

                    // Blocked Keywords
                    var keywordInput by remember { mutableStateOf("") }
                    Text("Blocked Title / Artist Keywords", color = AppText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = keywordInput,
                            onValueChange = { keywordInput = it },
                            label = { Text("Add blocked word or phrase") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            enabled = keywordInput.isNotBlank(),
                            onClick = {
                                ParentalPolicyStore.addBlockedKeyword(context, keywordInput)
                                keywordInput = ""
                            },
                            colors = accentFilledButtonColors,
                        ) { Text("Add") }
                    }
                    if (policy.blockedKeywords.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            policy.blockedKeywords.sorted().forEach { kw ->
                                RemovableChip(label = kw) { ParentalPolicyStore.removeBlockedKeyword(context, kw) }
                            }
                        }
                    }

                    // Blocked Regexes
                    var regexInput by remember { mutableStateOf("") }
                    Text("Advanced Blocked Regex Patterns", color = AppText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = regexInput,
                            onValueChange = { regexInput = it },
                            label = { Text("Case-insensitive regex") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            enabled = regexInput.isNotBlank(),
                            onClick = {
                                val valid = runCatching { Regex(regexInput.trim(), RegexOption.IGNORE_CASE) }.isSuccess
                                if (!valid) {
                                    Toast.makeText(context, "Invalid regular expression.", Toast.LENGTH_SHORT).show()
                                } else {
                                    ParentalPolicyStore.addBlockedRegex(context, regexInput)
                                    regexInput = ""
                                }
                            },
                            colors = accentFilledButtonColors,
                        ) { Text("Add") }
                    }
                    if (policy.blockedRegexPatterns.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            policy.blockedRegexPatterns.sorted().forEach { rx ->
                                RemovableChip(label = "/$rx/") { ParentalPolicyStore.removeBlockedRegex(context, rx) }
                            }
                        }
                    }
                }
            }

            // 3. Front Page (Home) Sections
            item(key = "home_sections") {
                HomeParentSettingsSection(policy)
            }

            // 4. Feature Access Gates (composed from each owning feature!)
            item(key = "feature_gates") {
                SectionCard("Feature Access & Library Protection") {
                    LibraryParentSettingsRow(policy)
                    PlaybackParentSettingsRow(policy)
                    PodcastParentSettingsRows(policy)
                    PolicyToggleRow(
                        title = stringResource(R.string.parent_audible_audiobooks_title),
                        subtitle = stringResource(R.string.parent_audible_audiobooks_subtitle),
                        checked = policy.spokenWord.allowAudible,
                        switchTestTag = "parent_audible_audiobooks_toggle",
                    ) { v ->
                        ParentalPolicyStore.update(context) { it.copy(spokenWord = it.spokenWord.copy(allowAudible = v)) }
                    }
                    AudibleParentControlsCard(
                        audibleEnabled = policy.spokenWord.allowAudible,
                        onOpenPicker = { showAudiblePicker = true },
                    )
                    RadioParentSettingsRow(policy)
                    LocalFilesParentSettingsRow(policy)
                    JamParentSettingsRow(policy)
                    LyricsParentSettingsRows(policy)
                }
            }

            // 5. Trusted Home Network & Speakers
            item(key = "speakers_network") {
                SpeakersParentSettingsSection(
                    policy = policy,
                    sessionTick = sessionTick,
                    externalRefreshKey = networkRefresh,
                )
            }

            // 6. Sibling Device JSON Backup & Restore
            item(key = "backup_restore") {
                SectionCard("Backup & Restore (Sibling Devices)") {
                    Text(
                        "Export your content rules, Home layout, Trusted Wi-Fi, and speaker nicknames to a JSON file to quickly set up another child's device. Parent PINs are never exported or overwritten.",
                        color = AppMuted,
                        fontSize = 12.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { exportLauncher.launch("soundnest-policy.json") },
                            colors = accentFilledButtonColors,
                        ) {
                            Text("Export JSON", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                        ) {
                            Text("Import JSON", color = AppText)
                        }
                    }
                }
            }
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

private data class ManagedRuleEntry(
    val kind: String,
    val id: String,
    val title: String,
    val subtitle: String,
)

private fun buildRuleSummaryText(
    playlists: Int,
    albums: Int,
    artists: Int,
    tracks: Int,
    shows: Int,
    audibleBooks: Int = 0,
): String {
    val total = playlists + albums + artists + tracks + shows + audibleBooks
    if (total == 0) return "None yet · Tap to inspect"
    val parts = buildList {
        if (playlists > 0) add("$playlists playlist${if (playlists == 1) "" else "s"}")
        if (albums > 0) add("$albums album${if (albums == 1) "" else "s"}")
        if (artists > 0) add("$artists artist${if (artists == 1) "" else "s"}")
        if (tracks > 0) add("$tracks song${if (tracks == 1) "" else "s"}")
        if (shows > 0) add("$shows show${if (shows == 1) "" else "s"}")
        if (audibleBooks > 0) add("$audibleBooks Audible book${if (audibleBooks == 1) "" else "s"}")
    }
    return parts.joinToString(" · ")
}

@Composable
private fun RuleSummaryCard(
    title: String,
    summary: String,
    accentColor: Color,
    testTag: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(tone(0xFF282828))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag(testTag),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = accentColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(summary, color = AppMuted, fontSize = 12.sp)
        }
        Text("Manage", color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ManageContentRulesSheet(
    initialTabIsApproved: Boolean,
    onDismiss: () -> Unit,
) {
    LookSheet(
        onDismissRequest = onDismiss,
        containerColor = tone(0xFF222222),
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
    val policy by ParentalPolicyStore.policyFlow.collectAsState()
    var showApproved by remember(initialTabIsApproved) { mutableStateOf(initialTabIsApproved) }
    var searchQuery by remember { mutableStateOf("") }

    val selectedChipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = AppAccent,
        selectedLabelColor = Color.Black,
        selectedLeadingIconColor = Color.Black,
    )

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
                text = "Manage Content Rules",
                color = AppText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = AppMuted)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = showApproved,
                onClick = { showApproved = true },
                colors = selectedChipColors,
                leadingIcon = if (showApproved) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null,
                label = {
                    Text(
                        "Approved (${approvedEntries.size})",
                        fontWeight = if (showApproved) FontWeight.Bold else FontWeight.Normal,
                    )
                },
                modifier = Modifier.testTag("rules_tab_approved"),
            )
            FilterChip(
                selected = !showApproved,
                onClick = { showApproved = false },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFFFF6B6B),
                    selectedLabelColor = Color.Black,
                    selectedLeadingIconColor = Color.Black,
                ),
                leadingIcon = if (!showApproved) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null,
                label = {
                    Text(
                        "Blocked (${blockedEntries.size})",
                        fontWeight = if (!showApproved) FontWeight.Bold else FontWeight.Normal,
                    )
                },
                modifier = Modifier.testTag("rules_tab_blocked"),
            )
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search by title, artist, or Spotify ID") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("rules_search_input"),
        )

        if (filteredEntries.isEmpty()) {
            val emptyMessage = when {
                searchQuery.isNotBlank() -> "No rules match \"${searchQuery.trim()}\"."
                showApproved -> "No approved items yet. Open the ⋮ menu on any playlist, album, artist, or song while Parent Mode is unlocked to approve it."
                else -> "No blocked items yet. Open the ⋮ menu on any item while Parent Mode is unlocked to block it."
            }
            Text(
                text = emptyMessage,
                color = AppMuted,
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
                    val badgeColor = if (showApproved) AppAccent else Color(0xFFFF6B6B)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(tone(0xFF2B2B2B))
                            .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                    ) {
                        Text(
                            text = RuleLabelStore.kindDisplayName(entry.kind),
                            color = badgeColor,
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
                                color = AppText,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (entry.subtitle.isNotBlank()) {
                                Text(
                                    text = entry.subtitle,
                                    color = AppMuted,
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
                                contentDescription = "Remove rule",
                                tint = AppMuted,
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

@Composable
internal fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(tone(0xFF202020))
            .padding(16.dp),
    ) {
        Text(title, color = AppAccent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
internal fun PolicyToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    switchTestTag: String? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    val alpha = if (enabled) 1f else 0.5f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable { onCheckedChange(!checked) } else Modifier)
            .padding(vertical = 2.dp),
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = AppText.copy(alpha = alpha), fontSize = 15.sp)
            Text(subtitle, color = AppMuted.copy(alpha = alpha), fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = if (enabled) onCheckedChange else null,
            enabled = enabled,
            modifier = if (switchTestTag != null) Modifier.testTag(switchTestTag) else Modifier,
        )
    }
}

@Composable
internal fun RemovableChip(label: String, onRemove: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(tone(0xFF303030))
            .clickable(onClick = onRemove)
            .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Text(label, color = AppText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Default.Close, contentDescription = "Remove", tint = AppMuted, modifier = Modifier.size(14.dp))
    }
}
