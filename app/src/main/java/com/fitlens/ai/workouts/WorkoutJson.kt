package com.fitlens.ai.workouts

import org.json.JSONArray
import org.json.JSONObject

/** Portable public format. Synchronization/session metadata is never exported. */
object WorkoutJson {
    const val VERSION = 1
    fun encode(workout: Workout): String {
        require(workout.validate().isEmpty()) { "Invalid workout" }
        return JSONObject().put("schemaVersion", VERSION).put("id", workout.id).put("name", workout.name)
            .put("description", workout.description).put("sport", workout.sport.name).put("equipment", workout.equipment)
            .put("isTemplate", workout.isTemplate).put("createdAt", workout.createdAt).put("modifiedAt", workout.modifiedAt)
            .put("steps", JSONArray(workout.steps.map(::stepJson))).toString(2)
    }
    private fun stepJson(s: WorkoutStep): JSONObject = JSONObject().put("id", s.id).put("kind", s.kind.name)
        .put("name", s.name).put("exerciseKey", s.exerciseKey).put("instructions", s.instructions)
        .put("durationKind", s.durationKind.name).put("value", s.value).put("restSeconds", s.restSeconds)
        .put("iterations", s.iterations).put("children", JSONArray(s.children.map(::stepJson))).apply {
            s.target?.let { put("target", JSONObject().put("kind", it.kind.name).put("minimum", it.minimum).put("maximum", it.maximum)) }
        }
    fun decode(raw: String): Workout {
        require(raw.length <= 500_000) { "File too large" }
        // Bound JSON nesting before org.json recurses, including malicious non-step fields.
        var depth = 0; var quoted = false; var escaped = false
        raw.forEach { c ->
            if (quoted) { if (escaped) escaped = false else if (c == '\\') escaped = true else if (c == '"') quoted = false }
            else when (c) { '"' -> quoted = true; '{', '[' -> { depth++; require(depth <= 24) { "JSON too deep" } }; '}', ']' -> depth-- }
        }
        val o = JSONObject(raw)
        require(o.getInt("schemaVersion") == VERSION) { "Unsupported schema version" }
        val w = Workout(id = o.getString("id"), name = o.getString("name"), description = o.getString("description"),
            sport = WorkoutSport.valueOf(o.getString("sport")), equipment = o.getString("equipment"),
            steps = readSteps(o.getJSONArray("steps"), 0), isTemplate = o.getBoolean("isTemplate"),
            createdAt = o.getLong("createdAt"), modifiedAt = o.getLong("modifiedAt"))
        require(w.validate().isEmpty()) { "Invalid workout: ${w.validate().joinToString()}" }
        return w
    }
    private fun readSteps(a: JSONArray, depth: Int): List<WorkoutStep> {
        require(depth <= 4 && a.length() <= 200)
        return (0 until a.length()).map { i ->
            val s = a.getJSONObject(i)
            WorkoutStep(id = s.getString("id"), kind = StepKind.valueOf(s.getString("kind")), name = s.getString("name"),
                exerciseKey = s.getString("exerciseKey"), instructions = s.getString("instructions"),
                durationKind = DurationKind.valueOf(s.getString("durationKind")), value = exactInt(s, "value"),
                restSeconds = exactInt(s, "restSeconds"), iterations = exactInt(s, "iterations"),
                children = readSteps(s.getJSONArray("children"), depth + 1), target = s.optJSONObject("target")?.let {
                    WorkoutTarget(TargetKind.valueOf(it.getString("kind")), it.getDouble("minimum"), it.getDouble("maximum"))
                })
        }
    }
    private fun exactInt(o: JSONObject, key: String): Int {
        val v = o.getDouble(key); require(v.isFinite() && v == v.toInt().toDouble()); return v.toInt()
    }
}
