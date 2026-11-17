/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.radio.settings

import kotlinx.serialization.Serializable

@Serializable
data class RadioParentalPolicy(
    val enabled: Boolean = false,
)
