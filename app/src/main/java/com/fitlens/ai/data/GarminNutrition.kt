package com.fitlens.ai.data

import org.json.JSONObject
import java.time.LocalDate

/** Only the nutrition response is persisted; no keys, tokens or request headers. */
data class GarminNutrition(val date: String, val json: String) {
    val root: JSONObject get() = JSONObject(json)
    val content: JSONObject get() = root.optJSONObject("dailyNutritionContent") ?: JSONObject()
    val goals: JSONObject get() = root.optJSONObject("dailyNutritionGoals") ?: JSONObject()
    val adjustedCalories: Double? get() = goals.numberOrNull("adjustedCalories")
    val consumedCalories: Double? get() = content.numberOrNull("calories")
    val remainingCalories: Double? get() = adjustedCalories?.let { goal -> consumedCalories?.let { goal - it } }

    companion object {
        fun parse(date: String, response: JSONObject): GarminNutrition {
            LocalDate.parse(date)
            val returned = response.optString("mealDate").takeIf { it.isNotBlank() }
            require(returned == null || returned == date) { "Garmin returned nutrition for another date." }
            val json = response.toString()
            require(json.length <= 2_000_000) { "Nutrition response exceeds the local storage limit." }
            return GarminNutrition(date, json)
        }
    }
}

internal fun JSONObject.numberOrNull(key: String): Double? = if (isNull(key) || !has(key)) null
    else (opt(key) as? Number)?.toDouble()?.takeIf { it.isFinite() && it >= 0 }
