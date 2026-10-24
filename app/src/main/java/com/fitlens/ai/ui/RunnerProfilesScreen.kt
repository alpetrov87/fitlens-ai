package com.fitlens.ai.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fitlens.ai.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun RunnerProfilesScreen() {
    val context = LocalContext.current
    val store = remember { RunnerProfileStore(context) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Моят профил на бегач", style = MaterialTheme.typography.headlineMedium)
        Text("Показатели и състезателна история от BTR и ITRA. Запазените данни се включват в отчета за ChatGPT.")
        Text("При ITRA WAF блокиране публичният профилен линк се прочита чрез Jina Reader. Не се изпращат ключове или лични Garmin данни.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        store.all().forEach { profile -> key(profile.provider) { RunnerProviderCard(profile, store) } }
    }
}

@Composable
private fun RunnerProviderCard(initial: RunnerProfile, store: RunnerProfileStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val importer = remember { RunnerProfileImporter() }
    var saved by remember { mutableStateOf(initial) }
    var editing by rememberSaveable { mutableStateOf(initial.url.isBlank() && initial.score.isBlank() && initial.results.isBlank()) }
    var url by rememberSaveable { mutableStateOf(initial.url) }
    var score by rememberSaveable { mutableStateOf(initial.score) }
    var results by rememberSaveable { mutableStateOf(initial.results) }
    var source by rememberSaveable { mutableStateOf(initial.source) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var expanded by rememberSaveable { mutableStateOf(false) }

    fun save(profile: RunnerProfile) {
        store.save(profile)
        saved = store.all().first { it.provider == initial.provider }
    }
    fun refresh() {
        busy = true
        scope.launch {
            try {
                val target = if (editing) url else saved.url
                val imported = importer.import(initial.provider, target)
                val previousScore = if (editing) score else saved.score
                val previousResults = if (editing) results else saved.results
                val mixed = (imported.score == null && previousScore.isNotBlank()) || (imported.results == null && previousResults.isNotBlank())
                val newSource = if (mixed) "Публична страница и предишни запазени стойности" else "Извлечено от публична профилна страница"
                val updated = saved.copy(url = target, score = imported.score ?: previousScore,
                    results = imported.results ?: previousResults, source = newSource)
                if (editing) {
                    score = updated.score; results = updated.results; source = updated.source
                    message = imported.message
                } else {
                    save(updated)
                    message = "Профилът е обновен. Липсващите полета са запазени от предишното обновяване."
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (failure: Exception) { message = failure.message ?: "Обновяването не успя."
            } finally { busy = false }
        }
    }

    InfoCard(initial.provider, Icons.Default.Person) {
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (!editing) {
            val indicators = saved.score.split(" · ").filter(String::isNotBlank)
            Text(indicators.firstOrNull() ?: "Няма извлечен показател", style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary)
            indicators.drop(1).forEach { indicator -> Text(indicator, style = MaterialTheme.typography.bodyLarge) }
            if (saved.updated.isNotBlank()) Text("Последен запис: ${saved.updated}", style = MaterialTheme.typography.labelMedium)
            Text(saved.source, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val raceRows = saved.results.lineSequence().filter(String::isNotBlank).toList()
            Text("Състезателни резултати · ${raceRows.size}", style = MaterialTheme.typography.titleMedium)
            if (raceRows.isEmpty()) Text("Няма налични резултати. Обнови профила или добави данни чрез „Редактирай“.")
            raceRows.take(if (expanded) raceRows.size else 3).forEach { result ->
                Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), shape = MaterialTheme.shapes.small) {
                    Text(result, modifier = Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (raceRows.size > 3) TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Покажи по-малко" else "Всички резултати (${raceRows.size})")
            }
            Button(onClick = { refresh() }, enabled = !busy && saved.url.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text(if (busy) "Обновяване…" else "Обнови от профила")
            }
            TextButton(onClick = {
                url = saved.url; score = saved.score; results = saved.results; source = saved.source; message = null; editing = true
            }, enabled = !busy) { Text("Редактирай профила") }
        } else {
            OutlinedTextField(url, { url = it.take(500); source = "Ръчно въведени данни" }, enabled = !busy,
                label = { Text("Линк към личния профил") }, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = { refresh() }, enabled = !busy && url.isNotBlank()) { Text("Прочети профила") }
            OutlinedTextField(score, { score = it.take(120); source = "Ръчно въведени или коригирани данни" }, enabled = !busy,
                label = { Text("Показател и стойност") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(results, { results = it.take(4000); source = "Ръчно въведени или коригирани данни" }, enabled = !busy,
                label = { Text("Състезателни резултати — по един на ред") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                runCatching { save(saved.copy(url = url, score = score, results = results, source = source)); editing = false; message = "Профилът е запазен." }
                    .onFailure { message = it.message ?: "Записът не успя." }
            }, enabled = !busy) { Text("Запази и покажи профила") }
            TextButton(onClick = { editing = false; message = null }, enabled = !busy) { Text("Отказ") }
        }
        TextButton(onClick = {
            val target = saved.url.ifBlank { if (initial.provider == "BTR") "https://www.my-btr.com/" else "https://itra.run/" }
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target))) }
                .onFailure { message = "Няма приложение за отваряне на линка." }
        }) { Text("Отвори ${initial.provider} профила") }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}
