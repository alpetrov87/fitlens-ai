package com.fitlens.ai.data

import org.junit.Assert.*
import org.junit.Test

class MfpDiaryPreviewTest {
    private val url = "https://www.myfitnesspal.com/food/diary/test?date=2026-10-27"
    @Test fun readsVisibleMealsWithoutDoubleCountingDailyTotal() {
        val data = parseMfpDiary("""<table><tr class="meal_header"><td>Breakfast</td><td>Calories</td><td>Protein</td><td>Carbs</td><td>Sodium</td></tr>
            <tr><td>Oats, 50 g</td><td>200</td><td>8.5</td><td>30</td><td>0</td></tr>
            <tr class="bottom"><td>Meal total</td><td>200</td></tr>
            <tr class="meal_header"><td>Lunch</td><td>Calories</td><td>Protein</td><td>Carbs</td><td>Sodium</td></tr>
            <tr><td>Rice</td><td>1,200</td><td>20</td><td>150</td><td>2,000</td></tr>
            <tr class="total"><td>Totals</td><td>1,400</td><td>28.5</td><td>180</td><td>2,000</td></tr><tr class="total alt"><td>Goal</td><td>2200</td><td>90</td></tr><tr class="total remaining"><td>Remaining</td><td>800</td><td>61.5</td></tr></table>""", url, "2026-10-27")
        assertEquals(2, data.foods.size)
        assertEquals(1400.0, data.totals["calories"]!!, 0.01)
        assertEquals(0.0, data.foods[0].nutrients["sodium"]!!, 0.01)
        assertEquals(2000.0, data.foods[1].nutrients["sodium"]!!, 0.01)
        assertFalse(data.totals.containsKey("fat"))
    }
    @Test fun onlyAllowsTrustedHttpsDiaryForExactDate() {
        assertTrue(isMfpDiaryForDate(url, "2026-10-27"))
        listOf("https://myfitnesspal.com.evil.test/food/diary/test?date=2026-10-27", "http://www.myfitnesspal.com/food/diary/test?date=2026-10-27", url+"&date=2026-10-28", url.replace("/food/diary/", "/food/diaryevil/"), url.replace("2026-10-27", "2026-10-26")).forEach {
            assertFalse(it, isMfpDiaryForDate(it, "2026-10-27"))
        }
    }
    @Test(expected = IllegalStateException::class) fun rejectsLoginPageInsteadOfInventingZeroData() {
        parseMfpDiary("<form><input type=password value=secret></form>", url, "2026-10-27")
    }
}
