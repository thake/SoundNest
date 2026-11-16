/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.thake.soundnest.api.SoundNestApi
import io.github.thake.soundnest.data.api.Api
import io.github.thake.soundnest.data.entity.HomeFeedModel
import io.github.thake.soundnest.data.entity.HomeItem
import io.github.thake.soundnest.data.entity.HomeSection
import io.github.thake.soundnest.data.entity.LibraryEntry
import io.github.thake.soundnest.data.preferences.Look
import io.github.thake.soundnest.di.Graph
import io.github.thake.soundnest.di.SongPlayer
import io.github.thake.soundnest.host.android.AccountStore
import io.github.thake.soundnest.host.android.SoundNestRuntime
import io.github.thake.soundnest.host.android.WebPlayerHost
import io.github.thake.soundnest.parental.FilterMode
import io.github.thake.soundnest.parental.HomeSectionConfig
import io.github.thake.soundnest.parental.NetworkSnapshot
import io.github.thake.soundnest.parental.ParentPinSetupScreen
import io.github.thake.soundnest.parental.ParentSecurityManager
import io.github.thake.soundnest.parental.ParentSettingsScreen
import io.github.thake.soundnest.parental.ParentalPolicy
import io.github.thake.soundnest.parental.ParentalPolicyStore
import io.github.thake.soundnest.parental.TrustedNetworkGate
import io.github.thake.soundnest.ui.components.ProvideWindowSize
import io.github.thake.soundnest.ui.components.WindowSize
import io.github.thake.soundnest.ui.components.windowSize
import io.github.thake.soundnest.ui.immersive.player.ImmersiveMiniPlayer
import io.github.thake.soundnest.ui.immersive.player.ImmersivePlayer
import io.github.thake.soundnest.ui.immersive.player.ImmersivePlayerActions
import io.github.thake.soundnest.ui.immersive.player.ImmersivePlayerState
import io.github.thake.soundnest.ui.immersive.player.ImmersiveRepeat
import io.github.thake.soundnest.ui.immersive.screens.ImmersiveHomeScreen
import io.github.thake.soundnest.ui.immersive.shell.ImmersiveBottomChrome
import io.github.thake.soundnest.ui.immersive.shell.LocalImmersiveChrome
import io.github.thake.soundnest.ui.immersive.shell.rememberImmersiveChromeState
import io.github.thake.soundnest.ui.look.LocalLook
import io.github.thake.soundnest.ui.navigation.MainBottomNavigation
import io.github.thake.soundnest.ui.navigation.MainNavigationRail
import io.github.thake.soundnest.ui.navigation.Routes
import io.github.thake.soundnest.ui.screens.SettingsScreen
import io.github.thake.soundnest.ui.theme.SpotuiTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.Proxy
import java.util.concurrent.TimeUnit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED

/**
 * On-demand headless documentation screenshot generator powered by Roborazzi + Robolectric native graphics.
 *
 * Skipped during regular `:app:testDebugUnitTest` runs so unit tests stay fast.
 * Run explicitly via:
 *   `./gradlew :app:generateDocScreenshots`
 *
 * Outputs deterministic PNG screenshots directly to `docs/screenshots/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xxhdpi", application = Application::class)
class DocScreenshotTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var outputDir: File
    private lateinit var covers: Map<String, String>

    private val approvedPlaylists = setOf("ghibli", "roadtrip", "curious")
    private val approvedAlbums = setOf("planets", "dragon")

    @Before
    fun setUp() {
        assumeTrue(
            "DocScreenshotTest runs only via :app:generateDocScreenshots",
            System.getProperty("soundnest.docScreenshots") == "true",
        )
        val app = RuntimeEnvironment.getApplication()
        Graph::class.java.getDeclaredField("app").apply { isAccessible = true }.set(null, app)

        ParentSecurityManager.resetForTests(app)
        ParentalPolicyStore.resetForTests(app)
        TrustedNetworkGate.resetForTests(app)
        TrustedNetworkGate.snapshotOverride = {
            NetworkSnapshot(
                onWifi = true,
                ssid = "FamilyHomeWiFi",
                bssid = "aa:bb:cc:dd:ee:01",
            )
        }
        TrustedNetworkGate.addTrustedWifi(app, "FamilyHomeWiFi", "aa:bb:cc:dd:ee:01")
        SongPlayer.setSleepTimer(null)

        val dirProp = System.getProperty("soundnest.docScreenshotsDir")
        outputDir = (if (!dirProp.isNullOrBlank()) File(dirProp) else File("../docs/screenshots")).apply { mkdirs() }
        val coverDir = File(app.cacheDir, "doc_covers").apply { mkdirs() }
        covers = generateProceduralCovers(coverDir)

        seedCurrentSong(
            title = "Clair de Lune",
            artist = "Claude Debussy",
            album = "Suite bergamasque",
            coverUri = covers.getValue("debussy"),
        )
        offlineRuntime()
        bareHost()
    }

    @After
    fun tearDown() {
        val app = RuntimeEnvironment.getApplication()
        SongPlayer.setSleepTimer(null)
        ParentSecurityManager.resetForTests(app)
        ParentalPolicyStore.resetForTests(app)
        TrustedNetworkGate.resetForTests(app)
        Api.HomeCache.clear()
        runCatching {
            SoundNestRuntime::class.java.getDeclaredField("host").apply { isAccessible = true }
                .set(SoundNestRuntime, null)
        }
    }

    @Test
    fun immersiveHome() {
        val app = RuntimeEnvironment.getApplication()
        ParentSecurityManager.setupInitialPin(app, "246810")
        ParentSecurityManager.lockNow(app)
        ParentalPolicyStore.replace(
            app,
            ParentalPolicy(
                filterMode = FilterMode.FILTERED_OPEN,
                blockExplicit = true,
                allowedPlaylistIds = approvedPlaylists,
                allowedAlbumIds = approvedAlbums,
                allowPodcasts = false,
                allowAudiobooks = false,
                homeSections = HomeSectionConfig(
                    minimalHomeMode = false,
                    showParentPicks = true,
                    showChartsAndTrending = false,
                ),
            ),
        )
        seedFeedAndLibraryCache()
        shootDoc("immersive-home", Look.IMMERSIVE) {
            ImmersiveShell(Routes.Home.route, capped = false) { nav ->
                ImmersiveHomeScreen(nav)
            }
        }
    }

    @Test
    fun minimalHome() {
        val app = RuntimeEnvironment.getApplication()
        ParentSecurityManager.setupInitialPin(app, "246810")
        ParentSecurityManager.lockNow(app)
        ParentalPolicyStore.replace(
            app,
            ParentalPolicy(
                filterMode = FilterMode.STRICT_ALLOWLIST,
                blockExplicit = true,
                allowedPlaylistIds = approvedPlaylists,
                allowedAlbumIds = approvedAlbums,
                allowPodcasts = false,
                allowAudiobooks = false,
                homeSections = HomeSectionConfig(
                    minimalHomeMode = true,
                    showFilterChips = false,
                    showWhatsNewBell = false,
                    showParentPicks = true,
                ),
            ),
        )
        seedFeedAndLibraryCache()
        shootDoc("minimal-home", Look.IMMERSIVE) {
            ImmersiveShell(Routes.Home.route, capped = false) { nav ->
                ImmersiveHomeScreen(nav)
            }
        }
    }

    @Test
    fun parentPinGate() {
        val app = RuntimeEnvironment.getApplication()
        ParentSecurityManager.resetForTests(app)
        shootDoc("parent-pin-gate", Look.CLASSIC) {
            ParentPinSetupScreen(
                onPinConfigured = {},
                onSwitchOrLogout = {},
            )
        }
    }

    @Test
    fun parentDashboard() {
        val app = RuntimeEnvironment.getApplication()
        ParentSecurityManager.setupInitialPin(app, "246810")
        ParentalPolicyStore.replace(
            app,
            ParentalPolicy(
                filterMode = FilterMode.FILTERED_OPEN,
                blockExplicit = true,
                allowedPlaylistIds = approvedPlaylists,
                allowedAlbumIds = approvedAlbums,
                blockedKeywords = setOf("explicit remix"),
                allowPodcasts = false,
                allowAudiobooks = false,
                allowLyrics = true,
                readOnlySpotifyLibrary = true,
                homeSections = HomeSectionConfig(
                    showChartsAndTrending = false,
                    showMixesAndMadeForYou = false,
                    showParentPicks = true,
                ),
            ),
        )
        seedFeedAndLibraryCache()
        shootDoc("parent-dashboard", Look.CLASSIC) {
            val nav = rememberNavController()
            ParentSettingsScreen(navController = nav)
        }
    }

    @Test
    fun kidSettings() {
        val app = RuntimeEnvironment.getApplication()
        ParentSecurityManager.setupInitialPin(app, "246810")
        ParentSecurityManager.lockNow(app)
        shootDoc("kid-settings", Look.CLASSIC) {
            ClassicShell("detail", capped = true) { nav ->
                SettingsScreen(nav)
            }
        }
    }

    @Test
    fun immersivePlayer() {
        val app = RuntimeEnvironment.getApplication()
        ParentSecurityManager.setupInitialPin(app, "246810")
        ParentSecurityManager.lockNow(app)
        SongPlayer.setSleepTimer(30)
        shootDoc("immersive-player", Look.IMMERSIVE) {
            ImmersivePlayer(
                state = ImmersivePlayerState(
                    title = "Jupiter, the Bringer of Jollity",
                    artist = "Gustav Holst • London Symphony",
                    album = "The Planets, Op. 32",
                    coverUrl = covers.getValue("planets"),
                    playing = true,
                    positionMs = { 142_000L },
                    durationMs = 478_000L,
                    shuffle = false,
                    repeatMode = ImmersiveRepeat.ALL,
                    liked = true,
                    contextName = "Parent Picks • Classical",
                ),
                actions = ImmersivePlayerActions(),
                navController = rememberNavController(),
            )
        }
    }

    private fun seedFeedAndLibraryCache() {
        val libraryEntries = listOf(
            LibraryEntry(
                spotifyId = "ghibli",
                name = "Studio Ghibli Piano",
                subtitle = "Playlist • SoundNest",
                coverUri = covers.getValue("ghibli"),
                isPlaylist = true,
                uri = "spotify:playlist:ghibli",
            ),
            LibraryEntry(
                spotifyId = "roadtrip",
                name = "Family Roadtrip",
                subtitle = "Playlist • SoundNest",
                coverUri = covers.getValue("roadtrip"),
                isPlaylist = true,
                uri = "spotify:playlist:roadtrip",
            ),
            LibraryEntry(
                spotifyId = "planets",
                name = "Holst: The Planets",
                subtitle = "Album • London Symphony Orchestra",
                coverUri = covers.getValue("planets"),
                isPlaylist = false,
                artists = "Gustav Holst",
                uri = "spotify:album:planets",
            ),
            LibraryEntry(
                spotifyId = "curious",
                name = "Classical for Curious Minds",
                subtitle = "Playlist • SoundNest",
                coverUri = covers.getValue("classical"),
                isPlaylist = true,
                uri = "spotify:playlist:curious",
            ),
        )
        val curatedFeed = HomeFeedModel(
            greeting = "Good afternoon",
            sections = listOf(
                HomeSection(
                    title = "",
                    items = listOf(
                        HomeItem.Playlist("Studio Ghibli Piano", covers.getValue("ghibli"), "Joe Hisaishi", id = "ghibli"),
                        HomeItem.Playlist("Family Roadtrip", covers.getValue("roadtrip"), "Sing-Along Favorites", id = "roadtrip"),
                        HomeItem.Album(id = "planets", name = "The Planets", imageUrl = covers.getValue("planets"), subtitle = "Gustav Holst", artists = "London Symphony"),
                        HomeItem.Playlist("Acoustic Morning", covers.getValue("acoustic"), "Warm Guitar & Folk", id = "acoustic"),
                        HomeItem.Playlist("Bedtime Lullabies", covers.getValue("bedtime"), "Soft Piano", id = "bedtime"),
                    ),
                ),
                HomeSection(
                    title = "Movie & Adventure Soundtracks",
                    uri = "spotify:section:soundtracks",
                    items = listOf(
                        HomeItem.Album(id = "dragon", name = "How to Train Your Dragon", imageUrl = covers.getValue("dragon"), subtitle = "John Powell", artists = "John Powell"),
                        HomeItem.Album(id = "encanto", name = "Encanto (Soundtrack)", imageUrl = covers.getValue("encanto"), subtitle = "Lin-Manuel Miranda", artists = "Lin-Manuel Miranda"),
                        HomeItem.Album(id = "starwars", name = "Star Wars: A New Hope", imageUrl = covers.getValue("starwars"), subtitle = "John Williams", artists = "John Williams"),
                    ),
                ),
                HomeSection(
                    title = "Calm & Instrumental",
                    uri = "spotify:section:calm",
                    items = listOf(
                        HomeItem.Playlist("Classical for Curious Minds", covers.getValue("classical"), "Mozart, Vivaldi & Bach", id = "curious"),
                        HomeItem.Album(id = "carnival", name = "Carnival of the Animals", imageUrl = covers.getValue("carnival"), subtitle = "Camille Saint-Saens", artists = "Camille Saint-Saens"),
                        HomeItem.Playlist("Rainy Day Study Beats", covers.getValue("study"), "Instrumental Focus", id = "study"),
                    ),
                ),
            ),
        )
        Api.HomeCache.library = libraryEntries
        Api.HomeCache.libraryRoot = libraryEntries
        Api.HomeCache.home = curatedFeed
    }

    private fun shootDoc(name: String, look: Look, content: @Composable () -> Unit) {
        val target = File(outputDir, "$name.png")
        rule.mainClock.autoAdvance = false
        rule.setContent {
            SpotuiTheme {
                CompositionLocalProvider(LocalLook provides look) {
                    ProvideWindowSize {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(if (look == Look.IMMERSIVE) Color.Black else Color(0xFF121212)),
                        ) {
                            content()
                        }
                    }
                }
            }
        }
        // Pump both Compose clock and Robolectric Looper so Glide's background decode of local file:// covers completes.
        repeat(18) {
            Thread.sleep(40)
            ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)
            rule.mainClock.advanceTimeBy(120)
        }
        rule.onRoot().captureRoboImage(target.absolutePath)
        assertTrue("Expected screenshot ${target.absolutePath} to exist", target.exists())
        assertTrue("Expected screenshot ${target.name} to be non-empty", target.length() > 8_000L)
    }

    @Composable
    private fun ClassicShell(route: String, capped: Boolean, page: @Composable (NavHostController) -> Unit) {
        val nav = rememberNavController()
        val large = windowSize().large
        Row(Modifier.fillMaxSize()) {
            if (windowSize().navigationRail) MainNavigationRail(nav)
            Scaffold(modifier = Modifier.weight(1f), bottomBar = { MainBottomNavigation(nav) }) { _ ->
                NavHost(nav, startDestination = route) {
                    listOf(Routes.Home.route, Routes.Search.route, Routes.Library.route, "detail").forEach { r ->
                        composable(r) {
                            CompositionLocalProvider(androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner provides rule.activity) {
                                Box(Modifier.fillMaxSize().background(Color(0xFF121212))) {
                                    Box(
                                        Modifier.fillMaxHeight().then(
                                            if (large && capped) {
                                                Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = WindowSize.MaxPageWidth)
                                            } else {
                                                Modifier.fillMaxWidth()
                                            },
                                        ),
                                    ) { page(nav) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ImmersiveShell(route: String, capped: Boolean, page: @Composable (NavHostController) -> Unit) {
        val nav = rememberNavController()
        val large = windowSize().large
        val chrome = rememberImmersiveChromeState()
        CompositionLocalProvider(LocalImmersiveChrome provides chrome) {
            Box(Modifier.fillMaxSize()) {
                NavHost(nav, startDestination = route) {
                    listOf(Routes.Home.route, Routes.Search.route, Routes.Library.route, "detail").forEach { r ->
                        composable(r) {
                            CompositionLocalProvider(androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner provides rule.activity) {
                                Box(Modifier.fillMaxSize().background(Color.Black)) {
                                    Box(
                                        Modifier.fillMaxHeight().then(
                                            if (large && capped) {
                                                Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = WindowSize.MaxPageWidth)
                                            } else {
                                                Modifier.fillMaxWidth()
                                            },
                                        ),
                                    ) { page(nav) }
                                }
                            }
                        }
                    }
                }
                ImmersiveBottomChrome(
                    nav,
                    miniPlayer = { m -> ImmersiveMiniPlayer(m) },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }

    private fun seedCurrentSong(title: String, artist: String, album: String, coverUri: String) {
        Graph.currentSongState.updateSongState(
            coverUri = coverUri,
            title = title,
            singer = artist,
            playingState = true,
            songId = 1,
            songIndex = 0,
            album = album,
        )
    }

    private fun offlineRuntime() {
        SoundNestRuntime.useApi(stub(SoundNestApi::class.java))
    }

    private fun bareHost() {
        val unsafe = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        val host = unsafe.javaClass.getMethod("allocateInstance", Class::class.java)
            .invoke(unsafe, WebPlayerHost::class.java) as WebPlayerHost
        val ctx = RuntimeEnvironment.getApplication()
        WebPlayerHost::class.java.getDeclaredField("accounts").apply { isAccessible = true }
            .set(host, AccountStore(File(ctx.filesDir, "accounts.json")))
        SoundNestRuntime.host = host
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> stub(type: Class<T>): T = Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, m, args ->
        val r = m.returnType
        val g = m.genericReturnType.typeName
        when {
            m.name == "toString" -> "stub(${type.simpleName})"
            m.name == "hashCode" -> System.identityHashCode(proxy)
            m.name == "equals" -> proxy === args?.get(0)
            args?.lastOrNull() is Continuation<*> -> COROUTINE_SUSPENDED
            SharedFlow::class.java.isAssignableFrom(r) && !StateFlow::class.java.isAssignableFrom(r) -> MutableSharedFlow<Any?>()
            StateFlow::class.java.isAssignableFrom(r) -> MutableStateFlow<Any?>(
                when {
                    g.contains("Boolean") -> false
                    g.contains("List<") -> emptyList<Any>()
                    else -> null
                },
            )
            Flow::class.java.isAssignableFrom(r) -> emptyFlow<Any?>()
            r == java.lang.Boolean.TYPE -> false
            r == Integer.TYPE -> 0
            r == java.lang.Long.TYPE -> 0L
            r == String::class.java -> ""
            List::class.java.isAssignableFrom(r) -> emptyList<Any>()
            Map::class.java.isAssignableFrom(r) -> emptyMap<Any, Any>()
            r.isInterface -> stub(r)
            else -> null
        }
    } as T

    /**
     * Generates deterministic, vibrant local cover artwork PNGs using Android Canvas gradients & geometric shapes
     * so documentation screenshots look rich offline without relying on network or third-party copyrighted art.
     */
    private fun generateProceduralCovers(dir: File): Map<String, String> {
        data class Spec(val key: String, val c1: Int, val c2: Int, val accent: Int, val motif: Int)

        val specs = listOf(
            Spec("ghibli", 0xFF0F766E.toInt(), 0xFF38BDF8.toInt(), 0xFFFDE68A.toInt(), 0),
            Spec("roadtrip", 0xFFEA580C.toInt(), 0xFFFACC15.toInt(), 0xFFFFFBEB.toInt(), 1),
            Spec("planets", 0xFF1E1B4B.toInt(), 0xFF4338CA.toInt(), 0xFFF59E0B.toInt(), 2),
            Spec("acoustic", 0xFF065F46.toInt(), 0xFF34D399.toInt(), 0xFFD1FAE5.toInt(), 1),
            Spec("bedtime", 0xFF1E293B.toInt(), 0xFF3B82F6.toInt(), 0xFFBAE6FD.toInt(), 2),
            Spec("dragon", 0xFF7C2D12.toInt(), 0xFFEF4444.toInt(), 0xFFFDE047.toInt(), 0),
            Spec("encanto", 0xFF701A75.toInt(), 0xFFEC4899.toInt(), 0xFF6EE7B7.toInt(), 2),
            Spec("starwars", 0xFF090D16.toInt(), 0xFF1D4ED8.toInt(), 0xFF93C5FD.toInt(), 2),
            Spec("classical", 0xFF3F2E21.toInt(), 0xFFB45309.toInt(), 0xFFFEF3C7.toInt(), 1),
            Spec("carnival", 0xFF14532D.toInt(), 0xFF22C55E.toInt(), 0xFFFEF08A.toInt(), 0),
            Spec("study", 0xFF164E63.toInt(), 0xFF06B6D4.toInt(), 0xFFCFFAFE.toInt(), 1),
            Spec("debussy", 0xFF172554.toInt(), 0xFF6366F1.toInt(), 0xFFE0E7FF.toInt(), 2),
        )

        return specs.associate { s ->
            val file = File(dir, "${s.key}.png")
            val size = 512
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            paint.shader = LinearGradient(
                0f, 0f, size.toFloat(), size.toFloat(),
                s.c1, s.c2, Shader.TileMode.CLAMP,
            )
            canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)

            // Soft radial glow
            paint.shader = RadialGradient(
                size * 0.72f, size * 0.28f, size * 0.55f,
                (s.accent and 0x00FFFFFF) or 0x66000000,
                0x00000000,
                Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(size * 0.72f, size * 0.28f, size * 0.55f, paint)
            paint.shader = null

            when (s.motif) {
                0 -> {
                    // Rolling hills + rising sun
                    paint.color = s.accent
                    paint.alpha = 220
                    canvas.drawCircle(size * 0.68f, size * 0.32f, size * 0.14f, paint)
                    val path = Path().apply {
                        moveTo(0f, size * 0.72f)
                        cubicTo(size * 0.28f, size * 0.55f, size * 0.62f, size * 0.82f, size.toFloat(), size * 0.62f)
                        lineTo(size.toFloat(), size.toFloat())
                        lineTo(0f, size.toFloat())
                        close()
                    }
                    paint.color = s.c1
                    paint.alpha = 185
                    canvas.drawPath(path, paint)
                }
                1 -> {
                    // Soundwave bars
                    paint.style = Paint.Style.STROKE
                    paint.strokeCap = Paint.Cap.ROUND
                    paint.strokeWidth = 22f
                    paint.color = s.accent
                    paint.alpha = 215
                    val heights = floatArrayOf(0.24f, 0.44f, 0.62f, 0.38f, 0.50f)
                    heights.forEachIndexed { idx, h ->
                        val x = size * (0.24f + idx * 0.13f)
                        val half = size * h * 0.5f
                        canvas.drawLine(x, size * 0.5f - half, x, size * 0.5f + half, paint)
                    }
                    paint.style = Paint.Style.FILL
                }
                else -> {
                    // Planet + orbital ring
                    paint.color = s.accent
                    paint.alpha = 230
                    canvas.drawCircle(size * 0.5f, size * 0.5f, size * 0.20f, paint)
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 12f
                    paint.alpha = 180
                    canvas.save()
                    canvas.rotate(-24f, size * 0.5f, size * 0.5f)
                    canvas.drawOval(RectF(size * 0.14f, size * 0.39f, size * 0.86f, size * 0.61f), paint)
                    canvas.restore()
                    paint.style = Paint.Style.FILL
                }
            }

            FileOutputStream(file).use { out -> bmp.compress(Bitmap.CompressFormat.PNG, 95, out) }
            bmp.recycle()
            s.key to file.toURI().toString()
        }
    }
}
