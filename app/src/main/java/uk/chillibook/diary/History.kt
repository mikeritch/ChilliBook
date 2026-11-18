package uk.chillibook.diary

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek
import kotlinx.coroutines.launch

private fun LocalDate.label(pattern: String): String = format(DateTimeFormatter.ofPattern(pattern))

@Composable
fun HistoryScreen(diary: Diary, open: (String) -> Unit) {
    val today = LocalDate.now()
    var monthKey by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    val month = YearMonth.parse(monthKey)
    val latest = minOf(today, month.atEndOfMonth())
    val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    LazyColumn(state=list, modifier=Modifier.fillMaxSize(), contentPadding=PaddingValues(horizontal=20.dp)) {
        item {
            TitleArt("History","history")
            Text(month.atDay(1).label("MMMM yyyy") + "   ⌄",
                Modifier.clickable(role=Role.Button) {
                    DatePickerDialog(context, { _, year, m, _ ->
                        monthKey = YearMonth.of(year,m+1).toString()
                        scope.launch { list.scrollToItem(0) }
                    }, latest.year, latest.monthValue-1, latest.dayOfMonth).apply {
                        datePicker.maxDate = System.currentTimeMillis()
                    }.show()
                }.heightIn(min=48.dp).wrapContentHeight(),color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        for (number in latest.dayOfMonth downTo 1) {
            val date = month.atDay(number)
            val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            if (date == latest || date.dayOfWeek == DayOfWeek.SUNDAY) item(key="week-$monday") {
                Text(when(monday) { weekStart -> "This week"; weekStart.minusWeeks(1) -> "Last week"; else -> "Week of " + monday.label("d MMM") },
                    Modifier.padding(top=20.dp,bottom=8.dp),fontSize=18.sp,fontWeight=FontWeight.SemiBold)
                HorizontalDivider()
            }
            item(key=date.toString()) { HistoryRow(diary,date) { open(date.toString()) } }
        }
    }
}

@Composable
private fun HistoryRow(diary: Diary,date: LocalDate, open: () -> Unit) {
    val key = date.toString()
    val checks = diary.checks.filter { it.date == key }
    val day = diary.days.find { it.date == key }
    val activities = diary.activities.filter { it.date == key }
    val categories = categoryTags.keys.filter { activityFlag(day,activities,it) == true }
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clickable(role=Role.Button,onClick=open).heightIn(min=80.dp)
        .padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.widthIn(min=54.dp)) {
            Text(date.label("EEE"),fontSize=13.sp,color=colors.onSurfaceVariant)
            Text(date.dayOfMonth.toString(),fontSize=25.sp,fontFamily=FontFamily.Serif)
        }
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
            if (checks.isEmpty() && day == null && activities.isEmpty()) Text("No entry",color=colors.onSurfaceVariant)
            else {
                @Composable fun SlotLabel(slot:String) {
                    val level = checks.find { it.slot == slot }?.energy?.let(energyLabels::get) ?: "—"
                    val morning = slot == "am"
                    Row(Modifier.clearAndSetSemantics { contentDescription = (if(morning) "Morning" else "Evening") + ", " + (if(level == "—") "No energy entry" else level) },
                        verticalAlignment=Alignment.CenterVertically) {
                        Text(if(morning) "☼ " else "☾ ",color=if(morning) colors.primary else colors.tertiary)
                        Text(level,style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.Medium)
                    }
                }
                if (LocalDensity.current.fontScale > 1.35f) { SlotLabel("am"); SlotLabel("pm") }
                else Row(Modifier.fillMaxWidth()) {
                    Box(Modifier.weight(1f)) { SlotLabel("am") }; Box(Modifier.weight(1f)) { SlotLabel("pm") }
                }
                val labels = (categories + activities.flatMap { it.tags }.filter { it !in categories }).distinct()
                if (labels.isNotEmpty()) Row(verticalAlignment=Alignment.CenterVertically) {
                    val icon = when(categories.firstOrNull()) { "Work" -> R.drawable.work_bag; "Socialising" -> R.drawable.cups; "Household" -> R.drawable.house; else -> R.drawable.symbook }
                    Icon(painterResource(icon),null,Modifier.size(16.dp),tint=colors.onSurfaceVariant)
                    Text("  "+labels.joinToString(" · "),style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                }

            }
        }
        Text("›",Modifier.padding(start=6.dp),color=colors.onSurfaceVariant)
    }

}

@Composable
fun DateDetail(diary: Diary,date: String,edit:(String)->Unit) {
    val local = LocalDate.parse(date)
    val day = diary.days.find { it.date == date }
    val activities = diary.activities.filter { it.date == date }
    val checks = diary.checks.filter { it.date == date }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp),
        verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(local.label("EEEE"),fontFamily=FontFamily.Serif,fontSize=38.sp,lineHeight=44.sp)
        Text(local.label("d MMMM yyyy"),color=MaterialTheme.colorScheme.onSurfaceVariant)
        if (checks.isEmpty() && day == null && activities.isEmpty()) Text("No entry")
        for ((slot, label) in listOf("am" to "Morning","pm" to "Evening")) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment=Alignment.CenterVertically) {
                Icon(painterResource(if (slot == "am") R.drawable.diary_sun else R.drawable.diary_moon), null,
                    Modifier.padding(end=8.dp).size(28.dp), tint=androidx.compose.ui.graphics.Color.Unspecified)
                Text(label,Modifier.weight(1f),fontSize=22.sp,fontFamily=FontFamily.Serif)
                TextButton(onClick={edit(slot)}) { Text("Edit " + label.lowercase()) }
            }
            val check = checks.find { it.slot == slot }
            Text("Energy · " + (check?.energy?.let(energyLabels::get) ?: "No entry"))
            if (slot == "am") Text("Waking refreshment · " + (check?.sleep?.let(sleepLabels::get) ?: "No entry"))
            for ((name, value) in check?.symptoms.orEmpty()) Text("$name · ${symptomRatingLabel(value)}")
            if (slot == "pm") {
                Text("Overall wellbeing · " + (day?.wellbeing?.let(wellbeingLabels::get) ?: "No entry"))
                categoryTags.keys.forEach { name ->
                    val answer = when(activityFlag(day,activities,name)) { true -> "Yes"; false -> "No"; null -> "Unanswered" }
                    Text("$name · $answer")
                }
                listOfNotNull(day?.impact).plus(day?.context.orEmpty()).forEach { Text(it) }
            }
        }
        for (record in activities) {
            HorizontalDivider()
            Text(record.tags.joinToString(" · "),fontWeight=FontWeight.SemiBold)
            val details = listOfNotNull(record.workLength,record.workPlace,record.duration,record.effort) + record.demands
            if(details.isNotEmpty()) Text(details.joinToString(" · "))
            record.worth?.let { Text("Worthwhile · $it") }
        }
        if (activities.isNotEmpty()) for(n in 1..2) {
            val energy = diary.checks.find { it.slot == "am" && it.date == local.plusDays(n.toLong()).toString() }?.energy
            Text((if(n==1) "Next morning" else "Two mornings later") + " · " + (energy?.let(energyLabels::get) ?: "No entry"))
        }
        Spacer(Modifier.height(16.dp))
    }
}
