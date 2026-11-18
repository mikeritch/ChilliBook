package uk.chillibook.diary

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.ZonedDateTime

internal fun nextReminder(now: ZonedDateTime, minute: Int, lastDate: String?): ZonedDateTime {
    val time = LocalTime.of(minute / 60, minute % 60)
    val today = now.toLocalDate()
    val target = today.atTime(time).atZone(now.zone)
    return if (target.isAfter(now) && today.toString() != lastDate) target
    else today.plusDays(1).atTime(time).atZone(now.zone)
}

internal fun Diary.answeredReminder(date: String, slot: String): Boolean =
    checks.any { it.date == date && it.slot == slot && (it.energy != null || it.sleep != null || it.symptoms.isNotEmpty()) } ||
        (slot == "pm" && (activities.any { it.date == date } || days.any { it.date == date &&
            (it.work != null || it.social != null || it.household != null || it.wellbeing != null ||
                it.impact != null || it.context.isNotEmpty()) }))

object Reminders {
    const val EXTRA_SLOT = "reminder_slot"
    const val EXTRA_DATE = "reminder_date"
    internal const val CHANNEL = "diary_reminders"
    internal const val FIRE = "uk.chillibook.diary.REMIND"
    private const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    private fun id(slot: String) = if (slot == "am") 1 else 2
    internal fun minute(prefs: Prefs, slot: String) = if (slot == "am") prefs.morningReminder else prefs.eveningReminder
    private fun state(context: Context) = context.getSharedPreferences("reminder_delivery", Context.MODE_PRIVATE)

    @Synchronized
    internal fun clear(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val manager = context.getSystemService(NotificationManager::class.java)
        for (slot in listOf("am", "pm")) {
            val intent = Intent(context, ReminderReceiver::class.java).setAction(FIRE)
            PendingIntent.getBroadcast(context, id(slot), intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let {
                alarms.cancel(it)
                it.cancel()
            }
            manager.cancel(id(slot))
        }
        state(context).edit().clear().apply()
    }

    internal fun allowed(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        return (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED) && manager.areNotificationsEnabled() &&
            manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun reconcile(context: Context, prefs: Prefs) = reconcile(context, prefs, null)
    fun reconcile(context: Context, diary: Diary) = reconcile(context, diary.prefs, diary)

    private fun reconcile(context: Context, prefs: Prefs, diary: Diary?) {
        try {
            schedule(context, prefs, diary, false)
        } catch (_: RuntimeException) {
            // A platform scheduling failure is independent of the committed diary write.
            // The next foreground visit retries from the saved preferences.
        }
    }

    @Synchronized
    internal fun schedule(context: Context, prefs: Prefs, diary: Diary?, force: Boolean,
        now: ZonedDateTime = ZonedDateTime.now()) {
        val state = state(context)
        val manager by lazy { context.getSystemService(NotificationManager::class.java) }
        val alarms by lazy { context.getSystemService(AlarmManager::class.java) }
        val canNotify by lazy { allowed(context) }
        for (slot in listOf("am", "pm")) {
            val time = minute(prefs, slot)
            if (time == null && !state.contains("minute_$slot")) continue
            val intent = Intent(context, ReminderReceiver::class.java).setAction(FIRE).putExtra(EXTRA_SLOT, slot)
            val last = state.getString("last_$slot", null)
            val oldMinute = state.getInt("minute_$slot", -1)
            if (time == null || oldMinute != time || !canNotify ||
                last != now.toLocalDate().toString() || diary?.answeredReminder(now.toLocalDate().toString(), slot) == true) {
                manager.cancel(id(slot))
            }
            val existing = PendingIntent.getBroadcast(context, id(slot), intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
            if (time == null || !canNotify) {
                existing?.let { alarms.cancel(it); it.cancel() }
                state.edit().remove("due_$slot").remove("minute_$slot").apply()
                continue
            }
            val oldDue = state.getLong("due_$slot", 0)
            // Keep today's inexact alarm while Android is delaying delivery.
            if (!force && existing != null && oldMinute == time &&
                java.time.Instant.ofEpochMilli(oldDue).atZone(now.zone).toLocalDate() == now.toLocalDate() &&
                last != now.toLocalDate().toString()) continue
            val next = nextReminder(now, time, last)
            val due = next.toInstant().toEpochMilli()
            if (!force && existing != null && oldMinute == time && oldDue == due) continue
            intent.putExtra("due", due)
            val pending = PendingIntent.getBroadcast(context, id(slot), intent, FLAGS)
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, due, pending)
            state.edit().putInt("minute_$slot", time).putLong("due_$slot", due).apply()
        }
    }

    @Synchronized
    internal fun deliver(context: Context, diary: Diary, intent: Intent, now: ZonedDateTime = ZonedDateTime.now()) {
        val slot = intent.getStringExtra(EXTRA_SLOT)
        if (slot != "am" && slot != "pm") return
        val date = now.toLocalDate()
        val state = state(context)
        val due = intent.getLongExtra("due", -1)
        val time = minute(diary.prefs, slot)
        if (time != null && due == state.getLong("due_$slot", 0) &&
            time == state.getInt("minute_$slot", -1) &&
            date.atTime(time / 60, time % 60).atZone(now.zone).toInstant().toEpochMilli() == due &&
            now.toInstant().toEpochMilli() >= due && allowed(context) &&
            state.getString("last_$slot", null) != date.toString() && !diary.answeredReminder(date.toString(), slot)) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Check-in reminders", NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            })
            val open = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_SLOT, slot).putExtra(EXTRA_DATE, date.toString())
            val notification = Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.reminder_icon)
                .setContentTitle("ChilliBook").setContentText("Open your diary when it suits you.")
                .setVisibility(Notification.VISIBILITY_PRIVATE).setOnlyAlertOnce(true).setAutoCancel(true)
                .setTimeoutAfter(date.plusDays(1).atStartOfDay(now.zone).toInstant().toEpochMilli() - now.toInstant().toEpochMilli())
                .setContentIntent(PendingIntent.getActivity(context, id(slot), open, FLAGS)).build()
            if (state.edit().putString("last_$slot", date.toString()).commit()) manager.notify(id(slot), notification)
        }
        // Remove the consumed deadline before reconciling. The other slot may still be waiting.
        if (due == state.getLong("due_$slot", 0)) state.edit().remove("due_$slot").apply()
        schedule(context, diary.prefs, diary, false, now)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                DiaryOperations.serialized {
                    val diary = Storage(context).use { it.read() }
                    if (intent.action == Reminders.FIRE) Reminders.deliver(context, diary, intent)
                    else Reminders.schedule(context, diary.prefs, diary, true)
                }
            } catch (_: RuntimeException) {
                // Storage or Android services can be unavailable. Opening the app reconciles again.
            } finally {
                pending.finish()
            }
        }
    }
}
