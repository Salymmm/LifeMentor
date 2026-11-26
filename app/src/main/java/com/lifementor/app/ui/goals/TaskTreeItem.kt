package com.lifementor.app.ui.goals

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifementor.app.data.TaskItem

@Composable
internal fun TaskTreeItem(
    task: TaskItem,
    depth: Int,
    childCount: Int,
    expanded: Boolean,
    canMutate: Boolean,
    onToggleExpanded: () -> Unit,
    onEdit: () -> Unit,
    onSetDate: () -> Unit,
    onDelete: () -> Unit,
    onAddSubtask: (() -> Unit)? = null,
    onToggleDone: ((Boolean) -> Unit)? = null,
) {
    var menuOpen by remember(task.id) { mutableStateOf(false) }
    var fullText by rememberSaveable(task.id) { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(start = (depth.coerceAtMost(3) * 12).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onToggleDone != null) {
            Checkbox(checked = task.done, onCheckedChange = onToggleDone, enabled = canMutate)
        }
        Column(
            Modifier.weight(1f).clickable(
                onClickLabel = if (childCount > 0) {
                    if (expanded) "Collapse subtasks" else "Expand subtasks"
                } else if (fullText) "Collapse text" else "Expand text",
            ) {
                if (childCount > 0) onToggleExpanded() else fullText = !fullText
            }.padding(vertical = 8.dp),
        ) {
            Text(
                (if (childCount > 0) { if (expanded) "▾ " else "▸ " } else "") + task.title,
                fontSize = if (depth == 0) 14.sp else 13.sp,
                fontWeight = if (depth == 0) FontWeight.SemiBold else FontWeight.Normal,
                textDecoration = if (task.done) TextDecoration.LineThrough else null,
                maxLines = if (fullText || (childCount > 0 && expanded)) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
            )
            task.dueDateText()?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        Box {
            IconButton(
                onClick = { menuOpen = true },
                enabled = canMutate,
                modifier = Modifier.semantics { contentDescription = "Manage task: ${task.title}" },
            ) { Text("⋯", fontSize = 24.sp) }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("Edit task") }, onClick = { menuOpen = false; onEdit() })
                DropdownMenuItem(text = { Text("Set date") }, onClick = { menuOpen = false; onSetDate() })
                onAddSubtask?.let { add ->
                    DropdownMenuItem(text = { Text("Add subtask") }, onClick = { menuOpen = false; add() })
                }
                DropdownMenuItem(
                    text = { Text("Delete task", color = MaterialTheme.colorScheme.error) },
                    onClick = { menuOpen = false; onDelete() },
                )
            }
        }
    }
}
