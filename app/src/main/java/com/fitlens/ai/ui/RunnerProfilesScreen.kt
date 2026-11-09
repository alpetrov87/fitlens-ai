package com.fitlens.ai.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
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
        Text(com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_140))
        Text(com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_141),
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

    val utmbLogin = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            saved = store.all().first { it.provider == initial.provider }
            url = saved.url; score = saved.score; results = saved.results; source = saved.source
            editing = false; message = context.getString(com.fitlens.ai.R.string.utmb_web_saved)
        }
    }
    fun save(profile: RunnerProfile) {
        store.save(profile)
        saved = store.all().first { it.provider == initial.provider }
    }
    fun refresh() {
        if (initial.provider == "UTMB") {
            runCatching {
                val target = (if (editing) url else saved.url).trim()
                utmbProfileSlug(target)
                utmbLogin.launch(Intent(context, com.fitlens.ai.UtmbProfileActivity::class.java).putExtra("profile_url", target))
            }.onFailure { message = context.getString(com.fitlens.ai.R.string.utmb_web_wrong) }
            return
        }
        busy = true
        scope.launch {
            try {
                val target = if (editing) url else saved.url
                val imported = importer.import(initial.provider, target)
                val previousScore = if (editing) score else saved.score
                val previousResults = if (editing) results else saved.results
                val mixed = (imported.score == null && previousScore.isNotBlank()) || (imported.results == null && previousResults.isNotBlank())
                val newSource = if (mixed) com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_142) else com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_143)
                val updated = saved.copy(url = target, score = imported.score ?: previousScore,
                    results = imported.results ?: previousResults, source = newSource)
                if (editing) {
                    score = updated.score; results = updated.results; source = updated.source
                    save(updated)
                    editing = false
                    message = com.fitlens.ai.data.LocalizedText.format(com.fitlens.ai.R.string.dynamic_003, initial.provider)
                } else {
                    save(updated)
                    message = com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_144)
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (failure: Exception) { message = "${initial.provider}: ${failure.message ?: com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.refresh_failed)}"
            } finally { busy = false }
        }
    }

    InfoCard(initial.provider, if (initial.provider == "UTMB") Icons.Default.Terrain else Icons.Default.Person) {
        if (initial.provider == "UTMB" && editing) Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.utmb_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (!editing) {
            val indicators = saved.score.split(" · ").filter(String::isNotBlank)
            if (initial.provider != "UTMB") Text(indicators.firstOrNull() ?: com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_145), style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary)
            if (initial.provider == "ITRA") EnduranceCards(saved.score)
            if (initial.provider == "UTMB") UtmbIndexCards(saved.score)
            indicators.drop(1).filterNot { it.startsWith("ITRA ") || initial.provider == "UTMB" }.forEach { indicator -> Text(indicator, style = MaterialTheme.typography.bodyLarge) }
            if (saved.updated.isNotBlank()) Text(com.fitlens.ai.data.LocalizedText.format(com.fitlens.ai.R.string.dynamic_000, saved.updated), style = MaterialTheme.typography.labelMedium)
            Text(saved.source, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val raceRows = saved.results.lineSequence().filter(String::isNotBlank).toList()
            if (initial.provider != "UTMB" || raceRows.isNotEmpty()) {
            Text(com.fitlens.ai.data.LocalizedText.format(com.fitlens.ai.R.string.dynamic_001, raceRows.size), style = MaterialTheme.typography.titleMedium)
            if (raceRows.isEmpty()) Text(com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_146))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(raceRows) { result ->
                    if (initial.provider == "UTMB") UtmbRaceResultCard(result) else Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(24.dp)) {
                        Column(Modifier.width(260.dp).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.EmojiEvents, null, tint = MaterialTheme.colorScheme.primary)
                            Text(result, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
            }
            Button(onClick = { refresh() }, enabled = !busy && saved.url.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text(if (initial.provider == "UTMB") context.getString(com.fitlens.ai.R.string.utmb_web_title) else if (busy) com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_147) else com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_148))
            }
            TextButton(onClick = {
                url = saved.url; score = saved.score; results = saved.results; source = saved.source; message = null; editing = true
            }, enabled = !busy) { Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_039)) }
        } else {
            OutlinedTextField(url, { if (initial.provider == "UTMB" && it.take(500) != url) { score = ""; results = "" }; url = it.take(500); source = com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_149) }, enabled = !busy,
                label = { Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_040)) }, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = { refresh() }, enabled = !busy && url.isNotBlank()) { Text(androidx.compose.ui.res.stringResource(if (initial.provider == "UTMB") com.fitlens.ai.R.string.utmb_web_title else com.fitlens.ai.R.string.ui_041)) }
            OutlinedTextField(score, { score = it.take(120); source = com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_150) }, enabled = !busy,
                label = { Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_042)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(results, { results = it.take(4000); source = com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_150) }, enabled = !busy,
                label = { Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_043)) }, minLines = 3, modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                runCatching { save(saved.copy(url = url, score = score, results = results, source = source)); editing = false; message = com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_151) }
                    .onFailure { message = it.message ?: com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_152) }
            }, enabled = !busy) { Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_044)) }
            TextButton(onClick = { editing = false; message = null }, enabled = !busy) { Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_014)) }
        }
        TextButton(onClick = {
            val target = saved.url.ifBlank { when (initial.provider) { "BTR" -> "https://www.my-btr.com/"; "UTMB" -> "https://utmb.world/utmb-index"; else -> "https://itra.run/" } }
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target))) }
                .onFailure { message = com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_153) }
        }) { Text(com.fitlens.ai.data.LocalizedText.format(com.fitlens.ai.R.string.dynamic_002, initial.provider)) }
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

@Composable
private fun UtmbIndexCards(score: String) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
        .background(Brush.linearGradient(listOf(Color(0xFF004B75), Color(0xFF030A35)))).padding(20.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Surface(color = Color(0xFF061433)) { Text("UTMB", Modifier.padding(horizontal = 7.dp, vertical = 3.dp), color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) }
                Surface(color = Color(0xFF00C4ED)) { Text("INDEX", Modifier.padding(horizontal = 7.dp, vertical = 3.dp), color = Color(0xFF030A35), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold) }
            }
            Text(utmbIndexValue(score, "UTMB Index")?.toString() ?: "—", color = Color.White,
                fontSize = 54.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.utmb_overall), color = Color(0xFFBDEAF5), style = MaterialTheme.typography.labelMedium)
        }
        Icon(Icons.Default.Terrain, null, modifier = Modifier.size(60.dp), tint = Color(0xFFBDEAF5).copy(alpha = 0.35f))
    }
    val colors = listOf(Color(0xFFF0FF00), Color(0xFFFF7100), Color(0xFF82D852), Color(0xFFFF343C))
    listOf("20K", "50K", "100K", "100M").withIndex().chunked(2).forEach { categories ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            categories.forEach { (index, category) ->
                val value = utmbIndexValue(score, category)
                Surface(Modifier.weight(1f), shape = RoundedCornerShape(16.dp),
                    color = if (value == null) MaterialTheme.colorScheme.surfaceVariant else colors[index].copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (value == null) MaterialTheme.colorScheme.outlineVariant else colors[index].copy(alpha = 0.7f))) {
                    Column(Modifier.padding(14.dp).heightIn(min = 110.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Surface(color = colors[index], shape = RoundedCornerShape(4.dp)) {
                                Text(category, Modifier.padding(horizontal = 7.dp, vertical = 3.dp), color = Color(0xFF030A35), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold)
                            }
                            Icon(Icons.Default.Terrain, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
                        }
                        if (value != null) Text(value.toString(), style = MaterialTheme.typography.headlineLarge, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        else {
                            Icon(Icons.Default.Lock, null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.utmb_missing_index), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UtmbRaceResultCard(raw: String) {
    val result = utmbRaceCardData(raw)
    val context = LocalContext.current
    Surface(Modifier.width(276.dp), color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.EmojiEvents, null, tint = MaterialTheme.colorScheme.primary)
                result.date?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
            }
            Text(result.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Surface(color = Color(0xFF04153D), shape = RoundedCornerShape(14.dp)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("UTMB Score", color = Color(0xFF92DBED), style = MaterialTheme.typography.labelLarge)
                    Text(result.score?.toString() ?: "—", color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineMedium)
                }
            }
            if (result.details.isNotBlank()) Text(result.details, style = MaterialTheme.typography.bodyMedium)
            result.url?.let { url -> TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.onFailure { android.widget.Toast.makeText(context, com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_153), android.widget.Toast.LENGTH_SHORT).show() } }) {
                Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.utmb_open_race))
            } }
        }
    }
}
