/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.settings.ui.classic

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.ui.classic.AppAccent
import io.github.thake.soundnest.core.ui.classic.AppBackground
import io.github.thake.soundnest.core.ui.classic.AppMuted
import io.github.thake.soundnest.core.ui.classic.AppText
import io.github.thake.soundnest.core.ui.classic.Section
import io.github.thake.soundnest.core.ui.classic.tone
import io.github.thake.soundnest.core.ui.look.bottomBarsSpace
import io.github.thake.soundnest.feature.accounts.settings.AccountsSettingsUi
import io.github.thake.soundnest.feature.appearance.settings.AppearanceSettingsUi
import io.github.thake.soundnest.feature.library.settings.LibrarySettingsUi
import io.github.thake.soundnest.feature.lyrics.settings.LyricsSettingsUi
import io.github.thake.soundnest.feature.parental.security.AppSurface
import io.github.thake.soundnest.feature.parental.security.ParentSecurityManager
import io.github.thake.soundnest.feature.parental.settings.rememberParentalPolicy
import io.github.thake.soundnest.feature.player.settings.PlaybackSettingsUi
import io.github.thake.soundnest.feature.settings.SettingsSearch
import io.github.thake.soundnest.feature.system.settings.SystemSettingsUi

/**
 * Settings, on one page: streaming quality, playback & transitions, library, lyrics,
 * storage, display, accounts, and the player's own repair tools.
 */
@Composable
fun SettingsScreen(
    navController: NavController,
    initialTab: Int = 0,
    initialSub: String? = null,
) {
    val ctx = LocalContext.current
    val policy = rememberParentalPolicy()
    var sub by rememberSaveable(initialSub) { mutableStateOf(initialSub) }
    BackHandler(enabled = sub != null) { sub = null }
    var tab by rememberSaveable(initialTab) { mutableStateOf(initialTab) }
    val visibleTabOrder = remember(policy.lyrics.enabled) {
        TAB_ORDER.filter { it != TAB_LYRICS || policy.lyrics.enabled }
    }
    LaunchedEffect(policy.lyrics.enabled, tab) {
        if (tab == TAB_LYRICS && !policy.lyrics.enabled) tab = 0
    }
    var query by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = query != null) { query = null }
    Column(Modifier.fillMaxSize().background(AppBackground).statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(4.dp)) {
            IconButton(onClick = { if (sub != null) sub = null else navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.accessibility_navigate_back), tint = AppText)
            }
            Text(
                stringResource(if (sub == SUB_TRANSITIONS) R.string.settings_transitions_title else R.string.settings_title),
                color = AppText, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
            )
            if (sub == null) IconButton(onClick = { query = if (query == null) "" else null }) {
                Icon(Icons.Default.Search, contentDescription = stringResource(R.string.settings_search), tint = AppText)
            }
        }
        query?.let { q ->
            val focus = remember { FocusRequester() }
            OutlinedTextField(
                value = q, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text(stringResource(R.string.settings_search_hint), color = AppMuted) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = AppMuted) },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = AppText, unfocusedTextColor = AppText, focusedBorderColor = AppAccent,
                    unfocusedBorderColor = tone(0xFF3E3E3E), cursorColor = AppAccent,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    .then(Modifier.focusRequester(focus)),
            )
            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
            if (q.isNotBlank()) {
                val allowedEntries = SettingsSearch.filterForPolicy(SettingsSearch.classic, policy)
                val hits = SettingsSearch.search(allowedEntries, q) { ctx.getString(it) }
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
                    if (hits.isEmpty()) Text(
                        stringResource(R.string.settings_search_empty), color = AppMuted, fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    )
                    hits.forEach { h ->
                        val place = h.classicPlace ?: return@forEach
                        Column(
                            Modifier.fillMaxWidth().clickable {
                                query = null
                                if (place == SUB_TRANSITIONS) sub = SUB_TRANSITIONS
                                else {
                                    val target = place.toInt()
                                    if (target == TAB_ACCOUNT) {
                                        ParentSecurityManager.runProtected(
                                            ctx,
                                            AppSurface.ACCOUNTS_AND_LOGOUT,
                                        ) { tab = TAB_ACCOUNT }
                                    } else tab = target
                                }
                            }.padding(horizontal = 20.dp, vertical = 10.dp),
                        ) {
                            Text(stringResource(h.title), color = AppText, fontSize = 16.sp)
                            if (h.detail != 0) Text(stringResource(h.detail), color = AppMuted, fontSize = 12.sp)
                        }
                    }
                }
                return@Column
            }
        }
        if (sub == SUB_TRANSITIONS) {
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
                PlaybackSettingsUi.ClassicTransitionsSection(policy)
                Spacer(Modifier.height(bottomBarsSpace()))
            }
            return@Column
        }
        val tabs = listOf(
            R.string.settings_section_playback,
            R.string.settings_section_lyrics,
            R.string.settings_section_storage,
            R.string.settings_tab_appearance,
            R.string.settings_tab_about,
            R.string.accounts_title,
            R.string.settings_section_library,
        )
        val selectedIndex = visibleTabOrder.indexOf(tab).coerceAtLeast(0)
        ScrollableTabRow(
            selectedTabIndex = selectedIndex, containerColor = Color.Transparent, contentColor = AppText, edgePadding = 12.dp,
            indicator = { positions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(positions[selectedIndex.coerceAtMost(positions.lastIndex)]),
                    color = AppAccent,
                )
            },
            divider = { HorizontalDivider(color = tone(0xFF2A2A2A)) },
        ) {
            visibleTabOrder.forEach { i ->
                Tab(
                    selected = tab == i,
                    onClick = {
                        if (i == TAB_ACCOUNT) {
                            ParentSecurityManager.runProtected(
                                ctx,
                                AppSurface.ACCOUNTS_AND_LOGOUT,
                            ) { tab = TAB_ACCOUNT }
                        } else {
                            tab = i
                        }
                    },
                    text = { Text(stringResource(tabs[i]), fontSize = 14.sp, fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal) },
                    selectedContentColor = AppText, unselectedContentColor = AppMuted,
                )
            }
        }
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            if (tab == 0) {
                PlaybackSettingsUi.ClassicPlaybackTabContent(
                    navController = navController,
                    policy = policy,
                    onOpenTransitions = { sub = SUB_TRANSITIONS },
                )
            }
            if (tab == TAB_LIBRARY) {
                LibrarySettingsUi.ClassicLibraryTabContent(navController, policy)
            }
            if (tab == TAB_LYRICS && policy.lyrics.enabled) {
                Section(R.string.settings_section_lyrics)
                LyricsSettingsUi.ClassicLyricsTabContent(policy)
            }
            if (tab == TAB_STORAGE) {
                SystemSettingsUi.ClassicStorageTabContent()
            }
            if (tab == TAB_LOOK) {
                AppearanceSettingsUi.ClassicAppearanceTabContent(policy)
            }
            if (tab == TAB_ABOUT) {
                SystemSettingsUi.ClassicAboutTabContent()
            }
            if (tab == TAB_ACCOUNT) {
                AccountsSettingsUi.ClassicAccountsTabContent(onLockedRedirect = { tab = 0 })
            }
            Spacer(Modifier.height(bottomBarsSpace()))
        }
    }
}

private const val SUB_TRANSITIONS = "transitions"
private const val TAB_LYRICS = 1
private const val TAB_STORAGE = 2
private const val TAB_LOOK = 3
private const val TAB_ABOUT = 4
private const val TAB_ACCOUNT = 5
private const val TAB_LIBRARY = 6
/** Kid-accessible tabs first (playback, appearance, library, lyrics, storage, about), PIN-locked accounts last. */
private val TAB_ORDER = listOf(0, TAB_LOOK, TAB_LIBRARY, TAB_LYRICS, TAB_STORAGE, TAB_ABOUT, TAB_ACCOUNT)
