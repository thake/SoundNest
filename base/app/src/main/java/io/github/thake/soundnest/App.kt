package io.github.thake.soundnest

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.thake.soundnest.core.ui.classic.MainBottomNavigation
import io.github.thake.soundnest.core.ui.navigation.MyNavHost
import io.github.thake.soundnest.core.ui.navigation.navigateSingleTop
import kotlinx.coroutines.flow.first

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun App() {
    val navController = rememberNavController()
    val menuDrawerState = androidx.compose.material3.rememberDrawerState(androidx.compose.material3.DrawerValue.Closed)

    // Debug builds only: `myspoti-cli nav <route>` (see CliReceiver).
    LaunchedEffect(Unit) {
        io.github.thake.soundnest.feature.cli.CliReceiver.nav.collect { r ->
            if (menuDrawerState.isOpen) runCatching { menuDrawerState.close() }
            runCatching { navController.navigate(r) }
        }
    }

    // The playlists at the top of the library, fetched ahead on Wi-Fi (see PagePrefetch).
    LaunchedEffect(Unit) { io.github.thake.soundnest.core.catalog.PagePrefetch.start() }

    // A collaborative-playlist invitation was opened (see InviteLinks): its playlist page offers "Join".
    val invite by io.github.thake.soundnest.core.catalog.InviteLinks.pending.collectAsState()
    LaunchedEffect(invite) {
        val i = invite?.takeIf { !it.opened } ?: return@LaunchedEffect
        if (!io.github.thake.soundnest.feature.parental.settings.FeatureGates.canModifySpotifyLibrary()) {
            io.github.thake.soundnest.core.catalog.InviteLinks.pending.value = null
            return@LaunchedEffect
        }
        io.github.thake.soundnest.host.android.SoundNestRuntime.api.ready.first { it }
        i.opened = true
        if (menuDrawerState.isOpen) runCatching { menuDrawerState.close() }
        navController.navigateSingleTop(io.github.thake.soundnest.core.ui.navigation.playlistRoute(i.playlistId))
    }

    // Genius's explanations of the song playing, read while it starts: ready when the lyrics open.
    val geniusLang = io.github.thake.soundnest.feature.lyrics.genius.ui.classic.readerLanguage()
    val playingUri = io.github.thake.soundnest.core.playback.SongPlayer.nowPlaying.value.uri
    LaunchedEffect(playingUri) {
        if (!playingUri.startsWith("spotify:track:")) return@LaunchedEffect
        if (!io.github.thake.soundnest.feature.parental.settings.FeatureGates.canUseGeniusMeanings()) return@LaunchedEffect
        kotlinx.coroutines.delay(2_000)
        if (!io.github.thake.soundnest.feature.parental.settings.FeatureGates.canUseGeniusMeanings()) return@LaunchedEffect
        val st = io.github.thake.soundnest.core.di.Graph.currentSongState
        io.github.thake.soundnest.feature.lyrics.genius.data.GeniusMeaning.prefetch(playingUri.substringAfterLast(':'), st.title.value, st.singer.value, geniusLang)
    }

    // A shared link to a list / artist / podcast: its page, over whatever was open.
    val pageLink by io.github.thake.soundnest.core.catalog.InviteLinks.page.collectAsState()
    LaunchedEffect(pageLink) {
        val uri = pageLink ?: return@LaunchedEffect
        io.github.thake.soundnest.core.catalog.InviteLinks.page.value = null
        val decision = io.github.thake.soundnest.feature.parental.filter.SpotifyLinkGuard.evaluate(uri, io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore.current())
        if (decision !is io.github.thake.soundnest.feature.parental.filter.LinkDecision.OpenInAppPage) return@LaunchedEffect
        val id = decision.spotifyUri.substringAfterLast(':')
        io.github.thake.soundnest.core.ui.navigation.PlayerSheet.close()
        if (menuDrawerState.isOpen) runCatching { menuDrawerState.close() }
        runCatching {
            navController.navigateSingleTop(when (decision.spotifyUri.split(':')[1]) {
                "playlist" -> io.github.thake.soundnest.core.ui.navigation.playlistRoute(id)
                "album" -> io.github.thake.soundnest.core.ui.navigation.albumRoute("", "", id)
                "artist" -> io.github.thake.soundnest.core.ui.navigation.artistRoute("artist", id)
                else -> io.github.thake.soundnest.core.ui.navigation.showRoute(id)
            })
        }
    }
    val shareTarget by io.github.thake.soundnest.feature.player.ui.SpotifyCode.request.collectAsState()
    shareTarget?.let { t -> io.github.thake.soundnest.feature.player.ui.SpotifyCodeSheet(t) { io.github.thake.soundnest.feature.player.ui.SpotifyCode.request.value = null } }

    val immersive = io.github.thake.soundnest.core.ui.navigation.isImmersive()
    val haze = io.github.thake.soundnest.core.ui.immersive.shell.rememberImmersiveHazeState()
    val glassPage = io.github.thake.soundnest.core.ui.immersive.shell.rememberImmersiveBackdrop()
    val liquidGlass = immersive && io.github.thake.soundnest.core.ui.immersive.shell.rememberLiquidGlassActive()
    val frostPage = immersive && !liquidGlass && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
    val chrome = io.github.thake.soundnest.core.ui.immersive.shell.rememberImmersiveChromeState()
    androidx.compose.runtime.CompositionLocalProvider(
        io.github.thake.soundnest.core.ui.look.LocalReduceMotion provides io.github.thake.soundnest.core.ui.look.rememberReduceMotion(),
        io.github.thake.soundnest.core.ui.immersive.shell.LocalImmersiveHaze provides if (immersive) haze else null,
        io.github.thake.soundnest.core.ui.immersive.shell.LocalImmersiveBackdrop provides if (liquidGlass) glassPage else null,
        io.github.thake.soundnest.core.ui.immersive.shell.LocalImmersiveFrostPage provides if (frostPage) glassPage else null,
        io.github.thake.soundnest.core.ui.immersive.shell.LocalImmersiveChrome provides if (immersive) chrome else null,
    ) {
        val rail = io.github.thake.soundnest.core.ui.look.windowSize().navigationRail
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        val ctx = androidx.compose.ui.platform.LocalContext.current
        val sessionTick by io.github.thake.soundnest.feature.parental.security.ParentSecurityManager.sessionTick.collectAsState()
        val adminUnlocked = androidx.compose.runtime.remember(sessionTick) {
            io.github.thake.soundnest.feature.parental.security.ParentSecurityManager.adminRemainingMs(ctx) > 0L
        }
        val showParentBanner = currentRoute != io.github.thake.soundnest.core.ui.navigation.Routes.ParentSettings.route && adminUnlocked
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
            io.github.thake.soundnest.core.ui.navigation.MenuNavigationDrawer(
                navController = navController,
                drawerState = menuDrawerState,
            ) {
                androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
                    if (showParentBanner) {
                        io.github.thake.soundnest.feature.parental.ui.ParentAdminSessionBanner(
                            onOpenParentSettings = {
                                navController.navigateSingleTop(io.github.thake.soundnest.core.ui.navigation.Routes.ParentSettings.route)
                            },
                        )
                    }
                    CompositionLocalProvider(
                        io.github.thake.soundnest.core.ui.look.LocalTopStatusBarConsumed provides showParentBanner,
                    ) {
                        androidx.compose.foundation.layout.Box(
                            Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .then(
                                    if (showParentBanner) {
                                        Modifier.consumeWindowInsets(WindowInsets.statusBars)
                                    } else {
                                        Modifier
                                    },
                                ),
                        ) {
                            androidx.compose.foundation.layout.Row(Modifier.fillMaxSize()) {
                                if (rail && !immersive) io.github.thake.soundnest.core.ui.classic.MainNavigationRail(navController)
                                Scaffold(
                                    modifier = Modifier.weight(1f),
                                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                                    bottomBar = {
                                        if (!immersive) MainBottomNavigation(navController)
                                    },
                                ) {
                                    MyNavHost(
                                        navHostController = navController,
                                        modifier = if (immersive) Modifier.background(io.github.thake.soundnest.core.ui.immersive.Immersive.Page) else Modifier,
                                    )
                                }
                            }
                            if (immersive) ImmersiveShell(navController)
                            else io.github.thake.soundnest.core.ui.navigation.PlayerOverlay(navController)
                        }
                    }
                }
            }
            io.github.thake.soundnest.feature.library.hiddensongs.ui.HiddenSongUndo()
            io.github.thake.soundnest.feature.parental.ui.ParentPinPromptHost()
        }
    }
}

@Composable
private fun ImmersiveShell(navController: androidx.navigation.NavHostController) {
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        io.github.thake.soundnest.core.ui.immersive.shell.ImmersiveBottomChrome(
            navController,
            miniPlayer = { m -> io.github.thake.soundnest.feature.player.ui.immersive.ImmersiveMiniPlayer(m) },
            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter),
        )
        io.github.thake.soundnest.feature.player.ui.immersive.ImmersivePlayerOverlay(navController)
    }
}
