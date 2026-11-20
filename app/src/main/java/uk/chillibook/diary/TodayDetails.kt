package uk.chillibook.diary

import androidx.compose.ui.semantics.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TodayDetails(diary: Diary, date: String, slot: String, edit: DiaryEdit) {
    val check = diary.checks.find { it.date == date && it.slot == slot }
    val symptoms = check?.symptoms.orEmpty()
    val names = (symptomLabels + symptoms.keys.sorted()).distinct()

    var removedName by rememberSaveable { mutableStateOf<String?>(null) }
    var removedRating by rememberSaveable { mutableIntStateOf(UNRATED_SYMPTOM) }
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { QuestionText("Symptoms") }
        removedName?.let { name ->
            TextButton(onClick = {
                val rating = removedRating
                edit { d -> d.changeCheck(date, slot) { check ->
                    if (name in check.symptoms) check else check.copy(symptoms = check.symptoms + (name to rating))
                } }
                removedName = null
            }, modifier = Modifier.semantics { contentDescription = "Undo removal of $name" }) { Text("Undo") }
        }
    }
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        names.forEach { name ->
            val selected = name in symptoms
            DiaryChip(name, selected, accent = categoryAccent("Symptoms"), symbol = if (selected) "−" else "+", remove = selected, onClick = {
                removedName = name.takeIf { selected }
                removedRating = symptoms[name] ?: UNRATED_SYMPTOM
                edit { d -> d.changeCheck(date, slot) {
                    it.copy(symptoms = if (selected) it.symptoms - name else it.symptoms + (name to UNRATED_SYMPTOM))
                } }
            }, modifier = Modifier.heightIn(min = 48.dp).semantics {
                contentDescription = if (selected) "Remove $name" else "Select $name"
            })
        }
    }
    for (name in names.filter { it in symptoms }) {
        Spacer(Modifier.height(16.dp))
        Text(name, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        AnswerGrid(name, severityLabels, symptoms[name]) { rating ->
            edit { d -> d.changeCheck(date, slot) { val value = if (it.symptoms[name] == rating) UNRATED_SYMPTOM else rating
                it.copy(symptoms = it.symptoms + (name to value)) } }
        }
    }

}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ContextOptions(selected: Set<String>, toggle: (String) -> Unit) {
    val presets = listOf("Fighting a bug", "Unusual stress", "Change in medication")
    QuestionText("Context")
    Spacer(Modifier.height(10.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (name in (presets + selected.sorted()).distinct()) {
            val checked = name in selected
            DiaryChip(name, checked, onClick = { toggle(name) }, symbol = if (checked) "−" else "+", remove = checked)
        }
    }
}
