package com.fitlens.ai.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Only parsed daily measurements are stored locally; source CSV files and account credentials are not. */
class SharedPreferencesImportedMetricsStore(context: Context) : ImportedMetricsStore {
    private val preferences = context.getSharedPreferences("fitlens_imports", Context.MODE_PRIVATE)
    private val lock = GLOBAL_LOCK

    override fun latestGarmin(): ImportedDailyMetrics? = synchronized(lock) {
        readRows().filter { it.garminData || it.garminPersonalApiData }.maxByOrNull(ImportedDailyMetrics::date)
    }

    override fun latestMyFitnessPal(): ImportedDailyMetrics? = synchronized(lock) {
        readRows().filter(ImportedDailyMetrics::myFitnessPalData).maxByOrNull(ImportedDailyMetrics::date)
    }

    override fun clear() = synchronized(lock) {
        check(preferences.edit().remove(KEY_DAILY).remove(KEY_GARMIN_ACTIVITIES).remove(KEY_GARMIN_RECOVERY).commit()) { "Не успях да изтрия импортираните данни." }
    }

    override fun clearGarmin() = synchronized(lock) {
        val rows = readRows().map { row ->
            if (row.garminData || row.garminPersonalApiData) row.copy(
                steps = null, activeMinutes = null, distanceKm = null,
                garminCaloriesConsumed = null, garminNutritionJson = null, garminNutritionStatus = null,
                garminData = false, garminPersonalApiData = false,
            ) else row
        }.filter { it.myFitnessPalData }
        val array = JSONArray().apply { rows.forEach { put(it.toJson()) } }
        check(preferences.edit().putString(KEY_DAILY, array.toString()).remove(KEY_GARMIN_ACTIVITIES).remove(KEY_GARMIN_RECOVERY).commit()) { "Не успях да изтрия Garmin данните." }
    }

    override fun activitiesForDate(date: String): List<GarminActivity> = synchronized(lock) {
        val activities = readActivities()
        (0 until activities.length()).mapNotNull { index ->
            runCatching { activities.getJSONObject(index).toActivity() }.getOrNull()
        }.filter { it.startTimeLocal.startsWith(date) }.sortedBy(GarminActivity::startTimeLocal)
    }

    override fun activitiesBetween(startDate: String, endDate: String): List<GarminActivity> = synchronized(lock) {
        readActivitiesList().filter { it.startTimeLocal.take(10) in startDate..endDate }
            .sortedBy(GarminActivity::startTimeLocal)
    }

    override fun replaceGarminActivities(date: String, activities: List<GarminActivity>) = synchronized(lock) {
        val all = readActivitiesList().filterNot { it.startTimeLocal.startsWith(date) } + activities
        val array = JSONArray().apply { all.sortedBy(GarminActivity::startTimeLocal).forEach { put(it.toJson()) } }
        check(preferences.edit().putString(KEY_GARMIN_ACTIVITIES, array.toString()).commit()) { "Не успях да запазя Garmin активностите." }
    }

    override fun replaceGarminActivitiesBetween(startDate: String, endDate: String, activities: List<GarminActivity>) = synchronized(lock) {
        val all = readActivitiesList().filterNot { it.startTimeLocal.take(10) in startDate..endDate } + activities
        val array = JSONArray().apply { all.sortedBy(GarminActivity::startTimeLocal).forEach { put(it.toJson()) } }
        check(preferences.edit().putString(KEY_GARMIN_ACTIVITIES, array.toString()).commit()) { "Не успях да запазя Garmin историята." }
    }

    override fun recoveryBetween(startDate: String, endDate: String): List<GarminRecoveryDay> = synchronized(lock) {
        val array = readRecovery()
        (0 until array.length()).mapNotNull { i -> runCatching { array.getJSONObject(i).toRecovery() }.getOrNull() }
            .filter { it.date in startDate..endDate }.sortedBy(GarminRecoveryDay::date)
    }

    override fun replaceGarminRecoveryBetween(startDate: String, endDate: String, recovery: List<GarminRecoveryDay>) = synchronized(lock) {
        val all = recoveryBetween("0000-01-01", "9999-12-31").filterNot { it.date in startDate..endDate } + recovery
        val array = JSONArray().apply { all.sortedBy(GarminRecoveryDay::date).forEach { put(it.toJson()) } }
        check(preferences.edit().putString(KEY_GARMIN_RECOVERY, array.toString()).commit()) { "Не успях да запазя данните за сън и HRV." }
    }

    private fun readActivitiesList(): List<GarminActivity> {
        val current = readActivities()
        return (0 until current.length()).mapNotNull { index ->
            runCatching { current.getJSONObject(index).toActivity() }.getOrNull()
        }
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
                    garminPersonalApiData = false,
                    garminCaloriesConsumed = null, garminNutritionJson = null, garminNutritionStatus = null,
                )
                metricsForDate.garminPersonalApiData -> previous.copy(
                    steps = metricsForDate.steps,
                    activeMinutes = metricsForDate.activeMinutes,
                    distanceKm = metricsForDate.distanceKm,
                    garminCaloriesConsumed = metricsForDate.garminCaloriesConsumed,
                    garminNutritionJson = if (metricsForDate.garminNutritionStatus == "unavailable") previous.garminNutritionJson else metricsForDate.garminNutritionJson,
                    garminNutritionStatus = metricsForDate.garminNutritionStatus,
                    garminData = false,
                    garminPersonalApiData = true,
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
        .put("garminPersonalApiData", garminPersonalApiData)
        .put("garminCaloriesConsumed", garminCaloriesConsumed.jsonValue())
        .put("garminNutritionJson", garminNutritionJson ?: JSONObject.NULL)
        .put("garminNutritionStatus", garminNutritionStatus ?: JSONObject.NULL)
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
        garminPersonalApiData = optBoolean("garminPersonalApiData"),
        garminCaloriesConsumed = optNullableInt("garminCaloriesConsumed"),
        myFitnessPalData = optBoolean("myFitnessPalData"),
        garminNutritionJson = optString("garminNutritionJson").takeUnless { isNull("garminNutritionJson") || it.isBlank() },
        garminNutritionStatus = optString("garminNutritionStatus").takeUnless { isNull("garminNutritionStatus") || it.isBlank() },
    )

    private fun Int?.jsonValue(): Any = this ?: JSONObject.NULL
    private fun Double?.jsonValue(): Any = this ?: JSONObject.NULL
    private fun JSONObject.optNullableInt(key: String): Int? = if (isNull(key)) null else optInt(key)
    private fun JSONObject.optNullableDouble(key: String): Double? = if (isNull(key)) null else optDouble(key)

    private fun readActivities(): JSONArray = JSONArray(preferences.getString(KEY_GARMIN_ACTIVITIES, "[]"))

    private fun readRecovery(): JSONArray = JSONArray(preferences.getString(KEY_GARMIN_RECOVERY, "[]"))

    private fun GarminRecoveryDay.toJson() = JSONObject().put("date", date)
        .put("sleepMinutes", sleepMinutes.jsonValue()).put("hrvLastNightMs", hrvLastNightMs.jsonValue())
        .put("hrvStatus", hrvStatus ?: JSONObject.NULL)

    private fun JSONObject.toRecovery() = GarminRecoveryDay(getString("date"), optNullableInt("sleepMinutes"),
        optNullableInt("hrvLastNightMs"), optString("hrvStatus").takeIf(String::isNotBlank))

    private fun GarminActivity.toJson() = JSONObject()
        .put("activityId", activityId).put("name", name).put("activityTypeKey", activityTypeKey)
        .put("startTimeLocal", startTimeLocal).put("durationSeconds", durationSeconds.jsonValue())
        .put("distanceMeters", distanceMeters.jsonValue()).put("calories", calories.jsonValue())
        .put("steps", steps.jsonValue()).put("averageHeartRate", averageHeartRate.jsonValue())
        .put("maxHeartRate", maxHeartRate.jsonValue()).put("elevationGainMeters", elevationGainMeters.jsonValue())

    private fun JSONObject.toActivity() = GarminActivity(
        activityId = getLong("activityId"), name = getString("name"),
        activityTypeKey = getString("activityTypeKey"), startTimeLocal = getString("startTimeLocal"),
        durationSeconds = optNullableDouble("durationSeconds"), distanceMeters = optNullableDouble("distanceMeters"),
        calories = optNullableInt("calories"), steps = optNullableInt("steps"),
        averageHeartRate = optNullableInt("averageHeartRate"), maxHeartRate = optNullableInt("maxHeartRate"),
        elevationGainMeters = optNullableDouble("elevationGainMeters"),
    )

    private companion object {
        val GLOBAL_LOCK = Any()
        const val KEY_DAILY = "daily_metrics_v1"
        const val KEY_GARMIN_ACTIVITIES = "garmin_activities_v1"
        const val KEY_GARMIN_RECOVERY = "garmin_recovery_v1"
    }
}
