import Dexie, { type Table } from 'dexie';

import type {
  AppSettingsRecord,
  DoseEventRecord,
  HiddenIntakeLogRecord,
  MedicationRecord,
  OutboxItem,
} from '@/domain/models';

class MedMinderDb extends Dexie {
  settings!: Table<AppSettingsRecord, AppSettingsRecord['id']>;
  medications!: Table<MedicationRecord, MedicationRecord['id']>;
  doseEvents!: Table<DoseEventRecord, DoseEventRecord['id']>;
  hiddenIntakeLogs!: Table<HiddenIntakeLogRecord, HiddenIntakeLogRecord['id']>;
  outbox!: Table<OutboxItem, OutboxItem['id']>;

  constructor() {
    super('medminder-web-db');

    this.version(1).stores({
      settings: 'id',
      medications: 'id, ownerProfileId, isActive, updatedAtEpochMs, deletedAtEpochMs',
      doseEvents: 'id, ownerProfileId, localDate, updatedAtEpochMs, deletedAtEpochMs',
      hiddenIntakeLogs: 'id, ownerProfileId, takenAtEpochMs, updatedAtEpochMs, operationDetails',
      outbox: 'id, [recordType+recordId], queuedAtEpochMs, attemptCount, lastAttemptAtEpochMs',
    });

    this.version(2).stores({
      settings: 'id',
      medications: 'id, ownerProfileId, isActive, updatedAtEpochMs, deletedAtEpochMs',
      doseEvents: 'id, ownerProfileId, localDate, updatedAtEpochMs, deletedAtEpochMs',
      hiddenIntakeLogs: 'id, ownerProfileId, takenAtEpochMs, createdAtEpochMs, updatedAtEpochMs, operationDetails',
      outbox: 'id, [recordType+recordId], queuedAtEpochMs, attemptCount, lastAttemptAtEpochMs',
    });
  }
}

export const db = new MedMinderDb();
