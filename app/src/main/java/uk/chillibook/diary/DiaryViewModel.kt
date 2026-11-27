package uk.chillibook.diary

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

// A tap already accepted by Today must finish even if Back immediately closes the activity.
// Serialize new screens' opening reads behind the preceding screen's writes and close.
internal object DiaryOperations {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val lock = Mutex()
    suspend fun serialized(operation: suspend () -> Unit) = lock.withLock { operation() }
    fun enqueue(operation: suspend () -> Unit) {
        scope.launch { serialized(operation) }
    }
}

class DiaryViewModel(app: Application) : AndroidViewModel(app) {
    private val storage = Storage(app)
    internal val updateBackup = UpdateBackup(app)
    internal var updateBackupAt by mutableStateOf<Long?>(null)
        private set
    private val wearablePrefs = app.getSharedPreferences("wearable_connection", Context.MODE_PRIVATE)
    internal var wearableEnabled by mutableStateOf(wearablePrefs.getBoolean("enabled", false))
        private set
    internal var wearableSyncing by mutableStateOf(false)
        private set
    internal var wearableStatus by mutableStateOf<String?>(null)
        private set
    internal var committed by mutableStateOf(Diary())
        private set
    var diary by mutableStateOf<Diary?>(null)
        private set
    var status by mutableStateOf("")
        private set
    private val completedDays = MutableSharedFlow<DayCompletion>(extraBufferCapacity = 1)
    internal val dayCompletions = completedDays.asSharedFlow()
    private val feedbackCues = MutableSharedFlow<FeedbackCue>(
        extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    internal val feedback = feedbackCues.asSharedFlow()
    private val feedbackPrefs = app.getSharedPreferences("feedback", Context.MODE_PRIVATE)
    internal var feedbackEnabled by mutableStateOf(
        FeedbackOption.entries.filterTo(mutableSetOf()) { feedbackPrefs.getBoolean(it.name, false) }.toSet()
    )
        private set

    internal fun setFeedback(option: FeedbackOption, enabled: Boolean) {
        val next = if (enabled) feedbackEnabled + option else feedbackEnabled - option
        feedbackPrefs.edit().putBoolean(option.name, enabled).apply()
        openModels.forEach { it.feedbackEnabled = next }
    }

    private val rewards = app.getSharedPreferences("day_rewards", Context.MODE_PRIVATE)
    var celebrationDay by mutableStateOf(rewards.getString(EARNED_ON, null))
        private set
    private var pendingEditDate: String? = null
    private var pendingRestore = false
    private var revision = 0L

    var resetting by mutableStateOf(false)
        private set
    var resetGeneration by mutableIntStateOf(0)
        private set

    // More than one activity can be alive when a notification opens the diary.
    private companion object {
        const val EARNED_ON = "earned_on"
        val openModels = mutableSetOf<DiaryViewModel>()
        var wearableJob: Job? = null
        var lastWearableRefresh: Long? = null
    }

    init {
        openModels.add(this)
        load()
    }

    private fun acceptReset(empty: Diary) {
        committed = empty
        diary = empty
        feedbackEnabled = emptySet()
        pendingRestore = false
        pendingEditDate = null
        resetGeneration++
        status = "Diary reset"
    }

    fun reset() {
        if (resetting || diary == null) return
        resetting = true
        wearableJob?.cancel()
        revision++
        status = "Resetting diary…"
        DiaryOperations.enqueue {
            try {
                withContext(Dispatchers.IO) { updateBackup.clear() }
                openModels.forEach { it.updateBackupAt = null }
                val empty = Diary()
                storage.replace(empty)
                setWearableEnabled(false)
                clearRewards()
                feedbackPrefs.edit().clear().apply()
                openModels.forEach { it.acceptReset(empty) }
                try {
                    Reminders.clear(getApplication())
                } catch (_: RuntimeException) {
                    status = "Diary reset. Couldn’t clear reminders."
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                status = "Couldn’t reset diary. Your entries are still here."
            } finally {
                resetting = false
            }
        }
    }

    fun refreshReminders() {
        if (diary != null) Reminders.reconcile(getApplication(), committed)
    }

    private fun load() {
        status = "Opening diary…"
        DiaryOperations.enqueue {
            try {
                updateBackupAt = withContext(Dispatchers.IO) {
                    try { updateBackup.modifiedAt() } catch (_: SecurityException) { null }
                }
                committed = storage.read()
                diary = committed
                status = ""
                refreshReminders()
                refreshWearable()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { status = "Couldn’t open diary" }
        }
    }

    internal suspend fun prepareUpdateBackup() = DiaryOperations.serialized {
        check(diary != null && !hasPendingDiaryChanges())
        val snapshot = storage.read()
        val savedAt = withContext(Dispatchers.IO) {
            updateBackup.write(snapshot)
            checkNotNull(updateBackup.modifiedAt())
        }
        openModels.forEach { it.updateBackupAt = savedAt }
        check(!hasPendingDiaryChanges())
    }

    private fun hasPendingDiaryChanges() = openModels.any { model ->
        model.resetting || model.diary != null && model.diary != model.committed
    }

    internal fun setWearableEnabled(enabled: Boolean) {
        wearablePrefs.edit().putBoolean("enabled", enabled).apply()
        lastWearableRefresh = null
        if (!enabled) wearableJob?.cancel()
        openModels.forEach {
            it.wearableEnabled = enabled
            it.wearableStatus = null
        }
        if (enabled) refreshWearable(force = true)
    }

    internal fun refreshWearable(force: Boolean = false) {
        if (!wearableEnabled || diary == null || wearableJob != null || busyReplacingDiary()) return
        val elapsed = SystemClock.elapsedRealtime()
        // Rotations and repeated foreground visits can follow the same sync closely.
        if (!force && lastWearableRefresh?.let { elapsed - it < 5 * 60_000 } == true) return
        lastWearableRefresh = elapsed
        openModels.forEach { it.wearableSyncing = true; it.wearableStatus = null }
        wearableJob = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val app = getApplication<Application>()
                val granted = WearableHealthConnect.granted(app).intersect(WearableHealthConnect.permissions)
                if (granted.isEmpty()) {
                    reportWearable("Allow steps or sleep in Health Connect to import readings.")
                    return@launch
                }
                val readings = WearableHealthConnect.read(app, granted)
                DiaryOperations.serialized {
                    if (!wearableEnabled || busyReplacingDiary()) return@serialized
                    if (readings.isEmpty()) reportWearable("No recent readings found. Sync your band in its app, then try again.")
                    else {
                        saveWearable(readings)
                        reportWearable("Readings updated.")
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { reportWearable("Couldn’t read Health Connect. Try again when it’s available.") }
            finally {
                wearableJob = null
                openModels.forEach { it.wearableSyncing = false }
            }
        }
        wearableJob?.start()
    }

    private fun busyReplacingDiary() = openModels.any { it.resetting || it.pendingRestore }

    private fun reportWearable(message: String) {
        openModels.forEach { it.wearableStatus = message }
    }

    private suspend fun saveWearable(readings: List<WearableReading>) {
        val before = storage.read()
        val byDate = before.wearable.associateBy { it.date }.toMutableMap()
        for (reading in readings) {
            val old = byDate[reading.date]
            byDate[reading.date] = reading.copy(
                steps = reading.steps ?: old?.steps,
                stepsSource = if (reading.steps != null) reading.stepsSource else old?.stepsSource,
                sleepMinutes = reading.sleepMinutes ?: old?.sleepMinutes,
                sleepSource = if (reading.sleepMinutes != null) reading.sleepSource else old?.sleepSource,
                sleepDetail = if (reading.sleepMinutes != null) reading.sleepDetail else old?.sleepDetail,
                sleepPeriods = if (reading.sleepMinutes != null) reading.sleepPeriods else old?.sleepPeriods,
                stepHours = reading.stepHours ?: old?.stepHours
            )
        }
        val merged = byDate.values.sortedBy { it.date }
        if (merged != before.wearable) storage.save(before, before.copy(wearable = merged))
        showWearable(merged)
    }

    private fun showWearable(readings: List<WearableReading>) {
        openModels.forEach {
            it.committed = it.committed.copy(wearable = readings)
            it.diary = it.diary?.copy(wearable = readings)
        }
    }

    private fun persist(target: Diary, editDate: String?) {
        val generation = resetGeneration
        val writeRevision = ++revision
        val replace = pendingRestore
        status = if (replace) "Restoring…" else "Saving…"
        DiaryOperations.enqueue {
            if (generation != resetGeneration) return@enqueue
            try {
                val before = committed
                val written = if (replace) target else target.copy(wearable = before.wearable)
                if (replace) storage.replace(written) else storage.save(before, written)
                committed = written
                if (replace) {
                    showWearable(written.wearable)
                    clearRewards()
                } else {
                    val celebrated = editDate != null && newlyCompletedDay(before, target, editDate) &&
                        celebrate(editDate)
                    val cue = if (celebrated) FeedbackCue.Celebrate else diaryEditCue(before, target, editDate)
                    cue?.let(feedbackCues::tryEmit)
                }
                refreshReminders()
                if (revision == writeRevision) {
                    status = if (replace) "Diary restored" else "Saved"
                    pendingRestore = false
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (revision == writeRevision) status = "Couldn’t save"
            }
        }
    }

    private fun celebrate(entryDate: String): Boolean {
        val entryKey = "completed_$entryDate"
        val earnedOn = LocalDate.now().toString()
        val sameDay = rewards.getString(EARNED_ON, null) == earnedOn
        if (sameDay && rewards.contains(entryKey)) return false
        val update = rewards.edit()
        if (!sameDay) update.clear()
        update.putBoolean(entryKey, true).putString(EARNED_ON, earnedOn).apply()
        openModels.forEach { it.celebrationDay = earnedOn }
        completedDays.tryEmit(DayCompletion(entryDate, committed.checkInStreak(entryDate, earnedOn)))
        return true
    }

    private fun clearRewards() {
        rewards.edit().clear().apply()
        openModels.forEach { it.celebrationDay = null }
    }

    fun edit(transform: (Diary) -> Diary) = editCheckIn(null, transform)

    fun editCheckIn(date: String?, transform: (Diary) -> Diary) {
        if (resetting) return
        val next = transform(diary ?: return)
        if (next == diary) return
        diary = next
        pendingEditDate = date
        persist(next, date)
    }

    fun restore(value: Diary) {
        if (resetting) return
        wearableJob?.cancel()
        pendingRestore = true
        diary = value
        pendingEditDate = null
        persist(value, null)
    }

    fun retry() {
        if (resetting) return
        diary?.let { persist(it, pendingEditDate) } ?: load()
    }

    override fun onCleared() {
        DiaryOperations.enqueue {
            openModels.remove(this)
            storage.close()
        }
    }
}
