package com.fitlens.ai.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fitlens.ai.data.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DashboardScreen(
    state: DashboardState,
    importMessage: String?,
    garminConnected: Boolean = false,
    garminBusy: Boolean = false,
    aiConnected: Boolean = false,
    historyBusy: Boolean = false,
    historyCount: Int = 0,
    analysisBusy: Boolean = false,
    analysis: String? = null,
    calendar: CalendarState = CalendarState(loading = false),
    onAddEvent: (EventDraft, () -> Unit) -> Unit = { _, _ -> },
    onImportEventLink: (String, String, (com.fitlens.ai.data.EventLinkImport) -> Unit) -> Unit = { _, _, _ -> },
    onDeleteEvent: (String) -> Unit = {},
    onRetryCalendar: () -> Unit = {},
    onConnectGarmin: (String) -> Unit = {},
    onSyncGarmin: () -> Unit = {},
    onDisconnectGarmin: () -> Unit = {},
    onSaveGeminiKey: (String) -> Unit = {},
    onDisconnectGemini: () -> Unit = {},
    onSyncHistory: () -> Unit = {},
    onAnalyzeTraining: (CalendarEvent?) -> Unit = {},
    onRefresh: () -> Unit,
    onImport: (CsvImportSource) -> Unit,
    onClearImports: () -> Unit,
) {
    var page by rememberSaveable { mutableStateOf("home") }
    val snackbar = remember { SnackbarHostState() }
    val pageState = rememberSaveableStateHolder()
    LaunchedEffect(importMessage) {
        importMessage?.let { snackbar.showSnackbar(it, duration = SnackbarDuration.Long) }
    }
    BackHandler(enabled = page != "home") { page = "home" }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                listOf(Triple("home", "Начало", Icons.Default.Home),
                    Triple("calendar", "Календар", Icons.Default.DateRange),
                    Triple("settings", "Настройки", Icons.Default.Settings)).forEach { (key, title, icon) ->
                    NavigationBarItem(selected = page == key, onClick = { page = key },
                        icon = { Icon(icon, contentDescription = null) }, label = { Text(title) })
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("FitLens AI", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f))
                if (garminConnected) Icon(Icons.Default.CheckCircle, contentDescription = "Garmin е свързан",
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            if (garminBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            pageState.SaveableStateProvider(page) {
                when (page) {
                    "calendar" -> CalendarScreen(calendar, onAddEvent, onImportEventLink, onDeleteEvent, onRetryCalendar)
                    "settings" -> SettingsScreen(garminConnected, garminBusy, onConnectGarmin, onSyncGarmin,
                        onDisconnectGarmin, onImport, onClearImports, aiConnected, historyBusy, historyCount,
                        onSaveGeminiKey, onDisconnectGemini, onSyncHistory)
                    else -> HomeScreen(state, garminConnected, garminBusy, calendar.events, onSyncGarmin,
                        onRefresh, { page = "settings" }, { page = "calendar" }, aiConnected, historyBusy,
                        analysisBusy, analysis, onAnalyzeTraining, { page = "settings" })
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    state: DashboardState, connected: Boolean, busy: Boolean, events: List<CalendarEvent>,
    onSync: () -> Unit, onRefresh: () -> Unit, onSettings: () -> Unit, onCalendar: () -> Unit,
    aiConnected: Boolean, historyBusy: Boolean, analysisBusy: Boolean, analysis: String?,
    onAnalyze: (CalendarEvent?) -> Unit, onAiSettings: () -> Unit,
) {
    var showAiConsent by rememberSaveable { mutableStateOf(false) }
    var eventMenu by remember { mutableStateOf(false) }
    var selectedEventId by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
        .padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Твоят ден, в баланс.", style = MaterialTheme.typography.headlineMedium)
        Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.forLanguageTag("bg"))),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (connected) {
            FilledTonalButton(onClick = onSync, enabled = !busy && !historyBusy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Sync, contentDescription = null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp)); Text(if (busy) "Изтегляне…" else "Изтегли данните за днес")
            }
        } else {
            FilledTonalButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Link, contentDescription = null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp)); Text("Свържи Garmin в настройките")
            }
        }
        when (state) {
            DashboardState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            DashboardState.Error -> InfoCard("Данните не могат да се заредят", Icons.Default.Info) {
                TextButton(onClick = onRefresh) { Text("Опитай отново") }
            }
            is DashboardState.Ready -> {
                val data = state.dashboard
                Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(
                    MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.secondaryContainer,
                )), MaterialTheme.shapes.large)) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.DirectionsWalk, contentDescription = null)
                            Text("ДНЕВНО ДВИЖЕНИЕ", style = MaterialTheme.typography.labelLarge)
                        }
                        Text("%,d".format(data.activity.steps), style = MaterialTheme.typography.displayMedium)
                        Text("крачки · ${data.activityOrigin.label()}${data.activityDate?.let { " · $it" }.orEmpty()}",
                            style = MaterialTheme.typography.bodyMedium)
                        LinearProgressIndicator(progress = { (data.activity.steps / 10000f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth())
                        Text("Примерна цел: 10 000 крачки", style = MaterialTheme.typography.labelMedium)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            StatValue("${data.activity.activeMinutes}", "активни минути")
                            StatValue("%.1f".format(data.activity.distanceKm), "километра")
                        }
                        if (data.activityOrigin != MetricOrigin.DEMO) Text("Липсващите показатели са примерни.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                InfoCard("Хранене", Icons.Default.Restaurant) {
                    Text("${data.nutrition.calories} kcal", style = MaterialTheme.typography.headlineMedium)
                    Text("${data.nutritionOrigin.label()}${data.nutritionDate?.let { " · $it" }.orEmpty()}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Цел ${data.nutrition.calorieGoal} kcal")
                    LinearProgressIndicator(progress = { (data.nutrition.calories.toFloat() / data.nutrition.calorieGoal.coerceAtLeast(1)).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatValue("${data.nutrition.proteinGrams} g", "протеин")
                        StatValue("${data.nutrition.carbsGrams} g", "въглехидрати")
                        StatValue("${data.nutrition.fatGrams} g", "мазнини")
                    }
                    Text("Липсващите показатели и целта са примерни.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (data.garminCaloriesConsumed != null && data.nutritionOrigin != MetricOrigin.GARMIN_PERSONAL_API) {
                        Text("Записан прием в Garmin: ${data.garminCaloriesConsumed} kcal")
                    }
                }
                Text("Активности днес", style = MaterialTheme.typography.titleLarge)
                if (data.garminActivities.isEmpty()) {
                    InfoCard("Денят е пред теб", Icons.Default.DirectionsRun) {
                        Text(if (connected) "Няма изтеглени активности за днес. Синхронизирай след следващата тренировка."
                            else "Свържи Garmin, за да виждаш тренировките си тук.")
                    }
                }
                data.garminActivities.forEach { activity -> ActivityCard(activity) }
            }
        }
        val next = events.firstOrNull { it.date >= LocalDate.now().toString() }
        InfoCard("Следващо събитие", Icons.Default.Event) {
            if (next == null) Text("Планирай следващата си спортна цел в календара.")
            else {
                Text(next.title, style = MaterialTheme.typography.titleLarge)
                Text("${next.date}${next.time.takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty()} · ${sportLabel(next.sport)}")
            }
            TextButton(onClick = onCalendar) { Text("Отвори календара") }
        }
        InfoCard("AI анализ на тренировките", Icons.Default.AutoAwesome) {
            Text("Прегледай натоварването през последните 30 дни и обсъди подготовката за състезание.")
            if (!aiConnected) {
                Text("Добави Gemini API ключ от Google AI Studio в настройките.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = onAiSettings, modifier = Modifier.fillMaxWidth()) { Text("Отвори настройките") }
            } else if (!connected) {
                Text("Свържи Garmin, за да изтеглиш тренировките си.")
                OutlinedButton(onClick = onAiSettings, modifier = Modifier.fillMaxWidth()) { Text("Настрой Garmin") }
            } else {
                OutlinedButton(onClick = { eventMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(events.firstOrNull { it.id == selectedEventId }?.let { "Състезание: ${it.title}" } ?: "Избери предстоящо събитие (по избор)")
                }
                DropdownMenu(expanded = eventMenu, onDismissRequest = { eventMenu = false }) {
                    DropdownMenuItem(text = { Text("Без избрано събитие") }, onClick = { selectedEventId = ""; eventMenu = false })
                    events.filter { it.date >= LocalDate.now().toString() }.forEach { event ->
                        DropdownMenuItem(text = { Text("${event.title} · ${event.date}") },
                            onClick = { selectedEventId = event.id; eventMenu = false })
                    }
                }
                Button(onClick = { showAiConsent = true }, enabled = !historyBusy && !analysisBusy,
                    modifier = Modifier.fillMaxWidth()) {
                    if (analysisBusy || historyBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp)); Text(when { historyBusy -> "Синхронизиране…"; analysisBusy -> "Подготвям анализа…"; else -> "Изтегли и анализирай 30 дни" })
                }
                Text("Ще бъдат изпратени тренировъчни обобщения към Google Gemini след потвърждение. Не е медицинска оценка.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                analysis?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
    if (showAiConsent) {
        AlertDialog(onDismissRequest = { showAiConsent = false },
            title = { Text("Изпращане на данни за AI анализ") },
            text = { Text("FitLens ще изтегли активностите, наличните обобщения за сън и HRV от Garmin за последните 30 дни и ще изпрати тези показатели към Google Gemini. Ако избереш събитие, ще изпрати и името, датата, спорта и мястото му. Безплатният план може да използва данните според условията на Google. Garmin ключът не се изпраща към Google. Продължаваш ли?") },
            confirmButton = { TextButton(onClick = {
                showAiConsent = false
                val event = events.firstOrNull { it.id == selectedEventId && it.date >= LocalDate.now().toString() }
                onAnalyze(event)
            }) { Text("Продължи") } },
            dismissButton = { TextButton(onClick = { showAiConsent = false }) { Text("Отказ") } })
    }
}

@Composable
private fun StatValue(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ActivityCard(activity: GarminActivity) {
    var expanded by rememberSaveable(activity.activityId) { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SportBadge(activity.activityTypeKey)
                Column(Modifier.weight(1f)) {
                    Text(activity.name, style = MaterialTheme.typography.titleMedium)
                    Text("${sportLabel(activity.activityTypeKey)} · ${activity.startTimeLocal.substringAfter(' ', activity.startTimeLocal)}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(buildList {
                activity.durationSeconds?.let { add("${(it / 60).toInt()} мин") }
                activity.distanceMeters?.let { add("%.2f km".format(it / 1000)) }
                activity.calories?.let { add("$it kcal") }
            }.joinToString("  •  "))
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Скрий подробностите" else "Подробности") }
            if (expanded) {
                activity.steps?.let { Text("Крачки: $it") }
                activity.averageHeartRate?.let { Text("Среден пулс: $it bpm") }
                activity.maxHeartRate?.let { Text("Максимален пулс: $it bpm") }
                activity.elevationGainMeters?.let { Text("Изкачване: ${it.toInt()} m") }
                Text("Garmin ID: ${activity.activityId}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Preview(name = "Начало · светла тема", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Начало · тъмна тема", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DashboardPreview() {
    FitLensTheme { DashboardScreen(DashboardState.Ready(Dashboard(ActivitySummary(7840, 42, 5.6),
        NutritionSummary(1640, 2200, 112, 185, 51))), null, onRefresh = {}, onImport = {}, onClearImports = {}) }
}
