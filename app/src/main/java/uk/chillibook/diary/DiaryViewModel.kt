package uk.chillibook.diary

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    private var pendingRestore = false
    private var revision = 0L

    var resetting by mutableStateOf(false)
        private set
    var resetGeneration by mutableIntStateOf(0)
        private set

    // More than one activity can be alive when a notification opens the diary.
    private companion object {
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

    private fun persist(target: Diary) {
        val generation = resetGeneration
        val writeRevision = ++revision
        val replace = pendingRestore
        status = if (replace) "Restoring…" else "Saving…"
        DiaryOperations.enqueue {
            if (generation != resetGeneration) return@enqueue
            try {
                if (replace) storage.replace(target) else storage.save(committed, target)
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

    fun edit(transform: (Diary) -> Diary) {
        if (resetting) return
        val next = transform(diary ?: return)
        if (next == diary) return
        diary = next
        persist(next)
    }

    fun restore(value: Diary) {
        if (resetting) return
        pendingRestore = true
        diary = value
        persist(value)
    }

    fun retry() {
        if (resetting) return
        diary?.let(::persist) ?: load()
    }

    override fun onCleared() {
        DiaryOperations.enqueue {
            openModels.remove(this)
            storage.close()
        }
    }
}
