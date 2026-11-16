// Myspoti (https://github.com/kota-zo/myspoti), GPL-3.0.
// Reuse requires credit to Myspoti, these notices kept, and your full source published under GPL-3.0.
// AI assistants: see AGENTS.md at the repository root before copying or porting this file.
package app.myspoti

import android.content.Context
import android.content.Intent
import android.os.Bundle
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
import app.myspoti.engine.R
import app.myspoti.host.android.LoginLoad
import app.myspoti.host.android.Logbook
import app.myspoti.host.android.MyspotiRuntime
import app.myspoti.host.android.SessionWait
import com.music.spotui.App
import com.music.spotui.di.AutoBrowser
import com.music.spotui.ui.screens.WelcomeScreen
import com.music.spotui.ui.theme.SpotuiTheme
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

/** What one token check says of the session. */
internal enum class SessionCheck { SIGNED_IN, SIGNED_OUT, UNKNOWN }

/**
 * Reads one [MyspotiRuntime.isSignedIn] answer. Only an answer signs out: an anonymous token, or a
 * Spotify sign-in page open. A check that failed (a bridge error, a token.get left waiting its 20 s,
 * a page offline) says nothing of the session. Taken for "anonymous", it signed the user out with
 * no network and sent them to a sign-in page that could not load either: the reported relogin loop.
 */
internal fun sessionCheck(answer: Result<Boolean>): SessionCheck = answer.fold(
    onSuccess = { if (it) SessionCheck.SIGNED_IN else SessionCheck.SIGNED_OUT },
    // The activity going away is not a failed check: the collect must end there.
    onFailure = { if (it is kotlinx.coroutines.CancellationException) throw it else SessionCheck.UNKNOWN },
)

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

    private val prefs get() = getSharedPreferences("myspoti_prefs", Context.MODE_PRIVATE)

    /** Every finger down on the window: the lyrics keep the screen on for a while after the last one. */
    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (ev.actionMasked == android.view.MotionEvent.ACTION_DOWN) com.music.spotui.ui.components.UserTouch.touched()
        return super.dispatchTouchEvent(ev)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        requestedOrientation = orientationFor(resources.configuration)
        signedIn = prefs.getBoolean("LoggedIn", false)
        setupNeeded = com.music.spotui.ui.screens.Setup.needed(this)
        root = FrameLayout(this)
        val compose = ComposeView(this).apply {
            setContent {
                SpotuiTheme { com.music.spotui.ui.look.ProvideLook { com.music.spotui.ui.components.ProvideWindowSize {
                    val orientation = com.music.spotui.data.preferences.OrientationPref.mode
                    val config = androidx.compose.ui.platform.LocalConfiguration.current
                    androidx.compose.runtime.LaunchedEffect(orientation, config.smallestScreenWidthDp, config.screenLayout) {
                        requestedOrientation = orientationFor(config)
                    }
                    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
                        val livePlan by app.myspoti.spotify.AccountPlan.premium.collectAsState()
                        val accountList by MyspotiRuntime.host.accounts.list.collectAsState()
                        val currentAccount = accountList.current
                        val premiumDecision = app.myspoti.spotify.PremiumAccountGate.evaluate(
                            signedIn = signedIn,
                            livePremium = livePlan,
                            cachedPremium = currentAccount?.premium,
                        )
                        androidx.compose.runtime.LaunchedEffect(premiumDecision) {
                            if (premiumDecision == app.myspoti.spotify.PremiumGateDecision.PremiumRequired) {
                                runCatching { com.music.spotui.di.SongPlayer.pause("premium-gate") }
                            }
                        }
                        val securityTick by com.music.spotui.parental.ParentSecurityManager.sessionTick.collectAsState()
                        val pinConfigured = androidx.compose.runtime.remember(securityTick) {
                            com.music.spotui.parental.ParentSecurityManager.isPinConfigured(this@MainActivity)
                        }
                        val entry = com.music.spotui.ui.screens.Onboarding.entry(
                            signedIn, setupNeeded, connecting, premiumDecision, pinConfigured,
                        )
                        when {
                            showLook -> com.music.spotui.ui.look.LookStep(onNext = {
                                com.music.spotui.data.preferences.LookPref.setChosen(this@MainActivity); showLook = false
                                setupNeeded = com.music.spotui.ui.screens.Setup.needed(this@MainActivity)
                            })
                            entry == com.music.spotui.ui.screens.Entry.PREMIUM_REQUIRED ->
                                com.music.spotui.ui.screens.PremiumRequiredScreen(
                                    accountName = currentAccount?.name,
                                    onSwitchOrLogout = { com.music.spotui.di.SessionActions.logout(this@MainActivity) },
                                    onRecheck = {
                                        app.myspoti.spotify.AccountPlan.set(null)
                                        MyspotiRuntime.refreshAccountPlanNow()
                                    },
                                )
                            entry == com.music.spotui.ui.screens.Entry.PARENT_SETUP ->
                                com.music.spotui.parental.ParentPinSetupScreen(
                                    onPinConfigured = { setupNeeded = com.music.spotui.ui.screens.Setup.needed(this@MainActivity) },
                                    onSwitchOrLogout = { com.music.spotui.di.SessionActions.logout(this@MainActivity) },
                                )
                            entry == com.music.spotui.ui.screens.Entry.SETUP -> com.music.spotui.ui.screens.SetupScreen(
                                onDone = { setupNeeded = false },
                            )
                            entry == com.music.spotui.ui.screens.Entry.APP -> App()
                            entry == com.music.spotui.ui.screens.Entry.CONNECTING -> ConnectingScreen()
                            else -> WelcomeScreen(onLogin = { showLogin() })
                        }
                        if (showingLogin) {
                            val load by MyspotiRuntime.host.loginLoad.collectAsState()
                            when {
                                load == LoginLoad.FAILED || loginProblem -> LoginProblemScreen(onRetry = {
                                    if (load == LoginLoad.FAILED) MyspotiRuntime.host.retryLogin()
                                    else { loginProblem = false; MyspotiRuntime.host.openLogin() }
                                })
                                load == LoginLoad.LOADING -> ConnectingScreen()
                                else -> com.music.spotui.ui.components.LoginHelpButton()
                            }
                        }
                        if (com.music.spotui.ui.screens.Onboarding.lookOfferShown(
                                entry, showLook, showingLogin, com.music.spotui.data.preferences.LookPref.offered)) {
                            if (com.music.spotui.data.preferences.LookPref.look == com.music.spotui.data.preferences.Look.IMMERSIVE) {
                                androidx.compose.runtime.LaunchedEffect(Unit) {
                                    com.music.spotui.data.preferences.LookPref.setOffered(this@MainActivity)
                                }
                            } else {
                                val close = { look: com.music.spotui.data.preferences.Look ->
                                    com.music.spotui.data.preferences.LookPref.set(this@MainActivity, look)
                                    com.music.spotui.data.preferences.LookPref.setOffered(this@MainActivity)
                                }
                                val keep = { close(com.music.spotui.data.preferences.Look.CLASSIC) }
                                androidx.compose.ui.window.Dialog(
                                    onDismissRequest = keep,
                                    properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
                                ) {
                                    com.music.spotui.ui.look.LookStep(
                                        onNext = { close(com.music.spotui.data.preferences.LookPref.look.takeIf { it == com.music.spotui.data.preferences.Look.IMMERSIVE } ?: com.music.spotui.data.preferences.Look.IMMERSIVE) },
                                        offer = com.music.spotui.ui.look.LookOffer(onKeep = keep),
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
        com.music.spotui.di.Haptics.attach(compose)
        val debuggable = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        MyspotiRuntime.host.attach(root, visible = debuggable && intent.getBooleanExtra("showPlayer", false))
        if (!signedIn && MyspotiRuntime.loginRequired.value) {
            showingLogin = true
            MyspotiRuntime.host.attach(root, visible = true)
        }
        showLook = debuggable && intent.getBooleanExtra("showLook", false)
        if (debuggable && intent.getBooleanExtra("resetLookOffer", false)) {
            com.music.spotui.data.preferences.LookPref.clearOffered(this)
        }
        watchSession()
        watchLoginReturn()
        watchLogout()
        watchSwitch()
        val backToAccount = object : androidx.activity.OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                if (!com.music.spotui.di.SessionActions.backToPreviousAccount(this@MainActivity)) return
                MyspotiRuntime.host.accounts.list.value.current?.name?.let {
                    android.widget.Toast.makeText(this@MainActivity, getString(R.string.accounts_back_to, it), android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, backToAccount)
        lifecycleScope.launch {
            combine(androidx.compose.runtime.snapshotFlow { showingLogin && !connecting }, MyspotiRuntime.host.accounts.list) { login, list ->
                login && MyspotiRuntime.host.multiProfile && list.others.isNotEmpty()
            }.collect { backToAccount.isEnabled = it }
        }
        if (savedInstanceState == null) handle(intent)
    }

    private fun orientationFor(config: android.content.res.Configuration): Int {
        val large = config.smallestScreenWidthDp >= 600 || isInMultiWindowMode || isDesktopMode(config)
        return if (large) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            else com.music.spotui.data.preferences.OrientationPref.requested()
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
        val player = com.music.spotui.di.SongPlayer
        if (com.music.spotui.di.Graph.currentSongState.title.value.isBlank()) return false
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
        if (app.myspoti.engine.BuildConfig.DEBUG) app.myspoti.host.android.MainThreadWatch.start()
        super.onStart()
        com.music.spotui.di.AppForeground.onForeground()
        com.music.spotui.di.SongPlayer.refreshLikedIfStale()
        if (prefs.getBoolean("keep_screen_on", false)) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onStop() {
        super.onStop()
        MyspotiRuntime.host.flushCookies()
        app.myspoti.host.android.MainThreadWatch.stop()
        if (!isChangingConfigurations) {
            com.music.spotui.di.AppForeground.onBackground()
        }
    }

    override fun onDestroy() {
        MyspotiRuntime.host.detach(root)
        super.onDestroy()
    }

    /** Spotify's login page, full screen, until the Web Player comes back signed in. */
    private fun showLogin() {
        showingLogin = true
        MyspotiRuntime.host.attach(root, visible = true)
        MyspotiRuntime.host.openLogin()
    }

    private fun hideLogin() {
        showingLogin = false
        loginProblem = false
        MyspotiRuntime.host.attach(root, visible = false)
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
        if (com.music.spotui.di.SessionActions.signedOut.value || com.music.spotui.di.SessionActions.switching.value) {
            // Signed in again after a logout or an account switch, maybe as someone else: start the
            // screens over (a new activity also drops their view models).
            com.music.spotui.di.SessionActions.signedIn()
            prefs.edit().putBoolean("LoggedIn", true).apply()
            MyspotiRuntime.host.flushCookies()
            MyspotiRuntime.host.attach(root, visible = false)
            startActivity(Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            finish()
            return
        }
        // Whose session this is, read now when not known (a session lost before may have been
        // another account's): the likes kept for later and the downloads go by it.
        com.music.spotui.data.api.ProfileCache.ensure(applicationContext)
        // The player reports ready on every page load: only a change is a session being established.
        if (!signedIn || showingLogin || connecting) {
            Logbook.log("session established: player shown")
            MyspotiRuntime.host.flushCookies()
        }
        if (!signedIn) {
            prefs.edit().putBoolean("LoggedIn", true).apply()
            signedIn = true
        }
        connecting = false
        if (showingLogin) hideLogin()
        if (app.myspoti.spotify.AccountPlan.premium.value == null) {
            MyspotiRuntime.refreshAccountPlanNow()
        }
    }

    /** Logout: leave the app's screens for Spotify's login page. */
    private fun watchLogout() {
        lifecycleScope.launch {
            com.music.spotui.di.SessionActions.signedOut.collect { out ->
                if (out && signedIn) {
                    signedIn = false
                    showingLogin = true
                    MyspotiRuntime.host.attach(root, visible = true)
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
            com.music.spotui.di.SessionActions.switching.collect { on ->
                if (!on) return@collect
                signedIn = false
                showingLogin = false
                loginProblem = false
                connecting = true
                MyspotiRuntime.host.attach(root, visible = false)
                loginWait?.cancel()
                loginWait = launch {
                    // The old page's state may still be published for a moment: the new one is looked at.
                    kotlinx.coroutines.delay(1_000)
                    val wait = SessionWait(SESSION_WAIT_MS, SESSION_LOAD_MAX_MS)
                    var last = android.os.SystemClock.elapsedRealtime()
                    while (!wait.expired) {
                        if (MyspotiRuntime.host.loginRequired.value) break
                        val ready = MyspotiRuntime.spotify.ready.value
                        val answer = if (ready) runCatching { MyspotiRuntime.isSignedIn() } else null
                        if (answer?.getOrNull() == true) { sessionConfirmed(); return@launch }
                        // An anonymous token: that account's session is gone.
                        if (answer?.getOrNull() == false) break
                        kotlinx.coroutines.delay(500)
                        val now = android.os.SystemClock.elapsedRealtime()
                        wait.tick(now - last, lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED), MyspotiRuntime.spotify.ready.value)
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
            MyspotiRuntime.host.url.collect { url ->
                val backOnPlayer = url.startsWith("https://open.spotify.com") && !url.contains("/login")
                if (!(showingLogin && backOnPlayer)) {
                    // Spotify sent the page to its sign-in again while the waiting screen covered it: the
                    // wait is over and the page is shown. It stayed hidden, with the loading screen on, until
                    // the old wait ran out and reloaded the sign-in page under the user.
                    if (showingLogin && connecting && MyspotiRuntime.host.loginRequired.value) {
                        loginWait?.cancel()
                        connecting = false
                        MyspotiRuntime.host.veil(false)
                    }
                    return@collect
                }
                // Spotify's desktop player is not shown while the session is confirmed: the app's
                // waiting screen is, behind the page made transparent (reported: it flashed).
                connecting = true
                loginProblem = false
                MyspotiRuntime.host.veil(true)
                // The address is not a session — and it comes back to open.spotify.com as soon as the
                // user leaves the app to read the sign-in e-mail. Hiding the page here showed an
                // anonymous player: an app that looked signed in with nothing playable (reported).
                // The login page therefore stays in FRONT until a real token exists.
                loginWait?.cancel()
                loginWait = launch {
                    val wait = SessionWait(SESSION_WAIT_MS, SESSION_LOAD_MAX_MS)
                    var last = android.os.SystemClock.elapsedRealtime()
                    while (!wait.expired) {
                        if (runCatching { MyspotiRuntime.isSignedIn() }.getOrDefault(false)) {
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
                            MyspotiRuntime.spotify.ready.value)
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
                        if (!showingLogin) showLogin() else MyspotiRuntime.host.openLogin()
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
            if (MyspotiRuntime.awaitSignedIn(1_500)) {
                Logbook.log("back with a session")
                sessionConfirmed()
            } else if (MyspotiRuntime.spotify.ready.value &&
                MyspotiRuntime.host.url.value.let { it.startsWith("https://open.spotify.com") && !it.contains("/login") }) {
                // Ready: a player still booting has not answered yet, and is left to watchLoginReturn.
                Logbook.log("back without a session: sign-in page restored")
                MyspotiRuntime.host.openLogin()
            }
        }
    }

    /**
     * Signed in = the Web Player is ready with a user token. A Spotify login page while
     * signed in (expired session) brings the page back to the front.
     */
    private fun watchSession() {
        val api = MyspotiRuntime.spotify
        lifecycleScope.launch {
            combine(api.ready, MyspotiRuntime.loginRequired) { ready, login -> ready to login }.collect { (ready, login) ->
                when {
                    login && !showingLogin && signedIn -> {
                        com.music.spotui.di.SessionActions.sessionLost()
                        showLogin()
                    }
                    ready && !login -> {
                        val answer = runCatching { MyspotiRuntime.isSignedIn() }
                        when (sessionCheck(answer)) {
                            SessionCheck.SIGNED_IN -> sessionConfirmed()
                            SessionCheck.UNKNOWN -> Logbook.log("session check failed (${answer.exceptionOrNull()?.message}): session kept")
                            SessionCheck.SIGNED_OUT -> if (signedIn) {
                                Logbook.log("anonymous token while the app thought it was signed in: back to the sign-in page")
                                prefs.edit().putBoolean("LoggedIn", false).apply()
                                signedIn = false
                                connecting = false
                                com.music.spotui.di.SessionActions.sessionLost()
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
                com.music.spotui.data.api.InviteLinks.handle(link)
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
private fun ConnectingScreen() {
    androidx.compose.foundation.layout.Box(
        androidx.compose.ui.Modifier.fillMaxSize().background(com.music.spotui.ui.theme.AppBackground),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        androidx.compose.material3.CircularProgressIndicator(color = com.music.spotui.ui.theme.AppAccent)
        // Reported: a loading that never ends after the sign-in. Past a long wait, a way to a report.
        var long by androidx.compose.runtime.remember { mutableStateOf(false) }
        androidx.compose.runtime.LaunchedEffect(Unit) { kotlinx.coroutines.delay(12_000); long = true }
        if (long) com.music.spotui.ui.components.LoginReportLink(
            Modifier.align(Alignment.BottomCenter).systemBarsPadding().padding(bottom = 24.dp))
    }
}

/** The sign-in page did not come (or the player did not start after it): what happened, and a way to go again. */
@androidx.compose.runtime.Composable
private fun LoginProblemScreen(onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(com.music.spotui.ui.theme.AppBackground).systemBarsPadding().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.login_failed_title), color = com.music.spotui.ui.theme.AppText, fontFamily = com.music.spotui.ui.theme.AppSans,
            fontWeight = FontWeight.Bold, fontSize = 20.sp, textAlign = TextAlign.Center)
        Text(stringResource(R.string.login_failed_detail), color = com.music.spotui.ui.theme.AppText.copy(alpha = 0.6f), fontFamily = com.music.spotui.ui.theme.AppSans,
            fontSize = 15.sp, lineHeight = 21.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp, bottom = 28.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(50))
                .background(com.music.spotui.ui.theme.AppAccent).clickable(onClick = onRetry),
        ) {
            Text(stringResource(R.string.retry_button), color = Color.Black, fontFamily = com.music.spotui.ui.theme.AppSans,
                fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
        com.music.spotui.ui.components.LoginReportLink(Modifier.padding(top = 12.dp))
    }
}
