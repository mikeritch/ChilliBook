package uk.chillibook.diary

import android.Manifest
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
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

@Composable
internal fun ReminderSettings(prefs: Prefs, edit: DiaryEdit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var allowed by remember { mutableStateOf(Reminders.allowed(context)) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        allowed = Reminders.allowed(context)
        Reminders.reconcile(context, prefs)
    }
    DisposableEffect(context, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) allowed = Reminders.allowed(context)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    Text("Check-in reminders", style = MaterialTheme.typography.titleLarge)
    Text("Choose a local time. Android may deliver a reminder a little later.",
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
        }
    }
    if (!allowed && (prefs.morningReminder != null || prefs.eveningReminder != null)) {
        Text("Notifications are turned off in Android.", style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = { openReminderSettings(context) }) { Text("Android notification settings") }
    }
}

private fun openReminderSettings(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java)
    val channelBlocked = manager.areNotificationsEnabled() &&
        manager.getNotificationChannel(Reminders.CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE
    val intent = if (channelBlocked) Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_CHANNEL_ID, Reminders.CHANNEL)
    else Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
    context.startActivity(intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
}
