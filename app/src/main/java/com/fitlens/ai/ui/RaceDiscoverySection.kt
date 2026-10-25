package com.fitlens.ai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fitlens.ai.data.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun groupCatalogue(entries: List<EventDraft>): List<List<EventDraft>> = entries
    .groupBy { if (it.catalogueTitle.isNotBlank()) it.sourceUrl + "|" + it.catalogueTitle else it.sourceUrl + "|" + it.title }
    .values.map { routes -> routes.sortedWith(compareBy(EventDraft::date, EventDraft::time)) }
    .sortedBy { it.first().date }

@Composable
internal fun RaceDiscoverySection(state: RaceCatalogueState, saved: List<CalendarEvent>, busy: Boolean,
    onLoad: (String) -> Unit, onSelect: (EventDraft) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var sport by rememberSaveable { mutableStateOf("all") }
    var calendar by rememberSaveable { mutableStateOf(false) }
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var day by rememberSaveable { mutableStateOf("") }
    var opened by remember { mutableStateOf<List<EventDraft>?>(null) }
    LaunchedEffect(state.loaded, state.loading, state.error) {
        if (!state.loaded && !state.loading && state.error == null) onLoad(RaceCalendarCatalogue.HOME)
    }
    InfoCard("Открий състезание", Icons.Default.Explore) {
        Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_030))
        OutlinedButton(onClick = { onLoad(RaceCalendarCatalogue.HOME) }, enabled = !state.loading) {
            Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_031))
        }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedTextField(query, { query = it }, label = { Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_032)) }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("all" to "Всички", "trail_running" to "Трейл", "mountain_biking" to "MTB").forEach { (key, label) ->
                FilterChip(selected = sport == key, onClick = { sport = key }, label = { Text(label) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !calendar, onClick = { calendar = false }, label = { Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_033)) })
            FilterChip(selected = calendar, onClick = { calendar = true }, label = { Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_034)) })
        }
        val groups = groupCatalogue(state.entries).filter { routes ->
            routes.any { it.date >= LocalDate.now().toString() } &&
                (sport == "all" || routes.any { it.sport == sport }) &&
                routes.any { it.title.contains(query, true) || it.catalogueTitle.contains(query, true) || it.location.contains(query, true) }
        }
        val month = YearMonth.parse(monthText)
        if (calendar) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = { monthText = month.minusMonths(1).toString(); day = "" }) { Icon(Icons.Default.ChevronLeft, "Предишен месец") }
                Text(month.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.forLanguageTag("bg"))), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { monthText = month.plusMonths(1).toString(); day = "" }) { Icon(Icons.Default.ChevronRight, "Следващ месец") }
            }
            val dates = groups.flatten().map { it.date }.toSet()
            Row { listOf("П", "В", "С", "Ч", "П", "С", "Н").forEach { Text(it, Modifier.weight(1f)) } }
            val offset = month.atDay(1).dayOfWeek.value - 1
            repeat((offset + month.lengthOfMonth() + 6) / 7) { week ->
                Row {
                    repeat(7) { weekday ->
                        val number = week * 7 + weekday - offset + 1
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            if (number in 1..month.lengthOfMonth()) {
                                val date = month.atDay(number).toString()
                                TextButton(onClick = { day = if (day == date) "" else date }, contentPadding = PaddingValues(0.dp),
                                    colors = ButtonDefaults.textButtonColors(containerColor = if (day == date) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
                                    Text("$number${if (date in dates) "•" else ""}")
                                }
                            }
                        }
                    }
                }
            }
            Text(if (day.isBlank()) "Състезания през месеца" else "Състезания на $day", style = MaterialTheme.typography.labelLarge)
            TextButton(onClick = { day = ""; monthText = YearMonth.now().toString() }) { Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_035)) }
        }
        val visible = groups.filter { routes -> !calendar || routes.any { if (day.isNotBlank()) it.date == day else it.date.startsWith(monthText) } }
        Text("${visible.size} състезания", style = MaterialTheme.typography.labelLarge)
        if (visible.isEmpty() && state.loaded) Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_036))
        visible.forEach { routes ->
            val first = routes.first()
            Card(onClick = { opened = routes }, modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(first.catalogueTitle.ifBlank { first.title }, style = MaterialTheme.typography.titleMedium)
                    Text(routes.map { it.date }.distinct().let { if (it.size == 1) it.first() else "${it.first()} – ${it.last()}" })
                    if (first.location.isNotBlank()) Text(first.location)
                    Text(routes.map { sportLabel(it.sport) }.distinct().joinToString(" · "), color = MaterialTheme.colorScheme.primary)
                    Text("${routes.size} дистанции · Виж и избери →", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Text("Източник: racecalendar.bg · Личните участия са в отделния ти календар.", style = MaterialTheme.typography.bodySmall)
    }
    opened?.let { routes ->
        AlertDialog(onDismissRequest = { opened = null }, title = { Text(routes.first().catalogueTitle.ifBlank { routes.first().title }) },
            text = {
                Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    routes.sortedBy { if (sport == "all" || it.sport == sport) 0 else 1 }.forEach { route ->
                        val added = saved.any { it.title == route.title && it.date == route.date && it.time == route.time && it.distanceKm == route.distanceKm && it.sport == route.sport }
                        HorizontalDivider()
                        Text(route.title, style = MaterialTheme.typography.titleSmall)
                        Text("${sportLabel(route.sport)} · ${route.date} · ${route.time.ifBlank { "Часът не е публикуван" }}")
                        Text(listOfNotNull(route.distanceKm?.let { "$it km" }, route.elevationGainMeters?.let { "D+ $it m" }, route.cutoffTime.takeIf(String::isNotBlank)?.let { "Лимит: $it" }).joinToString(" · "))
                        OutlinedButton(onClick = { opened = null; onSelect(route) }, enabled = !busy && !added) {
                            Text(if (added) "В моя календар ✓" else "Добави в моя календар")
                        }
                    }
                }
            }, confirmButton = { TextButton(onClick = { opened = null }) { Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_037)) } })
    }
}
