import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { HiddenLogPage } from '@/pages/HiddenLogPage';
import type { HiddenIntakeLogRecord } from '@/domain/models';

const logsState = vi.hoisted((): HiddenIntakeLogRecord[] => []);

vi.mock('@/hooks/useLocalData', () => ({
  useHiddenIntakeLogs: () => logsState,
}));

function makeLog(overrides: Partial<HiddenIntakeLogRecord>): HiddenIntakeLogRecord {
  return {
    id: 'log-1',
    ownerProfileId: 'owner_primary',
    doseEventId: 'event-1',
    medicationId: 'med-1',
    medicationName: 'אקמול',
    operationType: 'ADD',
    operationDetails: 'event_added',
    actionSource: 'APP',
    takenAtEpochMs: 1_700_000_000_000,
    createdAtEpochMs: 1_700_000_000_000,
    updatedAtEpochMs: 1_700_000_000_000,
    deletedAtEpochMs: null,
    ...overrides,
  };
}

describe('HiddenLogPage', () => {
  beforeEach(() => {
    logsState.splice(0, logsState.length);
    logsState.push(
      makeLog({
        id: 'log-1',
        medicationName: 'אקמול',
        operationType: 'ADD',
        actionSource: 'APP',
        operationDetails: 'event_added',
      }),
      makeLog({
        id: 'log-2',
        medicationName: 'נורופן',
        operationType: 'UPDATE',
        actionSource: 'NOTIFICATION',
        operationDetails: 'localTime:08:00->09:00',
      }),
      makeLog({
        id: 'log-3',
        medicationName: 'אדויל',
        operationType: 'DELETE',
        actionSource: 'SYSTEM',
        operationDetails: 'event_deleted',
      }),
      makeLog({
        id: 'log-4',
        medicationName: 'קומדין',
        operationType: 'CHANGE',
        actionSource: 'APP',
        operationDetails: 'status:PENDING->TAKEN',
      })
    );
  });

  it('filters by search query on medication name and details', async () => {
    const user = userEvent.setup();
    render(<HiddenLogPage />);

    expect(screen.getByText('אקמול')).toBeInTheDocument();
    expect(screen.getByText('נורופן')).toBeInTheDocument();
    expect(screen.getByText('אדויל')).toBeInTheDocument();
    expect(screen.getByText('קומדין')).toBeInTheDocument();

    await user.type(screen.getByLabelText('חיפוש'), '09:00');
    expect(screen.queryByText('אקמול')).not.toBeInTheDocument();
    expect(screen.getByText('נורופן')).toBeInTheDocument();
    expect(screen.queryByText('אדויל')).not.toBeInTheDocument();
    expect(screen.queryByText('קומדין')).not.toBeInTheDocument();
  });

  it('filters by operation type', async () => {
    const user = userEvent.setup();
    render(<HiddenLogPage />);

    await user.selectOptions(screen.getByLabelText('סוג פעולה'), 'DELETE');
    expect(screen.queryByText('אקמול')).not.toBeInTheDocument();
    expect(screen.queryByText('נורופן')).not.toBeInTheDocument();
    expect(screen.getByText('אדויל')).toBeInTheDocument();
    expect(screen.queryByText('קומדין')).not.toBeInTheDocument();
  });

  it('filters by action source', async () => {
    const user = userEvent.setup();
    render(<HiddenLogPage />);

    await user.selectOptions(screen.getByLabelText('מקור'), 'NOTIFICATION');
    expect(screen.queryByText('אקמול')).not.toBeInTheDocument();
    expect(screen.getByText('נורופן')).toBeInTheDocument();
    expect(screen.queryByText('אדויל')).not.toBeInTheDocument();
    expect(screen.queryByText('קומדין')).not.toBeInTheDocument();
  });

  it('translates known operation details to Hebrew', () => {
    render(<HiddenLogPage />);

    expect(screen.getByText('נוסף אירוע נטילה')).toBeInTheDocument();
    expect(screen.getByText('שעת הנטילה עודכנה מ־08:00 ל־09:00')).toBeInTheDocument();
    expect(screen.getByText('אירוע הנטילה נמחק')).toBeInTheDocument();
    expect(screen.getByText('סטטוס עודכן מ־ממתין ל־נלקח')).toBeInTheDocument();

    expect(screen.queryByText('event_added')).not.toBeInTheDocument();
    expect(screen.queryByText('status:PENDING->TAKEN')).not.toBeInTheDocument();
  });

  it('shows Hebrew fallback for unknown details and translates cloud deletion details', () => {
    logsState.splice(0, logsState.length);
    logsState.push(
      makeLog({
        id: 'log-cloud',
        medicationName: 'אקמול',
        operationType: 'DELETE',
        actionSource: 'SYSTEM',
        operationDetails: 'cloud_deleted:DOSE_EVENT:event-99:1700000000000:extra',
      }),
      makeLog({
        id: 'log-unknown',
        medicationName: 'נורופן',
        operationType: 'CHANGE',
        actionSource: 'APP',
        operationDetails: 'raw_english_detail',
      })
    );

    render(<HiddenLogPage />);

    expect(screen.getByText(/נמחק בענן: אירוע נטילה/)).toBeInTheDocument();
    expect(screen.getByText('פרטי הפעולה אינם זמינים להצגה')).toBeInTheDocument();
    expect(screen.queryByText('raw_english_detail')).not.toBeInTheDocument();
  });

  it('shows empty state when filters remove all records', async () => {
    const user = userEvent.setup();
    render(<HiddenLogPage />);

    await user.type(screen.getByLabelText('חיפוש'), 'לא קיים');
    expect(screen.getByText('אין רשומות להצגה')).toBeInTheDocument();
    expect(screen.getByText('נסו לשנות את הסינון או החיפוש')).toBeInTheDocument();
  });
});
