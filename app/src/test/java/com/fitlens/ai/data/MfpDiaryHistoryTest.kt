package com.fitlens.ai.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class MfpDiaryHistoryTest {
    private val end = LocalDate.of(2026, 10, 27)
    @Test fun firstImportHasThirtyDistinctDatesIncludingToday() {
        val dates = mfpSyncDates(end, emptySet())
        assertEquals(30, dates.size)
        assertEquals(30, dates.toSet().size)
        assertEquals("2026-10-27", dates.first())
        assertEquals("2026-09-28", dates.last())
    }
    @Test fun nextDayOnlyRefreshesRecentDaysWhenOlderDaysAreCached() {
        val old = mfpSyncDates(end, emptySet()).toSet()
        assertEquals(listOf("2026-10-28", "2026-10-27", "2026-10-26"), mfpSyncDates(end.plusDays(1), old))
    }
    @Test fun fillsHolesRatherThanTrustingOldestAndNewestDates() {
        val old = mfpSyncDates(end, emptySet()).toSet() - "2026-10-10"
        assertEquals(listOf("2026-10-27", "2026-10-26", "2026-10-25", "2026-10-10"), mfpSyncDates(end, old))
    }
    @Test fun olderCachedDaysCannotHideMissingRecentDays() {
        assertEquals(30, mfpSyncDates(end, setOf("2025-10-27", "2026-09-27")).size)
    }
    @Test fun explicitFullRefreshStillDownloadsAllDays() {
        val old = mfpSyncDates(end, emptySet()).toSet()
        assertEquals(30, mfpSyncDates(end, old, full = true).size)
    }
    @Test fun decodedSnapshotsRetainMealsAndMissingNutrients() {
        val original = MfpDiaryPreview("2026-10-27", listOf(
            MfpFoodPreview("Lunch", "Synthetic food", mapOf("calories" to 100.0, "sodium" to 20.0))
        ), mapOf("calories" to 100.0))
        assertEquals(original, readMfpSnapshot(mfpSnapshot(original).toString()))
        assertFalse(readMfpSnapshot(mfpSnapshot(original).toString()).totals.containsKey("protein"))
    }
    @Test fun zeroDayIsStillAValidCachedDay() {
        val day = MfpDiaryPreview("2026-10-27", emptyList(), mapOf("calories" to 0.0))
        assertEquals(day, readMfpSnapshot(mfpSnapshot(day).toString()))
    }
    @Test(expected = Exception::class) fun corruptSnapshotIsNotAZeroDay() {
        readMfpSnapshot("""{"date":"invalid","foods":[],"totals":{}}""")
    }
}
