package com.lifementor.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Scrapbook theme from the art design: light mode mirrors the mockups; dark mode keeps the paper feel on a dark board */
private val ScrapbookLight = lightColorScheme(
    primary = ScrapRed,
    onPrimary = PaperWhite,
    primaryContainer = ScrapYellow,
    onPrimaryContainer = InkBlack,
    secondary = ScrapYellowDeep,
    onSecondary = InkBlack,
    secondaryContainer = ScrapPurple,
    onSecondaryContainer = InkBlack,
    tertiary = SkinTan,
    onTertiary = InkBlack,
    background = PaperBackground,
    onBackground = InkBlack,
    surface = PaperBackground,
    onSurface = InkBlack,
    surfaceVariant = Cardboard,
    onSurfaceVariant = InkSoft,
    surfaceContainer = PaperWhite,
    surfaceContainerHigh = PaperWhite,
    surfaceContainerHighest = PaperWhite,
    outline = InkGray,
    outlineVariant = Cardboard,
    error = ScrapRedDeep,
    onError = PaperWhite,
)

private val ScrapbookDark = darkColorScheme(
    primary = ScrapRed,
    onPrimary = PaperWhite,
    primaryContainer = ScrapYellowDeep,
    onPrimaryContainer = InkBlack,
    secondary = ScrapYellow,
    background = DeepSlate,
    onBackground = PaperWhite,
    surface = DeepSlate,
    onSurface = PaperWhite,
    surfaceVariant = Charcoal,
    onSurfaceVariant = Color(0xFFBDB6A9),
    surfaceContainer = Color(0xFF2A2A2E),
    surfaceContainerHigh = Color(0xFF2A2A2E),
    surfaceContainerHighest = Color(0xFF33333A),
    outline = InkGray,
    outlineVariant = Charcoal,
)

@Composable
fun LifeMentorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) ScrapbookDark else ScrapbookLight,
        typography = LifeMentorTypography,
        shapes = LifeMentorShapes,
        content = content,
    )
}
