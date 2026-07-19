package com.medminder.app.feature.hidden

import com.medminder.app.domain.model.HiddenIntakeLog
import com.medminder.app.domain.model.IntakeLogOperation
import java.time.LocalTime
import java.time.ZoneId

internal object HiddenLogSuspicionAnalyzer {
    private const val PRE_TAKEN_TIME_CHANGE_REASON = "שינוי שעה שהוקדם לפני תרופה אחרת שנלקחה"

    fun buildSuspicionReasons(
        logs: List<HiddenIntakeLog>,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Map<String, List<String>> {
        if (logs.isEmpty()) return emptyMap()
        val takenMarkers = logs.filter { it.isTakenMarker() }
        if (takenMarkers.isEmpty()) return emptyMap()

        val reasons = mutableMapOf<String, MutableSet<String>>()
        logs.asSequence()
            .filter { it.operationType == IntakeLogOperation.UPDATE }
            .forEach { log ->
                val timeChange = parseLocalTimeChange(log.operationDetails) ?: return@forEach
                if (!timeChange.newTime.isBefore(timeChange.oldTime)) return@forEach
                val updateDate = log.takenAt.atZone(zoneId).toLocalDate()
                val movedBeforeTakenMedication = takenMarkers.any { takenLog ->
                    if (takenLog.medicationId == log.medicationId) return@any false
                    val takenAt = takenLog.takenAt.atZone(zoneId)
                    if (takenAt.toLocalDate() != updateDate) return@any false
                    val takenTime = takenAt.toLocalTime()
                    timeChange.newTime.isBefore(takenTime)
                }
                if (movedBeforeTakenMedication) {
                    reasons.getOrPut(log.id) { mutableSetOf() }.add(PRE_TAKEN_TIME_CHANGE_REASON)
                }
            }
        return reasons.mapValues { it.value.toList() }
    }

    private fun HiddenIntakeLog.isTakenMarker(): Boolean {
        return (operationType == IntakeLogOperation.CHANGE && operationDetails == "status:PENDING->TAKEN") ||
            (operationType == IntakeLogOperation.ADD &&
                operationDetails in setOf("as_needed_intake_added", "one_time_intake_added"))
    }

    private fun parseLocalTimeChange(details: String?): LocalTimeChange? {
        if (details.isNullOrBlank() || !details.startsWith("localTime:")) return null
        val parts = details.removePrefix("localTime:").split("->", limit = 2)
        if (parts.size != 2) return null
        val oldTime = runCatching { LocalTime.parse(parts[0]) }.getOrNull() ?: return null
        val newTime = runCatching { LocalTime.parse(parts[1]) }.getOrNull() ?: return null
        return LocalTimeChange(oldTime = oldTime, newTime = newTime)
    }

    private data class LocalTimeChange(
        val oldTime: LocalTime,
        val newTime: LocalTime
    )
}
