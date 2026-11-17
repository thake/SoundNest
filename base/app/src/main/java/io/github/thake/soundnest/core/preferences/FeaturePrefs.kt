/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.core.preferences

import android.content.Context
import android.content.SharedPreferences

/**
 * Clean-slate 1-to-1 preference store for a feature or nested sub-feature (`"feature_<name>"`).
 */
fun Context.featurePrefs(name: String): SharedPreferences =
    getSharedPreferences("feature_$name", Context.MODE_PRIVATE)

/**
 * Clean-slate 1-to-1 preference store for a shared core service (`"core_<name>"`).
 */
fun Context.corePrefs(name: String): SharedPreferences =
    getSharedPreferences("core_$name", Context.MODE_PRIVATE)
