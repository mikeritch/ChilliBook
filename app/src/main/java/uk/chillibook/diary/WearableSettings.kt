package uk.chillibook.diary

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
internal fun WearableSettings(vm: DiaryViewModel) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var availability by remember(context) { mutableIntStateOf(WearableHealthConnect.status(context)) }
    var connectionMessage by remember { mutableStateOf<String?>(null) }
    val requestPermission = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.any(WearableHealthConnect.permissions::contains)) vm.setWearableEnabled(true)
        else connectionMessage = "Allow steps or sleep in Health Connect to import readings."
    }
    DisposableEffect(lifecycle, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) availability = WearableHealthConnect.status(context)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        QuestionText("Steps and sleep")
        Text("Import daily steps and sleep sessions from Health Connect, including Mi Fitness when it shares there.")
        val button = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        if (availability == HealthConnectClient.SDK_AVAILABLE) {
            if (vm.wearableEnabled) {
                OutlinedButton(onClick = { vm.refreshWearable(force = true) }, enabled = !vm.wearableSyncing,
                    modifier = button) { Text("Sync readings now") }
                OutlinedButton(onClick = {
                    try {
                        context.startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))
                    } catch (_: ActivityNotFoundException) {
                        connectionMessage = "Open Health Connect in Android Settings to manage access."
                    }
                }, modifier = button) { Text("Manage Health Connect access") }
                OutlinedButton(onClick = {
                    vm.setWearableEnabled(false)
                    connectionMessage = "Sync paused. Previously imported readings stay in your diary."
                }, modifier = button) { Text("Pause sync") }
            } else {
                Button(onClick = {
                    connectionMessage = null
                    try {
                        requestPermission.launch(WearableHealthConnect.permissions)
                    } catch (_: ActivityNotFoundException) {
                        connectionMessage = "Couldn’t open Health Connect permissions. Try again from Android Settings."
                    }
                }, modifier = button) { Text("Connect Health Connect") }
            }
        } else {
            Text(if (availability == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED)
                "Install or update Health Connect in Android, then come back here."
            else "Health Connect isn’t available on this device.")
        }
        val message = if (vm.wearableSyncing) "Syncing readings…" else connectionMessage ?: vm.wearableStatus
        if (message != null) Text(message, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
}

class HealthPermissionsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DiaryTheme(isSystemInDarkTheme()) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        QuestionText("Health Connect privacy")
                        Text("ChilliBook reads steps and sleep with your permission. It saves daily step totals, available hourly readings, and sleep periods by wake date so you can see them alongside your diary.")
                        Text("Imported readings stay on this device unless you export a diary backup. Pause sync in ChilliBook or change access in Health Connect at any time.")
                        OutlinedButton(onClick = { finish() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Done") }
                    }
                }
            }
        }
    }
}
