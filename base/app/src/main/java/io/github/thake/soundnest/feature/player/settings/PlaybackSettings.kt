/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.player.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.thake.soundnest.core.di.Graph
import io.github.thake.soundnest.core.playback.LocalPlayer
import io.github.thake.soundnest.core.preferences.PolicySyncedSetting
import io.github.thake.soundnest.core.preferences.ProfilePrefs
import io.github.thake.soundnest.core.preferences.featurePrefs
import io.github.thake.soundnest.feature.localfiles.automix.settings.AutoMixSettings
import io.github.thake.soundnest.feature.parental.settings.FeatureGates
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy
import io.github.thake.soundnest.feature.system.settings.DataSaver
import io.github.thake.soundnest.host.android.SoundNestRuntime
import kotlinx.coroutines.launch

private const val FEATURE_PLAYER = "player"
private const val KEY_STREAMING = "streaming_quality"
private const val KEY_CHOSEN = "quality_chosen"
private const val KEY_STREAMING_CELL = "streaming_quality_cellular"
private const val KEY_AUTOPLAY = "autoplay"
private const val KEY_CROSSFADE = "crossfade_seconds"
private const val KEY_SOURCE_LINE = "source_line"
private const val KEY_IMMERSIVE_CAPSULE = "immersive_capsule"
private const val KEY_IMMERSIVE_MODES = "immersive_modes"
private const val KEY_IMMERSIVE_VOLUME = "immersive_volume"

private fun playerPrefs(c: Context): SharedPreferences = c.featurePrefs(FEATURE_PLAYER)
private fun safePlayerPrefs(): SharedPreferences? = runCatching { playerPrefs(Graph.app) }.getOrNull()

/**
 * Coordinates initialization and parental-policy enforcement for `"feature_player"` settings.
 */
object PlaybackSettings : PolicySyncedSetting {
    fun init(context: Context) {
        val p = playerPrefs(context)
        AutoplayPref.syncFromPrefs(p)
        CrossfadePref.syncFromPrefs(p)
        PlayerSourceLinePref.syncFromPrefs(p)
        PlayerCapsulePref.syncFromPrefs(p)
        PlayerModesPref.syncFromPrefs(p)
        PlayerVolumePref.syncFromPrefs(p)
    }

    override fun enforcePolicy(context: Context, policy: ParentalPolicy) {
        AutoplayPref.set(context, policy.effectiveAllowAutoplayAndRadio)
    }
}

/**
 * The Web Player's own streaming levels (its "Streaming quality" setting): 24, 96 and
 * 160 kbit/s. Only the levels a free account gets; "very_high" (256/320) is Premium's.
 */
enum class StreamingQuality(val level: String) {
    AUTO("auto"),
    LOW("low"),
    NORMAL("normal"),
    HIGH("high"),
    /** 256 kbit/s: the Web Player gives it to Premium accounts only (offered then, see StreamingQualityPicker). */
    VERY_HIGH("very_high");

    companion object {
        fun of(level: String?): StreamingQuality = entries.firstOrNull { it.level == level } ?: AUTO
    }
}

/**
 * The level for Wi-Fi, or for mobile data ([cellular]), as the official app's two settings.
 * Mobile data follows the Wi-Fi level until it is set on its own.
 */
fun getStreamingQuality(c: Context, cellular: Boolean = false): StreamingQuality {
    val p = playerPrefs(c)
    val wifi = ProfilePrefs.getString(p, KEY_STREAMING, null)
    return StreamingQuality.of(if (cellular) ProfilePrefs.getString(p, KEY_STREAMING_CELL, null) ?: wifi else wifi)
}

/** Saves the level for that network and hands the one for the current network to the player. */
fun setStreamingQuality(c: Context, q: StreamingQuality, cellular: Boolean = false) {
    playerPrefs(c).edit().putString(ProfilePrefs.key(if (cellular) KEY_STREAMING_CELL else KEY_STREAMING), q.level).apply()
    applyStreamingQuality(c)
}

/** Gives the current network's level to the Web Player (at start and when the network changes); harmless before it is up. */
fun applyStreamingQuality(c: Context) {
    val runtime = SoundNestRuntime
    if (!runtime.isStarted) return
    val q = getStreamingQuality(c, cellular = DataSaver.metered)
    runtime.scope.launch { runtime.spotify.player.setAudioQuality(q.level) }
}

/** The quality step of the onboarding was seen (new installs and updates alike). */
fun isQualityChosen(c: Context): Boolean = playerPrefs(c).getBoolean(KEY_CHOSEN, false)
fun setQualityChosen(c: Context) = playerPrefs(c).edit().putBoolean(KEY_CHOSEN, true).apply()

/** "When your music ends, keep playing similar songs" (on by default). Read by the player. */
object AutoplayPref {
    var on by mutableStateOf(safePlayerPrefs()?.getBoolean(KEY_AUTOPLAY, true) ?: true)
        private set

    internal fun syncFromPrefs(p: SharedPreferences) {
        on = p.getBoolean(KEY_AUTOPLAY, true)
    }

    fun set(c: Context, value: Boolean) {
        on = value
        playerPrefs(c).edit().putBoolean(KEY_AUTOPLAY, value).apply()
    }
}

/**
 * Crossfade length in seconds, 0 = off (the default: nothing changes unless asked for).
 *
 * It is a fade, not an overlap: the page plays one media element, so the next song cannot
 * start before this one ends. The song fades out over its last N seconds, the next one fades
 * in. The value is Compose state, so the settings row redraws when it changes.
 */
object CrossfadePref {
    const val MAX = 12
    var seconds by mutableStateOf(
        (safePlayerPrefs()?.getInt(KEY_CROSSFADE, 0) ?: 0).coerceIn(0, MAX),
    )
        private set

    internal fun syncFromPrefs(p: SharedPreferences) {
        seconds = p.getInt(KEY_CROSSFADE, 0).coerceIn(0, MAX)
    }

    /** Saves it and hands it to the player: the change applies to the song playing now. */
    fun set(c: Context, value: Int) {
        val v = value.coerceIn(0, MAX)
        seconds = v
        playerPrefs(c).edit().putInt(KEY_CROSSFADE, v).apply()
        applyCrossfade(v)
    }
}

fun getCrossfadeSeconds(c: Context): Int =
    playerPrefs(c).getInt(KEY_CROSSFADE, 0).coerceIn(0, CrossfadePref.MAX)

/** Gives the saved length to the player (at start, and at every change); 0 stops a fade at once. */
fun applyCrossfade(seconds: Int) {
    LocalPlayer.crossfadeChanged()
    val runtime = SoundNestRuntime
    if (!runtime.isStarted) return
    runtime.spotify.player.crossfadeSeconds = if (AutoMixSettings.on) TransitionPref.SPOTIFY_FADE_SECONDS else seconds
}

/**
 * How the local player's songs follow each other: one choice over [CrossfadePref] and AutoMix,
 * which never run together (AutoMix on, the crossfade's length is kept for later but unused).
 * Spotify songs, which AutoMix cannot mix (the app never has the Web Player's audio), then change
 * with a short fade of [SPOTIFY_FADE_SECONDS] rather than with that kept length (up to 12 s).
 */
object TransitionPref {
    enum class Mode { OFF, CROSSFADE, AUTOMIX }

    /** The Spotify songs' fade while AutoMix is on, seconds. */
    const val SPOTIFY_FADE_SECONDS = 3

    val mode: Mode get() = when {
        AutoMixSettings.on -> Mode.AUTOMIX
        CrossfadePref.seconds > 0 -> Mode.CROSSFADE
        else -> Mode.OFF
    }

    fun set(c: Context, mode: Mode) {
        if (mode == Mode.AUTOMIX && !FeatureGates.canUseLocalFiles(c)) return
        val automix = mode == Mode.AUTOMIX
        if (automix != AutoMixSettings.on) {
            AutoMixSettings.set(automix, c)
            LocalPlayer.autoMixChanged(automix)
        }
        when (mode) {
            Mode.OFF -> CrossfadePref.set(c, 0)
            Mode.CROSSFADE -> if (CrossfadePref.seconds == 0) CrossfadePref.set(c, 6)
            Mode.AUTOMIX -> Unit
        }
    }
}

/**
 * "Source and quality in the player" (Settings > Display, both looks): the line that says what
 * plays and how (under the title in the Immersive look, under the device in the Classic one).
 */
object PlayerSourceLinePref {
    var on by mutableStateOf(safePlayerPrefs()?.getBoolean(KEY_SOURCE_LINE, true) ?: true)
        private set

    val shown: Boolean get() = on

    internal fun syncFromPrefs(p: SharedPreferences) {
        on = p.getBoolean(KEY_SOURCE_LINE, true)
    }

    fun set(c: Context, value: Boolean) {
        on = value
        playerPrefs(c).edit().putBoolean(KEY_SOURCE_LINE, value).apply()
    }
}

/**
 * "Capsule in the player" (Settings > Display, Immersive look): the capsule at the bottom of the
 * player (devices, with shuffle and repeat when [PlayerModesPref] is on).
 */
object PlayerCapsulePref {
    var on by mutableStateOf(safePlayerPrefs()?.getBoolean(KEY_IMMERSIVE_CAPSULE, true) ?: true)
        private set

    val shown: Boolean get() = on

    internal fun syncFromPrefs(p: SharedPreferences) {
        on = p.getBoolean(KEY_IMMERSIVE_CAPSULE, true)
    }

    fun set(c: Context, value: Boolean) {
        on = value
        playerPrefs(c).edit().putBoolean(KEY_IMMERSIVE_CAPSULE, value).apply()
    }
}

/**
 * "Shuffle and repeat in the player" (Settings > Display, Immersive look): on, the player's
 * bottom capsule holds shuffle, repeat and devices; off, only devices.
 */
object PlayerModesPref {
    var on by mutableStateOf(safePlayerPrefs()?.getBoolean(KEY_IMMERSIVE_MODES, true) ?: true)
        private set

    internal fun syncFromPrefs(p: SharedPreferences) {
        on = p.getBoolean(KEY_IMMERSIVE_MODES, true)
    }

    fun set(c: Context, value: Boolean) {
        on = value
        playerPrefs(c).edit().putBoolean(KEY_IMMERSIVE_MODES, value).apply()
    }
}

/**
 * "Volume bar in the player" (Settings > Display, Immersive look): the volume slider under the
 * transport.
 */
object PlayerVolumePref {
    var on by mutableStateOf(safePlayerPrefs()?.getBoolean(KEY_IMMERSIVE_VOLUME, true) ?: true)
        private set

    internal fun syncFromPrefs(p: SharedPreferences) {
        on = p.getBoolean(KEY_IMMERSIVE_VOLUME, true)
    }

    fun set(c: Context, value: Boolean) {
        on = value
        playerPrefs(c).edit().putBoolean(KEY_IMMERSIVE_VOLUME, value).apply()
    }
}
