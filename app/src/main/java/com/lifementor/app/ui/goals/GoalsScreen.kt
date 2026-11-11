package com.lifementor.app.ui.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifementor.app.data.GameStore
import com.lifementor.app.data.Mentors
import com.lifementor.app.data.TaskItem

@Composable
fun GoalsScreen(vm: GoalsViewModel = viewModel()) {
    var showAdd by remember { mutableStateOf(false) }
    var quickAddGoalId by remember { mutableStateOf<Long?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("人生目标", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            vm.xpToast.value?.let {
                Text(it, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(Modifier.width(8.dp))
            }
            Button(onClick = { showAdd = true }) { Text("新增目标") }
        }
        vm.error.value?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }

        val active = vm.goals.filter { it.status == "active" }
        val done = vm.goals.filter { it.status != "active" }

        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 8.dp, bottom = 40.dp),
        ) {
            if (active.isEmpty() && done.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp)) {
                            Text("还没有目标", fontWeight = FontWeight.Bold)
                            Text(
                                "点「新增目标」写下你在意的事，再让 AI 把它拆成里程碑 → 周任务 → 每日行动。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            items(active + done, key = { it.id }) { goal ->
                GoalCard(goal, vm, onQuickAdd = { quickAddGoalId = goal.id })
            }
        }
    }

    if (showAdd) {
        AddGoalDialog(
            onDismiss = { showAdd = false },
            onCreate = { title, domain -> vm.addGoal(title, domain); showAdd = false },
        )
    }
    quickAddGoalId?.let { gid ->
        QuickActionDialog(onDismiss = { quickAddGoalId = null }, onAdd = { t -> vm.quickAction(gid, t); quickAddGoalId = null })
    }
}

@Composable
private fun GoalCard(goal: com.lifementor.app.data.Goal, vm: GoalsViewModel, onQuickAdd: () -> Unit) {
    val mentor = Mentors.byId(goal.domain)
    val sub = vm.tasks.filter { it.goalId == goal.id }
    val isDecomposing = vm.decomposingId.value == goal.id
    val open = vm.expanded[goal.id] == true || !goal.decomposed

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp)
                        .background(
                            (mentor?.color ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.16f),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(mentor?.emoji ?: "✨", fontSize = 20.sp)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        goal.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textDecoration = if (goal.status == "done")
                            androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                    )
                    Text(
                        if (goal.decomposed) {
                            val d = sub.count { it.done }
                            "${mentor?.domainLabel ?: goal.domain}　$d/${sub.size} 已完成"
                        } else {
                            mentor?.domainLabel ?: goal.domain
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (goal.decomposed) {
                    TextButton(onClick = { vm.expanded[goal.id] = !(vm.expanded[goal.id] == true) }) {
                        Text(if (open) "收起" else "展开")
                    }
                }
            }

            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!goal.decomposed) {
                    Button(onClick = { vm.decompose(goal.id) }, enabled = !vm.busy.value) {
                        if (isDecomposing) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                            Text("拆解中…")
                        } else {
                            Text("AI 拆解目标")
                        }
                    }
                }
                Spacer(Modifier.width(6.dp))
                if (goal.decomposed) {
                    TextButton(onClick = onQuickAdd) { Text("+ 今日行动") }
                }
                Spacer(Modifier.weight(1f))
                if (goal.status == "active") {
                    TextButton(onClick = { vm.completeGoal(goal.id) }) { Text("完成") }
                }
                TextButton(onClick = { vm.deleteGoal(goal.id) }) { Text("删除") }
            }

            if (open && goal.decomposed) {
                Spacer(Modifier.height(4.dp))
                val ms = sub.filter { it.level == 1 }
                ms.forEach { m ->
                    TaskRow(m, level = 1, vm = vm)
                    sub.filter { it.level == 2 && it.parentId == m.id }.forEach { w ->
                        TaskRow(w, level = 2, vm = vm)
                        sub.filter { it.level == 3 && it.parentId == w.id }.forEach { d ->
                            TaskRow(d, level = 3, vm = vm)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskRow(t: TaskItem, level: Int, vm: GoalsViewModel) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = ((level - 1) * 14).dp, top = 1.dp, bottom = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = t.done, onCheckedChange = { vm.toggle(t.id) })
        Text(
            t.title,
            fontSize = if (level == 1) 14.sp else 13.sp,
            fontWeight = if (level == 1) FontWeight.SemiBold else FontWeight.Normal,
            textDecoration = if (t.done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
            color = if (t.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun AddGoalDialog(onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var domain by remember { mutableStateOf("pathfinder") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增目标") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("想要达成什么？（具体一点）") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Text("归属领域", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth()) {
                    Mentors.all.forEach { m ->
                        val sel = m.id == domain
                        Text(
                            m.emoji,
                            fontSize = 22.sp,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(
                                    if (sel) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                                .clickable { domain = m.id }
                                .padding(8.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                }
                Text(Mentors.byId(domain)?.domainLabel ?: "", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(
                onClick = { if (title.isNotBlank()) onCreate(title.trim(), domain) },
            ) { Text("创建") }
        },
        dismissButton = { TextButton(onDismiss) { Text("取消") } },
    )
}

@Composable
private fun QuickActionDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var title by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("加一条今日行动") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("例如：投递 2 份岗位 JD 匹配的简历") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 3,
            )
        },
        confirmButton = { Button(onClick = { if (title.isNotBlank()) onAdd(title.trim()) }) { Text("添加 (+10 XP)") } },
        dismissButton = { TextButton(onDismiss) { Text("取消") } },
    )
}
