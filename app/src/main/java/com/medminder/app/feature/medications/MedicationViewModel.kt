package com.medminder.app.feature.medications

import android.app.AlarmManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medminder.app.core.Defaults
import com.medminder.app.domain.model.FoodRelation
import com.medminder.app.domain.model.Medication
import com.medminder.app.domain.model.MedicationInput
import com.medminder.app.domain.model.ScheduleMode
import com.medminder.app.domain.repository.MedicationRepository
import com.medminder.app.domain.usecase.GenerateDailyDoseEventsUseCase
import com.medminder.app.domain.usecase.ScheduleEngine
import com.medminder.app.domain.usecase.SchedulePendingDosesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class MedicationViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: MedicationRepository,
    private val generateDailyDoseEventsUseCase: GenerateDailyDoseEventsUseCase,
    private val schedulePendingDosesUseCase: SchedulePendingDosesUseCase,
    private val scheduleEngine: ScheduleEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(MedicationUiState())
    val uiState: StateFlow<MedicationUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeMedications(Defaults.OWNER_PROFILE_ID).collect { medications ->
                _uiState.update { state ->
                    val asNeededMedications = medications.filteredAsNeeded(state.showInactiveMedications)
                    state.copy(
                        allMedications = medications,
                        medications = medications.filteredRegular(state.showInactiveMedications),
                        asNeededMedications = asNeededMedications
                    )
                }
            }
        }
    }

    fun setShowInactiveMedications(showInactive: Boolean) {
        _uiState.update {
            it.copy(
                showInactiveMedications = showInactive,
                medications = it.allMedications.filteredRegular(showInactive),
                asNeededMedications = it.allMedications.filteredAsNeeded(showInactive)
            )
        }
    }

    fun deleteMedication(medicationId: String) {
        viewModelScope.launch {
            repository.deleteMedication(medicationId)
        }
    }

    fun disableMedication(medicationId: String) {
        viewModelScope.launch {
            repository.disableMedication(medicationId)
            schedulePendingDosesUseCase(Defaults.OWNER_PROFILE_ID)
        }
    }

    fun restoreMedication(medicationId: String) {
        viewModelScope.launch {
            repository.restoreMedication(medicationId)
            generateDailyDoseEventsUseCase(Defaults.OWNER_PROFILE_ID, LocalDate.now(), 3)
            schedulePendingDosesUseCase(Defaults.OWNER_PROFILE_ID)
        }
    }

    fun saveMedication(form: MedicationFormState): Boolean {
        val parsed = parseMedicationForm(form, scheduleEngine)
        if (parsed is ParsedMedicationResult.Error) {
            _uiState.update { it.copy(error = parsed.message) }
            return false
        }

        val medication = (parsed as ParsedMedicationResult.Success).value
        viewModelScope.launch {
            val input = MedicationInput(
                id = form.id,
                ownerProfileId = Defaults.OWNER_PROFILE_ID,
                name = medication.name,
                doseText = medication.doseText,
                doseUnit = medication.doseUnit,
                foodRelation = medication.foodRelation,
                scheduleMode = medication.scheduleMode,
                intervalHours = medication.intervalHours,
                windowStartTime = medication.windowStartTime,
                windowEndTime = medication.windowEndTime,
                startDate = medication.startDate,
                endDate = medication.endDate,
                notes = medication.notes,
                weekdays = medication.weekdays,
                times = medication.times,
                isActive = form.isActive
            )
            if (form.id == null) repository.saveMedication(input) else repository.updateMedication(input)
            generateDailyDoseEventsUseCase(Defaults.OWNER_PROFILE_ID, LocalDate.now(), 3)
            schedulePendingDosesUseCase(Defaults.OWNER_PROFILE_ID)
            val canScheduleExactAlarms = if (android.os.Build.VERSION.SDK_INT >= 31) {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            }
            val exactWarning = !canScheduleExactAlarms
            _uiState.update { it.copy(error = null, exactAlarmWarning = exactWarning) }
        }
        return true
    }

    fun addAsNeededMedication(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            _uiState.update { it.copy(error = "נא להזין שם תרופה") }
            return
        }
        viewModelScope.launch {
            repository.addAsNeededMedication(
                ownerProfileId = Defaults.OWNER_PROFILE_ID,
                name = trimmed
            )
            _uiState.update { it.copy(error = null) }
        }
    }

    fun markAsNeededMedicationTaken(medicationId: String) {
        viewModelScope.launch {
            repository.markMedicationTakenNow(medicationId)
        }
    }

    fun renameAsNeededMedication(medicationId: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            _uiState.update { it.copy(error = "נא להזין שם תרופה") }
            return
        }
        viewModelScope.launch {
            repository.renameMedication(medicationId, trimmed)
            _uiState.update { it.copy(error = null) }
        }
    }

    fun deleteAsNeededMedication(medicationId: String) {
        viewModelScope.launch {
            repository.deleteMedication(medicationId)
        }
    }

    fun disableAsNeededMedication(medicationId: String) {
        viewModelScope.launch {
            repository.disableMedication(medicationId)
        }
    }

    fun restoreAsNeededMedication(medicationId: String) {
        viewModelScope.launch {
            repository.restoreMedication(medicationId)
        }
    }
}

data class MedicationUiState(
    val allMedications: List<Medication> = emptyList(),
    val medications: List<Medication> = emptyList(),
    val asNeededMedications: List<Medication> = emptyList(),
    val showInactiveMedications: Boolean = false,
    val error: String? = null,
    val exactAlarmWarning: Boolean = false
)

data class MedicationFormState(
    val id: String? = null,
    val name: String = "",
    val doseText: String = "",
    val doseUnit: String = "",
    val foodRelation: FoodRelation = FoodRelation.NONE,
    val scheduleMode: ScheduleMode = ScheduleMode.FIXED_TIMES,
    val fixedTimes: List<LocalTime> = listOf(LocalTime.of(8, 0)),
    val intervalHours: Int = 8,
    val intervalDays: Int = 2,
    val windowStartTime: LocalTime = LocalTime.of(8, 0),
    val windowEndTime: LocalTime = LocalTime.of(22, 0),
    val startDate: LocalDate = LocalDate.now(),
    val endDate: LocalDate? = null,
    val notes: String = "",
    val weekdays: List<Int> = (1..7).toList(),
    val isActive: Boolean = true
)

private fun List<Medication>.filteredRegular(showInactive: Boolean): List<Medication> {
    val base = filter { it.times.isNotEmpty() }
    return if (showInactive) base.filter { !it.isActive } else base.filter { it.isActive }
}

private fun List<Medication>.filteredAsNeeded(showInactive: Boolean): List<Medication> {
    val base = filter { it.times.isEmpty() }
    return if (showInactive) base.filter { !it.isActive } else base.filter { it.isActive }
}

internal sealed class ParsedMedicationResult {
    data class Success(val value: ParsedMedication) : ParsedMedicationResult()
    data class Error(val message: String) : ParsedMedicationResult()
}

internal data class ParsedMedication(
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
    val times: List<LocalTime>
)

internal fun parseMedicationForm(
    form: MedicationFormState,
    scheduleEngine: ScheduleEngine
): ParsedMedicationResult {
    val name = form.name.trim()
    if (name.isEmpty()) return ParsedMedicationResult.Error("נא להזין שם תרופה")

    if (form.endDate != null && form.endDate.isBefore(form.startDate)) {
        return ParsedMedicationResult.Error("תאריך הסיום חייב להיות אחרי תאריך ההתחלה")
    }

    val times = when (form.scheduleMode) {
        ScheduleMode.FIXED_TIMES -> form.fixedTimes.distinct().sorted()
        ScheduleMode.EVERY_X_HOURS -> {
            if (form.intervalHours <= 0) {
                return ParsedMedicationResult.Error("יש לבחור מרווח שעות תקין")
            }
            if (form.windowEndTime.isBefore(form.windowStartTime)) {
                return ParsedMedicationResult.Error("שעת הסיום חייבת להיות אחרי שעת ההתחלה")
            }
            scheduleEngine.generateTimesEveryXHours(
                intervalHours = form.intervalHours,
                windowStart = form.windowStartTime,
                windowEnd = form.windowEndTime
            )
        }
        ScheduleMode.EVERY_X_DAYS -> {
            if (form.intervalDays <= 0) {
                return ParsedMedicationResult.Error("יש לבחור מרווח ימים תקין")
            }
            form.fixedTimes.distinct().sorted()
        }
    }

    if (times.isEmpty()) return ParsedMedicationResult.Error("יש להוסיף לפחות שעת נטילה אחת")

    val weekdays = form.weekdays
        .ifEmpty { (1..7).toList() }
        .distinct()
        .sorted()

    return ParsedMedicationResult.Success(
        ParsedMedication(
            name = name,
            doseText = form.doseText.ifBlank { "1" },
            doseUnit = form.doseUnit.ifBlank { "יחידה" },
            foodRelation = form.foodRelation,
            scheduleMode = form.scheduleMode,
            intervalHours = when (form.scheduleMode) {
                ScheduleMode.EVERY_X_HOURS -> form.intervalHours
                ScheduleMode.EVERY_X_DAYS -> form.intervalDays
                ScheduleMode.FIXED_TIMES -> null
            },
            windowStartTime = form.windowStartTime.takeIf { form.scheduleMode == ScheduleMode.EVERY_X_HOURS },
            windowEndTime = form.windowEndTime.takeIf { form.scheduleMode == ScheduleMode.EVERY_X_HOURS },
            startDate = form.startDate,
            endDate = form.endDate,
            notes = form.notes,
            weekdays = weekdays,
            times = times
        )
    )
}
