package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CineDarkColorScheme = darkColorScheme(
    primary = CineAmber,
    onPrimary = CineBlack,
    primaryContainer = CineSurfaceVariant,
    onPrimaryContainer = CineAmberBright,
    secondary = CineCyan,
    onSecondary = CineBlack,
    secondaryContainer = CineSurfaceVariant,
    onSecondaryContainer = CineCyan,
    tertiary = CineRecordRed,
    onTertiary = Color.White,
    background = CineBlack,
    onBackground = CineTextPrimary,
    surface = CineSurface,
    onSurface = CineTextPrimary,
    surfaceVariant = CineSurfaceVariant,
    onSurfaceVariant = CineTextSecondary,
    outline = CineBorder,
    outlineVariant = CineSurfaceVariant
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false, // Forced cinematic theme for camera HUD
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CineDarkColorScheme,
        typography = Typography,
        content = content
    )
}
