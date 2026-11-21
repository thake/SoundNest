/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.parental.remote.ui

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.ui.look.LookAlertDialog
import io.github.thake.soundnest.core.ui.settings.M3GroupCard
import io.github.thake.soundnest.core.ui.settings.M3RemovableChip
import io.github.thake.soundnest.core.ui.settings.M3SettingsScaffold
import io.github.thake.soundnest.core.ui.settings.M3SettingsSwitchRow
import io.github.thake.soundnest.core.ui.settings.m3FilledButtonColors
import io.github.thake.soundnest.core.ui.settings.m3FilterChipColors
import io.github.thake.soundnest.core.ui.settings.m3SettingsBlock
import io.github.thake.soundnest.core.ui.settings.m3SettingsSection
import io.github.thake.soundnest.core.ui.settings.rememberM3SettingsPalette
import io.github.thake.soundnest.feature.parental.remote.net.ParentRemoteClient
import io.github.thake.soundnest.feature.parental.remote.settings.PairedChildRecord
import io.github.thake.soundnest.feature.parental.settings.FilterMode
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore
import io.github.thake.soundnest.feature.parental.settings.RuleLabel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private val SPOTIFY_URI_REGEX = Regex(
    "(?:spotify:|https?://(?:open\\.|play\\.)?spotify\\.com/(?:intl-[a-z-]+/)?)(track|album|artist|playlist|show)[:/]([A-Za-z0-9]+)",
    RegexOption.IGNORE_CASE,
)

private fun parseSpotifyEntity(raw: String): Pair<String, String>? {
    val m = SPOTIFY_URI_REGEX.find(raw.trim()) ?: return null
    val kind = m.groupValues[1].lowercase()
    val id = ParentalPolicyStore.normalizeSpotifyId(m.groupValues[2])
    return if (id.isNotEmpty()) kind to id else null
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RemotePolicyEditorScreen(
    child: PairedChildRecord,
    otherChildren: List<PairedChildRecord>,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val palette = rememberM3SettingsPalette()

    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var reloadNonce by remember { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }

    var draftPolicy by remember { mutableStateOf<ParentalPolicy?>(null) }
    var draftLabels by remember { mutableStateOf<Map<String, RuleLabel>>(emptyMap()) }
    var baseRevision by remember { mutableStateOf<Long?>(null) }

    var uriInput by remember { mutableStateOf("") }
    var keywordInput by remember { mutableStateOf("") }
    var showSyncDialog by remember { mutableStateOf(false) }

    LaunchedEffect(child.childId, reloadNonce) {
        loading = true
        loadError = null
        ParentRemoteClient.fetchPolicy(context, child)
            .onSuccess { bundle ->
                draftPolicy = bundle.policy
                draftLabels = bundle.ruleLabels
                baseRevision = bundle.policyRevision
                loading = false
            }
            .onFailure { e ->
                loadError = e.message ?: "Offline"
                loading = false
            }
    }

    fun applyUriRule(allow: Boolean) {
        val current = draftPolicy ?: return
        val (kind, id) = parseSpotifyEntity(uriInput) ?: run {
            Toast.makeText(context, context.getString(R.string.remote_policy_invalid_link), Toast.LENGTH_SHORT).show()
            return
        }
        draftPolicy = when (kind) {
            "track" -> if (allow) current.copy(allowedTrackIds = current.allowedTrackIds + id, blockedTrackIds = current.blockedTrackIds - id)
            else current.copy(blockedTrackIds = current.blockedTrackIds + id, allowedTrackIds = current.allowedTrackIds - id)
            "album" -> if (allow) current.copy(allowedAlbumIds = current.allowedAlbumIds + id, blockedAlbumIds = current.blockedAlbumIds - id)
            else current.copy(blockedAlbumIds = current.blockedAlbumIds + id, allowedAlbumIds = current.allowedAlbumIds - id)
            "artist" -> if (allow) current.copy(allowedArtistIds = current.allowedArtistIds + id, blockedArtistIds = current.blockedArtistIds - id)
            else current.copy(blockedArtistIds = current.blockedArtistIds + id, allowedArtistIds = current.allowedArtistIds - id)
            "playlist" -> if (allow) current.copy(allowedPlaylistIds = current.allowedPlaylistIds + id, blockedPlaylistIds = current.blockedPlaylistIds - id)
            else current.copy(blockedPlaylistIds = current.blockedPlaylistIds + id, allowedPlaylistIds = current.allowedPlaylistIds - id)
            "show" -> {
                val pods = current.spokenWord.podcasts
                current.copy(
                    spokenWord = current.spokenWord.copy(
                        podcasts = if (allow) pods.copy(allowedShowIds = pods.allowedShowIds + id, blockedShowIds = pods.blockedShowIds - id)
                        else pods.copy(blockedShowIds = pods.blockedShowIds + id, allowedShowIds = pods.allowedShowIds - id),
                    ),
                )
            }
            else -> current
        }
        uriInput = ""
    }

    M3SettingsScaffold(
        title = stringResource(R.string.remote_policy_title, child.displayName),
        onBack = onBack,
        listTestTag = "remote_policy_editor_list",
    ) { scaffoldPalette ->
        val policy = draftPolicy
        if (loading) {
            m3SettingsBlock(key = "remote_policy:loading", palette = scaffoldPalette) {
                M3GroupCard(palette = scaffoldPalette) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                    ) {
                        CircularProgressIndicator(color = scaffoldPalette.accent)
                        Text(
                            text = stringResource(R.string.remote_policy_loading, child.displayName),
                            color = scaffoldPalette.muted,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
            return@M3SettingsScaffold
        }

        if (policy == null) {
            m3SettingsBlock(key = "remote_policy:error", palette = scaffoldPalette) {
                M3GroupCard(palette = scaffoldPalette) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.remote_policy_load_failed, child.displayName, loadError.orEmpty()),
                            color = scaffoldPalette.text,
                            fontSize = 14.sp,
                        )
                        Button(
                            onClick = { reloadNonce++ },
                            colors = m3FilledButtonColors(scaffoldPalette),
                        ) {
                            Text(stringResource(R.string.remote_policy_retry), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            return@M3SettingsScaffold
        }

        // 1. Filter Mode & Explicit
        m3SettingsBlock(key = "remote_policy:mode", palette = scaffoldPalette) {
            Text(
                text = stringResource(R.string.parent_section_filter_rules),
                color = scaffoldPalette.muted,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            )
            M3GroupCard(palette = scaffoldPalette) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = policy.filterMode == FilterMode.FILTERED_OPEN,
                            onClick = { draftPolicy = policy.copy(filterMode = FilterMode.FILTERED_OPEN) },
                            label = { Text(stringResource(R.string.parent_mode_filtered_open_title)) },
                            leadingIcon = if (policy.filterMode == FilterMode.FILTERED_OPEN) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            colors = m3FilterChipColors(scaffoldPalette),
                            modifier = Modifier.testTag("remote_policy_mode_open"),
                        )
                        FilterChip(
                            selected = policy.filterMode == FilterMode.STRICT_ALLOWLIST,
                            onClick = { draftPolicy = policy.copy(filterMode = FilterMode.STRICT_ALLOWLIST) },
                            label = { Text(stringResource(R.string.parent_mode_strict_allowlist_title)) },
                            leadingIcon = if (policy.filterMode == FilterMode.STRICT_ALLOWLIST) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            colors = m3FilterChipColors(scaffoldPalette),
                            modifier = Modifier.testTag("remote_policy_mode_strict"),
                        )
                    }
                    M3SettingsSwitchRow(
                        title = stringResource(R.string.parent_filter_block_explicit_title),
                        detail = stringResource(R.string.parent_filter_block_explicit_subtitle),
                        checked = policy.blockExplicit,
                        palette = scaffoldPalette,
                        onCheckedChange = { draftPolicy = policy.copy(blockExplicit = it) },
                    )
                }
            }
        }

        // 2. Quick Allow / Block URI + Keywords
        m3SettingsBlock(key = "remote_policy:quick_rules", palette = scaffoldPalette) {
            Text(
                text = stringResource(R.string.parent_quick_allow_block_title),
                color = scaffoldPalette.muted,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            )
            M3GroupCard(palette = scaffoldPalette) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = uriInput,
                        onValueChange = { uriInput = it },
                        label = { Text(stringResource(R.string.parent_quick_allow_block_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { applyUriRule(allow = true) },
                            enabled = uriInput.isNotBlank(),
                            colors = m3FilledButtonColors(scaffoldPalette),
                        ) {
                            Text(stringResource(R.string.parent_action_allow))
                        }
                        OutlinedButton(
                            onClick = { applyUriRule(allow = false) },
                            enabled = uriInput.isNotBlank(),
                        ) {
                            Text(stringResource(R.string.parent_action_block), color = scaffoldPalette.text)
                        }
                    }

                    OutlinedTextField(
                        value = keywordInput,
                        onValueChange = { keywordInput = it },
                        label = { Text(stringResource(R.string.remote_policy_keyword_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(
                        onClick = {
                            val kw = keywordInput.trim().lowercase()
                            if (kw.isNotEmpty()) {
                                draftPolicy = policy.copy(blockedKeywords = policy.blockedKeywords + kw)
                                keywordInput = ""
                            }
                        },
                        enabled = keywordInput.isNotBlank(),
                    ) {
                        Text(stringResource(R.string.remote_policy_add_keyword), color = scaffoldPalette.text)
                    }
                    if (policy.blockedKeywords.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            policy.blockedKeywords.sorted().forEach { kw ->
                                M3RemovableChip(
                                    label = kw,
                                    palette = scaffoldPalette,
                                    onRemove = { draftPolicy = policy.copy(blockedKeywords = policy.blockedKeywords - kw) },
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Feature Gates
        m3SettingsSection(
            key = "remote_policy:features",
            palette = scaffoldPalette,
            header = context.getString(R.string.parent_section_spotify),
        ) {
            row {
                M3SettingsSwitchRow(
                    title = stringResource(R.string.remote_policy_gate_podcasts),
                    checked = policy.spokenWord.allowPodcasts,
                    palette = scaffoldPalette,
                    onCheckedChange = {
                        draftPolicy = policy.copy(spokenWord = policy.spokenWord.copy(allowPodcasts = it))
                    },
                )
            }
            row {
                M3SettingsSwitchRow(
                    title = stringResource(R.string.remote_policy_gate_audiobooks),
                    checked = policy.spokenWord.allowAudiobooks,
                    palette = scaffoldPalette,
                    onCheckedChange = {
                        draftPolicy = policy.copy(spokenWord = policy.spokenWord.copy(allowAudiobooks = it))
                    },
                )
            }
            row {
                M3SettingsSwitchRow(
                    title = stringResource(R.string.remote_policy_gate_audible),
                    checked = policy.spokenWord.allowAudible,
                    palette = scaffoldPalette,
                    onCheckedChange = {
                        draftPolicy = policy.copy(spokenWord = policy.spokenWord.copy(allowAudible = it))
                    },
                )
            }
            row {
                M3SettingsSwitchRow(
                    title = stringResource(R.string.remote_policy_gate_radio),
                    checked = policy.radio.enabled,
                    palette = scaffoldPalette,
                    onCheckedChange = {
                        draftPolicy = policy.copy(radio = policy.radio.copy(enabled = it))
                    },
                )
            }
            row {
                M3SettingsSwitchRow(
                    title = stringResource(R.string.remote_policy_gate_local_files),
                    checked = policy.localFiles.enabled,
                    palette = scaffoldPalette,
                    onCheckedChange = {
                        draftPolicy = policy.copy(localFiles = policy.localFiles.copy(enabled = it))
                    },
                )
            }
            row {
                M3SettingsSwitchRow(
                    title = stringResource(R.string.remote_policy_gate_lyrics),
                    checked = policy.lyrics.enabled,
                    palette = scaffoldPalette,
                    onCheckedChange = {
                        draftPolicy = policy.copy(lyrics = policy.lyrics.copy(enabled = it))
                    },
                )
            }
            row {
                M3SettingsSwitchRow(
                    title = stringResource(R.string.remote_policy_gate_autoplay),
                    checked = policy.playback.allowAutoplayAndRadio,
                    palette = scaffoldPalette,
                    onCheckedChange = {
                        draftPolicy = policy.copy(playback = policy.playback.copy(allowAutoplayAndRadio = it))
                    },
                )
            }
            row {
                M3SettingsSwitchRow(
                    title = stringResource(R.string.remote_policy_gate_readonly_library),
                    checked = policy.library.readOnlySpotifyLibrary,
                    palette = scaffoldPalette,
                    onCheckedChange = {
                        draftPolicy = policy.copy(library = policy.library.copy(readOnlySpotifyLibrary = it))
                    },
                )
            }
            row {
                M3SettingsSwitchRow(
                    title = stringResource(R.string.remote_policy_gate_jam),
                    checked = policy.speakers.jam.enabled,
                    palette = scaffoldPalette,
                    onCheckedChange = {
                        draftPolicy = policy.copy(speakers = policy.speakers.copy(jam = policy.speakers.jam.copy(enabled = it)))
                    },
                )
            }
        }

        // 4. Home Screen Shelves
        m3SettingsSection(
            key = "remote_policy:home",
            palette = scaffoldPalette,
            header = context.getString(R.string.parent_section_home),
        ) {
            row {
                M3SettingsSwitchRow(
                    title = stringResource(R.string.parent_home_minimal_title),
                    checked = policy.home.minimalHomeMode,
                    palette = scaffoldPalette,
                    onCheckedChange = { draftPolicy = policy.copy(home = policy.home.copy(minimalHomeMode = it)) },
                )
            }
            row {
                M3SettingsSwitchRow(
                    title = stringResource(R.string.parent_home_picks_title),
                    checked = policy.home.showParentPicks,
                    palette = scaffoldPalette,
                    onCheckedChange = { draftPolicy = policy.copy(home = policy.home.copy(showParentPicks = it)) },
                )
            }
            row {
                M3SettingsSwitchRow(
                    title = stringResource(R.string.parent_home_charts_title),
                    checked = policy.home.showChartsAndTrending,
                    palette = scaffoldPalette,
                    onCheckedChange = { draftPolicy = policy.copy(home = policy.home.copy(showChartsAndTrending = it)) },
                )
            }
            row {
                M3SettingsSwitchRow(
                    title = stringResource(R.string.parent_home_mixes_title),
                    checked = policy.home.showMixesAndMadeForYou,
                    palette = scaffoldPalette,
                    onCheckedChange = { draftPolicy = policy.copy(home = policy.home.copy(showMixesAndMadeForYou = it)) },
                )
            }
            row {
                M3SettingsSwitchRow(
                    title = stringResource(R.string.parent_home_releases_title),
                    checked = policy.home.showNewReleases,
                    palette = scaffoldPalette,
                    onCheckedChange = { draftPolicy = policy.copy(home = policy.home.copy(showNewReleases = it)) },
                )
            }
            row {
                M3SettingsSwitchRow(
                    title = stringResource(R.string.parent_home_dj_title),
                    checked = policy.home.showDjCard,
                    palette = scaffoldPalette,
                    onCheckedChange = { draftPolicy = policy.copy(home = policy.home.copy(showDjCard = it)) },
                )
            }
        }

        // 4b. Listening Hours & Bedtime Inactivity Sleep Timer
        m3SettingsBlock(key = "remote_policy:schedule", palette = scaffoldPalette) {
            val sched = policy.schedule.clamped()
            Text(
                text = stringResource(R.string.parent_schedule_section_title),
                color = scaffoldPalette.muted,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            )
            M3GroupCard(palette = scaffoldPalette) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    M3SettingsSwitchRow(
                        title = stringResource(R.string.parent_schedule_hours_enable_title),
                        detail = "${stringResource(R.string.parent_schedule_weekdays)}: ${io.github.thake.soundnest.feature.parental.schedule.ScheduleParentalPolicy.formatMinutesOfDay(sched.weekdayWindow.startMinutes)}–${io.github.thake.soundnest.feature.parental.schedule.ScheduleParentalPolicy.formatMinutesOfDay(sched.weekdayWindow.endMinutes)}",
                        checked = sched.usageHoursEnabled,
                        palette = scaffoldPalette,
                        onCheckedChange = { enabled ->
                            draftPolicy = policy.copy(schedule = sched.copy(usageHoursEnabled = enabled))
                        },
                    )
                    M3SettingsSwitchRow(
                        title = stringResource(R.string.parent_schedule_bedtime_enable_title),
                        detail = "${stringResource(R.string.parent_schedule_bedtime_after)} ${io.github.thake.soundnest.feature.parental.schedule.ScheduleParentalPolicy.formatMinutesOfDay(sched.bedtimeStartMinutes)}",
                        checked = sched.bedtimeInactivityEnabled,
                        palette = scaffoldPalette,
                        onCheckedChange = { enabled ->
                            draftPolicy = policy.copy(schedule = sched.copy(bedtimeInactivityEnabled = enabled))
                        },
                    )
                    if (sched.bedtimeInactivityEnabled) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            io.github.thake.soundnest.feature.parental.schedule.ScheduleParentalPolicy.INACTIVITY_PRESETS.forEach { mins ->
                                val selected = sched.inactivityTimeoutMinutes == mins
                                FilterChip(
                                    selected = selected,
                                    onClick = { draftPolicy = policy.copy(schedule = sched.copy(inactivityTimeoutMinutes = mins)) },
                                    label = { Text(stringResource(R.string.parent_schedule_inactivity_min, mins)) },
                                    leadingIcon = if (selected) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null,
                                    colors = m3FilterChipColors(scaffoldPalette),
                                )
                            }
                        }
                    }
                }
            }
        }

        // 5. Save & Push Actions
        m3SettingsBlock(key = "remote_policy:actions", palette = scaffoldPalette) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        if (saving) return@Button
                        saving = true
                        scope.launch {
                            val res = ParentRemoteClient.pushPolicy(context, child, policy, draftLabels, baseRevision)
                            saving = false
                            res.onSuccess { updated ->
                                baseRevision = updated.policyRevision
                                Handler(Looper.getMainLooper()).post {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.remote_policy_saved_toast, child.displayName),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                                onSaved()
                            }.onFailure { e ->
                                Handler(Looper.getMainLooper()).post {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.remote_policy_load_failed, child.displayName, e.message ?: "Error"),
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                            }
                        }
                    },
                    enabled = !saving,
                    colors = m3FilledButtonColors(scaffoldPalette),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("remote_policy_save_btn"),
                ) {
                    Text(stringResource(R.string.remote_policy_save, child.displayName), fontWeight = FontWeight.Bold)
                }

                if (otherChildren.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { showSyncDialog = true },
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(18.dp), tint = scaffoldPalette.accent)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.remote_policy_sync_others), color = scaffoldPalette.text)
                    }
                }
            }
        }
    }

    if (showSyncDialog && draftPolicy != null) {
        val policyToPush = draftPolicy!!
        var selectedIds by remember(otherChildren) { mutableStateOf(otherChildren.map { it.childId }.toSet()) }
        var pushing by remember { mutableStateOf(false) }

        LookAlertDialog(
            onDismissRequest = { if (!pushing) showSyncDialog = false },
            containerColor = palette.dialogBg,
            title = {
                Text(
                    stringResource(R.string.remote_policy_sync_others),
                    color = palette.text,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    otherChildren.forEach { target ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Checkbox(
                                checked = target.childId in selectedIds,
                                onCheckedChange = { checked ->
                                    selectedIds = if (checked) selectedIds + target.childId else selectedIds - target.childId
                                },
                                enabled = !pushing,
                            )
                            Text(target.displayName, color = palette.text, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targets = otherChildren.filter { it.childId in selectedIds }
                        if (targets.isEmpty() || pushing) return@Button
                        pushing = true
                        scope.launch {
                            val results = coroutineScope {
                                targets.map { t ->
                                    async { ParentRemoteClient.pushPolicy(context, t, policyToPush, draftLabels) }
                                }.awaitAll()
                            }
                            val okCount = results.count { it.isSuccess }
                            pushing = false
                            showSyncDialog = false
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.remote_policy_sync_result, okCount, targets.size),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    },
                    enabled = !pushing && selectedIds.isNotEmpty(),
                    colors = m3FilledButtonColors(palette),
                ) {
                    Text(stringResource(R.string.remote_policy_sync_others), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSyncDialog = false }, enabled = !pushing) {
                    Text(stringResource(android.R.string.cancel), color = palette.muted)
                }
            },
        )
    }
}
