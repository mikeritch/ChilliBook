package uk.chillibook.diary

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class EveningDetailsFlowTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun optionalEveningDetailsSaveIndependentlyAndSurviveReopen() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(context.packageName.endsWith(".debug"))
        val date = LocalDate.now().toString()
        val stress = "How stressful was your day?, Moderate"
        fun described(label: String) = compose.onNodeWithContentDescription(label)
        fun tap(label: String) = described(label).performScrollTo().performClick()
        fun drink(label: String) = compose.onNodeWithText(label).performScrollTo()
        fun openEvening() {
            compose.waitUntil(10_000) {
                compose.onAllNodesWithContentDescription("Evening check-in").fetchSemanticsNodes().isNotEmpty()
            }
            tap("Evening check-in")
        }
        Storage(context).use { store ->
            val initial = runBlocking { store.read() }
            assumeTrue(date !in initial.dates())
            fun awaitSaved(condition: (Diary, Day) -> Boolean) {
                compose.waitUntil(10_000) {
                    val diary = runBlocking { store.read() }
                    diary.days.find { it.date == date }?.let { condition(diary, it) } == true
                }
            }
            try {
                ActivityScenario.launch(MainActivity::class.java).use {
                    openEvening()
                    tap(stress)
                    awaitSaved { _, day -> day.stress == 2 }

                    drink("Caffeine").assertIsOff().performClick()
                    tap("Caffeine, Morning")
                    tap("Caffeine, Afternoon")
                    drink("Alcohol").assertIsOff().performClick()
                    tap("Alcohol, Evening")
                    tap("Socialising, Yes")
                    tap("Select Coffee/Pub")
                    awaitSaved { diary, day ->
                        day.stress == 2 && day.caffeineTimes == setOf("Morning", "Afternoon") &&
                            day.alcoholTimes == setOf("Evening") && day.social == true &&
                            diary.activities.any { it.date == date && it.tags == setOf("Coffee/Pub") }
                    }
                }
                ActivityScenario.launch(MainActivity::class.java).use {
                    openEvening()
                    described(stress).performScrollTo().assertIsSelected()
                    described("Remove Coffee/Pub").performScrollTo().assertIsOn()
                    described("Caffeine, Morning").performScrollTo().assertIsOn()
                    described("Caffeine, Afternoon").performScrollTo().assertIsOn()
                    described("Alcohol, Evening").performScrollTo().assertIsOn().performClick()
                    awaitSaved { _, day -> day.alcoholTimes == emptySet<String>() }

                    tap("Caffeine, Morning")
                    drink("Alcohol").assertIsOn().performClick()
                    described("Alcohol, Evening").assertDoesNotExist()
                    awaitSaved { _, day ->
                        day.stress == 2 && day.caffeineTimes == setOf("Afternoon") && day.alcoholTimes == null
                    }
                }
            } finally {
                runBlocking { store.replace(initial) }
            }
        }
    }
}
