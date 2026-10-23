package com.fitlens.ai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fitlens.ai.data.*

@Composable
fun FitLensTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Color(0xFFB5F36C), background = Color(0xFF101614),
        surface = Color(0xFF1C2621), secondary = Color(0xFF93D5D4),
    ), content = content)
}

@Composable
fun DashboardScreen(
    state: DashboardState,
    importMessage: String?,
    onRefresh: () -> Unit,
    onImport: (CsvImportSource) -> Unit,
    onClearImports: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
            .padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("FitLens AI", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
            Text("Твоят ден в движение", style = MaterialTheme.typography.titleLarge)
            Text("Избирай CSV за локален импорт", color = MaterialTheme.colorScheme.secondary)
            when (state) {
                DashboardState.Loading -> CircularProgressIndicator()
                DashboardState.Error -> Text("Данните не могат да бъдат заредени. Опитай отново.")
                is DashboardState.Ready -> {
                    val activity = state.dashboard.activity
                    val nutrition = state.dashboard.nutrition
                    MetricCard("Активност · ${state.dashboard.activityOrigin.label()}${state.dashboard.activityDate?.let { " · $it" }.orEmpty()}") {
                        Text("${activity.steps} крачки", style = MaterialTheme.typography.headlineMedium)
                        Text("${activity.activeMinutes} активни минути • ${activity.distanceKm} km")
                        LinearProgressIndicator(progress = { (activity.steps / 10000f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        Text("Демо цел: 10 000 крачки")
                        if (state.dashboard.activityOrigin != MetricOrigin.DEMO) {
                            Text("Показатели, които липсват в CSV, са примерни.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    MetricCard("Хранене · ${state.dashboard.nutritionOrigin.label()}${state.dashboard.nutritionDate?.let { " · $it" }.orEmpty()}") {
                        Text("${nutrition.calories} / ${nutrition.calorieGoal} kcal", style = MaterialTheme.typography.headlineMedium)
                        LinearProgressIndicator(progress = { (nutrition.calories.toFloat() / nutrition.calorieGoal.coerceAtLeast(1)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        Text("Протеин ${nutrition.proteinGrams} g • Въглехидрати ${nutrition.carbsGrams} g • Мазнини ${nutrition.fatGrams} g")
                        if (state.dashboard.nutritionOrigin != MetricOrigin.DEMO) {
                            Text("Липсващите показатели и целта са примерни.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    MetricCard("Твоят фокус") {
                        Text("Още малко движение до демо целта. Разходка и балансирана вечеря са добра идея.")
                        Text("Примерен съвет, без AI анализ или медицински препоръки.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (state is DashboardState.Ready &&
                (state.dashboard.activityOrigin != MetricOrigin.DEMO || state.dashboard.nutritionOrigin != MetricOrigin.DEMO)
            ) {
                OutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Изтрий импортираните данни")
                }
            }
            MetricCard("Импорт на данни") {
                Text("Garmin Connect и MyFitnessPal CSV файлове се обработват на устройството.")
                Button(onClick = { onImport(CsvImportSource.GARMIN) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Избери Garmin CSV")
                }
                OutlinedButton(onClick = { onImport(CsvImportSource.MYFITNESSPAL) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Избери MyFitnessPal CSV")
                }
                if (importMessage != null) Text(importMessage, color = MaterialTheme.colorScheme.secondary)
                Text("Импортират се само измерванията от CSV. Файлът и акаунт пароли не се изпращат.", style = MaterialTheme.typography.bodySmall)
                Text("Garmin Personal API синхронизацията чака одобрен API достъп и backend.", style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = onRefresh, enabled = state != DashboardState.Loading) { Text("Обнови демо данните") }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Изтриване на импортираните данни") },
            text = { Text("Всички Garmin и MyFitnessPal обобщения ще бъдат изтрити от устройството.") },
            confirmButton = { TextButton(onClick = { confirmClear = false; onClearImports() }) { Text("Изтрий") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Отказ") } },
        )
    }
}

private fun MetricOrigin.label(): String = when (this) {
    MetricOrigin.DEMO -> "демо"
    MetricOrigin.GARMIN_CSV -> "Garmin CSV"
    MetricOrigin.MYFITNESSPAL_CSV -> "MyFitnessPal CSV"
}

@Composable
private fun MetricCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DashboardPreview() {
    FitLensTheme {
        DashboardScreen(
            DashboardState.Ready(Dashboard(ActivitySummary(7840, 42, 5.6), NutritionSummary(1640, 2200, 112, 185, 51))),
            importMessage = null,
            onRefresh = {},
            onImport = {},
            onClearImports = {},
        )
    }
}
