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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifementor.app.data.AppStorage
import com.lifementor.app.data.GameStore
import com.lifementor.app.data.Leveling
import com.lifementor.app.data.PlanMeta
import com.lifementor.app.ui.components.DisplayTitle
import com.lifementor.app.ui.components.PaperChip
import com.lifementor.app.ui.components.ScrapCard
import com.lifementor.app.ui.components.StickerButton
import com.lifementor.app.ui.components.MarkdownText
import com.lifementor.app.ui.theme.Cardboard
import com.lifementor.app.ui.theme.Charcoal
import com.lifementor.app.ui.theme.InkBlack
import com.lifementor.app.ui.theme.InkSoft
import com.lifementor.app.ui.theme.PaperWhite
import com.lifementor.app.ui.theme.ScrapBlue
import com.lifementor.app.ui.theme.ScrapRed
import com.lifementor.app.ui.theme.ScrapYellow
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
            Column {
                DisplayTitle("Archive")
                Text("Life Archive", style = MaterialTheme.typography.labelSmall, color = InkSoft)
            }
        }
        Row(Modifier.padding(horizontal = 12.dp)) {
            val labels = listOf("Career Plan", "Weekly Review", "Achievements")
            labels.forEachIndexed { i, l ->
                val sel = mode == i
                Text(
                    l,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                    color = if (sel) PaperWhite else InkSoft,
                    modifier = Modifier
                        .rotate(if (sel) -0.6f else 0.3f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (sel) ScrapRed else PaperWhite
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
        ScrapCard(
            paperColor = PaperWhite,
            shadowColor = Charcoal.copy(alpha = 0.18f),
            rotation = -0.2f,
            fillWidth = true,
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            Column(Modifier.padding(12.dp)) {
                OutlinedTextField(
                    value = vm.requestText.value,
                    onValueChange = { vm.requestText.value = it },
                    label = { Text("Anything to emphasize this time? (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 1,
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (vm.state is com.lifementor.app.ui.plan.GenState.Running) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = ScrapRed)
                            Spacer(Modifier.width(6.dp))
                            Text("Generating…", style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        StickerButton(
                            if (vm.versions.isEmpty()) "✦ Generate career plan" else "✦ Update career plan",
                            onClick = { vm.generate() },
                            arrow = true,
                        )
                    }
                    if (vm.versions.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        PaperChip("History (${vm.versions.size})", onClick = { showHistory = true })
                        Spacer(Modifier.width(8.dp))
                        PaperChip("Copy", onClick = {
                            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("plan", vm.content.value))
                        })
                        Spacer(Modifier.width(8.dp))
                        PaperChip("Export", onClick = {
                            vm.selectedId?.let { id ->
                                vm.versions.find { it.id == id }?.let { meta ->
                                    exportDoc(ctx, "plans", meta)
                                }
                            }
                        })
                    }
                }
                (vm.state as? com.lifementor.app.ui.plan.GenState.Error)?.let { e ->
                    Spacer(Modifier.height(6.dp))
                    Text(e.msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    PaperChip("Go to Settings", onClick = goSettings)
                }
            }
        }
        Box(Modifier.weight(1f)) {
            if (vm.content.value.isBlank()) {
                EmptyHint("No career blueprint yet. Fill in your profile, then tap the button above: AI writes a full Markdown plan and keeps it updated as your profile changes.")
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
        ScrapCard(
            paperColor = ScrapYellow,
            shadowColor = Charcoal.copy(alpha = 0.18f),
            rotation = 0.25f,
            fillWidth = true,
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (vm.running.value) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = ScrapRed)
                            Spacer(Modifier.width(6.dp))
                            Text("Drafting review…", style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        StickerButton("✦ Generate this week\u2019s review", onClick = { vm.generate() },
                            baseColor = ScrapBlue, contentColor = PaperWhite, arrow = true)
                    }
                    if (vm.versions.value.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        PaperChip("History (${vm.versions.value.size})", onClick = { showHistory = true })
                        Spacer(Modifier.width(8.dp))
                        PaperChip("Export", onClick = {
                            vm.selectedId.value?.let { id ->
                                vm.versions.value.find { it.id == id }?.let { exportDoc(ctx, "reviews", it) }
                            }
                        })
                    }
                }
                vm.error.value?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    PaperChip("Go to Settings", onClick = goSettings)
                }
            }
        }
        Box(Modifier.weight(1f)) {
            if (vm.content.value.isBlank()) {
                EmptyHint("The weekly review aggregates your completed tasks and XP, then AI analyzes obstacles and proposes up to 3 suggestions for next week. Finish a few tasks and come back.")
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
            StatCard("Total XP", "${totalXp}", Modifier.weight(1f))
            StatCard("Level", "Lv.${Leveling.levelOf(totalXp)}", Modifier.weight(1f))
            StatCard("Done tasks", "$done", Modifier.weight(1f))
            StatCard("Reviews", "${AppStorage.versions("reviews").size}", Modifier.weight(1f))
        }
        DisplayTitle("Achievements")
        Text("Achievement Wall", style = MaterialTheme.typography.titleMedium, color = InkBlack)
        ach.chunked(2).forEachIndexed { ri, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { a ->
                    ScrapCard(
                        paperColor = if (a.earned) PaperWhite else Cardboard.copy(alpha = 0.6f),
                        shadowColor = Charcoal.copy(alpha = 0.18f),
                        rotation = if (ri % 2 == 0) -0.4f else 0.4f,
                        fillWidth = true,
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(a.emoji, fontSize = MaterialTheme.typography.titleLarge.fontSize)
                            Text(a.name, fontWeight = FontWeight.Bold,
                                color = if (a.earned) InkBlack else InkSoft)
                            Text(a.desc, style = MaterialTheme.typography.bodySmall,
                                color = InkSoft)
                            if (!a.earned) Text("Locked", style = MaterialTheme.typography.labelSmall,
                                color = InkSoft.copy(alpha = 0.7f))
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
    ScrapCard(
        paperColor = PaperWhite,
        shadowColor = Charcoal.copy(alpha = 0.18f),
        rotation = if (k.length % 2 == 0) -0.5f else 0.5f,
        fillWidth = true,
        modifier = modifier,
    ) {
        Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(v, fontWeight = FontWeight.Bold, color = InkBlack)
            Text(k, style = MaterialTheme.typography.labelSmall, color = InkSoft)
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
        Text(text, style = MaterialTheme.typography.bodyMedium, color = InkSoft)
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
        Intent.createChooser(send, "Share / export Markdown").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
        title = { Text("Version history") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                list.forEachIndexed { i, m ->
                    val sel = m.id == selectedId
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (sel) ScrapYellow else PaperWhite
                            )
                            .clickable { onSelect(m.id) }
                            .padding(10.dp),
                    ) {
                        Column {
                            Text(fmt.format(Date(m.id)) + if (i == 0) "  · latest" else "",
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                color = InkBlack)
                            Text(
                                m.summary.ifBlank { "(no summary)" },
                                style = MaterialTheme.typography.bodySmall,
                                color = InkSoft,
                            )
                            Text("Model: ${m.model}", style = MaterialTheme.typography.bodySmall,
                                color = InkSoft.copy(alpha = 0.7f))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        },
        confirmButton = { PaperChip("Close", onClick = onDismiss) },
    )
}
