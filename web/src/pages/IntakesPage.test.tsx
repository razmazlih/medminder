import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { IntakesPage } from '@/pages/IntakesPage';
import type { DoseEventRecord, MedicationRecord } from '@/domain/models';

const medicationsState = vi.hoisted((): MedicationRecord[] => []);
const eventsState = vi.hoisted((): DoseEventRecord[] => []);
const createDoseEventMock = vi.hoisted(() => vi.fn());
const markFixedDoseTakenMock = vi.hoisted(() => vi.fn());
const setMessageMock = vi.hoisted(() => vi.fn());

vi.mock('@/hooks/useLocalData', () => ({
  useMedications: () => medicationsState,
  useDoseEvents: () => eventsState,
}));

vi.mock('@/data/actions', () => ({
  createDoseEvent: createDoseEventMock,
  markFixedDoseTaken: markFixedDoseTakenMock,
}));

vi.mock('@/state/useAppStore', () => ({
  useAppStore: () => ({ setMessage: setMessageMock }),
}));

function makeMedication(overrides: Partial<MedicationRecord> = {}): MedicationRecord {
  return {
    id: 'med-1',
    ownerProfileId: 'owner_primary',
    name: 'קומדין',
    doseText: '1',
    doseUnit: 'כדור',
    foodRelation: 'NONE',
    scheduleMode: 'EVERY_X_DAYS',
    intervalHours: 2,
    windowStartTime: null,
    windowEndTime: null,
    startDate: '2026-04-09',
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

function makeEvent(overrides: Partial<DoseEventRecord> = {}): DoseEventRecord {
  return {
    id: 'event-1',
    ownerProfileId: 'owner_primary',
    medicationId: 'med-1',
    medicationName: 'קומדין',
    scheduledEpochMs: new Date('2026-04-11T12:30:00').getTime(),
    localDate: '2026-04-11',
    localTime: '12:30',
    status: 'TAKEN',
    actionSource: 'APP',
    reminderAttemptCount: 0,
    snoozeUntilEpochMs: null,
    respondedEpochMs: new Date('2026-04-11T12:30:00').getTime(),
    createdAtEpochMs: 1,
    updatedAtEpochMs: 1,
    deletedAtEpochMs: null,
    ...overrides,
  };
}

describe('IntakesPage', () => {
  beforeEach(() => {
    medicationsState.splice(0, medicationsState.length);
    eventsState.splice(0, eventsState.length);
    createDoseEventMock.mockReset().mockResolvedValue('event-1');
    markFixedDoseTakenMock.mockReset().mockResolvedValue('fixed-1');
    setMessageMock.mockReset();
    vi.spyOn(Date, 'now').mockReturnValue(new Date('2026-04-11T12:00:00').getTime());
  });

  it('shows every-x-days medications only on due day and records them as all-day actions', async () => {
    const user = userEvent.setup();
    medicationsState.push(
      makeMedication({ id: 'med-due', name: 'קומדין', startDate: '2026-04-09', intervalHours: 2 }),
      makeMedication({ id: 'med-off', name: 'אוגמנטין', startDate: '2026-04-10', intervalHours: 3 })
    );

    render(<IntakesPage />);

    expect(screen.getByText('קומדין')).toBeInTheDocument();
    expect(screen.queryByText('אוגמנטין')).not.toBeInTheDocument();
    expect(screen.getByText(/שעת יעד: 08:00/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /08:00/ })).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /נטילה היום/ }));
    expect(createDoseEventMock).toHaveBeenCalledWith({ medicationId: 'med-due', status: 'TAKEN' });
  });

  it('locks every-x-days medication after it was already taken today', () => {
    medicationsState.push(makeMedication({ id: 'med-due', name: 'קומדין' }));
    eventsState.push(makeEvent({ medicationId: 'med-due', medicationName: 'קומדין' }));

    render(<IntakesPage />);

    expect(screen.getByRole('button', { name: /נרשמה להיום/ })).toBeDisabled();
    expect(screen.getByText('✓ נלקחה היום')).toBeInTheDocument();
  });
});
