package uk.chillibook.diary

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EveningDrinks(date: String, day: Day?, edit: DiaryEdit) {
    val caffeine = day?.caffeineTimes
    val alcohol = day?.alcoholTimes
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        QuestionText("Caffeine & alcohol")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DrinkToggle("Caffeine", R.drawable.cups, categoryAccent("Household"), caffeine != null) {
                edit { diary ->
                    diary.changeDay(date) { entry ->
                        entry.copy(caffeineTimes = if (entry.caffeineTimes == null) emptySet() else null)
                    }
                }
            }
            DrinkToggle("Alcohol", R.drawable.diary_glass, categoryAccent("Socialising"), alcohol != null) {
                edit { diary ->
                    diary.changeDay(date) { entry ->
                        entry.copy(alcoholTimes = if (entry.alcoholTimes == null) emptySet() else null)
                    }
                }
            }
        }
        if (caffeine != null) {
            DrinkTimes("Caffeine", caffeine) { time ->
                edit { diary -> diary.changeDay(date) { it.copy(caffeineTimes = it.caffeineTimes?.toggle(time)) } }
            }
        }
        if (alcohol != null) {
            DrinkTimes("Alcohol", alcohol) { time ->
                edit { diary -> diary.changeDay(date) { it.copy(alcoholTimes = it.alcoholTimes?.toggle(time)) } }
            }
        }
    }
}

@Composable
private fun DrinkToggle(label: String, icon: Int, accent: Color, checked: Boolean, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    Surface(
        modifier = Modifier.clip(shape).toggleable(checked, role = Role.Checkbox, onValueChange = { onToggle() }),
        shape = shape,
        color = if (checked) colors.primaryContainer else colors.surface,
        contentColor = if (checked) colors.onPrimaryContainer else colors.onSurface,
        border = BorderStroke(if (checked) 2.dp else 1.dp, if (checked) colors.primary else colors.outlineVariant)
    ) {
        Row(
            Modifier.heightIn(min = 48.dp).padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DiaryIcon(icon, accent)
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DrinkTimes(label: String, selected: Set<String>, onToggle: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("$label times (optional)", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            drinkTimes.forEach { time ->
                DiaryChip(time, time in selected, onClick = { onToggle(time) },
                    modifier = Modifier.semantics { contentDescription = "$label, $time" })
            }
        }
    }
}
