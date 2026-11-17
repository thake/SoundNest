/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.speakers.settings

import io.github.thake.soundnest.feature.speakers.jam.settings.JamParentalPolicy
import kotlinx.serialization.Serializable

@Serializable
enum class SpeakerVisibilityMode {
    ALLOW_ALL_EXCEPT_HIDDEN,
    ONLY_ALLOWED,
}

/**
 * Nested parental policy configuration for Trusted Home Network, speaker visibility,
 * custom speaker nicknames, and Spotify Jam.
 */
@Serializable
data class SpeakersParentalPolicy(
    val requireTrustedNetwork: Boolean = true,
    val trustedSsids: Set<String> = emptySet(),
    val trustedBssids: Set<String> = emptySet(),
    val trustedLanFingerprints: Set<String> = emptySet(),
    val speakerVisibilityMode: SpeakerVisibilityMode = SpeakerVisibilityMode.ALLOW_ALL_EXCEPT_HIDDEN,
    val hiddenDeviceIds: Set<String> = emptySet(),
    val allowedDeviceIds: Set<String> = emptySet(),
    val customSpeakerNames: Map<String, String> = emptyMap(),
    val jam: JamParentalPolicy = JamParentalPolicy(),
)
