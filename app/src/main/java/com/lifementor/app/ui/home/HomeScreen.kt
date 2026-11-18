package com.lifementor.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifementor.app.data.Mentors
import com.lifementor.app.data.TaskItem
import com.lifementor.app.data.TaskAgenda
import com.lifementor.app.data.GameStore
import com.lifementor.app.notify.TaskReminders
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.ZonedDateTime
import com.lifementor.app.ui.components.DisplayTitle
import com.lifementor.app.ui.components.PaperChip
import com.lifementor.app.ui.components.ScrapCard
import com.lifementor.app.ui.components.StickerButton
import com.lifementor.app.ui.theme.Cardboard
import com.lifementor.app.ui.theme.Charcoal
import com.lifementor.app.ui.theme.InkBlack
import com.lifementor.app.ui.theme.InkSoft
import com.lifementor.app.ui.theme.PaperWhite
import com.lifementor.app.ui.theme.ScrapRed
import com.lifementor.app.ui.components.RadarChart
import com.lifementor.app.ui.components.TaskDateDialog
import com.lifementor.app.ui.components.TaskDateAction
import com.lifementor.app.ui.components.ExpandableCard

@Composable
fun HomeScreen(
    goGoals: () -> Unit,
    goMentors: () -> Unit,
    goCalendar: () -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val context = LocalContext.current
    var clockRevision by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycle, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                TaskReminders.reconcile(context)
                clockRevision++
            }
        }
        lifecycle.addObserver(observer)
        val timeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { clockRevision++ }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
        }
        ContextCompat.registerReceiver(context, timeReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose {
            lifecycle.removeObserver(observer)
            context.unregisterReceiver(timeReceiver)
        }
    }
    LaunchedEffect(GameStore.taskRevision.intValue, clockRevision) {
        while (isActive) {
            vm.refresh()
            delay(TaskAgenda.nextRefreshDelay(ZonedDateTime.now()))
        }
    }
    val s = vm.snap.value
    var dateTaskId by remember { mutableStateOf<Long?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            DisplayTitle("Journey", modifier = Modifier.weight(1f))
            PaperChip("Calendar", onClick = goCalendar)
        }
        // Character card
        ScrapCard(
            paperColor = PaperWhite,
            shadowColor = Charcoal.copy(alpha = 0.18f),
            rotation = -0.2f,
            fillWidth = true,
            contentPadding = PaddingValues(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    Modifier
                        .size(56.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(s.role.avatar, fontSize = 28.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "${s.role.nickname}  Lv.${s.level}",
                        style = MaterialTheme.typography.titleMedium,
                        color = InkBlack,
                    )
                    Text(
                        s.role.stage.ifBlank { "Life player" },
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSoft,
                    )
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { s.into.toFloat() / s.need },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = ScrapRed,
                        trackColor = Cardboard,
                    )
                    Text(
                        "${s.into}/${s.need} XP  ·  ${s.totalXp} XP total",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSoft,
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🏆", fontSize = 20.sp)
                    Text(
                        "${s.achievementsDone}/${s.achievementsTotal}",
                        style = MaterialTheme.typography.labelSmall,
                        color = InkBlack,
                    )
                }
            }
        }

        // 五维成长
        ExpandableCard("Five-dimension growth") {
            RadarChart(
                labels = s.radar.map { it.first },
                values = s.radar.map { it.second },
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().height(220.dp),
            )
            Row(Modifier.fillMaxWidth()) {
                s.radar.forEachIndexed { i, p ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(p.first, style = MaterialTheme.typography.labelSmall)
                        Text("${s.radarXp[i]}", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        if (s.role.vision.isNotBlank()) {
            Text(
                "\u201c${s.role.vision}\u201d",
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                color = InkSoft,
            )
        }

        vm.error.value?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        AgendaSection("Today", s.agenda.today, "No actions scheduled for today", s.goalTitles, vm) { dateTaskId = it }
        TextButton(onClick = goGoals) { Text("Add actions / schedule dates →") }
        AgendaSection("Reschedule", s.agenda.overdue, "No actions need rescheduling", s.goalTitles, vm) { dateTaskId = it }
        AgendaSection("Upcoming", s.agenda.future, "No upcoming actions", s.goalTitles, vm) { dateTaskId = it }
        AgendaSection("Unscheduled", s.agenda.unscheduled, "All actions have dates", s.goalTitles, vm) { dateTaskId = it }

        StickerButton("Talk to a mentor", onClick = goMentors, arrow = true)
        Spacer(Modifier.height(8.dp))
    }

    val dateTask = s.agenda.run { today + overdue + future + unscheduled }
        .find { it.id == dateTaskId }
    if (dateTask != null) {
        key(dateTask.id) {
            TaskDateDialog(
                dueDate = dateTask.dueDate,
                onDismiss = { dateTaskId = null },
                onSave = { date ->
                    vm.setDueDate(dateTask, date)
                    dateTaskId = null
                },
            )
        }
    }
}

@Composable
private fun AgendaSection(
    title: String,
    tasks: List<TaskItem>,
    emptyText: String,
    goalTitles: Map<Long, String>,
    vm: HomeViewModel,
    onSetDate: (Long) -> Unit,
) {
    ExpandableCard("$title · ${tasks.size}", initiallyExpanded = title == "Today" || title == "Reschedule") {
        if (tasks.isEmpty()) {
            Text(emptyText, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        tasks.forEach { task ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = false, onCheckedChange = { vm.complete(task) })
                Column(Modifier.weight(1f)) {
                    Text(task.title, fontSize = 14.sp)
                    Text("${goalTitles[task.goalId].orEmpty()} · ${task.dueDateText() ?: "No date"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TaskDateAction(task.title, task.dueDateText()) { onSetDate(task.id) }
            }
        }
        if (tasks.isNotEmpty()) {
            Text("First completion of each action: +10 XP", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
