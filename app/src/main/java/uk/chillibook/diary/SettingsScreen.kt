package uk.chillibook.diary

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStreamReader
import java.time.LocalDate

@Composable
internal fun SettingsScreen(
    diary: Diary, edit: ((Diary) -> Diary) -> Unit, restore: (Diary) -> Unit,
    status: String, retry: () -> Unit, onClose: () -> Unit, reset: () -> Unit, resetting: Boolean,
    feedbackEnabled: Set<FeedbackOption>,
    onFeedbackChanged: (FeedbackOption, Boolean) -> Unit,
    previewFeedback: (FeedbackOption) -> Boolean,
    refreshReminders: () -> Unit,
) {
    val resolver = LocalContext.current.contentResolver
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var fileStatus by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<Diary?>(null) }
    var resetStep by remember { mutableIntStateOf(0) }
    BackHandler(onBack = onClose)

    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            fileStatus = "Writing backup…"
            try {
                withContext(Dispatchers.IO) {
                    val json = Backup.encode(diary)
                    val output = resolver.openOutputStream(uri, "wt") ?: error("File unavailable")
                    output.bufferedWriter(Charsets.UTF_8).use { it.write(json) }
                }
                fileStatus = "Backup saved."
            } catch (e: CancellationException) {
                throw e
            } catch (_: Backup.LimitExceeded) {
                fileStatus = "The diary exceeds the backup limit of 8,388,608 characters or 250,000 values."
            } catch (_: Exception) {
                fileStatus = "Couldn’t write the backup. Choose a destination and try again."
            } finally {
                busy = false
            }
        }
    }

    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            fileStatus = "Checking backup…"
            try {
                preview = withContext(Dispatchers.IO) {
                    val input = resolver.openInputStream(uri) ?: error("File unavailable")
                    Backup.decode(InputStreamReader(input, Charsets.UTF_8.newDecoder()))
                }
                fileStatus = ""
            } catch (e: CancellationException) {
                throw e
            } catch (_: Backup.LimitExceeded) {
                fileStatus = "This file exceeds the backup limit of 8,388,608 characters or 250,000 values."
            } catch (_: Exception) {
                fileStatus = "Couldn’t restore this file. Choose a valid ChilliBook backup."
            } finally {
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            QuestionText("Appearance")
            val themes = listOf("system" to "Use device theme", "light" to "Light", "dark" to "Dark")
            themes.forEach { (id, label) ->
                AnswerTile("Appearance", label, diary.prefs.theme == id, Modifier.fillMaxWidth()) {
                    edit { current -> current.copy(prefs = current.prefs.copy(theme = id)) }
                }
            }
            if (!status.contains("reset", ignoreCase = true)) SaveNotice(status, retry)
        }
        FeedbackSettings(feedbackEnabled, onFeedbackChanged, previewFeedback)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ReminderSettings(diary.prefs, edit, refreshReminders)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            UpdateSettings()
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            QuestionText("Your diary backup")
            Text("A backup contains readable health data, including your entries and settings. The location you choose may sync the file to the cloud.")
            Button(onClick = {
                fileStatus = ""
                create.launch("ChilliBook-${LocalDate.now()}.json")
            }, enabled = !busy && !resetting, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Back up diary")
            }
            OutlinedButton(onClick = {
                fileStatus = ""
                open.launch(arrayOf("*/*"))
            }, enabled = !busy && !resetting, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Restore diary")
            }
            if (fileStatus.isNotEmpty()) {
                Text(fileStatus, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { resetStep = 1 }, enabled = !busy && !resetting,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Reset all diary data", color = MaterialTheme.colorScheme.error)
            }
            if (status.contains("reset", ignoreCase = true)) {
                Text(status, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
        }
        SupportSettings()
    }

    if (resetStep != 0) {
        val final = resetStep == 2
        AlertDialog(onDismissRequest = { resetStep = 0 },
            title = { Text(if (final) "Permanently erase this diary?" else "Reset all diary data?") },
            text = {
                Text(if (final) "Every entry on this device will be permanently deleted. This cannot be undone."
                    else "This permanently removes your check-ins and activities, resets your settings and turns off reminders. Backups you exported are kept.")
            },
            confirmButton = {
                TextButton(onClick = {
                    if (final) {
                        resetStep = 0
                        fileStatus = ""
                        reset()
                    } else resetStep = 2
                }) { Text(if (final) "Permanently erase diary" else "Continue", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { resetStep = 0 }) { Text("Cancel") } })
    }

    preview?.let { imported ->
        AlertDialog(onDismissRequest = { preview = null }, title = { Text("Replace this diary?") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val dates = remember(imported) { imported.dates() }
                    Text(when (dates.size) {
                        0 -> "No recorded dates"
                        1 -> dates.first()
                        else -> "${dates.first()} to ${dates.last()}"
                    })
                    Text("${imported.days.size} day records\n${imported.checks.size} check-ins\n${imported.activities.size} activity entries\n${imported.courses.size} medication courses\n${imported.doses.size} dose records")
                    Text("Restoring this backup replaces all entries and settings on this device.")
                }
            }, confirmButton = {
                TextButton(onClick = { preview = null; restore(imported) }) { Text("Replace diary") }
            }, dismissButton = {
                TextButton(onClick = { preview = null }) { Text("Cancel") }
            })
    }
}
