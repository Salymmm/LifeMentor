package com.lifementor.app.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.lifementor.app.R

@Composable
fun TaskDateAction(title: String, dueDate: String?, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(40.dp)) {
        Icon(painterResource(R.drawable.ic_task_calendar),
            contentDescription = if (dueDate == null) "为「$title」安排日期" else "更改「$title」日期：$dueDate",
            modifier = Modifier.size(20.dp))
    }
}
