package com.medminder.app.domain.usecase

import com.medminder.app.core.security.AppLockManager
import javax.inject.Inject

class ToggleAppLockUseCase @Inject constructor(
    private val appLockManager: AppLockManager
) {
    suspend operator fun invoke(enabled: Boolean) {
        appLockManager.setEnabled(enabled)
    }
}
