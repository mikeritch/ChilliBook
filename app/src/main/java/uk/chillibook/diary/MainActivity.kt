package uk.chillibook.diary

import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
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
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import java.time.LocalTime

private data class ReminderTarget(val date: String, val slot: String)

class MainActivity : ComponentActivity() {
    private var reminderTarget by mutableStateOf<ReminderTarget?>(null)
    private var diaryModel: DiaryViewModel? = null

    private var localDay by mutableStateOf(LocalDate.now())
    private val calendarChanged = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { localDay = LocalDate.now() }
    }

    override fun onStart() {
        super.onStart()
        val changes = IntentFilter(Intent.ACTION_DATE_CHANGED).apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(this, calendarChanged, changes, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStop() {
        unregisterReceiver(calendarChanged)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        localDay = LocalDate.now()
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
            DiaryTheme(dark) { DiaryHome(vm, localDay.toString(), reminderTarget) { reminderTarget = null } }
        }
    }
}

@Composable
private fun DiaryHome(vm: DiaryViewModel, localDay: String, target: ReminderTarget?, consume: () -> Unit) {
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val player = remember(view) { FeedbackPlayer(view.context) }
    DisposableEffect(player) {
        onDispose { player.close() }
    }
    LaunchedEffect(vm, player, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try {
                vm.feedback.collect { cue ->
                    val celebration = cue == FeedbackCue.Celebrate
                    val enabled = vm.feedbackEnabled
                    val sound = if (celebration) FeedbackOption.CelebrationSound else FeedbackOption.EverydaySound
                    val haptics = if (celebration) FeedbackOption.CelebrationHaptics else FeedbackOption.EverydayHaptics
                    player.play(view, cue, sound = sound in enabled, haptic = haptics in enabled)
                }
            } finally {
                player.stop()
            }
        }
    }
    fun preview(option: FeedbackOption): Boolean {
        val cue = when (option) {
            FeedbackOption.EverydaySound, FeedbackOption.EverydayHaptics -> FeedbackCue.Select
            FeedbackOption.CelebrationSound, FeedbackOption.CelebrationHaptics -> FeedbackCue.Celebrate
        }
        val sound = option == FeedbackOption.EverydaySound || option == FeedbackOption.CelebrationSound
        return player.play(view, cue, sound = sound, haptic = !sound)
    }
    var tab by rememberSaveable { mutableStateOf("Today") }
    var medications by rememberSaveable { mutableStateOf(false) }
    var medTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var patternVisit by rememberSaveable { mutableIntStateOf(0) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var detail by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var editSlot by rememberSaveable { mutableStateOf("am") }
    var date by rememberSaveable { mutableStateOf(localDay) }
    var previousLocalDay by rememberSaveable { mutableStateOf(localDay) }
    LaunchedEffect(localDay) {
        if (date == previousLocalDay) date = localDay
        previousLocalDay = localDay
    }
    var slot by rememberSaveable { mutableStateOf(if (LocalTime.now().hour < 15) "am" else "pm") }
    val holder = key(vm.resetGeneration) { rememberSaveableStateHolder() }
    var todayVisit by rememberSaveable { mutableIntStateOf(0) }
    var seenReset by rememberSaveable { mutableIntStateOf(vm.resetGeneration) }
    LaunchedEffect(vm.resetGeneration) {
        if (seenReset != vm.resetGeneration) {
            medications = false
            medTarget = null
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
            medications = false
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
            medications -> medications = false
            editing -> editing = false
            detail != null -> detail = null
            else -> tab = "Today"
        }
    }
    BackHandler(settings || medications || detail != null || tab != "Today", onBack = back)
    val colors = MaterialTheme.colorScheme
    val checkInDate = when {
        settings || medications -> null
        editing -> detail
        tab == "Today" && detail == null -> date
        else -> null
    }
    val celebration = key(localDay, vm.resetGeneration) { rememberDayCelebration(checkInDate, vm.dayCompletions) }
    val saved = vm.committed
    val streak = remember(saved, checkInDate, localDay) {
        if (checkInDate == null) 0 else saved.checkInStreak(checkInDate, localDay)
    }
    val earned = checkInDate != null && vm.celebrationDay == localDay
    Surface(color = colors.background, contentColor = colors.onBackground, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp,end = 10.dp).heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    if (settings || medications || detail != null) TextButton(onClick = back) { Text("‹  Back") }
                    DayThanksHeading(
                        title = when {
                            settings -> "Settings"
                            medications -> "Medication history"
                            detail != null -> "Diary entry"
                            tab != "Today" -> "ChilliBook"
                            date == localDay -> "Today"
                            else -> "Check-in"
                        },
                        earned = earned, streak = streak, animation = celebration?.progress, modifier = Modifier.weight(1f)
                    )
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
                        settings -> SettingsScreen(diary, vm::edit, vm::restore, vm.status, vm::retry, back,
                            vm::reset, vm.resetting, vm.feedbackEnabled, vm::setFeedback, ::preview,
                            refreshReminders = vm::refreshReminders)
                        medications -> Column {
                            Box(Modifier.weight(1f)) {
                                MedicationManagement(diary, vm::edit) { course ->
                                    holder.removeState("Patterns-$patternVisit")
                                    patternVisit++
                                    medTarget = course
                                    editing = false
                                    detail = null
                                    medications = false
                                    tab = "Patterns"
                                }
                            }
                            SaveNotice(vm.status, vm::retry)
                        }
                        detail != null && editing -> holder.SaveableStateProvider("edit-${detail}") {
                            TodayScreen(diary,saved,detail!!,editSlot,{detail=it},{editSlot=it},{ change -> vm.editCheckIn(detail, change) },vm.status,vm::retry) { medications = true }
                        }
                        detail != null -> holder.SaveableStateProvider("day-${detail}") {
                            DateDetail(diary, detail!!) { editSlot = it; editing = true }
                        }
                        else -> holder.SaveableStateProvider(if (tab == "Today") "Today-$todayVisit" else if (tab == "Patterns") "Patterns-$patternVisit" else tab) {
                            when (tab) {
                                "Today" -> TodayScreen(diary,saved,date,slot,{date=it},{slot=it},{ change -> vm.editCheckIn(date, change) },vm.status,vm::retry) { medications = true }
                                "History" -> HistoryScreen(diary, { medications = true }) { detail=it }
                                else -> PatternsScreen(diary,{detail=it},{tab="Today"; date=LocalDate.now().toString()}, medTarget)
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
                                settings = false; medications = false; detail = null; editing = false; tab = name
                            }, icon = { Icon(painterResource(icon), null, Modifier.size(22.dp)) },
                            label = { Text(name) }, colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = colors.primary, selectedTextColor = colors.primary,
                                indicatorColor = colors.secondaryContainer, unselectedIconColor = colors.onSurfaceVariant))
                        }
                }
            }
            if (earned && celebration != null) {
                DayCelebrationBurst({ celebration.progress.value }, celebration.streak, Modifier.matchParentSize())
            }
        }
    }
}
