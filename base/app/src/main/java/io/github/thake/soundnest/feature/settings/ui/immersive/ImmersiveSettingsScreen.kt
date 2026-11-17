/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.settings.ui.immersive

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavController
import io.github.thake.soundnest.R
import io.github.thake.soundnest.core.ui.immersive.Immersive
import io.github.thake.soundnest.core.ui.immersive.components.ImmersiveSearchField
import io.github.thake.soundnest.core.ui.immersive.motion.ImmersiveMotion
import io.github.thake.soundnest.core.ui.immersive.pages.ImmersiveGroupRow
import io.github.thake.soundnest.core.ui.immersive.pages.ImmersivePage
import io.github.thake.soundnest.core.ui.immersive.pages.immersiveBlock
import io.github.thake.soundnest.core.ui.immersive.pages.immersiveGroup
import io.github.thake.soundnest.core.ui.immersive.shell.ImmersiveBarAction
import io.github.thake.soundnest.core.ui.look.rememberReduceMotion
import io.github.thake.soundnest.core.ui.navigation.Routes
import io.github.thake.soundnest.feature.accounts.settings.AccountsSettingsUi
import io.github.thake.soundnest.feature.accounts.ui.switchAccountDetail
import io.github.thake.soundnest.feature.appearance.settings.AppearanceSettingsUi
import io.github.thake.soundnest.feature.library.settings.LibrarySettingsUi
import io.github.thake.soundnest.feature.lyrics.settings.LyricsSettingsUi
import io.github.thake.soundnest.feature.parental.security.AppSurface
import io.github.thake.soundnest.feature.parental.security.ParentSecurityManager
import io.github.thake.soundnest.feature.parental.settings.rememberParentalPolicy
import io.github.thake.soundnest.feature.player.settings.CrossfadePref
import io.github.thake.soundnest.feature.player.settings.PlaybackSettingsUi
import io.github.thake.soundnest.feature.settings.SettingsSearch
import io.github.thake.soundnest.feature.system.settings.SystemSettingsUi

/** Where the Immersive Settings are: the list of categories, one category, or a choice inside it. */
enum class SettingsPage { Root, Playback, Lyrics, StreamingWifi, StreamingCell, Crossfade, Storage, Data, Display, Look, Rotation, Language, LyricsLanguage, App, Accounts, Library }

/**
 * Settings of the Immersive look, replacing the Classic `SettingsScreen`: the same preferences
 * and actions (every switch writes through the same stores) as iOS-style inset groups, one page
 * per category and one for each choice (quality, language, look...). A pushed page slides in on a
 * spring and back leaves it; with "reduce motion" they cross-fade.
 */
@Composable
fun ImmersiveSettingsScreen(
    navController: NavController,
    initialPage: SettingsPage = SettingsPage.Root,
) {
    val stack = rememberSaveable(
        initialPage,
        saver = listSaver(
            save = { list -> list.map { it.name } },
            restore = { names -> names.mapNotNull { runCatching { SettingsPage.valueOf(it) }.getOrNull() }.toMutableStateList() },
        ),
    ) {
        if (initialPage == SettingsPage.Root) mutableStateListOf()
        else mutableStateListOf(initialPage)
    }
    val page = stack.lastOrNull() ?: SettingsPage.Root
    val reduce = rememberReduceMotion()
    var pushing by remember { mutableStateOf(true) }
    val pop: () -> Unit = { pushing = false; stack.removeAt(stack.lastIndex) }
    BackHandler(enabled = stack.isNotEmpty(), onBack = pop)
    val open: (SettingsPage) -> Unit = { pushing = true; stack += it }
    val back: () -> Unit = { if (stack.isNotEmpty()) pop() else navController.popBackStack() }
    AnimatedContent(
        targetState = page,
        transitionSpec = {
            when {
                reduce -> fadeIn(tween(ImmersiveMotion.FADE_MS)) togetherWith fadeOut(tween(ImmersiveMotion.FADE_MS))
                !pushing ->
                    (slideInHorizontally(tween(ImmersiveMotion.MEDIUM_MS)) { -it / 3 } + fadeIn(tween(ImmersiveMotion.MEDIUM_MS))) togetherWith
                        (slideOutHorizontally(tween(ImmersiveMotion.MEDIUM_MS)) { it } + fadeOut(tween(160)))
                else ->
                    (slideInHorizontally(spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset(1, 1))) { it } + fadeIn(tween(160))) togetherWith
                        (slideOutHorizontally(tween(ImmersiveMotion.MEDIUM_MS)) { -it / 3 } + fadeOut(tween(ImmersiveMotion.MEDIUM_MS)))
            }
        },
        label = "settings pages",
    ) { p ->
        when (p) {
            SettingsPage.Root -> SettingsRoot(navController, open, back)
            SettingsPage.Playback -> PlaybackSettingsUi.ImmersivePlaybackPage(
                navController = navController,
                openStreamingWifi = { open(SettingsPage.StreamingWifi) },
                openStreamingCell = { open(SettingsPage.StreamingCell) },
                openCrossfade = { open(SettingsPage.Crossfade) },
                back = back,
            )
            SettingsPage.Lyrics -> LyricsSettingsUi.ImmersiveLyricsPage(
                openLyricsLanguage = { open(SettingsPage.LyricsLanguage) },
                back = back,
            )
            SettingsPage.StreamingWifi -> PlaybackSettingsUi.ImmersiveStreamingPage(cellular = false, back)
            SettingsPage.StreamingCell -> PlaybackSettingsUi.ImmersiveStreamingPage(cellular = true, back)
            SettingsPage.Crossfade -> PlaybackSettingsUi.ImmersiveCrossfadePage(back)
            SettingsPage.Storage -> SystemSettingsUi.ImmersiveStoragePage(back)
            SettingsPage.Data -> SystemSettingsUi.ImmersiveDataPage(back)
            SettingsPage.Display -> AppearanceSettingsUi.ImmersiveDisplayPage(
                openLook = { open(SettingsPage.Look) },
                openRotation = { open(SettingsPage.Rotation) },
                openLanguage = { open(SettingsPage.Language) },
                back = back,
            )
            SettingsPage.Look -> AppearanceSettingsUi.ImmersiveLookPage(back)
            SettingsPage.Rotation -> AppearanceSettingsUi.ImmersiveRotationPage(back)
            SettingsPage.Language -> AppearanceSettingsUi.ImmersiveLanguagePage(back)
            SettingsPage.LyricsLanguage -> LyricsSettingsUi.ImmersiveLyricsLanguagePage(back)
            SettingsPage.App -> SystemSettingsUi.ImmersiveAppPage(back)
            SettingsPage.Accounts -> AccountsSettingsUi.ImmersiveAccountsPage(back)
            SettingsPage.Library -> LibrarySettingsUi.ImmersiveLibraryPage(navController, back)
        }
    }
}

@Composable
private fun SettingsRoot(navController: NavController, open: (SettingsPage) -> Unit, back: () -> Unit) {
    val crossfade = CrossfadePref.seconds
    val policy = rememberParentalPolicy()
    val secTick by ParentSecurityManager.sessionTick.collectAsState()
    var query by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = query != null) { query = null }
    val ctx = LocalContext.current
    val unlocked = remember(secTick) { ParentSecurityManager.isAdminUnlocked(ctx) }
    val focus = remember { FocusRequester() }
    ImmersivePage(
        title = stringResource(R.string.settings_title), onBack = back,
        actions = {
            ImmersiveBarAction(
                Icons.Rounded.Search, stringResource(R.string.settings_search), onClick = { query = if (query == null) "" else null },
            )
        },
    ) {
        query?.let { q ->
            immersiveBlock("search") {
                ImmersiveSearchField(
                    query = q, onQueryChange = { query = it }, onSubmit = {}, focusRequester = focus,
                    placeholder = stringResource(R.string.settings_search_hint),
                )
                LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
            }
            if (q.isNotBlank()) {
                val allowedEntries = SettingsSearch.filterForPolicy(
                    SettingsSearch.immersive,
                    policy,
                )
                val hits = SettingsSearch.search(allowedEntries, q) { ctx.getString(it) }
                    .filter { entry -> entry.immersivePage != null && runCatching { SettingsPage.valueOf(entry.immersivePage) }.isSuccess }
                if (hits.isEmpty()) immersiveBlock("search:none") { Text(stringResource(R.string.settings_search_empty), style = Immersive.Subhead) }
                immersiveGroup("search:hits") {
                    hits.forEach { h ->
                        val pageName = h.immersivePage ?: return@forEach
                        row {
                            ImmersiveGroupRow(
                                stringResource(h.title), detail = if (h.detail != 0) stringResource(h.detail) else null, chevron = true,
                                onClick = {
                                    query = null
                                    val target = SettingsPage.valueOf(pageName)
                                    if (target == SettingsPage.Accounts) {
                                        ParentSecurityManager.runProtected(
                                            ctx,
                                            AppSurface.ACCOUNTS_AND_LOGOUT,
                                        ) { open(SettingsPage.Accounts) }
                                    } else open(target)
                                },
                            )
                        }
                    }
                }
                return@ImmersivePage
            }
        }
        immersiveGroup("parental") {
            row {
                ImmersiveGroupRow(
                    title = "Parental Controls",
                    detail = if (unlocked) "Parent Mode active · Tap to edit rules" else "Filters, Home layout, speakers & PIN",
                    detailColor = if (unlocked) Immersive.Accent else Immersive.TextSecondary,
                    icon = Icons.Rounded.Lock,
                    chevron = true,
                    onClick = {
                        ParentSecurityManager.runProtected(
                            ctx,
                            AppSurface.PARENT_CONTROLS,
                        ) { navController.navigate(Routes.ParentSettings.route) }
                    },
                )
            }
        }
        immersiveGroup("root") {
            row { ImmersiveGroupRow(stringResource(R.string.settings_section_display), icon = Icons.Rounded.Palette, chevron = true, onClick = { open(SettingsPage.Display) }) }
            row { ImmersiveGroupRow(stringResource(R.string.settings_section_playback), icon = Icons.Rounded.PlayArrow, chevron = true, onClick = { open(SettingsPage.Playback) }) }
            row {
                ImmersiveGroupRow(
                    stringResource(R.string.settings_transitions_title), icon = Icons.Rounded.GraphicEq, chevron = true,
                    value = PlaybackSettingsUi.transitionValue(crossfade), onClick = { open(SettingsPage.Crossfade) },
                )
            }
            row { ImmersiveGroupRow(stringResource(R.string.settings_section_library), icon = Icons.Rounded.LibraryMusic, chevron = true, onClick = { open(SettingsPage.Library) }) }
            if (policy.lyrics.enabled) {
                row { ImmersiveGroupRow(stringResource(R.string.settings_section_lyrics), icon = Icons.Rounded.Lyrics, chevron = true, onClick = { open(SettingsPage.Lyrics) }) }
            }
        }
        immersiveGroup("more") {
            row { ImmersiveGroupRow(stringResource(R.string.settings_section_data), icon = Icons.Rounded.SignalCellularAlt, chevron = true, onClick = { open(SettingsPage.Data) }) }
            row { ImmersiveGroupRow(stringResource(R.string.settings_section_storage), icon = Icons.Rounded.Storage, chevron = true, onClick = { open(SettingsPage.Storage) }) }
            row { ImmersiveGroupRow(stringResource(R.string.settings_section_app), icon = Icons.Rounded.Info, chevron = true, onClick = { open(SettingsPage.App) }) }
            row {
                ImmersiveGroupRow(
                    stringResource(R.string.accounts_title), detail = switchAccountDetail(),
                    icon = Icons.Rounded.Person, chevron = true,
                    onClick = {
                        ParentSecurityManager.runProtected(
                            ctx,
                            AppSurface.ACCOUNTS_AND_LOGOUT,
                        ) { open(SettingsPage.Accounts) }
                    },
                )
            }
        }
    }
}
