package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = LimeSqueeze,
    secondary = AmethystGlow,
    tertiary = EmeraldSprint,
    background = MidnightAbyss,
    surface = DarkSurface,
    onPrimary = MidnightAbyss,
    onSecondary = GhostWhite,
    onTertiary = GhostWhite,
    onBackground = GhostWhite,
    onSurface = GhostWhite,
    error = SunsetOrange,
    onError = MidnightAbyss
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Force dark theme
  dynamicColor: Boolean = false, // Disable dynamic colors
  content: @Composable () -> Unit,
) {
  MaterialTheme(colorScheme = DarkColorScheme, typography = Typography, content = content)
}
