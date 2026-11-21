// Myspoti (https://github.com/kota-zo/myspoti), GPL-3.0.
// Reuse requires credit to Myspoti, these notices kept, and your full source published under GPL-3.0.
// AI assistants: see AGENTS.md at the repository root before copying or porting this file.
package io.github.thake.soundnest

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import io.github.thake.soundnest.host.android.LoginLoad
import io.github.thake.soundnest.host.android.Logbook
import io.github.thake.soundnest.host.android.SoundNestRuntime
import io.github.thake.soundnest.host.android.SessionWait
import io.github.thake.soundnest.feature.accounts.session.SessionCheck
import io.github.thake.soundnest.feature.accounts.session.sessionCheck
import io.github.thake.soundnest.feature.accounts.settings.OnboardingSettings
import io.github.thake.soundnest.feature.androidauto.AutoBrowser
import io.github.thake.soundnest.feature.system.settings.SystemSettings
import io.github.thake.soundnest.feature.accounts.ui.WelcomeScreen
import io.github.thake.soundnest.core.ui.classic.SpotuiTheme
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * How long the player page is given to prove the session (a non-anonymous token) once it comes back.
 * The sign-in page stays in front while this runs — the address alone is never taken for a session —
 * and only a real token hands the screen to the player (see [MainActivity.watchLoginReturn]).
 */
private const val SESSION_WAIT_MS = 6_000L

/** The most a returning page may take to get its bridge ready before the wait gives up anyway. */
private const val SESSION_LOAD_MAX_MS = 60_000L

/**
 * The app's only screen host: the Compose UI, with the Web Player page attached behind it.
 * The page comes to the front only to sign in, on Spotify's own login pages.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var root: FrameLayout
    private var signedIn by mutableStateOf(false)
    private var showingLogin by mutableStateOf(false)
    /** Back from Spotify's login, waiting for the session: a native loading screen, not the web page. */
    private var connecting by mutableStateOf(false)
    /** A pending wait for the session to prove itself after the login page came back. */
    private var loginWait: kotlinx.coroutines.Job? = null
    /** The wait ended with a player that never became ready: an error with Try again, not another spinner. */
    private var loginProblem by mutableStateOf(false)
    /** The one-time setup screen (permissions, battery) once the account is in place. */
    private var setupNeeded by mutableStateOf(false)
    /** Debug builds: `am start ... --ez showLook true` opens the look step alone, signed in or not. */
    private var showLook by mutableStateOf(false)

    /** Every finger down on the window: the lyrics keep the screen on for a while after the last one. */
    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (ev.actionMasked == android.view.MotionEvent.ACTION_DOWN) io.github.thake.soundnest.core.ui.look.UserTouch.touched()
        return super.dispatchTouchEvent(ev)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        requestedOrientation = orientationFor(resources.configuration)
        signedIn = OnboardingSettings.isLoggedIn(this)
        setupNeeded = io.github.thake.soundnest.feature.accounts.ui.Setup.needed(this)
        root = FrameLayout(this)
        val compose = ComposeView(this).apply {
            importantForAutofill = if (showingLogin) View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS else View.IMPORTANT_FOR_AUTOFILL_AUTO
            setContent {
                SpotuiTheme { io.github.thake.soundnest.core.ui.look.ProvideLook { io.github.thake.soundnest.core.ui.look.ProvideWindowSize {
                    val orientation = io.github.thake.soundnest.feature.appearance.settings.OrientationPref.mode
                    val config = androidx.compose.ui.platform.LocalConfiguration.current
                    androidx.compose.runtime.LaunchedEffect(orientation, config.smallestScreenWidthDp, config.screenLayout) {
                        requestedOrientation = orientationFor(config)
                    }
                    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
                        val securityTick by io.github.thake.soundnest.feature.parental.security.ParentSecurityManager.sessionTick.collectAsState()
                        val pinConfigured = androidx.compose.runtime.remember(securityTick) {
                            io.github.thake.soundnest.feature.parental.security.ParentSecurityManager.isPinConfigured(this@MainActivity)
                        }
                        val deviceRole by io.github.thake.soundnest.feature.parental.remote.settings.DeviceRoleStore.roleFlow.collectAsState()
                        val entry = io.github.thake.soundnest.feature.accounts.ui.Onboarding.entry(
                            signedIn,
                            setupNeeded,
                            connecting,
                            pinConfigured,
                            isParentController = deviceRole == io.github.thake.soundnest.feature.parental.remote.settings.DeviceRole.PARENT_CONTROLLER,
                        )
                        when {
                            showLook -> io.github.thake.soundnest.feature.appearance.ui.LookStep(onNext = {
                                io.github.thake.soundnest.feature.appearance.settings.LookPref.setChosen(this@MainActivity); showLook = false
                                setupNeeded = io.github.thake.soundnest.feature.accounts.ui.Setup.needed(this@MainActivity)
                            })
                            entry == io.github.thake.soundnest.feature.accounts.ui.Entry.PARENT_HUB ->
                                io.github.thake.soundnest.feature.parental.remote.ui.ParentHubScreen(
                                    isDedicatedMode = true,
                                    onBack = null,
                                    onSwitchToStandardMode = {
                                        io.github.thake.soundnest.feature.parental.remote.settings.DeviceRoleStore.setRole(
                                            this@MainActivity,
                                            io.github.thake.soundnest.feature.parental.remote.settings.DeviceRole.STANDARD,
                                        )
                                    },
                                )
                            entry == io.github.thake.soundnest.feature.accounts.ui.Entry.PARENT_SETUP ->
                                io.github.thake.soundnest.feature.parental.ui.ParentPinSetupScreen(
                                    onPinConfigured = { setupNeeded = io.github.thake.soundnest.feature.accounts.ui.Setup.needed(this@MainActivity) },
                                    onSwitchOrLogout = { io.github.thake.soundnest.feature.accounts.session.SessionActions.logout(this@MainActivity) },
                                )
                            entry == io.github.thake.soundnest.feature.accounts.ui.Entry.SETUP -> io.github.thake.soundnest.feature.accounts.ui.SetupScreen(
                                onDone = { setupNeeded = false },
                            )
                            entry == io.github.thake.soundnest.feature.accounts.ui.Entry.APP -> App()
                            entry == io.github.thake.soundnest.feature.accounts.ui.Entry.CONNECTING -> ConnectingScreen(
                                onCancel = {
                                    loginWait?.cancel()
                                    connecting = false
                                    showLogin()
                                },
                            )
                            else -> WelcomeScreen(
                                onLogin = { showLogin() },
                                onParentMode = {
                                    io.github.thake.soundnest.feature.parental.remote.settings.DeviceRoleStore.setRole(
                                        this@MainActivity,
                                        io.github.thake.soundnest.feature.parental.remote.settings.DeviceRole.PARENT_CONTROLLER,
                                    )
                                },
                            )
                        }
                        if (showingLogin) {
                            val load by SoundNestRuntime.host.loginLoad.collectAsState()
                            when {
                                load == LoginLoad.FAILED || loginProblem -> LoginProblemScreen(onRetry = {
                                    if (load == LoginLoad.FAILED) SoundNestRuntime.host.retryLogin()
                                    else { loginProblem = false; SoundNestRuntime.host.openLogin() }
                                })
                                load == LoginLoad.LOADING -> ConnectingScreen(
                                    onCancel = {
                                        loginWait?.cancel()
                                        connecting = false
                                        hideLogin()
                                    },
                                )
                                else -> io.github.thake.soundnest.feature.accounts.ui.LoginHelpButton()
                            }
                        }
                        if (io.github.thake.soundnest.feature.accounts.ui.Onboarding.lookOfferShown(
                                entry, showLook, showingLogin, io.github.thake.soundnest.feature.appearance.settings.LookPref.offered)) {
                            if (io.github.thake.soundnest.feature.appearance.settings.LookPref.look == io.github.thake.soundnest.feature.appearance.settings.Look.IMMERSIVE) {
                                androidx.compose.runtime.LaunchedEffect(Unit) {
                                    io.github.thake.soundnest.feature.appearance.settings.LookPref.setOffered(this@MainActivity)
                                }
                            } else {
                                val close = { look: io.github.thake.soundnest.feature.appearance.settings.Look ->
                                    io.github.thake.soundnest.feature.appearance.settings.LookPref.set(this@MainActivity, look)
                                    io.github.thake.soundnest.feature.appearance.settings.LookPref.setOffered(this@MainActivity)
                                }
                                val keep = { close(io.github.thake.soundnest.feature.appearance.settings.Look.CLASSIC) }
                                androidx.compose.ui.window.Dialog(
                                    onDismissRequest = keep,
                                    properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
                                ) {
                                    io.github.thake.soundnest.feature.appearance.ui.LookStep(
                                        onNext = { close(io.github.thake.soundnest.feature.appearance.settings.LookPref.look.takeIf { it == io.github.thake.soundnest.feature.appearance.settings.Look.IMMERSIVE } ?: io.github.thake.soundnest.feature.appearance.settings.Look.IMMERSIVE) },
                                        offer = io.github.thake.soundnest.feature.appearance.ui.LookOffer(onKeep = keep),
                                    )
                                }
                            }
                        }
                    }
                } } }
            }
        }
        root.addView(compose, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)
        io.github.thake.soundnest.core.util.Haptics.attach(compose)
        val debuggable = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        SoundNestRuntime.host.attach(root, visible = debuggable && intent.getBooleanExtra("showPlayer", false))
        if (!signedIn && SoundNestRuntime.loginRequired.value) {
            showingLogin = true
            SoundNestRuntime.host.attach(root, visible = true)
        }
        showLook = debuggable && intent.getBooleanExtra("showLook", false)
        if (debuggable && intent.getBooleanExtra("resetLookOffer", false)) {
            io.github.thake.soundnest.feature.appearance.settings.LookPref.clearOffered(this)
        }
        watchSession()
        watchLoginReturn()
        watchLogout()
        watchSwitch()
        val backToAccount = object : androidx.activity.OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                if (!io.github.thake.soundnest.feature.accounts.session.SessionActions.backToPreviousAccount(this@MainActivity)) return
                SoundNestRuntime.host.accounts.list.value.current?.name?.let {
                    android.widget.Toast.makeText(this@MainActivity, getString(R.string.accounts_back_to, it), android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, backToAccount)
        lifecycleScope.launch {
            androidx.compose.runtime.snapshotFlow { showingLogin }.collect { login ->
                compose.importantForAutofill = if (login) {
                    View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                } else {
                    View.IMPORTANT_FOR_AUTOFILL_AUTO
                }
            }
        }
        lifecycleScope.launch {
            combine(androidx.compose.runtime.snapshotFlow { showingLogin && !connecting }, SoundNestRuntime.host.accounts.list) { login, list ->
                login && SoundNestRuntime.host.multiProfile && list.others.isNotEmpty()
            }.collect { backToAccount.isEnabled = it }
        }
        if (savedInstanceState == null) handle(intent)
    }

    private fun orientationFor(config: android.content.res.Configuration): Int {
        val large = config.smallestScreenWidthDp >= 600 || isInMultiWindowMode || isDesktopMode(config)
        return if (large) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            else io.github.thake.soundnest.feature.appearance.settings.OrientationPref.requested()
    }

    private fun isDesktopMode(config: android.content.res.Configuration): Boolean {
        val dex = runCatching {
            val c = config.javaClass
            c.getField("SEM_DESKTOP_MODE_ENABLED").getInt(c) == c.getField("semDesktopModeEnabled").getInt(config)
        }.getOrDefault(false)
        return dex || (config.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK) == android.content.res.Configuration.UI_MODE_TYPE_DESK
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
        if (signedIn && handleShortcut(keyCode, event)) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.action == android.view.KeyEvent.ACTION_DOWN && event.isCtrlPressed && signedIn &&
            (event.keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT || event.keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT)
            && handleShortcut(event.keyCode, event)) return true
        return super.dispatchKeyEvent(event)
    }

    private fun handleShortcut(keyCode: Int, event: android.view.KeyEvent): Boolean {
        val player = io.github.thake.soundnest.core.playback.SongPlayer
        if (io.github.thake.soundnest.core.di.Graph.currentSongState.title.value.isBlank()) return false
        when (keyCode) {
            android.view.KeyEvent.KEYCODE_SPACE, android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                if (event.repeatCount == 0) { if (player.isPlaying()) player.pause(by = "keyboard") else player.play(by = "keyboard") }
            }
            android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> if (event.repeatCount == 0) player.play(by = "keyboard")
            android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> if (event.repeatCount == 0) player.pause(by = "keyboard")
            android.view.KeyEvent.KEYCODE_MEDIA_NEXT -> if (event.repeatCount == 0) player.next(by = "keyboard")
            android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS -> if (event.repeatCount == 0) player.previous(by = "keyboard")
            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> player.skipBy(-10_000L)
            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> player.skipBy(10_000L)
            else -> return false
        }
        return true
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onStart() {
        if (io.github.thake.soundnest.BuildConfig.DEBUG) io.github.thake.soundnest.host.android.MainThreadWatch.start()
        super.onStart()
        io.github.thake.soundnest.core.util.AppForeground.onForeground()
        io.github.thake.soundnest.feature.parental.remote.net.ChildRemoteServer.syncLifecycle(this)
        io.github.thake.soundnest.core.playback.SongPlayer.refreshLikedIfStale()
        if (SystemSettings.isKeepScreenOn(this)) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onStop() {
        super.onStop()
        SoundNestRuntime.host.flushCookies()
        io.github.thake.soundnest.host.android.MainThreadWatch.stop()
        if (!isChangingConfigurations) {
            io.github.thake.soundnest.core.util.AppForeground.onBackground()
        }
    }

    override fun onDestroy() {
        SoundNestRuntime.host.detach(root)
        super.onDestroy()
    }

    /** Spotify's login page, full screen, until the Web Player comes back signed in. */
    private fun showLogin() {
        showingLogin = true
        SoundNestRuntime.host.attach(root, visible = true)
        SoundNestRuntime.host.openLogin()
    }

    private fun hideLogin() {
        showingLogin = false
        loginProblem = false
        SoundNestRuntime.host.attach(root, visible = false)
    }

    /**
     * The token says signed in: the screens take over from the sign-in page. One door for the three
     * places that find the session (the wait after the page came back, the return from the e-mail
     * app, the player becoming ready). The wait used to hand the screen over without telling the
     * app it was signed in, which only the player's ready signal did: when that signal had already
     * come and gone, the loading screen stayed for ever (nothing else sets [signedIn]).
     */
    private fun sessionConfirmed() {
        // Another door already started the screens over: this activity is on its way out.
        if (isFinishing) return
        loginProblem = false
        if (io.github.thake.soundnest.feature.accounts.session.SessionActions.signedOut.value || io.github.thake.soundnest.feature.accounts.session.SessionActions.switching.value) {
            // Signed in again after a logout or an account switch, maybe as someone else: start the
            // screens over (a new activity also drops their view models).
            io.github.thake.soundnest.feature.accounts.session.SessionActions.signedIn()
            OnboardingSettings.setLoggedIn(this, true)
            SoundNestRuntime.host.flushCookies()
            SoundNestRuntime.host.attach(root, visible = false)
            startActivity(Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            finish()
            return
        }
        // Whose session this is, read now when not known (a session lost before may have been
        // another account's): the likes kept for later and the downloads go by it.
        io.github.thake.soundnest.feature.accounts.session.ProfileCache.ensure(applicationContext)
        // The player reports ready on every page load: only a change is a session being established.
        if (!signedIn || showingLogin || connecting) {
            Logbook.log("session established: player shown")
            SoundNestRuntime.host.flushCookies()
        }
        if (!signedIn) {
            OnboardingSettings.setLoggedIn(this, true)
            signedIn = true
        }
        connecting = false
        if (showingLogin) hideLogin()
        if (io.github.thake.soundnest.spotify.AccountPlan.premium.value == null) {
            SoundNestRuntime.refreshAccountPlanNow()
        }
    }

    /** Logout: leave the app's screens for Spotify's login page. */
    private fun watchLogout() {
        lifecycleScope.launch {
            io.github.thake.soundnest.feature.accounts.session.SessionActions.signedOut.collect { out ->
                if (out && signedIn) {
                    signedIn = false
                    showingLogin = true
                    SoundNestRuntime.host.attach(root, visible = true)
                }
            }
        }
    }

    /**
     * An account switch: the page is rebuilt on the other account's profile, hidden, while the app's
     * own loading screen shows. Its session confirmed, the screens start over ([sessionConfirmed]).
     * Spotify's sign-in page instead (that account's session is gone), or no session in time: the
     * sign-in page is shown, as for any sign-in.
     */
    private fun watchSwitch() {
        lifecycleScope.launch {
            io.github.thake.soundnest.feature.accounts.session.SessionActions.switching.collect { on ->
                if (!on) return@collect
                signedIn = false
                showingLogin = false
                loginProblem = false
                connecting = true
                SoundNestRuntime.host.attach(root, visible = false)
                loginWait?.cancel()
                loginWait = launch {
                    // The old page's state may still be published for a moment: the new one is looked at.
                    kotlinx.coroutines.delay(1_000)
                    val wait = SessionWait(SESSION_WAIT_MS, SESSION_LOAD_MAX_MS)
                    var last = android.os.SystemClock.elapsedRealtime()
                    while (!wait.expired) {
                        if (SoundNestRuntime.host.loginRequired.value) break
                        val ready = SoundNestRuntime.spotify.ready.value
                        val answer = if (ready) runCatching { SoundNestRuntime.isSignedIn() } else null
                        if (answer?.getOrNull() == true) { sessionConfirmed(); return@launch }
                        // An anonymous token: that account's session is gone.
                        if (answer?.getOrNull() == false) break
                        kotlinx.coroutines.delay(500)
                        val now = android.os.SystemClock.elapsedRealtime()
                        wait.tick(now - last, lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED), SoundNestRuntime.spotify.ready.value)
                        last = now
                    }
                    Logbook.log("account switch: no session on that account, sign-in page shown")
                    connecting = false
                    // Its sign-in, on its own profile: a session still good there comes straight back.
                    showLogin()
                }
            }
        }
    }

    /**
     * Spotify sent the user back to the player: the page is hidden now, the session follows.
     *
     * The address is not a session. The page comes back to open.spotify.com while its token is
     * still anonymous when the login never finished (the e-mail step that hangs — the reported
     * "stuck on the e-mail screen"), and hiding the page on that alone left a spinner that never
     * stopped. The return is bounded: no token after [SESSION_WAIT_MS], the login page comes back.
     */
    private fun watchLoginReturn() {
        lifecycleScope.launch {
            SoundNestRuntime.host.url.collect { url ->
                val backOnPlayer = url.startsWith("https://open.spotify.com") && !url.contains("/login")
                if (!(showingLogin && backOnPlayer)) {
                    // Spotify sent the page to its sign-in again while the waiting screen covered it: the
                    // wait is over and the page is shown. It stayed hidden, with the loading screen on, until
                    // the old wait ran out and reloaded the sign-in page under the user.
                    if (showingLogin && connecting && SoundNestRuntime.host.loginRequired.value) {
                        loginWait?.cancel()
                        connecting = false
                        SoundNestRuntime.host.veil(false)
                    }
                    return@collect
                }
                // Spotify's desktop player is not shown while the session is confirmed: the app's
                // waiting screen is, behind the page made transparent (reported: it flashed).
                connecting = true
                loginProblem = false
                SoundNestRuntime.host.veil(true)
                // The address is not a session — and it comes back to open.spotify.com as soon as the
                // user leaves the app to read the sign-in e-mail. Hiding the page here showed an
                // anonymous player: an app that looked signed in with nothing playable (reported).
                // The login page therefore stays in FRONT until a real token exists.
                loginWait?.cancel()
                loginWait = launch {
                    val wait = SessionWait(SESSION_WAIT_MS, SESSION_LOAD_MAX_MS)
                    var last = android.os.SystemClock.elapsedRealtime()
                    while (!wait.expired) {
                        if (runCatching { SoundNestRuntime.isSignedIn() }.getOrDefault(false)) {
                            sessionConfirmed()
                            return@launch
                        }
                        kotlinx.coroutines.delay(500)
                        // Real time, counted only while the user is looking at the app: away to read the
                        // e-mail, the sign-in page must not be reset under them. Nor while the player is
                        // still loading: it answers nothing before its bridge is ready, which takes 10 s
                        // and more on a slow device (a DAP, a low-end Xiaomi). Counted from the address
                        // alone, the wait ran out under a page that was still booting and put the sign-in
                        // page back: the reported loop to the login page. And by turns of this loop, not
                        // by the clock, a page that left each token request waiting its 20 s made the
                        // minute last 40.
                        val now = android.os.SystemClock.elapsedRealtime()
                        wait.tick(now - last, lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED),
                            SoundNestRuntime.spotify.ready.value)
                        last = now
                    }
                    connecting = false
                    if (wait.playerNeverReady) {
                        // Not signed out: the player never started. The sign-in page would send it straight
                        // back here (the cookies are good), minute after minute: the reported loop.
                        Logbook.log("no session after ${wait.elapsedMs / 1000} s: the player never became ready (waited ${wait.waitedMs / 1000} s with it ready)")
                        loginProblem = true
                    } else {
                        Logbook.log("no session ${SESSION_WAIT_MS / 1000} s after the player came back: sign-in page restored")
                        // The page may now be the anonymous player: send it back to the e-mail step so the
                        // user is not left where nothing plays.
                        if (!showingLogin) showLogin() else SoundNestRuntime.host.openLogin()
                    }
                }
            }
        }
    }

    /**
     * Back from the e-mail app: the session may have been established while away, or the page may
     * have reloaded into the anonymous player. Only the token decides — never the address.
     */
    override fun onResume() {
        super.onResume()
        if (!showingLogin) return
        lifecycleScope.launch {
            if (SoundNestRuntime.awaitSignedIn(1_500)) {
                Logbook.log("back with a session")
                sessionConfirmed()
            } else if (SoundNestRuntime.spotify.ready.value &&
                SoundNestRuntime.host.url.value.let { it.startsWith("https://open.spotify.com") && !it.contains("/login") }) {
                // Ready: a player still booting has not answered yet, and is left to watchLoginReturn.
                Logbook.log("back without a session: sign-in page restored")
                SoundNestRuntime.host.openLogin()
            }
        }
    }

    /**
     * Signed in = the Web Player is ready with a user token. A Spotify login page while
     * signed in (expired session) brings the page back to the front.
     */
    private fun watchSession() {
        val api = SoundNestRuntime.spotify
        lifecycleScope.launch {
            combine(api.ready, SoundNestRuntime.loginRequired) { ready, login -> ready to login }.collect { (ready, login) ->
                when {
                    login && !showingLogin && signedIn -> {
                        io.github.thake.soundnest.feature.accounts.session.SessionActions.sessionLost()
                        showLogin()
                    }
                    ready && !login -> {
                        val answer = runCatching { SoundNestRuntime.isSignedIn() }
                        when (sessionCheck(answer)) {
                            SessionCheck.SIGNED_IN -> sessionConfirmed()
                            SessionCheck.UNKNOWN -> Logbook.log("session check failed (${answer.exceptionOrNull()?.message}): session kept")
                            SessionCheck.SIGNED_OUT -> if (signedIn) {
                                Logbook.log("anonymous token while the app thought it was signed in: back to the sign-in page")
                                OnboardingSettings.setLoggedIn(this@MainActivity, false)
                                signedIn = false
                                connecting = false
                                io.github.thake.soundnest.feature.accounts.session.SessionActions.sessionLost()
                                showLogin()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun handle(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                val link = intent.data?.toString() ?: return
                io.github.thake.soundnest.core.catalog.InviteLinks.handle(link)
            }
            android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH -> {
                val query = intent.getStringExtra(android.app.SearchManager.QUERY).orEmpty()
                if (query.isNotBlank()) lifecycleScope.launch { AutoBrowser.playFromSearch(query) }
            }
        }
    }
}

/** Shown between Spotify's login page and the first screen. */
@androidx.compose.runtime.Composable
internal fun ConnectingScreen(onCancel: (() -> Unit)? = null) {
    androidx.compose.foundation.layout.Box(
        androidx.compose.ui.Modifier.fillMaxSize().background(io.github.thake.soundnest.core.ui.classic.AppBackground),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            androidx.compose.material3.CircularProgressIndicator(color = io.github.thake.soundnest.core.ui.classic.AppAccent)
            Text(
                text = stringResource(R.string.connecting_spotify_status),
                color = io.github.thake.soundnest.core.ui.classic.AppText,
                fontFamily = io.github.thake.soundnest.core.ui.classic.AppSans,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = stringResource(R.string.connecting_spotify_detail),
                color = io.github.thake.soundnest.core.ui.classic.AppMuted,
                fontFamily = io.github.thake.soundnest.core.ui.classic.AppSans,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
            )
            if (onCancel != null) {
                androidx.compose.material3.TextButton(
                    onClick = onCancel,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.connecting_cancel_sign_in),
                        color = io.github.thake.soundnest.core.ui.classic.AppMuted,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        // Reported: a loading that never ends after the sign-in. Past a long wait, a way to a report.
        var long by androidx.compose.runtime.remember { mutableStateOf(false) }
        androidx.compose.runtime.LaunchedEffect(Unit) { kotlinx.coroutines.delay(12_000); long = true }
        if (long) io.github.thake.soundnest.feature.accounts.ui.LoginReportLink(
            Modifier.align(Alignment.BottomCenter).systemBarsPadding().padding(bottom = 24.dp))
    }
}

/** The sign-in page did not come (or the player did not start after it): what happened, and a way to go again. */
@androidx.compose.runtime.Composable
internal fun LoginProblemScreen(onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(io.github.thake.soundnest.core.ui.classic.AppBackground).systemBarsPadding().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.login_failed_title), color = io.github.thake.soundnest.core.ui.classic.AppText, fontFamily = io.github.thake.soundnest.core.ui.classic.AppSans,
            fontWeight = FontWeight.Bold, fontSize = 20.sp, textAlign = TextAlign.Center)
        Text(stringResource(R.string.login_failed_detail), color = io.github.thake.soundnest.core.ui.classic.AppText.copy(alpha = 0.6f), fontFamily = io.github.thake.soundnest.core.ui.classic.AppSans,
            fontSize = 15.sp, lineHeight = 21.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp, bottom = 28.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(50))
                .background(io.github.thake.soundnest.core.ui.classic.AppAccent).clickable(onClick = onRetry),
        ) {
            Text(stringResource(R.string.retry_button), color = Color.Black, fontFamily = io.github.thake.soundnest.core.ui.classic.AppSans,
                fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
        io.github.thake.soundnest.feature.accounts.ui.LoginReportLink(Modifier.padding(top = 12.dp))
    }
}
