package com.dusk.app

import android.graphics.Bitmap
import android.view.WindowManager
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dusk.app.data.JournalStore
import com.dusk.app.data.ThemeMode
import com.dusk.app.security.AppLock
import com.dusk.app.security.SecureScreens
import com.dusk.core.backup.BackupException
import com.dusk.core.journal.DayEntry
import com.dusk.core.journal.journalDay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class DuskTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = ctx.applicationContext as DuskApp
    private val today get() = journalDay(LocalDateTime.now())

    @Before
    fun cleanSlate() {
        AppLock.autoPrompt = false
        SecureScreens.allowCaptureInTests = false
        runBlocking {
            withTimeout(10_000) { app.journal.loaded.first { it } }
            app.journal.deleteEverything()
        }
        rule.runOnUiThread { app.prefs.setTheme(ThemeMode.LIGHT); app.prefs.setLock(true) }
        rule.waitForIdle()
        // The emulator has no screen lock, so the app opens straight away. Wait for the home screen.
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTag("journal-button").fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodesWithTag("lock-screen").fetchSemanticsNodes().isNotEmpty()
        }
        if (rule.onAllNodesWithTag("lock-screen").fetchSemanticsNodes().isNotEmpty()) rule.runOnUiThread { AppLock.unlockWithoutPrompt() }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("journal-button").fetchSemanticsNodes().isNotEmpty() }
    }

    @After
    fun tidy() {
        SecureScreens.allowCaptureInTests = false
        runBlocking { app.journal.deleteEverything() }
    }

    private fun shot(name: String) {
        rule.waitForIdle()
        val dialogs = rule.onAllNodes(isDialog()).fetchSemanticsNodes()
        val node = if (dialogs.isNotEmpty()) rule.onAllNodes(isDialog())[0] else rule.onRoot(useUnmergedTree = true)
        val bitmap = try {
            node.captureToImage().asAndroidBitmap()
        } catch (e: Throwable) {
            rule.onRoot(useUnmergedTree = true).captureToImage().asAndroidBitmap()
        }
        val dir = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let { File(it) }
            ?: ctx.getExternalFilesDir("screens")!!
        dir.mkdirs()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun secureFlagOn(): Boolean {
        var on = false
        rule.runOnUiThread { on = rule.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0 }
        return on
    }

    private fun shell(cmd: String): String {
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(cmd)
        val out = android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().readText()
        Thread.sleep(1000)
        return out
    }

    private fun seed(date: LocalDate, text: String, mood: Int? = null, spend: Long? = null) {
        val now = System.currentTimeMillis()
        rule.runOnUiThread { app.journal.put(DayEntry(date, text = text, mood = mood, energy = 3, progress = 4, spend = spend, createdAt = now, updatedAt = now)) }
        runBlocking { app.journal.flush() }
    }

    private fun freshStore(): JournalStore {
        val s = JournalStore(ctx)
        s.load()
        runBlocking { withTimeout(10_000) { s.loaded.first { it } } }
        return s
    }

    @Test
    fun a_journalScreensBlockScreenshots() {
        assertFalse("home must allow screenshots", secureFlagOn())
        rule.onNodeWithTag("journal-button").performClick()
        rule.onNodeWithTag("journal-field").assertIsDisplayed()
        assertTrue("journal must block screenshots", secureFlagOn())
        rule.onNodeWithTag("journal-back").performClick()
        rule.onNodeWithTag("journal-button").assertIsDisplayed()
        assertFalse("home must allow screenshots again", secureFlagOn())
    }

    @Test
    fun b_speakSaveAndReopen() {
        SecureScreens.allowCaptureInTests = true
        rule.onNodeWithTag("today-panel").assertTextContains("not written yet", substring = true)
        shot("01-home-empty")

        rule.onNodeWithTag("journal-button").performClick()
        shot("02-journal-empty")
        rule.onNodeWithTag("journal-field").performTextInput("Aaj subah late utha, phir gym gaya. Terumo call went well and I felt calm.")
        rule.onNodeWithTag("mood-4").performScrollTo().performClick()
        rule.onNodeWithTag("energy-3").performScrollTo().performClick()
        rule.onNodeWithTag("progress-5").performScrollTo().performClick()
        rule.onNodeWithTag("spend").performScrollTo().performTextInput("450")
        rule.waitUntil(5_000) { app.journal.get(today)?.spend == 450L }
        // The draft autosaves within about a second, before Done.
        rule.waitUntil(5_000) { app.journal.get(today)?.draft?.contains("Terumo") == true }
        rule.onNodeWithTag("journal-field").performScrollTo()
        shot("03-journal-written")
        rule.onNodeWithTag("journal-done").performClick()

        val saved = app.journal.get(today)!!
        assertTrue(saved.text.startsWith("Aaj subah late utha"))
        assertEquals("", saved.draft)
        assertEquals(listOf(4, 3, 5), listOf(saved.mood, saved.energy, saved.progress))
        rule.onNodeWithTag("today-panel").assertTextContains("15 words", substring = true)
        shot("04-home-written")

        // Open the day from the calendar, then add a second session: it must get a time stamp.
        rule.onNodeWithTag("day-$today").performClick()
        rule.onNodeWithTag("sheet-preview").assertIsDisplayed()
        shot("05-day-sheet")
        rule.onNodeWithTag("sheet-journal").performClick()
        rule.onNodeWithTag("earlier-text").assertIsDisplayed()
        rule.onNodeWithTag("journal-field").performTextInput("Later: dinner with family.")
        shot("06-journal-second-session")
        rule.onNodeWithTag("journal-back").performClick()
        val text = app.journal.get(today)!!.text
        assertTrue("second session needs a time stamp: $text", Regex("\\n\\n\\[\\d{1,2}:\\d{2} [AP]M]\\nLater: dinner with family\\.").containsMatchIn(text))

        // Everything must be on disk and readable by a fresh copy of the store.
        runBlocking { app.journal.flush() }
        assertEquals(app.journal.get(today), freshStore().get(today))

        rule.onNodeWithTag("journal-button").performClick()
        rule.onNodeWithTag("open-prompts").performClick()
        rule.onNodeWithTag("prompts").assertIsDisplayed()
        shot("07-prompts")
    }

    @Test
    fun c_settingsBackupDialogAndDarkTheme() {
        SecureScreens.allowCaptureInTests = true
        seed(today, "One day of text so that backup is possible.", mood = 2)
        seed(today.minusDays(2), "An older day.", mood = 5)
        seed(today.minusDays(5), "Another one.")
        rule.onNodeWithTag("open-settings").performClick()
        rule.onNodeWithTag("internet-status").performScrollTo().assertTextContains("none", substring = true)
        rule.onNodeWithTag("backup-status").performScrollTo().assertTextContains("Never", substring = true)
        rule.onNodeWithTag("theme-LIGHT").performScrollTo()
        shot("08-settings")
        rule.onNodeWithTag("backup-now").performScrollTo().performClick()
        rule.onNodeWithTag("pass-1").performTextInput("correct horse")
        rule.onNodeWithTag("pass-2").performTextInput("correct horse")
        shot("09-backup-passphrase")
        rule.onNodeWithText("Cancel").performClick()

        rule.onNodeWithTag("theme-DARK").performScrollTo().performClick()
        shot("10-settings-dark")
        rule.onNodeWithTag("settings-back").performClick()
        shot("11-home-dark")
        rule.onNodeWithTag("day-$today").performClick()
        rule.onNodeWithTag("sheet-ratings").performClick()
        shot("12-day-sheet-dark")
        rule.onNodeWithTag("sheet-journal").performClick()
        shot("13-journal-dark")
        rule.onNodeWithTag("journal-back").performClick()
    }

    @Test
    fun d_restoreDrill() {
        seed(today, "Day one with \"quotes\", ₹ and Hinglish: kal subah 7 baje gym.", mood = 4, spend = 450)
        seed(today.minusDays(1), "Day two.", mood = 2)
        seed(today.minusDays(9), "Day three, a week earlier.")
        val before = app.journal.entries.value
        assertEquals(3, before.size)

        val file = File(ctx.cacheDir, "drill.duskbackup")
        val t0 = System.currentTimeMillis()
        val n = runBlocking { app.backup.write(file.outputStream(), "correct horse battery".toCharArray()) }
        val ms = System.currentTimeMillis() - t0
        println("DuskTest: backup of $n days took $ms ms on the emulator")
        assertEquals(3, n)
        assertTrue("backup took $ms ms", ms < 15_000)
        assertFalse("backup must not contain the text", file.readText().contains("Hinglish"))
        assertTrue(app.prefs.lastBackupAt != null)

        runBlocking { app.journal.deleteEverything() }
        assertTrue(app.journal.entries.value.isEmpty())
        assertTrue(freshStore().entries.value.isEmpty())

        val bytes = file.readBytes()
        try {
            runBlocking { app.backup.open(bytes, "wrong passphrase".toCharArray()) }
            fail("a wrong passphrase must be refused")
        } catch (e: BackupException) {
            assertEquals(BackupException.Reason.WRONG_PASSPHRASE, e.reason)
        }
        val contents = runBlocking { app.backup.open(bytes, "correct horse battery".toCharArray()) }
        runBlocking { app.backup.apply(contents, replace = false) }
        assertEquals("restored journal matches the original", before, app.journal.entries.value)
        assertEquals("restored files read back the same", before, freshStore().entries.value)
        file.delete()
    }

    @Test
    fun e_addingAndRemovingAPinKeepsTheJournalReadable() {
        seed(today, "Written before the phone had a PIN.")
        try {
            shell("locksettings set-pin 1234")
            rule.runOnUiThread { AppLock.lockNow() }
            rule.activityRule.scenario.recreate()
            rule.waitUntil(10_000) { rule.onAllNodesWithTag("lock-screen").fetchSemanticsNodes().isNotEmpty() }
            shot("14-lock-screen")
            assertEquals("journal opens after a PIN is added", "Written before the phone had a PIN.", freshStore().get(today)?.text)
            rule.runOnUiThread { AppLock.unlockWithoutPrompt() }
            rule.onNodeWithTag("journal-button").assertIsDisplayed()
        } finally {
            shell("locksettings clear --old 1234")
        }
        val after = freshStore()
        assertEquals("journal opens after the PIN is removed", "Written before the phone had a PIN.", after.get(today)?.text)
        assertEquals(0, after.unreadable.value)
    }

    @Test
    fun f_aDamagedDayIsSetAsideNotDeleted() {
        SecureScreens.allowCaptureInTests = true
        seed(today, "A good day.")
        val bad = File(ctx.filesDir, "journal/2026-01-01.day")
        bad.writeBytes(byteArrayOf(1, 12) + ByteArray(40) { 7 })
        val s = freshStore()
        assertEquals(1, s.unreadable.value)
        assertEquals("the good day still opens", "A good day.", s.get(today)?.text)
        assertFalse("the damaged file moved out of the way", bad.exists())
        assertTrue("the damaged file is kept", File(ctx.filesDir, "journal/unreadable").walkTopDown().any { it.name == "2026-01-01.day" })
        // The running app shows the banner after it reloads.
        app.journal.load()
        rule.waitUntil(10_000) { rule.onAllNodes(hasTestTag("unreadable-banner")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("unreadable-banner").performScrollTo()
        shot("15-unreadable-banner")
    }
}
