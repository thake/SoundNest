/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.accounts.settings

import android.content.Context
import io.github.thake.soundnest.core.preferences.SessionResettable
import io.github.thake.soundnest.core.preferences.featurePrefs

/**
 * Account onboarding & sign-in session flags (`logged_in`, `setup_done`, `current_account_name`),
 * consolidated under `"feature_accounts"`.
 */
object OnboardingSettings : SessionResettable {
    internal const val PREF_NAME = "accounts"
    internal const val KEY_LOGGED_IN = "logged_in"
    internal const val KEY_SETUP_DONE = "setup_done"
    internal const val KEY_CURRENT_ACCOUNT_NAME = "current_account_name"

    private fun prefs(context: Context) = context.featurePrefs(PREF_NAME)

    fun isLoggedIn(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LOGGED_IN, false)

    fun setLoggedIn(context: Context, loggedIn: Boolean) {
        prefs(context).edit().putBoolean(KEY_LOGGED_IN, loggedIn).apply()
    }

    fun isSetupDone(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SETUP_DONE, false)

    fun setSetupDone(context: Context, done: Boolean = true) {
        prefs(context).edit().putBoolean(KEY_SETUP_DONE, done).apply()
    }

    fun currentAccountName(context: Context): String? =
        prefs(context).getString(KEY_CURRENT_ACCOUNT_NAME, null)

    fun setCurrentAccountName(context: Context, name: String?) {
        val editor = prefs(context).edit()
        if (name == null) editor.remove(KEY_CURRENT_ACCOUNT_NAME)
        else editor.putString(KEY_CURRENT_ACCOUNT_NAME, name)
        editor.apply()
    }

    override fun onLeaveAccount(context: Context, forget: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_LOGGED_IN, false)
            .remove(KEY_CURRENT_ACCOUNT_NAME)
            .apply()
    }
}
