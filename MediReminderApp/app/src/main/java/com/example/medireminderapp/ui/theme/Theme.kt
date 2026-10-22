package com.example.medireminderapp

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightGreenScheme = lightColorScheme(
    primary = Color(0xFF2E7D32),      // เขียวเข้ม
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA5D6A7),
    onPrimaryContainer = Color(0xFF073B0A),

    secondary = Color(0xFF66BB6A),
    onSecondary = Color.White,
    tertiary = Color(0xFF26A69A),

    background = Color(0xFFF6FFF6),
    onBackground = Color(0xFF1C1C1C),
    surface = Color(0xFFF1FFF1),
    onSurface = Color(0xFF1C1C1C),
)

private val DarkGreenScheme = darkColorScheme(
    primary = Color(0xFF81C784),
    onPrimary = Color(0xFF06320A),
    primaryContainer = Color(0xFF1B5E20),
    onPrimaryContainer = Color(0xFFA5D6A7),

    secondary = Color(0xFF81C784),
    onSecondary = Color(0xFF0B2F0F),
    tertiary = Color(0xFF4DB6AC),

    background = Color(0xFF0F140F),
    onBackground = Color(0xFFE6FFE6),
    surface = Color(0xFF121A12),
    onSurface = Color(0xFFE6FFE6),
)

@Composable
fun MediReminderTheme(
    darkTheme: Boolean = false, // ปรับตามระบบได้หากต้องการ
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkGreenScheme else LightGreenScheme
    MaterialTheme(
        colorScheme = colors,
        typography = androidx.compose.material3.Typography(),
        content = content
    )
}
