package com.medminder.app.domain.model

import java.time.Instant

data class HiddenIntakeLog(
    val id: String,
    val doseEventId: String?,
    val medicationId: String,
    val medicationName: String,
    val operationType: IntakeLogOperation,
    val operationDetails: String?,
    val actionSource: ActionSource,
    val takenAt: Instant
)

enum class IntakeLogOperation {
    ADD,
    UPDATE,
    DELETE,
    CHANGE
}
