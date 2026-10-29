package com.fitlens.ai.workouts

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GarminWorkoutTest {
    private val provider = GarminWorkoutProvider()
    private fun sample() = WorkoutTemplates.all().first()
    private class FakeTransport : WorkoutUploadTransport {
        var state = WorkoutConnection.CONNECTED
        var calls = 0
        var failure: Exception? = null
        var response = JSONObject("{\"workoutId\":123}")
        override fun authentication() = state
        override fun create(payload: JSONObject, requestKey: String): JSONObject { calls++; failure?.let { throw it }; return response }
        override fun update(id: String, payload: JSONObject, requestKey: String) = create(payload, requestKey)
    }
    @Test fun bothTemplatePayloadsAreSavedForIndependentSchemaInspection() {
        val dir = java.io.File("build/reports/workout-payloads").apply { mkdirs() }
        WorkoutTemplates.all().forEachIndexed { index, workout ->
            java.io.File(dir, "strength-${index + 1}.json").writeText(provider.payload(workout, true).toString(2))
        }
    }
    @Test fun strengthPayloadHasWarmupsTimedWorkRestAndCorrectRepeat() {
        val payload = provider.payload(sample(), true)
        assertEquals(5, payload.getJSONObject("sportType").getInt("sportTypeId"))
        assertEquals(840, payload.getInt("estimatedDurationInSecs"))
        val steps = payload.getJSONArray("workoutSegments").getJSONObject(0).getJSONArray("workoutSteps")
        assertEquals(5, steps.length())
        for (i in 0..3) { assertEquals(1, steps.getJSONObject(i).getJSONObject("stepType").getInt("stepTypeId")); assertEquals(30, steps.getJSONObject(i).getInt("endConditionValue")) }
        val repeat = steps.getJSONObject(4)
        assertEquals(2, repeat.getInt("numberOfIterations")); assertFalse(repeat.getBoolean("skipLastRestStep"))
        val children = repeat.getJSONArray("workoutSteps")
        assertEquals(12, children.length())
        assertEquals("AIR_SQUAT", children.getJSONObject(0).getString("exerciseName"))
        assertEquals(40, children.getJSONObject(0).getInt("endConditionValue"))
        assertEquals(20, children.getJSONObject(1).getInt("endConditionValue"))
        assertEquals(5, children.getJSONObject(1).getJSONObject("stepType").getInt("stepTypeId"))
        assertEquals(17, children.getJSONObject(11).getInt("stepOrder"))
    }
    @Test fun unknownMappingNeedsConsentAndDoesNotSilentlySelectAnotherExercise() {
        assertTrue(provider.compatibility(sample()).genericSteps.contains("Reverse Lunge"))
        assertTrue(runCatching { provider.payload(sample(), false) }.isFailure)
        val children = provider.payload(sample(), true).getJSONArray("workoutSegments").getJSONObject(0).getJSONArray("workoutSteps").getJSONObject(4).getJSONArray("workoutSteps")
        assertFalse(children.getJSONObject(6).has("exerciseName"))
        assertTrue(children.getJSONObject(6).getString("description").contains("Reverse Lunge"))
        assertEquals("ROW", GarminWorkoutMapping.exact["band_row"]!!.name)
    }
    @Test fun otherSportsTargetsAndNestedRepeatsAreNotClaimedSupported() {
        assertFalse(provider.compatibility(sample().copy(sport = WorkoutSport.SWIMMING)).supported)
        val nested = sample().steps.last().copy(children = listOf(sample().steps.last()))
        assertFalse(provider.compatibility(sample().copy(steps = listOf(nested))).supported)
        assertFalse(provider.compatibility(sample().copy(steps = listOf(WorkoutTemplates.step("squat").copy(target = WorkoutTarget(TargetKind.HEART_RATE, 100.0, 120.0))))).supported)
        assertEquals(WorkoutConnection.SETUP_REQUIRED, provider.connect())
    }
    @Test fun mockedSuccessStoresRealResponseIdAndRepeatedTapDoesNotUploadAgain() {
        val repository = WorkoutRepository(MemoryWorkoutStorage()); val w = sample(); repository.save(w)
        val transport = FakeTransport(); val coordinator = WorkoutSyncCoordinator(repository, transport)
        assertEquals("123", coordinator.send(w.id, UploadMode.CREATE_NEW, true).garminId)
        coordinator.send(w.id, UploadMode.CREATE_NEW, true)
        assertEquals(1, transport.calls)
    }
    @Test fun authAndExpiredSessionsNeverStartUpload() {
        for (state in listOf(WorkoutConnection.SETUP_REQUIRED, WorkoutConnection.EXPIRED)) {
            val repository = WorkoutRepository(MemoryWorkoutStorage()); val w = sample(); repository.save(w)
            val transport = FakeTransport().apply { this.state = state }
            assertTrue(runCatching { WorkoutSyncCoordinator(repository, transport).send(w.id, UploadMode.CREATE_NEW, true) }.isFailure)
            assertEquals(0, transport.calls)
            assertEquals(WorkoutSyncStatus.SYNC_FAILED, repository.all().single().syncStatus)
            assertNotNull(repository.all().single().lastSyncAttempt)
        }
    }
    @Test fun networkOrInvalidResponseKeepsUncertainMarkerAndBlocksDuplicateRetry() {
        for (failure in listOf<Exception?>(java.io.IOException("offline"), null)) {
            val storage = MemoryWorkoutStorage()
            val repository = WorkoutRepository(storage); val w = sample(); repository.save(w)
            val transport = FakeTransport().apply { this.failure = failure; response = JSONObject("{}") }
            val coordinator = WorkoutSyncCoordinator(repository, transport)
            assertTrue(runCatching { coordinator.send(w.id, UploadMode.CREATE_NEW, true) }.isFailure)
            assertNotNull(repository.all().single().pendingFingerprint)
            assertEquals(WorkoutSyncStatus.SYNC_FAILED, repository.all().single().syncStatus)
            assertTrue(runCatching { WorkoutSyncCoordinator(WorkoutRepository(storage), transport).send(w.id, UploadMode.CREATE_NEW, true) }.isFailure)
            assertEquals(1, transport.calls)
        }
    }
    @Test fun rateLimitOrAuthenticationRejectionCanBeRetriedWithoutInventingAnId() {
        for (code in listOf("rate_limit", "authentication", "expired")) {
            val repository = WorkoutRepository(MemoryWorkoutStorage()); val w = sample(); repository.save(w)
            val transport = FakeTransport().apply { failure = WorkoutRemoteFailure(code) }
            assertTrue(runCatching { WorkoutSyncCoordinator(repository, transport).send(w.id, UploadMode.CREATE_NEW, true) }.isFailure)
            val stored = repository.all().single(); assertNull(stored.garminId); assertNull(stored.pendingFingerprint); assertEquals(code, stored.error)
        }
    }
    @Test fun editingUploadedWorkoutAllowsExplicitUpdateAndNoAutomaticUpload() {
        val repository = WorkoutRepository(MemoryWorkoutStorage()); val w = sample(); repository.save(w)
        val transport = FakeTransport(); val coordinator = WorkoutSyncCoordinator(repository, transport)
        val uploaded = coordinator.send(w.id, UploadMode.CREATE_NEW, true)
        repository.save(uploaded.copy(name = "Changed", syncStatus = WorkoutSyncStatus.READY_TO_SYNC))
        assertEquals(1, transport.calls)
        assertEquals("123", coordinator.send(w.id, UploadMode.UPDATE_EXISTING, true).garminId)
        assertEquals(2, transport.calls)
    }
}

