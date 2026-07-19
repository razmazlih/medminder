package com.medminder.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medminder.app.data.local.dao.SettingsDao
import com.medminder.app.data.local.entity.AppSettingsEntity
import com.medminder.app.data.local.entity.UserProfileEntity
import com.medminder.app.data.sync.CloudSyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDao: SettingsDao,
    private val cloudSyncManager: CloudSyncManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

    init {
        viewModelScope.launch {
            ensureDefaults()
        }
        viewModelScope.launch {
            settingsDao.observeSettings().collect { settings ->
                val value = settings ?: AppSettingsEntity()
                _uiState.update {
                    it.copy(
                        textScale = value.internalTextScale,
                        highContrast = value.highContrast,
                        escalatedReminderEnabled = value.escalatedReminderEnabled,
                        appLockEnabled = value.appLockEnabled,
                        cloudBackupEnabled = value.cloudBackupEnabled,
                        hasCloudConfiguration = !value.datasetId.isNullOrBlank() && !value.wrappedSyncSecret.isNullOrBlank(),
                        generatedSyncCode = value.lastGeneratedSyncCode,
                        syncState = syncStateLabel(value.syncState),
                        lastSyncAtLabel = formatEpoch(value.lastSyncAtEpochMs),
                        lastPulledAtLabel = formatEpoch(value.lastPulledAtEpochMs)
                    )
                }
            }
        }
        viewModelScope.launch {
            settingsDao.observeUserProfile().collect { profile ->
                _uiState.update { it.copy(displayName = profile?.displayName.orEmpty()) }
            }
        }
    }

    fun setTextScale(scale: Float) = updateSettings { copy(internalTextScale = scale) }

    fun setHighContrast(enabled: Boolean) = updateSettings { copy(highContrast = enabled) }

    fun setEscalatedReminderEnabled(enabled: Boolean) = updateSettings { copy(escalatedReminderEnabled = enabled) }

    fun setAppLock(enabled: Boolean) = updateSettings { copy(appLockEnabled = enabled) }

    fun setCloudBackupEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val result = if (!enabled) {
                cloudSyncManager.setCloudBackupEnabled(false)
            } else {
                val current = settingsDao.getSettings() ?: AppSettingsEntity()
                if (current.datasetId.isNullOrBlank() || current.wrappedSyncSecret.isNullOrBlank()) {
                    cloudSyncManager.enableCloudBackupWithGeneratedCode().map { generatedCode ->
                        _uiState.update {
                            it.copy(
                                generatedSyncCode = generatedCode,
                                syncMessage = "נוצר קוד סנכרון חדש. שמרו אותו לשימוש באתר."
                            )
                        }
                        Unit
                    }
                } else {
                    cloudSyncManager.setCloudBackupEnabled(true)
                }
            }
            result.onFailure { error ->
                _uiState.update { it.copy(syncMessage = "שגיאת סנכרון: ${error.message.orEmpty()}") }
            }
        }
    }

    fun generateNewSyncCode() {
        viewModelScope.launch {
            cloudSyncManager.enableCloudBackupWithGeneratedCode()
                .onSuccess { code ->
                    _uiState.update {
                        it.copy(
                            generatedSyncCode = code,
                            syncMessage = "נוצר קוד סנכרון חדש. שתפו אותו רק עם האתר שלכם."
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(syncMessage = "יצירת קוד נכשלה: ${error.message.orEmpty()}") }
                }
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            cloudSyncManager.syncNow("settings_manual")
                .onSuccess {
                    _uiState.update { it.copy(syncMessage = "הסנכרון הושלם בהצלחה") }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(syncMessage = "שגיאת סנכרון: ${error.message.orEmpty()}") }
                }
        }
    }

    fun exportAllLocalDataToCloud() {
        viewModelScope.launch {
            cloudSyncManager.exportAllLocalDataToCloud()
                .onSuccess {
                    _uiState.update { it.copy(syncMessage = "כל הנתונים המקומיים נשלחו לענן") }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(syncMessage = "ייצוא מלא נכשל: ${error.message.orEmpty()}") }
                }
        }
    }

    fun clearSyncMessage() {
        _uiState.update { it.copy(syncMessage = null) }
    }

    fun setDisplayName(name: String) {
        _uiState.update { it.copy(displayName = name) }
    }

    fun saveDisplayName() {
        viewModelScope.launch {
            val trimmed = _uiState.value.displayName.trim()
            if (trimmed.isEmpty()) return@launch
            val current = settingsDao.getUserProfile()
            val updated = if (current != null) {
                current.copy(displayName = trimmed)
            } else {
                UserProfileEntity(
                    displayName = trimmed,
                    hourFormat24 = true,
                    seniorMode = true,
                    language = "he",
                    timezoneMode = "DEVICE_AUTO"
                )
            }
            settingsDao.upsertUserProfile(updated)
            _uiState.update { it.copy(displayName = trimmed) }
        }
    }

    private fun updateSettings(transform: AppSettingsEntity.() -> AppSettingsEntity) {
        viewModelScope.launch {
            val current = settingsDao.getSettings() ?: AppSettingsEntity()
            settingsDao.upsertSettings(current.transform())
        }
    }

    private suspend fun ensureDefaults() {
        if (settingsDao.getSettings() == null) {
            settingsDao.upsertSettings(AppSettingsEntity())
        }
        if (settingsDao.getUserProfile() == null) {
            settingsDao.upsertUserProfile(
                UserProfileEntity(
                    displayName = "משתמש",
                    hourFormat24 = true,
                    seniorMode = true,
                    language = "he",
                    timezoneMode = "DEVICE_AUTO"
                )
            )
        }
    }

    private fun formatEpoch(epochMs: Long?): String {
        if (epochMs == null) return "-"
        return Instant.ofEpochMilli(epochMs)
            .atZone(ZoneId.systemDefault())
            .format(dateFormatter)
    }

    private fun syncStateLabel(raw: String): String {
        return when (raw) {
            "LOCAL_ONLY" -> "מקומי בלבד"
            "SYNCING" -> "מסנכרן"
            "IDLE" -> "מחובר"
            "ERROR" -> "שגיאה"
            else -> raw
        }
    }
}

data class SettingsUiState(
    val textScale: Float = 1.1f,
    val highContrast: Boolean = false,
    val escalatedReminderEnabled: Boolean = true,
    val appLockEnabled: Boolean = false,
    val cloudBackupEnabled: Boolean = false,
    val hasCloudConfiguration: Boolean = false,
    val syncState: String = "LOCAL_ONLY",
    val lastSyncAtLabel: String = "-",
    val lastPulledAtLabel: String = "-",
    val generatedSyncCode: String? = null,
    val syncMessage: String? = null,
    val displayName: String = ""
)
