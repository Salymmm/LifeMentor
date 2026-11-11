package com.lifementor.app.ui.mentors

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifementor.app.data.AppStorage
import com.lifementor.app.data.GameStore
import com.lifementor.app.data.Leveling
import com.lifementor.app.data.Mentors
import org.json.JSONObject

@Composable
fun MentorsTab() {
    var openId by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var showCustom by remember { mutableStateOf(false) }

    val list = remember(refreshKey) { Mentors.visibleList() }
    val xpTotals = remember(refreshKey) { Leveling.domainTotals(GameStore.loadXP()) }

    openId?.let { id ->
        ChatScreen(mentorId = id, onBack = { openId = null })
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("我的导师团", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "他们共享你授权的人生档案：画像、活跃目标和本周进度。各管一摊，性格各异。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))

        list.forEach { m ->
            Card(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .clickable { openId = m.id },
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(46.dp)
                            .background(m.color.copy(alpha = 0.16f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(m.emoji, fontSize = 24.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m.name, fontWeight = FontWeight.Bold)
                        Text(m.tagline, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${m.domainLabel}已积累 ${xpTotals[m.id] ?: 0} XP",
                            style = MaterialTheme.typography.labelSmall,
                            color = m.color,
                        )
                    }
                    Text("聊聊 →", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (Mentors.custom() == null) {
            OutlinedButton(
                onClick = { showCustom = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("＋ 创建第六位自定义导师（创业 / 艺术 / 旅行…）") }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showCustom) {
        CustomMentorDialog(
            onDismiss = { showCustom = false },
            onCreate = { json ->
                AppStorage.putPref("customMentor", json)
                showCustom = false
                refreshKey++
            },
        )
    }
}

@Composable
private fun CustomMentorDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("🚀") }
    var domain by remember { mutableStateOf("") }
    var persona by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义导师") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名字，如：创业导师 · 老 K") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = emoji, onValueChange = { emoji = it }, label = { Text("一个表情当头像，如 🚀") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = domain, onValueChange = { domain = it }, label = { Text("领域名，如：创业成长") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = persona, onValueChange = { persona = it },
                    label = { Text("TA 是谁、怎么说话、擅长什么（30 字以上）") },
                    modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 6)
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isNotBlank() && persona.length >= 20) {
                    onCreate(
                        JSONObject()
                            .put("name", name.trim()).put("emoji", emoji.trim())
                            .put("domain", domain.trim().ifBlank { "自定义领域" })
                            .put("tagline", "由你定义的导师").put("persona", persona.trim()).toString()
                    )
                }
            }) { Text("创建") }
        },
        dismissButton = { TextButton(onDismiss) { Text("取消") } },
    )
}
