package com.fitlens.ai.workouts

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WorkoutLiveSyncTest {
    private class Transport : WorkoutUploadTransport {
        var owner = "https://synthetic.test#" + "a".repeat(64)
        var calls = 0
        var callback: () -> Unit = {}
        var failure = false
        override fun authentication() = WorkoutConnection.CONNECTED
        override fun identity() = owner
        override fun create(payload: JSONObject, requestKey: String): JSONObject {
            calls++; callback(); if (failure) throw java.io.IOException("synthetic failure")
            return JSONObject().put("workoutId", 123)
        }
        override fun update(id: String, payload: JSONObject, requestKey: String) = create(payload, requestKey)
        override fun lookup(requestKey: String) = JSONObject().put("workoutId", 123)
    }
    @Test fun changingAccountCannotUpdateAnExistingRemoteWorkout() {
        val repo = WorkoutRepository(MemoryWorkoutStorage()); val w = WorkoutTemplates.all().first(); repo.save(w)
        val transport = Transport(); val coordinator = WorkoutSyncCoordinator(repo, transport)
        coordinator.send(w.id, UploadMode.CREATE_NEW, true)
        transport.owner = "https://another.test#" + "b".repeat(64)
        val failure = runCatching { coordinator.send(w.id, UploadMode.UPDATE_EXISTING, true) }.exceptionOrNull()
        assertEquals("account_mismatch", (failure as WorkoutRemoteFailure).code)
        assertEquals(1, transport.calls)
    }
    @Test fun recoveryAcrossRepositoryRestartUsesLookupWithoutAnotherPost() {
        val storage = MemoryWorkoutStorage(); val repo = WorkoutRepository(storage); val w = WorkoutTemplates.all().first(); repo.save(w)
        val transport = Transport().apply { failure = true }
        assertTrue(runCatching { WorkoutSyncCoordinator(repo, transport).send(w.id, UploadMode.CREATE_NEW, true) }.isFailure)
        val restored = WorkoutSyncCoordinator(WorkoutRepository(storage), transport).reconcile(w.id)
        assertEquals("123", restored.garminId); assertNull(restored.pendingFingerprint); assertEquals(1, transport.calls)
    }
    @Test fun editingDuringUploadKeepsNewContentAndMarksItAsChanged() {
        val repo = WorkoutRepository(MemoryWorkoutStorage()); val w = WorkoutTemplates.all().first(); repo.save(w)
        val transport = Transport().apply { callback = {
            val live = repo.all().single()
            repo.saveEdited(live.copy(name = "Edited during upload", modifiedAt = live.modifiedAt + 1), live.modifiedAt)
        } }
        WorkoutSyncCoordinator(repo, transport).send(w.id, UploadMode.CREATE_NEW, true)
        val current = repo.all().single()
        assertEquals("Edited during upload", current.name)
        assertEquals(WorkoutSyncStatus.READY_TO_SYNC, current.syncStatus)
        assertEquals("123", current.garminId)
    }
    @Test fun deletingDuringUploadDoesNotResurrectLocalWorkout() {
        val repo = WorkoutRepository(MemoryWorkoutStorage()); val w = WorkoutTemplates.all().first(); repo.save(w)
        val transport = Transport().apply { callback = { repo.delete(w.id) } }
        WorkoutSyncCoordinator(repo, transport).send(w.id, UploadMode.CREATE_NEW, true)
        assertTrue(repo.all().isEmpty())
    }
    @Test fun exportedFilesNeverIncludeBackendAccountBindingOrPendingKeys() {
        val w = WorkoutTemplates.all().first().copy(uploadedIdentity = "private-account", pendingIdentity = "pending-account", pendingFingerprint = "pending-key")
        val json = WorkoutJson.encode(w)
        assertFalse(json.contains("private-account")); assertFalse(json.contains("pending-account")); assertFalse(json.contains("pending-key"))
    }
}
