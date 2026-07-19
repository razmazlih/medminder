import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { MedicationsPage } from '@/pages/MedicationsPage';
import type { DoseEventRecord, MedicationRecord } from '@/domain/models';

const medicationsState = vi.hoisted((): MedicationRecord[] => []);
const eventsState = vi.hoisted((): DoseEventRecord[] => []);
const saveMedicationMock = vi.hoisted(() => vi.fn());
const createDoseEventMock = vi.hoisted(() => vi.fn());
const setMedicationActiveMock = vi.hoisted(() => vi.fn());
const setMessageMock = vi.hoisted(() => vi.fn());

vi.mock('@/hooks/useLocalData', () => ({
  useMedications: () => medicationsState,
  useDoseEvents: () => eventsState,
}));

vi.mock('@/data/actions', () => ({
  saveMedication: saveMedicationMock,
  createDoseEvent: createDoseEventMock,
  setMedicationActive: setMedicationActiveMock,
}));

vi.mock('@/state/useAppStore', () => ({
  useAppStore: () => ({ setMessage: setMessageMock }),
}));

describe('MedicationsPage', () => {
  beforeEach(() => {
    medicationsState.splice(0, medicationsState.length);
    eventsState.splice(0, eventsState.length);
    saveMedicationMock.mockReset().mockResolvedValue('med-1');
    createDoseEventMock.mockReset().mockResolvedValue('event-1');
    setMedicationActiveMock.mockReset().mockResolvedValue(undefined);
    setMessageMock.mockReset();
  });

  it('shows a clearer dedicated flow for every-x-days medications', async () => {
    const user = userEvent.setup();
    render(<MedicationsPage />);

    await user.click(screen.getByRole('button', { name: /חדשה$/ }));
    await user.selectOptions(screen.getByLabelText('מצב תזמון'), 'EVERY_X_DAYS');

    expect(screen.getByText(/התרופה תופיע בדף הבית רק בימי הנטילה/)).toBeInTheDocument();
    expect(screen.getByLabelText('כל כמה ימים לקחת?')).toBeInTheDocument();
    expect(screen.getByLabelText('מתי לקחת בפעם הראשונה?')).toBeInTheDocument();
    expect(screen.getByLabelText('שעת נטילה ביום היעד')).toBeInTheDocument();
    expect(screen.queryByLabelText('שעות נטילה (מופרדות בפסיק)')).not.toBeInTheDocument();
    expect(screen.queryByText('ימי השבוע')).not.toBeInTheDocument();
  });
});
