package com.lifementor.app.ui.calendar

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifementor.app.notify.CalendarSyncProvider

@Composable
fun CalendarSyncPanel(vm: CalendarSyncViewModel = viewModel()) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var allowed by remember { mutableStateOf(CalendarSyncProvider.hasPermissions(context)) }
    var denied by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        allowed = CalendarSyncProvider.hasPermissions(context)
        denied = !allowed
        if (allowed) vm.beginSync()
    }
    DisposableEffect(lifecycle, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val wasAllowed = allowed
                allowed = CalendarSyncProvider.hasPermissions(context)
                if (allowed && wasAllowed) vm.refreshTargets()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Device calendar", style = MaterialTheme.typography.titleMedium)
                    Text(vm.targets.find { it.id == vm.selectedId }?.let { "${it.name} · ${it.account}" }
                        ?: "Allow access and choose a calendar to get started", style = MaterialTheme.typography.bodySmall)
                }
                if (allowed) TextButton(onClick = vm::chooseTarget, enabled = !vm.busy) { Text("Change") }
            }
            Text("Sync all dated tasks, including completed tasks, across all months. App data updates and removes events created by this sync feature.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = {
                if (CalendarSyncProvider.hasPermissions(context)) vm.beginSync()
                else permission.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
            }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
                if (vm.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary)
                Text(if (vm.busy) "  Working…" else "Sync all tasks")
            }
            if (denied && !allowed) {
                Text("Allow calendar read and write access to sync tasks in bulk.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                }) { Text("Open system permission settings") }
            }
            vm.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
    if (vm.choosing && allowed) {
        var selected by remember(vm.choosing, vm.targets) {
            mutableStateOf(vm.selectedId?.takeIf { id -> vm.targets.any { it.id == id } } ?: vm.targets.firstOrNull()?.id)
        }
        AlertDialog(
            onDismissRequest = vm::dismissPicker,
            title = { Text("Choose a calendar to sync") },
            text = {
                Column {
                    Text("Only the selected calendar is synced. Switching calendars keeps events in the old calendar. Events added manually with ↗ remain separate.",
                        style = MaterialTheme.typography.bodySmall)
                    Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                        vm.targets.forEach { target ->
                            Row(Modifier.fillMaxWidth().clickable { selected = target.id }.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = selected == target.id, onClick = { selected = target.id })
                                Column(Modifier.weight(1f)) {
                                    Text(target.name)
                                    Text(target.account, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selected?.let(vm::selectAndSync) },
                enabled = vm.targets.any { it.id == selected } && !vm.busy) { Text("Sync to this calendar") } },
            dismissButton = { TextButton(onClick = vm::dismissPicker) { Text("Cancel") } },
        )
    }
}
