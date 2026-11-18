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
private object DiaryOperations {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val lock = Mutex()
    fun enqueue(operation: suspend () -> Unit) {
        scope.launch { lock.withLock { operation() } }
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

    init { load() }

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
        val writeRevision = ++revision
        val replace = pendingRestore
        status = if (replace) "Restoring…" else "Saving…"
        DiaryOperations.enqueue {
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
        val next = transform(diary ?: return)
        if (next == diary) return
        diary = next
        persist(next)
    }

    fun restore(value: Diary) {
        pendingRestore = true
        diary = value
        persist(value)
    }

    fun retry() {
        diary?.let(::persist) ?: load()
    }

    override fun onCleared() {
        DiaryOperations.enqueue { storage.close() }
    }
}
