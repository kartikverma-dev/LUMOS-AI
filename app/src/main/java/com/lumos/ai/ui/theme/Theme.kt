package com.lumos.ai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// LUMOS: light in name, dark by default, with a warm "wand-light" accent.
private val LumosColors = darkColorScheme(
    primary = Color(0xFFFFD166),
    onPrimary = Color(0xFF1A1A2E),
    secondary = Color(0xFF8ECAE6),
    background = Color(0xFF0D0D1A),
    surface = Color(0xFF16162A),
    surfaceVariant = Color(0xFF1F1F38),
    onBackground = Color(0xFFE8E8F0),
    onSurface = Color(0xFFE8E8F0),
    onSurfaceVariant = Color(0xFFB8B8CC)
)

@Composable
fun LumosTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LumosColors, content = content)
}
