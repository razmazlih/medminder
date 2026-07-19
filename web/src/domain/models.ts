export type SyncRecordType = 'MEDICATION' | 'DOSE_EVENT' | 'HIDDEN_INTAKE_LOG';
export type SyncOperation = 'UPSERT' | 'TOMBSTONE';

export type SyncState = 'LOCAL_ONLY' | 'IDLE' | 'SYNCING' | 'ERROR';

export interface MedicationTime {
  localTime: string;
  sortOrder: number;
}

export interface MedicationRecord {
  id: string;
  ownerProfileId: string;
  name: string;
  doseText: string;
  doseUnit: string;
  foodRelation: 'BEFORE_FOOD' | 'AFTER_FOOD' | 'WITH_FOOD' | 'NONE';
  scheduleMode: 'FIXED_TIMES' | 'EVERY_X_HOURS' | 'EVERY_X_DAYS';
  intervalHours: number | null;
  windowStartTime: string | null;
  windowEndTime: string | null;
  startDate: string;
  endDate: string | null;
  notes: string;
  isActive: boolean;
  createdAtEpochMs: number;
  updatedAtEpochMs: number;
  deletedAtEpochMs: number | null;
  weekdays: number[];
  times: MedicationTime[];
}

export interface DoseEventRecord {
  id: string;
  ownerProfileId: string;
  medicationId: string;
  medicationName: string;
  scheduledEpochMs: number;
  localDate: string;
  localTime: string;
  status: 'PENDING' | 'MISSED' | 'TAKEN';
  actionSource: 'APP' | 'NOTIFICATION' | 'SYSTEM' | null;
  recordSource?: 'mobile' | 'web' | null;
  reminderAttemptCount: number;
  snoozeUntilEpochMs: number | null;
  respondedEpochMs: number | null;
  createdAtEpochMs: number;
  updatedAtEpochMs: number;
  deletedAtEpochMs: number | null;
}

export interface HiddenIntakeLogRecord {
  id: string;
  ownerProfileId: string;
  doseEventId: string | null;
  medicationId: string;
  medicationName: string;
  operationType: 'ADD' | 'UPDATE' | 'DELETE' | 'CHANGE';
  operationDetails: string | null;
  actionSource: 'APP' | 'NOTIFICATION' | 'SYSTEM';
  takenAtEpochMs: number;
  createdAtEpochMs: number;
  updatedAtEpochMs: number;
  deletedAtEpochMs: number | null;
}

export interface OutboxItem {
  id: string;
  recordType: SyncRecordType;
  recordId: string;
  operation: SyncOperation;
  payloadJson: string | null;
  updatedAtEpochMs: number;
  deletedAtEpochMs: number | null;
  attemptCount: number;
  lastAttemptAtEpochMs: number | null;
  queuedAtEpochMs: number;
}

export interface AppSettingsRecord {
  id: 'singleton';
  cloudBackupEnabled: boolean;
  localOnlyMode: boolean;
  datasetId: string | null;
  wrappedSyncSecret: string | null;
  localWrapKeyBase64: string | null;
  lastSyncAtEpochMs: number | null;
  lastPulledAtEpochMs: number | null;
  syncState: SyncState;
}

export interface CloudRecordEnvelope {
  recordType: SyncRecordType;
  recordId: string;
  updatedAt: number;
  deletedAt: number | null;
  source?: 'mobile' | 'web' | null;
  iv: string | null;
  ciphertext: string | null;
}

export interface CloudRecordDocument extends CloudRecordEnvelope {
  source: 'mobile' | 'web';
  schemaVersion: number;
}

export interface CloudDeletionNotice {
  id: string;
  recordType: SyncRecordType;
  recordTypeLabel: string;
  recordId: string;
  medicationName: string;
  deletedAtEpochMs: number;
  deletedAtLabel: string;
}

export const OWNER_PROFILE_ID = 'owner_primary';
