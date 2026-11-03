package com.luofelix.laninbox

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val lightColors = lightColorScheme()
private val darkColors = darkColorScheme()

@Composable
internal fun LanInboxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkColors else lightColors,
        content = content,
    )
}
