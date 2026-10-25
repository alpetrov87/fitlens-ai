package com.fitlens.ai.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
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
        Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_038), style = MaterialTheme.typography.headlineMedium)
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
                    save(updated)
                    editing = false
                    message = "${initial.provider} профилът е прочетен, запазен и показан."
                } else {
                    save(updated)
                    message = "Профилът е обновен. Липсващите полета са запазени от предишното обновяване."
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (failure: Exception) { message = "${initial.provider}: ${failure.message ?: "Обновяването не успя."}"
            } finally { busy = false }
        }
    }

    InfoCard(initial.provider, Icons.Default.Person) {
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (!editing) {
            val indicators = saved.score.split(" · ").filter(String::isNotBlank)
            Text(indicators.firstOrNull() ?: "Няма извлечен показател", style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary)
            if (initial.provider == "ITRA") EnduranceCards(saved.score)
            indicators.drop(1).filterNot { it.startsWith("ITRA ") }.forEach { indicator -> Text(indicator, style = MaterialTheme.typography.bodyLarge) }
            if (saved.updated.isNotBlank()) Text("Последен запис: ${saved.updated}", style = MaterialTheme.typography.labelMedium)
            Text(saved.source, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val raceRows = saved.results.lineSequence().filter(String::isNotBlank).toList()
            Text("Състезателни резултати · ${raceRows.size}", style = MaterialTheme.typography.titleMedium)
            if (raceRows.isEmpty()) Text("Няма налични резултати. Обнови профила или добави данни чрез „Редактирай“.")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(raceRows) { result ->
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(24.dp)) {
                        Column(Modifier.width(260.dp).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.EmojiEvents, null, tint = MaterialTheme.colorScheme.primary)
                            Text(result, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
            Button(onClick = { refresh() }, enabled = !busy && saved.url.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text(if (busy) "Обновяване…" else "Обнови от профила")
            }
            TextButton(onClick = {
                url = saved.url; score = saved.score; results = saved.results; source = saved.source; message = null; editing = true
            }, enabled = !busy) { Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_039)) }
        } else {
            OutlinedTextField(url, { url = it.take(500); source = "Ръчно въведени данни" }, enabled = !busy,
                label = { Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_040)) }, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = { refresh() }, enabled = !busy && url.isNotBlank()) { Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_041)) }
            OutlinedTextField(score, { score = it.take(120); source = "Ръчно въведени или коригирани данни" }, enabled = !busy,
                label = { Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_042)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(results, { results = it.take(4000); source = "Ръчно въведени или коригирани данни" }, enabled = !busy,
                label = { Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_043)) }, minLines = 3, modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                runCatching { save(saved.copy(url = url, score = score, results = results, source = source)); editing = false; message = "Профилът е запазен." }
                    .onFailure { message = it.message ?: "Записът не успя." }
            }, enabled = !busy) { Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_044)) }
            TextButton(onClick = { editing = false; message = null }, enabled = !busy) { Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_014)) }
        }
        TextButton(onClick = {
            val target = saved.url.ifBlank { if (initial.provider == "BTR") "https://www.my-btr.com/" else "https://itra.run/" }
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target))) }
                .onFailure { message = "Няма приложение за отваряне на линка." }
        }) { Text("Отвори ${initial.provider} профила") }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun EnduranceCards(score: String) {
    val colors = listOf(Color(0xFF777C76), Color(0xFF88CA32), Color(0xFFCBC600),
        Color(0xFF54C5E5), Color(0xFFAC6CAD), Color(0xFFDF7126), Color(0xFFB92E28))
    Text("Endurance Points", style = MaterialTheme.typography.titleLarge)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        (0..6).toList().chunked(2).forEach { points ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                points.forEach { point ->
                    val count = Regex("ITRA $point: (\\d+)").find(score)?.groupValues?.get(1) ?: "0"
                    Surface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(shape = RoundedCornerShape(10.dp), color = colors[point], modifier = Modifier.weight(1f)) {
                                Text("ITRA $point", modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp),
                                    style = MaterialTheme.typography.labelLarge, color = Color.Black)
                            }
                            Text("×$count", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
                if (points.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
