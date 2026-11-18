package com.lifementor.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifementor.app.data.Role
import com.lifementor.app.ui.components.DisplayTitle
import com.lifementor.app.ui.components.PaperChip
import com.lifementor.app.ui.components.StickerButton
import com.lifementor.app.ui.theme.Cardboard
import com.lifementor.app.ui.theme.InkBlack
import com.lifementor.app.ui.theme.InkSoft
import com.lifementor.app.ui.theme.PaperWhite
import com.lifementor.app.ui.theme.ScrapBlue
import com.lifementor.app.ui.theme.ScrapRed
import com.lifementor.app.ui.theme.ScrapRedDeep
import com.lifementor.app.ui.theme.ScrapYellow
import com.lifementor.app.ui.components.LabeledField

@Composable
fun OnboardingScreen(onDone: () -> Unit, vm: OnboardingViewModel = viewModel()) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        DisplayTitle("LifeQuest")
        Text(
            "Your Life, Your Game · ${vm.step + 1}/4",
            style = MaterialTheme.typography.bodySmall,
            color = InkSoft,
        )
        Spacer(Modifier.height(20.dp))

        when (vm.step) {
            0 -> {
                Text("Who are you?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text("Pick an avatar", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                vm.avatars.chunked(6).forEach { row ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        row.forEach { e ->
                            val sel = e == vm.avatar.value
                            Box3(
                                selected = sel,
                                onClick = { vm.avatar.value = e },
                            ) {
                                Text(e, fontSize = 26.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                LabeledField("Nickname (can be changed later)", vm.nickname.value) { vm.nickname.value = it }
            }
            1 -> {
                Text("Which stage of life are you in?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Role.stages.forEach { s ->
                    Box3(selected = vm.stage.value == s, onClick = { vm.stage.value = s }) {
                        Text(s, fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            2 -> {
                Text("In the next 12 months, who do you want to become?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("One sentence is enough — it opens every weekly review.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                LabeledField("My vision", vm.vision.value, singleLine = false, minLines = 3) { vm.vision.value = it }
            }
            else -> {
                Text("What matters most right now?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Pick 2–3; they become your main-line goals automatically.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Role.focuses.forEach { (title, _, emoji) ->
                    Box3(selected = vm.focus.contains(title), onClick = {
                        if (vm.focus.contains(title)) vm.focus.remove(title)
                        else if (vm.focus.size < 4) vm.focus.add(title)
                    }) {
                        Text("$emoji  $title", fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (vm.step > 0) {
                PaperChip("‹ Back", onClick = { vm.step-- })
                Spacer(Modifier.width(10.dp))
            }
            Spacer(Modifier.weight(1f))
            StickerButton(
                label = if (vm.step < 3) "Next" else "Start my life game 🎮",
                onClick = {
                    if (vm.step < 3) vm.step++ else {
                        vm.finish()
                        onDone()
                    }
                },
                arrow = vm.step < 3,
                baseColor = if (vm.step < 3) ScrapYellow else ScrapRedDeep,
                shadowColor = if (vm.step < 3) ScrapBlue else ScrapYellow,
                contentColor = if (vm.step < 3) InkBlack else PaperWhite,
            )
        }
    }
}

@Composable
private fun Box3(selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    androidx.compose.foundation.layout.Box(
        Modifier.padding(end = 10.dp).rotate(if (selected) -0.6f else 0f),
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier
                .matchParentSize()
                .offset(3.dp, 3.dp)
                .background(if (selected) ScrapRed.copy(alpha = 0.4f) else Cardboard, shape),
        )
        androidx.compose.foundation.layout.Box(
            Modifier
                .clip(shape)
                .background(if (selected) ScrapYellow else PaperWhite)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) { content() }
    }
}
