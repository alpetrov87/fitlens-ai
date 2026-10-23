package com.fitlens.ai.data

/** Provider-neutral models. Dates and OAuth/token storage belong in production adapters. */
data class ActivitySummary(val steps: Int, val activeMinutes: Int, val distanceKm: Double)
data class NutritionSummary(val calories: Int, val calorieGoal: Int, val proteinGrams: Int, val carbsGrams: Int, val fatGrams: Int)
enum class MetricOrigin { DEMO, GARMIN_CSV, MYFITNESSPAL_CSV }
data class Dashboard(
    val activity: ActivitySummary,
    val nutrition: NutritionSummary,
    val activityOrigin: MetricOrigin = MetricOrigin.DEMO,
    val nutritionOrigin: MetricOrigin = MetricOrigin.DEMO,
    val activityDate: String? = null,
    val nutritionDate: String? = null,
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
)

interface ImportedMetricsStore {
    fun latestGarmin(): ImportedDailyMetrics?
    fun latestMyFitnessPal(): ImportedDailyMetrics?
    fun mergeAll(metrics: List<ImportedDailyMetrics>)
    fun clear()
}

/** Implement behind a secure backend once Garmin access and consent are approved. */
interface ActivitySource { suspend fun activity(): ActivitySummary }
/** Implement behind a secure backend once MyFitnessPal access and consent are approved. */
interface NutritionSource { suspend fun nutrition(): NutritionSummary }
interface HealthRepository { suspend fun dashboard(): Dashboard }
interface CsvImportRepository : HealthRepository {
    suspend fun importCsv(source: CsvImportSource, contents: String): Int
    suspend fun clearImports()
}
class DefaultHealthRepository(
    private val activities: ActivitySource,
    private val nutrition: NutritionSource,
    private val imported: ImportedMetricsStore? = null,
) : CsvImportRepository {
    override suspend fun dashboard(): Dashboard {
        val activity = activities.activity()
        val nutrition = nutrition.nutrition()
        val latestGarmin = imported?.latestGarmin()
        val latestNutrition = imported?.latestMyFitnessPal()
        if (latestGarmin == null && latestNutrition == null) return Dashboard(activity, nutrition)
        return Dashboard(
            activity = ActivitySummary(
                steps = latestGarmin?.steps ?: activity.steps,
                activeMinutes = latestGarmin?.activeMinutes ?: activity.activeMinutes,
                distanceKm = latestGarmin?.distanceKm ?: activity.distanceKm,
            ),
            nutrition = NutritionSummary(
                calories = latestNutrition?.calories ?: nutrition.calories,
                calorieGoal = latestNutrition?.calorieGoal ?: nutrition.calorieGoal,
                proteinGrams = latestNutrition?.proteinGrams ?: nutrition.proteinGrams,
                carbsGrams = latestNutrition?.carbsGrams ?: nutrition.carbsGrams,
                fatGrams = latestNutrition?.fatGrams ?: nutrition.fatGrams,
            ),
            activityOrigin = if (latestGarmin != null) MetricOrigin.GARMIN_CSV else MetricOrigin.DEMO,
            nutritionOrigin = if (latestNutrition != null) MetricOrigin.MYFITNESSPAL_CSV else MetricOrigin.DEMO,
            activityDate = latestGarmin?.date,
            nutritionDate = latestNutrition?.date,
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
}
class DemoActivitySource : ActivitySource {
    override suspend fun activity() = ActivitySummary(7840, 42, 5.6)
}
class DemoNutritionSource : NutritionSource {
    override suspend fun nutrition() = NutritionSummary(1640, 2200, 112, 185, 51)
}
