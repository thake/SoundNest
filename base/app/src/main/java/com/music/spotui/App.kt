package com.music.spotui

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.music.spotui.ui.navigation.MainBottomNavigation
import com.music.spotui.ui.navigation.MyNavHost
import com.music.spotui.ui.navigation.navigateSingleTop
import kotlinx.coroutines.flow.first

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun App() {
    val navController = rememberNavController()

    // Debug builds only: `myspoti-cli nav <route>` (see CliReceiver).
    LaunchedEffect(Unit) { com.music.spotui.cli.CliReceiver.nav.collect { r -> runCatching { navController.navigate(r) } } }

    // The playlists at the top of the library, fetched ahead on Wi-Fi (see PagePrefetch).
    LaunchedEffect(Unit) { com.music.spotui.data.api.PagePrefetch.start() }

    // A collaborative-playlist invitation was opened (see InviteLinks): its playlist page offers "Join".
    val invite by com.music.spotui.data.api.InviteLinks.pending.collectAsState()
    LaunchedEffect(invite) {
        val i = invite?.takeIf { !it.opened } ?: return@LaunchedEffect
        app.myspoti.host.android.MyspotiRuntime.api.ready.first { it }
        i.opened = true
        navController.navigateSingleTop(com.music.spotui.ui.navigation.playlistRoute(i.playlistId))
    }

    // Genius's explanations of the song playing, read while it starts: ready when the lyrics open.
    val geniusLang = com.music.spotui.ui.components.readerLanguage()
    val playingUri = com.music.spotui.di.SongPlayer.nowPlaying.value.uri
    LaunchedEffect(playingUri) {
        if (!playingUri.startsWith("spotify:track:")) return@LaunchedEffect
        kotlinx.coroutines.delay(2_000)
        val st = com.music.spotui.di.Graph.currentSongState
        com.music.spotui.data.api.GeniusMeaning.prefetch(playingUri.substringAfterLast(':'), st.title.value, st.singer.value, geniusLang)
    }

    // A shared link to a list / artist / podcast: its page, over whatever was open.
    val pageLink by com.music.spotui.data.api.InviteLinks.page.collectAsState()
    LaunchedEffect(pageLink) {
        val uri = pageLink ?: return@LaunchedEffect
        com.music.spotui.data.api.InviteLinks.page.value = null
        val id = uri.substringAfterLast(':')
        com.music.spotui.ui.navigation.PlayerSheet.close()
        runCatching {
            navController.navigateSingleTop(when (uri.split(':')[1]) {
                "playlist" -> com.music.spotui.ui.navigation.playlistRoute(id)
                "album" -> com.music.spotui.ui.navigation.albumRoute("", "", id)
                "artist" -> com.music.spotui.ui.navigation.artistRoute("artist", id)
                else -> com.music.spotui.ui.navigation.showRoute(id)
            })
        }
    }
    val shareTarget by com.music.spotui.ui.components.SpotifyCode.request.collectAsState()
    shareTarget?.let { t -> com.music.spotui.ui.components.SpotifyCodeSheet(t) { com.music.spotui.ui.components.SpotifyCode.request.value = null } }

    val immersive = com.music.spotui.ui.navigation.isImmersive()
    val haze = com.music.spotui.ui.immersive.shell.rememberImmersiveHazeState()
    val glassPage = com.music.spotui.ui.immersive.shell.rememberImmersiveBackdrop()
    val liquidGlass = immersive && com.music.spotui.ui.immersive.shell.rememberLiquidGlassActive()
    val frostPage = immersive && !liquidGlass && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
    val chrome = com.music.spotui.ui.immersive.shell.rememberImmersiveChromeState()
    androidx.compose.runtime.CompositionLocalProvider(
        com.music.spotui.ui.look.LocalReduceMotion provides com.music.spotui.ui.look.rememberReduceMotion(),
        com.music.spotui.ui.immersive.shell.LocalImmersiveHaze provides if (immersive) haze else null,
        com.music.spotui.ui.immersive.shell.LocalImmersiveBackdrop provides if (liquidGlass) glassPage else null,
        com.music.spotui.ui.immersive.shell.LocalImmersiveFrostPage provides if (frostPage) glassPage else null,
        com.music.spotui.ui.immersive.shell.LocalImmersiveChrome provides if (immersive) chrome else null,
    ) {
        val rail = com.music.spotui.ui.components.windowSize().navigationRail
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
            androidx.compose.foundation.layout.Row(Modifier.fillMaxSize()) {
                if (rail && !immersive) com.music.spotui.ui.navigation.MainNavigationRail(navController)
                Scaffold(
                    modifier = Modifier.weight(1f),
                    bottomBar = {
                        if (!immersive) MainBottomNavigation(navController)
                    }
                ) {
                    MyNavHost(
                        navHostController = navController,
                        modifier = if (immersive) Modifier.background(com.music.spotui.ui.immersive.Immersive.Page) else Modifier,
                    )
                }
            }
            if (immersive) ImmersiveShell(navController)
            else com.music.spotui.ui.navigation.PlayerOverlay(navController)
            com.music.spotui.ui.components.HiddenSongUndo()
            com.music.spotui.parental.ParentPinPromptHost()
        }
    }
}

@Composable
private fun ImmersiveShell(navController: androidx.navigation.NavHostController) {
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        com.music.spotui.ui.immersive.shell.ImmersiveBottomChrome(
            navController,
            miniPlayer = { m -> com.music.spotui.ui.immersive.player.ImmersiveMiniPlayer(m) },
            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter),
        )
        com.music.spotui.ui.immersive.player.ImmersivePlayerOverlay(navController)
    }
}
