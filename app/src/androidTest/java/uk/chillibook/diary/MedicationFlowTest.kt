package uk.chillibook.diary

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class MedicationFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun tap(label: String) = compose.onNodeWithText(label).performScrollTo().assertIsDisplayed().performClick()

    private fun picture(name: String) {
        compose.waitForIdle()
        instrumentation.uiAutomation.waitForIdle(150, 2000)
        val image = instrumentation.uiAutomation.takeScreenshot()
        try {
            File(context.filesDir, "medication-$name.png").outputStream().use {
                check(image.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally {
            image.recycle()
        }
    }

    @Test fun logCorrectAndCompareMedication() {
        assumeTrue(context.packageName.endsWith(".debug"))
        val large = context.resources.configuration.fontScale >= 1.98f
        val today = LocalDate.now()
        val start = today.minusDays(4).toString()
        val scheduled = MedicationCourse(name = "Daily tablet", start = start)
        val prn = MedicationCourse(name = "Occasional tablet", start = start)
        val past = MedicationCourse(name = "Past course", start = today.minusDays(14).toString(),
            ended = true, end = today.minusDays(7).toString())
        val regimen = MedicationRegimen(courseId = scheduled.id, effective = start, strength = "5 mg",
            amount = "1", unit = "tablet", schedule = "Morning")
        val asNeeded = MedicationRegimen(courseId = prn.id, effective = start, schedule = "As needed")

        Storage(context).use { store ->
            val initial = runBlocking { store.read() }
            assumeTrue(initial.dates().isEmpty() && initial.courses.isEmpty() && initial.regimens.isEmpty() && initial.doses.isEmpty())
            fun stored(condition: (Diary) -> Boolean) = compose.waitUntil(10_000) {
                condition(runBlocking { store.read() })
            }
            try {
                val earlierChecks = (1L..14L).map { offset ->
                    Check(today.minusDays(offset).toString(), "pm", energy = if (offset in 7L..11L) 1 else 3)
                }
                runBlocking { store.replace(initial.copy(checks = earlierChecks, courses = listOf(scheduled, prn, past), regimens = listOf(regimen, asNeeded))) }
                ActivityScenario.launch(MainActivity::class.java).use {
                    compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Morning check-in").fetchSemanticsNodes().isNotEmpty() }
                    for (slot in listOf("Evening", "Morning")) {
                        compose.onNodeWithContentDescription("$slot check-in").performScrollTo().performClick()
                        val symptoms = compose.onNodeWithText("Symptoms").getUnclippedBoundsInRoot()
                        val medications = compose.onNodeWithText("Medications").getUnclippedBoundsInRoot()
                        assertTrue("$slot symptoms should precede medications", symptoms.bottom <= medications.top)
                    }
                    val taken = compose.onNodeWithContentDescription("Daily tablet, Taken")
                    val skipped = compose.onNodeWithContentDescription("Daily tablet, Skipped")
                    val assumed = compose.onNodeWithText("Assumed taken", substring = true)
                    assumed.assertExists()
                    taken.performScrollTo().assertIsSelected()
                    skipped.assertIsNotSelected()
                    assertTrue(runBlocking { store.read() }.doses.isEmpty())
                    compose.onNodeWithContentDescription("Occasional tablet, Taken").assertDoesNotExist()
                    taken.performClick().assertIsSelected()
                    assumed.assertDoesNotExist()
                    stored { d -> d.doses.singleOrNull()?.status == "Taken" && d.days.isEmpty() && d.checks.none { it.date == today.toString() } && d.activities.isEmpty() }
                    val firstId = runBlocking { store.read() }.doses.single().id
                    skipped.performScrollTo().performClick().assertIsSelected()
                    stored { d -> d.doses.singleOrNull()?.let { it.id == firstId && it.status == "Skipped" } == true }
                    skipped.performClick().assertIsNotSelected()
                    stored { d -> d.doses.isEmpty() }
                    taken.assertIsSelected()
                    assumed.assertExists()
                    compose.onNodeWithText("Medications").performScrollTo().assertIsDisplayed()
                    compose.onNodeWithText("Manage medications").assertIsDisplayed()
                    taken.assertIsDisplayed()
                    picture(if (large) "today-large" else "today")

                    repeat(2) {
                        tap("+  Add as-needed dose")
                        val expected = it + 1
                        stored { d -> d.doses.size == expected }
                    }
                    val occurrences = runBlocking { store.read() }.doses
                    assertEquals(2, occurrences.map { it.id }.distinct().size)
                    assertTrue(occurrences.all { it.courseId == prn.id && it.asNeeded && it.date == today.toString() && it.slot == "am" })
                    compose.onNodeWithText("Dose 2", substring = true).performScrollTo().assertIsDisplayed()
                    tap("Manage medications")
                    if (!large) {
                        tap("Add medication")
                        compose.onNodeWithText("Medication name").performTextInput("Trial course")
                        tap("Save")
                        stored { d -> d.courses.any { it.name == "Trial course" && it.start == null } }
                        tap("Record change")
                        tap("Morning")
                        tap("Save")
                        val courseId = runBlocking { store.read() }.courses.single { it.name == "Trial course" }.id
                        stored { d -> d.regimens.singleOrNull { it.courseId == courseId }?.effective == today.toString() }
                        tap("Record stop date")
                        tap("Save")
                        stored { d -> d.courses.single { it.id == courseId }.end == today.toString() }
                        tap("Restart medication")
                        tap("Save")
                        stored { d -> d.courses.count { it.name == "Trial course" } == 2 }
                    }
                    compose.onNodeWithText("Daily tablet").assertExists()
                    tap("Past")
                    compose.onNodeWithText("Daily tablet").assertDoesNotExist()
                    compose.onNodeWithText("Past course").assertExists()
                    compose.onAllNodesWithText("Show details")[0].performScrollTo().performClick()
                    if (!large) picture("history")
                    tap("View in Patterns")
                    compose.onNodeWithText("Week ending on the last day").performScrollTo().assertIsDisplayed()
                    if (!large) picture("patterns")
                }
            } finally {
                runBlocking { store.replace(initial) }
            }
        }
    }
}
