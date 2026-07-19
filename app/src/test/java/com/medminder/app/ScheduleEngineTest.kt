package com.medminder.app

import com.medminder.app.domain.usecase.ScheduleEngine
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleEngineTest {

    private val engine = ScheduleEngine()

    @Test
    fun `isDateEligible returns true for in-range weekday`() {
        val date = LocalDate.of(2026, 2, 16) // Monday
        val result = engine.isDateEligible(
            date = date,
            start = LocalDate.of(2026, 2, 1),
            end = LocalDate.of(2026, 2, 28),
            weekdays = listOf(1)
        )
        assertTrue(result)
    }

    @Test
    fun `isDateEligible returns false when date outside range`() {
        val result = engine.isDateEligible(
            date = LocalDate.of(2026, 1, 31),
            start = LocalDate.of(2026, 2, 1),
            end = LocalDate.of(2026, 2, 28),
            weekdays = emptyList()
        )
        assertFalse(result)
    }

    @Test
    fun `isDateEligible treats empty weekdays as every day`() {
        val result = engine.isDateEligible(
            date = LocalDate.of(2026, 2, 18),
            start = LocalDate.of(2026, 2, 1),
            end = LocalDate.of(2026, 2, 28),
            weekdays = emptyList()
        )

        assertTrue(result)
    }

    @Test
    fun `toEpochMs handles DST zone conversion`() {
        val epoch = engine.toEpochMs(
            date = LocalDate.of(2026, 3, 8),
            time = LocalTime.of(8, 0),
            zoneId = ZoneId.of("America/New_York")
        )
        assertTrue(epoch > 0)
    }

    @Test
    fun `generateTimesEveryXHours returns expected sequence`() {
        val result = engine.generateTimesEveryXHours(
            intervalHours = 8,
            windowStart = LocalTime.of(8, 0),
            windowEnd = LocalTime.of(22, 0)
        )

        assertEquals(
            listOf(LocalTime.of(8, 0), LocalTime.of(16, 0)),
            result
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `generateTimesEveryXHours fails when end before start`() {
        engine.generateTimesEveryXHours(
            intervalHours = 8,
            windowStart = LocalTime.of(20, 0),
            windowEnd = LocalTime.of(8, 0)
        )
    }

    @Test
    fun `isDateEligibleEveryXDays returns true on matching cadence`() {
        val result = engine.isDateEligibleEveryXDays(
            date = LocalDate.of(2026, 2, 7),
            start = LocalDate.of(2026, 2, 1),
            end = null,
            intervalDays = 3
        )

        assertTrue(result)
    }

    @Test
    fun `isDateEligibleEveryXDays returns false off cadence`() {
        val result = engine.isDateEligibleEveryXDays(
            date = LocalDate.of(2026, 2, 8),
            start = LocalDate.of(2026, 2, 1),
            end = null,
            intervalDays = 3
        )

        assertFalse(result)
    }

    @Test
    fun `isDateEligibleEveryXDays coerces non-positive interval to one day`() {
        val result = engine.isDateEligibleEveryXDays(
            date = LocalDate.of(2026, 2, 8),
            start = LocalDate.of(2026, 2, 1),
            end = null,
            intervalDays = 0
        )

        assertTrue(result)
    }
}
