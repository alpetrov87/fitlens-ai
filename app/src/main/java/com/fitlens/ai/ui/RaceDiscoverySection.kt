package com.fitlens.ai.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fitlens.ai.data.*
import java.time.LocalDate

@Composable
internal fun RaceDiscoverySection(state: RaceCatalogueState, saved: List<CalendarEvent>, busy: Boolean,
    onLoad: (String) -> Unit, onSelect: (EventDraft) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var includePast by rememberSaveable { mutableStateOf(false) }
    var sportFilter by rememberSaveable { mutableStateOf("all") }
    val context = LocalContext.current
    LaunchedEffect(state.loaded, state.loading, state.error) {
        if (!state.loaded && !state.loading && state.error == null) onLoad(RaceCalendarCatalogue.HOME)
    }
    InfoCard("Трейл и MTB в България", Icons.Default.Explore) {
        Text("Разгледай публикуваните дистанции и добави тази, за която си записан, към личния календар.")
        Button(onClick = { onLoad(RaceCalendarCatalogue.HOME) }, enabled = !state.loading,
            modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (state.loading) "Обновявам календара…" else if (state.loaded) "Обнови календара" else "Зареди календара")
        }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        TextButton(onClick = {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RaceCalendarCatalogue.HOME))) }
        }) { Text("Отвори racecalendar.bg") }
        if (state.loaded) {
            OutlinedTextField(query, { query = it }, label = { Text("Търси по име или място") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                FilterChip(selected = sportFilter == "all", onClick = { sportFilter = "all" }, label = { Text("Всички") })
                FilterChip(selected = sportFilter == "trail_running", onClick = { sportFilter = "trail_running" },
                    label = { Text("Трейл") }, leadingIcon = { Icon(sportIcon("trail_running"), null, Modifier.size(18.dp)) })
                FilterChip(selected = sportFilter == "mountain_biking", onClick = { sportFilter = "mountain_biking" },
                    label = { Text("MTB") }, leadingIcon = { Icon(sportIcon("mountain_biking"), null, Modifier.size(18.dp)) })
            }
            Row {
                Checkbox(checked = includePast, onCheckedChange = { includePast = it })
                Text("Включи минали състезания", modifier = Modifier.padding(top = 12.dp))
            }
            val entries = state.entries.filter { (includePast || it.date >= LocalDate.now().toString()) &&
                (sportFilter == "all" || it.sport == sportFilter) &&
                (it.title.contains(query, true) || it.location.contains(query, true)) }
            Text("Намерени: ${entries.size}", style = MaterialTheme.typography.labelLarge)
            if (entries.isEmpty()) Text("Няма състезания за избраните филтри. Опитай друга страница от сайта.")
            entries.take(if (expanded) 200 else 10).forEach { draft ->
                HorizontalDivider()
                Text(draft.title, style = MaterialTheme.typography.titleMedium)
                Text("${draft.date} · ${sportLabel(draft.sport)}", style = MaterialTheme.typography.bodySmall)
                if (draft.location.isNotBlank()) Text(draft.location, style = MaterialTheme.typography.bodySmall)
                draft.distanceKm?.let { Text("$it km", style = MaterialTheme.typography.bodySmall) }
                val added = saved.any { it.title == draft.title && it.date == draft.date && it.time == draft.time && it.distanceKm == draft.distanceKm }
                OutlinedButton(onClick = { onSelect(draft) }, enabled = !busy && !added) {
                    Text(if (added) "В твоя календар" else "Прегледай и добави")
                }
            }
            if (entries.size > 10) TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Покажи по-малко" else "Покажи всички") }
        }
        Text("Източник: racecalendar.bg. Добавянето е след преглед; FitLens не приема, че си записан само защото състезанието е в каталога.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
