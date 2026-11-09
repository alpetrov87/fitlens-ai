package com.fitlens.ai.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class UtmbRaceResultTest {
    private val url = "https://utmb.world/utmb-index/races/12345.example.2026"
    private fun race(score: Any? = 312) = JSONObject().put("title", "Example Trail").put("date", "2026-10-03")
        .put("score", score ?: JSONObject.NULL).put("time", "7:33:47").put("distance", "27 km").put("url", url)
    @Test fun structuredRacePreservesScoreWithoutMixingOverallIndexOrRank() {
        val row = utmbRaceRow(race().put("rank", 237))!!
        val card = utmbRaceCardData(row)
        assertEquals(312, card.score)
        assertEquals("Example Trail", card.title)
        assertEquals("2026-10-03", card.date)
        assertTrue(card.details.contains("7:33:47"))
        assertTrue(card.details.contains("27 km"))
        assertEquals(url, card.url)
    }
    @Test fun lockedOrInvalidRaceScoreRemainsUnknown() {
        for (value in listOf(null, 0, -1, 1001, 312.5, "312")) {
            assertNull(utmbRaceCardData(utmbRaceRow(race(value))!!).score)
        }
    }
    @Test fun invalidRaceLinksAndMissingIdentityFieldsAreRejected() {
        assertNull(utmbRaceRow(race().put("url", "https://utmb.world.evil.test/utmb-index/races/123")))
        assertNull(utmbRaceRow(race().put("url", "https://user:secret@utmb.world/utmb-index/races/123")))
        assertNull(utmbRaceRow(race().put("title", "")))
        assertNull(utmbRaceRow(race().put("date", "unknown")))
    }
    @Test fun browserSnapshotStoresHumanReadableRaceScoresAndChatReceivesThem() {
        val snapshot = JSONObject().put("races", org.json.JSONArray().put(race()))
        val preview = parseUtmbBrowserSnapshot(snapshot.toString(), "https://utmb.world/en/runner/123.sample.runner", "123.sample.runner")
        assertEquals(1, preview.rows)
        assertTrue(preview.results!!.contains("UTMB Score: 312"))
        val profile = RunnerProfile("UTMB", "https://utmb.world/en/runner/123.sample.runner", "UTMB Index: 339", preview.results)
        val report = ChatGptShareReport.build(emptyList(), emptyList(), null, listOf(profile))
        assertTrue(report.contains("UTMB Index: 339"))
        assertTrue(report.contains("UTMB Score: 312"))
    }
    @Test fun paginatedResultsMergeAndUnknownScoresCannotEraseKnownScores() {
        val first = utmbRaceRow(race())!!
        val second = utmbRaceRow(race(400).put("url", "https://utmb.world/utmb-index/races/456.another.2026"))!!
        val merged = mergeUtmbRaceRows(first, second)!!
        assertEquals(2, merged.lines().size)
        val preserved = mergeUtmbRaceRows(merged, utmbRaceRow(race(null)))!!
        assertEquals(2, preserved.lines().size)
        assertTrue(preserved.contains("UTMB Score: 312"))
    }
    @Test fun categoryValuesAreNotRaceScoresAndInvalidValuesDoNotBecomeZero() {
        assertEquals(339, utmbIndexValue("UTMB Index: 339 · 20K: 340 · 50K: —", "UTMB Index"))
        assertEquals(340, utmbIndexValue("UTMB Index: 339 · 20K: 340 · 50K: —", "20K"))
        assertNull(utmbIndexValue("50K: —", "50K"))
        assertNull(utmbIndexValue("50K: 0", "50K"))
        assertNull(utmbRaceCardData("2026 Example | place 237 | 7:33:47").score)
    }
}
