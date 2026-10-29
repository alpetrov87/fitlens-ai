package com.fitlens.ai.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.fitlens.ai.R
import com.fitlens.ai.workouts.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun WorkoutsScreen() {
    val context = LocalContext.current
    val repository = remember { WorkoutRepository(PreferencesWorkoutStorage(context)) }
    val scope = rememberCoroutineScope()
    var library by remember { mutableStateOf<List<Workout>>(emptyList()) }
    var message by remember { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Workout?>(null) }
    var delete by remember { mutableStateOf<Workout?>(null) }
    var integration by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var sport by remember { mutableStateOf<WorkoutSport?>(null) }
    fun refresh() { library = repository.all() }
    fun action(block: () -> Unit) { runCatching { block(); refresh() }.onFailure { message = context.getString(R.string.w_operation_failed) } }
    LaunchedEffect(Unit) { action { repository.seedTemplates() } }
    val current = library.firstOrNull { it.id == selected }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null && current != null) scope.launch {
            val ok = runCatching { withContext(Dispatchers.IO) { checkNotNull(context.contentResolver.openOutputStream(uri)).use { it.write(WorkoutJson.encode(current).toByteArray()) } } }.isSuccess
            message = context.getString(if (ok) R.string.w_exported else R.string.w_operation_failed)
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val bytes = checkNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                        val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                        while (output.size() <= 500_000) { val n = input.read(buffer, 0, minOf(buffer.size, 500_001 - output.size())); if (n < 0) break; output.write(buffer, 0, n) }
                        output.toByteArray()
                    }
                    require(bytes.size <= 500_000)
                    repository.importJson(bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF"))
                }
            }.onSuccess { refresh(); selected = it.id; message = context.getString(R.string.w_imported) }
                .onFailure { message = context.getString(R.string.w_import_failed) }
        }
    }
    fun share(workout: Workout) {
        action {
            val dir = File(context.cacheDir, "workouts").apply { mkdirs() }
            dir.listFiles()?.filter { it.lastModified() < System.currentTimeMillis() - 86400000 }?.forEach { it.delete() }
            val file = File(dir, "${workout.id}.json").apply { writeText(WorkoutJson.encode(workout)) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.workouts", file)
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/json")
                .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), context.getString(R.string.w_share)))
        }
    }
    BackHandler(enabled = editing != null || selected != null || integration) {
        if (editing != null) discard = true else if (integration) integration = false else selected = null
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (message.isNotBlank()) {
            Text(message, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { message = "" }) { Text(stringResource(R.string.w_dismiss)) }
        }
        val draft = editing
        if (draft != null) {
            Text(stringResource(R.string.w_editor), style = MaterialTheme.typography.headlineSmall)
            WorkoutEditor(draft, { editing = it })
            Text(durationText(draft), style = MaterialTheme.typography.titleMedium)
            if (draft.validate().isNotEmpty()) Text(stringResource(R.string.w_validation), color = MaterialTheme.colorScheme.error)
            Button(onClick = { action {
                val now = maxOf(System.currentTimeMillis(), draft.createdAt)
                val updated = draft.copy(modifiedAt = now, syncStatus = if (draft.garminId != null) WorkoutSyncStatus.READY_TO_SYNC else WorkoutSyncStatus.LOCAL_ONLY, error = null)
                repository.saveEdited(updated, draft.modifiedAt); selected = updated.id; editing = null
            } }, enabled = draft.validate().isEmpty()) { Text(stringResource(R.string.w_save)) }
            TextButton(onClick = { discard = true }) { Text(stringResource(R.string.w_cancel)) }
        } else if (integration && current != null) {
            GarminWorkoutScreen(current, repository, { refresh() }, { integration = false })
        } else if (current != null) {
            TextButton(onClick = { selected = null }) { Text(stringResource(R.string.w_back)) }
            Text(current.name, style = MaterialTheme.typography.headlineSmall)
            Text(sportLabel(current.sport) + " · " + durationText(current))
            Text(current.description)
            if (current.equipment.isNotBlank()) Text(stringResource(R.string.w_equipment) + ": " + current.equipment)
            Text(statusLabel(current.syncStatus), color = MaterialTheme.colorScheme.primary)
            if (current.garminId != null) Text("Garmin ID: ${current.garminId}")
            current.error?.let { Text(stringResource(R.string.w_sync_error), color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { editing = current }) { Text(stringResource(R.string.w_edit)) }
                OutlinedButton(onClick = { action { selected = repository.duplicate(current.id).id } }) { Text(stringResource(R.string.w_duplicate)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { export.launch("${current.id}.json") }) { Text(stringResource(R.string.w_export)) }
                OutlinedButton(onClick = { share(current) }) { Text(stringResource(R.string.w_share)) }
            }
            Button(onClick = { integration = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.w_send_garmin)) }
            current.steps.forEach { WorkoutStepDetails(it) }
            TextButton(onClick = { delete = current }) { Text(stringResource(R.string.w_delete)) }
        } else {
            Text(stringResource(R.string.w_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.w_offline), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { editing = Workout(name = context.getString(R.string.w_new_name), steps = listOf(WorkoutTemplates.step("squat"))) }) { Text(stringResource(R.string.w_create)) }
                OutlinedButton(onClick = { import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) { Text(stringResource(R.string.w_import)) }
            }
            OutlinedTextField(query, { query = it }, label = { Text(stringResource(R.string.w_search)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Choice(stringResource(R.string.w_sport), sport?.let { sportLabel(it) } ?: stringResource(R.string.w_all)) {
                DropdownMenuItem(text = { Text(stringResource(R.string.w_all)) }, onClick = { sport = null; it() })
                WorkoutSport.entries.forEach { type -> DropdownMenuItem(text = { Text(sportLabel(type)) }, onClick = { sport = type; it() }) }
            }
            library.filter { it.name.contains(query, true) && (sport == null || it.sport == sport) }.forEach { workout ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(workout.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.clickable { selected = workout.id })
                        Text(sportLabel(workout.sport) + " · " + durationText(workout), style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.w_counts, workout.exerciseCount(), workout.rounds()), style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(if (workout.isTemplate) R.string.w_template else R.string.w_user_created) + " · " + statusLabel(workout.syncStatus), style = MaterialTheme.typography.labelSmall)
                        Row {
                            TextButton(onClick = { selected = workout.id }) { Text(stringResource(R.string.w_open)) }
                            IconButton(onClick = { action { selected = repository.duplicate(workout.id).id } }) { Icon(Icons.Default.ContentCopy, stringResource(R.string.w_duplicate)) }
                            IconButton(onClick = { delete = workout }) { Icon(Icons.Default.Delete, stringResource(R.string.w_delete)) }
                        }
                    }
                }
            }
        }
    }
    delete?.let { workout -> AlertDialog(onDismissRequest = { delete = null }, title = { Text(stringResource(R.string.w_delete)) }, text = { Text(workout.name) },
        confirmButton = { TextButton(onClick = { action { repository.delete(workout.id); if (selected == workout.id) selected = null }; delete = null }) { Text(stringResource(R.string.w_delete)) } },
        dismissButton = { TextButton(onClick = { delete = null }) { Text(stringResource(R.string.w_cancel)) } }) }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text(stringResource(R.string.w_discard)) },
        confirmButton = { TextButton(onClick = { editing = null; discard = false }) { Text(stringResource(R.string.w_discard)) } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text(stringResource(R.string.w_cancel)) } })
}

@Composable
private fun WorkoutStepDetails(step: WorkoutStep) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(kindLabel(step.kind) + " · " + step.name, style = MaterialTheme.typography.titleSmall)
            if (step.kind == StepKind.REPEAT) {
                Text(stringResource(R.string.w_repeat_count, step.iterations))
                step.children.forEach { WorkoutStepDetails(it) }
            } else {
                Text("${step.value} ${unitLabel(step.durationKind)} · " + stringResource(R.string.w_rest_value, step.restSeconds))
                if (step.instructions.isNotBlank()) Text(step.instructions, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun WorkoutEditor(workout: Workout, change: (Workout) -> Unit) {
    OutlinedTextField(workout.name, { change(workout.copy(name = it.take(120))) }, label = { Text(stringResource(R.string.w_name)) }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(workout.description, { change(workout.copy(description = it.take(4000))) }, label = { Text(stringResource(R.string.w_description)) }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(workout.equipment, { change(workout.copy(equipment = it.take(1000))) }, label = { Text(stringResource(R.string.w_equipment)) }, modifier = Modifier.fillMaxWidth())
    Choice(stringResource(R.string.w_sport), sportLabel(workout.sport)) { close -> WorkoutSport.entries.forEach { sport ->
        DropdownMenuItem(text = { Text(sportLabel(sport)) }, onClick = { change(workout.copy(sport = sport)); close() })
    } }
    StepListEditor(workout.steps, { change(workout.copy(steps = it)) }, 0)
}

@Composable
private fun StepListEditor(steps: List<WorkoutStep>, change: (List<WorkoutStep>) -> Unit, depth: Int) {
    val cooldownName = kindLabel(StepKind.COOLDOWN)
    val repeatName = kindLabel(StepKind.REPEAT)
    steps.forEachIndexed { index, step -> key(step.id) {
        var expanded by rememberSaveable { mutableStateOf(step.kind == StepKind.REPEAT) }
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(step.name, modifier = Modifier.weight(1f).clickable { expanded = !expanded }, style = MaterialTheme.typography.titleSmall)
                    IconButton(onClick = { if (index > 0) change(steps.toMutableList().apply { add(index - 1, removeAt(index)) }) }, enabled = index > 0) { Icon(Icons.Default.ArrowUpward, stringResource(R.string.w_up)) }
                    IconButton(onClick = { if (index < steps.lastIndex) change(steps.toMutableList().apply { add(index + 1, removeAt(index)) }) }, enabled = index < steps.lastIndex) { Icon(Icons.Default.ArrowDownward, stringResource(R.string.w_down)) }
                    IconButton(onClick = { change(steps.filterIndexed { i, _ -> i != index }) }) { Icon(Icons.Default.Delete, stringResource(R.string.w_delete)) }
                }
                TextButton(onClick = { expanded = !expanded }) { Text(stringResource(if (expanded) R.string.w_collapse else R.string.w_edit)) }
                fun update(s: WorkoutStep) { change(steps.toMutableList().apply { set(index, s) }) }
                if (expanded) {
                    OutlinedTextField(step.name, { update(step.copy(name = it.take(120), exerciseKey = "custom")) }, label = { Text(stringResource(R.string.w_name)) }, modifier = Modifier.fillMaxWidth())
                    if (step.kind == StepKind.REPEAT) {
                        NumberField(stringResource(R.string.w_rounds), step.iterations) { update(step.copy(iterations = it)) }
                        StepListEditor(step.children, { update(step.copy(children = it)) }, depth + 1)
                    } else {
                        Choice(stringResource(R.string.w_step_type), kindLabel(step.kind)) { close -> StepKind.entries.filter { it != StepKind.REPEAT }.forEach { kind ->
                            DropdownMenuItem(text = { Text(kindLabel(kind)) }, onClick = { update(step.copy(kind = kind)); close() })
                        } }
                        Choice(stringResource(R.string.w_exercise), step.name) { close -> WorkoutTemplates.exercises.forEach { exercise ->
                            DropdownMenuItem(text = { Text(exercise.name) }, onClick = { update(step.copy(exerciseKey = exercise.key, name = exercise.name, instructions = exercise.instructions)); close() })
                        } }
                        Choice(stringResource(R.string.w_duration_type), unitLabel(step.durationKind)) { close -> DurationKind.entries.forEach { kind ->
                            DropdownMenuItem(text = { Text(unitLabel(kind)) }, onClick = { update(step.copy(durationKind = kind)); close() })
                        } }
                        NumberField(unitLabel(step.durationKind), step.value) { update(step.copy(value = it)) }
                        NumberField(stringResource(R.string.w_rest_seconds), step.restSeconds) { update(step.copy(restSeconds = it)) }
                        OutlinedTextField(step.instructions, { update(step.copy(instructions = it.take(2000))) }, label = { Text(stringResource(R.string.w_instructions)) }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    } }
    TextButton(onClick = { change(steps + WorkoutTemplates.step("squat")) }) { Text(stringResource(R.string.w_add_exercise)) }
    if (depth == 0) {
        Row {
            TextButton(onClick = { change(listOf(WorkoutTemplates.step("march", StepKind.WARMUP, 30, 0)) + steps) }) { Text(stringResource(R.string.w_add_warmup)) }
            TextButton(onClick = { change(steps + WorkoutStep(kind = StepKind.COOLDOWN, name = cooldownName, value = 30)) }) { Text(stringResource(R.string.w_add_cooldown)) }
        }
    }
    if (depth < 2) TextButton(onClick = { change(steps + WorkoutStep(kind = StepKind.REPEAT, name = repeatName, children = listOf(WorkoutTemplates.step("squat")))) }) { Text(stringResource(R.string.w_add_repeat)) }
}

@Composable
private fun NumberField(label: String, value: Int, change: (Int) -> Unit) {
    OutlinedTextField(value.toString(), { change(it.filter(Char::isDigit).take(5).toIntOrNull() ?: 0) }, label = { Text(label) }, singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number), modifier = Modifier.fillMaxWidth())
}
@Composable
private fun Choice(label: String, value: String, content: @Composable (close: () -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box { OutlinedButton(onClick = { open = true }) { Text("$label: $value") }; DropdownMenu(expanded = open, onDismissRequest = { open = false }) { content { open = false } } }
}

@Composable private fun durationText(w: Workout): String {
    val d = w.duration(); val formatted = "%d:%02d".format(d.seconds / 60, d.seconds % 60)
    return if (d.exact) formatted else stringResource(R.string.w_duration_unknown, formatted)
}
@Composable private fun sportLabel(s: WorkoutSport) = stringResource(when(s) { WorkoutSport.STRENGTH -> R.string.w_strength; WorkoutSport.RUNNING -> R.string.w_running; WorkoutSport.CYCLING -> R.string.w_cycling; WorkoutSport.SWIMMING -> R.string.w_swimming; WorkoutSport.CARDIO -> R.string.w_cardio; WorkoutSport.OTHER -> R.string.w_other })
@Composable private fun kindLabel(k: StepKind) = stringResource(when(k) { StepKind.WARMUP -> R.string.w_warmup; StepKind.EXERCISE -> R.string.w_exercise; StepKind.REST -> R.string.w_rest; StepKind.RECOVERY -> R.string.w_recovery; StepKind.COOLDOWN -> R.string.w_cooldown; StepKind.REPEAT -> R.string.w_repeat })
@Composable private fun unitLabel(k: DurationKind) = stringResource(when(k) { DurationKind.TIME -> R.string.w_seconds; DurationKind.REPS -> R.string.w_reps; DurationKind.DISTANCE -> R.string.w_metres; DurationKind.LAP -> R.string.w_lap })
@Composable private fun statusLabel(s: WorkoutSyncStatus) = stringResource(when(s) { WorkoutSyncStatus.LOCAL_ONLY -> R.string.w_local; WorkoutSyncStatus.READY_TO_SYNC -> R.string.w_ready; WorkoutSyncStatus.SYNCING -> R.string.w_syncing; WorkoutSyncStatus.SYNCED_TO_GARMIN -> R.string.w_synced; WorkoutSyncStatus.SYNC_FAILED -> R.string.w_sync_failed; WorkoutSyncStatus.UNSUPPORTED -> R.string.w_unsupported })
