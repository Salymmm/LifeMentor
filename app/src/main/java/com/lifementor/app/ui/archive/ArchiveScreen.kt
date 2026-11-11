package com.lifementor.app.ui.archive

import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifementor.app.data.AppStorage
import com.lifementor.app.data.GameStore
import com.lifementor.app.data.Leveling
import com.lifementor.app.data.PlanMeta
import com.lifementor.app.ui.components.MarkdownText
import com.lifementor.app.ui.plan.PlanViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ArchiveScreen(goSettings: () -> Unit) {
    var mode by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("人生档案", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Row(Modifier.padding(horizontal = 12.dp)) {
            val labels = listOf("职业规划", "每周复盘", "成就墙")
            labels.forEachIndexed { i, l ->
                val sel = mode == i
                Text(
                    l,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                    color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        )
                        .clickable { mode = i }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.weight(1f)) {
            when (mode) {
                0 -> PlanSection(goSettings)
                1 -> ReviewSection(goSettings)
                else -> AchievementsSection()
            }
        }
    }
}

@Composable
private fun PlanSection(goSettings: () -> Unit, vm: PlanViewModel = viewModel()) {
    val ctx = LocalContext.current
    var showHistory by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Column(Modifier.padding(12.dp)) {
                OutlinedTextField(
                    value = vm.requestText.value,
                    onValueChange = { vm.requestText.value = it },
                    label = { Text("这次想特别强调？（可选）") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 1,
                    maxLines = 3,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { vm.generate() }, enabled = vm.state !is com.lifementor.app.ui.plan.GenState.Running) {
                        if (vm.state is com.lifementor.app.ui.plan.GenState.Running) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                            Text("生成中…")
                        } else {
                            Text(if (vm.versions.isEmpty()) "生成职业规划" else "更新职业规划")
                        }
                    }
                    if (vm.versions.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { showHistory = true }) { Text("历史(${vm.versions.size})") }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = {
                            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("plan", vm.content.value))
                        }) { Text("复制") }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = {
                            vm.selectedId?.let { id ->
                                vm.versions.find { it.id == id }?.let { meta ->
                                    exportDoc(ctx, "plans", meta)
                                }
                            }
                        }) { Text("导出") }
                    }
                }
                (vm.state as? com.lifementor.app.ui.plan.GenState.Error)?.let { e ->
                    Spacer(Modifier.height(6.dp))
                    Text(e.msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Row {
                        TextButton(goSettings) { Text("去设置") }
                    }
                }
            }
        }
        Box(Modifier.weight(1f)) {
            if (vm.content.value.isBlank()) {
                EmptyHint("还没有职业规划蓝图。填好画像后点上方按钮，AI 会生成完整 Markdown 规划，之后随画像变化持续更新。")
            } else {
                MarkdownText(vm.content.value)
            }
        }
    }

    if (showHistory) {
        MetaHistoryDialog(vm.versions, vm.selectedId, onDismiss = { showHistory = false }, onSelect = { vm.select(it) })
    }
}

@Composable
private fun ReviewSection(goSettings: () -> Unit, vm: ReviewViewModel = viewModel()) {
    val ctx = LocalContext.current
    var showHistory by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { vm.generate() }, enabled = !vm.running.value) {
                        if (vm.running.value) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                            Text("复盘生成中…")
                        } else {
                            Text("生成本周复盘")
                        }
                    }
                    if (vm.versions.value.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { showHistory = true }) { Text("历史(${vm.versions.value.size})") }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = {
                            vm.selectedId.value?.let { id ->
                                vm.versions.value.find { it.id == id }?.let { exportDoc(ctx, "reviews", it) }
                            }
                        }) { Text("导出") }
                    }
                }
                vm.error.value?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    TextButton(goSettings) { Text("去设置") }
                }
            }
        }
        Box(Modifier.weight(1f)) {
            if (vm.content.value.isBlank()) {
                EmptyHint("每周复盘会聚合本周任务完成情况与 XP 数据，由 AI 给出障碍分析和最多 3 条下周建议。完成几个任务后回来试试。")
            } else {
                MarkdownText(vm.content.value)
            }
        }
    }
    if (showHistory) {
        MetaHistoryDialog(vm.versions.value, vm.selectedId.value, onDismiss = { showHistory = false }, onSelect = { vm.select(it) })
    }
}

@Composable
private fun AchievementsSection() {
    val ach = remember { Leveling.achievements() }
    val totalXp = GameStore.loadXP().sumOf { it.amount }
    val done = GameStore.loadTasks().count { it.done }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("总 XP", "${totalXp}", Modifier.weight(1f))
            StatCard("等级", "Lv.${Leveling.levelOf(totalXp)}", Modifier.weight(1f))
            StatCard("完成任务", "$done", Modifier.weight(1f))
            StatCard("复盘次数", "${AppStorage.versions("reviews").size}", Modifier.weight(1f))
        }
        Text("成就墙", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        ach.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { a ->
                    Card(Modifier.weight(1f)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(a.emoji, fontSize = MaterialTheme.typography.titleLarge.fontSize)
                            Text(a.name, fontWeight = FontWeight.Bold,
                                color = if (a.earned) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline)
                            Text(a.desc, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (!a.earned) Text("未解锁", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun StatCard(k: String, v: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(v, fontWeight = FontWeight.Bold)
            Text(k, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun exportDoc(ctx: Context, bucket: String, meta: PlanMeta) {
    val f = AppStorage.fileForShare(bucket, meta) ?: return
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/markdown"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(
        Intent.createChooser(send, "分享 / 导出 Markdown").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

@Composable
private fun MetaHistoryDialog(
    list: List<PlanMeta>,
    selectedId: Long?,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit,
) {
    val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("版本历史") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                list.forEachIndexed { i, m ->
                    val sel = m.id == selectedId
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            )
                            .clickable { onSelect(m.id) }
                            .padding(10.dp),
                    ) {
                        Column {
                            Text(fmt.format(Date(m.id)) + if (i == 0) "　· 最新" else "",
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                            Text(
                                m.summary.ifBlank { "（无摘要）" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text("模型：${m.model}", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("关闭") } },
    )
}
