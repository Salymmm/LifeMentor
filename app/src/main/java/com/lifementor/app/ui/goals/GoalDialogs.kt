package com.lifementor.app.ui.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifementor.app.data.Goal
import com.lifementor.app.data.Mentors
import com.lifementor.app.ui.components.TaskDateDialog
import java.time.Instant
import java.time.ZoneOffset

@Composable
internal fun GoalEditorDialog(
    goal: Goal? = null,
    enabled: Boolean = true,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var title by remember(goal?.id) { mutableStateOf(goal?.title ?: "") }
    var domain by remember(goal?.id) { mutableStateOf(goal?.domain ?: "pathfinder") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (goal == null) "新增目标" else "编辑目标") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("目标名称") },
                    minLines = 2,
                    maxLines = 4,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Text("归属领域", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Mentors.all.forEach { mentor ->
                        val selected = mentor.id == domain
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                mentor.emoji,
                                fontSize = 22.sp,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                                    )
                                    .clickable(enabled = enabled) { domain = mentor.id }
                                    .semantics { contentDescription = mentor.domainLabel }
                                    .padding(8.dp),
                            )
                        }
                    }
                }
                Text(
                    Mentors.byId(domain)?.domainLabel ?: domain,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title.trim(), domain) },
                enabled = enabled && title.isNotBlank(),
            ) { Text(if (goal == null) "创建" else "保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
internal fun TaskEditorDialog(
    level: Int,
    parentTitle: String,
    parentLabel: String,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onAdd: (String, Long?) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf<Long?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    val label = when (level) {
        1 -> "里程碑"
        2 -> "周任务"
        else -> "行动"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加$label") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "$parentLabel：$parentTitle",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("${label}名称") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    enabled = enabled,
                )
                Spacer(Modifier.height(10.dp))
                Text("日期（可选）", style = MaterialTheme.typography.titleSmall)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        dueDate?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() } ?: "未安排",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = { showDatePicker = true }, enabled = enabled) {
                        Text(if (dueDate == null) "选择日期" else "更改日期")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onAdd(title.trim(), dueDate) }, enabled = enabled && title.isNotBlank()) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
    if (showDatePicker && enabled) {
        TaskDateDialog(
            dueDate = dueDate,
            onDismiss = { showDatePicker = false },
            onSave = { date -> dueDate = date; showDatePicker = false },
        )
    }
}
