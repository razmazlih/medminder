package com.medminder.app.core.security

import android.content.Context
import androidx.biometric.BiometricManager
import com.medminder.app.data.local.dao.SettingsDao
import com.medminder.app.data.local.entity.PinCredentialEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class AppLockManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsDao: SettingsDao,
    private val pinHasher: PinHasher
) {

    fun observeAppLockEnabled(): Flow<Boolean> = settingsDao.observeSettings().map { it?.appLockEnabled ?: false }

    suspend fun setEnabled(enabled: Boolean) {
        val current = settingsDao.getSettings() ?: return
        settingsDao.upsertSettings(current.copy(appLockEnabled = enabled))
    }

    suspend fun hasPin(): Boolean = settingsDao.getPinCredential() != null

    suspend fun setPin(pin: CharArray) {
        val hashed = pinHasher.hash(pin)
        settingsDao.upsertPin(
            PinCredentialEntity(
                id = 1,
                saltBase64 = hashed.saltBase64,
                hashBase64 = hashed.hashBase64,
                iterations = hashed.iterations,
                updatedAtEpochMs = System.currentTimeMillis()
            )
        )
    }

    suspend fun verifyPin(pin: CharArray): Boolean {
        val credential = settingsDao.getPinCredential() ?: return false
        return pinHasher.verify(pin, credential.saltBase64, credential.hashBase64, credential.iterations)
    }

    fun isBiometricAvailable(): Boolean {
        val result = BiometricManager.from(context).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }
}
