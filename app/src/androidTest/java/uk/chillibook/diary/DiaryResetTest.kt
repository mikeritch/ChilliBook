package uk.chillibook.diary

import android.content.Context
import android.view.KeyEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiaryResetTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun onlyFinalConfirmationErasesTheSyntheticDiaryAndResetsPreferences() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(context.packageName.endsWith(".debug"))
        val date = "1999-04-02"
        val fixture = Diary(days = listOf(Day(date, work = false)),
            checks = listOf(Check(date, "am", energy = 1)),
            activities = listOf(ActivityEntry(date = date, tags = setOf("Hobby"))),
            prefs = Prefs(theme = "dark", pins = emptySet(), eveningReminder = 1200))
        val delivery = context.getSharedPreferences("reminder_delivery", Context.MODE_PRIVATE)
        Storage(context).use { storage ->
            assumeTrue(runBlocking { storage.read() } == Diary() && delivery.all.isEmpty())
            try {
                runBlocking { storage.replace(fixture) }
                delivery.edit().putString("last_pm", date).commit()
                ActivityScenario.launch(MainActivity::class.java).use {
                    compose.onNodeWithContentDescription("Settings").performClick()
                    val reset = compose.onNodeWithText("Reset all diary data")
                    compose.waitUntil(10_000) {
                        compose.onAllNodesWithText("Reset all diary data").fetchSemanticsNodes().isNotEmpty()
                    }
                    reset.performScrollTo().performClick()
                    compose.onNodeWithText("Cancel").performClick()
                    assertEquals(fixture, runBlocking { storage.read() })
                    reset.performClick()
                    compose.onNodeWithText("Continue").performClick()
                    instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                    compose.onNodeWithText("Permanently erase diary").assertDoesNotExist()
                    assertEquals(fixture, runBlocking { storage.read() })
                    reset.performScrollTo().performClick()
                    compose.onNodeWithText("Continue").performClick()
                    compose.onNodeWithText("Permanently erase diary").performClick()
                    compose.waitUntil(10_000) {
                        compose.onAllNodesWithText("Diary reset").fetchSemanticsNodes().isNotEmpty()
                    }
                    assertTrue(delivery.all.isEmpty())
                    compose.onNodeWithText("Today").performClick()
                    compose.onNodeWithText("Undo").assertDoesNotExist()
                }
                Storage(context).use { reopened -> assertEquals(Diary(), runBlocking { reopened.read() }) }
            } finally {
                runBlocking { storage.replace(Diary()) }
                Reminders.clear(context)
            }
        }
    }
}
