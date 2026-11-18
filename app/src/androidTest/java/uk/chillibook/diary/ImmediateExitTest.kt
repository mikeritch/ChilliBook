package uk.chillibook.diary

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImmediateExitTest {
    @Test fun acceptedTapOutlivesScreenAndPrecedesReopen() {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val app = instrument.targetContext.applicationContext as Application
        assumeTrue(app.packageName.endsWith(".debug"))
        val date = "1997-06-06"
        Storage(app).use { storage ->
            val before = runBlocking { storage.read() }
            assumeTrue(date !in before.dates())
            val first = ViewModelStore()
            val second = ViewModelStore()
            fun model(store: ViewModelStore) = ViewModelProvider.create(store,
                ViewModelProvider.AndroidViewModelFactory.getInstance(app))[DiaryViewModel::class]
            lateinit var initial: DiaryViewModel
            lateinit var reopened: DiaryViewModel
            fun ready(vm: DiaryViewModel) {
                var loaded = false
                repeat(200) {
                    instrument.runOnMainSync { loaded = vm.diary != null }
                    if (loaded) return
                    Thread.sleep(25)
                }
                error("Diary did not open")
            }
            try {
                instrument.runOnMainSync { initial = model(first) }
                ready(initial)
                instrument.runOnMainSync {
                    initial.edit { it.changeCheck(date, "am") { check -> check.copy(energy = 1) } }
                    first.clear()
                    reopened = model(second)
                }
                ready(reopened)
                instrument.runOnMainSync {
                    val check = reopened.diary!!.checks.single { it.date == date }
                    assertEquals(1, check.energy)
                    assertEquals(null, check.sleep)
                }
            } finally {
                instrument.runOnMainSync { first.clear(); second.clear() }
                runBlocking { storage.replace(before) }
            }
        }
    }
}
