package com.lifementor.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDateDialog(dueDate: Long?, onDismiss: () -> Unit, onSave: (Long?) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = dueDate)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onSave(state.selectedDateMillis) },
                enabled = state.selectedDateMillis != null,
            ) { Text("保存") }
        },
        dismissButton = {
            Row {
                if (dueDate != null) {
                    TextButton(onClick = { onSave(null) }) { Text("清除日期") }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    ) {
        DatePicker(state = state, showModeToggle = false)
    }
}
