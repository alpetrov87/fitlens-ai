package com.fitlens.ai.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.fitlens.ai.R
import com.fitlens.ai.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
internal fun GarminWellnessCard() {
    val context = LocalContext.current
    val bg = java.util.Locale.getDefault().language == "bg"
    var version by remember { mutableStateOf(0) }
    var days by remember { mutableStateOf<List<GarminRecoveryDay>>(emptyList()) }
    var expanded by remember { mutableStateOf(false) }
    DisposableEffect(context) {
        val prefs = context.getSharedPreferences("fitlens_imports", Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> version++ }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    LaunchedEffect(version) {
        days = withContext(Dispatchers.IO) {
            val today = LocalDate.now()
            SharedPreferencesImportedMetricsStore(context).recoveryBetween(today.minusDays(29).toString(), today.toString()).sortedByDescending { it.date }
        }
    }
    val sleep = days.firstOrNull { it.sleepMinutes != null }
    val hrv = days.firstOrNull { it.hrvLastNightMs != null }
    val battery = days.firstOrNull { "batteryLast" in it.wellness }
    val stress = days.firstOrNull { "stress" in it.wellness }
    val commonDate = listOfNotNull(sleep?.date, hrv?.date, battery?.date, stress?.date).distinct().singleOrNull()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.home_summary), style = MaterialTheme.typography.titleMedium)
            commonDate?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (days.isEmpty()) Text(stringResource(R.string.gw_empty)) else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val score = sleep?.wellness?.get("sleepScore")
                CompactWellnessTile(Modifier.weight(1f), Icons.Default.Bedtime,
                    if (bg) "Сън" else "Sleep",
                    sleep?.sleepMinutes?.let { if (bg) "${it / 60} ч ${it % 60} мин" else "${it / 60} h ${it % 60} min" } ?: "—",
                    score?.let { "${if (bg) "Оценка" else "Score"} ${it.toInt()} / 100" },
                    sleep?.date?.takeIf { it != commonDate }, score)
                CompactWellnessTile(Modifier.weight(1f), Icons.Default.MonitorHeart, "HRV",
                    hrv?.hrvLastNightMs?.let { "$it ms" } ?: "—",
                    hrv?.wellness?.get("hrvWeekly")?.let { "${if (bg) "Седмично" else "Weekly"} ${it.toInt()} ms" },
                    hrv?.date?.takeIf { it != commonDate }, null)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val level = battery?.wellness?.get("batteryLast")
                CompactWellnessTile(Modifier.weight(1f), Icons.Default.BatteryChargingFull, "Body Battery",
                    level?.let { "${it.toInt()} / 100" } ?: "—",
                    battery?.wellness?.get("batteryMax")?.let { "${if (bg) "Максимум" else "Maximum"} ${it.toInt()}" },
                    battery?.date?.takeIf { it != commonDate }, level)
                val value = stress?.wellness?.get("stress")
                CompactWellnessTile(Modifier.weight(1f), Icons.Default.Spa, if (bg) "Стрес" else "Stress",
                    value?.let { "${it.toInt()} / 100" } ?: "—", null,
                    stress?.date?.takeIf { it != commonDate }, value, stressColor = true)
            }
            TextButton(onClick = { expanded = !expanded }) { Text(stringResource(if (expanded) R.string.gw_hide else R.string.gw_history)) }
            if (expanded) {
                wellnessMetrics.filterKeys { it !in setOf("sleepScore", "stress", "batteryLast", "aerobic", "anaerobic", "trainingLoad", "batteryImpact") }.forEach { (key, definition) ->
                    val day = days.firstOrNull { key in it.wellness }
                    Text(day?.let { "${wellnessText(mapOf(key to it.wellness.getValue(key)), bg)} · ${it.date}" }
                        ?: "${if (bg) definition.bg else definition.en}: ${stringResource(R.string.gw_unavailable)}", style = MaterialTheme.typography.bodySmall)
                }
                days.forEach { day ->
                HorizontalDivider()
                Text(day.date, style = MaterialTheme.typography.titleSmall)
                Text(listOfNotNull(day.sleepMinutes?.let { "${if (bg) "Сън" else "Sleep"}: $it min" },
                    day.hrvLastNightMs?.let { "HRV: $it ms" }, wellnessText(day.wellness, bg).takeIf { it.isNotBlank() }).joinToString("\n"), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        }
    }
}

@Composable
private fun CompactWellnessTile(
    modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String,
    value: String, subtitle: String?, date: String?, progress: Double?, stressColor: Boolean = false,
) {
    val accent = if (stressColor) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        if (progress != null) LinearProgressIndicator(progress = { (progress / 100).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)),
            color = accent, trackColor = accent.copy(alpha = 0.14f), strokeCap = StrokeCap.Round)
        subtitle?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        date?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
