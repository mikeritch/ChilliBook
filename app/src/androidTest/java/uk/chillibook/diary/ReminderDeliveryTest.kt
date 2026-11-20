package uk.chillibook.diary

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZonedDateTime

@RunWith(AndroidJUnit4::class)
class ReminderDeliveryTest {
    @Test fun dueAlarmPostsPrivateReminderWithActivityIntent() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(context.packageName.endsWith(".debug"))
        val now = ZonedDateTime.now()
        val due = now.minusMinutes(1).withSecond(0).withNano(0)
        assumeTrue(due.toLocalDate() == now.toLocalDate())
        val date = due.toLocalDate().toString()
        val manager = context.getSystemService(NotificationManager::class.java)
        Storage(context).use { storage ->
            val before = runBlocking { storage.read() }
            assumeTrue("Use an isolated debug diary with reminders off",
                before.prefs.morningReminder == null && before.prefs.eveningReminder == null)
            val slot = if (before.answeredReminder(date, "am")) "pm" else "am"
            assumeTrue("The debug diary needs an unanswered check-in today", !before.answeredReminder(date, slot))
            val requestId = if (slot == "am") 1 else 2
            val minute = due.hour * 60 + due.minute
            val prefs = if (slot == "am") before.prefs.copy(morningReminder = minute)
                else before.prefs.copy(eveningReminder = minute)
            val diary = before.copy(prefs = prefs)
            if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) {
                instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
            }
            assertTrue("Enable notifications for the isolated debug app", Reminders.allowed(context))

            try {
                Reminders.clear(context)
                runBlocking { storage.save(before, diary) }
                Reminders.schedule(context, prefs, diary, force = true, now = due.minusMinutes(1))
                val alarmIntent = Intent(context, ReminderReceiver::class.java).setAction(Reminders.FIRE)
                val alarm = checkNotNull(PendingIntent.getBroadcast(context, requestId, alarmIntent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE))
                alarm.send()

                val deadline = SystemClock.elapsedRealtime() + 10_000
                var posted = manager.activeNotifications.firstOrNull { it.id == requestId }
                while (posted == null && SystemClock.elapsedRealtime() < deadline) {
                    SystemClock.sleep(50)
                    posted = manager.activeNotifications.firstOrNull { it.id == requestId }
                }
                val notification = checkNotNull(posted) { "The receiver did not post the scheduled reminder" }.notification
                assertEquals(Reminders.CHANNEL, notification.channelId)
                val channel = checkNotNull(manager.getNotificationChannel(notification.channelId))
                assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
                assertNotNull(channel.sound)
                assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility)
                assertEquals("Open your diary when it suits you.", notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())
                val contentIntent = checkNotNull(notification.contentIntent)
                if (Build.VERSION.SDK_INT >= 31) assertTrue(contentIntent.isActivity)
            } finally {
                runBlocking {
                    DiaryOperations.serialized {
                        val current = storage.read()
                        storage.save(current, current.copy(prefs = before.prefs))
                        Reminders.clear(context)
                    }
                }
            }
        }
    }
}
