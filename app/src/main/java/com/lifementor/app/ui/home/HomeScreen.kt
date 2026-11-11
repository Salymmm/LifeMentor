package com.lifementor.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.lifementor.app.data.Mentors
import com.lifementor.app.ui.components.RadarChart

@Composable
fun HomeScreen(
    goGoals: () -> Unit,
    goMentors: () -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    LaunchedEffect(Unit) { vm.refresh() }
    val s = vm.snap.value

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 角色卡
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(
                    Modifier
                        .size(56.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(s.role.avatar, fontSize = 28.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "${s.role.nickname}　Lv.${s.level}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        s.role.stage.ifBlank { "人生玩家" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { s.into.toFloat() / s.need },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                    )
                    Text(
                        "${s.into}/${s.need} XP　·　总 ${s.totalXp} XP",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🏆", fontSize = 20.sp)
                    Text(
                        "${s.achievementsDone}/${s.achievementsTotal}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }

        if (s.role.vision.isNotBlank()) {
            Text(
                "「${s.role.vision}」",
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        // 今日行动
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("今日行动", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                if (s.today.isEmpty()) {
                    Text(
                        "今天还没有可勾选的行动。去「目标」页让 AI 帮你拆一个目标吧。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = goGoals) { Text("去拆解目标 →") }
                } else {
                    s.today.forEach { t ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(onCheckedChange = { vm.toggle(t) }, checked = false)
                            Text(t.title, fontSize = 14.sp)
                        }
                    }
                    Text(
                        "完成一项 +10 XP",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // 五维成长
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("五维成长", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                RadarChart(
                    labels = s.radar.map { it.first },
                    values = s.radar.map { it.second },
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                )
                Row(Modifier.fillMaxWidth()) {
                    s.radar.forEachIndexed { i, p ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(p.first, style = MaterialTheme.typography.labelSmall)
                            Text("${s.radarXp[i]}", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        TextButton(onClick = goMentors) { Text("找导师聊聊 →") }
        Spacer(Modifier.height(8.dp))
    }
}
