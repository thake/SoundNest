/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.core.ui.navigation


import io.github.thake.soundnest.feature.accounts.ui.AccountBrowser
import io.github.thake.soundnest.feature.home.whatsnew.ui.classic.route
import io.github.thake.soundnest.feature.home.whatsnew.ui.classic.title
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import io.github.thake.soundnest.R
import io.github.thake.soundnest.feature.accounts.session.ProfileCache
import io.github.thake.soundnest.feature.localfiles.settings.LocalFilesSettings
import io.github.thake.soundnest.feature.accounts.session.SessionActions
import io.github.thake.soundnest.host.android.SoundNestRuntime
import io.github.thake.soundnest.feature.parental.security.AppSurface
import io.github.thake.soundnest.feature.parental.filter.HomeSectionFilter
import io.github.thake.soundnest.feature.parental.security.ParentSecurityManager
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore
import io.github.thake.soundnest.feature.equalizer.settings.EqualizerSettings
import io.github.thake.soundnest.feature.radio.settings.RadioSettings
import io.github.thake.soundnest.spotify.AccountPlan
import io.github.thake.soundnest.feature.accounts.ui.ProfileAvatar
import io.github.thake.soundnest.feature.accounts.ui.switchAccountDetail
import io.github.thake.soundnest.core.ui.look.windowSize
import io.github.thake.soundnest.core.ui.immersive.Immersive
import io.github.thake.soundnest.core.ui.immersive.components.immersiveClickable
import io.github.thake.soundnest.feature.home.ui.immersive.AvatarDisc
import io.github.thake.soundnest.core.ui.immersive.shell.LocalBarControlSize
import io.github.thake.soundnest.core.ui.look.ImmersiveSans
import io.github.thake.soundnest.core.ui.classic.AppAccent
import io.github.thake.soundnest.core.ui.classic.AppMuted
import io.github.thake.soundnest.core.ui.classic.AppText
import io.github.thake.soundnest.core.ui.classic.tone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Top-level tab routes where left-edge swipe opens the 90% Material 3 navigation drawer. */
private val TopLevelMenuTabRoutes = setOf(
    Routes.Home.route,
    Routes.Search.route,
    Routes.Library.route,
)

private val MenuDrawerShape = RoundedCornerShape(
    topStart = 0.dp,
    bottomStart = 0.dp,
    topEnd = 28.dp,
    bottomEnd = 28.dp,
)

/**
 * Controller exposed via [LocalMenuDrawer] so any top-level screen's top-left avatar (or deep-link
 * handler) can open or close the left-sliding Material Design 3 menu drawer.
 */
class MenuDrawerController(
    val drawerState: DrawerState,
    private val scope: CoroutineScope,
) {
    val isOpen: Boolean
        get() = drawerState.isOpen || drawerState.targetValue == DrawerValue.Open

    fun open() {
        scope.launch { drawerState.open() }
    }

    fun close() {
        scope.launch { drawerState.close() }
    }
}

val LocalMenuDrawer = staticCompositionLocalOf<MenuDrawerController?> { null }

private data class MenuPalette(
    val immersive: Boolean,
    val sheetBg: Color,
    val cardBg: Color,
    val quickCardBg: Color,
    val text: Color,
    val muted: Color,
    val tertiary: Color,
    val accent: Color,
    val destructive: Color,
    val divider: Color,
    val border: Color?,
    val fontFamily: FontFamily?,
)

@Composable
private fun rememberMenuPalette(): MenuPalette {
    val immersive = isImmersive()
    return if (immersive) {
        MenuPalette(
            immersive = true,
            sheetBg = Color(0xFF111114),
            cardBg = Immersive.Raised,
            quickCardBg = Color(0xFF202026),
            text = Immersive.Text,
            muted = Immersive.TextSecondary,
            tertiary = Immersive.TextTertiary,
            accent = Immersive.Accent,
            destructive = Immersive.Destructive,
            divider = Immersive.Separator,
            border = Immersive.GlassEdge,
            fontFamily = ImmersiveSans,
        )
    } else {
        MenuPalette(
            immersive = false,
            sheetBg = tone(0xFF161616),
            cardBg = tone(0xFF202020),
            quickCardBg = tone(0xFF242424),
            text = AppText,
            muted = AppMuted,
            tertiary = AppMuted.copy(alpha = 0.72f),
            accent = AppAccent,
            destructive = Color(0xFFFF6B6B),
            divider = tone(0xFF303030),
            border = null,
            fontFamily = null,
        )
    }
}

/**
 * Shell-level Material Design 3 [ModalNavigationDrawer] that flies in from the left edge covering
 * 90% (`9/10ths`) of the screen width on phones—leaving `1/10th` (`10%`) of the previous screen
 * peeking behind a dimmed scrim on the right.
 */
@Composable
fun MenuNavigationDrawer(
    navController: NavController,
    drawerState: DrawerState = rememberDrawerState(initialValue = DrawerValue.Closed),
    gesturesEnabledOverride: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val controller = remember(drawerState, scope) { MenuDrawerController(drawerState, scope) }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val gesturesEnabled = gesturesEnabledOverride ?: (
        drawerState.isOpen ||
            (currentRoute in TopLevelMenuTabRoutes && !PlayerSheet.open.value)
        )

    BackHandler(enabled = drawerState.isOpen) {
        controller.close()
    }

    val drawerVisible = drawerState.isOpen || drawerState.targetValue == DrawerValue.Open

    CompositionLocalProvider(LocalMenuDrawer provides controller) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = gesturesEnabled,
            scrimColor = Color.Black.copy(alpha = 0.60f),
            drawerContent = {
                MenuDrawerSheet(
                    navController = navController,
                    onClose = { controller.close() },
                    modifier = if (!drawerVisible) Modifier.clearAndSetSemantics {} else Modifier,
                )
            },
            content = content,
        )
    }
}

/**
 * Material Design 3 `ModalDrawerSheet` sized to `0.90f` (`9/10ths`) width on phones (capped at
 * `420.dp` on large tablet/desktop windows), adapted to both Classic and Immersive looks.
 */
@Composable
fun MenuDrawerSheet(
    navController: NavController,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val palette = rememberMenuPalette()
    val isLargeWindow = windowSize().large

    LaunchedEffect(Unit) {
        ParentalPolicyStore.current(context)
        if (SoundNestRuntime.isStarted) {
            ProfileCache.ensure(context)
        }
    }

    val policy by ParentalPolicyStore.policyFlow.collectAsState()
    val sessionTick by ParentSecurityManager.sessionTick.collectAsState()
    val adminUnlocked = remember(sessionTick) { ParentSecurityManager.isAdminUnlocked(context) }
    val livePremium by AccountPlan.premium.collectAsState()

    val savedAccount = if (SoundNestRuntime.isStarted) {
        val accountList by SoundNestRuntime.host.accounts.list.collectAsState()
        accountList.current
    } else {
        null
    }

    val displayName = ProfileCache.name?.takeIf { it.isNotBlank() }
        ?: savedAccount?.name?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.spotify_account)
    val avatarUrl = ProfileCache.imageUrl?.takeIf { it.isNotBlank() } ?: savedAccount?.imageUrl
    val accountSubtitle = ProfileCache.id?.takeIf { it.isNotBlank() && !it.equals(displayName, ignoreCase = true) }
        ?: stringResource(R.string.spotify_account)
    val isPremium = livePremium ?: savedAccount?.premium
    val switchDetail = if (SoundNestRuntime.isStarted) {
        switchAccountDetail()
    } else {
        stringResource(R.string.accounts_switch_row_detail)
    }

    val openRoute: (String) -> Unit = { route ->
        onClose()
        navController.navigateSingleTop(route)
    }

    val widthModifier = if (isLargeWindow) {
        Modifier.fillMaxHeight().fillMaxWidth(0.90f).widthIn(max = 420.dp)
    } else {
        Modifier.fillMaxHeight().fillMaxWidth(0.90f)
    }

    Surface(
        shape = MenuDrawerShape,
        color = palette.sheetBg,
        contentColor = palette.text,
        tonalElevation = 0.dp,
        modifier = modifier
            .then(widthModifier)
            .then(
                if (palette.border != null) Modifier.border(1.dp, palette.border, MenuDrawerShape)
                else Modifier
            )
            .testTag("menu_drawer_sheet"),
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .testTag("menu_drawer_list"),
        ) {
            // 1. Drawer Top Header
            item(key = "menu:header") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, top = 4.dp, bottom = 2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.menu_title),
                        color = palette.text,
                        fontFamily = palette.fontFamily,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.accessibility_navigate_back),
                            tint = palette.muted,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }

            // 2. Elevated Account Summary Card + Inline Parent Mode Quick-Lock
            item(key = "menu:account-card") {
                MenuAccountSummaryCard(
                    palette = palette,
                    displayName = displayName,
                    subtitle = accountSubtitle,
                    avatarUrl = avatarUrl,
                    isPremium = isPremium,
                    adminUnlocked = adminUnlocked,
                    onLockNow = { ParentSecurityManager.lockNow(context) },
                )
            }

            // 3. 2-Column Quick Access Tonal Cards (Recents & Your Recap)
            item(key = "menu:quick-access") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MenuSectionLabel(stringResource(R.string.menu_section_quick_access), palette)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        MenuQuickAccessCard(
                            icon = Icons.Rounded.History,
                            title = stringResource(R.string.recents_title),
                            subtitle = stringResource(R.string.menu_recents_subtitle),
                            palette = palette,
                            modifier = Modifier.weight(1f),
                            onClick = { openRoute(Routes.Recent.route) },
                        )
                        MenuQuickAccessCard(
                            icon = Icons.Rounded.BarChart,
                            title = stringResource(R.string.recap_title),
                            subtitle = stringResource(R.string.menu_recap_subtitle),
                            palette = palette,
                            modifier = Modifier.weight(1f),
                            onClick = { openRoute(Routes.Recap.route) },
                        )
                    }
                }
            }

            // 4. Explore & Audio Section
            val showWhatsNew = HomeSectionFilter.isWhatsNewAllowed(policy)
            val showEqualizer = EqualizerSettings.isAvailable(policy)
            val showLocalFiles = policy.localFiles.enabled && LocalFilesSettings.shown
            val showLiveRadio = policy.radio.enabled && RadioSettings.on
            if (showWhatsNew || showEqualizer || showLocalFiles || showLiveRadio) {
                item(key = "menu:explore-audio") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MenuSectionLabel(stringResource(R.string.menu_section_explore), palette)
                        MenuGroupCard(palette = palette) {
                            var hasPrevious = false
                            if (showWhatsNew) {
                                MenuGroupRow(
                                    icon = Icons.Rounded.NotificationsNone,
                                    title = stringResource(R.string.whats_new_title),
                                    detail = stringResource(R.string.whats_new_subtitle),
                                    palette = palette,
                                    onClick = { openRoute(Routes.WhatsNew.route) },
                                )
                                hasPrevious = true
                            }
                            if (showEqualizer) {
                                if (hasPrevious) MenuRowDivider(palette)
                                MenuGroupRow(
                                    icon = Icons.Rounded.GraphicEq,
                                    title = stringResource(R.string.equalizer_title),
                                    detail = stringResource(R.string.equalizer_settings_detail_soundnest),
                                    palette = palette,
                                    onClick = { openRoute(Routes.Equalizer.route) },
                                )
                                hasPrevious = true
                            }
                            if (showLocalFiles) {
                                if (hasPrevious) MenuRowDivider(palette)
                                MenuGroupRow(
                                    icon = Icons.Rounded.Folder,
                                    title = stringResource(R.string.local_files_title),
                                    detail = stringResource(R.string.local_playlist_subtitle),
                                    palette = palette,
                                    onClick = { openRoute(Routes.LocalFiles.route) },
                                )
                                hasPrevious = true
                            }
                            if (showLiveRadio) {
                                if (hasPrevious) MenuRowDivider(palette)
                                MenuGroupRow(
                                    icon = Icons.Rounded.Radio,
                                    title = stringResource(R.string.live_radio_title),
                                    detail = null,
                                    palette = palette,
                                    onClick = { openRoute(Routes.LiveRadio.route) },
                                )
                            }
                        }
                    }
                }
            }

            // 5. App & Preferences Section
            item(key = "menu:app-preferences") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MenuSectionLabel(stringResource(R.string.menu_section_app), palette)
                    MenuGroupCard(palette = palette) {
                        MenuGroupRow(
                            icon = Icons.Rounded.Settings,
                            title = stringResource(R.string.settings_title),
                            detail = stringResource(R.string.settings_detail),
                            palette = palette,
                            onClick = { openRoute(Routes.Settings.route) },
                        )
                        MenuRowDivider(palette)
                        MenuGroupRow(
                            icon = Icons.Rounded.Info,
                            title = stringResource(R.string.menu_about_title),
                            detail = stringResource(R.string.about_detail),
                            palette = palette,
                            onClick = { openRoute(Routes.About.route) },
                        )
                    }
                }
            }

            // 6. Parent & Account Section (PIN-Protected)
            item(key = "menu:parent-account") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MenuSectionLabel(stringResource(R.string.menu_section_parent_account), palette)
                    MenuGroupCard(palette = palette) {
                        MenuGroupRow(
                            icon = Icons.Rounded.Security,
                            title = "Parental Controls",
                            detail = "Content filters, Home sections, features, speakers & backup",
                            palette = palette,
                            locked = !adminUnlocked,
                            emphasizeIcon = true,
                            onClick = {
                                ParentSecurityManager.runProtected(context.applicationContext, AppSurface.PARENT_CONTROLS) {
                                    openRoute(Routes.ParentSettings.route)
                                }
                            },
                        )
                        MenuRowDivider(palette)
                        MenuGroupRow(
                            icon = Icons.Rounded.VisibilityOff,
                            title = stringResource(R.string.hidden_songs_title),
                            detail = stringResource(R.string.hidden_songs_detail),
                            palette = palette,
                            locked = !adminUnlocked,
                            onClick = {
                                ParentSecurityManager.runProtected(context.applicationContext, AppSurface.HIDDEN_SONGS_AND_BANS) {
                                    openRoute(Routes.HiddenSongs.route)
                                }
                            },
                        )
                        MenuRowDivider(palette)
                        MenuGroupRow(
                            icon = Icons.Rounded.Group,
                            title = stringResource(R.string.accounts_switch_row),
                            detail = switchDetail,
                            palette = palette,
                            locked = !adminUnlocked,
                            onClick = {
                                ParentSecurityManager.runProtected(context.applicationContext, AppSurface.ACCOUNTS_AND_LOGOUT) {
                                    openRoute(Routes.Accounts.route)
                                }
                            },
                        )
                        MenuRowDivider(palette)
                        MenuGroupRow(
                            icon = Icons.Rounded.ManageAccounts,
                            title = stringResource(R.string.account_page_title),
                            detail = stringResource(R.string.account_page_detail),
                            palette = palette,
                            locked = !adminUnlocked,
                            trailingIcon = Icons.AutoMirrored.Rounded.OpenInNew,
                            onClick = {
                                ParentSecurityManager.runProtected(context.applicationContext, AppSurface.ACCOUNTS_AND_LOGOUT) {
                                    onClose()
                                    AccountBrowser.open(context, AccountBrowser.ACCOUNT_URL)
                                }
                            },
                        )
                        MenuRowDivider(palette)
                        MenuGroupRow(
                            icon = Icons.AutoMirrored.Rounded.Logout,
                            title = stringResource(R.string.connect_device_menu_logout),
                            detail = null,
                            palette = palette,
                            locked = !adminUnlocked,
                            destructive = true,
                            showTrailingChevron = false,
                            onClick = {
                                ParentSecurityManager.runProtected(context.applicationContext, AppSurface.ACCOUNTS_AND_LOGOUT) {
                                    onClose()
                                    SessionActions.logout(context.applicationContext)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuAccountSummaryCard(
    palette: MenuPalette,
    displayName: String,
    subtitle: String,
    avatarUrl: String?,
    isPremium: Boolean?,
    adminUnlocked: Boolean,
    onLockNow: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Surface(
        shape = shape,
        color = palette.cardBg,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (palette.border != null) Modifier.border(1.dp, palette.border, shape) else Modifier),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                if (palette.immersive) {
                    AvatarDisc(
                        url = avatarUrl,
                        initial = displayName.trim().firstOrNull()?.uppercase() ?: "•",
                        contentDescription = null,
                        onClick = null,
                        size = 52.dp,
                    )
                } else {
                    ProfileAvatar(
                        url = avatarUrl,
                        name = displayName,
                        size = 52.dp,
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 14.dp),
                ) {
                    Text(
                        text = displayName,
                        color = palette.text,
                        fontFamily = palette.fontFamily,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            color = palette.muted,
                            fontFamily = palette.fontFamily,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (isPremium != null) {
                    val planText = stringResource(
                        if (isPremium) R.string.accounts_plan_premium else R.string.accounts_plan_free,
                    )
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(palette.accent.copy(alpha = 0.16f))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Text(
                            text = planText,
                            color = palette.accent,
                            fontFamily = palette.fontFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            if (adminUnlocked) {
                HorizontalDivider(color = palette.divider)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.accent.copy(alpha = 0.08f))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LockOpen,
                        contentDescription = null,
                        tint = palette.accent,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.menu_parent_mode_active),
                        color = palette.accent,
                        fontFamily = palette.fontFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp),
                    )
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(palette.accent.copy(alpha = 0.20f))
                            .clickable(role = Role.Button, onClick = onLockNow)
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.menu_lock_now),
                            color = palette.accent,
                            fontFamily = palette.fontFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuSectionLabel(title: String, palette: MenuPalette) {
    Text(
        text = title,
        color = palette.muted,
        fontFamily = palette.fontFamily,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 6.dp),
    )
}

@Composable
private fun MenuQuickAccessCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    palette: MenuPalette,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Surface(
        shape = shape,
        color = palette.quickCardBg,
        modifier = modifier
            .clip(shape)
            .then(if (palette.border != null) Modifier.border(1.dp, palette.border, shape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.accent.copy(alpha = 0.16f)),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = title,
                color = palette.text,
                fontFamily = palette.fontFamily,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                color = palette.muted,
                fontFamily = palette.fontFamily,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MenuGroupCard(
    palette: MenuPalette,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Surface(
        shape = shape,
        color = palette.cardBg,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (palette.border != null) Modifier.border(1.dp, palette.border, shape) else Modifier),
    ) {
        Column(Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
private fun MenuRowDivider(palette: MenuPalette) {
    HorizontalDivider(
        color = palette.divider,
        modifier = Modifier.padding(start = 66.dp, end = 16.dp),
    )
}

@Composable
private fun MenuGroupRow(
    icon: ImageVector,
    title: String,
    detail: String?,
    palette: MenuPalette,
    locked: Boolean = false,
    emphasizeIcon: Boolean = false,
    destructive: Boolean = false,
    trailingIcon: ImageVector? = null,
    showTrailingChevron: Boolean = true,
    onClick: () -> Unit,
) {
    val iconTint = when {
        destructive -> palette.destructive
        emphasizeIcon -> palette.accent
        else -> palette.accent
    }
    val iconBg = when {
        destructive -> palette.destructive.copy(alpha = 0.14f)
        else -> palette.accent.copy(alpha = 0.14f)
    }
    val titleColor = if (destructive) palette.destructive else palette.text

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = if (detail == null) 13.dp else 11.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconBg),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp, end = 8.dp),
        ) {
            Text(
                text = title,
                color = titleColor,
                fontFamily = palette.fontFamily,
                fontSize = 15.sp,
                fontWeight = if (emphasizeIcon || destructive) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!detail.isNullOrBlank()) {
                Text(
                    text = detail,
                    color = palette.muted,
                    fontFamily = palette.fontFamily,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (locked) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = "Protected by Parent PIN",
                tint = palette.tertiary,
                modifier = Modifier.size(16.dp),
            )
            if (trailingIcon != null || showTrailingChevron) {
                Spacer(Modifier.width(6.dp))
            }
        }
        val endIcon = trailingIcon ?: if (showTrailingChevron) Icons.AutoMirrored.Rounded.KeyboardArrowRight else null
        if (endIcon != null) {
            Icon(
                imageVector = endIcon,
                contentDescription = null,
                tint = palette.tertiary,
                modifier = Modifier.size(if (trailingIcon != null) 18.dp else 20.dp),
            )
        }
    }
}

/**
 * Consistent top-left circular avatar button for Classic top bars (`Home`, `Search`, `Your Library`)
 * that opens [LocalMenuDrawer].
 */
@Composable
fun ClassicMenuAvatarButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val menuDrawer = LocalMenuDrawer.current
    LaunchedEffect(Unit) {
        if (SoundNestRuntime.isStarted) {
            ProfileCache.ensure(context)
        }
    }
    val avatarUrl = ProfileCache.imageUrl
    val name = ProfileCache.name
    val contentDesc = stringResource(R.string.menu_open_content_description)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(CircleShape)
            .semantics {
                contentDescription = contentDesc
            }
            .clickable(
                role = Role.Button,
                onClickLabel = contentDesc,
                onClick = { menuDrawer?.open() },
            )
            .testTag("top_left_menu_avatar"),
    ) {
        ProfileAvatar(
            url = avatarUrl,
            name = name,
            size = 34.dp,
        )
    }
}

/**
 * Consistent top-left circular avatar button for Immersive headers (`Home`, `Search`, `Your Library`)
 * that opens [LocalMenuDrawer] and scales smoothly with [LocalBarControlSize].
 */
@Composable
fun ImmersiveMenuAvatarButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val menuDrawer = LocalMenuDrawer.current
    val controlSize = LocalBarControlSize.current
    LaunchedEffect(Unit) {
        if (SoundNestRuntime.isStarted) {
            ProfileCache.ensure(context)
        }
    }
    val avatarUrl = ProfileCache.imageUrl
    val initial = ProfileCache.name?.trim()?.firstOrNull()?.uppercase() ?: "•"
    val contentDesc = stringResource(R.string.menu_open_content_description)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(controlSize)
            .semantics {
                contentDescription = contentDesc
            }
            .immersiveClickable(onClick = { menuDrawer?.open() })
            .testTag("top_left_menu_avatar"),
    ) {
        AvatarDisc(
            url = avatarUrl,
            initial = initial,
            contentDescription = null,
            onClick = null,
            size = (controlSize * 0.82f).coerceIn(30.dp, 36.dp),
        )
    }
}
