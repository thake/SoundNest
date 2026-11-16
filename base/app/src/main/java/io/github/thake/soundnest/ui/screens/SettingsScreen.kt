package io.github.thake.soundnest.ui.screens

import io.github.thake.soundnest.data.preferences.TransitionPref.Mode as TransitionMode
import io.github.thake.soundnest.ui.theme.tone
import io.github.thake.soundnest.ui.theme.AppText
import io.github.thake.soundnest.ui.theme.AppMuted

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import io.github.thake.soundnest.R
import kotlinx.coroutines.launch
import io.github.thake.soundnest.ui.components.StreamingQualityPicker
import io.github.thake.soundnest.ui.theme.AppBackground

/**
 * Settings, on one page: streaming quality, playback & transitions, library, lyrics,
 * storage, display, accounts, and the player's own repair tools.
 */
@Composable
fun SettingsScreen(
    navController: NavController,
    initialTab: Int = 0,
    initialSub: String? = null,
) {
    val energy by io.github.thake.soundnest.power.EnergySettings.state.collectAsStateWithLifecycle()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    // A page inside Settings (Transitions and AutoMix's options): its own screen, back returns to the tab.
    var sub by androidx.compose.runtime.saveable.rememberSaveable(initialSub) { mutableStateOf(initialSub) }
    androidx.activity.compose.BackHandler(enabled = sub != null) { sub = null }
    // One tab per group (see below); kept up here so a search hit can open one.
    var tab by androidx.compose.runtime.saveable.rememberSaveable(initialTab) { mutableStateOf(initialTab) }
    // Settings' search: null while closed.
    var query by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    androidx.activity.compose.BackHandler(enabled = query != null) { query = null }
    Column(Modifier.fillMaxSize().background(AppBackground).statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(4.dp)) {
            IconButton(onClick = { if (sub != null) sub = null else navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.accessibility_navigate_back), tint = AppText)
            }
            Text(stringResource(if (sub == SUB_TRANSITIONS) R.string.settings_transitions_title else R.string.settings_title),
                color = AppText, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (sub == null) IconButton(onClick = { query = if (query == null) "" else null }) {
                Icon(androidx.compose.material.icons.Icons.Default.Search, contentDescription = stringResource(R.string.settings_search), tint = AppText)
            }
        }
        // The search button: a field over the tabs, the rows of every tab it finds; a tap opens its tab.
        query?.let { q ->
            val focus = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
            androidx.compose.material3.OutlinedTextField(
                value = q, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text(stringResource(R.string.settings_search_hint), color = AppMuted) },
                leadingIcon = { Icon(androidx.compose.material.icons.Icons.Default.Search, null, tint = AppMuted) },
                shape = RoundedCornerShape(24.dp),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedTextColor = AppText, unfocusedTextColor = AppText, focusedBorderColor = io.github.thake.soundnest.ui.theme.AppAccent,
                    unfocusedBorderColor = tone(0xFF3E3E3E), cursorColor = io.github.thake.soundnest.ui.theme.AppAccent,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    .then(Modifier.focusRequester(focus)),
            )
            androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
            if (q.isNotBlank()) {
                val hits = SettingsSearch.search(SettingsSearch.classic, q) { ctx.getString(it) }
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
                    if (hits.isEmpty()) Text(stringResource(R.string.settings_search_empty), color = AppMuted, fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp))
                    hits.forEach { h ->
                        Column(
                            Modifier.fillMaxWidth().clickable {
                                query = null
                                if (h.place == SUB_TRANSITIONS) sub = SUB_TRANSITIONS
                                else {
                                    val target = h.place.toInt()
                                    if (target == TAB_ACCOUNT) {
                                        io.github.thake.soundnest.parental.ParentSecurityManager.runProtected(
                                            ctx,
                                            io.github.thake.soundnest.parental.AppSurface.ACCOUNTS_AND_LOGOUT,
                                        ) { tab = TAB_ACCOUNT }
                                    } else tab = target
                                }
                            }.padding(horizontal = 20.dp, vertical = 10.dp),
                        ) {
                            Text(stringResource(h.title), color = AppText, fontSize = 16.sp)
                            if (h.detail != 0) Text(stringResource(h.detail), color = AppMuted, fontSize = 12.sp)
                        }
                    }
                }
                return@Column
            }
        }
        if (sub == SUB_TRANSITIONS) {
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
                TransitionsRow()
                Spacer(Modifier.height(io.github.thake.soundnest.ui.components.bottomBarsSpace()))
            }
            return@Column
        }
        // Kid-accessible tabs first; Accounts tab is PIN-protected.
        val tabs = listOf(
            R.string.settings_section_playback,
            R.string.settings_section_lyrics,
            R.string.settings_section_storage,
            R.string.settings_tab_appearance,
            R.string.settings_tab_about,
            R.string.accounts_title,
            R.string.settings_section_library,
        )
        androidx.compose.material3.ScrollableTabRow(
            selectedTabIndex = TAB_ORDER.indexOf(tab).coerceAtLeast(0), containerColor = Color.Transparent, contentColor = AppText, edgePadding = 12.dp,
            indicator = { positions ->
                androidx.compose.material3.TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(positions[TAB_ORDER.indexOf(tab).coerceAtLeast(0)]), color = io.github.thake.soundnest.ui.theme.AppAccent)
            },
            divider = { HorizontalDivider(color = tone(0xFF2A2A2A)) },
        ) {
            TAB_ORDER.forEach { i ->
                androidx.compose.material3.Tab(
                    selected = tab == i,
                    onClick = {
                        if (i == TAB_ACCOUNT) {
                            io.github.thake.soundnest.parental.ParentSecurityManager.runProtected(
                                ctx,
                                io.github.thake.soundnest.parental.AppSurface.ACCOUNTS_AND_LOGOUT,
                            ) { tab = TAB_ACCOUNT }
                        } else {
                            tab = i
                        }
                    },
                    text = { Text(stringResource(tabs[i]), fontSize = 14.sp, fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal) },
                    selectedContentColor = AppText, unselectedContentColor = AppMuted,
                )
            }
        }
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            io.github.thake.soundnest.parental.ParentSecurityControlsSection(
                onOpenParentSettings = { navController.navigate(io.github.thake.soundnest.ui.navigation.Routes.ParentSettings.route) },
            )
            HorizontalDivider(color = tone(0xFF2A2A2A))
            if (tab == 0) {
            Section(R.string.settings_section_playback)
            StreamingQualityPicker(title = R.string.quality_streaming_wifi_title)
            StreamingQualityPicker(cellular = true, title = R.string.quality_streaming_cell_title)
            OptionRow(
                label = stringResource(R.string.settings_autoplay_title),
                detail = stringResource(R.string.settings_autoplay_detail),
                checked = io.github.thake.soundnest.data.preferences.AutoplayPref.on,
                onChecked = { io.github.thake.soundnest.data.preferences.AutoplayPref.set(ctx, it) },
            )
            OptionRow(
                label = stringResource(R.string.myspoti_shuffle_title),
                detail = stringResource(R.string.myspoti_shuffle_detail),
                checked = io.github.thake.soundnest.data.preferences.AppShufflePref.on,
                onChecked = { io.github.thake.soundnest.data.preferences.AppShufflePref.set(ctx, it) },
            )
            TransitionsLink(onOpen = { sub = SUB_TRANSITIONS })
            // The local player's equalizer, speed and silence skipping (never Spotify's own songs).
            Column(
                Modifier.fillMaxWidth().clickable { navController.navigate(io.github.thake.soundnest.ui.navigation.Routes.Equalizer.route) }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Text(stringResource(R.string.equalizer_title), color = AppText, fontSize = 16.sp)
                Text(stringResource(R.string.equalizer_settings_detail), color = AppMuted, fontSize = 12.sp)
            }
            // What keeps the music going and what the headset buttons do.
            Section(R.string.settings_section_controls)
            // The native engine plays the account's songs instead of the hidden page (on by default).
            var nativeAudio by remember { mutableStateOf(io.github.thake.soundnest.di.NativeAudio.enabled) }
            OptionRow(
                label = stringResource(R.string.settings_native_audio_title),
                detail = stringResource(R.string.settings_native_audio_detail),
                checked = nativeAudio,
                onChecked = { nativeAudio = it; io.github.thake.soundnest.di.NativeAudio.set(ctx, it) },
            )
            var keepPlaying by remember { mutableStateOf(io.github.thake.soundnest.host.android.PlaybackService.keepsPlayingOnClose(ctx)) }
            OptionRow(
                label = stringResource(R.string.settings_keep_playing_title),
                detail = stringResource(R.string.settings_keep_playing_detail),
                checked = keepPlaying,
                onChecked = { keepPlaying = it; io.github.thake.soundnest.host.android.PlaybackService.setKeepsPlayingOnClose(ctx, it) },
            )
            var headsetSkip by remember { mutableStateOf(io.github.thake.soundnest.host.android.PlaybackService.skipsOnEpisodes(ctx)) }
            OptionRow(
                label = stringResource(R.string.settings_headset_skip_title),
                detail = stringResource(R.string.settings_headset_skip_detail),
                checked = headsetSkip,
                onChecked = { headsetSkip = it; io.github.thake.soundnest.host.android.PlaybackService.setSkipsOnEpisodes(ctx, it) },
            )
            Section(R.string.settings_section_data)
            OptionRow(
                label = stringResource(R.string.energy_saver_title),
                detail = stringResource(R.string.energy_saver_summary),
                checked = energy.manualSaver,
                onChecked = io.github.thake.soundnest.power.EnergySettings::setSaver,
            )
            OptionRow(
                label = stringResource(R.string.data_saver_title),
                detail = stringResource(R.string.data_saver_summary),
                checked = io.github.thake.soundnest.data.DataSaver.enabled,
                onChecked = io.github.thake.soundnest.data.DataSaver::set,
            )
            // Battery optimization off = music and Connect keep going with the screen off.
            var exempt by remember { androidx.compose.runtime.mutableStateOf(io.github.thake.soundnest.power.BatteryExemption.isExempt(ctx)) }
            androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                exempt = io.github.thake.soundnest.power.BatteryExemption.isExempt(ctx)
            }
            OptionRow(
                label = stringResource(R.string.background_playback_title),
                detail = stringResource(if (exempt) R.string.background_playback_on else R.string.background_playback_off),
                checked = exempt,
                onChecked = {
                    if (exempt) ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    else io.github.thake.soundnest.power.BatteryExemption.request(ctx)
                },
            )
            // Samsung's "Sleeping apps" is a second list: the app has to be in Never sleeping apps too.
            if (io.github.thake.soundnest.power.BatteryExemption.isSamsung()) Column(
                Modifier.fillMaxWidth().clickable { io.github.thake.soundnest.power.BatteryExemption.openAppSettings(ctx) }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Text(stringResource(R.string.background_playback_open), color = AppText, fontSize = 16.sp)
                Text(stringResource(R.string.background_playback_samsung), color = AppMuted, fontSize = 12.sp)
            }
            }
            if (tab == TAB_LIBRARY) {
            // What the library shows and what it tells about.
            Section(R.string.settings_section_library)
            OptionRow(
                label = stringResource(R.string.settings_release_notify),
                detail = stringResource(R.string.settings_release_notify_detail),
                checked = io.github.thake.soundnest.data.NewReleaseNotifier.on,
                onChecked = { io.github.thake.soundnest.data.NewReleaseNotifier.setOn(ctx, it) },
            )
            OptionRow(
                label = stringResource(R.string.settings_local_files_shown),
                detail = stringResource(R.string.settings_local_files_shown_detail),
                checked = io.github.thake.soundnest.data.preferences.LocalFilesPref.shown,
                onChecked = { io.github.thake.soundnest.data.preferences.LocalFilesPref.set(ctx, it) },
            )
            // Live Radio: adds the pinned Library row under Local Files (see io.github.thake.soundnest.radio).
            OptionRow(
                label = stringResource(R.string.live_radio_settings_title),
                detail = stringResource(R.string.live_radio_settings_detail),
                checked = io.github.thake.soundnest.radio.RadioFeaturePref.on,
                onChecked = { io.github.thake.soundnest.radio.RadioFeaturePref.set(ctx, it) },
            )
            // Songs marked "Not interested": Parent PIN required so kids cannot unhide songs.
            Column(
                Modifier.fillMaxWidth().clickable {
                    io.github.thake.soundnest.parental.ParentSecurityManager.runProtected(
                        ctx,
                        io.github.thake.soundnest.parental.AppSurface.HIDDEN_SONGS_AND_BANS,
                    ) { navController.navigate(io.github.thake.soundnest.ui.navigation.Routes.HiddenSongs.route) }
                }.padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Text(stringResource(R.string.hidden_songs_title), color = AppText, fontSize = 16.sp)
                Text(stringResource(R.string.hidden_songs_detail), color = AppMuted, fontSize = 12.sp)
            }
            }
            if (tab == TAB_LYRICS) {
            // Lyrics: word by word (karaoke) first, or line by line with Spotify's first.
            Section(R.string.settings_section_lyrics)
            var wordSync by remember { mutableStateOf(io.github.thake.soundnest.data.preferences.isLyricsWordSync(ctx)) }
            OptionRow(
                label = stringResource(R.string.lyrics_word_sync_title),
                detail = stringResource(if (wordSync) R.string.lyrics_word_sync_on else R.string.lyrics_word_sync_off),
                checked = wordSync,
                onChecked = {
                    wordSync = it
                    io.github.thake.soundnest.data.preferences.setLyricsWordSync(ctx, it)
                    io.github.thake.soundnest.data.api.LyricsApi.wordSync = it
                    io.github.thake.soundnest.data.api.LyricsApi.forgetAll()
                },
            )
            // NetEase and Genius after Spotify and LRCLIB (also in the lyrics' own menu).
            var lyricsExtra by remember { mutableStateOf(io.github.thake.soundnest.data.preferences.isLyricsExtraSources(ctx)) }
            OptionRow(
                label = stringResource(R.string.lyrics_extra_title),
                detail = stringResource(R.string.lyrics_extra_summary),
                checked = lyricsExtra,
                onChecked = {
                    lyricsExtra = it
                    io.github.thake.soundnest.data.preferences.setLyricsExtraSources(ctx, it)
                    io.github.thake.soundnest.data.api.LyricsApi.extraSources = it
                    io.github.thake.soundnest.data.api.LyricsApi.extraSourcesOff = !it
                    io.github.thake.soundnest.data.api.LyricsApi.forgetAll()
                },
            )
            // Genius: the meaning of songs and the notes on their lines, and its lyrics as a last resort.
            OptionRow(
                label = stringResource(R.string.settings_genius_title),
                detail = stringResource(R.string.settings_genius_detail),
                checked = io.github.thake.soundnest.data.preferences.GeniusPref.on,
                onChecked = { io.github.thake.soundnest.data.preferences.GeniusPref.set(ctx, it) },
            )
            OptionRow(
                label = stringResource(R.string.settings_lyrics_keep_awake_title),
                detail = stringResource(R.string.settings_lyrics_keep_awake_detail),
                checked = io.github.thake.soundnest.data.preferences.LyricsKeepAwakePref.on,
                onChecked = { io.github.thake.soundnest.data.preferences.LyricsKeepAwakePref.set(ctx, it) },
            )
            // The song in Latin letters under its lines, when the lyrics carry one (as in the lyrics' menu).
            OptionRow(
                label = stringResource(R.string.lyrics_romanization),
                detail = stringResource(R.string.settings_lyrics_romanization_detail),
                checked = io.github.thake.soundnest.data.preferences.LyricsRomanizationPref.on,
                onChecked = { io.github.thake.soundnest.data.preferences.LyricsRomanizationPref.set(ctx, it) },
            )
            LyricsTranslationRow()
            }
            if (tab == TAB_STORAGE) {
            Section(R.string.settings_section_storage)
            StorageRows()
            }
            if (tab == TAB_LOOK) {
            Section(R.string.settings_section_display)
            OptionRow(
                label = stringResource(R.string.settings_amoled_theme),
                detail = stringResource(R.string.settings_amoled_theme_subtitle),
                checked = energy.blackPreference,
                onChecked = io.github.thake.soundnest.power.EnergySettings::setBlackBackground,
            )
            OptionRow(
                label = stringResource(R.string.settings_player_lyrics_card_title),
                detail = stringResource(R.string.settings_player_lyrics_card_detail),
                checked = io.github.thake.soundnest.data.preferences.PlayerLyricsCardPref.on,
                onChecked = { io.github.thake.soundnest.data.preferences.PlayerLyricsCardPref.set(ctx, it) },
            )
            OptionRow(
                label = stringResource(R.string.settings_player_source_title),
                detail = stringResource(R.string.settings_player_source_detail),
                checked = io.github.thake.soundnest.data.preferences.PlayerSourceLinePref.on,
                onChecked = { io.github.thake.soundnest.data.preferences.PlayerSourceLinePref.set(ctx, it) },
            )
            OptionRow(
                label = stringResource(R.string.settings_solid_nav_title),
                detail = stringResource(R.string.settings_solid_nav_detail),
                checked = io.github.thake.soundnest.data.preferences.SolidNavBar.on,
                onChecked = { io.github.thake.soundnest.data.preferences.SolidNavBar.set(ctx, it) },
            )
            // Material You: the wallpaper's accent (Android 12+), in place of the one picked below.
            if (io.github.thake.soundnest.data.preferences.DynamicColorPref.available) OptionRow(
                label = stringResource(R.string.settings_dynamic_color_title),
                detail = stringResource(R.string.settings_dynamic_color_detail),
                checked = io.github.thake.soundnest.data.preferences.DynamicColorPref.on,
                onChecked = { io.github.thake.soundnest.data.preferences.DynamicColorPref.set(ctx, it) },
            )
            if (!io.github.thake.soundnest.data.preferences.DynamicColorPref.active) AccentRow(io.github.thake.soundnest.data.preferences.Look.CLASSIC)
            OptionRow(
                label = stringResource(R.string.settings_system_font_title),
                detail = stringResource(R.string.settings_system_font_detail),
                checked = io.github.thake.soundnest.data.preferences.AppFontPref.system,
                onChecked = { io.github.thake.soundnest.data.preferences.AppFontPref.set(ctx, it) },
            )
            LookRow()
            OrientationRow()
            LanguageRow()
            }
            if (tab == TAB_ABOUT) {
            Section(R.string.settings_section_app)
            AboutRow()
            // The hidden Web Player stuck or crashed: rebuild it without leaving the app.
            Column(
                Modifier.fillMaxWidth().clickable {
                    io.github.thake.soundnest.host.android.SoundNestRuntime.host.reload()
                    // Reported: nothing said the restart happened.
                    android.widget.Toast.makeText(ctx, ctx.getString(R.string.restart_player_done), android.widget.Toast.LENGTH_SHORT).show()
                }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Text(stringResource(R.string.restart_player_title), color = AppText, fontSize = 16.sp)
                Text(stringResource(R.string.restart_player_detail), color = AppMuted, fontSize = 12.sp)
            }
            // The report a tester hands us. The engine's log is otherwise reachable only through
            // adb logcat, which the person reporting the bug cannot run.
            Column(
                Modifier.fillMaxWidth().clickable {
                    io.github.thake.soundnest.parental.ParentSecurityManager.runProtected(
                        ctx,
                        io.github.thake.soundnest.parental.AppSurface.DIAGNOSTIC_EXPORT,
                    ) {
                        val file = runCatching { io.github.thake.soundnest.host.android.Logbook.export(ctx) }.getOrNull()
                        if (file != null) shareDiagnosticReport(ctx, file)
                        else android.widget.Toast.makeText(ctx, ctx.getString(R.string.diagnostic_export_failed), android.widget.Toast.LENGTH_SHORT).show()
                    }
                }.padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Text(stringResource(R.string.diagnostic_export_title), color = AppText, fontSize = 16.sp)
                Text(stringResource(R.string.diagnostic_export_detail), color = AppMuted, fontSize = 12.sp)
            }
            }
            if (tab == TAB_ACCOUNT) {
            val secTick by io.github.thake.soundnest.parental.ParentSecurityManager.sessionTick.collectAsStateWithLifecycle()
            if (!io.github.thake.soundnest.parental.ParentSecurityManager.canAccessWithoutPrompt(ctx, io.github.thake.soundnest.parental.AppSurface.ACCOUNTS_AND_LOGOUT) && secTick >= 0L) {
                androidx.compose.runtime.LaunchedEffect(secTick) { tab = 0 }
            } else {
                Section(R.string.accounts_title)
                io.github.thake.soundnest.ui.components.AccountsList()
                Text(
                    stringResource(R.string.connect_device_menu_logout), color = AppText, fontSize = 16.sp,
                    modifier = Modifier.fillMaxWidth().clickable {
                        io.github.thake.soundnest.parental.ParentSecurityManager.runProtected(
                            ctx,
                            io.github.thake.soundnest.parental.AppSurface.ACCOUNTS_AND_LOGOUT,
                        ) { io.github.thake.soundnest.di.SessionActions.logout(io.github.thake.soundnest.di.Graph.app) }
                    }.padding(horizontal = 20.dp, vertical = 14.dp),
                )
            }
            }
            // Clear of the mini player and the navigation bar, as every other page (96 dp left the
            // last rows, "Restart the player" among them, under the mini player).
            Spacer(Modifier.height(io.github.thake.soundnest.ui.components.bottomBarsSpace()))
        }
    }
}

@Composable
internal fun Section(title: Int) {
    Text(stringResource(title).uppercase(), color = io.github.thake.soundnest.ui.theme.AppAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp))
}

/** Classic or Immersive: the choice made in the onboarding, changeable here. */
@Composable
private fun LookRow() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val current = io.github.thake.soundnest.data.preferences.LookPref.look
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Text(stringResource(R.string.settings_look_title), color = AppText, fontSize = 16.sp)
        for (l in io.github.thake.soundnest.data.preferences.Look.entries) {
            val classic = l == io.github.thake.soundnest.data.preferences.Look.CLASSIC
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
                    .semantics { role = Role.RadioButton }
                    .clickable { io.github.thake.soundnest.data.preferences.LookPref.set(ctx, l) }
                    .padding(vertical = 8.dp),
            ) {
                androidx.compose.material3.RadioButton(selected = current == l, onClick = null)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(stringResource(if (classic) R.string.look_classic else R.string.look_immersive), color = AppText, fontSize = 15.sp)
                    Text(stringResource(if (classic) R.string.look_classic_detail else R.string.look_immersive_detail), color = AppMuted, fontSize = 12.sp)
                }
            }
        }
    }
}

/** The accent colour of one look. */
@Composable
private fun AccentRow(look: io.github.thake.soundnest.data.preferences.Look) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val pref = io.github.thake.soundnest.data.preferences.AccentPref
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Text(stringResource(R.string.supporter_accent), color = AppText, fontSize = 16.sp)
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 10.dp)) {
            for (a in io.github.thake.soundnest.data.preferences.AccentPref.Accent.entries) {
                val selected = pref.accentOf(look) == a
                val name = stringResource(when (a) {
                    io.github.thake.soundnest.data.preferences.AccentPref.Accent.GREEN -> R.string.color_green
                    io.github.thake.soundnest.data.preferences.AccentPref.Accent.BLUE -> R.string.color_blue
                    io.github.thake.soundnest.data.preferences.AccentPref.Accent.PURPLE -> R.string.color_purple
                    io.github.thake.soundnest.data.preferences.AccentPref.Accent.PINK -> R.string.color_pink
                    io.github.thake.soundnest.data.preferences.AccentPref.Accent.ORANGE -> R.string.color_orange
                })
                Box(
                    Modifier.size(40.dp)
                        .semantics { contentDescription = name; role = Role.RadioButton }
                        .clip(CircleShape).background(a.color)
                        .then(if (selected) Modifier.border(3.dp, AppText, CircleShape) else Modifier)
                        .clickable { pref.setAccent(ctx, look, a) },
                )
            }
        }
    }
}

/**
 * "Crossfade": the length of the fade at the end of a song. The label says what it is: the two
 * songs never overlap (the page plays one media element), so this is a fade, not a true
 * crossfade. The default is 0: off, as before.
 */
private const val SUB_TRANSITIONS = "transitions"
private const val TAB_LYRICS = 1
private const val TAB_STORAGE = 2
private const val TAB_LOOK = 3
private const val TAB_ABOUT = 4
private const val TAB_ACCOUNT = 5
private const val TAB_LIBRARY = 6
/** Kid-accessible tabs first (playback, appearance, library, lyrics, storage, about), PIN-locked accounts last. */
private val TAB_ORDER = listOf(0, TAB_LOOK, TAB_LIBRARY, TAB_LYRICS, TAB_STORAGE, TAB_ABOUT, TAB_ACCOUNT)

/** Transitions in the Playback tab: what is on, one line; its choices and AutoMix's options on their own page. */
@Composable
private fun TransitionsLink(onOpen: () -> Unit) {
    val seconds = io.github.thake.soundnest.data.preferences.CrossfadePref.seconds
    Row(verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 10.dp)) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_transitions_title), color = AppText, fontSize = 16.sp)
            Text(stringResource(R.string.settings_transitions_detail), color = AppMuted, fontSize = 12.sp)
        }
        Text(
            when (io.github.thake.soundnest.data.preferences.TransitionPref.mode) {
                TransitionMode.AUTOMIX -> stringResource(R.string.settings_automix_title)
                TransitionMode.CROSSFADE -> stringResource(R.string.settings_transition_crossfade_value, seconds)
                TransitionMode.OFF -> stringResource(R.string.settings_crossfade_off)
            },
            color = io.github.thake.soundnest.ui.theme.AppAccent, fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp),
        )
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = AppMuted)
    }
}

/**
 * Transitions: off, a crossfade (its length under it) or AutoMix (its options under it), with
 * what the choice does in one line.
 */
@Composable
private fun TransitionsRow() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val transition = io.github.thake.soundnest.data.preferences.TransitionPref
    val automix = io.github.thake.soundnest.automix.AutoMix
    val mode = transition.mode
    var intro by remember { mutableStateOf(false) }
    @Composable
    fun Chip(label: String, selected: Boolean, onClick: () -> Unit) = Text(
        label,
        color = if (selected) Color.Black else AppText, fontSize = 14.sp,
        modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
            .background(if (selected) io.github.thake.soundnest.ui.theme.AppAccent else tone(0xFF2A2A2A))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
    androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Text(stringResource(R.string.settings_transitions_title), color = AppText, fontSize = 16.sp)
        Text(
            stringResource(R.string.settings_transitions_detail),
            color = AppMuted, fontSize = 12.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
        Row(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 10.dp),
        ) {
            Chip(stringResource(R.string.settings_crossfade_off), mode == TransitionMode.OFF) { transition.set(ctx, TransitionMode.OFF) }
            Chip(stringResource(R.string.settings_crossfade_title), mode == TransitionMode.CROSSFADE) { transition.set(ctx, TransitionMode.CROSSFADE) }
            Chip(stringResource(R.string.settings_automix_title), mode == TransitionMode.AUTOMIX) {
                transition.set(ctx, TransitionMode.AUTOMIX)
                if (!io.github.thake.soundnest.ui.components.AutoMixIntro.seen) intro = true
            }
        }
        Text(
            stringResource(when (mode) {
                TransitionMode.OFF -> R.string.settings_transition_off_detail
                TransitionMode.CROSSFADE -> R.string.settings_transition_crossfade_detail
                TransitionMode.AUTOMIX -> R.string.settings_transition_automix_detail
            }),
            color = AppMuted, fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
        // The long explanation of AutoMix, as the Immersive page gives it under the choice.
        if (mode == TransitionMode.AUTOMIX) Text(
            stringResource(R.string.settings_automix_detail),
            color = AppMuted, fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
        // Why the song playing will not mix into the next one, while that is so.
        if (mode == TransitionMode.AUTOMIX) io.github.thake.soundnest.ui.components.autoMixReasonText(automix.whyNot())?.let {
            Text(stringResource(it), color = io.github.thake.soundnest.ui.theme.AppAccent, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }
        // Any length from 1 to 12 s on a slider (it was four fixed choices).
        if (mode == TransitionMode.CROSSFADE) io.github.thake.soundnest.ui.components.CrossfadeSlider(
            io.github.thake.soundnest.ui.theme.AppAccent, AppText, AppMuted, Modifier.padding(top = 12.dp))
    }
    if (mode == TransitionMode.AUTOMIX) {
        // The data-plan transitions are the blend's own engine; the session DJ steers only the older planner.
        if (automix.style == 0) OptionRow(stringResource(R.string.settings_automix_segue), stringResource(R.string.settings_automix_segue_detail),
            checked = automix.segue, onChecked = automix::changeSegue)
        if (!automix.segueActive) OptionRow(stringResource(R.string.settings_automix_session), stringResource(R.string.settings_automix_session_detail),
            checked = automix.sessionDj, onChecked = automix::changeSessionDj)
        // The planner: Orpheon by default, the earlier one as a way back.
        OptionRow(stringResource(R.string.settings_automix_orpheon), stringResource(R.string.settings_automix_orpheon_detail),
            checked = automix.orpheon, onChecked = automix::changeOrpheon)
        // Each Orpheon idea on its own switch, only in the Mix Lab build, so their text is not translated.
        if (io.github.thake.soundnest.BuildConfig.APPLICATION_ID == "io.github.thake.soundnest.automixlab") {
            for (idea in io.github.thake.soundnest.automix.segue.MixIdea.values()) OptionRow(idea.title, idea.detail,
                checked = automix.hasIdea(idea), enabled = automix.orpheon, onChecked = { automix.changeIdea(idea, it) })
        }
        OptionRow(stringResource(R.string.settings_automix_album_order), stringResource(R.string.settings_automix_album_order_detail),
            checked = automix.albumOrder, onChecked = automix::changeAlbumOrder)
        OptionRow(stringResource(R.string.settings_automix_podcasts), stringResource(R.string.settings_automix_podcasts_detail),
            checked = automix.mixPodcasts, onChecked = automix::changeMixPodcasts)
        OptionRow(stringResource(R.string.settings_automix_harmonic), stringResource(R.string.settings_automix_harmonic_detail),
            checked = automix.harmonic, onChecked = automix::changeHarmonic)
        OptionRow(stringResource(R.string.settings_automix_eq), stringResource(R.string.settings_automix_eq_detail),
            checked = automix.djEq, onChecked = automix::changeDjEq)
        OptionRow(stringResource(R.string.settings_automix_show), stringResource(R.string.settings_automix_show_detail),
            checked = automix.showInPlayer, onChecked = automix::changeShowInPlayer)
        OptionRow(stringResource(R.string.settings_automix_mobile_data), stringResource(R.string.settings_automix_mobile_data_detail),
            checked = automix.analyzeOnMobileData, onChecked = automix::changeAnalyzeOnMobileData)
        // Chips wrap: the style's labels do not fit one line in every language.
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        @Composable
        fun Choices(title: Int, detail: Int?, labels: List<Int>, selected: Int, pick: (Int) -> Unit) =
            androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                Text(stringResource(title), color = AppText, fontSize = 16.sp)
                detail?.let { Text(stringResource(it), color = AppMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp)) }
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                    labels.forEachIndexed { i, l -> Chip(stringResource(l), selected == i) { pick(i) } }
                }
            }
        Choices(R.string.settings_automix_on_next, R.string.settings_automix_on_next_detail,
            listOf(R.string.settings_automix_next_cut, R.string.settings_automix_next_quick, R.string.settings_automix_next_full),
            automix.nextStyle, automix::changeNextStyle)
        Choices(R.string.settings_automix_style, R.string.settings_automix_style_detail,
            listOf(R.string.settings_automix_style_blend, R.string.settings_automix_style_cut, R.string.settings_automix_style_fade),
            automix.style, automix::changeStyle)
        Choices(R.string.settings_automix_length, R.string.settings_automix_length_detail,
            listOf(R.string.settings_automix_length_short, R.string.settings_automix_length_auto, R.string.settings_automix_length_long),
            automix.mixLength, automix::changeMixLength)
        AutoMixDurationBoundsRow()
        OptionRow(stringResource(R.string.settings_automix_performance), stringResource(R.string.settings_automix_performance_detail),
            checked = automix.performancePriority, onChecked = automix::changePerformancePriority)
        Choices(R.string.settings_automix_tempo, R.string.settings_automix_tempo_detail,
            listOf(R.string.settings_automix_tempo_strict, R.string.settings_automix_tempo_normal, R.string.settings_automix_tempo_wide),
            automix.tempoRange, automix::changeTempoRange)
        if (automix.showInPlayer) Choices(R.string.settings_automix_pulse, null,
            listOf(R.string.settings_crossfade_off, R.string.settings_automix_pulse_subtle, R.string.settings_automix_pulse_strong),
            automix.pulseLevel, automix::changePulseLevel)
        AutoMixDiagnosticsPanel()
        AutoMixCacheRow()
    }
    if (intro) androidx.compose.material3.AlertDialog(
        onDismissRequest = { intro = false; io.github.thake.soundnest.ui.components.AutoMixIntro.markSeen() },
        containerColor = tone(0xFF282828),
        title = { Text(stringResource(R.string.settings_automix_intro_title), color = AppText) },
        text = { Text(stringResource(R.string.settings_automix_intro_text), color = AppMuted) },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { intro = false; io.github.thake.soundnest.ui.components.AutoMixIntro.markSeen() }) {
                Text(stringResource(R.string.settings_automix_intro_ok), color = io.github.thake.soundnest.ui.theme.AppAccent)
            }
        },
    )
}

@Composable
private fun AutoMixDurationBoundsRow() {
    val automix = io.github.thake.soundnest.automix.AutoMix
    var minText by androidx.compose.runtime.remember(automix.minDurationSeconds) { androidx.compose.runtime.mutableStateOf(automix.minDurationSeconds.toString()) }
    var maxText by androidx.compose.runtime.remember(automix.maxDurationSeconds) { androidx.compose.runtime.mutableStateOf(automix.maxDurationSeconds.toString()) }
    val minValue = minText.toIntOrNull()?.takeIf { it in 0..90 }
    val maxValue = maxText.toIntOrNull()?.takeIf { it in 0..90 }
    val valid = minValue != null && maxValue != null && (maxValue == 0 || minValue <= maxValue)
    androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Text(stringResource(R.string.settings_automix_duration), color = AppText, fontSize = 16.sp)
        Text(stringResource(R.string.settings_automix_duration_detail), color = AppMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
            androidx.compose.material3.OutlinedTextField(minText, { minText = it.filter(Char::isDigit).take(2) }, label = { Text(stringResource(R.string.settings_automix_duration_min)) },
                singleLine = true, isError = minValue == null || (maxValue != null && maxValue > 0 && minValue != null && minValue > maxValue),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number), modifier = Modifier.weight(1f))
            androidx.compose.material3.OutlinedTextField(maxText, { maxText = it.filter(Char::isDigit).take(2) }, label = { Text(stringResource(R.string.settings_automix_duration_max)) },
                singleLine = true, isError = maxValue == null || (minValue != null && maxValue != 0 && minValue > maxValue),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number), modifier = Modifier.weight(1f))
        }
        if (!valid) Text(stringResource(R.string.settings_automix_duration_error), color = io.github.thake.soundnest.ui.theme.AppAccent, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        else if (minValue != automix.minDurationSeconds || maxValue != automix.maxDurationSeconds) {
            androidx.compose.material3.TextButton(onClick = { automix.changeDurationBounds(minValue!!, maxValue!!) }, modifier = Modifier.align(androidx.compose.ui.Alignment.End)) {
                Text(stringResource(R.string.settings_automix_duration_apply))
            }
        }
    }
}

/** Event-driven status and recent AutoMix events; copied text is redacted before leaving the app. */
@Composable
private fun AutoMixDiagnosticsPanel() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val expandedState = remember { mutableStateOf(false) }
    val expanded = expandedState.value
    val events = io.github.thake.soundnest.automix.AutoMix.journalSnapshot
    val cue by io.github.thake.soundnest.di.LocalPlayer.mixCue
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_automix_diagnostics), color = AppText, fontSize = 16.sp)
                Text(
                    stringResource(if (cue == null) R.string.settings_automix_diagnostics_no_plan else
                        if (cue?.mixing == true) R.string.settings_automix_diagnostics_running else R.string.settings_automix_diagnostics_planned),
                    color = AppMuted, fontSize = 12.sp,
                )
            }
            androidx.compose.material3.TextButton(onClick = {
                val reason = io.github.thake.soundnest.ui.components.autoMixReasonText(io.github.thake.soundnest.automix.AutoMix.whyNot())
                    ?.let(context.resources::getString).orEmpty()
                val cueLine = cue?.let {
                    "${if (it.mixing) "running" else "planned"} ${it.style}, A ${it.exitMs}-${it.endMs} ms, B ${it.entryMs}-${it.resumeMs} ms"
                } ?: "no transition planned"
                val safeEvents = io.github.thake.soundnest.automix.AutoMixDiagnostics.safeJournal(events.takeLast(20))
                val report = buildString {
                    appendLine("AutoMix diagnostics")
                    appendLine("Enabled: ${io.github.thake.soundnest.automix.AutoMix.on}")
                    appendLine("Transition: $cueLine")
                    if (reason.isNotBlank()) appendLine("Reason: $reason")
                    appendLine("Recent events:")
                    safeEvents.forEach { appendLine(it) }
                }
                clipboard.setText(androidx.compose.ui.text.AnnotatedString(report))
            }) { Text(stringResource(R.string.settings_automix_diagnostics_copy), color = io.github.thake.soundnest.ui.theme.AppAccent) }
            androidx.compose.material3.TextButton(onClick = { expandedState.value = !expanded }) {
                Text(stringResource(if (expanded) R.string.settings_automix_diagnostics_hide else R.string.settings_automix_diagnostics_show), color = io.github.thake.soundnest.ui.theme.AppAccent)
            }
        }
        if (expanded) {
            cue?.let {
                Text(
                    stringResource(R.string.settings_automix_diagnostics_cue,
                        stringResource(if (it.mixing) R.string.settings_automix_diagnostics_running_short else R.string.settings_automix_diagnostics_planned_short),
                        it.style.name, it.exitMs, it.endMs, it.entryMs, it.resumeMs),
                    color = AppMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp),
                )
            }
            io.github.thake.soundnest.ui.components.autoMixReasonText(io.github.thake.soundnest.automix.AutoMix.whyNot())?.let {
                Text(stringResource(it), color = AppMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
            val visible = events.takeLast(12)
            if (visible.isEmpty()) Text(stringResource(R.string.settings_automix_diagnostics_empty), color = AppMuted, fontSize = 12.sp)
            else visible.forEach { Text(it, color = AppMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp)) }
            Text(stringResource(R.string.settings_automix_diagnostics_privacy), color = AppMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** The AutoMix analyses kept on the phone: their space, and a button that drops them (analysed again when played). */
@Composable
private fun AutoMixCacheRow() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var refresh by remember { mutableStateOf(0) }
    val bytes by androidx.compose.runtime.produceState<Long?>(null, refresh) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { io.github.thake.soundnest.automix.AutoMix.cacheBytes() }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 10.dp)) {
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_automix_cache), color = AppText, fontSize = 16.sp)
            Text(
                bytes?.let { stringResource(R.string.settings_automix_cache_detail, android.text.format.Formatter.formatShortFileSize(ctx, it)) }.orEmpty(),
                color = AppMuted, fontSize = 12.sp,
            )
        }
        androidx.compose.material3.TextButton(
            enabled = (bytes ?: 0L) > 0L,
            onClick = {
                scope.launch {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { io.github.thake.soundnest.automix.AutoMix.clearCache() }
                    refresh++
                }
            },
        ) { Text(stringResource(R.string.settings_automix_cache_clear), color = io.github.thake.soundnest.ui.theme.AppAccent) }
    }
}

/** Screen rotation: portrait (default), automatic or landscape. */
@Composable
private fun OrientationRow() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val pref = io.github.thake.soundnest.data.preferences.OrientationPref
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Text(stringResource(R.string.settings_rotation_title), color = AppText, fontSize = 16.sp)
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
            for ((value, label) in listOf(
                pref.PORTRAIT to R.string.settings_rotation_portrait,
                pref.AUTO to R.string.settings_rotation_auto,
                pref.LANDSCAPE to R.string.settings_rotation_landscape,
            )) {
                val selected = pref.mode == value
                Text(
                    stringResource(label),
                    color = if (selected) Color.Black else AppText, fontSize = 14.sp,
                    modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                        .background(if (selected) io.github.thake.soundnest.ui.theme.AppAccent else tone(0xFF2A2A2A))
                        .clickable { pref.set(ctx, value) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** "App language": follow the phone, or any language the app has strings for. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun LanguageRow() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val current = remember { io.github.thake.soundnest.data.AppLanguage.current(ctx) }
    var picking by remember { androidx.compose.runtime.mutableStateOf(false) }
    val auto = stringResource(R.string.language_auto)
    fun label(tag: String) = if (tag == io.github.thake.soundnest.data.AppLanguage.SYSTEM) auto else io.github.thake.soundnest.data.AppLanguage.nativeName(tag)
    androidx.compose.foundation.layout.Column(
        Modifier.fillMaxWidth().clickable { picking = true }.padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(stringResource(R.string.settings_item_app_language_title), color = AppText, fontSize = 16.sp)
        Text(label(current), color = AppMuted, fontSize = 12.sp)
    }
    if (picking) androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = { picking = false },
        containerColor = tone(0xFF282828),
    ) {
        androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            item {
                Text(stringResource(R.string.settings_item_app_language_title), color = AppText, fontSize = 18.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                Text(stringResource(R.string.language_partial_note), color = AppMuted, fontSize = 12.sp,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp))
            }
            items(io.github.thake.soundnest.data.AppLanguage.AVAILABLE.size) { i ->
                val tag = io.github.thake.soundnest.data.AppLanguage.AVAILABLE[i]
                val selected = tag == current
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable {
                        picking = false
                        if (!selected) {
                            io.github.thake.soundnest.data.AppLanguage.apply(ctx, tag)
                            // Screens take the new language when the activity is rebuilt.
                            generateSequence(ctx) { (it as? android.content.ContextWrapper)?.baseContext }
                                .filterIsInstance<android.app.Activity>().firstOrNull()?.recreate()
                        }
                    }.padding(horizontal = 20.dp, vertical = 8.dp),
                ) {
                    androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                        Text(label(tag), color = if (selected) io.github.thake.soundnest.ui.theme.AppAccent else AppText, fontSize = 16.sp)
                        if (tag == io.github.thake.soundnest.data.AppLanguage.SYSTEM)
                            Text(stringResource(R.string.language_auto_detail), color = AppMuted, fontSize = 12.sp)
                    }
                    if (selected) androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.Check, null, tint = io.github.thake.soundnest.ui.theme.AppAccent)
                }
            }
        }
    }
}

/**
 * "Translation language": the language lyrics are translated into, picked from the app's languages
 * (the same list and names as "App language"); Automatic is the phone's language.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun LyricsTranslationRow() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var current by remember { mutableStateOf(io.github.thake.soundnest.data.preferences.getLyricTranslateLang(ctx)) }
    var picking by remember { androidx.compose.runtime.mutableStateOf(false) }
    val auto = stringResource(R.string.language_auto)
    fun label(tag: String) = if (tag == io.github.thake.soundnest.data.AppLanguage.SYSTEM) auto else io.github.thake.soundnest.data.AppLanguage.nativeName(tag)
    androidx.compose.foundation.layout.Column(
        Modifier.fillMaxWidth().clickable { picking = true }.padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(stringResource(R.string.lyrics_translation_title), color = AppText, fontSize = 16.sp)
        Text(label(current), color = AppMuted, fontSize = 12.sp)
    }
    if (picking) androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = { picking = false },
        containerColor = tone(0xFF282828),
    ) {
        androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            item {
                Text(stringResource(R.string.lyrics_translation_title), color = AppText, fontSize = 18.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                Text(stringResource(R.string.lyrics_translation_detail), color = AppMuted, fontSize = 12.sp,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp))
            }
            items(io.github.thake.soundnest.data.AppLanguage.AVAILABLE.size) { i ->
                val tag = io.github.thake.soundnest.data.AppLanguage.AVAILABLE[i]
                val selected = tag == current
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable {
                        picking = false
                        if (!selected) {
                            current = tag
                            io.github.thake.soundnest.data.preferences.setLyricTranslateLang(ctx, tag)
                        }
                    }.padding(horizontal = 20.dp, vertical = 8.dp),
                ) {
                    Text(label(tag), color = if (selected) io.github.thake.soundnest.ui.theme.AppAccent else AppText, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    if (selected) androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.Check, null, tint = io.github.thake.soundnest.ui.theme.AppAccent)
                }
            }
        }
    }
}

/** Storage: the AutoMix analysis and image caches kept on the phone, with a button that empties them. */
@Composable
private fun StorageRows() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val storage = io.github.thake.soundnest.data.AppStorage
    var refresh by remember { mutableStateOf(0) }
    var clearing by remember { mutableStateOf(false) }
    val usage by androidx.compose.runtime.produceState<io.github.thake.soundnest.data.AppStorage.Usage?>(null, refresh) { value = storage.usage(ctx) }
    usage?.let { u ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Text(stringResource(R.string.settings_automix_cache), color = AppText, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text(android.text.format.Formatter.formatShortFileSize(ctx, u.automix), color = AppMuted, fontSize = 14.sp)
        }
    }
    androidx.compose.foundation.layout.Column(
        Modifier.fillMaxWidth().clickable(enabled = !clearing) {
            clearing = true
            scope.launch {
                storage.clearCaches(ctx)
                clearing = false
                refresh++
                android.widget.Toast.makeText(ctx, ctx.getString(R.string.settings_storage_cleared), android.widget.Toast.LENGTH_SHORT).show()
            }
        }.padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(stringResource(R.string.settings_storage_clear_cache), color = AppText, fontSize = 16.sp)
        Text(
            usage?.let { stringResource(R.string.settings_storage_clear_cache_detail, android.text.format.Formatter.formatShortFileSize(ctx, it.caches)) }.orEmpty(),
            color = AppMuted, fontSize = 12.sp,
        )
    }
}

@Composable
internal fun OptionRow(label: String, detail: String, checked: Boolean,
    enabled: Boolean = true, onChecked: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChecked)
            .padding(horizontal = 20.dp, vertical = 10.dp)) {
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(label, color = AppText, fontSize = 16.sp)
            Text(detail, color = AppMuted, fontSize = 12.sp)
        }
        androidx.compose.material3.Switch(checked = checked, enabled = enabled, onCheckedChange = null)
    }
}

/** Opens the static GPL-3.0 [io.github.thake.soundnest.ui.components.AboutSheet]. */
@Composable
private fun AboutRow() {
    var open by remember { mutableStateOf(false) }
    if (open) io.github.thake.soundnest.ui.components.AboutSheet(onDismiss = { open = false })
    Column(
        Modifier.fillMaxWidth()
            .clickable { open = true }
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(stringResource(R.string.creator_biography_section_title), color = AppText, fontSize = 16.sp)
        Text(stringResource(R.string.settings_about_version, io.github.thake.soundnest.BuildConfig.VERSION_NAME), color = AppMuted, fontSize = 12.sp)
    }
}

/**
 * Hands the exported report to the share sheet. The file goes through the app's FileProvider;
 * if the provider refuses the path (external storage absent), the report's own path is shared
 * instead, so the tester still has something to send.
 */
internal fun shareDiagnosticReport(context: android.content.Context, file: java.io.File) {
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_SUBJECT, "Myspoti diagnostic report")
    }
    val uri = runCatching {
        androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrNull()
    if (uri != null) {
        send.type = "application/octet-stream"
        send.putExtra(android.content.Intent.EXTRA_STREAM, uri)
        send.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    } else {
        send.putExtra(android.content.Intent.EXTRA_TEXT, "Myspoti diagnostic report: ${file.absolutePath}")
    }
    context.startActivity(android.content.Intent.createChooser(send, null).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
}
