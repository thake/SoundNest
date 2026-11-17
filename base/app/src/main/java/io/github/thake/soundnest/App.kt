package io.github.thake.soundnest

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.thake.soundnest.ui.navigation.MainBottomNavigation
import io.github.thake.soundnest.ui.navigation.MyNavHost
import io.github.thake.soundnest.ui.navigation.navigateSingleTop
import kotlinx.coroutines.flow.first

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun App() {
    val navController = rememberNavController()
    val menuDrawerState = androidx.compose.material3.rememberDrawerState(androidx.compose.material3.DrawerValue.Closed)

    // Debug builds only: `myspoti-cli nav <route>` (see CliReceiver).
    LaunchedEffect(Unit) {
        io.github.thake.soundnest.cli.CliReceiver.nav.collect { r ->
            if (menuDrawerState.isOpen) runCatching { menuDrawerState.close() }
            runCatching { navController.navigate(r) }
        }
    }

    // The playlists at the top of the library, fetched ahead on Wi-Fi (see PagePrefetch).
    LaunchedEffect(Unit) { io.github.thake.soundnest.data.api.PagePrefetch.start() }

    // A collaborative-playlist invitation was opened (see InviteLinks): its playlist page offers "Join".
    val invite by io.github.thake.soundnest.data.api.InviteLinks.pending.collectAsState()
    LaunchedEffect(invite) {
        val i = invite?.takeIf { !it.opened } ?: return@LaunchedEffect
        if (!io.github.thake.soundnest.parental.FeatureGates.canModifySpotifyLibrary()) {
            io.github.thake.soundnest.data.api.InviteLinks.pending.value = null
            return@LaunchedEffect
        }
        io.github.thake.soundnest.host.android.SoundNestRuntime.api.ready.first { it }
        i.opened = true
        if (menuDrawerState.isOpen) runCatching { menuDrawerState.close() }
        navController.navigateSingleTop(io.github.thake.soundnest.ui.navigation.playlistRoute(i.playlistId))
    }

    // Genius's explanations of the song playing, read while it starts: ready when the lyrics open.
    val geniusLang = io.github.thake.soundnest.ui.components.readerLanguage()
    val playingUri = io.github.thake.soundnest.di.SongPlayer.nowPlaying.value.uri
    LaunchedEffect(playingUri) {
        if (!playingUri.startsWith("spotify:track:")) return@LaunchedEffect
        if (!io.github.thake.soundnest.parental.FeatureGates.canUseGeniusMeanings()) return@LaunchedEffect
        kotlinx.coroutines.delay(2_000)
        if (!io.github.thake.soundnest.parental.FeatureGates.canUseGeniusMeanings()) return@LaunchedEffect
        val st = io.github.thake.soundnest.di.Graph.currentSongState
        io.github.thake.soundnest.data.api.GeniusMeaning.prefetch(playingUri.substringAfterLast(':'), st.title.value, st.singer.value, geniusLang)
    }

    // A shared link to a list / artist / podcast: its page, over whatever was open.
    val pageLink by io.github.thake.soundnest.data.api.InviteLinks.page.collectAsState()
    LaunchedEffect(pageLink) {
        val uri = pageLink ?: return@LaunchedEffect
        io.github.thake.soundnest.data.api.InviteLinks.page.value = null
        val decision = io.github.thake.soundnest.parental.SpotifyLinkGuard.evaluate(uri, io.github.thake.soundnest.parental.ParentalPolicyStore.current())
        if (decision !is io.github.thake.soundnest.parental.LinkDecision.OpenInAppPage) return@LaunchedEffect
        val id = decision.spotifyUri.substringAfterLast(':')
        io.github.thake.soundnest.ui.navigation.PlayerSheet.close()
        if (menuDrawerState.isOpen) runCatching { menuDrawerState.close() }
        runCatching {
            navController.navigateSingleTop(when (decision.spotifyUri.split(':')[1]) {
                "playlist" -> io.github.thake.soundnest.ui.navigation.playlistRoute(id)
                "album" -> io.github.thake.soundnest.ui.navigation.albumRoute("", "", id)
                "artist" -> io.github.thake.soundnest.ui.navigation.artistRoute("artist", id)
                else -> io.github.thake.soundnest.ui.navigation.showRoute(id)
            })
        }
    }
    val shareTarget by io.github.thake.soundnest.ui.components.SpotifyCode.request.collectAsState()
    shareTarget?.let { t -> io.github.thake.soundnest.ui.components.SpotifyCodeSheet(t) { io.github.thake.soundnest.ui.components.SpotifyCode.request.value = null } }

    val immersive = io.github.thake.soundnest.ui.navigation.isImmersive()
    val haze = io.github.thake.soundnest.ui.immersive.shell.rememberImmersiveHazeState()
    val glassPage = io.github.thake.soundnest.ui.immersive.shell.rememberImmersiveBackdrop()
    val liquidGlass = immersive && io.github.thake.soundnest.ui.immersive.shell.rememberLiquidGlassActive()
    val frostPage = immersive && !liquidGlass && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
    val chrome = io.github.thake.soundnest.ui.immersive.shell.rememberImmersiveChromeState()
    androidx.compose.runtime.CompositionLocalProvider(
        io.github.thake.soundnest.ui.look.LocalReduceMotion provides io.github.thake.soundnest.ui.look.rememberReduceMotion(),
        io.github.thake.soundnest.ui.immersive.shell.LocalImmersiveHaze provides if (immersive) haze else null,
        io.github.thake.soundnest.ui.immersive.shell.LocalImmersiveBackdrop provides if (liquidGlass) glassPage else null,
        io.github.thake.soundnest.ui.immersive.shell.LocalImmersiveFrostPage provides if (frostPage) glassPage else null,
        io.github.thake.soundnest.ui.immersive.shell.LocalImmersiveChrome provides if (immersive) chrome else null,
    ) {
        val rail = io.github.thake.soundnest.ui.components.windowSize().navigationRail
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
            io.github.thake.soundnest.ui.screens.MenuNavigationDrawer(
                navController = navController,
                drawerState = menuDrawerState,
            ) {
                androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
                    if (currentRoute != io.github.thake.soundnest.ui.navigation.Routes.ParentSettings.route) {
                        io.github.thake.soundnest.parental.ParentAdminSessionBanner(
                            onOpenParentSettings = {
                                navController.navigateSingleTop(io.github.thake.soundnest.ui.navigation.Routes.ParentSettings.route)
                            },
                        )
                    }
                    androidx.compose.foundation.layout.Box(Modifier.weight(1f).fillMaxSize()) {
                        androidx.compose.foundation.layout.Row(Modifier.fillMaxSize()) {
                            if (rail && !immersive) io.github.thake.soundnest.ui.navigation.MainNavigationRail(navController)
                            Scaffold(
                                modifier = Modifier.weight(1f),
                                bottomBar = {
                                    if (!immersive) MainBottomNavigation(navController)
                                }
                            ) {
                                MyNavHost(
                                    navHostController = navController,
                                    modifier = if (immersive) Modifier.background(io.github.thake.soundnest.ui.immersive.Immersive.Page) else Modifier,
                                )
                            }
                        }
                        if (immersive) ImmersiveShell(navController)
                        else io.github.thake.soundnest.ui.navigation.PlayerOverlay(navController)
                    }
                }
            }
            io.github.thake.soundnest.ui.components.HiddenSongUndo()
            io.github.thake.soundnest.parental.ParentPinPromptHost()
        }
    }
}

@Composable
private fun ImmersiveShell(navController: androidx.navigation.NavHostController) {
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        io.github.thake.soundnest.ui.immersive.shell.ImmersiveBottomChrome(
            navController,
            miniPlayer = { m -> io.github.thake.soundnest.ui.immersive.player.ImmersiveMiniPlayer(m) },
            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter),
        )
        io.github.thake.soundnest.ui.immersive.player.ImmersivePlayerOverlay(navController)
    }
}
