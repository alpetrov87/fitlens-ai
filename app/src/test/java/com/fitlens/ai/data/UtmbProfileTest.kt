package com.fitlens.ai.data

import org.junit.Assert.*
import org.junit.Test

class UtmbProfileTest {
    private val slug = "1234567.sample.runner"
    private fun page(indexes: String = """[{"piCategory":"general","index":456},{"piCategory":"20k","index":480},{"piCategory":"50k","index":440},{"piCategory":"100k","index":0},{"piCategory":"100m","index":null}]""") =
        """<link rel=canonical href="https://utmb.world/en/runner/$slug"><script id="__NEXT_DATA__" type="application/json">{"props":{"pageProps":{"fullname":"Synthetic Runner","performanceIndexes":$indexes}},"query":{"uri":"$slug"}}</script>"""

    @Test fun importsOnlyKnownProfileCategoriesAndTreatsZeroAsUnavailable() {
        val result = parseUtmbProfile(page(), slug)
        assertEquals("UTMB Index: 456 · 20K: 480 · 50K: 440 · 100K: — · 100M: —", result.score)
        assertNull(result.results)
    }
    @Test fun ignoresUnrelatedRatingsAndDoesNotInventResultsOrIndexes() {
        val html = """<h1>UTMB Index 999</h1><script type=application/json>{"index":999}</script>"""
        assertNull(parseUtmbProfile(html, slug).score)
        assertEquals("UTMB Index: — · 20K: — · 50K: — · 100K: — · 100M: —", parseUtmbProfile(page("[]"), slug).score)
        assertNull(parseUtmbProfile(page().replace("performanceIndexes", "recommendations"), slug).score)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsAnotherRunnerCanonical() {
        parseUtmbProfile(page().replace("href=\"https://utmb.world/en/runner/$slug", "href=\"https://utmb.world/en/runner/999.other.runner"), slug)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsConflictingCategories() {
        parseUtmbProfile(page("""[{"piCategory":"general","index":456},{"piCategory":"general","index":789}]"""), slug)
    }
    @Test fun profileUrlValidationRequiresOfficialHostAndFullRunnerSlug() {
        assertEquals(slug, utmbProfileSlug("https://utmb.world/en/runner/$slug"))
        assertEquals(slug, utmbProfileSlug("https://utmb.world/runner/$slug"))
        for (url in listOf("http://utmb.world/runner/$slug", "https://utmb.world.evil.test/runner/$slug",
            "https://user@utmb.world/runner/$slug", "https://utmb.world:444/runner/$slug",
            "https://utmb.world/runner/1234567", "https://utmb.world/runner/$slug?url=other", "https://utmb.world/account")) {
            assertTrue(url, runCatching { utmbProfileSlug(url) }.isFailure)
        }
        assertNull(utmbSlugFromPath("/en/runner/1234567"))
    }
    @Test(expected = IllegalArgumentException::class) fun deeplyNestedDataIsRejectedBeforeJsonRecursion() {
        parseUtmbProfile(page("[".repeat(40) + "]".repeat(40)), slug)
    }
    @Test fun utmbProfileIsIncludedInBothChatPromptsWithCategoryMeaning() {
        val profile = RunnerProfile("UTMB", "https://utmb.world/en/runner/$slug", parseUtmbProfile(page(), slug).score!!,
            updated = "2026-11-09", source = "public profile")
        for (language in listOf("bg", "en")) {
            val report = ChatGptShareReport.build(emptyList(), emptyList(), null, listOf(profile), language)
            assertTrue(report.contains(profile.url))
            assertTrue(report.contains("UTMB Index: 456"))
            assertTrue(report.contains("100K: —"))
            assertTrue(report.contains("Running Stones"))
        }
    }
}
