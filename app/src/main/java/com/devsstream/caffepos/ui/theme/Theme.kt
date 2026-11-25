package com.devsstream.caffepos.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF4E342E),
    onPrimary = Color.White,
    secondary = Color(0xFF6D4C41),
    background = Color(0xFFFBF8F5),
    surface = Color.White
)

@Composable
fun CaffePOSTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content
    )
}
