package com.medminder.app.data.repository

import com.medminder.app.domain.model.ScheduleMode
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicationRepositoryImplTest {

    @Test
    fun `every x days due today is generated even when scheduled time already passed`() {
        val shouldCreate = shouldCreatePendingDoseEvent(
            scheduleMode = ScheduleMode.EVERY_X_DAYS,
            scheduledEpochMs = 100L,
            eventDate = LocalDate.of(2026, 4, 11),
            today = LocalDate.of(2026, 4, 11),
            nowEpochMs = 200L
        )

        assertTrue(shouldCreate)
    }

    @Test
    fun `past fixed time event is not generated`() {
        val shouldCreate = shouldCreatePendingDoseEvent(
            scheduleMode = ScheduleMode.FIXED_TIMES,
            scheduledEpochMs = 100L,
            eventDate = LocalDate.of(2026, 4, 11),
            today = LocalDate.of(2026, 4, 11),
            nowEpochMs = 200L
        )

        assertFalse(shouldCreate)
    }

    @Test
    fun `every x days past event is not generated on non due date`() {
        val shouldCreate = shouldCreatePendingDoseEvent(
            scheduleMode = ScheduleMode.EVERY_X_DAYS,
            scheduledEpochMs = 100L,
            eventDate = LocalDate.of(2026, 4, 12),
            today = LocalDate.of(2026, 4, 11),
            nowEpochMs = 200L
        )

        assertFalse(shouldCreate)
    }
}
