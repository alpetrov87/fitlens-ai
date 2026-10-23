package com.fitlens.ai.data

/** Provider-neutral models. Dates and OAuth/token storage belong in production adapters. */
data class ActivitySummary(val steps: Int, val activeMinutes: Int, val distanceKm: Double)
data class NutritionSummary(val calories: Int, val calorieGoal: Int, val proteinGrams: Int, val carbsGrams: Int, val fatGrams: Int)
data class Dashboard(val activity: ActivitySummary, val nutrition: NutritionSummary)

/** Implement behind a secure backend once Garmin access and consent are approved. */
interface ActivitySource { suspend fun activity(): ActivitySummary }
/** Implement behind a secure backend once MyFitnessPal access and consent are approved. */
interface NutritionSource { suspend fun nutrition(): NutritionSummary }
interface HealthRepository { suspend fun dashboard(): Dashboard }
class DefaultHealthRepository(
    private val activities: ActivitySource,
    private val nutrition: NutritionSource,
) : HealthRepository {
    override suspend fun dashboard() = Dashboard(activities.activity(), nutrition.nutrition())
}
class DemoActivitySource : ActivitySource {
    override suspend fun activity() = ActivitySummary(7840, 42, 5.6)
}
class DemoNutritionSource : NutritionSource {
    override suspend fun nutrition() = NutritionSummary(1640, 2200, 112, 185, 51)
}
