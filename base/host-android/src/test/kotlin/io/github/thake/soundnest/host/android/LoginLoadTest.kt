package io.github.thake.soundnest.host.android

import android.app.Activity
import android.content.ContextWrapper
import android.content.MutableContextWrapper
import io.github.thake.soundnest.host.android.LoginLoad.Blank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class LoginLoadTest {

    @Test fun `spotify sign-in pages are login pages`() {
        assertTrue(LoginLoad.isLoginUrl("https://accounts.spotify.com/en/login?continue=https%3A%2F%2Fopen.spotify.com%2F"))
        assertTrue(LoginLoad.isLoginUrl("https://accounts.spotify.com/authorize?client_id=x"))
        assertTrue(LoginLoad.isLoginUrl("https://challenge.spotify.com/c/abc"))
        assertTrue(LoginLoad.isLoginUrl("https://open.spotify.com/login"))
        assertTrue(LoginLoad.isLoginUrl("https://www.spotify.com/es/login/"))
    }

    @Test fun `continue with google facebook apple stays mid sign-in`() {
        assertTrue(LoginLoad.isLoginUrl("https://accounts.google.com/o/oauth2/v2/auth?client_id=1"))
        assertTrue(LoginLoad.isLoginUrl("https://www.facebook.com/login.php?x=1"))
        assertTrue(LoginLoad.isLoginUrl("https://m.facebook.com/dialog/oauth"))
        assertTrue(LoginLoad.isLoginUrl("https://appleid.apple.com/auth/authorize"))
    }

    @Test fun `the player is not a login page, whatever its query says`() {
        assertFalse(LoginLoad.isLoginUrl("https://open.spotify.com/"))
        assertFalse(LoginLoad.isLoginUrl("https://open.spotify.com/intl-es/?flow_ctx=36e1%3A1792946057&ubi=CAIQ"))
        // The old check read the whole address and took these for sign-in pages.
        assertFalse(LoginLoad.isLoginUrl("https://open.spotify.com/intl-fr/?continue=https://accounts.spotify.com/login"))
        assertFalse(LoginLoad.isLoginUrl("https://open.spotify.com/?next=facebook.com"))
        assertFalse(LoginLoad.isLoginUrl("https://evilfacebook.com/"))
    }

    @Test fun `nothing and nonsense are not login pages`() {
        assertFalse(LoginLoad.isLoginUrl(null))
        assertFalse(LoginLoad.isLoginUrl(""))
        assertFalse(LoginLoad.isLoginUrl("about:blank"))
        assertFalse(LoginLoad.isLoginUrl("not a url at all"))
    }

    @Test fun `only accounts spotify is the sign-in's own host`() {
        assertTrue(LoginLoad.isSpotifyAccounts("https://accounts.spotify.com/en/login"))
        assertFalse(LoginLoad.isSpotifyAccounts("https://accounts.google.com/"))
        assertFalse(LoginLoad.isSpotifyAccounts("https://open.spotify.com/?u=accounts.spotify.com"))
        assertFalse(LoginLoad.isSpotifyAccounts(null))
    }

    @Test fun `the page's answer says whether it painted`() {
        assertTrue(LoginLoad.painted("42"))
        assertTrue(LoginLoad.painted("\"7\""))
        assertFalse(LoginLoad.painted("0"))
        assertFalse(LoginLoad.painted("null"))
        assertFalse(LoginLoad.painted(null))
        assertFalse(LoginLoad.painted(""))
    }

    @Test fun `a blank page is waited on, loaded again once, then given up`() {
        assertEquals(Blank.WAIT, LoginLoad.blank(1, reloaded = false))
        assertEquals(Blank.WAIT, LoginLoad.blank(5, reloaded = false))
        assertEquals(Blank.RELOAD, LoginLoad.blank(6, reloaded = false))
        // After the reload the count starts over: the second page gets its own wait, then the error.
        assertEquals(Blank.WAIT, LoginLoad.blank(1, reloaded = true))
        assertEquals(Blank.WAIT, LoginLoad.blank(9, reloaded = true))
        assertEquals(Blank.FAIL, LoginLoad.blank(10, reloaded = true))
    }

    @Test fun `the wait counts only the time the screen is in front`() {
        val w = SessionWait(readyBudgetMs = 6_000, maxMs = 60_000)
        w.tick(10_000, inFront = false, ready = true)
        assertEquals(0L, w.elapsedMs)
        assertFalse(w.expired)
    }

    @Test fun `ready time is the short budget, a player that is not ready gets the long one`() {
        val ready = SessionWait(6_000, 60_000)
        repeat(12) { ready.tick(500, inFront = true, ready = true) }
        assertTrue(ready.expired)
        assertFalse(ready.playerNeverReady)

        val booting = SessionWait(6_000, 60_000)
        repeat(119) { booting.tick(500, inFront = true, ready = false) }
        assertFalse(booting.expired)
        booting.tick(500, inFront = true, ready = false)
        assertTrue(booting.expired)
        // Out of time without the player ever being ready: not "signed out".
        assertTrue(booting.playerNeverReady)
        assertEquals(0L, booting.waitedMs)
    }

    @Test fun `a turn that waited on the page counts its real time`() {
        // Each turn asks the page for its token and waits the bridge's 20 s: three turns are a minute,
        // not three turns of half a second.
        val w = SessionWait(6_000, 60_000)
        repeat(3) { w.tick(20_500, inFront = true, ready = false) }
        assertTrue(w.expired)
    }

    @Test fun `a gap while the phone slept is not time waited`() {
        val w = SessionWait(6_000, 60_000)
        w.tick(5 * 60_000, inFront = true, ready = false)
        assertEquals(SessionWait.MAX_TURN_MS, w.elapsedMs)
        assertFalse(w.expired)
    }

    @Test fun `findActivity unwraps direct activity`() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        assertSame(activity, findActivity(activity))
    }

    @Test fun `findActivity unwraps nested ContextWrapper and MutableContextWrapper`() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val wrapped = ContextWrapper(MutableContextWrapper(ContextWrapper(activity)))
        assertSame(activity, findActivity(wrapped))
    }

    @Test fun `findActivity returns null for Application context and wrapped Application context`() {
        val app = RuntimeEnvironment.getApplication()
        assertNull(findActivity(app))
        assertNull(findActivity(ContextWrapper(app)))
        assertNull(findActivity(MutableContextWrapper(app)))
        assertNull(findActivity(null))
    }

    @Test fun `findActivity ignores finishing or destroyed activity`() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        controller.pause().stop().destroy()
        assertNull(findActivity(activity))
        assertNull(findActivity(ContextWrapper(activity)))
    }
}
