package com.medminder.app.data.sync

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctions
import com.medminder.app.core.Defaults
import com.medminder.app.core.security.SyncCryptoManager
import com.medminder.app.core.time.TimeProvider
import com.medminder.app.data.local.dao.DoseEventDao
import com.medminder.app.data.local.dao.HiddenIntakeLogDao
import com.medminder.app.data.local.dao.MedicationDao
import com.medminder.app.data.local.dao.SettingsDao
import com.medminder.app.data.local.dao.SyncOutboxDao
import com.medminder.app.data.local.entity.AppSettingsEntity
import com.medminder.app.data.local.entity.HiddenIntakeLogEntity
import com.medminder.app.data.local.entity.SyncOutboxEntity
import com.medminder.app.domain.model.ActionSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

@Singleton
class CloudSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsDao: SettingsDao,
    private val syncOutboxDao: SyncOutboxDao,
    private val medicationDao: MedicationDao,
    private val doseEventDao: DoseEventDao,
    private val hiddenIntakeLogDao: HiddenIntakeLogDao,
    private val syncCryptoManager: SyncCryptoManager,
    private val clock: TimeProvider
) {
    companion object {
        private const val HALF_HOUR_MS = 30L * 60L * 1000L
    }

    private val codec = SyncPayloadCodec()
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun enableCloudBackupWithGeneratedCode(): Result<String> = runCatching {
        val syncCode = syncCryptoManager.generateSyncCode()
        enableCloudBackupWithCodeInternal(syncCode = syncCode)
        syncCode
    }

    suspend fun setCloudBackupEnabled(enabled: Boolean): Result<Unit> = runCatching {
        val current = ensureSettings()
        if (!enabled) {
            settingsDao.upsertSettings(
                current.copy(
                    cloudBackupEnabled = false,
                    syncState = "LOCAL_ONLY"
                )
            )
            return@runCatching
        }
        if (current.datasetId.isNullOrBlank() || current.wrappedSyncSecret.isNullOrBlank()) {
            error("Sync code is required before enabling cloud backup")
        }
        settingsDao.upsertSettings(current.copy(cloudBackupEnabled = true, syncState = "IDLE"))
        requestSync("toggle_on")
    }

    fun requestSync(reason: String = "manual") {
        scope.launch {
            syncNow(reason)
        }
    }

    fun requestSyncIfDue(reason: String = "due_check") {
        scope.launch {
            runCatching {
                if (isSyncDue()) {
                    syncNow(reason)
                }
            }
        }
    }

    suspend fun syncNow(reason: String = "manual"): Result<Unit> = runCatching {
        mutex.withLock {
            val settings = ensureSettings()
            if (!settings.cloudBackupEnabled) return@withLock
            updateSyncState("SYNCING")
            try {
                val datasetId = settings.datasetId ?: error("Missing dataset id")
                val wrappedSyncSecret = settings.wrappedSyncSecret ?: error("Missing wrapped sync secret")
                ensureFirebaseInitialized()
                ensureAnonymousUser()
                val key = syncCryptoManager.unwrapKey(wrappedSyncSecret)
                val pulledAt = pullRemote(datasetId = datasetId, key = key, sinceEpochMs = settings.lastPulledAtEpochMs)
                pushOutbox(datasetId = datasetId, key = key)
                val now = clock.now().toEpochMilli()
                mutateSettings {
                    it.copy(
                        lastSyncAtEpochMs = now,
                        lastPulledAtEpochMs = maxOf(it.lastPulledAtEpochMs ?: Long.MIN_VALUE, pulledAt ?: Long.MIN_VALUE)
                            .takeIf { value -> value != Long.MIN_VALUE },
                        syncState = "IDLE"
                    )
                }
            } catch (t: Throwable) {
                updateSyncState("ERROR")
                throw t
            }
        }
    }

    suspend fun queueMedicationUpsert(medicationId: String) {
        val snapshot = medicationDao.getWithScheduleById(medicationId) ?: return
        enqueueOutbox(
            recordType = SyncRecordType.MEDICATION,
            recordId = medicationId,
            operation = SyncOperation.UPSERT,
            payloadJson = codec.medicationToJson(snapshot),
            updatedAtEpochMs = snapshot.medication.updatedAtEpochMs,
            deletedAtEpochMs = null
        )
    }

    suspend fun queueDoseEventUpsert(doseEventId: String) {
        val snapshot = doseEventDao.getById(doseEventId) ?: return
        enqueueOutbox(
            recordType = SyncRecordType.DOSE_EVENT,
            recordId = doseEventId,
            operation = SyncOperation.UPSERT,
            payloadJson = codec.doseEventToJson(snapshot),
            updatedAtEpochMs = snapshot.updatedAtEpochMs,
            deletedAtEpochMs = null
        )
    }

    suspend fun queueDoseEventTombstone(doseEventId: String, deletedAtEpochMs: Long) {
        enqueueOutbox(
            recordType = SyncRecordType.DOSE_EVENT,
            recordId = doseEventId,
            operation = SyncOperation.TOMBSTONE,
            payloadJson = null,
            updatedAtEpochMs = deletedAtEpochMs,
            deletedAtEpochMs = deletedAtEpochMs
        )
    }

    suspend fun queueHiddenLogUpsert(logId: String) {
        val snapshot = hiddenIntakeLogDao.getById(logId) ?: return
        enqueueOutbox(
            recordType = SyncRecordType.HIDDEN_INTAKE_LOG,
            recordId = logId,
            operation = SyncOperation.UPSERT,
            payloadJson = codec.hiddenLogToJson(snapshot),
            updatedAtEpochMs = snapshot.createdAtEpochMs,
            deletedAtEpochMs = null
        )
    }

    suspend fun exportAllLocalDataToCloud(): Result<Unit> = runCatching {
        val settings = ensureSettings()
        if (!settings.cloudBackupEnabled) {
            error("יש להפעיל גיבוי ענן לפני ייצוא מלא")
        }
        val ownerId = Defaults.OWNER_PROFILE_ID
        medicationDao.getAllWithSchedule(ownerId).forEach { med ->
            enqueueOutbox(
                recordType = SyncRecordType.MEDICATION,
                recordId = med.medication.id,
                operation = SyncOperation.UPSERT,
                payloadJson = codec.medicationToJson(med),
                updatedAtEpochMs = med.medication.updatedAtEpochMs,
                deletedAtEpochMs = null,
                triggerSync = false
            )
        }
        doseEventDao.getAll(ownerId).forEach { event ->
            enqueueOutbox(
                recordType = SyncRecordType.DOSE_EVENT,
                recordId = event.id,
                operation = SyncOperation.UPSERT,
                payloadJson = codec.doseEventToJson(event),
                updatedAtEpochMs = event.updatedAtEpochMs,
                deletedAtEpochMs = null,
                triggerSync = false
            )
        }
        hiddenIntakeLogDao.getAll(ownerId).forEach { log ->
            enqueueOutbox(
                recordType = SyncRecordType.HIDDEN_INTAKE_LOG,
                recordId = log.id,
                operation = SyncOperation.UPSERT,
                payloadJson = codec.hiddenLogToJson(log),
                updatedAtEpochMs = log.createdAtEpochMs,
                deletedAtEpochMs = null,
                triggerSync = false
            )
        }
        syncNow("full_export")
            .getOrElse { throw it }
    }

    private suspend fun enableCloudBackupWithCodeInternal(syncCode: String) {
        val trimmed = syncCode.trim()
        require(trimmed.isNotEmpty()) { "Sync code is empty" }
        ensureFirebaseInitialized()
        ensureAnonymousUser()
        val datasetId = joinDataset(trimmed)
        val dataKey = syncCryptoManager.deriveDataKey(trimmed)
        val wrapped = syncCryptoManager.wrapKey(dataKey)
        mutateSettings {
            it.copy(
                cloudBackupEnabled = true,
                datasetId = datasetId,
                wrappedSyncSecret = wrapped,
                lastGeneratedSyncCode = trimmed,
                syncState = "IDLE"
            )
        }
        exportAllLocalDataToCloud()
            .getOrElse { throw it }
    }

    private suspend fun enqueueOutbox(
        recordType: SyncRecordType,
        recordId: String,
        operation: SyncOperation,
        payloadJson: String?,
        updatedAtEpochMs: Long,
        deletedAtEpochMs: Long?,
        triggerSync: Boolean = true
    ) {
        val queuedAt = clock.now().toEpochMilli()
        syncOutboxDao.upsert(
            SyncOutboxEntity(
                id = outboxId(recordType, recordId),
                recordType = recordType.name,
                recordId = recordId,
                operation = operation.name,
                payloadJson = payloadJson,
                updatedAtEpochMs = updatedAtEpochMs,
                deletedAtEpochMs = deletedAtEpochMs,
                attemptCount = 0,
                lastAttemptAtEpochMs = null,
                queuedAtEpochMs = queuedAt
            )
        )
        if (triggerSync && ensureSettings().cloudBackupEnabled) {
            requestSync("local_change")
        }
    }

    private suspend fun pushOutbox(datasetId: String, key: ByteArray) {
        val firestore = FirebaseFirestore.getInstance()
        val records = firestore.collection("datasets_v2").document(datasetId).collection("records")
        val pending = syncOutboxDao.getPending(limit = 200)
        pending.forEach { item ->
            val recordType = runCatching { SyncRecordType.valueOf(item.recordType) }.getOrNull() ?: return@forEach
            val data: Map<String, Any?> = when (SyncOperation.valueOf(item.operation)) {
                SyncOperation.UPSERT -> {
                    val payload = item.payloadJson ?: return@forEach
                    val aad = aad(datasetId, recordType, item.recordId, item.updatedAtEpochMs)
                    val (iv, ciphertext) = syncCryptoManager.encryptPayload(payload, key, aad)
                    mapOf(
                        "recordType" to recordType.name,
                        "recordId" to item.recordId,
                        "updatedAt" to item.updatedAtEpochMs,
                        "deletedAt" to null,
                        "source" to "mobile",
                        "schemaVersion" to 1,
                        "iv" to iv,
                        "ciphertext" to ciphertext
                    )
                }
                SyncOperation.TOMBSTONE -> {
                    mapOf(
                        "recordType" to recordType.name,
                        "recordId" to item.recordId,
                        "updatedAt" to item.updatedAtEpochMs,
                        "deletedAt" to (item.deletedAtEpochMs ?: item.updatedAtEpochMs),
                        "source" to "mobile",
                        "schemaVersion" to 1,
                        "iv" to null,
                        "ciphertext" to null
                    )
                }
            }
            try {
                records.document(documentId(recordType, item.recordId))
                    .set(data, SetOptions.merge())
                    .await()
                syncOutboxDao.deleteById(item.id)
            } catch (t: Throwable) {
                syncOutboxDao.markAttempt(item.id, clock.now().toEpochMilli())
            }
        }
    }

    private suspend fun pullRemote(datasetId: String, key: ByteArray, sinceEpochMs: Long?): Long? {
        val firestore = FirebaseFirestore.getInstance()
        val records = firestore.collection("datasets_v2").document(datasetId).collection("records")
        var query = records.orderBy("updatedAt", Query.Direction.ASCENDING).limit(500)
        if (sinceEpochMs != null) {
            query = query.whereGreaterThan("updatedAt", sinceEpochMs)
        }
        val snapshot = query.get().await()
        var maxUpdatedAt: Long? = sinceEpochMs
        snapshot.documents.forEach { doc ->
            val recordType = runCatching {
                SyncRecordType.valueOf(doc.getString("recordType").orEmpty())
            }.getOrNull() ?: return@forEach
            val recordId = doc.getString("recordId").orEmpty()
            if (recordId.isBlank()) return@forEach
            val updatedAt = doc.getLong("updatedAt") ?: return@forEach
            maxUpdatedAt = maxOf(maxUpdatedAt ?: Long.MIN_VALUE, updatedAt)
            val deletedAt = doc.getLong("deletedAt")
            if (deletedAt != null) {
                applyRemoteTombstone(recordType, recordId, updatedAt, deletedAt)
                return@forEach
            }
            val iv = doc.getString("iv") ?: return@forEach
            val ciphertext = doc.getString("ciphertext") ?: return@forEach
            val aad = aad(datasetId, recordType, recordId, updatedAt)
            val payloadJson = runCatching {
                syncCryptoManager.decryptPayload(ciphertext, iv, key, aad)
            }.getOrNull() ?: return@forEach
            applyRemoteUpsert(recordType = recordType, recordId = recordId, updatedAtEpochMs = updatedAt, payloadJson = payloadJson)
        }
        return maxUpdatedAt
    }

    private suspend fun applyRemoteUpsert(
        recordType: SyncRecordType,
        recordId: String,
        updatedAtEpochMs: Long,
        payloadJson: String
    ) {
        when (recordType) {
            SyncRecordType.MEDICATION -> {
                val localUpdatedAt = medicationDao.getById(recordId)?.updatedAtEpochMs
                if (!SyncConflictResolver.shouldApplyRemote(localUpdatedAt, updatedAtEpochMs)) return
                val payload = codec.medicationFromJson(payloadJson)
                medicationDao.insertMedication(
                    payload.medication.copy(
                        ownerProfileId = Defaults.OWNER_PROFILE_ID,
                        updatedAtEpochMs = updatedAtEpochMs
                    )
                )
                medicationDao.clearWeekdays(recordId)
                medicationDao.clearTimes(recordId)
                if (payload.weekdays.isNotEmpty()) medicationDao.insertWeekdays(payload.weekdays)
                if (payload.times.isNotEmpty()) medicationDao.insertTimes(payload.times)
            }
            SyncRecordType.DOSE_EVENT -> {
                val localUpdatedAt = doseEventDao.getById(recordId)?.updatedAtEpochMs
                if (!SyncConflictResolver.shouldApplyRemote(localUpdatedAt, updatedAtEpochMs)) return
                val payload = codec.doseEventFromJson(payloadJson)
                doseEventDao.upsert(
                    payload.copy(
                        ownerProfileId = Defaults.OWNER_PROFILE_ID,
                        updatedAtEpochMs = updatedAtEpochMs
                    )
                )
            }
            SyncRecordType.HIDDEN_INTAKE_LOG -> {
                val localUpdatedAt = hiddenIntakeLogDao.getById(recordId)?.createdAtEpochMs
                if (!SyncConflictResolver.shouldApplyRemote(localUpdatedAt, updatedAtEpochMs)) return
                val payload = codec.hiddenLogFromJson(payloadJson)
                hiddenIntakeLogDao.insert(
                    payload.copy(ownerProfileId = Defaults.OWNER_PROFILE_ID)
                )
            }
        }
    }

    private suspend fun applyRemoteTombstone(
        recordType: SyncRecordType,
        recordId: String,
        updatedAtEpochMs: Long,
        deletedAtEpochMs: Long
    ) {
        when (recordType) {
            SyncRecordType.MEDICATION -> {
                val local = medicationDao.getById(recordId) ?: return
                if (!SyncConflictResolver.shouldApplyRemote(local.updatedAtEpochMs, updatedAtEpochMs)) return
                medicationDao.deactivateMedication(recordId, updatedAtEpochMs)
                doseEventDao.deleteNonTakenByMedication(recordId)
                logCloudDeletionNotice(
                    recordType = recordType,
                    recordId = recordId,
                    medicationId = local.id,
                    medicationName = local.name,
                    deletedAtEpochMs = deletedAtEpochMs
                )
            }
            SyncRecordType.DOSE_EVENT -> {
                val local = doseEventDao.getById(recordId) ?: return
                if (!SyncConflictResolver.shouldApplyRemote(local.updatedAtEpochMs, updatedAtEpochMs)) return
                doseEventDao.deleteById(recordId)
                logCloudDeletionNotice(
                    recordType = recordType,
                    recordId = recordId,
                    medicationId = local.medicationId,
                    medicationName = local.medicationName,
                    deletedAtEpochMs = deletedAtEpochMs
                )
            }
            SyncRecordType.HIDDEN_INTAKE_LOG -> {
                val local = hiddenIntakeLogDao.getById(recordId) ?: return
                if (!SyncConflictResolver.shouldApplyRemote(local.createdAtEpochMs, updatedAtEpochMs)) return
                logCloudDeletionNotice(
                    recordType = recordType,
                    recordId = recordId,
                    medicationId = local.medicationId,
                    medicationName = local.medicationName,
                    deletedAtEpochMs = deletedAtEpochMs
                )
            }
        }
    }

    private suspend fun logCloudDeletionNotice(
        recordType: SyncRecordType,
        recordId: String,
        medicationId: String,
        medicationName: String,
        deletedAtEpochMs: Long
    ) {
        val details = "cloud_deleted:${recordType.name}:$recordId:$deletedAtEpochMs"
        if (hiddenIntakeLogDao.countByOperationDetails(details) > 0) return
        val now = clock.now().toEpochMilli()
        val entity = HiddenIntakeLogEntity(
            id = UUID.randomUUID().toString(),
            ownerProfileId = Defaults.OWNER_PROFILE_ID,
            doseEventId = recordId.takeIf { recordType == SyncRecordType.DOSE_EVENT },
            medicationId = medicationId,
            medicationName = medicationName,
            operationType = "CHANGE",
            operationDetails = details,
            actionSource = ActionSource.SYSTEM.name,
            takenAtEpochMs = deletedAtEpochMs,
            createdAtEpochMs = now
        )
        hiddenIntakeLogDao.insert(entity)
        enqueueOutbox(
            recordType = SyncRecordType.HIDDEN_INTAKE_LOG,
            recordId = entity.id,
            operation = SyncOperation.UPSERT,
            payloadJson = codec.hiddenLogToJson(entity),
            updatedAtEpochMs = entity.createdAtEpochMs,
            deletedAtEpochMs = null,
            triggerSync = false
        )
    }

    private suspend fun ensureAnonymousUser(): String {
        val auth = FirebaseAuth.getInstance()
        val current = auth.currentUser
        if (current != null) return current.uid
        val result = auth.signInAnonymously().await()
        return result.user?.uid ?: error("Anonymous auth failed")
    }

    private suspend fun joinDataset(syncCode: String): String {
        val payload = mapOf(
            "syncCode" to syncCode
        )
        val data = FirebaseFunctions.getInstance()
            .getHttpsCallable("joinDataset")
            .call(payload)
            .await()
            .data
        val map = data as? Map<*, *> ?: error("joinDataset returned invalid response")
        return map["datasetId"] as? String ?: error("joinDataset response missing datasetId")
    }

    private fun ensureFirebaseInitialized() {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context) ?: error("Firebase not initialized. Add google-services.json")
        }
    }

    private fun aad(
        datasetId: String,
        recordType: SyncRecordType,
        recordId: String,
        updatedAtEpochMs: Long
    ): String = "$datasetId|${recordType.name}|$recordId|$updatedAtEpochMs"

    private fun documentId(recordType: SyncRecordType, recordId: String): String {
        return "${recordType.name}_$recordId"
    }

    private fun outboxId(recordType: SyncRecordType, recordId: String): String {
        return "${recordType.name}:$recordId"
    }

    private suspend fun ensureSettings(): AppSettingsEntity {
        val current = settingsDao.getSettings()
        if (current != null) return current
        val defaults = AppSettingsEntity()
        settingsDao.upsertSettings(defaults)
        return defaults
    }

    private suspend fun isSyncDue(): Boolean {
        val settings = ensureSettings()
        if (!settings.cloudBackupEnabled || settings.datasetId.isNullOrBlank() || settings.wrappedSyncSecret.isNullOrBlank()) {
            return false
        }
        val lastSyncAt = settings.lastSyncAtEpochMs ?: return true
        return clock.now().toEpochMilli() - lastSyncAt >= HALF_HOUR_MS
    }

    private suspend fun updateSyncState(state: String) {
        mutateSettings { it.copy(syncState = state) }
    }

    private suspend fun mutateSettings(transform: (AppSettingsEntity) -> AppSettingsEntity) {
        val current = ensureSettings()
        settingsDao.upsertSettings(transform(current))
    }
}
