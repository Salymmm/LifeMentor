package com.lifementor.app.ui.mentors

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifementor.app.data.GameStore
import com.lifementor.app.data.Leveling
import com.lifementor.app.data.Mentors
import com.lifementor.app.data.Msg
import com.lifementor.app.ui.components.MarkdownText

@Composable
fun ChatScreen(mentorId: String, onBack: () -> Unit, vm: ChatViewModel = viewModel()) {
    LaunchedEffect(mentorId) { vm.open(mentorId) }
    val mentor = Mentors.byId(mentorId) ?: return
    val listState = rememberLazyListState()
    val msgs = vm.messages.value
    val canProposeHandoff = msgs.any { it.role == "assistant" && it.content.isNotBlank() }
    val domainXp = Leveling.domainTotals(GameStore.loadXP())[mentor.id] ?: 0

    LaunchedEffect(msgs.size, msgs.lastOrNull()?.content, vm.sending.value) {
        val itemCount = msgs.size + if (vm.sending.value) 1 else 0
        if (itemCount > 0) listState.scrollToItem(itemCount - 1)
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← 返回", fontSize = 16.sp) }
            Box(
                Modifier
                    .widthIn(44.dp, 44.dp)
                    .height(44.dp)
                    .background(mentor.color.copy(alpha = 0.16f), RoundedCornerShape(50)),
                contentAlignment = Alignment.Center,
            ) {
                Text(mentor.emoji, fontSize = 22.sp)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(mentor.name, fontWeight = FontWeight.Bold)
                Text(
                    "${mentor.domainLabel} ${domainXp} XP · ${mentor.en}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(
                onClick = { vm.requestHandoff() },
                enabled = canProposeHandoff && !vm.sending.value &&
                    !vm.handoffBusy.value && vm.handoffSuggestion.value == null,
            ) {
                Text(if (vm.handoffBusy.value) "分析中…" else "联动建议")
            }
            TextButton(onClick = { vm.clearHistory() }) { Text("清空") }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
        ) {
            items(msgs) { m -> Bubble(m, mentor.color) }
            if (vm.sending.value) {
                item {
                    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("${mentor.name.split(" ").lastOrNull()}正在输入…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        if (vm.handoffBusy.value) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("正在分析是否适合交给另一位导师", style = MaterialTheme.typography.bodySmall)
            }
        }

        vm.handoffSuggestion.value?.let { suggestion ->
            val targetName = Mentors.byId(suggestion.targetMentorId)?.name ?: suggestion.targetMentorId
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("建议联动至 $targetName", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("任务：${suggestion.taskTitle}")
                    if (suggestion.rationale.isNotBlank()) {
                        Text("原因：${suggestion.rationale}", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { vm.acceptHandoff() }) { Text("确认转交") }
                        TextButton(onClick = { vm.dismissHandoff() }) { Text("取消") }
                    }
                }
            }
        }

        vm.handoffStatus.value?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        vm.error.value?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        if (vm.crisis.value) {
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            ) {
                Text(
                    "🫂 如果你此刻非常难受：全国心理援助热线 12356（24 小时）。" +
                        "也可以联系信任的人或学校心理中心。树洞可以陪你梳理，但不能替代专业帮助。",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(10.dp),
                )
            }
        }

        TextButton(
            onClick = { vm.toggleSimulationMode() },
            enabled = !vm.sending.value && !vm.handoffBusy.value,
            modifier = Modifier.padding(start = 8.dp, top = 2.dp),
        ) {
            Text(if (vm.simulationMode.value) "🔮 推演模式已开启 · 点击关闭" else "🔮 推演模式")
        }

        if (vm.simulationMode.value) {
            Text(
                "本次选择会结合你的画像进行三路径比较；关闭后恢复普通导师对话。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = vm.draft.value,
                onValueChange = { vm.draft.value = it },
                placeholder = {
                    Text(
                        if (vm.simulationMode.value) "描述要比较的选择…"
                        else "和${mentor.name.split("·").lastOrNull()?.trim() ?: "导师"}说说…",
                    )
                },
                modifier = Modifier.weight(1f),
                maxLines = 4,
            )
            Spacer(Modifier.width(8.dp))
            if (vm.sending.value) {
                Button(onClick = { vm.cancelSending() }) { Text("停止") }
            } else {
                Button(
                    onClick = { vm.send() },
                    enabled = !vm.handoffBusy.value,
                ) {
                    Text(if (vm.simulationMode.value) "推演" else "发送")
                }
            }
        }
    }
}

@Composable
private fun Bubble(m: Msg, accent: androidx.compose.ui.graphics.Color) {
    val mine = m.role == "user"
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        if (mine) Spacer(Modifier.weight(1f))
        Card(
            Modifier.widthIn(max = 320.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (mine) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (mine) 16.dp else 4.dp,
                bottomEnd = if (mine) 4.dp else 16.dp,
            ),
        ) {
            if (mine) Text(
                m.content,
                style = MaterialTheme.typography.bodyMedium,
                color = if (mine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(12.dp),
            )
            else MarkdownText(m.content, scrollable = false)
        }
        if (!mine) Spacer(Modifier.weight(1f))
    }
}
