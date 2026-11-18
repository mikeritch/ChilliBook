package uk.chillibook.diary

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class LargeTextFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun tap(label: String) {
        val node = compose.onNodeWithText(label)
        if (!node.isDisplayed()) node.performScrollTo()
        node.assertIsDisplayed().performClick()
    }
    private fun navigation(label: String) = compose.onAllNodes(hasText(label) and hasClickAction()).onLast().performClick()
    private fun select(question: String, choices: List<String>) {
        for (label in choices) {
            val node = compose.onNodeWithContentDescription("$question, $label")
            node.performScrollTo().assertIsDisplayed().performClick().assertIsSelected()
        }
    }
    private fun picture(name: String) {
        compose.waitForIdle()
        instrumentation.uiAutomation.waitForIdle(150, 2000)
        val image = instrumentation.uiAutomation.takeScreenshot()
        File(context.getExternalFilesDir(null), "large-$name.png").outputStream().use {
            check(image.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        image.recycle()
    }

    @Test fun correctAnswersAndNavigateAtTwoHundredPercent() {
        assumeTrue(context.packageName.endsWith(".debug"))
        assumeTrue(context.resources.configuration.fontScale >= 1.98f)
        assumeTrue(context.resources.configuration.screenWidthDp in 355..365)
        Storage(context).use { store ->
            val initial = runBlocking { store.read() }
            assumeTrue(initial.dates().isEmpty())
            try {
                ActivityScenario.launch(MainActivity::class.java).use {
                    compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Morning check-in").fetchSemanticsNodes().isNotEmpty() }
                    compose.onNodeWithContentDescription("Morning check-in").performScrollTo().performClick()
                    select("How much energy do you have now?", energyLabels)
                    select("How refreshed did you feel on waking?", sleepLabels)
                    compose.onNodeWithContentDescription("Morning check-in").performScrollTo()
                    picture("morning")
                    compose.onNodeWithContentDescription("Select Brain fog").performScrollTo().assertIsDisplayed().performClick()
                    select("Brain fog", severityLabels)
                    picture("symptom")
                    compose.onNodeWithContentDescription("Evening check-in").performScrollTo().performClick()
                    select("How much energy do you have now?", energyLabels.reversed())
                    select("Did you work?", listOf("No", "Yes"))
                    select("Socialising", listOf("Yes", "No"))
                    select("How did today feel overall?", wellbeingLabels)
                    select("Household tasks", listOf("Yes"))
                    compose.onNodeWithContentDescription("Select Cooking").performScrollTo().performClick()
                    picture("activity-editor")
                    compose.onNodeWithText("Duration").performScrollTo().assertIsDisplayed()
                    compose.onNodeWithContentDescription("Settings").performClick()
                    select("Appearance", listOf("Dark"))
                    picture("settings-dark")
                    compose.onNodeWithText("Restore diary").performScrollTo().assertIsDisplayed()
                    navigation("Today")
                    compose.onNodeWithText("Did you work?").performScrollTo()
                    picture("evening-dark")
                    navigation("History")
                    compose.onNodeWithText("This week").assertIsDisplayed()
                    picture("history-dark")
                    navigation("Patterns")
                    picture("patterns-dark")
                    tap("All levels and dates   ›")
                    compose.onNodeWithText("All levels and dates").assertIsDisplayed()
                    picture("comparison-dark")
                    tap("Close")
                    compose.onNodeWithContentDescription("Settings").performClick()
                    select("Appearance", listOf("Light"))
                    navigation("Today")
                    compose.onNodeWithText("Did you work?").performScrollTo().assertIsDisplayed()
                }
            } finally {
                runBlocking { store.replace(initial) }
            }
        }
    }
}
