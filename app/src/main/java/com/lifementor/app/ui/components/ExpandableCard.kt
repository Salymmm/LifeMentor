package com.lifementor.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ExpandableCard(title: String, initiallyExpanded: Boolean = true, content: @Composable () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = if (expanded) "收起$title" else "展开$title") { expanded = !expanded }
            .semantics { stateDescription = if (expanded) "已展开" else "已收起" }
            .padding(14.dp)) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(if (expanded) "▾" else "▸", color = MaterialTheme.colorScheme.primary)
        }
        if (expanded) Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) { content() }
    }
}
