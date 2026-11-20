package uk.chillibook.diary

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.withResumed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
internal fun UpdateSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var release by remember { mutableStateOf<UpdateRelease?>(null) }
    var installer by rememberSaveable { mutableStateOf<Intent?>(null) }
    var launchPending by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf("") }
    var work by remember { mutableStateOf<Job?>(null) }
    val button = Modifier.fillMaxWidth().heightIn(min = 48.dp)

    fun openInstaller() {
        val intent = installer ?: return
        try {
            context.startActivity(intent)
            message = "Confirm the update in the Android installer."
        } catch (_: RuntimeException) {
            message = "Couldn’t open the Android installer. Try again."
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (context.packageManager.canRequestPackageInstalls()) openInstaller()
        else message = "To install the update, allow ChilliBook to install apps in Android settings."
    }

    fun requestInstall() {
        if (context.packageManager.canRequestPackageInstalls()) openInstaller()
        else try {
            permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
        } catch (_: RuntimeException) {
            message = "Couldn’t open Android settings. Allow ChilliBook to install apps, then try again."
        }
    }

    LaunchedEffect(launchPending) {
        if (launchPending) lifecycle.withResumed {
            launchPending = false
            if (installer != null) requestInstall()
        }
    }

    QuestionText("App updates")
    Text("Installed version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})")
    if (BuildConfig.UPDATE_REPOSITORY.isBlank()) {
        Text("An update source hasn’t been set for this build.")
        return
    }
    if (message.isNotEmpty()) Text(message, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    val available = release
    when {
        work != null -> {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedButton(onClick = { work?.cancel() }, modifier = button) { Text("Cancel") }
        }
        installer != null -> {
            OutlinedButton(onClick = ::requestInstall, modifier = button) { Text("Install update") }
        }
        available != null -> {
            Text("Version ${available.version} is available.")
            OutlinedButton(onClick = {
                message = "Downloading and checking the update…"
                work = scope.launch {
                    try {
                        installer = GitHubUpdate.download(context, available)
                        launchPending = true
                    } catch (e: CancellationException) {
                        message = "Download cancelled."
                        throw e
                    } catch (_: Exception) {
                        message = "Couldn’t download or verify the update. Check your connection and try again."
                    } finally {
                        work = null
                    }
                }
            }, modifier = button) { Text("Download and install update") }
        }
        else -> {
            OutlinedButton(onClick = {
                message = "Checking GitHub for an update…"
                work = scope.launch {
                    try {
                        release = GitHubUpdate.check()
                        message = if (release == null) "You have the latest version." else ""
                    } catch (e: CancellationException) {
                        message = "Update check cancelled."
                        throw e
                    } catch (_: Exception) {
                        message = "Couldn’t check for an update. Check your connection and try again."
                    } finally {
                        work = null
                    }
                }
            }, modifier = button) { Text("Check for updates") }
        }
    }
}
