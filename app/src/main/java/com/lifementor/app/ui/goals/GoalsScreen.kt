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
import com.lifementor.app.ui.components.DisplayTitle
import com.lifementor.app.ui.components.LabeledField
import com.lifementor.app.ui.components.PaperChip
import com.lifementor.app.ui.components.ScrapCard
import com.lifementor.app.ui.components.StickerButton
import com.lifementor.app.ui.theme.Charcoal
import com.lifementor.app.ui.theme.InkSoft
import com.lifementor.app.ui.theme.PaperWhite
import com.lifementor.app.ui.theme.ScrapBlue
import com.lifementor.app.ui.theme.ScrapRed
import com.lifementor.app.ui.theme.ScrapYellow
import com.lifementor.app.ui.components.MentorAvatar

@Composable
fun GoalsScreen(vm: GoalsViewModel = viewModel()) {
    var showAdd by remember { mutableStateOf(false) }
    var quickAddGoalId by remember { mutableStateOf<Long?>(null) }

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
            vm.xpToast.value?.let {
                Text(it, color = ScrapRed, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(Modifier.width(8.dp))
            }
            StickerButton("+ New goal", onClick = { showAdd = true })
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
                    ScrapCard(
                        paperColor = PaperWhite,
                        shadowColor = Charcoal.copy(alpha = 0.18f),
                        fillWidth = true,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            Text("No goals yet", fontWeight = FontWeight.Bold)
                            Text(
                                "Tap New goal and write down what matters. AI will split it into milestones → weekly tasks → daily actions.",
                                style = MaterialTheme.typography.bodySmall,
                                color = InkSoft,
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

    ScrapCard(
        paperColor = if (goal.status == "active") PaperWhite else ScrapYellow.copy(alpha = 0.6f),
        shadowColor = Charcoal.copy(alpha = 0.18f),
        rotation = -0.2f,
        fillWidth = true,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val avatarMentor = mentor
                if (avatarMentor != null) {
                    MentorAvatar(avatarMentor, 40.dp, fallbackEmojiSize = 20.sp)
                } else {
                    Box(
                        Modifier
                            .size(40.dp)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                CircleShape,
                            ),
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
                        textDecoration = if (goal.status == "done")
                            androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                    )
                    Text(
                        if (goal.decomposed) {
                            val d = sub.count { it.done }
                            "${mentor?.domainLabel ?: goal.domain}  $d/${sub.size} done"
                        } else {
                            mentor?.domainLabel ?: goal.domain
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (goal.decomposed) {
                    PaperChip(if (open) "Collapse" else "Expand",
                        onClick = { vm.expanded[goal.id] = !(vm.expanded[goal.id] == true) })
                }
            }

            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!goal.decomposed) {
                    if (isDecomposing) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = ScrapRed)
                            Spacer(Modifier.width(6.dp))
                            Text("Decomposing…", style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        StickerButton("✂ AI-decompose", onClick = { vm.decompose(goal.id) },
                            enabled = !vm.busy.value, arrow = true)
                    }
                }
                Spacer(Modifier.width(6.dp))
                if (goal.decomposed) {
                    PaperChip("+ Daily action", onClick = onQuickAdd)
                }
                Spacer(Modifier.weight(1f))
                if (goal.status == "active") {
                    PaperChip("✓ Done", onClick = { vm.completeGoal(goal.id) })
                }
                PaperChip("Delete", onClick = { vm.deleteGoal(goal.id) })
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
        title = { Text("New goal") },
        text = {
            Column {
                LabeledField("What do you want to achieve? (be specific)", title, singleLine = false, minLines = 2) { title = it }
                Spacer(Modifier.height(10.dp))
                Text("Domain", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth()) {
                    Mentors.all.forEach { m ->
                        val sel = m.id == domain
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(
                                    if (sel) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                                .clickable { domain = m.id }
                                .padding(4.dp),
                        ) {
                            MentorAvatar(m, 34.dp, fallbackEmojiSize = 22.sp)
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                }
                Text(Mentors.byId(domain)?.domainLabel ?: "", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            StickerButton("Create", onClick = { if (title.isNotBlank()) onCreate(title.trim(), domain) },
                shadowColor = ScrapBlue)
        },
        dismissButton = { PaperChip("Cancel", onClick = onDismiss) },
    )
}

@Composable
private fun QuickActionDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var title by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a daily action") },
        text = {
            LabeledField("e.g. Send 2 CVs matched to the job description", title, singleLine = false, minLines = 2) { title = it }
        },
        confirmButton = { StickerButton("Add (+10 XP)", onClick = { if (title.isNotBlank()) onAdd(title.trim()) }) },
        dismissButton = { PaperChip("Cancel", onClick = onDismiss) },
    )
}
