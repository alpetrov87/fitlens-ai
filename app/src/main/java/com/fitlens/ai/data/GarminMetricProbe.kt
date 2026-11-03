package com.fitlens.ai.data

import org.json.JSONArray
import org.json.JSONObject

/** Only selected numeric fitness fields leave the transient JSON response. */
internal fun garminProbeFields(value: Any?): List<String> {
    val result = linkedSetOf<String>()
    val metric = Regex("vo2|aerobic|anaerobic|training|recovery|readiness|load|endurance|hillscore|lactate|fitnessage|hrv|stress|bodybattery|sleep|restingheart", RegexOption.IGNORE_CASE)
    fun visit(node: Any?, path: String, depth: Int) {
        if (depth > 10 || result.size >= 100) return
        when (node) {
            is JSONObject -> node.keys().asSequence().take(300).forEach { key ->
                if (key.matches(Regex("[A-Za-z][A-Za-z0-9_]{0,79}")) &&
                    !Regex("token|secret|password|cookie|email|username|displayname|(?:^id$|Id$|_id$)", RegexOption.IGNORE_CASE).containsMatchIn(key)) {
                    val item = node.opt(key)
                    val next = if (path.isEmpty()) key else "$path.$key"
                    if (item is Number && metric.containsMatchIn(key) && item.toDouble().isFinite()) {
                        if (result.size < 100) result.add("$next = $item")
                    } else if (item is JSONObject || item is JSONArray) visit(item, next, depth + 1)
                }
            }
            is JSONArray -> for (i in 0 until minOf(node.length(), 5)) visit(node.opt(i), "$path[$i]", depth + 1)
        }
    }
    visit(value, "", 0)
    return result.toList()
}

internal data class GarminProbeResult(val source: String, val status: Int, val fields: List<String> = emptyList())
