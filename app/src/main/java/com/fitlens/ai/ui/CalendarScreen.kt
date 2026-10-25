package com.fitlens.ai.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fitlens.ai.data.CalendarEvent
import com.fitlens.ai.data.EventDraft
import com.fitlens.ai.data.EventLinkImport
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun CalendarScreen(state: CalendarState, onAdd: (EventDraft, () -> Unit) -> Unit,
    onImportLink: (String, String, (EventLinkImport) -> Unit) -> Unit,
    onDelete: (String) -> Unit, onRetry: () -> Unit,
    catalogue: RaceCatalogueState = RaceCatalogueState(), onLoadCatalogue: (String) -> Unit = {}) {
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var selected by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var selectedDraft by remember { mutableStateOf<EventDraft?>(null) }
    var deleteEvent by remember { mutableStateOf<CalendarEvent?>(null) }
    var discover by rememberSaveable { mutableStateOf(false) }
    val month = YearMonth.parse(monthText)
    val eventDates = state.events.map { it.date }.toSet()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
        .padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_045), style = MaterialTheme.typography.headlineMedium)
        Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_046), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { error -> InfoCard("Календарът има нужда от внимание", Icons.Default.Info) {
            Text(error)
            TextButton(onClick = onRetry, enabled = !state.loading) { Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_017)) }
        } }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(selected = !discover, onClick = { discover = false }, label = { Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_047)) })
            FilterChip(selected = discover, onClick = { discover = true }, label = { Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_048)) })
        }
        if (discover) RaceDiscoverySection(catalogue, state.events, state.loading, onLoadCatalogue) { draft ->
            selectedDraft = draft; showAdd = true
        }
        if (!discover) {
        TextButton(onClick = { selected = LocalDate.now().toString(); monthText = YearMonth.now().toString() }) { Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_049)) }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = { monthText = month.minusMonths(1).toString() }) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Предишен месец")
                    }
                    Text(month.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.forLanguageTag("bg")))
                        .replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { monthText = month.plusMonths(1).toString() }) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Следващ месец")
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    listOf("П", "В", "С", "Ч", "П", "С", "Н").forEach {
                        Box(Modifier.weight(1f).height(32.dp), contentAlignment = Alignment.Center) {
                            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                val offset = month.atDay(1).dayOfWeek.value - 1
                val rows = (offset + month.lengthOfMonth() + 6) / 7
                repeat(rows) { week ->
                    Row(Modifier.fillMaxWidth()) {
                        repeat(7) { weekday ->
                            val day = week * 7 + weekday - offset + 1
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                if (day in 1..month.lengthOfMonth()) {
                                    val date = month.atDay(day).toString()
                                    TextButton(onClick = { selected = date }, modifier = Modifier.heightIn(min = 48.dp).semantics {
                                        contentDescription = "$date${if (selected == date) ", избран ден" else ""}${if (date in eventDates) ", има събития" else ""}"
                                    },
                                        contentPadding = PaddingValues(0.dp), shape = CircleShape,
                                        colors = ButtonDefaults.textButtonColors(
                                            containerColor = if (selected == date) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                            contentColor = if (selected == date) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                        )) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(day.toString(), style = if (date == LocalDate.now().toString()) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium)
                                            Text(if (date in eventDates) "•" else " ", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Button(onClick = { selectedDraft = null; showAdd = true }, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp)); Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_050))
        }
        Text(LocalDate.parse(selected).format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("bg"))),
            style = MaterialTheme.typography.titleLarge)
        val dayEvents = state.events.filter { it.date == selected }
        if (dayEvents.isEmpty()) Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_051), color = MaterialTheme.colorScheme.onSurfaceVariant)
        dayEvents.forEach { event -> EventCard(event, !state.loading) { deleteEvent = event } }
        val upcoming = state.events.filter { it.date >= LocalDate.now().toString() && it.date != selected }
        if (upcoming.isNotEmpty()) {
            Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_052), style = MaterialTheme.typography.titleLarge)
            upcoming.forEach { event -> EventCard(event, !state.loading) { deleteEvent = event } }
        }
        }
        Text("Събитията са лични и се пазят на устройството. Garmin Personal API все още няма endpoint за бъдещ календар.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (showAdd) EventDialog(selected, state.loading, state.error,
        onDismiss = { showAdd = false; selectedDraft = null }, onSave = { draft -> onAdd(draft) {
            selected = draft.date; monthText = draft.date.take(7); showAdd = false; selectedDraft = null
        } }, onImportLink = { url, imported -> onImportLink(url, selectedDraft?.date ?: selected, imported) }, initialDraft = selectedDraft)
    deleteEvent?.let { event ->
        AlertDialog(onDismissRequest = { deleteEvent = null }, title = { Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_053)) },
            text = { Text("Да се изтрие ли „${event.title}“ от календара?") },
            confirmButton = { TextButton(onClick = { onDelete(event.id); deleteEvent = null }) { Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_013)) } },
            dismissButton = { TextButton(onClick = { deleteEvent = null }) { Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_014)) } })
    }
}

@Composable
private fun EventCard(event: CalendarEvent, enabled: Boolean, onDelete: () -> Unit) {
    val context = LocalContext.current
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            SportBadge(event.sport)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(event.title, style = MaterialTheme.typography.titleMedium)
                Text("${event.date} · ${event.time.ifBlank { "Цял ден" }}", style = MaterialTheme.typography.bodySmall)
                Text(sportLabel(event.sport), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                if (event.location.isNotBlank()) Text(event.location, style = MaterialTheme.typography.bodySmall)
                val details = buildList {
                    event.distanceKm?.let { add("${"%.2f".format(Locale.US, it).trimEnd('0').trimEnd('.')} km") }
                    event.elevationGainMeters?.let { add("D+ $it m") }
                    event.cutoffTime.takeIf(String::isNotBlank)?.let { add("лимит $it") }
                }
                if (details.isNotEmpty()) Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                if (event.sourceUrl.isNotBlank()) TextButton(onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(event.sourceUrl))) }
                }, contentPadding = PaddingValues(0.dp)) { Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_054)) }
            }
            IconButton(onClick = onDelete, enabled = enabled) { Icon(Icons.Default.DeleteOutline, contentDescription = "Изтрий ${event.title}") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventDialog(initialDate: String, busy: Boolean, storeError: String?, onDismiss: () -> Unit,
    onSave: (EventDraft) -> Unit, onImportLink: (String, (EventLinkImport) -> Unit) -> Unit,
    initialDraft: EventDraft? = null) {
    var title by rememberSaveable { mutableStateOf(initialDraft?.title.orEmpty()) }
    var date by rememberSaveable { mutableStateOf(initialDraft?.date ?: initialDate) }
    var time by rememberSaveable { mutableStateOf(initialDraft?.time.orEmpty()) }
    var location by rememberSaveable { mutableStateOf(initialDraft?.location.orEmpty()) }
    var sourceUrl by rememberSaveable { mutableStateOf(initialDraft?.sourceUrl.orEmpty()) }
    var distanceKm by rememberSaveable { mutableStateOf(initialDraft?.distanceKm?.toString().orEmpty()) }
    var cutoffTime by rememberSaveable { mutableStateOf(initialDraft?.cutoffTime.orEmpty()) }
    var elevationGainMeters by rememberSaveable { mutableStateOf(initialDraft?.elevationGainMeters?.toString().orEmpty()) }
    var linkStatus by remember { mutableStateOf<String?>(null) }
    var sport by rememberSaveable { mutableStateOf(initialDraft?.sport ?: "running") }
    var sportsOpen by remember { mutableStateOf(false) }
    var dateOpen by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_055)) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(title, { title = it.take(120) }, label = { Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_056)) }, singleLine = true)
                OutlinedTextField(sourceUrl, { sourceUrl = it.take(500); linkStatus = null },
                    label = { Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_057)) }, singleLine = true,
                    placeholder = { Text("https://…") }, enabled = !busy)
                Text("При попълване FitLens отваря публичната HTTPS страница от телефона ти. Провери намерените данни преди запис.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { onImportLink(sourceUrl) { imported ->
                    title = imported.draft.title
                    date = imported.draft.date
                    time = imported.draft.time
                    sport = imported.draft.sport
                    location = imported.draft.location
                    sourceUrl = imported.draft.sourceUrl
                    distanceKm = imported.draft.distanceKm?.toString().orEmpty()
                    cutoffTime = imported.draft.cutoffTime
                    elevationGainMeters = imported.draft.elevationGainMeters?.toString().orEmpty()
                    linkStatus = if (imported.dateWasFound) "Данните са попълнени. Провери ги преди запис." else
                        "Намерих името, но не и датата. Провери и попълни останалото ръчно."
                    error = null
                } }, enabled = sourceUrl.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Link, contentDescription = null)
                    Spacer(Modifier.width(8.dp)); Text(if (busy) "Чета страницата…" else "Попълни от линка")
                }
                linkStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                OutlinedButton(onClick = { dateOpen = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.DateRange, contentDescription = null)
                    Spacer(Modifier.width(8.dp)); Text(date)
                }
                OutlinedTextField(time, { time = it.take(5) }, label = { Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_058)) }, placeholder = { Text("09:30") }, singleLine = true)
                OutlinedTextField(distanceKm, { distanceKm = it.take(12) }, label = { Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_059)) },
                    placeholder = { Text("42.2") }, singleLine = true)
                OutlinedTextField(elevationGainMeters, { elevationGainMeters = it.take(8) }, label = { Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_060)) },
                    placeholder = { Text("1200") }, singleLine = true)
                OutlinedTextField(cutoffTime, { cutoffTime = it.take(80) }, label = { Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_061)) },
                    placeholder = { Text("6 часа") }, singleLine = true)
                Box {
                    OutlinedButton(onClick = { sportsOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(sportIcon(sport), contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp)); Text(sportLabel(sport))
                    }
                    DropdownMenu(expanded = sportsOpen, onDismissRequest = { sportsOpen = false }) {
                        listOf("running", "trail_running", "mountain_biking", "cycling", "swimming", "hiking", "walking", "strength_training", "yoga", "other").forEach { key ->
                            DropdownMenuItem(text = { Text(sportLabel(key)) }, leadingIcon = { Icon(sportIcon(key), contentDescription = null) },
                                onClick = { sport = key; sportsOpen = false })
                        }
                    }
                }
                OutlinedTextField(location, { location = it.take(200) }, label = { Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_062)) }, singleLine = true)
                (error ?: storeError)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            val draft = EventDraft(title = title, date = date, time = time.trim(), sport = sport, location = location,
                sourceUrl = sourceUrl.trim(), distanceKm = distanceKm.trim().replace(',', '.').toDoubleOrNull(),
                cutoffTime = cutoffTime.trim(), elevationGainMeters = elevationGainMeters.trim().toIntOrNull())
            if (distanceKm.isNotBlank() && draft.distanceKm == null) {
                error = "Дистанцията трябва да е число, например 42.2."
                return@TextButton
            }
            if (elevationGainMeters.isNotBlank() && draft.elevationGainMeters == null) {
                error = "Денивелацията трябва да е цяло число в метри."
                return@TextButton
            }
            val failure = runCatching { draft.validate() }.exceptionOrNull()
            if (failure != null) error = failure.message else { error = null; onSave(draft) }
        }) { Text(if (busy) "Запазване…" else "Запази") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_014)) } })
    if (dateOpen) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { dateOpen = false },
            confirmButton = { TextButton(onClick = {
                picker.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }
                dateOpen = false
            }) { Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_063)) } },
            dismissButton = { TextButton(onClick = { dateOpen = false }) { Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_014)) } }) { DatePicker(picker) }
    }
}
