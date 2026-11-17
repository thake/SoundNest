/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.spokenword.audible.settings

import io.github.thake.soundnest.feature.parental.security.ParentSecurityManager
import io.github.thake.soundnest.feature.parental.ui.ParentSettingsScreen
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore
import io.github.thake.soundnest.feature.speakers.settings.TrustedNetworkGate

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.navigation.compose.rememberNavController
import io.github.thake.soundnest.audible.AudibleAuth
import io.github.thake.soundnest.audible.AudibleBook
import io.github.thake.soundnest.feature.spokenword.audible.data.AudibleCatalogCache
import io.github.thake.soundnest.audible.AudibleMarketplace
import io.github.thake.soundnest.feature.spokenword.audible.data.AudibleProgressStore
import io.github.thake.soundnest.audible.AudibleSessionTokens
import io.github.thake.soundnest.feature.spokenword.audible.ui.AudiblePickerSheetBody
import io.github.thake.soundnest.core.ui.classic.SpotuiTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AudibleParentSettingsUiTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        io.github.thake.soundnest.core.di.Graph::class.java.getDeclaredField("app").apply { isAccessible = true }.set(null, app)
        ParentSecurityManager.resetForTests(app)
        ParentalPolicyStore.resetForTests(app)
        TrustedNetworkGate.resetForTests(app)
        AudibleCredentialStore.clear(app)
        AudibleCredentialStore.resetMemoryForTests()
        AudibleCatalogCache.clear(app)
        AudibleCatalogCache.resetMemoryForTests()
        AudibleProgressStore.clearAll(app)
        AudibleProgressStore.resetMemoryForTests()
    }

    @After
    fun tearDown() {
        val app = RuntimeEnvironment.getApplication()
        AudibleCredentialStore.clear(app)
        AudibleCredentialStore.resetMemoryForTests()
        AudibleCatalogCache.clear(app)
        AudibleCatalogCache.resetMemoryForTests()
        AudibleProgressStore.clearAll(app)
        AudibleProgressStore.resetMemoryForTests()
        ParentSecurityManager.resetForTests(app)
        ParentalPolicyStore.resetForTests(app)
        TrustedNetworkGate.resetForTests(app)
    }

    @Test
    fun `disconnected card auto-defaults marketplace from locale and lets parent override before sign-in`() {
        var startedMarketplace: AudibleMarketplace? = null

        rule.setContent {
            SpotuiTheme {
                AudibleParentControlsCard(
                    audiobooksEnabled = true,
                    defaultLocale = Locale.GERMANY,
                    onOpenPicker = {},
                    onStartSignIn = { startedMarketplace = it },
                )
            }
        }

        // Auto-defaulted to Germany (audible.de)
        rule.onNodeWithText("Germany / DACH (audible.de)", substring = true).assertIsDisplayed()

        // Parent overrides marketplace to United Kingdom before signing in
        rule.onNodeWithTag("audible_marketplace_selector").performClick()
        rule.onNodeWithTag("audible_marketplace_option_uk").performClick()
        rule.onNodeWithText("United Kingdom (audible.co.uk)", substring = true).assertIsDisplayed()

        rule.onNodeWithTag("audible_sign_in_button").performClick()
        assertEquals(AudibleMarketplace.UK, startedMarketplace)
    }

    @Test
    fun `redirect interception extracts auth code on maplanding and immediately wipes WebView cookies`() {
        var wipedCount = 0

        // Non-maplanding Amazon URL must not wipe session prematurely
        val step1 = AudibleAccountCoordinator.interceptRedirectUrl(
            url = "https://www.amazon.de/ap/signin?openid.mode=checkid_setup",
            wipeLoginProfile = { wipedCount++ },
        )
        assertEquals(AudibleAuth.MapLandingResult.NotMapLanding, step1)
        assertEquals(0, wipedCount)

        // Final /ap/maplanding redirect extracts code and wipes login WebView cookies/storage
        val step2 = AudibleAccountCoordinator.interceptRedirectUrl(
            url = "https://www.audible.de/ap/maplanding?openid.oa2.authorization_code=ANcode12345",
            wipeLoginProfile = { wipedCount++ },
        )
        assertEquals(AudibleAuth.MapLandingResult.Authorized("ANcode12345"), step2)
        assertEquals(1, wipedCount)
    }

    @Test
    fun `connected card displays marketplace, counts, Manage Approved Books, Sync Library, and Disconnect`() {
        val app = RuntimeEnvironment.getApplication()
        AudibleCredentialStore.save(
            app,
            AudibleSessionTokens(
                marketplace = AudibleMarketplace.DE,
                deviceSerial = "SERIAL123",
                adpToken = "adp_tok",
                devicePrivateKeyPem = "-----BEGIN RSA PRIVATE KEY-----\nMIIB\n-----END RSA PRIVATE KEY-----",
                refreshToken = "refresh_tok",
                accessToken = "access_tok",
                customerName = "Parent Account",
            ),
        )
        AudibleCatalogCache.saveBooks(
            app,
            listOf(
                AudibleBook(asin = "B001KIDS01", title = "Jim Knopf", authors = listOf("Michael Ende"), runtimeLengthMs = 360_000L),
                AudibleBook(asin = "B002KIDS02", title = "Momo", authors = listOf("Michael Ende"), runtimeLengthMs = 540_000L),
            ),
        )
        ParentalPolicyStore.setAudibleBookAllowed(app, "B001KIDS01", true, title = "Jim Knopf")
        ParentalPolicyStore.setAudibleBookPinned(app, "B001KIDS01", true)

        var manageClicked = false
        var syncClicked = false

        rule.setContent {
            SpotuiTheme {
                AudibleParentControlsCard(
                    audiobooksEnabled = true,
                    onOpenPicker = { manageClicked = true },
                    onSyncLibraryOverride = {
                        syncClicked = true
                        Result.success(2)
                    },
                    onDisconnectOverride = {
                        AudibleAccountCoordinator.disconnect(app, deregister = { true })
                    },
                )
            }
        }

        rule.onNodeWithText("audible.de", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Parent Account", substring = true).assertIsDisplayed()
        rule.onNodeWithTag("audible_manage_books_button").performClick()
        assertTrue(manageClicked)

        rule.onNodeWithTag("audible_sync_library_button").performClick()
        assertTrue(syncClicked)

        rule.onNodeWithTag("audible_disconnect_button").performClick()
        rule.waitForIdle()
        assertFalse(AudibleCredentialStore.isConnected(app))
        assertTrue(AudibleCatalogCache.books(app).isEmpty())
    }

    @Test
    fun `picker sheet filters by search, renders 18+ badge on adult books, and toggles Allow and Pin separately`() {
        val app = RuntimeEnvironment.getApplication()
        AudibleCatalogCache.saveBooks(
            app,
            listOf(
                AudibleBook(
                    asin = "B001KIDS01",
                    title = "Paddington Bear",
                    authors = listOf("Michael Bond"),
                    runtimeLengthMs = 18_000_000L,
                    isAdultProduct = false,
                ),
                AudibleBook(
                    asin = "B009ADULT9",
                    title = "Midnight Thriller",
                    authors = listOf("Adult Author"),
                    runtimeLengthMs = 36_000_000L,
                    isAdultProduct = true,
                ),
            ),
        )

        rule.setContent {
            SpotuiTheme {
                AudiblePickerSheetBody(onDismiss = {})
            }
        }

        // Adult product displays 18+ warning badge; children's book does not
        rule.onNodeWithTag("audible_adult_badge_B009ADULT9").assertIsDisplayed()
        rule.onNodeWithTag("audible_adult_badge_B001KIDS01").assertDoesNotExist()

        // Toggle Allow on Paddington Bear -> allowed, but NOT auto-pinned to Home
        rule.onNodeWithTag("audible_allow_toggle_B001KIDS01").performClick()
        assertTrue("B001KIDS01" in ParentalPolicyStore.current(app).spokenWord.audible.allowedAsins)
        assertFalse("B001KIDS01" in ParentalPolicyStore.current(app).spokenWord.audible.pinnedAsins)

        // Toggle separate Pin to Home control on Paddington Bear -> pinned
        rule.onNodeWithTag("audible_pin_toggle_B001KIDS01").performClick()
        assertTrue("B001KIDS01" in ParentalPolicyStore.current(app).spokenWord.audible.pinnedAsins)

        // Un-allowing automatically clears Home pin too
        rule.onNodeWithTag("audible_allow_toggle_B001KIDS01").performClick()
        assertFalse("B001KIDS01" in ParentalPolicyStore.current(app).spokenWord.audible.allowedAsins)
        assertFalse("B001KIDS01" in ParentalPolicyStore.current(app).spokenWord.audible.pinnedAsins)

        // Search filter narrows list to matching title/author
        rule.onNodeWithTag("audible_picker_search").performTextInput("Paddington")
        rule.onNodeWithText("Paddington Bear").assertIsDisplayed()
        rule.onNodeWithText("Midnight Thriller").assertDoesNotExist()
    }

    @Test
    fun `ParentSettingsScreen Home Customization exposes separate Pinned Audible Books on Home switch`() {
        val app = RuntimeEnvironment.getApplication()
        ParentSecurityManager.setupInitialPin(app, "1234")
        assertTrue(ParentSecurityManager.isAdminUnlocked(app))
        val listState = LazyListState()

        rule.setContent {
            SpotuiTheme {
                val nav = rememberNavController()
                ParentSettingsScreen(navController = nav, listState = listState)
            }
        }

        // Scroll to Home Sections card (index 2 in LazyColumn)
        rule.onNodeWithTag("parent_settings_list").performScrollToIndex(2)
        rule.onNodeWithTag("parent_home_pinned_audible_toggle").assertIsOn().performClick()
        assertFalse(ParentalPolicyStore.current(app).spokenWord.audible.showPinnedOnHome)
        rule.onNodeWithTag("parent_home_pinned_audible_toggle").assertIsOff()
    }
}
