/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.core.preferences

import androidx.annotation.StringRes
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy

/**
 * Self-gated setting row metadata registered by a feature's `*SettingsUi` for Settings Search.
 */
data class SettingsEntry(
    @StringRes val title: Int,
    @StringRes val detail: Int = 0,
    val classicPlace: String? = null,
    val immersivePage: String? = null,
    val isVisible: (ParentalPolicy) -> Boolean = { true },
)
