package uk.chillibook.diary

import android.view.KeyEvent
import android.view.ViewGroup
import android.view.View
import android.view.inspector.WindowInspector
import androidx.core.view.descendants
import androidx.test.filters.SdkSuppress
import android.widget.DatePicker
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.graphics.toPixelMap
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class TodayFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val today = LocalDate.now()
    private val yesterday = today.minusDays(1)
    private val energyQuestion = "How much energy do you have now?"

    private fun tap(text: String) = compose.onNodeWithText(text).performScrollTo().performClick()
    private fun choice(question: String, label: String) =
        compose.onNodeWithContentDescription("$question, $label")

    private fun select(question: String, label: String) {
        choice(question, label).performScrollTo().performClick()
    }

    private fun slot(morning: Boolean) {
        val label = if (morning) "Morning check-in" else "Evening check-in"
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription(label).performScrollTo().performClick()
    }

    private fun chooseYesterday() {
        val format = DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.getDefault())
        tap("${today.format(format)}   ⌄")
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val picker = WindowInspector.getGlobalWindowViews().asSequence()
                .filterIsInstance<ViewGroup>().flatMap { it.descendants }.filterIsInstance<DatePicker>().single()
            picker.updateDate(yesterday.year, yesterday.monthValue - 1, yesterday.dayOfMonth)
            picker.rootView.findViewById<View>(android.R.id.button1).performClick()
        }
    }

    private fun awaitStored(store: Storage, condition: (Diary) -> Boolean) {
        compose.waitUntil(10_000) { condition(runBlocking { store.read() }) }
    }

    @Test fun holdingAnAnswerChangesItsFillWithoutMovingOrSelectingIt() {
        assumeTrue(context.packageName.endsWith(".debug"))
        ActivityScenario.launch(MainActivity::class.java).use {
            slot(morning = true)
            val tile = choice(energyQuestion, "Medium").performScrollTo()
            val original = tile.fetchSemanticsNode().boundsInRoot
            val selected = tile.fetchSemanticsNode().config[SemanticsProperties.Selected]
            fun fill() = tile.captureToImage().toPixelMap().let { it[it.width / 2, it.height / 8] }
            val idleFill = fill()
            tile.performTouchInput { down(center) }
            try {
                compose.mainClock.advanceTimeBy(300)
                assertNotEquals(idleFill, fill())
                assertEquals(original, tile.fetchSemanticsNode().boundsInRoot)
                assertEquals(selected, tile.fetchSemanticsNode().config[SemanticsProperties.Selected])
            } finally {
                tile.performTouchInput { cancel() }
            }
            compose.mainClock.advanceTimeBy(500)
            compose.waitUntil(3_000) { fill() == idleFill }
        }
    }

    @Test fun symptomSheetStaysAtScrollBoundsAndReturnsToLastSymptom() {
        assumeTrue(context.packageName.endsWith(".debug"))
        Storage(context).use { store ->
            val initial = runBlocking { store.read() }
            assumeTrue(initial.dates().isEmpty())
            try {
                ActivityScenario.launch(MainActivity::class.java).use {
                    slot(morning = false)
                    tap("Symptoms & context")
                    val footer = compose.onNodeWithText("Done")
                    val body = compose.onNode(hasScrollAction() and hasAnyDescendant(hasText("Crash episodes")))
                    fun footerTop() = footer.fetchSemanticsNode().boundsInRoot.top
                    val initialTop = footerTop()
                    repeat(3) { body.performTouchInput { swipeDown() } }
                    assertEquals(initialTop, footerTop(), 1f)
                    compose.onNodeWithText("Crash episodes").performScrollTo()
                    repeat(3) { body.performTouchInput { swipeUp() } }
                    compose.onNodeWithText("Crash episodes").assertIsDisplayed()
                    assertEquals(initialTop, footerTop(), 1f)
                    val symptom = symptomLabels.last()
                    val row = compose.onNodeWithText(symptom)
                    row.performScrollTo()
                    val rowTop = row.fetchSemanticsNode().boundsInRoot.top
                    row.performClick()
                    select(symptom, "Mild")
                    choice(symptom, "Mild").assertIsSelected()
                    InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                    val rated = compose.onNodeWithText("$symptom · Mild").assertIsDisplayed()
                    assertEquals(rowTop, rated.fetchSemanticsNode().boundsInRoot.top, 1f)
                    footer.performClick()
                    awaitStored(store) { data -> data.checks.any { it.symptoms[symptom] == severityLabels.indexOf("Mild") } }
                }
            } finally {
                runBlocking { store.replace(initial) }
            }
        }
    }

    @Test fun activityToggleOpensDetailsAndUndoRestoresTheSameEntry() {
        assumeTrue(context.packageName.endsWith(".debug"))
        Storage(context).use { store ->
            val initial = runBlocking { store.read() }
            assumeTrue(initial.dates().isEmpty())
            try {
                ActivityScenario.launch(MainActivity::class.java).use {
                    slot(morning = false)
                    select("Household tasks", "Yes")
                    awaitStored(store) { it.days.singleOrNull()?.household == true && it.activities.isEmpty() }
                    fun toggle(action: String) =
                        compose.onNodeWithContentDescription(action).performScrollTo().performClick()
                    toggle("Select Cooking")
                    tap("Under 15 min")
                    awaitStored(store) { it.activities.singleOrNull()?.duration == "Under 15 min" }
                    val cooking = runBlocking { store.read() }.activities.single()
                    assertEquals(setOf("Cooking"), cooking.tags)
                    toggle("Remove Cooking")
                    awaitStored(store) { it.activities.isEmpty() && it.days.singleOrNull()?.household == true }
                    tap("Undo")
                    awaitStored(store) { it.activities == listOf(cooking) }
                    tap("Write your own")
                    compose.onNode(hasSetTextAction()).performScrollTo().performTextInput("Gardening")
                    compose.onNode(hasSetTextAction()).assertTextContains("Gardening")
                    compose.onNodeWithText("Add activity").assertIsEnabled().performSemanticsAction(SemanticsActions.OnClick)
                    awaitStored(store) { diary -> diary.activities.any { it.tags == setOf(customActivityTag("Gardening")) } }
                    toggle("Remove Gardening")
                    awaitStored(store) { it.activities == listOf(cooking) && it.days.single().work == null }
                }
            } finally {
                runBlocking { store.replace(initial) }
            }
        }
    }

    @SdkSuppress(minSdkVersion = 29)
    @Test fun oneTapReopenFourTapEveningAndYesterdayCorrection() {
        // The release package and owner data are never test fixtures.
        assumeTrue(context.packageName.endsWith(".debug"))
        Storage(context).use { store ->
            val initial = runBlocking { store.read() }
            assumeTrue(initial.dates().none { it == today.toString() || it == yesterday.toString() })
            try {
                ActivityScenario.launch(MainActivity::class.java).use {
                    slot(morning = true)
                    select(energyQuestion, "Low")
                    awaitStored(store) { data -> data.checks.any { it.date == today.toString() &&
                        it.slot == "am" && it.energy == 1 && it.sleep == null } }
                }
                ActivityScenario.launch(MainActivity::class.java).use {
                    slot(morning = true)
                    choice(energyQuestion, "Low").assertIsSelected()
                    select("How refreshed did you feel on waking?", "Mostly")
                    slot(morning = false)
                    select(energyQuestion, "Very low")
                    select("Work", "No")
                    select("Socialising", "Yes")
                    select("How did today feel overall?", "Fairly well")
                    awaitStored(store) { data ->
                        val day = data.days.singleOrNull { it.date == today.toString() }
                        day?.work == false && day.social == true && day.wellbeing == 3 &&
                            data.checks.any { it.date == today.toString() && it.slot == "pm" && it.energy == 0 }
                    }
                    chooseYesterday()
                    slot(morning = true)
                    select(energyQuestion, "Medium")
                    select(energyQuestion, "High")
                    tap("Symptoms")
                    tap("Brain fog")
                    select("Brain fog", "Mild")
                    InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                    compose.onNodeWithText("Brain fog · Mild").assertExists()
                    InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                    choice(energyQuestion, "High").assertIsSelected()
                    awaitStored(store) { data ->
                        val check = data.checks.singleOrNull { it.date == yesterday.toString() && it.slot == "am" }
                        check?.energy == 4 && check.symptoms["Brain fog"] == 1 && check.sleep == null
                    }
                    val data = runBlocking { store.read() }
                    assertEquals(2, data.checks.count { it.date == today.toString() })
                    assertEquals(2, data.checks.single { it.date == today.toString() && it.slot == "am" }.sleep)
                }
            } finally {
                runBlocking { store.replace(initial) }
            }
        }
    }
}
