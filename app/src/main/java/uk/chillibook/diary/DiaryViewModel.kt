package uk.chillibook.diary

import android.app.Application
import android.content.Context
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private var committed = Diary()
    var diary by mutableStateOf<Diary?>(null)
        private set
    var status by mutableStateOf("")
        private set
    private val completedDays = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val dayCompletions = completedDays.asSharedFlow()
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
    }

    init {
        openModels.add(this)
        load()
    }

    private fun acceptReset(empty: Diary) {
        committed = empty
        diary = empty
        pendingRestore = false
        pendingEditDate = null
        resetGeneration++
        status = "Diary reset"
    }

    fun reset() {
        if (resetting || diary == null) return
        resetting = true
        revision++
        status = "Resetting diary…"
        DiaryOperations.enqueue {
            try {
                val empty = Diary()
                storage.replace(empty)
                clearRewards()
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
                committed = storage.read()
                diary = committed
                status = ""
                refreshReminders()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { status = "Couldn’t open diary" }
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
                if (replace) storage.replace(target) else storage.save(committed, target)
                if (replace) {
                    clearRewards()
                } else if (editDate != null && newlyCompletedDay(committed, target, editDate)) {
                    celebrate(editDate)
                }
                committed = target
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

    private fun celebrate(entryDate: String) {
        val entryKey = "completed_$entryDate"
        val earnedOn = LocalDate.now().toString()
        val sameDay = rewards.getString(EARNED_ON, null) == earnedOn
        if (sameDay && rewards.contains(entryKey)) return
        val update = rewards.edit()
        if (!sameDay) update.clear()
        update.putBoolean(entryKey, true).putString(EARNED_ON, earnedOn).apply()
        openModels.forEach { it.celebrationDay = earnedOn }
        completedDays.tryEmit(entryDate)
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
