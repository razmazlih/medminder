import { describe, expect, it } from 'vitest';

import { cloudRecordSchema } from '@/domain/schemas';
import type { DoseEventRecord, HiddenIntakeLogRecord, MedicationRecord } from '@/domain/models';
import {
  doseEventFromJson,
  doseEventToJson,
  hiddenLogFromJson,
  hiddenLogToJson,
  medicationFromJson,
  medicationToJson,
} from '@/sync/payloadCodec';

describe('payload codec cloud compatibility', () => {
  it('serializes and deserializes medication payload in canonical format', () => {
    const medication: MedicationRecord = {
      id: 'med-1',
      ownerProfileId: 'owner_primary',
      name: 'Paracetamol',
      doseText: '1',
      doseUnit: 'tablet',
      foodRelation: 'NONE',
      scheduleMode: 'FIXED_TIMES',
      intervalHours: null,
      windowStartTime: null,
      windowEndTime: null,
      startDate: '2026-03-16',
      endDate: null,
      notes: '',
      isActive: true,
      createdAtEpochMs: 1000,
      updatedAtEpochMs: 2000,
      deletedAtEpochMs: null,
      weekdays: [7, 1, 4],
      times: [
        { localTime: '10:00', sortOrder: 1 },
        { localTime: '08:00', sortOrder: 0 },
      ],
    };

    const roundtrip = medicationFromJson(medicationToJson(medication));
    expect(roundtrip.weekdays).toEqual([1, 4, 7]);
    expect(roundtrip.times).toEqual([
      { localTime: '08:00', sortOrder: 0 },
      { localTime: '10:00', sortOrder: 1 },
    ]);
    expect(roundtrip.updatedAtEpochMs).toBe(2000);
  });

  it('accepts snake_case dose event payloads from cloud', () => {
    const payloadJson = JSON.stringify({
      id: 'event-1',
      owner_profile_id: 'owner_primary',
      medication_id: 'med-1',
      medication_name: 'Paracetamol',
      scheduled_epoch_ms: 5000,
      local_date: '2026-03-16',
      local_time: '08:00',
      status: 'TAKEN',
      action_source: 'APP',
      record_source: 'web',
      reminder_attempt_count: 2,
      snooze_until_epoch_ms: null,
      responded_epoch_ms: null,
      created_at_epoch_ms: 4000,
      updated_at_epoch_ms: 4500,
    });

    const parsed = doseEventFromJson(payloadJson);
    expect(parsed.ownerProfileId).toBe('owner_primary');
    expect(parsed.reminderAttemptCount).toBe(2);
    expect(parsed.respondedEpochMs).toBe(5000);
    expect(parsed.updatedAtEpochMs).toBe(4500);
    expect(parsed.recordSource).toBe('web');
  });

  it('accepts hidden log payloads with optional updatedAtEpochMs', () => {
    const payloadJson = JSON.stringify({
      id: 'log-1',
      owner_profile_id: 'owner_primary',
      dose_event_id: null,
      medication_id: 'med-1',
      medication_name: 'Paracetamol',
      operation_type: 'CHANGE',
      operation_details: 'cloud_deleted:MEDICATION:med-1:10000',
      action_source: 'SYSTEM',
      taken_at_epoch_ms: 10000,
      created_at_epoch_ms: 9000,
      updated_at_epoch_ms: 9100,
    });

    const parsed = hiddenLogFromJson(payloadJson);
    expect(parsed.createdAtEpochMs).toBe(9000);
    expect(parsed.updatedAtEpochMs).toBe(9100);
    expect(parsed.actionSource).toBe('SYSTEM');
  });

  it('parses cloud envelope when optional tombstone fields are omitted', () => {
    const parsed = cloudRecordSchema.parse({
      recordType: 'DOSE_EVENT',
      recordId: 'event-1',
      updatedAt: 1234,
    });
    expect(parsed.deletedAt).toBeNull();
    expect(parsed.iv).toBeNull();
    expect(parsed.ciphertext).toBeNull();
  });

  it('keeps canonical web payload shape for dose and hidden logs', () => {
    const doseEvent: DoseEventRecord = {
      id: 'dose-1',
      ownerProfileId: 'owner_primary',
      medicationId: 'med-1',
      medicationName: 'Paracetamol',
      scheduledEpochMs: 1234,
      localDate: '2026-03-16',
      localTime: '08:00',
      status: 'PENDING',
      actionSource: 'APP',
      recordSource: 'web',
      reminderAttemptCount: 0,
      snoozeUntilEpochMs: null,
      respondedEpochMs: null,
      createdAtEpochMs: 1234,
      updatedAtEpochMs: 1234,
      deletedAtEpochMs: null,
    };
    const hiddenLog: HiddenIntakeLogRecord = {
      id: 'log-2',
      ownerProfileId: 'owner_primary',
      doseEventId: 'dose-1',
      medicationId: 'med-1',
      medicationName: 'Paracetamol',
      operationType: 'ADD',
      operationDetails: 'event_added',
      actionSource: 'APP',
      takenAtEpochMs: 1234,
      createdAtEpochMs: 1234,
      updatedAtEpochMs: 1234,
      deletedAtEpochMs: null,
    };

    expect(() => JSON.parse(doseEventToJson(doseEvent))).not.toThrow();
    expect(() => JSON.parse(hiddenLogToJson(hiddenLog))).not.toThrow();
  });
});
