package com.medminder.app.domain.usecase

import com.medminder.app.core.Defaults
import com.medminder.app.data.local.dao.SettingsDao
import com.medminder.app.data.local.entity.AppSettingsEntity
import com.medminder.app.data.local.entity.UserProfileEntity
import javax.inject.Inject

class CompleteOnboardingUseCase @Inject constructor(
    private val settingsDao: SettingsDao
) {
    suspend operator fun invoke(displayName: String, hourFormat24: Boolean, seniorMode: Boolean) {
        settingsDao.upsertUserProfile(
            UserProfileEntity(
                id = "default",
                ownerProfileId = Defaults.OWNER_PROFILE_ID,
                displayName = displayName,
                hourFormat24 = hourFormat24,
                seniorMode = seniorMode,
                language = "he",
                timezoneMode = "DEVICE_AUTO"
            )
        )
        if (settingsDao.getSettings() == null) {
            settingsDao.upsertSettings(AppSettingsEntity())
        }
    }
}
