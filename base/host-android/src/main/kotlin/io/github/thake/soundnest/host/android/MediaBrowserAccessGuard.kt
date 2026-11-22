/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.host.android

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build

/**
 * Access-control guard for [PlaybackService]'s exported `MediaBrowserServiceCompat` surface.
 *
 * Ensures that only trusted system/auto/companion packages (or Play-Store-installed allowlisted
 * third-party browsers such as Waze) can browse the user's library or run search queries, and
 * validates that the claimed package name actually belongs to the caller's UID.
 */
class MediaBrowserAccessGuard(
    private val selfPackageName: String,
    private val packagesForUid: (Int) -> List<String>,
    private val installerOf: (String) -> String?,
    private val isSystemApp: (String) -> Boolean,
) {
    fun isCallerUidValid(clientPackageName: String, clientUid: Int): Boolean {
        if (clientPackageName == selfPackageName) return true
        return clientPackageName in packagesForUid(clientUid)
    }

    /**
     * The app itself, the listed browsers, and system apps may browse the library. A listed
     * third-party app ([STORE_BROWSERS]) must come from Google Play: its package name alone is
     * free for any sideloaded app to take when the real one is not installed.
     */
    fun mayBrowse(clientPackageName: String): Boolean {
        if (clientPackageName == selfPackageName || clientPackageName in ALLOWED_BROWSERS) return true
        if (clientPackageName in STORE_BROWSERS) return installerOf(clientPackageName) == PLAY_STORE
        return isSystemApp(clientPackageName)
    }

    fun canCallerBrowse(clientPackageName: String, clientUid: Int): Boolean =
        isCallerUidValid(clientPackageName, clientUid) && mayBrowse(clientPackageName)

    companion object {
        /** Packages allowed to browse the library. */
        val ALLOWED_BROWSERS: Set<String> = setOf(
            "com.google.android.projection.gearhead", // Android Auto (phone)
            "com.google.android.apps.automotive.templates.host", // Android Automotive OS
            "com.google.android.googlequicksearchbox", // Google Assistant
            "com.google.android.carassistant",
            "com.android.systemui",
            "com.android.bluetooth", // car stereos browsing over Bluetooth AVRCP
            "com.android.wearable.app", // Wear OS
            "com.android.car.media", // Android Automotive media center
        )

        /** Third-party apps allowed to browse when Google Play installed them. */
        val STORE_BROWSERS: Set<String> = setOf(
            "com.waze", // Waze's audio player: its "Show list" lists the playlists (#177)
        )

        const val PLAY_STORE: String = "com.android.vending"

        fun fromPackageManager(selfPackageName: String, pm: PackageManager): MediaBrowserAccessGuard =
            MediaBrowserAccessGuard(
                selfPackageName = selfPackageName,
                packagesForUid = { uid -> runCatching { pm.getPackagesForUid(uid)?.toList() }.getOrNull().orEmpty() },
                installerOf = { pkg ->
                    runCatching {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            pm.getInstallSourceInfo(pkg).installingPackageName
                        } else {
                            @Suppress("DEPRECATION")
                            pm.getInstallerPackageName(pkg)
                        }
                    }.getOrNull()
                },
                isSystemApp = { pkg ->
                    val flags = runCatching { pm.getApplicationInfo(pkg, 0).flags }.getOrDefault(0)
                    (flags and ApplicationInfo.FLAG_SYSTEM) != 0
                },
            )
    }
}
