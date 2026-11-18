package uk.chillibook.diary

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import java.time.LocalTime

private data class ReminderTarget(val date: String, val slot: String)

class MainActivity : ComponentActivity() {
    private var reminderTarget by mutableStateOf<ReminderTarget?>(null)
    private var diaryModel: DiaryViewModel? = null

    override fun onResume() {
        super.onResume()
        diaryModel?.refreshReminders()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readReminder(intent)
    }

    private fun readReminder(intent: Intent) {
        val slot = intent.getStringExtra(Reminders.EXTRA_SLOT) ?: return
        val date = intent.getStringExtra(Reminders.EXTRA_DATE) ?: return
        if (slot in listOf("am", "pm") && date.length == 10 &&
            runCatching { LocalDate.parse(date) }.isSuccess) {
            reminderTarget = ReminderTarget(date, slot)
        }
        intent.removeExtra(Reminders.EXTRA_SLOT)
        intent.removeExtra(Reminders.EXTRA_DATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readReminder(intent)
        enableEdgeToEdge()
        setContent {
            val vm: DiaryViewModel = viewModel()
            val dark = when(vm.diary?.prefs?.theme) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            val view = LocalView.current
            SideEffect {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            SideEffect { diaryModel = vm }
            DiaryTheme(dark) { DiaryHome(vm, reminderTarget) { reminderTarget = null } }
        }
    }
}

@Composable
private fun DiaryHome(vm: DiaryViewModel, target: ReminderTarget?, consume: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf("Today") }
    var settings by rememberSaveable { mutableStateOf(false) }
    var detail by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var editSlot by rememberSaveable { mutableStateOf("am") }
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var slot by rememberSaveable { mutableStateOf(if (LocalTime.now().hour < 15) "am" else "pm") }
    val holder = key(vm.resetGeneration) { rememberSaveableStateHolder() }
    var todayVisit by rememberSaveable { mutableIntStateOf(0) }
    var seenReset by rememberSaveable { mutableIntStateOf(vm.resetGeneration) }
    LaunchedEffect(vm.resetGeneration) {
        if (seenReset != vm.resetGeneration) {
            seenReset = vm.resetGeneration
            detail = null
            editing = false
            tab = "Today"
            date = LocalDate.now().toString()
            slot = if (LocalTime.now().hour < 15) "am" else "pm"
        }
    }
    LaunchedEffect(target) {
        if (target != null) {
            holder.removeState("Today-$todayVisit")
            todayVisit++
            settings = false
            editing = false
            detail = null
            date = target.date
            slot = target.slot
            tab = "Today"
            consume()
        }
    }
    val back = {
        when {
            settings -> settings = false
            editing -> editing = false
            detail != null -> detail = null
            else -> tab = "Today"
        }
    }
    BackHandler(settings || detail != null || tab != "Today", onBack = back)
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.background, contentColor = colors.onBackground, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp,end = 10.dp).heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (settings || detail != null) TextButton(onClick = back) { Text("‹  Back") }
                Text(if (settings) "Settings" else if (detail != null) "Diary entry" else if (tab == "Today") "Today" else "ChilliBook",
                    Modifier.weight(1f), color = colors.primary, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                if (!settings) IconButton(onClick = { settings = true }) {
                    Icon(painterResource(R.drawable.settings), "Settings", Modifier.size(21.dp))
                }
            }
            Box(Modifier.weight(1f)) {
                val diary = vm.diary
                when {
                    diary == null -> Column(Modifier.padding(20.dp)) {
                        Text(vm.status)
                        if (vm.status.startsWith("Couldn’t")) TextButton(onClick = vm::retry) { Text("Retry") }
                    }
                    settings -> SettingsScreen(diary, vm::edit, vm::restore, vm.status, vm::retry, back, vm::reset, vm.resetting)
                    detail != null && editing -> holder.SaveableStateProvider("edit-${detail}") {
                        TodayScreen(diary,detail!!,editSlot,{detail=it},{editSlot=it},vm::edit,vm.status,vm::retry)
                    }
                    detail != null -> holder.SaveableStateProvider("day-${detail}") {
                        DateDetail(diary, detail!!) { editSlot = it; editing = true }
                    }
                    else -> holder.SaveableStateProvider(if (tab == "Today") "Today-$todayVisit" else tab) {
                        when (tab) {
                            "Today" -> TodayScreen(diary,date,slot,{date=it},{slot=it},vm::edit,vm.status,vm::retry)
                            "History" -> HistoryScreen(diary) { detail=it }
                            else -> PatternsScreen(diary,{detail=it},{tab="Today"; date=LocalDate.now().toString()})
                        }
                    }
                }
            }
            HorizontalDivider(color = colors.outlineVariant)
            NavigationBar(containerColor = colors.background, tonalElevation = 0.dp,
                windowInsets = WindowInsets(0), modifier = Modifier.fillMaxWidth()) {
                listOf("Today" to R.drawable.nav_today,"History" to R.drawable.nav_history,"Patterns" to R.drawable.nav_patterns)
                    .forEach { (name, icon) ->
                        NavigationBarItem(selected = tab == name && !settings, onClick = {
                            settings = false; detail = null; editing = false; tab = name
                        }, icon = { Icon(painterResource(icon), null, Modifier.size(22.dp)) },
                        label = { Text(name) }, colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = colors.primary, selectedTextColor = colors.primary,
                            indicatorColor = colors.secondaryContainer, unselectedIconColor = colors.onSurfaceVariant))
                    }
            }
        }
    }
}
