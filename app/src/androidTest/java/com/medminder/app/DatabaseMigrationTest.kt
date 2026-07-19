package com.medminder.app

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.medminder.app.data.local.AppDatabase
import com.medminder.app.di.DbModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate1To2AddsScheduleColumnsWithDefaults() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                """
                INSERT INTO medication (
                    id, ownerProfileId, name, doseText, doseUnit, foodRelation,
                    startDate, endDate, notes, isActive, createdAtEpochMs, updatedAtEpochMs
                ) VALUES (
                    'med-1', 'owner_primary', 'אקמול', '1', 'כדור', 'NONE',
                    '2026-02-13', NULL, '', 1, 1, 1
                )
                """.trimIndent()
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, DbModule.MIGRATION_1_2)

        db.query(
            "SELECT scheduleMode, intervalHours, windowStartTime, windowEndTime FROM medication WHERE id = 'med-1'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("FIXED_TIMES", cursor.getString(0))
            assertTrue(cursor.isNull(1))
            assertTrue(cursor.isNull(2))
            assertTrue(cursor.isNull(3))
        }
    }

    @Test
    fun migrate2To3AddsEscalatedReminderFlagWithDefaultEnabled() {
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL(
                """
                INSERT INTO app_settings (
                    id, internalTextScale, highContrast, notificationSoundUri, snoozeDefaultMinutes,
                    repeatIntervalMinutes, repeatMaxCount, appLockEnabled
                ) VALUES (
                    1, 1.1, 0, NULL, 10, 10, 3, 0
                )
                """.trimIndent()
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, DbModule.MIGRATION_2_3)

        db.query(
            "SELECT escalatedReminderEnabled FROM app_settings WHERE id = 1"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
    }

    @Test
    fun migrate3To4AddsHiddenIntakeLogTable() {
        helper.createDatabase(TEST_DB, 3).apply {
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 4, true, DbModule.MIGRATION_3_4)

        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='hidden_intake_log'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("hidden_intake_log", cursor.getString(0))
        }
    }

    @Test
    fun migrate4To5AddsOperationColumnsForHiddenIntakeLog() {
        helper.createDatabase(TEST_DB, 4).apply {
            execSQL(
                """
                INSERT INTO hidden_intake_log (
                    id, ownerProfileId, doseEventId, medicationId, medicationName, actionSource, takenAtEpochMs, createdAtEpochMs
                ) VALUES (
                    'log-1', 'owner_primary', 'dose-1', 'med-1', 'אקמול', 'APP', 100, 100
                )
                """.trimIndent()
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 5, true, DbModule.MIGRATION_4_5)

        db.query(
            "SELECT operationType, operationDetails FROM hidden_intake_log WHERE id = 'log-1'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("CHANGE", cursor.getString(0))
            assertTrue(cursor.isNull(1))
        }
    }

    @Test
    fun migrate5To6AddsCloudSyncSettingsAndOutboxTable() {
        helper.createDatabase(TEST_DB, 5).apply {
            execSQL(
                """
                INSERT INTO app_settings (
                    id, internalTextScale, highContrast, notificationSoundUri, snoozeDefaultMinutes,
                    repeatIntervalMinutes, repeatMaxCount, escalatedReminderEnabled, appLockEnabled
                ) VALUES (
                    1, 1.1, 0, NULL, 10, 10, 3, 1, 0
                )
                """.trimIndent()
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 6, true, DbModule.MIGRATION_5_6)

        db.query(
            """
            SELECT cloudBackupEnabled, datasetId, wrappedSyncSecret, lastSyncAtEpochMs, lastPulledAtEpochMs, syncState
            FROM app_settings WHERE id = 1
            """.trimIndent()
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
            assertTrue(cursor.isNull(1))
            assertTrue(cursor.isNull(2))
            assertTrue(cursor.isNull(3))
            assertTrue(cursor.isNull(4))
            assertEquals("LOCAL_ONLY", cursor.getString(5))
        }

        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='sync_outbox'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("sync_outbox", cursor.getString(0))
        }
    }

    @Test
    fun migrate6To7AddsLastGeneratedSyncCodeColumn() {
        helper.createDatabase(TEST_DB, 6).apply {
            execSQL(
                """
                INSERT INTO app_settings (
                    id, internalTextScale, highContrast, notificationSoundUri, snoozeDefaultMinutes,
                    repeatIntervalMinutes, repeatMaxCount, escalatedReminderEnabled, appLockEnabled,
                    cloudBackupEnabled, datasetId, wrappedSyncSecret, lastSyncAtEpochMs, lastPulledAtEpochMs, syncState
                ) VALUES (
                    1, 1.1, 0, NULL, 10, 10, 3, 1, 0,
                    1, 'dataset-1', 'wrapped-1', 100, 101, 'IDLE'
                )
                """.trimIndent()
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 7, true, DbModule.MIGRATION_6_7)

        db.query(
            "SELECT lastGeneratedSyncCode FROM app_settings WHERE id = 1"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.isNull(0))
        }
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
