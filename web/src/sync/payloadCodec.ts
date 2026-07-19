import type { DoseEventRecord, HiddenIntakeLogRecord, MedicationRecord } from '@/domain/models';
import {
  doseEventPayloadSchema,
  hiddenIntakeLogPayloadSchema,
  medicationPayloadSchema,
} from '@/domain/schemas';

type UnknownRecord = Record<string, unknown>;

interface MedicationPayload {
  id: string;
  ownerProfileId: string;
  name: string;
  doseText: string;
  doseUnit: string;
  foodRelation: MedicationRecord['foodRelation'];
  scheduleMode: MedicationRecord['scheduleMode'];
  intervalHours: number | null;
  windowStartTime: string | null;
  windowEndTime: string | null;
  startDate: string;
  endDate: string | null;
  notes: string;
  isActive: boolean;
  createdAtEpochMs: number;
  updatedAtEpochMs: number;
  weekdays: number[];
  times: Array<{ localTime: string; sortOrder: number }>;
}

function pick(raw: UnknownRecord, ...keys: string[]): unknown {
  for (const key of keys) {
    if (raw[key] !== undefined) {
      return raw[key];
    }
  }
  return undefined;
}

function numberOrNull(value: unknown): number | null {
  if (value == null || value === '') {
    return null;
  }
  const numeric = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(numeric) ? numeric : null;
}

function normalizeMedicationPayload(raw: UnknownRecord): UnknownRecord {
  const normalizedWeekdays = Array.isArray(pick(raw, 'weekdays', 'week_days'))
    ? (pick(raw, 'weekdays', 'week_days') as unknown[]).map((item) => Number(item))
    : [];
  const normalizedTimes = Array.isArray(raw.times)
    ? raw.times.map((entry, index) => {
        const value = (entry ?? {}) as UnknownRecord;
        const localTime = pick(value, 'localTime', 'local_time');
        return {
          localTime: typeof localTime === 'string' ? localTime : '',
          sortOrder: numberOrNull(pick(value, 'sortOrder', 'sort_order')) ?? index,
        };
      })
    : [];

  const createdAtEpochMs = numberOrNull(pick(raw, 'createdAtEpochMs', 'created_at_epoch_ms'));
  const updatedAtEpochMs = numberOrNull(pick(raw, 'updatedAtEpochMs', 'updated_at_epoch_ms')) ?? createdAtEpochMs;

  return {
    id: pick(raw, 'id'),
    ownerProfileId: pick(raw, 'ownerProfileId', 'owner_profile_id'),
    name: pick(raw, 'name'),
    doseText: pick(raw, 'doseText', 'dose_text'),
    doseUnit: pick(raw, 'doseUnit', 'dose_unit'),
    foodRelation: pick(raw, 'foodRelation', 'food_relation'),
    scheduleMode: pick(raw, 'scheduleMode', 'schedule_mode'),
    intervalHours: numberOrNull(pick(raw, 'intervalHours', 'interval_hours')),
    windowStartTime: pick(raw, 'windowStartTime', 'window_start_time') ?? null,
    windowEndTime: pick(raw, 'windowEndTime', 'window_end_time') ?? null,
    startDate: pick(raw, 'startDate', 'start_date'),
    endDate: pick(raw, 'endDate', 'end_date') ?? null,
    notes: pick(raw, 'notes') ?? '',
    isActive: pick(raw, 'isActive', 'is_active'),
    createdAtEpochMs,
    updatedAtEpochMs,
    weekdays: normalizedWeekdays,
    times: normalizedTimes,
  };
}

function normalizeDoseEventPayload(raw: UnknownRecord): UnknownRecord {
  const scheduledEpochMs = numberOrNull(pick(raw, 'scheduledEpochMs', 'scheduled_epoch_ms'));
  const createdAtEpochMs = numberOrNull(pick(raw, 'createdAtEpochMs', 'created_at_epoch_ms'));
  const updatedAtEpochMs = numberOrNull(pick(raw, 'updatedAtEpochMs', 'updated_at_epoch_ms')) ?? createdAtEpochMs;
  const status = pick(raw, 'status');
  const recordSourceRaw = pick(raw, 'recordSource', 'record_source');
  const recordSource = recordSourceRaw === 'mobile' || recordSourceRaw === 'web'
    ? recordSourceRaw
    : null;

  return {
    id: pick(raw, 'id'),
    ownerProfileId: pick(raw, 'ownerProfileId', 'owner_profile_id'),
    medicationId: pick(raw, 'medicationId', 'medication_id'),
    medicationName: pick(raw, 'medicationName', 'medication_name'),
    scheduledEpochMs,
    localDate: pick(raw, 'localDate', 'local_date'),
    localTime: pick(raw, 'localTime', 'local_time'),
    status,
    actionSource: pick(raw, 'actionSource', 'action_source') ?? 'APP',
    recordSource,
    reminderAttemptCount: numberOrNull(pick(raw, 'reminderAttemptCount', 'reminder_attempt_count')) ?? 0,
    snoozeUntilEpochMs: numberOrNull(pick(raw, 'snoozeUntilEpochMs', 'snooze_until_epoch_ms')),
    respondedEpochMs:
      numberOrNull(pick(raw, 'respondedEpochMs', 'responded_epoch_ms'))
      ?? (status === 'TAKEN' ? scheduledEpochMs : null),
    createdAtEpochMs,
    updatedAtEpochMs,
  };
}

function normalizeHiddenLogPayload(raw: UnknownRecord): UnknownRecord {
  const createdAtEpochMs = numberOrNull(pick(raw, 'createdAtEpochMs', 'created_at_epoch_ms'));

  return {
    id: pick(raw, 'id'),
    ownerProfileId: pick(raw, 'ownerProfileId', 'owner_profile_id'),
    doseEventId: pick(raw, 'doseEventId', 'dose_event_id') ?? null,
    medicationId: pick(raw, 'medicationId', 'medication_id'),
    medicationName: pick(raw, 'medicationName', 'medication_name'),
    operationType: pick(raw, 'operationType', 'operation_type'),
    operationDetails: pick(raw, 'operationDetails', 'operation_details') ?? null,
    actionSource: pick(raw, 'actionSource', 'action_source') ?? 'APP',
    takenAtEpochMs: numberOrNull(pick(raw, 'takenAtEpochMs', 'taken_at_epoch_ms')),
    createdAtEpochMs,
    updatedAtEpochMs:
      numberOrNull(pick(raw, 'updatedAtEpochMs', 'updated_at_epoch_ms'))
      ?? createdAtEpochMs,
  };
}

export function medicationToJson(record: MedicationRecord): string {
  const payload = medicationPayloadSchema.parse({
    id: record.id,
    ownerProfileId: record.ownerProfileId,
    name: record.name,
    doseText: record.doseText,
    doseUnit: record.doseUnit,
    foodRelation: record.foodRelation,
    scheduleMode: record.scheduleMode,
    intervalHours: record.intervalHours,
    windowStartTime: record.windowStartTime,
    windowEndTime: record.windowEndTime,
    startDate: record.startDate,
    endDate: record.endDate,
    notes: record.notes,
    isActive: record.isActive,
    createdAtEpochMs: record.createdAtEpochMs,
    updatedAtEpochMs: record.updatedAtEpochMs,
    weekdays: [...record.weekdays].sort((a, b) => a - b),
    times: [...record.times].sort((a, b) => a.sortOrder - b.sortOrder),
  }) as MedicationPayload;
  return JSON.stringify(payload);
}

export function medicationFromJson(payloadJson: string): MedicationRecord {
  const parsed = medicationPayloadSchema.parse(normalizeMedicationPayload(JSON.parse(payloadJson) as UnknownRecord));
  return {
    ...parsed,
    deletedAtEpochMs: null,
  };
}

export function doseEventToJson(record: DoseEventRecord): string {
  const payload = doseEventPayloadSchema.parse({
    id: record.id,
    ownerProfileId: record.ownerProfileId,
    medicationId: record.medicationId,
    medicationName: record.medicationName,
    scheduledEpochMs: record.scheduledEpochMs,
    localDate: record.localDate,
    localTime: record.localTime,
    status: record.status,
    actionSource: record.actionSource,
    recordSource: record.recordSource,
    reminderAttemptCount: record.reminderAttemptCount,
    snoozeUntilEpochMs: record.snoozeUntilEpochMs,
    respondedEpochMs: record.respondedEpochMs,
    createdAtEpochMs: record.createdAtEpochMs,
    updatedAtEpochMs: record.updatedAtEpochMs,
  });
  return JSON.stringify(payload);
}

export function doseEventFromJson(payloadJson: string): DoseEventRecord {
  const normalized = normalizeDoseEventPayload(JSON.parse(payloadJson) as UnknownRecord);
  const parsed = doseEventPayloadSchema.parse(normalized);
  return {
    ...parsed,
    deletedAtEpochMs: null,
  };
}

export function hiddenLogToJson(record: HiddenIntakeLogRecord): string {
  const payload = hiddenIntakeLogPayloadSchema.parse({
    id: record.id,
    ownerProfileId: record.ownerProfileId,
    doseEventId: record.doseEventId,
    medicationId: record.medicationId,
    medicationName: record.medicationName,
    operationType: record.operationType,
    operationDetails: record.operationDetails,
    actionSource: record.actionSource,
    takenAtEpochMs: record.takenAtEpochMs,
    createdAtEpochMs: record.createdAtEpochMs,
  });
  return JSON.stringify(payload);
}

export function hiddenLogFromJson(payloadJson: string): HiddenIntakeLogRecord {
  const normalized = normalizeHiddenLogPayload(JSON.parse(payloadJson) as UnknownRecord);
  const parsed = hiddenIntakeLogPayloadSchema.parse(normalized);
  const updatedAtEpochMs = numberOrNull(normalized.updatedAtEpochMs) ?? parsed.createdAtEpochMs;
  return {
    ...parsed,
    updatedAtEpochMs,
    deletedAtEpochMs: null,
  };
}
