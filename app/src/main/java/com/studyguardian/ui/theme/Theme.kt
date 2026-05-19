package com.studyguardian.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = MatchaGreen,
    onPrimary = Color.White,
    secondary = PeachPink,
    onSecondary = TextPrimary,
    tertiary = LavenderGray,
    background = CreamWhite,
    onBackground = TextPrimary,
    surface = Oatmeal,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFFEDE4D8),
    onSurfaceVariant = TextSecondary,
)

@Composable
fun StudyGuardianTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // Sleep overlay uses its own palette; main app stays warm light.
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = GuardianTypography,
        content = content,
    )
}
