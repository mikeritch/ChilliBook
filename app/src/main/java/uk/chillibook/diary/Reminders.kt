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
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime

internal fun nextReminder(now: ZonedDateTime, minute: Int, lastDate: String?): ZonedDateTime {
    val time = LocalTime.of(minute / 60, minute % 60)
    val today = now.toLocalDate()
    val target = today.atTime(time).atZone(now.zone)
    return if (target.isAfter(now) && today.toString() != lastDate) target
    else today.plusDays(1).atTime(time).atZone(now.zone)
}

internal fun Check.hasSlotAnswer() = energy != null || sleep != null || symptoms.isNotEmpty()

internal fun Day.hasEveningAnswer() = work != null || social != null || household != null ||
    wellbeing != null || impact != null || context.isNotEmpty() || noRests == true

internal fun Diary.answeredReminder(date: String, slot: String): Boolean =
    doses.any { it.date == date && it.slot == slot } ||
        checks.any { it.date == date && it.slot == slot && it.hasSlotAnswer() } ||
        (slot == "pm" && (activities.any { it.date == date } || days.any { it.date == date && it.hasEveningAnswer() }))

object Reminders {
    const val EXTRA_SLOT = "reminder_slot"
    const val EXTRA_DATE = "reminder_date"
    internal const val CHANNEL = "diary_check_in_alerts"
    private const val LEGACY_CHANNEL = "diary_reminders"
    internal const val FIRE = "uk.chillibook.diary.REMIND"
    private const val TEST_ID = 3
    private const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    private fun id(slot: String) = if (slot == "am") 1 else 2
    internal fun minute(prefs: Prefs, slot: String) = if (slot == "am") prefs.morningReminder else prefs.eveningReminder
    internal fun state(context: Context): SharedPreferences =
        context.getSharedPreferences("reminder_delivery", Context.MODE_PRIVATE)

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
        manager.cancel(TEST_ID)
        state(context).edit().clear().apply()
    }

    internal fun allowed(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        return (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED) && manager.areNotificationsEnabled() &&
            manager.getNotificationChannel(channelId(manager))?.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun reconcile(context: Context, diary: Diary) {
        try {
            schedule(context, diary.prefs, diary, false)
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
        if (prefs.morningReminder != null || prefs.eveningReminder != null) createChannel(manager)
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
                Instant.ofEpochMilli(oldDue).atZone(now.zone).toLocalDate() == now.toLocalDate() &&
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

    internal fun channelId(manager: NotificationManager): String {
        if (manager.getNotificationChannel(CHANNEL) != null) return CHANNEL
        val legacy = manager.getNotificationChannel(LEGACY_CHANNEL) ?: return CHANNEL
        val customized = legacy.importance != NotificationManager.IMPORTANCE_LOW ||
            legacy.sound != null || legacy.shouldVibrate() || legacy.canBypassDnd() ||
            (Build.VERSION.SDK_INT >= 29 && legacy.hasUserSetImportance()) ||
            (Build.VERSION.SDK_INT >= 30 && legacy.hasUserSetSound())
        return if (customized) LEGACY_CHANNEL else CHANNEL
    }

    private fun createChannel(manager: NotificationManager) {
        if (channelId(manager) != CHANNEL || manager.getNotificationChannel(CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Diary reminder alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                setSound(Settings.System.DEFAULT_NOTIFICATION_URI,
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build())
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
        )
    }

    internal fun due(context: Context, prefs: Prefs, slot: String): Instant? {
        val state = state(context)
        val minute = minute(prefs, slot) ?: return null
        return state.getLong("due_$slot", 0).takeIf { it > 0 && minute == state.getInt("minute_$slot", -1) }
            ?.let(Instant::ofEpochMilli)
    }

    internal fun sendTest(context: Context, slot: String, now: ZonedDateTime = ZonedDateTime.now()): Boolean {
        return try {
            val manager = context.getSystemService(NotificationManager::class.java)
            createChannel(manager)
            if (!allowed(context)) return false
            manager.notify(TEST_ID, notification(context, slot, now, TEST_ID, test = true))
            true
        } catch (_: RuntimeException) {
            false
        }
    }

    private fun notification(context: Context, slot: String, now: ZonedDateTime, id: Int, test: Boolean): Notification {
        val date = now.toLocalDate()
        val open = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_SLOT, slot).putExtra(EXTRA_DATE, date.toString())
        val expires = date.plusDays(1).atStartOfDay(now.zone).toInstant().toEpochMilli() - now.toInstant().toEpochMilli()
        val channel = channelId(context.getSystemService(NotificationManager::class.java))
        return Notification.Builder(context, channel).setSmallIcon(R.drawable.reminder_icon)
            .setContentTitle(if (test) "ChilliBook test reminder" else "ChilliBook")
            .setContentText("Open your diary when it suits you.")
            .setVisibility(Notification.VISIBILITY_PRIVATE).setOnlyAlertOnce(true).setAutoCancel(true)
            .setTimeoutAfter(expires)
            .setContentIntent(PendingIntent.getActivity(context, id, open, FLAGS)).build()
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
            createChannel(manager)
            manager.notify(id(slot), notification(context, slot, now, id(slot), test = false))
            state.edit().putString("last_$slot", date.toString()).apply()
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
