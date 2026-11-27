package uk.chillibook.diary

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun UpdateBackupRecovery(backup: UpdateBackup, savedAt: Long?, onRestore: ((Diary) -> Unit)?) {
    val resolver = LocalContext.current.contentResolver
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var status by remember(savedAt) { mutableStateOf("") }
    var preview by remember(savedAt) { mutableStateOf<Diary?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            status = "Exporting update backup…"
            try {
                withContext(Dispatchers.IO) {
                    val json = Backup.encode(backup.read())
                    val output = resolver.openOutputStream(uri, "wt") ?: error("File unavailable")
                    output.bufferedWriter(Charsets.UTF_8).use { it.write(json) }
                }
                status = "Update backup exported."
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                status = "Couldn’t export the update backup."
            } finally {
                busy = false
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        QuestionText("Update backup")
        if (savedAt == null) {
            Text("ChilliBook saves a private copy of your diary just before opening the installer for an in-app update.")
        } else {
            val timestamp = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(savedAt))
            Text("Saved $timestamp")
            if (onRestore != null) {
                OutlinedButton(onClick = {
                    scope.launch {
                        busy = true
                        status = "Checking update backup…"
                        try {
                            preview = withContext(Dispatchers.IO) { backup.read() }
                            status = ""
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            status = "Couldn’t read the update backup."
                        } finally {
                            busy = false
                        }
                    }
                }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Preview update backup")
                }
            } else {
                Text("You can export this copy now and restore it from Settings once the diary opens again.")
            }
            Text("The exported file contains readable health data. Your chosen location may sync it to the cloud.")
            OutlinedButton(onClick = {
                status = ""
                val date = Instant.ofEpochMilli(savedAt).atZone(ZoneId.systemDefault()).toLocalDate()
                export.launch("ChilliBook-update-backup-$date.json")
            }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Export update backup")
            }
        }
        if (status.isNotEmpty()) {
            Text(status, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
    }

    val imported = preview
    if (imported != null && onRestore != null) {
        AlertDialog(
            onDismissRequest = { preview = null },
            title = { Text("Replace this diary?") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val dates = remember(imported) { imported.dates() }
                    Text(when (dates.size) {
                        0 -> "No recorded dates"
                        1 -> dates.first()
                        else -> "${dates.first()} to ${dates.last()}"
                    })
                    Text("${imported.days.size} day records\n${imported.checks.size} check-ins\n${imported.activities.size} activity entries\n${imported.courses.size} medication courses\n${imported.doses.size} dose records")
                    Text("Restoring the update backup replaces all entries and settings, including entries saved or edited since the backup.")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    preview = null
                    onRestore(imported)
                }) { Text("Replace diary") }
            },
            dismissButton = { TextButton(onClick = { preview = null }) { Text("Cancel") } },
        )
    }
}
