package com.fitlens.ai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.fitlens.ai.data.MetricOrigin

@Composable
internal fun InfoCard(title: String, icon: ImageVector? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (icon != null) Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}

@Composable
internal fun SportBadge(sport: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
        Icon(sportIcon(sport), contentDescription = sportLabel(sport), modifier = Modifier.padding(12.dp).size(24.dp),
            tint = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

internal fun sportIcon(sport: String): ImageVector = when {
    sport == "trail_running" -> Icons.Default.Terrain
    sport == "mountain_biking" -> Icons.Default.PedalBike
    sport.contains("cycl") || sport.contains("bik") -> Icons.Default.DirectionsBike
    sport.contains("swim") -> Icons.Default.Pool
    sport.contains("walk") || sport.contains("hik") -> Icons.Default.Hiking
    sport.contains("strength") || sport.contains("fitness") || sport.contains("cardio") -> Icons.Default.FitnessCenter
    sport.contains("yoga") || sport.contains("pilates") -> Icons.Default.SelfImprovement
    sport.contains("ski") || sport.contains("snow") -> Icons.Default.DownhillSkiing
    sport.contains("row") -> Icons.Default.Rowing
    sport.contains("tennis") -> Icons.Default.SportsTennis
    sport.contains("soccer") || sport.contains("football") -> Icons.Default.SportsSoccer
    sport.contains("run") -> Icons.Default.DirectionsRun
    else -> Icons.Default.Sports
}

internal fun sportLabel(sport: String): String = when (sport) {
    "running" -> com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_119)
    "trail_running" -> com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_120)
    "mountain_biking" -> "MTB"
    "cycling" -> com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_121)
    "swimming" -> com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_122)
    "walking" -> com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_123)
    "hiking" -> com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_124)
    "strength_training" -> com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_125)
    "yoga" -> com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_126)
    "other" -> com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_127)
    else -> sport.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

internal fun MetricOrigin.label(): String = when (this) {
    MetricOrigin.NONE -> com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_128)
    MetricOrigin.GARMIN_CSV -> "Garmin CSV"
    MetricOrigin.GARMIN_PERSONAL_API -> "Garmin"
    MetricOrigin.MYFITNESSPAL_CSV -> "MyFitnessPal CSV"
}
