package com.medminder.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val internalTextScale: Float = 1.1f,
    val highContrast: Boolean = false,
    val notificationSoundUri: String? = null,
    val snoozeDefaultMinutes: Int = 10,
    val repeatIntervalMinutes: Int = 10,
    val repeatMaxCount: Int = 3,
    val escalatedReminderEnabled: Boolean = true,
    val appLockEnabled: Boolean = false,
    val cloudBackupEnabled: Boolean = false,
    val datasetId: String? = null,
    val wrappedSyncSecret: String? = null,
    val lastGeneratedSyncCode: String? = null,
    val lastSyncAtEpochMs: Long? = null,
    val lastPulledAtEpochMs: Long? = null,
    val syncState: String = "LOCAL_ONLY"
)
