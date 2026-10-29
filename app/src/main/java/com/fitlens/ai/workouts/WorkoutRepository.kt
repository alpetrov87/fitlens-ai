package com.fitlens.ai.workouts

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

interface WorkoutStorage { fun read(): String?; fun write(value: String): Boolean }
class PreferencesWorkoutStorage(context: Context) : WorkoutStorage {
    private val prefs = context.getSharedPreferences("bair_workouts", Context.MODE_PRIVATE)
    override fun read(): String? = prefs.getString("library", null)
    override fun write(value: String): Boolean = prefs.edit().putString("library", value).commit()
}

/** One atomically committed JSON envelope; other application stores are untouched. */
class WorkoutRepository(private val storage: WorkoutStorage) {
    private data class Library(val workouts: MutableList<Workout>, val seeded: MutableSet<String>)
    private fun load(): Library {
        val raw = storage.read() ?: return Library(mutableListOf(), mutableSetOf())
        require(raw.length <= 8_000_000) { "Workout storage too large" }
        val root = JSONObject(raw)
        val version = root.getInt("storageVersion")
        require(root.getDouble("storageVersion") == version.toDouble() && version in 0..1) { "Unsupported workout storage version; original data preserved" }
        val seeded = root.optJSONArray("seeded") ?: JSONArray()
        val records = root.getJSONArray("workouts")
        val workouts = (0 until records.length()).map { i ->
            val record = records.getJSONObject(i)
            if (version == 0) WorkoutJson.decode(record.toString()) else {
                val w = WorkoutJson.decode(record.getJSONObject("workout").toString())
                val state = WorkoutSyncStatus.valueOf(record.optString("status", "LOCAL_ONLY"))
                w.copy(garminId = record.optString("garminId").takeIf { it.isNotBlank() },
                    syncStatus = if (state == WorkoutSyncStatus.SYNCING) WorkoutSyncStatus.SYNC_FAILED else state,
                    lastSyncAttempt = record.optLong("lastAttempt", 0).takeIf { it > 0 }, error = record.optString("error").takeIf { it.isNotBlank() },
                    uploadedFingerprint = record.optString("uploaded").takeIf { it.isNotBlank() },
                    pendingFingerprint = record.optString("pending").takeIf { it.isNotBlank() },
                    uploadedIdentity = record.optString("uploadedIdentity").takeIf { it.isNotBlank() },
                    pendingIdentity = record.optString("pendingIdentity").takeIf { it.isNotBlank() })
            }
        }
        require(workouts.map { it.id }.distinct().size == workouts.size)
        return Library(workouts.toMutableList(), (0 until seeded.length()).map { seeded.getString(it) }.toMutableSet())
    }
    private fun persist(library: Library) {
        val records = library.workouts.map { w ->
            JSONObject().put("workout", JSONObject(WorkoutJson.encode(w))).put("status", w.syncStatus.name)
                .put("garminId", w.garminId).put("lastAttempt", w.lastSyncAttempt).put("error", w.error)
                .put("uploaded", w.uploadedFingerprint).put("pending", w.pendingFingerprint)
                .put("uploadedIdentity", w.uploadedIdentity).put("pendingIdentity", w.pendingIdentity)
        }
        val encoded = JSONObject().put("storageVersion", 1).put("seeded", JSONArray(library.seeded.toList())).put("workouts", JSONArray(records)).toString()
        require(encoded.length <= 8_000_000) { "Workout storage full" }
        check(storage.write(encoded)) { "Cannot save workouts" }
    }
    fun seedTemplates() = synchronized(lock) {
        val library = load()
        WorkoutTemplates.all().forEach { template ->
            if (template.id !in library.seeded) {
                if (library.workouts.none { it.id == template.id }) library.workouts.add(template)
                library.seeded.add(template.id)
            }
        }
        persist(library)
    }
    fun all(): List<Workout> = synchronized(lock) { load().workouts.toList() }
    fun save(workout: Workout) = synchronized(lock) {
        require(workout.validate().isEmpty()) { "Invalid workout" }
        val library = load()
        val index = library.workouts.indexOfFirst { it.id == workout.id }
        if (index < 0) { require(library.workouts.size < 200); library.workouts.add(workout) } else library.workouts[index] = workout
        persist(library)
    }
    fun modify(id: String, transform: (Workout) -> Workout): Workout? = synchronized(lock) {
        val library = load()
        val index = library.workouts.indexOfFirst { it.id == id }
        if (index < 0) return null
        val changed = transform(library.workouts[index])
        require(changed.id == id && changed.validate().isEmpty())
        library.workouts[index] = changed
        persist(library)
        changed
    }
    fun saveEdited(workout: Workout, expectedModifiedAt: Long) = synchronized(lock) {
        val existing = load().workouts.firstOrNull { it.id == workout.id }
        if (existing == null) save(workout) else {
            require(existing.modifiedAt == expectedModifiedAt) { "Workout changed during editing" }
            save(workout.copy(garminId = existing.garminId, uploadedFingerprint = existing.uploadedFingerprint,
                uploadedIdentity = existing.uploadedIdentity, pendingFingerprint = existing.pendingFingerprint,
                pendingIdentity = existing.pendingIdentity, lastSyncAttempt = existing.lastSyncAttempt,
                syncStatus = if (existing.pendingFingerprint != null) existing.syncStatus else if (existing.garminId != null) WorkoutSyncStatus.READY_TO_SYNC else WorkoutSyncStatus.LOCAL_ONLY))
        }
    }
    fun delete(id: String) = synchronized(lock) { val library = load(); library.workouts.removeAll { it.id == id }; persist(library) }
    fun duplicate(id: String): Workout = synchronized(lock) { all().first { it.id == id }.duplicate().also(::save) }
    fun importJson(raw: String): Workout = WorkoutJson.decode(raw).let { it.duplicate(it.name) }.also(::save)
    companion object { private val lock = Any() }
}
