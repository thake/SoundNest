package io.github.thake.soundnest.data.preferences

/*
 * Appearance choices on top of the look: Accent color, wallpaper colours (Material You) for the Classic
 * look, and the font of both looks. Compose state, so every screen redraws when one changes.
 */

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

private const val PREF = "settings_prefs"

private fun prefs(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)

/**
 * Per-look Accent color preference (preserved from upstream theme settings without donation popups).
 */
object AccentPref {
    private const val ACCENT_PREFS = "support_reminder"
    private const val KEY_ACCENT = "accent"
    private const val KEY_ACCENT_CLASSIC = "accent_classic"
    private const val KEY_ACCENT_IMMERSIVE = "accent_immersive"

    enum class Accent(val key: String, val color: Color) {
        GREEN("green", Color(0xFF1ED760)),
        BLUE("blue", Color(0xFF509BF5)),
        PURPLE("purple", Color(0xFFAF7AF6)),
        PINK("pink", Color(0xFFF573A0)),
        ORANGE("orange", Color(0xFFFF9E3D)),
    }

    var classicAccent by mutableStateOf(Accent.GREEN)
        private set

    var immersiveAccent by mutableStateOf(Accent.GREEN)
        private set

    var immersiveAccentChosen by mutableStateOf(false)
        private set

    fun accentOf(look: Look): Accent = if (look == Look.IMMERSIVE) immersiveAccent else classicAccent

    val accentColor: Color get() = accentOf(LookPref.look).color

    private fun accentPrefs(context: Context) = context.getSharedPreferences(ACCENT_PREFS, Context.MODE_PRIVATE)

    fun init(context: Context) {
        val p = accentPrefs(context)
        if (p.contains(KEY_ACCENT) && !p.contains(KEY_ACCENT_CLASSIC) && !p.contains(KEY_ACCENT_IMMERSIVE)) {
            val old = p.getString(KEY_ACCENT, "green")
            p.edit().putString(KEY_ACCENT_CLASSIC, old).putString(KEY_ACCENT_IMMERSIVE, old).apply()
        }
        classicAccent = Accent.entries.firstOrNull { it.key == p.getString(KEY_ACCENT_CLASSIC, "green") } ?: Accent.GREEN
        immersiveAccent = Accent.entries.firstOrNull { it.key == p.getString(KEY_ACCENT_IMMERSIVE, "green") } ?: Accent.GREEN
        immersiveAccentChosen = p.contains(KEY_ACCENT_IMMERSIVE)
    }

    fun setAccent(context: Context, look: Look, value: Accent) {
        if (look == Look.IMMERSIVE) {
            immersiveAccent = value
            immersiveAccentChosen = true
            accentPrefs(context).edit().putString(KEY_ACCENT_IMMERSIVE, value.key).apply()
        } else {
            classicAccent = value
            accentPrefs(context).edit().putString(KEY_ACCENT_CLASSIC, value.key).apply()
        }
    }
}

/**
 * "Dynamic colors (Material You)": the Classic look's accent taken from the wallpaper (Android 12
 * and later), in place of the accent chosen in the settings. Off by default. The Immersive look
 * keeps its own colours, taken from the playing cover.
 */
object DynamicColorPref {
    private const val KEY = "dynamic_color"

    /** The phone has wallpaper colours to give (Android 12 and later). */
    val available: Boolean get() = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S

    var on by mutableStateOf(prefs(io.github.thake.soundnest.di.Graph.app).getBoolean(KEY, false))
        private set

    /** On, and the phone can give the colours. */
    val active: Boolean get() = on && available

    fun set(c: Context, value: Boolean) {
        on = value
        cached = null
        prefs(c).edit().putBoolean(KEY, value).apply()
    }

    // The wallpaper's accent, read once per configuration (see [refresh]): it is asked at every draw.
    @Volatile private var cached: Color? = null

    /** The configuration changed (a new wallpaper recreates the activity): the accent is read again. */
    fun refresh() { cached = null }

    /**
     * The wallpaper's accent for the Classic look, or null (off, Immersive, before Android 12). The
     * same tone Material's dynamic dark scheme gives its primary colour (system_accent1_200).
     */
    fun accent(): Color? {
        if (!on || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S || LookPref.look != Look.CLASSIC) return null
        cached?.let { return it }
        return runCatching { Color(io.github.thake.soundnest.di.Graph.app.getColor(android.R.color.system_accent1_200)) }
            .getOrNull()?.also { cached = it }
    }
}

/**
 * "App font": the app's own fonts (Manrope in the Classic look, Inter in the Immersive one, the
 * default) or the phone's system font.
 */
object AppFontPref {
    private const val KEY = "system_font"

    /** True: the phone's system font everywhere in the app. */
    var system by mutableStateOf(prefs(io.github.thake.soundnest.di.Graph.app).getBoolean(KEY, false))
        private set

    fun set(c: Context, value: Boolean) {
        system = value
        prefs(c).edit().putBoolean(KEY, value).apply()
    }
}
