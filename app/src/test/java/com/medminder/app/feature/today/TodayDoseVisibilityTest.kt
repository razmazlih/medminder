package com.medminder.app.feature.today

import com.medminder.app.domain.model.DoseEvent
import com.medminder.app.domain.model.DoseStatus
import com.medminder.app.domain.model.FoodRelation
import com.medminder.app.domain.model.Medication
import com.medminder.app.domain.model.ScheduleMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayDoseVisibilityTest {

    @Test
    fun `every x days due today stays visible outside regular window`() {
        val today = LocalDate.of(2026, 4, 11)

        val visible = shouldShowTodayDoseItem(
            event = doseEvent(
                localDate = today,
                scheduledAt = Instant.parse("2026-04-11T06:00:00Z")
            ),
            medication = medication(scheduleMode = ScheduleMode.EVERY_X_DAYS),
            today = today,
            recentStart = Instant.parse("2026-04-11T09:00:00Z"),
            upcomingEnd = Instant.parse("2026-04-11T12:00:00Z")
        )

        assertTrue(visible)
    }

    @Test
    fun `every x days dose stays hidden on non due day even inside regular window`() {
        val today = LocalDate.of(2026, 4, 11)

        val visible = shouldShowTodayDoseItem(
            event = doseEvent(
                localDate = today.plusDays(1),
                scheduledAt = Instant.parse("2026-04-12T06:00:00Z")
            ),
            medication = medication(scheduleMode = ScheduleMode.EVERY_X_DAYS),
            today = today,
            recentStart = Instant.parse("2026-04-11T09:00:00Z"),
            upcomingEnd = Instant.parse("2026-04-12T12:00:00Z")
        )

        assertFalse(visible)
    }

    @Test
    fun `fixed time dose outside regular window stays hidden`() {
        val today = LocalDate.of(2026, 4, 11)

        val visible = shouldShowTodayDoseItem(
            event = doseEvent(
                localDate = today,
                scheduledAt = Instant.parse("2026-04-11T06:00:00Z")
            ),
            medication = medication(scheduleMode = ScheduleMode.FIXED_TIMES),
            today = today,
            recentStart = Instant.parse("2026-04-11T09:00:00Z"),
            upcomingEnd = Instant.parse("2026-04-11T12:00:00Z")
        )

        assertFalse(visible)
    }

    private fun medication(scheduleMode: ScheduleMode): Medication {
        return Medication(
            id = "med-1",
            ownerProfileId = "owner-1",
            name = "Medication",
            doseText = "1",
            doseUnit = "pill",
            foodRelation = FoodRelation.NONE,
            scheduleMode = scheduleMode,
            intervalHours = if (scheduleMode == ScheduleMode.EVERY_X_DAYS) 3 else null,
            windowStartTime = null,
            windowEndTime = null,
            startDate = LocalDate.of(2026, 4, 1),
            endDate = null,
            notes = "",
            weekdays = (1..7).toList(),
            times = listOf(LocalTime.of(8, 0)),
            isActive = true
        )
    }

    private fun doseEvent(localDate: LocalDate, scheduledAt: Instant): DoseEvent {
        return DoseEvent(
            id = "dose-1",
            ownerProfileId = "owner-1",
            medicationId = "med-1",
            medicationName = "Medication",
            scheduledAt = scheduledAt,
            localDate = localDate,
            localTime = LocalTime.of(8, 0),
            status = DoseStatus.PENDING,
            actionSource = null,
            reminderAttemptCount = 0,
            snoozeUntil = null,
            respondedAt = null,
            updatedAt = scheduledAt
        )
    }
}
