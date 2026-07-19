import { db } from '@/db/appDb';
import type { AppSettingsRecord, OutboxItem, SyncOperation, SyncRecordType } from '@/domain/models';

const SETTINGS_ID: AppSettingsRecord['id'] = 'singleton';

export const defaultSettings: AppSettingsRecord = {
  id: SETTINGS_ID,
  cloudBackupEnabled: false,
  localOnlyMode: true,
  datasetId: null,
  wrappedSyncSecret: null,
  localWrapKeyBase64: null,
  lastSyncAtEpochMs: null,
  lastPulledAtEpochMs: null,
  syncState: 'LOCAL_ONLY',
};

export interface SyncSnapshot {
  settings: AppSettingsRecord;
  outboxCount: number;
}

export async function getOrCreateSettings(): Promise<AppSettingsRecord> {
  const existing = await db.settings.get(SETTINGS_ID);
  if (existing) {
    return existing;
  }
  await db.settings.put(defaultSettings);
  return defaultSettings;
}

export async function patchSettings(partial: Partial<AppSettingsRecord>): Promise<AppSettingsRecord> {
  const current = await getOrCreateSettings();
  const next: AppSettingsRecord = {
    ...current,
    ...partial,
    id: SETTINGS_ID,
  };
  await db.settings.put(next);
  return next;
}

export function outboxId(recordType: SyncRecordType, recordId: string): string {
  return `${recordType}:${recordId}`;
}

export function documentId(recordType: SyncRecordType, recordId: string): string {
  return `${recordType}_${recordId}`;
}

export async function queueOutbox(
  recordType: SyncRecordType,
  recordId: string,
  operation: SyncOperation,
  payloadJson: string | null,
  updatedAtEpochMs: number,
  deletedAtEpochMs: number | null
): Promise<void> {
  const entity: OutboxItem = {
    id: outboxId(recordType, recordId),
    recordType,
    recordId,
    operation,
    payloadJson,
    updatedAtEpochMs,
    deletedAtEpochMs,
    attemptCount: 0,
    lastAttemptAtEpochMs: null,
    queuedAtEpochMs: Date.now(),
  };
  await db.outbox.put(entity);
}

export async function queueOutboxUpsert(
  recordType: SyncRecordType,
  recordId: string,
  payloadJson: string,
  updatedAtEpochMs: number
): Promise<void> {
  await queueOutbox(recordType, recordId, 'UPSERT', payloadJson, updatedAtEpochMs, null);
}

export async function queueOutboxTombstone(
  recordType: SyncRecordType,
  recordId: string,
  updatedAtEpochMs: number,
  deletedAtEpochMs: number
): Promise<void> {
  await queueOutbox(recordType, recordId, 'TOMBSTONE', null, updatedAtEpochMs, deletedAtEpochMs);
}

export async function getPendingOutbox(limit = 200): Promise<OutboxItem[]> {
  return db.outbox.orderBy('queuedAtEpochMs').limit(limit).toArray();
}

export async function deleteOutboxItem(id: string): Promise<void> {
  await db.outbox.delete(id);
}

export async function markOutboxAttempt(id: string, attemptedAtEpochMs: number): Promise<void> {
  const existing = await db.outbox.get(id);
  if (!existing) {
    return;
  }
  await db.outbox.put({
    ...existing,
    attemptCount: existing.attemptCount + 1,
    lastAttemptAtEpochMs: attemptedAtEpochMs,
  });
}

export async function getSyncSnapshot(): Promise<SyncSnapshot> {
  const [settings, outboxCount] = await Promise.all([getOrCreateSettings(), db.outbox.count()]);
  return {
    settings,
    outboxCount,
  };
}

export async function clearAllLocalData(): Promise<void> {
  await db.settings.clear();
  await db.medications.clear();
  await db.doseEvents.clear();
  await db.hiddenIntakeLogs.clear();
  await db.outbox.clear();
}

export async function clearAllDataForTests(): Promise<void> {
  await clearAllLocalData();
}
