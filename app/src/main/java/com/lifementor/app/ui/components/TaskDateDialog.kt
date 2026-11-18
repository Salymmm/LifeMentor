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
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (dueDate != null) {
                    TextButton(onClick = { onSave(null) }) { Text("Clear date") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    ) {
        DatePicker(state = state, showModeToggle = false)
    }
}
