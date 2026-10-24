package com.fitlens.ai.data

/** Provider-neutral models. Dates and OAuth/token storage belong in production adapters. */
data class ActivitySummary(val steps: Int, val activeMinutes: Int, val distanceKm: Double)
data class NutritionSummary(val calories: Int, val calorieGoal: Int, val proteinGrams: Int, val carbsGrams: Int, val fatGrams: Int)
enum class MetricOrigin { NONE, GARMIN_CSV, GARMIN_PERSONAL_API, MYFITNESSPAL_CSV }
data class Dashboard(
    val activity: ActivitySummary,
    val nutrition: NutritionSummary,
    val activityOrigin: MetricOrigin = MetricOrigin.NONE,
    val nutritionOrigin: MetricOrigin = MetricOrigin.NONE,
    val activityDate: String? = null,
    val nutritionDate: String? = null,
    val garminActivities: List<GarminActivity> = emptyList(),
    val garminCaloriesConsumed: Int? = null,
)

enum class CsvImportSource { GARMIN, MYFITNESSPAL }

data class ImportedDailyMetrics(
    val date: String,
    val steps: Int? = null,
    val activeMinutes: Int? = null,
    val distanceKm: Double? = null,
    val calories: Int? = null,
    val calorieGoal: Int? = null,
    val proteinGrams: Int? = null,
    val carbsGrams: Int? = null,
    val fatGrams: Int? = null,
    val garminData: Boolean = false,
    val myFitnessPalData: Boolean = false,
    val garminPersonalApiData: Boolean = false,
    val garminCaloriesConsumed: Int? = null,
)

/** Garmin's type key stays open-ended so new sports do not require an app schema change. */
data class GarminActivity(
    val activityId: Long,
    val name: String,
    val activityTypeKey: String,
    val startTimeLocal: String,
    val durationSeconds: Double?,
    val distanceMeters: Double?,
    val calories: Int?,
    val steps: Int?,
    val averageHeartRate: Int?,
    val maxHeartRate: Int?,
    val elevationGainMeters: Double?,
)

data class GarminRecoveryDay(
    val date: String,
    val sleepMinutes: Int? = null,
    val hrvLastNightMs: Int? = null,
    val hrvStatus: String? = null,
)

data class GarminTrainingHistory(val activities: List<GarminActivity>, val recovery: List<GarminRecoveryDay>)

interface ImportedMetricsStore {
    fun latestGarmin(): ImportedDailyMetrics?
    fun latestMyFitnessPal(): ImportedDailyMetrics?
    fun mergeAll(metrics: List<ImportedDailyMetrics>)
    fun clear()
    fun clearGarmin()
    fun activitiesForDate(date: String): List<GarminActivity>
    fun replaceGarminActivities(date: String, activities: List<GarminActivity>)
    fun activitiesBetween(startDate: String, endDate: String): List<GarminActivity>
    fun replaceGarminActivitiesBetween(startDate: String, endDate: String, activities: List<GarminActivity>)
    fun recoveryBetween(startDate: String, endDate: String): List<GarminRecoveryDay>
    fun replaceGarminRecoveryBetween(startDate: String, endDate: String, recovery: List<GarminRecoveryDay>)
}

/** Implement behind a secure backend once Garmin access and consent are approved. */
interface ActivitySource { suspend fun activity(): ActivitySummary }
/** Implement behind a secure backend once MyFitnessPal access and consent are approved. */
interface NutritionSource { suspend fun nutrition(): NutritionSummary }
interface HealthRepository { suspend fun dashboard(): Dashboard }
interface CsvImportRepository : HealthRepository {
    suspend fun importCsv(source: CsvImportSource, contents: String): Int
    suspend fun clearImports()
    fun isGarminConnected(): Boolean
    suspend fun connectGarmin(apiKey: String)
    suspend fun syncGarmin()
    suspend fun disconnectGarmin()
    suspend fun syncTrainingHistory(days: Int): GarminTrainingHistory
    fun garminActivitiesBetween(startDate: String, endDate: String): List<GarminActivity>
    fun garminRecoveryBetween(startDate: String, endDate: String): List<GarminRecoveryDay>
}
class DefaultHealthRepository(
    private val activities: ActivitySource,
    private val nutrition: NutritionSource,
    private val imported: ImportedMetricsStore? = null,
    private val garminApi: GarminPersonalApi? = null,
) : CsvImportRepository {
    override suspend fun dashboard(): Dashboard {
        val activity = activities.activity()
        val nutrition = nutrition.nutrition()
        val latestGarmin = imported?.latestGarmin()
        val latestNutrition = imported?.latestMyFitnessPal()
        val todaysActivities = imported?.activitiesForDate(java.time.LocalDate.now().toString()).orEmpty()
        if (latestGarmin == null && latestNutrition == null) return Dashboard(activity, nutrition, garminActivities = todaysActivities)
        return Dashboard(
            activity = ActivitySummary(
                steps = latestGarmin?.steps ?: activity.steps,
                activeMinutes = latestGarmin?.activeMinutes ?: activity.activeMinutes,
                distanceKm = latestGarmin?.distanceKm ?: activity.distanceKm,
            ),
            nutrition = NutritionSummary(
                calories = latestNutrition?.calories ?: latestGarmin?.garminCaloriesConsumed ?: nutrition.calories,
                calorieGoal = latestNutrition?.calorieGoal ?: nutrition.calorieGoal,
                proteinGrams = latestNutrition?.proteinGrams ?: nutrition.proteinGrams,
                carbsGrams = latestNutrition?.carbsGrams ?: nutrition.carbsGrams,
                fatGrams = latestNutrition?.fatGrams ?: nutrition.fatGrams,
            ),
            activityOrigin = when {
                latestGarmin?.garminPersonalApiData == true -> MetricOrigin.GARMIN_PERSONAL_API
                latestGarmin != null -> MetricOrigin.GARMIN_CSV
                else -> MetricOrigin.NONE
            },
            nutritionOrigin = when {
                latestNutrition != null -> MetricOrigin.MYFITNESSPAL_CSV
                latestGarmin?.garminCaloriesConsumed != null -> MetricOrigin.GARMIN_PERSONAL_API
                else -> MetricOrigin.NONE
            },
            activityDate = latestGarmin?.date,
            nutritionDate = latestNutrition?.date ?: latestGarmin?.date?.takeIf { latestGarmin.garminCaloriesConsumed != null },
            garminActivities = todaysActivities,
            garminCaloriesConsumed = latestGarmin?.garminCaloriesConsumed,
        )
    }

    override suspend fun importCsv(source: CsvImportSource, contents: String): Int {
        val rows = DailyMetricsCsv.parse(source, contents)
        val store = checkNotNull(imported) { "CSV import storage is unavailable" }
        store.mergeAll(rows)
        return rows.size
    }

    override suspend fun clearImports() {
        checkNotNull(imported) { "CSV import storage is unavailable" }.clear()
    }

    override fun isGarminConnected(): Boolean = garminApi?.isConnected() == true

    override suspend fun connectGarmin(apiKey: String) {
        val store = checkNotNull(imported) { "Garmin storage is unavailable" }
        val api = checkNotNull(garminApi) { "Garmin Personal API is unavailable" }
        val data = api.connect(apiKey)
        store.mergeAll(listOf(data.summary))
        store.replaceGarminActivities(data.summary.date, data.activities)
    }

    override suspend fun syncGarmin() {
        val store = checkNotNull(imported) { "Garmin storage is unavailable" }
        val api = checkNotNull(garminApi) { "Garmin Personal API is unavailable" }
        val data = api.sync()
        store.mergeAll(listOf(data.summary))
        store.replaceGarminActivities(data.summary.date, data.activities)
    }

    override suspend fun disconnectGarmin() {
        checkNotNull(garminApi) { "Garmin Personal API is unavailable" }.disconnect()
        checkNotNull(imported) { "Garmin storage is unavailable" }.clearGarmin()
    }

    override suspend fun syncTrainingHistory(days: Int): GarminTrainingHistory {
        require(days in 7..90) { "Избери период между 7 и 90 дни." }
        val store = checkNotNull(imported) { "Garmin storage is unavailable" }
        val api = checkNotNull(garminApi) { "Garmin Personal API is unavailable" }
        val end = java.time.LocalDate.now()
        val start = end.minusDays(days.toLong() - 1)
        val history = api.fetchTrainingHistory(start.toString(), end.toString())
        store.replaceGarminActivitiesBetween(start.toString(), end.toString(), history.activities)
        store.replaceGarminRecoveryBetween(start.toString(), end.toString(), history.recovery)
        return history
    }

    override fun garminActivitiesBetween(startDate: String, endDate: String): List<GarminActivity> =
        imported?.activitiesBetween(startDate, endDate).orEmpty()

    override fun garminRecoveryBetween(startDate: String, endDate: String): List<GarminRecoveryDay> =
        imported?.recoveryBetween(startDate, endDate).orEmpty()
}
class EmptyActivitySource : ActivitySource {
    override suspend fun activity() = ActivitySummary(0, 0, 0.0)
}
class EmptyNutritionSource : NutritionSource {
    override suspend fun nutrition() = NutritionSummary(0, 0, 0, 0, 0)
}
