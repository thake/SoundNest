/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.core.localization

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import io.github.thake.soundnest.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de")
class GermanLocalizationUiTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `german strings resolve with accurate translations`() {
        val context: Context = ApplicationProvider.getApplicationContext()

        assertEquals("Kindersicherung", context.getString(R.string.parent_settings_title))
        assertEquals("Kindersicherung", context.getString(R.string.menu_parental_controls_title))
        assertEquals("Eltern-PIN / Passwort erforderlich", context.getString(R.string.parent_pin_dialog_title))
        assertEquals("Künstler*in", context.getString(R.string.entity_artist))
        assertEquals("Bibliothek synchronisieren", context.getString(R.string.audible_sync_library_button))
        assertEquals("Mit Audible anmelden", context.getString(R.string.audible_sign_in_button))
        assertEquals("Von der Kindersicherung blockiert", context.getString(R.string.blocked_by_parental_controls))
    }

    @Test
    fun `german plurals resolve with correct singular and plural grammar`() {
        val context: Context = ApplicationProvider.getApplicationContext()

        val onePlaylist = context.resources.getQuantityString(R.plurals.parent_rules_summary_playlists, 1, 1)
        val fivePlaylists = context.resources.getQuantityString(R.plurals.parent_rules_summary_playlists, 5, 5)
        assertEquals("1 Playlist", onePlaylist)
        assertEquals("5 Playlists", fivePlaylists)

        val oneArtist = context.resources.getQuantityString(R.plurals.parent_rules_summary_artists, 1, 1)
        val threeArtists = context.resources.getQuantityString(R.plurals.parent_rules_summary_artists, 3, 3)
        assertEquals("1 Künstler", oneArtist)
        assertEquals("3 Künstler", threeArtists)
    }

    @Test
    fun `compose renders localized german text without english fallback`() {
        composeTestRule.setContent {
            Text(text = stringResource(R.string.parent_settings_title))
        }

        composeTestRule.onNodeWithText("Kindersicherung").assertIsDisplayed()
        assertFalse(composeTestRule.onAllNodes(androidx.compose.ui.test.hasText("Parental Controls")).fetchSemanticsNodes().isNotEmpty())
    }
}
