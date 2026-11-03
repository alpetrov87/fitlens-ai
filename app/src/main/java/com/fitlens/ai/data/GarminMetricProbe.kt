package com.fitlens.ai.data

import org.json.JSONArray
import org.json.JSONObject

/** Only selected numeric fitness fields leave the transient JSON response. */
internal fun garminProbeFields(value: Any?): List<String> {
    val result = linkedSetOf<String>()
    val metric = Regex("vo2|aerobic|anaerobic|training|recovery|readiness|load|endurance|hillscore|lactate|fitnessage|hrv|stress|bodybattery|sleep|restingheart", RegexOption.IGNORE_CASE)
    fun visit(node: Any?, path: String, depth: Int) {
        if (depth > 10 || result.size >= 1000) return
        when (node) {
            is JSONObject -> node.keys().asSequence().take(300).forEach { key ->
                if (key.matches(Regex("[A-Za-z][A-Za-z0-9_]{0,79}")) &&
                    !Regex("token|secret|password|cookie|email|username|displayname|(?:^id$|Id$|_id$)", RegexOption.IGNORE_CASE).containsMatchIn(key)) {
                    val item = node.opt(key)
                    val next = if (path.isEmpty()) key else "$path.$key"
                    val generic = key in setOf("value", "score", "min", "max", "charged", "drained", "highest", "lowest", "lastNightAvg", "lastNight5MinHigh", "weeklyAvg", "baselineLowUpper", "baselineBalancedLower", "baselineBalancedUpper")
                    val selected = metric.containsMatchIn(key) || generic && metric.containsMatchIn(path)
                    if (key == "bodyBatteryValuesArray" && item is JSONArray) {
                        val levels = (0 until minOf(item.length(), 3000)).mapNotNull { i ->
                            val pair = item.optJSONArray(i)
                            (pair?.opt(1) as? Number)?.toDouble()?.takeIf { it.isFinite() && it in 0.0..100.0 }
                        }
                        if (levels.isNotEmpty() && result.size <= 997) {
                            result.add("$next.minimumFromReadings = ${levels.min()}")
                            result.add("$next.maximumFromReadings = ${levels.max()}")
                            result.add("$next.lastValidReading = ${levels.last()}")
                        }
                    } else if (item is Number && selected && !key.contains("index", ignoreCase = true) && item.toDouble().isFinite()) {
                        if (result.size < 1000) result.add("$next = $item")
                    } else if (item is JSONObject || item is JSONArray) visit(item, next, depth + 1)
                }
            }
            is JSONArray -> for (i in 0 until minOf(node.length(), 30)) {
                val entry = node.opt(i)
                val date = (entry as? JSONObject)?.let { obj ->
                    listOf("calendarDate", "date", "startTimeLocal").firstNotNullOfOrNull { k ->
                        obj.optString(k).take(10).takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }
                    }
                }
                visit(entry, "$path[${date ?: i}]", depth + 1)
            }
        }
    }
    visit(value, "", 0)
    return result.toList()
}

internal data class GarminProbeResult(val source: String, val status: Int, val fields: List<String> = emptyList())

/** Stable plain text for the system clipboard; no response bodies or credentials. */
internal fun garminProbeReport(results: List<GarminProbeResult>, empty: String): String = buildString {
    appendLine("bAIr — Garmin Personal API test")
    results.forEach { result ->
        appendLine()
        appendLine("=== ${result.source} ===")
        appendLine("HTTP ${result.status}")
        appendLine(result.fields.joinToString("\n").ifBlank { empty })
    }
}.take(120_000)

internal fun garminProbeActivities(activities: JSONArray): List<JSONObject> {
    val items = (0 until minOf(50, activities.length())).mapNotNull(activities::optJSONObject)
    return items.sortedBy { item ->
        val type = item.optJSONObject("activityType")?.optString("typeKey").orEmpty()
        if (Regex("running|cycling|biking", RegexOption.IGNORE_CASE).containsMatchIn(type)) 0 else 1
    }.take(12)
}
