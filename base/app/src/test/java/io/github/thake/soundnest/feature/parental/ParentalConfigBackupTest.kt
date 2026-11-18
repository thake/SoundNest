/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.parental

import io.github.thake.soundnest.feature.home.settings.HomeParentalPolicy
import io.github.thake.soundnest.feature.library.settings.LibraryParentalPolicy
import io.github.thake.soundnest.feature.localfiles.settings.LocalFilesParentalPolicy
import io.github.thake.soundnest.feature.lyrics.settings.LyricsParentalPolicy
import io.github.thake.soundnest.feature.parental.security.*
import io.github.thake.soundnest.feature.player.settings.PlaybackParentalPolicy
import io.github.thake.soundnest.feature.radio.settings.RadioParentalPolicy
import io.github.thake.soundnest.feature.speakers.jam.settings.JamParentalPolicy
import io.github.thake.soundnest.feature.spokenword.audible.settings.AudibleParentalPolicy
import io.github.thake.soundnest.feature.spokenword.podcasts.settings.PodcastsParentalPolicy
import io.github.thake.soundnest.feature.spokenword.settings.SpokenWordParentalPolicy

import io.github.thake.soundnest.feature.parental.settings.*
import io.github.thake.soundnest.feature.speakers.settings.*

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.thake.soundnest.feature.speakers.settings.CustomSpeakerNames
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ParentalConfigBackupTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ParentSecurityManager.resetForTests(context)
        ParentalPolicyStore.resetForTests(context)
        TrustedNetworkGate.resetForTests(context)
        CustomSpeakerNames.clearAll(context)
    }

    @After
    fun tearDown() {
        ParentSecurityManager.resetForTests(context)
        ParentalPolicyStore.resetForTests(context)
        TrustedNetworkGate.resetForTests(context)
        CustomSpeakerNames.clearAll(context)
    }

    @Test
    fun `round-trip JSON backup restores policy, home sections, trusted network, and speaker names without exporting or overwriting PIN`() {
        assertEquals(PinSetupResult.Success, ParentSecurityManager.setupInitialPin(context, "654321"))
        ParentSecurityManager.setBiometricEnabled(context, true)

        val richPolicy = ParentalPolicy(
            filterMode = FilterMode.STRICT_ALLOWLIST,
            blockExplicit = true,
            allowedTrackIds = setOf("trk1", "trk2"),
            allowedAlbumIds = setOf("alb1"),
            allowedArtistIds = setOf("art1"),
            allowedPlaylistIds = setOf("pl1"),
            blockedTrackIds = setOf("bad_trk"),
            blockedAlbumIds = setOf("bad_alb"),
            blockedArtistIds = setOf("bad_art"),
            blockedPlaylistIds = setOf("bad_pl"),
            blockedKeywords = setOf("explicitword", "curse"),
            blockedRegexPatterns = setOf("\\bbad\\d+\\b"),
            spokenWord = SpokenWordParentalPolicy(
                allowPodcasts = true,
                allowAudiobooks = false,
                podcasts = PodcastsParentalPolicy(
                    allowedShowIds = setOf("shw1"),
                    blockedShowIds = setOf("bad_shw"),
                ),
            ),
            radio = RadioParentalPolicy(enabled = true),
            localFiles = LocalFilesParentalPolicy(enabled = false),
            speakers = SpeakersParentalPolicy(jam = JamParentalPolicy(enabled = true)),
            lyrics = LyricsParentalPolicy(
                enabled = true,
                allowGeniusMeanings = false,
                allowExtraSources = false,
            ),
            playback = PlaybackParentalPolicy(allowAutoplayAndRadio = false),
            library = LibraryParentalPolicy(readOnlySpotifyLibrary = true),
            home = HomeParentalPolicy(
                minimalHomeMode = false,
                showShortcutsGrid = true,
                showWhatsNewBell = false,
                showFilterChips = true,
                showDjCard = false,
                showParentPicks = true,
                showChartsAndTrending = false,
                showMixesAndMadeForYou = true,
                showNewReleases = false,
                showRecents = true,
                hiddenShelfKeys = setOf("uri:spotify:section:viral", "title:throwback"),
            ),
        )
        ParentalPolicyStore.replace(context, richPolicy)

        TrustedNetworkGate.setRequireTrustedNetwork(context, true)
        TrustedNetworkGate.addTrustedWifi(context, "HomeKidsWiFi", "AA:BB:CC:DD:EE:01")
        TrustedNetworkGate.addTrustedLanFingerprint(context, "echo-living-room")
        TrustedNetworkGate.setSpeakerVisibilityMode(context, SpeakerVisibilityMode.ONLY_ALLOWED)
        TrustedNetworkGate.setDeviceHidden(context, "parents-bedroom-speaker", true)
        TrustedNetworkGate.setDeviceAllowedInStrictMode(context, "emma-echo-dot", true)

        CustomSpeakerNames.setCustomName(context, "emma-echo-dot", "Emma's Bedroom")
        CustomSpeakerNames.setCustomName(context, "kitchen-sonos", "Kitchen Speaker")

        RuleLabelStore.record(context, "playlist", "pl1", "Disney Sing-Alongs", "42 songs")
        RuleLabelStore.record(context, "artist", "bad_art", "Blocked Rapper")

        val expectedRestoredPolicy = ParentalPolicyStore.current(context)
        val exportedJson = ParentalConfigBackup.exportJson(context)

        // Security invariant: exported JSON must NEVER contain PIN digits, salt, or hash blobs
        assertFalse(exportedJson.contains("654321"))
        assertFalse(exportedJson.contains("pin_salt"))
        assertFalse(exportedJson.contains("pin_hash"))

        // Simulate importing onto a second sibling device that already has its own Parent PIN ("987654")
        ParentSecurityManager.resetForTests(context)
        ParentalPolicyStore.resetForTests(context)
        TrustedNetworkGate.resetForTests(context)
        CustomSpeakerNames.clearAll(context)
        assertEquals(PinSetupResult.Success, ParentSecurityManager.setupInitialPin(context, "987654"))

        val importResult = ParentalConfigBackup.importJson(context, exportedJson)
        assertTrue("Expected backup import to succeed: $importResult", importResult.isSuccess)

        // Policy + HomeSectionConfig restored identically
        assertEquals(expectedRestoredPolicy, ParentalPolicyStore.current(context))

        // Human-readable rule labels restored identically
        assertEquals(RuleLabel("Disney Sing-Alongs", "42 songs"), RuleLabelStore.resolve(context, "playlist", "pl1"))
        assertEquals(RuleLabel("Blocked Rapper", ""), RuleLabelStore.resolve(context, "artist", "bad_art"))
        assertEquals(RuleLabel("Album · alb1", "spotify:album:alb1"), RuleLabelStore.resolve(context, "album", "alb1"))

        // TrustedNetworkGate restored identically
        assertTrue(TrustedNetworkGate.isRequireTrustedNetwork(context))
        assertEquals(setOf("homekidswifi"), TrustedNetworkGate.trustedSsids(context))
        assertEquals(setOf("aa:bb:cc:dd:ee:01"), TrustedNetworkGate.trustedBssids(context))
        assertEquals(setOf("echo-living-room"), TrustedNetworkGate.trustedLanFingerprints(context))
        assertEquals(SpeakerVisibilityMode.ONLY_ALLOWED, TrustedNetworkGate.speakerVisibilityMode(context))
        assertEquals(setOf("parents-bedroom-speaker"), TrustedNetworkGate.hiddenDeviceIds(context))
        assertEquals(setOf("emma-echo-dot"), TrustedNetworkGate.allowedDeviceIds(context))
        assertTrue(TrustedNetworkGate.isJamAllowed(context))

        // Custom speaker nicknames restored identically
        assertEquals(
            mapOf("emma-echo-dot" to "Emma's Bedroom", "kitchen-sonos" to "Kitchen Speaker"),
            CustomSpeakerNames.all(context),
        )

        // Target device's Parent PIN ("987654") was NOT overwritten by the backup!
        assertEquals(PinVerifyResult.Verified, ParentSecurityManager.verifyPin(context, "987654"))
        assertTrue(ParentSecurityManager.verifyPin(context, "654321") is PinVerifyResult.InvalidPin)
    }

    @Test
    fun `allowShow, blockShow, and clearShowRule persist labels and remove rules cleanly`() {
        ParentalPolicyStore.allowShow(context, "spotify:show:kids_stories", "Bedtime Stories", "Kids Podcast")
        assertEquals(setOf("kids_stories"), ParentalPolicyStore.current(context).spokenWord.podcasts.allowedShowIds)
        assertEquals(
            RuleLabel("Bedtime Stories", "Kids Podcast"),
            RuleLabelStore.resolve(context, "show", "kids_stories"),
        )

        ParentalPolicyStore.clearShowRule(context, "kids_stories")
        assertTrue(ParentalPolicyStore.current(context).spokenWord.podcasts.allowedShowIds.isEmpty())
    }

    @Test
    fun `malformed JSON or unsupported schema version fails cleanly without corrupting current policy`() {
        val baseline = ParentalPolicy(blockExplicit = true, blockedKeywords = setOf("keepme"))
        ParentalPolicyStore.replace(context, baseline)

        val malformed = ParentalConfigBackup.importJson(context, "{ not valid json")
        assertTrue(malformed.isFailure)
        assertEquals(baseline, ParentalPolicyStore.current(context))

        val futureSchema = ParentalConfigBackup.importJson(
            context,
            """{"schemaVersion": 999, "policy": {}}""",
        )
        assertTrue(futureSchema.isFailure)
        assertEquals(baseline, ParentalPolicyStore.current(context))
    }

    @Test
    fun `round-trip backup preserves Audible ASIN allowlist and Home pins while never exporting Audible tokens`() {
        io.github.thake.soundnest.feature.spokenword.audible.settings.AudibleCredentialStore.save(
            context,
            io.github.thake.soundnest.audible.AudibleSessionTokens(
                marketplace = io.github.thake.soundnest.audible.AudibleMarketplace.DE,
                deviceSerial = "DEV_SERIAL_99",
                adpToken = "DO_NOT_EXPORT_ADP_TOKEN",
                devicePrivateKeyPem = "DO_NOT_EXPORT_PRIVATE_KEY",
                refreshToken = "DO_NOT_EXPORT_REFRESH_TOKEN",
            ),
        )
        ParentalPolicyStore.update(context) {
            it.copy(
                spokenWord = it.spokenWord.copy(
                    allowAudiobooks = false,
                    allowAudible = true,
                    audible = it.spokenWord.audible.copy(showPinnedOnHome = false),
                ),
            )
        }
        ParentalPolicyStore.setAudibleBookAllowed(context, "B08G9PRS1K", allowed = true)
        ParentalPolicyStore.setAudibleBookAllowed(context, "B07KIDSBOOK", allowed = true)
        ParentalPolicyStore.setAudibleBookPinned(context, "B07KIDSBOOK", pinned = true)

        val expectedRestoredPolicy = ParentalPolicyStore.current(context)
        val exportedJson = ParentalConfigBackup.exportJson(context)
        assertFalse(exportedJson.contains("DO_NOT_EXPORT_ADP_TOKEN"))
        assertFalse(exportedJson.contains("DO_NOT_EXPORT_PRIVATE_KEY"))
        assertFalse(exportedJson.contains("DO_NOT_EXPORT_REFRESH_TOKEN"))

        // Wipe and restore on sibling device
        io.github.thake.soundnest.feature.spokenword.audible.settings.AudibleCredentialStore.clear(context)
        ParentalPolicyStore.resetForTests(context)

        assertTrue(ParentalConfigBackup.importJson(context, exportedJson).isSuccess)
        val restored = ParentalPolicyStore.current(context)
        assertEquals(setOf("B08G9PRS1K", "B07KIDSBOOK"), restored.spokenWord.audible.allowedAsins)
        assertEquals(setOf("B07KIDSBOOK"), restored.spokenWord.audible.pinnedAsins)
        assertFalse(restored.spokenWord.audible.showPinnedOnHome)
        assertFalse(restored.spokenWord.allowAudiobooks)
        assertTrue(restored.spokenWord.allowAudible)
    }

    @Test
    fun `legacy JSON migration sets allowAudible true when older config had allowAudiobooks true and approved Audible books`() {
        val legacyJson = """
            {
              "schemaVersion": 1,
              "policy": {
                "filterMode": "FILTERED_OPEN",
                "spokenWord": {
                  "allowPodcasts": false,
                  "allowAudiobooks": true,
                  "audible": {
                    "allowedAsins": ["B08G9PRS1K"]
                  }
                }
              },
              "ruleLabels": {}
            }
        """.trimIndent()

        assertTrue(ParentalConfigBackup.importJson(context, legacyJson).isSuccess)
        val restored = ParentalPolicyStore.current(context)
        assertTrue(restored.spokenWord.allowAudiobooks)
        assertTrue(restored.spokenWord.allowAudible)
        assertEquals(setOf("B08G9PRS1K"), restored.spokenWord.audible.allowedAsins)
    }

    @Test
    fun `legacy JSON migration does not enable allowAudible if older config had no approved Audible books`() {
        val legacyJson = """
            {
              "schemaVersion": 1,
              "policy": {
                "filterMode": "FILTERED_OPEN",
                "spokenWord": {
                  "allowPodcasts": false,
                  "allowAudiobooks": true,
                  "audible": {
                    "allowedAsins": []
                  }
                }
              },
              "ruleLabels": {}
            }
        """.trimIndent()

        assertTrue(ParentalConfigBackup.importJson(context, legacyJson).isSuccess)
        val restored = ParentalPolicyStore.current(context)
        assertTrue(restored.spokenWord.allowAudiobooks)
        assertFalse(restored.spokenWord.allowAudible)
    }
}
