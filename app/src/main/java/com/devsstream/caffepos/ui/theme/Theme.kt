package com.devsstream.caffepos.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val CafeLightColors = lightColorScheme(
    primary = Color(0xFF4A2C2A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEFE4DC),
    onPrimaryContainer = Color(0xFF3A201E),
    secondary = Color(0xFF795548),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3ECE6),
    onSecondaryContainer = Color(0xFF4E342E),
    tertiary = Color(0xFF2E7D32),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE8F5E9),
    onTertiaryContainer = Color(0xFF1B5E20),
    background = Color(0xFFFAF7F4),
    onBackground = Color(0xFF1F1A17),
    surface = Color.White,
    onSurface = Color(0xFF1F1A17),
    surfaceVariant = Color(0xFFF3ECE6),
    onSurfaceVariant = Color(0xFF5D534C),
    outline = Color(0xFFD7CCC8),
    error = Color(0xFFC62828),
    onError = Color.White,
    errorContainer = Color(0xFFFFEBEE),
    onErrorContainer = Color(0xFFB71C1C)
)

private val CafeTypography = Typography(
    titleLarge = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold
    ),
    titleSmall = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal
    ),
    labelLarge = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.2.sp
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium
    )
)

@Composable
fun CaffePOSTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CafeLightColors,
        typography = CafeTypography,
        content = content
    )
}
