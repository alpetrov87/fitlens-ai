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
    InfoCard(stringResource(R.string.gw_title), Icons.Default.Watch) {
        if (days.isEmpty()) Text(stringResource(R.string.gw_empty)) else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val sleep = days.firstOrNull { it.sleepMinutes != null }
                val hrv = days.firstOrNull { it.hrvLastNightMs != null }
                listOf(
                    Triple(Icons.Default.Bedtime, if (bg) "Сън" else "Sleep", sleep),
                    Triple(Icons.Default.MonitorHeart, "HRV", hrv),
                ).forEachIndexed { index, (icon, label, day) ->
                    Surface(Modifier.weight(1f), shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Text(label, style = MaterialTheme.typography.labelLarge)
                            }
                            Text(if (index == 0) day?.sleepMinutes?.let {
                                if (bg) "${it / 60} ч ${it % 60} мин" else "${it / 60} h ${it % 60} min"
                            } ?: "—" else day?.hrvLastNightMs?.let { "$it ms" } ?: "—",
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            day?.let { Text(it.date, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
            listOf("sleepScore", "stress", "batteryLast").forEach { key ->
                val definition = wellnessMetrics.getValue(key)
                val day = days.firstOrNull { key in it.wellness }
                val value = day?.wellness?.get(key)
                val accent = if (key == "stress") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                val icon = when (key) {
                    "sleepScore" -> Icons.Default.Bedtime
                    "stress" -> Icons.Default.Spa
                    else -> Icons.Default.BatteryChargingFull
                }
                Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(icon, null, tint = accent, modifier = Modifier.size(22.dp))
                        Text(if (key == "batteryLast") "Body Battery" else if (bg) definition.bg else definition.en, style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f))
                        Text(value?.let { "${it.toInt()} / 100" } ?: stringResource(R.string.gw_unavailable),
                            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    }
                    if (value != null) {
                        LinearProgressIndicator(progress = { (value / 100).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(50)),
                            color = accent, trackColor = accent.copy(alpha = 0.14f), strokeCap = StrokeCap.Round)
                        Text(day?.date.orEmpty(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
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
