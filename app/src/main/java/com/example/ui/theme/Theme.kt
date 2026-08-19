package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val VoxoraAtmosphericColorScheme = darkColorScheme(
    primary = LavenderPrimary,
    onPrimary = DeepVioletButton,
    primaryContainer = DeepVioletButton,
    onPrimaryContainer = LavenderPrimary,
    secondary = LavenderGlow,
    onSecondary = TextPrimary,
    background = AtmosphericBackground,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceGlass,
    onSurfaceVariant = TextSubtle,
    error = StatusRed,
    onError = TextPrimary
)

@Composable
fun VoxoraTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = VoxoraAtmosphericColorScheme,
        typography = Typography,
        content = content
    )
}


