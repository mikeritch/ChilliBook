package uk.chillibook.diary

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EpisodeSheet(diary: Diary, date: String, edit: DiaryEdit, status: String, retry: () -> Unit, close: () -> Unit) {
    val context = LocalContext.current
    var selected by rememberSaveable { mutableStateOf(diary.episodes.filter { it.start <= date && (it.end == null || it.end >= date) }.maxByOrNull { it.start }?.id) }
    var remove by rememberSaveable { mutableStateOf(false) }
    val item = diary.episodes.find { it.id == selected }
    fun update(change: (Episode) -> Episode) = edit { d ->
        d.copy(episodes = d.episodes.map { if (it.id == selected) change(it) else it })
    }
    DiaryDetailSheet(onDismiss = close) {
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState(), overscrollEffect = null).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Crash episodes", style = MaterialTheme.typography.headlineSmall)
            if (item != null) {
                DetailLink("Onset · ${item.start}") {
                    chooseDiaryDate(context, item.start, max = item.end ?: LocalDate.now().toString()) { onset -> update { it.copy(start = onset) } }
                }
                AnswerTile("Onset date", "Approximate onset", item.approxStart, Modifier.fillMaxWidth()) { update { it.copy(approxStart = !it.approxStart) } }
                DetailLink("Recovery · ${item.end ?: "Recovery date unknown"}") {
                    chooseDiaryDate(context, item.end ?: maxOf(item.start, date), min = item.start) { end -> update { it.copy(end = end) } }
                }
                if (item.end != null) {
                    AnswerTile("Recovery date", "Approximate recovery", item.approxEnd, Modifier.fillMaxWidth()) { update { it.copy(approxEnd = !it.approxEnd) } }
                    TextButton(onClick = { update { it.copy(end = null, approxEnd = false) } }) { Text("Clear recovery date") }
                }
                OptionalField("Worst symptom", symptomLabels, item.worst) { symptom -> update { it.copy(worst = symptom) } }
                TextButton(onClick = { remove = true }) { Text("Remove episode") }
                DetailLink("All episodes") { selected = null }
            } else {
                if (diary.episodes.isEmpty()) Text("No crash episodes recorded.")
                diary.episodes.sortedByDescending { it.start }.forEach { ep ->
                    DetailLink("${ep.start} · ${ep.end ?: "Recovery date unknown"}") { selected = ep.id }
                }
                DetailLink("Record an episode", "+") {
                    chooseDiaryDate(context, date) { start ->
                        val episode = Episode(start = start)
                        edit { it.copy(episodes = it.episodes + episode) }
                        selected = episode.id
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            SaveNotice(status, retry, Modifier.weight(1f))
            TextButton(onClick = close) { Text("Done") }
        }
    }
    if (remove && item != null) AlertDialog(onDismissRequest = { remove = false }, title = { Text("Remove this crash episode?") },
        text = { Text("Onset ${item.start}") }, confirmButton = { TextButton(onClick = {
            edit { d -> d.copy(episodes = d.episodes.filterNot { it.id == item.id }) }
            selected = null; remove = false
        }) { Text("Remove") } }, dismissButton = { TextButton(onClick = { remove = false }) { Text("Keep") } })
}
