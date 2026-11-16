package com.music.spotui.ui.immersive.pages

import com.music.spotui.ui.immersive.sheets.ImmersiveAlertDialog
import com.music.spotui.data.preferences.TransitionPref.Mode as TransitionMode
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.myspoti.engine.R
import com.music.spotui.data.preferences.StreamingQuality
import com.music.spotui.data.preferences.getStreamingQuality
import com.music.spotui.data.preferences.setStreamingQuality
import com.music.spotui.ui.immersive.Immersive
import com.music.spotui.ui.look.rememberReduceMotion
import com.music.spotui.ui.navigation.Routes
import com.music.spotui.ui.screens.shareDiagnosticReport
import kotlinx.coroutines.launch

/** Where the Immersive Settings are: the list of categories, one category, or a choice inside it. */
private enum class SettingsPage { Root, Playback, Lyrics, StreamingWifi, StreamingCell, Crossfade, Storage, Data, Display, Look, Rotation, Language, LyricsLanguage, App, Accounts, Library }

private val StreamingChoices = listOf(
    Triple(StreamingQuality.AUTO, R.string.quality_streaming_auto, R.string.quality_streaming_auto_detail),
    Triple(StreamingQuality.LOW, R.string.quality_streaming_low, R.string.quality_streaming_low_detail),
    Triple(StreamingQuality.NORMAL, R.string.quality_streaming_normal, R.string.quality_streaming_normal_detail),
    Triple(StreamingQuality.HIGH, R.string.quality_streaming_high, R.string.quality_streaming_high_detail),
    // Offered with a Premium account only (see the page below).
    Triple(StreamingQuality.VERY_HIGH, R.string.quality_streaming_very_high, R.string.quality_streaming_very_high_detail),
)

/**
 * Settings of the Immersive look, replacing the Classic `SettingsScreen`: the same preferences
 * and actions (every switch writes through the same stores) as iOS-style inset groups, one page
 * per category and one for each choice (quality, language, look...). A pushed page slides in on a
 * spring and back leaves it; with "reduce motion" they cross-fade.
 */
@Composable
fun ImmersiveSettingsScreen(navController: NavController) {
    // The pages pushed so far, so back pops one before it leaves Settings.
    // Saved by name, so a recreation (a language change, say) keeps the page the user is on.
    val stack = rememberSaveable(
        saver = listSaver(
            save = { list -> list.map { it.name } },
            restore = { names -> names.mapNotNull { runCatching { SettingsPage.valueOf(it) }.getOrNull() }.toMutableStateList() },
        ),
    ) { mutableStateListOf<SettingsPage>() }
    val page = stack.lastOrNull() ?: SettingsPage.Root
    val reduce = rememberReduceMotion()
    var pushing by remember { mutableStateOf(true) }
    val pop: () -> Unit = { pushing = false; stack.removeAt(stack.lastIndex) }
    BackHandler(enabled = stack.isNotEmpty(), onBack = pop)
    val open: (SettingsPage) -> Unit = { pushing = true; stack += it }
    val back: () -> Unit = { if (stack.isNotEmpty()) pop() else navController.popBackStack() }
    AnimatedContent(
        targetState = page,
        transitionSpec = {
            when {
                reduce -> fadeIn(tween(com.music.spotui.ui.immersive.motion.ImmersiveMotion.FADE_MS)) togetherWith fadeOut(tween(com.music.spotui.ui.immersive.motion.ImmersiveMotion.FADE_MS))
                !pushing ->
                    (slideInHorizontally(tween(com.music.spotui.ui.immersive.motion.ImmersiveMotion.MEDIUM_MS)) { -it / 3 } + fadeIn(tween(com.music.spotui.ui.immersive.motion.ImmersiveMotion.MEDIUM_MS))) togetherWith
                        (slideOutHorizontally(tween(com.music.spotui.ui.immersive.motion.ImmersiveMotion.MEDIUM_MS)) { it } + fadeOut(tween(160)))
                else ->
                    (slideInHorizontally(spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset(1, 1))) { it } + fadeIn(tween(160))) togetherWith
                        (slideOutHorizontally(tween(com.music.spotui.ui.immersive.motion.ImmersiveMotion.MEDIUM_MS)) { -it / 3 } + fadeOut(tween(com.music.spotui.ui.immersive.motion.ImmersiveMotion.MEDIUM_MS)))
            }
        },
        label = "settings pages",
    ) { p ->
        when (p) {
            SettingsPage.Root -> SettingsRoot(navController, open, back)
            SettingsPage.Playback -> PlaybackPage(navController, open, back)
            SettingsPage.Lyrics -> LyricsPage(open, back)
            SettingsPage.StreamingWifi -> StreamingPage(cellular = false, back)
            SettingsPage.StreamingCell -> StreamingPage(cellular = true, back)
            SettingsPage.Crossfade -> CrossfadePage(back)
            SettingsPage.Storage -> StoragePage(back)
            SettingsPage.Data -> DataPage(back)
            SettingsPage.Display -> DisplayPage(open, back)
            SettingsPage.Look -> LookPage(back)
            SettingsPage.Rotation -> RotationPage(back)
            SettingsPage.Language -> LanguagePage(back)
            SettingsPage.LyricsLanguage -> LyricsLanguagePage(back)
            SettingsPage.App -> AppPage(back)
            SettingsPage.Accounts -> AccountsPage(back)
            SettingsPage.Library -> LibraryPage(navController, back)
        }
    }
}

@Composable
private fun SettingsRoot(navController: NavController, open: (SettingsPage) -> Unit, back: () -> Unit) {
    val crossfade = com.music.spotui.data.preferences.CrossfadePref.seconds
    // The search button: a field over the page, the rows of every page it finds, a tap opens one.
    var query by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = query != null) { query = null }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    ImmersivePage(
        title = stringResource(R.string.settings_title), onBack = back,
        actions = {
            com.music.spotui.ui.immersive.shell.ImmersiveBarAction(
                Icons.Rounded.Search, stringResource(R.string.settings_search), onClick = { query = if (query == null) "" else null },
            )
        },
    ) {
        query?.let { q ->
            immersiveBlock("search") {
                com.music.spotui.ui.immersive.components.ImmersiveSearchField(
                    query = q, onQueryChange = { query = it }, onSubmit = {}, focusRequester = focus,
                    placeholder = stringResource(R.string.settings_search_hint),
                )
                androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
            }
            if (q.isNotBlank()) {
                val hits = com.music.spotui.ui.screens.SettingsSearch.search(com.music.spotui.ui.screens.SettingsSearch.immersive, q) { ctx.getString(it) }
                    .filter { runCatching { SettingsPage.valueOf(it.place) }.isSuccess }
                if (hits.isEmpty()) immersiveBlock("search:none") { Text(stringResource(R.string.settings_search_empty), style = com.music.spotui.ui.immersive.Immersive.Subhead) }
                immersiveGroup("search:hits") {
                    hits.forEach { h ->
                        row {
                            ImmersiveGroupRow(
                                stringResource(h.title), detail = if (h.detail != 0) stringResource(h.detail) else null, chevron = true,
                                onClick = {
                                    query = null
                                    val target = SettingsPage.valueOf(h.place)
                                    if (target == SettingsPage.Accounts) {
                                        com.music.spotui.parental.ParentSecurityManager.runProtected(
                                            ctx,
                                            com.music.spotui.parental.AppSurface.ACCOUNTS_AND_LOGOUT,
                                        ) { open(SettingsPage.Accounts) }
                                    } else open(target)
                                },
                            )
                        }
                    }
                }
                return@ImmersivePage
            }
        }
        immersiveGroup("parental") {
            row {
                com.music.spotui.parental.ParentSecurityControlsSection(
                    onOpenParentSettings = { navController.navigate(com.music.spotui.ui.navigation.Routes.ParentSettings.route) },
                )
            }
        }
        immersiveGroup("root") {
            row { ImmersiveGroupRow(stringResource(R.string.settings_section_display), icon = Icons.Rounded.Palette, chevron = true, onClick = { open(SettingsPage.Display) }) }
            row { ImmersiveGroupRow(stringResource(R.string.settings_section_playback), icon = Icons.Rounded.PlayArrow, chevron = true, onClick = { open(SettingsPage.Playback) }) }
            row {
                ImmersiveGroupRow(
                    stringResource(R.string.settings_transitions_title), icon = Icons.Rounded.GraphicEq, chevron = true,
                    value = transitionValue(crossfade), onClick = { open(SettingsPage.Crossfade) },
                )
            }
            row { ImmersiveGroupRow(stringResource(R.string.settings_section_library), icon = Icons.Rounded.LibraryMusic, chevron = true, onClick = { open(SettingsPage.Library) }) }
            row { ImmersiveGroupRow(stringResource(R.string.settings_section_lyrics), icon = Icons.Rounded.Lyrics, chevron = true, onClick = { open(SettingsPage.Lyrics) }) }
        }
        immersiveGroup("more") {
            row { ImmersiveGroupRow(stringResource(R.string.settings_section_data), icon = Icons.Rounded.SignalCellularAlt, chevron = true, onClick = { open(SettingsPage.Data) }) }
            row { ImmersiveGroupRow(stringResource(R.string.settings_section_storage), icon = Icons.Rounded.Storage, chevron = true, onClick = { open(SettingsPage.Storage) }) }
            row { ImmersiveGroupRow(stringResource(R.string.settings_section_app), icon = Icons.Rounded.Info, chevron = true, onClick = { open(SettingsPage.App) }) }
            row {
                ImmersiveGroupRow(
                    stringResource(R.string.accounts_title), detail = com.music.spotui.ui.components.switchAccountDetail(),
                    icon = Icons.Rounded.Person, chevron = true,
                    onClick = {
                        com.music.spotui.parental.ParentSecurityManager.runProtected(
                            ctx,
                            com.music.spotui.parental.AppSurface.ACCOUNTS_AND_LOGOUT,
                        ) { open(SettingsPage.Accounts) }
                    },
                )
            }
        }
    }
}

/** Off, a crossfade of N s or AutoMix: the value of the Transitions rows. */
@Composable
private fun transitionValue(crossfade: Int): String = when (com.music.spotui.data.preferences.TransitionPref.mode) {
    com.music.spotui.data.preferences.TransitionPref.Mode.AUTOMIX -> stringResource(R.string.settings_automix_title)
    com.music.spotui.data.preferences.TransitionPref.Mode.CROSSFADE -> stringResource(R.string.settings_transition_crossfade_value, crossfade)
    com.music.spotui.data.preferences.TransitionPref.Mode.OFF -> stringResource(R.string.settings_crossfade_off)
}

// ---------------------------------------------------------------- Playback

@Composable
private fun PlaybackPage(navController: NavController, open: (SettingsPage) -> Unit, back: () -> Unit) {
    val ctx = LocalContext.current
    val wifi = getStreamingQuality(ctx, false)
    val cell = getStreamingQuality(ctx, true)
    val label = { q: StreamingQuality -> StreamingChoices.first { it.first == q }.second }
    var headsetSkip by remember { mutableStateOf(app.myspoti.host.android.PlaybackService.skipsOnEpisodes(ctx)) }
    var keepPlaying by remember { mutableStateOf(app.myspoti.host.android.PlaybackService.keepsPlayingOnClose(ctx)) }
    val crossfade = com.music.spotui.data.preferences.CrossfadePref.seconds
    ImmersivePage(title = stringResource(R.string.settings_section_playback), onBack = back) {
        immersiveGroup("quality", footerRes = R.string.quality_streaming_detail) {
            row { ImmersiveGroupRow(stringResource(R.string.quality_streaming_wifi_title), value = stringResource(label(wifi)), chevron = true, onClick = { open(SettingsPage.StreamingWifi) }) }
            row { ImmersiveGroupRow(stringResource(R.string.quality_streaming_cell_title), value = stringResource(label(cell)), chevron = true, onClick = { open(SettingsPage.StreamingCell) }) }
        }
        immersiveGroup("playback") {
            row {
                ImmersiveSwitchRow(
                    stringResource(R.string.settings_autoplay_title),
                    checked = com.music.spotui.data.preferences.AutoplayPref.on,
                    onChecked = { com.music.spotui.data.preferences.AutoplayPref.set(ctx, it) },
                    detail = stringResource(R.string.settings_autoplay_detail),
                )
            }
            row {
                ImmersiveSwitchRow(
                    stringResource(R.string.myspoti_shuffle_title),
                    checked = com.music.spotui.data.preferences.AppShufflePref.on,
                    onChecked = { com.music.spotui.data.preferences.AppShufflePref.set(ctx, it) },
                    detail = stringResource(R.string.myspoti_shuffle_detail),
                )
            }
            row {
                ImmersiveGroupRow(
                    stringResource(R.string.settings_transitions_title), detail = stringResource(R.string.settings_transitions_detail),
                    value = transitionValue(crossfade),
                    chevron = true, onClick = { open(SettingsPage.Crossfade) },
                )
            }
            // The local player's equalizer, speed and silence skipping (never Spotify's own songs).
            row {
                ImmersiveGroupRow(
                    stringResource(R.string.equalizer_title), detail = stringResource(R.string.equalizer_settings_detail),
                    value = if (com.music.spotui.data.preferences.EqualizerPref.enabled) stringResource(com.music.spotui.ui.screens.eqPresetLabel(com.music.spotui.data.preferences.EqualizerPref.preset)) else null,
                    chevron = true, onClick = { navController.navigate(Routes.Equalizer.route) },
                )
            }
        }
        // What keeps the music going and what the headset buttons do.
        immersiveGroup("controls", headerRes = R.string.settings_section_controls) {
            row {
                var nativeAudio by remember { mutableStateOf(com.music.spotui.di.NativeAudio.enabled) }
                ImmersiveSwitchRow(
                    stringResource(R.string.settings_native_audio_title), checked = nativeAudio,
                    onChecked = { nativeAudio = it; com.music.spotui.di.NativeAudio.set(ctx, it) },
                    detail = stringResource(R.string.settings_native_audio_detail),
                )
            }
            row {
                ImmersiveSwitchRow(
                    stringResource(R.string.settings_keep_playing_title), checked = keepPlaying,
                    onChecked = { keepPlaying = it; app.myspoti.host.android.PlaybackService.setKeepsPlayingOnClose(ctx, it) },
                    detail = stringResource(R.string.settings_keep_playing_detail),
                )
            }
            row {
                ImmersiveSwitchRow(
                    stringResource(R.string.settings_headset_skip_title), checked = headsetSkip,
                    onChecked = { headsetSkip = it; app.myspoti.host.android.PlaybackService.setSkipsOnEpisodes(ctx, it) },
                    detail = stringResource(R.string.settings_headset_skip_detail),
                )
            }
        }
    }
}

/** The library: what it shows (local files, Live Radio, hidden songs) and new releases. */
@Composable
private fun LibraryPage(navController: NavController, back: () -> Unit) {
    val ctx = LocalContext.current
    ImmersivePage(title = stringResource(R.string.settings_section_library), onBack = back) {
        immersiveGroup("library") {
            row {
                ImmersiveSwitchRow(
                    stringResource(R.string.settings_release_notify),
                    checked = com.music.spotui.data.NewReleaseNotifier.on,
                    onChecked = { com.music.spotui.data.NewReleaseNotifier.setOn(ctx, it) },
                    detail = stringResource(R.string.settings_release_notify_detail),
                )
            }
            row {
                ImmersiveSwitchRow(
                    stringResource(R.string.settings_local_files_shown),
                    checked = com.music.spotui.data.preferences.LocalFilesPref.shown,
                    onChecked = { com.music.spotui.data.preferences.LocalFilesPref.set(ctx, it) },
                    detail = stringResource(R.string.settings_local_files_shown_detail),
                )
            }
            // Live Radio: adds the pinned Library row under Local Files (see com.music.spotui.radio).
            row {
                ImmersiveSwitchRow(
                    stringResource(R.string.live_radio_settings_title),
                    checked = com.music.spotui.radio.RadioFeaturePref.on,
                    onChecked = { com.music.spotui.radio.RadioFeaturePref.set(ctx, it) },
                    detail = stringResource(R.string.live_radio_settings_detail),
                )
            }
            // Songs marked "Not interested": Parent PIN required so kids cannot unhide songs.
            row {
                ImmersiveGroupRow(
                    stringResource(R.string.hidden_songs_title), detail = stringResource(R.string.hidden_songs_detail),
                    chevron = true,
                    onClick = {
                        com.music.spotui.parental.ParentSecurityManager.runProtected(
                            ctx,
                            com.music.spotui.parental.AppSurface.HIDDEN_SONGS_AND_BANS,
                        ) { navController.navigate(Routes.HiddenSongs.route) }
                    },
                )
            }
        }
    }
}

/** Lyrics: their sources, word by word or by line, the screen kept on, romanization and translation. */
@Composable
private fun LyricsPage(open: (SettingsPage) -> Unit, back: () -> Unit) {
    val ctx = LocalContext.current
    var wordSync by remember { mutableStateOf(com.music.spotui.data.preferences.isLyricsWordSync(ctx)) }
    var lyricsExtra by remember { mutableStateOf(com.music.spotui.data.preferences.isLyricsExtraSources(ctx)) }
    ImmersivePage(title = stringResource(R.string.settings_section_lyrics), onBack = back) {
        immersiveGroup("lyrics") {
            // Lyrics: word by word (karaoke) first, or line by line with Spotify's first.
            row {
                ImmersiveSwitchRow(
                    stringResource(R.string.lyrics_word_sync_title), checked = wordSync,
                    onChecked = {
                        wordSync = it
                        com.music.spotui.data.preferences.setLyricsWordSync(ctx, it)
                        com.music.spotui.data.api.LyricsApi.wordSync = it
                        com.music.spotui.data.api.LyricsApi.forgetAll()
                    },
                    detail = stringResource(if (wordSync) R.string.lyrics_word_sync_on else R.string.lyrics_word_sync_off),
                )
            }
            // NetEase and Genius after Spotify and LRCLIB (also in the lyrics' own menu).
            row {
                ImmersiveSwitchRow(
                    stringResource(R.string.lyrics_extra_title), checked = lyricsExtra,
                    onChecked = {
                        lyricsExtra = it
                        com.music.spotui.data.preferences.setLyricsExtraSources(ctx, it)
                        com.music.spotui.data.api.LyricsApi.extraSources = it
                        com.music.spotui.data.api.LyricsApi.extraSourcesOff = !it
                        com.music.spotui.data.api.LyricsApi.forgetAll()
                    },
                    detail = stringResource(R.string.lyrics_extra_summary),
                )
            }
            // Genius: the meaning of songs and the notes on their lines, and its lyrics as a last resort.
            row {
                ImmersiveSwitchRow(
                    stringResource(R.string.settings_genius_title),
                    checked = com.music.spotui.data.preferences.GeniusPref.on,
                    onChecked = { com.music.spotui.data.preferences.GeniusPref.set(ctx, it) },
                    detail = stringResource(R.string.settings_genius_detail),
                )
            }
            row {
                ImmersiveSwitchRow(
                    stringResource(R.string.settings_lyrics_keep_awake_title),
                    checked = com.music.spotui.data.preferences.LyricsKeepAwakePref.on,
                    onChecked = { com.music.spotui.data.preferences.LyricsKeepAwakePref.set(ctx, it) },
                    detail = stringResource(R.string.settings_lyrics_keep_awake_detail),
                )
            }
            // The song in Latin letters under its lines, when the lyrics carry one (as in the lyrics' menu).
            row {
                ImmersiveSwitchRow(
                    stringResource(R.string.lyrics_romanization),
                    checked = com.music.spotui.data.preferences.LyricsRomanizationPref.on,
                    onChecked = { com.music.spotui.data.preferences.LyricsRomanizationPref.set(ctx, it) },
                    detail = stringResource(R.string.settings_lyrics_romanization_detail),
                )
            }
            row {
                val translateTo = com.music.spotui.data.preferences.getLyricTranslateLang(ctx)
                ImmersiveGroupRow(
                    stringResource(R.string.lyrics_translation_title), detail = stringResource(R.string.lyrics_translation_detail),
                    value = if (translateTo.isEmpty()) stringResource(R.string.language_auto)
                    else com.music.spotui.data.AppLanguage.nativeName(translateTo),
                    chevron = true, onClick = { open(SettingsPage.LyricsLanguage) },
                )
            }
        }
    }
}

@Composable
private fun StreamingPage(cellular: Boolean, back: () -> Unit) {
    val ctx = LocalContext.current
    var current by remember(cellular) { mutableStateOf(getStreamingQuality(ctx, cellular)) }
    // Very high is the Web Player's Premium level: offered with a Premium account, kept while chosen.
    val premium by app.myspoti.spotify.AccountPlan.premium.collectAsState()
    val choices = StreamingChoices.filter { it.first != StreamingQuality.VERY_HIGH || premium == true || current == StreamingQuality.VERY_HIGH }
    ImmersivePage(
        title = stringResource(if (cellular) R.string.quality_streaming_cell_title else R.string.quality_streaming_wifi_title),
        onBack = back,
    ) {
        immersiveGroup("choices", footerRes = R.string.quality_streaming_detail) {
            choices.forEach { (value, label, detail) ->
                row {
                    ImmersiveChoiceRow(stringResource(label), selected = current == value, detail = stringResource(detail),
                        onClick = { current = value; setStreamingQuality(ctx, value, cellular) })
                }
            }
        }
    }
}

/** Transitions: off, a crossfade of a chosen length, or AutoMix with its options. */
@Composable
private fun CrossfadePage(back: () -> Unit) {
    val ctx = LocalContext.current
    val transition = com.music.spotui.data.preferences.TransitionPref
    val automix = com.music.spotui.automix.AutoMix
    val mode = transition.mode
    val scope = rememberCoroutineScope()
    var intro by remember { mutableStateOf(false) }
    // Why the song playing will not mix into the next one, while that is so.
    val reason = if (mode == TransitionMode.AUTOMIX) com.music.spotui.ui.components.autoMixReasonText(automix.whyNot()) else null
    var cacheRefresh by remember { mutableStateOf(0) }
    val cacheBytes by androidx.compose.runtime.produceState<Long?>(null, cacheRefresh) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { automix.cacheBytes() }
    }
    ImmersivePage(title = stringResource(R.string.settings_transitions_title), onBack = back) {
        immersiveGroup("mode", footerRes = R.string.settings_transitions_detail) {
            for ((m, title, detail) in listOf(
                Triple(TransitionMode.OFF, R.string.settings_crossfade_off, R.string.settings_transition_off_detail),
                Triple(TransitionMode.CROSSFADE, R.string.settings_crossfade_title, R.string.settings_transition_crossfade_detail),
                Triple(TransitionMode.AUTOMIX, R.string.settings_automix_title, R.string.settings_transition_automix_detail),
            )) {
                row {
                    ImmersiveChoiceRow(stringResource(title), selected = mode == m, detail = stringResource(detail),
                        onClick = {
                            transition.set(ctx, m)
                            if (m == TransitionMode.AUTOMIX && !com.music.spotui.ui.components.AutoMixIntro.seen) intro = true
                        })
                }
            }
        }
        if (reason != null) immersiveGroup("automix-now") {
            row { ImmersiveGroupRow(stringResource(reason)) }
        }
        if (mode == TransitionMode.CROSSFADE) immersiveGroup("length", headerRes = R.string.settings_crossfade_length, footerRes = R.string.settings_crossfade_detail) {
            // Any length from 1 to 12 s on a slider (it was four fixed choices).
            row {
                com.music.spotui.ui.components.CrossfadeSlider(Immersive.Accent, Immersive.Text, Immersive.TextSecondary,
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            }
        }
        // AutoMix, in the order its engine decides: how the songs are joined, how long and how far
        // the tempo may move, what Next does, then the musical options, the analysis and what is shown.
        if (mode == TransitionMode.AUTOMIX) immersiveGroup("automix-style", headerRes = R.string.settings_automix_style, footerRes = R.string.settings_automix_style_detail) {
            listOf(R.string.settings_automix_style_blend, R.string.settings_automix_style_cut, R.string.settings_automix_style_fade).forEachIndexed { i, label ->
                row { ImmersiveChoiceRow(stringResource(label), selected = automix.style == i, onClick = { automix.changeStyle(i) }) }
            }
            // The data-plan transitions are the blend's own engine: shown only with it.
            if (automix.style == 0) row {
                ImmersiveSwitchRow(stringResource(R.string.settings_automix_segue), checked = automix.segue,
                    onChecked = automix::changeSegue, detail = stringResource(R.string.settings_automix_segue_detail))
            }
        }
        if (mode == TransitionMode.AUTOMIX) immersiveGroup("automix-length", headerRes = R.string.settings_automix_length, footerRes = R.string.settings_automix_length_detail) {
            listOf(R.string.settings_automix_length_short, R.string.settings_automix_length_auto, R.string.settings_automix_length_long).forEachIndexed { i, label ->
                row { ImmersiveChoiceRow(stringResource(label), selected = automix.mixLength == i, onClick = { automix.changeMixLength(i) }) }
            }
            row { DurationStepper(R.string.settings_automix_duration_min, automix.minDurationSeconds, zero = R.string.settings_automix_duration_auto) { v ->
                automix.changeDurationBounds(v, if (automix.maxDurationSeconds in 1 until v) v else automix.maxDurationSeconds)
            } }
            row { DurationStepper(R.string.settings_automix_duration_max, automix.maxDurationSeconds, zero = R.string.settings_automix_duration_none) { v ->
                automix.changeDurationBounds(if (v in 1 until automix.minDurationSeconds) v else automix.minDurationSeconds, v)
            } }
        }
        if (mode == TransitionMode.AUTOMIX) immersiveGroup("automix-tempo", headerRes = R.string.settings_automix_tempo, footerRes = R.string.settings_automix_tempo_detail) {
            listOf(R.string.settings_automix_tempo_strict, R.string.settings_automix_tempo_normal, R.string.settings_automix_tempo_wide).forEachIndexed { i, label ->
                row { ImmersiveChoiceRow(stringResource(label), selected = automix.tempoRange == i, onClick = { automix.changeTempoRange(i) }) }
            }
        }
        if (mode == TransitionMode.AUTOMIX) immersiveGroup("automix-next", headerRes = R.string.settings_automix_on_next, footerRes = R.string.settings_automix_on_next_detail) {
            listOf(R.string.settings_automix_next_cut, R.string.settings_automix_next_quick, R.string.settings_automix_next_full).forEachIndexed { i, label ->
                row { ImmersiveChoiceRow(stringResource(label), selected = automix.nextStyle == i, onClick = { automix.changeNextStyle(i) }) }
            }
        }
        if (mode == TransitionMode.AUTOMIX) immersiveGroup("automix", headerRes = R.string.settings_automix_options, footerRes = R.string.settings_automix_detail) {
            row {
                ImmersiveSwitchRow(stringResource(R.string.settings_automix_harmonic), checked = automix.harmonic,
                    onChecked = automix::changeHarmonic, detail = stringResource(R.string.settings_automix_harmonic_detail))
            }
            row {
                ImmersiveSwitchRow(stringResource(R.string.settings_automix_eq), checked = automix.djEq,
                    onChecked = automix::changeDjEq, detail = stringResource(R.string.settings_automix_eq_detail))
            }
            row {
                ImmersiveSwitchRow(stringResource(R.string.settings_automix_album_order), checked = automix.albumOrder,
                    onChecked = automix::changeAlbumOrder, detail = stringResource(R.string.settings_automix_album_order_detail))
            }
            row {
                ImmersiveSwitchRow(stringResource(R.string.settings_automix_podcasts), checked = automix.mixPodcasts,
                    onChecked = automix::changeMixPodcasts, detail = stringResource(R.string.settings_automix_podcasts_detail))
            }
            row {
                ImmersiveSwitchRow(stringResource(R.string.settings_automix_orpheon), checked = automix.orpheon,
                    onChecked = automix::changeOrpheon, detail = stringResource(R.string.settings_automix_orpheon_detail))
            }
            // The session DJ steers the older planner only: the data-plan transitions decide on their own.
            if (!automix.segueActive) row {
                ImmersiveSwitchRow(stringResource(R.string.settings_automix_session), checked = automix.sessionDj,
                    onChecked = automix::changeSessionDj, detail = stringResource(R.string.settings_automix_session_detail))
            }
        }
        if (mode == TransitionMode.AUTOMIX) immersiveGroup("automix-analysis", headerRes = R.string.settings_automix_analysis) {
            row {
                ImmersiveSwitchRow(stringResource(R.string.settings_automix_mobile_data), checked = automix.analyzeOnMobileData,
                    onChecked = automix::changeAnalyzeOnMobileData, detail = stringResource(R.string.settings_automix_mobile_data_detail))
            }
            row {
                ImmersiveSwitchRow(stringResource(R.string.settings_automix_performance), checked = automix.performancePriority,
                    onChecked = automix::changePerformancePriority, detail = stringResource(R.string.settings_automix_performance_detail))
            }
        }
        if (mode == TransitionMode.AUTOMIX) immersiveGroup("automix-display", headerRes = R.string.settings_automix_display) {
            row {
                ImmersiveSwitchRow(stringResource(R.string.settings_automix_show), checked = automix.showInPlayer,
                    onChecked = automix::changeShowInPlayer, detail = stringResource(R.string.settings_automix_show_detail))
            }
            if (automix.showInPlayer) listOf(R.string.settings_crossfade_off, R.string.settings_automix_pulse_subtle, R.string.settings_automix_pulse_strong).forEachIndexed { i, label ->
                row { ImmersiveChoiceRow(stringResource(R.string.settings_automix_pulse) + " · " + stringResource(label), selected = automix.pulseLevel == i, onClick = { automix.changePulseLevel(i) }) }
            }
        }
        // The analyses kept on the phone: dropped, the songs are analysed again when they play.
        if (mode == TransitionMode.AUTOMIX) immersiveGroup("automix-cache") {
            row {
                ImmersiveGroupRow(
                    stringResource(R.string.settings_automix_cache),
                    detail = cacheBytes?.let { stringResource(R.string.settings_automix_cache_detail, android.text.format.Formatter.formatShortFileSize(ctx, it)) },
                    trailing = {
                        TextButton(enabled = (cacheBytes ?: 0L) > 0L, onClick = {
                            scope.launch {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { automix.clearCache() }
                                cacheRefresh++
                            }
                        }) { Text(stringResource(R.string.settings_automix_cache_clear), color = if ((cacheBytes ?: 0L) > 0L) Immersive.Accent else Immersive.TextSecondary) }
                    },
                )
            }
        }
    }
    if (intro) ImmersiveAlertDialog(
        onDismissRequest = { intro = false; com.music.spotui.ui.components.AutoMixIntro.markSeen() },
        title = { Text(stringResource(R.string.settings_automix_intro_title), style = Immersive.Headline) },
        text = { Text(stringResource(R.string.settings_automix_intro_text), style = Immersive.Subhead) },
        confirmButton = {
            TextButton(onClick = { intro = false; com.music.spotui.ui.components.AutoMixIntro.markSeen() }) {
                Text(stringResource(R.string.settings_automix_intro_ok), color = Immersive.Accent)
            }
        },
    )
}

// ---------------------------------------------------------------- Storage

/** What the app keeps on the phone: the AutoMix analysis and image caches (emptied here). */
@Composable
private fun StoragePage(back: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val storage = com.music.spotui.data.AppStorage
    var refresh by remember { mutableStateOf(0) }
    var clearing by remember { mutableStateOf(false) }
    val usage by androidx.compose.runtime.produceState<com.music.spotui.data.AppStorage.Usage?>(null, refresh) { value = storage.usage(ctx) }
    ImmersivePage(title = stringResource(R.string.settings_section_storage), onBack = back) {
        immersiveGroup("caches") {
            usage?.let { u ->
                row {
                    ImmersiveGroupRow(
                        stringResource(R.string.settings_automix_cache),
                        value = android.text.format.Formatter.formatShortFileSize(ctx, u.automix),
                    )
                }
            }
            row {
                ImmersiveGroupRow(
                    stringResource(R.string.settings_storage_clear_cache),
                    detail = usage?.let { stringResource(R.string.settings_storage_clear_cache_detail, android.text.format.Formatter.formatShortFileSize(ctx, it.caches)) },
                    enabled = !clearing,
                    onClick = {
                        clearing = true
                        scope.launch {
                            storage.clearCaches(ctx)
                            clearing = false
                            refresh++
                            android.widget.Toast.makeText(ctx, ctx.getString(R.string.settings_storage_cleared), android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Data and battery

@Composable
private fun DataPage(back: () -> Unit) {
    val ctx = LocalContext.current
    val energy by app.myspoti.power.EnergySettings.state.collectAsStateWithLifecycle()
    // Battery optimization off = music and Connect keep going with the screen off.
    var exempt by remember { mutableStateOf(app.myspoti.power.BatteryExemption.isExempt(ctx)) }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        exempt = app.myspoti.power.BatteryExemption.isExempt(ctx)
    }
    ImmersivePage(title = stringResource(R.string.settings_section_data), onBack = back) {
        immersiveGroup("data") {
            row {
                ImmersiveSwitchRow(stringResource(R.string.energy_saver_title), checked = energy.manualSaver,
                    onChecked = app.myspoti.power.EnergySettings::setSaver, detail = stringResource(R.string.energy_saver_summary))
            }
            row {
                ImmersiveSwitchRow(stringResource(R.string.data_saver_title), checked = com.music.spotui.data.DataSaver.enabled,
                    onChecked = com.music.spotui.data.DataSaver::set, detail = stringResource(R.string.data_saver_summary))
            }
            row {
                ImmersiveSwitchRow(
                    stringResource(R.string.background_playback_title), checked = exempt,
                    onChecked = {
                        if (exempt) ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        else app.myspoti.power.BatteryExemption.request(ctx)
                    },
                    detail = stringResource(if (exempt) R.string.background_playback_on else R.string.background_playback_off),
                )
            }
            // Samsung's "Sleeping apps" is a second list: the app has to be in Never sleeping apps too.
            if (app.myspoti.power.BatteryExemption.isSamsung()) row {
                ImmersiveGroupRow(
                    stringResource(R.string.background_playback_open), detail = stringResource(R.string.background_playback_samsung),
                    chevron = true, onClick = { app.myspoti.power.BatteryExemption.openAppSettings(ctx) },
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Display

@Composable
private fun DisplayPage(open: (SettingsPage) -> Unit, back: () -> Unit) {
    val ctx = LocalContext.current
    val energy by app.myspoti.power.EnergySettings.state.collectAsStateWithLifecycle()
    val look = com.music.spotui.data.preferences.LookPref.look
    val rotation = com.music.spotui.data.preferences.OrientationPref.mode
    val language = remember { com.music.spotui.data.AppLanguage.current(ctx) }
    val auto = stringResource(R.string.language_auto)
    ImmersivePage(title = stringResource(R.string.settings_section_display), onBack = back) {
        immersiveGroup("look") {
            row {
                ImmersiveGroupRow(
                    stringResource(R.string.settings_look_title),
                    value = stringResource(if (look == com.music.spotui.data.preferences.Look.CLASSIC) R.string.look_classic else R.string.look_immersive),
                    chevron = true, onClick = { open(SettingsPage.Look) },
                )
            }
            row { AccentRow(com.music.spotui.data.preferences.Look.IMMERSIVE) }
            // Material You is the Classic look's: this look's colours come from the playing cover.
            if (look == com.music.spotui.data.preferences.Look.CLASSIC && com.music.spotui.data.preferences.DynamicColorPref.available) row {
                ImmersiveSwitchRow(stringResource(R.string.settings_dynamic_color_title), checked = com.music.spotui.data.preferences.DynamicColorPref.on,
                    onChecked = { com.music.spotui.data.preferences.DynamicColorPref.set(ctx, it) }, detail = stringResource(R.string.settings_dynamic_color_detail))
            }
            row {
                ImmersiveSwitchRow(stringResource(R.string.settings_system_font_title), checked = com.music.spotui.data.preferences.AppFontPref.system,
                    onChecked = { com.music.spotui.data.preferences.AppFontPref.set(ctx, it) }, detail = stringResource(R.string.settings_system_font_detail))
            }
        }
        immersiveGroup("display") {
            if (look == com.music.spotui.data.preferences.Look.IMMERSIVE) row {
                val glass = com.music.spotui.ui.immersive.shell.liquidGlassAvailability()
                val unsupported = glass == com.music.spotui.ui.immersive.shell.LiquidGlassAvailability.UNSUPPORTED
                ImmersiveSwitchRow(
                    stringResource(R.string.settings_liquid_glass_title),
                    checked = com.music.spotui.data.preferences.LiquidGlassPref.on && !unsupported,
                    enabled = !unsupported,
                    onChecked = { com.music.spotui.data.preferences.LiquidGlassPref.set(ctx, it) },
                    detail = stringResource(
                        when (glass) {
                            com.music.spotui.ui.immersive.shell.LiquidGlassAvailability.UNSUPPORTED -> R.string.settings_liquid_glass_unsupported
                            com.music.spotui.ui.immersive.shell.LiquidGlassAvailability.REDUCED -> R.string.settings_liquid_glass_reduced
                            com.music.spotui.ui.immersive.shell.LiquidGlassAvailability.AVAILABLE -> R.string.settings_liquid_glass_detail
                        },
                    ),
                )
            }
            row {
                ImmersiveSwitchRow(stringResource(R.string.settings_amoled_theme), checked = energy.blackPreference,
                    onChecked = app.myspoti.power.EnergySettings::setBlackBackground, detail = stringResource(R.string.settings_amoled_theme_subtitle))
            }
            if (look == com.music.spotui.data.preferences.Look.CLASSIC) {
                row {
                    ImmersiveSwitchRow(stringResource(R.string.settings_player_lyrics_card_title), checked = com.music.spotui.data.preferences.PlayerLyricsCardPref.on,
                        onChecked = { com.music.spotui.data.preferences.PlayerLyricsCardPref.set(ctx, it) }, detail = stringResource(R.string.settings_player_lyrics_card_detail))
                }
            }
            row {
                ImmersiveSwitchRow(stringResource(R.string.settings_player_source_title), checked = com.music.spotui.data.preferences.PlayerSourceLinePref.on,
                    onChecked = { com.music.spotui.data.preferences.PlayerSourceLinePref.set(ctx, it) }, detail = stringResource(R.string.settings_player_source_detail))
            }
            if (look == com.music.spotui.data.preferences.Look.IMMERSIVE) {
                row {
                    ImmersiveSwitchRow(stringResource(R.string.settings_player_capsule_title), checked = com.music.spotui.data.preferences.PlayerCapsulePref.on,
                        onChecked = { com.music.spotui.data.preferences.PlayerCapsulePref.set(ctx, it) }, detail = stringResource(R.string.settings_player_capsule_detail))
                }
                row {
                    ImmersiveSwitchRow(stringResource(R.string.settings_player_modes_title), checked = com.music.spotui.data.preferences.PlayerModesPref.on,
                        onChecked = { com.music.spotui.data.preferences.PlayerModesPref.set(ctx, it) }, detail = stringResource(R.string.settings_player_modes_detail))
                }
                row {
                    ImmersiveSwitchRow(stringResource(R.string.settings_player_volume_title), checked = com.music.spotui.data.preferences.PlayerVolumePref.on,
                        onChecked = { com.music.spotui.data.preferences.PlayerVolumePref.set(ctx, it) }, detail = stringResource(R.string.settings_player_volume_detail))
                }
            }
        }
        immersiveGroup("layout") {
            row {
                ImmersiveGroupRow(
                    stringResource(R.string.settings_rotation_title),
                    value = stringResource(rotationLabel(rotation)), chevron = true, onClick = { open(SettingsPage.Rotation) },
                )
            }
            row {
                ImmersiveGroupRow(
                    stringResource(R.string.settings_item_app_language_title),
                    value = if (language == com.music.spotui.data.AppLanguage.SYSTEM) auto else com.music.spotui.data.AppLanguage.nativeName(language),
                    chevron = true, onClick = { open(SettingsPage.Language) },
                )
            }
        }
    }
}

private fun rotationLabel(mode: String): Int = when (mode) {
    com.music.spotui.data.preferences.OrientationPref.AUTO -> R.string.settings_rotation_auto
    com.music.spotui.data.preferences.OrientationPref.LANDSCAPE -> R.string.settings_rotation_landscape
    else -> R.string.settings_rotation_portrait
}

/** The accent colour of one look. */
@Composable
private fun AccentRow(look: com.music.spotui.data.preferences.Look) {
    val ctx = LocalContext.current
    val accentPref = com.music.spotui.data.preferences.AccentPref
    androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(stringResource(R.string.supporter_accent), style = Immersive.Body)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 10.dp)) {
            for (a in com.music.spotui.data.preferences.AccentPref.Accent.entries) {
                val selected = accentPref.accentOf(look) == a
                val name = stringResource(when (a) {
                    com.music.spotui.data.preferences.AccentPref.Accent.GREEN -> R.string.color_green
                    com.music.spotui.data.preferences.AccentPref.Accent.BLUE -> R.string.color_blue
                    com.music.spotui.data.preferences.AccentPref.Accent.PURPLE -> R.string.color_purple
                    com.music.spotui.data.preferences.AccentPref.Accent.PINK -> R.string.color_pink
                    com.music.spotui.data.preferences.AccentPref.Accent.ORANGE -> R.string.color_orange
                })
                Box(
                    Modifier.size(38.dp)
                        .semantics { contentDescription = name; role = Role.RadioButton }
                        .clip(CircleShape).background(a.color)
                        .then(if (selected) Modifier.border(3.dp, Immersive.Text, CircleShape) else Modifier)
                        .clickable { accentPref.setAccent(ctx, look, a) },
                )
            }
        }
    }
}

@Composable
private fun LookPage(back: () -> Unit) {
    val ctx = LocalContext.current
    val current = com.music.spotui.data.preferences.LookPref.look
    ImmersivePage(title = stringResource(R.string.settings_look_title), onBack = back) {
        immersiveGroup("choices") {
            for (l in com.music.spotui.data.preferences.Look.entries) {
                val classic = l == com.music.spotui.data.preferences.Look.CLASSIC
                row {
                    ImmersiveChoiceRow(
                        stringResource(if (classic) R.string.look_classic else R.string.look_immersive),
                        selected = current == l,
                        detail = stringResource(if (classic) R.string.look_classic_detail else R.string.look_immersive_detail),
                        onClick = { com.music.spotui.data.preferences.LookPref.set(ctx, l) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RotationPage(back: () -> Unit) {
    val ctx = LocalContext.current
    val pref = com.music.spotui.data.preferences.OrientationPref
    ImmersivePage(title = stringResource(R.string.settings_rotation_title), onBack = back) {
        immersiveGroup("choices") {
            for ((value, label) in listOf(
                pref.PORTRAIT to R.string.settings_rotation_portrait,
                pref.AUTO to R.string.settings_rotation_auto,
                pref.LANDSCAPE to R.string.settings_rotation_landscape,
            )) {
                row { ImmersiveChoiceRow(stringResource(label), selected = pref.mode == value, onClick = { pref.set(ctx, value) }) }
            }
        }
    }
}

/** "App language": Automatic (French or English) or any language with Spotify's labels. */
@Composable
private fun LanguagePage(back: () -> Unit) {
    val ctx = LocalContext.current
    val current = remember { com.music.spotui.data.AppLanguage.current(ctx) }
    val auto = stringResource(R.string.language_auto)
    fun label(tag: String) = if (tag == com.music.spotui.data.AppLanguage.SYSTEM) auto else com.music.spotui.data.AppLanguage.nativeName(tag)
    ImmersivePage(title = stringResource(R.string.settings_item_app_language_title), onBack = back) {
        immersiveGroup("choices", footerRes = R.string.language_partial_note) {
            com.music.spotui.data.AppLanguage.AVAILABLE.forEach { tag ->
                row {
                    ImmersiveChoiceRow(
                        label(tag), selected = tag == current,
                        detail = if (tag == com.music.spotui.data.AppLanguage.SYSTEM) stringResource(R.string.language_auto_detail) else null,
                        onClick = {
                            if (tag != current) {
                                com.music.spotui.data.AppLanguage.apply(ctx, tag)
                                // Screens take the new language when the activity is rebuilt.
                                generateSequence(ctx) { (it as? android.content.ContextWrapper)?.baseContext }
                                    .filterIsInstance<android.app.Activity>().firstOrNull()?.recreate()
                            }
                        },
                    )
                }
            }
        }
    }
}

/** "Translation language" of the lyrics: the app's languages (as "App language"); Automatic is the phone's. */
@Composable
private fun LyricsLanguagePage(back: () -> Unit) {
    val ctx = LocalContext.current
    var current by remember { mutableStateOf(com.music.spotui.data.preferences.getLyricTranslateLang(ctx)) }
    val auto = stringResource(R.string.language_auto)
    fun label(tag: String) = if (tag == com.music.spotui.data.AppLanguage.SYSTEM) auto else com.music.spotui.data.AppLanguage.nativeName(tag)
    ImmersivePage(title = stringResource(R.string.lyrics_translation_title), onBack = back) {
        immersiveGroup("choices", footerRes = R.string.lyrics_translation_detail) {
            com.music.spotui.data.AppLanguage.AVAILABLE.forEach { tag ->
                row {
                    ImmersiveChoiceRow(label(tag), selected = tag == current,
                        onClick = { current = tag; com.music.spotui.data.preferences.setLyricTranslateLang(ctx, tag) })
                }
            }
        }
    }
}

// ---------------------------------------------------------------- App

@Composable
private fun AppPage(back: () -> Unit) {
    val ctx = LocalContext.current
    var about by remember { mutableStateOf(false) }
    if (about) com.music.spotui.ui.components.AboutSheet(onDismiss = { about = false })
    ImmersivePage(title = stringResource(R.string.settings_section_app), onBack = back) {
        immersiveGroup("about") {
            row {
                ImmersiveGroupRow(
                    stringResource(R.string.creator_biography_section_title),
                    detail = stringResource(R.string.about_detail),
                    value = app.myspoti.engine.BuildConfig.VERSION_NAME,
                    chevron = true,
                    onClick = { about = true },
                )
            }
        }
        immersiveGroup("repair") {
            // The hidden Web Player stuck or crashed: rebuild it without leaving the app.
            row {
                ImmersiveGroupRow(
                    stringResource(R.string.restart_player_title), detail = stringResource(R.string.restart_player_detail),
                    onClick = {
                        app.myspoti.host.android.MyspotiRuntime.host.reload()
                        android.widget.Toast.makeText(ctx, ctx.getString(R.string.restart_player_done), android.widget.Toast.LENGTH_SHORT).show()
                    },
                )
            }
            row {
                ImmersiveGroupRow(
                    stringResource(R.string.diagnostic_export_title), detail = stringResource(R.string.diagnostic_export_detail),
                    onClick = {
                        com.music.spotui.parental.ParentSecurityManager.runProtected(
                            ctx,
                            com.music.spotui.parental.AppSurface.DIAGNOSTIC_EXPORT,
                        ) {
                            val file = runCatching { app.myspoti.host.android.Logbook.export(ctx) }.getOrNull()
                            if (file != null) shareDiagnosticReport(ctx, file)
                            else android.widget.Toast.makeText(ctx, ctx.getString(R.string.diagnostic_export_failed), android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Accounts

/** The Spotify accounts kept on the phone ([com.music.spotui.ui.components.AccountsList]), and signing this one out. */
@Composable
private fun AccountsPage(back: () -> Unit) {
    val ctx = LocalContext.current
    val secTick by com.music.spotui.parental.ParentSecurityManager.sessionTick.collectAsStateWithLifecycle()
    if (!com.music.spotui.parental.ParentSecurityManager.canAccessWithoutPrompt(ctx, com.music.spotui.parental.AppSurface.ACCOUNTS_AND_LOGOUT) && secTick >= 0L) {
        androidx.compose.runtime.LaunchedEffect(secTick) { back() }
        return
    }
    ImmersivePage(title = stringResource(R.string.accounts_title), onBack = back) {
        immersiveGroup("accounts") {
            row { com.music.spotui.ui.components.AccountsList() }
        }
        immersiveGroup("sign-out") {
            row {
                ImmersiveGroupRow(
                    stringResource(R.string.connect_device_menu_logout), destructive = true,
                    onClick = {
                        com.music.spotui.parental.ParentSecurityManager.runProtected(
                            ctx,
                            com.music.spotui.parental.AppSurface.ACCOUNTS_AND_LOGOUT,
                        ) { com.music.spotui.di.SessionActions.logout(com.music.spotui.di.Graph.app) }
                    },
                )
            }
        }
    }
}


/** A whole number of seconds from 0 to 90 with − and +; 0 reads as [zero] ("Automatic", "No limit"). */
@Composable
private fun DurationStepper(title: Int, value: Int, zero: Int, onChange: (Int) -> Unit) {
    val steps = listOf(0, 2, 4, 6, 8, 10, 12, 15, 20, 25, 30, 40, 50, 60, 75, 90)
    val at = steps.indexOfFirst { it >= value }.let { if (it < 0) steps.lastIndex else it }
    ImmersiveGroupRow(
        stringResource(title),
        value = if (value == 0) stringResource(zero) else stringResource(R.string.settings_automix_seconds, value),
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(enabled = at > 0, onClick = { onChange(steps[at - 1]) }) { Text("−", style = Immersive.Headline) }
                TextButton(enabled = at < steps.lastIndex, onClick = { onChange(steps[(if (steps[at] == value) at + 1 else at).coerceAtMost(steps.lastIndex)]) }) { Text("+", style = Immersive.Headline) }
            }
        },
    )
}
