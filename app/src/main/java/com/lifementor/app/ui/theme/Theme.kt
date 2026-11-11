package com.lifementor.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F6D5F),
    secondary = Color(0xFF4C635C),
    tertiary = Color(0xFF7A5900),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6FD8C4),
    secondary = Color(0xFFB3CCC5),
    tertiary = Color(0xFFFFD699),
)

@Composable
fun LifeMentorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
