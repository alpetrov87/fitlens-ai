package com.fitlens.ai.workouts

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject

internal class MemoryWorkoutStorage : WorkoutStorage {
    var raw: String? = null
    var fail = false
    override fun read() = raw
    override fun write(value: String): Boolean { if (fail) return false; raw = value; return true }
}
class WorkoutTest {
    private fun sample() = WorkoutTemplates.all().first()
    @Test fun bothTemplatesAreExactlyFourteenMinutesAndHaveFourDistinctWarmups() {
        WorkoutTemplates.all().forEach { w ->
            assertTrue(w.validate().isEmpty())
            assertEquals(WorkoutDuration(840, true), w.duration())
            assertEquals(listOf("march", "arm_circles", "hip_circles", "shallow_squat"), w.steps.take(4).map { it.exerciseKey })
            assertEquals(listOf(30,30,30,30), w.steps.take(4).map { it.value })
            assertEquals(2, w.steps.last().iterations)
            assertEquals(6, w.exerciseCount())
            assertTrue(w.steps.last().children.all { it.value == 40 && it.restSeconds == 20 })
            assertEquals(720L, w.steps.last().duration().seconds)
            assertTrue(w.description.contains("болка"))
        }
    }
    @Test fun exerciseOrderMatchesBothPrescribedRoutines() {
        val templates = WorkoutTemplates.all()
        assertEquals(listOf("squat","incline_pushup","band_row","reverse_lunge","calf_raise","band_pull_apart"), templates[0].steps.last().children.map { it.exerciseKey })
        assertEquals(listOf("sit_to_stand","band_chest_press","band_row","glute_bridge","standing_hip_abduction","calf_raise"), templates[1].steps.last().children.map { it.exerciseKey })
    }
    @Test fun editingTimingAndRepeatRecalculatesWithoutLosingRest() {
        val w = sample(); val group = w.steps.last(); val steps = group.children.toMutableList()
        steps[0] = steps[0].copy(value = 50)
        val edited = w.copy(steps = w.steps.dropLast(1) + group.copy(iterations = 3, children = steps))
        assertEquals(1230L, edited.duration().seconds)
        assertEquals(840L, w.duration().seconds)
    }
    @Test fun repStepsDoNotInventDuration() {
        assertFalse(sample().copy(steps = listOf(WorkoutTemplates.step("squat").copy(durationKind = DurationKind.REPS))).duration().exact)
    }
    @Test fun invalidNamesDurationsRepeatsIdsAndTargetsAreRejected() {
        assertTrue(sample().copy(name = "").validate().isNotEmpty())
        assertTrue(sample().copy(steps = listOf(WorkoutTemplates.step("squat").copy(value = -1))).validate().isNotEmpty())
        assertTrue(sample().copy(steps = listOf(sample().steps.last().copy(iterations = 0))).validate().isNotEmpty())
        val repeated = sample().steps[0]
        assertTrue(sample().copy(steps = listOf(repeated, repeated)).validate().isNotEmpty())
        assertTrue(sample().copy(steps = listOf(WorkoutTemplates.step("squat").copy(target = WorkoutTarget(TargetKind.POWER, 200.0, 100.0)))).validate().isNotEmpty())
    }
    @Test fun duplicatesHaveIndependentWorkoutAndStepIdsAndNoRemoteState() {
        val original = sample().copy(garminId = "9", syncStatus = WorkoutSyncStatus.SYNCED_TO_GARMIN)
        val copy = original.duplicate()
        assertNotEquals(original.id, copy.id)
        assertNotEquals(original.steps[0].id, copy.steps[0].id)
        assertNotEquals(original.steps.last().children[0].id, copy.steps.last().children[0].id)
        assertNull(copy.garminId); assertFalse(copy.isTemplate)
        assertEquals(original.duration(), copy.duration())
    }
    @Test fun jsonRoundTripPreservesOrderedGroupsAndInstructionsButNeverRemoteMetadata() {
        val w = sample()
        assertEquals(w, WorkoutJson.decode(WorkoutJson.encode(w)))
        val exported = WorkoutJson.encode(w.copy(garminId = "99", error = "secret-session", pendingFingerprint = "pending"))
        assertFalse(exported.contains("secret-session")); assertFalse(exported.contains("garminId"))
    }
    @Test fun rejectsMalformedFutureVersionAndDeepUntrustedJson() {
        listOf("garbage", WorkoutJson.encode(sample()).replace("\"schemaVersion\": 1", "\"schemaVersion\": 999"), WorkoutJson.encode(sample()).replace("\"schemaVersion\": 1", "\"schemaVersion\": 1.5"), "[".repeat(100) + "]".repeat(100)).forEach {
            assertTrue(runCatching { WorkoutJson.decode(it) }.isFailure)
        }
    }
    @Test fun seedingNeverOverwritesEditsOrRecreatesDeletedTemplate() {
        val storage = MemoryWorkoutStorage(); val repo = WorkoutRepository(storage)
        repo.seedTemplates(); val first = repo.all().first()
        repo.save(first.copy(name = "My edited workout")); repo.seedTemplates()
        assertEquals("My edited workout", repo.all().first().name)
        repo.delete(first.id); repo.seedTemplates()
        assertEquals(1, repo.all().size)
        assertEquals(1, WorkoutRepository(storage).all().size)
    }
    @Test fun createUpdateDeleteAndImportSurviveRepositoryRestart() {
        val storage = MemoryWorkoutStorage(); val repo = WorkoutRepository(storage)
        repo.save(sample()); val changed = repo.all().first().copy(name = "Edited")
        repo.save(changed)
        assertEquals(changed, WorkoutRepository(storage).all().single())
        val restored = repo.importJson(WorkoutJson.encode(changed))
        assertNotEquals(changed.id, restored.id)
        repo.delete(changed.id)
        assertEquals(restored, WorkoutRepository(storage).all().single())
    }
    @Test fun versionZeroMigrationPreservesWorkoutsAndBecomesVersionOneOnWrite() {
        val storage = MemoryWorkoutStorage().apply { raw = JSONObject().put("storageVersion", 0).put("workouts", JSONArray().put(JSONObject(WorkoutJson.encode(sample())))).toString() }
        val repo = WorkoutRepository(storage); val original = repo.all().single()
        repo.seedTemplates()
        assertEquals(original, repo.all().first { it.id == original.id })
        assertEquals(1, JSONObject(storage.raw!!).getInt("storageVersion"))
    }
    @Test fun unsupportedStoreVersionAndFailedWritesPreserveOriginalBytes() {
        val storage = MemoryWorkoutStorage().apply { raw = "{\"storageVersion\":99,\"workouts\":[]}" }
        val raw = storage.raw
        assertTrue(runCatching { WorkoutRepository(storage).seedTemplates() }.isFailure)
        assertEquals(raw, storage.raw)
        storage.raw = null; storage.fail = true
        assertTrue(runCatching { WorkoutRepository(storage).save(sample()) }.isFailure)
        assertNull(storage.raw)
    }
}
