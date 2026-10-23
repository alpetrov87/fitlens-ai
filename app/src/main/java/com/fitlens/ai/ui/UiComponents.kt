package com.fitlens.ai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import com.fitlens.ai.R
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
internal fun TrailWelcomeCard() {
    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
        .background(Brush.linearGradient(listOf(Color(0xFF102F50), Color(0xFF07182E))))
        .padding(20.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("ТВОЯТ ПЪТ НАПРЕД", style = MaterialTheme.typography.labelSmall, color = Color(0xFF60E3D1))
            Text("Тренирай\nс посока.", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Text("Движение. Възстановяване.\nСледваща цел.", style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFBCD0E0))
        }
        Image(painterResource(R.drawable.fitlens_logo), contentDescription = null,
            modifier = Modifier.size(112.dp).clip(MaterialTheme.shapes.medium))
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
    "running" -> "Бягане"
    "cycling" -> "Колоездене"
    "swimming" -> "Плуване"
    "walking" -> "Ходене"
    "hiking" -> "Преход"
    "strength_training" -> "Силова тренировка"
    "yoga" -> "Йога"
    "other" -> "Друго"
    else -> sport.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

internal fun MetricOrigin.label(): String = when (this) {
    MetricOrigin.DEMO -> "Примерни данни"
    MetricOrigin.GARMIN_CSV -> "Garmin CSV"
    MetricOrigin.GARMIN_PERSONAL_API -> "Garmin"
    MetricOrigin.MYFITNESSPAL_CSV -> "MyFitnessPal CSV"
}
