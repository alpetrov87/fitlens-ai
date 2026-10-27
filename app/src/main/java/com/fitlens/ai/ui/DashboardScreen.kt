package com.fitlens.ai.ui

import androidx.lifecycle.repeatOnLifecycle

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
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
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import com.fitlens.ai.R
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
    historyBusy: Boolean = false,
    historyCount: Int = 0,
    reportBusy: Boolean = false,
    shareReport: String? = null,
    calendar: CalendarState = CalendarState(loading = false),
    catalogue: RaceCatalogueState = RaceCatalogueState(),
    onLoadRaceCalendar: (String) -> Unit = {},
    onAddEvent: (EventDraft, () -> Unit) -> Unit = { _, _ -> },
    onImportEventLink: (String, String, (com.fitlens.ai.data.EventLinkImport) -> Unit) -> Unit = { _, _, _ -> },
    onDeleteEvent: (String) -> Unit = {},
    onRetryCalendar: () -> Unit = {},
    onConnectGarmin: (String) -> Unit = {},
    onSyncGarmin: () -> Unit = {},
    onDisconnectGarmin: () -> Unit = {},
    onSyncHistory: () -> Unit = {},
    onPrepareChatGptReport: (CalendarEvent?) -> Unit = {},
    onOpenChatGpt: (String) -> Unit = {},
    onDismissShareReport: () -> Unit = {},
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
                listOf(Triple("home", com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_092), Icons.Default.Home),
                    Triple("calendar", com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.ui_034), Icons.Default.DateRange),
                    Triple("runner", com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.ui_038), Icons.Default.Insights),
                    Triple("mfp", "MFP", Icons.Default.Restaurant),
                    Triple("settings", com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.ui_000), Icons.Default.Settings)).forEach { (key, title, icon) ->
                    NavigationBarItem(selected = page == key, onClick = { page = key },
                        icon = { Icon(icon, contentDescription = null) }, label = { Text(title) })
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Image(painterResource(R.drawable.fitlens_logo), contentDescription = null,
                    modifier = Modifier.size(40.dp).clip(MaterialTheme.shapes.small))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    val wordmarkInk = MaterialTheme.colorScheme.onBackground
                    val wordmarkAccent = MaterialTheme.colorScheme.secondary
                    Text(androidx.compose.ui.text.buildAnnotatedString {
                        withStyle(androidx.compose.ui.text.SpanStyle(color = wordmarkInk)) { append("b") }
                        withStyle(androidx.compose.ui.text.SpanStyle(color = wordmarkAccent)) { append("AI") }
                        withStyle(androidx.compose.ui.text.SpanStyle(color = wordmarkInk)) { append("r") }
                    }, style = MaterialTheme.typography.headlineMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold)
                    Text(androidx.compose.ui.res.stringResource(R.string.brand_legacy),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.weight(1f))
                if (garminConnected) Icon(Icons.Default.CheckCircle, contentDescription = com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_093),
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            if (garminBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            pageState.SaveableStateProvider(page) {
                when (page) {
                    "mfp" -> MfpScreen()
                    "runner" -> RunnerProfilesScreen()
                    "calendar" -> CalendarScreen(calendar, onAddEvent, onImportEventLink, onDeleteEvent, onRetryCalendar, catalogue, onLoadRaceCalendar)
                    "settings" -> SettingsScreen(garminConnected, garminBusy, onConnectGarmin, onSyncGarmin,
                        onDisconnectGarmin, onImport, onClearImports, historyBusy, historyCount, onSyncHistory)
                    else -> HomeScreen(state, garminConnected, garminBusy, calendar.events, onSyncGarmin,
                        onRefresh, { page = "settings" }, { page = "calendar" }, historyBusy,
                        reportBusy, shareReport, onPrepareChatGptReport, onOpenChatGpt, onDismissShareReport)
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    state: DashboardState, connected: Boolean, busy: Boolean, events: List<CalendarEvent>,
    onSync: () -> Unit, onRefresh: () -> Unit, onSettings: () -> Unit, onCalendar: () -> Unit,
    historyBusy: Boolean, reportBusy: Boolean, shareReport: String?,
    onPrepareReport: (CalendarEvent?) -> Unit, onOpenChatGpt: (String) -> Unit, onDismissShareReport: () -> Unit,
) {
    var eventMenu by remember { mutableStateOf(false) }
    var selectedEventId by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
        .padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_015), style = MaterialTheme.typography.headlineMedium)
        Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (connected) {
            FilledTonalButton(onClick = onSync, enabled = !busy && !historyBusy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Sync, contentDescription = null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp)); Text(if (busy) com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_094) else com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.ui_002))
            }
        } else {
            FilledTonalButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Link, contentDescription = null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp)); Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_016))
            }
        }
        when (state) {
            DashboardState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            DashboardState.Error -> InfoCard(com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_095), Icons.Default.Info) {
                TextButton(onClick = onRefresh) { Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_017)) }
            }
            is DashboardState.Ready -> {
                val data = state.dashboard
                Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(
                    MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surface,
                )), MaterialTheme.shapes.large)) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.DirectionsWalk, contentDescription = null)
                            Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_018), style = MaterialTheme.typography.labelLarge)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            Surface(shape = androidx.compose.foundation.shape.CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer) {
                                Icon(Icons.Default.DirectionsWalk, null, tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(24.dp).size(36.dp))
                            }
                            Text("%,d".format(data.activity.steps), style = MaterialTheme.typography.displayMedium)
                        }
                        Text("${com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.steps)} · ${data.activityOrigin.label()}${data.activityDate?.let { " · $it" }.orEmpty()}",
                            style = MaterialTheme.typography.bodyMedium)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            StatValue("${data.activity.activeMinutes}", com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_096))
                            StatValue("%.1f".format(data.activity.distanceKm), com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_097))
                        }
                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            val sleep = data.recovery?.sleepMinutes
                            StatValue(sleep?.let { com.fitlens.ai.data.LocalizedText.format(com.fitlens.ai.R.string.dynamic_020, it / 60, it % 60) } ?: "—", com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_098))
                            StatValue(data.recovery?.hrvLastNightMs?.let { "$it ms" } ?: "—", "HRV")
                        }
                        if (data.recovery == null) Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_019),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    }
                }
                InfoCard(com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_099), Icons.Default.Restaurant) {
                    if (data.garminNutrition != null) {
                        if (data.garminNutritionStatus == "unavailable") Text(androidx.compose.ui.res.stringResource(R.string.nutrition_cached), style = MaterialTheme.typography.bodySmall)
                        GarminNutritionContent(data.garminNutrition)
                    } else {
                        if (data.garminNutritionStatus != null) Text(androidx.compose.ui.res.stringResource(
                            if (data.garminNutritionStatus == "unavailable") R.string.nutrition_unavailable else R.string.nutrition_empty),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${data.nutrition.calories} kcal", style = MaterialTheme.typography.headlineMedium)
                        Text("${data.nutritionOrigin.label()}${data.nutritionDate?.let { " · $it" }.orEmpty()}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(com.fitlens.ai.data.LocalizedText.format(com.fitlens.ai.R.string.dynamic_010, data.nutrition.calorieGoal))
                        LinearProgressIndicator(progress = { (data.nutrition.calories.toFloat() / data.nutrition.calorieGoal.coerceAtLeast(1)).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth())
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            StatValue("${data.nutrition.proteinGrams} g", com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_100))
                            StatValue("${data.nutrition.carbsGrams} g", com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_101))
                            StatValue("${data.nutrition.fatGrams} g", com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_102))
                        }
                        if (data.garminCaloriesConsumed != null && data.nutritionOrigin != MetricOrigin.GARMIN_PERSONAL_API) {
                            Text(com.fitlens.ai.data.LocalizedText.format(com.fitlens.ai.R.string.dynamic_011, data.garminCaloriesConsumed))
                        }
                    }
                }
                Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_020), style = MaterialTheme.typography.titleLarge)
                if (data.garminActivities.isEmpty()) {
                    InfoCard(com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_103), Icons.Default.DirectionsRun) {
                        Text(if (connected) com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_104)
                            else com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_105))
                    }
                }
                data.garminActivities.forEach { activity -> ActivityCard(activity) }
            }
        }
        val next = events.firstOrNull { it.date >= LocalDate.now().toString() }
        InfoCard(com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_106), Icons.Default.Event) {
            if (next == null) Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_021))
            else {
                Text(next.title, style = MaterialTheme.typography.titleLarge)
                Text("${next.date}${next.time.takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty()} · ${sportLabel(next.sport)}")
            }
            TextButton(onClick = onCalendar) { Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_022)) }
        }
        InfoCard(com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_107), Icons.Default.AutoAwesome) {
            Text(com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_108))
            if (!connected) {
                Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_023))
                OutlinedButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) { Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_024)) }
            } else {
                OutlinedButton(onClick = { eventMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(events.firstOrNull { it.id == selectedEventId }?.let { com.fitlens.ai.data.LocalizedText.format(com.fitlens.ai.R.string.dynamic_012, it.title) } ?: com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_109))
                }
                DropdownMenu(expanded = eventMenu, onDismissRequest = { eventMenu = false }) {
                    DropdownMenuItem(text = { Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_025)) }, onClick = { selectedEventId = ""; eventMenu = false })
                    events.filter { it.date >= LocalDate.now().toString() }.forEach { event ->
                        DropdownMenuItem(text = { Text("${event.title} · ${event.date}") },
                            onClick = { selectedEventId = event.id; eventMenu = false })
                    }
                }
                Button(onClick = {
                    val event = events.firstOrNull { it.id == selectedEventId && it.date >= LocalDate.now().toString() }
                    onPrepareReport(event)
                }, enabled = !historyBusy && !reportBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary), modifier = Modifier.fillMaxWidth()) {
                    if (reportBusy || historyBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSecondary)
                    else Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp)); Text(if (reportBusy || historyBusy) com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_110) else com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_111))
                }
                Text(com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_112),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (shareReport != null) {
        val projectContext = androidx.compose.ui.platform.LocalContext.current
        val projectUrl = ChatGptProjectStore(projectContext).load()
        var projectError by remember(shareReport) { mutableStateOf<String?>(null) }
        AlertDialog(onDismissRequest = onDismissShareReport,
            title = { Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_026)) },
            text = { Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                Text(com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_113),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (projectUrl.isNotBlank()) {
                    FilledTonalButton(onClick = {
                        try {
                            val clipboard = projectContext.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText(com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_114), shareReport))
                            projectContext.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(projectUrl)))
                            android.widget.Toast.makeText(projectContext, com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_115), android.widget.Toast.LENGTH_LONG).show()
                            onDismissShareReport()
                        } catch (_: Exception) { projectError = com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_116) }
                    }, modifier = Modifier.fillMaxWidth()) { Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_027)) }
                    Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_028), style = MaterialTheme.typography.bodySmall)
                }
                projectError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.height(12.dp)); Text(shareReport, style = MaterialTheme.typography.bodySmall)
            } },
            confirmButton = { TextButton(onClick = { onOpenChatGpt(shareReport); onDismissShareReport() }) {
                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp)); Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_029))
            } },
            dismissButton = { TextButton(onClick = onDismissShareReport) { Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_014)) } })
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
                activity.durationSeconds?.let { add(com.fitlens.ai.data.LocalizedText.format(com.fitlens.ai.R.string.dynamic_021, (it / 60).toInt())) }
                activity.distanceMeters?.let { add("%.2f km".format(it / 1000)) }
                activity.calories?.let { add("$it kcal") }
            }.joinToString("  •  "))
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_117) else com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_118)) }
            if (expanded) {
                activity.steps?.let { Text(com.fitlens.ai.data.LocalizedText.format(com.fitlens.ai.R.string.dynamic_013, it)) }
                activity.averageHeartRate?.let { Text(com.fitlens.ai.data.LocalizedText.format(com.fitlens.ai.R.string.dynamic_014, it)) }
                activity.maxHeartRate?.let { Text(com.fitlens.ai.data.LocalizedText.format(com.fitlens.ai.R.string.dynamic_015, it)) }
                activity.elevationGainMeters?.let { Text(com.fitlens.ai.data.LocalizedText.format(com.fitlens.ai.R.string.dynamic_016, it.toInt())) }
                Text("Garmin ID: ${activity.activityId}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Preview(name = "Начало · светла тема", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Начало · тъмна тема", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DashboardPreview() {
    FitLensTheme { DashboardScreen(DashboardState.Ready(Dashboard(ActivitySummary(0, 0, 0.0),
        NutritionSummary(0, 0, 0, 0, 0))), null, onRefresh = {}, onImport = {}, onClearImports = {}) }
}

@Composable
private fun MfpScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var history by remember { mutableStateOf("") }
    var syncStatus by remember { mutableStateOf("") }
    var account by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val screenLifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(context) {
        val manager = androidx.work.WorkManager.getInstance(context)
        screenLifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            while (true) {
                val info = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    manager.getWorkInfosForUniqueWork(MfpSyncWorker.NAME).get().maxByOrNull { item -> item.tags.firstOrNull { it.startsWith("mfp-start:") }?.substringAfter(':')?.toLongOrNull() ?: 0L }
                }
                account = context.getSharedPreferences("mfp_experiment", android.content.Context.MODE_PRIVATE).getString("username", MfpHistoryStore(context).account()).orEmpty()
                busy = info != null && !info.state.isFinished
                history = MfpHistoryStore(context).report(language = Locale.getDefault().language)
                syncStatus = info?.let {
                    val done = if (it.state == androidx.work.WorkInfo.State.SUCCEEDED) 30 else it.progress.getInt("done", it.outputData.getInt("done", 0))
                    context.getString(R.string.mfp_background_progress, done, mfpWorkState(context, it.state)) +
                        if (it.state == androidx.work.WorkInfo.State.FAILED) "\n" + context.getString(R.string.mfp_background_failed, it.outputData.getString("error").orEmpty()) else ""
                }.orEmpty()
                kotlinx.coroutines.delay(1500)
            }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("MyFitnessPal", style = MaterialTheme.typography.headlineMedium)
        Text(androidx.compose.ui.res.stringResource(R.string.mfp_tab_help))
        Button(onClick = { context.startActivity(android.content.Intent(context, com.fitlens.ai.MfpExperimentActivity::class.java)) }, modifier = Modifier.fillMaxWidth()) {
            Text(androidx.compose.ui.res.stringResource(R.string.mfp_manage))
        }
        if (account.isNotBlank()) {
            Text("MyFitnessPal · $account", style = MaterialTheme.typography.titleMedium)
        }
        Button(onClick = { confirm = true }, enabled = !busy && account.matches(Regex("[A-Za-z0-9_.-]{1,100}")), modifier = Modifier.fillMaxWidth()) {
            Text(androidx.compose.ui.res.stringResource(R.string.mfp_batch_start))
        }
        if (busy) {
            OutlinedButton(onClick = { androidx.work.WorkManager.getInstance(context).cancelUniqueWork(MfpSyncWorker.NAME) }) {
                Text(androidx.compose.ui.res.stringResource(R.string.mfp_batch_stop))
            }
        }
        if (syncStatus.isNotBlank()) {
            Text(syncStatus)
        }
        Text(history.ifBlank { context.getString(R.string.mfp_history_empty) })
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false },
        title = { Text(androidx.compose.ui.res.stringResource(R.string.mfp_batch_start)) },
        text = { Text(androidx.compose.ui.res.stringResource(R.string.mfp_batch_consent)) },
        confirmButton = { TextButton(onClick = {
            confirm = false
            val request = androidx.work.OneTimeWorkRequestBuilder<MfpSyncWorker>()
                .addTag("mfp-start:${System.currentTimeMillis()}")
                .setInputData(androidx.work.workDataOf("account" to account, "end" to LocalDate.now().toString(),
                    "agent" to android.webkit.WebSettings.getDefaultUserAgent(context), "generation" to MfpHistoryStore(context).generation()))
                .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
                .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, java.util.concurrent.TimeUnit.SECONDS).build()
            androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(MfpSyncWorker.NAME, androidx.work.ExistingWorkPolicy.KEEP, request)
            busy = true
        }) { Text(androidx.compose.ui.res.stringResource(R.string.mfp_batch_start)) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(androidx.compose.ui.res.stringResource(R.string.mfp_close)) } })
}
