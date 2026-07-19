package com.medminder.app.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medminder.app.data.local.dao.SettingsDao
import com.medminder.app.data.local.entity.AppSettingsEntity
import com.medminder.app.domain.usecase.CompleteOnboardingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsDao: SettingsDao,
    private val completeOnboardingUseCase: CompleteOnboardingUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val existingProfile = settingsDao.getUserProfile()
            _uiState.value = _uiState.value.copy(
                name = existingProfile?.displayName.orEmpty(),
                is24Hour = existingProfile?.hourFormat24 ?: true,
                seniorMode = existingProfile?.seniorMode ?: true,
                completed = existingProfile != null
            )
        }
    }

    fun onNameChanged(value: String) {
        _uiState.value = _uiState.value.copy(name = value)
    }

    fun on24HourChanged(value: Boolean) {
        _uiState.value = _uiState.value.copy(is24Hour = value)
    }

    fun onSeniorModeChanged(value: Boolean) {
        _uiState.value = _uiState.value.copy(seniorMode = value)
    }

    fun submit() {
        val state = _uiState.value
        if (state.name.isBlank()) {
            _uiState.value = state.copy(error = "נא להזין שם")
            return
        }
        viewModelScope.launch {
            completeOnboardingUseCase(
                displayName = state.name.trim(),
                hourFormat24 = state.is24Hour,
                seniorMode = state.seniorMode
            )
            if (settingsDao.getSettings() == null) {
                settingsDao.upsertSettings(AppSettingsEntity())
            }
            _uiState.value = _uiState.value.copy(completed = true, error = null)
        }
    }
}

data class OnboardingUiState(
    val name: String = "",
    val is24Hour: Boolean = true,
    val seniorMode: Boolean = true,
    val timezoneDisplay: String = "אוטומטי לפי המכשיר",
    val completed: Boolean = false,
    val error: String? = null
)
