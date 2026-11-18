/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.settings.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.preferences.SettingsEntry
import io.github.thake.soundnest.core.ui.immersive.components.ImmersiveSearchField
import io.github.thake.soundnest.core.ui.immersive.shell.ImmersiveBarAction
import io.github.thake.soundnest.core.ui.navigation.Routes
import io.github.thake.soundnest.core.ui.navigation.navigateSingleTop
import io.github.thake.soundnest.core.ui.settings.M3GroupCard
import io.github.thake.soundnest.core.ui.settings.M3SettingsNavRow
import io.github.thake.soundnest.core.ui.settings.M3SettingsScaffold
import io.github.thake.soundnest.core.ui.settings.M3SubpageHost
import io.github.thake.soundnest.core.ui.settings.m3SettingsBlock
import io.github.thake.soundnest.core.ui.settings.m3SettingsSection
import io.github.thake.soundnest.feature.accounts.settings.AccountsSettingsUi
import io.github.thake.soundnest.feature.accounts.ui.switchAccountDetail
import io.github.thake.soundnest.feature.appearance.settings.AppearanceSettingsUi
import io.github.thake.soundnest.feature.appearance.settings.Look
import io.github.thake.soundnest.feature.appearance.settings.LookPref
import io.github.thake.soundnest.feature.equalizer.settings.EqualizerSettingsUi
import io.github.thake.soundnest.feature.library.settings.LibrarySettingsUi
import io.github.thake.soundnest.feature.lyrics.settings.LyricsSettingsUi
import io.github.thake.soundnest.feature.parental.security.AppSurface
import io.github.thake.soundnest.feature.parental.security.ParentSecurityManager
import io.github.thake.soundnest.feature.parental.settings.rememberParentalPolicy
import io.github.thake.soundnest.feature.player.settings.CrossfadePref
import io.github.thake.soundnest.feature.player.settings.PlaybackSettingsUi
import io.github.thake.soundnest.feature.settings.SettingsSearch
import io.github.thake.soundnest.feature.settings.ui.immersive.SettingsPage
import io.github.thake.soundnest.feature.system.settings.SystemSettingsUi

/**
 * Unified Material Design 3 Settings hub and progressive-disclosure subpage hierarchy shared by
 * both Classic (`SettingsScreen`) and Immersive (`ImmersiveSettingsScreen`) looks.
 */
@Composable
fun MaterialSettingsScreen(
    navController: NavController,
    initialPage: SettingsPage = SettingsPage.Root,
) {
    M3SubpageHost(
        initialPage = initialPage,
        rootPage = SettingsPage.Root,
        valueOf = SettingsPage::valueOf,
        onExit = { navController.popBackStack() },
        label = "settings_pages",
    ) { page, open, back, _ ->
        when (page) {
            SettingsPage.Root -> SettingsRootHub(navController, open, back)
            SettingsPage.Playback -> PlaybackSettingsUi.M3PlaybackPage(
                navController = navController,
                openStreamingWifi = { open(SettingsPage.StreamingWifi) },
                openStreamingCell = { open(SettingsPage.StreamingCell) },
                back = back,
            )
            SettingsPage.StreamingWifi -> PlaybackSettingsUi.M3StreamingPage(cellular = false, back = back)
            SettingsPage.StreamingCell -> PlaybackSettingsUi.M3StreamingPage(cellular = true, back = back)
            SettingsPage.Crossfade -> PlaybackSettingsUi.M3CrossfadePage(back = back)
            SettingsPage.Display -> AppearanceSettingsUi.M3DisplayPage(
                openLook = { open(SettingsPage.Look) },
                openRotation = { open(SettingsPage.Rotation) },
                openLanguage = { open(SettingsPage.Language) },
                back = back,
            )
            SettingsPage.Look -> AppearanceSettingsUi.M3LookPage(back = back)
            SettingsPage.Rotation -> AppearanceSettingsUi.M3RotationPage(back = back)
            SettingsPage.Language -> AppearanceSettingsUi.M3LanguagePage(back = back)
            SettingsPage.Library -> LibrarySettingsUi.M3LibraryPage(navController = navController, back = back)
            SettingsPage.LocalAndRadio -> M3LocalAndRadioSettingsPage(
                navController = navController,
                openTransitions = { open(SettingsPage.Crossfade) },
                back = back,
            )
            SettingsPage.Lyrics -> LyricsSettingsUi.M3LyricsPage(
                openLyricsLanguage = { open(SettingsPage.LyricsLanguage) },
                back = back,
            )
            SettingsPage.LyricsLanguage -> LyricsSettingsUi.M3LyricsLanguagePage(back = back)
            SettingsPage.Data -> SystemSettingsUi.M3DataPage(back = back)
            SettingsPage.Storage -> SystemSettingsUi.M3StoragePage(back = back)
            SettingsPage.App -> SystemSettingsUi.M3AppPage(navController = navController, back = back)
            SettingsPage.Accounts -> AccountsSettingsUi.M3AccountsPage(back = back)
        }
    }
}

@Composable
private fun SettingsRootHub(
    navController: NavController,
    open: (SettingsPage) -> Unit,
    back: () -> Unit,
) {
    val ctx = LocalContext.current
    val policy = rememberParentalPolicy()
    val secTick by ParentSecurityManager.sessionTick.collectAsState()
    val unlocked = remember(secTick) { ParentSecurityManager.isAdminUnlocked(ctx) }
    val look = LookPref.look
    val switchDetail = switchAccountDetail()
    var query by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = query != null) { query = null }
    val focus = remember { FocusRequester() }

    M3SettingsScaffold(
        title = stringResource(R.string.settings_title),
        onBack = back,
        classicActions = {
            IconButton(onClick = { query = if (query == null) "" else null }) {
                Icon(
                    imageVector = if (query == null) Icons.Rounded.Search else Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.settings_search),
                )
            }
        },
        immersiveActions = {
            ImmersiveBarAction(
                icon = if (query == null) Icons.Rounded.Search else Icons.Rounded.Close,
                contentDescription = stringResource(R.string.settings_search),
                onClick = { query = if (query == null) "" else null },
            )
        },
    ) { palette ->
        query?.let { q ->
            m3SettingsBlock(key = "settings:search-field", palette = palette) {
                if (palette.immersive) {
                    ImmersiveSearchField(
                        query = q,
                        onQueryChange = { query = it },
                        onSubmit = {},
                        focusRequester = focus,
                        placeholder = stringResource(R.string.settings_search_hint),
                    )
                } else {
                    OutlinedTextField(
                        value = q,
                        onValueChange = { query = it },
                        singleLine = true,
                        placeholder = {
                            Text(
                                stringResource(R.string.settings_search_hint),
                                color = palette.muted,
                                fontFamily = palette.fontFamily,
                            )
                        },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = palette.muted) },
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = palette.text,
                            unfocusedTextColor = palette.text,
                            focusedBorderColor = palette.accent,
                            unfocusedBorderColor = palette.divider,
                            cursorColor = palette.accent,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focus),
                    )
                }
                LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
            }

            if (q.isNotBlank()) {
                val baseEntries = if (palette.immersive) SettingsSearch.immersive else SettingsSearch.classic
                val allowedEntries = SettingsSearch.filterForPolicy(baseEntries, policy)
                val hits = SettingsSearch.search(allowedEntries, q) { ctx.getString(it) }
                if (hits.isEmpty()) {
                    m3SettingsBlock(key = "settings:search-empty", palette = palette) {
                        M3GroupCard(palette = palette) {
                            Text(
                                text = stringResource(R.string.settings_search_empty),
                                color = palette.muted,
                                fontFamily = palette.fontFamily,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(20.dp),
                            )
                        }
                    }
                } else {
                    m3SettingsSection(key = "settings:search-hits", palette = palette) {
                        hits.forEach { entry ->
                            val targetPage = resolveSearchPage(entry) ?: return@forEach
                            row {
                                M3SettingsNavRow(
                                    title = stringResource(entry.title),
                                    detail = if (entry.detail != 0) stringResource(entry.detail) else null,
                                    palette = palette,
                                    locked = (targetPage == SettingsPage.Accounts || entry.title == R.string.parent_settings_title) && !unlocked,
                                    onClick = {
                                        query = null
                                        when {
                                            entry.title == R.string.parent_settings_title -> {
                                                ParentSecurityManager.runProtected(ctx, AppSurface.PARENT_CONTROLS) {
                                                    navController.navigateSingleTop(Routes.ParentSettings.route)
                                                }
                                            }
                                            targetPage == SettingsPage.Accounts -> {
                                                ParentSecurityManager.runProtected(ctx, AppSurface.ACCOUNTS_AND_LOGOUT) {
                                                    open(SettingsPage.Accounts)
                                                }
                                            }
                                            else -> open(targetPage)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
                return@M3SettingsScaffold
            }
        }

        // 1. Audio & Playback
        m3SettingsSection(
            key = "settings:audio-playback",
            palette = palette,
            header = ctx.getString(R.string.settings_group_audio_playback),
            hasLeadingIconBadges = true,
        ) {
            row {
                M3SettingsNavRow(
                    icon = Icons.Rounded.PlayArrow,
                    title = stringResource(R.string.settings_section_playback),
                    detail = stringResource(R.string.settings_playback_row_detail),
                    palette = palette,
                    onClick = { open(SettingsPage.Playback) },
                )
            }
        }

        // 2. Display & Content
        m3SettingsSection(
            key = "settings:display-content",
            palette = palette,
            header = ctx.getString(R.string.settings_group_display_content),
            hasLeadingIconBadges = true,
        ) {
            row {
                M3SettingsNavRow(
                    icon = Icons.Rounded.Palette,
                    title = stringResource(R.string.settings_section_display),
                    detail = stringResource(R.string.settings_display_row_detail),
                    value = stringResource(if (look == Look.CLASSIC) R.string.look_classic else R.string.look_immersive),
                    palette = palette,
                    onClick = { open(SettingsPage.Display) },
                )
            }
            row {
                M3SettingsNavRow(
                    icon = Icons.Rounded.LibraryMusic,
                    title = stringResource(R.string.settings_section_library),
                    detail = stringResource(R.string.settings_library_row_detail),
                    palette = palette,
                    onClick = { open(SettingsPage.Library) },
                )
            }
            if (policy.localFiles.enabled || policy.radio.enabled) {
                row {
                    M3SettingsNavRow(
                        icon = Icons.Rounded.Radio,
                        title = stringResource(R.string.settings_section_local_radio),
                        detail = stringResource(R.string.settings_local_radio_row_detail),
                        palette = palette,
                        testTag = "settings_nav_local_radio",
                        onClick = { open(SettingsPage.LocalAndRadio) },
                    )
                }
            }
            if (policy.lyrics.enabled) {
                row {
                    M3SettingsNavRow(
                        icon = Icons.Rounded.Lyrics,
                        title = stringResource(R.string.settings_section_lyrics),
                        detail = stringResource(R.string.settings_lyrics_row_detail),
                        palette = palette,
                        onClick = { open(SettingsPage.Lyrics) },
                    )
                }
            }
        }

        // 3. Data, Storage & System
        m3SettingsSection(
            key = "settings:data-system",
            palette = palette,
            header = ctx.getString(R.string.settings_group_data_system),
            hasLeadingIconBadges = true,
        ) {
            row {
                M3SettingsNavRow(
                    icon = Icons.Rounded.SignalCellularAlt,
                    title = stringResource(R.string.settings_section_data),
                    detail = stringResource(R.string.settings_data_row_detail),
                    palette = palette,
                    onClick = { open(SettingsPage.Data) },
                )
            }
            row {
                M3SettingsNavRow(
                    icon = Icons.Rounded.Storage,
                    title = stringResource(R.string.settings_section_storage),
                    detail = stringResource(R.string.settings_storage_row_detail),
                    palette = palette,
                    onClick = { open(SettingsPage.Storage) },
                )
            }
            row {
                M3SettingsNavRow(
                    icon = Icons.Rounded.Info,
                    title = stringResource(R.string.settings_section_app),
                    detail = stringResource(R.string.settings_app_row_detail),
                    palette = palette,
                    onClick = { open(SettingsPage.App) },
                )
            }
        }

        // 4. Parent & Account (PIN-Protected)
        m3SettingsSection(
            key = "settings:parent-account",
            palette = palette,
            header = ctx.getString(R.string.settings_group_parent_account),
            hasLeadingIconBadges = true,
        ) {
            row {
                M3SettingsNavRow(
                    icon = Icons.Rounded.Security,
                    title = stringResource(R.string.parent_settings_title),
                    detail = if (unlocked) stringResource(R.string.parental_controls_detail_unlocked)
                    else stringResource(R.string.parental_controls_detail_locked),
                    detailColor = if (unlocked) palette.accent else palette.muted,
                    palette = palette,
                    testTag = "compact_parental_controls_row",
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (unlocked) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(palette.accent.copy(alpha = 0.16f))
                                        .clickable(role = Role.Button) { ParentSecurityManager.lockNow(ctx) }
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.parent_pin_lock),
                                        color = palette.accent,
                                        fontFamily = palette.fontFamily,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = null,
                                    tint = palette.tertiary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = null,
                                tint = palette.tertiary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                    onClick = {
                        ParentSecurityManager.runProtected(ctx, AppSurface.PARENT_CONTROLS) {
                            navController.navigateSingleTop(Routes.ParentSettings.route)
                        }
                    },
                )
            }
            row {
                M3SettingsNavRow(
                    icon = Icons.Rounded.Group,
                    title = stringResource(R.string.accounts_title),
                    detail = switchDetail,
                    palette = palette,
                    locked = !unlocked,
                    onClick = {
                        ParentSecurityManager.runProtected(ctx, AppSurface.ACCOUNTS_AND_LOGOUT) {
                            open(SettingsPage.Accounts)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun M3LocalAndRadioSettingsPage(
    navController: NavController,
    openTransitions: () -> Unit,
    back: () -> Unit,
) {
    val ctx = LocalContext.current
    val policy = rememberParentalPolicy()
    val allowed = policy.localFiles.enabled || policy.radio.enabled
    LaunchedEffect(allowed) {
        if (!allowed) back()
    }
    M3SettingsScaffold(
        title = stringResource(R.string.settings_section_local_radio),
        onBack = back,
    ) { palette ->
        m3SettingsSection(
            key = "local_radio:audio",
            palette = palette,
            header = ctx.getString(R.string.local_radio_section_audio),
            hasLeadingIconBadges = true,
        ) {
            with(EqualizerSettingsUi) {
                m3SettingsRow(navController = navController, policy = policy, palette = palette)
            }
            if (policy.localFiles.enabled) {
                row {
                    M3SettingsNavRow(
                        icon = Icons.Rounded.GraphicEq,
                        title = stringResource(R.string.settings_transitions_title),
                        detail = stringResource(R.string.settings_transitions_detail_soundnest),
                        value = PlaybackSettingsUi.transitionValue(CrossfadePref.seconds),
                        palette = palette,
                        testTag = "local_radio_transitions_nav",
                        onClick = openTransitions,
                    )
                }
            }
        }
    }
}

private fun resolveSearchPage(entry: SettingsEntry): SettingsPage? {
    entry.immersivePage?.let { name ->
        runCatching { SettingsPage.valueOf(name) }.getOrNull()?.let { return it }
    }
    return when (entry.classicPlace) {
        "transitions" -> SettingsPage.Crossfade
        "0" -> SettingsPage.Playback
        "1" -> SettingsPage.Lyrics
        "2" -> SettingsPage.Storage
        "3" -> SettingsPage.Display
        "4" -> SettingsPage.App
        "5" -> SettingsPage.Accounts
        "6" -> SettingsPage.Library
        "7" -> SettingsPage.LocalAndRadio
        else -> null
    }
}
