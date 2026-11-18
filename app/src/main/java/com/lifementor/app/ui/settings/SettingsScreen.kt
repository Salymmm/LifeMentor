package com.lifementor.app.ui.settings

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifementor.app.ui.components.DisplayTitle
import com.lifementor.app.ui.components.PaperChip
import com.lifementor.app.ui.components.ScrapCard
import com.lifementor.app.ui.components.StickerButton
import com.lifementor.app.ui.components.LabeledField
import com.lifementor.app.ui.components.SectionTitle
import com.lifementor.app.ui.theme.Charcoal
import com.lifementor.app.ui.theme.InkBlack
import com.lifementor.app.ui.theme.InkSoft
import com.lifementor.app.ui.theme.PaperWhite
import com.lifementor.app.ui.theme.ScrapRed
import com.lifementor.app.ui.theme.ScrapRedDeep
import com.lifementor.app.ui.theme.ScrapYellow
import com.lifementor.app.notify.ReminderSettings
import com.lifementor.app.ui.profile.ProfileViewModel

private data class Preset(val name: String, val url: String, val model: String, val hint: String)

private val presets = listOf(
    Preset("Zhipu GLM (default · free)", "https://open.bigmodel.cn/api/paas/v4", "glm-4-flash", "Sign up at open.bigmodel.cn → User Center → API Keys"),
    Preset("SiliconFlow (free credits for new users)", "https://api.siliconflow.cn/v1", "deepseek-ai/DeepSeek-V3", "Sign up at siliconflow.cn to claim free credits"),
    Preset("DeepSeek (very low price)", "https://api.deepseek.com/v1", "deepseek-chat", "A small top-up at platform.deepseek.com lasts a long while"),
    Preset("Local Ollama (free, works in emulator)", "http://10.0.2.2:11434/v1", "qwen2.5:7b", "Run ollama serve on your computer; any API key works, e.g. ollama"),
)

private enum class SettingsPage(val title: String, val summary: String, val emoji: String) {
    PERSONAL("Personal Info", "Nickname, avatar, life stage & vision", "👤"),
    PROFILE("Career Profile", "Background, experience, skills & preferences", "📋"),
    REMINDERS("Reminders & Notifications", "Daily reminder time, notification permission & test", "🔔"),
    API("LLM Endpoint", "Base URL, model, API key & connection test", "🔌"),
    DATA("Data & Privacy", "Local storage & erase data", "🗂️"),
}

@Composable
fun SettingsScreen(
    onRedoOnboarding: () -> Unit,
    vm: SettingsViewModel = viewModel(),
    pvm: ProfileViewModel = viewModel(),
) {
    var page by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    val indexScroll = rememberScrollState()
    val detailScroll = key(page) { rememberScrollState() }
    BackHandler(enabled = page != null) { page = null }
    Column(Modifier.fillMaxSize()
        .verticalScroll(if (page == null) indexScroll else detailScroll).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (page != null) {
                PaperChip("‹ Settings", onClick = { page = null })
                Spacer(Modifier.width(12.dp))
            }
            DisplayTitle(page?.title ?: "Settings", modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        when (page) {
            null -> SettingsPage.entries.forEach { entry ->
                ScrapCard(
                    paperColor = PaperWhite,
                    shadowColor = Charcoal.copy(alpha = 0.18f),
                    fillWidth = true,
                    modifier = Modifier.fillMaxWidth().clickable { page = entry },
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(entry.emoji, fontSize = 24.sp, modifier = Modifier.clearAndSetSemantics {})
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(entry.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(entry.summary, style = MaterialTheme.typography.bodySmall,
                                color = InkSoft)
                        }
                        Text("›", fontSize = 24.sp, color = ScrapRed,
                            modifier = Modifier.clearAndSetSemantics {})
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            SettingsPage.PERSONAL -> PersonalSettings(vm)
            SettingsPage.PROFILE -> CareerProfileSettings(pvm)
            SettingsPage.REMINDERS -> ReminderSettings()
            SettingsPage.API -> ApiSettings(vm)
            SettingsPage.DATA -> DataSettings(vm, onRedoOnboarding)
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun PersonalSettings(vm: SettingsViewModel) {
    Text("Pick an avatar", style = MaterialTheme.typography.labelMedium)
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
                            if (sel) ScrapYellow else PaperWhite
                        )
                        .clickable { vm.roleAvatar.value = e }
                        .padding(10.dp),
                )
                Spacer(Modifier.width(4.dp))
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    LabeledField("Nickname", vm.roleNickname.value) { vm.roleNickname.value = it }
    LabeledField("Life stage", vm.roleStage.value) { vm.roleStage.value = it }
    LabeledField("Vision (shown on Home and in every review)", vm.roleVision.value, singleLine = false, minLines = 2) {
        vm.roleVision.value = it
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        StickerButton("✓ Save role", onClick = { vm.saveRole() })
        vm.roleSavedAt?.let {
            Spacer(Modifier.width(10.dp))
            Text("✓ $it", style = MaterialTheme.typography.bodySmall, color = ScrapRed)
        }
    }
}

@Composable
private fun CareerProfileSettings(pvm: ProfileViewModel) {
    val p = pvm.profile.value
    SectionTitle("Shared with mentors & planning")
    LabeledField("Current status (school & major / company & role)", p.current) { v -> pvm.update { it.copy(current = v) } }
    LabeledField("Academic background", p.academic, singleLine = false, minLines = 2) { v -> pvm.update { it.copy(academic = v) } }
    LabeledField("Experience (internships / projects / jobs)", p.experience, singleLine = false, minLines = 3) { v -> pvm.update { it.copy(experience = v) } }
    LabeledField("Skills", p.skills, singleLine = false, minLines = 2) { v -> pvm.update { it.copy(skills = v) } }
    LabeledField("Interests", p.interests, singleLine = false, minLines = 2) { v -> pvm.update { it.copy(interests = v) } }
    LabeledField("Personality", p.personality, singleLine = false, minLines = 2) { v -> pvm.update { it.copy(personality = v) } }
    LabeledField("Values & preferences", p.values, singleLine = false, minLines = 2) { v -> pvm.update { it.copy(values = v) } }
    LabeledField("Current city / target city", p.location) { v -> pvm.update { it.copy(location = v) } }
    LabeledField("Anything else", p.extra, singleLine = false, minLines = 2) { v -> pvm.update { it.copy(extra = v) } }
    Row(verticalAlignment = Alignment.CenterVertically) {
        StickerButton("✓ Save profile", onClick = { pvm.save() })
        pvm.savedAt?.let {
            Spacer(Modifier.width(10.dp))
            Text("✓ $it", style = MaterialTheme.typography.bodySmall, color = ScrapRed)
        }
    }
}

@Composable
private fun ApiSettings(vm: SettingsViewModel) {
    var keyVisible by remember { mutableStateOf(false) }
    ScrapCard(
        paperColor = ScrapYellow,
        shadowColor = Charcoal.copy(alpha = 0.18f),
        rotation = 0.3f,
        fillWidth = true,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("Get a free API key (about 3 minutes)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "1. Open open.bigmodel.cn and sign up / log in\n" +
                    "2. User Center → API Keys: create & copy\n" +
                    "3. Paste it below and save\n" +
                    "4. Tap \"Test connection\" and look for a ✓\n" +
                    "The default model glm-4-flash is free to keep using.",
                style = MaterialTheme.typography.bodySmall,
                color = InkBlack,
            )
        }
    }
    Spacer(Modifier.height(10.dp))
    LabeledField("Base URL", vm.baseUrl.value) { vm.baseUrl.value = it }
    LabeledField("Model name", vm.model.value) { vm.model.value = it }
    LabeledField(
        label = "API Key",
        value = vm.apiKey.value,
        singleLine = false,
        minLines = 1,
        isError = vm.apiKey.value.isBlank(),
        supportingText = if (vm.apiKey.value.isBlank()) "Required. Even free models need a key to call" else null,
        visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailing = {
            TextButton(onClick = { keyVisible = !keyVisible }) {
                Text(if (keyVisible) "Hide" else "Show")
            }
        },
        onChange = { vm.apiKey.value = it },
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        StickerButton("✓ Save endpoint", onClick = { vm.save() })
        Spacer(Modifier.width(10.dp))
        if (vm.testing) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = ScrapRed)
                Spacer(Modifier.width(6.dp))
                Text("Testing…", style = MaterialTheme.typography.bodySmall)
            }
        } else {
            PaperChip("⚡ Test connection", onClick = { vm.test() })
        }
        vm.savedAt?.let {
            Spacer(Modifier.width(10.dp))
            Text("✓ $it", style = MaterialTheme.typography.bodySmall, color = ScrapRed)
        }
    }
    vm.testResult?.let {
        Spacer(Modifier.height(6.dp))
        Text(
            it,
            style = MaterialTheme.typography.bodySmall,
            color = if (it.startsWith("✓")) ScrapRed else MaterialTheme.colorScheme.error,
        )
    }
    Text("Handy presets (tap to fill in)", style = MaterialTheme.typography.labelMedium)
    Spacer(Modifier.height(4.dp))
    presets.forEach { pr ->
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(PaperWhite)
                .clickable { vm.applyPreset(pr.url, pr.model) }
                .padding(10.dp),
        ) {
            Column {
                Text(pr.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${pr.url}  ·  ${pr.model}",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSoft,
                )
            }
        }
    }
}

@Composable
private fun DataSettings(vm: SettingsViewModel, onRedoOnboarding: () -> Unit) {
    var showWipe by remember { mutableStateOf(false) }
    Text(
        "Profiles, goals, tasks, chats and plans are stored on this device only; uninstalling wipes everything.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    ScrapCard(
        paperColor = ScrapRed,
        shadowColor = Charcoal.copy(alpha = 0.22f),
        rotation = -0.3f,
        fillWidth = true,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Erase all data", fontWeight = FontWeight.Bold,
                    color = PaperWhite)
                Text("Role, goals, XP, chats, plans, reviews and profile will all be deleted",
                    style = MaterialTheme.typography.bodySmall,
                    color = PaperWhite.copy(alpha = 0.9f))
            }
            StickerButton("Erase", onClick = { showWipe = true },
                baseColor = ScrapRedDeep, shadowColor = ScrapYellow, contentColor = PaperWhite)
        }
    }

    if (showWipe) {
        AlertDialog(
            onDismissRequest = { showWipe = false },
            title = { Text("Erase everything?") },
            text = { Text("This cannot be undone. The API key config goes too (profile / goals / XP / chats / docs all deleted).") },
            confirmButton = {
                StickerButton(
                    "Erase all",
                    onClick = {
                        vm.wipeAll()
                        showWipe = false
                        onRedoOnboarding()
                    },
                    baseColor = ScrapRedDeep,
                    shadowColor = ScrapYellow,
                    contentColor = PaperWhite,
                )
            },
            dismissButton = { PaperChip("Cancel", onClick = { showWipe = false }) },
        )
    }
}
