package com.medminder.app.data.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncConflictResolverTest {

    @Test
    fun shouldApplyRemoteWhenRemoteIsNewer() {
        assertTrue(SyncConflictResolver.shouldApplyRemote(localUpdatedAtEpochMs = 100L, remoteUpdatedAtEpochMs = 101L))
    }

    @Test
    fun shouldNotApplyRemoteWhenRemoteIsOlderOrEqual() {
        assertFalse(SyncConflictResolver.shouldApplyRemote(localUpdatedAtEpochMs = 100L, remoteUpdatedAtEpochMs = 100L))
        assertFalse(SyncConflictResolver.shouldApplyRemote(localUpdatedAtEpochMs = 100L, remoteUpdatedAtEpochMs = 99L))
    }

    @Test
    fun shouldApplyRemoteWhenLocalMissing() {
        assertTrue(SyncConflictResolver.shouldApplyRemote(localUpdatedAtEpochMs = null, remoteUpdatedAtEpochMs = 1L))
    }
}
