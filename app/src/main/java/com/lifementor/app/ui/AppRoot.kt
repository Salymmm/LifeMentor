package com.lifementor.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.lifementor.app.data.GameStore
import com.lifementor.app.ui.archive.ArchiveScreen
import com.lifementor.app.ui.goals.GoalsScreen
import com.lifementor.app.ui.home.HomeScreen
import com.lifementor.app.ui.mentors.MentorsTab
import com.lifementor.app.ui.onboarding.OnboardingScreen
import com.lifementor.app.ui.settings.SettingsScreen

private enum class HomeTab { HOME, GOALS, MENTORS, ARCHIVE, SETTINGS }

@Composable
fun AppRoot() {
    var onboarded by rememberSaveable { mutableStateOf(GameStore.isOnboarded()) }
    if (!onboarded) {
        OnboardingScreen(onDone = { onboarded = true })
        return
    }

    var tab by rememberSaveable { mutableStateOf(HomeTab.HOME) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                val items = listOf(
                    Triple(HomeTab.HOME, "🏠", "主页"),
                    Triple(HomeTab.GOALS, "🎯", "目标"),
                    Triple(HomeTab.MENTORS, "🎓", "导师"),
                    Triple(HomeTab.ARCHIVE, "📖", "档案"),
                    Triple(HomeTab.SETTINGS, "⚙️", "设置"),
                )
                items.forEach { (t, emoji, label) ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Text(emoji, fontSize = 22.sp) },
                        label = { Text(label) },
                    )
                }
            }
        }
    ) { insets ->
        Box(Modifier.fillMaxSize().padding(insets)) {
            when (tab) {
                HomeTab.HOME -> HomeScreen(goGoals = { tab = HomeTab.GOALS }, goMentors = { tab = HomeTab.MENTORS })
                HomeTab.GOALS -> GoalsScreen()
                HomeTab.MENTORS -> MentorsTab()
                HomeTab.ARCHIVE -> ArchiveScreen(goSettings = { tab = HomeTab.SETTINGS })
                HomeTab.SETTINGS -> SettingsScreen(onRedoOnboarding = { onboarded = false })
            }
        }
    }
}
