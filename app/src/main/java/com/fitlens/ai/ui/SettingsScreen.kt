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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.fitlens.ai.data.CsvImportSource

@Composable
internal fun SettingsScreen(
    connected: Boolean, busy: Boolean, onConnect: (String) -> Unit, onSync: () -> Unit,
    onDisconnect: () -> Unit, onImport: (CsvImportSource) -> Unit, onClear: () -> Unit,
    historyBusy: Boolean, historyCount: Int, onSyncHistory: () -> Unit,
) {
    var key by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
        .padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Настройки", style = MaterialTheme.typography.headlineMedium)
        Text("Връзките и данните ти, на едно място.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        InfoCard("Garmin Connect", Icons.Default.Watch) {
            Text(if (connected) "Свързан с личен API ключ" else "Добави личния си Personal API ключ",
                color = MaterialTheme.colorScheme.primary)
            if (connected) {
                Button(onClick = onSync, enabled = !busy && !historyBusy, modifier = Modifier.fillMaxWidth()) { Text("Изтегли данните за днес") }
                OutlinedButton(onClick = onSyncHistory, enabled = !busy && !historyBusy, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp)); Text(if (historyBusy) "Изтегляне на история…" else "Изтегли тренировките за 30 дни")
                }
                Text("Активности в последната синхронизация: $historyCount", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { confirm = "garmin" }, enabled = !busy && !historyBusy, modifier = Modifier.fillMaxWidth()) {
                    Text("Премахни връзката и Garmin данните")
                }
            } else {
                OutlinedTextField(value = key, onValueChange = { key = it }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), label = { Text("Personal API key") },
                    visualTransformation = PasswordVisualTransformation(), enabled = !busy)
                Button(onClick = { onConnect(key); key = "" }, enabled = key.isNotBlank() && !busy,
                    modifier = Modifier.fillMaxWidth()) { Text("Свържи и синхронизирай") }
            }
            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse("https://connect.garmin.com/app/settings/personalApiToken"))) }) { Text("Създай ключ в Garmin Connect") }
            Text("Личният ключ се пази криптиран на това устройство и се изпраща само към Garmin през HTTPS.",
                style = MaterialTheme.typography.bodySmall)
            if (connected) Text("Данните се обновяват при отваряне на приложението и във фонова задача приблизително на всеки 4 часа при налична мрежа. Android може да отложи задачата, за да пести батерия.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { onImport(CsvImportSource.GARMIN) }, enabled = !busy) {
                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp)); Text("Импортирай Garmin CSV")
            }
        }
        InfoCard("ChatGPT анализ", Icons.Default.AutoAwesome) {
            Text("Не е необходим API ключ. От Начало FitLens подготвя отчет и го отваря чрез Android менюто за споделяне.")
            Text("Преди споделяне ще видиш целия текст. Отговорът остава в избрания ChatGPT разговор и не се връща автоматично във FitLens.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        InfoCard("MyFitnessPal", Icons.Default.Restaurant) {
            Text("Добави хранителни данни от CSV файл. Директна API връзка още не е налична.")
            Button(onClick = { onImport(CsvImportSource.MYFITNESSPAL) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text("Импортирай MyFitnessPal CSV")
            }
            Text("CSV файловете се обработват на устройството. Пароли за MyFitnessPal не са нужни.",
                style = MaterialTheme.typography.bodySmall)
        }
        InfoCard("Външен вид", Icons.Default.BrightnessAuto) {
            Text("Темата следва телефона")
            Text("Светлите и тъмните цветове се сменят автоматично според системната настройка.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        InfoCard("Календар и Garmin", Icons.Default.Event) {
            Text("Добавяй предстоящите си участия ръчно. Публикуваният Garmin Personal API няма достъп до бъдещи календарни събития.")
            Text("Календарът се пази локално. Не се изпращат събития към Garmin.", style = MaterialTheme.typography.bodySmall)
        }
        InfoCard("Управление на данните", Icons.Default.Storage) {
            Text("Премахва изтеглените показатели и активности. Запазва календарните събития и Garmin връзката.")
            OutlinedButton(onClick = { confirm = "imports" }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text("Изтрий импортираните данни")
            }
        }
        Text("FitLens AI · Лична версия", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (confirm != null) {
        val garmin = confirm == "garmin"
        AlertDialog(onDismissRequest = { confirm = null },
            title = { Text(if (garmin) "Премахване на Garmin връзката" else "Изтриване на импортираните данни") },
            text = { Text(if (garmin) "Garmin ключът и локалните Garmin данни ще бъдат изтрити. Календарът и MyFitnessPal данните ще останат."
                else "Изтеглените Garmin и MyFitnessPal показатели, активности, сън и HRV ще бъдат изтрити. Календарът и Garmin ключът ще останат.") },
            confirmButton = { TextButton(onClick = { confirm = null; if (garmin) onDisconnect() else onClear() }) { Text("Изтрий") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Отказ") } })
    }
}
