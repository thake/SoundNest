/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.host.android

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.core.os.ConfigurationCompat
import java.util.Locale

/**
 * Android host-level locale resolution honoring per-app language selections on API 29-36.
 */
object HostLocale {
    /** Returns a Context configured for the active per-app locale, or null if context is null. */
    fun localizedContext(context: Context?): Context? =
        context?.let { ContextCompat.getContextForLanguage(it) }

    /** Returns the active per-app [Locale], falling back to configuration or system default. */
    fun effectiveLocale(context: Context?): Locale {
        val locCtx = localizedContext(context) ?: return Locale.getDefault()
        val locales = ConfigurationCompat.getLocales(locCtx.resources.configuration)
        return if (!locales.isEmpty) locales[0] ?: Locale.getDefault() else Locale.getDefault()
    }

    /** Formats standard HTTP Accept-Language header matching the active locale (e.g. "de-DE,de;q=0.9,en;q=0.5"). */
    fun acceptLanguage(context: Context?): String {
        val loc = effectiveLocale(context)
        val tag = loc.toLanguageTag()
        val lang = loc.language.lowercase(Locale.ROOT)
        return when {
            lang == "en" -> if (tag.equals("en", ignoreCase = true)) "en;q=0.9" else "$tag,en;q=0.9"
            tag.equals(lang, ignoreCase = true) -> "$lang,en;q=0.5"
            else -> "$tag,$lang;q=0.9,en;q=0.5"
        }
    }
}
