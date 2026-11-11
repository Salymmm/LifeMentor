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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifementor.app.data.Role
import com.lifementor.app.ui.components.LabeledField

@Composable
fun OnboardingScreen(onDone: () -> Unit, vm: OnboardingViewModel = viewModel()) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            "LifeQuest",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            "Your Life, Your Game · ${vm.step + 1}/4",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        when (vm.step) {
            0 -> {
                Text("你是谁？", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text("选个头像", style = MaterialTheme.typography.titleSmall)
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
                LabeledField("称呼（之后可改）", vm.nickname.value) { vm.nickname.value = it }
            }
            1 -> {
                Text("你现在处于哪个人生阶段？", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Role.stages.forEach { s ->
                    Box3(selected = vm.stage.value == s, onClick = { vm.stage.value = s }) {
                        Text(s, fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            2 -> {
                Text("未来 12 个月，你希望活成什么样？", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("一句话愿景即可，它会出现在你每次复盘的开头。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                LabeledField("我的愿景", vm.vision.value, singleLine = false, minLines = 3) { vm.vision.value = it }
            }
            else -> {
                Text("哪几件事最重要？", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("选 2~3 项，会自动生成你的主线目标。",
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
        Row(Modifier.fillMaxWidth()) {
            if (vm.step > 0) {
                OutlinedButton(onClick = { vm.step-- }) { Text("上一步") }
                Spacer(Modifier.height(1.dp))
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = {
                if (vm.step < 3) vm.step++ else {
                    vm.finish()
                    onDone()
                }
            }) {
                Text(if (vm.step < 3) "下一步" else "开始我的人生游戏 🎮")
            }
        }
    }
}

@Composable
private fun Box3(selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    androidx.compose.foundation.layout.Box(
        Modifier
            .padding(end = 8.dp)
            .clip(shape)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHighest
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) { content() }
}
