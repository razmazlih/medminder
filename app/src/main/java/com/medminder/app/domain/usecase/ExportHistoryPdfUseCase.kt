package com.medminder.app.domain.usecase

import android.content.ContentResolver
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.medminder.app.core.i18n.displayLabelHe
import com.medminder.app.data.local.dao.SettingsDao
import com.medminder.app.domain.model.DoseEvent
import com.medminder.app.domain.model.DoseStatus
import com.medminder.app.domain.model.HiddenIntakeLog
import com.medminder.app.domain.repository.MedicationRepository
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class ExportHistoryPdfUseCase @Inject constructor(
    private val repository: MedicationRepository,
    private val settingsDao: SettingsDao
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
            .sortedWith(compareByDescending<DoseEvent> { it.localDate }.thenByDescending { it.localTime })
        val asNeededOrOneTimeMedicationIds = repository.observeMedications(ownerProfileId).first()
            .asSequence()
            .filter { it.times.isEmpty() }
            .map { it.id }
            .toSet()
        val cloudDeletionNotices = repository.observeHiddenIntakeLogs(ownerProfileId).first()
            .toCloudDeletionRows()
        val cloudDeletedDoseEventIds = cloudDeletionNotices
            .asSequence()
            .filter { it.recordType == "DOSE_EVENT" }
            .map { it.recordId }
            .toSet()
        val cloudDeletedMedicationIds = cloudDeletionNotices
            .asSequence()
            .filter { it.recordType == "MEDICATION" }
            .map { it.recordId }
            .toSet()
        val reportDate = specificDate ?: from
        val totalEvents = events.size
        val takenCount = events.count { it.status == DoseStatus.TAKEN }
        val pendingCount = events.count { it.status == DoseStatus.PENDING }
        val displayName = settingsDao.getUserProfile()?.displayName?.trim().orEmpty()
        val reportTitle = if (displayName.isNotEmpty()) "$displayName האלופה" else "האלופה"

        val document = PdfDocument()
        try {
            val pageWidth = 842
            val pageHeight = 1191
            val marginX = 42f
            val marginTop = 40f
            val rowHeight = 26f
            val dateSectionHeight = 24f
            val contentWidth = pageWidth - marginX * 2
            
            // Column Widths
            val dateColWidth = 90f
            val schedTimeColWidth = 80f
            val actualTimeColWidth = 80f
            val diffColWidth = 60f
            val medColWidth = 190f
            val typeColWidth = 150f
            // statusColWidth fills the rest

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1F2937")
                textSize = 28f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#4B5563")
                textSize = 14f
            }
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#374151")
                textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#111827")
                textSize = 13f
            }
            val tableHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#111827")
                textSize = 13f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val dateSectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0F172A")
                textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val rowTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1F2937")
                textSize = 12f
            }
            val diffLatePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.RED
                textSize = 12f
            }
            val diffEarlyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#008000") // Green
                textSize = 12f
            }
            val medicationFixedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#2E7D32")
                textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val medicationAsNeededPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1565C0")
                textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val statusTakenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#065F46")
                textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val statusPendingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#92400E")
                textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val statusCloudDeletedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#B91C1C")
                textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0EA5E9") }
            val tableHeaderBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E0F2FE") }
            val dateSectionBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#EEF2FF") }
            val rowEvenBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F8FAFC") }
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#CBD5E1")
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }

            fun truncateToWidth(text: String, maxWidth: Float, paint: Paint): String {
                if (paint.measureText(text) <= maxWidth) return text
                val ellipsis = "..."
                val ellipsisWidth = paint.measureText(ellipsis)
                var end = text.length
                while (end > 0 && paint.measureText(text, 0, end) + ellipsisWidth > maxWidth) {
                    end--
                }
                return if (end <= 0) ellipsis else text.substring(0, end) + ellipsis
            }

            fun drawPageFooter(canvas: android.graphics.Canvas, pageNum: Int) {
                val footerText = "עמוד $pageNum"
                val footerX = pageWidth - marginX - subtitlePaint.measureText(footerText)
                canvas.drawText(footerText, footerX, pageHeight - 20f, subtitlePaint)
            }

            fun drawPageHeader(canvas: android.graphics.Canvas, pageNumber: Int): Float {
                var y = marginTop
                canvas.drawRect(marginX, y - 18f, marginX + 6f, y + 44f, accentPaint)
                canvas.drawText(reportTitle, marginX + 16f, y + 8f, titlePaint)
                y += 34f
                canvas.drawText("תאריך דוח: ${reportDate.format(dateFormatter)}", marginX + 16f, y, subtitlePaint)
                val generatedText = "נוצר בתאריך ${LocalDateTime.now().format(dateTimeFormatter)}"
                val generatedX = pageWidth - marginX - subtitlePaint.measureText(generatedText)
                canvas.drawText(generatedText, generatedX, y, subtitlePaint)
                y += 26f
                if (pageNumber == 1) {
                    val summaryTop = y
                    val summaryBottom = summaryTop + 84f
                    canvas.drawRoundRect(
                        RectF(marginX, summaryTop, pageWidth - marginX, summaryBottom),
                        10f,
                        10f,
                        rowEvenBgPaint
                    )
                    canvas.drawRoundRect(
                        RectF(marginX, summaryTop, pageWidth - marginX, summaryBottom),
                        10f,
                        10f,
                        borderPaint
                    )
                    
                    // Summary layout
                    canvas.drawText("סה\"כ אירועים", marginX + 14f, summaryTop + 24f, labelPaint)
                    canvas.drawText(totalEvents.toString(), marginX + 14f, summaryTop + 48f, valuePaint)
                    
                    canvas.drawText("נלקחו", marginX + 150f, summaryTop + 24f, labelPaint)
                    canvas.drawText(takenCount.toString(), marginX + 150f, summaryTop + 48f, statusTakenPaint)
                    
                    canvas.drawText("ממתינים", marginX + 250f, summaryTop + 24f, labelPaint)
                    canvas.drawText(pendingCount.toString(), marginX + 250f, summaryTop + 48f, statusPendingPaint)
                    canvas.drawText("מחיקות ענן", marginX + 340f, summaryTop + 24f, labelPaint)
                    canvas.drawText(cloudDeletionNotices.size.toString(), marginX + 340f, summaryTop + 48f, valuePaint)
                    
                    val fixedLegend = "קבועה"
                    val asNeededLegend = "לפי צורך/חד-פעמית"
                    val fixedLegendX = pageWidth - marginX - medicationFixedPaint.measureText(fixedLegend)
                    val asNeededLegendX = pageWidth - marginX - medicationAsNeededPaint.measureText(asNeededLegend)
                    canvas.drawText(fixedLegend, fixedLegendX, summaryTop + 24f, medicationFixedPaint)
                    canvas.drawText(asNeededLegend, asNeededLegendX, summaryTop + 48f, medicationAsNeededPaint)

                    val periodText = "טווח: ${from.format(dateFormatter)} - ${to.format(dateFormatter)}"
                    canvas.drawText(periodText, marginX + 380f, summaryTop + 66f, valuePaint)
                    
                    y = summaryBottom + 20f
                }
                
                // Header Background
                canvas.drawRoundRect(
                    RectF(marginX, y, pageWidth - marginX, y + rowHeight),
                    8f,
                    8f,
                    tableHeaderBgPaint
                )
                
                // Headers
                var currentX = marginX + 12f
                canvas.drawText("תאריך", currentX, y + 18f, tableHeaderPaint)
                currentX += dateColWidth
                canvas.drawText("מתוכנן", currentX, y + 18f, tableHeaderPaint)
                currentX += schedTimeColWidth
                canvas.drawText("בפועל", currentX, y + 18f, tableHeaderPaint)
                currentX += actualTimeColWidth
                canvas.drawText("הפרש", currentX, y + 18f, tableHeaderPaint)
                currentX += diffColWidth
                canvas.drawText("תרופה", currentX, y + 18f, tableHeaderPaint)
                currentX += medColWidth
                canvas.drawText("סוג", currentX, y + 18f, tableHeaderPaint)
                currentX += typeColWidth
                canvas.drawText("סטטוס", currentX, y + 18f, tableHeaderPaint)
                
                return y + rowHeight
            }

            var pageNumber = 1
            var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            var canvas = page.canvas
            var y = drawPageHeader(canvas, pageNumber)
            val pageBottom = pageHeight - 50f
            var currentDate: LocalDate? = null
            var eventRowIndex = 0

            events.forEach { event ->
                if (currentDate != event.localDate) {
                    if (y + dateSectionHeight + rowHeight > pageBottom) {
                        drawPageFooter(canvas, pageNumber)
                        document.finishPage(page)
                        pageNumber += 1
                        page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                        canvas = page.canvas
                        y = drawPageHeader(canvas, pageNumber)
                    }
                    currentDate = event.localDate
                    canvas.drawRoundRect(
                        RectF(marginX, y, pageWidth - marginX, y + dateSectionHeight),
                        6f,
                        6f,
                        dateSectionBgPaint
                    )
                    canvas.drawText(
                        "תאריך: ${event.localDate.format(dateFormatter)}",
                        marginX + 10f,
                        y + 16f,
                        dateSectionPaint
                    )
                    y += dateSectionHeight
                }

                if (y + rowHeight > pageBottom) {
                    drawPageFooter(canvas, pageNumber)
                    document.finishPage(page)
                    pageNumber += 1
                    page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                    canvas = page.canvas
                    y = drawPageHeader(canvas, pageNumber)
                }

                if (eventRowIndex % 2 == 0) {
                    canvas.drawRect(marginX, y, pageWidth - marginX, y + rowHeight, rowEvenBgPaint)
                }
                
                // Vertical lines separators
                var lineX = marginX
                canvas.drawLine(lineX, y + rowHeight, pageWidth - marginX, y + rowHeight, borderPaint) // Bottom line
                
                lineX += dateColWidth
                canvas.drawLine(lineX, y, lineX, y + rowHeight, borderPaint)
                lineX += schedTimeColWidth
                canvas.drawLine(lineX, y, lineX, y + rowHeight, borderPaint)
                lineX += actualTimeColWidth
                canvas.drawLine(lineX, y, lineX, y + rowHeight, borderPaint)
                lineX += diffColWidth
                canvas.drawLine(lineX, y, lineX, y + rowHeight, borderPaint)
                lineX += medColWidth
                canvas.drawLine(lineX, y, lineX, y + rowHeight, borderPaint)
                lineX += typeColWidth
                canvas.drawLine(lineX, y, lineX, y + rowHeight, borderPaint)

                // Content
                var currentX = marginX + 10f
                
                // Date
                canvas.drawText(event.localDate.format(shortDateFormatter), currentX, y + 18f, rowTextPaint)
                currentX += dateColWidth
                
                // Scheduled Time
                canvas.drawText(event.localTime.format(timeFormatter), currentX, y + 18f, rowTextPaint)
                currentX += schedTimeColWidth
                
                // Actual Time & Difference
                var actualTimeText = "-"
                var diffText = "-"
                var diffPaint = rowTextPaint
                
                if (event.respondedAt != null) {
                    val actualTime = event.respondedAt.atZone(ZoneId.systemDefault()).toLocalTime()
                    actualTimeText = actualTime.format(timeFormatter)
                    
                    val diffMinutes = Duration.between(event.scheduledAt, event.respondedAt).toMinutes()
                    val diffAbsText = "${abs(diffMinutes)} דק'"
                    diffText = when {
                        diffMinutes > 0 -> "+$diffAbsText"
                        diffMinutes < 0 -> "-$diffAbsText"
                        else -> "בזמן"
                    }
                    
                    if (diffMinutes > 0) {
                        diffPaint = diffLatePaint
                    } else if (diffMinutes < 0) {
                        diffPaint = diffEarlyPaint
                    } else {
                        diffPaint = diffEarlyPaint 
                    }
                }
                
                canvas.drawText(actualTimeText, currentX, y + 18f, rowTextPaint)
                currentX += actualTimeColWidth
                
                canvas.drawText(diffText, currentX, y + 18f, diffPaint)
                currentX += diffColWidth
                
                // Medication Name
                val medicationText = truncateToWidth(event.medicationName, medColWidth - 10f, rowTextPaint)
                val medicationPaint = if (event.medicationId in asNeededOrOneTimeMedicationIds) {
                    medicationAsNeededPaint
                } else {
                    medicationFixedPaint
                }
                canvas.drawText(medicationText, currentX, y + 18f, medicationPaint)
                currentX += medColWidth

                val typeText: String
                val typePaint: Paint
                if (event.medicationId in asNeededOrOneTimeMedicationIds) {
                    typeText = "לפי צורך/חד-פעמית"
                    typePaint = medicationAsNeededPaint
                } else {
                    typeText = "קבועה"
                    typePaint = medicationFixedPaint
                }
                val typeDisplayText = truncateToWidth(typeText, typeColWidth - 8f, typePaint)
                canvas.drawText(typeDisplayText, currentX, y + 18f, typePaint)
                currentX += typeColWidth
                
                // Status
                val isCloudDeleted = event.id in cloudDeletedDoseEventIds || event.medicationId in cloudDeletedMedicationIds
                val statusText: String
                val statusPaint: Paint
                if (isCloudDeleted) {
                    statusText = "נמחק מהענן"
                    statusPaint = statusCloudDeletedPaint
                } else if (event.status == DoseStatus.TAKEN) {
                    statusText = event.status.displayLabelHe()
                    statusPaint = statusTakenPaint
                } else {
                    statusText = event.status.displayLabelHe()
                    statusPaint = statusPendingPaint
                }
                canvas.drawText(
                    statusText,
                    currentX,
                    y + 18f,
                    statusPaint
                )
                
                y += rowHeight
                eventRowIndex += 1
            }

            if (cloudDeletionNotices.isNotEmpty()) {
                if (y + rowHeight > pageBottom) {
                    drawPageFooter(canvas, pageNumber)
                    document.finishPage(page)
                    pageNumber += 1
                    page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                    canvas = page.canvas
                    y = drawPageHeader(canvas, pageNumber)
                }
                canvas.drawRoundRect(
                    RectF(marginX, y, pageWidth - marginX, y + rowHeight),
                    8f,
                    8f,
                    tableHeaderBgPaint
                )
                canvas.drawText("אירועי מחיקה מהענן", marginX + 10f, y + 18f, tableHeaderPaint)
                y += rowHeight

                cloudDeletionNotices.forEach { notice ->
                    if (y + rowHeight > pageBottom) {
                        drawPageFooter(canvas, pageNumber)
                        document.finishPage(page)
                        pageNumber += 1
                        page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                        canvas = page.canvas
                        y = drawPageHeader(canvas, pageNumber)
                    }
                    val line = "${notice.deletedAtLabel} • ${notice.recordTypeLabel} • ${notice.medicationName}"
                    canvas.drawText(
                        truncateToWidth(line, contentWidth - 16f, rowTextPaint),
                        marginX + 8f,
                        y + 18f,
                        statusCloudDeletedPaint
                    )
                    y += rowHeight
                }
            }

            drawPageFooter(canvas, pageNumber)
            document.finishPage(page)

            resolver.openOutputStream(uri)?.use { output ->
                document.writeTo(output)
            } ?: error("Failed to open output stream")
        } finally {
            document.close()
        }
    }

    private fun List<DoseEvent>.filterBy(
        medicationId: String?,
        specificDate: LocalDate?
    ): List<DoseEvent> {
        return asSequence()
            .filter { medicationId == null || it.medicationId == medicationId }
            .filter { specificDate == null || it.localDate == specificDate }
            .toList()
    }

    private fun List<HiddenIntakeLog>.toCloudDeletionRows(): List<CloudDeletionPdfRow> {
        return asSequence()
            .mapNotNull { log ->
                val details = log.operationDetails ?: return@mapNotNull null
                if (!details.startsWith("cloud_deleted:")) return@mapNotNull null
                val parts = details.split(":")
                if (parts.size < 4) return@mapNotNull null
                val deletedAtEpochMs = parts[3].toLongOrNull() ?: return@mapNotNull null
                val recordTypeLabel = when (parts[1]) {
                    "MEDICATION" -> "תרופה"
                    "DOSE_EVENT" -> "אירוע נטילה"
                    "HIDDEN_INTAKE_LOG" -> "יומן פנימי"
                    else -> "רשומה"
                }
                CloudDeletionPdfRow(
                    recordType = parts[1],
                    recordId = parts[2],
                    deletedAtEpochMs = deletedAtEpochMs,
                    deletedAtLabel = java.time.Instant.ofEpochMilli(deletedAtEpochMs)
                        .atZone(ZoneId.systemDefault())
                        .format(dateTimeFormatter),
                    medicationName = log.medicationName,
                    recordTypeLabel = recordTypeLabel
                )
            }
            .sortedByDescending { it.deletedAtEpochMs }
            .toList()
    }

    private data class CloudDeletionPdfRow(
        val recordType: String,
        val recordId: String,
        val deletedAtEpochMs: Long,
        val deletedAtLabel: String,
        val medicationName: String,
        val recordTypeLabel: String
    )
}

private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val shortDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM")
private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val dateTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.getDefault())
