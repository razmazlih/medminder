package com.medminder.app.feature.today

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medminder.app.R
import com.medminder.app.core.Defaults
import com.medminder.app.data.local.dao.SettingsDao
import com.medminder.app.core.time.TimeProvider
import com.medminder.app.core.time.ZoneProvider
import com.medminder.app.domain.model.ActionSource
import com.medminder.app.domain.model.DoseEvent
import com.medminder.app.domain.model.DoseStatus
import com.medminder.app.domain.model.FoodRelation
import com.medminder.app.domain.model.IntakeAction
import com.medminder.app.domain.model.Medication
import com.medminder.app.domain.model.ScheduleMode
import com.medminder.app.domain.repository.MedicationRepository
import com.medminder.app.domain.usecase.ConfirmDoseActionUseCase
import com.medminder.app.domain.usecase.GenerateDailyDoseEventsUseCase
import com.medminder.app.domain.usecase.MarkMissedDosesUseCase
import com.medminder.app.domain.usecase.SchedulePendingDosesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class TodayViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsDao: SettingsDao,
    private val repository: MedicationRepository,
    private val generateDailyDoseEventsUseCase: GenerateDailyDoseEventsUseCase,
    private val markMissedDosesUseCase: MarkMissedDosesUseCase,
    private val schedulePendingDosesUseCase: SchedulePendingDosesUseCase,
    private val confirmDoseActionUseCase: ConfirmDoseActionUseCase,
    private val clock: TimeProvider,
    private val zoneProvider: ZoneProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(TodayUiState())
    val uiState: StateFlow<TodayUiState> = _uiState.asStateFlow()
    private val manualEncouragementShift = MutableStateFlow(0)

    init {
        refreshNotificationState()
        viewModelScope.launch {
            val today = todayInCurrentZone()
            generateDailyDoseEventsUseCase(Defaults.OWNER_PROFILE_ID, today, days = 2)
            markMissedDosesUseCase()
            schedulePendingDosesUseCase(Defaults.OWNER_PROFILE_ID)
        }
        viewModelScope.launch {
            val today = todayInCurrentZone()
            val yesterday = today.minusDays(1)
            val tomorrow = today.plusDays(1)
            val baseDataFlow = combine(
                repository.observeTodayDoses(Defaults.OWNER_PROFILE_ID, yesterday),
                repository.observeTodayDoses(Defaults.OWNER_PROFILE_ID, today),
                repository.observeTodayDoses(Defaults.OWNER_PROFILE_ID, tomorrow),
                repository.observeMedications(Defaults.OWNER_PROFILE_ID),
                settingsDao.observeUserProfile()
            ) { yesterdayEvents, todayEvents, tomorrowEvents, medications, userProfile ->
                Triple(
                    yesterdayEvents + todayEvents + tomorrowEvents,
                    medications.associateBy { it.id },
                    userProfile?.displayName?.trim().orEmpty()
                )
            }
            combine(baseDataFlow, minuteTickerFlow()) { data, _ -> data }.collect { events ->
                val (allEvents, medicationById, displayName) = events
                val now = clock.now()
                val nowInZone = ZonedDateTime.ofInstant(now, zoneProvider.currentZone())
                val recentStart = now.minus(RECENT_WINDOW)
                val upcomingEnd = now.plus(UPCOMING_WINDOW)
                val encouragementShift = manualEncouragementShift.value
                val baseEncouragementIndex = EncouragementMessages.indexForTime(now, zoneProvider.currentZone())
                val encouragementIndex = Math.floorMod(
                    baseEncouragementIndex + encouragementShift,
                    EncouragementMessages.count()
                )
                val greeting = buildGreeting(displayName, nowInZone.toLocalTime(), nowInZone.toLocalDate())
                val asNeededMedicationIds = medicationById.values
                    .filter { it.times.isEmpty() && it.isActive }
                    .map { it.id }
                    .toSet()
                val asNeededLatestTaken = allEvents
                    .asSequence()
                    .filter { it.medicationId in asNeededMedicationIds }
                    .filter { it.status == DoseStatus.TAKEN }
                    .filter { !it.scheduledAt.isAfter(now) }
                    .groupBy { it.medicationId }
                    .mapValues { (_, events) -> events.maxByOrNull { it.scheduledAt }?.scheduledAt }
                _uiState.update {
                    it.copy(
                        items = allEvents
                            .filter { event ->
                                shouldShowTodayDoseItem(
                                    event = event,
                                    medication = medicationById[event.medicationId],
                                    today = today,
                                    recentStart = recentStart,
                                    upcomingEnd = upcomingEnd
                                )
                            }
                            .sortedBy { event -> event.scheduledAt }
                            .map { event -> toUiModel(event, medicationById[event.medicationId]) },
                        asNeededMedications = medicationById.values
                            .filter { medication -> medication.times.isEmpty() && medication.isActive }
                            .sortedBy { medication -> medication.name }
                            .map { medication ->
                                val lastTakenAt = asNeededLatestTaken[medication.id]
                                val lastTakenMinutesAgo = lastTakenAt?.let { Duration.between(it, now).toMinutes().coerceAtLeast(0) }
                                AsNeededMedicationOptionUiModel(
                                    id = medication.id,
                                    name = medication.name,
                                    lastTakenMinutesAgo = lastTakenMinutesAgo
                                )
                            },
                        encouragementMessage = EncouragementMessages.at(encouragementIndex),
                        encouragementEmoji = EncouragementMessages.emojiAt(encouragementIndex),
                        encouragementIndex = encouragementIndex,
                        greetingTitle = greeting
                    )
                }
            }
        }
    }

    fun refreshNotificationState() {
        val permissionGranted = android.os.Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val enabled = NotificationManagerCompat.from(context).areNotificationsEnabled() && permissionGranted
        _uiState.update {
            it.copy(
                notificationsEnabled = enabled,
                permissionGranted = permissionGranted
            )
        }
    }

    fun onAction(doseEventId: String, action: IntakeAction) {
        viewModelScope.launch {
            confirmDoseActionUseCase(doseEventId, action, ActionSource.APP)
            if (action == IntakeAction.TAKEN) {
                showSuccessBanner(R.string.today_history_banner_added)
            }
        }
    }

    fun markAsNeededMedicationTaken(medicationId: String) {
        viewModelScope.launch {
            repository.markMedicationTakenNow(medicationId)
            showSuccessBanner(R.string.today_history_banner_added)
        }
    }

    fun addOneTimeMedicationIntake(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repository.addOneTimeMedicationIntake(
                ownerProfileId = Defaults.OWNER_PROFILE_ID,
                name = trimmed
            )
            showSuccessBanner(R.string.today_history_banner_added)
        }
    }

    fun showNextEncouragement() {
        manualEncouragementShift.update { it + 1 }
        _uiState.update { state ->
            state.copy(
                encouragementIndex = (state.encouragementIndex + 1) % EncouragementMessages.count(),
                encouragementMessage = EncouragementMessages.at(state.encouragementIndex + 1),
                encouragementEmoji = EncouragementMessages.emojiAt(state.encouragementIndex + 1)
            )
        }
    }

    private fun toUiModel(event: DoseEvent, medication: Medication?): TodayDoseUiModel {
        val canTakeNow = !clock.now().isBefore(event.scheduledAt.minus(EARLY_TAKE_WINDOW))
        return TodayDoseUiModel(
            id = event.id,
            timeLabel = event.localTime.toString(),
            medicationName = event.medicationName,
            doseLabel = medication?.let { "${it.doseText} ${it.doseUnit}" } ?: "",
            foodRelation = medication?.foodRelation ?: FoodRelation.NONE,
            notes = medication?.notes.orEmpty(),
            status = event.status,
            canTakeNow = canTakeNow
        )
    }

    private fun buildGreeting(displayName: String, time: LocalTime, date: LocalDate): String {
        val trimmedName = displayName.ifBlank { "שלך" }
        val variants = when {
            time.isBefore(LocalTime.NOON) -> listOf(
                "בוקר טוב, $trimmedName היקרה",
                "בוקר טוב ונעים, $trimmedName היקרה",
                "בוקר רגוע וטוב, $trimmedName היקרה",
                "בוקר של שלווה, $trimmedName היקרה"
            )
            time.isBefore(LocalTime.of(18, 0)) -> listOf(
                "צהריים טובים, $trimmedName היקרה",
                "צהריים נעימים, $trimmedName היקרה",
                "צהריים שקטים וטובים, $trimmedName היקרה",
                "צהריים של רוגע, $trimmedName היקרה"
            )
            else -> listOf(
                "ערב טוב, $trimmedName היקרה",
                "ערב טוב ונעים, $trimmedName היקרה",
                "ערב שקט ונעים, $trimmedName היקרה",
                "ערב של שלווה, $trimmedName היקרה"
            )
        }
        val variantIndex = Math.floorMod(date.toEpochDay().toInt(), variants.size)
        return variants[variantIndex]
    }

    private fun todayInCurrentZone(): LocalDate {
        return clock.now().atZone(zoneProvider.currentZone()).toLocalDate()
    }

    private companion object {
        val EARLY_TAKE_WINDOW: Duration = Duration.ofMinutes(60)
        val RECENT_WINDOW: Duration = Duration.ofHours(5)
        val UPCOMING_WINDOW: Duration = Duration.ofHours(18)
        val UI_REFRESH_INTERVAL: Duration = Duration.ofMinutes(1)
        val SUCCESS_BANNER_DURATION: Duration = Duration.ofSeconds(3)
    }

    private fun minuteTickerFlow() = flow {
        while (true) {
            emit(Unit)
            delay(UI_REFRESH_INTERVAL.toMillis())
        }
    }

    private suspend fun showSuccessBanner(messageResId: Int) {
        _uiState.update { it.copy(successBannerMessageResId = messageResId) }
        delay(SUCCESS_BANNER_DURATION.toMillis())
        _uiState.update { state ->
            if (state.successBannerMessageResId == messageResId) {
                state.copy(successBannerMessageResId = null)
            } else {
                state
            }
        }
    }
}

data class TodayUiState(
    val items: List<TodayDoseUiModel> = emptyList(),
    val asNeededMedications: List<AsNeededMedicationOptionUiModel> = emptyList(),
    val notificationsEnabled: Boolean = true,
    val permissionGranted: Boolean = true,
    val encouragementMessage: String = EncouragementMessages.default(),
    val encouragementEmoji: String = EncouragementMessages.emojiAt(0),
    val encouragementIndex: Int = 0,
    val greetingTitle: String = "",
    val successBannerMessageResId: Int? = null
)

data class TodayDoseUiModel(
    val id: String,
    val timeLabel: String,
    val medicationName: String,
    val doseLabel: String,
    val foodRelation: FoodRelation,
    val notes: String,
    val status: DoseStatus,
    val canTakeNow: Boolean
)

data class AsNeededMedicationOptionUiModel(
    val id: String,
    val name: String,
    val lastTakenMinutesAgo: Long?
)

internal fun shouldShowTodayDoseItem(
    event: DoseEvent,
    medication: Medication?,
    today: LocalDate,
    recentStart: Instant,
    upcomingEnd: Instant
): Boolean {
    if (event.status != DoseStatus.PENDING) return false
    if (medication?.scheduleMode == ScheduleMode.EVERY_X_DAYS) {
        return event.localDate == today
    }
    return !event.scheduledAt.isBefore(recentStart) && !event.scheduledAt.isAfter(upcomingEnd)
}
