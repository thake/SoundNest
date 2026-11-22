/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.parental.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.thake.soundnest.core.preferences.PolicySyncedSetting
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ParentalPolicyRegistrationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ParentalPolicyStore.clearRegistrationsForTests()
    }

    @After
    fun tearDown() {
        ParentalPolicyStore.clearRegistrationsForTests()
    }

    @Test
    fun `registered PolicySyncedSetting is invoked once per replace even if registered twice`() {
        var calls = 0
        var lastPolicy: ParentalPolicy? = null
        val fake = object : PolicySyncedSetting {
            override fun enforcePolicy(context: Context, policy: ParentalPolicy) {
                calls++
                lastPolicy = policy
            }
        }

        ParentalPolicyStore.registerPolicySynced(fake)
        ParentalPolicyStore.registerPolicySynced(fake)

        val updated = ParentalPolicy(blockExplicit = false)
        ParentalPolicyStore.replace(context, updated)

        assertEquals(1, calls)
        assertEquals(updated, lastPolicy)
    }

    @Test
    fun `registered cache invalidator runs on replace`() {
        var invalidated = 0
        val invalidator: () -> Unit = { invalidated++ }

        ParentalPolicyStore.registerCacheInvalidator(invalidator)
        ParentalPolicyStore.registerCacheInvalidator(invalidator)

        ParentalPolicyStore.replace(context, ParentalPolicy(blockExplicit = true))

        assertEquals(1, invalidated)
    }
}
