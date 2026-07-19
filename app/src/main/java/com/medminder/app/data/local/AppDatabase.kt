package com.medminder.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.medminder.app.data.local.dao.DoseEventDao
import com.medminder.app.data.local.dao.HiddenIntakeLogDao
import com.medminder.app.data.local.dao.MedicationDao
import com.medminder.app.data.local.dao.SettingsDao
import com.medminder.app.data.local.dao.SyncOutboxDao
import com.medminder.app.data.local.entity.AppSettingsEntity
import com.medminder.app.data.local.entity.DoseEventEntity
import com.medminder.app.data.local.entity.HiddenIntakeLogEntity
import com.medminder.app.data.local.entity.MedicationEntity
import com.medminder.app.data.local.entity.MedicationTimeEntity
import com.medminder.app.data.local.entity.MedicationWeekdayEntity
import com.medminder.app.data.local.entity.PinCredentialEntity
import com.medminder.app.data.local.entity.SyncOutboxEntity
import com.medminder.app.data.local.entity.UserProfileEntity

@Database(
    entities = [
        UserProfileEntity::class,
        MedicationEntity::class,
        MedicationWeekdayEntity::class,
        MedicationTimeEntity::class,
        DoseEventEntity::class,
        HiddenIntakeLogEntity::class,
        AppSettingsEntity::class,
        PinCredentialEntity::class,
        SyncOutboxEntity::class
    ],
    version = 7,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun doseEventDao(): DoseEventDao
    abstract fun hiddenIntakeLogDao(): HiddenIntakeLogDao
    abstract fun settingsDao(): SettingsDao
    abstract fun syncOutboxDao(): SyncOutboxDao
}
