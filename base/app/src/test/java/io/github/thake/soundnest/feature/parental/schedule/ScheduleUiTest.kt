/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.parental.schedule

import android.app.Application
import android.content.Context
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import io.github.thake.soundnest.feature.parental.remote.settings.DeviceRoleStore
import io.github.thake.soundnest.feature.parental.schedule.ui.ParentScheduleSettingsPage
import io.github.thake.soundnest.feature.parental.schedule.ui.UsageHoursLockOverlay
import io.github.thake.soundnest.feature.parental.security.ParentSecurityManager
import io.github.thake.soundnest.feature.parental.settings.ParentalPolicyStore
import io.github.thake.soundnest.feature.parental.settings.rememberParentalPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ScheduleUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        DeviceRoleStore.resetForTests(context)
        ParentalScheduleController.resetForTests()
        ParentSecurityManager.resetForTests(context)
        ParentalPolicyStore.resetForTests(context)
    }

    @After
    fun tearDown() {
        ParentalScheduleController.resetForTests()
    }

    @Test
    fun lockOverlayDisplaysNextAllowedTimeAndRequestsParentPin() {
        ParentSecurityManager.setupInitialPin(context, "1234")
        ParentSecurityManager.lockNow(context)
        ParentalScheduleController.nowZonedProvider = {
            ZonedDateTime.of(2026, 11, 20, 21, 0, 0, 0, ZoneId.of("Europe/Berlin"))
        }
        ParentalPolicyStore.update(context) {
            it.copy(schedule = ScheduleParentalPolicy(usageHoursEnabled = true))
        }
        ParentalScheduleController.evaluateNow(context)

        composeRule.setContent { UsageHoursLockOverlay() }

        composeRule.onNodeWithTag("usage_hours_lock_overlay").assertIsDisplayed()
        composeRule.onNodeWithTag("usage_hours_lock_next_time").assertIsDisplayed()
        composeRule.onNodeWithTag("usage_hours_parent_unlock_btn").performClick()

        assertNotNull("Clicking Parent Unlock must trigger Parent PIN prompt", ParentSecurityManager.pendingPrompt.value)
    }

    @Test
    fun scheduleSettingsPagePersistsHoursAndBedtimeInactivityPreset() {
        ParentSecurityManager.setupInitialPin(context, "1234")

        composeRule.setContent {
            val policy = rememberParentalPolicy()
            ParentScheduleSettingsPage(
                context = context,
                policy = policy,
                listState = rememberLazyListState(),
                back = {},
                lockAndExit = {},
            )
        }

        // Toggle Allowed Listening Hours on
        composeRule.onNodeWithTag("schedule_usage_hours_toggle").performClick()
        assertTrue(ParentalPolicyStore.current(context).schedule.usageHoursEnabled)

        // Scroll to Bedtime Inactivity toggle, enable it, and select 30 min preset
        composeRule.onNodeWithTag("parent_schedule_list")
            .performScrollToNode(hasTestTag("schedule_bedtime_toggle"))
        composeRule.onNodeWithTag("schedule_bedtime_toggle").performClick()
        assertTrue(ParentalPolicyStore.current(context).schedule.bedtimeInactivityEnabled)

        composeRule.onNodeWithTag("parent_schedule_list")
            .performScrollToNode(hasTestTag("schedule_inactivity_30m"))
        composeRule.onNodeWithTag("schedule_inactivity_30m").performClick()
        assertEquals(30, ParentalPolicyStore.current(context).schedule.inactivityTimeoutMinutes)
    }
}
