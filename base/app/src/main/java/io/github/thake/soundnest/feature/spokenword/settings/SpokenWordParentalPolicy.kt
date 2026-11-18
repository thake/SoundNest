/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.spokenword.settings

import io.github.thake.soundnest.feature.spokenword.audible.settings.AudibleParentalPolicy
import io.github.thake.soundnest.feature.spokenword.podcasts.settings.PodcastsParentalPolicy
import kotlinx.serialization.Serializable

@Serializable
data class SpokenWordParentalPolicy(
    val allowPodcasts: Boolean = false,
    val allowAudiobooks: Boolean = false,
    val allowAudible: Boolean = false,
    val podcasts: PodcastsParentalPolicy = PodcastsParentalPolicy(),
    val audible: AudibleParentalPolicy = AudibleParentalPolicy(),
) {
    val allowAnyAudiobooks: Boolean get() = allowAudiobooks || allowAudible
}
