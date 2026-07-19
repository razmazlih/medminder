package com.medminder.app.data.sync

enum class SyncRecordType {
    MEDICATION,
    DOSE_EVENT,
    HIDDEN_INTAKE_LOG
}

enum class SyncOperation {
    UPSERT,
    TOMBSTONE
}

data class CloudRecordEnvelope(
    val recordType: SyncRecordType,
    val recordId: String,
    val updatedAtEpochMs: Long,
    val deletedAtEpochMs: Long?,
    val ivBase64: String?,
    val ciphertextBase64: String?
)
