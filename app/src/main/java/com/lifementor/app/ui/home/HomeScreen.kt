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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifementor.app.data.Mentors
import com.lifementor.app.data.TaskItem
import com.lifementor.app.notify.TaskReminders
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import com.lifementor.app.ui.components.RadarChart

@Composable
fun HomeScreen(
    goGoals: () -> Unit,
    goMentors: () -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val context = LocalContext.current
    DisposableEffect(lifecycle, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                TaskReminders.reconcile(context)
                vm.refresh()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        while (isActive) {
            vm.refresh()
            val now = ZonedDateTime.now()
            delay(Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(now.zone))
                .toMillis().coerceAtLeast(1000))
        }
    }
    val s = vm.snap.value

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 角色卡
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
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
                        "${s.role.nickname}　Lv.${s.level}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        s.role.stage.ifBlank { "人生玩家" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { s.into.toFloat() / s.need },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                    )
                    Text(
                        "${s.into}/${s.need} XP　·　总 ${s.totalXp} XP",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🏆", fontSize = 20.sp)
                    Text(
                        "${s.achievementsDone}/${s.achievementsTotal}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }

        if (s.role.vision.isNotBlank()) {
            Text(
                "「${s.role.vision}」",
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        vm.error.value?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        AgendaSection("今天", s.agenda.today, "今天没有已安排的行动", s.goalTitles, vm)
        TextButton(onClick = goGoals) { Text("添加行动 / 安排日期 →") }
        AgendaSection("待重新安排", s.agenda.overdue, "没有需要重新安排的行动", s.goalTitles, vm)
        AgendaSection("未来", s.agenda.future, "还没有未来的安排", s.goalTitles, vm)
        AgendaSection("未安排", s.agenda.unscheduled, "所有行动都已安排日期", s.goalTitles, vm)

        // 五维成长
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("五维成长", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
        }

        TextButton(onClick = goMentors) { Text("找导师聊聊 →") }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun AgendaSection(
    title: String,
    tasks: List<TaskItem>,
    emptyText: String,
    goalTitles: Map<Long, String>,
    vm: HomeViewModel,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text("$title · ${tasks.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (tasks.isEmpty()) {
                Text(emptyText, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            tasks.forEach { task ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = false, onCheckedChange = { vm.complete(task) })
                    Column(Modifier.weight(1f)) {
                        Text(task.title, fontSize = 14.sp)
                        Text("${goalTitles[task.goalId].orEmpty()} · ${task.dueDateText() ?: "未安排日期"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(Modifier.padding(start = 36.dp)) {
                    if (task.dueDate == null) {
                        TextButton(onClick = { vm.schedule(task, LocalDate.now()) }) { Text("安排到今天") }
                    }
                    TextButton(onClick = { vm.schedule(task, LocalDate.now().plusDays(1)) }) { Text("移到明天") }
                }
            }
            if (tasks.isNotEmpty()) {
                Text("首次完成每项行动 +10 XP", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
