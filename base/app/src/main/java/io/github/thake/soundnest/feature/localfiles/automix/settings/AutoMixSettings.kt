/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.localfiles.automix.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.thake.soundnest.BuildConfig
import io.github.thake.soundnest.automix.segue.MixIdea
import io.github.thake.soundnest.core.di.Graph
import io.github.thake.soundnest.core.playback.LocalPlayer
import io.github.thake.soundnest.core.preferences.PolicySyncedSetting
import io.github.thake.soundnest.core.preferences.featurePrefs
import io.github.thake.soundnest.feature.player.settings.CrossfadePref
import io.github.thake.soundnest.feature.player.settings.applyCrossfade
import io.github.thake.soundnest.feature.localfiles.automix.engine.AutoMix
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy

/**
 * All user and adaptive preferences for AutoMix (DJ-style transitions between local files),
 * backed by `"feature_automix"`. Enforces [ParentalPolicy.allowLocalFiles] so disabling Local Files
 * turns AutoMix off cleanly.
 */
object AutoMixSettings : PolicySyncedSetting {
    private const val PREF_NAME = "automix"
    private const val KEY_ON = "automix_on"
    private const val KEY_DURATION_MIN = "automix_duration_min_seconds"
    private const val KEY_DURATION_MAX = "automix_duration_max_seconds"
    private const val KEY_PERFORMANCE_PRIORITY = "automix_performance_priority"
    private const val KEY_NEXT_STYLE = "automix_next_style"
    private const val KEY_ALBUM_ORDER = "automix_album_order"
    private const val KEY_SHOW = "automix_show"
    private const val KEY_LENGTH = "automix_length"
    private const val KEY_SESSION_DJ = "automix_session_dj"
    private const val KEY_TEMPO = "automix_tempo"
    private const val KEY_EQ = "automix_eq"
    private const val KEY_PULSE = "automix_pulse"
    private const val KEY_STYLE = "automix_style"
    private const val KEY_HARMONIC = "automix_harmonic"
    private const val KEY_PODCASTS = "automix_podcasts"
    private const val KEY_MOBILE_DATA = "automix_mobile_data"
    private const val KEY_SEGUE = "automix_segue"
    private const val KEY_ORPHEON = "automix_orpheon"
    private const val KEY_IDEAS = "automix_ideas"
    private const val KEY_INTRO_SEEN = "automix_intro_seen"
    private const val KEY_START_LEAD = "automix_start_lead"

    private fun prefs(c: Context) = c.featurePrefs(PREF_NAME)
    private val p get() = runCatching { prefs(Graph.app) }.getOrNull()

    private fun defaultIdeas(): Int =
        if (BuildConfig.APPLICATION_ID == "io.github.thake.soundnest.automixlab") MixIdea.DEFAULT else 0

    private fun loadDurationPair(sp: android.content.SharedPreferences?): Pair<Int, Int> {
        sp ?: return 0 to 0
        val rawMin = runCatching { sp.getInt(KEY_DURATION_MIN, 0) }.getOrDefault(-1)
        val rawMax = runCatching { sp.getInt(KEY_DURATION_MAX, 0) }.getOrDefault(-1)
        val pair = normalizeDurationPair(rawMin, rawMax)
        if (pair.first != rawMin || pair.second != rawMax) {
            sp.edit().putInt(KEY_DURATION_MIN, pair.first).putInt(KEY_DURATION_MAX, pair.second).apply()
        }
        return pair
    }

    private val initialDurationPair = loadDurationPair(p)

    /** The setting, Compose state (off by default). */
    var on by mutableStateOf(p?.getBoolean(KEY_ON, false) ?: false)
        private set

    fun set(value: Boolean, context: Context = Graph.app) {
        on = value
        if (!value) AutoMix.cancelRequests()
        prefs(context).edit().putBoolean(KEY_ON, value).apply()
        // The Spotify songs' fade follows: a short one with AutoMix on (see TransitionPref).
        applyCrossfade(CrossfadePref.seconds)
        AutoMix.journal("AutoMix ${if (value) "on" else "off"}")
    }

    /**
     * What Next does (see LocalPlayer.mixOnSkip): 0 cuts, 1 a quick mix from the next downbeat (B from
     * its start), 2 the real mix (A out on a downbeat or phrase within 4 s, B in at its best entry,
     * 4 bars with the bass swap). The real mix by default.
     */
    var nextStyle by mutableStateOf((p?.getInt(KEY_NEXT_STYLE, 2) ?: 2).coerceIn(0, 2))
        private set
    fun changeNextStyle(value: Int, context: Context = Graph.app) {
        val v = value.coerceIn(0, 2)
        nextStyle = v
        prefs(context).edit().putInt(KEY_NEXT_STYLE, v).apply()
    }
    /** Next mixes at all. */
    val mixOnNext: Boolean get() = nextStyle != 0

    /** No mix between two songs that follow each other on their album. On by default. */
    var albumOrder by mutableStateOf(p?.getBoolean(KEY_ALBUM_ORDER, true) ?: true)
        private set
    fun changeAlbumOrder(value: Boolean, context: Context = Graph.app) {
        albumOrder = value
        prefs(context).edit().putBoolean(KEY_ALBUM_ORDER, value).apply()
        LocalPlayer.autoMixSettingsChanged()
    }

    /** The mix shown in the player: its stretch on the seek bar, the cover and its glow pulsing with it. On by default. */
    var showInPlayer by mutableStateOf(p?.getBoolean(KEY_SHOW, true) ?: true)
        private set
    fun changeShowInPlayer(value: Boolean, context: Context = Graph.app) {
        showInPlayer = value
        prefs(context).edit().putBoolean(KEY_SHOW, value).apply()
    }

    /** Mix length: 0 short, 1 as the music says, 2 long. */
    var mixLength by mutableStateOf((p?.getInt(KEY_LENGTH, 1) ?: 1).coerceIn(0, 2))
        private set
    fun changeMixLength(value: Int, context: Context = Graph.app) {
        val v = value.coerceIn(0, 2)
        mixLength = v
        prefs(context).edit().putInt(KEY_LENGTH, v).apply()
        LocalPlayer.autoMixSettingsChanged()
    }

    /** Optional hard bounds on overlap seconds; minimum 0 is automatic and maximum 0 is unlimited. */
    var minDurationSeconds by mutableStateOf(initialDurationPair.first)
        private set
    var maxDurationSeconds by mutableStateOf(initialDurationPair.second)
        private set

    /** Updates the pair atomically so a temporary min > max can never reach the planner. */
    fun changeDurationBounds(minSeconds: Int, maxSeconds: Int, context: Context = Graph.app): Boolean {
        if (normalizeDurationPair(minSeconds, maxSeconds) != (minSeconds to maxSeconds)) return false
        minDurationSeconds = minSeconds
        maxDurationSeconds = maxSeconds
        prefs(context).edit()
            .putInt(KEY_DURATION_MIN, minSeconds)
            .putInt(KEY_DURATION_MAX, maxSeconds)
            .apply()
        LocalPlayer.autoMixSettingsChanged()
        return true
    }

    /** Same planner and signal path, with one analysis worker thread and no third-song prefetch. */
    var performancePriority by mutableStateOf(p?.getBoolean(KEY_PERFORMANCE_PRIORITY, false) ?: false)
        private set
    fun changePerformancePriority(value: Boolean, context: Context = Graph.app) {
        performancePriority = value
        prefs(context).edit().putBoolean(KEY_PERFORMANCE_PRIORITY, value).apply()
        LocalPlayer.autoMixSettingsChanged()
    }

    /** A DJ session may leave at an earlier musical section; independent of overlap length. */
    var sessionDj by mutableStateOf(p?.getBoolean(KEY_SESSION_DJ, true) ?: true)
        private set
    fun changeSessionDj(value: Boolean, context: Context = Graph.app) {
        sessionDj = value
        prefs(context).edit().putBoolean(KEY_SESSION_DJ, value).apply()
        LocalPlayer.autoMixSettingsChanged()
    }

    /** How far the next song's tempo may be bent to match: 0 strict (4 %), 1 normal (8 %), 2 wide (12 %). */
    var tempoRange by mutableStateOf((p?.getInt(KEY_TEMPO, 1) ?: 1).coerceIn(0, 2))
        private set
    fun changeTempoRange(value: Int, context: Context = Graph.app) {
        val v = value.coerceIn(0, 2)
        tempoRange = v
        prefs(context).edit().putInt(KEY_TEMPO, v).apply()
        LocalPlayer.autoMixSettingsChanged()
    }

    /** The DJ EQ: the bass swap and the filters (each player's DynamicsProcessing); off, only the volumes move. */
    var djEq by mutableStateOf(p?.getBoolean(KEY_EQ, true) ?: true)
        private set
    fun changeDjEq(value: Boolean, context: Context = Graph.app) {
        djEq = value
        prefs(context).edit().putBoolean(KEY_EQ, value).apply()
        LocalPlayer.autoMixEqChanged()
    }

    /** How much the player moves with the mix: 0 not at all (the stretch on the bar stays), 1 subtle, 2 strong. */
    var pulseLevel by mutableStateOf((p?.getInt(KEY_PULSE, 2) ?: 2).coerceIn(0, 2))
        private set
    fun changePulseLevel(value: Int, context: Context = Graph.app) {
        val v = value.coerceIn(0, 2)
        pulseLevel = v
        prefs(context).edit().putInt(KEY_PULSE, v).apply()
    }

    /**
     * The transition style: 0 beat blend (beat-matched where both grids allow, else a cut on the beat
     * or a fade), 1 always a cut on A's downbeat (a fade when A has no grid), 2 always a plain fade.
     */
    var style by mutableStateOf((p?.getInt(KEY_STYLE, 0) ?: 0).coerceIn(0, 2))
        private set
    fun changeStyle(value: Int, context: Context = Graph.app) {
        val v = value.coerceIn(0, 2)
        style = v
        prefs(context).edit().putInt(KEY_STYLE, v).apply()
        LocalPlayer.autoMixSettingsChanged()
    }

    /** Harmonic mixing: the songs' keys weigh strongly in the choice of the mix; off, they are ignored. On by default. */
    var harmonic by mutableStateOf(p?.getBoolean(KEY_HARMONIC, true) ?: true)
        private set
    fun changeHarmonic(value: Boolean, context: Context = Graph.app) {
        harmonic = value
        prefs(context).edit().putBoolean(KEY_HARMONIC, value).apply()
        LocalPlayer.autoMixSettingsChanged()
    }

    /** Podcast episodes and audiobooks mixed like songs; off (the default), they start and end whole. */
    var mixPodcasts by mutableStateOf(p?.getBoolean(KEY_PODCASTS, false) ?: false)
        private set
    fun changeMixPodcasts(value: Boolean, context: Context = Graph.app) {
        mixPodcasts = value
        prefs(context).edit().putBoolean(KEY_PODCASTS, value).apply()
        LocalPlayer.autoMixSettingsChanged()
    }

    /** Streams analysed on mobile data too. On by default: off, streams heard on mobile data are not mixed. */
    var analyzeOnMobileData by mutableStateOf(p?.getBoolean(KEY_MOBILE_DATA, true) ?: true)
        private set
    fun changeAnalyzeOnMobileData(value: Boolean, context: Context = Graph.app) {
        analyzeOnMobileData = value
        prefs(context).edit().putBoolean(KEY_MOBILE_DATA, value).apply()
        if (value) AutoMix.clearMobileDataRefusals()
        LocalPlayer.autoMixSettingsChanged()
    }

    /** The data-plan transitions, in place of the planner; only for the beat blend style. On unless the listener turned them off. */
    var segue by mutableStateOf(p?.getBoolean(KEY_SEGUE, true) ?: true)
        private set
    fun changeSegue(value: Boolean, context: Context = Graph.app) {
        segue = value
        prefs(context).edit().putBoolean(KEY_SEGUE, value).apply()
        LocalPlayer.autoMixSettingsChanged()
    }
    val segueActive: Boolean get() = segue && style == 0

    /** The Orpheon planner (on by default), else the compact one it replaces, kept as a way back. */
    var orpheon by mutableStateOf(p?.getBoolean(KEY_ORPHEON, true) ?: true)
        private set
    fun changeOrpheon(value: Boolean, context: Context = Graph.app) {
        orpheon = value
        prefs(context).edit().putBoolean(KEY_ORPHEON, value).apply()
        LocalPlayer.autoMixSettingsChanged()
    }

    /** The [MixIdea]s the Orpheon planner plays with, as their bits: each is one switch of the Mix Lab. */
    var ideas by mutableStateOf(p?.getInt(KEY_IDEAS, defaultIdeas()) ?: defaultIdeas())
        private set
    fun hasIdea(idea: MixIdea) = ideas and idea.bit != 0
    fun changeIdea(idea: MixIdea, on: Boolean, context: Context = Graph.app) {
        ideas = if (on) ideas or idea.bit else ideas and idea.bit.inv()
        prefs(context).edit().putInt(KEY_IDEAS, ideas).apply()
        LocalPlayer.autoMixSettingsChanged()
    }

    /** The explanation shown once, the first time AutoMix is picked in Transitions (both looks). */
    var introSeen by mutableStateOf(p?.getBoolean(KEY_INTRO_SEEN, false) ?: false)
        private set
    fun markIntroSeen(context: Context = Graph.app) {
        introSeen = true
        prefs(context).edit().putBoolean(KEY_INTRO_SEEN, true).apply()
    }

    /** How long a deck takes from play to sound (ms), learned at each start and kept across launches. */
    var startLead by mutableDoubleStateOf((p?.getFloat(KEY_START_LEAD, 120f) ?: 120f).toDouble())
        private set
    fun setStartLead(value: Double, context: Context = Graph.app) {
        val clamped = value.coerceIn(0.0, 500.0)
        startLead = clamped
        prefs(context).edit().putFloat(KEY_START_LEAD, clamped.toFloat()).apply()
    }

    fun init(context: Context) {
        val sp = prefs(context)
        on = sp.getBoolean(KEY_ON, false)
        nextStyle = sp.getInt(KEY_NEXT_STYLE, 2).coerceIn(0, 2)
        albumOrder = sp.getBoolean(KEY_ALBUM_ORDER, true)
        showInPlayer = sp.getBoolean(KEY_SHOW, true)
        mixLength = sp.getInt(KEY_LENGTH, 1).coerceIn(0, 2)
        val pair = loadDurationPair(sp)
        minDurationSeconds = pair.first
        maxDurationSeconds = pair.second
        performancePriority = sp.getBoolean(KEY_PERFORMANCE_PRIORITY, false)
        sessionDj = sp.getBoolean(KEY_SESSION_DJ, true)
        tempoRange = sp.getInt(KEY_TEMPO, 1).coerceIn(0, 2)
        djEq = sp.getBoolean(KEY_EQ, true)
        pulseLevel = sp.getInt(KEY_PULSE, 2).coerceIn(0, 2)
        style = sp.getInt(KEY_STYLE, 0).coerceIn(0, 2)
        harmonic = sp.getBoolean(KEY_HARMONIC, true)
        mixPodcasts = sp.getBoolean(KEY_PODCASTS, false)
        analyzeOnMobileData = sp.getBoolean(KEY_MOBILE_DATA, true)
        segue = sp.getBoolean(KEY_SEGUE, true)
        orpheon = sp.getBoolean(KEY_ORPHEON, true)
        ideas = sp.getInt(KEY_IDEAS, defaultIdeas())
        introSeen = sp.getBoolean(KEY_INTRO_SEEN, false)
        startLead = sp.getFloat(KEY_START_LEAD, 120f).toDouble()
    }

    override fun enforcePolicy(context: Context, policy: ParentalPolicy) {
        if (!policy.localFiles.enabled && on) {
            set(false, context)
            runCatching { LocalPlayer.autoMixChanged(false) }
        }
    }
}

/** Invalid persisted pairs recover together to Auto; max=0 means no configured maximum. */
internal fun normalizeDurationPair(minSeconds: Int, maxSeconds: Int): Pair<Int, Int> =
    if (minSeconds !in 0..90 || maxSeconds !in 0..90 || (maxSeconds != 0 && minSeconds > maxSeconds)) 0 to 0
    else minSeconds to maxSeconds
