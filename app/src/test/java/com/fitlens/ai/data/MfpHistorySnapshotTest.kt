package com.fitlens.ai.data

import org.junit.Assert.*
import org.junit.Test

class MfpHistorySnapshotTest {
    @Test fun retainsAllMealsAndNutrientsWithoutInventingMissingTotals() {
        val data = MfpDiaryPreview("2026-10-27", listOf(
            MfpFoodPreview("Breakfast", "Synthetic food A", mapOf("calories" to 120.0)),
            MfpFoodPreview("Lunch", "Synthetic food B", mapOf("protein" to 18.0)),
            MfpFoodPreview("Dinner", "Synthetic food C", mapOf("fiber" to 4.0)),
        ), mapOf("calories" to 400.0))
        val json = mfpSnapshot(data)
        assertEquals(data.date, json.getString("date"))
        assertEquals(3, json.getJSONArray("foods").length())
        assertEquals("Dinner", json.getJSONArray("foods").getJSONObject(2).getString("meal"))
        assertEquals(4.0, json.getJSONArray("foods").getJSONObject(2).getJSONObject("nutrients").getDouble("fiber"), 0.0)
        assertFalse(json.getJSONObject("totals").has("protein"))
    }
    @Test fun explicitZeroIsPreservedForAnEmptyRecognizedDay() {
        val json = mfpSnapshot(MfpDiaryPreview("2026-10-27", emptyList(), mapOf("calories" to 0.0)))
        assertEquals(0, json.getJSONArray("foods").length())
        assertEquals(0.0, json.getJSONObject("totals").getDouble("calories"), 0.0)
    }
}
