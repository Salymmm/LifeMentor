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
import androidx.compose.foundation.layout.PaddingValues
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
import com.lifementor.app.ui.components.PaperChip
import com.lifementor.app.ui.components.ScrapCard
import com.lifementor.app.ui.components.StickerButton
import com.lifementor.app.ui.theme.Cardboard
import com.lifementor.app.ui.theme.Charcoal
import com.lifementor.app.ui.theme.InkBlack
import com.lifementor.app.ui.theme.InkSoft
import com.lifementor.app.ui.theme.PaperWhite
import com.lifementor.app.ui.theme.ScrapRed
import com.lifementor.app.ui.theme.ScrapYellow
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
        // Character card
        ScrapCard(
            paperColor = PaperWhite,
            shadowColor = Charcoal.copy(alpha = 0.18f),
            rotation = -0.2f,
            fillWidth = true,
            contentPadding = PaddingValues(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                        "${s.role.nickname}  Lv.${s.level}",
                        style = MaterialTheme.typography.titleMedium,
                        color = InkBlack,
                    )
                    Text(
                        s.role.stage.ifBlank { "Life player" },
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSoft,
                    )
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { s.into.toFloat() / s.need },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = ScrapRed,
                        trackColor = Cardboard,
                    )
                    Text(
                        "${s.into}/${s.need} XP  ·  ${s.totalXp} XP total",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSoft,
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🏆", fontSize = 20.sp)
                    Text(
                        "${s.achievementsDone}/${s.achievementsTotal}",
                        style = MaterialTheme.typography.labelSmall,
                        color = InkBlack,
                    )
                }
            }
        }

        if (s.role.vision.isNotBlank()) {
            Text(
                "\u201c${s.role.vision}\u201d",
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                color = InkSoft,
            )
        }

        // Today's actions
        ScrapCard(
            paperColor = ScrapYellow,
            shadowColor = Charcoal.copy(alpha = 0.18f),
            rotation = 0.3f,
            fillWidth = true,
            contentPadding = PaddingValues(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Today\u2019s Actions", style = MaterialTheme.typography.titleMedium, color = InkBlack)
                Spacer(Modifier.height(6.dp))
                if (s.today.isEmpty()) {
                    Text(
                        "Nothing to check off yet. Head to Goals and let AI decompose one for you.",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkBlack.copy(alpha = 0.72f),
                    )
                    Spacer(Modifier.height(8.dp))
                    PaperChip("✂ Go decompose a goal", onClick = goGoals)
                } else {
                    s.today.forEach { t ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(onCheckedChange = { vm.toggle(t) }, checked = false)
                            Text(t.title, fontSize = 14.sp, color = InkBlack)
                        }
                    }
                    Text(
                        "+10 XP per completed action",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkBlack.copy(alpha = 0.72f),
                    )
                }
        }

        // Five-dimension growth
        ScrapCard(
            paperColor = PaperWhite,
            shadowColor = Charcoal.copy(alpha = 0.18f),
            rotation = -0.3f,
            fillWidth = true,
            contentPadding = PaddingValues(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Five-Dimension Growth", style = MaterialTheme.typography.titleMedium, color = InkBlack)
                RadarChart(
                    labels = s.radar.map { it.first },
                    values = s.radar.map { it.second },
                    color = ScrapRed,
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                )
                Row(Modifier.fillMaxWidth()) {
                    s.radar.forEachIndexed { i, p ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(p.first, style = MaterialTheme.typography.labelSmall, color = InkBlack)
                            Text("${s.radarXp[i]}", style = MaterialTheme.typography.labelSmall,
                                color = ScrapRed)
                        }
                    }
                }
        }

        StickerButton("Talk to a mentor", onClick = goMentors, arrow = true)
        Spacer(Modifier.height(8.dp))
    }
}
