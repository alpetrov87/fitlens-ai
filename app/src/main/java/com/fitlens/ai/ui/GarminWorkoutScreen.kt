package com.fitlens.ai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.fitlens.ai.R
import com.fitlens.ai.workouts.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Only backend pairing credentials are entered here, never a Garmin password. */
@Composable
internal fun GarminWorkoutScreen(workout: Workout, repository: WorkoutRepository, updated: () -> Unit, close: () -> Unit) {
    val context = LocalContext.current
    val store = remember { WorkoutBackendStore(context) }
    var saved by remember { mutableStateOf(runCatching { store.settings() }.getOrNull()) }
    var url by remember { mutableStateOf(saved?.url.orEmpty()) }
    var token by remember { mutableStateOf("") }
    var trusted by remember { mutableStateOf(false) }
    var connected by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf(false) }
    var generic by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var disconnectConfirm by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(if (workout.garminId != null) UploadMode.UPDATE_EXISTING else UploadMode.CREATE_NEW) }
    val scope = rememberCoroutineScope()
    val provider = remember { GarminWorkoutProvider() }
    val compatibility = remember(workout) { provider.compatibility(workout) }
    fun error(failure: Throwable) {
        val code = if (failure is IllegalArgumentException) "validation" else (failure as? WorkoutRemoteFailure)?.code
        message = context.getString(when(code) {
            "authentication", "expired" -> R.string.wb_error_auth
            "rate_limit" -> R.string.wb_error_rate
            "validation" -> R.string.wb_error_validation
            "account_mismatch" -> R.string.wb_error_account
            "uncertain", "invalid_response" -> R.string.wb_error_uncertain
            else -> R.string.wb_error_network
        })
        connected = false
    }
    TextButton(onClick = close, enabled = !busy) { Text(stringResource(R.string.w_back)) }
    Text(stringResource(R.string.wb_title), style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(R.string.wb_intro), style = MaterialTheme.typography.bodyMedium)
    OutlinedTextField(url, { url = it.take(2048); trusted = false; connected = false }, enabled = !busy,
        label = { Text(stringResource(R.string.wb_url)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(token, { token = it.trim().take(512); trusted = false; connected = false }, enabled = !busy,
        label = { Text(stringResource(R.string.wb_token)) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
    if (saved != null) Text(stringResource(R.string.wb_token_saved), style = MaterialTheme.typography.bodySmall)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(trusted, { trusted = it }, enabled = !busy)
        Text(stringResource(R.string.wb_trust), style = MaterialTheme.typography.bodySmall)
    }
    Button(onClick = {
        scope.launch {
            busy = true; message = ""
            try {
                val origin = workoutBackendUrl(url)
                // Do not reuse an existing token if the user changes the origin.
                val key = token.ifBlank { saved?.takeIf { it.url == origin }?.token.orEmpty() }
                val settings = WorkoutBackendSettings(origin, key)
                val state = withContext(Dispatchers.IO) { BackendWorkoutTransport(settings).authentication() }
                store.configure(origin, key); saved = settings; token = ""; url = origin
                connected = state == WorkoutConnection.CONNECTED
                message = context.getString(if (connected) R.string.wb_connected else R.string.wb_login_needed)
            } catch (failure: Exception) { error(failure) }
            finally { busy = false }
        }
    }, enabled = trusted && !busy && url.isNotBlank()) { Text(stringResource(R.string.wb_connect)) }
    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodyMedium)
    if (saved != null || store.hasKey()) {
        TextButton(onClick = { disconnectConfirm = true }, enabled = !busy && saved != null) { Text(stringResource(R.string.wb_disconnect)) }
        TextButton(onClick = { store.clear(); saved = null; token = ""; connected = false; message = context.getString(R.string.wb_forgotten) }, enabled = !busy) {
            Text(stringResource(R.string.wb_forget))
        }
    }
    HorizontalDivider()
    Text(workout.name, style = MaterialTheme.typography.titleLarge)
    if (workout.garminId != null) Text("Garmin ID: ${workout.garminId}")
    if (!compatibility.supported) Text(stringResource(R.string.w_unsupported), color = MaterialTheme.colorScheme.error)
    if (compatibility.genericSteps.isNotEmpty()) {
        Text(stringResource(R.string.w_generic_warning) + "\n" + compatibility.genericSteps.joinToString("\n"))
        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(generic, { generic = it }, enabled = !busy); Text(stringResource(R.string.wb_generic_consent), style = MaterialTheme.typography.bodySmall) }
    }
    val allowed = compatibility.supported && (compatibility.genericSteps.isEmpty() || generic)
    OutlinedButton(onClick = { preview = !preview }, enabled = allowed && !busy) { Text(stringResource(R.string.w_payload_preview)) }
    if (preview) Text(provider.payload(workout, generic).toString(2), style = MaterialTheme.typography.bodySmall)
    if (workout.garminId != null) {
        Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(mode == UploadMode.UPDATE_EXISTING, { mode = UploadMode.UPDATE_EXISTING }, enabled = !busy); Text(stringResource(R.string.wb_update_existing)) }
        Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(mode == UploadMode.CREATE_NEW, { mode = UploadMode.CREATE_NEW }, enabled = !busy); Text(stringResource(R.string.wb_create_new)) }
    }
    if (workout.pendingFingerprint != null) {
        Text(stringResource(R.string.wb_pending), style = MaterialTheme.typography.bodyMedium)
        Button(onClick = {
            val settings = saved ?: return@Button
            scope.launch {
                busy = true
                try {
                    val result = withContext(Dispatchers.IO) { WorkoutSyncCoordinator(repository, BackendWorkoutTransport(settings)).reconcile(workout.id) }
                    message = context.getString(R.string.wb_accepted, result.garminId.orEmpty()); updated()
                } catch (failure: Exception) { error(failure) }
                finally { busy = false }
            }
        }, enabled = saved != null && !busy) { Text(stringResource(R.string.wb_check_result)) }
    }
    Button(onClick = { confirm = true }, enabled = trusted && connected && saved != null && allowed && !busy && workout.pendingFingerprint == null,
        modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.w_send_garmin)) }
    Text(stringResource(R.string.wb_watch_note), style = MaterialTheme.typography.bodySmall)
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text(stringResource(R.string.w_send_garmin)) },
        text = { Text(workout.name + "\n" + saved?.url.orEmpty() + "\n" + context.getString(if (mode == UploadMode.CREATE_NEW) R.string.wb_create_new else R.string.wb_update_existing) + "\n" + context.getString(R.string.wb_confirm)) },
        confirmButton = { TextButton(onClick = {
            confirm = false
            val settings = saved ?: return@TextButton
            scope.launch {
                busy = true; message = context.getString(R.string.w_syncing)
                try {
                    val result = withContext(Dispatchers.IO) { WorkoutSyncCoordinator(repository, BackendWorkoutTransport(settings)).send(workout.id, mode, generic) }
                    message = context.getString(R.string.wb_accepted, result.garminId.orEmpty())
                } catch (failure: Exception) { error(failure) }
                finally { updated(); busy = false }
            }
        }) { Text(stringResource(R.string.w_send_garmin)) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.w_cancel)) } })
    if (disconnectConfirm) AlertDialog(onDismissRequest = { disconnectConfirm = false }, title = { Text(stringResource(R.string.wb_disconnect)) },
        text = { Text(stringResource(R.string.wb_disconnect_note)) },
        confirmButton = { TextButton(onClick = {
            disconnectConfirm = false
            val settings = saved ?: return@TextButton
            scope.launch {
                busy = true
                try {
                    withContext(Dispatchers.IO) { BackendWorkoutTransport(settings).disconnect() }
                    store.clear(); saved = null; token = ""; connected = false; message = context.getString(R.string.wb_disconnected)
                } catch (failure: Exception) { error(failure) }
                finally { busy = false }
            }
        }) { Text(stringResource(R.string.wb_disconnect)) } },
        dismissButton = { TextButton(onClick = { disconnectConfirm = false }) { Text(stringResource(R.string.w_cancel)) } })
}
