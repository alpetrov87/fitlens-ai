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
    @Test fun demoDataHasExpectedValues() = runBlocking {
        val dashboard = DefaultHealthRepository(DemoActivitySource(), DemoNutritionSource()).dashboard()
        assertEquals(7840, dashboard.activity.steps)
        assertEquals(1640, dashboard.nutrition.calories)
        assertEquals(2200, dashboard.nutrition.calorieGoal)
    }
    @Test fun providerFailureIsNotReplacedByDemoData() = runBlocking {
        val repository = DefaultHealthRepository(
            object : ActivitySource { override suspend fun activity(): ActivitySummary = error("Unavailable") },
            DemoNutritionSource(),
        )
        try {
            repository.dashboard()
            fail("Provider error must propagate")
        } catch (expected: IllegalStateException) {
            assertEquals("Unavailable", expected.message)
        }
    }
}
