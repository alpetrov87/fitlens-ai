package com.fitlens.ai.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.repeatOnLifecycle
import androidx.work.*
import com.fitlens.ai.R
import com.fitlens.ai.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@Composable
internal fun MfpHomeCard(onOpenDiary: () -> Unit) {
    val context = LocalContext.current
    val store = remember { MfpHistoryStore(context) }
    val manager = remember { WorkManager.getInstance(context) }
    var account by remember { mutableStateOf("") }
    var day by remember { mutableStateOf<MfpDiaryPreview?>(null) }
    var work by remember { mutableStateOf<WorkInfo?>(null) }
    var today by remember { mutableStateOf(LocalDate.now().toString()) }
    var confirm by remember { mutableStateOf(false) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            while (true) {
                today = LocalDate.now().toString()
                val result = withContext(Dispatchers.IO) {
                    val user = context.getSharedPreferences("mfp_experiment", Context.MODE_PRIVATE)
                        .getString("username", store.account()).orEmpty().trim()
                    val recent = manager.getWorkInfosForUniqueWork(MfpSyncWorker.NAME).get().maxByOrNull {
                        it.tags.firstOrNull { tag -> tag.startsWith("mfp-start:") }?.substringAfter(':')?.toLongOrNull() ?: 0L
                    }
                    Triple(user, mfpHomeDay(store.days(user), today), recent)
                }
                account = result.first; day = result.second; work = result.third
                delay(2000)
            }
        }
    }
    val busy = work?.state?.isFinished == false
    val login = work?.state == WorkInfo.State.FAILED && work?.outputData?.getString("error") == "login"
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Restaurant, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.mh_title), style = MaterialTheme.typography.titleMedium)
                    Text("MyFitnessPal · $today", style = MaterialTheme.typography.labelSmall)
                }
                IconButton(enabled = !busy, onClick = {
                    if (!account.matches(Regex("[A-Za-z0-9_.-]{1,100}")) || login)
                        context.startActivity(Intent(context, com.fitlens.ai.MfpExperimentActivity::class.java))
                    else confirm = true
                }) { Icon(Icons.Default.Sync, stringResource(R.string.mfp_update_diary)) }
            }
            val snapshot = day
            if (snapshot == null) Text(stringResource(R.string.mh_missing), style = MaterialTheme.typography.bodySmall)
            else {
                val calories = mfpHomeNutrient(snapshot, "calories")
                val goal = snapshot.goals["calories"]?.takeIf { it > 0 }
                Text((calories?.let { formatMfpNumber(it) } ?: "—") + " kcal" + (goal?.let { " / ${formatMfpNumber(it)} kcal" } ?: ""),
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (calories != null && goal != null) LinearProgressIndicator(progress = { (calories / goal).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("protein" to R.string.extra_100, "carbs" to R.string.extra_101, "fat" to R.string.extra_102).forEach { (key, label) ->
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(label), style = MaterialTheme.typography.labelSmall)
                            Text((mfpHomeNutrient(snapshot, key)?.let { formatMfpNumber(it) } ?: "—") + " g", style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
            if (busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.mfp_sync_progress, work?.progress?.getInt("done", 0) ?: 0,
                    work?.progress?.getInt("total", 30) ?: 30, stringResource(R.string.mfp_state_running)), style = MaterialTheme.typography.labelSmall)
            }
            if (work?.state == WorkInfo.State.FAILED) Text(stringResource(when {
                login -> R.string.mfp_session_required
                work?.outputData?.getString("error") == "date_mismatch" -> R.string.mfp_date_sync_mismatch
                else -> R.string.mh_failed
            }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onOpenDiary, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.mh_diary)) }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false },
        title = { Text(stringResource(R.string.mfp_update_diary)) },
        text = { Text(stringResource(R.string.mfp_incremental_consent)) },
        confirmButton = { TextButton(onClick = {
            confirm = false
            val request = OneTimeWorkRequestBuilder<MfpSyncWorker>().addTag("mfp-start:${System.currentTimeMillis()}")
                .setInputData(workDataOf("account" to account, "end" to LocalDate.now().toString(), "incremental" to true,
                    "agent" to android.webkit.WebSettings.getDefaultUserAgent(context), "generation" to store.generation()))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
            manager.enqueueUniqueWork(MfpSyncWorker.NAME, ExistingWorkPolicy.KEEP, request)
        }) { Text(stringResource(R.string.mfp_update_diary)) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.mfp_close)) } })
}
private fun formatMfpNumber(value: Double): String = java.text.NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 }.format(value)
