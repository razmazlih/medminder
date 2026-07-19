import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { toLocalDate, toLocalTime } from '@/core/time';
import {
  createDoseEvent,
  deleteDoseEvent,
  markFixedDoseTaken,
  saveMedication,
  setLocalOnlyMode,
  setMedicationActive,
  updateDoseEventTime,
  type MedicationInput,
} from '@/data/actions';
import { defaultSettings } from '@/data/localStore';
import { db } from '@/db/appDb';
import { syncEngine } from '@/sync/instance';

function buildMedicationInput(overrides: Partial<MedicationInput> = {}): MedicationInput {
  return {
    name: '  אקמול  ',
    doseText: ' 1 ',
    doseUnit: ' כדור ',
    foodRelation: 'NONE',
    scheduleMode: 'FIXED_TIMES',
    intervalHours: null,
    windowStartTime: null,
    windowEndTime: null,
    startDate: '2026-03-15',
    endDate: null,
    notes: 'הערה',
    weekdays: [7, 1, 3, 1, 2, 2],
    times: ['20:00', '08:00'],
    isActive: true,
    ...overrides,
  };
}

async function enableAutoSync(): Promise<void> {
  await db.settings.put({
    ...defaultSettings,
    cloudBackupEnabled: true,
    localOnlyMode: false,
    syncState: 'IDLE',
  });
}

describe('data/actions', () => {
  beforeEach(() => {
    vi.spyOn(syncEngine, 'syncNow').mockResolvedValue();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('saveMedication', () => {
    it('normalizes fields, deduplicates weekdays, preserves times order and queues outbox', async () => {
      const now = 1_700_000_000_000;
      vi.spyOn(Date, 'now').mockReturnValue(now);

      const medicationId = await saveMedication(buildMedicationInput());
      const medication = await db.medications.get(medicationId);
      const outboxItems = await db.outbox.toArray();

      expect(medication).toBeDefined();
      expect(medication?.name).toBe('אקמול');
      expect(medication?.doseText).toBe('1');
      expect(medication?.doseUnit).toBe('כדור');
      expect(medication?.createdAtEpochMs).toBe(now);
      expect(medication?.updatedAtEpochMs).toBe(now);
      expect(medication?.deletedAtEpochMs).toBeNull();
      expect(medication?.weekdays).toEqual([1, 2, 3, 7]);
      expect(medication?.times).toEqual([
        { localTime: '20:00', sortOrder: 0 },
        { localTime: '08:00', sortOrder: 1 },
      ]);
      expect(outboxItems).toHaveLength(1);
      expect(outboxItems[0]?.recordType).toBe('MEDICATION');
      expect(syncEngine.syncNow).not.toHaveBeenCalled();
    });

    it('keeps original createdAtEpochMs on edit and can trigger auto sync', async () => {
      await enableAutoSync();
      vi.spyOn(Date, 'now')
        .mockReturnValueOnce(1_700_000_000_000)
        .mockReturnValue(1_700_000_100_000);

      const id = await saveMedication(buildMedicationInput({ name: 'אקמול' }));
      const first = await db.medications.get(id);

      await saveMedication(buildMedicationInput({ id, name: 'אדויל', weekdays: [5, 5, 4] }));
      const updated = await db.medications.get(id);

      expect(first?.createdAtEpochMs).toBe(1_700_000_000_000);
      expect(updated?.createdAtEpochMs).toBe(1_700_000_000_000);
      expect(updated?.updatedAtEpochMs).toBe(1_700_000_100_000);
      expect(updated?.name).toBe('אדויל');
      expect(updated?.weekdays).toEqual([4, 5]);
      expect(syncEngine.syncNow).toHaveBeenCalledWith('local_change');
    });
  });

  describe('setMedicationActive', () => {
    it('does nothing for missing or foreign medications', async () => {
      await db.medications.put({
        id: 'med-foreign',
        ownerProfileId: 'foreign',
        name: 'תרופה זרה',
        doseText: '1',
        doseUnit: 'כדור',
        foodRelation: 'NONE',
        scheduleMode: 'FIXED_TIMES',
        intervalHours: null,
        windowStartTime: null,
        windowEndTime: null,
        startDate: '2026-03-15',
        endDate: null,
        notes: '',
        isActive: true,
        createdAtEpochMs: 10,
        updatedAtEpochMs: 10,
        deletedAtEpochMs: null,
        weekdays: [1],
        times: [{ localTime: '08:00', sortOrder: 0 }],
      });

      await setMedicationActive('missing-id', false);
      await setMedicationActive('med-foreign', false);

      const unchanged = await db.medications.get('med-foreign');
      expect(unchanged?.isActive).toBe(true);
      expect(await db.outbox.count()).toBe(0);
      expect(syncEngine.syncNow).not.toHaveBeenCalled();
    });

    it('toggles active state and queues sync records', async () => {
      await enableAutoSync();
      vi.spyOn(Date, 'now').mockReturnValue(2_000);
      const id = await saveMedication(buildMedicationInput({ name: 'לוסטרל' }));
      await db.outbox.clear();
      vi.mocked(syncEngine.syncNow).mockClear();

      await setMedicationActive(id, false);
      const disabled = await db.medications.get(id);
      expect(disabled?.isActive).toBe(false);
      expect(disabled?.updatedAtEpochMs).toBe(2_000);
      expect(await db.outbox.count()).toBe(1);

      vi.spyOn(Date, 'now').mockReturnValue(3_000);
      await setMedicationActive(id, true);
      const enabled = await db.medications.get(id);
      expect(enabled?.isActive).toBe(true);
      expect(enabled?.deletedAtEpochMs).toBeNull();
      expect(enabled?.updatedAtEpochMs).toBe(3_000);
      expect(await db.outbox.count()).toBe(1);
      expect(syncEngine.syncNow).toHaveBeenCalledTimes(2);
    });
  });

  describe('createDoseEvent', () => {
    it('throws when medication does not exist', async () => {
      await expect(createDoseEvent({ medicationId: 'missing' })).rejects.toThrow('Medication not found');
      expect(await db.doseEvents.count()).toBe(0);
      expect(await db.hiddenIntakeLogs.count()).toBe(0);
    });

    it('creates event with default local date/time and logs hidden ADD entry', async () => {
      const now = 1_710_000_000_000;
      vi.spyOn(Date, 'now').mockReturnValue(now);
      const medicationId = await saveMedication(buildMedicationInput({ name: 'נורופן' }));
      await db.outbox.clear();

      const eventId = await createDoseEvent({ medicationId });
      const event = await db.doseEvents.get(eventId);
      const logs = await db.hiddenIntakeLogs.toArray();
      const outbox = await db.outbox.toArray();

      expect(event).toBeDefined();
      expect(event?.status).toBe('PENDING');
      expect(event?.recordSource).toBe('web');
      expect(event?.localDate).toBe(toLocalDate(now));
      expect(event?.localTime).toBe(toLocalTime(now));
      expect(event?.respondedEpochMs).toBeNull();
      expect(logs).toHaveLength(1);
      expect(logs[0]?.doseEventId).toBe(eventId);
      expect(logs[0]?.operationType).toBe('ADD');
      expect(logs[0]?.operationDetails).toBe('event_added');
      expect(outbox.map((item) => item.recordType).sort()).toEqual(['DOSE_EVENT', 'HIDDEN_INTAKE_LOG']);
    });

    it('creates TAKEN event with explicit date/time and responded timestamp', async () => {
      vi.spyOn(Date, 'now').mockReturnValue(1_710_500_000_000);
      const medicationId = await saveMedication(buildMedicationInput({ name: 'אוגמנטין' }));
      await db.outbox.clear();

      const eventId = await createDoseEvent({
        medicationId,
        localDate: '2026-03-21',
        localTime: '19:45',
        status: 'TAKEN',
      });
      const event = await db.doseEvents.get(eventId);

      expect(event?.status).toBe('TAKEN');
      expect(event?.recordSource).toBe('web');
      expect(event?.localDate).toBe('2026-03-21');
      expect(event?.localTime).toBe('19:45');
      expect(event?.respondedEpochMs).toBe(1_710_500_000_000);
      expect(event?.scheduledEpochMs).toBe(new Date('2026-03-21T19:45:00').getTime());
    });
  });

  describe('markFixedDoseTaken', () => {
    it('updates existing fixed pending event to TAKEN without creating a new event', async () => {
      const now = 1_711_000_000_000;
      vi.spyOn(Date, 'now').mockReturnValue(now);
      const medicationId = await saveMedication(buildMedicationInput({ name: 'קומדין' }));
      const localDate = '2026-03-27';
      const scheduledLocalTime = '08:00';
      const scheduledEpochMs = new Date(`${localDate}T${scheduledLocalTime}:00`).getTime();
      const eventId = `${medicationId}_${scheduledEpochMs}`;

      await db.doseEvents.put({
        id: eventId,
        ownerProfileId: 'owner_primary',
        medicationId,
        medicationName: 'קומדין',
        scheduledEpochMs,
        localDate,
        localTime: scheduledLocalTime,
        status: 'PENDING',
        actionSource: null,
        reminderAttemptCount: 1,
        snoozeUntilEpochMs: now + 60_000,
        respondedEpochMs: null,
        createdAtEpochMs: now - 10_000,
        updatedAtEpochMs: now - 10_000,
        deletedAtEpochMs: null,
      });

      await db.hiddenIntakeLogs.clear();
      await db.outbox.clear();

      const markedId = await markFixedDoseTaken({
        medicationId,
        localDate,
        scheduledLocalTime,
      });

      const event = await db.doseEvents.get(eventId);
      const outbox = await db.outbox.toArray();
      const logs = await db.hiddenIntakeLogs.toArray();

      expect(markedId).toBe(eventId);
      expect(await db.doseEvents.count()).toBe(1);
      expect(event?.status).toBe('TAKEN');
      expect(event?.actionSource).toBe('APP');
      expect(event?.recordSource).toBe('web');
      expect(event?.snoozeUntilEpochMs).toBeNull();
      expect(event?.respondedEpochMs).toBe(now);
      expect(event?.updatedAtEpochMs).toBe(now);
      expect(event?.localTime).toBe(scheduledLocalTime);
      expect(outbox.map((item) => item.recordType).sort()).toEqual(['DOSE_EVENT', 'HIDDEN_INTAKE_LOG']);
      expect(outbox.find((item) => item.recordType === 'DOSE_EVENT')?.recordId).toBe(eventId);
      expect(logs).toHaveLength(1);
      expect(logs[0]?.operationType).toBe('CHANGE');
      expect(logs[0]?.operationDetails).toBe('status:PENDING->TAKEN');
    });

    it('creates deterministic TAKEN event when fixed slot does not exist locally', async () => {
      const now = 1_711_500_000_000;
      vi.spyOn(Date, 'now').mockReturnValue(now);
      const medicationId = await saveMedication(buildMedicationInput({ name: 'פבוקסיל' }));
      const localDate = '2026-03-28';
      const scheduledLocalTime = '21:15';
      const scheduledEpochMs = new Date(`${localDate}T${scheduledLocalTime}:00`).getTime();
      const expectedEventId = `${medicationId}_${scheduledEpochMs}`;

      await db.hiddenIntakeLogs.clear();
      await db.outbox.clear();

      const eventId = await markFixedDoseTaken({
        medicationId,
        localDate,
        scheduledLocalTime,
      });

      const event = await db.doseEvents.get(eventId);
      const logs = await db.hiddenIntakeLogs.toArray();
      const outbox = await db.outbox.toArray();

      expect(eventId).toBe(expectedEventId);
      expect(event).toBeDefined();
      expect(event?.status).toBe('TAKEN');
      expect(event?.recordSource).toBe('web');
      expect(event?.localDate).toBe(localDate);
      expect(event?.localTime).toBe(scheduledLocalTime);
      expect(event?.scheduledEpochMs).toBe(scheduledEpochMs);
      expect(event?.respondedEpochMs).toBe(now);
      expect(logs).toHaveLength(1);
      expect(logs[0]?.operationType).toBe('ADD');
      expect(logs[0]?.operationDetails).toBe('fixed_time_event_added');
      expect(outbox.map((item) => item.recordType).sort()).toEqual(['DOSE_EVENT', 'HIDDEN_INTAKE_LOG']);
    });
  });

  describe('updateDoseEventTime', () => {
    it('does nothing for missing, deleted, or foreign events', async () => {
      await db.doseEvents.put({
        id: 'event-deleted',
        ownerProfileId: 'owner_primary',
        medicationId: 'med-1',
        medicationName: 'אקמול',
        scheduledEpochMs: 1_000,
        localDate: '2026-03-17',
        localTime: '08:00',
        status: 'TAKEN',
        actionSource: 'APP',
        reminderAttemptCount: 0,
        snoozeUntilEpochMs: null,
        respondedEpochMs: 1_000,
        createdAtEpochMs: 1_000,
        updatedAtEpochMs: 1_000,
        deletedAtEpochMs: 1_500,
      });
      await db.doseEvents.put({
        id: 'event-foreign',
        ownerProfileId: 'foreign',
        medicationId: 'med-1',
        medicationName: 'אקמול',
        scheduledEpochMs: 1_000,
        localDate: '2026-03-17',
        localTime: '08:00',
        status: 'TAKEN',
        actionSource: 'APP',
        reminderAttemptCount: 0,
        snoozeUntilEpochMs: null,
        respondedEpochMs: 1_000,
        createdAtEpochMs: 1_000,
        updatedAtEpochMs: 1_000,
        deletedAtEpochMs: null,
      });

      await updateDoseEventTime('missing', '09:00');
      await updateDoseEventTime('event-deleted', '09:00');
      await updateDoseEventTime('event-foreign', '09:00');

      expect((await db.doseEvents.get('event-deleted'))?.localTime).toBe('08:00');
      expect((await db.doseEvents.get('event-foreign'))?.localTime).toBe('08:00');
      expect(await db.outbox.count()).toBe(0);
      expect(await db.hiddenIntakeLogs.count()).toBe(0);
    });

    it('updates time, recalculates schedule and updates responded time when already responded', async () => {
      vi.spyOn(Date, 'now').mockReturnValue(4_444);
      const medicationId = await saveMedication(buildMedicationInput({ name: 'סימבקור' }));
      const eventId = await createDoseEvent({
        medicationId,
        localDate: '2026-03-19',
        localTime: '07:30',
        status: 'TAKEN',
      });
      await db.hiddenIntakeLogs.clear();
      await db.outbox.clear();

      await updateDoseEventTime(eventId, '11:20');

      const updated = await db.doseEvents.get(eventId);
      const logs = await db.hiddenIntakeLogs.toArray();
      expect(updated?.localTime).toBe('11:20');
      expect(updated?.scheduledEpochMs).toBe(new Date('2026-03-19T11:20:00').getTime());
      expect(updated?.respondedEpochMs).toBe(new Date('2026-03-19T11:20:00').getTime());
      expect(updated?.updatedAtEpochMs).toBe(4_444);
      expect(logs).toHaveLength(1);
      expect(logs[0]?.operationType).toBe('UPDATE');
      expect(logs[0]?.operationDetails).toBe('localTime:07:30->11:20');
      expect((await db.outbox.toArray()).map((item) => item.recordType).sort()).toEqual(['DOSE_EVENT', 'HIDDEN_INTAKE_LOG']);
    });

    it('keeps respondedEpochMs null when event was not responded', async () => {
      vi.spyOn(Date, 'now').mockReturnValue(9_999);
      const medicationId = await saveMedication(buildMedicationInput({ name: 'לוסק' }));
      const eventId = await createDoseEvent({
        medicationId,
        localDate: '2026-03-22',
        localTime: '06:00',
        status: 'PENDING',
      });
      await db.hiddenIntakeLogs.clear();
      await db.outbox.clear();

      await updateDoseEventTime(eventId, '10:00');
      const updated = await db.doseEvents.get(eventId);

      expect(updated?.respondedEpochMs).toBeNull();
      expect(updated?.scheduledEpochMs).toBe(new Date('2026-03-22T10:00:00').getTime());
    });
  });

  describe('deleteDoseEvent', () => {
    it('does nothing for missing, deleted, or foreign events', async () => {
      await db.doseEvents.put({
        id: 'event-deleted',
        ownerProfileId: 'owner_primary',
        medicationId: 'med-1',
        medicationName: 'אקמול',
        scheduledEpochMs: 1_000,
        localDate: '2026-03-17',
        localTime: '08:00',
        status: 'TAKEN',
        actionSource: 'APP',
        reminderAttemptCount: 0,
        snoozeUntilEpochMs: null,
        respondedEpochMs: 1_000,
        createdAtEpochMs: 1_000,
        updatedAtEpochMs: 1_000,
        deletedAtEpochMs: 1_500,
      });
      await db.doseEvents.put({
        id: 'event-foreign',
        ownerProfileId: 'foreign',
        medicationId: 'med-1',
        medicationName: 'אקמול',
        scheduledEpochMs: 1_000,
        localDate: '2026-03-17',
        localTime: '08:00',
        status: 'TAKEN',
        actionSource: 'APP',
        reminderAttemptCount: 0,
        snoozeUntilEpochMs: null,
        respondedEpochMs: 1_000,
        createdAtEpochMs: 1_000,
        updatedAtEpochMs: 1_000,
        deletedAtEpochMs: null,
      });

      await deleteDoseEvent('missing');
      await deleteDoseEvent('event-deleted');
      await deleteDoseEvent('event-foreign');

      expect(await db.outbox.count()).toBe(0);
      expect(await db.hiddenIntakeLogs.count()).toBe(0);
    });

    it('soft-deletes event, writes tombstone, and logs delete action', async () => {
      vi.spyOn(Date, 'now').mockReturnValue(8_888);
      const medicationId = await saveMedication(buildMedicationInput({ name: 'פרדניזון' }));
      const eventId = await createDoseEvent({
        medicationId,
        localDate: '2026-03-25',
        localTime: '12:00',
        status: 'TAKEN',
      });
      await db.hiddenIntakeLogs.clear();
      await db.outbox.clear();

      await deleteDoseEvent(eventId);

      const event = await db.doseEvents.get(eventId);
      const outbox = await db.outbox.toArray();
      const logs = await db.hiddenIntakeLogs.toArray();
      expect(event?.deletedAtEpochMs).toBe(8_888);
      expect(event?.updatedAtEpochMs).toBe(8_888);
      expect(outbox).toHaveLength(2);
      expect(outbox.find((item) => item.recordType === 'DOSE_EVENT')?.operation).toBe('TOMBSTONE');
      expect(outbox.find((item) => item.recordType === 'DOSE_EVENT')?.deletedAtEpochMs).toBe(8_888);
      expect(logs).toHaveLength(1);
      expect(logs[0]?.operationType).toBe('DELETE');
      expect(logs[0]?.operationDetails).toBe('event_deleted');
    });
  });

  describe('setLocalOnlyMode', () => {
    it('updates local-only settings and sync state transitions', async () => {
      await setLocalOnlyMode(true);
      const localOnly = await db.settings.get('singleton');
      expect(localOnly?.localOnlyMode).toBe(true);
      expect(localOnly?.cloudBackupEnabled).toBe(false);
      expect(localOnly?.syncState).toBe('LOCAL_ONLY');

      await setLocalOnlyMode(false);
      const cloudMode = await db.settings.get('singleton');
      expect(cloudMode?.localOnlyMode).toBe(false);
      expect(cloudMode?.cloudBackupEnabled).toBe(true);
      expect(cloudMode?.syncState).toBe('IDLE');
    });
  });
});
