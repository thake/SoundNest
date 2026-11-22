/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.host.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaBrowserAccessGuardTest {

    private fun guard(
        selfPkg: String = "io.github.thake.soundnest",
        uidPackages: Map<Int, List<String>> = emptyMap(),
        installers: Map<String, String?> = emptyMap(),
        systemPackages: Set<String> = emptySet(),
    ) = MediaBrowserAccessGuard(
        selfPackageName = selfPkg,
        packagesForUid = { uid -> uidPackages[uid].orEmpty() },
        installerOf = { pkg -> installers[pkg] },
        isSystemApp = { pkg -> pkg in systemPackages },
    )

    @Test
    fun `allows self package and allowlisted browsers`() {
        val g = guard()
        assertTrue(g.mayBrowse("io.github.thake.soundnest"))
        assertTrue(g.mayBrowse("com.google.android.projection.gearhead"))
        assertTrue(g.mayBrowse("com.android.bluetooth"))
    }

    @Test
    fun `store browser requires Google Play installer`() {
        val fromPlay = guard(installers = mapOf("com.waze" to MediaBrowserAccessGuard.PLAY_STORE))
        val sideloaded = guard(installers = mapOf("com.waze" to "com.evil.installer"))
        val unknown = guard()

        assertTrue(fromPlay.mayBrowse("com.waze"))
        assertFalse(sideloaded.mayBrowse("com.waze"))
        assertFalse(unknown.mayBrowse("com.waze"))
    }

    @Test
    fun `allows system apps and rejects unknown third-party packages`() {
        val g = guard(systemPackages = setOf("com.oem.car.media"))
        assertTrue(g.mayBrowse("com.oem.car.media"))
        assertFalse(g.mayBrowse("com.random.scraper"))
    }

    @Test
    fun `isCallerUidValid rejects spoofed package name not belonging to uid`() {
        val g = guard(uidPackages = mapOf(10100 to listOf("com.random.app")))
        assertTrue(g.isCallerUidValid("io.github.thake.soundnest", 99999))
        assertTrue(g.isCallerUidValid("com.random.app", 10100))
        assertFalse(g.isCallerUidValid("com.google.android.projection.gearhead", 10100))
    }

    @Test
    fun `canCallerBrowse requires both valid uid ownership and browse permission`() {
        val g = guard(
            uidPackages = mapOf(
                1000 to listOf("com.google.android.projection.gearhead"),
                2000 to listOf("com.random.app"),
            ),
        )
        assertTrue(g.canCallerBrowse("com.google.android.projection.gearhead", 1000))
        assertFalse(g.canCallerBrowse("com.google.android.projection.gearhead", 2000))
        assertFalse(g.canCallerBrowse("com.random.app", 2000))
    }
}
