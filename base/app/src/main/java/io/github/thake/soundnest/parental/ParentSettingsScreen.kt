/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.parental

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.github.thake.soundnest.connect.CustomSpeakerNames
import io.github.thake.soundnest.connect.LanDiscovery
import io.github.thake.soundnest.di.SongPlayer
import io.github.thake.soundnest.ui.components.LookSheet
import io.github.thake.soundnest.ui.components.bottomBarsSpace
import io.github.thake.soundnest.ui.theme.AppAccent
import io.github.thake.soundnest.ui.theme.AppBackground
import io.github.thake.soundnest.ui.theme.AppMuted
import io.github.thake.soundnest.ui.theme.AppText
import io.github.thake.soundnest.ui.theme.tone

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ParentSettingsScreen(
    navController: NavController,
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
) {
    val context = LocalContext.current
    val sessionTick by ParentSecurityManager.sessionTick.collectAsState()
    val unlocked = remember(sessionTick) { ParentSecurityManager.isAdminUnlocked(context) }
    val policy by ParentalPolicyStore.policyFlow.collectAsState()

    var networkRefresh by remember { mutableIntStateOf(0) }
    var editingSpeakerId by remember { mutableStateOf<String?>(null) }
    var editingSpeakerName by remember { mutableStateOf("") }
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

    androidx.compose.runtime.DisposableEffect(context, unlocked) {
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
                        text = "Tip: While Parent Mode is unlocked, open the ⋮ menu on any song, artist, or playlist to Allow or Block it in one tap.",
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
                            shows = policy.allowedShowIds.size,
                            audibleBooks = policy.allowedAudibleAsins.size,
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
                            shows = policy.blockedShowIds.size,
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
                val home = policy.homeSections
                SectionCard("Front Page (Home) Sections") {
                    PolicyToggleRow(
                        title = "Minimal Home Mode",
                        subtitle = "Hide all algorithmic Spotify carousels; show only Shortcuts & Parent Picks",
                        checked = home.minimalHomeMode,
                    ) { v ->
                        ParentalPolicyStore.update(context) { it.copy(homeSections = home.copy(minimalHomeMode = v)) }
                    }
                    PolicyToggleRow(
                        title = "Show Shortcuts Grid",
                        subtitle = "Two-column quick-access tiles at the top of Home (including Liked Songs)",
                        checked = home.showShortcutsGrid,
                    ) { v ->
                        ParentalPolicyStore.update(context) { it.copy(homeSections = home.copy(showShortcutsGrid = v)) }
                    }
                    PolicyToggleRow(
                        title = "Show Parent Picks Shelf",
                        subtitle = "Pin a dedicated shelf of parent-approved playlists, albums, and artists",
                        checked = home.showParentPicks,
                    ) { v ->
                        ParentalPolicyStore.update(context) { it.copy(homeSections = home.copy(showParentPicks = v)) }
                    }
                    PolicyToggleRow(
                        title = androidx.compose.ui.res.stringResource(io.github.thake.soundnest.R.string.audible_home_pinned_toggle_title),
                        subtitle = androidx.compose.ui.res.stringResource(io.github.thake.soundnest.R.string.audible_home_pinned_toggle_subtitle),
                        checked = home.showParentPicks && home.showPinnedAudibleOnHome,
                        enabled = home.showParentPicks,
                        switchTestTag = "parent_home_pinned_audible_toggle",
                    ) { v ->
                        ParentalPolicyStore.update(context) { it.copy(homeSections = home.copy(showPinnedAudibleOnHome = v)) }
                    }
                    PolicyToggleRow(
                        title = "Show What's New Bell & Notifications",
                        subtitle = "Bell icon on Home and background release notifications",
                        checked = home.showWhatsNewBell,
                        enabled = !home.minimalHomeMode && home.showNewReleases,
                    ) { v ->
                        ParentalPolicyStore.update(context) { it.copy(homeSections = home.copy(showWhatsNewBell = v)) }
                    }
                    PolicyToggleRow(
                        title = "Show Filter Chips",
                        subtitle = "All / Music / Podcasts / Audiobooks chips at the top of Home",
                        checked = home.showFilterChips,
                        enabled = !home.minimalHomeMode,
                    ) { v ->
                        ParentalPolicyStore.update(context) { it.copy(homeSections = home.copy(showFilterChips = v)) }
                    }
                    PolicyToggleRow(
                        title = "Show Spotify AI DJ",
                        subtitle = "Allow Spotify's AI DJ card on Home (off by default)",
                        checked = home.showDjCard,
                    ) { v ->
                        ParentalPolicyStore.update(context) { it.copy(homeSections = home.copy(showDjCard = v)) }
                    }

                    HorizontalDivider(color = tone(0xFF3A3A3A))
                    Text("Category Carousels", color = AppText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)

                    PolicyToggleRow(
                        title = "Charts, Trending & Popular",
                        subtitle = "Top 50, Viral Hits, Trending now, Popular albums",
                        checked = home.showChartsAndTrending,
                        enabled = !home.minimalHomeMode,
                    ) { v ->
                        ParentalPolicyStore.update(context) { it.copy(homeSections = home.copy(showChartsAndTrending = v)) }
                    }
                    PolicyToggleRow(
                        title = "Mixes, Made For You & Discover",
                        subtitle = "Daily Mixes, Discover Weekly, Recommended for today",
                        checked = home.showMixesAndMadeForYou,
                        enabled = !home.minimalHomeMode,
                    ) { v ->
                        ParentalPolicyStore.update(context) { it.copy(homeSections = home.copy(showMixesAndMadeForYou = v)) }
                    }
                    PolicyToggleRow(
                        title = "New Releases",
                        subtitle = "New releases for you, Release Radar, Fresh new music",
                        checked = home.showNewReleases,
                        enabled = !home.minimalHomeMode,
                    ) { v ->
                        ParentalPolicyStore.update(context) { it.copy(homeSections = home.copy(showNewReleases = v)) }
                    }
                    PolicyToggleRow(
                        title = "Recently Played & Jump Back In",
                        subtitle = "Recently played and Heavy rotation carousels",
                        checked = home.showRecents,
                        enabled = !home.minimalHomeMode,
                    ) { v ->
                        ParentalPolicyStore.update(context) { it.copy(homeSections = home.copy(showRecents = v)) }
                    }

                    if (home.hiddenShelfKeys.isNotEmpty()) {
                        HorizontalDivider(color = tone(0xFF3A3A3A))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "Individually Hidden Shelves (${home.hiddenShelfKeys.size})",
                                color = AppText,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = {
                                ParentalPolicyStore.update(context) {
                                    it.copy(homeSections = home.copy(hiddenShelfKeys = emptySet()))
                                }
                            }) {
                                Text("Restore All", color = AppAccent)
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            home.hiddenShelfKeys.sorted().forEach { key ->
                                RemovableChip(label = key) { ParentalPolicyStore.unhideHomeShelf(context, key) }
                            }
                        }
                    }
                }
            }

            // 4. Feature Access Gates
            item(key = "feature_gates") {
                SectionCard("Feature Access & Library Protection") {
                    PolicyToggleRow(
                        title = "Read-Only Spotify Library",
                        subtitle = "Prevent unliking songs or deleting/editing Spotify playlists (local device playlists remain usable)",
                        checked = policy.readOnlySpotifyLibrary,
                    ) { v ->
                        ParentalPolicyStore.update(context) { it.copy(readOnlySpotifyLibrary = v) }
                    }
                    PolicyToggleRow(
                        title = "Autoplay, Smart Shuffle & Radio",
                        subtitle = if (policy.filterMode == FilterMode.STRICT_ALLOWLIST) {
                            "Automatically disabled in Strict Allowlist mode"
                        } else {
                            "Allow endless autoplay, Smart Shuffle, Song/Artist Radio, and Browse Categories"
                        },
                        checked = policy.effectiveAllowAutoplayAndRadio,
                        enabled = policy.filterMode != FilterMode.STRICT_ALLOWLIST,
                    ) { v ->
                        ParentalPolicyStore.update(context) { it.copy(allowAutoplayAndRadio = v) }
                    }
                    PolicyToggleRow(
                        title = "Podcasts",
                        subtitle = "Allow podcast shows and episodes (unmoderated spoken-word audio)",
                        checked = policy.allowPodcasts,
                    ) { v ->
                        ParentalPolicyStore.update(context) { it.copy(allowPodcasts = v) }
                    }
                    PolicyToggleRow(
                        title = "Audiobooks",
                        subtitle = "Allow Spotify audiobooks & parent-approved Audible audiobooks",
                        checked = policy.allowAudiobooks,
                    ) { v ->
                        ParentalPolicyStore.update(context) { it.copy(allowAudiobooks = v) }
                    }
                    io.github.thake.soundnest.audible.ui.AudibleParentControlsCard(
                        audiobooksEnabled = policy.allowAudiobooks,
                        onOpenPicker = { showAudiblePicker = true },
                    )
                    PolicyToggleRow(
                        title = "Live Internet Radio",
                        subtitle = "Allow streaming live internet radio stations",
                        checked = policy.allowLiveRadio,
                    ) { v ->
                        ParentalPolicyStore.update(context) { it.copy(allowLiveRadio = v) }
                    }
                    PolicyToggleRow(
                        title = "Local Audio Files",
                        subtitle = "Allow scanning and playing audio files stored on this device",
                        checked = policy.allowLocalFiles,
                    ) { v ->
                        ParentalPolicyStore.update(context) { it.copy(allowLocalFiles = v) }
                    }
                    PolicyToggleRow(
                        title = "Spotify Jam",
                        subtitle = "Allow starting or joining shared Spotify Jam listening sessions",
                        checked = policy.allowJam,
                    ) { v ->
                        ParentalPolicyStore.update(context) { it.copy(allowJam = v) }
                    }
                    PolicyToggleRow(
                        title = "Song Lyrics",
                        subtitle = "Show synchronized Spotify lyrics in the player",
                        checked = policy.allowLyrics,
                    ) { v ->
                        ParentalPolicyStore.update(context) { it.copy(allowLyrics = v) }
                    }
                    PolicyToggleRow(
                        title = "Genius Song Explanations",
                        subtitle = "Allow community-written Genius annotations (off by default)",
                        checked = policy.allowLyrics && policy.allowGeniusMeanings,
                        enabled = policy.allowLyrics,
                    ) { v ->
                        ParentalPolicyStore.update(context) { it.copy(allowGeniusMeanings = v) }
                    }
                    PolicyToggleRow(
                        title = "Third-Party Lyric Providers",
                        subtitle = "Fallback to LRCLIB / NetEase / QQ when Spotify has no lyrics",
                        checked = policy.allowLyrics && policy.allowExtraLyricSources,
                        enabled = policy.allowLyrics,
                    ) { v ->
                        ParentalPolicyStore.update(context) { it.copy(allowExtraLyricSources = v) }
                    }
                }
            }

            // 5. Trusted Home Network & Speakers
            item(key = "speakers_network") {
                val lanDeviceCount = LanDiscovery.devices.size
                val requireWifi = remember(networkRefresh, sessionTick) { TrustedNetworkGate.isRequireTrustedNetwork(context) }
                val ssids = remember(networkRefresh, sessionTick) { TrustedNetworkGate.trustedSsids(context) }
                val lanFingerprints = remember(networkRefresh, sessionTick) { TrustedNetworkGate.trustedLanFingerprints(context) }
                val currentSnap = remember(networkRefresh, sessionTick, lanDeviceCount) { TrustedNetworkGate.currentSnapshot(context) }
                val isTrustedNow = remember(networkRefresh, sessionTick, currentSnap) {
                    TrustedNetworkGate.isNetworkTrusted(context, currentSnap)
                }
                val visibilityMode = remember(networkRefresh, sessionTick) { TrustedNetworkGate.speakerVisibilityMode(context) }
                val hiddenIds = remember(networkRefresh, sessionTick) { TrustedNetworkGate.hiddenDeviceIds(context) }
                val allowedIds = remember(networkRefresh, sessionTick) { TrustedNetworkGate.allowedDeviceIds(context) }
                val customNames = remember(networkRefresh, sessionTick) { CustomSpeakerNames.all(context) }
                val liveDevices by produceState(emptyList<SongPlayer.ConnectDevice>(), networkRefresh, sessionTick) {
                    value = runCatching { SongPlayer.devices() }.getOrDefault(emptyList())
                }

                SectionCard("Trusted Home Network & Speakers") {
                    val statusBg = when {
                        !requireWifi -> tone(0xFF2C2C2C)
                        isTrustedNow -> AppAccent.copy(alpha = 0.16f)
                        else -> Color(0xFFFFB74D).copy(alpha = 0.14f)
                    }
                    val statusTint = when {
                        !requireWifi -> AppMuted
                        isTrustedNow -> AppAccent
                        else -> Color(0xFFFFB74D)
                    }
                    val statusTitle = when {
                        !requireWifi -> "Network boundary disabled — speakers allowed everywhere"
                        isTrustedNow -> "✓ Trusted Home LAN active — speakers enabled"
                        !currentSnap.onWifi -> "Not connected to Wi-Fi — remote speakers hidden"
                        else -> "Untrusted Wi-Fi — remote speakers hidden"
                    }
                    val statusDetail = when {
                        currentSnap.onWifi && !currentSnap.lanNetworkLabel.isNullOrBlank() ->
                            "Current connection: ${currentSnap.lanNetworkLabel} (zero location permissions)"
                        currentSnap.onWifi ->
                            "Connected to Wi-Fi · Zero location permissions used"
                        else ->
                            "Connect to your Home Wi-Fi to trust its router & speaker fingerprint."
                    }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(statusBg)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Text(statusTitle, color = statusTint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(statusDetail, color = AppMuted, fontSize = 11.sp)
                    }

                    PolicyToggleRow(
                        title = "Require Trusted Home Network",
                        subtitle = "Hide cloud Spotify Connect & Alexa speakers and block LAN speaker setup when away from Home Wi-Fi",
                        checked = requireWifi,
                    ) { v ->
                        TrustedNetworkGate.setRequireTrustedNetwork(context, v)
                        networkRefresh++
                    }

                    Button(
                        enabled = currentSnap.onWifi,
                        onClick = {
                            val ok = TrustedNetworkGate.trustCurrentNetwork(context)
                            networkRefresh++
                            Toast.makeText(
                                context,
                                if (ok) "Current Home LAN & visible speakers trusted." else "Connect to your Home Wi-Fi first.",
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                        colors = accentFilledButtonColors,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Trust Current Home LAN & Visible Speakers", fontWeight = FontWeight.Bold)
                    }

                    if (lanFingerprints.isNotEmpty() || ssids.isNotEmpty()) {
                        Text(
                            "Trusted Networks & Speaker Fingerprints",
                            color = AppMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            lanFingerprints.sorted().forEach { fp ->
                                val label = TrustedNetworkGate.displayLabelForFingerprint(context, fp)
                                RemovableChip(label = label) {
                                    TrustedNetworkGate.removeTrustedLanFingerprint(context, fp)
                                    networkRefresh++
                                }
                            }
                            ssids.sorted().forEach { ssid ->
                                val displaySsid = TrustedNetworkGate.displayLabelForSsid(context, ssid)
                                RemovableChip(label = "Wi-Fi: $displaySsid") {
                                    TrustedNetworkGate.removeTrustedSsid(context, ssid)
                                    networkRefresh++
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = tone(0xFF3A3A3A))
                    Text("Speaker Visibility Mode", color = AppText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val allExceptHiddenSelected = visibilityMode == SpeakerVisibilityMode.ALLOW_ALL_EXCEPT_HIDDEN
                        FilterChip(
                            selected = allExceptHiddenSelected,
                            onClick = {
                                TrustedNetworkGate.setSpeakerVisibilityMode(context, SpeakerVisibilityMode.ALLOW_ALL_EXCEPT_HIDDEN)
                                networkRefresh++
                            },
                            colors = selectedChipColors,
                            leadingIcon = if (allExceptHiddenSelected) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            label = { Text("All Except Hidden", fontWeight = if (allExceptHiddenSelected) FontWeight.Bold else FontWeight.Normal) },
                        )
                        val onlyAllowedSelected = visibilityMode == SpeakerVisibilityMode.ONLY_ALLOWED
                        FilterChip(
                            selected = onlyAllowedSelected,
                            onClick = {
                                TrustedNetworkGate.setSpeakerVisibilityMode(context, SpeakerVisibilityMode.ONLY_ALLOWED)
                                networkRefresh++
                            },
                            colors = selectedChipColors,
                            leadingIcon = if (onlyAllowedSelected) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            label = { Text("Only Allowed", fontWeight = if (onlyAllowedSelected) FontWeight.Bold else FontWeight.Normal) },
                        )
                    }

                    val knownSpeakers = remember(liveDevices, hiddenIds, allowedIds, customNames) {
                        val map = linkedMapOf<String, String>()
                        liveDevices.filter { !it.isSelf && it.id.isNotBlank() }.forEach { d ->
                            map[d.id] = customNames[d.id] ?: d.name
                        }
                        runCatching {
                            LanDiscovery.devices.toList().forEach { lan ->
                                val id = lan.info.deviceId
                                if (id.isNotBlank() && id !in map) {
                                    map[id] = customNames[id] ?: lan.info.name.ifBlank { id }
                                }
                            }
                        }
                        (hiddenIds + allowedIds + customNames.keys).forEach { id ->
                            if (id.isNotBlank() && id !in map) map[id] = customNames[id] ?: id
                        }
                        map.entries.toList()
                    }

                    if (knownSpeakers.isEmpty()) {
                        Text(
                            "Open the Connect device sheet while on Home Wi-Fi to discover speakers you can rename, hide, or allow.",
                            color = AppMuted,
                            fontSize = 12.sp,
                        )
                    } else {
                        knownSpeakers.forEach { (id, label) ->
                            val isHidden = id in hiddenIds
                            val isAllowed = id in allowedIds
                            val speakerUsable = if (visibilityMode == SpeakerVisibilityMode.ONLY_ALLOWED) isAllowed else !isHidden
                            val statusLabel = if (visibilityMode == SpeakerVisibilityMode.ONLY_ALLOWED) {
                                if (speakerUsable) "Allowed" else "Blocked"
                            } else {
                                if (speakerUsable) "Visible" else "Hidden"
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(label, color = AppText, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(id, color = AppMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                IconButton(onClick = {
                                    editingSpeakerId = id
                                    editingSpeakerName = customNames[id].orEmpty()
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Rename speaker", tint = AppMuted, modifier = Modifier.size(18.dp))
                                }
                                Text(
                                    text = statusLabel,
                                    color = if (speakerUsable) AppAccent else AppMuted,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                                Switch(
                                    checked = speakerUsable,
                                    onCheckedChange = { usable ->
                                        if (visibilityMode == SpeakerVisibilityMode.ONLY_ALLOWED) {
                                            TrustedNetworkGate.setDeviceAllowedInStrictMode(context, id, usable)
                                        } else {
                                            TrustedNetworkGate.setDeviceHidden(context, id, !usable)
                                        }
                                        networkRefresh++
                                    },
                                )
                            }
                        }
                    }
                }
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
        io.github.thake.soundnest.audible.ui.AudiblePickerSheet(
            onDismiss = { showAudiblePicker = false },
        )
    }

    editingSpeakerId?.let { speakerId ->
        AlertDialog(
            onDismissRequest = { editingSpeakerId = null },
            containerColor = tone(0xFF282828),
            title = { Text("Custom Speaker Nickname", color = AppText, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = editingSpeakerName,
                    onValueChange = { editingSpeakerName = it },
                    label = { Text("Nickname (e.g. Emma's Bedroom)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = AppAccent,
                        disabledContentColor = AppMuted.copy(alpha = 0.45f),
                    ),
                    onClick = {
                        CustomSpeakerNames.setCustomName(context, speakerId, editingSpeakerName)
                        editingSpeakerId = null
                        networkRefresh++
                    },
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingSpeakerId = null }) {
                    Text("Cancel", color = AppMuted)
                }
            },
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
            shows = policy.allowedShowIds,
            audibleAsins = policy.allowedAudibleAsins,
        )
    }
    val blockedEntries = remember(policy) {
        collectManagedRuleEntries(
            context = context,
            playlists = policy.blockedPlaylistIds,
            albums = policy.blockedAlbumIds,
            artists = policy.blockedArtistIds,
            tracks = policy.blockedTrackIds,
            shows = policy.blockedShowIds,
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
    context: android.content.Context,
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

private fun clearManagedRule(context: android.content.Context, kind: String, id: String) {
    when (kind) {
        "playlist" -> ParentalPolicyStore.clearPlaylistRule(context, id)
        "album" -> ParentalPolicyStore.clearAlbumRule(context, id)
        "artist" -> ParentalPolicyStore.clearArtistRule(context, id)
        "track" -> ParentalPolicyStore.clearTrackRule(context, id)
        "show" -> ParentalPolicyStore.clearShowRule(context, id)
        "audible" -> ParentalPolicyStore.setAudibleBookAllowed(context, id, false)
    }
}

private fun applyQuickUriRule(context: android.content.Context, raw: String, allow: Boolean): Boolean {
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
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
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
private fun PolicyToggleRow(
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
private fun RemovableChip(label: String, onRemove: () -> Unit) {
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
