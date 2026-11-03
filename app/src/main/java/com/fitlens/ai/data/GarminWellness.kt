package com.fitlens.ai.data

import org.json.JSONArray
import org.json.JSONObject

internal data class WellnessMetric(val bg: String, val en: String, val unit: String)
internal val wellnessMetrics = linkedMapOf(
    "sleepScore" to WellnessMetric("Оценка на съня", "Sleep score", "/100"),
    "deepMinutes" to WellnessMetric("Дълбок сън", "Deep sleep", "min"),
    "lightMinutes" to WellnessMetric("Лек сън", "Light sleep", "min"),
    "remMinutes" to WellnessMetric("REM сън", "REM sleep", "min"),
    "awakeMinutes" to WellnessMetric("Будуване", "Awake", "min"),
    "sleepStress" to WellnessMetric("Стрес по време на сън", "Sleep stress", "/100"),
    "hrvWeekly" to WellnessMetric("Седмично HRV", "Weekly HRV", "ms"),
    "hrvHigh" to WellnessMetric("Най-високо 5-минутно HRV", "Highest 5-minute HRV", "ms"),
    "stress" to WellnessMetric("Дневен стрес", "Daily stress", "/100"),
    "batteryMin" to WellnessMetric("Минимално Body Battery", "Minimum Body Battery", "/100"),
    "batteryMax" to WellnessMetric("Максимално Body Battery", "Maximum Body Battery", "/100"),
    "batteryLast" to WellnessMetric("Последно измерване Body Battery", "Last Body Battery reading", "/100"),
    "fitnessAge" to WellnessMetric("Фитнес възраст (прочетена на датата)", "Fitness age (retrieved on date)", "years"),
    "achievableFitnessAge" to WellnessMetric("Достижима фитнес възраст", "Achievable fitness age", "years"),
    "aerobic" to WellnessMetric("Аеробен тренировъчен ефект", "Aerobic training effect", "/5"),
    "anaerobic" to WellnessMetric("Анаеробен тренировъчен ефект", "Anaerobic training effect", "/5"),
    "trainingLoad" to WellnessMetric("Натоварване на активността", "Activity training load", "Garmin"),
    "batteryImpact" to WellnessMetric("Промяна на Body Battery от активност", "Activity Body Battery change", "points"),
)
internal fun readWellness(json: JSONObject?): Map<String, Double> = wellnessMetrics.keys.mapNotNull { key ->
    val value = json?.opt(key) as? Number
    value?.toDouble()?.takeIf(Double::isFinite)?.let { key to it }
}.toMap()
internal fun wellnessText(values: Map<String, Double>, bg: Boolean): String = values.mapNotNull { (key, value) ->
    wellnessMetrics[key]?.let { "${if (bg) it.bg else it.en}: ${String.format(java.util.Locale.US, "%.1f", value)} ${it.unit}" }
}.joinToString(" | ")
internal fun wellnessValue(json: JSONObject, key: String): Double? = (json.opt(key) as? Number)?.toDouble()?.takeIf { it.isFinite() && it >= 0 }
internal fun bodyBatteryLevels(json: JSONObject): Map<String, Double> {
    val array = json.optJSONArray("bodyBatteryValuesArray") ?: return emptyMap()
    val pairs = (0 until minOf(array.length(), 3000)).mapNotNull { i ->
        val pair = array.optJSONArray(i) ?: return@mapNotNull null
        val time = (pair.opt(0) as? Number)?.toLong() ?: return@mapNotNull null
        val level = (pair.opt(1) as? Number)?.toDouble()?.takeIf { it.isFinite() && it in 0.0..100.0 } ?: return@mapNotNull null
        time to level
    }
    if (pairs.isEmpty()) return emptyMap()
    return mapOf("batteryMin" to pairs.minOf { it.second }, "batteryMax" to pairs.maxOf { it.second },
        "batteryLast" to pairs.maxBy { it.first }.second)
}
