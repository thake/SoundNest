/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.system.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thake.soundnest.BuildConfig
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.preferences.SettingsEntry
import io.github.thake.soundnest.core.ui.classic.AppMuted
import io.github.thake.soundnest.core.ui.classic.AppText
import io.github.thake.soundnest.core.ui.immersive.pages.ImmersiveGroupRow
import io.github.thake.soundnest.core.ui.immersive.pages.ImmersivePage
import io.github.thake.soundnest.core.ui.immersive.pages.ImmersiveSwitchRow
import io.github.thake.soundnest.core.ui.immersive.pages.immersiveGroup
import io.github.thake.soundnest.core.util.AppStorage
import io.github.thake.soundnest.feature.parental.security.AppSurface
import io.github.thake.soundnest.feature.parental.security.ParentSecurityManager
import io.github.thake.soundnest.feature.system.data.BatteryExemption
import io.github.thake.soundnest.feature.system.ui.AboutSheet
import io.github.thake.soundnest.host.android.Logbook
import io.github.thake.soundnest.host.android.SoundNestRuntime
import io.github.thake.soundnest.core.ui.classic.OptionRow
import io.github.thake.soundnest.core.ui.classic.Section
import kotlinx.coroutines.launch
import java.io.File

/**
 * Classic & Immersive Settings UI surfaces and Settings Search entries for Storage,
 * Energy/Data/Battery, and App/About/Repair (including PIN-gated diagnostic export).
 */
object SystemSettingsUi {
    val settingsEntries: List<SettingsEntry> = listOf(
        SettingsEntry(
            title = R.string.settings_storage_clear_cache,
            classicPlace = "2",
            immersivePage = "Storage",
        ),
        SettingsEntry(
            title = R.string.settings_section_storage,
            classicPlace = "2",
            immersivePage = "Storage",
        ),
        SettingsEntry(
            title = R.string.energy_saver_title,
            detail = R.string.energy_saver_summary,
            classicPlace = "0",
            immersivePage = "Data",
        ),
        SettingsEntry(
            title = R.string.data_saver_title,
            detail = R.string.data_saver_summary_soundnest,
            classicPlace = "0",
            immersivePage = "Data",
        ),
        SettingsEntry(
            title = R.string.background_playback_title,
            classicPlace = "0",
            immersivePage = "Data",
        ),
        SettingsEntry(
            title = R.string.settings_section_data,
            classicPlace = "0",
            immersivePage = "Data",
        ),
        SettingsEntry(
            title = R.string.creator_biography_section_title,
            detail = R.string.about_detail,
            classicPlace = "4",
            immersivePage = "App",
        ),
        SettingsEntry(
            title = R.string.restart_player_title,
            detail = R.string.restart_player_detail,
            classicPlace = "4",
            immersivePage = "App",
        ),
        SettingsEntry(
            title = R.string.diagnostic_export_title,
            detail = R.string.diagnostic_export_detail,
            classicPlace = "4",
            immersivePage = "App",
        ),
        SettingsEntry(
            title = R.string.settings_tab_about,
            classicPlace = "4",
        ),
        SettingsEntry(
            title = R.string.settings_section_app,
            classicPlace = "4",
            immersivePage = "App",
        ),
    )

    @Composable
    fun ClassicDataAndBatterySection() {
        val ctx = LocalContext.current
        val energy by EnergySettings.state.collectAsStateWithLifecycle()
        Section(R.string.settings_section_data)
        OptionRow(
            label = stringResource(R.string.energy_saver_title),
            detail = stringResource(R.string.energy_saver_summary),
            checked = energy.manualSaver,
            onChecked = EnergySettings::setSaver,
        )
        OptionRow(
            label = stringResource(R.string.data_saver_title),
            detail = stringResource(R.string.data_saver_summary_soundnest),
            checked = DataSaver.enabled,
            onChecked = DataSaver::set,
        )
        var exempt by remember { mutableStateOf(BatteryExemption.isExempt(ctx)) }
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            exempt = BatteryExemption.isExempt(ctx)
        }
        OptionRow(
            label = stringResource(R.string.background_playback_title),
            detail = stringResource(if (exempt) R.string.background_playback_on else R.string.background_playback_off),
            checked = exempt,
            onChecked = {
                if (exempt) ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                else BatteryExemption.request(ctx)
            },
        )
        if (BatteryExemption.isSamsung()) Column(
            Modifier
                .fillMaxWidth()
                .clickable { BatteryExemption.openAppSettings(ctx) }
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Text(stringResource(R.string.background_playback_open), color = AppText, fontSize = 16.sp)
            Text(stringResource(R.string.background_playback_samsung), color = AppMuted, fontSize = 12.sp)
        }
    }

    @Composable
    fun ClassicStorageTabContent() {
        Section(R.string.settings_section_storage)
        val ctx = LocalContext.current
        val scope = rememberCoroutineScope()
        val storage = AppStorage
        var refresh by remember { mutableStateOf(0) }
        var clearing by remember { mutableStateOf(false) }
        val usage by produceState<AppStorage.Usage?>(null, refresh) { value = storage.usage(ctx) }
        usage?.let { u ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Text(stringResource(R.string.settings_automix_cache), color = AppText, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Text(Formatter.formatShortFileSize(ctx, u.automix), color = AppMuted, fontSize = 14.sp)
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = !clearing) {
                    clearing = true
                    scope.launch {
                        storage.clearCaches(ctx)
                        clearing = false
                        refresh++
                        Toast.makeText(ctx, ctx.getString(R.string.settings_storage_cleared), Toast.LENGTH_SHORT).show()
                    }
                }
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Text(stringResource(R.string.settings_storage_clear_cache), color = AppText, fontSize = 16.sp)
            Text(
                usage?.let {
                    stringResource(
                        R.string.settings_storage_clear_cache_detail_soundnest,
                        Formatter.formatShortFileSize(ctx, it.caches),
                    )
                }.orEmpty(),
                color = AppMuted,
                fontSize = 12.sp,
            )
        }
    }

    @Composable
    fun ClassicAboutTabContent() {
        val ctx = LocalContext.current
        var open by remember { mutableStateOf(false) }
        if (open) AboutSheet(onDismiss = { open = false })
        Section(R.string.settings_section_app)
        Column(
            Modifier
                .fillMaxWidth()
                .clickable { open = true }
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Text(stringResource(R.string.creator_biography_section_title), color = AppText, fontSize = 16.sp)
            Text(stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME), color = AppMuted, fontSize = 12.sp)
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clickable {
                    SoundNestRuntime.host.reload()
                    Toast.makeText(ctx, ctx.getString(R.string.restart_player_done), Toast.LENGTH_SHORT).show()
                }
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Text(stringResource(R.string.restart_player_title), color = AppText, fontSize = 16.sp)
            Text(stringResource(R.string.restart_player_detail), color = AppMuted, fontSize = 12.sp)
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clickable {
                    ParentSecurityManager.runProtected(
                        ctx,
                        AppSurface.DIAGNOSTIC_EXPORT,
                    ) {
                        val file = runCatching { Logbook.export(ctx) }.getOrNull()
                        if (file != null) shareDiagnosticReport(ctx, file)
                        else Toast.makeText(ctx, ctx.getString(R.string.diagnostic_export_failed), Toast.LENGTH_SHORT).show()
                    }
                }
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Text(stringResource(R.string.diagnostic_export_title), color = AppText, fontSize = 16.sp)
            Text(stringResource(R.string.diagnostic_export_detail), color = AppMuted, fontSize = 12.sp)
        }
    }

    @Composable
    fun ImmersiveStoragePage(back: () -> Unit) {
        val ctx = LocalContext.current
        val scope = rememberCoroutineScope()
        val storage = AppStorage
        var refresh by remember { mutableStateOf(0) }
        var clearing by remember { mutableStateOf(false) }
        val usage by produceState<AppStorage.Usage?>(null, refresh) { value = storage.usage(ctx) }
        ImmersivePage(title = stringResource(R.string.settings_section_storage), onBack = back) {
            immersiveGroup("caches") {
                usage?.let { u ->
                    row {
                        ImmersiveGroupRow(
                            stringResource(R.string.settings_automix_cache),
                            value = Formatter.formatShortFileSize(ctx, u.automix),
                        )
                    }
                }
                row {
                    ImmersiveGroupRow(
                        stringResource(R.string.settings_storage_clear_cache),
                        detail = usage?.let {
                            stringResource(
                                R.string.settings_storage_clear_cache_detail_soundnest,
                                Formatter.formatShortFileSize(ctx, it.caches),
                            )
                        },
                        enabled = !clearing,
                        onClick = {
                            clearing = true
                            scope.launch {
                                storage.clearCaches(ctx)
                                clearing = false
                                refresh++
                                Toast.makeText(ctx, ctx.getString(R.string.settings_storage_cleared), Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                }
            }
        }
    }

    @Composable
    fun ImmersiveDataPage(back: () -> Unit) {
        val ctx = LocalContext.current
        val energy by EnergySettings.state.collectAsStateWithLifecycle()
        var exempt by remember { mutableStateOf(BatteryExemption.isExempt(ctx)) }
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            exempt = BatteryExemption.isExempt(ctx)
        }
        ImmersivePage(title = stringResource(R.string.settings_section_data), onBack = back) {
            immersiveGroup("data") {
                row {
                    ImmersiveSwitchRow(
                        stringResource(R.string.energy_saver_title),
                        checked = energy.manualSaver,
                        onChecked = EnergySettings::setSaver,
                        detail = stringResource(R.string.energy_saver_summary),
                    )
                }
                row {
                    ImmersiveSwitchRow(
                        stringResource(R.string.data_saver_title),
                        checked = DataSaver.enabled,
                        onChecked = DataSaver::set,
                        detail = stringResource(R.string.data_saver_summary_soundnest),
                    )
                }
                row {
                    ImmersiveSwitchRow(
                        stringResource(R.string.background_playback_title),
                        checked = exempt,
                        onChecked = {
                            if (exempt) ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                            else BatteryExemption.request(ctx)
                        },
                        detail = stringResource(if (exempt) R.string.background_playback_on else R.string.background_playback_off),
                    )
                }
                if (BatteryExemption.isSamsung()) row {
                    ImmersiveGroupRow(
                        stringResource(R.string.background_playback_open),
                        detail = stringResource(R.string.background_playback_samsung),
                        chevron = true,
                        onClick = { BatteryExemption.openAppSettings(ctx) },
                    )
                }
            }
        }
    }

    @Composable
    fun ImmersiveAppPage(back: () -> Unit) {
        val ctx = LocalContext.current
        var about by remember { mutableStateOf(false) }
        if (about) AboutSheet(onDismiss = { about = false })
        ImmersivePage(title = stringResource(R.string.settings_section_app), onBack = back) {
            immersiveGroup("about") {
                row {
                    ImmersiveGroupRow(
                        stringResource(R.string.creator_biography_section_title),
                        detail = stringResource(R.string.about_detail),
                        value = BuildConfig.VERSION_NAME,
                        chevron = true,
                        onClick = { about = true },
                    )
                }
            }
            immersiveGroup("repair") {
                row {
                    ImmersiveGroupRow(
                        stringResource(R.string.restart_player_title),
                        detail = stringResource(R.string.restart_player_detail),
                        onClick = {
                            SoundNestRuntime.host.reload()
                            Toast.makeText(ctx, ctx.getString(R.string.restart_player_done), Toast.LENGTH_SHORT).show()
                        },
                    )
                }
                row {
                    ImmersiveGroupRow(
                        stringResource(R.string.diagnostic_export_title),
                        detail = stringResource(R.string.diagnostic_export_detail),
                        onClick = {
                            ParentSecurityManager.runProtected(
                                ctx,
                                AppSurface.DIAGNOSTIC_EXPORT,
                            ) {
                                val file = runCatching { Logbook.export(ctx) }.getOrNull()
                                if (file != null) shareDiagnosticReport(ctx, file)
                                else Toast.makeText(ctx, ctx.getString(R.string.diagnostic_export_failed), Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                }
            }
        }
    }
}

/**
 * Hands the exported report to the share sheet through the app's FileProvider.
 */
fun shareDiagnosticReport(context: Context, file: File) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "SoundNest diagnostic report")
    }
    val uri = runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrNull()
    if (uri != null) {
        send.type = "application/octet-stream"
        send.putExtra(Intent.EXTRA_STREAM, uri)
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    } else {
        send.putExtra(Intent.EXTRA_TEXT, "SoundNest diagnostic report: ${file.absolutePath}")
    }
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
