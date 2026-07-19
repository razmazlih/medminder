import { createId } from '@/core/id';
import { getActiveProfileId } from '@/core/profile';
import { nowEpochMs, toLocalDate, toLocalTime } from '@/core/time';
import { db } from '@/db/appDb';
import type { DoseEventRecord, HiddenIntakeLogRecord, MedicationRecord } from '@/domain/models';
import { getOrCreateSettings, queueOutboxTombstone } from '@/data/localStore';
import { syncEngine } from '@/sync/instance';

const WEB_RECORD_SOURCE = 'web' as const;

export interface MedicationInput {
  id?: string;
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
  weekdays: number[];
  times: string[];
  isActive: boolean;
}

async function maybeAutoSync(): Promise<void> {
  const settings = await getOrCreateSettings();
  if (settings.cloudBackupEnabled && !settings.localOnlyMode) {
    void syncEngine.syncNow('local_change');
  }
}

async function addHiddenLog(input: {
  doseEventId: string | null;
  medicationId: string;
  medicationName: string;
  operationType: HiddenIntakeLogRecord['operationType'];
  operationDetails: string;
  actionSource: HiddenIntakeLogRecord['actionSource'];
  takenAtEpochMs: number;
}): Promise<string> {
  const profileId = await getActiveProfileId();
  const id = createId();
  const now = nowEpochMs();
  const entity: HiddenIntakeLogRecord = {
    id,
    ownerProfileId: profileId,
    doseEventId: input.doseEventId,
    medicationId: input.medicationId,
    medicationName: input.medicationName,
    operationType: input.operationType,
    operationDetails: input.operationDetails,
    actionSource: input.actionSource,
    takenAtEpochMs: input.takenAtEpochMs,
    createdAtEpochMs: now,
    updatedAtEpochMs: now,
    deletedAtEpochMs: null,
  };

  await db.hiddenIntakeLogs.put(entity);
  await syncEngine.queueHiddenLogUpsert(id);
  return id;
}

export async function saveMedication(input: MedicationInput): Promise<string> {
  const profileId = await getActiveProfileId();
  const now = nowEpochMs();
  const id = input.id ?? createId();
  const existing = await db.medications.get(id);
  const normalizedTimes = input.scheduleMode === 'EVERY_X_DAYS'
    ? input.times.filter(Boolean).slice(0, 1)
    : input.times.filter(Boolean);

  const entity: MedicationRecord = {
    id,
    ownerProfileId: profileId,
    name: input.name.trim(),
    doseText: input.doseText.trim(),
    doseUnit: input.doseUnit.trim(),
    foodRelation: input.foodRelation,
    scheduleMode: input.scheduleMode,
    intervalHours: input.intervalHours,
    windowStartTime: input.windowStartTime,
    windowEndTime: input.windowEndTime,
    startDate: input.startDate,
    endDate: input.endDate,
    notes: input.notes,
    isActive: input.isActive,
    createdAtEpochMs: existing?.createdAtEpochMs ?? now,
    updatedAtEpochMs: now,
    deletedAtEpochMs: null,
    weekdays: [...new Set(input.weekdays)].sort((a, b) => a - b),
    times: normalizedTimes
      .map((localTime, index) => ({
        localTime,
        sortOrder: index,
      }))
      .sort((a, b) => a.sortOrder - b.sortOrder),
  };

  await db.medications.put(entity);
  await syncEngine.queueMedicationUpsert(id);
  await maybeAutoSync();
  return id;
}

export async function setMedicationActive(medicationId: string, isActive: boolean): Promise<void> {
  const profileId = await getActiveProfileId();
  const medication = await db.medications.get(medicationId);
  if (!medication || medication.ownerProfileId !== profileId) {
    return;
  }

  await db.medications.put({
    ...medication,
    isActive,
    updatedAtEpochMs: nowEpochMs(),
    deletedAtEpochMs: isActive ? null : medication.deletedAtEpochMs,
  });

  await syncEngine.queueMedicationUpsert(medicationId);
  await maybeAutoSync();
}

export async function createDoseEvent(input: {
  medicationId: string;
  localDate?: string;
  localTime?: string;
  status?: DoseEventRecord['status'];
}): Promise<string> {
  const profileId = await getActiveProfileId();
  const medication = await db.medications.get(input.medicationId);
  if (!medication || medication.ownerProfileId !== profileId) {
    throw new Error('Medication not found');
  }

  const now = nowEpochMs();
  const localDate = input.localDate ?? toLocalDate(now);
  const localTime = input.localTime ?? toLocalTime(now);
  const scheduledEpochMs = new Date(`${localDate}T${localTime}:00`).getTime();
  const id = createId();

  const event: DoseEventRecord = {
    id,
    ownerProfileId: profileId,
    medicationId: medication.id,
    medicationName: medication.name,
    scheduledEpochMs,
    localDate,
    localTime,
    status: input.status ?? 'PENDING',
    actionSource: 'APP',
    recordSource: WEB_RECORD_SOURCE,
    reminderAttemptCount: 0,
    snoozeUntilEpochMs: null,
    respondedEpochMs: input.status === 'TAKEN' ? now : null,
    createdAtEpochMs: now,
    updatedAtEpochMs: now,
    deletedAtEpochMs: null,
  };

  await db.doseEvents.put(event);
  await syncEngine.queueDoseEventUpsert(event.id);
  await addHiddenLog({
    doseEventId: event.id,
    medicationId: event.medicationId,
    medicationName: event.medicationName,
    operationType: 'ADD',
    operationDetails: 'event_added',
    actionSource: 'APP',
    takenAtEpochMs: now,
  });
  await maybeAutoSync();
  return id;
}

export async function markFixedDoseTaken(input: {
  medicationId: string;
  scheduledLocalTime: string;
  localDate?: string;
}): Promise<string> {
  const profileId = await getActiveProfileId();
  const medication = await db.medications.get(input.medicationId);
  if (!medication || medication.ownerProfileId !== profileId) {
    throw new Error('Medication not found');
  }

  const now = nowEpochMs();
  const localDate = input.localDate ?? toLocalDate(now);
  const scheduledEpochMs = new Date(`${localDate}T${input.scheduledLocalTime}:00`).getTime();
  const deterministicEventId = `${input.medicationId}_${scheduledEpochMs}`;

  const existingById = await db.doseEvents.get(deterministicEventId);
  const existingByTime = await db.doseEvents
    .where('ownerProfileId')
    .equals(profileId)
    .and(
      (event) =>
        event.medicationId === input.medicationId
        && event.localDate === localDate
        && event.localTime === input.scheduledLocalTime
        && event.deletedAtEpochMs == null
    )
    .first();

  const existing = [existingById, existingByTime]
    .find(
      (event) =>
        event != null
        && event.ownerProfileId === profileId
        && event.medicationId === input.medicationId
        && event.deletedAtEpochMs == null
    );

  if (existing?.status === 'TAKEN') {
    return existing.id;
  }

  if (existing) {
    await db.doseEvents.put({
      ...existing,
      status: 'TAKEN',
      actionSource: 'APP',
      recordSource: WEB_RECORD_SOURCE,
      snoozeUntilEpochMs: null,
      respondedEpochMs: now,
      updatedAtEpochMs: now,
      deletedAtEpochMs: null,
    });
    await syncEngine.queueDoseEventUpsert(existing.id);
    await addHiddenLog({
      doseEventId: existing.id,
      medicationId: existing.medicationId,
      medicationName: existing.medicationName,
      operationType: 'CHANGE',
      operationDetails: `status:${existing.status}->TAKEN`,
      actionSource: 'APP',
      takenAtEpochMs: now,
    });
    await maybeAutoSync();
    return existing.id;
  }

  const event: DoseEventRecord = {
    id: deterministicEventId,
    ownerProfileId: profileId,
    medicationId: medication.id,
    medicationName: medication.name,
    scheduledEpochMs,
    localDate,
    localTime: input.scheduledLocalTime,
    status: 'TAKEN',
    actionSource: 'APP',
    recordSource: WEB_RECORD_SOURCE,
    reminderAttemptCount: 0,
    snoozeUntilEpochMs: null,
    respondedEpochMs: now,
    createdAtEpochMs: now,
    updatedAtEpochMs: now,
    deletedAtEpochMs: null,
  };

  await db.doseEvents.put(event);
  await syncEngine.queueDoseEventUpsert(event.id);
  await addHiddenLog({
    doseEventId: event.id,
    medicationId: event.medicationId,
    medicationName: event.medicationName,
    operationType: 'ADD',
    operationDetails: 'fixed_time_event_added',
    actionSource: 'APP',
    takenAtEpochMs: now,
  });
  await maybeAutoSync();
  return event.id;
}

export async function updateDoseEventTime(doseEventId: string, newLocalTime: string): Promise<void> {
  const profileId = await getActiveProfileId();
  const current = await db.doseEvents.get(doseEventId);
  if (!current || current.deletedAtEpochMs != null || current.ownerProfileId !== profileId) {
    return;
  }

  const now = nowEpochMs();
  const scheduledEpochMs = new Date(`${current.localDate}T${newLocalTime}:00`).getTime();

  const updated: DoseEventRecord = {
    ...current,
    localTime: newLocalTime,
    scheduledEpochMs,
    updatedAtEpochMs: now,
    respondedEpochMs: current.respondedEpochMs == null ? null : scheduledEpochMs,
  };

  await db.doseEvents.put(updated);
  await syncEngine.queueDoseEventUpsert(doseEventId);

  await addHiddenLog({
    doseEventId,
    medicationId: updated.medicationId,
    medicationName: updated.medicationName,
    operationType: 'UPDATE',
    operationDetails: `localTime:${current.localTime}->${newLocalTime}`,
    actionSource: 'APP',
    takenAtEpochMs: now,
  });

  await maybeAutoSync();
}

export async function deleteDoseEvent(doseEventId: string): Promise<void> {
  const profileId = await getActiveProfileId();
  const current = await db.doseEvents.get(doseEventId);
  if (!current || current.deletedAtEpochMs != null || current.ownerProfileId !== profileId) {
    return;
  }

  const now = nowEpochMs();

  await db.doseEvents.put({
    ...current,
    updatedAtEpochMs: now,
    deletedAtEpochMs: now,
  });

  await queueOutboxTombstone('DOSE_EVENT', doseEventId, now, now);

  await addHiddenLog({
    doseEventId,
    medicationId: current.medicationId,
    medicationName: current.medicationName,
    operationType: 'DELETE',
    operationDetails: 'event_deleted',
    actionSource: 'APP',
    takenAtEpochMs: now,
  });

  await maybeAutoSync();
}

export async function setLocalOnlyMode(localOnlyMode: boolean): Promise<void> {
  const settings = await getOrCreateSettings();
  await db.settings.put({
    ...settings,
    localOnlyMode,
    cloudBackupEnabled: !localOnlyMode,
    syncState: localOnlyMode ? 'LOCAL_ONLY' : 'IDLE',
  });
}
