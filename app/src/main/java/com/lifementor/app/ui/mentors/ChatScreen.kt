package com.lifementor.app.ui.mentors

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.res.painterResource
import com.lifementor.app.R
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifementor.app.data.GameStore
import com.lifementor.app.data.Leveling
import com.lifementor.app.data.Mentors
import com.lifementor.app.ui.components.MarkdownText
import com.lifementor.app.data.Msg
import com.lifementor.app.llm.TaskQueryRequest
import com.lifementor.app.llm.TaskQueryScope
import com.lifementor.app.ui.components.MentorAvatar
import com.lifementor.app.ui.components.PaperChip
import com.lifementor.app.ui.components.ScrapCard
import com.lifementor.app.ui.components.StickerButton
import com.lifementor.app.ui.components.TaskDateDialog
import com.lifementor.app.ui.theme.Charcoal
import com.lifementor.app.ui.theme.DeepSlate
import com.lifementor.app.ui.theme.InkBlack
import com.lifementor.app.ui.theme.PaperWhite
import com.lifementor.app.ui.theme.ScrapBlue
import com.lifementor.app.ui.theme.ScrapPurple
import com.lifementor.app.ui.theme.ScrapRed
import com.lifementor.app.ui.theme.ScrapRedDeep
import com.lifementor.app.ui.theme.ScrapYellow
import com.lifementor.app.ui.theme.ScrapYellowDeep
import java.time.Instant
import java.time.ZoneOffset

@Composable
fun ChatScreen(mentorId: String, onBack: () -> Unit, vm: ChatViewModel = viewModel()) {
    LaunchedEffect(mentorId) { vm.open(mentorId) }
    val mentor = Mentors.byId(mentorId) ?: return
    val listState = rememberLazyListState()
    val msgs = if (vm.mentorId.value == mentorId) vm.messages.value else emptyList()
    val canProposeHandoff = msgs.any { it.role == "assistant" && it.content.isNotBlank() }
    val suggestionReplyTime = vm.taskSuggestionReplyTime.value
    val chatBusy = vm.sending.value || vm.handoffBusy.value || suggestionReplyTime != null
    val domainXp = Leveling.domainTotals(GameStore.loadXP())[mentor.id] ?: 0

    LaunchedEffect(msgs.size, msgs.lastOrNull()?.content, vm.sending.value) {
        val itemCount = msgs.size + if (vm.sending.value) 1 else 0
        if (itemCount > 0) listState.scrollToItem(itemCount - 1)
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PaperChip("‹ Back", onClick = onBack)
            Spacer(Modifier.width(8.dp))
            MentorAvatar(mentor, 44.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(mentor.en, style = MaterialTheme.typography.headlineSmall)
                Text(
                    "${mentor.name} · ${mentor.domainLabel} ${domainXp} XP",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PaperChip(
                label = if (vm.handoffBusy.value) "Analyzing…" else "Link a mentor",
                onClick = { vm.requestHandoff() },
                enabled = canProposeHandoff && !chatBusy && vm.handoffSuggestion.value == null,
            )
            Spacer(Modifier.width(6.dp))
            PaperChip("Clear", onClick = { vm.requestClearHistory() }, enabled = vm.mentorId.value == mentorId)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
        ) {
            itemsIndexed(msgs) { i, message ->
                Bubble(
                    message,
                    i,
                    mentorColor = mentor.color,
                    extractingTask = suggestionReplyTime == message.time,
                    canAddToPlan = !chatBusy,
                    onAddToPlan = { vm.requestTaskSuggestion(message.time) },
                )
            }
            if (vm.sending.value) {
                item {
                    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("${mentor.name} is typing…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        if (vm.handoffBusy.value) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Checking whether another mentor should take this next", style = MaterialTheme.typography.bodySmall)
            }
        }

        vm.handoffSuggestion.value?.let { suggestion ->
            val targetName = Mentors.byId(suggestion.targetMentorId)?.name ?: suggestion.targetMentorId
            ScrapCard(
                paperColor = ScrapPurple,
                shadowColor = Charcoal.copy(alpha = 0.25f),
                rotation = -0.3f,
                fillWidth = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text("Suggested handoff to $targetName", style = MaterialTheme.typography.titleMedium, color = InkBlack)
                Spacer(Modifier.height(4.dp))
                Text("Task: ${suggestion.taskTitle}", style = MaterialTheme.typography.bodyMedium, color = InkBlack)
                if (suggestion.rationale.isNotBlank()) {
                    Text("Why: ${suggestion.rationale}", style = MaterialTheme.typography.bodySmall, color = InkBlack)
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StickerButton("Confirm handoff", onClick = { vm.acceptHandoff() })
                    Spacer(Modifier.width(10.dp))
                    PaperChip("Cancel", onClick = { vm.dismissHandoff() })
                }
            }
        }

        vm.handoffStatus.value?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        vm.error.value?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        if (vm.crisis.value) {
            ScrapCard(
                paperColor = ScrapRed,
                shadowColor = Charcoal.copy(alpha = 0.25f),
                rotation = 0.3f,
                fillWidth = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    "🫂 If you are really struggling right now: call 12356 (24h mental-health hotline in mainland China) or local emergency services. " +
                        "You can also reach someone you trust or your campus counseling center. Heart can listen, but cannot replace professional help.",
                    style = MaterialTheme.typography.bodySmall,
                    color = PaperWhite,
                )
            }
        }

        if (vm.taskSuggestionTitle.value == null) {
            vm.taskSuggestionStatus.value?.let { status ->
                Text(status, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
        }

        vm.taskQueryPage.value?.let { page ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (page.total == 0) "No matching tasks" else "${page.shown} shown · ${page.total} matches",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                if (page.nextOffset != null) {
                    PaperChip("Next page", onClick = { vm.nextTaskPage() }, enabled = !chatBusy)
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        TaskScopePicker(mentorId, vm, enabled = !chatBusy)
        PaperChip(
            label = if (vm.simulationMode.value) "🔮 Simulation on" else "🔮 Simulation mode",
            onClick = { vm.toggleSimulationMode() },
            enabled = !chatBusy,
            baseColor = if (vm.simulationMode.value) ScrapYellow else PaperWhite,
        )
        }

        if (vm.simulationMode.value) {
            Text(
                "This compares three paths using your profile; turn it off to return to normal mentor chat.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = vm.draft.value,
                onValueChange = { vm.draft.value = it },
                placeholder = {
                    Text(
                        if (vm.simulationMode.value) "Describe the choices to compare…"
                        else "Talk to ${mentor.name}…",
                    )
                },
                modifier = Modifier.weight(1f),
                maxLines = 4,
                shape = RoundedCornerShape(12.dp),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = PaperWhite,
                    unfocusedContainerColor = PaperWhite,
                    focusedBorderColor = InkBlack,
                    unfocusedBorderColor = ScrapYellowDeep,
                    cursorColor = ScrapRed,
                    focusedTextColor = InkBlack,
                    unfocusedTextColor = InkBlack,
                ),
            )
            Spacer(Modifier.width(8.dp))
            if (vm.sending.value) {
                StickerButton(
                    "Stop",
                    onClick = { vm.cancelSending() },
                    baseColor = ScrapRedDeep,
                    shadowColor = ScrapYellowDeep,
                    contentColor = PaperWhite,
                )
            } else {
                StickerButton(
                    if (vm.simulationMode.value) "Simulate" else "Send",
                    onClick = { vm.send() },
                    enabled = !chatBusy,
                    arrow = true,
                )
            }
        }
    }

    vm.clearChatMentor.value?.takeIf { it.id == mentorId }?.let { selectedMentor ->
        AlertDialog(
            onDismissRequest = vm::dismissClearHistory,
            title = { Text("Clear the chat with ${selectedMentor.name}?") },
            text = { Text("This removes the messages in this conversation.") },
            confirmButton = { TextButton(onClick = vm::confirmClearHistory) { Text("Clear chat") } },
            dismissButton = { TextButton(onClick = vm::dismissClearHistory) { Text("Cancel") } },
        )
    }

    if (vm.mentorId.value == mentorId) {
        vm.taskSuggestionTitle.value?.let { TaskSuggestionDialog(it, vm) }
    }
}

private val TaskScopeLabels = mapOf(
    TaskQueryScope.TODAY_DUE to "Tasks due today",
    TaskQueryScope.TODAY_COMPLETED to "Tasks completed today",
    TaskQueryScope.OVERDUE to "Overdue tasks",
    TaskQueryScope.UNSCHEDULED to "Unscheduled tasks",
    TaskQueryScope.ALL to "All tasks",
)

@Composable
private fun TaskScopePicker(mentorId: String, vm: ChatViewModel, enabled: Boolean) {
    var expanded by remember(mentorId) { mutableStateOf(false) }
    var choosingGoal by remember(mentorId) { mutableStateOf(false) }
    val selection = vm.taskScopeSelection.value
    Box {
        PaperChip(
            selection?.label ?: "Task scope",
            onClick = {
                choosingGoal = false
                expanded = true
            },
            enabled = enabled,
            baseColor = if (selection == null) PaperWhite else ScrapYellow,
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (choosingGoal) {
                val goals = remember { vm.taskGoals() }
                DropdownMenuItem(text = { Text("‹ Task scopes") }, onClick = { choosingGoal = false })
                if (goals.isEmpty()) {
                    DropdownMenuItem(text = { Text("No goals yet") }, onClick = {}, enabled = false)
                }
                goals.forEach { goal ->
                    DropdownMenuItem(
                        text = { Text(goal.title) },
                        onClick = {
                            vm.taskScopeSelection.value = ChatTaskScope(goal.title, TaskQueryRequest(setOf(TaskQueryScope.GOAL), goal.id))
                            expanded = false
                        },
                    )
                }
            } else {
                DropdownMenuItem(text = { Text("Automatic") }, onClick = {
                    vm.taskScopeSelection.value = null
                    expanded = false
                })
                TaskScopeLabels.forEach { (scope, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = {
                        vm.taskScopeSelection.value = ChatTaskScope(label, TaskQueryRequest(setOf(scope)))
                        expanded = false
                    })
                }
                DropdownMenuItem(text = { Text("By goal…") }, onClick = { choosingGoal = true })
            }
        }
    }
}

@Composable
private fun TaskSuggestionDialog(initialTitle: String, vm: ChatViewModel) {
    var title by rememberSaveable(initialTitle) { mutableStateOf(initialTitle) }
    var goalId by rememberSaveable { mutableStateOf<Long?>(null) }
    var parentId by rememberSaveable { mutableStateOf<Long?>(null) }
    var dueDate by rememberSaveable { mutableStateOf<Long?>(null) }
    var choosingDate by rememberSaveable { mutableStateOf(false) }
    val status = vm.taskSuggestionStatus.value
    val goals = remember(status) { vm.taskGoals().filter { it.status == "active" } }
    val selectedGoal = goals.find { it.id == goalId }
    val parents = remember(selectedGoal?.id, status) { selectedGoal?.let { vm.goalTasks(it.id) }.orEmpty() }

    AlertDialog(
        onDismissRequest = vm::dismissTaskSuggestion,
        title = { Text("Add to plan") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Task name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                if (goals.isEmpty()) {
                    Text("Create an active goal before adding this task.", modifier = Modifier.padding(top = 8.dp))
                } else {
                    TaskFormChoice(
                        selectedGoal?.title ?: "Choose an active goal",
                        goals.map { it.id to it.title },
                    ) { selected ->
                        if (goalId != selected) parentId = null
                        goalId = selected
                    }
                }
                if (parents.isNotEmpty() || parentId != null) {
                    val parentLabel = parents.find { it.id == parentId }?.title
                        ?: if (parentId == null) "No parent task" else "Choose a parent task"
                    TaskFormChoice(
                        parentLabel,
                        listOf(null to "No parent task") + parents.map { it.id to it.title },
                    ) { parentId = it }
                }
                TextButton(onClick = { choosingDate = true }) {
                    val day = dueDate?.let { Instant.ofEpochMilli(it).atOffset(ZoneOffset.UTC).toLocalDate() }
                    Text(if (day == null) "Set date" else "Due $day")
                }
                status?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selectedGoal?.let { vm.confirmTaskSuggestion(title, it.id, parentId, dueDate) } },
                enabled = selectedGoal != null && title.isNotBlank(),
            ) { Text("Save task") }
        },
        dismissButton = { TextButton(onClick = vm::dismissTaskSuggestion) { Text("Cancel") } },
    )
    if (choosingDate) {
        TaskDateDialog(dueDate, onDismiss = { choosingDate = false }, onSave = {
            dueDate = it
            choosingDate = false
        })
    }
}

@Composable
private fun TaskFormChoice(label: String, options: List<Pair<Long?, String>>, onSelect: (Long?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.padding(top = 8.dp)) {
        OutlinedButton(onClick = { expanded = true }) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, title) ->
                DropdownMenuItem(text = { Text(title) }, onClick = {
                    onSelect(id)
                    expanded = false
                })
            }
        }
    }
}

@Composable
private fun Bubble(m: Msg, index: Int, mentorColor: Color, extractingTask: Boolean, canAddToPlan: Boolean, onAddToPlan: () -> Unit) {
    val context = LocalContext.current
    val mine = m.role == "user"
    val paper = if (mine) DeepSlate else lerp(PaperWhite, mentorColor, 0.18f)
    val ink = if (mine) PaperWhite else InkBlack
    val rot = if (index % 2 == 0) -0.4f else 0.5f
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        if (mine) Spacer(Modifier.weight(1f))
        ScrapCard(
            paperColor = paper,
            shadowColor = Charcoal.copy(alpha = 0.22f),
            rotation = rot,
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            if (mine) Text(
                m.content,
                style = MaterialTheme.typography.bodyMedium,
                color = ink,
            )
            else {
                MarkdownText(m.content, scrollable = false, contentPadding = PaddingValues(0.dp))
            }
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 32.dp) {
                Row(
                    modifier = Modifier.align(Alignment.Start),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (m.content.isNotBlank()) {
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Message", m.content))
                                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                                Icon(
                                    painterResource(R.drawable.ic_copy),
                                    contentDescription = "Copy message",
                                    tint = ink,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                    if (!mine && m.complete && m.content.isNotBlank()) {
                        TextButton(
                            onClick = onAddToPlan,
                            enabled = canAddToPlan,
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) {
                            Text(if (extractingTask) "Finding an action…" else "Add to plan")
                        }
                    }
                }
            }
        }
        if (!mine) Spacer(Modifier.weight(1f))
    }
}
