package com.lifementor.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifementor.app.data.Role
import com.lifementor.app.ui.components.LabeledField
import com.lifementor.app.ui.components.SectionTitle
import com.lifementor.app.ui.profile.ProfileViewModel

private data class Preset(val name: String, val url: String, val model: String, val hint: String)

private val presets = listOf(
    Preset("智谱 GLM（默认 · 免费）", "https://open.bigmodel.cn/api/paas/v4", "glm-4-flash", "open.bigmodel.cn 注册 → 用户中心 → API Keys"),
    Preset("SiliconFlow（新用户送额度）", "https://api.siliconflow.cn/v1", "deepseek-ai/DeepSeek-V3", "siliconflow.cn 注册领取免费额度"),
    Preset("DeepSeek（价格极低）", "https://api.deepseek.com/v1", "deepseek-chat", "platform.deepseek.com 少量充值即可长期使用"),
    Preset("本机 Ollama（完全免费，模拟器可用）", "http://10.0.2.2:11434/v1", "qwen2.5:7b", "电脑上运行 ollama serve；API Key 随便填，如 ollama"),
)

@Composable
fun SettingsScreen(
    onRedoOnboarding: () -> Unit,
    vm: SettingsViewModel = viewModel(),
    pvm: ProfileViewModel = viewModel(),
) {
    var keyVisible by remember { mutableStateOf(false) }
    var showWipe by remember { mutableStateOf(false) }
    val p = pvm.profile.value

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("设置", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))

        // ---------- 人生角色 ----------
        SectionTitle("人生角色")
        Text("选个头像", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(6.dp))
        vm.let { _ ->
            Row(Modifier.fillMaxWidth()) {
                listOf("🧭", "🌟", "🦉", "🐳", "🌱", "🔥", "🗻", "🦊").forEach { e ->
                    val sel = e == vm.roleAvatar.value
                    Text(
                        e,
                        fontSize = 22.sp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                if (sel) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                            .clickable { vm.roleAvatar.value = e }
                            .padding(10.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        LabeledField("称呼", vm.roleNickname.value) { vm.roleNickname.value = it }
        LabeledField("人生阶段", vm.roleStage.value) { vm.roleStage.value = it }
        LabeledField("愿景（会出现在主页和每次复盘）", vm.roleVision.value, singleLine = false, minLines = 2) {
            vm.roleVision.value = it
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { vm.saveRole() }) { Text("保存角色") }
            vm.roleSavedAt?.let {
                Spacer(Modifier.width(10.dp))
                Text("✓ $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }

        // ---------- 职业画像 ----------
        SectionTitle("职业画像（导师与规划的数据源）")
        LabeledField("当前状态（学校专业/公司岗位）", p.current) { v -> pvm.update { it.copy(current = v) } }
        LabeledField("学术背景", p.academic, singleLine = false, minLines = 2) { v -> pvm.update { it.copy(academic = v) } }
        LabeledField("经历（实习/项目/工作）", p.experience, singleLine = false, minLines = 3) { v -> pvm.update { it.copy(experience = v) } }
        LabeledField("技能", p.skills, singleLine = false, minLines = 2) { v -> pvm.update { it.copy(skills = v) } }
        LabeledField("兴趣", p.interests, singleLine = false, minLines = 2) { v -> pvm.update { it.copy(interests = v) } }
        LabeledField("性格特质", p.personality, singleLine = false, minLines = 2) { v -> pvm.update { it.copy(personality = v) } }
        LabeledField("价值观与偏好", p.values, singleLine = false, minLines = 2) { v -> pvm.update { it.copy(values = v) } }
        LabeledField("所在城市 / 目标城市", p.location) { v -> pvm.update { it.copy(location = v) } }
        LabeledField("其他补充", p.extra, singleLine = false, minLines = 2) { v -> pvm.update { it.copy(extra = v) } }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { pvm.save() }) { Text("保存画像") }
            pvm.savedAt?.let {
                Spacer(Modifier.width(10.dp))
                Text("✓ $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }

        // ---------- API ----------
        SectionTitle("大模型接口")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("获取免费 API Key（约 3 分钟）", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "1. 打开 open.bigmodel.cn 注册/登录　2. 用户中心 → API Keys 新建并复制\n" +
                        "3. 粘贴到下方并保存　4. 点「测试连接」看到 ✓ 即可使用\n" +
                        "默认模型 glm-4-flash 长期免费。",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        LabeledField("API 地址（Base URL）", vm.baseUrl.value) { vm.baseUrl.value = it }
        LabeledField("模型名", vm.model.value) { vm.model.value = it }
        LabeledField(
            label = "API Key",
            value = vm.apiKey.value,
            singleLine = false,
            minLines = 1,
            isError = vm.apiKey.value.isBlank(),
            supportingText = if (vm.apiKey.value.isBlank()) "必填。免费模型也需要 Key 才能调用" else null,
            visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailing = {
                TextButton(onClick = { keyVisible = !keyVisible }) {
                    Text(if (keyVisible) "隐藏" else "显示")
                }
            },
            onChange = { vm.apiKey.value = it },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { vm.save() }) { Text("保存接口配置") }
            Spacer(Modifier.width(10.dp))
            OutlinedButton(onClick = { vm.test() }, enabled = !vm.testing) {
                if (vm.testing) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("测试中…")
                } else {
                    Text("测试连接")
                }
            }
            vm.savedAt?.let {
                Spacer(Modifier.width(10.dp))
                Text("✓ $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        vm.testResult?.let {
            Spacer(Modifier.height(6.dp))
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = if (it.startsWith("✓")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
        Text("常用预设（点一下填入）", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        presets.forEach { pr ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .clickable { vm.applyPreset(pr.url, pr.model) }
                    .padding(10.dp),
            ) {
                Column {
                    Text(pr.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${pr.url}　·　${pr.model}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // ---------- 数据 ----------
        SectionTitle("数据与隐私")
        Text(
            "所有画像、目标、任务、聊天记录与规划都只存本机；卸载即清空。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("清空全部数据", fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer)
                    Text("角色、目标、XP、聊天、规划、复盘、画像将全部删除",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer)
                }
                OutlinedButton(onClick = { showWipe = true }) { Text("清空") }
            }
        }
        Spacer(Modifier.height(30.dp))
    }

    if (showWipe) {
        AlertDialog(
            onDismissRequest = { showWipe = false },
            title = { Text("确认清空？") },
            text = { Text("此操作不可恢复。API Key 配置也会一起清除（画像/目标/XP/聊天/文档全部删除）。") },
            confirmButton = {
                Button(onClick = {
                    vm.wipeAll()
                    showWipe = false
                    onRedoOnboarding()
                }) { Text("全部清空") }
            },
            dismissButton = { TextButton({ showWipe = false }) { Text("取消") } },
        )
    }
}
