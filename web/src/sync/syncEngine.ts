import {
  decryptPayloadJson,
  deriveDataKey,
  encryptPayloadJson,
  generateLocalWrapKeyBase64,
  unwrapDataKey,
  wrapDataKey,
} from '@/core/crypto';
import { shouldApplyRemote } from '@/core/syncConflict';
import { db } from '@/db/appDb';
import type {
  CloudRecordDocument,
  DoseEventRecord,
  HiddenIntakeLogRecord,
  MedicationRecord,
  SyncRecordType,
} from '@/domain/models';
import { cloudRecordDocumentSchema, cloudRecordSchema } from '@/domain/schemas';
import {
  clearAllLocalData,
  deleteOutboxItem,
  documentId,
  getOrCreateSettings,
  getPendingOutbox,
  markOutboxAttempt,
  patchSettings,
  queueOutboxUpsert
} from '@/data/localStore';
import type { CloudAdapter } from '@/sync/cloudAdapter';
import {
  doseEventFromJson,
  doseEventToJson,
  hiddenLogFromJson,
  hiddenLogToJson,
  medicationFromJson,
  medicationToJson,
} from '@/sync/payloadCodec';

function aad(datasetId: string, recordType: SyncRecordType, recordId: string, updatedAtEpochMs: number): string {
  return `${datasetId}|${recordType}|${recordId}|${updatedAtEpochMs}`;
}

function unique(values: string[]): string[] {
  return Array.from(new Set(values));
}

function recordTypeFromUnknown(raw: unknown): SyncRecordType | null {
  if (raw === 'MEDICATION' || raw === 'DOSE_EVENT' || raw === 'HIDDEN_INTAKE_LOG') {
    return raw;
  }
  return null;
}

export class SyncEngine {
  private inFlight: Promise<void> | null = null;
  private rerunRequested = false;
  private readonly cloud: CloudAdapter;

  constructor(cloud: CloudAdapter) {
    this.cloud = cloud;
  }

  private isAccessRevokedError(error: unknown): boolean {
    const raw = error instanceof Error ? error.message : String(error ?? '');
    const message = raw.toLowerCase();
    return (
      message.includes('permission-denied') ||
      message.includes('insufficient permissions') ||
      message.includes('dataset is not active') ||
      message.includes('dataset not found') ||
      message.includes('not-found')
    );
  }

  async connectWithSyncCode(syncCode: string): Promise<string> {
    const normalizedSyncCode = syncCode.trim();
    if (!normalizedSyncCode) {
      throw new Error('Sync code is required');
    }

    await this.cloud.ensureAnonymousUser();
    const currentSettings = await getOrCreateSettings();
    const previousDatasetId = currentSettings.datasetId;
    const { datasetId } = await this.cloud.loginWebWithSyncCode(normalizedSyncCode);

    if (previousDatasetId && previousDatasetId !== datasetId) {
      try {
        await this.cloud.logoutWebSession(previousDatasetId);
      } catch {
        // Best effort: local reset still protects from data mixing.
      }
      await clearAllLocalData();
    }

    const dataKey = await deriveDataKey(normalizedSyncCode);
    const settings = await getOrCreateSettings();
    const wrapKeyBase64 = settings.localWrapKeyBase64 ?? generateLocalWrapKeyBase64();
    const wrappedSyncSecret = await wrapDataKey(dataKey, wrapKeyBase64);

    await patchSettings({
      cloudBackupEnabled: true,
      localOnlyMode: false,
      datasetId,
      wrappedSyncSecret,
      localWrapKeyBase64: wrapKeyBase64,
      syncState: 'IDLE',
    });

    return datasetId;
  }

  async logoutFromWeb(): Promise<void> {
    const settings = await getOrCreateSettings();
    const datasetId = settings.datasetId;

    if (datasetId) {
      try {
        await this.cloud.logoutWebSession(datasetId);
      } catch {
        // Best effort logout from server-side session.
      }
    }

    try {
      await this.cloud.signOut();
    } catch {
      // Best effort local auth sign-out.
    }

    await clearAllLocalData();
  }

  async syncNow(reason = 'manual'): Promise<void> {
    void reason;

    if (this.inFlight) {
      this.rerunRequested = true;
      return this.inFlight;
    }

    this.inFlight = (async () => {
      do {
        this.rerunRequested = false;
        await this.performSync();
      } while (this.rerunRequested);
    })().finally(() => {
      this.inFlight = null;
    });

    return this.inFlight;
  }

  private async performSync(): Promise<void> {
    const settings = await getOrCreateSettings();

    if (!settings.cloudBackupEnabled || settings.localOnlyMode) {
      await patchSettings({ syncState: 'LOCAL_ONLY' });
      return;
    }

    const datasetId = settings.datasetId;
    const wrappedSyncSecret = settings.wrappedSyncSecret;
    const localWrapKeyBase64 = settings.localWrapKeyBase64;

    if (!datasetId || !wrappedSyncSecret || !localWrapKeyBase64) {
      throw new Error('Missing sync credentials. Reconnect with sync code.');
    }

    await this.cloud.ensureAnonymousUser();
    await patchSettings({ syncState: 'SYNCING' });

    try {
      const key = await unwrapDataKey(wrappedSyncSecret, localWrapKeyBase64);
      const pulledAt = await this.pullRemote(datasetId, key, settings.lastPulledAtEpochMs);
      await this.pushOutbox(datasetId, key);

      const currentSettings = await getOrCreateSettings();
      const lastPulledAtEpochMs = currentSettings.lastPulledAtEpochMs;
      await patchSettings({
        syncState: 'IDLE',
        lastSyncAtEpochMs: Date.now(),
        lastPulledAtEpochMs: Math.max(lastPulledAtEpochMs ?? Number.MIN_SAFE_INTEGER, pulledAt ?? Number.MIN_SAFE_INTEGER),
      });
    } catch (error) {
      if (this.isAccessRevokedError(error)) {
        await this.logoutFromWeb();
        throw new Error('SESSION_REVOKED');
      }
      await patchSettings({ syncState: 'ERROR' });
      throw error;
    }
  }

  private async pullRemote(datasetId: string, dataKey: Uint8Array, sinceEpochMs: number | null): Promise<number | null> {
    const cloudRecords = await this.cloud.pullRecords(datasetId, sinceEpochMs);
    let maxUpdatedAt = sinceEpochMs;

    for (const record of cloudRecords) {
      try {
        const parsed = cloudRecordSchema.parse(record);
        const recordType = recordTypeFromUnknown(parsed.recordType);
        if (!recordType) {
          continue;
        }

        if (parsed.deletedAt != null) {
          await this.applyRemoteTombstone(recordType, parsed.recordId, parsed.updatedAt, parsed.deletedAt);
          maxUpdatedAt = maxUpdatedAt == null ? parsed.updatedAt : Math.max(maxUpdatedAt, parsed.updatedAt);
          continue;
        }

        if (!parsed.iv || !parsed.ciphertext) {
          continue;
        }

        const payloadJson = await this.decryptPayloadWithFallback({
          datasetId,
          recordType,
          recordId: parsed.recordId,
          updatedAt: parsed.updatedAt,
          iv: parsed.iv,
          ciphertext: parsed.ciphertext,
          dataKey,
        });

        await this.applyRemoteUpsert(
          recordType,
          parsed.recordId,
          parsed.updatedAt,
          payloadJson,
          parsed.source ?? null
        );
        maxUpdatedAt = maxUpdatedAt == null ? parsed.updatedAt : Math.max(maxUpdatedAt, parsed.updatedAt);
      } catch {
        continue;
      }
    }

    return maxUpdatedAt;
  }

  private async decryptPayloadWithFallback(params: {
    datasetId: string;
    recordType: SyncRecordType;
    recordId: string;
    updatedAt: number;
    iv: string;
    ciphertext: string;
    dataKey: Uint8Array;
  }): Promise<string> {
    const updatedAtSeconds = Math.floor(params.updatedAt / 1000);
    const candidates = unique([
      aad(params.datasetId, params.recordType, params.recordId, params.updatedAt),
      aad(params.datasetId, params.recordType, params.recordId, updatedAtSeconds),
      `${params.datasetId}|${params.recordType}|${params.recordId}`,
      `${params.recordType}|${params.recordId}|${params.updatedAt}`,
      `${params.recordType}|${params.recordId}|${updatedAtSeconds}`,
      '',
    ]);

    for (const candidate of candidates) {
      try {
        return await decryptPayloadJson(params.ciphertext, params.iv, params.dataKey, candidate);
      } catch {
        continue;
      }
    }

    throw new Error('Unable to decrypt payload with known AAD variants');
  }

  private async applyRemoteUpsert(
    recordType: SyncRecordType,
    recordId: string,
    updatedAtEpochMs: number,
    payloadJson: string,
    remoteSource: 'mobile' | 'web' | null
  ): Promise<void> {
    if (recordType === 'MEDICATION') {
      const local = await db.medications.get(recordId);
      if (!shouldApplyRemote(local?.updatedAtEpochMs, updatedAtEpochMs)) {
        return;
      }
      const payload = medicationFromJson(payloadJson);
      const remote: MedicationRecord = {
        ...payload,
        updatedAtEpochMs,
        deletedAtEpochMs: null,
      };
      await db.medications.put(remote);
      await this.clearOutboxIfStale(recordType, recordId, updatedAtEpochMs);
      return;
    }

    if (recordType === 'DOSE_EVENT') {
      const local = await db.doseEvents.get(recordId);
      if (!shouldApplyRemote(local?.updatedAtEpochMs, updatedAtEpochMs)) {
        if (local && remoteSource && local.updatedAtEpochMs === updatedAtEpochMs && local.recordSource !== remoteSource) {
          await db.doseEvents.put({
            ...local,
            recordSource: remoteSource,
          });
        }
        return;
      }
      const payload = doseEventFromJson(payloadJson);
      const remote: DoseEventRecord = {
        ...payload,
        recordSource: remoteSource ?? payload.recordSource ?? null,
        updatedAtEpochMs,
        deletedAtEpochMs: null,
      };
      await db.doseEvents.put(remote);
      await this.clearOutboxIfStale(recordType, recordId, updatedAtEpochMs);
      return;
    }

    const local = await db.hiddenIntakeLogs.get(recordId);
    if (!shouldApplyRemote(local?.updatedAtEpochMs, updatedAtEpochMs)) {
      return;
    }
    const payload = hiddenLogFromJson(payloadJson);
    const remote: HiddenIntakeLogRecord = {
      ...payload,
      updatedAtEpochMs,
      deletedAtEpochMs: null,
    };
    await db.hiddenIntakeLogs.put(remote);
    await this.clearOutboxIfStale(recordType, recordId, updatedAtEpochMs);
  }

  private async applyRemoteTombstone(
    recordType: SyncRecordType,
    recordId: string,
    updatedAtEpochMs: number,
    deletedAtEpochMs: number
  ): Promise<void> {
    if (recordType === 'MEDICATION') {
      const local = await db.medications.get(recordId);
      if (!local) {
        return;
      }
      if (!shouldApplyRemote(local.updatedAtEpochMs, updatedAtEpochMs)) {
        return;
      }
      await db.medications.put({
        ...local,
        isActive: false,
        updatedAtEpochMs,
        deletedAtEpochMs,
      });
      await this.clearOutboxIfStale(recordType, recordId, updatedAtEpochMs);
      return;
    }

    if (recordType === 'DOSE_EVENT') {
      const local = await db.doseEvents.get(recordId);
      if (!local) {
        return;
      }
      if (!shouldApplyRemote(local.updatedAtEpochMs, updatedAtEpochMs)) {
        return;
      }
      await db.doseEvents.put({
        ...local,
        updatedAtEpochMs,
        deletedAtEpochMs,
      });
      await this.clearOutboxIfStale(recordType, recordId, updatedAtEpochMs);
      return;
    }

    const local = await db.hiddenIntakeLogs.get(recordId);
    if (!local) {
      return;
    }
    if (!shouldApplyRemote(local.updatedAtEpochMs, updatedAtEpochMs)) {
      return;
    }
    await this.clearOutboxIfStale(recordType, recordId, updatedAtEpochMs);
  }

  private async pushOutbox(datasetId: string, dataKey: Uint8Array): Promise<void> {
    const pendingItems = await getPendingOutbox(200);

    for (const item of pendingItems) {
      try {
        const currentOutboxItem = await db.outbox.get(item.id);
        if (
          !currentOutboxItem
          || currentOutboxItem.operation !== item.operation
          || currentOutboxItem.updatedAtEpochMs !== item.updatedAtEpochMs
        ) {
          continue;
        }

        let payloadJson = item.payloadJson;
        let updatedAtEpochMs = item.updatedAtEpochMs;
        let deletedAtEpochMs = item.deletedAtEpochMs;

        if (item.operation === 'UPSERT') {
          const latestLocal = await this.getCurrentRecordPayload(item.recordType, item.recordId);
          if (!latestLocal) {
            await deleteOutboxItem(item.id);
            continue;
          }

          if (latestLocal.updatedAtEpochMs !== item.updatedAtEpochMs) {
            await this.clearOutboxIfStale(item.recordType, item.recordId, latestLocal.updatedAtEpochMs);
            continue;
          }

          payloadJson = latestLocal.payloadJson;
          updatedAtEpochMs = latestLocal.updatedAtEpochMs;
          deletedAtEpochMs = null;
        }

        const docId = documentId(item.recordType, item.recordId);
        const payload = await this.toCloudRecord(
          datasetId,
          item.recordType,
          item.recordId,
          item.operation,
          payloadJson,
          updatedAtEpochMs,
          deletedAtEpochMs,
          dataKey
        );
        await this.cloud.pushRecord(datasetId, docId, payload);
        await deleteOutboxItem(item.id);
      } catch (error) {
        void error;
        const currentOutboxItem = await db.outbox.get(item.id);
        if (
          currentOutboxItem
          && currentOutboxItem.operation === item.operation
          && currentOutboxItem.updatedAtEpochMs === item.updatedAtEpochMs
        ) {
          await markOutboxAttempt(item.id, Date.now());
        }
      }
    }
  }

  private async getCurrentRecordPayload(
    recordType: SyncRecordType,
    recordId: string
  ): Promise<{ payloadJson: string; updatedAtEpochMs: number } | null> {
    if (recordType === 'MEDICATION') {
      const medication = await db.medications.get(recordId);
      if (!medication) return null;
      return { payloadJson: medicationToJson(medication), updatedAtEpochMs: medication.updatedAtEpochMs };
    }

    if (recordType === 'DOSE_EVENT') {
      const event = await db.doseEvents.get(recordId);
      if (!event) return null;
      return { payloadJson: doseEventToJson(event), updatedAtEpochMs: event.updatedAtEpochMs };
    }

    const hiddenLog = await db.hiddenIntakeLogs.get(recordId);
    if (!hiddenLog) return null;
    return { payloadJson: hiddenLogToJson(hiddenLog), updatedAtEpochMs: hiddenLog.updatedAtEpochMs };
  }

  private async clearOutboxIfStale(
    recordType: SyncRecordType,
    recordId: string,
    updatedAtEpochMs: number
  ): Promise<void> {
    const id = `${recordType}:${recordId}`;
    const currentOutboxItem = await db.outbox.get(id);
    if (!currentOutboxItem) {
      return;
    }
    if (currentOutboxItem.updatedAtEpochMs <= updatedAtEpochMs) {
      await deleteOutboxItem(id);
    }
  }

  private async toCloudRecord(
    datasetId: string,
    recordType: SyncRecordType,
    recordId: string,
    operation: 'UPSERT' | 'TOMBSTONE',
    payloadJson: string | null,
    updatedAtEpochMs: number,
    deletedAtEpochMs: number | null,
    dataKey: Uint8Array
  ): Promise<CloudRecordDocument> {
    if (operation === 'TOMBSTONE') {
      return cloudRecordDocumentSchema.parse({
        recordType,
        recordId,
        updatedAt: updatedAtEpochMs,
        deletedAt: deletedAtEpochMs ?? updatedAtEpochMs,
        source: 'web',
        schemaVersion: 1,
        iv: null,
        ciphertext: null,
      });
    }

    if (!payloadJson) {
      throw new Error('Missing payload for UPSERT');
    }

    const encrypted = await encryptPayloadJson(
      payloadJson,
      dataKey,
      aad(datasetId, recordType, recordId, updatedAtEpochMs)
    );

    return cloudRecordDocumentSchema.parse({
      recordType,
      recordId,
      updatedAt: updatedAtEpochMs,
      deletedAt: null,
      source: 'web',
      schemaVersion: 1,
      iv: encrypted.iv,
      ciphertext: encrypted.ciphertext,
    });
  }

  async queueMedicationUpsert(medicationId: string): Promise<void> {
    const medication = await db.medications.get(medicationId);
    if (!medication) {
      return;
    }
    await queueOutboxUpsert('MEDICATION', medicationId, medicationToJson(medication), medication.updatedAtEpochMs);
  }

  async queueDoseEventUpsert(doseEventId: string): Promise<void> {
    const event = await db.doseEvents.get(doseEventId);
    if (!event) {
      return;
    }
    await queueOutboxUpsert('DOSE_EVENT', doseEventId, doseEventToJson(event), event.updatedAtEpochMs);
  }

  async queueHiddenLogUpsert(logId: string): Promise<void> {
    const log = await db.hiddenIntakeLogs.get(logId);
    if (!log) {
      return;
    }

    await queueOutboxUpsert('HIDDEN_INTAKE_LOG', logId, hiddenLogToJson(log), log.updatedAtEpochMs);
  }
}
