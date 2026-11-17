package io.github.thake.soundnest.core.ui.classic

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** The page: black with the AMOLED setting, else #121212. */
val AppBackground: Color get() =
    if (io.github.thake.soundnest.feature.system.settings.EnergySettings.oledBlack) Color.Black else Color(0xFF121212)
val GridBackground: Color get() = tone(0xFF2A2A2A)
/** The accent (Spotify green, the colour chosen in the settings, or the wallpaper's: see DynamicColorPref). */
val AppPalette: Color get() = AppAccent
val AppAccent: Color get() =
    io.github.thake.soundnest.feature.appearance.settings.DynamicColorPref.accent()
        ?: io.github.thake.soundnest.feature.appearance.settings.AccentPref.accentOf(io.github.thake.soundnest.feature.appearance.settings.LookPref.look).color

/** Primary text and icons. */
val AppText: Color = Color.White
/** Secondary text and icons. */
val AppMuted: Color = Color(0xFFB3B3B3)
/** Destructive actions (delete, leave, block) and failures shown as text. */
val AppDestructive = Color(0xFFF15E6C)
/** What a tint is mixed with to make a header. */
val AppShade: Color = Color.Black

/** One of the palette's raised greys (#181818 ... #3E3E3E): "a bit raised from the page". */
fun tone(argb: Long): Color = Color(argb)

/** The cover's colour as a page header: darkened toward black, so the title stays readable. */
fun headerTint(tint: Color, amount: Float): Color = lerp(tint, Color.Black, amount)
