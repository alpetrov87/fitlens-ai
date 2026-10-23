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

@Composable
internal fun RunnerProfilesScreen() {
    val context = LocalContext.current
    val store = remember { RunnerProfileStore(context) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Бегач · BTR и ITRA", style = MaterialTheme.typography.headlineMedium)
        Text("Добави личните си показатели и резултати. Данните се пазят на телефона и се включват в отчета за ChatGPT. Автоматична синхронизация със сайтовете не е налична.")
        store.all().forEach { initial ->
            var url by rememberSaveable(initial.provider) { mutableStateOf(initial.url) }
            var score by rememberSaveable(initial.provider) { mutableStateOf(initial.score) }
            var results by rememberSaveable(initial.provider) { mutableStateOf(initial.results) }
            var status by remember { mutableStateOf(initial.updated.takeIf(String::isNotBlank)?.let { "Обновено: $it" }.orEmpty()) }
            InfoCard(initial.provider) {
                OutlinedTextField(url, { url = it.take(500) }, label = { Text("Линк към личния профил") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(score, { score = it.take(120) }, label = { Text("Показател и стойност (например Performance Index: 520)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(results, { results = it.take(4000) }, label = { Text("Резултати: състезание, дата, дистанция, време, място") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    status = runCatching { store.save(initial.copy(url = url, score = score, results = results)); "Запазено. Ще бъде включено в следващия отчет." }
                        .getOrElse { it.message ?: "Записът не успя." }
                }) { Text("Запази ${initial.provider}") }
                TextButton(onClick = {
                    val target = if (initial.provider == "BTR") "https://www.my-btr.com/" else "https://itra.run/"
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target))) }
                }) { Text("Отвори сайта") }
                if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
