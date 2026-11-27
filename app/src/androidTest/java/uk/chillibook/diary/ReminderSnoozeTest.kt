package uk.chillibook.diary

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZonedDateTime

@RunWith(AndroidJUnit4::class)
class ReminderSnoozeTest {
    @Test fun eveningSnoozeCrossesMidnightAndRespectsAnAnswerOnTheOriginalDate() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(context.packageName.endsWith(".debug"))
        val existing = Storage(context).use { runBlocking { it.read().prefs } }
        assumeTrue("Use an isolated debug diary with reminders off", existing.morningReminder == null && existing.eveningReminder == null)
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        assertTrue("Enable notifications for the isolated debug app", Reminders.allowed(context))

        val manager = context.getSystemService(NotificationManager::class.java)
        // Keep platform alarms in the future while advancing the reminder clock in the test.
        val evening = ZonedDateTime.now().plusDays(7).withHour(23).withMinute(45).withSecond(0).withNano(0)
        val morning = evening.withHour(8).withMinute(0)
        val date = evening.toLocalDate().toString()
        val prefs = Prefs(morningReminder = 8 * 60, eveningReminder = 23 * 60 + 45)
        val diary = Diary(prefs = prefs)
        fun fire(slot: String, due: ZonedDateTime) = Intent(Reminders.FIRE)
            .putExtra(Reminders.EXTRA_SLOT, slot)
            .putExtra("due", due.toInstant().toEpochMilli())
        fun awaitEveningNotification(visible: Boolean) {
            val timeout = SystemClock.elapsedRealtime() + 3_000
            while (manager.activeNotifications.any { it.id == 2 } != visible &&
                SystemClock.elapsedRealtime() < timeout) SystemClock.sleep(30)
            assertEquals(visible, manager.activeNotifications.any { it.id == 2 })
        }

        try {
            Reminders.clear(context)
            Reminders.schedule(context, prefs, diary, force = true, now = morning.minusMinutes(1))
            Reminders.deliver(context, diary, fire("am", morning), morning)
            Reminders.deliver(context, diary, fire("pm", evening), evening)
            val morningDue = Reminders.due(context, prefs, "am")
            awaitEveningNotification(true)
            val eveningNotification = manager.activeNotifications.single { it.id == 2 }.notification
            assertEquals(listOf("Snooze 1 hour"), eveningNotification.actions.map { it.title.toString() })

            val tap = evening.plusMinutes(5)
            val deadline = tap.plusHours(1)
            val snooze = Intent(Reminders.SNOOZE)
                .putExtra(Reminders.EXTRA_SLOT, "pm")
                .putExtra(Reminders.EXTRA_DATE, date)
                .putExtra("due", evening.toInstant().toEpochMilli())
            Reminders.snooze(context, diary, snooze, tap)
            awaitEveningNotification(false)
            assertTrue(manager.activeNotifications.any { it.id == 1 })
            assertEquals(morningDue, Reminders.due(context, prefs, "am"))
            assertEquals(deadline.toInstant(), Reminders.due(context, prefs, "pm"))

            Reminders.snooze(context, diary, snooze, tap.plusMinutes(10))
            assertEquals(deadline.toInstant(), Reminders.due(context, prefs, "pm"))

            Reminders.schedule(context, prefs, diary, force = false, now = deadline.minusMinutes(10))
            assertEquals(deadline.toInstant(), Reminders.due(context, prefs, "pm"))

            Reminders.deliver(context, diary, fire("pm", deadline), deadline)
            awaitEveningNotification(true)
            assertEquals(evening.plusDays(1).toInstant(), Reminders.due(context, prefs, "pm"))
            Reminders.snooze(context, diary, snooze.putExtra("due", deadline.toInstant().toEpochMilli()), deadline)
            val secondDeadline = deadline.plusHours(1)
            assertEquals(secondDeadline.toInstant(), Reminders.due(context, prefs, "pm"))

            val answered = diary.copy(days = listOf(Day(date = date, work = false)))
            Reminders.schedule(context, prefs, answered, force = false, now = deadline.plusMinutes(5))
            Reminders.deliver(context, answered, fire("pm", secondDeadline), secondDeadline)
            awaitEveningNotification(false)
            assertEquals(evening.plusDays(1).toInstant(), Reminders.due(context, prefs, "pm"))
        } finally {
            Reminders.clear(context)
        }
    }
}
