package uk.chillibook.diary

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(diary: Diary, date: String, slot: String, onDate: (String) -> Unit,
                onSlot: (String) -> Unit, edit: ((Diary) -> Diary) -> Unit, status: String, retry: () -> Unit) {
    val morning = slot == "am"
    val day = diary.days.find { it.date == date }
    val check = diary.checks.find { it.date == date && it.slot == slot }
    val context = LocalContext.current
    val title = if (morning) "Morning" else "Evening"
    var sheet by rememberSaveable(date, slot) { mutableStateOf(false) }
    val activeEpisode = diary.episodes.filter { it.start <= date && (it.end == null || it.end >= date) }.maxByOrNull { it.start }
    var undoId by rememberSaveable(date) { mutableStateOf<String?>(null) }
    var undoStart by rememberSaveable(date) { mutableStateOf(false) }
    var showEpisodes by rememberSaveable(date) { mutableStateOf(false) }
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 20.dp)) {
        TitleArt(title, slot)
        val prettyDate = LocalDate.parse(date).format(DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.getDefault()))
        Text("$prettyDate   ⌄", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clickable(role = Role.Button) { chooseDiaryDate(context, date, chosen = onDate) }
                .heightIn(min = 48.dp).wrapContentHeight())
        SlotSwitch(slot, onSlot)
        Spacer(Modifier.height(22.dp))
        val energyQuestion = "How much energy do you have now?"
        QuestionText(energyQuestion)
        Spacer(Modifier.height(10.dp))
        AnswerGrid(energyQuestion, energyLabels, check?.energy, 5, bars = true) { level ->
            edit { d -> d.changeCheck(date, slot) { it.copy(energy = level) } }
        }
        Spacer(Modifier.height(18.dp))
        if (morning) {
            val question = "How refreshed did you feel on waking?"
            QuestionText(question)
            Spacer(Modifier.height(10.dp))
            AnswerGrid(question, sleepLabels, check?.sleep) { level ->
                edit { d -> d.changeCheck(date, slot) { it.copy(sleep = level) } }
            }
        } else {
            key(date) { EveningActivities(diary, date, edit) }
            Spacer(Modifier.height(14.dp))
            val question = "How did today feel overall?"
            QuestionText(question)
            Spacer(Modifier.height(10.dp))
            AnswerGrid(question, wellbeingLabels, day?.wellbeing, 5) { level ->
                edit { d -> d.changeDay(date) { it.copy(wellbeing = level) } }
            }
        }
        Spacer(Modifier.height(14.dp))
        DetailLink(if (morning) "Symptoms" else "Symptoms & context", icon = R.drawable.symbook) { sheet = true }
        val crashLabel = when {
            activeEpisode == null -> "Crash started"
            activeEpisode.end == null -> "Back to my usual"
            else -> "Edit crash episode"
        }
        DetailLink(crashLabel, if (activeEpisode == null) "⊕" else "›", R.drawable.cloud) {
            when {
                activeEpisode == null -> {
                    val episode = Episode(id = UUID.randomUUID().toString(), start = date)
                    undoId = episode.id; undoStart = true
                    edit { d -> d.copy(episodes = d.episodes + episode) }
                }
                activeEpisode.end == null -> {
                    undoId = activeEpisode.id; undoStart = false
                    edit { d -> d.copy(episodes = d.episodes.map { if (it.id == activeEpisode.id) it.copy(end = date) else it }) }
                }
                else -> showEpisodes = true
            }
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            SaveNotice(status, retry, Modifier.weight(1f))
            if (undoId != null) TextButton(onClick = {
                val id = undoId
                edit { d -> if (undoStart) d.copy(episodes = d.episodes.filterNot { it.id == id })
                    else d.copy(episodes = d.episodes.map { if (it.id == id) it.copy(end = null, approxEnd = false) else it }) }
                undoId = null
            }) { Text("Undo") }
        }
    }
    if (sheet) {
        TodayDetails(diary, date, slot, edit, status, retry, { sheet = false }) {
            sheet = false; showEpisodes = true; undoId = null
        }
    }
    if (showEpisodes) EpisodeSheet(diary, date, edit, status, retry) { showEpisodes = false }

}

@Composable
internal fun SaveNotice(status: String, retry: () -> Unit, modifier: Modifier = Modifier) {
    val failed = status.contains("save", ignoreCase = true) && (status.contains("couldn", true) || status.contains("fail", true))
    Column(modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        Text(if (status.equals("Saved", true)) "✓  Saved" else status, style = MaterialTheme.typography.labelMedium,
            color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        if (failed) TextButton(onClick = retry) { Text("Retry") }
    }
}

@Composable
private fun SlotSwitch(slot: String, choose: (String) -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clip(shape).background(colors.surfaceVariant).padding(2.dp)) {
        listOf("am" to "☼  Morning", "pm" to "☾  Evening").forEach { (key, label) ->
            val selected = key == slot
            val name = if (key == "am") "Morning" else "Evening"
            Box(Modifier.weight(1f).clip(shape)
                .then(if (selected) Modifier.background(colors.surface).border(1.5.dp, colors.primary, shape) else Modifier)
                .selectable(selected, role = Role.Tab) { choose(key) }.semantics { contentDescription = "$name check-in" }.heightIn(min = 48.dp).padding(4.dp),
                contentAlignment = Alignment.Center) {
                Text(if (LocalDensity.current.fontScale >= 1.5f) name else label, color = if (selected) colors.primary else colors.onSurfaceVariant,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

@Composable
internal fun YesNoRow(question: String, answer: Boolean?, tag: String, choose: (Boolean) -> Unit) {
    val scale = LocalDensity.current.fontScale
    @Composable fun Buttons() {
        for (value in listOf(true, false)) {
            AnswerTile(question, if (value) "Yes" else "No", answer == value,
                Modifier.widthIn(min = (58 * scale).dp), onClick = { choose(value) })
        }
    }
    val label: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val icon = when (tag) {
                "Work" -> R.drawable.work_bag
                "Socialising" -> R.drawable.cups
                else -> R.drawable.house
            }
            Icon(painterResource(icon), null, Modifier.size(21.dp), tint = MaterialTheme.colorScheme.primary)
            Text(question, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        }
    }
    if (scale > 1.3f) Column { label(); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Buttons() } }
    else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.weight(1f)) { label() }
        Buttons()
    }
}
