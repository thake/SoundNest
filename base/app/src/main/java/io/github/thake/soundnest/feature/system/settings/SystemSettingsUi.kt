/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.system.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SettingsApplications
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import io.github.thake.soundnest.BuildConfig
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.preferences.SettingsEntry
import io.github.thake.soundnest.core.ui.navigation.Routes
import io.github.thake.soundnest.core.ui.navigation.navigateSingleTop
import io.github.thake.soundnest.core.ui.settings.M3SettingsNavRow
import io.github.thake.soundnest.core.ui.settings.M3SettingsScaffold
import io.github.thake.soundnest.core.ui.settings.M3SettingsSwitchRow
import io.github.thake.soundnest.core.ui.settings.m3SettingsSection
import io.github.thake.soundnest.core.util.AppStorage
import io.github.thake.soundnest.feature.parental.security.AppSurface
import io.github.thake.soundnest.feature.parental.security.ParentSecurityManager
import io.github.thake.soundnest.feature.system.data.BatteryExemption
import io.github.thake.soundnest.host.android.Logbook
import io.github.thake.soundnest.host.android.SoundNestRuntime
import kotlinx.coroutines.launch
import java.io.File

/**
 * Unified Material Design 3 Settings UI surfaces and Settings Search entries for Storage,
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
    fun M3DataPage(back: () -> Unit) {
        val ctx = LocalContext.current
        val energy by EnergySettings.state.collectAsStateWithLifecycle()
        var exempt by remember { mutableStateOf(BatteryExemption.isExempt(ctx)) }
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            exempt = BatteryExemption.isExempt(ctx)
        }

        M3SettingsScaffold(
            title = stringResource(R.string.settings_section_data),
            onBack = back,
        ) { palette ->
            m3SettingsSection(
                key = "data:savers",
                palette = palette,
                header = ctx.getString(R.string.settings_data_section_savers),
            ) {
                row {
                    M3SettingsSwitchRow(
                        title = stringResource(R.string.data_saver_title),
                        detail = stringResource(R.string.data_saver_summary_soundnest),
                        checked = DataSaver.enabled,
                        palette = palette,
                        onCheckedChange = DataSaver::set,
                    )
                }
                row {
                    M3SettingsSwitchRow(
                        title = stringResource(R.string.energy_saver_title),
                        detail = stringResource(R.string.energy_saver_summary),
                        checked = energy.manualSaver,
                        palette = palette,
                        onCheckedChange = EnergySettings::setSaver,
                    )
                }
            }

            m3SettingsSection(
                key = "data:background",
                palette = palette,
                header = ctx.getString(R.string.background_playback_title),
            ) {
                row {
                    M3SettingsSwitchRow(
                        title = stringResource(R.string.background_playback_title),
                        detail = stringResource(if (exempt) R.string.background_playback_on else R.string.background_playback_off),
                        checked = exempt,
                        palette = palette,
                        onCheckedChange = {
                            if (exempt) ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                            else BatteryExemption.request(ctx)
                        },
                    )
                }
                if (BatteryExemption.isSamsung()) {
                    row {
                        M3SettingsNavRow(
                            icon = Icons.Rounded.SettingsApplications,
                            title = stringResource(R.string.background_playback_open),
                            detail = stringResource(R.string.background_playback_samsung),
                            palette = palette,
                            onClick = { BatteryExemption.openAppSettings(ctx) },
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun M3StoragePage(back: () -> Unit) {
        val ctx = LocalContext.current
        val scope = rememberCoroutineScope()
        val storage = AppStorage
        var refresh by remember { mutableStateOf(0) }
        var clearing by remember { mutableStateOf(false) }
        val usage by produceState<AppStorage.Usage?>(null, refresh) { value = storage.usage(ctx) }

        M3SettingsScaffold(
            title = stringResource(R.string.settings_section_storage),
            onBack = back,
        ) { palette ->
            m3SettingsSection(
                key = "storage:caches",
                palette = palette,
                header = ctx.getString(R.string.settings_section_storage),
            ) {
                usage?.let { u ->
                    row {
                        M3SettingsNavRow(
                            title = stringResource(R.string.settings_automix_cache),
                            value = Formatter.formatShortFileSize(ctx, u.automix),
                            palette = palette,
                            showChevron = false,
                        )
                    }
                }
                row {
                    M3SettingsNavRow(
                        icon = Icons.Rounded.CleaningServices,
                        title = stringResource(R.string.settings_storage_clear_cache),
                        detail = usage?.let {
                            stringResource(
                                R.string.settings_storage_clear_cache_detail_soundnest,
                                Formatter.formatShortFileSize(ctx, it.caches),
                            )
                        },
                        palette = palette,
                        enabled = !clearing,
                        showChevron = false,
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
    fun M3AppPage(navController: NavController, back: () -> Unit) {
        val ctx = LocalContext.current
        val secTick by ParentSecurityManager.sessionTick.collectAsState()
        val adminUnlocked = remember(secTick) { ParentSecurityManager.isAdminUnlocked(ctx) }

        M3SettingsScaffold(
            title = stringResource(R.string.settings_section_app),
            onBack = back,
        ) { palette ->
            m3SettingsSection(
                key = "app:repair",
                palette = palette,
                header = ctx.getString(R.string.settings_app_section_repair),
                hasLeadingIconBadges = true,
            ) {
                row {
                    M3SettingsNavRow(
                        icon = Icons.Rounded.Refresh,
                        title = stringResource(R.string.restart_player_title),
                        detail = stringResource(R.string.restart_player_detail),
                        palette = palette,
                        showChevron = false,
                        onClick = {
                            if (SoundNestRuntime.isStarted) {
                                SoundNestRuntime.host.reload()
                            }
                            Toast.makeText(ctx, ctx.getString(R.string.restart_player_done), Toast.LENGTH_SHORT).show()
                        },
                    )
                }
                row {
                    M3SettingsNavRow(
                        icon = Icons.Rounded.BugReport,
                        title = stringResource(R.string.diagnostic_export_title),
                        detail = stringResource(R.string.diagnostic_export_detail),
                        palette = palette,
                        locked = !adminUnlocked,
                        showChevron = false,
                        onClick = {
                            ParentSecurityManager.runProtected(ctx, AppSurface.DIAGNOSTIC_EXPORT) {
                                val file = runCatching { Logbook.export(ctx) }.getOrNull()
                                if (file != null) shareDiagnosticReport(ctx, file)
                                else Toast.makeText(ctx, ctx.getString(R.string.diagnostic_export_failed), Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                }
            }

            m3SettingsSection(
                key = "app:about",
                palette = palette,
                header = ctx.getString(R.string.settings_app_section_about),
                hasLeadingIconBadges = true,
            ) {
                row {
                    M3SettingsNavRow(
                        icon = Icons.Rounded.Info,
                        title = stringResource(R.string.menu_about_title),
                        detail = stringResource(R.string.about_detail),
                        value = BuildConfig.VERSION_NAME,
                        palette = palette,
                        onClick = { navController.navigateSingleTop(Routes.About.route) },
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
