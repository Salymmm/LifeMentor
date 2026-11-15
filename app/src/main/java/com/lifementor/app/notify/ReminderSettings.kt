package com.lifementor.app.notify

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun ReminderSettings() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var enabled by remember { mutableStateOf(TaskReminders.isEnabled(context)) }
    var allowed by remember { mutableStateOf(TaskReminders.canPost(context)) }
    var message by remember { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) TaskReminders.setEnabled(context, true)
        enabled = TaskReminders.isEnabled(context)
        allowed = TaskReminders.canPost(context)
        message = if (allowed) null else "通知权限未允许，可在系统通知设置中开启。"
    }
    fun requestEnable() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            TaskReminders.setEnabled(context, true)
            enabled = true
            allowed = TaskReminders.canPost(context)
            message = if (allowed) null else "系统通知已关闭，请在系统通知设置中允许提醒。"
        }
    }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                TaskReminders.reconcile(context)
                enabled = TaskReminders.isEnabled(context)
                allowed = TaskReminders.canPost(context)
                message = null
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("每日行动提醒", style = MaterialTheme.typography.titleSmall)
                Switch(checked = enabled, onCheckedChange = { checked ->
                    if (checked) requestEnable() else {
                        TaskReminders.setEnabled(context, false)
                        enabled = false
                        message = null
                    }
                })
            }
            Text(
                when {
                    enabled && allowed -> "已开启 · 系统通知已允许"
                    enabled -> "已暂停 · 系统通知未允许"
                    else -> "未开启"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text("每天本地时间 08:00 左右，提醒今日或明日的一个未完成行动。系统省电可能延迟提醒。", style = MaterialTheme.typography.bodySmall)
            if (!allowed) TextButton(onClick = { requestEnable() }) { Text("允许通知") }
            TextButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }) { Text("系统通知设置") }
            OutlinedButton(onClick = { message = TaskReminders.showTest(context) }) { Text("发送测试提醒") }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
