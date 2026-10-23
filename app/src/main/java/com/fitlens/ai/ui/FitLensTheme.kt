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
        primary = Color(0xFF60E3D1), onPrimary = Color(0xFF003D3B),
        primaryContainer = Color(0xFF124448), onPrimaryContainer = Color(0xFFBCFAF0),
        secondary = Color(0xFF73CAFF), onSecondary = Color(0xFF00334F),
        secondaryContainer = Color(0xFF133E5C), onSecondaryContainer = Color(0xFFD3EEFF),
        tertiary = Color(0xFF8DEDDC), onTertiary = Color(0xFF003D34),
        background = Color(0xFF07182E), onBackground = Color(0xFFF0F7FF),
        surface = Color(0xFF102740), onSurface = Color(0xFFF0F7FF),
        surfaceVariant = Color(0xFF203C55), onSurfaceVariant = Color(0xFFBCD0E0),
        surfaceContainer = Color(0xFF122C46), surfaceContainerHigh = Color(0xFF1A354F),
        surfaceContainerHighest = Color(0xFF203C55), surfaceContainerLow = Color(0xFF0D2139),
        surfaceContainerLowest = Color(0xFF051326), surfaceTint = Color(0xFF60E3D1),
        outline = Color(0xFF8199AD), outlineVariant = Color(0xFF2B485F),
    ) else lightColorScheme(
        primary = Color(0xFF006B65), onPrimary = Color.White,
        primaryContainer = Color(0xFFCBF5EC), onPrimaryContainer = Color(0xFF003B36),
        secondary = Color(0xFF00658F), onSecondary = Color.White,
        secondaryContainer = Color(0xFFD3EEFF), onSecondaryContainer = Color(0xFF073951),
        tertiary = Color(0xFF176B5C), onTertiary = Color.White,
        background = Color(0xFFF0F6FB), onBackground = Color(0xFF0C2540),
        surface = Color.White, onSurface = Color(0xFF0C2540),
        surfaceVariant = Color(0xFFE0ECF4), onSurfaceVariant = Color(0xFF435E73),
        surfaceContainer = Color(0xFFE8F1F8), surfaceContainerHigh = Color(0xFFE0ECF4),
        surfaceContainerHighest = Color(0xFFD8E6F0), surfaceContainerLow = Color(0xFFF6FAFD),
        surfaceContainerLowest = Color.White, surfaceTint = Color(0xFF006B65),
        outline = Color(0xFF6D8598), outlineVariant = Color(0xFFCBDDE9),
    )
    MaterialTheme(colorScheme = colors, shapes = Shapes(
        small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp),
    ), content = content)
}
