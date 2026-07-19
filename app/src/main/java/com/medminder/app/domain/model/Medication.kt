package com.medminder.app.domain.model

import java.time.LocalDate
import java.time.LocalTime

data class Medication(
    val id: String,
    val ownerProfileId: String,
    val name: String,
    val doseText: String,
    val doseUnit: String,
    val foodRelation: FoodRelation,
    val scheduleMode: ScheduleMode,
    val intervalHours: Int?,
    val windowStartTime: LocalTime?,
    val windowEndTime: LocalTime?,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val notes: String,
    val weekdays: List<Int>,
    val times: List<LocalTime>,
    val isActive: Boolean
)

data class MedicationInput(
    val id: String? = null,
    val ownerProfileId: String,
    val name: String,
    val doseText: String,
    val doseUnit: String,
    val foodRelation: FoodRelation,
    val scheduleMode: ScheduleMode,
    val intervalHours: Int?,
    val windowStartTime: LocalTime?,
    val windowEndTime: LocalTime?,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val notes: String,
    val weekdays: List<Int>,
    val times: List<LocalTime>,
    val isActive: Boolean = true
)
