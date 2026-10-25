package com.fitlens.ai.data

import org.junit.Assert.*
import org.junit.Test

class ChatGptReportLanguageTest {
    @Test fun englishReportIncludesEventAndRecoveryWithoutTranslatingUserData() {
        val event = CalendarEvent("1", "Планински старт", "2027-05-01", "08:00", "trail_running", "София",
            "https://example.org/race", 21.0, "5 h", 1200)
        val text = ChatGptShareReport.build(emptyList(), listOf(GarminRecoveryDay("2027-04-01", 450, 48, "BALANCED")), event, language = "en")
        assertTrue(text.contains("Reply in English"))
        assertTrue(text.contains("Планински старт"))
        assertTrue(text.contains("Distance: 21.00 km"))
        assertTrue(text.contains("Elevation gain: 1200 m"))
        assertTrue(text.contains("sleep 7 h 30 min"))
        assertTrue(text.contains("HRV 48 ms"))
        assertTrue(text.contains("https://example.org/race"))
    }
    @Test fun bulgarianRemainsAvailableAndOtherLanguagesFallBackToEnglish() {
        assertTrue(ChatGptShareReport.build(emptyList(), emptyList(), null).contains("Отговори на български"))
        assertTrue(ChatGptShareReport.build(emptyList(), emptyList(), null, language = "de").contains("Reply in English"))
    }
}
