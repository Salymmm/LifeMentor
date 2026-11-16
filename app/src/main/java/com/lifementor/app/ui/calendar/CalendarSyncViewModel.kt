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
                if (targets.isEmpty()) message = "没有可写入的日历，请先在手机日历中添加账户或创建日历，再重试。"
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { message = "读取日历失败，请检查日历权限和日历账户后重试。" }
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
        catch (_: Exception) { message = "任务数据读取失败，未执行同步。请先检查 App 中的任务是否正常。"; return }
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
                message = "同步完成：新增 ${plan.inserts.size}，更新 ${plan.updates.size}，移除 ${plan.deletes.size}，未变化 ${plan.unchanged}。" +
                    if (revision != GameStore.taskRevision.intValue) "任务已发生变化，请再同步一次以更新最新内容。" else ""
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: SecurityException) { message = "日历权限已关闭，请重新允许后再同步。" }
            catch (_: Exception) { message = "同步未完成，请检查目标日历仍可写后重试。再次同步会核对已写入的日程。" }
            finally { busy = false }
        }
    }
}
