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
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.lifementor.app.ui.components.MarkdownText
import com.lifementor.app.data.Msg
import com.lifementor.app.ui.components.MentorAvatar
import com.lifementor.app.ui.components.PaperChip
import com.lifementor.app.ui.components.ScrapCard
import com.lifementor.app.ui.components.StickerButton
import com.lifementor.app.ui.theme.Charcoal
import com.lifementor.app.ui.theme.DeepSlate
import com.lifementor.app.ui.theme.InkBlack
import com.lifementor.app.ui.theme.InkSoft
import com.lifementor.app.ui.theme.PaperWhite
import com.lifementor.app.ui.theme.ScrapBlue
import com.lifementor.app.ui.theme.ScrapPurple
import com.lifementor.app.ui.theme.ScrapRed
import com.lifementor.app.ui.theme.ScrapRedDeep
import com.lifementor.app.ui.theme.ScrapYellow
import com.lifementor.app.ui.theme.ScrapYellowDeep

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
            PaperChip("‹ Back", onClick = onBack)
            Spacer(Modifier.width(8.dp))
            MentorAvatar(mentor, 44.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(mentor.en, style = MaterialTheme.typography.headlineSmall)
                Text(
                    "${mentor.name} · ${mentor.domainLabel} ${domainXp} XP",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PaperChip(
                label = if (vm.handoffBusy.value) "Analyzing…" else "Link a mentor",
                onClick = { vm.requestHandoff() },
                enabled = canProposeHandoff && !vm.sending.value &&
                    !vm.handoffBusy.value && vm.handoffSuggestion.value == null,
            )
            Spacer(Modifier.width(6.dp))
            PaperChip("Clear", onClick = { vm.clearHistory() })
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
        ) {
            itemsIndexed(msgs) { i, m -> Bubble(m, i) }
            if (vm.sending.value) {
                item {
                    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("${mentor.name} is typing…",
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
                Text("Checking whether another mentor should take this next", style = MaterialTheme.typography.bodySmall)
            }
        }

        vm.handoffSuggestion.value?.let { suggestion ->
            val targetName = Mentors.byId(suggestion.targetMentorId)?.name ?: suggestion.targetMentorId
            ScrapCard(
                paperColor = ScrapPurple,
                shadowColor = Charcoal.copy(alpha = 0.25f),
                rotation = -0.3f,
                fillWidth = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text("Suggested handoff to $targetName", style = MaterialTheme.typography.titleMedium, color = InkBlack)
                Spacer(Modifier.height(4.dp))
                Text("Task: ${suggestion.taskTitle}", style = MaterialTheme.typography.bodyMedium, color = InkBlack)
                if (suggestion.rationale.isNotBlank()) {
                    Text("Why: ${suggestion.rationale}", style = MaterialTheme.typography.bodySmall, color = InkBlack)
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StickerButton("Confirm handoff", onClick = { vm.acceptHandoff() })
                    Spacer(Modifier.width(10.dp))
                    PaperChip("Cancel", onClick = { vm.dismissHandoff() })
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
            ScrapCard(
                paperColor = ScrapRed,
                shadowColor = Charcoal.copy(alpha = 0.25f),
                rotation = 0.3f,
                fillWidth = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    "🫂 If you are really struggling right now: call 12356 (24h mental-health hotline in mainland China) or local emergency services. " +
                        "You can also reach someone you trust or your campus counseling center. Heart can listen, but cannot replace professional help.",
                    style = MaterialTheme.typography.bodySmall,
                    color = PaperWhite,
                )
            }
        }

        PaperChip(
            label = if (vm.simulationMode.value) "🔮 Simulation on · tap to turn off" else "🔮 Simulation mode",
            onClick = { vm.toggleSimulationMode() },
            enabled = !vm.sending.value && !vm.handoffBusy.value,
            baseColor = if (vm.simulationMode.value) ScrapYellow else PaperWhite,
            modifier = Modifier.padding(start = 12.dp, top = 2.dp),
        )

        if (vm.simulationMode.value) {
            Text(
                "This compares three paths using your profile; turn it off to return to normal mentor chat.",
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
                        if (vm.simulationMode.value) "Describe the choices to compare…"
                        else "Talk to ${mentor.name}…",
                    )
                },
                modifier = Modifier.weight(1f),
                maxLines = 4,
                shape = RoundedCornerShape(12.dp),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = PaperWhite,
                    unfocusedContainerColor = PaperWhite,
                    focusedBorderColor = InkBlack,
                    unfocusedBorderColor = ScrapYellowDeep,
                    cursorColor = ScrapRed,
                    focusedTextColor = InkBlack,
                    unfocusedTextColor = InkBlack,
                ),
            )
            Spacer(Modifier.width(8.dp))
            if (vm.sending.value) {
                StickerButton(
                    "Stop",
                    onClick = { vm.cancelSending() },
                    baseColor = ScrapRedDeep,
                    shadowColor = ScrapYellowDeep,
                    contentColor = PaperWhite,
                )
            } else {
                StickerButton(
                    if (vm.simulationMode.value) "Simulate" else "Send",
                    onClick = { vm.send() },
                    enabled = !vm.handoffBusy.value,
                    arrow = true,
                )
            }
        }
    }
}

private val NotePalette = listOf(
    PaperWhite to InkSoft,
    ScrapYellow to InkBlack,
    ScrapPurple to InkBlack,
)

@Composable
private fun Bubble(m: Msg, index: Int) {
    val mine = m.role == "user"
    val (paper, ink) = if (mine) DeepSlate to PaperWhite
    else NotePalette[index % NotePalette.size]
    val rot = if (index % 2 == 0) -0.4f else 0.5f
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        if (mine) Spacer(Modifier.weight(1f))
        ScrapCard(
            paperColor = paper,
            shadowColor = Charcoal.copy(alpha = 0.22f),
            rotation = rot,
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            if (mine) Text(
                m.content,
                style = MaterialTheme.typography.bodyMedium,
                color = ink,
            )
            else MarkdownText(m.content, scrollable = false)
        }
        if (!mine) Spacer(Modifier.weight(1f))
    }
}
