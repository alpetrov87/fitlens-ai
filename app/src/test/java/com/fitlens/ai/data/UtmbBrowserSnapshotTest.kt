package com.fitlens.ai.data

import org.junit.Assert.*
import org.junit.Test

class UtmbBrowserSnapshotTest {
    private val slug = "1234567.sample.runner"
    private val page = "https://utmb.world/en/runner/$slug"
    @Test fun onlySelectedMetricsAndRowsAreImported() {
        val raw = """{"password":"DO_NOT_COPY","cookies":"DO_NOT_COPY","indexes":[{"piCategory":"20k","index":450}],"rows":["2026 Example Race | 2:34:56 | UTMB Score 442"]}"""
        val preview = parseUtmbBrowserSnapshot(raw, page, slug)
        assertEquals(1, preview.categories)
        assertEquals(1, preview.rows)
        assertTrue(preview.score!!.contains("20K: 450"))
        assertFalse(preview.toString().contains("DO_NOT_COPY"))
    }
    @Test fun pageGateAllowsProfileTabsButNotOtherRunnersOrLoginOrigins() {
        assertTrue(isUtmbBrowserProfile("$page?tab=results#races", slug))
        for (url in listOf("https://utmb.world/en/runner/999.other.runner", "https://auth.example.test/login",
            "https://utmb.world/login", "http://utmb.world/runner/$slug", "https://utmb.world.evil.test/runner/$slug")) {
            assertFalse(url, isUtmbBrowserProfile(url, slug))
            assertTrue(runCatching { parseUtmbBrowserSnapshot("{}", url, slug) }.isFailure)
        }
    }
    @Test fun incompleteReadDoesNotErasePreviouslyReadCategories() {
        val merged = mergeUtmbCategoryScore("UTMB Index: 480 · 20K: 470 · 50K: 460 · 100K: — · 100M: —",
            "UTMB Index: — · 20K: 475 · 50K: — · 100K: — · 100M: —")!!
        assertTrue(merged.contains("UTMB Index: 480"))
        assertTrue(merged.contains("20K: 475"))
        assertTrue(merged.contains("50K: 460"))
    }
    @Test fun emptyUnsupportedAndOversizedReadsCannotReplaceSavedData() {
        assertTrue(runCatching { parseUtmbBrowserSnapshot("{}", page, slug) }.isFailure)
        assertTrue(runCatching { parseUtmbBrowserSnapshot(" ".repeat(60001), page, slug) }.isFailure)
        val raw = """{"indexes":[{"piCategory":"20k","index":0},{"piCategory":"password","index":400}]}"""
        assertTrue(runCatching { parseUtmbBrowserSnapshot(raw, page, slug) }.isFailure)
        assertTrue(runCatching { parseUtmbBrowserSnapshot("[".repeat(20), page, slug) }.isFailure)
    }
    @Test fun rowStorageStaysWithinExistingProfileLimit() {
        val raw = org.json.JSONObject().put("rows", org.json.JSONArray((1..30).map { "$it " + "x".repeat(500) })).toString()
        val result = parseUtmbBrowserSnapshot(raw, page, slug)
        assertTrue(result.results!!.length <= 4000)
        assertTrue(result.rows in 1..30)
    }
}
