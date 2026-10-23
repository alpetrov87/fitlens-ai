package com.fitlens.ai.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun FitLensTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) darkColorScheme(
        primary = Color(0xFFB5EE8A), onPrimary = Color(0xFF153A16),
        primaryContainer = Color(0xFF234C29), onPrimaryContainer = Color(0xFFD5F8C3),
        secondary = Color(0xFF8DD5D1), secondaryContainer = Color(0xFF204D50),
        onSecondaryContainer = Color(0xFFBCF0EE),
        background = Color(0xFF101714), onBackground = Color(0xFFE3EBE4),
        surface = Color(0xFF17211C), onSurface = Color(0xFFE3EBE4),
        surfaceVariant = Color(0xFF2C3B32), onSurfaceVariant = Color(0xFFBCCBBF),
    ) else lightColorScheme(
        primary = Color(0xFF326B35), onPrimary = Color.White,
        primaryContainer = Color(0xFFD5F2C3), onPrimaryContainer = Color(0xFF153A16),
        secondary = Color(0xFF28676A), secondaryContainer = Color(0xFFC8ECE9),
        onSecondaryContainer = Color(0xFF123F42),
        background = Color(0xFFF5F8F3), onBackground = Color(0xFF19271E),
        surface = Color(0xFFFFFFFF), onSurface = Color(0xFF19271E),
        surfaceVariant = Color(0xFFE5EDE2), onSurfaceVariant = Color(0xFF4B5D50),
    )
    MaterialTheme(colorScheme = colors, shapes = Shapes(
        small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp),
    ), content = content)
}
