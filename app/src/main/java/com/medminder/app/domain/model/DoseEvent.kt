package com.medminder.app.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

data class DoseEvent(
    val id: String,
    val ownerProfileId: String,
    val medicationId: String,
    val medicationName: String,
    val scheduledAt: Instant,
    val localDate: LocalDate,
    val localTime: LocalTime,
    val status: DoseStatus,
    val actionSource: ActionSource?,
    val reminderAttemptCount: Int,
    val snoozeUntil: Instant?,
    val respondedAt: Instant?,
    val updatedAt: Instant? = null
)
