package com.medminder.app.data.sync

object SyncConflictResolver {
    fun shouldApplyRemote(localUpdatedAtEpochMs: Long?, remoteUpdatedAtEpochMs: Long): Boolean {
        val local = localUpdatedAtEpochMs ?: Long.MIN_VALUE
        return remoteUpdatedAtEpochMs > local
    }
}
