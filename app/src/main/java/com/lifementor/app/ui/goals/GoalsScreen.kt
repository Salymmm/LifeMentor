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
import androidx.compose.material3.AlertDialog
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
import com.lifementor.app.ui.components.DisplayTitle
import com.lifementor.app.ui.components.MentorAvatar
import com.lifementor.app.ui.components.PaperChip
import com.lifementor.app.ui.components.ScrapCard
import com.lifementor.app.ui.components.StickerButton
import com.lifementor.app.ui.components.Tape
import com.lifementor.app.ui.theme.Charcoal
import com.lifementor.app.ui.theme.InkSoft
import com.lifementor.app.ui.theme.PaperWhite
import com.lifementor.app.ui.theme.ScrapRed
import com.lifementor.app.ui.theme.ScrapYellow

private data class NewTask(val goalId: Long, val parentId: Long?)

@Composable
fun GoalsScreen(onOpenCalendar: () -> Unit, vm: GoalsViewModel = viewModel()) {
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
            Column {
                DisplayTitle("Goals")
                Text("Life Goals", style = MaterialTheme.typography.labelSmall, color = InkSoft)
            }
            Spacer(Modifier.weight(1f))
            PaperChip("Calendar", onClick = onOpenCalendar)
            Spacer(Modifier.width(8.dp))
            StickerButton("+ New goal", onClick = { showAdd = true })
        }
        Text(listOfNotNull(vm.xpToast.value, "Tap a task title to edit or delete").joinToString(" · "), style = MaterialTheme.typography.bodySmall,
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
                    ScrapCard(
                        paperColor = PaperWhite,
                        shadowColor = Charcoal.copy(alpha = 0.18f),
                        fillWidth = true,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            Text("No goals yet", fontWeight = FontWeight.Bold)
                            Text(
                                "Tap New goal and write down what matters. Plan tasks yourself or let AI split the goal into steps.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (active.isNotEmpty()) {
                item(key = "active-heading") {
                    Text("In progress", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 4.dp))
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
                    Text("Completed", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 4.dp))
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
            title = { Text("Delete task?") },
            text = { Text("Delete “${task.title}”${if (children > 0) " and its $children subtasks" else ""}? This cannot be undone. Earned XP is kept.") },
            confirmButton = { TextButton(
                enabled = vm.decomposingId.value != task.goalId,
                onClick = { if (vm.deleteTask(task.id)) { deleteTaskId = null; editTaskId = null } },
            ) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteTaskId = null }) { Text("Cancel") } },
        )
    }
    vm.goals.find { it.id == deleteGoalId }?.let { goal ->
        AlertDialog(
            onDismissRequest = { deleteGoalId = null },
            title = { Text("Delete goal?") },
            text = { Text("Delete “${goal.title}” and its ${vm.tasks.count { it.goalId == goal.id }} tasks? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = { vm.deleteGoal(goal.id); deleteGoalId = null },
                    enabled = vm.decomposingId.value != goal.id,
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteGoalId = null }) { Text("Cancel") } },
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

    ScrapCard(
        paperColor = if (active) PaperWhite else ScrapYellow.copy(alpha = 0.6f),
        shadowColor = Charcoal.copy(alpha = 0.18f),
        rotation = -0.2f,
        fillWidth = true,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Tape(Modifier.align(Alignment.End))
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (mentor != null) {
                    MentorAvatar(mentor, 40.dp, fallbackEmojiSize = 20.sp)
                } else {
                    Box(
                        Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("✨", fontSize = 20.sp)
                    }
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
                        "${mentor?.domainLabel ?: goal.domain} · ${if (active) "In progress" else "Completed"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (sub.isNotEmpty()) {
                        Text(
                            "${sub.count { it.done }}/${sub.size} tasks completed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (sub.isNotEmpty()) {
                    PaperChip(if (open) "Collapse" else "Expand",
                        onClick = { vm.expanded[goal.id] = !open })
                }
                Box {
                    IconButton(
                        onClick = { showManagement = true },
                        modifier = Modifier.semantics { contentDescription = "Manage goal: ${goal.title}" },
                    ) { Text("⋮", fontSize = 24.sp) }
                    DropdownMenu(expanded = showManagement, onDismissRequest = { showManagement = false }) {
                        DropdownMenuItem(
                            text = { Text("Edit goal") },
                            enabled = !isDecomposing,
                            onClick = { showManagement = false; onEdit() },
                        )
                        DropdownMenuItem(
                            text = { Text(if (active) "Complete goal" else "Undo completion") },
                            enabled = !isDecomposing,
                            onClick = {
                                showManagement = false
                                if (active) vm.completeGoal(goal.id) else vm.reopenGoal(goal.id)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete goal", color = MaterialTheme.colorScheme.error) },
                            enabled = !isDecomposing,
                            onClick = { showManagement = false; onDelete() },
                        )
                    }
                }
            }

            if (active) {
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!goal.decomposed) {
                        if (isDecomposing) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = ScrapRed)
                            Spacer(Modifier.width(6.dp))
                        }
                        StickerButton(
                            if (isDecomposing) "Decomposing…" else "✂ AI-decompose",
                            onClick = { vm.decompose(goal.id) },
                            enabled = !vm.busy.value,
                            arrow = !isDecomposing,
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    PaperChip("+ Task", onClick = { onAddTask(null) }, enabled = canMutate)
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
                onClickLabel = if (canMutate) "Edit “${t.title}”" else if (textExpanded) "Collapse text" else "Expand full text") {
                if (canMutate) onEdit() else textExpanded = !textExpanded
            }) {
                if (depth > 3) {
                    Text("Subtask level ${depth + 1}", style = MaterialTheme.typography.labelSmall,
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
                    .semantics { contentDescription = "Add subtask to “${t.title}”" }) { Text("＋", fontSize = 24.sp) }
            }
            if (childCount > 0) {
                val open = vm.expandedTasks[t.id] == true
                IconButton(onClick = { vm.expandedTasks[t.id] = !open }, modifier = Modifier.size(36.dp)
                    .semantics { contentDescription = if (open) "Collapse subtasks of “${t.title}”" else "Expand $childCount subtasks of “${t.title}”" }) {
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
