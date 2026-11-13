package com.dusk.lab

import android.Manifest
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.dusk.lab.data.LabLog
import com.dusk.lab.reminders.ReminderScheduler
import com.dusk.lab.reminders.ReminderStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File

/** Taps through every screen on an emulator, saves a screenshot of each, and waits for a real reminder. */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class LabTapThroughTest {

    @get:Rule(order = 0)
    val permissions: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.RECORD_AUDIO)

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun cleanSlate() {
        ReminderScheduler.clear(ctx)
        LabLog.clear(ctx)
    }

    private fun shot(name: String) {
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let { File(it) }
            ?: ctx.getExternalFilesDir("screens")!!
        dir.mkdirs()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun shell(cmd: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(cmd).close()
        Thread.sleep(1500)
    }

    @Test
    fun a_everyScreenOpensAndAPlanBecomesACard() {
        rule.onNodeWithText("Speech test").assertIsDisplayed()
        shot("1-speech-keyboard")
        rule.onNodeWithTag("engine-1").performClick()
        shot("2-speech-recogniser")

        rule.onNodeWithTag("tab-1").performClick()
        rule.onNodeWithText("Reminder test").assertIsDisplayed()
        shot("3-reminders")

        rule.onNodeWithTag("tab-2").performClick()
        rule.onNodeWithTag("plan-input").performTextInput("dinner with friends on 20th Nov")
        rule.onNodeWithTag("plan-make").performClick()
        rule.onNodeWithText("Dinner with friends").assertIsDisplayed()
        shot("4-plan-card-inferred-time")
        rule.onNodeWithTag("plan-ok").performScrollTo().performClick()
        rule.onNodeWithTag("plan-count").assertTextContains("1 of 30 checked, 0 wrong")

        rule.onNodeWithTag("plan-input").performTextInput("next Friday team lunch at 1")
        rule.onNodeWithTag("plan-make").performClick()
        rule.onNodeWithText("Team lunch").assertIsDisplayed()
        shot("5-plan-card-ambiguous-date")
        rule.onNodeWithTag("plan-wrong").performScrollTo().performClick()
        rule.onNodeWithTag("plan-count").assertTextContains("2 of 30 checked, 1 wrong")

        rule.onNodeWithTag("tab-3").performClick()
        rule.onNodeWithText("DUSK LAB RESULTS", substring = true).assertExists()
        rule.onNodeWithText("2 checked, 1 wrong", substring = true).assertExists()
        shot("6-results")
    }

    @Test
    fun b_aReminderFiresWithinAMinuteOfItsTime() {
        rule.onNodeWithTag("tab-1").performClick()
        rule.onNodeWithTag("sched-1").performClick()
        rule.waitForIdle()
        assertEquals(2, ReminderStore.load(ctx).alarms.size)
        // One reminder is due in 1 minute (standard method), the other in 2 (alarm-clock method).
        rule.waitUntil(timeoutMillis = 200_000) {
            ReminderStore.load(ctx).alarms.all { it.firedAt != null }
        }
        ReminderStore.load(ctx).alarms.forEach { a ->
            val lateMs = a.firedAt!! - a.scheduledAt
            assertTrue("${a.api} reminder fired ${lateMs}ms from its time", lateMs in -1_000..60_000)
        }
        Thread.sleep(2500) // let the tab reload its log
        shot("7-reminders-fired")
    }

    @Test
    fun c_darkTheme() {
        shell("cmd uimode night yes")
        try {
            rule.activityRule.scenario.recreate()
            rule.onNodeWithText("Speech test").assertIsDisplayed()
            shot("8-dark-speech")
            rule.onNodeWithTag("tab-2").performClick()
            rule.onNodeWithTag("plan-input").performTextInput("Visiting Buldhana on 11th, return 12th")
            rule.onNodeWithTag("plan-make").performClick()
            rule.onNodeWithText("Visiting Buldhana").assertIsDisplayed()
            shot("9-dark-plan-card")
        } finally {
            shell("cmd uimode night no")
        }
    }
}
