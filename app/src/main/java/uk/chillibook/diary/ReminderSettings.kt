package uk.chillibook.diary

import android.Manifest
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.Calendar
import java.util.Date

@Composable
internal fun ReminderSettings(prefs: Prefs, edit: DiaryEdit, refreshReminders: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var allowed by remember { mutableStateOf(Reminders.allowed(context)) }
    var testStatus by remember { mutableStateOf<String?>(null) }
    val delivery = remember(context) { Reminders.state(context) }
    var dueTimes by remember(context, prefs.morningReminder, prefs.eveningReminder) {
        mutableStateOf(listOf(Reminders.due(context, prefs, "am"), Reminders.due(context, prefs, "pm")))
    }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        allowed = Reminders.allowed(context)
        refreshReminders()
    }
    DisposableEffect(context, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) allowed = Reminders.allowed(context)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    DisposableEffect(delivery, prefs.morningReminder, prefs.eveningReminder) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            dueTimes = listOf(Reminders.due(context, prefs, "am"), Reminders.due(context, prefs, "pm"))
        }
        delivery.registerOnSharedPreferenceChangeListener(listener)
        dueTimes = listOf(Reminders.due(context, prefs, "am"), Reminders.due(context, prefs, "pm"))
        onDispose { delivery.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    Text("Check-in reminders", style = MaterialTheme.typography.titleLarge)
    Text("Android may deliver reminders after the scheduled time.",
        style = MaterialTheme.typography.bodyMedium)
    for (morning in listOf(true, false)) {
        val label = if (morning) "Morning reminder" else "Evening reminder"
        val minute = if (morning) prefs.morningReminder else prefs.eveningReminder
        fun update(value: Int?) {
            edit { diary ->
                diary.copy(prefs = if (morning) diary.prefs.copy(morningReminder = value)
                    else diary.prefs.copy(eveningReminder = value))
            }
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
            value = minute != null, role = Role.Switch, onValueChange = { enabled ->
                update(if (!enabled) null else if (morning) 9 * 60 else 20 * 60)
                if (enabled && Build.VERSION.SDK_INT >= 33 &&
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                    request.launch(Manifest.permission.POST_NOTIFICATIONS)
            }), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f))
            Switch(checked = minute != null, onCheckedChange = null)
        }
        if (minute != null) {
            val value = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, minute / 60)
                set(Calendar.MINUTE, minute % 60)
            }
            OutlinedButton(onClick = {
                TimePickerDialog(context, { _, hour, minutes -> update(hour * 60 + minutes) },
                    minute / 60, minute % 60, DateFormat.is24HourFormat(context)).show()
            }) { Text("${if (morning) "Morning" else "Evening"} time · ${DateFormat.getTimeFormat(context).format(value.time)}") }
            if (allowed) dueTimes[if (morning) 0 else 1]?.let { due ->
                val pending = Date.from(due)
                Text("Scheduled for ${DateFormat.getDateFormat(context).format(pending)} at ${DateFormat.getTimeFormat(context).format(pending)}",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (prefs.morningReminder != null || prefs.eveningReminder != null) {
        if (!allowed) Text("Notifications are turned off in Android.", style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = { openReminderSettings(context) }) { Text("Android notification settings") }
        OutlinedButton(enabled = allowed, onClick = {
            val slot = if (prefs.morningReminder != null) "am" else "pm"
            testStatus = if (Reminders.sendTest(context, slot)) "Test notification sent. Check the notification shade."
                else "Couldn’t send a test notification."
        }) { Text("Send test notification") }
        if (allowed) testStatus?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}

private fun openReminderSettings(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java)
    val channel = Reminders.channelId(manager)
    val hasChannel = manager.areNotificationsEnabled() && manager.getNotificationChannel(channel) != null
    val intent = if (hasChannel) Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_CHANNEL_ID, channel)
    else Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
    context.startActivity(intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
}
