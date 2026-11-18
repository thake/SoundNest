package io.github.thake.soundnest.core.ui.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import io.github.thake.soundnest.core.ui.classic.AppBackground
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import io.github.thake.soundnest.feature.library.ui.classic.AlbumByIdScreen
import io.github.thake.soundnest.feature.library.ui.classic.ContextScreen
import io.github.thake.soundnest.feature.library.ui.classic.ArtistReleasesScreen
import io.github.thake.soundnest.feature.library.ui.classic.ArtistScreen
import io.github.thake.soundnest.feature.search.categories.ui.classic.CategoryScreen
import io.github.thake.soundnest.feature.home.ui.classic.HomeScreen
import io.github.thake.soundnest.feature.library.ui.classic.LibraryScreen
import io.github.thake.soundnest.feature.spokenword.ui.classic.ShowScreen
import io.github.thake.soundnest.feature.player.ui.classic.QueueScreen
import io.github.thake.soundnest.feature.search.ui.classic.SearchScreen
import io.github.thake.soundnest.core.ui.immersive.motion.ImmersiveMotion.Pages as IPages
import io.github.thake.soundnest.core.ui.immersive.motion.ImmersiveMotion.PageFade as IFade

@Composable
fun MyNavHost(
    navHostController: NavHostController,
    modifier: Modifier = Modifier,
) {

    val context = LocalContext.current
    val startDestination = Routes.Home.route
    // The look (Settings > Display) is read through State objects: the graph builder and the
    // transitions keep the same lambdas, so a switch never rebuilds the graph (or the back stack).
    val immersiveState = androidx.compose.runtime.rememberUpdatedState(isImmersive())
    val reduceState = androidx.compose.runtime.rememberUpdatedState(io.github.thake.soundnest.core.ui.look.rememberReduceMotion())

    io.github.thake.soundnest.feature.library.ui.ArtistChooserHost()
    NavHost(
        navController = navHostController,
        startDestination = startDestination,
        modifier = modifier,
        // Pages: a short shift with a fade-through, tabs fade in, the player and queue rise
        // from the bottom (see navEnter). The fades put a page in an offscreen layer for
        // their 0.1-0.2 s: the owner's call, a page change read as a hard block without them.
        // The Immersive look pushes pages like a navigation stack (ImmersiveMotion.Pages).
        enterTransition = { if (immersiveState.value) IPages.enter(reduceState.value)(this) else if (reduceState.value) EnterTransition.None else navEnter(initialState, targetState) },
        exitTransition = { if (immersiveState.value) IPages.exit(reduceState.value)(this) else if (reduceState.value) ExitTransition.None else navExit(initialState, targetState) },
        popEnterTransition = { if (immersiveState.value) IPages.popEnter(reduceState.value)(this) else if (reduceState.value) EnterTransition.None else navPopEnter(initialState, targetState) },
        popExitTransition = { if (immersiveState.value) IPages.popExit(reduceState.value)(this) else if (reduceState.value) ExitTransition.None else navPopExit(initialState, targetState) },
    ){
        page(Routes.Home.route){
            if (isImmersive()) io.github.thake.soundnest.feature.home.ui.immersive.ImmersiveHomeScreen(navHostController) else HomeScreen(navHostController)
        }
        page(Routes.Search.route){
            if (isImmersive()) io.github.thake.soundnest.feature.search.ui.immersive.ImmersiveSearchScreen(navHostController) else SearchScreen(navHostController)
        }
        page(Routes.Library.route) {
            if (isImmersive()) io.github.thake.soundnest.feature.library.ui.immersive.ImmersiveLibraryScreen(navHostController) else LibraryScreen(navHostController)
        }
        // Older links to the player route open the player sheet over the page instead.
        page(Routes.Player.route){
            LaunchedEffect(Unit) {
                navHostController.popBackStack(Routes.Player.route, inclusive = true)
                PlayerSheet.show()
            }
        }

        page(Routes.Queue.route){
            if (isImmersive()) io.github.thake.soundnest.feature.player.ui.immersive.ImmersiveQueueScreen(navHostController) else QueueScreen(navHostController)
        }

        page(Routes.LocalFiles.route){
            val policy = io.github.thake.soundnest.feature.parental.settings.rememberParentalPolicy()
            val allowed = policy.localFiles.enabled
            LaunchedEffect(allowed) { if (!allowed) blockAndPop(context, navHostController) }
            if (allowed) {
                if (isImmersive()) io.github.thake.soundnest.feature.localfiles.ui.immersive.ImmersiveLocalFilesScreen(navHostController) else io.github.thake.soundnest.feature.localfiles.ui.classic.LocalFilesScreen(navHostController)
            }
        }

        // Live Radio (see io.github.thake.soundnest.feature.radio): plays through LocalPlayer, as a Local Files song does.
        page(Routes.LiveRadio.route){
            val policy = io.github.thake.soundnest.feature.parental.settings.rememberParentalPolicy()
            val allowed = policy.radio.enabled
            LaunchedEffect(allowed) { if (!allowed) blockAndPop(context, navHostController) }
            if (allowed) {
                if (isImmersive()) io.github.thake.soundnest.feature.radio.ui.immersive.ImmersiveLiveRadioScreen(navHostController)
                else io.github.thake.soundnest.feature.radio.ui.classic.LiveRadioScreen(navHostController)
            }
        }

        page(Routes.HiddenSongs.route) {
            val tick by io.github.thake.soundnest.feature.parental.security.ParentSecurityManager.sessionTick.collectAsState()
            val unlocked = androidx.compose.runtime.remember(tick) {
                io.github.thake.soundnest.feature.parental.security.ParentSecurityManager.canAccessWithoutPrompt(
                    context,
                    io.github.thake.soundnest.feature.parental.security.AppSurface.HIDDEN_SONGS_AND_BANS,
                )
            }
            LaunchedEffect(unlocked) {
                if (!unlocked) {
                    navHostController.popBackStack()
                    io.github.thake.soundnest.feature.parental.security.ParentSecurityManager.runProtected(
                        context,
                        io.github.thake.soundnest.feature.parental.security.AppSurface.HIDDEN_SONGS_AND_BANS,
                    ) { navHostController.navigate(Routes.HiddenSongs.route) }
                }
            }
            if (unlocked) {
                if (isImmersive()) io.github.thake.soundnest.feature.library.hiddensongs.ui.immersive.ImmersiveHiddenSongsScreen(navHostController)
                else io.github.thake.soundnest.feature.library.hiddensongs.ui.classic.HiddenSongsScreen(navHostController)
            }
        }

        // The local player's equalizer, speed and silence skipping (Settings > Playback, the player's menu).
        page(Routes.Equalizer.route) {
            val policy = io.github.thake.soundnest.feature.parental.settings.rememberParentalPolicy()
            val allowed = io.github.thake.soundnest.feature.equalizer.settings.EqualizerSettings.isAvailable(policy)
            LaunchedEffect(allowed) { if (!allowed) blockAndPop(context, navHostController) }
            if (allowed) {
                if (isImmersive()) io.github.thake.soundnest.feature.equalizer.ui.immersive.ImmersiveEqualizerScreen(navHostController)
                else io.github.thake.soundnest.feature.equalizer.ui.classic.EqualizerScreen(navHostController)
            }
        }

        page(Routes.NewReleases.route) {
            val policy = io.github.thake.soundnest.feature.parental.settings.rememberParentalPolicy()
            val allowed = io.github.thake.soundnest.feature.parental.filter.HomeSectionFilter.isWhatsNewAllowed(policy)
            LaunchedEffect(allowed) { if (!allowed) blockAndPop(context, navHostController) }
            if (allowed) {
                if (isImmersive()) io.github.thake.soundnest.feature.home.whatsnew.ui.immersive.ImmersiveNewReleasesScreen(navHostController) else io.github.thake.soundnest.feature.home.whatsnew.ui.classic.NewReleasesScreen(navHostController)
            }
        }

        page(Routes.Episodes.route) {
            val policy = io.github.thake.soundnest.feature.parental.settings.rememberParentalPolicy()
            val allowed = policy.spokenWord.allowPodcasts
            LaunchedEffect(allowed) { if (!allowed) blockAndPop(context, navHostController) }
            if (allowed) {
                if (isImmersive()) io.github.thake.soundnest.feature.spokenword.podcasts.ui.immersive.ImmersiveEpisodesScreen(navHostController) else io.github.thake.soundnest.feature.spokenword.podcasts.ui.classic.EpisodesScreen(navHostController)
            }
        }

        page(Routes.NewEpisodes.route) {
            val policy = io.github.thake.soundnest.feature.parental.settings.rememberParentalPolicy()
            val allowed = policy.spokenWord.allowPodcasts
            LaunchedEffect(allowed) { if (!allowed) blockAndPop(context, navHostController) }
            if (allowed) {
                if (isImmersive()) io.github.thake.soundnest.feature.spokenword.podcasts.ui.immersive.ImmersiveNewEpisodesScreen(navHostController) else io.github.thake.soundnest.feature.spokenword.podcasts.ui.classic.NewEpisodesScreen(navHostController)
            }
        }

        page(Routes.Recap.route) {
            if (isImmersive()) io.github.thake.soundnest.feature.recap.ui.immersive.ImmersiveRecapScreen(navHostController)
            else io.github.thake.soundnest.feature.recap.ui.classic.RecapScreen(navHostController)
        }

        // Spotify's own list for the account (every device), not this phone's counter.
        page(Routes.Recent.route) {
            if (isImmersive()) io.github.thake.soundnest.feature.recap.ui.immersive.ImmersiveRecentlyPlayedScreen(navHostController)
            else io.github.thake.soundnest.feature.recap.ui.classic.RecentlyPlayedScreen(navHostController)
        }

        // New releases and episodes of what the account follows (the bell on Home).
        page(Routes.WhatsNew.route) {
            val policy = io.github.thake.soundnest.feature.parental.settings.rememberParentalPolicy()
            val allowed = io.github.thake.soundnest.feature.parental.filter.HomeSectionFilter.isWhatsNewAllowed(policy)
            LaunchedEffect(allowed) { if (!allowed) blockAndPop(context, navHostController) }
            if (allowed) {
                if (isImmersive()) io.github.thake.soundnest.feature.home.whatsnew.ui.immersive.ImmersiveWhatsNewScreen(navHostController) else io.github.thake.soundnest.feature.home.whatsnew.ui.classic.WhatsNewScreen(navHostController)
            }
        }

        page(Routes.Liked.route){
            if (isImmersive()) io.github.thake.soundnest.feature.library.ui.immersive.ImmersiveContextScreen(navHostController, type = "liked", id = "") else ContextScreen(navHostController, type = "liked", id = "")
        }

        // Settings: sound quality, storage, data, display, language.
        page(Routes.Settings.route){
            if (isImmersive()) io.github.thake.soundnest.feature.settings.ui.immersive.ImmersiveSettingsScreen(navHostController) else io.github.thake.soundnest.feature.settings.ui.classic.SettingsScreen(navHostController)
        }

        page(Routes.ParentSettings.route) {
            io.github.thake.soundnest.feature.parental.ui.ParentSettingsScreen(navHostController)
        }

        page(Routes.About.route) {
            io.github.thake.soundnest.feature.system.ui.AboutScreen(navHostController)
        }

        page(Routes.Accounts.route) {
            val tick by io.github.thake.soundnest.feature.parental.security.ParentSecurityManager.sessionTick.collectAsState()
            val unlocked = androidx.compose.runtime.remember(tick) {
                io.github.thake.soundnest.feature.parental.security.ParentSecurityManager.canAccessWithoutPrompt(
                    context,
                    io.github.thake.soundnest.feature.parental.security.AppSurface.ACCOUNTS_AND_LOGOUT,
                )
            }
            LaunchedEffect(unlocked) {
                if (!unlocked) {
                    navHostController.popBackStack()
                    io.github.thake.soundnest.feature.parental.security.ParentSecurityManager.runProtected(
                        context,
                        io.github.thake.soundnest.feature.parental.security.AppSurface.ACCOUNTS_AND_LOGOUT,
                    ) { navHostController.navigate(Routes.Accounts.route) }
                }
            }
            if (unlocked) {
                io.github.thake.soundnest.feature.accounts.ui.AccountsScreen(navHostController)
            }
        }

        page(
            "${Routes.Category.route}/{genre}?title={title}",
            arguments = listOf(navArgument("title") { defaultValue = "" }),
        ) { navBackStackEntry ->
            val policy = io.github.thake.soundnest.feature.parental.settings.rememberParentalPolicy()
            val allowed = policy.effectiveAllowAutoplayAndRadio
            LaunchedEffect(allowed) { if (!allowed) blockAndPop(context, navHostController) }
            if (allowed) {
                val genre = navBackStackEntry.arguments?.getString("genre").orEmpty()
                val title = navBackStackEntry.arguments?.getString("title").orEmpty()
                if (isImmersive()) io.github.thake.soundnest.feature.search.categories.ui.immersive.ImmersiveCategoryScreen(navHostController, genre = genre, title = title.ifBlank { genre }) else CategoryScreen(navHostController, genre = genre, title = title.ifBlank { genre })
            }
        }

        page(
            "${Routes.Album.route}/{uString}?artist={artist}&id={albumId}",
            arguments = listOf(
                navArgument("artist") { defaultValue = "" },
                navArgument("albumId") { defaultValue = "" },
            ),
        ) { navBackStackEntry ->

            /* Extracting the id from the route */
            val uId = navBackStackEntry.arguments?.getString("uString")
            val artist = navBackStackEntry.arguments?.getString("artist").orEmpty()
            /* We check if it's not null */
            val albumId = navBackStackEntry.arguments?.getString("albumId").orEmpty()
            uId?.let { name ->
                if (isImmersive()) io.github.thake.soundnest.feature.library.ui.immersive.ImmersiveAlbumByIdScreen(navHostController, name = name, artist = artist, id = albumId) else AlbumByIdScreen(navHostController, name = name, artist = artist, id = albumId)
            }
        }

        page(
            "${Routes.Playlist.route}/{pId}?name={name}",
            arguments = listOf(navArgument("name") { defaultValue = "" }),
        ) { navBackStackEntry ->
            val pId = navBackStackEntry.arguments?.getString("pId")
            val name = navBackStackEntry.arguments?.getString("name").orEmpty()
            pId?.let { if (isImmersive()) io.github.thake.soundnest.feature.library.ui.immersive.ImmersiveContextScreen(navHostController, type = "playlist", id = it) else ContextScreen(navHostController, type = "playlist", id = it) }
        }

        page("$LIKED_BY_ARTIST_ROUTE/{key}") { navBackStackEntry ->
            navBackStackEntry.arguments?.getString("key")?.let { if (isImmersive()) io.github.thake.soundnest.feature.library.ui.immersive.ImmersiveContextScreen(navHostController, type = "liked-artist", id = it) else ContextScreen(navHostController, type = "liked-artist", id = it) }
        }

        page(
            "${Routes.Show.route}/{sId}?name={name}",
            arguments = listOf(navArgument("name") { defaultValue = "" }),
        ) { navBackStackEntry ->
            val policy = io.github.thake.soundnest.feature.parental.settings.rememberParentalPolicy()
            val sId = navBackStackEntry.arguments?.getString("sId").orEmpty()
            val allowed = if (io.github.thake.soundnest.feature.spokenword.audible.data.AudibleUris.isAudibleUri(sId)) {
                policy.spokenWord.allowAudible
            } else {
                policy.spokenWord.allowPodcasts || policy.spokenWord.allowAudiobooks
            }
            LaunchedEffect(allowed) { if (!allowed) blockAndPop(context, navHostController) }
            if (allowed && sId.isNotEmpty()) {
                val name = navBackStackEntry.arguments?.getString("name").orEmpty()
                if (isImmersive()) io.github.thake.soundnest.feature.spokenword.ui.immersive.ImmersiveShowScreen(navHostController, showId = sId, showName = name) else ShowScreen(navHostController, showId = sId, showName = name)
            }
        }

        page(
            "${Routes.Episode.route}/{eId}?name={name}",
            arguments = listOf(navArgument("name") { defaultValue = "" }),
        ) { navBackStackEntry ->
            val policy = io.github.thake.soundnest.feature.parental.settings.rememberParentalPolicy()
            val allowed = policy.spokenWord.allowPodcasts
            LaunchedEffect(allowed) { if (!allowed) blockAndPop(context, navHostController) }
            if (allowed) {
                val eId = navBackStackEntry.arguments?.getString("eId")
                val name = navBackStackEntry.arguments?.getString("name").orEmpty()
                eId?.let { if (isImmersive()) io.github.thake.soundnest.feature.spokenword.podcasts.ui.immersive.ImmersiveEpisodeScreen(navHostController, episodeId = it, fallbackName = name) else io.github.thake.soundnest.feature.spokenword.podcasts.ui.classic.EpisodeScreen(navHostController, episodeId = it, fallbackName = name) }
            }
        }

        page(
            "${Routes.Folder.route}/{uri}?name={name}",
            arguments = listOf(navArgument("name") { defaultValue = "" }),
        ) { navBackStackEntry ->
            val uri = navBackStackEntry.arguments?.getString("uri")
            val name = navBackStackEntry.arguments?.getString("name").orEmpty()
            uri?.let { if (isImmersive()) io.github.thake.soundnest.feature.library.folders.ui.immersive.ImmersiveFolderScreen(navHostController, it, name) else io.github.thake.soundnest.feature.library.folders.ui.classic.FolderScreen(navHostController, it, name) }
        }

        page("${Routes.LocalPlaylist.route}/{id}") { navBackStackEntry ->
            navBackStackEntry.arguments?.getString("id")?.let { if (isImmersive()) io.github.thake.soundnest.feature.localfiles.ui.immersive.ImmersiveLocalPlaylistScreen(navHostController, it) else io.github.thake.soundnest.feature.localfiles.ui.classic.LocalPlaylistScreen(navHostController, it) }
        }

        page(
            "${Routes.ArtistReleases.route}/{aString}?id={artistId}",
            arguments = listOf(navArgument("artistId") { defaultValue = "" }),
        ) { navBackStackEntry ->
            val aId = navBackStackEntry.arguments?.getString("aString")
            val artistId = navBackStackEntry.arguments?.getString("artistId").orEmpty()
            aId?.let { if (isImmersive()) io.github.thake.soundnest.feature.library.ui.immersive.ImmersiveArtistReleasesScreen(navHostController, it, artistId) else ArtistReleasesScreen(navHostController, it, artistId) }
        }

        page(
            "${Routes.Artist.route}/{aString}?id={artistId}",
            arguments = listOf(navArgument("artistId") { defaultValue = "" }),
        ) { navBackStackEntry ->

            /* Extracting the id from the route */
            val aId = navBackStackEntry.arguments?.getString("aString")
            val artistId = navBackStackEntry.arguments?.getString("artistId").orEmpty()
            /* We check if it's not null */
            aId?.let { aid->
                if (isImmersive()) io.github.thake.soundnest.feature.library.ui.immersive.ImmersiveArtistScreen(navHostController, aid, artistId) else ArtistScreen(navHostController, aid, artistId)
            }
        }
    }
}

private val tabRoutes = setOf(Routes.Home.route, Routes.Search.route, Routes.Library.route)
// Full-height sheets over the current page (the official player / queue).
private val sheetRoutes = setOf(Routes.Player.route, Routes.Queue.route)

private fun blockAndPop(context: android.content.Context, navHostController: NavHostController) {
    android.widget.Toast.makeText(context, "Blocked by Parental Controls", android.widget.Toast.LENGTH_SHORT).show()
    navHostController.popBackStack()
}

private fun isTabSwitch(from: NavBackStackEntry, to: NavBackStackEntry) =
    from.destination.route in tabRoutes && to.destination.route in tabRoutes
private fun NavBackStackEntry.isSheet() = destination.route in sheetRoutes

// Material 3 emphasized curves: quick start, long soft landing (and the reverse to leave).
internal val Decelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
internal val Accelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
private const val PAGE_MS = 300
private const val TAB_MS = 150
internal const val SHEET_IN_MS = 340
internal const val SHEET_OUT_MS = 240

// Pages: Material's shared axis with a fade-through. The page leaving fades out over the first
// 90 ms while it moves a step aside; the page coming in moves the same short step and fades in
// over the rest. Two opaque pages sliding across the whole screen (as before) read as a heavy
// block with a hard edge running over the content; a short shift and a fade at the hand-over
// read as the official apps' quick page change. Tabs: the new one fades in, nothing moves.
private const val SHIFT = 0.08f
private const val FADE_OUT_MS = 90
private val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)

private fun pageIn(fromRight: Boolean): EnterTransition =
    slideInHorizontally(tween(PAGE_MS, easing = Standard)) { (it * SHIFT).toInt() * if (fromRight) 1 else -1 } +
        fadeIn(tween(PAGE_MS - FADE_OUT_MS, delayMillis = FADE_OUT_MS, easing = Decelerate))

private fun pageOut(toLeft: Boolean): ExitTransition =
    slideOutHorizontally(tween(PAGE_MS, easing = Standard)) { (it * SHIFT).toInt() * if (toLeft) -1 else 1 } +
        fadeOut(tween(FADE_OUT_MS, easing = Accelerate))

private fun navEnter(from: NavBackStackEntry, to: NavBackStackEntry): EnterTransition = when {
    isTabSwitch(from, to) -> fadeIn(tween(TAB_MS, easing = Decelerate))
    to.isSheet() -> slideInVertically(tween(SHEET_IN_MS, easing = Decelerate)) { it }
    else -> pageIn(fromRight = true)
}

private fun navExit(from: NavBackStackEntry, to: NavBackStackEntry): ExitTransition = when {
    isTabSwitch(from, to) -> ExitTransition.None
    // The page stays still under the rising sheet until it covers it.
    to.isSheet() -> fadeOut(tween(durationMillis = 1, delayMillis = SHEET_IN_MS))
    else -> pageOut(toLeft = true)
}

private fun navPopEnter(from: NavBackStackEntry, to: NavBackStackEntry): EnterTransition = when {
    isTabSwitch(from, to) -> fadeIn(tween(TAB_MS, easing = Decelerate))
    from.isSheet() -> EnterTransition.None
    else -> pageIn(fromRight = false)
}

private fun navPopExit(from: NavBackStackEntry, to: NavBackStackEntry): ExitTransition = when {
    isTabSwitch(from, to) -> ExitTransition.None
    from.isSheet() -> slideOutVertically(tween(SHEET_OUT_MS, easing = Accelerate)) { it }
    else -> pageOut(toLeft = false)
}

/** A destination on an opaque background, so sliding pages never show through each other. */
private fun NavGraphBuilder.page(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) = composable(route, arguments = arguments) { entry ->
    val scope = this
    val settled = androidx.compose.runtime.remember {
        androidx.compose.runtime.derivedStateOf {
            scope.transition.currentState == scope.transition.targetState
        }
    }
    val immersive = isImmersive()
    // Immersive: the page runs its own fade (ImmersiveMotion.PageFade) and hands it to the Liquid
    // Glass recording it, which then fades with it on every frame of the transition.
    val fade = if (!immersive) null else scope.transition.animateFloat(
        transitionSpec = {
            val ms = if (targetState == androidx.compose.animation.EnterExitState.Visible) IFade.enterMs else IFade.exitMs
            tween(ms)
        },
        label = "pageFade",
    ) { if (it == androidx.compose.animation.EnterExitState.Visible) 1f else 0f }
    val alpha = androidx.compose.runtime.remember(fade) { { fade?.value ?: 1f } }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalPageSettled provides settled,
        io.github.thake.soundnest.core.ui.immersive.shell.backdrop.backdrops.LocalBackdropSourceAlpha provides alpha,
    ) {
        // Large windows: detail pages (album, playlist, artist, settings…) keep a readable width,
        // centred, instead of rows 2000 px wide; the tab pages and grids use the whole width
        // (more columns). The inner box is always there, so a resize never rebuilds the page.
        val capped = io.github.thake.soundnest.core.ui.look.windowSize().large &&
            entry.destination.route?.substringBefore('/')?.substringBefore('?') !in FullWidthRoutes
        // Immersive look: every page (Classic fallbacks too) sits on its black page colour.
        Box(Modifier.fillMaxSize()
            .then(if (fade == null) Modifier else Modifier.graphicsLayer { this.alpha = fade.value })
            .background(if (immersive) io.github.thake.soundnest.core.ui.immersive.Immersive.Page else AppBackground)) {
            Box(
                Modifier.fillMaxHeight().then(
                    if (capped) Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally)
                        .widthIn(max = io.github.thake.soundnest.core.ui.look.WindowSize.MaxPageWidth)
                    else Modifier.fillMaxWidth()
                )
            ) { scope.content(entry) }
        }
    }
}

/** Pages that use a large window's whole width (grids, the tabs' own pages, the player). */
private val FullWidthRoutes = setOf(
    Routes.Home.route, Routes.Search.route, Routes.Library.route, Routes.Player.route, Routes.Queue.route,
    Routes.Category.route, Routes.NewReleases.route,
)

/**
 * false while this page is sliding in or out. Heavy views (video players, surfaces)
 * wait for it: built in the first frame, they stalled it and the motion jumped.
 */
val LocalPageSettled = androidx.compose.runtime.staticCompositionLocalOf<androidx.compose.runtime.State<Boolean>> {
    androidx.compose.runtime.mutableStateOf(true)
}

/** The Immersive look is on (Settings > Display, or the onboarding's look step). */
@Composable
internal fun isImmersive(): Boolean =
    io.github.thake.soundnest.core.ui.look.LocalLook.current == io.github.thake.soundnest.feature.appearance.settings.Look.IMMERSIVE
