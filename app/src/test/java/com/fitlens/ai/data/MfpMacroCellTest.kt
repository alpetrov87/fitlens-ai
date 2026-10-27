package com.fitlens.ai.data

import org.junit.Assert.*
import org.junit.Test

class MfpMacroCellTest {
    private val date = "2026-10-27"
    private val url = "https://www.myfitnesspal.com/food/diary/synthetic?date=$date"
    private fun table(carbs: String, fat: String = "6.25", protein: String = "12.5", sodium: String = "2,500") = """
        <table id=diary-table><tr class=meal_header><td>Lunch</td><td>Calories kcal</td><td>Carbs g</td><td>Fat g</td><td>Protein g</td><td>Sodium mg</td></tr>
        <tr><td>Synthetic meal</td><td>300</td><td>$carbs</td><td>$fat</td><td>$protein</td><td>$sodium</td></tr>
        <tr class=total><td>Totals</td><td>300</td><td>$carbs</td><td>$fat</td><td>$protein</td><td>$sodium</td></tr></table>
    """
    private fun checkMacros(html: String) {
        val data = parseMfpDiary(html, url, date)
        for (nutrients in listOf(data.foods.single().nutrients, data.totals)) {
            assertEquals(32.0, nutrients.getValue("carbs"), 0.0)
            assertEquals(6.25, nutrients.getValue("fat"), 0.0)
            assertEquals(12.5, nutrients.getValue("protein"), 0.0)
            assertEquals(2500.0, nutrients.getValue("sodium"), 0.0)
        }
    }
    @Test fun nativeExtractionSeparatesGramValuesFromHiddenPercentages() {
        val html = table("<span class=macro-value>32</span><span class=macro-percentage style='display:none'>64</span>",
            "<span class=macro-percentage>21%</span><span class=macro-value>6.25</span>",
            "<span class=macro-value>12.5</span><span class=macro-percentage>15</span>")
        checkMacros(mfpDiaryTables(html))
    }
    @Test fun directParserAlsoUnderstandsMacroSpans() {
        checkMacros(table("<span class=macro-value>32</span> <span class=macro-percentage>64</span>"))
    }
    @Test fun doesNotTurnAmbiguousAdjacentNumbersIntoOneLargeNumber() {
        val data = parseMfpDiary(table("32 64"), url, date)
        assertFalse(data.foods.single().nutrients.containsKey("carbs"))
        assertFalse(data.totals.containsKey("carbs"))
    }
    @Test fun preservesThousandsAndDecimalSeparators() {
        val data = parseMfpDiary(table("32,5", fat = "6.25", protein = "12,5", sodium = "2\u202f500"), url, date)
        assertEquals(32.5, data.totals.getValue("carbs"), 0.0)
        assertEquals(12.5, data.totals.getValue("protein"), 0.0)
        assertEquals(2500.0, data.totals.getValue("sodium"), 0.0)
    }
    @Test(expected = IllegalArgumentException::class) fun oldSnapshotsRequireReimportRatherThanGuessingTheirValues() {
        val old = mfpSnapshot(MfpDiaryPreview(date, emptyList(), mapOf("carbs" to 3264.0)))
        old.remove("parserVersion")
        readMfpSnapshot(old.toString())
    }
    @Test fun correctedSnapshotKeepsItsVersionAndExactValues() {
        val source = parseMfpDiary(table("<span class=macro-value>32</span><span class=macro-percentage>64</span>"), url, date)
        val json = mfpSnapshot(source)
        assertEquals(MFP_DIARY_PARSER_VERSION, json.getInt("parserVersion"))
        assertEquals(source, readMfpSnapshot(json.toString()))
    }
}
