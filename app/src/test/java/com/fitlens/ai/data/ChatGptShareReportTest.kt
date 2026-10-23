package com.fitlens.ai.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatGptShareReportTest {
    @Test fun includesAvailableTrainingDataAndEventWithoutCredentials() {
        val report = ChatGptShareReport.build(
            activities = listOf(GarminActivity(42, "Morning Run", "running", "2026-10-22 07:30:00",
                3600.0, 10_000.0, 620, 9_000, 148, 172, 120.0)),
            recovery = listOf(GarminRecoveryDay("2026-10-22", 450, 58, "BALANCED")),
            event = CalendarEvent("event-1", "City Marathon", "2026-11-01", "09:00", "running", "Sofia"),
        )

        assertTrue(report.contains("Morning Run"))
        assertTrue(report.contains("10.00 km"))
        assertTrue(report.contains("HRV 58 ms"))
        assertTrue(report.contains("City Marathon"))
        assertFalse(report.contains("activityId"))
        assertFalse(report.contains("API key", ignoreCase = true))
    }

    @Test fun explainsMissingOptionalData() {
        val activity = GarminActivity(1, "Walk", "walking", "2026-10-21 12:00:00",
            null, null, null, null, null, null, null)
        val report = ChatGptShareReport.build(listOf(activity), emptyList(), null)

        assertTrue(report.contains("Няма избрано събитие"))
        assertTrue(report.contains("Няма налични обобщения за сън и HRV"))
    }
}
