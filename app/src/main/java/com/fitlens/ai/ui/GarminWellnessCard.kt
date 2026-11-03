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
        Text(stringResource(R.string.gw_note), style = MaterialTheme.typography.bodySmall)
        if (days.isEmpty()) Text(stringResource(R.string.gw_empty)) else {
            days.firstOrNull { it.sleepMinutes != null }?.let { day ->
                Text("${if (bg) "Сън" else "Sleep"}: ${day.sleepMinutes!! / 60}h ${day.sleepMinutes % 60}min · ${day.date}")
            }
            days.firstOrNull { it.hrvLastNightMs != null }?.let { day -> Text("HRV: ${day.hrvLastNightMs} ms · ${day.date}") }
            wellnessMetrics.filterKeys { it !in setOf("aerobic", "anaerobic", "trainingLoad", "batteryImpact") }.forEach { (key, _) ->
                days.firstOrNull { key in it.wellness }?.let { day ->
                    Text("${wellnessText(mapOf(key to day.wellness.getValue(key)), bg)} · ${day.date}", style = MaterialTheme.typography.bodyMedium)
                } ?: Text("${if (bg) wellnessMetrics.getValue(key).bg else wellnessMetrics.getValue(key).en}: ${stringResource(R.string.gw_unavailable)}", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { expanded = !expanded }) { Text(stringResource(if (expanded) R.string.gw_hide else R.string.gw_history)) }
            if (expanded) days.forEach { day ->
                HorizontalDivider()
                Text(day.date, style = MaterialTheme.typography.titleSmall)
                Text(listOfNotNull(day.sleepMinutes?.let { "${if (bg) "Сън" else "Sleep"}: $it min" },
                    day.hrvLastNightMs?.let { "HRV: $it ms" }, wellnessText(day.wellness, bg).takeIf { it.isNotBlank() }).joinToString("\n"), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
