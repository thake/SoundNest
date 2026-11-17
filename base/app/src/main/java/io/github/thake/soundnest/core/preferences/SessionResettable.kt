/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.core.preferences

import android.content.Context

/**
 * Implemented by feature `*Settings` or stores whose account-bound state must reset or park
 * when leaving a Spotify account (`forget = true` on sign-out/removal, `false` on profile switch).
 */
interface SessionResettable {
    fun onLeaveAccount(context: Context, forget: Boolean)
}
