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
    )

    private fun tap(text: String) = compose.onNodeWithText(text).performScrollTo().performClick()
    private val dialog = hasAnyAncestor(isDialog())
    private fun inDialog(text: String, substring: Boolean = false) =
        compose.onNode(dialog and hasText(text, substring = substring))

    @Test fun activityAndSymptomDatesFollowTheSelectedRange() {
        var opened = ""
        val week = today.minusDays(13)
        val cooking = week.plusDays(6)
        val data = fixture.copy(
            days = fixture.days + Day(cooking.toString(), wellbeing = 3),
            activities = fixture.activities + ActivityEntry(date = cooking.toString(), tags = setOf("Cooking")),
            checks = fixture.checks + listOf(Check(cooking.plusDays(2).toString(), "am", energy = 1),
                Check(gap.toString(), "am", symptoms = mapOf("Brain fog" to 0)))
        )
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent { DiaryTheme(false) {
                    Surface { PatternsScreen(data, onDate = { opened = it }, onToday = {}) }
                } }
            }
            compose.onNodeWithContentDescription("Cooking, $week, 1 recorded day").performScrollTo().performClick()
            val entry = compose.onNodeWithContentDescription("Open diary entry for $cooking")
            entry.assert(hasText("Fairly well") and hasText("No entry") and hasText("Low"))
            entry.assert(hasText("Next morning’s energy · ${cooking.plusDays(1).format(shortDate)}") and
                hasText("Two mornings later · ${cooking.plusDays(2).format(shortDate)}"))
            entry.performScrollTo().performClick()
            compose.runOnIdle { assertEquals(cooking.toString(), opened) }
            tap("7 days")
            compose.onNodeWithText("Morning · 1 of 7 days").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Morning · 6 of 7 days").assertExists()
            tap("Symptom rating dates   ›")
            inDialog("Morning · None · 1").assertExists()
            compose.onNode(dialog and hasText(gap.format(longDate)) and hasText("Morning · None")).performClick()
            compose.runOnIdle { assertEquals(gap.toString(), opened) }
        }
    }

    @Test fun selectsHistoricalSymptomAndOpensAllTimeMonthlyPlot() {
        val historical = "Historical symptom"
        val older = today.minusMonths(4)
        val data = fixture.copy(checks = listOf(Check(older.toString(), "pm", symptoms = mapOf(historical to 3))))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent { DiaryTheme(false) {
                    Surface { PatternsScreen(data, onDate = {}, onToday = {}) }
                } }
            }
            tap("Energy")
            compose.onNode(dialog and hasScrollAction()).performScrollToNode(hasText(historical))
            inDialog(historical).performClick()
            compose.onNodeWithText("No historical symptom entries in this period.").assertExists()
            tap("All time")
            val month = YearMonth.from(older).format(monthLabel)
            compose.onNodeWithContentDescription("None 0 · Mild 0 · Moderate 0 · Strong 1", useUnmergedTree = true).assertExists()
            tap("$month   ›")
            tap("Timeline as text")
            compose.onNode(dialog and hasScrollAction()).performScrollToNode(hasText(older.format(longDate)))
            compose.onNode(dialog and hasText(older.format(longDate)) and hasText("Evening · Strong", substring = true))
                .assertExists()
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
            inDialog("Call").performClick()
            inDialog("Next morning · No entry\nTwo mornings later · Low\nWorthwhile · Yes", true).assertExists()
            inDialog("Close").performClick()
            tap("Timeline as text")
            val gapRow = dialog and hasText(gap.format(longDate)) and hasText("Morning · No entry\nEvening · No entry", substring = true)
            compose.onNode(gapRow).assertExists()
            inDialog("Close").performClick()
            tap("90 days")
            val month = YearMonth.from(today).format(monthLabel)
            compose.onNodeWithContentDescription("Energy timeline", substring = true).assertExists()
            tap("Explore a month")
            inDialog(month).performClick()
            compose.onNodeWithText("‹ Months   ·   $month").assertExists()
            tap("Timeline as text")
            inDialog(today.format(longDate)).assertExists()
        }
    }
}
