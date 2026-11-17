/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.appearance.settings

import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.os.LocaleListCompat
import io.github.thake.soundnest.core.di.Graph
import io.github.thake.soundnest.core.preferences.featurePrefs
import java.util.Locale

private const val APPEARANCE_PREFS = "appearance"

private fun appearancePrefs(context: Context) = context.featurePrefs(APPEARANCE_PREFS)

private fun safeAppPrefs() = runCatching { appearancePrefs(Graph.app) }.getOrNull()

/** The two looks. [CLASSIC] is the default, so nothing changes for anyone who has not chosen. */
enum class Look {
    CLASSIC,
    IMMERSIVE;

    companion object {
        fun of(name: String?): Look = entries.firstOrNull { it.name == name } ?: CLASSIC
    }
}

object LookPref {
    private const val KEY = "look"
    private const val KEY_CHOSEN = "look_chosen"
    private const val KEY_OFFERED = "look_offered"

    /** The chosen look, as Compose state: everything reading it redraws when it changes. */
    var look by mutableStateOf(Look.of(safeAppPrefs()?.getString(KEY, null)))
        private set

    fun set(c: Context, value: Look) {
        look = value
        appearancePrefs(c).edit().putString(KEY, value.name).apply()
    }

    /** The look step of the onboarding was seen (new installs and updates alike). */
    fun isChosen(c: Context): Boolean = appearancePrefs(c).getBoolean(KEY_CHOSEN, false)

    fun setChosen(c: Context) {
        appearancePrefs(c).edit().putBoolean(KEY_CHOSEN, true).putBoolean(KEY_OFFERED, true).apply()
        offered = true
    }

    var offered by mutableStateOf(safeAppPrefs()?.getBoolean(KEY_OFFERED, false) ?: false)
        private set

    fun isOffered(c: Context): Boolean = appearancePrefs(c).getBoolean(KEY_OFFERED, false)

    fun setOffered(c: Context) {
        appearancePrefs(c).edit().putBoolean(KEY_OFFERED, true).apply()
        offered = true
    }

    fun clearOffered(c: Context) {
        appearancePrefs(c).edit().remove(KEY_OFFERED).apply()
        offered = false
    }
}

/**
 * Per-look Accent color preference (backed by `"feature_appearance"`).
 */
object AccentPref {
    private const val KEY_ACCENT_CLASSIC = "accent_classic"
    private const val KEY_ACCENT_IMMERSIVE = "accent_immersive"

    enum class Accent(val key: String, val color: Color) {
        GREEN("green", Color(0xFF1ED760)),
        BLUE("blue", Color(0xFF509BF5)),
        PURPLE("purple", Color(0xFFAF7AF6)),
        PINK("pink", Color(0xFFFF4F7B)),
        ORANGE("orange", Color(0xFFFF9E3D)),
    }

    var classicAccent by mutableStateOf(Accent.GREEN)
        private set

    var immersiveAccent by mutableStateOf(Accent.PINK)
        private set

    var immersiveAccentChosen by mutableStateOf(false)
        private set

    fun accentOf(look: Look): Accent =
        if (look == Look.IMMERSIVE) (if (immersiveAccentChosen) immersiveAccent else Accent.PINK) else classicAccent

    val accentColor: Color get() = accentOf(LookPref.look).color

    fun init(context: Context) {
        val p = appearancePrefs(context)
        classicAccent = Accent.entries.firstOrNull { it.key == p.getString(KEY_ACCENT_CLASSIC, "green") } ?: Accent.GREEN
        immersiveAccent = Accent.entries.firstOrNull { it.key == p.getString(KEY_ACCENT_IMMERSIVE, "pink") } ?: Accent.PINK
        immersiveAccentChosen = p.contains(KEY_ACCENT_IMMERSIVE)
    }

    fun setAccent(context: Context, look: Look, value: Accent) {
        if (look == Look.IMMERSIVE) {
            immersiveAccent = value
            immersiveAccentChosen = true
            appearancePrefs(context).edit().putString(KEY_ACCENT_IMMERSIVE, value.key).apply()
        } else {
            classicAccent = value
            appearancePrefs(context).edit().putString(KEY_ACCENT_CLASSIC, value.key).apply()
        }
    }
}

/**
 * "Dynamic colors (Material You)": the Classic look's accent taken from the wallpaper (Android 12+).
 */
object DynamicColorPref {
    private const val KEY = "dynamic_color"

    val available: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    var on by mutableStateOf(safeAppPrefs()?.getBoolean(KEY, false) ?: false)
        private set

    val active: Boolean get() = on && available

    fun set(c: Context, value: Boolean) {
        on = value
        cached = null
        appearancePrefs(c).edit().putBoolean(KEY, value).apply()
    }

    @Volatile
    private var cached: Color? = null

    fun refresh() {
        cached = null
    }

    fun accent(): Color? {
        if (!on || Build.VERSION.SDK_INT < Build.VERSION_CODES.S || LookPref.look != Look.CLASSIC) return null
        cached?.let { return it }
        return runCatching { Color(Graph.app.getColor(android.R.color.system_accent1_200)) }
            .getOrNull()?.also { cached = it }
    }
}

/**
 * "App font": the app's own fonts (Manrope in Classic, Inter in Immersive) or the phone's system font.
 */
object AppFontPref {
    private const val KEY = "system_font"

    var system by mutableStateOf(safeAppPrefs()?.getBoolean(KEY, false) ?: false)
        private set

    fun set(c: Context, value: Boolean) {
        system = value
        appearancePrefs(c).edit().putBoolean(KEY, value).apply()
    }
}

/**
 * Liquid Glass in the Immersive look: floating bars refract the page through a lens.
 */
object LiquidGlassPref {
    private const val KEY = "immersive_liquid_glass"

    var on by mutableStateOf(safeAppPrefs()?.getBoolean(KEY, true) ?: true)
        private set

    fun set(c: Context, value: Boolean) {
        on = value
        appearancePrefs(c).edit().putBoolean(KEY, value).apply()
    }
}

/** The bottom bar: fading into the list (the default), or solid black. */
object SolidNavBar {
    private const val KEY = "solid_nav_bar"

    var on by mutableStateOf(safeAppPrefs()?.getBoolean(KEY, false) ?: false)
        private set

    fun set(c: Context, value: Boolean) {
        on = value
        appearancePrefs(c).edit().putBoolean(KEY, value).apply()
    }
}

/** Screen rotation: portrait (the default), automatic (follows the system), or landscape. */
object OrientationPref {
    private const val KEY = "screen_orientation"
    const val PORTRAIT = "portrait"
    const val AUTO = "auto"
    const val LANDSCAPE = "landscape"

    var mode by mutableStateOf(safeAppPrefs()?.getString(KEY, PORTRAIT) ?: PORTRAIT)
        private set

    fun set(c: Context, value: String) {
        mode = value
        appearancePrefs(c).edit().putString(KEY, value).apply()
    }

    fun requested(): Int = when (mode) {
        AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
}

/** The app's own language (per-app locale), independent of the phone's. */
object AppLanguage {
    /** Follow the phone. */
    const val SYSTEM = ""

    /** Languages the app has strings for, as BCP 47 tags, after "automatic". */
    val AVAILABLE: List<String> = listOf(SYSTEM) + listOf(
        "af", "am", "ar", "az", "bg", "bn", "bs", "ca", "cs", "da", "de", "el", "en", "en-GB", "es", "es-AR", "es-MX",
        "et", "eu", "fa", "fi", "fr", "fr-CA", "gl", "gu", "hi", "hr", "hu", "id", "is", "it", "he", "ja", "kn", "ko",
        "lt", "lv", "mk", "ml", "mr", "ms", "nb", "ne", "nl", "or", "pa", "pl", "pt", "pt-BR", "pt-PT", "ro", "ru", "sk", "sl",
        "sr", "sv", "sw", "ta", "te", "th", "tl", "tr", "uk", "ur", "vi", "zh", "zh-HK", "zh-TW", "zu",
    ).sortedBy { nativeName(it).lowercase() }

    fun current(context: Context): String =
        AppCompatDelegate.getApplicationLocales().takeIf { !it.isEmpty }?.get(0)?.toLanguageTag() ?: SYSTEM

    fun apply(context: Context, tag: String) {
        appearancePrefs(context).edit().putString("app_language", tag).apply()
        AppCompatDelegate.setApplicationLocales(
            if (tag == SYSTEM) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag),
        )
    }

    /** The language's name in itself ("Français", "Deutsch"…). */
    fun nativeName(tag: String): String {
        val locale = Locale.forLanguageTag(tag)
        return locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
    }
}
