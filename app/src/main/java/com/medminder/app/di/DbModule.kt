package com.medminder.app.di

import android.content.Context
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import com.medminder.app.data.local.AppDatabase
import com.medminder.app.data.local.dao.DoseEventDao
import com.medminder.app.data.local.dao.HiddenIntakeLogDao
import com.medminder.app.data.local.dao.MedicationDao
import com.medminder.app.data.local.dao.SettingsDao
import com.medminder.app.data.local.dao.SyncOutboxDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DbModule {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE medication ADD COLUMN scheduleMode TEXT NOT NULL DEFAULT 'FIXED_TIMES'")
            db.execSQL("ALTER TABLE medication ADD COLUMN intervalHours INTEGER")
            db.execSQL("ALTER TABLE medication ADD COLUMN windowStartTime TEXT")
            db.execSQL("ALTER TABLE medication ADD COLUMN windowEndTime TEXT")
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE app_settings ADD COLUMN escalatedReminderEnabled INTEGER NOT NULL DEFAULT 1")
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS hidden_intake_log (
                    id TEXT NOT NULL PRIMARY KEY,
                    ownerProfileId TEXT NOT NULL,
                    doseEventId TEXT,
                    medicationId TEXT NOT NULL,
                    medicationName TEXT NOT NULL,
                    actionSource TEXT NOT NULL,
                    takenAtEpochMs INTEGER NOT NULL,
                    createdAtEpochMs INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_hidden_intake_log_ownerProfileId_takenAtEpochMs " +
                    "ON hidden_intake_log(ownerProfileId, takenAtEpochMs)"
            )
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE hidden_intake_log ADD COLUMN operationType TEXT NOT NULL DEFAULT 'CHANGE'")
            db.execSQL("ALTER TABLE hidden_intake_log ADD COLUMN operationDetails TEXT")
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE app_settings ADD COLUMN cloudBackupEnabled INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE app_settings ADD COLUMN datasetId TEXT")
            db.execSQL("ALTER TABLE app_settings ADD COLUMN wrappedSyncSecret TEXT")
            db.execSQL("ALTER TABLE app_settings ADD COLUMN lastSyncAtEpochMs INTEGER")
            db.execSQL("ALTER TABLE app_settings ADD COLUMN lastPulledAtEpochMs INTEGER")
            db.execSQL("ALTER TABLE app_settings ADD COLUMN syncState TEXT NOT NULL DEFAULT 'LOCAL_ONLY'")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS sync_outbox (
                    id TEXT NOT NULL PRIMARY KEY,
                    recordType TEXT NOT NULL,
                    recordId TEXT NOT NULL,
                    operation TEXT NOT NULL,
                    payloadJson TEXT,
                    updatedAtEpochMs INTEGER NOT NULL,
                    deletedAtEpochMs INTEGER,
                    attemptCount INTEGER NOT NULL,
                    lastAttemptAtEpochMs INTEGER,
                    queuedAtEpochMs INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_sync_outbox_recordType_recordId " +
                    "ON sync_outbox(recordType, recordId)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_sync_outbox_queuedAtEpochMs " +
                    "ON sync_outbox(queuedAtEpochMs)"
            )
        }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE app_settings ADD COLUMN lastGeneratedSyncCode TEXT")
        }
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "medminder.db")
            .addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7
            )
            .build()
    }

    @Provides
    fun provideMedicationDao(db: AppDatabase): MedicationDao = db.medicationDao()

    @Provides
    fun provideDoseEventDao(db: AppDatabase): DoseEventDao = db.doseEventDao()

    @Provides
    fun provideHiddenIntakeLogDao(db: AppDatabase): HiddenIntakeLogDao = db.hiddenIntakeLogDao()

    @Provides
    fun provideSettingsDao(db: AppDatabase): SettingsDao = db.settingsDao()

    @Provides
    fun provideSyncOutboxDao(db: AppDatabase): SyncOutboxDao = db.syncOutboxDao()
}
