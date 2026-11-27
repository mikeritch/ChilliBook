package uk.chillibook.diary

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
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

internal data class HistoryMatch(val date: String, val labels: List<String>)

internal fun historyMatches(diary: Diary, query: String): List<HistoryMatch> {
    val term = query.trim()
    if (term.isEmpty()) return emptyList()
    val days = diary.days.associateBy { it.date }
    val activities = diary.activities.groupBy { it.date }
    val checks = diary.checks.groupBy { it.date }
    return diary.dates().asReversed().mapNotNull { date ->
        val records = activities[date].orEmpty()
        val labels = buildSet {
            addAll(categoryTags.keys.filter { activityFlag(days[date], records, it) == true })
            if (term.equals(REST_GROUP, ignoreCase = true) && restRecorded(days[date], records) == true) add(REST_GROUP)
            records.forEach { addAll(it.tags) }
            checks[date].orEmpty().forEach { addAll(it.symptoms.keys) }
        }.filter { it.contains(term, ignoreCase = true) }
        if (labels.isEmpty()) null else HistoryMatch(date, labels)
    }
}

@Composable
fun HistoryScreen(diary: Diary, manageMedication: () -> Unit, open: (String) -> Unit) {
    val today = LocalDate.now()
    var monthKey by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    val month = YearMonth.parse(monthKey)
    val latest = minOf(today, month.atEndOfMonth())
    val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    val matches = remember(diary, query) { historyMatches(diary, query) }
    val searchList = rememberLazyListState()
    val context = LocalContext.current
    LazyColumn(state=if (query.isBlank()) list else searchList, modifier=Modifier.fillMaxSize(),
        contentPadding=PaddingValues(start=20.dp, end=20.dp, bottom=16.dp)) {
        item {
            TitleArt("History","history")
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(onClick = manageMedication) { Text("Medication history") }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Find a symptom or activity", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(value=query, onValueChange={ query=it }, singleLine=true,
                        modifier=Modifier.fillMaxWidth().semantics { contentDescription = "Find a symptom or activity" },
                        placeholder={ Text("e.g. Headache or Commute") },
                        trailingIcon=if (query.isNotEmpty()) {
                            { TextButton(onClick={ query="" }) { Text("Clear") } }
                        } else null)
                }
                if (query.isBlank()) {
                    OutlinedButton(onClick = {
                        DatePickerDialog(context, { _, year, m, _ ->
                            monthKey = YearMonth.of(year,m+1).toString()
                            scope.launch { list.scrollToItem(0) }
                        }, latest.year, latest.monthValue-1, latest.dayOfMonth).apply {
                            datePicker.maxDate = System.currentTimeMillis()
                        }.show()
                    }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(month.atDay(1).label("MMMM yyyy") + "   ⌄")
                    }
                }
            }
        }
        if (query.isNotBlank()) {
            item { Spacer(Modifier.height(16.dp)) }
            if (matches.isEmpty()) item { Text("No matching dates.") }
            items(matches, key={ it.date }) { match ->
                Column(Modifier.fillMaxWidth().clickable(role=Role.Button) { open(match.date) }
                    .heightIn(min=64.dp).padding(vertical=12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(LocalDate.parse(match.date).label("EEEE, d MMM yyyy"), fontWeight=FontWeight.SemiBold)
                    Text(match.labels.joinToString(" · "), color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider()
            }
        } else for (number in latest.dayOfMonth downTo 1) {
            val date = month.atDay(number)
            val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            if (date == latest || date.dayOfWeek == DayOfWeek.SUNDAY) item(key="week-$monday") {
                Text(when(monday) { weekStart -> "This week"; weekStart.minusWeeks(1) -> "Last week"; else -> "Week of " + monday.label("d MMM") },
                    Modifier.padding(top=24.dp,bottom=8.dp).semantics { heading() },fontSize=18.sp,fontWeight=FontWeight.SemiBold)
                HorizontalDivider()
            }
            item(key=date.toString()) {
                HistoryRow(diary,date) { open(date.toString()) }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun HistoryRow(diary: Diary,date: LocalDate, open: () -> Unit) {
    val key = date.toString()
    val checks = diary.checks.filter { it.date == key }
    val day = diary.days.find { it.date == key }
    val activities = diary.activities.filter { it.date == key }
    val wearable = diary.wearable.find { it.date == key }
    val medicineNames = diary.doses.filter { it.date == key }.mapNotNull { dose -> diary.courses.find { it.id == dose.courseId }?.name }.distinct()
    val categories = categoryTags.keys.filter { activityFlag(day,activities,it) == true }
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clickable(role=Role.Button,onClick=open).heightIn(min=80.dp)
        .padding(vertical=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),
        verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.widthIn(min=54.dp)) {
            Text(date.label("EEE"),fontSize=13.sp,color=colors.onSurfaceVariant)
            Text(date.dayOfMonth.toString(),fontSize=25.sp,fontFamily=FontFamily.Serif)
        }
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
            val hasEntry = checks.isNotEmpty() || day != null || activities.isNotEmpty() || medicineNames.isNotEmpty()
            if (!hasEntry && wearable == null) Text("No entry", color = colors.onSurfaceVariant)
            if (hasEntry) {
                @Composable fun SlotLabel(slot:String) {
                    val energy = checks.find { it.slot == slot }?.energy?.let(energyLabels::get)
                    val recorded = energy != null || diary.answeredReminder(key, slot)
                    val status = energy ?: if (recorded) "Recorded" else "—"
                    val spokenStatus = energy ?: if (recorded) "Check-in saved" else "No check-in"
                    val morning = slot == "am"
                    val slotName = if (morning) "Morning" else "Evening"
                    Row(Modifier.clearAndSetSemantics { contentDescription = "$slotName, $spokenStatus" },
                        horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=Alignment.CenterVertically) {
                        Text(if(morning) "☼" else "☾",color=if(morning) colors.primary else colors.tertiary)
                        Text(status,style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.Medium)
                    }
                }
                if (LocalDensity.current.fontScale > 1.35f) { SlotLabel("am"); SlotLabel("pm") }
                else Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { SlotLabel("am") }; Box(Modifier.weight(1f)) { SlotLabel("pm") }
                }
                val labels = (medicineNames + categories + activities.flatMap { it.tags } +
                    listOfNotNull("No rests or naps".takeIf { restRecorded(day, activities) == false })).distinct()
                if (labels.isNotEmpty()) Row(horizontalArrangement=Arrangement.spacedBy(6.dp),
                    verticalAlignment=Alignment.CenterVertically) {
                    val icon = when(categories.firstOrNull()) { "Work" -> R.drawable.work_bag; "Socialising" -> R.drawable.cups; "Household" -> R.drawable.house; else -> R.drawable.symbook }
                    Icon(painterResource(icon),null,Modifier.size(16.dp),tint=colors.onSurfaceVariant)
                    Text(labels.joinToString(" · "),style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                }

            }
            wearable?.let { WearableSummary(it) }
        }
        Text("›",color=colors.onSurfaceVariant)
    }

}

@Composable
fun DateDetail(diary: Diary,date: String,edit:(String)->Unit) {
    val local = LocalDate.parse(date)
    val day = diary.days.find { it.date == date }
    val activities = diary.activities.filter { it.date == date }
    val checks = diary.checks.filter { it.date == date }
    val wearable = diary.wearable.find { it.date == date }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp),
        verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(local.label("EEEE"),fontFamily=FontFamily.Serif,fontSize=38.sp,lineHeight=44.sp)
        Text(local.label("d MMMM yyyy"),color=MaterialTheme.colorScheme.onSurfaceVariant)
        if (wearable == null && checks.isEmpty() && day == null && activities.isEmpty() && diary.doses.none { it.date == date }) Text("No entry")
        wearable?.let { reading ->
            Spacer(Modifier.height(12.dp))
            Text("Wearable readings", Modifier.semantics { heading() }, fontFamily = FontFamily.Serif, fontSize = 22.sp)
            WearableReadingValues(reading)
        }
        val largeType = LocalDensity.current.fontScale > 1.35f
        for ((slot, label) in listOf("am" to "Morning","pm" to "Evening")) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp), verticalAlignment=Alignment.CenterVertically) {
                Icon(painterResource(if (slot == "am") R.drawable.diary_sun else R.drawable.diary_moon), null,
                    Modifier.size(28.dp), tint=androidx.compose.ui.graphics.Color.Unspecified)
                Text(label,Modifier.weight(1f),fontSize=22.sp,fontFamily=FontFamily.Serif)
                if (!largeType) OutlinedButton(onClick = { edit(slot) }) { Text("Edit " + label.lowercase()) }
            }
            if (largeType) OutlinedButton(onClick = { edit(slot) }) { Text("Edit " + label.lowercase()) }
            val check = checks.find { it.slot == slot }
            Text("Energy · " + (check?.energy?.let(energyLabels::get) ?: "Not recorded"))
            if (slot == "am") Text("Waking refreshment · " + (check?.sleep?.let(sleepLabels::get) ?: "Not recorded"))
            if (slot == "pm") {
                Text("Overall wellbeing · " + (day?.wellbeing?.let(wellbeingLabels::get) ?: "No entry"))
                day?.stress?.let { Text("Stress · ${stressLabels[it]}") }
            }
            for ((name, value) in check?.symptoms.orEmpty()) Text("$name · ${symptomRatingLabel(value)}")
            if (slot == "pm") {
                day?.caffeineTimes?.let { Text("Caffeine · ${drinkHistoryLabel(it)}") }
                day?.alcoholTimes?.let { Text("Alcohol · ${drinkHistoryLabel(it)}") }
            }
            diary.courses.forEach { course ->
                val actual = diary.doses.filter { it.courseId == course.id && it.date == date && it.slot == slot }
                val plan = diary.scheduledRegimen(course, date)
                if (plan != null && plan.scheduledDoseDueIn(slot) && actual.none { !it.asNeeded }) {
                    Text(listOf(course.name, medicationAmount(plan.strength, plan.amount, plan.unit), ASSUMED_TAKEN_LABEL).filter { it.isNotBlank() }.joinToString(" · "))
                }
                actual.forEach { dose ->
                    Text(listOfNotNull(course.name, dose.status, dose.schedule, medicationAmount(dose.strength, dose.amount, dose.unit).ifBlank { null }, dose.time, dose.reason).joinToString(" · "))
                }
            }
            if (slot == "pm") {
                categoryTags.keys.forEach { name ->
                    val answer = when(activityFlag(day,activities,name)) { true -> "Yes"; false -> "No"; null -> "Unanswered" }
                    Text("$name · $answer")
                }
                val restAnswer = when (restRecorded(day, activities)) {
                    true -> "Yes"
                    false -> "No"
                    null -> "Unanswered"
                }
                Text("$REST_GROUP · $restAnswer")
                day?.impact?.let { Text("Recorded change to plans · $it") }
                day?.context.orEmpty().forEach { Text(it) }
            }
        }
        for (record in activities) {
            HorizontalDivider()
            Text(record.tags.joinToString(" · "),fontWeight=FontWeight.SemiBold)
            val details = if (record.restKind != null) listOfNotNull(record.restPeriod, record.duration,
                record.restEnergy?.let { "Energy afterwards · $it" })
                else if (record.workSetup) listOfNotNull(record.workLength, record.effort) + record.demands
                else listOfNotNull(record.workLength, record.workPlace, record.duration, record.effort) + record.demands
            if(details.isNotEmpty()) Text(details.joinToString(" · "))
            if (!record.workSetup) record.worth?.let { Text("Worthwhile · $it") }
        }
        val today = LocalDate.now()
        for ((offset, title) in listOf(-2L to "Two days earlier", -1L to "Previous day",
            1L to "Next morning", 2L to "Two mornings later")) {
            val nearby = local.plusDays(offset)
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Column(Modifier.padding(vertical=8.dp), verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(title, Modifier.semantics { heading() }, fontFamily=FontFamily.Serif, fontSize=22.sp)
                Text(nearby.label("EEEE, d MMMM yyyy"), color=MaterialTheme.colorScheme.onSurfaceVariant)
                nearbyDayLines(diary, nearby, today, includeEvening=offset < 0).forEach { Text(it) }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

private fun drinkHistoryLabel(times: Set<String>): String =
    if (times.isEmpty()) "Recorded, time unspecified" else drinkTimes.filter { it in times }.joinToString(" · ")

internal fun nearbyDayLines(diary: Diary, date: LocalDate, today: LocalDate, includeEvening: Boolean): List<String> {
    if (date > today) return listOf("Still to come")
    val key = date.toString()
    val checks = diary.checks.filter { it.date == key }
    fun energy(slot: String) = checks.find { it.slot == slot }?.energy?.let(energyLabels::get) ?: "No entry"
    return buildList {
        add("Morning energy · ${energy("am")}")
        if (includeEvening) {
            add("Evening energy · ${energy("pm")}")
            val day = diary.days.find { it.date == key }
            val activities = diary.activities.filter { it.date == key }
            val tags = activities.flatMap { it.tags }.distinct()
            val summary = categoryTags.keys.mapNotNull { category ->
                when (activityFlag(day, activities, category)) {
                    true -> category.takeUnless { tags.any { tag -> tag in categoryTags.getValue(category) } }
                    false -> "No ${category.lowercase()}"
                    null -> null
                }
            } + tags + listOfNotNull("No rests or naps".takeIf { restRecorded(day, activities) == false })
            if (summary.isNotEmpty()) add(summary.joinToString(" · "))
            if (!day?.context.isNullOrEmpty()) add(day?.context.orEmpty().joinToString(" · "))
        }
    }
}
