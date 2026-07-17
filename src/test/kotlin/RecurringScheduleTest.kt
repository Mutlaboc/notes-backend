package com.example.mutlabocnotes.notes

import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecurringScheduleTest {
    @Test
    fun dailyScheduleSkipsElapsedOccurrences() {
        val start = millis(2026, 7, 1, 9)
        val now = millis(2026, 7, 4, 10)

        assertEquals(millis(2026, 7, 5, 9), nextOccurrenceStart(start, now, RepeatRule.DAILY))
    }

    @Test
    fun monthlyScheduleKeepsOriginalAnchorAfterShortMonth() {
        val january = millis(2026, 1, 31, 9)
        val february = nextOccurrenceStart(january, millis(2026, 2, 1, 0), RepeatRule.MONTHLY, 31)
        val march = nextOccurrenceStart(february, february, RepeatRule.MONTHLY, 31)

        assertEquals(millis(2026, 2, 28, 9), february)
        assertEquals(millis(2026, 3, 31, 9), march)
        assertTrue(march > february)
    }

    private fun millis(year: Int, month: Int, day: Int, hour: Int): Long =
        LocalDateTime.of(year, month, day, hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
}
