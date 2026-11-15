package com.lifementor.app.ui.goals

import androidx.compose.foundation.background
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifementor.app.data.GameStore
import com.lifementor.app.data.Goal
import com.lifementor.app.data.Mentors
import com.lifementor.app.data.TaskItem
import com.lifementor.app.ui.components.TaskDateDialog

private data class NewTask(val goalId: Long, val level: Int, val parentId: Long?)

@Composable
fun GoalsScreen(vm: GoalsViewModel = viewModel()) {
    LaunchedEffect(GameStore.taskRevision.intValue) { vm.refresh() }
    var showAdd by remember { mutableStateOf(false) }
    var editGoalId by rememberSaveable { mutableStateOf<Long?>(null) }
    var newTask by remember { mutableStateOf<NewTask?>(null) }
    var deleteGoalId by rememberSaveable { mutableStateOf<Long?>(null) }

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
                                "点「新增目标」写下你在意的事，可以手动规划，也可以让 AI 帮你拆解。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (active.isNotEmpty()) {
                item(key = "active-heading") {
                    Text("进行中", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 4.dp))
                }
                items(active, key = { it.id }) { goal ->
                    GoalCard(
                        goal, vm,
                        onEdit = { editGoalId = goal.id },
                        onAddTask = { level, parentId -> newTask = NewTask(goal.id, level, parentId) },
                        onDelete = { deleteGoalId = goal.id },
                    )
                }
            }
            if (done.isNotEmpty()) {
                item(key = "done-heading") {
                    Text("已完成", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 4.dp))
                }
                items(done, key = { it.id }) { goal ->
                    GoalCard(
                        goal, vm,
                        onEdit = { editGoalId = goal.id },
                        onAddTask = { level, parentId -> newTask = NewTask(goal.id, level, parentId) },
                        onDelete = { deleteGoalId = goal.id },
                    )
                }
            }
        }
    }

    if (showAdd) {
        GoalEditorDialog(
            onDismiss = { showAdd = false },
            onSave = { title, domain -> vm.addGoal(title, domain); showAdd = false },
        )
    }
    vm.goals.find { it.id == editGoalId }?.let { goal ->
        key(goal.id) {
            GoalEditorDialog(
                goal = goal,
                enabled = vm.decomposingId.value != goal.id,
                onDismiss = { editGoalId = null },
                onSave = { title, domain -> vm.updateGoal(goal.id, title, domain); editGoalId = null },
            )
        }
    }
    newTask?.let { request ->
        vm.goals.find { it.id == request.goalId }?.let { goal ->
            val parent = vm.tasks.find { it.id == request.parentId && it.goalId == goal.id }
            TaskEditorDialog(
                level = request.level,
                parentTitle = parent?.title ?: goal.title,
                parentLabel = when (parent?.level) {
                    1 -> "所属里程碑"
                    2 -> "所属周任务"
                    else -> "所属目标"
                },
                enabled = goal.status == "active" && vm.decomposingId.value != goal.id,
                onDismiss = { newTask = null },
                onAdd = { title, date ->
                    vm.addTask(goal.id, title, request.level, request.parentId, date)
                    newTask = null
                },
            )
        }
    }
    vm.goals.find { it.id == deleteGoalId }?.let { goal ->
        AlertDialog(
            onDismissRequest = { deleteGoalId = null },
            title = { Text("删除目标？") },
            text = { Text("将删除「${goal.title}」及其中的 ${vm.tasks.count { it.goalId == goal.id }} 项任务。此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = { vm.deleteGoal(goal.id); deleteGoalId = null },
                    enabled = vm.decomposingId.value != goal.id,
                ) { Text("确认删除") }
            },
            dismissButton = { TextButton(onClick = { deleteGoalId = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun GoalCard(
    goal: Goal,
    vm: GoalsViewModel,
    onEdit: () -> Unit,
    onAddTask: (Int, Long?) -> Unit,
    onDelete: () -> Unit,
) {
    val mentor = Mentors.byId(goal.domain)
    val sub = vm.tasks.filter { it.goalId == goal.id }
    val isDecomposing = vm.decomposingId.value == goal.id
    val active = goal.status == "active"
    val canMutate = active && !isDecomposing
    val open = vm.expanded[goal.id] ?: !goal.decomposed
    var showManagement by remember(goal.id) { mutableStateOf(false) }
    var showPlanning by remember(goal.id) { mutableStateOf(false) }

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
                        textDecoration = if (!active)
                            androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                    )
                    Text(
                        "${mentor?.domainLabel ?: goal.domain} · ${if (active) "进行中" else "已完成"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (sub.isNotEmpty()) {
                        Text(
                            "${sub.count { it.done }}/${sub.size} 项任务已完成",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (sub.isNotEmpty()) {
                    TextButton(onClick = { vm.expanded[goal.id] = !open }) {
                        Text(if (open) "收起" else "展开")
                    }
                }
                Box {
                    IconButton(
                        onClick = { showManagement = true },
                        modifier = Modifier.semantics { contentDescription = "管理目标：${goal.title}" },
                    ) { Text("⋮", fontSize = 24.sp) }
                    DropdownMenu(expanded = showManagement, onDismissRequest = { showManagement = false }) {
                        DropdownMenuItem(
                            text = { Text("编辑目标") },
                            enabled = !isDecomposing,
                            onClick = { showManagement = false; onEdit() },
                        )
                        DropdownMenuItem(
                            text = { Text(if (active) "完成此目标" else "撤回完成") },
                            enabled = !isDecomposing,
                            onClick = {
                                showManagement = false
                                if (active) vm.completeGoal(goal.id) else vm.reopenGoal(goal.id)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("删除此目标", color = MaterialTheme.colorScheme.error) },
                            enabled = !isDecomposing,
                            onClick = { showManagement = false; onDelete() },
                        )
                    }
                }
            }

            if (active) {
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
                        Spacer(Modifier.width(6.dp))
                    }
                    Box {
                        TextButton(onClick = { showPlanning = true }, enabled = canMutate) { Text("手动规划") }
                        DropdownMenu(expanded = showPlanning, onDismissRequest = { showPlanning = false }) {
                            DropdownMenuItem(
                                text = { Text("添加里程碑") },
                                enabled = canMutate,
                                onClick = { showPlanning = false; onAddTask(1, null) },
                            )
                            DropdownMenuItem(
                                text = { Text("添加独立行动") },
                                enabled = canMutate,
                                onClick = { showPlanning = false; onAddTask(3, null) },
                            )
                        }
                    }
                }
            }

            if (open && sub.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                val expandedIds = vm.expandedTasks.filterValues { it }.keys
                visibleTaskRows(sub, expandedIds).forEach { task ->
                    key(task.id) {
                        val depth = if (task.parentId == null) 0 else task.level - 1
                        val childCount = sub.count { it.parentId == task.id }
                        val canAddChild = canMutate && task.level in 1..2
                        TaskRow(task, depth, vm, childCount, canMutate, canExpand = childCount > 0 || canAddChild)
                        if (canAddChild && task.id in expandedIds) {
                            TextButton(
                                onClick = { onAddTask(task.level + 1, task.id) },
                                modifier = Modifier.padding(start = ((depth + 1) * 14 + 48).dp),
                            ) { Text(if (task.level == 1) "+ 添加周任务" else "+ 添加行动", fontSize = 12.sp) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskRow(
    t: TaskItem,
    depth: Int,
    vm: GoalsViewModel,
    childCount: Int,
    canMutate: Boolean,
    canExpand: Boolean,
) {
    var showDatePicker by remember(t.id) { mutableStateOf(false) }
    var textExpanded by rememberSaveable(t.id) { mutableStateOf(false) }
    var textOverflows by remember(t.id, t.title) { mutableStateOf(false) }
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = (depth * 14).dp, top = 1.dp, bottom = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = t.done, onCheckedChange = { vm.toggle(t.id) }, enabled = canMutate)
            Text(
                t.title,
                modifier = Modifier.weight(1f),
                fontSize = if (t.level == 1) 14.sp else 13.sp,
                fontWeight = if (t.level == 1) FontWeight.SemiBold else FontWeight.Normal,
                textDecoration = if (t.done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                color = if (t.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = if (textExpanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { if (!textExpanded) textOverflows = it.hasVisualOverflow },
            )
            if (canExpand) {
                val open = vm.expandedTasks[t.id] == true
                TextButton(onClick = { vm.expandedTasks[t.id] = !open }) {
                    Text(if (open) "收起" else if (childCount > 0) "展开 $childCount" else "展开", fontSize = 12.sp, maxLines = 1)
                }
            }
        }
        Row(Modifier.padding(start = (depth * 14 + 48).dp)) {
            Text(
                when (t.level) { 1 -> "里程碑"; 2 -> "周任务"; else -> "行动" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterVertically).padding(end = 4.dp),
            )
            TextButton(onClick = { showDatePicker = true }, enabled = canMutate) {
                Text(t.dueDateText() ?: "安排日期", fontSize = 12.sp, maxLines = 1)
            }
            if (textOverflows || textExpanded) {
                TextButton(onClick = { textExpanded = !textExpanded }) {
                    Text(if (textExpanded) "收起文字" else "展开全文", fontSize = 12.sp)
                }
            }
        }
    }
    if (showDatePicker && canMutate) {
        TaskDateDialog(
            dueDate = t.dueDate,
            onDismiss = { showDatePicker = false },
            onSave = { date ->
                vm.setDueDate(t.id, date)
                showDatePicker = false
            },
        )
    }
}
