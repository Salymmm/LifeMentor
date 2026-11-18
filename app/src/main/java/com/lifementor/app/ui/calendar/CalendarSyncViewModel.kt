package com.lifementor.app.ui.calendar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifementor.app.data.AppStorage
import com.lifementor.app.data.GameStore
import com.lifementor.app.notify.CalendarSyncProvider
import com.lifementor.app.notify.CalendarSyncTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CalendarSyncViewModel : ViewModel() {
    private val provider = CalendarSyncProvider(AppStorage.appCtx)
    var targets by mutableStateOf<List<CalendarSyncTarget>>(emptyList())
        private set
    var selectedId by mutableStateOf(provider.selectedId())
        private set
    var busy by mutableStateOf(false)
        private set
    var choosing by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    fun dismissPicker() { choosing = false }
    fun refreshTargets() { loadTargets(false) }
    fun beginSync() { loadTargets(true) }

    private fun loadTargets(syncAfterLoad: Boolean) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            var loaded = false
            try {
                targets = withContext(Dispatchers.IO) { provider.calendars() }
                loaded = true
                if (targets.isEmpty()) message = "No writable calendars found. Add an account or create a calendar in your calendar app, then try again."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { message = "Could not load calendars. Check calendar permissions and accounts, then try again." }
            finally { busy = false }
            if (loaded && syncAfterLoad && targets.isNotEmpty()) {
                val selected = targets.find { it.id == selectedId }
                if (selected == null) choosing = true else synchronize(selected.id, false)
            }
        }
    }

    fun chooseTarget() {
        if (!busy && targets.isNotEmpty()) choosing = true
        else if (!busy) loadTargets(true)
    }

    fun selectAndSync(id: Long) { synchronize(id, true) }

    private fun synchronize(id: Long, saveSelection: Boolean) {
        if (busy) return
        val source = try { GameStore.loadCalendarSyncSource() }
        catch (_: Exception) { message = "Could not read task data. Nothing was synced. Check your tasks in the app first."; return }
        val revision = GameStore.taskRevision.intValue
        choosing = false
        busy = true
        message = null
        viewModelScope.launch {
            try {
                if (saveSelection) {
                    withContext(Dispatchers.IO) { provider.select(id) }
                    selectedId = id
                }
                val plan = withContext(Dispatchers.IO) {
                    provider.synchronize(id, source)
                }
                selectedId = id
                message = "Sync complete: ${plan.inserts.size} added, ${plan.updates.size} updated, ${plan.deletes.size} removed, ${plan.unchanged} unchanged. " +
                    if (revision != GameStore.taskRevision.intValue) "Tasks changed during sync. Sync again to include the latest changes." else ""
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: SecurityException) { message = "Calendar access was revoked. Allow access again before syncing." }
            catch (_: Exception) { message = "Sync did not finish. Check that the selected calendar is still writable, then retry. Syncing again checks events already written." }
            finally { busy = false }
        }
    }
}
