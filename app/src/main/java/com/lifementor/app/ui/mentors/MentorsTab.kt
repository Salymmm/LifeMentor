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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.lifementor.app.ui.components.DisplayTitle
import com.lifementor.app.ui.components.PaperChip
import com.lifementor.app.ui.components.LabeledField
import com.lifementor.app.ui.components.ScrapCard
import com.lifementor.app.ui.components.StickerButton
import com.lifementor.app.ui.theme.Charcoal
import com.lifementor.app.ui.theme.InkBlack
import com.lifementor.app.ui.theme.InkSoft
import com.lifementor.app.ui.theme.PaperWhite
import com.lifementor.app.ui.components.MentorAvatar
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
        DisplayTitle("My Guides")
        Text(
            "Five perspectives for every step. They share the archive you authorize: profile, active goals and weekly progress.",
            style = MaterialTheme.typography.bodySmall,
            color = InkSoft,
        )
        Spacer(Modifier.height(14.dp))

        list.forEachIndexed { mi, m ->
            ScrapCard(
                paperColor = PaperWhite,
                shadowColor = Charcoal.copy(alpha = 0.18f),
                rotation = if (mi % 2 == 0) -0.3f else 0.35f,
                fillWidth = true,
                modifier = Modifier
                    .clickable { openId = m.id }
                    .padding(bottom = 14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MentorAvatar(m, 54.dp, fallbackEmojiSize = 26.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m.name, style = MaterialTheme.typography.titleMedium, color = InkBlack)
                        Text(m.tagline, style = MaterialTheme.typography.bodySmall, color = InkSoft)
                        Spacer(Modifier.height(4.dp))
                        Box {
                            Box(
                                Modifier
                                    .matchParentSize()
                                    .offset(2.dp, 2.dp)
                                    .background(m.color.copy(alpha = 0.45f), RoundedCornerShape(4.dp)),
                            )
                            Text(
                                "${m.domainLabel} · ${xpTotals[m.id] ?: 0} XP earned",
                                style = MaterialTheme.typography.labelSmall,
                                color = InkBlack,
                                modifier = Modifier
                                    .background(m.color, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                    PaperChip("Chat ›", onClick = { openId = m.id })
                }
            }
        }

        if (Mentors.custom() == null) {
            StickerButton(
                "+ Create a custom sixth mentor (startup / art / travel…)",
                onClick = { showCustom = true },
            )
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
        title = { Text("Custom mentor") },
        text = {
            Column {
                LabeledField("Name, e.g. Founder Coach · K", name) { name = it }
                LabeledField("One emoji as the avatar, e.g. 🚀", emoji) { emoji = it }
                LabeledField("Domain label, e.g. Startup Growth", domain) { domain = it }
                LabeledField("Who they are, how they talk, what they are good at (30+ chars)", persona,
                    singleLine = false, minLines = 3) { persona = it }
            }
        },
        confirmButton = {
            StickerButton(
                "Create",
                onClick = {
                    if (name.isNotBlank() && persona.length >= 20) {
                        onCreate(
                            JSONObject()
                                .put("name", name.trim()).put("emoji", emoji.trim())
                                .put("domain", domain.trim().ifBlank { "Custom Guide" })
                                .put("tagline", "A mentor you defined").put("persona", persona.trim()).toString()
                        )
                    }
                },
            )
        },
        dismissButton = { PaperChip("Cancel", onClick = onDismiss) },
    )
}
