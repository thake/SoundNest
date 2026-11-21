/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.core.ui

import io.github.thake.soundnest.feature.library.settings.LibraryParentalPolicy
import io.github.thake.soundnest.feature.localfiles.settings.LocalFilesParentalPolicy
import io.github.thake.soundnest.feature.lyrics.settings.LyricsParentalPolicy
import io.github.thake.soundnest.feature.player.settings.PlaybackParentalPolicy
import io.github.thake.soundnest.feature.radio.settings.RadioParentalPolicy
import io.github.thake.soundnest.feature.spokenword.audible.settings.AudibleParentalPolicy
import io.github.thake.soundnest.feature.spokenword.settings.SpokenWordParentalPolicy

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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.thake.soundnest.ConnectingScreen
import io.github.thake.soundnest.LoginProblemScreen
import io.github.thake.soundnest.feature.lyrics.ui.classic.LyricsScreen
import io.github.thake.soundnest.feature.parental.ui.ManageContentRulesSheet
import io.github.thake.soundnest.feature.parental.ui.ParentSettingsPage
import io.github.thake.soundnest.api.Account
import io.github.thake.soundnest.api.Album
import io.github.thake.soundnest.api.AlbumRef
import io.github.thake.soundnest.api.Artist
import io.github.thake.soundnest.api.ArtistRef
import io.github.thake.soundnest.api.Category
import io.github.thake.soundnest.api.CreditRole
import io.github.thake.soundnest.api.Credits
import io.github.thake.soundnest.api.Device
import io.github.thake.soundnest.api.Devices
import io.github.thake.soundnest.api.Episode
import io.github.thake.soundnest.api.Home
import io.github.thake.soundnest.api.HomeFeed
import io.github.thake.soundnest.api.HomeFilter
import io.github.thake.soundnest.api.Image
import io.github.thake.soundnest.api.Library
import io.github.thake.soundnest.api.LibraryFilter
import io.github.thake.soundnest.api.LibraryItem
import io.github.thake.soundnest.api.MediaCard
import io.github.thake.soundnest.api.MediaKind
import io.github.thake.soundnest.api.Page
import io.github.thake.soundnest.api.Shelf
import io.github.thake.soundnest.api.Podcasts
import io.github.thake.soundnest.api.RecentEntry
import io.github.thake.soundnest.api.RecentItem
import io.github.thake.soundnest.api.Recents
import io.github.thake.soundnest.api.SoundNestApi
import io.github.thake.soundnest.api.TopContent
import io.github.thake.soundnest.api.Track
import io.github.thake.soundnest.api.TrackExtras
import io.github.thake.soundnest.api.Users
import io.github.thake.soundnest.api.WhatsNewItem
import io.github.thake.soundnest.api.WhatsNewState
import io.github.thake.soundnest.core.catalog.Api
import io.github.thake.soundnest.core.catalog.Response
import io.github.thake.soundnest.core.model.AccountModel
import io.github.thake.soundnest.core.model.AlbumsModel
import io.github.thake.soundnest.core.model.ArtistOverviewModel
import io.github.thake.soundnest.core.model.ArtistTrackUi
import io.github.thake.soundnest.core.model.ArtistsModel
import io.github.thake.soundnest.core.model.ContextPageModel
import io.github.thake.soundnest.core.model.HomeFeedModel
import io.github.thake.soundnest.core.model.HomeItem
import io.github.thake.soundnest.core.model.HomeSection
import io.github.thake.soundnest.core.model.LibraryEntry
import io.github.thake.soundnest.feature.lyrics.data.LyricLine
import io.github.thake.soundnest.feature.lyrics.data.Lyrics
import io.github.thake.soundnest.feature.lyrics.data.LyricsColors
import io.github.thake.soundnest.core.model.PodcastModel
import io.github.thake.soundnest.core.model.SearchResults
import io.github.thake.soundnest.core.model.ShowPage
import io.github.thake.soundnest.core.model.SongsModel
import io.github.thake.soundnest.feature.recap.data.ListenHistory
import io.github.thake.soundnest.feature.localfiles.data.LocalItem
import io.github.thake.soundnest.feature.localfiles.data.LocalPlaylist
import io.github.thake.soundnest.feature.localfiles.data.LocalPlaylists
import io.github.thake.soundnest.feature.library.hiddensongs.settings.HiddenSongs
import io.github.thake.soundnest.feature.appearance.settings.Look
import io.github.thake.soundnest.feature.appearance.settings.LookPref
import io.github.thake.soundnest.feature.library.settings.setLibraryGridView
import io.github.thake.soundnest.core.di.Graph
import io.github.thake.soundnest.core.playback.SongPlayer
import io.github.thake.soundnest.host.android.AccountStore
import io.github.thake.soundnest.host.android.SoundNestRuntime
import io.github.thake.soundnest.host.android.WebPlayerHost
import io.github.thake.soundnest.feature.parental.security.AppSurface
import io.github.thake.soundnest.feature.parental.ui.ChangeParentPinDialog
import io.github.thake.soundnest.feature.parental.settings.FilterMode
import io.github.thake.soundnest.feature.home.settings.HomeParentalPolicy
import io.github.thake.soundnest.feature.speakers.settings.NetworkSnapshot
import io.github.thake.soundnest.feature.parental.ui.ParentAdminSessionBanner
import io.github.thake.soundnest.feature.parental.ui.ParentBlockTarget
import io.github.thake.soundnest.feature.parental.ui.ParentPinPromptHost
import io.github.thake.soundnest.feature.parental.ui.ParentPinSetupScreen
import io.github.thake.soundnest.feature.parental.ui.ParentQuickBlockSheet
import io.github.thake.soundnest.feature.parental.ui.ParentRuleKind
import io.github.thake.soundnest.feature.parental.security.ParentSecurityManager
import io.github.thake.soundnest.feature.parental.ui.ParentSettingsScreen
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore
import io.github.thake.soundnest.feature.parental.settings.RuleLabelStore
import io.github.thake.soundnest.feature.speakers.settings.TrustedNetworkGate
import io.github.thake.soundnest.feature.radio.data.RadioFavorite
import io.github.thake.soundnest.feature.radio.data.RadioFavoritesStore
import io.github.thake.soundnest.feature.radio.data.RadioStation
import io.github.thake.soundnest.feature.radio.ui.immersive.ImmersiveLiveRadioScreen
import io.github.thake.soundnest.feature.radio.ui.classic.LiveRadioScreen
import io.github.thake.soundnest.feature.system.ui.AboutSheet
import io.github.thake.soundnest.feature.speakers.ui.DevicesSheet
import io.github.thake.soundnest.feature.spokenword.ui.EpisodeOptionsSheet
import io.github.thake.soundnest.feature.accounts.ui.LoginHelpDialog
import io.github.thake.soundnest.feature.library.ui.PlaylistOptionsSheet
import io.github.thake.soundnest.core.ui.look.ProvideWindowSize
import io.github.thake.soundnest.feature.player.settings.QualitySheet
import io.github.thake.soundnest.feature.player.sleeptimer.ui.classic.SleepTimerSheet
import io.github.thake.soundnest.feature.player.ui.SongOptionsSheet
import io.github.thake.soundnest.feature.system.ui.StatsForNerdsSheet
import io.github.thake.soundnest.core.ui.look.WindowSize
import io.github.thake.soundnest.core.ui.look.windowSize
import io.github.thake.soundnest.feature.spokenword.podcasts.ui.immersive.ImmersiveEpisodeScreen
import io.github.thake.soundnest.feature.spokenword.podcasts.ui.immersive.ImmersiveEpisodesScreen
import io.github.thake.soundnest.feature.equalizer.ui.immersive.ImmersiveEqualizerScreen
import io.github.thake.soundnest.feature.library.folders.ui.immersive.ImmersiveFolderScreen
import io.github.thake.soundnest.feature.library.hiddensongs.ui.immersive.ImmersiveHiddenSongsScreen
import io.github.thake.soundnest.feature.localfiles.ui.immersive.ImmersiveLocalFilesScreen
import io.github.thake.soundnest.feature.localfiles.ui.immersive.ImmersiveLocalPlaylistScreen
import io.github.thake.soundnest.feature.spokenword.podcasts.ui.immersive.ImmersiveNewEpisodesScreen
import io.github.thake.soundnest.feature.home.whatsnew.ui.immersive.ImmersiveNewReleasesScreen
import io.github.thake.soundnest.feature.player.ui.immersive.ImmersiveQueueScreen
import io.github.thake.soundnest.feature.recap.ui.immersive.ImmersiveRecapScreen
import io.github.thake.soundnest.feature.recap.ui.immersive.ImmersiveRecentlyPlayedScreen
import io.github.thake.soundnest.feature.settings.ui.immersive.ImmersiveSettingsScreen
import io.github.thake.soundnest.feature.home.whatsnew.ui.immersive.ImmersiveWhatsNewScreen
import io.github.thake.soundnest.feature.settings.ui.immersive.SettingsPage
import io.github.thake.soundnest.feature.player.ui.immersive.ImmersiveMiniPlayer
import io.github.thake.soundnest.feature.player.ui.immersive.ImmersivePlayer
import io.github.thake.soundnest.feature.player.ui.immersive.ImmersivePlayerActions
import io.github.thake.soundnest.feature.player.ui.immersive.ImmersivePlayerState
import io.github.thake.soundnest.feature.spokenword.ui.immersive.ImmersivePodcastPlayer
import io.github.thake.soundnest.feature.player.ui.immersive.ImmersiveRepeat
import io.github.thake.soundnest.feature.player.ui.immersive.Panel
import io.github.thake.soundnest.feature.spokenword.audiobooks.ui.AudiobookChaptersSheet
import io.github.thake.soundnest.feature.spokenword.audiobooks.ui.ChapterOptionsSheet
import io.github.thake.soundnest.feature.spokenword.audiobooks.ui.classic.AudiobookPlayerScreen
import io.github.thake.soundnest.feature.spokenword.audiobooks.ui.immersive.ImmersiveAudiobookPlayer
import io.github.thake.soundnest.feature.spokenword.ui.immersive.ImmersiveShowScreen
import io.github.thake.soundnest.feature.library.ui.immersive.ImmersiveArtistReleasesScreen
import io.github.thake.soundnest.feature.library.ui.immersive.ImmersiveArtistScreen
import io.github.thake.soundnest.feature.search.categories.ui.immersive.ImmersiveCategoryScreen
import io.github.thake.soundnest.feature.library.ui.immersive.ImmersiveContextScreen
import io.github.thake.soundnest.feature.home.ui.immersive.ImmersiveHomeScreen
import io.github.thake.soundnest.feature.library.ui.immersive.ImmersiveLibraryScreen
import io.github.thake.soundnest.feature.search.ui.immersive.ImmersiveSearchScreen
import io.github.thake.soundnest.feature.system.ui.AboutScreen
import io.github.thake.soundnest.feature.accounts.ui.AccountsScreen
import io.github.thake.soundnest.core.ui.navigation.MenuNavigationDrawer
import io.github.thake.soundnest.feature.player.sleeptimer.ui.immersive.ImmersiveSleepTimerSheet
import io.github.thake.soundnest.feature.player.ui.immersive.ImmersiveSongOptionsSheet
import io.github.thake.soundnest.core.ui.immersive.shell.ImmersiveBottomChrome
import io.github.thake.soundnest.core.ui.immersive.shell.LocalImmersiveChrome
import io.github.thake.soundnest.core.ui.immersive.shell.rememberImmersiveChromeState
import io.github.thake.soundnest.core.ui.look.LocalLook
import io.github.thake.soundnest.core.ui.look.LocalTopStatusBarConsumed
import io.github.thake.soundnest.feature.appearance.ui.LookOffer
import io.github.thake.soundnest.feature.appearance.ui.LookStep
import io.github.thake.soundnest.core.ui.classic.MainBottomNavigation
import io.github.thake.soundnest.core.ui.classic.MainNavigationRail
import io.github.thake.soundnest.core.ui.navigation.Routes
import io.github.thake.soundnest.feature.library.ui.classic.ArtistReleasesScreen
import io.github.thake.soundnest.feature.library.ui.classic.ArtistScreen
import io.github.thake.soundnest.feature.search.categories.ui.classic.CategoryScreen
import io.github.thake.soundnest.feature.library.ui.classic.ContextScreen
import io.github.thake.soundnest.feature.library.ui.ContextViewModel
import io.github.thake.soundnest.feature.spokenword.podcasts.ui.classic.EpisodeScreen
import io.github.thake.soundnest.feature.spokenword.podcasts.ui.classic.EpisodesScreen
import io.github.thake.soundnest.feature.equalizer.ui.classic.EqualizerScreen
import io.github.thake.soundnest.feature.library.folders.ui.classic.FolderScreen
import io.github.thake.soundnest.feature.library.hiddensongs.ui.classic.HiddenSongsScreen
import io.github.thake.soundnest.feature.home.ui.classic.HomeScreen
import io.github.thake.soundnest.feature.library.ui.classic.LibraryScreen
import io.github.thake.soundnest.feature.localfiles.ui.classic.LocalFilesScreen
import io.github.thake.soundnest.feature.localfiles.ui.classic.LocalPlaylistScreen
import io.github.thake.soundnest.feature.spokenword.podcasts.ui.classic.NewEpisodesScreen
import io.github.thake.soundnest.feature.home.whatsnew.ui.classic.NewReleasesScreen
import io.github.thake.soundnest.feature.player.ui.classic.PlayerScreen
import io.github.thake.soundnest.feature.spokenword.ui.classic.PodcastPlayerScreen
import io.github.thake.soundnest.feature.player.ui.classic.QueueScreen
import io.github.thake.soundnest.feature.recap.ui.classic.RecapScreen
import io.github.thake.soundnest.feature.recap.ui.classic.RecentlyPlayedScreen
import io.github.thake.soundnest.feature.accounts.ui.STEP_INTRO
import io.github.thake.soundnest.feature.accounts.ui.STEP_LOOK
import io.github.thake.soundnest.feature.accounts.ui.STEP_PERMISSIONS
import io.github.thake.soundnest.feature.accounts.ui.STEP_QUALITY
import io.github.thake.soundnest.feature.search.ui.classic.SearchScreen
import io.github.thake.soundnest.feature.settings.ui.classic.SettingsScreen
import io.github.thake.soundnest.feature.accounts.ui.SetupScreen
import io.github.thake.soundnest.feature.spokenword.ui.classic.ShowScreen
import io.github.thake.soundnest.feature.accounts.ui.WelcomeScreen
import io.github.thake.soundnest.feature.home.whatsnew.ui.classic.WhatsNewScreen
import io.github.thake.soundnest.core.ui.classic.SpotuiTheme
import io.github.thake.soundnest.feature.library.ui.ArtistViewModel
import io.github.thake.soundnest.feature.search.categories.ui.CategoryViewModel
import io.github.thake.soundnest.feature.spokenword.podcasts.ui.EpisodeViewModel
import io.github.thake.soundnest.feature.library.ui.LibraryViewModel
import io.github.thake.soundnest.feature.lyrics.ui.LyricsViewModel
import io.github.thake.soundnest.feature.search.ui.SearchViewModel
import io.github.thake.soundnest.feature.spokenword.ui.ShowViewModel
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
 * Complete headless documentation & UX screenshot catalog generator powered by Roborazzi + Robolectric native graphics.
 *
 * Skipped during regular `:app:testDebugUnitTest` runs so unit tests stay fast.
 * Run explicitly via:
 *   `./gradlew :app:generateDocScreenshots`
 *
 * Outputs deterministic PNG screenshots for every reachable screen, look (`Classic` & `Immersive`),
 * onboarding step, parental gate, player panel, settings sub-page, and bottom sheet/dialog into `docs/screenshots/`,
 * plus an auto-generated `docs/screenshots/README.md` catalog index.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xxhdpi", application = Application::class)
class DocScreenshotTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var hostActivity: ComponentActivity
    private lateinit var outputDir: File
    private lateinit var covers: Map<String, String>
    private var spotifyJob: kotlinx.coroutines.CompletableJob? = null
    private var lastAudibleShowPage: ShowPage? = null

    private val approvedPlaylists = setOf("ghibli", "roadtrip", "curious")
    private val approvedAlbums = setOf("planets", "dragon")
    private val approvedArtists = setOf("holst", "hisaishi")

    private data class ShotSpec(
        val id: String,
        val category: String,
        val relativePath: String,
        val title: String,
        val description: String,
        val look: Look,
        val prepare: (Application) -> Unit = {},
        val content: @Composable () -> Unit,
    )

    @Before
    fun setUp() {
        assumeTrue(
            "DocScreenshotTest runs only via :app:generateDocScreenshots",
            System.getProperty("soundnest.docScreenshots") == "true",
        )
        hostActivity = rule.activity
        val app = RuntimeEnvironment.getApplication()
        Graph::class.java.getDeclaredField("app").apply { isAccessible = true }.set(null, app)
        SongPlayer::class.java.getDeclaredField("boundState").apply { isAccessible = true }
            .set(SongPlayer, Graph.currentSongState)

        val dirProp = System.getProperty("soundnest.docScreenshotsDir")
        outputDir = (if (!dirProp.isNullOrBlank()) File(dirProp) else File("../docs/screenshots")).apply { mkdirs() }
        listOf("onboarding", "parental", "classic", "immersive", "sheets").forEach { sub ->
            File(outputDir, sub).mkdirs()
        }

        val coverDir = File(app.cacheDir, "doc_covers").apply { mkdirs() }
        covers = generateProceduralCovers(coverDir)

        bareHost()
        installRichFakeApi()
        seedAllStoresAndViewModels(app)
    }

    @After
    fun tearDown() {
        val app = RuntimeEnvironment.getApplication()
        SongPlayer.setSleepTimer(null)
        io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayer.resetForTests()
        io.github.thake.soundnest.feature.spokenword.audible.settings.AudibleCredentialStore.clear(app)
        io.github.thake.soundnest.feature.spokenword.audible.settings.AudibleCredentialStore.resetMemoryForTests()
        io.github.thake.soundnest.feature.spokenword.audible.data.AudibleCatalogCache.clear(app)
        io.github.thake.soundnest.feature.spokenword.audible.data.AudibleCatalogCache.resetMemoryForTests()
        io.github.thake.soundnest.feature.spokenword.audible.data.AudibleProgressStore.clearAll(app)
        io.github.thake.soundnest.feature.spokenword.audible.data.AudibleProgressStore.resetMemoryForTests()
        ParentSecurityManager.dismissPrompt()
        ParentSecurityManager.resetForTests(app)
        io.github.thake.soundnest.feature.parental.remote.settings.RemotePolicySession.resetForTests(app)
        io.github.thake.soundnest.feature.parental.remote.net.ChildRemoteServer.resetForTests()
        io.github.thake.soundnest.feature.parental.remote.settings.PairedDevicesStore.resetForTests(app)
        io.github.thake.soundnest.feature.parental.remote.settings.DeviceRoleStore.resetForTests(app)
        io.github.thake.soundnest.feature.parental.schedule.ParentalScheduleController.resetForTests()
        ParentalPolicyStore.resetForTests(app)
        TrustedNetworkGate.resetForTests(app)
        setLibraryGridView(app, false)
        LookPref.set(app, Look.CLASSIC)
        Api.HomeCache.clear()
        ContextViewModel.clearCache()
        ListenHistory.clear()
        LocalPlaylists.playlists.clear()
        RadioFavoritesStore.favorites.clear()
        spotifyJob?.cancel()
        spotifyJob = null
        runCatching {
            SoundNestRuntime::class.java.getDeclaredField("host").apply { isAccessible = true }
                .set(SoundNestRuntime, null)
        }
    }

    @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
    @Test
    fun generateCompleteCatalog() {
        val app = RuntimeEnvironment.getApplication()
        val shots = buildCompleteCatalog()

        var activeShot by mutableStateOf<ShotSpec?>(null)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val shot = activeShot
            if (shot != null) {
                key(shot.id) {
                    SpotuiTheme {
                        CompositionLocalProvider(LocalLook provides shot.look) {
                            ProvideWindowSize {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .background(if (shot.look == Look.IMMERSIVE) Color.Black else Color(0xFF121212)),
                                ) {
                                    shot.content()
                                }
                            }
                        }
                    }
                }
            } else {
                Box(Modifier.fillMaxSize().background(Color.Black))
            }
        }

        for (shot in shots) {
            // Clear any open dialog/sheet window from the previous shot before preparing the next one.
            activeShot = null
            ParentSecurityManager.dismissPrompt()
            ShadowLooper.idleMainLooper(40, TimeUnit.MILLISECONDS)
            rule.mainClock.advanceTimeBy(80)

            resetShotDefaults(app, shot.look)
            shot.prepare(app)

            activeShot = shot
            repeat(14) {
                Thread.sleep(25)
                ShadowLooper.idleMainLooper(80, TimeUnit.MILLISECONDS)
                rule.mainClock.advanceTimeBy(120)
            }
            ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)
            rule.mainClock.advanceTimeBy(400)

            val target = File(outputDir, shot.relativePath)
            target.parentFile?.mkdirs()
            captureScreenRoboImage(target)
            assertRenderedNonBlank(shot.relativePath, target)
        }

        activeShot = null
        ParentSecurityManager.dismissPrompt()
        ShadowLooper.idleMainLooper(60, TimeUnit.MILLISECONDS)
        rule.mainClock.advanceTimeBy(120)

        writeCatalogReadme(shots)
    }

    private fun assertRenderedNonBlank(relativePath: String, target: File) {
        assertTrue("Expected screenshot ${target.absolutePath} to exist", target.exists())
        val bmp = android.graphics.BitmapFactory.decodeFile(target.absolutePath)
        assertTrue("Expected screenshot $relativePath to decode as Bitmap", bmp != null)
        val firstPixel = bmp!!.getPixel(0, 0)
        var nonUniformPixels = 0
        val stepX = (bmp.width / 64).coerceAtLeast(1)
        val stepY = (bmp.height / 128).coerceAtLeast(1)
        var y = 0
        while (y < bmp.height) {
            var x = 0
            while (x < bmp.width) {
                if (bmp.getPixel(x, y) != firstPixel) nonUniformPixels++
                x += stepX
            }
            y += stepY
        }
        bmp.recycle()
        assertTrue(
            "Expected screenshot $relativePath to contain rendered UI pixels (found $nonUniformPixels non-background samples)",
            nonUniformPixels >= 5,
        )
    }

    private fun seedRuleLabels(app: Application) {
        RuleLabelStore.record(app, "playlist", "ghibli", "Studio Ghibli Piano", "Playlist • Parent Picks")
        RuleLabelStore.record(app, "playlist", "roadtrip", "Family Road Trip Sing-Along", "Playlist • Spotify")
        RuleLabelStore.record(app, "playlist", "curious", "Classical for Curious Minds", "Playlist • Mozart, Holst & Debussy")
        RuleLabelStore.record(app, "album", "planets", "The Planets, Op. 32", "Album • Gustav Holst")
        RuleLabelStore.record(app, "album", "dragon", "How to Train Your Dragon", "Album • John Powell")
        RuleLabelStore.record(app, "artist", "holst", "Gustav Holst", "Artist • Classical")
        RuleLabelStore.record(app, "artist", "hisaishi", "Joe Hisaishi", "Artist • Film Scores")
        RuleLabelStore.record(app, "track", "explicit1", "Late-Night Club Anthem", "Track • Blocked by Parent")
    }

    private fun resetShotDefaults(app: Application, look: Look) {
        LookPref.set(app, look)
        setLibraryGridView(app, false)
        SongPlayer.setSleepTimer(null)
        lastAudibleShowPage = null
        io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayer.resetForTests()
        SongPlayer.clearAudiobookContextsForTests()
        io.github.thake.soundnest.feature.spokenword.audible.settings.AudibleCredentialStore.clear(app)
        io.github.thake.soundnest.feature.spokenword.audible.settings.AudibleCredentialStore.resetMemoryForTests()
        io.github.thake.soundnest.feature.spokenword.audible.data.AudibleCatalogCache.clear(app)
        io.github.thake.soundnest.feature.spokenword.audible.data.AudibleCatalogCache.resetMemoryForTests()
        io.github.thake.soundnest.feature.spokenword.audible.data.AudibleProgressStore.clearAll(app)
        io.github.thake.soundnest.feature.spokenword.audible.data.AudibleProgressStore.resetMemoryForTests()
        ShadowLooper.idleMainLooper()
        io.github.thake.soundnest.feature.parental.remote.settings.RemotePolicySession.resetForTests(app)
        io.github.thake.soundnest.feature.parental.remote.net.ChildRemoteServer.resetForTests()
        io.github.thake.soundnest.feature.parental.remote.settings.PairedDevicesStore.resetForTests(app)
        io.github.thake.soundnest.feature.parental.remote.settings.DeviceRoleStore.resetForTests(app)
        io.github.thake.soundnest.feature.parental.schedule.ParentalScheduleController.resetForTests()
        ParentSecurityManager.resetForTests(app)
        ParentSecurityManager.setupInitialPin(app, "246810")
        ParentSecurityManager.lockNow(app)

        val homeLanFp = "lan-net:gw=192.168.1.1|sub=192.168.1.0/24|dns=192.168.1.1|dom=home.arpa"
        val homeLanLabel = "Home LAN (192.168.1.1 · home.arpa)"
        TrustedNetworkGate.resetForTests(app)
        TrustedNetworkGate.snapshotOverride = {
            NetworkSnapshot(
                onWifi = true,
                lanNetworkFingerprint = homeLanFp,
                lanNetworkLabel = homeLanLabel,
                discoveredLanDeviceIds = setOf("living-room-sonos"),
            )
        }
        TrustedNetworkGate.addTrustedLanFingerprint(app, homeLanFp, homeLanLabel)
        TrustedNetworkGate.addTrustedLanFingerprint(app, "living-room-sonos", "Speaker: Living Room Sonos")

        ParentalPolicyStore.replace(
            app,
            ParentalPolicy(
                filterMode = FilterMode.FILTERED_OPEN,
                blockExplicit = true,
                allowedPlaylistIds = approvedPlaylists,
                allowedAlbumIds = approvedAlbums,
                allowedArtistIds = approvedArtists,
                blockedTrackIds = setOf("explicit1"),
                blockedKeywords = setOf("explicit remix"),
                spokenWord = SpokenWordParentalPolicy(allowPodcasts = true, allowAudiobooks = true),
                lyrics = LyricsParentalPolicy(enabled = true),
                localFiles = LocalFilesParentalPolicy(enabled = true),
                radio = RadioParentalPolicy(enabled = true),
                playback = PlaybackParentalPolicy(allowAutoplayAndRadio = true),
                library = LibraryParentalPolicy(readOnlySpotifyLibrary = true),
                home = HomeParentalPolicy(
                    minimalHomeMode = false,
                    showFilterChips = true,
                    showWhatsNewBell = true,
                    showParentPicks = true,
                    showChartsAndTrending = false,
                ),
            ),
        )
        seedRuleLabels(app)

        seedFeedAndLibraryCache()
        seedMusicNowPlaying()
        seedDefaultMusicQueue()
    }

    private fun buildCompleteCatalog(): List<ShotSpec> {
        val shots = mutableListOf<ShotSpec>()
        fun add(
            category: String,
            relativePath: String,
            title: String,
            description: String,
            look: Look,
            prepare: (Application) -> Unit = {},
            content: @Composable () -> Unit,
        ) {
            shots += ShotSpec(
                id = relativePath,
                category = category,
                relativePath = relativePath,
                title = title,
                description = description,
                look = look,
                prepare = prepare,
                content = content,
            )
        }

        val immersivePlayerState = {
            ImmersivePlayerState(
                song = sampleTrackSongs().first(),
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
            )
        }

        val filteredOpenPrep: (Application) -> Unit = { app ->
            ParentalPolicyStore.replace(
                app,
                ParentalPolicy(
                    filterMode = FilterMode.FILTERED_OPEN,
                    blockExplicit = true,
                    allowedPlaylistIds = approvedPlaylists,
                    allowedAlbumIds = approvedAlbums,
                    allowedArtistIds = approvedArtists,
                    blockedTrackIds = setOf("explicit1"),
                    spokenWord = SpokenWordParentalPolicy(allowPodcasts = false, allowAudiobooks = false),
                    home = HomeParentalPolicy(
                        minimalHomeMode = false,
                        showParentPicks = true,
                        showChartsAndTrending = false,
                    ),
                ),
            )
            seedRuleLabels(app)
            seedFeedAndLibraryCache()
        }

        val strictMinimalPrep: (Application) -> Unit = { app ->
            ParentalPolicyStore.replace(
                app,
                ParentalPolicy(
                    filterMode = FilterMode.STRICT_ALLOWLIST,
                    blockExplicit = true,
                    allowedPlaylistIds = approvedPlaylists,
                    allowedAlbumIds = approvedAlbums,
                    allowedArtistIds = approvedArtists,
                    spokenWord = SpokenWordParentalPolicy(allowPodcasts = false, allowAudiobooks = false),
                    home = HomeParentalPolicy(
                        minimalHomeMode = true,
                        showFilterChips = false,
                        showWhatsNewBell = false,
                        showParentPicks = true,
                    ),
                ),
            )
            seedRuleLabels(app)
            seedFeedAndLibraryCache()
        }

        val parentAdminPrep: (Application) -> Unit = { app ->
            ParentSecurityManager.resetForTests(app)
            ParentSecurityManager.setupInitialPin(app, "246810")
            // setupInitialPin grants a 5-minute Admin Session
            ParentalPolicyStore.replace(
                app,
                ParentalPolicy(
                    filterMode = FilterMode.FILTERED_OPEN,
                    blockExplicit = true,
                    allowedPlaylistIds = approvedPlaylists,
                    allowedAlbumIds = approvedAlbums,
                    allowedArtistIds = approvedArtists,
                    blockedTrackIds = setOf("explicit1"),
                    blockedKeywords = setOf("explicit remix"),
                    spokenWord = SpokenWordParentalPolicy(allowPodcasts = false, allowAudiobooks = false),
                    lyrics = LyricsParentalPolicy(enabled = true),
                    library = LibraryParentalPolicy(readOnlySpotifyLibrary = true),
                    home = HomeParentalPolicy(
                        showChartsAndTrending = false,
                        showMixesAndMadeForYou = false,
                        showParentPicks = true,
                    ),
                ),
            )
            seedRuleLabels(app)
            seedFeedAndLibraryCache()
        }

        var remoteLoopbackChild: io.github.thake.soundnest.feature.parental.remote.settings.PairedChildRecord? = null
        val remoteHubPrep: (Application) -> Unit = { app ->
            parentAdminPrep(app)
            io.github.thake.soundnest.feature.parental.remote.settings.DeviceRoleStore.setDeviceName(app, "Emma's Tablet")
            io.github.thake.soundnest.feature.parental.remote.net.ChildRemoteServer.enableMdns = false
            val childPolicy = ParentalPolicyStore.current(app)
            io.github.thake.soundnest.feature.parental.remote.net.ChildRemoteServer.delegate =
                object : io.github.thake.soundnest.feature.parental.remote.net.ChildControlDelegate {
                    override fun getStatus(context: android.content.Context) =
                        io.github.thake.soundnest.feature.parental.remote.net.ChildStatusSnapshot(
                            childId = io.github.thake.soundnest.feature.parental.remote.settings.DeviceRoleStore.deviceId(context),
                            deviceName = "Emma's Tablet",
                            title = "Path of the Wind",
                            artist = "Joe Hisaishi",
                            album = "My Neighbor Totoro",
                            coverUrl = covers["ghibli"].orEmpty(),
                            isPlaying = true,
                            volumePercent = 45,
                            policyRevision = 3L,
                            filterMode = childPolicy.filterMode,
                        )
                    override fun executePlayback(
                        context: android.content.Context,
                        action: io.github.thake.soundnest.feature.parental.remote.net.RemoteRequest.PlaybackAction,
                    ) = getStatus(context)
                    override fun getPolicy(context: android.content.Context) =
                        io.github.thake.soundnest.feature.parental.remote.net.RemotePolicyBundle(
                            childId = io.github.thake.soundnest.feature.parental.remote.settings.DeviceRoleStore.deviceId(context),
                            deviceName = "Emma's Tablet",
                            policy = childPolicy,
                            ruleLabels = RuleLabelStore.all(context),
                            policyRevision = 3L,
                        )
                    override fun updatePolicy(
                        context: android.content.Context,
                        policy: ParentalPolicy,
                        ruleLabels: Map<String, io.github.thake.soundnest.feature.parental.settings.RuleLabel>,
                    ) = 4L to getStatus(context)
                    override fun renameDevice(context: android.content.Context, newName: String) = getStatus(context)
                    override fun searchCatalog(context: android.content.Context, query: String) = listOf(
                        io.github.thake.soundnest.feature.parental.remote.net.RemoteCatalogItem(
                            "track", "t_ghibli", "Path of the Wind", "Joe Hisaishi", covers["ghibli"].orEmpty(),
                        ),
                        io.github.thake.soundnest.feature.parental.remote.net.RemoteCatalogItem(
                            "playlist", "ghibli", "Studio Ghibli Piano", "Parent Picks", covers["ghibli"].orEmpty(),
                        ),
                    )
                    override fun getChildCatalog(context: android.content.Context) =
                        io.github.thake.soundnest.feature.parental.remote.net.RemoteResponse.ChildCatalog(
                            libraryItems = listOf(
                                io.github.thake.soundnest.feature.parental.remote.net.RemoteCatalogItem(
                                    "playlist", "ghibli", "Studio Ghibli Piano", "Parent Picks", covers["ghibli"].orEmpty(),
                                ),
                                io.github.thake.soundnest.feature.parental.remote.net.RemoteCatalogItem(
                                    "album", "planets", "The Planets Suite", "Gustav Holst", covers["planets"].orEmpty(),
                                ),
                                io.github.thake.soundnest.feature.parental.remote.net.RemoteCatalogItem(
                                    "album", "bedtime", "Calm Bedtime Classics", "Lullaby Orchestra", covers["bedtime"].orEmpty(),
                                ),
                                io.github.thake.soundnest.feature.parental.remote.net.RemoteCatalogItem(
                                    "track", "explicit1", "Late Night Explicit Remix", "Club DJ", covers["starwars"].orEmpty(),
                                ),
                            ),
                            audibleBooks = listOf(
                                io.github.thake.soundnest.feature.parental.remote.net.RemoteCatalogItem(
                                    "audible", "B08G9PRS1K", "Project Hail Mary", "Andy Weir", covers["planets"].orEmpty(),
                                ),
                                io.github.thake.soundnest.feature.parental.remote.net.RemoteCatalogItem(
                                    "audible", "B004V0725Y", "Momo", "Michael Ende", covers["bedtime"].orEmpty(),
                                ),
                            ),
                            audibleConnected = true,
                        )
                }
            val session = io.github.thake.soundnest.feature.parental.remote.net.ChildRemoteServer
                .startPairingSession(app, java.net.InetAddress.getLoopbackAddress()).getOrThrow()
            val paired = kotlinx.coroutines.runBlocking {
                io.github.thake.soundnest.feature.parental.remote.net.ParentRemoteClient
                    .pairWithChild(app, "127.0.0.1", session.port, session.code).getOrThrow()
            }
            remoteLoopbackChild = paired
            io.github.thake.soundnest.feature.parental.remote.settings.PairedDevicesStore.upsertPairedChild(
                context = app,
                childId = "child-leo-offline",
                displayName = "Leo's Phone",
                host = "127.0.0.1",
                port = 1,
                rawSecret = io.github.thake.soundnest.feature.parental.remote.crypto.RemoteCrypto.randomBytes(32),
            )
            io.github.thake.soundnest.feature.parental.remote.settings.DeviceRoleStore.setRole(
                app,
                io.github.thake.soundnest.feature.parental.remote.settings.DeviceRole.PARENT_CONTROLLER,
            )
        }
        val schedulePrep: (Application) -> Unit = { app ->
            parentAdminPrep(app)
            ParentalPolicyStore.update(app) {
                it.copy(
                    schedule = io.github.thake.soundnest.feature.parental.schedule.ScheduleParentalPolicy(
                        usageHoursEnabled = true,
                        weekdayWindow = io.github.thake.soundnest.feature.parental.schedule.DailyTimeWindow(7 * 60, 20 * 60),
                        weekendWindow = io.github.thake.soundnest.feature.parental.schedule.DailyTimeWindow(8 * 60, 21 * 60),
                        bedtimeInactivityEnabled = true,
                        bedtimeStartMinutes = 19 * 60 + 30,
                        bedtimeEndMinutes = 6 * 60,
                        inactivityTimeoutMinutes = 20,
                    ),
                )
            }
        }

        // ── 1. Hero Screenshots (README.md top-level files) ──
        add("Hero Overview", "immersive-home.png", "Immersive Home (Filtered Open)", "Curated shelves, Parent Picks, and floating glass mini-player.", Look.IMMERSIVE, filteredOpenPrep) {
            ImmersiveShell(Routes.Home.route, capped = false) { nav -> ImmersiveHomeScreen(nav) }
        }
        add("Hero Overview", "minimal-home.png", "Minimal Home (Strict Allowlist)", "Distraction-free home showing only parent-approved playlists and albums.", Look.IMMERSIVE, strictMinimalPrep) {
            ImmersiveShell(Routes.Home.route, capped = false) { nav -> ImmersiveHomeScreen(nav) }
        }
        add("Hero Overview", "parent-pin-gate.png", "Mandatory Parent PIN Gate", "Post-login security gate supporting a 4+ character PIN or password.", Look.CLASSIC, prepare = { app ->
            ParentSecurityManager.resetForTests(app)
        }) {
            ParentPinSetupScreen(onPinConfigured = {}, onSwitchOrLogout = {})
        }
        add("Hero Overview", "parent-dashboard.png", "Parental Controls Dashboard", "PIN-locked configuration for filter modes, allowlists, and feature gates.", Look.CLASSIC, parentAdminPrep) {
            ParentSettingsScreen(navController = rememberNavController())
        }
        add("Hero Overview", "kid-settings.png", "Kid-Accessible Settings", "Material 3 Settings hub with audio, display, library, system, and PIN-protected Parental Controls.", Look.CLASSIC) {
            ClassicShell("detail", capped = true) { nav -> SettingsScreen(nav) }
        }
        add("Hero Overview", "immersive-player.png", "Immersive Full-Screen Player", "Full-screen artwork mesh backdrop, scrubber, sleep timer, and transport controls.", Look.IMMERSIVE, prepare = {
            SongPlayer.setSleepTimer(30)
        }) {
            ImmersivePlayer(state = immersivePlayerState(), actions = ImmersivePlayerActions(), navController = rememberNavController())
        }
        add("Hero Overview", "parent-device-hub.png", "Parent Device Hub (Wi-Fi Remote)", "Dedicated Parent Device mode managing multiple paired children over encrypted LAN with live playback, volume, Sleep Timer, Pick / Block, and Pause All Kids.", Look.CLASSIC, remoteHubPrep) {
            io.github.thake.soundnest.feature.parental.remote.ui.ParentHubScreen(isDedicatedMode = true, onBack = {}, onSwitchToStandardMode = {})
        }
        add("Hero Overview", "remote-catalog-browser.png", "Remote Pick / Block Browser", "Browse and search a child's Spotify catalog, library, and Audible books from the Parent Device with 1-tap Pick / Block syncing live.", Look.CLASSIC, prepare = { app ->
            remoteHubPrep(app)
            val target = requireNotNull(remoteLoopbackChild)
            kotlinx.coroutines.runBlocking {
                io.github.thake.soundnest.feature.parental.remote.settings.RemotePolicySession.attach(app, target)
            }
        }) {
            io.github.thake.soundnest.feature.parental.remote.ui.RemoteCatalogBrowserScreen(
                child = requireNotNull(remoteLoopbackChild),
                onBack = {},
                initialTab = 1,
            )
        }
        add("Hero Overview", "listening-hours-schedule.png", "Listening Hours & Bedtime Sleep", "Weekday/weekend allowed listening windows and automatic bedtime inactivity fade-out sleep timer.", Look.CLASSIC, schedulePrep) {
            ParentSettingsScreen(navController = rememberNavController(), initialPage = ParentSettingsPage.ScheduleAndSleep)
        }

        // ── 2. Onboarding & Session States ──
        add("Onboarding & Session", "onboarding/01-welcome.png", "Welcome Screen", "First-launch landing screen before Spotify login.", Look.CLASSIC) {
            WelcomeScreen(onLogin = {})
        }
        add("Onboarding & Session", "onboarding/02-login-help-dialog.png", "Sign-In Help Dialog", "Troubleshooting modal explaining Spotify WebView sign-in options.", Look.CLASSIC) {
            WelcomeScreen(onLogin = {})
            LoginHelpDialog(onClose = {})
        }
        add("Onboarding & Session", "onboarding/03-connecting.png", "Connecting Session", "Startup loading screen while verifying the Spotify WebPlayer session.", Look.CLASSIC) {
            ConnectingScreen()
        }
        add("Onboarding & Session", "onboarding/04-login-problem.png", "Connection Problem", "Offline or session verification failure screen with retry action.", Look.CLASSIC) {
            LoginProblemScreen(onRetry = {})
        }
        add("Onboarding & Session", "onboarding/05-setup-intro.png", "Setup Step 1: Intro", "Initial onboarding welcome step after Spotify sign-in.", Look.CLASSIC) {
            SetupScreen(onDone = {}, initialStep = STEP_INTRO)
        }
        add("Onboarding & Session", "onboarding/06-setup-look-classic.png", "Setup Step 2: Look (Classic)", "Live look chooser previewing the Classic dark layout.", Look.CLASSIC) {
            SetupScreen(onDone = {}, initialStep = STEP_LOOK)
        }
        add("Onboarding & Session", "onboarding/07-setup-look-immersive.png", "Setup Step 2: Look (Immersive)", "Live look chooser previewing the Immersive mesh-glass layout.", Look.IMMERSIVE) {
            SetupScreen(onDone = {}, initialStep = STEP_LOOK)
        }
        add("Onboarding & Session", "onboarding/08-setup-quality.png", "Setup Step 3: Audio Quality", "Streaming quality selection for Wi-Fi and mobile data.", Look.CLASSIC) {
            SetupScreen(onDone = {}, initialStep = STEP_QUALITY)
        }
        add("Onboarding & Session", "onboarding/09-setup-permissions.png", "Setup Step 4: Permissions", "Notification & background playback permission prompt step.", Look.CLASSIC) {
            SetupScreen(onDone = {}, initialStep = STEP_PERMISSIONS)
        }

        // ── 3. Parental Controls & Security Gates ──
        add("Parental Controls & Security", "parental/01-pin-setup.png", "Initial Parent PIN / Password Setup", "Mandatory setup screen with 4+ character PIN or password and zero-permission Home LAN trust option.", Look.CLASSIC, prepare = { app ->
            ParentSecurityManager.resetForTests(app)
        }) {
            ParentPinSetupScreen(onPinConfigured = {}, onSwitchOrLogout = {})
        }
        add("Parental Controls & Security", "parental/02-pin-unlock-prompt.png", "Parent Unlock Prompt", "Modal PIN/password prompt with Forgot PIN recovery guidance.", Look.CLASSIC, prepare = { app ->
            ParentSecurityManager.runProtected(app, AppSurface.PARENT_CONTROLS) {}
        }) {
            ClassicShell("detail", capped = true) { nav -> SettingsScreen(nav) }
            ParentPinPromptHost()
        }
        add("Parental Controls & Security", "parental/03-pin-unlock-lockout.png", "Brute-Force Cooldown Lockout", "Exponential backoff lockout state after 5 failed PIN attempts.", Look.CLASSIC, prepare = { app ->
            repeat(5) { ParentSecurityManager.verifyPin(app, "wrong") }
            ParentSecurityManager.runProtected(app, AppSurface.PARENT_CONTROLS) {}
        }) {
            ClassicShell("detail", capped = true) { nav -> SettingsScreen(nav) }
            ParentPinPromptHost()
        }
        add("Parental Controls & Security", "parental/04-change-pin-dialog.png", "Change Parent PIN / Password Dialog", "Modal dialog to update the current parent PIN or password.", Look.CLASSIC, parentAdminPrep) {
            ParentSettingsScreen(navController = rememberNavController())
            ChangeParentPinDialog(onDismiss = {})
        }
        add("Parental Controls & Security", "parental/05-dashboard-top.png", "Dashboard: Parental Controls Hub", "Material 3 Parental Controls hub with admin session status and categorized management sections.", Look.CLASSIC, parentAdminPrep) {
            ParentSettingsScreen(navController = rememberNavController(), initialPage = ParentSettingsPage.Root)
        }
        add("Parental Controls & Security", "parental/06-dashboard-allowlists.png", "Dashboard: Content Allowlists & Rule Counts", "Approved playlists, albums, artists, and summary rule manager rows.", Look.CLASSIC, parentAdminPrep) {
            ParentSettingsScreen(navController = rememberNavController(), initialPage = ParentSettingsPage.FilterRules)
        }
        add("Parental Controls & Security", "parental/07-dashboard-home-sections.png", "Dashboard: Home Customization", "Minimal Home mode, Parent Picks shelf, and shelf visibility toggles.", Look.CLASSIC, parentAdminPrep) {
            ParentSettingsScreen(navController = rememberNavController(), initialPage = ParentSettingsPage.HomeSections)
        }
        add("Parental Controls & Security", "parental/08-dashboard-feature-gates.png", "Dashboard: Spotify Features & Library", "Spotify access controls for read-only library protection, autoplay, podcasts, audiobooks, Jam, and lyrics.", Look.CLASSIC, parentAdminPrep) {
            ParentSettingsScreen(navController = rememberNavController(), initialPage = ParentSettingsPage.Spotify)
        }
        add("Parental Controls & Security", "parental/08b-dashboard-local-radio.png", "Dashboard: Local Files & Live Radio", "Source gates for local audio files and Live Internet Radio.", Look.CLASSIC, prepare = { app ->
            parentAdminPrep(app)
            ParentalPolicyStore.update(app) {
                it.copy(
                    localFiles = LocalFilesParentalPolicy(enabled = true),
                    radio = RadioParentalPolicy(enabled = true),
                )
            }
        }) {
            ParentSettingsScreen(navController = rememberNavController(), initialPage = ParentSettingsPage.LocalAndRadio)
        }
        add("Parental Controls & Security", "parental/09-dashboard-speakers-network.png", "Dashboard: Speakers & Trusted Home LAN", "Zero-permission Home LAN & mDNS speaker fingerprints and Spotify Connect visibility rules.", Look.CLASSIC, parentAdminPrep) {
            ParentSettingsScreen(navController = rememberNavController(), initialPage = ParentSettingsPage.SpeakersNetwork)
        }
        add("Parental Controls & Security", "parental/10-dashboard-backup-restore.png", "Dashboard: Encrypted Config Backup", "Passphrase-encrypted parental policy export and import.", Look.CLASSIC, parentAdminPrep) {
            ParentSettingsScreen(navController = rememberNavController(), initialPage = ParentSettingsPage.SecurityBackup)
        }
        add("Parental Controls & Security", "parental/11-settings-admin-unlocked.png", "Settings (Admin Session Active)", "Floating global Parent Mode banner while the 5-minute Admin Session is unlocked.", Look.CLASSIC, parentAdminPrep) {
            ClassicShell("detail", capped = true) { nav -> SettingsScreen(nav) }
        }
        add("Parental Controls & Security", "parental/12-manage-content-rules-sheet.png", "Manage Content Rules Sheet", "Searchable Approved & Blocked item rules manager with human-readable titles.", Look.CLASSIC, parentAdminPrep) {
            ParentSettingsScreen(navController = rememberNavController(), initialPage = ParentSettingsPage.FilterRules)
            ManageContentRulesSheet(initialTabIsApproved = true, onDismiss = {})
        }
        val audibleAdminPrep: (Application) -> Unit = { app ->
            parentAdminPrep(app)
            io.github.thake.soundnest.feature.spokenword.audible.settings.AudibleCredentialStore.save(
                app,
                io.github.thake.soundnest.audible.AudibleSessionTokens(
                    marketplace = io.github.thake.soundnest.audible.AudibleMarketplace.DE,
                    deviceSerial = "DOCSHOTSERIAL1",
                    adpToken = "DOC_ADP_TOKEN",
                    devicePrivateKeyPem = "DOC_PEM",
                    refreshToken = "DOC_REFRESH",
                    accessToken = "DOC_ACCESS",
                    customerName = "Family Account",
                ),
            )
            io.github.thake.soundnest.feature.spokenword.audible.data.AudibleCatalogCache.saveBooks(
                app,
                listOf(
                    io.github.thake.soundnest.audible.AudibleBook(
                        asin = "B08G9PRS1K",
                        title = "Project Hail Mary",
                        authors = listOf("Andy Weir"),
                        narrators = listOf("Ray Porter"),
                        runtimeLengthMs = 58_200_000L,
                        coverUrl = covers["planets"].orEmpty(),
                    ),
                    io.github.thake.soundnest.audible.AudibleBook(
                        asin = "B004V0725Y",
                        title = "Momo",
                        authors = listOf("Michael Ende"),
                        narrators = listOf("Rufus Beck"),
                        runtimeLengthMs = 31_800_000L,
                        coverUrl = covers["bedtime"].orEmpty(),
                    ),
                    io.github.thake.soundnest.audible.AudibleBook(
                        asin = "B09adult99",
                        title = "Midnight Crime Casebook",
                        authors = listOf("H. Detective"),
                        narrators = listOf("A. Reader"),
                        runtimeLengthMs = 42_000_000L,
                        coverUrl = covers["starwars"].orEmpty(),
                    ),
                ),
            )
            ParentalPolicyStore.update(app) {
                it.copy(
                    spokenWord = it.spokenWord.copy(
                        allowAudiobooks = true,
                        audible = AudibleParentalPolicy(
                            allowedAsins = setOf("B08G9PRS1K", "B004V0725Y"),
                            pinnedAsins = setOf("B08G9PRS1K"),
                        ),
                    ),
                )
            }
        }
        add("Parental Controls & Security", "parental/13-audible-account-controls.png", "Dashboard: Audible Audiobooks", "Connected Audible marketplace status, library sync, and approved/pinned book counts.", Look.CLASSIC, audibleAdminPrep) {
            ParentSettingsScreen(navController = rememberNavController(), initialPage = ParentSettingsPage.Audible)
        }
        add("Parental Controls & Security", "parental/14-audible-parent-picker-sheet.png", "Audible Parent Picker Sheet", "Explicit per-book Approve and separate Pin to Home controls over the parent's owned Audible library.", Look.CLASSIC, audibleAdminPrep) {
            ParentSettingsScreen(navController = rememberNavController(), initialPage = ParentSettingsPage.Audible)
            io.github.thake.soundnest.feature.spokenword.audible.ui.AudiblePickerSheet(onDismiss = {})
        }
        add("Parental Controls & Security", "parental/15-playlist-inline-block-pill.png", "In-App Blocking: Playlist Header Pill", "1-tap Block Playlist pill and floating Parent Mode banner directly on a regular playlist detail page (zero Spotify URIs needed).", Look.IMMERSIVE, parentAdminPrep) {
            ImmersiveShell("detail", capped = true) { nav ->
                ImmersiveContextScreen(nav, type = "playlist", id = "ghibli")
            }
        }
        add("Parental Controls & Security", "parental/16-song-options-parent-block.png", "In-App Blocking: Song Options Menu", "Song context menu while Parent Mode is unlocked, exposing 1-tap Block Song, Block Artist, and Block Album actions.", Look.IMMERSIVE, parentAdminPrep) {
            val ctx = LocalContext.current
            ImmersiveShell("detail", capped = true) { nav ->
                ImmersiveContextScreen(nav, type = "playlist", id = "ghibli")
                ImmersiveSongOptionsSheet(song = sampleTrackSongs()[1], navController = nav, context = ctx, onDismiss = {})
            }
        }
        add("Parental Controls & Security", "parental/17-quick-block-sheet.png", "In-App Blocking: Home Card Quick-Block Sheet", "Home card overflow buttons and 1-tap ParentQuickBlockSheet while Parent Mode is unlocked.", Look.IMMERSIVE, parentAdminPrep) {
            ImmersiveShell(Routes.Home.route, capped = false) { nav ->
                ImmersiveHomeScreen(nav)
                ParentQuickBlockSheet(
                    target = ParentBlockTarget(
                        kind = ParentRuleKind.PLAYLIST,
                        id = "ghibli",
                        title = "Studio Ghibli Piano",
                        subtitle = "Playlist • Parent Picks",
                        coverUri = covers.getValue("ghibli"),
                    ),
                    onDismiss = {},
                )
            }
        }
        add("Parental Controls & Security", "parental/18-dashboard-schedule.png", "Dashboard: Listening Hours & Bedtime Sleep", "Weekday/weekend listening hour windows and automatic bedtime inactivity fade-out sleep timer.", Look.CLASSIC, schedulePrep) {
            ParentSettingsScreen(navController = rememberNavController(), initialPage = ParentSettingsPage.ScheduleAndSleep)
        }
        add("Parental Controls & Security", "parental/19-usage-hours-lock.png", "Allowed Listening Hours Lock Overlay", "Calm full-screen lock shown outside allowed listening hours with next allowed start time and Parent Unlock / Extension action.", Look.CLASSIC, prepare = { app ->
            ParentalPolicyStore.update(app) {
                it.copy(
                    schedule = io.github.thake.soundnest.feature.parental.schedule.ScheduleParentalPolicy(
                        usageHoursEnabled = true,
                        weekdayWindow = io.github.thake.soundnest.feature.parental.schedule.DailyTimeWindow(7 * 60, 20 * 60),
                        weekendWindow = io.github.thake.soundnest.feature.parental.schedule.DailyTimeWindow(8 * 60, 21 * 60),
                    ),
                )
            }
            io.github.thake.soundnest.feature.parental.schedule.ParentalScheduleController.nowZonedProvider = {
                java.time.ZonedDateTime.of(2026, 11, 20, 20, 30, 0, 0, java.time.ZoneId.of("UTC"))
            }
            io.github.thake.soundnest.feature.parental.schedule.ParentalScheduleController.evaluateNow(app)
        }) {
            io.github.thake.soundnest.feature.parental.schedule.ui.UsageHoursLockOverlay()
        }
        add("Parental Controls & Security", "parental/20-family-remote-pairing.png", "Family Remote Control & Pairing", "Child device pairing screen with paired parents list, QR code, and 6-digit one-time pairing code.", Look.CLASSIC, prepare = { app ->
            parentAdminPrep(app)
            io.github.thake.soundnest.feature.parental.remote.settings.DeviceRoleStore.setDeviceName(app, "Emma's Tablet")
            io.github.thake.soundnest.feature.parental.remote.net.ChildRemoteServer.enableMdns = false
            io.github.thake.soundnest.feature.parental.remote.settings.PairedDevicesStore.upsertPairedParent(
                context = app,
                parentId = "parent-mom",
                parentName = "Mom's Phone",
                rawSecret = io.github.thake.soundnest.feature.parental.remote.crypto.RemoteCrypto.randomBytes(32),
            )
            io.github.thake.soundnest.feature.parental.remote.net.ChildRemoteServer
                .startPairingSession(app, java.net.InetAddress.getLoopbackAddress())
        }) {
            ParentSettingsScreen(navController = rememberNavController(), initialPage = ParentSettingsPage.FamilyRemote)
        }
        add("Parental Controls & Security", "parental/21-parent-device-hub.png", "Parent Device Mode: Multi-Child Hub", "Dedicated Parent Device hub controlling multiple paired children over encrypted home Wi-Fi.", Look.CLASSIC, remoteHubPrep) {
            io.github.thake.soundnest.feature.parental.remote.ui.ParentHubScreen(isDedicatedMode = true, onBack = {}, onSwitchToStandardMode = {})
        }
        add("Parental Controls & Security", "parental/22-remote-catalog-browser.png", "Parent Device Mode: Remote Pick / Block", "Remote Spotify Search, Child Library, and Audible catalog browser with instant Pick / Block syncing.", Look.CLASSIC, prepare = { app ->
            remoteHubPrep(app)
            val target = requireNotNull(remoteLoopbackChild)
            kotlinx.coroutines.runBlocking {
                io.github.thake.soundnest.feature.parental.remote.settings.RemotePolicySession.attach(app, target)
            }
        }) {
            io.github.thake.soundnest.feature.parental.remote.ui.RemoteCatalogBrowserScreen(
                child = requireNotNull(remoteLoopbackChild),
                onBack = {},
                initialTab = 1,
            )
        }

        // ── 4. Classic Look — Complete Screen & Settings Catalog ──
        add("Classic Look", "classic/01-home-filtered-open.png", "Home (Filtered Open)", "Classic Home with filter chips, shortcuts, and family-friendly shelves.", Look.CLASSIC, filteredOpenPrep) {
            ClassicShell(Routes.Home.route, capped = false) { nav -> HomeScreen(nav) }
        }
        add("Classic Look", "classic/02-home-strict-minimal.png", "Home (Strict Minimal)", "Classic Home in Strict Allowlist + Minimal Home mode.", Look.CLASSIC, strictMinimalPrep) {
            ClassicShell(Routes.Home.route, capped = false) { nav -> HomeScreen(nav) }
        }
        add("Classic Look", "classic/03-search-browse-idle.png", "Search (Browse Categories)", "Classic Search idle state displaying Spotify genre browse cards.", Look.CLASSIC, prepare = {
            seedSearchIdle()
        }) {
            ClassicShell(Routes.Search.route, capped = false) { nav -> SearchScreen(nav) }
        }
        add("Classic Look", "classic/04-search-results.png", "Search (Results)", "Classic Search with query, filter chips, and mixed track/artist/album hits.", Look.CLASSIC, prepare = {
            seedSearchPopulated()
        }) {
            ClassicShell(Routes.Search.route, capped = false) { nav -> SearchScreen(nav) }
        }
        add("Classic Look", "classic/05-library-list.png", "Library (List View)", "Classic Your Library in list mode with playlists, albums, and artists.", Look.CLASSIC) {
            ClassicShell(Routes.Library.route, capped = false) { nav -> LibraryScreen(nav) }
        }
        add("Classic Look", "classic/06-library-grid.png", "Library (Grid View)", "Classic Your Library in cover grid mode.", Look.CLASSIC, prepare = { app ->
            setLibraryGridView(app, true)
        }) {
            ClassicShell(Routes.Library.route, capped = false) { nav -> LibraryScreen(nav) }
        }
        add("Classic Look", "classic/07-playlist-detail.png", "Playlist Detail", "Classic Playlist screen with cover header, play/shuffle controls, and tracks.", Look.CLASSIC) {
            ClassicShell("detail", capped = true) { nav -> ContextScreen(nav, type = "playlist", id = "ghibli") }
        }
        add("Classic Look", "classic/08-album-detail.png", "Album Detail", "Classic Album screen with release metadata, tracklist, and copyright footer.", Look.CLASSIC) {
            ClassicShell("detail", capped = true) { nav -> ContextScreen(nav, type = "album", id = "planets") }
        }
        add("Classic Look", "classic/09-liked-songs.png", "Liked Songs", "Classic Liked Songs collection view.", Look.CLASSIC) {
            ClassicShell("detail", capped = true) { nav -> ContextScreen(nav, type = "liked", id = "") }
        }
        add("Classic Look", "classic/10-artist-overview.png", "Artist Overview", "Classic Artist page with monthly listeners, top tracks, releases, and related artists.", Look.CLASSIC) {
            ClassicShell("detail", capped = true) { nav -> ArtistScreen(nav, "Gustav Holst", "holst") }
        }
        add("Classic Look", "classic/11-artist-releases.png", "Artist Discography", "Classic Artist Releases screen with spotlight and Album/Single filter chips.", Look.CLASSIC) {
            ClassicShell("detail", capped = true) { nav -> ArtistReleasesScreen(nav, "Gustav Holst", "holst") }
        }
        add("Classic Look", "classic/12-category-browse.png", "Genre / Category Browse", "Classic Category playlist grid.", Look.CLASSIC) {
            ClassicShell(Routes.Category.route, capped = false) { nav -> CategoryScreen(nav, genre = "classical", title = "Classical & Instrumental") }
        }
        add("Classic Look", "classic/13-podcast-show.png", "Podcast Show", "Classic Podcast Show page with episode progress bars and filter.", Look.CLASSIC, prepare = {
            seedShowPodcast()
        }) {
            ClassicShell("detail", capped = true) { nav -> ShowScreen(nav, showId = "brainson", showName = "Brains On! Science Podcast") }
        }
        add("Classic Look", "classic/14-audiobook-detail.png", "Audiobook Detail", "Classic Audiobook page with author/narrator byline and chapters.", Look.CLASSIC, prepare = {
            seedShowAudiobook()
        }) {
            ClassicShell("detail", capped = true) { nav -> ShowScreen(nav, showId = "littleprince", showName = "The Little Prince") }
        }
        add("Classic Look", "classic/15-podcast-episode.png", "Podcast Episode Detail", "Classic single Episode page with show link, progress, and description.", Look.CLASSIC) {
            ClassicShell("detail", capped = true) { nav -> EpisodeScreen(nav, episodeId = "ep1", fallbackName = "Why Do Stars Twinkle?") }
        }
        add("Classic Look", "classic/16-your-episodes.png", "Your Episodes", "Classic saved podcast episodes list.", Look.CLASSIC) {
            ClassicShell("detail", capped = true) { nav -> EpisodesScreen(nav) }
        }
        add("Classic Look", "classic/17-new-episodes.png", "New Episodes", "Classic unplayed episodes from followed shows.", Look.CLASSIC) {
            ClassicShell("detail", capped = true) { nav -> NewEpisodesScreen(nav) }
        }
        add("Classic Look", "classic/18-whats-new.png", "What's New Feed", "Classic What's New feed with New and Earlier sections.", Look.CLASSIC) {
            ClassicShell("detail", capped = true) { nav -> WhatsNewScreen(nav) }
        }
        add("Classic Look", "classic/19-new-releases.png", "New Releases Grid", "Classic New Releases grid from followed artists.", Look.CLASSIC) {
            ClassicShell(Routes.NewReleases.route, capped = false) { nav -> NewReleasesScreen(nav) }
        }
        add("Classic Look", "classic/20-recently-played.png", "Recently Played", "Classic account play history grouped by context and day.", Look.CLASSIC) {
            ClassicShell("detail", capped = true) { nav -> RecentlyPlayedScreen(nav) }
        }
        add("Classic Look", "classic/21-recap-spotify-stats.png", "Listening Stats (Spotify Tab)", "Classic Listening Stats showing account top tracks and artists.", Look.CLASSIC) {
            ClassicShell("detail", capped = true) { nav -> RecapScreen(nav, initialTab = 0) }
        }
        add("Classic Look", "classic/22-recap-phone-stats.png", "Listening Stats (This Phone Tab)", "Classic on-device listening minutes, weekly chart, and top tracks.", Look.CLASSIC) {
            ClassicShell("detail", capped = true) { nav -> RecapScreen(nav, initialTab = 1) }
        }
        add("Classic Look", "classic/23-library-folder.png", "Library Folder", "Classic Spotify library folder view.", Look.CLASSIC) {
            ClassicShell("detail", capped = true) { nav -> FolderScreen(nav, uri = "spotify:folder:kids", initialName = "Bedtime & Calm") }
        }
        add("Classic Look", "classic/24-local-files.png", "Local Files", "Classic Local Files screen prompting for on-device audio access.", Look.CLASSIC) {
            ClassicShell("detail", capped = true) { nav -> LocalFilesScreen(nav) }
        }
        add("Classic Look", "classic/25-local-playlist.png", "Local Playlist", "Classic on-device playlist screen.", Look.CLASSIC) {
            ClassicShell("detail", capped = true) { nav -> LocalPlaylistScreen(nav, id = "local-bedtime") }
        }
        add("Classic Look", "classic/26-live-radio.png", "Live Radio", "Classic Live Radio screen with favorite stations and search.", Look.CLASSIC) {
            ClassicShell("detail", capped = true) { nav -> LiveRadioScreen(nav) }
        }
        add("Classic Look", "classic/27-hidden-songs.png", "Hidden Songs", "Classic Hidden Songs list.", Look.CLASSIC) {
            ClassicShell("detail", capped = true) { nav -> HiddenSongsScreen(nav) }
        }
        add("Classic Look", "classic/28-equalizer.png", "Equalizer & Audio Effects", "Classic 5-band equalizer, presets, playback speed, and silence skip.", Look.CLASSIC) {
            ClassicShell("detail", capped = true) { nav -> EqualizerScreen(nav) }
        }
        add("Classic Look", "classic/29-player-music.png", "Full-Screen Music Player", "Classic full-screen music player with inline lyrics card below.", Look.CLASSIC) {
            PlayerScreen(rememberNavController())
        }
        add("Classic Look", "classic/30-player-podcast.png", "Full-Screen Podcast Player", "Classic podcast player with 15s skip controls, speed, and sleep timer.", Look.CLASSIC, prepare = {
            seedEpisodeNowPlaying()
        }) {
            PodcastPlayerScreen(rememberNavController())
        }
        add("Classic Look", "classic/31-lyrics-fullscreen.png", "Full-Screen Synced Lyrics", "Classic full-screen time-synced lyrics view.", Look.CLASSIC) {
            LyricsScreen(
                title = "Clair de Lune",
                artist = "Claude Debussy",
                album = "Suite bergamasque",
                accentColor = Color(0xFF3B82F6),
                onClose = {},
            )
        }
        add("Classic Look", "classic/32-queue.png", "Play Queue", "Classic Queue screen with Now Playing, Next in Queue, and Next Up.", Look.CLASSIC) {
            QueueScreen(rememberNavController())
        }
        add("Classic Look", "classic/33-settings-playback.png", "Settings: Playback", "Classic Settings Playback sub-page with streaming quality, queue & controls.", Look.CLASSIC) {
            ClassicShell("detail", capped = true) { nav -> SettingsScreen(nav, initialTab = 0) }
        }
        add("Classic Look", "classic/34-settings-transitions.png", "Settings: Transitions & AutoMix", "Classic Settings Transitions sub-page (crossfade & AutoMix).", Look.CLASSIC) {
            ClassicShell("detail", capped = true) { nav -> SettingsScreen(nav, initialTab = 0, initialSub = "transitions") }
        }
        add("Classic Look", "classic/35-settings-lyrics.png", "Settings: Lyrics", "Classic Settings Lyrics sub-page.", Look.CLASSIC) {
            ClassicShell("detail", capped = true) { nav -> SettingsScreen(nav, initialTab = 1) }
        }
        add("Classic Look", "classic/36-settings-storage.png", "Settings: Storage", "Classic Settings Storage sub-page (image/audio & AutoMix caches).", Look.CLASSIC) {
            ClassicShell("detail", capped = true) { nav -> SettingsScreen(nav, initialTab = 2) }
        }
        add("Classic Look", "classic/37-settings-appearance.png", "Settings: Display & Appearance", "Classic Settings Display sub-page (Look, accent color, AMOLED theme, language).", Look.CLASSIC) {
            ClassicShell("detail", capped = true) { nav -> SettingsScreen(nav, initialTab = 3) }
        }
        add("Classic Look", "classic/38-settings-about.png", "Settings: App & Troubleshooting", "Classic Settings App sub-page (player repair, diagnostics, About SoundNest).", Look.CLASSIC) {
            ClassicShell("detail", capped = true) { nav -> SettingsScreen(nav, initialTab = 4) }
        }
        add("Classic Look", "classic/39-settings-accounts.png", "Settings: Accounts (PIN-Unlocked)", "Classic Settings Accounts sub-page after parent PIN unlock.", Look.CLASSIC, parentAdminPrep) {
            ClassicShell("detail", capped = true) { nav -> SettingsScreen(nav, initialTab = 5) }
        }
        add("Classic Look", "classic/40-settings-library.png", "Settings: Library", "Classic Settings Library sub-page.", Look.CLASSIC) {
            ClassicShell("detail", capped = true) { nav -> SettingsScreen(nav, initialTab = 6) }
        }
        add("Classic Look", "classic/41-about-soundnest.png", "Menu: About SoundNest", "Full-screen Classic About page with GPL-3.0 credits and upstream attributions.", Look.CLASSIC) {
            ClassicShell("detail", capped = true) { nav -> AboutScreen(nav) }
        }
        add("Classic Look", "classic/42-switch-account.png", "Menu: Switch Account (PIN-Unlocked)", "Full-screen Classic Switch Account page opened from the menu drawer.", Look.CLASSIC, parentAdminPrep) {
            ClassicShell("detail", capped = true) { nav -> AccountsScreen(nav) }
        }
        add("Classic Look", "classic/43-audible-audiobook-chapters.png", "Audible Audiobook Chapters", "Classic Audible book page showing flattened chapters, local progress, and mini-player state.", Look.CLASSIC, prepare = { app ->
            seedAudibleChapterView(app)
        }) {
            ClassicShell("detail", capped = true) { nav -> ShowScreen(nav, showId = "audible:B08G9PRS1K", showName = "Project Hail Mary") }
        }
        add("Classic Look", "classic/44-player-audible-chapter.png", "Full-Screen Audiobook Player", "Classic audiobook player while streaming an Audible audiobook chapter.", Look.CLASSIC, prepare = { app ->
            seedAudibleChapterView(app)
        }) {
            AudiobookPlayerScreen(rememberNavController())
        }

        // ── 5. Immersive Look — Complete Screen, Player & Settings Catalog ──
        add("Immersive Look", "immersive/01-home-filtered-open.png", "Home (Filtered Open)", "Immersive Home with glass pills, shortcuts, and curated shelves.", Look.IMMERSIVE, filteredOpenPrep) {
            ImmersiveShell(Routes.Home.route, capped = false) { nav -> ImmersiveHomeScreen(nav) }
        }
        add("Immersive Look", "immersive/02-home-strict-minimal.png", "Home (Strict Minimal)", "Immersive Home in Strict Allowlist + Minimal Home mode.", Look.IMMERSIVE, strictMinimalPrep) {
            ImmersiveShell(Routes.Home.route, capped = false) { nav -> ImmersiveHomeScreen(nav) }
        }
        add("Immersive Look", "immersive/03-search-browse-idle.png", "Search (Browse Categories)", "Immersive Search idle state with large title and genre cards.", Look.IMMERSIVE, prepare = {
            seedSearchIdle()
        }) {
            ImmersiveShell(Routes.Search.route, capped = false) { nav -> ImmersiveSearchScreen(nav) }
        }
        add("Immersive Look", "immersive/04-search-results.png", "Search (Results)", "Immersive Search with glass filter pills and mixed hits.", Look.IMMERSIVE, prepare = {
            seedSearchPopulated()
        }) {
            ImmersiveShell(Routes.Search.route, capped = false) { nav -> ImmersiveSearchScreen(nav) }
        }
        add("Immersive Look", "immersive/05-library-list.png", "Library (List View)", "Immersive Library list with filter pills and floating glass chrome.", Look.IMMERSIVE) {
            ImmersiveShell(Routes.Library.route, capped = false) { nav -> ImmersiveLibraryScreen(nav) }
        }
        add("Immersive Look", "immersive/06-library-grid.png", "Library (Grid View)", "Immersive Library cover grid view.", Look.IMMERSIVE, prepare = { app ->
            setLibraryGridView(app, true)
        }) {
            ImmersiveShell(Routes.Library.route, capped = false) { nav -> ImmersiveLibraryScreen(nav) }
        }
        add("Immersive Look", "immersive/07-playlist-detail.png", "Playlist Detail", "Immersive Playlist page with artwork glow and glass action buttons.", Look.IMMERSIVE) {
            ImmersiveShell("detail", capped = true) { nav -> ImmersiveContextScreen(nav, type = "playlist", id = "ghibli") }
        }
        add("Immersive Look", "immersive/08-album-detail.png", "Album Detail", "Immersive Album page with centered cover art, metadata, and numbered tracks.", Look.IMMERSIVE) {
            ImmersiveShell("detail", capped = true) { nav -> ImmersiveContextScreen(nav, type = "album", id = "planets") }
        }
        add("Immersive Look", "immersive/09-liked-songs.png", "Liked Songs", "Immersive Liked Songs collection view.", Look.IMMERSIVE) {
            ImmersiveShell("detail", capped = true) { nav -> ImmersiveContextScreen(nav, type = "liked", id = "") }
        }
        add("Immersive Look", "immersive/10-artist-overview.png", "Artist Overview", "Immersive Artist page with full-bleed hero header, top tracks, and releases.", Look.IMMERSIVE) {
            ImmersiveShell("detail", capped = true) { nav -> ImmersiveArtistScreen(nav, "Gustav Holst", "holst") }
        }
        add("Immersive Look", "immersive/11-artist-releases.png", "Artist Discography", "Immersive Artist Releases screen.", Look.IMMERSIVE) {
            ImmersiveShell("detail", capped = true) { nav -> ImmersiveArtistReleasesScreen(nav, "Gustav Holst", "holst") }
        }
        add("Immersive Look", "immersive/12-category-browse.png", "Genre / Category Browse", "Immersive Category playlist grid.", Look.IMMERSIVE) {
            ImmersiveShell(Routes.Category.route, capped = false) { nav -> ImmersiveCategoryScreen(nav, genre = "classical", title = "Classical & Instrumental") }
        }
        add("Immersive Look", "immersive/13-podcast-show.png", "Podcast Show", "Immersive Podcast Show page with cover glow and episode rows.", Look.IMMERSIVE, prepare = {
            seedShowPodcast()
        }) {
            ImmersiveShell("detail", capped = true) { nav -> ImmersiveShowScreen(nav, showId = "brainson", showName = "Brains On! Science Podcast") }
        }
        add("Immersive Look", "immersive/14-audiobook-detail.png", "Audiobook Detail", "Immersive Audiobook page with chapters and narrator metadata.", Look.IMMERSIVE, prepare = {
            seedShowAudiobook()
        }) {
            ImmersiveShell("detail", capped = true) { nav -> ImmersiveShowScreen(nav, showId = "littleprince", showName = "The Little Prince") }
        }
        add("Immersive Look", "immersive/15-podcast-episode.png", "Podcast Episode Detail", "Immersive single Episode detail page.", Look.IMMERSIVE) {
            ImmersiveShell("detail", capped = true) { nav -> ImmersiveEpisodeScreen(nav, episodeId = "ep1", fallbackName = "Why Do Stars Twinkle?") }
        }
        add("Immersive Look", "immersive/16-your-episodes.png", "Your Episodes", "Immersive saved podcast episodes screen.", Look.IMMERSIVE) {
            ImmersiveShell("detail", capped = true) { nav -> ImmersiveEpisodesScreen(nav) }
        }
        add("Immersive Look", "immersive/17-new-episodes.png", "New Episodes", "Immersive unplayed episodes from followed shows.", Look.IMMERSIVE) {
            ImmersiveShell("detail", capped = true) { nav -> ImmersiveNewEpisodesScreen(nav) }
        }
        add("Immersive Look", "immersive/18-whats-new.png", "What's New Feed", "Immersive What's New feed.", Look.IMMERSIVE) {
            ImmersiveShell("detail", capped = true) { nav -> ImmersiveWhatsNewScreen(nav) }
        }
        add("Immersive Look", "immersive/19-new-releases.png", "New Releases Grid", "Immersive New Releases grid.", Look.IMMERSIVE) {
            ImmersiveShell(Routes.NewReleases.route, capped = false) { nav -> ImmersiveNewReleasesScreen(nav) }
        }
        add("Immersive Look", "immersive/20-recently-played.png", "Recently Played", "Immersive Recently Played screen with expandable context groups.", Look.IMMERSIVE) {
            ImmersiveShell("detail", capped = true) { nav -> ImmersiveRecentlyPlayedScreen(nav) }
        }
        add("Immersive Look", "immersive/21-recap-spotify-stats.png", "Listening Stats (Spotify Tab)", "Immersive Listening Stats with segmented control on Spotify rankings.", Look.IMMERSIVE) {
            ImmersiveShell("detail", capped = true) { nav -> ImmersiveRecapScreen(nav, initialTab = 0) }
        }
        add("Immersive Look", "immersive/22-recap-phone-stats.png", "Listening Stats (This Phone Tab)", "Immersive on-device listening stats and weekly bar chart.", Look.IMMERSIVE) {
            ImmersiveShell("detail", capped = true) { nav -> ImmersiveRecapScreen(nav, initialTab = 1) }
        }
        add("Immersive Look", "immersive/23-library-folder.png", "Library Folder", "Immersive Spotify library folder screen.", Look.IMMERSIVE) {
            ImmersiveShell("detail", capped = true) { nav -> ImmersiveFolderScreen(nav, uri = "spotify:folder:kids", initialName = "Bedtime & Calm") }
        }
        add("Immersive Look", "immersive/24-local-files.png", "Local Files", "Immersive Local Files permission screen.", Look.IMMERSIVE) {
            ImmersiveShell("detail", capped = true) { nav -> ImmersiveLocalFilesScreen(nav) }
        }
        add("Immersive Look", "immersive/25-local-playlist.png", "Local Playlist", "Immersive on-device playlist screen.", Look.IMMERSIVE) {
            ImmersiveShell("detail", capped = true) { nav -> ImmersiveLocalPlaylistScreen(nav, id = "local-bedtime") }
        }
        add("Immersive Look", "immersive/26-live-radio.png", "Live Radio", "Immersive Live Radio station browser and favorites.", Look.IMMERSIVE) {
            ImmersiveShell("detail", capped = true) { nav -> ImmersiveLiveRadioScreen(nav) }
        }
        add("Immersive Look", "immersive/27-hidden-songs.png", "Hidden Songs", "Immersive Hidden Songs screen.", Look.IMMERSIVE) {
            ImmersiveShell("detail", capped = true) { nav -> ImmersiveHiddenSongsScreen(nav) }
        }
        add("Immersive Look", "immersive/28-equalizer.png", "Equalizer & Audio Effects", "Immersive inset-grouped Equalizer & playback effects screen.", Look.IMMERSIVE) {
            ImmersiveShell("detail", capped = true) { nav -> ImmersiveEqualizerScreen(nav) }
        }
        add("Immersive Look", "immersive/29-player-music.png", "Full-Screen Music Player", "Immersive music player with dynamic artwork mesh backdrop.", Look.IMMERSIVE, prepare = {
            SongPlayer.setSleepTimer(30)
        }) {
            ImmersivePlayer(state = immersivePlayerState(), actions = ImmersivePlayerActions(), navController = rememberNavController(), initialPanel = Panel.NONE)
        }
        add("Immersive Look", "immersive/30-player-lyrics-panel.png", "Player: Synced Lyrics Panel", "Immersive player with inline swept karaoke/synced lyrics panel open.", Look.IMMERSIVE) {
            ImmersivePlayer(state = immersivePlayerState(), actions = ImmersivePlayerActions(), navController = rememberNavController(), initialPanel = Panel.LYRICS)
        }
        add("Immersive Look", "immersive/31-player-queue-panel.png", "Player: Queue Panel", "Immersive player with reorderable Up Next queue panel open.", Look.IMMERSIVE) {
            ImmersivePlayer(state = immersivePlayerState(), actions = ImmersivePlayerActions(), navController = rememberNavController(), initialPanel = Panel.QUEUE)
        }
        add("Immersive Look", "immersive/32-player-podcast.png", "Full-Screen Podcast Player", "Immersive podcast player with 15s skip controls and speed pill.", Look.IMMERSIVE, prepare = {
            seedEpisodeNowPlaying()
        }) {
            ImmersivePodcastPlayer(rememberNavController())
        }
        add("Immersive Look", "immersive/33-queue-page.png", "Standalone Queue Page", "Immersive standalone Queue route.", Look.IMMERSIVE) {
            ImmersiveShell(Routes.Queue.route, capped = false) { nav -> ImmersiveQueueScreen(nav) }
        }
        add("Immersive Look", "immersive/34-settings-root.png", "Settings: Root Categories", "Immersive Settings root with inset groups and Parental Controls card.", Look.IMMERSIVE) {
            ImmersiveShell("detail", capped = true) { nav -> ImmersiveSettingsScreen(nav, initialPage = SettingsPage.Root) }
        }
        add("Immersive Look", "immersive/35-settings-playback.png", "Settings: Playback", "Immersive Settings Playback sub-page.", Look.IMMERSIVE) {
            ImmersiveShell("detail", capped = true) { nav -> ImmersiveSettingsScreen(nav, initialPage = SettingsPage.Playback) }
        }
        add("Immersive Look", "immersive/36-settings-streaming-wifi.png", "Settings: Wi-Fi Streaming Quality", "Immersive Settings Wi-Fi quality picker page.", Look.IMMERSIVE) {
            ImmersiveShell("detail", capped = true) { nav -> ImmersiveSettingsScreen(nav, initialPage = SettingsPage.StreamingWifi) }
        }
        add("Immersive Look", "immersive/37-settings-crossfade.png", "Settings: Crossfade & AutoMix", "Immersive Settings Crossfade and AutoMix transitions page.", Look.IMMERSIVE) {
            ImmersiveShell("detail", capped = true) { nav -> ImmersiveSettingsScreen(nav, initialPage = SettingsPage.Crossfade) }
        }
        add("Immersive Look", "immersive/38-settings-lyrics.png", "Settings: Lyrics", "Immersive Settings Lyrics sub-page.", Look.IMMERSIVE) {
            ImmersiveShell("detail", capped = true) { nav -> ImmersiveSettingsScreen(nav, initialPage = SettingsPage.Lyrics) }
        }
        add("Immersive Look", "immersive/39-settings-storage.png", "Settings: Storage", "Immersive Settings Storage & cache usage sub-page.", Look.IMMERSIVE) {
            ImmersiveShell("detail", capped = true) { nav -> ImmersiveSettingsScreen(nav, initialPage = SettingsPage.Storage) }
        }
        add("Immersive Look", "immersive/40-settings-data.png", "Settings: Data Saver", "Immersive Settings Data Saver & cellular quality sub-page.", Look.IMMERSIVE) {
            ImmersiveShell("detail", capped = true) { nav -> ImmersiveSettingsScreen(nav, initialPage = SettingsPage.Data) }
        }
        add("Immersive Look", "immersive/41-settings-display.png", "Settings: Display & Appearance", "Immersive Settings Display sub-page.", Look.IMMERSIVE) {
            ImmersiveShell("detail", capped = true) { nav -> ImmersiveSettingsScreen(nav, initialPage = SettingsPage.Display) }
        }
        add("Immersive Look", "immersive/42-settings-look.png", "Settings: Look Chooser", "Immersive Settings Classic vs Immersive look switcher.", Look.IMMERSIVE) {
            ImmersiveShell("detail", capped = true) { nav -> ImmersiveSettingsScreen(nav, initialPage = SettingsPage.Look) }
        }
        add("Immersive Look", "immersive/43-settings-language.png", "Settings: App Language", "Immersive Settings Language picker.", Look.IMMERSIVE) {
            ImmersiveShell("detail", capped = true) { nav -> ImmersiveSettingsScreen(nav, initialPage = SettingsPage.Language) }
        }
        add("Immersive Look", "immersive/44-settings-library.png", "Settings: Library", "Immersive Settings Library preferences sub-page.", Look.IMMERSIVE) {
            ImmersiveShell("detail", capped = true) { nav -> ImmersiveSettingsScreen(nav, initialPage = SettingsPage.Library) }
        }
        add("Immersive Look", "immersive/45-settings-accounts.png", "Settings: Accounts (PIN-Unlocked)", "Immersive Settings Spotify Accounts sub-page.", Look.IMMERSIVE, parentAdminPrep) {
            ImmersiveShell("detail", capped = true) { nav -> ImmersiveSettingsScreen(nav, initialPage = SettingsPage.Accounts) }
        }
        add("Immersive Look", "immersive/46-settings-app-about.png", "Settings: About SoundNest", "Immersive Settings App version, diagnostics, and credits sub-page.", Look.IMMERSIVE) {
            ImmersiveShell("detail", capped = true) { nav -> ImmersiveSettingsScreen(nav, initialPage = SettingsPage.App) }
        }
        add("Immersive Look", "immersive/47-about-soundnest.png", "Menu: About SoundNest", "Full-screen Immersive About page with GPL-3.0 credits and upstream attributions.", Look.IMMERSIVE) {
            ImmersiveShell("detail", capped = true) { nav -> AboutScreen(nav) }
        }
        add("Immersive Look", "immersive/48-switch-account.png", "Menu: Switch Account (PIN-Unlocked)", "Full-screen Immersive Switch Account page opened from the menu drawer.", Look.IMMERSIVE, parentAdminPrep) {
            ImmersiveShell("detail", capped = true) { nav -> AccountsScreen(nav) }
        }
        add("Immersive Look", "immersive/49-audible-audiobook-chapters.png", "Audible Audiobook Chapters", "Immersive Audible book page with cover glow, chapter rows, and floating mini-player.", Look.IMMERSIVE, prepare = { app ->
            seedAudibleChapterView(app)
        }) {
            ImmersiveShell("detail", capped = true) { nav -> ImmersiveShowScreen(nav, showId = "audible:B08G9PRS1K", showName = "Project Hail Mary") }
        }
        add("Immersive Look", "immersive/50-player-audible-chapter.png", "Full-Screen Audiobook Player", "Immersive audiobook player while streaming an Audible audiobook chapter.", Look.IMMERSIVE, prepare = { app ->
            seedAudibleChapterView(app)
        }) {
            ImmersiveAudiobookPlayer(rememberNavController())
        }

        // ── 6. Bottom Sheets, Modals & Overlays ──
        add("Sheets & Modals", "sheets/01-classic-song-options.png", "Classic Song Options Sheet", "Track context menu in Classic look (read-only library aware).", Look.CLASSIC) {
            val ctx = LocalContext.current
            ClassicShell("detail", capped = true) { nav ->
                ContextScreen(nav, type = "album", id = "planets")
                SongOptionsSheet(song = sampleTrackSongs().first(), navController = nav, context = ctx, onDismiss = {}, nowPlaying = true)
            }
        }
        add("Sheets & Modals", "sheets/02-immersive-song-options.png", "Immersive Song Options Sheet", "Glass track context menu over the Immersive player.", Look.IMMERSIVE) {
            val ctx = LocalContext.current
            val nav = rememberNavController()
            ImmersivePlayer(state = immersivePlayerState(), actions = ImmersivePlayerActions(), navController = nav)
            ImmersiveSongOptionsSheet(song = sampleTrackSongs().first(), navController = nav, context = ctx, onDismiss = {}, nowPlaying = true)
        }
        add("Sheets & Modals", "sheets/03-devices-connect-sheet.png", "Spotify Connect & Cast Devices Sheet", "Speaker & device switcher filtered by Parental Speaker Visibility rules.", Look.IMMERSIVE) {
            ImmersivePlayer(state = immersivePlayerState(), actions = ImmersivePlayerActions(), navController = rememberNavController())
            DevicesSheet(onDismiss = {})
        }
        add("Sheets & Modals", "sheets/04-classic-sleep-timer-sheet.png", "Classic Sleep Timer Sheet", "Kid-accessible sleep timer options in Classic look.", Look.CLASSIC, prepare = {
            SongPlayer.setSleepTimer(30)
        }) {
            PlayerScreen(rememberNavController())
            SleepTimerSheet(onDismiss = {})
        }
        add("Sheets & Modals", "sheets/05-immersive-sleep-timer-sheet.png", "Immersive Sleep Timer Sheet", "Glass sleep timer options in Immersive look.", Look.IMMERSIVE, prepare = {
            SongPlayer.setSleepTimer(30)
        }) {
            ImmersivePlayer(state = immersivePlayerState(), actions = ImmersivePlayerActions(), navController = rememberNavController())
            ImmersiveSleepTimerSheet(onDismiss = {})
        }
        add("Sheets & Modals", "sheets/06-streaming-quality-sheet.png", "Streaming Quality Sheet", "Quick audio quality picker sheet from the player.", Look.CLASSIC) {
            PlayerScreen(rememberNavController())
            QualitySheet(onDismiss = {})
        }
        add("Sheets & Modals", "sheets/07-about-sheet.png", "About SoundNest Sheet", "App version, license, and open-source notices modal sheet.", Look.CLASSIC) {
            ClassicShell("detail", capped = true) { nav -> SettingsScreen(nav, initialTab = 4) }
            AboutSheet(onDismiss = {})
        }
        add("Sheets & Modals", "sheets/08-stats-for-nerds-sheet.png", "Stats for Nerds Sheet", "Playback engine, codec, buffer, and audio pipeline diagnostics sheet.", Look.CLASSIC) {
            PlayerScreen(rememberNavController())
            StatsForNerdsSheet(onDismiss = {})
        }
        add("Sheets & Modals", "sheets/09-look-offer-modal.png", "New Look Offer Screen", "One-time upgrade prompt offering existing users the Immersive look.", Look.IMMERSIVE) {
            LookStep(onNext = {}, offer = LookOffer(onKeep = {}))
        }
        add("Sheets & Modals", "sheets/10-immersive-menu-drawer.png", "Immersive Menu Navigation Drawer", "90%-width Material 3 left-sliding drawer from top-left avatar with 10% Home peek on the right.", Look.IMMERSIVE) {
            val nav = rememberNavController()
            val drawerState = androidx.compose.material3.rememberDrawerState(androidx.compose.material3.DrawerValue.Open)
            MenuNavigationDrawer(navController = nav, drawerState = drawerState) {
                ImmersiveShell(Routes.Home.route, capped = false) { innerNav ->
                    ImmersiveHomeScreen(innerNav)
                }
            }
        }
        add("Sheets & Modals", "sheets/11-playlist-options-sheet.png", "Playlist Options Sheet", "Playlist actions sheet (shuffle, sort, share, library controls).", Look.CLASSIC) {
            ClassicShell("detail", capped = true) { nav ->
                ContextScreen(nav, type = "playlist", id = "ghibli")
                PlaylistOptionsSheet(
                    page = playlistPageModel(),
                    onDismiss = {},
                    onChanged = {},
                    onEdited = { _, _ -> },
                    onDeleted = {},
                )
            }
        }
        add("Sheets & Modals", "sheets/12-episode-options-sheet.png", "Podcast Episode Options Sheet", "Episode actions sheet (save, mark played, share).", Look.CLASSIC, prepare = {
            seedShowPodcast()
        }) {
            ClassicShell("detail", capped = true) { nav ->
                ShowScreen(nav, showId = "brainson", showName = "Brains On! Science Podcast")
                EpisodeOptionsSheet(
                    song = sampleEpisodes().first(),
                    finished = false,
                    onPlay = {},
                    onMarkFinished = {},
                    onOpen = {},
                    onDismiss = {},
                )
            }
        }
        add("Sheets & Modals", "sheets/13-classic-menu-drawer.png", "Classic Menu Navigation Drawer", "90%-width Material 3 left-sliding drawer in Classic look with 10% Home peek on the right.", Look.CLASSIC) {
            val nav = rememberNavController()
            val drawerState = androidx.compose.material3.rememberDrawerState(androidx.compose.material3.DrawerValue.Open)
            MenuNavigationDrawer(navController = nav, drawerState = drawerState) {
                ClassicShell(Routes.Home.route, capped = false) { innerNav ->
                    HomeScreen(innerNav)
                }
            }
        }
        add("Sheets & Modals", "sheets/14-audible-chapter-options-sheet.png", "Audible Chapter Options Sheet", "Chapter overflow sheet for an Audible audiobook chapter.", Look.CLASSIC, prepare = { app ->
            seedAudibleChapterView(app)
        }) {
            ClassicShell("detail", capped = true) { nav ->
                ShowScreen(nav, showId = "audible:B08G9PRS1K", showName = "Project Hail Mary")
                lastAudibleShowPage?.items?.getOrNull(2)?.let { chapterSong ->
                    ChapterOptionsSheet(
                        chapter = chapterSong,
                        bookTitle = "Project Hail Mary",
                        finished = chapterSong.played,
                        started = chapterSong.playedPositionMs > 0L,
                        onPlay = {},
                        onRestart = {},
                        onMarkFinished = {},
                        onOpenBook = null,
                        onDismiss = {},
                    )
                }
            }
        }
        add("Sheets & Modals", "sheets/15-audiobook-chapters-sheet.png", "Audiobook Chapters Sheet", "Table-of-contents bottom sheet opened from the audiobook player.", Look.CLASSIC, prepare = { app ->
            seedAudibleChapterView(app)
        }) {
            AudiobookPlayerScreen(rememberNavController())
            AudiobookChaptersSheet(
                bookTitle = lastAudibleShowPage?.show?.name ?: "Project Hail Mary",
                chapters = lastAudibleShowPage?.items ?: emptyList(),
                currentUri = SongPlayer.nowPlaying.value.uri,
                playing = SongPlayer.isPlaying(),
                isFinished = { it.played },
                onSelectChapter = { _, _ -> },
                onDismiss = {},
            )
        }

        return shots
    }

    private fun writeCatalogReadme(shots: List<ShotSpec>) {
        val grouped = shots.groupBy { it.category }
        val md = buildString {
            appendLine("# SoundNest Complete Screen Catalog")
            appendLine()
            appendLine("Auto-generated deterministically by `./gradlew :app:generateDocScreenshots` ([`DocScreenshotTest.kt`](../../base/app/src/test/java/io/github/thake/soundnest/ui/DocScreenshotTest.kt)).")
            appendLine()
            appendLine("Total captured screens, panels, dialogs & sheets: **${shots.size}**.")
            appendLine()
            appendLine("## Contents")
            grouped.keys.forEach { cat ->
                val anchor = cat.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
                appendLine("- [$cat (${grouped.getValue(cat).size})](#$anchor)")
            }
            appendLine()

            grouped.forEach { (cat, list) ->
                appendLine("## $cat")
                appendLine()
                appendLine("| Screen | Preview | File & Description |")
                appendLine("| :--- | :---: | :--- |")
                list.forEach { s ->
                    appendLine("| **${s.title}** (`${s.look.name}`) | <img src=\"${s.relativePath}\" width=\"180\" alt=\"${s.title}\" /> | [`${s.relativePath}`](${s.relativePath})<br/>${s.description} |")
                }
                appendLine()
            }
        }
        File(outputDir, "README.md").writeText(md)
    }

    // ── Deterministic Data & ViewModel Seeding ──────────────────────────────────

    private inline fun <reified T : ViewModel> seedVm(key: String? = null): T {
        val p = ViewModelProvider(hostActivity)
        return if (key == null) p[T::class.java] else p[key, T::class.java]
    }

    private fun Any.poke(field: String, value: Any?) {
        var c: Class<*>? = javaClass
        while (c != null) {
            runCatching { c.getDeclaredField(field) }.getOrNull()?.let { f ->
                f.isAccessible = true
                val cur = f.get(this)
                if (cur is MutableStateFlow<*>) {
                    @Suppress("UNCHECKED_CAST")
                    (cur as MutableStateFlow<Any?>).value = value
                } else {
                    f.set(this, value)
                }
                return
            }
            c = c.superclass
        }
        error("No field '$field' on ${javaClass.name}")
    }

    private fun sampleTrackSongs(): List<SongsModel> = listOf(
        SongsModel(
            id = 1,
            title = "Jupiter, the Bringer of Jollity",
            album = "The Planets, Op. 32",
            singer = "Gustav Holst, London Symphony Orchestra",
            coverUri = covers.getValue("planets"),
            url = "spotify:track:jupiter",
            spotifyTrackId = "jupiter",
            durationMs = 478_000,
            albumId = "planets",
            artistIds = listOf("holst"),
        ),
        SongsModel(
            id = 2,
            title = "One Summer's Day",
            album = "Spirited Away Soundtrack",
            singer = "Joe Hisaishi",
            coverUri = covers.getValue("ghibli"),
            url = "spotify:track:onesummersday",
            spotifyTrackId = "onesummersday",
            durationMs = 249_000,
            albumId = "spirited",
            artistIds = listOf("hisaishi"),
        ),
        SongsModel(
            id = 3,
            title = "Test Drive",
            album = "How to Train Your Dragon",
            singer = "John Powell",
            coverUri = covers.getValue("dragon"),
            url = "spotify:track:testdrive",
            spotifyTrackId = "testdrive",
            durationMs = 156_000,
            albumId = "dragon",
        ),
        SongsModel(
            id = 4,
            title = "Clair de Lune",
            album = "Suite bergamasque",
            singer = "Claude Debussy",
            coverUri = covers.getValue("debussy"),
            url = "spotify:track:debussy",
            spotifyTrackId = "debussy",
            durationMs = 304_000,
            albumId = "bergamasque",
        ),
        SongsModel(
            id = 5,
            title = "Aquarium",
            album = "Carnival of the Animals",
            singer = "Camille Saint-Saens",
            coverUri = covers.getValue("carnival"),
            url = "spotify:track:aquarium",
            spotifyTrackId = "aquarium",
            durationMs = 148_000,
            albumId = "carnival",
        ),
        SongsModel(
            id = 6,
            title = "Surface Pressure (Instrumental)",
            album = "Encanto (Soundtrack)",
            singer = "Lin-Manuel Miranda",
            coverUri = covers.getValue("encanto"),
            url = "spotify:track:surface",
            spotifyTrackId = "surface",
            durationMs = 202_000,
            albumId = "encanto",
        ),
    )

    private fun sampleEpisodes(): List<SongsModel> = listOf(
        SongsModel(
            id = 101,
            title = "Why Do Stars Twinkle in the Night Sky?",
            album = "Brains On! Science Podcast",
            singer = "Brains On! Science Podcast",
            coverUri = covers.getValue("planets"),
            url = "episode:ep1",
            spotifyTrackId = "ep1",
            durationMs = 1_680_000,
            description = "Astronomer Dr. Maya Lin joins kid co-host Leo to explore how Earth's moving atmosphere bends starlight before it reaches our eyes.",
            releaseDate = "2026-10-28",
            played = false,
            playedPositionMs = 620_000L,
            showId = "brainson",
        ),
        SongsModel(
            id = 102,
            title = "How Whales Sing Across Entire Oceans",
            album = "Brains On! Science Podcast",
            singer = "Brains On! Science Podcast",
            coverUri = covers.getValue("study"),
            url = "episode:ep2",
            spotifyTrackId = "ep2",
            durationMs = 1_440_000,
            description = "Dive deep beneath the waves to hear humpback whale songs and discover how sound travels four times faster in water than in air.",
            releaseDate = "2026-10-21",
            played = false,
            playedPositionMs = 0L,
            showId = "brainson",
        ),
        SongsModel(
            id = 103,
            title = "The Secret Life of Honeybees",
            album = "Wow in the World",
            singer = "Wow in the World",
            coverUri = covers.getValue("carnival"),
            url = "episode:ep3",
            spotifyTrackId = "ep3",
            durationMs = 1_320_000,
            description = "How honeybees dance to share directions to the best wildflowers.",
            releaseDate = "2026-10-14",
            played = true,
            playedPositionMs = 1_320_000L,
            showId = "brainson",
        ),
    )

    private fun playlistPageModel() = ContextPageModel(
        uri = "spotify:playlist:ghibli",
        type = "playlist",
        name = "Studio Ghibli Piano",
        coverUri = covers.getValue("ghibli"),
        description = "Gentle solo piano arrangements from Spirited Away, My Neighbor Totoro & Kiki's Delivery Service.",
        ownerName = "Parent Picks",
        ownerAvatar = covers.getValue("classical"),
        ownerUri = "spotify:user:family",
        metaLine = "6 songs • 25 min",
        tracks = sampleTrackSongs(),
    )

    private fun albumPageModel() = ContextPageModel(
        uri = "spotify:album:planets",
        type = "album",
        name = "The Planets, Op. 32",
        coverUri = covers.getValue("planets"),
        ownerName = "Gustav Holst",
        ownerAvatar = covers.getValue("classical"),
        ownerUri = "spotify:artist:holst",
        metaLine = "Album • 2021",
        tracks = sampleTrackSongs(),
        footer = listOf("September 14, 2021", "6 tracks • 25 min", "© 2021 London Symphony Orchestra"),
    )

    private fun likedPageModel() = ContextPageModel(
        uri = "spotify:collection:tracks",
        type = "liked",
        name = "Liked Songs",
        coverUri = covers.getValue("debussy"),
        ownerName = "Emma's Music",
        metaLine = "6 songs",
        tracks = sampleTrackSongs(),
    )

    private fun seedDefaultMusicQueue() {
        val tracks = sampleTrackSongs()
        SongPlayer.showProbeQueue(
            SongPlayer.EngineQueue(
                current = tracks[0],
                queued = listOf(
                    SongPlayer.QueueItem(tracks[1], tracks[1].url, "q1", "queue"),
                    SongPlayer.QueueItem(tracks[2], tracks[2].url, "q2", "queue"),
                ),
                nextUp = tracks.drop(3).mapIndexed { idx, s ->
                    SongPlayer.QueueItem(s, s.url, "c$idx", "context")
                },
            ),
        )
    }

    private fun seedAllStoresAndViewModels(app: Application) {
        seedFeedAndLibraryCache()
        seedMusicNowPlaying()

        // Queue (for Classic QueueScreen, ImmersiveQueueScreen & ImmersivePlayer Queue panel)
        val tracks = sampleTrackSongs()
        seedDefaultMusicQueue()

        // Context pages (Playlist, Album, Liked Songs)
        seedVm<ContextViewModel>("playlist:ghibli").apply {
            poke("key", "playlist:ghibli")
            poke("_page", Response.Success(playlistPageModel()))
        }
        seedVm<ContextViewModel>("album:planets").apply {
            poke("key", "album:planets")
            poke("_page", Response.Success(albumPageModel()))
        }
        seedVm<ContextViewModel>("liked:").apply {
            poke("key", "liked:")
            poke("_page", Response.Success(likedPageModel()))
        }

        // Artist & Discography
        val releases = listOf(
            AlbumsModel(1, "Gustav Holst", covers.getValue("planets"), "The Planets, Op. 32", "2021-09-14", "ALBUM", "planets"),
            AlbumsModel(2, "Gustav Holst", covers.getValue("classical"), "St Paul's Suite", "2020-04-10", "ALBUM", "stpauls"),
            AlbumsModel(3, "Gustav Holst", covers.getValue("bedtime"), "Venus, the Bringer of Peace", "2023-02-01", "SINGLE", "venus"),
            AlbumsModel(4, "Gustav Holst", covers.getValue("study"), "First Suite in E-Flat", "2019-11-20", "EP", "suite1"),
        )
        val artistOverview = ArtistOverviewModel(
            id = "holst",
            name = "Gustav Holst",
            verified = true,
            monthlyListeners = 2_845_120L,
            biography = "English composer, arranger and teacher best known for his orchestral suite The Planets.",
            headerImage = covers.getValue("planets"),
            avatarImage = covers.getValue("classical"),
            topTracks = tracks.take(5).mapIndexed { idx, s -> ArtistTrackUi(s, playcount = 14_500_000L - idx * 2_100_000L) },
            popularReleases = releases,
            appearsOn = listOf(
                AlbumsModel(5, "Various Artists", covers.getValue("carnival"), "Classical for Curious Minds", "2024-01-15", "COMPILATION", "curious"),
            ),
            relatedArtists = listOf(
                ArtistsModel("Joe Hisaishi", covers.getValue("ghibli"), id = "hisaishi"),
                ArtistsModel("Claude Debussy", covers.getValue("debussy"), id = "debussy"),
                ArtistsModel("Camille Saint-Saens", covers.getValue("carnival"), id = "saintsaens"),
                ArtistsModel("John Williams", covers.getValue("starwars"), id = "williams"),
            ),
        )
        seedVm<ArtistViewModel>().apply {
            poke("loadedOverview", "holst")
            poke("_overview", Response.Success(artistOverview))
        }

        // Category
        seedVm<CategoryViewModel>().apply {
            poke("genreKey", "classical")
            poke(
                "_playlists",
                Response.Success(
                    listOf(
                        LibraryEntry("curious", "Classical for Curious Minds", "Mozart, Holst & Debussy", covers.getValue("classical"), isPlaylist = true, uri = "spotify:playlist:curious"),
                        LibraryEntry("ghibli", "Studio Ghibli Piano", "Gentle Piano Favorites", covers.getValue("ghibli"), isPlaylist = true, uri = "spotify:playlist:ghibli"),
                        LibraryEntry("bedtime", "Bedtime Lullabies", "Soft Strings & Harp", covers.getValue("bedtime"), isPlaylist = true, uri = "spotify:playlist:bedtime"),
                        LibraryEntry("study", "Rainy Day Study Beats", "Calm Focus Music", covers.getValue("study"), isPlaylist = true, uri = "spotify:playlist:study"),
                    ),
                ),
            )
        }

        // Podcast Show & Episode
        seedShowPodcast()
        seedVm<EpisodeViewModel>().apply {
            poke("key", "ep1")
            loading.value = false
            failed.value = false
            finished.value = false
            poke("_episode", sampleEpisodes().first())
        }

        // Lyrics (for Classic LyricsScreen, PlayerScreen inline card & ImmersivePlayer Lyrics panel)
        val syncedLyrics = Lyrics(
            lines = listOf(
                LyricLine(0L, "♪ Soft piano introduction ♪"),
                LyricLine(120_000L, "Moonlight shimmers quietly across the water"),
                LyricLine(132_000L, "Silver clouds drift gently past the trees"),
                LyricLine(140_000L, "Every star awakens in the evening sky"),
                LyricLine(152_000L, "Carried softly on a peaceful breeze"),
                LyricLine(166_000L, "Dreaming through the calm of night"),
                LyricLine(180_000L, "Until the morning brings its golden light"),
            ),
            synced = true,
            language = "en",
            provider = "Musixmatch",
            colors = LyricsColors(
                background = 0xFF1E3A8A.toInt(),
                text = 0xFFFFFFFF.toInt(),
                highlight = 0xFF93C5FD.toInt(),
            ),
        )
        seedVm<LyricsViewModel>().apply {
            poke("_state", LyricsViewModel.State.Loaded(syncedLyrics))
        }

        // LibraryViewModel account & artists
        seedVm<LibraryViewModel>().apply {
            poke("_account", Response.Success(AccountModel("Emma's Music", "", covers.getValue("ghibli"), "Premium")))
            poke(
                "_followedArtists",
                listOf(
                    ArtistsModel("Gustav Holst", covers.getValue("planets"), id = "holst"),
                    ArtistsModel("Joe Hisaishi", covers.getValue("ghibli"), id = "hisaishi"),
                ),
            )
        }

        // Local Playlist
        LocalPlaylists.init(app)
        LocalPlaylists.playlists["local-bedtime"] = LocalPlaylist(
            id = "local-bedtime",
            name = "Bedtime Stories & Lullabies",
            createdAt = System.currentTimeMillis(),
            items = listOf(
                LocalItem("l1", "file:///music/brahms.mp3", "Brahms' Lullaby", "Clara Schumann Trio", "Evening Piano", 164_000L, covers.getValue("bedtime")),
                LocalItem("l2", "file:///music/twinkle.mp3", "Twinkle Variations", "Wolfgang Amadeus Mozart", "Little Classics", 212_000L, covers.getValue("classical")),
                LocalItem("l3", "file:///music/rain.mp3", "Forest Rain & Harp", "Nature Ensemble", "Calm Sleep", 295_000L, covers.getValue("acoustic")),
            ),
        )

        // Hidden Songs
        HiddenSongs.hide(
            app,
            SongsModel(
                id = 99,
                title = "Loud Late-Night Party Remix",
                album = "Club Hits",
                singer = "DJ Noise",
                coverUri = covers.getValue("dragon"),
                url = "spotify:track:hidden1",
                spotifyTrackId = "hidden1",
            ),
        )

        // Radio Favorites
        RadioFavoritesStore.favorites["uuid-klassik"] = RadioFavorite(
            station = RadioStation(
                stationUuid = "uuid-klassik",
                name = "Klassik Radio Movie & Orchestral",
                streamUrl = "https://stream.klassikradio.de/movie/mp3-192",
                homepage = "https://www.klassikradio.de",
                favicon = covers.getValue("planets"),
                tags = listOf("classical", "film scores", "orchestral"),
                country = "Germany",
                countryCode = "DE",
                language = "german",
                codec = "MP3",
                bitrateKbps = 192,
                votes = 1420,
                clickCount = 8500,
                lastCheckOk = true,
            ),
            addedAt = System.currentTimeMillis(),
        )
        RadioFavoritesStore.favorites["uuid-lullaby"] = RadioFavorite(
            station = RadioStation(
                stationUuid = "uuid-lullaby",
                name = " Calm Kids Piano Radio",
                streamUrl = "https://stream.example.org/kidspiano.mp3",
                homepage = "https://example.org",
                favicon = covers.getValue("bedtime"),
                tags = listOf("piano", "children", "calm"),
                country = "United Kingdom",
                countryCode = "GB",
                language = "english",
                codec = "AAC",
                bitrateKbps = 128,
                votes = 890,
                clickCount = 4100,
                lastCheckOk = true,
            ),
            addedAt = System.currentTimeMillis() - 60_000L,
        )

        // On-device ListenHistory (for RecapScreen / ImmersiveRecapScreen "This phone" tab)
        ListenHistory.init(app)
        ListenHistory.clear()
        val now = System.currentTimeMillis()
        tracks.forEachIndexed { idx, s ->
            repeat(4 - (idx % 3)) { rep ->
                ListenHistory.record(
                    ListenHistory.Listen(
                        atMs = now - (idx * 86_400_000L) - rep * 3_600_000L,
                        key = s.url,
                        title = s.title,
                        artist = s.singer,
                        album = s.album,
                        cover = s.coverUri,
                        playedMs = s.durationMs.toLong(),
                        source = if (idx % 2 == 0) ListenHistory.Source.SPOTIFY else ListenHistory.Source.LOCAL,
                    ),
                )
            }
        }
    }

    private fun seedShowPodcast() {
        val show = PodcastModel(
            id = "brainson",
            name = "Brains On! Science Podcast",
            publisher = "American Public Media",
            coverUri = covers.getValue("planets"),
            description = "An award-winning science podcast for curious kids and adults. Every week, a different kid co-host teams up with Molly Bloom to find answers to fascinating questions about the world.",
            rating = 4.9,
            saved = true,
            audiobook = false,
        )
        seedVm<ShowViewModel>().apply {
            finished.clear()
            filter.value = io.github.thake.soundnest.feature.spokenword.podcasts.settings.EpisodeFilter.ALL
            previousOpened.value = 0L
            poke("showKey", "brainson")
            following.value = true
            poke("_page", Response.Success(ShowPage(show = show, items = sampleEpisodes(), audiobook = false, coverUri = show.coverUri)))
        }
    }

    private fun seedShowAudiobook() {
        val book = PodcastModel(
            id = "littleprince",
            name = "The Little Prince",
            publisher = "Classic Family Audiobooks",
            coverUri = covers.getValue("bedtime"),
            description = "Antoine de Saint-Exupery's timeless fable about friendship, curiosity, and seeing with the heart.",
            rating = 4.9,
            saved = true,
            authors = listOf("Antoine de Saint-Exupery"),
            narrators = listOf("Kenneth Branagh"),
            audiobook = true,
        )
        val chapters = listOf(
            SongsModel(201, "Chapter 1 • Drawing Number One", "The Little Prince", "Antoine de Saint-Exupery", covers.getValue("bedtime"), "episode:ch1", "ch1", durationMs = 780_000, description = "The narrator recalls his childhood drawing of a boa constrictor.", releaseDate = "2024-01-01", played = true, playedPositionMs = 780_000L, showId = "littleprince"),
            SongsModel(202, "Chapter 2 • Meeting in the Sahara", "The Little Prince", "Antoine de Saint-Exupery", covers.getValue("bedtime"), "episode:ch2", "ch2", durationMs = 920_000, description = "Please... draw me a sheep!", releaseDate = "2024-01-01", played = false, playedPositionMs = 410_000L, showId = "littleprince"),
            SongsModel(203, "Chapter 3 • Asteroid B-612", "The Little Prince", "Antoine de Saint-Exupery", covers.getValue("bedtime"), "episode:ch3", "ch3", durationMs = 840_000, description = "Learning where the Little Prince came from.", releaseDate = "2024-01-01", played = false, playedPositionMs = 0L, showId = "littleprince"),
        )
        seedVm<ShowViewModel>().apply {
            finished.clear()
            filter.value = io.github.thake.soundnest.feature.spokenword.podcasts.settings.EpisodeFilter.ALL
            previousOpened.value = 0L
            poke("showKey", "littleprince")
            following.value = true
            poke("_page", Response.Success(ShowPage(show = book, items = chapters, audiobook = true, coverUri = book.coverUri)))
        }
    }

    private class FakeDocAudibleBackend :
        io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayerBackend {
        private var playing = false
        private var buffering = false
        private var positionMs = 0L
        private var listener: io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayerBackend.Listener? = null

        override fun load(
            grant: io.github.thake.soundnest.audible.AudibleStreamGrant,
            initialPositionMs: Long,
            speed: Float,
            playWhenReady: Boolean,
            listener: io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayerBackend.Listener,
        ) {
            this.positionMs = initialPositionMs
            this.playing = playWhenReady
            this.buffering = false
            this.listener = listener
            listener.onPlaybackChanged(playWhenReady, false, initialPositionMs)
        }

        override fun resume() {
            playing = true
            listener?.onPlaybackChanged(true, false, positionMs)
        }

        override fun pause() {
            playing = false
            listener?.onPlaybackChanged(false, false, positionMs)
        }

        override fun stop() {
            playing = false
        }

        override fun seekToAbsolute(positionMs: Long) {
            this.positionMs = positionMs
            listener?.onPlaybackChanged(playing, false, positionMs)
        }

        override fun setSpeed(speed: Float) {}
        override fun setGain(gain: Float) {}
        override fun isPlaying(): Boolean = playing
        override fun isBuffering(): Boolean = buffering
        override fun currentPositionMs(): Long = positionMs
        override fun release() {
            playing = false
        }
    }

    private fun seedAudibleChapterView(app: Application) {
        ParentSecurityManager.lockNow(app)
        io.github.thake.soundnest.feature.spokenword.audible.settings.AudibleCredentialStore.save(
            app,
            io.github.thake.soundnest.audible.AudibleSessionTokens(
                marketplace = io.github.thake.soundnest.audible.AudibleMarketplace.DE,
                deviceSerial = "DOCSHOTSERIAL1",
                adpToken = "DOC_ADP_TOKEN",
                devicePrivateKeyPem = "DOC_PEM",
                refreshToken = "DOC_REFRESH",
                accessToken = "DOC_ACCESS",
                customerName = "Family Account",
            ),
        )
        val book = io.github.thake.soundnest.audible.AudibleBook(
            asin = "B08G9PRS1K",
            title = "Project Hail Mary",
            subtitle = "A Novel",
            authors = listOf("Andy Weir"),
            narrators = listOf("Ray Porter"),
            seriesTitle = "Hail Mary",
            seriesSequence = "1",
            runtimeLengthMs = 58_200_000L,
            coverUrl = covers.getValue("planets"),
            description = "Ryland Grace is the sole survivor on a desperate, last-chance mission—and if he fails, humanity and the earth itself will perish.",
            releaseDate = "2021-05-04",
        )
        val chapters = listOf(
            io.github.thake.soundnest.audible.AudibleChapter(0, "Opening Credits", 0L, 2_368L),
            io.github.thake.soundnest.audible.AudibleChapter(1, "Part One: The Mission", 2_368L, 8_840L),
            io.github.thake.soundnest.audible.AudibleChapter(2, "Chapter 1", 11_208L, 2_258_533L),
            io.github.thake.soundnest.audible.AudibleChapter(3, "Chapter 2", 2_269_741L, 1_440_000L),
            io.github.thake.soundnest.audible.AudibleChapter(4, "Part Two - Chapter 3", 3_709_741L, 1_020_000L),
        )
        io.github.thake.soundnest.feature.spokenword.audible.data.AudibleCatalogCache.saveBooks(app, listOf(book))
        io.github.thake.soundnest.feature.spokenword.audible.data.AudibleCatalogCache.saveChapters(app, "B08G9PRS1K", chapters)
        ParentalPolicyStore.update(app) {
            it.copy(
                spokenWord = it.spokenWord.copy(
                    allowAudiobooks = true,
                    allowAudible = true,
                    audible = AudibleParentalPolicy(
                        allowedAsins = setOf("B08G9PRS1K"),
                        pinnedAsins = setOf("B08G9PRS1K"),
                    ),
                ),
            )
        }
        io.github.thake.soundnest.feature.spokenword.audible.data.AudibleProgressStore.setChapterFinished(app, "B08G9PRS1K", 0, true)
        io.github.thake.soundnest.feature.spokenword.audible.data.AudibleProgressStore.savePosition(
            app,
            asin = "B08G9PRS1K",
            positionMs = 11_208L + 918_000L,
            chapterIndex = 2,
        )
        io.github.thake.soundnest.feature.spokenword.audible.playback.AudiblePlayer.configureForTests(
            context = app,
            backend = FakeDocAudibleBackend(),
            grantLoader = { asin ->
                io.github.thake.soundnest.audible.AudibleStreamGrant(
                    asin = asin,
                    manifestUrl = "https://cdn.audible.de/$asin/manifest.mpd",
                    drmType = "Widevine",
                    chapters = chapters,
                )
            },
        )
        SongPlayer.playSong("audible:book:B08G9PRS1K")
        ShadowLooper.idleMainLooper()

        val page = kotlinx.coroutines.runBlocking {
            io.github.thake.soundnest.feature.spokenword.audible.data.AudibleShowPageLoader.load(
                context = app,
                showId = "audible:B08G9PRS1K",
                policy = ParentalPolicyStore.current(app),
            ).getOrThrow()
        }
        lastAudibleShowPage = page
        seedVm<ShowViewModel>().apply {
            finished.clear()
            filter.value = io.github.thake.soundnest.feature.spokenword.podcasts.settings.EpisodeFilter.ALL
            previousOpened.value = 0L
            following.value = false
            poke("showKey", "audible:B08G9PRS1K")
            poke("_page", Response.Success(page))
        }
    }

    private fun seedSearchIdle() {
        seedVm<SearchViewModel>().apply {
            query = ""
            poke("_results", Response.Success(SearchResults()))
        }
    }

    private fun seedSearchPopulated() {
        val results = SearchResults(
            songs = sampleTrackSongs().take(4),
            artists = listOf(
                ArtistsModel("Gustav Holst", covers.getValue("planets"), id = "holst"),
                ArtistsModel("Joe Hisaishi", covers.getValue("ghibli"), id = "hisaishi"),
            ),
            albums = listOf(
                AlbumsModel(1, "Gustav Holst", covers.getValue("planets"), "The Planets, Op. 32", "2021", "ALBUM", "planets"),
                AlbumsModel(2, "John Powell", covers.getValue("dragon"), "How to Train Your Dragon", "2010", "ALBUM", "dragon"),
            ),
        )
        seedVm<SearchViewModel>().apply {
            query = "orchestra"
            poke("_results", Response.Success(results))
        }
    }

    private fun seedMusicNowPlaying() {
        Graph.currentSongState.updateSongState(
            coverUri = covers.getValue("debussy"),
            title = "Clair de Lune",
            singer = "Claude Debussy",
            playingState = true,
            songId = 4,
            songIndex = 0,
            album = "Suite bergamasque",
        )
        setNowPlayingMeta(
            SongPlayer.NowPlaying(
                contextUri = "spotify:playlist:ghibli",
                contextName = "Parent Picks • Classical",
                provider = "context",
                uri = "spotify:track:debussy",
                albumUri = "spotify:album:planets",
            ),
        )
    }

    private fun seedEpisodeNowPlaying() {
        Graph.currentSongState.updateSongState(
            coverUri = covers.getValue("planets"),
            title = "Why Do Stars Twinkle in the Night Sky?",
            singer = "Brains On! Science Podcast",
            playingState = true,
            songId = 101,
            songIndex = 0,
            album = "Brains On! Science Podcast",
        )
        setNowPlayingMeta(
            SongPlayer.NowPlaying(
                contextUri = "spotify:show:brainson",
                contextName = "Brains On! Science Podcast",
                provider = "context",
                uri = "spotify:episode:ep1",
            ),
        )
    }

    private fun setNowPlayingMeta(np: SongPlayer.NowPlaying) {
        @Suppress("UNCHECKED_CAST")
        val state = SongPlayer::class.java.getDeclaredField("_nowPlaying")
            .apply { isAccessible = true }
            .get(SongPlayer) as androidx.compose.runtime.MutableState<SongPlayer.NowPlaying>
        state.value = np
    }

    private fun seedFeedAndLibraryCache() {
        val libraryEntries = listOf(
            LibraryEntry(
                spotifyId = "ghibli",
                name = "Studio Ghibli Piano",
                subtitle = "Playlist • Parent Picks",
                coverUri = covers.getValue("ghibli"),
                isPlaylist = true,
                pinned = true,
                uri = "spotify:playlist:ghibli",
            ),
            LibraryEntry(
                spotifyId = "roadtrip",
                name = "Family Roadtrip",
                subtitle = "Playlist • Sing-Along",
                coverUri = covers.getValue("roadtrip"),
                isPlaylist = true,
                pinned = true,
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
            LibraryEntry(
                spotifyId = "folder:kids",
                name = "Bedtime & Calm",
                subtitle = "Folder • 2 playlists",
                coverUri = covers.getValue("bedtime"),
                isPlaylist = false,
                folderUri = "spotify:folder:kids",
                childCount = 2,
                uri = "spotify:folder:kids",
            ),
        )
        val folderEntries = listOf(
            LibraryEntry(
                spotifyId = "bedtime",
                name = "Bedtime Lullabies",
                subtitle = "Playlist • Soft Piano",
                coverUri = covers.getValue("bedtime"),
                isPlaylist = true,
                uri = "spotify:playlist:bedtime",
            ),
            LibraryEntry(
                spotifyId = "acoustic",
                name = "Acoustic Morning",
                subtitle = "Playlist • Warm Guitar",
                coverUri = covers.getValue("acoustic"),
                isPlaylist = true,
                uri = "spotify:playlist:acoustic",
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
        Api.HomeCache.folders["spotify:folder:kids"] = folderEntries
        Api.HomeCache.home = curatedFeed
    }

    /**
     * Installs a deterministic offline `SoundNestApi` that immediately answers secondary page
     * queries (Browse categories, What's New, New Releases, Saved/New Episodes, Recently Played,
     * Spotify Top Stats, Credits, and Connect Devices) while keeping primary feed/library fetches
     * suspended so `Api.HomeCache` and `poke`-seeded ViewModels remain authoritative.
     */
    private fun installRichFakeApi() {
        val img = { key: String -> listOf(Image(covers.getValue(key), 512, 512)) }
        val apiAlbums = listOf(
            Album("spotify:album:planets", "The Planets, Op. 32", listOf(ArtistRef("spotify:artist:holst", "Gustav Holst")), img("planets"), "ALBUM", "2026-10-30", 7),
            Album("spotify:album:dragon", "How to Train Your Dragon", listOf(ArtistRef("spotify:artist:powell", "John Powell")), img("dragon"), "ALBUM", "2026-10-24", 12),
            Album("spotify:album:carnival", "Carnival of the Animals", listOf(ArtistRef("spotify:artist:saintsaens", "Camille Saint-Saens")), img("carnival"), "ALBUM", "2026-10-12", 14),
            Album("spotify:album:encanto", "Encanto (Instrumental)", listOf(ArtistRef("spotify:artist:miranda", "Lin-Manuel Miranda")), img("encanto"), "ALBUM", "2026-10-02", 9),
        )
        val apiEpisodes = listOf(
            Episode(
                uri = "spotify:episode:ep1",
                name = "Why Do Stars Twinkle in the Night Sky?",
                description = "Astronomer Dr. Maya Lin joins kid co-host Leo to explore starlight.",
                durationMs = 1_680_000L,
                releaseDate = "2026-10-28",
                images = img("planets"),
                show = AlbumRef("spotify:show:brainson", "Brains On! Science Podcast", img("planets")),
                playedPositionMs = 620_000L,
                played = false,
                explicit = false,
                playable = true,
                previewUrl = null,
            ),
            Episode(
                uri = "spotify:episode:ep2",
                name = "How Whales Sing Across Entire Oceans",
                description = "Dive beneath the waves to hear humpback whale songs.",
                durationMs = 1_440_000L,
                releaseDate = "2026-10-21",
                images = img("study"),
                show = AlbumRef("spotify:show:brainson", "Brains On! Science Podcast", img("study")),
                playedPositionMs = 0L,
                played = false,
                explicit = false,
                playable = true,
                previewUrl = null,
            ),
            Episode(
                uri = "spotify:episode:ep3",
                name = "The Secret Life of Honeybees",
                description = "How honeybees dance to share directions to wildflowers.",
                durationMs = 1_320_000L,
                releaseDate = "2026-10-14",
                images = img("carnival"),
                show = AlbumRef("spotify:show:brainson", "Wow in the World", img("carnival")),
                playedPositionMs = 1_320_000L,
                played = true,
                explicit = false,
                playable = true,
                previewUrl = null,
            ),
        )
        val categories = listOf(
            Category("spotify:genre:classical", "Classical", img("classical"), "#7C3AED"),
            Category("spotify:genre:soundtracks", "Soundtracks", img("dragon"), "#DC2626"),
            Category("spotify:genre:kids", "Kids & Family", img("roadtrip"), "#EA580C"),
            Category("spotify:genre:instrumental", "Instrumental", img("ghibli"), "#0F766E"),
            Category("spotify:genre:sleep", "Sleep & Lullabies", img("bedtime"), "#1D4ED8"),
            Category("spotify:genre:focus", "Study & Focus", img("study"), "#0891B2"),
            Category("spotify:genre:acoustic", "Acoustic Folk", img("acoustic"), "#059669"),
            Category("spotify:genre:nature", "Nature Sounds", img("carnival"), "#15803D"),
        )
        val whatsNewItems = listOf(
            WhatsNewItem("wn1", WhatsNewState.NEW, album = apiAlbums[0]),
            WhatsNewItem("wn2", WhatsNewState.NEW, episode = apiEpisodes[0]),
            WhatsNewItem("wn3", WhatsNewState.SEEN, stateAt = "2020-01-01T00:00:00Z", album = apiAlbums[1]),
            WhatsNewItem("wn4", WhatsNewState.SEEN, stateAt = "2020-01-01T00:00:00Z", episode = apiEpisodes[1]),
        )
        val todayMs = 1_793_404_800_000L
        val yesterdayMs = todayMs - 86_400_000L
        val recentEntries = listOf(
            RecentEntry(
                item = RecentItem("spotify:playlist:ghibli", MediaKind.PLAYLIST, "Studio Ghibli Piano", "Parent Picks", img("ghibli"), playedAtMs = todayMs, typeLabel = "Playlist"),
                childrenGroup = "g1",
                groupCount = 2,
            ),
            RecentEntry(
                item = RecentItem("spotify:track:onesummersday", MediaKind.TRACK, "One Summer's Day", "Joe Hisaishi", img("ghibli"), durationMs = 249_000L, playedAtMs = todayMs),
                group = "g1",
            ),
            RecentEntry(
                item = RecentItem("spotify:album:planets", MediaKind.ALBUM, "The Planets, Op. 32", "Gustav Holst", img("planets"), playedAtMs = yesterdayMs, typeLabel = "Album"),
                childrenGroup = "g2",
                groupCount = 3,
            ),
            RecentEntry(
                item = RecentItem("spotify:track:jupiter", MediaKind.TRACK, "Jupiter, the Bringer of Jollity", "Gustav Holst", img("planets"), durationMs = 478_000L, playedAtMs = yesterdayMs),
                group = "g2",
            ),
        )
        val topContent = TopContent(
            artists = listOf(
                Artist("spotify:artist:holst", "Gustav Holst", img("planets"), monthlyListeners = 2_845_120L, verified = true),
                Artist("spotify:artist:hisaishi", "Joe Hisaishi", img("ghibli"), monthlyListeners = 6_410_000L, verified = true),
                Artist("spotify:artist:debussy", "Claude Debussy", img("debussy"), monthlyListeners = 8_120_000L, verified = true),
            ),
            tracks = listOf(
                Track("spotify:track:jupiter", "Jupiter, the Bringer of Jollity", 478_000L, listOf(ArtistRef("spotify:artist:holst", "Gustav Holst")), AlbumRef("spotify:album:planets", "The Planets", img("planets"))),
                Track("spotify:track:onesummersday", "One Summer's Day", 249_000L, listOf(ArtistRef("spotify:artist:hisaishi", "Joe Hisaishi")), AlbumRef("spotify:album:ghibli", "Spirited Away", img("ghibli"))),
                Track("spotify:track:debussy", "Clair de Lune", 304_000L, listOf(ArtistRef("spotify:artist:debussy", "Claude Debussy")), AlbumRef("spotify:album:bergamasque", "Suite bergamasque", img("debussy"))),
            ),
            artistTotal = 3,
            trackTotal = 3,
        )
        val devicesFlow = MutableStateFlow(
            listOf(
                Device("self-phone", "This Phone (Emma's Pixel)", "SMARTPHONE", active = true, volume = 0.65, local = true),
                Device("living-room", "Living Room Kitchen Speaker", "SPEAKER", active = false, volume = 0.45, local = false),
                Device("bedroom-mini", "Emma's Bedroom Speaker", "SPEAKER", active = false, volume = 0.35, local = false),
            ),
        )
        val readyFlow = MutableStateFlow(true)

        val musicFeedAlbums = HomeFeed(
            greeting = "Good afternoon",
            filters = listOf(HomeFilter.ALL, HomeFilter.MUSIC),
            shelves = listOf(
                Shelf(
                    uri = "spotify:section:new-releases",
                    title = "Latest releases",
                    items = apiAlbums.reversed().map { a ->
                        MediaCard(
                            uri = a.uri,
                            kind = MediaKind.ALBUM,
                            title = a.name,
                            subtitle = a.artists.joinToString(", ") { it.name },
                            images = a.images,
                        )
                    },
                    total = apiAlbums.size,
                ),
            ),
        )
        val homeProxy = stub(Home::class.java) { m, args ->
            when {
                m.name == "feed" && args?.firstOrNull() == HomeFilter.MUSIC -> musicFeedAlbums
                m.name == "categories" -> categories
                m.name == "whatsNew" -> Page(apiAlbums, 0, apiAlbums.size, null)
                m.name == "whatsNewFeed" -> Page(whatsNewItems, 0, whatsNewItems.size, null)
                m.name == "markWhatsNewSeen" -> Unit
                else -> StubDefault
            }
        }
        val podcastsProxy = stub(Podcasts::class.java) { m, _ ->
            when (m.name) {
                "savedEpisodes" -> Page(apiEpisodes, 0, apiEpisodes.size, null)
                "episodes" -> Page(apiEpisodes.filter { !it.played }, 0, 2, null)
                else -> StubDefault
            }
        }
        val libraryProxy = stub(Library::class.java) { m, args ->
            when {
                m.name == "items" && args?.firstOrNull() == LibraryFilter.PODCASTS -> {
                    val shows = listOf(
                        LibraryItem("spotify:show:brainson", MediaKind.SHOW, "Brains On! Science Podcast", "American Public Media", img("planets"), pinned = false, addedAt = "2026-10-01"),
                    )
                    Page(shows, 0, shows.size, null)
                }
                m.name == "contains" -> {
                    val list = (args?.firstOrNull() as? List<*>) ?: emptyList<Any>()
                    list.map { true }
                }
                else -> StubDefault
            }
        }
        val usersProxy = stub(Users::class.java) { m, _ ->
            when (m.name) {
                "me" -> Account("spotify:user:family", "family_account", "Emma's Music", img("ghibli"), premium = true, country = "DE")
                "top" -> topContent
                else -> StubDefault
            }
        }
        val recentsProxy = stub(Recents::class.java) { m, _ ->
            when (m.name) {
                "recentEntries" -> Page(recentEntries, 0, recentEntries.size, null)
                else -> StubDefault
            }
        }
        val extrasProxy = stub(TrackExtras::class.java) { m, _ ->
            when (m.name) {
                "credits" -> Credits(
                    listOf(
                        CreditRole("Composer", listOf(ArtistRef("spotify:artist:holst", "Gustav Holst"))),
                        CreditRole("Orchestra", listOf(ArtistRef("spotify:artist:lso", "London Symphony Orchestra"))),
                    ),
                )
                else -> StubDefault
            }
        }
        val devicesProxy = stub(Devices::class.java) { m, _ ->
            when (m.name) {
                "getDevices" -> devicesFlow
                "refresh" -> Unit
                else -> StubDefault
            }
        }
        val apiProxy = stub(SoundNestApi::class.java) { m, _ ->
            when (m.name) {
                "getReady" -> readyFlow
                "getHome" -> homeProxy
                "getPodcasts" -> podcastsProxy
                "getLibrary" -> libraryProxy
                "getUsers" -> usersProxy
                "getRecents" -> recentsProxy
                "getExtras" -> extrasProxy
                "getDevices" -> devicesProxy
                else -> StubDefault
            }
        }
        SoundNestRuntime.useApi(apiProxy)
    }

    // ── Shell Wrappers ──────────────────────────────────────────────────────────

    @Composable
    private fun ClassicShell(route: String, capped: Boolean, page: @Composable (NavHostController) -> Unit) {
        val nav = rememberNavController()
        val large = windowSize().large
        val ctx = LocalContext.current
        val tick by ParentSecurityManager.sessionTick.collectAsState()
        val showParentBanner = androidx.compose.runtime.remember(tick) {
            ParentSecurityManager.adminRemainingMs(ctx) > 0L
        }
        val routes = listOf(Routes.Home.route, Routes.Search.route, Routes.Library.route, Routes.Category.route, Routes.NewReleases.route, "detail")
        Column(Modifier.fillMaxSize()) {
            if (showParentBanner) {
                ParentAdminSessionBanner(onOpenParentSettings = {})
            }
            CompositionLocalProvider(LocalTopStatusBarConsumed provides showParentBanner) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .then(if (showParentBanner) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier),
                ) {
                    Row(Modifier.fillMaxSize()) {
                        if (windowSize().navigationRail) MainNavigationRail(nav)
                        Scaffold(
                            modifier = Modifier.weight(1f),
                            contentWindowInsets = WindowInsets(0, 0, 0, 0),
                            bottomBar = { MainBottomNavigation(nav) },
                        ) { _ ->
                            NavHost(nav, startDestination = if (route in routes) route else "detail") {
                                routes.forEach { r ->
                                    composable(r) {
                                        CompositionLocalProvider(LocalViewModelStoreOwner provides hostActivity) {
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
            }
        }
    }

    @Composable
    private fun ImmersiveShell(route: String, capped: Boolean, page: @Composable (NavHostController) -> Unit) {
        val nav = rememberNavController()
        val large = windowSize().large
        val chrome = rememberImmersiveChromeState()
        val ctx = LocalContext.current
        val tick by ParentSecurityManager.sessionTick.collectAsState()
        val showParentBanner = androidx.compose.runtime.remember(tick) {
            ParentSecurityManager.adminRemainingMs(ctx) > 0L
        }
        val routes = listOf(Routes.Home.route, Routes.Search.route, Routes.Library.route, Routes.Category.route, Routes.NewReleases.route, Routes.Queue.route, "detail")
        CompositionLocalProvider(LocalImmersiveChrome provides chrome) {
            Column(Modifier.fillMaxSize()) {
                if (showParentBanner) {
                    ParentAdminSessionBanner(onOpenParentSettings = {})
                }
                CompositionLocalProvider(LocalTopStatusBarConsumed provides showParentBanner) {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .then(if (showParentBanner) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier),
                    ) {
                        NavHost(nav, startDestination = if (route in routes) route else "detail") {
                            routes.forEach { r ->
                                composable(r) {
                                    CompositionLocalProvider(LocalViewModelStoreOwner provides hostActivity) {
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
        }
    }

    private fun bareHost() {
        val unsafe = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        val host = unsafe.javaClass.getMethod("allocateInstance", Class::class.java)
            .invoke(unsafe, WebPlayerHost::class.java) as WebPlayerHost
        val ctx = RuntimeEnvironment.getApplication()
        val store = AccountStore(File(ctx.filesDir, "accounts.json"))
        fun setHostField(name: String, value: Any?) {
            WebPlayerHost::class.java.getDeclaredField(name).apply { isAccessible = true }.set(host, value)
        }
        setHostField("accounts", store)
        setHostField("profile", io.github.thake.soundnest.host.android.AccountList.DEFAULT_PROFILE)
        setHostField("_loginRequired", MutableStateFlow(false))
        setHostField("_online", MutableStateFlow(true))
        setHostField("_url", MutableStateFlow("https://open.spotify.com/"))
        setHostField("_licenseLimitedUntil", MutableStateFlow(0L))
        SoundNestRuntime.host = host

        val bridgeMessages = MutableSharedFlow<String>(extraBufferCapacity = 16)
        val callIdRegex = Regex("""window\.Myspoti\.call\((\d+),""")
        val fakeBridgeHost = object : io.github.thake.soundnest.spotify.bridge.BridgeHost {
            override val messages: Flow<String> = bridgeMessages
            override suspend fun evaluate(script: String) {}
            override suspend fun evaluateValue(script: String): String {
                if (script.contains("\"player.audioInfo\"")) {
                    val id = callIdRegex.find(script)?.groupValues?.getOrNull(1)?.toIntOrNull()
                    if (id != null) {
                        bridgeMessages.tryEmit(
                            """{"type":"result","id":$id,"ok":true,"value":{"bitrate":320000,"mime":"audio/ogg; codecs=vorbis"}}""",
                        )
                        return "true"
                    }
                }
                return "false"
            }
        }
        val job = kotlinx.coroutines.SupervisorJob()
        spotifyJob = job
        val scope = kotlinx.coroutines.CoroutineScope(job + kotlinx.coroutines.Dispatchers.Unconfined)
        bridgeMessages.tryEmit("""{"type":"ready"}""")
        val fakeSpotify = io.github.thake.soundnest.spotify.SpotifyApi(fakeBridgeHost, scope)
        bridgeMessages.tryEmit("""{"type":"ready"}""")
        SoundNestRuntime::class.java.getDeclaredField("spotify").apply { isAccessible = true }
            .set(SoundNestRuntime, fakeSpotify)
    }

    private object StubDefault

    @Suppress("UNCHECKED_CAST")
    private fun <T> stub(
        type: Class<T>,
        override: ((java.lang.reflect.Method, Array<out Any?>?) -> Any?)? = null,
    ): T = Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, m, args ->
        if (override != null) {
            val custom = override(m, args)
            if (custom !== StubDefault) return@newProxyInstance custom
        }
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
