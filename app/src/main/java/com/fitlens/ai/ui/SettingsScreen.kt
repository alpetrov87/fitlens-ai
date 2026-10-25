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
    val projectStore = remember { com.fitlens.ai.data.ChatGptProjectStore(context) }
    var projectUrl by remember { mutableStateOf(projectStore.load()) }
    var projectMessage by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
        .padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_000), style = MaterialTheme.typography.headlineMedium)
        Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_001), color = MaterialTheme.colorScheme.onSurfaceVariant)
        InfoCard("Garmin Connect", Icons.Default.Watch) {
            Text(if (connected) com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_066) else com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_067),
                color = MaterialTheme.colorScheme.primary)
            if (connected) {
                Button(onClick = onSync, enabled = !busy && !historyBusy, modifier = Modifier.fillMaxWidth()) { Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_002)) }
                OutlinedButton(onClick = onSyncHistory, enabled = !busy && !historyBusy, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp)); Text(if (historyBusy) com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_068) else com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_069))
                }
                Text(com.fitlens.ai.data.LocalizedText.format(com.fitlens.ai.R.string.dynamic_004, historyCount), style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { confirm = "garmin" }, enabled = !busy && !historyBusy, modifier = Modifier.fillMaxWidth()) {
                    Text(com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_070))
                }
            } else {
                OutlinedTextField(value = key, onValueChange = { key = it }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), label = { Text("Personal API key") },
                    visualTransformation = PasswordVisualTransformation(), enabled = !busy)
                Button(onClick = { onConnect(key); key = "" }, enabled = key.isNotBlank() && !busy,
                    modifier = Modifier.fillMaxWidth()) { Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_003)) }
            }
            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse("https://connect.garmin.com/app/settings/personalApiToken"))) }) { Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_004)) }
            Text(com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_071),
                style = MaterialTheme.typography.bodySmall)
            if (connected) Text(com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_072),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { onImport(CsvImportSource.GARMIN) }, enabled = !busy) {
                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp)); Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_005))
            }
        }
        InfoCard(com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_073), Icons.Default.AutoAwesome) {
            OutlinedTextField(projectUrl, { projectUrl = it.take(500); projectMessage = null },
                label = { Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_006)) }, placeholder = { Text("https://chatgpt.com/g/g-p-…/project") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Row {
                TextButton(onClick = {
                    runCatching { projectStore.save(projectUrl) }.onSuccess { projectMessage = com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_074) }
                        .onFailure { projectMessage = it.message }
                }) { Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_007)) }
                TextButton(onClick = {
                    runCatching { projectStore.save(""); projectUrl = "" }.onSuccess { projectMessage = com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_075) }
                        .onFailure { projectMessage = it.message }
                }) { Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_008)) }
            }
            projectMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text(com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_076),
                style = MaterialTheme.typography.bodySmall)

            Text(com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_077))
            Text(com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_078),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        InfoCard("MyFitnessPal", Icons.Default.Restaurant) {
            Text(com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_079))
            Button(onClick = { onImport(CsvImportSource.MYFITNESSPAL) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_009))
            }
            Text(com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_080),
                style = MaterialTheme.typography.bodySmall)
        }
        InfoCard(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.language), Icons.Default.Language) {
            val selectedLanguage = com.fitlens.ai.data.AppLanguage.selection(context)
            listOf("" to androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.system_language), "bg" to "Български", "en" to "English").forEach { (tag, label) ->
                TextButton(onClick = { com.fitlens.ai.data.AppLanguage.select(context, tag) }) {
                    Text((if (selectedLanguage == tag) "✓ " else "") + label)
                }
            }
        }
        InfoCard(com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_081), Icons.Default.BrightnessAuto) {
            Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_010))
            Text(com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_082),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        InfoCard(com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_083), Icons.Default.Event) {
            Text(com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_084))
            Text(com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_085), style = MaterialTheme.typography.bodySmall)
        }
        InfoCard(com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_086), Icons.Default.Storage) {
            Text(com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_087))
            OutlinedButton(onClick = { confirm = "imports" }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_011))
            }
        }
        Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_012), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (confirm != null) {
        val garmin = confirm == "garmin"
        AlertDialog(onDismissRequest = { confirm = null },
            title = { Text(if (garmin) com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_088) else com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_089)) },
            text = { Text(if (garmin) com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_090)
                else com.fitlens.ai.data.LocalizedText.get(com.fitlens.ai.R.string.extra_091)) },
            confirmButton = { TextButton(onClick = { confirm = null; if (garmin) onDisconnect() else onClear() }) { Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_013)) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(androidx.compose.ui.res.stringResource(com.fitlens.ai.R.string.ui_014)) } })
    }
}
