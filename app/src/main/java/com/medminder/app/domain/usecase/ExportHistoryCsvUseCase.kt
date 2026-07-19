package com.medminder.app.domain.usecase

import android.content.ContentResolver
import android.net.Uri
import com.medminder.app.core.i18n.displayLabelHe
import com.medminder.app.domain.model.HiddenIntakeLog
import com.medminder.app.domain.repository.MedicationRepository
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class ExportHistoryCsvUseCase @Inject constructor(
    private val repository: MedicationRepository
) {
    suspend operator fun invoke(
        ownerProfileId: String,
        from: LocalDate,
        to: LocalDate,
        medicationId: String?,
        specificDate: LocalDate?,
        resolver: ContentResolver,
        uri: Uri
    ): Result<Unit> = runCatching {
        val events = repository.observeHistory(ownerProfileId, from, to).first()
            .filterBy(medicationId, specificDate)
            .sortedWith(compareByDescending<com.medminder.app.domain.model.DoseEvent> { it.localDate }.thenByDescending { it.localTime })
        val cloudDeletionNotices = repository.observeHiddenIntakeLogs(ownerProfileId).first()
            .toCloudDeletionNotices()
        resolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
            writer.appendLine("תאריך,שעה,תרופה,סטטוס,מקור")
            events.forEach { event ->
                writer.appendLine(
                    listOf(
                        event.localDate.toString(),
                        event.localTime.toString(),
                        event.medicationName,
                        event.status.displayLabelHe(),
                        event.actionSource?.displayLabelHe().orEmpty()
                    ).joinToString(",")
                )
            }
            if (cloudDeletionNotices.isNotEmpty()) {
                writer.appendLine("")
                writer.appendLine("אירועי סנכרון ענן")
                writer.appendLine("תאריך,שעה,פרטים")
                cloudDeletionNotices.forEach { notice ->
                    writer.appendLine(
                        listOf(
                            notice.deletedDate,
                            notice.deletedTime,
                            "נמחק מהענן: ${notice.recordTypeLabel} / ${notice.medicationName}"
                        ).joinToString(",")
                    )
                }
            }
        } ?: error("Failed to open output stream")
    }

    private fun List<com.medminder.app.domain.model.DoseEvent>.filterBy(
        medicationId: String?,
        specificDate: LocalDate?
    ): List<com.medminder.app.domain.model.DoseEvent> {
        return asSequence()
            .filter { medicationId == null || it.medicationId == medicationId }
            .filter { specificDate == null || it.localDate == specificDate }
            .toList()
    }

    private fun List<HiddenIntakeLog>.toCloudDeletionNotices(): List<CloudDeletionCsvRow> {
        val formatterDate = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val formatterTime = DateTimeFormatter.ofPattern("HH:mm")
        return asSequence()
            .mapNotNull { log ->
                val details = log.operationDetails ?: return@mapNotNull null
                if (!details.startsWith("cloud_deleted:")) return@mapNotNull null
                val parts = details.split(":")
                if (parts.size < 4) return@mapNotNull null
                val deletedAtEpochMs = parts[3].toLongOrNull() ?: return@mapNotNull null
                val deletedAt = java.time.Instant.ofEpochMilli(deletedAtEpochMs).atZone(ZoneId.systemDefault())
                val recordTypeLabel = when (parts[1]) {
                    "MEDICATION" -> "תרופה"
                    "DOSE_EVENT" -> "אירוע נטילה"
                    "HIDDEN_INTAKE_LOG" -> "יומן פנימי"
                    else -> "רשומה"
                }
                CloudDeletionCsvRow(
                    deletedDate = deletedAt.format(formatterDate),
                    deletedTime = deletedAt.format(formatterTime),
                    medicationName = log.medicationName,
                    recordTypeLabel = recordTypeLabel
                )
            }
            .sortedByDescending { "${it.deletedDate} ${it.deletedTime}" }
            .toList()
    }

    private data class CloudDeletionCsvRow(
        val deletedDate: String,
        val deletedTime: String,
        val medicationName: String,
        val recordTypeLabel: String
    )
}
