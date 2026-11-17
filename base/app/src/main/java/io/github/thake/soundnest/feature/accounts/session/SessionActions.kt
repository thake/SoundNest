package io.github.thake.soundnest.feature.accounts.session

import android.content.Context
import io.github.thake.soundnest.core.catalog.Api
import io.github.thake.soundnest.core.catalog.EditablePlaylists
import io.github.thake.soundnest.core.catalog.LibraryOutbox
import io.github.thake.soundnest.core.playback.LocalPlayer
import io.github.thake.soundnest.core.playback.SongPlayer
import io.github.thake.soundnest.core.preferences.SessionResettable
import io.github.thake.soundnest.core.util.accountKeyOf
import io.github.thake.soundnest.feature.accounts.settings.OnboardingSettings
import io.github.thake.soundnest.feature.androidauto.AutoBrowser
import io.github.thake.soundnest.feature.library.settings.LibraryCache
import io.github.thake.soundnest.feature.library.ui.ContextViewModel
import io.github.thake.soundnest.feature.recap.data.ListenHistory
import io.github.thake.soundnest.feature.recap.data.TopStats
import io.github.thake.soundnest.feature.recap.settings.RecentsSettings
import io.github.thake.soundnest.feature.speakers.connect.ConnectCode
import io.github.thake.soundnest.feature.speakers.settings.SpeakersSettings
import io.github.thake.soundnest.host.android.Logbook
import io.github.thake.soundnest.host.android.PlaybackService
import io.github.thake.soundnest.host.android.SoundNestRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** What one token check says of the session. */
internal enum class SessionCheck { SIGNED_IN, SIGNED_OUT, UNKNOWN }

/**
 * Reads one [SoundNestRuntime.isSignedIn] answer. Only an answer signs out: an anonymous token, or a
 * Spotify sign-in page open. A check that failed (a bridge error, a token.get left waiting its 20 s,
 * a page offline) says nothing of the session. Taken for "anonymous", it signed the user out with
 * no network and sent them to a sign-in page that could not load either: the reported relogin loop.
 */
internal fun sessionCheck(answer: Result<Boolean>): SessionCheck = answer.fold(
    onSuccess = { if (it) SessionCheck.SIGNED_IN else SessionCheck.SIGNED_OUT },
    // The activity going away is not a failed check: the collect must end there.
    onFailure = { if (it is CancellationException) throw it else SessionCheck.UNKNOWN },
)

/** Session-wide actions the screens trigger. */
object SessionActions {
    private val sessionResettables: List<SessionResettable> = listOf(
        OnboardingSettings,
        RecentsSettings,
        ConnectCode,
        SpeakersSettings,
        LibraryCache,
    )

    private val _signedOut = MutableStateFlow(false)
    /** True from a logout until the next sign-in: the activity shows the login page, then starts over. */
    val signedOut: StateFlow<Boolean> = _signedOut

    private val _switching = MutableStateFlow(false)
    /**
     * True from an account switch until that account's session is confirmed: the activity waits for
     * it on its own loading screen (the sign-in page only if that account's session is gone), then starts over.
     */
    val switching: StateFlow<Boolean> = _switching

    /**
     * Signs out and drops everything tied to the account, so the next account on
     * this phone starts clean: prefs and in-memory caches, the saved screens,
     * the listen history (the Recap), and the Web Player's cookies, Web storage
     * and cache (in the host).
     * With other accounts kept on the phone, this one is removed and the one used last takes over.
     */
    fun logout(context: Context) {
        val host = SoundNestRuntime.host
        val list = host.accounts.list.value
        if (host.multiProfile && list.others.isNotEmpty()) {
            removeAccount(context, list.active)
            return
        }
        leaveAccount(context.applicationContext, forget = true)
        _signedOut.value = true
        host.logout()
    }

    /**
     * Everything that follows the account leaves with it: nothing of it keeps playing or showing,
     * and its caches are dropped. [forget]: a sign-out or a removal, its listen history goes;
     * otherwise (a switch) it is put aside under it too.
     */
    private fun leaveAccount(app: Context, forget: Boolean) {
        val account = ProfileCache.id
        val key = account?.let { accountKeyOf(it) } ?: SoundNestRuntime.host.accounts.list.value.current?.key
        // Nothing of the account keeps playing or showing: local songs, the notification.
        LocalPlayer.stop()
        PlaybackService.end()
        runCatching { SoundNestRuntime.scope.launch { runCatching { SoundNestRuntime.api.player.pause() } } }
        ContextViewModel.clearCache()
        Api.HomeCache.clear()
        Api.LikedCache.tracks = null
        TopStats.clear()
        ProfileCache.clearSession()
        // Likes kept for later stay this account's, sent when it signs in again; the next account gets none.
        LibraryOutbox.signedOut()
        SongPlayer.clearSession()
        EditablePlaylists.clear()
        sessionResettables.forEach { it.onLeaveAccount(app, forget) }
        if (forget) ListenHistory.clear()
        else ListenHistory.park(key)
        AutoBrowser.clearSession()
        // The API's token, username and Jam, and the plan: the next account's are read.
        SoundNestRuntime.accountChanged()
    }

    /**
     * Another account kept on the phone takes over ([profile], a WebView profile of its own): playback
     * stops, what follows this account leaves ([leaveAccount]), and the page is rebuilt on the other
     * profile, signed in as it was left. Without WebView profiles, one account at a time: signs out.
     */
    fun switchAccount(context: Context, profile: String) {
        val host = SoundNestRuntime.host
        if (!host.multiProfile) { logout(context); return }
        val list = host.accounts.list.value
        if (profile == list.active || list.accounts.none { it.profile == profile }) return
        Logbook.log("account: switching to another account kept on the phone")
        leaveAccount(context.applicationContext, forget = false)
        _switching.value = true
        host.switchProfile(profile)
    }

    /** "Add account": this one stays kept, and Spotify's sign-in page opens in a new profile. Without profiles: signs out. */
    fun addAccount(context: Context) {
        val host = SoundNestRuntime.host
        if (!host.multiProfile) { logout(context); return }
        Logbook.log("account: adding an account")
        leaveAccount(context.applicationContext, forget = false)
        _signedOut.value = true
        host.addProfile()
    }

    /**
     * Back on the sign-in page of an added account (or of one whose session was gone): the account
     * used last takes over again. False when there is none to go back to.
     */
    fun backToPreviousAccount(context: Context): Boolean {
        val host = SoundNestRuntime.host
        if (!host.multiProfile) return false
        val list = host.accounts.list.value
        val previous = list.others.firstOrNull() ?: return false
        val left = list.current
        switchAccount(context, previous.profile)
        // An added profile nobody signed in to is not kept.
        if (left != null && !left.known) host.removeProfile(left.profile)
        return true
    }

    /**
     * [profile] is removed from the phone: its sign-in and Web data are deleted (its profile), and
     * its listen history too. The active account is first left for the one used last; the only
     * account left is signed out instead.
     */
    fun removeAccount(context: Context, profile: String) {
        val host = SoundNestRuntime.host
        val list = host.accounts.list.value
        val gone = list.accounts.firstOrNull { it.profile == profile } ?: return
        if (profile == list.active) {
            val next = list.others.firstOrNull()
            if (!host.multiProfile || next == null) {
                leaveAccount(context.applicationContext, forget = true)
                _signedOut.value = true
                host.logout()
                return
            }
            Logbook.log("account: removing the active account, the one used last takes over")
            leaveAccount(context.applicationContext, forget = true)
            _switching.value = true
            host.switchProfile(next.profile)
        } else {
            Logbook.log("account: removing an account kept on the phone")
            gone.key?.let { ListenHistory.forget(it) }
        }
        host.removeProfile(profile)
    }

    /**
     * The Spotify session was lost without a sign-out (an anonymous token, Spotify's sign-in page
     * back): whose account comes next is not known, maybe another one. What follows the account
     * forgets it as a sign-out does (the profile, so ProfileCache.id is read again as soon as the
     * next session is ready; the likes kept for later stay their account's, sent to it only).
     */
    fun sessionLost() {
        Logbook.log("session lost without a sign-out: the account is read again at the next session")
        ProfileCache.clearSession()
        LibraryOutbox.signedOut()
    }

    /** The new account is signed in. */
    fun signedIn() { _signedOut.value = false; _switching.value = false }
}
