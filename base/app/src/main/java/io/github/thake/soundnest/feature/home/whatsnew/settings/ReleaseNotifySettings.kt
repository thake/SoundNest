/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.home.whatsnew.settings

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.thake.soundnest.R
import io.github.thake.soundnest.api.Album
import io.github.thake.soundnest.core.preferences.PolicySyncedSetting
import io.github.thake.soundnest.core.preferences.featurePrefs
import io.github.thake.soundnest.feature.accounts.settings.OnboardingSettings
import io.github.thake.soundnest.feature.parental.filter.HomeSectionFilter
import io.github.thake.soundnest.feature.parental.filter.ParentalFilterEngine
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicy
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore
import io.github.thake.soundnest.host.android.Logbook
import io.github.thake.soundnest.host.android.SoundNestRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * A notification when an artist the user follows releases something (asked on Reddit, Discord
 * and GitHub #125 #130): Spotify's own "What's New" feed is read while the app runs, at most
 * every [GAP_MS], and what it did not hold the last time is said, five at most, a tap opening
 * the release. The first read only remembers the feed: nothing old is announced.
 */
object NewReleaseNotifier : PolicySyncedSetting {
    private const val FEATURE_NAME = "whats_new"
    private const val KEY_ON = "on"
    private const val KEY_SEEN = "seen"
    private const val KEY_AT = "checked_at"
    private const val CHANNEL = "new_releases"
    private const val GROUP = "io.github.thake.soundnest.new_releases"
    private const val GAP_MS = 6 * 3_600_000L
    private const val MAX_SHOWN = 5
    private const val SEEN_KEPT = 300

    private fun prefs(context: Context) = context.featurePrefs(FEATURE_NAME)

    /** The notifications are wanted (a setting, on by default). */
    var on by mutableStateOf(true)
        private set

    fun setOn(context: Context, value: Boolean) {
        on = value
        prefs(context).edit().putBoolean(KEY_ON, value).apply()
    }

    override fun enforcePolicy(context: Context, policy: ParentalPolicy) {
        if (!HomeSectionFilter.isWhatsNewAllowed(policy)) {
            setOn(context, false)
        }
    }

    /** Started once with the app: waits for the session, then looks every hour whether a read is due. */
    fun start(context: Context) {
        val app = context.applicationContext
        on = prefs(app).getBoolean(KEY_ON, true)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            SoundNestRuntime.api.ready.first { it }
            while (true) {
                if (on && signedIn(app)) runCatching { checkIfDue(app) }.onFailure { Logbook.log("new releases: not read (${it.javaClass.simpleName})") }
                delay(3_600_000L)
            }
        }
    }

    private fun signedIn(context: Context) = OnboardingSettings.isLoggedIn(context)

    internal fun filterCandidateReleases(
        releases: List<Album>,
        policy: ParentalPolicy,
        whatsNewEnabled: Boolean = true,
    ): List<Album> {
        if (!whatsNewEnabled) return emptyList()
        return releases.filter { a ->
            ParentalFilterEngine.isAlbumAllowed(
                albumId = a.uri.substringAfterLast(':'),
                name = a.name,
                artists = a.artists.joinToString(", ") { it.name },
                artistIds = a.artists.map { it.uri.substringAfterLast(':') },
                policy = policy,
            )
        }
    }

    private suspend fun checkIfDue(context: Context) {
        val p = prefs(context)
        val now = System.currentTimeMillis()
        if (now - p.getLong(KEY_AT, 0L) in 0 until GAP_MS) return
        val policy = ParentalPolicyStore.current(context)
        val whatsNewAllowed = HomeSectionFilter.isWhatsNewAllowed(policy)
        if (!whatsNewAllowed) return
        val feed = SoundNestRuntime.api.home.whatsNew(0, 30).items
        val seen = p.getString(KEY_SEEN, null)?.split('\n')?.filter { it.isNotBlank() }
        val fresh = if (seen == null) emptyList() else filterCandidateReleases(
            releases = feed.filter { it.uri !in seen },
            policy = policy,
            whatsNewEnabled = whatsNewAllowed,
        )
        p.edit()
            .putString(KEY_SEEN, (feed.map { it.uri } + seen.orEmpty()).distinct().take(SEEN_KEPT).joinToString("\n"))
            .putLong(KEY_AT, now).apply()
        Logbook.log("new releases: ${feed.size} in the feed, ${fresh.size} new${if (seen == null) " (first read, nothing said)" else ""}")
        if (fresh.isNotEmpty()) show(context, fresh.take(MAX_SHOWN))
    }

    private fun show(context: Context, releases: List<Album>) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val locContext = io.github.thake.soundnest.feature.appearance.settings.AppLanguage.localizedContext(context)
        if (manager.getNotificationChannel(CHANNEL) == null)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, locContext.getString(R.string.notify_releases_channel), NotificationManager.IMPORTANCE_DEFAULT))
        val compat = NotificationManagerCompat.from(context)
        releases.forEach { a ->
            val id = a.uri.substringAfterLast(':')
            val open = Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/album/$id")).setClassName(context, "io.github.thake.soundnest.MainActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val tap = PendingIntent.getActivity(context, id.hashCode(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val artist = a.artists.joinToString { it.name }
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.msp_ic_notification)
                .setContentTitle(locContext.getString(R.string.notify_release_title, artist))
                .setContentText(a.name)
                .setContentIntent(tap).setAutoCancel(true).setGroup(GROUP)
                .build()
            runCatching { compat.notify(id.hashCode(), n) }
        }
    }
}
