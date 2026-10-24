package com.fitlens.ai.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class HealthRepositoryTest {
    @Test fun combinesIndependentProviders() = runBlocking {
        val activity = ActivitySummary(1234, 12, 0.9)
        val nutrition = NutritionSummary(800, 2000, 40, 100, 20)
        val repository = DefaultHealthRepository(
            object : ActivitySource { override suspend fun activity() = activity },
            object : NutritionSource { override suspend fun nutrition() = nutrition },
        )
        assertEquals(Dashboard(activity, nutrition), repository.dashboard())
    }
    @Test fun missingDataIsDisplayedAsZero() = runBlocking {
        val dashboard = DefaultHealthRepository(EmptyActivitySource(), EmptyNutritionSource()).dashboard()
        assertEquals(0, dashboard.activity.steps)
        assertEquals(0, dashboard.activity.activeMinutes)
        assertEquals(0.0, dashboard.activity.distanceKm, 0.0)
        assertEquals(0, dashboard.nutrition.calories)
        assertEquals(0, dashboard.nutrition.calorieGoal)
        assertEquals(MetricOrigin.NONE, dashboard.activityOrigin)
        assertEquals(MetricOrigin.NONE, dashboard.nutritionOrigin)
    }
    @Test fun providerFailureIsNotReplacedByZeroData() = runBlocking {
        val repository = DefaultHealthRepository(
            object : ActivitySource { override suspend fun activity(): ActivitySummary = error("Unavailable") },
            EmptyNutritionSource(),
        )
        try {
            repository.dashboard()
            fail("Provider error must propagate")
        } catch (expected: IllegalStateException) {
            assertEquals("Unavailable", expected.message)
        }
    }
}
