import { describe, expect, it } from 'vitest';

import { doseEventEffectiveEpochMs, doseEventEffectiveLocalDate, doseEventEffectiveLocalTime } from '@/domain/doseEventTime';
import type { DoseEventRecord } from '@/domain/models';

function makeEvent(overrides: Partial<DoseEventRecord> = {}): DoseEventRecord {
  const scheduledEpochMs = new Date(2026, 2, 28, 21, 0, 0, 0).getTime();
  return {
    id: 'dose-1',
    ownerProfileId: 'owner_primary',
    medicationId: 'med-1',
    medicationName: 'אקמול',
    scheduledEpochMs,
    localDate: '2026-03-28',
    localTime: '21:00',
    status: 'TAKEN',
    actionSource: 'APP',
    reminderAttemptCount: 0,
    snoozeUntilEpochMs: null,
    respondedEpochMs: null,
    createdAtEpochMs: scheduledEpochMs,
    updatedAtEpochMs: scheduledEpochMs,
    deletedAtEpochMs: null,
    ...overrides,
  };
}

describe('doseEventTime helpers', () => {
  it('shows responded date and time when intake happened after midnight', () => {
    const respondedEpochMs = new Date(2026, 2, 29, 1, 0, 0, 0).getTime();
    const event = makeEvent({ respondedEpochMs });

    expect(doseEventEffectiveEpochMs(event)).toBe(respondedEpochMs);
    expect(doseEventEffectiveLocalDate(event)).toBe('2026-03-29');
    expect(doseEventEffectiveLocalTime(event)).toBe('01:00');
  });

  it('falls back to scheduled time when response time is missing', () => {
    const event = makeEvent({ respondedEpochMs: null });

    expect(doseEventEffectiveEpochMs(event)).toBe(event.scheduledEpochMs);
    expect(doseEventEffectiveLocalDate(event)).toBe('2026-03-28');
    expect(doseEventEffectiveLocalTime(event)).toBe('21:00');
  });
});
