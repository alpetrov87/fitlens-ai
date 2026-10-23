package com.fitlens.ai.data

import org.junit.Assert.assertTrue
import org.junit.Test

class EventDraftTest {
    @Test fun acceptsAllDayAndTimedEvents() {
        EventDraft("Софийски маратон", "2026-11-08").validate()
        EventDraft("Плуване", "2026-11-08", "09:30", "swimming", "Басейн").validate()
    }

    @Test fun rejectsImpossibleDatesAndTimes() {
        assertTrue(runCatching { EventDraft("Събитие", "2026-02-30").validate() }.isFailure)
        assertTrue(runCatching { EventDraft("Събитие", "2026-11-08", "25:00").validate() }.isFailure)
        assertTrue(runCatching { EventDraft("Събитие", "2026-11-08", "9:3").validate() }.isFailure)
    }

    @Test fun rejectsBlankTitles() {
        assertTrue(runCatching { EventDraft("  ", "2026-11-08").validate() }.isFailure)
    }
}
