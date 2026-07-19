import { describe, expect, it } from 'vitest';

import type { MedicationRecord } from '@/domain/models';
import {
  isEveryXDaysMedicationDueOnDate,
  isMedicationWithinDateRange,
  shouldShowMedicationOnIntakesDate,
} from '@/domain/medicationSchedule';

function makeMedication(overrides: Partial<MedicationRecord> = {}): MedicationRecord {
  return {
    id: 'med-1',
    ownerProfileId: 'owner_primary',
    name: 'קומדין',
    doseText: '1',
    doseUnit: 'כדור',
    foodRelation: 'NONE',
    scheduleMode: 'EVERY_X_DAYS',
    intervalHours: 3,
    windowStartTime: null,
    windowEndTime: null,
    startDate: '2026-04-05',
    endDate: null,
    notes: '',
    isActive: true,
    createdAtEpochMs: 1,
    updatedAtEpochMs: 1,
    deletedAtEpochMs: null,
    weekdays: [1, 2, 3, 4, 5, 6, 7],
    times: [{ localTime: '08:00', sortOrder: 0 }],
    ...overrides,
  };
}

describe('medicationSchedule', () => {
  it('marks every-x-days medication as due only on matching interval days', () => {
    const medication = makeMedication({ intervalHours: 3, startDate: '2026-04-05' });

    expect(isEveryXDaysMedicationDueOnDate(medication, '2026-04-05')).toBe(true);
    expect(isEveryXDaysMedicationDueOnDate(medication, '2026-04-08')).toBe(true);
    expect(isEveryXDaysMedicationDueOnDate(medication, '2026-04-11')).toBe(true);
    expect(isEveryXDaysMedicationDueOnDate(medication, '2026-04-10')).toBe(false);
  });

  it('respects start and end dates for visibility', () => {
    const medication = makeMedication({ endDate: '2026-04-11' });

    expect(isMedicationWithinDateRange(medication, '2026-04-04')).toBe(false);
    expect(isMedicationWithinDateRange(medication, '2026-04-05')).toBe(true);
    expect(isMedicationWithinDateRange(medication, '2026-04-11')).toBe(true);
    expect(isMedicationWithinDateRange(medication, '2026-04-12')).toBe(false);
  });

  it('shows non-every-x-days medications on intakes date while hiding off-day every-x-days medications', () => {
    const everyXDaysMedication = makeMedication({ intervalHours: 2, startDate: '2026-04-09' });
    const fixedMedication = makeMedication({
      id: 'med-2',
      scheduleMode: 'FIXED_TIMES',
      intervalHours: null,
      startDate: '2026-04-01',
    });

    expect(shouldShowMedicationOnIntakesDate(everyXDaysMedication, '2026-04-11')).toBe(true);
    expect(shouldShowMedicationOnIntakesDate(everyXDaysMedication, '2026-04-12')).toBe(false);
    expect(shouldShowMedicationOnIntakesDate(fixedMedication, '2026-04-12')).toBe(true);
  });
});
