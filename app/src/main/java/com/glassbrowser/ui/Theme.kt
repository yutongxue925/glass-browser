package com.glassbrowser.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 主题：玻璃效果需要一个"有层次"的深色底才看得出来（纯黑上看不出模糊，纯白上文字发飘）。
 * 这里给一套深色 M3 配色，并让 Surface 保持透明 —— **玻璃容器一旦有实心 surface 色，模糊就废了**。
 */
private val GlassColors = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF0B1220),
    secondary = Color(0xFF9CC7FF),
    background = Color(0xFF0B0F14),
    onBackground = Color(0xFFE6EAF0),
    surface = Color(0xFF101418),
    onSurface = Color(0xFFE6EAF0),
    surfaceVariant = Color(0xFF1A2027),
    onSurfaceVariant = Color(0xFFB6C0CC),
)

@Composable
fun GlassBrowserTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GlassColors,
        content = content,
    )
}
