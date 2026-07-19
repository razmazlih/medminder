import { describe, expect, it } from 'vitest';

import { deriveDataKey, encryptPayloadJson } from '@/core/crypto';
import { db } from '@/db/appDb';
import type { CloudRecordDocument, MedicationRecord } from '@/domain/models';
import { getOrCreateSettings } from '@/data/localStore';
import { FakeCloudAdapter } from '@/sync/fakeCloudAdapter';
import { medicationToJson } from '@/sync/payloadCodec';
import { SyncEngine } from '@/sync/syncEngine';

function sampleMedication(id: string, updatedAtEpochMs: number, name = 'אקמול'): MedicationRecord {
  return {
    id,
    ownerProfileId: 'owner_primary',
    name,
    doseText: '1',
    doseUnit: 'יחידה',
    foodRelation: 'NONE',
    scheduleMode: 'FIXED_TIMES',
    intervalHours: null,
    windowStartTime: null,
    windowEndTime: null,
    startDate: '2026-03-16',
    endDate: null,
    notes: '',
    isActive: true,
    createdAtEpochMs: updatedAtEpochMs,
    updatedAtEpochMs,
    deletedAtEpochMs: null,
    weekdays: [1, 2, 3, 4, 5, 6, 7],
    times: [{ localTime: '08:00', sortOrder: 0 }],
  };
}

describe('SyncEngine integration Pull/Merge/Push', () => {
  it('pushes local, pulls remote updates, resolves conflicts, and handles tombstones', async () => {
    const adapter = new FakeCloudAdapter();
    const engine = new SyncEngine(adapter);

    const syncCode = 'SYNC-CODE-12345';
    await adapter.issueAppSyncCode(syncCode);
    const datasetId = await engine.connectWithSyncCode(syncCode);

    const localMedication = sampleMedication('med-1', 1000, 'אקמול');
    await db.medications.put(localMedication);
    await engine.queueMedicationUpsert(localMedication.id);

    await engine.syncNow('push_local');

    expect(await db.outbox.count()).toBe(0);
    const pushedRecords = await adapter.pullRecords(datasetId, null);
    const pushedMedication = pushedRecords.find((record) => record.recordId === localMedication.id);
    expect(pushedMedication?.ciphertext).toBeTruthy();
    expect(pushedMedication?.iv).toBeTruthy();

    const dataKey = await deriveDataKey(syncCode);

    const newerRemoteMedication = sampleMedication('med-1', 2000, 'אקמול חדש');
    const newerEncrypted = await encryptPayloadJson(
      medicationToJson(newerRemoteMedication),
      dataKey,
      `${datasetId}|MEDICATION|${newerRemoteMedication.id}|${newerRemoteMedication.updatedAtEpochMs}`
    );

    const newerRemoteRecord: CloudRecordDocument = {
      recordType: 'MEDICATION',
      recordId: newerRemoteMedication.id,
      updatedAt: newerRemoteMedication.updatedAtEpochMs,
      deletedAt: null,
      source: 'web',
      schemaVersion: 1,
      iv: newerEncrypted.iv,
      ciphertext: newerEncrypted.ciphertext,
    };

    await adapter.pushRecord(datasetId, `MEDICATION_${newerRemoteMedication.id}`, newerRemoteRecord);
    await engine.syncNow('pull_remote');

    expect((await db.medications.get('med-1'))?.name).toBe('אקמול חדש');

    await db.medications.put(sampleMedication('med-1', 4000, 'גרסה מקומית'));
    await engine.queueMedicationUpsert('med-1');

    const olderRemoteMedication = sampleMedication('med-1', 3500, 'גרסה עננית ישנה');
    const olderEncrypted = await encryptPayloadJson(
      medicationToJson(olderRemoteMedication),
      dataKey,
      `${datasetId}|MEDICATION|${olderRemoteMedication.id}|${olderRemoteMedication.updatedAtEpochMs}`
    );

    await adapter.pushRecord(datasetId, `MEDICATION_${olderRemoteMedication.id}`, {
      recordType: 'MEDICATION',
      recordId: olderRemoteMedication.id,
      updatedAt: olderRemoteMedication.updatedAtEpochMs,
      deletedAt: null,
      source: 'web',
      schemaVersion: 1,
      iv: olderEncrypted.iv,
      ciphertext: olderEncrypted.ciphertext,
    });

    await engine.syncNow('conflict_resolution');

    expect((await db.medications.get('med-1'))?.name).toBe('גרסה מקומית');

    await adapter.pushRecord(datasetId, 'MEDICATION_med-1', {
      recordType: 'MEDICATION',
      recordId: 'med-1',
      updatedAt: 5000,
      deletedAt: 5000,
      source: 'web',
      schemaVersion: 1,
      iv: null,
      ciphertext: null,
    });

    await engine.syncNow('remote_tombstone');

    const localAfterTombstone = await db.medications.get('med-1');
    expect(localAfterTombstone?.isActive).toBe(false);

    const logs = await db.hiddenIntakeLogs
      .where('operationDetails')
      .startsWith('cloud_deleted:MEDICATION:')
      .toArray();
    expect(logs.length).toBe(0);
  });

  it('continues pulling valid records when one remote record is malformed', async () => {
    const adapter = new FakeCloudAdapter();
    const engine = new SyncEngine(adapter);

    const syncCode = 'SYNC-CODE-12345';
    await adapter.issueAppSyncCode(syncCode);
    const datasetId = await engine.connectWithSyncCode(syncCode);
    const dataKey = await deriveDataKey(syncCode);

    const malformedRecord: CloudRecordDocument = {
      recordType: 'MEDICATION',
      recordId: 'med-bad',
      updatedAt: 1000,
      deletedAt: null,
      source: 'web',
      schemaVersion: 1,
      iv: 'not-base64',
      ciphertext: 'not-base64',
    };

    const validRemoteMedication = sampleMedication('med-good', 2000, 'נורופן');
    const encrypted = await encryptPayloadJson(
      medicationToJson(validRemoteMedication),
      dataKey,
      `${datasetId}|MEDICATION|${validRemoteMedication.id}|${validRemoteMedication.updatedAtEpochMs}`
    );

    const validRecord: CloudRecordDocument = {
      recordType: 'MEDICATION',
      recordId: validRemoteMedication.id,
      updatedAt: validRemoteMedication.updatedAtEpochMs,
      deletedAt: null,
      source: 'web',
      schemaVersion: 1,
      iv: encrypted.iv,
      ciphertext: encrypted.ciphertext,
    };

    await adapter.pushRecord(datasetId, 'MEDICATION_med-bad', malformedRecord);
    await adapter.pushRecord(datasetId, `MEDICATION_${validRemoteMedication.id}`, validRecord);

    await engine.syncNow('pull_with_malformed_remote_record');

    expect((await db.medications.get(validRemoteMedication.id))?.name).toBe('נורופן');
  });

  it('pulls records encrypted with legacy aad format', async () => {
    const adapter = new FakeCloudAdapter();
    const engine = new SyncEngine(adapter);

    const syncCode = 'SYNC-CODE-12345';
    await adapter.issueAppSyncCode(syncCode);
    const datasetId = await engine.connectWithSyncCode(syncCode);
    const dataKey = await deriveDataKey(syncCode);

    const remoteMedication = sampleMedication('med-legacy-aad', 3000, 'אדויל');
    const encrypted = await encryptPayloadJson(
      medicationToJson(remoteMedication),
      dataKey,
      `${datasetId}|MEDICATION|${remoteMedication.id}`
    );

    await adapter.pushRecord(datasetId, `MEDICATION_${remoteMedication.id}`, {
      recordType: 'MEDICATION',
      recordId: remoteMedication.id,
      updatedAt: remoteMedication.updatedAtEpochMs,
      deletedAt: null,
      source: 'mobile',
      schemaVersion: 1,
      iv: encrypted.iv,
      ciphertext: encrypted.ciphertext,
    });

    await engine.syncNow('pull_legacy_aad');

    expect((await db.medications.get(remoteMedication.id))?.name).toBe('אדויל');
  });

  it('logs out web automatically when app replaces the sync code', async () => {
    const adapter = new FakeCloudAdapter();
    const engine = new SyncEngine(adapter);

    const syncCodeA1 = 'SYNC-CODE-A1';
    await adapter.issueAppSyncCode(syncCodeA1, 'app-owner-a');
    await engine.connectWithSyncCode(syncCodeA1);

    const syncCodeA2 = 'SYNC-CODE-A2';
    await adapter.issueAppSyncCode(syncCodeA2, 'app-owner-a');

    await expect(engine.syncNow('heartbeat')).rejects.toThrow('SESSION_REVOKED');

    const settings = await getOrCreateSettings();
    expect(settings.datasetId).toBeNull();
    expect(settings.wrappedSyncSecret).toBeNull();
    expect(settings.cloudBackupEnabled).toBe(false);
    expect(settings.localOnlyMode).toBe(true);
  });
});
