package com.fitlens.ai.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fitlens.ai.data.RunnerProfileStore
import com.fitlens.ai.data.RunnerProfileImporter
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

@Composable
internal fun RunnerProfilesScreen() {
    val context = LocalContext.current
    val store = remember { RunnerProfileStore(context) }
    val importer = remember { RunnerProfileImporter() }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Бегач · BTR и ITRA", style = MaterialTheme.typography.headlineMedium)
        Text("Постави профилния линк и обнови публичните показатели и резултати. Провери намереното и запази, за да го включиш в отчета за ChatGPT.")
        store.all().forEach { initial ->
            var url by rememberSaveable(initial.provider) { mutableStateOf(initial.url) }
            var score by rememberSaveable(initial.provider) { mutableStateOf(initial.score) }
            var results by rememberSaveable(initial.provider) { mutableStateOf(initial.results) }
            var source by rememberSaveable(initial.provider) { mutableStateOf(initial.source) }
            var busy by remember { mutableStateOf(false) }
            var status by remember { mutableStateOf(initial.updated.takeIf(String::isNotBlank)?.let { "Обновено: $it" }.orEmpty()) }
            InfoCard(initial.provider) {
                OutlinedTextField(url, { url = it.take(500); source = "Ръчно въведени данни" }, label = { Text("Линк към личния профил") }, enabled = !busy, modifier = Modifier.fillMaxWidth())
                OutlinedButton(enabled = !busy && url.isNotBlank(), onClick = {
                    busy = true
                    scope.launch {
                        try {
                            val imported = importer.import(initial.provider, url)
                            val partial = (imported.score == null && score.isNotBlank()) || (imported.results == null && results.isNotBlank())
                            imported.score?.let { score = it.take(120) }
                            imported.results?.let { results = it }
                            source = if (partial) "Публична страница и запазени ръчни стойности; прегледани от потребителя" else
                                "Извлечено от публична профилна страница; прегледано от потребителя"
                            status = imported.message
                        } catch (cancelled: CancellationException) { throw cancelled
                        } catch (failure: Exception) { status = failure.message ?: "Профилът не може да бъде прочетен."
                        } finally { busy = false }
                    }
                }) { Text(if (busy) "Чета профила…" else "Обнови от профила") }
                OutlinedTextField(score, { score = it.take(120); source = "Ръчно въведени или коригирани данни" }, enabled = !busy, label = { Text("Показател и стойност (например Performance Index: 520)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(results, { results = it.take(4000); source = "Ръчно въведени или коригирани данни" }, enabled = !busy, label = { Text("Резултати: състезание, дата, дистанция, време, място") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    status = runCatching { store.save(initial.copy(url = url, score = score, results = results, source = source)); "Запазено. Ще бъде включено в следващия отчет." }
                        .getOrElse { it.message ?: "Записът не успя." }
                }, enabled = !busy) { Text("Запази ${initial.provider}") }
                TextButton(onClick = {
                    val target = if (initial.provider == "BTR") "https://www.my-btr.com/" else "https://itra.run/"
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target))) }
                }) { Text("Отвори сайта") }
                if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall)
                Text("Четат се само публични страници. Данни зад вход, абонамент или JavaScript може да липсват. Не са нужни парола или cookies.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
