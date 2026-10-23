package com.fitlens.ai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
fun DashboardScreen(state: DashboardState, onRefresh: () -> Unit) {
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
            .padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("FitLens AI", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
            Text("Твоят ден в движение", style = MaterialTheme.typography.titleLarge)
            Text("ДЕМО РЕЖИМ • Примерни данни", color = MaterialTheme.colorScheme.secondary)
            when (state) {
                DashboardState.Loading -> CircularProgressIndicator()
                DashboardState.Error -> Text("Данните не могат да бъдат заредени. Опитай отново.")
                is DashboardState.Ready -> {
                    val activity = state.dashboard.activity
                    val nutrition = state.dashboard.nutrition
                    MetricCard("Активност") {
                        Text("${activity.steps} крачки", style = MaterialTheme.typography.headlineMedium)
                        Text("${activity.activeMinutes} активни минути • ${activity.distanceKm} km")
                        LinearProgressIndicator(progress = { (activity.steps / 10000f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        Text("Демо цел: 10 000 крачки")
                    }
                    MetricCard("Хранене") {
                        Text("${nutrition.calories} / ${nutrition.calorieGoal} kcal", style = MaterialTheme.typography.headlineMedium)
                        LinearProgressIndicator(progress = { (nutrition.calories.toFloat() / nutrition.calorieGoal.coerceAtLeast(1)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        Text("Протеин ${nutrition.proteinGrams} g • Въглехидрати ${nutrition.carbsGrams} g • Мазнини ${nutrition.fatGrams} g")
                    }
                    MetricCard("Твоят фокус") {
                        Text("Още малко движение до демо целта. Разходка и балансирана вечеря са добра идея.")
                        Text("Примерен съвет, без AI анализ или медицински препоръки.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            MetricCard("Бъдещи интеграции") {
                Text("Garmin • Не е свързан")
                Text("MyFitnessPal • Не е свързан")
                Text("В тази версия няма вход, синхронизация или достъп до лични данни.", style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = onRefresh, enabled = state != DashboardState.Loading) { Text("Обнови демо данните") }
        }
    }
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
        DashboardScreen(DashboardState.Ready(Dashboard(ActivitySummary(7840, 42, 5.6), NutritionSummary(1640, 2200, 112, 185, 51))), {})
    }
}
