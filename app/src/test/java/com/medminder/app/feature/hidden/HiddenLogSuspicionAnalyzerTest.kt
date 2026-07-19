package com.medminder.app.feature.hidden

import com.medminder.app.domain.model.ActionSource
import com.medminder.app.domain.model.HiddenIntakeLog
import com.medminder.app.domain.model.IntakeLogOperation
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HiddenLogSuspicionAnalyzerTest {
    private val zoneId = ZoneId.of("UTC")

    @Test
    fun `marks update as suspicious when time moved to before another taken medication`() {
        val logs = listOf(
            log(
                id = "taken_b",
                medicationId = "med_b",
                operationType = IntakeLogOperation.CHANGE,
                operationDetails = "status:PENDING->TAKEN",
                takenAt = "2026-03-10T10:00:00Z"
            ),
            log(
                id = "update_a",
                medicationId = "med_a",
                operationType = IntakeLogOperation.UPDATE,
                operationDetails = "localTime:11:30->09:00",
                takenAt = "2026-03-10T10:05:00Z"
            )
        )

        val reasons = HiddenLogSuspicionAnalyzer.buildSuspicionReasons(logs, zoneId)

        assertTrue(reasons.containsKey("update_a"))
        assertEquals("שינוי שעה שהוקדם לפני תרופה אחרת שנלקחה", reasons["update_a"]?.single())
    }

    @Test
    fun `does not mark update when time was not moved earlier`() {
        val logs = listOf(
            log(
                id = "taken_b",
                medicationId = "med_b",
                operationType = IntakeLogOperation.CHANGE,
                operationDetails = "status:PENDING->TAKEN",
                takenAt = "2026-03-10T10:00:00Z"
            ),
            log(
                id = "update_a",
                medicationId = "med_a",
                operationType = IntakeLogOperation.UPDATE,
                operationDetails = "localTime:09:00->10:30",
                takenAt = "2026-03-10T10:05:00Z"
            )
        )

        val reasons = HiddenLogSuspicionAnalyzer.buildSuspicionReasons(logs, zoneId)

        assertFalse(reasons.containsKey("update_a"))
    }

    @Test
    fun `does not mark update when comparison is against same medication`() {
        val logs = listOf(
            log(
                id = "taken_a",
                medicationId = "med_a",
                operationType = IntakeLogOperation.CHANGE,
                operationDetails = "status:PENDING->TAKEN",
                takenAt = "2026-03-10T10:00:00Z"
            ),
            log(
                id = "update_a",
                medicationId = "med_a",
                operationType = IntakeLogOperation.UPDATE,
                operationDetails = "localTime:11:30->09:00",
                takenAt = "2026-03-10T10:05:00Z"
            )
        )

        val reasons = HiddenLogSuspicionAnalyzer.buildSuspicionReasons(logs, zoneId)

        assertFalse(reasons.containsKey("update_a"))
    }

    @Test
    fun `counts add-intake logs as taken markers for suspicious detection`() {
        val logs = listOf(
            log(
                id = "taken_b",
                medicationId = "med_b",
                operationType = IntakeLogOperation.ADD,
                operationDetails = "as_needed_intake_added",
                takenAt = "2026-03-10T08:00:00Z"
            ),
            log(
                id = "update_a",
                medicationId = "med_a",
                operationType = IntakeLogOperation.UPDATE,
                operationDetails = "localTime:09:30->07:30",
                takenAt = "2026-03-10T08:05:00Z"
            )
        )

        val reasons = HiddenLogSuspicionAnalyzer.buildSuspicionReasons(logs, zoneId)

        assertTrue(reasons.containsKey("update_a"))
    }

    @Test
    fun `does not mark update when taken marker is on different local date`() {
        val logs = listOf(
            log(
                id = "taken_b",
                medicationId = "med_b",
                operationType = IntakeLogOperation.CHANGE,
                operationDetails = "status:PENDING->TAKEN",
                takenAt = "2026-03-11T00:15:00Z"
            ),
            log(
                id = "update_a",
                medicationId = "med_a",
                operationType = IntakeLogOperation.UPDATE,
                operationDetails = "localTime:11:30->09:00",
                takenAt = "2026-03-10T23:55:00Z"
            )
        )

        val reasons = HiddenLogSuspicionAnalyzer.buildSuspicionReasons(logs, zoneId)

        assertFalse(reasons.containsKey("update_a"))
    }

    @Test
    fun `ignores malformed localTime update payload`() {
        val logs = listOf(
            log(
                id = "taken_b",
                medicationId = "med_b",
                operationType = IntakeLogOperation.CHANGE,
                operationDetails = "status:PENDING->TAKEN",
                takenAt = "2026-03-10T10:00:00Z"
            ),
            log(
                id = "update_a",
                medicationId = "med_a",
                operationType = IntakeLogOperation.UPDATE,
                operationDetails = "localTime:bad->09:00",
                takenAt = "2026-03-10T10:05:00Z"
            )
        )

        val reasons = HiddenLogSuspicionAnalyzer.buildSuspicionReasons(logs, zoneId)

        assertFalse(reasons.containsKey("update_a"))
    }

    private fun log(
        id: String,
        medicationId: String,
        operationType: IntakeLogOperation,
        operationDetails: String?,
        takenAt: String
    ): HiddenIntakeLog {
        return HiddenIntakeLog(
            id = id,
            doseEventId = null,
            medicationId = medicationId,
            medicationName = medicationId,
            operationType = operationType,
            operationDetails = operationDetails,
            actionSource = ActionSource.APP,
            takenAt = Instant.parse(takenAt)
        )
    }
}
