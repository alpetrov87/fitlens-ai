package com.fitlens.ai.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GarminNutritionTest {
    @Test fun usesAdjustedGoalWithoutAddingActivityAgain() {
        val data = GarminNutrition.parse("2026-10-27", JSONObject("""{"mealDate":"2026-10-27",
          "dailyNutritionContent":{"calories":2300,"protein":120.5},
          "dailyNutritionGoals":{"calories":1800,"adjustedCalories":2600},
          "mealDetails":[{"mealName":"Lunch","loggedFoods":[{"servingQty":1.5,"nutritionContent":{"sodium":200}}]}]}"""))
        assertEquals(2600.0, data.adjustedCalories!!, 0.01)
        assertEquals(300.0, data.remainingCalories!!, 0.01)
        assertTrue(data.json.contains("sodium"))
        assertEquals(120.5, data.content.numberOrNull("protein")!!, 0.01)
    }
    @Test fun doesNotSubstituteBaseGoalForMissingAdjustedGoal() {
        val data = GarminNutrition.parse("2026-10-27", JSONObject("""{"dailyNutritionGoals":{"calories":1800},"dailyNutritionContent":{"calories":0}}"""))
        assertNull(data.adjustedCalories)
        assertNull(data.remainingCalories)
        assertEquals(0.0, data.consumedCalories!!, 0.01)
        assertNull(data.content.numberOrNull("fat"))
    }
    @Test fun retainsNegativeRemainingAndUnknownFutureFields() {
        val data = GarminNutrition.parse("2026-10-27", JSONObject("""{"dailyNutritionContent":{"calories":2500},"dailyNutritionGoals":{"adjustedCalories":2000},"futureField":{"test":5}}"""))
        assertEquals(-500.0, data.remainingCalories!!, 0.01)
        assertEquals(5, data.root.getJSONObject("futureField").getInt("test"))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsDateMismatch() {
        GarminNutrition.parse("2026-10-27", JSONObject("""{"mealDate":"2026-10-26"}"""))
    }
}
