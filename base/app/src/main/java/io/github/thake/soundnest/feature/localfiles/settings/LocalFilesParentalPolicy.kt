/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.localfiles.settings

import kotlinx.serialization.Serializable

@Serializable
data class LocalFilesParentalPolicy(
    val enabled: Boolean = false,
)
