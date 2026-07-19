import { afterEach, describe, expect, it, vi } from 'vitest';

import { createDoseEvent, deleteDoseEvent, saveMedication } from '@/data/actions';
import { db } from '@/db/appDb';
import { doseEventEffectiveLocalDate } from '@/domain/doseEventTime';

afterEach(() => {
  vi.restoreAllMocks();
});

describe('Tombstone handling', () => {
  it('creates a taken event when status is TAKEN and stores the actual response timestamp', async () => {
    const fixedNow = new Date(2026, 2, 17, 1, 0, 45, 123).getTime();
    vi.spyOn(Date, 'now').mockReturnValue(fixedNow);

    const medicationId = await saveMedication({
      name: 'נורופן',
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
      weekdays: [1, 2, 3, 4, 5, 6, 7],
      times: ['08:00'],
      isActive: true,
    });

    const eventId = await createDoseEvent({
      medicationId,
      status: 'TAKEN',
    });

    const event = await db.doseEvents.get(eventId);
    expect(event?.status).toBe('TAKEN');
    expect(event?.respondedEpochMs).toBe(fixedNow);
    expect(event?.respondedEpochMs).not.toBe(event?.scheduledEpochMs);
  });

  it('shows cross-midnight fixed intake on the actual intake day in display helpers', async () => {
    const fixedNow = new Date(2026, 2, 29, 1, 0, 0, 0).getTime();
    vi.spyOn(Date, 'now').mockReturnValue(fixedNow);

    const medicationId = await saveMedication({
      name: 'אדויל',
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
      weekdays: [1, 2, 3, 4, 5, 6, 7],
      times: ['21:00'],
      isActive: true,
    });

    const eventId = await createDoseEvent({
      medicationId,
      localDate: '2026-03-28',
      localTime: '21:00',
      status: 'TAKEN',
    });

    const event = await db.doseEvents.get(eventId);
    expect(event?.localDate).toBe('2026-03-28');
    expect(event?.respondedEpochMs).toBe(fixedNow);
    if (!event) throw new Error('Expected event to exist');
    expect(doseEventEffectiveLocalDate(event)).toBe('2026-03-29');
  });

  it('keeps local event and queues TOMBSTONE outbox entry', async () => {
    const medicationId = await saveMedication({
      name: 'אקמול',
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
      weekdays: [1, 2, 3, 4, 5, 6, 7],
      times: ['08:00'],
      isActive: true,
    });

    const eventId = await createDoseEvent({
      medicationId,
      localDate: '2026-03-16',
      localTime: '09:30',
    });

    await deleteDoseEvent(eventId);

    const event = await db.doseEvents.get(eventId);
    const outbox = await db.outbox.get(`DOSE_EVENT:${eventId}`);

    expect(event?.deletedAtEpochMs).not.toBeNull();
    expect(outbox?.operation).toBe('TOMBSTONE');
    expect(outbox?.payloadJson).toBeNull();
    expect(outbox?.deletedAtEpochMs).not.toBeNull();
  });
});
