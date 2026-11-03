package com.fitlens.ai.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Watch
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
        Text(stringResource(R.string.gw_home_note), style = MaterialTheme.typography.bodySmall)
        if (days.isEmpty()) Text(stringResource(R.string.gw_empty)) else {
            days.firstOrNull { it.sleepMinutes != null }?.let { day ->
                Text("${if (bg) "Сън" else "Sleep"}: ${day.sleepMinutes!! / 60}h ${day.sleepMinutes % 60}min · ${day.date}")
            }
            days.firstOrNull { it.hrvLastNightMs != null }?.let { day -> Text("HRV: ${day.hrvLastNightMs} ms · ${day.date}") }
            listOf("sleepScore", "stress", "batteryLast").forEach { key ->
                val definition = wellnessMetrics.getValue(key)
                val day = days.firstOrNull { key in it.wellness }
                val value = day?.wellness?.get(key)
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (bg) definition.bg else definition.en, style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f))
                        Text(value?.let { "${it.toInt()} / 100" } ?: stringResource(R.string.gw_unavailable),
                            style = MaterialTheme.typography.labelLarge)
                    }
                    if (value != null) {
                        LinearProgressIndicator(progress = { (value / 100).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                            color = if (key == "stress") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary)
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
