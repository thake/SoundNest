/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.core.ui.navigation

import io.github.thake.soundnest.feature.localfiles.settings.LocalFilesParentalPolicy
import io.github.thake.soundnest.feature.radio.settings.RadioParentalPolicy

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.thake.soundnest.feature.localfiles.settings.LocalFilesSettings
import io.github.thake.soundnest.feature.appearance.settings.Look
import io.github.thake.soundnest.core.di.Graph
import io.github.thake.soundnest.feature.parental.security.AppSurface
import io.github.thake.soundnest.feature.parental.settings.FilterMode
import io.github.thake.soundnest.feature.home.settings.HomeParentalPolicy
import io.github.thake.soundnest.feature.parental.security.ParentSecurityManager
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore
import io.github.thake.soundnest.core.ui.look.ProvideWindowSize
import io.github.thake.soundnest.core.ui.look.LocalLook
import io.github.thake.soundnest.feature.system.ui.AboutScreen
import io.github.thake.soundnest.core.ui.classic.SpotuiTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class MenuDrawerTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var appContext: Context

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        appContext = app
        Graph::class.java.getDeclaredField("app").apply { isAccessible = true }.set(null, app)
        ParentSecurityManager.resetForTests(app)
        ParentalPolicyStore.resetForTests(app)
    }

    @After
    fun tearDown() {
        ParentSecurityManager.resetForTests(appContext)
        ParentalPolicyStore.resetForTests(appContext)
    }

    @Test
    fun topLeftAvatarOpens90PercentWidthDrawerAndRight10PercentScrimDismisses() {
        var capturedNav: NavHostController? = null

        rule.setContent {
            SpotuiTheme {
                CompositionLocalProvider(LocalLook provides Look.CLASSIC) {
                    ProvideWindowSize {
                        val nav = rememberNavController().also { capturedNav = it }
                        MenuNavigationDrawer(navController = nav) {
                            NavHost(navController = nav, startDestination = Routes.Home.route) {
                                composable(Routes.Home.route) {
                                    Column(Modifier.fillMaxSize().background(Color.DarkGray)) {
                                        ClassicMenuAvatarButton()
                                        Text("Home Feed Content")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        assertNotNull(capturedNav)
        assertEquals(0, rule.onAllNodesWithText("QUICK ACCESS").fetchSemanticsNodes().size)

        // Tap the top-left avatar to fly in the left-sliding drawer.
        rule.onNodeWithTag("top_left_menu_avatar").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Menu").assertIsDisplayed()
        rule.onNodeWithText("QUICK ACCESS").assertIsDisplayed()
        rule.onNodeWithText("Recents").assertIsDisplayed()
        rule.onNodeWithText("Your recap").assertIsDisplayed()

        // Verify exact 9/10ths (90%) width geometry on a phone viewport, leaving 1/10th peeking on the right.
        val rootBounds = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val sheetBounds = rule.onNodeWithTag("menu_drawer_sheet").fetchSemanticsNode().boundsInRoot
        val ratio = sheetBounds.width / rootBounds.width
        assertEquals("Drawer width should be 90% (9/10ths) of screen width", 0.90f, ratio, 0.02f)

        // Tap inside the 1/10th (right 10%) scrim peek strip at x = 95% of root width to dismiss.
        rule.onRoot().performTouchInput {
            click(Offset(x = rootBounds.width * 0.95f, y = rootBounds.height * 0.50f))
        }
        rule.waitForIdle()

        assertEquals(0, rule.onAllNodesWithText("QUICK ACCESS").fetchSemanticsNodes().size)
    }

    @Test
    fun immersiveDrawerNavigatesToFullScreenAboutAndClosesDrawer() {
        var currentRoute: String? = null

        rule.setContent {
            SpotuiTheme {
                CompositionLocalProvider(LocalLook provides Look.IMMERSIVE) {
                    ProvideWindowSize {
                        val nav = rememberNavController()
                        val drawerState = rememberDrawerState(DrawerValue.Closed)
                        MenuNavigationDrawer(navController = nav, drawerState = drawerState) {
                            NavHost(navController = nav, startDestination = Routes.Home.route) {
                                composable(Routes.Home.route) {
                                    currentRoute = Routes.Home.route
                                    Box(Modifier.fillMaxSize()) {
                                        ImmersiveMenuAvatarButton()
                                    }
                                }
                                composable(Routes.About.route) {
                                    currentRoute = Routes.About.route
                                    AboutScreen(navController = nav)
                                }
                            }
                        }
                    }
                }
            }
        }

        rule.onNodeWithTag("top_left_menu_avatar").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("menu_drawer_list")
            .performScrollToNode(hasText("About SoundNest"))
        rule.onNodeWithText("About SoundNest").performClick()
        rule.waitForIdle()

        assertEquals(Routes.About.route, currentRoute)
        assertEquals(0, rule.onAllNodesWithText("QUICK ACCESS").fetchSemanticsNodes().size)
        rule.onNodeWithText("Source Code (GPL-3.0)").assertIsDisplayed()
        rule.onNodeWithText("Upstream Attribution").assertIsDisplayed()
    }

    @Test
    fun parentalPolicyHidesDisallowedExploreItemsAndPinGatesProtectedRows() {
        ParentSecurityManager.setupInitialPin(appContext, "2468")
        ParentSecurityManager.lockNow(appContext)
        LocalFilesSettings.init(appContext)
        ParentalPolicyStore.update(appContext) {
            ParentalPolicy(
                filterMode = FilterMode.STRICT_ALLOWLIST,
                localFiles = LocalFilesParentalPolicy(enabled = false),
                radio = RadioParentalPolicy(enabled = false),
                home = HomeParentalPolicy(
                    minimalHomeMode = true,
                    showNewReleases = false,
                ),
            )
        }

        var openedParentSettings = false

        rule.setContent {
            SpotuiTheme {
                CompositionLocalProvider(LocalLook provides Look.CLASSIC) {
                    ProvideWindowSize {
                        val nav = rememberNavController()
                        val drawerState = rememberDrawerState(DrawerValue.Open)
                        MenuNavigationDrawer(navController = nav, drawerState = drawerState) {
                            NavHost(navController = nav, startDestination = Routes.Home.route) {
                                composable(Routes.Home.route) { Text("Home") }
                                composable(Routes.ParentSettings.route) {
                                    openedParentSettings = true
                                    Text("Parent Settings")
                                }
                            }
                        }
                    }
                }
            }
        }

        rule.waitForIdle()

        // Disallowed What's New, Local Files, Live Radio, and Equalizer must be filtered out of Explore & Audio.
        assertEquals(0, rule.onAllNodesWithText("What's New").fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithText("Local Files").fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithText("Equalizer").fetchSemanticsNodes().size)

        // Enabling Live Radio (or Local Files) reveals Equalizer in Explore & Audio.
        ParentalPolicyStore.update(appContext) { it.copy(radio = RadioParentalPolicy(enabled = true)) }
        rule.waitForIdle()
        rule.onNodeWithTag("menu_drawer_list")
            .performScrollToNode(hasText("Equalizer"))
        rule.onNodeWithText("Equalizer").assertIsDisplayed()

        ParentalPolicyStore.update(appContext) { it.copy(radio = RadioParentalPolicy(enabled = false)) }
        rule.waitForIdle()
        assertEquals(0, rule.onAllNodesWithText("Equalizer").fetchSemanticsNodes().size)

        // Tapping Parental Controls while locked triggers PIN prompt rather than navigating directly.
        assertNull(ParentSecurityManager.pendingPrompt.value)
        rule.onNodeWithTag("menu_drawer_list")
            .performScrollToNode(hasText("Parental Controls"))
        rule.onNodeWithText("Parental Controls").performClick()
        rule.waitForIdle()

        assertFalse(openedParentSettings)
        val prompt = ParentSecurityManager.pendingPrompt.value
        assertNotNull(prompt)
        assertEquals(AppSurface.PARENT_CONTROLS, prompt?.surface)

        // Submitting the valid PIN authorizes navigation and closes the drawer.
        ParentSecurityManager.submitPromptPin(appContext, "2468")
        rule.waitForIdle()
        assertTrue(openedParentSettings)
    }

    @Test
    fun unlockedAdminSessionShowsParentModeActiveAndQuickLockLocksImmediately() {
        ParentSecurityManager.setupInitialPin(appContext, "1357")
        assertTrue(ParentSecurityManager.isAdminUnlocked(appContext))

        rule.setContent {
            SpotuiTheme {
                CompositionLocalProvider(LocalLook provides Look.IMMERSIVE) {
                    ProvideWindowSize {
                        val nav = rememberNavController()
                        val drawerState = rememberDrawerState(DrawerValue.Open)
                        MenuNavigationDrawer(navController = nav, drawerState = drawerState) {
                            Box(Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }

        rule.waitForIdle()
        rule.onNodeWithText("Parent Mode Active").assertIsDisplayed()
        rule.onNodeWithText("Lock").assertIsDisplayed()

        rule.onNodeWithText("Lock").performClick()
        rule.waitForIdle()

        assertFalse(ParentSecurityManager.isAdminUnlocked(appContext))
        assertEquals(0, rule.onAllNodesWithText("Parent Mode Active").fetchSemanticsNodes().size)
    }

    @Test
    fun hiddenSongsRowIsChildAccessibleWhenLocked() {
        ParentSecurityManager.setupInitialPin(appContext, "2468")
        ParentSecurityManager.lockNow(appContext)

        var openedHiddenSongs = false

        rule.setContent {
            SpotuiTheme {
                CompositionLocalProvider(LocalLook provides Look.CLASSIC) {
                    ProvideWindowSize {
                        val nav = rememberNavController()
                        val drawerState = rememberDrawerState(DrawerValue.Open)
                        MenuNavigationDrawer(navController = nav, drawerState = drawerState) {
                            NavHost(navController = nav, startDestination = Routes.Home.route) {
                                composable(Routes.Home.route) { Text("Home") }
                                composable(Routes.HiddenSongs.route) {
                                    openedHiddenSongs = true
                                    Text("Hidden Songs")
                                }
                            }
                        }
                    }
                }
            }
        }

        rule.waitForIdle()

        // Tapping Hidden songs while locked navigates directly without opening a PIN prompt
        assertNull(ParentSecurityManager.pendingPrompt.value)
        rule.onNodeWithTag("menu_drawer_list")
            .performScrollToNode(hasText("Hidden songs"))
        rule.onNodeWithText("Hidden songs").performClick()
        rule.waitForIdle()

        assertNull(ParentSecurityManager.pendingPrompt.value)
        assertTrue(openedHiddenSongs)
    }
}
