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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(diary: Diary, date: String, slot: String, onDate: (String) -> Unit,
                onSlot: (String) -> Unit, edit: ((Diary) -> Diary) -> Unit, status: String, retry: () -> Unit, manageMedication: () -> Unit) {
    val morning = slot == "am"
    val day = diary.days.find { it.date == date }
    val check = diary.checks.find { it.date == date && it.slot == slot }
    val context = LocalContext.current
    val title = if (morning) "Morning" else "Evening"
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)) {
            TitleArt(title, slot)
            CheckInDateSelector(date) { chooseDiaryDate(context, date, chosen = onDate) }
            SlotSwitch(slot, onSlot)
            Spacer(Modifier.height(22.dp))
            val energyQuestion = "How much energy do you have now?"
            QuestionText(energyQuestion)
            Spacer(Modifier.height(10.dp))
            AnswerGrid(energyQuestion, energyLabels, check?.energy, 5, bars = true) { level ->
                edit { d -> d.changeCheck(date, slot) { answer -> answer.copy(energy = level.takeUnless { it == answer.energy }) } }
            }
            Spacer(Modifier.height(18.dp))
            if (morning) {
                val question = "How refreshed did you feel on waking?"
                QuestionText(question)
                Spacer(Modifier.height(10.dp))
                AnswerGrid(question, sleepLabels, check?.sleep) { level ->
                    edit { d -> d.changeCheck(date, slot) { answer -> answer.copy(sleep = level.takeUnless { it == answer.sleep }) } }
                }
            } else {
                val question = "How did today feel overall?"
                QuestionText(question)
                Spacer(Modifier.height(10.dp))
                AnswerGrid(question, wellbeingLabels, day?.wellbeing, 5) { level ->
                    edit { d -> d.changeDay(date) { answer -> answer.copy(wellbeing = level.takeUnless { it == answer.wellbeing }) } }
                }
            }
            if (!morning) {
                Spacer(Modifier.height(28.dp))
                key(date) { EveningActivities(diary, date, edit) }
            }
            Spacer(Modifier.height(28.dp))
            key(date, slot) { TodayDetails(diary, date, slot, edit) }
            Spacer(Modifier.height(20.dp))
            key(date, slot) { TodayMedication(diary, date, slot, edit, manageMedication) }
            if (!morning) {
                MultiField("Context", listOf("Fighting a bug", "Unusual stress", "Change in medication"), day?.context.orEmpty()) { value ->
                    edit { d -> d.changeDay(date) { it.copy(context = it.context.toggle(value)) } }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        SaveNotice(status, retry)
    }
}

@Composable
private fun CheckInDateSelector(date: String, openCalendar: () -> Unit) {
    val prettyDate = LocalDate.parse(date).format(DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.getDefault()))
    OutlinedButton(onClick = openCalendar, modifier = Modifier.heightIn(min = 48.dp)) {
        Text("$prettyDate   ⌄", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
internal fun SaveNotice(status: String, retry: () -> Unit, modifier: Modifier = Modifier) {
    val failed = status.contains("save", ignoreCase = true) && (status.contains("couldn", true) || status.contains("fail", true))
    if (failed) {
        Column(modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
            Text(status, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = retry) { Text("Retry") }
        }
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
            DiaryIcon(icon, categoryAccent(tag))
            Text(question, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        }
    }
    if (scale > 1.3f) Column { label(); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Buttons() } }
    else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.weight(1f)) { label() }
        Buttons()
    }
}
