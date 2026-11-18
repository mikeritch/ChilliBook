package uk.chillibook.diary

import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@RunWith(AndroidJUnit4::class)
class PatternsFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val today = LocalDate.now()
    private val anchor = today.minusDays(2)
    private val gap = today.minusDays(1)
    private val shortDate = DateTimeFormatter.ofPattern("d MMM")
    private val longDate = DateTimeFormatter.ofPattern("EEE d MMM yyyy")
    private val monthLabel = DateTimeFormatter.ofPattern("MMMM yyyy")
    private val fixture = Diary(
        days = listOf(Day(today.minusYears(1).toString(), work = false),
            Day(anchor.toString(), work = true, wellbeing = 3)),
        checks = listOf(Check(anchor.toString(), "pm", energy = 2),
            Check(today.toString(), "am", energy = 1)),
        activities = listOf(ActivityEntry(date = anchor.toString(), tags = setOf("Call"), worth = "Yes")),
        episodes = listOf(Episode(start = gap.toString()))
    )

    private fun tap(text: String) = compose.onNodeWithText(text).performScrollTo().performClick()
    private val dialog = hasAnyAncestor(isDialog())
    private fun inDialog(text: String, substring: Boolean = false) =
        compose.onNode(dialog and hasText(text, substring = substring))

    @Test fun activityAndSymptomDatesFollowTheSelectedRange() {
        var opened = ""
        val data = fixture.copy(checks = fixture.checks + Check(gap.toString(), "am",
            symptoms = mapOf("Brain fog" to 0)))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent { DiaryTheme(false) {
                    Surface { PatternsScreen(data, onDate = { opened = it }, onToday = {}) }
                } }
            }
            tap("7 days")
            compose.onNodeWithText("Yes · 1 of 7 days").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Unanswered · 6 of 7 days").assertExists()
            tap("Activity dates   ›")
            inDialog(anchor.format(longDate)).performClick()
            compose.runOnIdle { assertEquals(anchor.toString(), opened) }
            inDialog("Close").performClick()
            compose.onNodeWithText("Morning · 1 of 7 days").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Morning · 6 of 7 days").assertExists()
            tap("Symptom rating dates   ›")
            inDialog("Morning · None · 1").assertExists()
            compose.onNode(dialog and hasText(gap.format(longDate)) and hasText("Morning · None")).performClick()
            compose.runOnIdle { assertEquals(gap.toString(), opened) }
        }
    }

    @Test fun followsCalendarDatesAndDrillsThroughGapsCountsAndMonths() {
        var opened = ""
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent { DiaryTheme(false) {
                    Surface { PatternsScreen(fixture, onDate = { opened = it }, onToday = {}) }
                } }
            }
            compose.onNodeWithContentDescription("Work, 0 of 0, 1 missing").assertExists()
            tap("Next morning’s energy")
            compose.onNodeWithText("Two mornings later").performClick()
            compose.onNodeWithContentDescription("Work, 1 of 1, 0 missing").assertExists()
            tap("All levels and dates   ›")
            inDialog("1 usable · 0 missing").assertExists()
            inDialog("Very low 0 · Low 1 · Medium 0 · Fairly high 0 · High 0").assertExists()
            inDialog("Work · ${today.format(shortDate)} · Low").performClick()
            compose.runOnIdle { assertEquals(anchor.toString(), opened) }
            inDialog("Close").performClick()
            tap("Look up an activity or symptom")
            compose.onNodeWithText("Call").performClick()
            inDialog("Next morning · No entry\nTwo mornings later · Low\nWorthwhile · Yes", true).assertExists()
            inDialog("Close").performClick()
            tap("Timeline as text")
            val gapRow = dialog and hasText(gap.format(longDate)) and hasText("Morning · No entry\nEvening · No entry", substring = true)
            compose.onNode(gapRow).assertExists()
            inDialog("Close").performClick()
            tap("90 days")
            val month = YearMonth.from(today).format(monthLabel)
            compose.onNodeWithText(month).performScrollTo().performClick()
            compose.onNodeWithText("‹ Months   ·   $month").assertExists()
            tap("Timeline as text")
            inDialog(today.format(longDate)).assertExists()
        }
    }
}
