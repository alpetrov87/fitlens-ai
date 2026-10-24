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
        primary = Color(0xFFAACB98), onPrimary = Color(0xFF193B1D),
        primaryContainer = Color(0xFF2B4B30), onPrimaryContainer = Color(0xFFDEEDD2),
        secondary = Color(0xFFFFB17A), onSecondary = Color(0xFF522300),
        secondaryContainer = Color(0xFF703407), onSecondaryContainer = Color(0xFFFFDBC2),
        tertiary = Color(0xFFE5C16C), onTertiary = Color(0xFF3F2E00),
        tertiaryContainer = Color(0xFF594300), onTertiaryContainer = Color(0xFFFFE3A0),
        background = Color(0xFF111A14), onBackground = Color(0xFFF0EEDD),
        surface = Color(0xFF1A261D), onSurface = Color(0xFFF0EEDD),
        surfaceVariant = Color(0xFF344237), onSurfaceVariant = Color(0xFFC5CCB9),
        surfaceContainer = Color(0xFF1F2D23), surfaceContainerHigh = Color(0xFF2C3A2F),
        surfaceContainerHighest = Color(0xFF344237), surfaceContainerLow = Color(0xFF17221A),
        surfaceContainerLowest = Color(0xFF0D150F), surfaceTint = Color(0xFFAACB98),
        outline = Color(0xFF929E88), outlineVariant = Color(0xFF3D4B3F),
        inverseSurface = Color(0xFFE5E9DF), inverseOnSurface = Color(0xFF2A352C), inversePrimary = Color(0xFF355B35),
    ) else lightColorScheme(
        primary = Color(0xFF355B35), onPrimary = Color.White,
        primaryContainer = Color(0xFFE0E8D1), onPrimaryContainer = Color(0xFF203B24),
        secondary = Color(0xFFA94815), onSecondary = Color.White,
        secondaryContainer = Color(0xFFFFDBC2), onSecondaryContainer = Color(0xFF552000),
        tertiary = Color(0xFF795800), onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFFE3A0), onTertiaryContainer = Color(0xFF3F2E00),
        background = Color(0xFFFAF6E9), onBackground = Color(0xFF1B3023),
        surface = Color(0xFFFFFCF3), onSurface = Color(0xFF1B3023),
        surfaceVariant = Color(0xFFE8E9DA), onSurfaceVariant = Color(0xFF505E4F),
        surfaceContainer = Color(0xFFF1EEDF), surfaceContainerHigh = Color(0xFFEAE7D7),
        surfaceContainerHighest = Color(0xFFE3E2D1), surfaceContainerLow = Color(0xFFFCF9EF),
        surfaceContainerLowest = Color(0xFFFFFFFF), surfaceTint = Color(0xFF355B35),
        outline = Color(0xFF7B8775), outlineVariant = Color(0xFFDADCCB),
        inverseSurface = Color(0xFF24352A), inverseOnSurface = Color(0xFFF2F4E6), inversePrimary = Color(0xFFAACB98),
    )
    MaterialTheme(colorScheme = colors, shapes = Shapes(
        small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp),
    ), content = content)
}
