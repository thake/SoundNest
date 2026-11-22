/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.parental.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.ui.immersive.shell.ImmersiveBarAction
import io.github.thake.soundnest.core.ui.settings.M3SectionBlock
import io.github.thake.soundnest.core.ui.settings.M3SettingsScaffold
import io.github.thake.soundnest.core.ui.settings.M3SettingsSwitchRow
import io.github.thake.soundnest.core.ui.settings.m3FilledButtonColors
import io.github.thake.soundnest.core.ui.settings.m3SettingsSection
import io.github.thake.soundnest.core.ui.settings.rememberM3SettingsPalette
import io.github.thake.soundnest.feature.home.settings.m3HomeParentSettingsSections
import io.github.thake.soundnest.feature.library.settings.m3LibraryParentRow
import io.github.thake.soundnest.feature.localfiles.settings.m3LocalFilesParentRow
import io.github.thake.soundnest.feature.lyrics.settings.m3LyricsParentRows
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore
import io.github.thake.soundnest.feature.player.settings.m3PlaybackParentRow
import io.github.thake.soundnest.feature.radio.settings.m3RadioParentRow
import io.github.thake.soundnest.feature.speakers.jam.settings.m3JamParentRow
import io.github.thake.soundnest.feature.speakers.settings.SpeakersNicknameDialogHost
import io.github.thake.soundnest.feature.speakers.settings.m3SpeakersParentSettingsSections
import io.github.thake.soundnest.feature.speakers.settings.rememberSpeakersParentUiState
import io.github.thake.soundnest.feature.spokenword.audible.settings.AudibleParentControlsCard
import io.github.thake.soundnest.feature.spokenword.podcasts.settings.m3PodcastParentRows

@Composable
internal fun ParentHomeSectionsPage(
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
                contentDescription = stringResource(R.string.parent_pin_lock),
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
internal fun ParentSpotifyPage(
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
                contentDescription = stringResource(R.string.parent_pin_lock),
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
internal fun ParentAudiblePage(
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
                contentDescription = stringResource(R.string.parent_pin_lock),
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
                Box(Modifier.padding(16.dp)) {
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
internal fun ParentLocalAndRadioPage(
    context: Context,
    policy: ParentalPolicy,
    listState: LazyListState,
    back: () -> Unit,
    lockAndExit: () -> Unit,
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
                contentDescription = stringResource(R.string.parent_pin_lock),
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
    }
}

@Composable
internal fun ParentSpeakersNetworkPage(
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
                contentDescription = stringResource(R.string.parent_pin_lock),
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
internal fun ParentSecurityBackupPage(
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
                contentDescription = stringResource(R.string.parent_pin_lock),
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
