package com.luofelix.laninbox

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val lightColors = lightColorScheme(
    primary = Color(0xFF2457C5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7EEFF),
    onPrimaryContainer = Color(0xFF163B87),
    background = Color(0xFFF4F7FA),
    onBackground = Color(0xFF172739),
    surface = Color.White,
    onSurface = Color(0xFF172739),
    surfaceVariant = Color(0xFFEAF0F7),
    onSurfaceVariant = Color(0xFF4B5D72),
    outline = Color(0xFF76869A),
    outlineVariant = Color(0xFFD6E0EB),
)

private val darkColors = darkColorScheme(
    primary = Color(0xFFB5C7FF),
    onPrimary = Color(0xFF10264F),
    primaryContainer = Color(0xFF2D416C),
    onPrimaryContainer = Color(0xFFE5EDFF),
    background = Color(0xFF171E27),
    onBackground = Color(0xFFEDF3FA),
    surface = Color(0xFF212B37),
    onSurface = Color(0xFFEDF3FA),
    surfaceVariant = Color(0xFF303C4B),
    onSurfaceVariant = Color(0xFFBBC9D9),
    outline = Color(0xFF9AAABF),
    outlineVariant = Color(0xFF455365),
)

@Composable
internal fun LanInboxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkColors else lightColors,
        content = content,
    )
}
