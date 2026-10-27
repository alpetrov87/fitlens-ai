package com.fitlens.ai.data

import org.junit.Assert.*
import org.junit.Test

class MfpNativeDiaryTest {
    private val page = "https://www.myfitnesspal.com/food/diary/synthetic?date=2026-10-27"
    private val html = """<html><script>secretScript</script><form><input type=password value=secretPassword></form>
        <input id=date_selector name=hidden_date_selector value=2026-10-27>
        <table><tr class=meal_header><td>Breakfast</td><td>Calories kcal</td><td>Protein g</td></tr>
        <tr><td>Example A</td><td>100</td><td>5</td></tr></table>
        <table><tr class=meal_header><td>Lunch</td><td></td><td></td></tr>
        <tr><td>Example B</td><td>200</td><td>10</td></tr>
        <tr class=total><td>Totals</td><td>300</td><td>15</td></tr></table></html>"""
    @Test fun nativeHtmlReadsMultipleMealsAndPreservesDateWithoutLoginFields() {
        val sanitized = mfpDiaryTables(html)
        assertFalse(sanitized.contains("secretPassword"))
        assertFalse(sanitized.contains("secretScript"))
        val diary = parseMfpDiary(sanitized, page, "2026-10-27")
        assertEquals(listOf("Breakfast", "Lunch"), diary.foods.map { it.meal })
        assertEquals(300.0, diary.totals.getValue("calories"), 0.0)
    }
    @Test fun nestedLayoutAndUnrelatedTableDoNotDuplicateFoods() {
        val wrapped = "<table><tr><td>" + html + "</td></tr></table><table><tr><td>Other data</td><td>99</td></tr></table>"
        val sanitized = mfpDiaryTables(wrapped)
        assertFalse(sanitized.contains("Other data"))
        assertEquals(2, parseMfpDiary(sanitized, page, "2026-10-27").foods.size)
    }
    @Test(expected = MfpDiaryDateMismatch::class) fun mismatchedServerDateCannotOverwriteRequestedDay() {
        parseMfpDiary(mfpDiaryTables(html.replace("value=2026-10-27", "value=2026-10-26")), page, "2026-10-27")
    }
    @Test(expected = IllegalStateException::class) fun loginPageIsNotAnEmptyDiary() {
        parseMfpDiary(mfpDiaryTables("<form><input name=password></form>"), page, "2026-10-27")
    }
}
