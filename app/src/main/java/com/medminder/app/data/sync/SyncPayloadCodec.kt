package com.medminder.app.data.sync

import com.medminder.app.data.local.entity.DoseEventEntity
import com.medminder.app.data.local.entity.HiddenIntakeLogEntity
import com.medminder.app.data.local.entity.MedicationEntity
import com.medminder.app.data.local.entity.MedicationTimeEntity
import com.medminder.app.data.local.entity.MedicationWeekdayEntity
import com.medminder.app.data.local.entity.MedicationWithSchedule
import org.json.JSONArray
import org.json.JSONObject

class SyncPayloadCodec {

    data class MedicationPayload(
        val medication: MedicationEntity,
        val weekdays: List<MedicationWeekdayEntity>,
        val times: List<MedicationTimeEntity>
    )

    fun medicationToJson(value: MedicationWithSchedule): String {
        val medication = value.medication
        val obj = JSONObject().apply {
            put("id", medication.id)
            put("ownerProfileId", medication.ownerProfileId)
            put("name", medication.name)
            put("doseText", medication.doseText)
            put("doseUnit", medication.doseUnit)
            put("foodRelation", medication.foodRelation)
            put("scheduleMode", medication.scheduleMode)
            put("intervalHours", medication.intervalHours)
            put("windowStartTime", medication.windowStartTime)
            put("windowEndTime", medication.windowEndTime)
            put("startDate", medication.startDate)
            put("endDate", medication.endDate)
            put("notes", medication.notes)
            put("isActive", medication.isActive)
            put("createdAtEpochMs", medication.createdAtEpochMs)
            put("updatedAtEpochMs", medication.updatedAtEpochMs)
            put(
                "weekdays",
                JSONArray(value.weekdays.sortedBy { it.weekday }.map { it.weekday })
            )
            put(
                "times",
                JSONArray(
                    value.times
                        .sortedBy { it.sortOrder }
                        .map { time ->
                            JSONObject().apply {
                                put("localTime", time.localTime)
                                put("sortOrder", time.sortOrder)
                            }
                        }
                )
            )
        }
        return obj.toString()
    }

    fun medicationFromJson(payloadJson: String): MedicationPayload {
        val obj = JSONObject(payloadJson)
        val medication = MedicationEntity(
            id = obj.getString("id"),
            ownerProfileId = obj.getString("ownerProfileId"),
            name = obj.getString("name"),
            doseText = obj.getString("doseText"),
            doseUnit = obj.getString("doseUnit"),
            foodRelation = obj.getString("foodRelation"),
            scheduleMode = obj.getString("scheduleMode"),
            intervalHours = obj.optIntOrNull("intervalHours"),
            windowStartTime = obj.optStringOrNull("windowStartTime"),
            windowEndTime = obj.optStringOrNull("windowEndTime"),
            startDate = obj.getString("startDate"),
            endDate = obj.optStringOrNull("endDate"),
            notes = obj.getString("notes"),
            isActive = obj.getBoolean("isActive"),
            createdAtEpochMs = obj.getLong("createdAtEpochMs"),
            updatedAtEpochMs = obj.getLong("updatedAtEpochMs")
        )
        val weekdays = buildList {
            val weekdayArray = obj.optJSONArray("weekdays") ?: JSONArray()
            repeat(weekdayArray.length()) { idx ->
                add(
                    MedicationWeekdayEntity(
                        medicationId = medication.id,
                        weekday = weekdayArray.getInt(idx)
                    )
                )
            }
        }
        val times = buildList {
            val timesArray = obj.optJSONArray("times") ?: JSONArray()
            repeat(timesArray.length()) { idx ->
                val timeObj = timesArray.getJSONObject(idx)
                val sortOrder = timeObj.optInt("sortOrder", idx)
                add(
                    MedicationTimeEntity(
                        id = "${medication.id}_time_$sortOrder",
                        medicationId = medication.id,
                        localTime = timeObj.getString("localTime"),
                        sortOrder = sortOrder
                    )
                )
            }
        }
        return MedicationPayload(medication = medication, weekdays = weekdays, times = times)
    }

    fun doseEventToJson(value: DoseEventEntity): String {
        return JSONObject().apply {
            put("id", value.id)
            put("ownerProfileId", value.ownerProfileId)
            put("medicationId", value.medicationId)
            put("medicationName", value.medicationName)
            put("scheduledEpochMs", value.scheduledEpochMs)
            put("localDate", value.localDate)
            put("localTime", value.localTime)
            put("status", value.status)
            put("actionSource", value.actionSource)
            put("reminderAttemptCount", value.reminderAttemptCount)
            put("snoozeUntilEpochMs", value.snoozeUntilEpochMs)
            put("respondedEpochMs", value.respondedEpochMs)
            put("createdAtEpochMs", value.createdAtEpochMs)
            put("updatedAtEpochMs", value.updatedAtEpochMs)
        }.toString()
    }

    fun doseEventFromJson(payloadJson: String): DoseEventEntity {
        val obj = JSONObject(payloadJson)
        return DoseEventEntity(
            id = obj.getString("id"),
            ownerProfileId = obj.getString("ownerProfileId"),
            medicationId = obj.getString("medicationId"),
            medicationName = obj.getString("medicationName"),
            scheduledEpochMs = obj.getLong("scheduledEpochMs"),
            localDate = obj.getString("localDate"),
            localTime = obj.getString("localTime"),
            status = obj.getString("status"),
            actionSource = obj.optStringOrNull("actionSource"),
            reminderAttemptCount = obj.getInt("reminderAttemptCount"),
            snoozeUntilEpochMs = obj.optLongOrNull("snoozeUntilEpochMs"),
            respondedEpochMs = obj.optLongOrNull("respondedEpochMs"),
            createdAtEpochMs = obj.getLong("createdAtEpochMs"),
            updatedAtEpochMs = obj.getLong("updatedAtEpochMs")
        )
    }

    fun hiddenLogToJson(value: HiddenIntakeLogEntity): String {
        return JSONObject().apply {
            put("id", value.id)
            put("ownerProfileId", value.ownerProfileId)
            put("doseEventId", value.doseEventId)
            put("medicationId", value.medicationId)
            put("medicationName", value.medicationName)
            put("operationType", value.operationType)
            put("operationDetails", value.operationDetails)
            put("actionSource", value.actionSource)
            put("takenAtEpochMs", value.takenAtEpochMs)
            put("createdAtEpochMs", value.createdAtEpochMs)
        }.toString()
    }

    fun hiddenLogFromJson(payloadJson: String): HiddenIntakeLogEntity {
        val obj = JSONObject(payloadJson)
        return HiddenIntakeLogEntity(
            id = obj.getString("id"),
            ownerProfileId = obj.getString("ownerProfileId"),
            doseEventId = obj.optStringOrNull("doseEventId"),
            medicationId = obj.getString("medicationId"),
            medicationName = obj.getString("medicationName"),
            operationType = obj.getString("operationType"),
            operationDetails = obj.optStringOrNull("operationDetails"),
            actionSource = obj.getString("actionSource"),
            takenAtEpochMs = obj.getLong("takenAtEpochMs"),
            createdAtEpochMs = obj.getLong("createdAtEpochMs")
        )
    }
}

private fun JSONObject.optStringOrNull(key: String): String? {
    return if (!has(key) || isNull(key)) null else optString(key)
}

private fun JSONObject.optLongOrNull(key: String): Long? {
    return if (!has(key) || isNull(key)) null else optLong(key)
}

private fun JSONObject.optIntOrNull(key: String): Int? {
    return if (!has(key) || isNull(key)) null else optInt(key)
}
