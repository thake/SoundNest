/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.core.preferences

import android.content.Context
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy

/**
 * Implemented by feature `*Settings` objects whose local state must stay in sync with
 * [ParentalPolicy] whenever parent rules are loaded or updated.
 */
interface PolicySyncedSetting {
    fun enforcePolicy(context: Context, policy: ParentalPolicy)
}
