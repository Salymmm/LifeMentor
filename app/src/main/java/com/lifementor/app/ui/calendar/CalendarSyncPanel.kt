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
                    Text("手机日历", style = MaterialTheme.typography.titleMedium)
                    Text(vm.targets.find { it.id == vm.selectedId }?.let { "${it.name} · ${it.account}" }
                        ?: "首次使用需授权并选择目标日历", style = MaterialTheme.typography.bodySmall)
                }
                if (allowed) TextButton(onClick = vm::chooseTarget, enabled = !vm.busy) { Text("更换") }
            }
            Text("同步所有有日期的任务，含已完成、不限月份。以 App 为准，更新并清理本功能创建的日程。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = {
                if (CalendarSyncProvider.hasPermissions(context)) vm.beginSync()
                else permission.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
            }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
                if (vm.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary)
                Text(if (vm.busy) "  正在处理…" else "一键同步全部")
            }
            if (denied && !allowed) {
                Text("需要允许日历读写权限后才能批量同步。", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                }) { Text("打开系统权限设置") }
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
            title = { Text("选择同步日历") },
            text = {
                Column {
                    Text("仅同步到选中的日历。更换目标会保留旧日历内容；以前通过 ↗ 手动添加的日程不会被接管。",
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
                enabled = vm.targets.any { it.id == selected } && !vm.busy) { Text("同步到此日历") } },
            dismissButton = { TextButton(onClick = vm::dismissPicker) { Text("取消") } },
        )
    }
}
