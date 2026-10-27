package com.fitlens.ai.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.repeatOnLifecycle
import androidx.work.*
import com.fitlens.ai.R
import com.fitlens.ai.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MfpScreen() {
    val context = LocalContext.current
    val store = remember { MfpHistoryStore(context) }
    val manager = remember { WorkManager.getInstance(context) }
    var days by remember { mutableStateOf<List<MfpDiaryPreview>>(emptyList()) }
    var account by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var syncStatus by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var today by remember { mutableStateOf(LocalDate.now()) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(context, lifecycle) {
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            while (true) {
                today = LocalDate.now()
                val result = withContext(Dispatchers.IO) {
                    val user = context.getSharedPreferences("mfp_experiment", Context.MODE_PRIVATE)
                        .getString("username", store.account()).orEmpty()
                    val work = manager.getWorkInfosForUniqueWork(MfpSyncWorker.NAME).get().maxByOrNull { item ->
                        item.tags.firstOrNull { it.startsWith("mfp-start:") }?.substringAfter(':')?.toLongOrNull() ?: 0L
                    }
                    Triple(user, store.days(user), work)
                }
                account = result.first
                days = result.second
                val work = result.third
                busy = work != null && !work.state.isFinished
                syncStatus = work?.let {
                    val done = it.progress.getInt("done", it.outputData.getInt("done", 0))
                    val total = it.progress.getInt("total", it.outputData.getInt("total", 30))
                    context.getString(R.string.mfp_sync_progress, done, total, mfpWorkState(context, it.state)) +
                        if (it.state == WorkInfo.State.FAILED) "\n" + context.getString(R.string.mfp_background_failed, it.outputData.getString("error").orEmpty()) else ""
                }.orEmpty()
                delay(2000)
            }
        }
    }
    val byDate = remember(days) { days.associateBy { it.date } }
    val data = byDate[selected]
    val dates = remember(today) { (29 downTo 0).map { today.minusDays(it.toLong()) } }
    val coverage = dates.count { it.toString() in byDate }
    val dateFormat = remember(Locale.getDefault()) { DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.mfp_diary_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(account.ifBlank { "MyFitnessPal" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { context.startActivity(Intent(context, com.fitlens.ai.MfpExperimentActivity::class.java)) }) {
                Icon(Icons.Default.ManageAccounts, stringResource(R.string.mfp_manage))
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.mfp_coverage, coverage), style = MaterialTheme.typography.titleSmall)
                if (days.isNotEmpty()) Text(stringResource(R.string.mfp_saved_range,
                    LocalDate.parse(days.last().date).format(dateFormat), LocalDate.parse(days.first().date).format(dateFormat), days.size),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.mfp_incremental_help), style = MaterialTheme.typography.bodySmall)
                Button(onClick = { confirm = true }, enabled = !busy && account.matches(Regex("[A-Za-z0-9_.-]{1,100}")), modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Sync, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.mfp_update_diary))
                }
                if (busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    TextButton(onClick = { manager.cancelUniqueWork(MfpSyncWorker.NAME) }) { Text(stringResource(R.string.mfp_batch_stop)) }
                }
                if (syncStatus.isNotBlank()) Text(syncStatus, style = MaterialTheme.typography.bodySmall)
                if (account.isBlank()) Text(stringResource(R.string.mfp_setup_hint), style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { selected = LocalDate.parse(selected).minusDays(1).toString() }) { Icon(Icons.Default.ChevronLeft, stringResource(R.string.mfp_previous_day)) }
            TextButton(onClick = { picker = true }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.CalendarMonth, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(LocalDate.parse(selected).format(dateFormat))
            }
            IconButton(onClick = { selected = LocalDate.parse(selected).plusDays(1).toString() }, enabled = LocalDate.parse(selected).isBefore(today)) {
                Icon(Icons.Default.ChevronRight, stringResource(R.string.mfp_next_day))
            }
        }
        LazyRow(state = rememberLazyListState(initialFirstVisibleItemIndex = 24), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(dates, key = { it.toString() }) { day ->
                FilterChip(selected = selected == day.toString(), onClick = { selected = day.toString() }, label = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(day.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault())), style = MaterialTheme.typography.labelSmall)
                        Text(day.dayOfMonth.toString(), fontWeight = FontWeight.Bold)
                        Text(if (day.toString() in byDate) "●" else "·", color = MaterialTheme.colorScheme.primary)
                    }
                })
            }
        }
        if (data == null) {
            Text(stringResource(R.string.mfp_day_missing), style = MaterialTheme.typography.bodyMedium)
        } else {
            NutritionDay(data)
        }
        Text(stringResource(R.string.mfp_week_title), style = MaterialTheme.typography.titleSmall)
        val week = (6 downTo 0).map { today.minusDays(it.toLong()) }
        val max = week.mapNotNull { byDate[it.toString()]?.totals?.get("calories") }.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
        Row(Modifier.fillMaxWidth().height(140.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            week.forEach { day ->
                val calories = byDate[day.toString()]?.totals?.get("calories")
                Column(Modifier.weight(1f).clickable { selected = day.toString() }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(calories?.let { number(it) } ?: "—", style = MaterialTheme.typography.labelSmall)
                    Box(Modifier.fillMaxWidth().height(if (calories == null) 3.dp else (4 + 84 * calories / max).dp)
                        .background(if (calories == null) MaterialTheme.colorScheme.outlineVariant else if (selected == day.toString()) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp)))
                    Text(day.format(DateTimeFormatter.ofPattern("dd.MM")), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Text(stringResource(R.string.mfp_missing_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (picker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = LocalDate.parse(selected).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = !Instant.ofEpochMilli(utcTimeMillis).atOffset(ZoneOffset.UTC).toLocalDate().isAfter(today)
            })
        DatePickerDialog(onDismissRequest = { picker = false }, confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { selected = Instant.ofEpochMilli(it).atOffset(ZoneOffset.UTC).toLocalDate().toString() }; picker = false }) { Text(stringResource(R.string.ui_044)) }
        }, dismissButton = { TextButton(onClick = { picker = false }) { Text(stringResource(R.string.mfp_close)) } }) { DatePicker(state) }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text(stringResource(R.string.mfp_update_diary)) },
        text = { Text(stringResource(R.string.mfp_incremental_consent)) },
        confirmButton = { TextButton(onClick = {
            confirm = false
            val request = OneTimeWorkRequestBuilder<MfpSyncWorker>().addTag("mfp-start:${System.currentTimeMillis()}")
                .setInputData(workDataOf("account" to account, "end" to today.toString(), "incremental" to true,
                    "agent" to android.webkit.WebSettings.getDefaultUserAgent(context), "generation" to store.generation()))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
            manager.enqueueUniqueWork(MfpSyncWorker.NAME, ExistingWorkPolicy.KEEP, request)
            busy = true
        }) { Text(stringResource(R.string.mfp_update_diary)) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.mfp_close)) } })
}

@Composable
private fun NutritionDay(data: MfpDiaryPreview) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.mfp_daily_energy), style = MaterialTheme.typography.labelLarge)
            Text((data.totals["calories"]?.let { number(it) } ?: "—") + " kcal", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("carbs", "protein", "fat").forEach { nutrient ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(nutrientLabel(nutrient), style = MaterialTheme.typography.labelSmall)
                        Text((data.totals[nutrient]?.let { number(it) } ?: "—") + " g", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
    val extras = data.totals.filterKeys { it !in listOf("calories", "carbs", "protein", "fat") }
    var extrasExpanded by rememberSaveable(data.date) { mutableStateOf(false) }
    if (extras.isNotEmpty()) {
        TextButton(onClick = { extrasExpanded = !extrasExpanded }) {
            Text(stringResource(R.string.mfp_other_nutrients))
            Icon(if (extrasExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
        }
        if (extrasExpanded) extras.forEach { (key, value) -> NutrientLine(key, value) }
    }
    Text(stringResource(R.string.mfp_meals_title, data.foods.groupBy { it.meal }.size), style = MaterialTheme.typography.titleMedium)
    if (data.foods.isEmpty()) Text(stringResource(R.string.mfp_no_food_rows), style = MaterialTheme.typography.bodySmall)
    data.foods.groupBy { it.meal }.forEach { (meal, foods) ->
        var expanded by rememberSaveable(data.date, meal) { mutableStateOf(false) }
        OutlinedCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Restaurant, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(mealLabel(meal), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.mfp_food_count, foods.size), style = MaterialTheme.typography.bodySmall)
                }
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, stringResource(R.string.mfp_meal_details))
            }
            if (expanded) Column(Modifier.padding(horizontal = 14.dp).padding(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                foods.forEach { food ->
                    HorizontalDivider()
                    Text(food.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    food.nutrients.forEach { (key, value) -> NutrientLine(key, value) }
                }
            }
        }
    }
}

@Composable
private fun NutrientLine(key: String, value: Double) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(nutrientLabel(key), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text("${number(value)} ${if (key == "calories") "kcal" else if (key in listOf("sodium", "potassium")) "mg" else "g"}", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun nutrientLabel(key: String): String = when (key) {
    "calories" -> stringResource(R.string.mfp_n_calories)
    "carbs" -> stringResource(R.string.mfp_n_carbs)
    "protein" -> stringResource(R.string.mfp_n_protein)
    "fat" -> stringResource(R.string.mfp_n_fat)
    "sugar" -> stringResource(R.string.mfp_n_sugar)
    "fiber" -> stringResource(R.string.mfp_n_fiber)
    "sodium" -> stringResource(R.string.mfp_n_sodium)
    "potassium" -> stringResource(R.string.mfp_n_potassium)
    else -> key
}

@Composable
private fun mealLabel(meal: String): String = when (meal.lowercase(Locale.ROOT)) {
    "breakfast" -> stringResource(R.string.mfp_breakfast)
    "lunch" -> stringResource(R.string.mfp_lunch)
    "dinner" -> stringResource(R.string.mfp_dinner)
    "snack", "snacks" -> stringResource(R.string.mfp_snacks)
    else -> meal
}

private fun number(value: Double): String = java.text.NumberFormat.getNumberInstance(Locale.getDefault()).apply { maximumFractionDigits = 1 }.format(value)
