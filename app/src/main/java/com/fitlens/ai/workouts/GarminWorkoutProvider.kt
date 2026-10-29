package com.fitlens.ai.workouts

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

interface WorkoutSyncProvider {
    fun authentication(): WorkoutConnection
    fun connect(): WorkoutConnection
    fun disconnect()
    fun compatibility(workout: Workout): WorkoutCompatibility
    fun payload(workout: Workout, allowGeneric: Boolean): JSONObject
}
enum class WorkoutConnection { SETUP_REQUIRED, CONNECTED, EXPIRED }
data class WorkoutCompatibility(val supported: Boolean, val genericSteps: List<String>, val problems: List<String>)
data class GarminExercise(val category: String, val name: String)

/** Catalog identifiers verified in python-garminconnect 3eee7a4; no unrelated replacements. */
object GarminWorkoutMapping {
    val exact = mapOf(
        "squat" to GarminExercise("SQUAT", "AIR_SQUAT"),
        "incline_pushup" to GarminExercise("PUSH_UP", "INCLINE_PUSH_UP"),
        "band_row" to GarminExercise("BANDED_EXERCISES", "ROW"),
        "calf_raise" to GarminExercise("CALF_RAISE", "CALF_RAISE"),
        "band_pull_apart" to GarminExercise("BANDED_EXERCISES", "PULL_APART"),
        "band_chest_press" to GarminExercise("PUSH_UP", "CHEST_PRESS_WITH_BAND"),
        "standing_hip_abduction" to GarminExercise("HIP_STABILITY", "STANDING_HIP_ABDUCTION"),
        "arm_circles" to GarminExercise("WARM_UP", "ARM_CIRCLES"),
        "hip_circles" to GarminExercise("HIP_STABILITY", "HIP_CIRCLES"),
    )
}
class GarminWorkoutProvider : WorkoutSyncProvider {
    override fun authentication() = WorkoutConnection.SETUP_REQUIRED
    override fun connect() = WorkoutConnection.SETUP_REQUIRED
    override fun disconnect() = Unit // No backend session or Garmin credentials have been created.
    override fun compatibility(workout: Workout): WorkoutCompatibility {
        val issues = workout.validate().toMutableList()
        if (workout.sport != WorkoutSport.STRENGTH) issues += "sport"
        val generic = mutableListOf<String>()
        fun check(step: WorkoutStep, depth: Int) {
            if (step.kind == StepKind.REPEAT) {
                if (depth > 0) issues += "nested_repeat"
                step.children.forEach { check(it, depth + 1) }
            } else {
                if (step.durationKind !in listOf(DurationKind.TIME, DurationKind.REPS) || step.target != null) issues += "target"
                if (step.kind !in listOf(StepKind.REST, StepKind.RECOVERY) && step.exerciseKey !in GarminWorkoutMapping.exact) generic += step.name
            }
        }
        workout.steps.forEach { check(it, 0) }
        return WorkoutCompatibility(issues.isEmpty(), generic.distinct(), issues.distinct())
    }
    override fun payload(workout: Workout, allowGeneric: Boolean): JSONObject {
        val compatible = compatibility(workout)
        require(compatible.supported) { "Unsupported workout" }
        require(allowGeneric || compatible.genericSteps.isEmpty()) { "Generic exercise confirmation required" }
        var order = 0
        fun enum(idKey: String, id: Int, keyName: String, key: String) = JSONObject().put(idKey, id).put(keyName, key).put("displayOrder", id)
        fun type(kind: StepKind): JSONObject = when(kind) {
            StepKind.WARMUP -> enum("stepTypeId", 1, "stepTypeKey", "warmup")
            StepKind.COOLDOWN -> enum("stepTypeId", 2, "stepTypeKey", "cooldown")
            StepKind.EXERCISE -> enum("stepTypeId", 3, "stepTypeKey", "interval")
            StepKind.RECOVERY -> enum("stepTypeId", 4, "stepTypeKey", "recovery")
            StepKind.REST -> enum("stepTypeId", 5, "stepTypeKey", "rest")
            StepKind.REPEAT -> enum("stepTypeId", 6, "stepTypeKey", "repeat")
        }
        fun condition(id: Int, key: String) = enum("conditionTypeId", id, "conditionTypeKey", key).put("displayable", id != 7)
        fun steps(list: List<WorkoutStep>): JSONArray {
            val output = JSONArray()
            list.forEach { step ->
                val item = JSONObject().put("stepOrder", ++order).put("stepType", type(step.kind))
                if (step.kind == StepKind.REPEAT) {
                    item.put("type", "RepeatGroupDTO").put("numberOfIterations", step.iterations)
                        .put("endCondition", condition(7, "iterations")).put("endConditionValue", step.iterations)
                        .put("smartRepeat", false).put("skipLastRestStep", false).put("workoutSteps", steps(step.children))
                } else {
                    item.put("type", "ExecutableStepDTO").put("description", "${step.name}: ${step.instructions}".take(2000))
                        .put("endCondition", if (step.durationKind == DurationKind.REPS) condition(10, "reps") else condition(2, "time"))
                        .put("endConditionValue", step.value)
                        .put("targetType", enum("workoutTargetTypeId", 1, "workoutTargetTypeKey", "no.target"))
                    if (step.kind !in listOf(StepKind.REST, StepKind.RECOVERY)) GarminWorkoutMapping.exact[step.exerciseKey]?.let {
                        item.put("category", it.category).put("exerciseName", it.name)
                    }
                }
                output.put(item)
                if (step.restSeconds > 0 && step.kind != StepKind.REPEAT) {
                    output.put(JSONObject().put("type", "ExecutableStepDTO").put("stepOrder", ++order).put("stepType", type(StepKind.REST))
                        .put("endCondition", condition(2, "time")).put("endConditionValue", step.restSeconds)
                        .put("targetType", enum("workoutTargetTypeId", 1, "workoutTargetTypeKey", "no.target")))
                }
            }
            return output
        }
        val sport = JSONObject().put("sportTypeId", 5).put("sportTypeKey", "strength_training").put("displayOrder", 5)
        return JSONObject().put("workoutName", workout.name).put("description", workout.description)
            .put("sportType", sport).put("estimatedDurationInSecs", if (workout.duration().exact) workout.duration().seconds else 0L)
            .put("workoutSegments", JSONArray().put(JSONObject().put("segmentOrder", 1).put("sportType", sport).put("workoutSteps", steps(workout.steps))))
    }
}

/** Boundary for a future authenticated trusted backend. No username/password transport on Android. */
interface WorkoutUploadTransport {
    fun authentication(): WorkoutConnection
    fun create(payload: JSONObject, requestKey: String): JSONObject
    fun update(id: String, payload: JSONObject, requestKey: String): JSONObject
}
class WorkoutRemoteFailure(val code: String) : Exception(code)
enum class UploadMode { CREATE_NEW, UPDATE_EXISTING }
class WorkoutSyncCoordinator(private val repository: WorkoutRepository, private val transport: WorkoutUploadTransport) {
    private companion object { val uploadLock = Any() }
    private val provider = GarminWorkoutProvider()
    fun send(id: String, mode: UploadMode, allowGeneric: Boolean): Workout = synchronized(uploadLock) {
        val w = repository.all().first { it.id == id }
        val payload = provider.payload(w, allowGeneric)
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(WorkoutJson.encode(w).toByteArray()).joinToString("") { "%02x".format(it) }
        if (w.uploadedFingerprint == fingerprint && w.garminId != null) return w
        check(w.pendingFingerprint == null) { "Uncertain previous upload; verify Garmin before retrying" }
        val connection = transport.authentication()
        if (connection != WorkoutConnection.CONNECTED) {
            val code = if (connection == WorkoutConnection.EXPIRED) "expired" else "authentication"
            repository.save(w.copy(syncStatus = WorkoutSyncStatus.SYNC_FAILED, lastSyncAttempt = System.currentTimeMillis(), error = code))
            throw WorkoutRemoteFailure(code)
        }
        if (mode == UploadMode.UPDATE_EXISTING) require(w.garminId != null)
        val pending = w.copy(syncStatus = WorkoutSyncStatus.SYNCING, pendingFingerprint = fingerprint, lastSyncAttempt = System.currentTimeMillis(), error = null)
        repository.save(pending)
        try {
            val response = if (mode == UploadMode.UPDATE_EXISTING) transport.update(w.garminId!!, payload.put("workoutId", w.garminId.toLong()), fingerprint) else transport.create(payload, fingerprint)
            val remoteId = response.opt("workoutId")?.toString()?.takeIf { it.matches(Regex("[1-9][0-9]{0,17}")) } ?: throw WorkoutRemoteFailure("invalid_response")
            if (mode == UploadMode.UPDATE_EXISTING && remoteId != w.garminId) throw WorkoutRemoteFailure("invalid_response")
            return pending.copy(garminId = remoteId, uploadedFingerprint = fingerprint, pendingFingerprint = null, syncStatus = WorkoutSyncStatus.SYNCED_TO_GARMIN).also(repository::save)
        } catch (failure: Exception) {
            val code = (failure as? WorkoutRemoteFailure)?.code ?: "network"
            val certainRejection = code in setOf("authentication", "expired", "rate_limit", "validation")
            repository.save(pending.copy(syncStatus = WorkoutSyncStatus.SYNC_FAILED, error = code, pendingFingerprint = if (certainRejection) null else fingerprint))
            throw WorkoutRemoteFailure(code)
        }
    }
}
