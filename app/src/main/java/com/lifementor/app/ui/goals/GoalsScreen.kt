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
import com.lifementor.app.data.taskBranchIds
import com.lifementor.app.ui.components.TaskDateDialog
import com.lifementor.app.ui.components.TaskDateAction

private data class NewTask(val goalId: Long, val parentId: Long?)

@Composable
fun GoalsScreen(vm: GoalsViewModel = viewModel()) {
    LaunchedEffect(GameStore.taskRevision.intValue) { vm.refresh() }
    var showAdd by remember { mutableStateOf(false) }
    var editGoalId by rememberSaveable { mutableStateOf<Long?>(null) }
    var newTask by remember { mutableStateOf<NewTask?>(null) }
    var deleteGoalId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editTaskId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleteTaskId by rememberSaveable { mutableStateOf<Long?>(null) }

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
        Text("点任务名称可编辑或删除", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
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
                        onEditTask = { editTaskId = it },
                        onAddTask = { parentId -> newTask = NewTask(goal.id, parentId) },
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
                        onEditTask = { editTaskId = it },
                        onAddTask = { parentId -> newTask = NewTask(goal.id, parentId) },
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
                parentTitle = parent?.title ?: goal.title,
                isSubtask = request.parentId != null,
                enabled = goal.status == "active" && vm.decomposingId.value != goal.id,
                onDismiss = { newTask = null },
                onAdd = { title, date ->
                    vm.addTask(goal.id, title, request.parentId, date)
                    newTask = null
                },
            )
        }
    }
    vm.tasks.find { it.id == editTaskId }?.let { task ->
        val goal = vm.goals.find { it.id == task.goalId }
        val parent = vm.tasks.find { it.id == task.parentId }
        TaskEditorDialog(
            parentTitle = parent?.title ?: goal?.title.orEmpty(),
            isSubtask = task.parentId != null,
            enabled = goal?.status == "active" && vm.decomposingId.value != task.goalId,
            task = task,
            onDismiss = { editTaskId = null },
            onAdd = { title, date -> if (vm.updateTask(task.id, title, date)) editTaskId = null },
            onDelete = { deleteTaskId = task.id },
        )
    }
    vm.tasks.find { it.id == deleteTaskId }?.let { task ->
        val children = taskBranchIds(vm.tasks, task.id).size - 1
        AlertDialog(
            onDismissRequest = { deleteTaskId = null },
            title = { Text("删除任务？") },
            text = { Text("将删除「${task.title}」${if (children > 0) "及其 $children 个子任务" else ""}。此操作不可撤销，已获得的经验值保留。") },
            confirmButton = { TextButton(
                enabled = vm.decomposingId.value != task.goalId,
                onClick = { if (vm.deleteTask(task.id)) { deleteTaskId = null; editTaskId = null } },
            ) { Text("确认删除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteTaskId = null }) { Text("取消") } },
        )
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
    onEditTask: (Long) -> Unit,
    onAddTask: (Long?) -> Unit,
    onDelete: () -> Unit,
) {
    val mentor = Mentors.byId(goal.domain)
    val sub = vm.tasks.filter { it.goalId == goal.id }
    val isDecomposing = vm.decomposingId.value == goal.id
    val active = goal.status == "active"
    val canMutate = active && !isDecomposing
    val open = vm.expanded[goal.id] ?: !goal.decomposed
    var showManagement by remember(goal.id) { mutableStateOf(false) }

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
                    TextButton(onClick = { onAddTask(null) }, enabled = canMutate) { Text("添加任务") }
                }
            }

            if (open && sub.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                val expandedIds = vm.expandedTasks.filterValues { it }.keys
                val childCounts = sub.groupingBy { it.parentId }.eachCount()
                visibleTaskRows(sub, expandedIds).forEach { row ->
                    val task = row.task
                    key(task.id) {
                        TaskRow(task, row.depth, vm, childCounts[task.id] ?: 0, canMutate,
                            onEdit = { onEditTask(task.id) },
                            onAddSubtask = { onAddTask(task.id) })
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
    onEdit: () -> Unit,
    onAddSubtask: () -> Unit,
) {
    var showDatePicker by remember(t.id) { mutableStateOf(false) }
    var textExpanded by rememberSaveable(t.id) { mutableStateOf(false) }
    var textOverflows by remember(t.id, t.title) { mutableStateOf(false) }
    val indent = depth.coerceAtMost(3) * 12
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = indent.dp, top = 1.dp, bottom = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = t.done, onCheckedChange = { vm.toggle(t.id) }, enabled = canMutate)
            Column(Modifier.weight(1f).clickable(enabled = canMutate || textOverflows || textExpanded,
                onClickLabel = if (canMutate) "编辑「${t.title}」" else if (textExpanded) "收起文字" else "展开全文") {
                if (canMutate) onEdit() else textExpanded = !textExpanded
            }) {
                if (depth > 3) {
                    Text("第 ${depth + 1} 层子任务", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    t.title,
                    fontSize = if (depth == 0) 14.sp else 13.sp,
                    fontWeight = if (depth == 0) FontWeight.SemiBold else FontWeight.Normal,
                    textDecoration = if (t.done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                    color = if (t.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = if (textExpanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { if (!textExpanded) textOverflows = it.hasVisualOverflow },
                )
                t.dueDateText()?.let { date ->
                    Text(date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            TaskDateAction(t.title, t.dueDateText(), canMutate) { showDatePicker = true }
            if (canMutate) {
                IconButton(onClick = onAddSubtask, modifier = Modifier.size(40.dp)
                    .semantics { contentDescription = "为「${t.title}」添加子任务" }) { Text("＋", fontSize = 24.sp) }
            }
            if (childCount > 0) {
                val open = vm.expandedTasks[t.id] == true
                IconButton(onClick = { vm.expandedTasks[t.id] = !open }, modifier = Modifier.size(36.dp)
                    .semantics { contentDescription = if (open) "收起「${t.title}」子任务" else "展开「${t.title}」的 $childCount 个子任务" }) {
                    Text(if (open) "▾" else "▸", fontSize = 20.sp)
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
