package com.lifementor.app.ui.calendar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import com.lifementor.app.data.GameStore
import com.lifementor.app.data.TaskAgenda
import com.lifementor.app.data.TaskCalendar
import com.lifementor.app.notify.CalendarBridge
import com.lifementor.app.ui.components.TaskDateAction
import com.lifementor.app.ui.components.TaskDateDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZonedDateTime

@Composable
fun CalendarScreen(onBack: () -> Unit, goGoals: () -> Unit, vm: CalendarViewModel = viewModel()) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val cellHeight = 52.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var today by remember { mutableStateOf(LocalDate.now()) }
    var clockRevision by remember { mutableIntStateOf(0) }
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var dayText by rememberSaveable { mutableStateOf(today.toString()) }
    var dateTaskId by rememberSaveable { mutableStateOf<Long?>(null) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    val month = YearMonth.parse(monthText)
    val selectedDay = LocalDate.parse(dayText)
    fun showMonth(value: YearMonth) {
        monthText = value.toString()
        dayText = value.atDay(selectedDay.dayOfMonth.coerceAtMost(value.lengthOfMonth())).toString()
    }
    DisposableEffect(lifecycle, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) clockRevision++
        }
        lifecycle.addObserver(observer)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { clockRevision++ }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { lifecycle.removeObserver(observer); context.unregisterReceiver(receiver) }
    }
    LaunchedEffect(GameStore.taskRevision.intValue, clockRevision) {
        while (isActive) {
            today = LocalDate.now()
            vm.refresh()
            delay(TaskAgenda.nextRefreshDelay(ZonedDateTime.now()))
        }
    }
    val data = vm.snapshot
    val dayTasks = data.byDate[selectedDay].orEmpty()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹ 返回") }
                Text("任务日历", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
        item {
            CalendarSyncPanel()
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { showMonth(month.minusMonths(1)) },
                            modifier = Modifier.semantics { contentDescription = "上个月" }) { Text("‹", fontSize = 24.sp) }
                        Text("${month.year}年${month.monthValue}月", Modifier.weight(1f), textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold)
                        TextButton(onClick = { showMonth(month.plusMonths(1)) },
                            modifier = Modifier.semantics { contentDescription = "下个月" }) { Text("›", fontSize = 24.sp) }
                        TextButton(onClick = {
                            today = LocalDate.now(); monthText = YearMonth.from(today).toString(); dayText = today.toString()
                        }) { Text("今天") }
                    }
                    Row(Modifier.fillMaxWidth()) {
                        listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                            Text(it, Modifier.weight(1f), textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    TaskCalendar.monthCells(month).chunked(7).forEach { week ->
                        Row(Modifier.fillMaxWidth()) {
                            week.forEach { date ->
                                if (date == null) Spacer(Modifier.weight(1f).height(cellHeight))
                                else key(date) {
                                    val count = data.byDate[date]?.size ?: 0
                                    val shape = RoundedCornerShape(8.dp)
                                    Column(Modifier.weight(1f).height(cellHeight).padding(2.dp)
                                        .background(if (date == selectedDay) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, shape)
                                        .then(if (date == today) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
                                        .selectable(selected = date == selectedDay, role = Role.Button,
                                            onClick = { dayText = date.toString() })
                                        .semantics { contentDescription = "$date，$count 项任务${if (date == today) "，今天" else ""}" },
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center) {
                                        Text("${date.dayOfMonth}", style = MaterialTheme.typography.bodyMedium)
                                        if (count > 0) Text(if (count > 99) "99+项" else "${count}项",
                                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            TextButton(onClick = goGoals) { Text("未安排日期：${data.unscheduledCount} 项 · 前往目标安排 →") }
            Text("进行中目标的任务", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("$selectedDay · ${dayTasks.size} 项 · 已完成 ${dayTasks.count { it.done }}",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("日历图标改日期。↗ 是手动添加，不参与批量更新；批量同步请用上方按钮。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        vm.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        message?.let { item { Text(it, style = MaterialTheme.typography.bodySmall) } }
        if (dayTasks.isEmpty()) item {
            Text("这一天还没有安排任务，可以从目标页为任务设置日期。", style = MaterialTheme.typography.bodyMedium)
        }
        items(dayTasks, key = { it.id }) { task ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = task.done, onCheckedChange = { vm.toggle(task.id) })
                    Column(Modifier.weight(1f)) {
                        Text(task.title, textDecoration = if (task.done) TextDecoration.LineThrough else null)
                        Text(data.goalTitles[task.goalId].orEmpty(), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TaskDateAction(task.title, task.dueDateText()) { dateTaskId = task.id }
                    IconButton(onClick = {
                        message = CalendarBridge.open(context, task, data.goalTitles[task.goalId].orEmpty())
                    }, modifier = Modifier.semantics { contentDescription = "将「${task.title}」添加到手机日历" }) {
                        Text("↗", fontSize = 22.sp)
                    }
                }
            }
        }
    }
    val dateTask = data.byDate.values.flatten().find { it.id == dateTaskId }
    if (dateTask != null) key(dateTask.id) {
        TaskDateDialog(dateTask.dueDate, onDismiss = { dateTaskId = null },
            onSave = { vm.setDate(dateTask.id, it); dateTaskId = null })
    }
}
