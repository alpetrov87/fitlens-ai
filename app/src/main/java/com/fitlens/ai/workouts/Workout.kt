package com.fitlens.ai.workouts

import java.util.UUID

enum class WorkoutSport { STRENGTH, RUNNING, CYCLING, SWIMMING, CARDIO, OTHER }
enum class StepKind { WARMUP, EXERCISE, REST, RECOVERY, COOLDOWN, REPEAT }
enum class DurationKind { TIME, REPS, DISTANCE, LAP }
enum class WorkoutSyncStatus { LOCAL_ONLY, READY_TO_SYNC, SYNCING, SYNCED_TO_GARMIN, SYNC_FAILED, UNSUPPORTED }
enum class TargetKind { HEART_RATE, PACE, POWER }
data class WorkoutTarget(val kind: TargetKind, val minimum: Double, val maximum: Double)
data class WorkoutStep(
    val id: String = UUID.randomUUID().toString(),
    val kind: StepKind = StepKind.EXERCISE,
    val name: String = "",
    val exerciseKey: String = "custom",
    val instructions: String = "",
    val durationKind: DurationKind = DurationKind.TIME,
    val value: Int = 40,
    val restSeconds: Int = 0,
    val iterations: Int = 2,
    val children: List<WorkoutStep> = emptyList(),
    val target: WorkoutTarget? = null,
)
data class Workout(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val sport: WorkoutSport = WorkoutSport.STRENGTH,
    val equipment: String = "",
    val steps: List<WorkoutStep>,
    val isTemplate: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = createdAt,
    val garminId: String? = null,
    val syncStatus: WorkoutSyncStatus = WorkoutSyncStatus.LOCAL_ONLY,
    val lastSyncAttempt: Long? = null,
    val error: String? = null,
    val uploadedFingerprint: String? = null,
    val pendingFingerprint: String? = null,
)
data class WorkoutDuration(val seconds: Long, val exact: Boolean)
fun WorkoutStep.duration(): WorkoutDuration {
    if (kind == StepKind.REPEAT) {
        val values = children.map { it.duration() }
        return WorkoutDuration(values.sumOf { it.seconds } * iterations, values.all { it.exact })
    }
    return WorkoutDuration((if (durationKind == DurationKind.TIME) value.toLong() else 0L) + restSeconds, durationKind == DurationKind.TIME)
}
fun Workout.duration() = steps.map { it.duration() }.let { list -> WorkoutDuration(list.sumOf { it.seconds }, list.all { it.exact }) }
fun Workout.exerciseCount(): Int = steps.sumOf { it.exerciseCount() }
private fun WorkoutStep.exerciseCount(): Int = if (kind == StepKind.REPEAT) children.sumOf { it.exerciseCount() } else if (kind == StepKind.EXERCISE) 1 else 0
fun Workout.rounds(): Int = steps.filter { it.kind == StepKind.REPEAT }.sumOf { it.iterations }

/** Stable machine codes; Android maps them to localized feedback. */
fun Workout.validate(): List<String> = buildList {
    if (runCatching { UUID.fromString(id) }.isFailure) add("id")
    if (name.isBlank() || name.length > 120) add("name")
    if (description.length > 4000 || equipment.length > 1000) add("text")
    if (createdAt <= 0 || modifiedAt < createdAt) add("timestamp")
    if (steps.isEmpty()) add("steps")
    val ids = mutableSetOf<String>()
    var count = 0
    fun check(step: WorkoutStep, depth: Int) {
        count++
        if (!ids.add(step.id) || runCatching { UUID.fromString(step.id) }.isFailure) add("step_id")
        if (step.name.isBlank() || step.name.length > 120 || step.instructions.length > 2000 || step.exerciseKey.length > 120) add("step_name")
        if (depth > 3) { add("depth"); return }
        if (step.kind == StepKind.REPEAT) {
            if (step.iterations !in 1..50 || step.children.isEmpty() || step.restSeconds != 0) add("repeat")
            step.children.forEach { check(it, depth + 1) }
        } else {
            if (step.children.isNotEmpty() || step.value !in 1..100000 || step.restSeconds !in 0..3600) add("value")
            if (step.durationKind == DurationKind.TIME && step.value > 3600) add("time")
            if (step.durationKind == DurationKind.REPS && step.value > 1000) add("reps")
        }
        step.target?.let { if (!it.minimum.isFinite() || !it.maximum.isFinite() || it.minimum < 0 || it.maximum < it.minimum) add("target") }
    }
    steps.forEach { check(it, 0) }
    if (count > 200) add("step_limit")
    if (isEmpty() && duration().seconds > 86400) add("duration_limit")
}.distinct()

fun Workout.duplicate(newName: String = "$name (copy)"): Workout {
    fun copy(step: WorkoutStep): WorkoutStep = step.copy(id = UUID.randomUUID().toString(), children = step.children.map(::copy))
    return Workout(name = newName.take(120), description = description, sport = sport, equipment = equipment, steps = steps.map(::copy))
}
