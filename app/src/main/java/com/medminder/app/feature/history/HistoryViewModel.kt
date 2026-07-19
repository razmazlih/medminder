package com.medminder.app.feature.history

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medminder.app.R
import com.medminder.app.core.Defaults
import com.medminder.app.domain.model.ActionSource
import com.medminder.app.domain.model.DoseEvent
import com.medminder.app.domain.model.DoseStatus
import com.medminder.app.domain.model.IntakeAction
import com.medminder.app.domain.repository.MedicationRepository
import com.medminder.app.domain.usecase.ConfirmDoseActionUseCase
import com.medminder.app.domain.usecase.ExportHistoryPdfUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: MedicationRepository,
    private val confirmDoseActionUseCase: ConfirmDoseActionUseCase,
    private val exportHistoryPdfUseCase: ExportHistoryPdfUseCase
) : ViewModel() {

    private var allItems: List<DoseEvent> = emptyList()
    private var asNeededOrOneTimeMedicationIds: Set<String> = emptySet()
    private var takenTodayCountByMedicationId: Map<String, Int> = emptyMap()
    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val to = today()
            val from = to.minusYears(10)
            combine(
                repository.observeHistory(Defaults.OWNER_PROFILE_ID, from, to),
                repository.observeMedications(Defaults.OWNER_PROFILE_ID),
                repository.observeHiddenIntakeLogs(Defaults.OWNER_PROFILE_ID)
            ) { history, medications, hiddenLogs ->
                Triple(history, medications, hiddenLogs)
            }.collect { (history, medications, hiddenLogs) ->
                allItems = history
                val today = today()
                takenTodayCountByMedicationId = history
                    .asSequence()
                    .filter { effectiveEventDate(it) == today && it.status == DoseStatus.TAKEN }
                    .groupingBy { it.medicationId }
                    .eachCount()
                asNeededOrOneTimeMedicationIds = medications
                    .asSequence()
                    .filter { it.times.isEmpty() }
                    .map { it.id }
                    .toSet()
                val cloudNotices = hiddenLogs
                    .asSequence()
                    .mapNotNull { log -> log.toCloudDeletionNotice() }
                    .sortedByDescending { it.deletedAtEpochMs }
                    .toList()
                val cloudNoticeByMedicationId = cloudNotices
                    .groupBy { it.medicationId }
                    .mapValues { (_, notices) -> notices.maxByOrNull { it.deletedAtEpochMs } }
                    .mapValuesNotNull { (_, notice) -> notice }
                _uiState.update {
                    it.copy(
                        cloudDeletionNotices = cloudNotices,
                        cloudDeletionNoticeByMedicationId = cloudNoticeByMedicationId
                    )
                }
                applyFilters()
            }
        }
    }

    fun exportPdf(
        uri: Uri,
        resolver: ContentResolver,
        request: HistoryReportRequest,
        onResult: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            val from = minOf(request.fromDate, request.toDate)
            val to = maxOf(request.fromDate, request.toDate)
            val result = exportHistoryPdfUseCase(
                ownerProfileId = Defaults.OWNER_PROFILE_ID,
                from = from,
                to = to,
                medicationId = request.medicationId,
                specificDate = null,
                resolver = resolver,
                uri = uri
            )
            _uiState.update {
                it.copy(
                    messageResId = if (result.isSuccess) R.string.history_export_pdf_success
                    else R.string.history_export_pdf_failed
                )
            }
            onResult(result.isSuccess)
        }
    }

    fun markTaken(doseEventId: String) {
        viewModelScope.launch {
            confirmDoseActionUseCase(doseEventId, IntakeAction.TAKEN, ActionSource.APP)
        }
    }

    fun updateEventTime(doseEventId: String, newTime: LocalTime) {
        viewModelScope.launch {
            runCatching { repository.updateDoseEventTime(doseEventId, newTime) }
                .onSuccess {
                    _uiState.update { it.copy(messageResId = R.string.history_time_update_success) }
                }
                .onFailure {
                    _uiState.update { it.copy(messageResId = R.string.history_time_update_failed) }
                }
        }
    }

    fun deleteEvent(doseEventId: String) {
        viewModelScope.launch {
            runCatching { repository.deleteDoseEvent(doseEventId) }
                .onSuccess {
                    _uiState.update { it.copy(messageResId = R.string.history_delete_success) }
                }
                .onFailure { error ->
                    val messageResId = if (error is IllegalStateException) {
                        R.string.history_delete_locked_after_edit_window
                    } else {
                        R.string.history_delete_failed
                    }
                    _uiState.update { it.copy(messageResId = messageResId) }
                }
        }
    }

    fun setMedicationFilter(medicationId: String?) {
        _uiState.update { it.copy(selectedMedicationId = medicationId) }
        applyFilters()
    }

    fun setMedicationSearchQuery(query: String) {
        _uiState.update { it.copy(medicationSearchQuery = query) }
        applyFilters()
    }

    fun setDateRangeFilter(from: LocalDate, to: LocalDate) {
        val normalizedFrom = minOf(from, to)
        val normalizedTo = maxOf(from, to)
        _uiState.update { it.copy(selectedFromDate = normalizedFrom, selectedToDate = normalizedTo) }
        applyFilters()
    }

    private fun applyFilters() {
        _uiState.update { current ->
            val dateFilteredItems = allItems.filter { event ->
                val effectiveDate = effectiveEventDate(event)
                !effectiveDate.isBefore(current.selectedFromDate) &&
                    !effectiveDate.isAfter(current.selectedToDate)
            }
            val normalizedQuery = current.medicationSearchQuery.trim()
            val textFilteredItems = if (normalizedQuery.isBlank()) {
                dateFilteredItems
            } else {
                dateFilteredItems.filter { event ->
                    event.medicationName.contains(normalizedQuery, ignoreCase = true)
                }
            }
            val medicationFilters = textFilteredItems
                .distinctBy { it.medicationId }
                .map { HistoryMedicationFilter(id = it.medicationId, name = it.medicationName) }
                .sortedBy { it.name.lowercase() }

            val selectedMedicationId = current.selectedMedicationId
                ?.takeIf { selected -> medicationFilters.any { it.id == selected } }

            val finalItems = textFilteredItems
                .filter { event ->
                    selectedMedicationId == null || event.medicationId == selectedMedicationId
                }
                .sortedByDescending { effectiveEventDateTime(it) }

            current.copy(
                items = finalItems,
                medicationFilters = medicationFilters,
                selectedMedicationId = selectedMedicationId,
                matchedMedicationCount = medicationFilters.size,
                asNeededOrOneTimeMedicationIds = asNeededOrOneTimeMedicationIds,
                takenTodayCountByMedicationId = takenTodayCountByMedicationId
            )
        }
    }

    private fun today(): LocalDate = LocalDate.now()

    private fun effectiveEventDate(event: DoseEvent): LocalDate {
        return event.respondedAt
            ?.atZone(ZoneId.systemDefault())
            ?.toLocalDate()
            ?: event.localDate
    }

    private fun effectiveEventDateTime(event: DoseEvent): LocalDateTime {
        return event.respondedAt
            ?.atZone(ZoneId.systemDefault())
            ?.toLocalDateTime()
            ?: LocalDateTime.of(event.localDate, event.localTime)
    }
}

data class HistoryMedicationFilter(
    val id: String,
    val name: String
)

data class HistoryUiState(
    val items: List<DoseEvent> = emptyList(),
    val cloudDeletionNotices: List<CloudDeletionNoticeUi> = emptyList(),
    val cloudDeletionNoticeByMedicationId: Map<String, CloudDeletionNoticeUi> = emptyMap(),
    val messageResId: Int? = null,
    val selectedMedicationId: String? = null,
    val medicationSearchQuery: String = "",
    val matchedMedicationCount: Int = 0,
    val selectedFromDate: LocalDate = LocalDate.now().minusDays(1),
    val selectedToDate: LocalDate = LocalDate.now(),
    val medicationFilters: List<HistoryMedicationFilter> = emptyList(),
    val asNeededOrOneTimeMedicationIds: Set<String> = emptySet(),
    val takenTodayCountByMedicationId: Map<String, Int> = emptyMap()
)

data class CloudDeletionNoticeUi(
    val medicationId: String,
    val recordTypeLabel: String,
    val medicationName: String,
    val deletedAtEpochMs: Long,
    val deletedAtLabel: String
)

data class HistoryReportRequest(
    val medicationId: String?,
    val fromDate: LocalDate,
    val toDate: LocalDate
)

private fun com.medminder.app.domain.model.HiddenIntakeLog.toCloudDeletionNotice(): CloudDeletionNoticeUi? {
    val details = operationDetails ?: return null
    if (!details.startsWith("cloud_deleted:")) return null
    val parts = details.split(":")
    if (parts.size < 4) return null
    val recordType = parts[1]
    val deletedAtEpochMs = parts[3].toLongOrNull() ?: return null
    val deletedAtLabel = java.time.Instant.ofEpochMilli(deletedAtEpochMs)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
    val recordTypeLabel = when (recordType) {
        "MEDICATION" -> "תרופה"
        "DOSE_EVENT" -> "אירוע נטילה"
        "HIDDEN_INTAKE_LOG" -> "יומן פנימי"
        else -> "רשומה"
    }
    return CloudDeletionNoticeUi(
        medicationId = medicationId,
        recordTypeLabel = recordTypeLabel,
        medicationName = medicationName,
        deletedAtEpochMs = deletedAtEpochMs,
        deletedAtLabel = deletedAtLabel
    )
}

private inline fun <K, V, R : Any> Map<K, V>.mapValuesNotNull(transform: (Map.Entry<K, V>) -> R?): Map<K, R> {
    val destination = LinkedHashMap<K, R>()
    for (entry in entries) {
        val value = transform(entry) ?: continue
        destination[entry.key] = value
    }
    return destination
}
