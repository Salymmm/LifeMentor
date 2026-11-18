package com.lifementor.app.notify

import android.Manifest
import android.app.TimePickerDialog
import android.text.format.DateFormat
import java.time.LocalTime
import java.time.format.DateTimeFormatter
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
    var time by remember { mutableStateOf(TaskReminders.reminderTime(context)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) TaskReminders.setEnabled(context, true)
        enabled = TaskReminders.isEnabled(context)
        allowed = TaskReminders.canPost(context)
        message = if (allowed) null else "Notification permission was not granted. Enable it in system notification settings."
    }
    fun requestEnable() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            TaskReminders.setEnabled(context, true)
            enabled = true
            allowed = TaskReminders.canPost(context)
            message = if (allowed) null else "System notifications are off. Allow reminders in system notification settings."
        }
    }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                TaskReminders.reconcile(context)
                enabled = TaskReminders.isEnabled(context)
                allowed = TaskReminders.canPost(context)
                time = TaskReminders.reminderTime(context)
                message = null
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Daily action reminders", style = MaterialTheme.typography.titleSmall)
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
                    enabled && allowed -> "On · System notifications allowed"
                    enabled -> "Paused · System notifications not allowed"
                    else -> "Off"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            val timeText = time.format(DateTimeFormatter.ofPattern("HH:mm"))
            TextButton(onClick = {
                TimePickerDialog(context, { _, hour, minute ->
                    time = LocalTime.of(hour, minute)
                    TaskReminders.setReminderTime(context, time)
                    message = "Reminder time saved: " + time.format(DateTimeFormatter.ofPattern("HH:mm"))
                }, time.hour, time.minute, DateFormat.is24HourFormat(context)).show()
            }) { Text("Daily reminder time: $timeText · Change") }
            Text("Around $timeText local time each day, get a reminder for one unfinished action due today or tomorrow. Battery saving may delay reminders.", style = MaterialTheme.typography.bodySmall)
            if (!allowed) TextButton(onClick = { requestEnable() }) { Text("Allow notifications") }
            TextButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }) { Text("System notification settings") }
            OutlinedButton(onClick = { message = TaskReminders.showTest(context) }) { Text("Send test reminder") }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
