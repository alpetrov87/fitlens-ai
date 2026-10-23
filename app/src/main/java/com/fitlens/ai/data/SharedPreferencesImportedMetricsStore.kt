package com.fitlens.ai.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Only parsed daily measurements are stored locally; source CSV files and account credentials are not. */
class SharedPreferencesImportedMetricsStore(context: Context) : ImportedMetricsStore {
    private val preferences = context.getSharedPreferences("fitlens_imports", Context.MODE_PRIVATE)
    private val lock = Any()

    override fun latestGarmin(): ImportedDailyMetrics? = synchronized(lock) {
        readRows().filter(ImportedDailyMetrics::garminData).maxByOrNull(ImportedDailyMetrics::date)
    }

    override fun latestMyFitnessPal(): ImportedDailyMetrics? = synchronized(lock) {
        readRows().filter(ImportedDailyMetrics::myFitnessPalData).maxByOrNull(ImportedDailyMetrics::date)
    }

    override fun clear() = synchronized(lock) {
        check(preferences.edit().remove(KEY_DAILY).commit()) { "Не успях да изтрия импортираните данни." }
    }

    override fun mergeAll(metrics: List<ImportedDailyMetrics>) = synchronized(lock) {
        val rows = readRows().associateBy(ImportedDailyMetrics::date).toMutableMap()
        for (metricsForDate in metrics) {
            val previous = rows[metricsForDate.date] ?: ImportedDailyMetrics(metricsForDate.date)
            rows[metricsForDate.date] = when {
                metricsForDate.garminData -> previous.copy(
                    steps = metricsForDate.steps ?: previous.steps,
                    activeMinutes = metricsForDate.activeMinutes ?: previous.activeMinutes,
                    distanceKm = metricsForDate.distanceKm ?: previous.distanceKm,
                    garminData = true,
                )
                metricsForDate.myFitnessPalData -> previous.copy(
                    calories = metricsForDate.calories ?: previous.calories,
                    calorieGoal = metricsForDate.calorieGoal ?: previous.calorieGoal,
                    proteinGrams = metricsForDate.proteinGrams ?: previous.proteinGrams,
                    carbsGrams = metricsForDate.carbsGrams ?: previous.carbsGrams,
                    fatGrams = metricsForDate.fatGrams ?: previous.fatGrams,
                    myFitnessPalData = true,
                )
                else -> previous
            }
        }
        val array = JSONArray().apply { rows.values.sortedBy(ImportedDailyMetrics::date).forEach { put(it.toJson()) } }
        check(preferences.edit().putString(KEY_DAILY, array.toString()).commit()) {
            "Не успях да запазя импортираните данни на устройството."
        }
    }

    private fun readRows(): List<ImportedDailyMetrics> {
        val text = preferences.getString(KEY_DAILY, null) ?: return emptyList()
        val array = JSONArray(text)
        return (0 until array.length()).mapNotNull { index ->
            runCatching { array.getJSONObject(index).toMetrics() }.getOrNull()
        }
    }

    private fun ImportedDailyMetrics.toJson() = JSONObject()
        .put("date", date)
        .put("steps", steps.jsonValue())
        .put("activeMinutes", activeMinutes.jsonValue())
        .put("distanceKm", distanceKm.jsonValue())
        .put("calories", calories.jsonValue())
        .put("calorieGoal", calorieGoal.jsonValue())
        .put("proteinGrams", proteinGrams.jsonValue())
        .put("carbsGrams", carbsGrams.jsonValue())
        .put("fatGrams", fatGrams.jsonValue())
        .put("garminData", garminData)
        .put("myFitnessPalData", myFitnessPalData)

    private fun JSONObject.toMetrics() = ImportedDailyMetrics(
        date = getString("date"),
        steps = optNullableInt("steps"),
        activeMinutes = optNullableInt("activeMinutes"),
        distanceKm = optNullableDouble("distanceKm"),
        calories = optNullableInt("calories"),
        calorieGoal = optNullableInt("calorieGoal"),
        proteinGrams = optNullableInt("proteinGrams"),
        carbsGrams = optNullableInt("carbsGrams"),
        fatGrams = optNullableInt("fatGrams"),
        garminData = optBoolean("garminData"),
        myFitnessPalData = optBoolean("myFitnessPalData"),
    )

    private fun Int?.jsonValue(): Any = this ?: JSONObject.NULL
    private fun Double?.jsonValue(): Any = this ?: JSONObject.NULL
    private fun JSONObject.optNullableInt(key: String): Int? = if (isNull(key)) null else optInt(key)
    private fun JSONObject.optNullableDouble(key: String): Double? = if (isNull(key)) null else optDouble(key)

    private companion object { const val KEY_DAILY = "daily_metrics_v1" }
}
