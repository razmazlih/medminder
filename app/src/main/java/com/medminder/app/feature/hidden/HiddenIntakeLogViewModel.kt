package com.medminder.app.feature.hidden

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medminder.app.core.Defaults
import com.medminder.app.core.time.TimeProvider
import com.medminder.app.domain.model.ActionSource
import com.medminder.app.domain.model.HiddenIntakeLog
import com.medminder.app.domain.model.IntakeLogOperation
import com.medminder.app.domain.repository.MedicationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class HiddenIntakeLogViewModel @Inject constructor(
    repository: MedicationRepository,
    private val clock: TimeProvider
) : ViewModel() {

    private var allItems: List<HiddenIntakeLog> = emptyList()
    private val _uiState = MutableStateFlow(HiddenIntakeLogUiState())
    val uiState: StateFlow<HiddenIntakeLogUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeHiddenIntakeLogs(Defaults.OWNER_PROFILE_ID).collect { logs ->
                allItems = logs.sortedByDescending { it.takenAt }
                applyFilters()
            }
        }
    }

    fun setQuery(query: String) {
        _uiState.update { it.copy(query = query) }
        applyFilters()
    }

    fun setOperationFilter(operation: IntakeLogOperation?) {
        _uiState.update { it.copy(selectedOperation = operation) }
        applyFilters()
    }

    fun setSourceFilter(source: ActionSource?) {
        _uiState.update { it.copy(selectedSource = source) }
        applyFilters()
    }

    fun setTimeRange(range: HiddenLogTimeRange) {
        _uiState.update { it.copy(selectedRange = range) }
        applyFilters()
    }

    fun setSuspiciousOnly(enabled: Boolean) {
        _uiState.update { it.copy(suspiciousOnly = enabled) }
        applyFilters()
    }

    fun clearFilters() {
        _uiState.update {
            it.copy(
                query = "",
                selectedOperation = null,
                selectedSource = null,
                selectedRange = HiddenLogTimeRange.LAST_7_DAYS,
                suspiciousOnly = false
            )
        }
        applyFilters()
    }

    private fun applyFilters() {
        val now = clock.now()
        val state = _uiState.value
        val rangeStart = state.selectedRange.hours?.let { now.minus(Duration.ofHours(it)) }
        val itemsInRange = allItems.filter { log ->
            rangeStart?.let { !log.takenAt.isBefore(it) } ?: true
        }
        val reasonsById = HiddenLogSuspicionAnalyzer.buildSuspicionReasons(itemsInRange)
        val filtered = itemsInRange
            .filter { log ->
                val query = state.query.trim()
                query.isEmpty() ||
                    log.medicationName.contains(query, ignoreCase = true) ||
                    (log.operationDetails?.contains(query, ignoreCase = true) == true)
            }
            .filter { log ->
                state.selectedOperation?.let { log.operationType == it } ?: true
            }
            .filter { log ->
                state.selectedSource?.let { log.actionSource == it } ?: true
            }
            .map { log ->
                HiddenLogEntryUi(
                    log = log,
                    suspiciousReasons = reasonsById[log.id].orEmpty()
                )
            }
            .filter { entry ->
                if (state.suspiciousOnly) entry.suspiciousReasons.isNotEmpty() else true
            }

        val summary = HiddenLogSummary(
            total = itemsInRange.size,
            addCount = itemsInRange.count { it.operationType == IntakeLogOperation.ADD },
            updateCount = itemsInRange.count { it.operationType == IntakeLogOperation.UPDATE },
            deleteCount = itemsInRange.count { it.operationType == IntakeLogOperation.DELETE },
            changeCount = itemsInRange.count { it.operationType == IntakeLogOperation.CHANGE },
            suspiciousCount = reasonsById.size,
            uniqueMedicationCount = itemsInRange.map { it.medicationId }.toSet().size,
            blockedDeleteAttempts = itemsInRange.count {
                it.operationDetails?.contains("delete_denied", ignoreCase = true) == true
            }
        )

        _uiState.update {
            it.copy(
                items = filtered,
                summary = summary
            )
        }
    }

}

data class HiddenIntakeLogUiState(
    val query: String = "",
    val selectedOperation: IntakeLogOperation? = null,
    val selectedSource: ActionSource? = null,
    val selectedRange: HiddenLogTimeRange = HiddenLogTimeRange.LAST_7_DAYS,
    val suspiciousOnly: Boolean = false,
    val items: List<HiddenLogEntryUi> = emptyList(),
    val summary: HiddenLogSummary = HiddenLogSummary()
)

data class HiddenLogEntryUi(
    val log: HiddenIntakeLog,
    val suspiciousReasons: List<String>
)

data class HiddenLogSummary(
    val total: Int = 0,
    val addCount: Int = 0,
    val updateCount: Int = 0,
    val deleteCount: Int = 0,
    val changeCount: Int = 0,
    val suspiciousCount: Int = 0,
    val uniqueMedicationCount: Int = 0,
    val blockedDeleteAttempts: Int = 0
)

enum class HiddenLogTimeRange(val hours: Long?) {
    LAST_24_HOURS(hours = 24),
    LAST_7_DAYS(hours = 24 * 7),
    LAST_30_DAYS(hours = 24 * 30),
    ALL(hours = null)
}
