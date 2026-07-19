package com.medminder.app.domain.usecase

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleEngine @Inject constructor() {

    fun isDateEligible(date: LocalDate, start: LocalDate, end: LocalDate?, weekdays: List<Int>): Boolean {
        if (date.isBefore(start)) return false
        if (end != null && date.isAfter(end)) return false
        if (weekdays.isEmpty()) return true
        return date.dayOfWeek.value in weekdays
    }

    fun isDateEligibleEveryXDays(
        date: LocalDate,
        start: LocalDate,
        end: LocalDate?,
        intervalDays: Int
    ): Boolean {
        if (date.isBefore(start)) return false
        if (end != null && date.isAfter(end)) return false
        val safeInterval = intervalDays.coerceAtLeast(1)
        val daysFromStart = ChronoUnit.DAYS.between(start, date)
        return daysFromStart % safeInterval.toLong() == 0L
    }

    fun toEpochMs(date: LocalDate, time: LocalTime, zoneId: ZoneId): Long {
        return LocalDateTime.of(date, time).atZone(zoneId).toInstant().toEpochMilli()
    }

    fun generateTimesEveryXHours(
        intervalHours: Int,
        windowStart: LocalTime,
        windowEnd: LocalTime
    ): List<LocalTime> {
        require(intervalHours > 0) { "intervalHours must be greater than 0" }
        require(!windowEnd.isBefore(windowStart)) { "windowEnd must be after or equal to windowStart" }

        val times = mutableListOf<LocalTime>()
        var current = windowStart
        while (!current.isAfter(windowEnd)) {
            times += current
            val next = current.plusHours(intervalHours.toLong())
            if (!next.isAfter(current)) break
            current = next
        }
        return times
    }
}
