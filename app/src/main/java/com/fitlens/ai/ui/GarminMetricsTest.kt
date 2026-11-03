package com.fitlens.ai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fitlens.ai.R
import com.fitlens.ai.data.GarminPersonalApi
import com.fitlens.ai.data.GarminPersonalKeyStore
import com.fitlens.ai.data.GarminProbeResult
import kotlinx.coroutines.launch

@Composable
internal fun GarminMetricsTest(enabled: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<GarminProbeResult>>(emptyList()) }
    var failed by remember { mutableStateOf(false) }
    OutlinedButton(onClick = {
        running = true; failed = false; results = emptyList(); copied = false
        scope.launch {
            try {
                results = GarminPersonalApi(GarminPersonalKeyStore(context)).probeMetrics()
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
            } catch (_: Exception) { failed = true
            } finally { running = false }
            visible = true
        }
    }, enabled = enabled && !running, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(if (running) R.string.gp_running else R.string.gp_title))
    }
    if (running) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (visible) AlertDialog(onDismissRequest = { visible = false },
        title = { Text(stringResource(R.string.gp_title)) },
        text = {
            SelectionContainer {
                Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.gp_help))
                    if (failed) Text(stringResource(R.string.gp_failed))
                    Text(com.fitlens.ai.data.garminProbeReport(results, context.getString(R.string.gp_empty)),
                        style = MaterialTheme.typography.bodySmall)

                }
            }
        }, dismissButton = {
            TextButton(enabled = results.isNotEmpty(), onClick = {
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Garmin metrics",
                    com.fitlens.ai.data.garminProbeReport(results, context.getString(R.string.gp_empty)))
                if (android.os.Build.VERSION.SDK_INT >= 33) clip.description.extras = android.os.PersistableBundle().apply {
                    putBoolean("android.content.extra.IS_SENSITIVE", true)
                }
                clipboard.setPrimaryClip(clip)
                copied = true
            }) { Text(stringResource(if (copied) R.string.gp_copied else R.string.gp_copy)) }
        }, confirmButton = { TextButton(onClick = { visible = false }) { Text(stringResource(R.string.mfp_close)) } })
}
