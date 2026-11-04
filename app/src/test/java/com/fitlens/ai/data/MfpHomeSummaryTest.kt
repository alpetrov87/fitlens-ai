package com.fitlens.ai.data

import org.junit.Assert.*
import org.junit.Test

class MfpHomeSummaryTest {
    @Test fun yesterdayMustNeverAppearAsToday() {
        val days = listOf(MfpDiaryPreview("2026-11-03", emptyList(), mapOf("calories" to 2150.0)))
        assertNull(mfpHomeDay(days, "2026-11-04"))
        assertEquals(2150.0, mfpHomeDay(days, "2026-11-03")!!.totals.getValue("calories"), 0.0)
    }
    @Test fun totalsAreNotAddedToFoodsAndMissingValuesStayUnknown() {
        val day = MfpDiaryPreview("2026-11-04", listOf(MfpFoodPreview("Lunch", "Test", mapOf("calories" to 100.0, "protein" to 5.0))), mapOf("calories" to 100.0))
        assertEquals(100.0, mfpHomeNutrient(day, "calories")!!, 0.0)
        assertEquals(5.0, mfpHomeNutrient(day, "protein")!!, 0.0)
        assertNull(mfpHomeNutrient(day, "fat"))
    }
    @Test fun readsOnlyExplicitMfpDailyGoalAndPreservesItAcrossSnapshotRoundTrip() {
        val html = """<table><tr class=meal_header><td>Breakfast</td><td>Calories</td><td>Protein</td></tr>
        <tr><td>Test food</td><td>100</td><td>5</td></tr>
        <tr class=total><td>Totals</td><td>100</td><td>5</td></tr>
        <tr class="total alt"><td>Your Daily Goal</td><td>2450</td><td>128</td></tr>
        <tr class="total remaining"><td>Remaining</td><td>2350</td><td>123</td></tr></table>"""
        val day = parseMfpDiary(html, "https://www.myfitnesspal.com/food/diary/example?date=2026-11-04", "2026-11-04")
        assertEquals(2450.0, day.goals.getValue("calories"), 0.0)
        assertEquals(100.0, day.totals.getValue("calories"), 0.0)
        assertEquals(day, readMfpSnapshot(mfpSnapshot(day).toString()))
        val legacy = mfpSnapshot(day).apply { remove("goals") }
        assertTrue(readMfpSnapshot(legacy.toString()).goals.isEmpty())
    }
}
