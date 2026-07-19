package com.medminder.app.feature.applock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medminder.app.core.security.AppLockManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class AppLockViewModel @Inject constructor(
    private val appLockManager: AppLockManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppLockUiState())
    val uiState: StateFlow<AppLockUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(hasPin = appLockManager.hasPin()) }
        }
    }

    fun submit(pin: String, onSuccess: () -> Unit) {
        if (pin.length < 4) {
            _uiState.update { it.copy(errorMessage = "יש להזין לפחות 4 ספרות") }
            return
        }
        viewModelScope.launch {
            val pinChars = pin.toCharArray()
            val hasPin = appLockManager.hasPin()
            val success = if (hasPin) {
                appLockManager.verifyPin(pinChars)
            } else {
                appLockManager.setPin(pinChars)
                true
            }
            pinChars.fill('\u0000')
            if (success) {
                _uiState.update { it.copy(errorMessage = null, hasPin = true) }
                onSuccess()
            } else {
                _uiState.update { it.copy(errorMessage = "קוד שגוי") }
            }
        }
    }
}

data class AppLockUiState(
    val hasPin: Boolean = false,
    val errorMessage: String? = null
)
