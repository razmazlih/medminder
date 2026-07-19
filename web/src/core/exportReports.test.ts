import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { CloudDeletionNotice, DoseEventRecord } from '@/domain/models';

type MockPdf = {
  setFontSize: ReturnType<typeof vi.fn>;
  text: ReturnType<typeof vi.fn>;
  addPage: ReturnType<typeof vi.fn>;
  output: ReturnType<typeof vi.fn>;
  setLanguage: ReturnType<typeof vi.fn>;
  setR2L: ReturnType<typeof vi.fn>;
  setFont: ReturnType<typeof vi.fn>;
  splitTextToSize: ReturnType<typeof vi.fn>;
  setDrawColor: ReturnType<typeof vi.fn>;
  setFillColor: ReturnType<typeof vi.fn>;
  line: ReturnType<typeof vi.fn>;
  roundedRect: ReturnType<typeof vi.fn>;
  rect: ReturnType<typeof vi.fn>;
  setTextColor: ReturnType<typeof vi.fn>;
  getTextWidth: ReturnType<typeof vi.fn>;
  getNumberOfPages: ReturnType<typeof vi.fn>;
  setPage: ReturnType<typeof vi.fn>;
  addFileToVFS: ReturnType<typeof vi.fn>;
  addFont: ReturnType<typeof vi.fn>;
  internal: {
    pageSize: {
      getHeight: ReturnType<typeof vi.fn>;
    };
  };
};

const mockState = vi.hoisted(() => {
  const instances: MockPdf[] = [];
  const jsPDF = vi.fn(function jsPDFMock(this: unknown, _options: unknown) {
    const instance: MockPdf = {
      setFontSize: vi.fn(),
      text: vi.fn(),
      addPage: vi.fn(),
      output: vi.fn(() => new Blob(['pdf-bytes'], { type: 'application/pdf' })),
      setLanguage: vi.fn(),
      setR2L: vi.fn(),
      setFont: vi.fn(),
      splitTextToSize: vi.fn((line: string) => [line]),
      setDrawColor: vi.fn(),
      setFillColor: vi.fn(),
      line: vi.fn(),
      roundedRect: vi.fn(),
      rect: vi.fn(),
      setTextColor: vi.fn(),
      getTextWidth: vi.fn((text: string) => text.length * 5),
      getNumberOfPages: vi.fn(() => 1),
      setPage: vi.fn(),
      addFileToVFS: vi.fn(),
      addFont: vi.fn(),
      internal: {
        pageSize: {
          getHeight: vi.fn(() => 842),
        },
      },
    };
    instances.push(instance);
    return instance as never;
  });

  return { instances, jsPDF };
});

vi.mock('jspdf', () => ({
  jsPDF: mockState.jsPDF,
}));

import { exportHistoryCsv, exportHistoryPdf } from '@/core/exportReports';

const originalCreateElement = document.createElement.bind(document);

function makeEvent(overrides: Partial<DoseEventRecord> = {}): DoseEventRecord {
  return {
    id: 'event-1',
    ownerProfileId: 'owner_primary',
    medicationId: 'med-1',
    medicationName: 'אקמול',
    scheduledEpochMs: new Date(2026, 2, 17, 8, 15, 0, 0).getTime(),
    localDate: '2026-03-17',
    localTime: '08:15',
    status: 'TAKEN',
    actionSource: 'APP',
    recordSource: 'web',
    reminderAttemptCount: 0,
    snoozeUntilEpochMs: null,
    respondedEpochMs: new Date(2026, 2, 17, 8, 15, 0, 0).getTime(),
    createdAtEpochMs: new Date(2026, 2, 17, 8, 15, 0, 0).getTime(),
    updatedAtEpochMs: new Date(2026, 2, 17, 8, 15, 0, 0).getTime(),
    deletedAtEpochMs: null,
    ...overrides,
  };
}

function makeNotice(overrides: Partial<CloudDeletionNotice> = {}): CloudDeletionNotice {
  return {
    id: 'notice-1',
    recordType: 'MEDICATION',
    recordTypeLabel: 'תרופה',
    recordId: 'med-1',
    medicationName: 'אקמול',
    deletedAtEpochMs: 1_700_000_002_000,
    deletedAtLabel: '17.03.2026, 08:30',
    ...overrides,
  };
}

beforeEach(() => {
  mockState.instances.length = 0;
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('exportHistoryCsv', () => {
  it('downloads csv with metadata and range-based filename', async () => {
    const anchor = document.createElement('a');
    const clickSpy = vi.spyOn(anchor, 'click').mockImplementation(() => undefined);
    const createObjectURLSpy = vi.fn((_: Blob | MediaSource) => 'blob:csv-test');
    const revokeObjectURLSpy = vi.fn();

    vi.spyOn(document, 'createElement').mockImplementation((tagName: string) => {
      if (tagName === 'a') return anchor;
      return originalCreateElement(tagName);
    });
    Object.defineProperty(URL, 'createObjectURL', { configurable: true, value: createObjectURLSpy });
    Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: revokeObjectURLSpy });

    exportHistoryCsv([makeEvent()], [makeNotice()], {
      fromDate: '2026-03-01',
      toDate: '2026-03-17',
      medicationNameFilter: 'אקמול',
    });

    expect(createObjectURLSpy).toHaveBeenCalledTimes(1);
    expect(clickSpy).toHaveBeenCalledTimes(1);
    expect(anchor.download).toBe('history_report_2026-03-01_2026-03-17.csv');
    expect(revokeObjectURLSpy).toHaveBeenCalledWith('blob:csv-test');

    const blob = createObjectURLSpy.mock.calls[0]?.[0];
    expect(blob).toBeInstanceOf(Blob);
    if (!(blob instanceof Blob)) {
      throw new Error('Expected Blob from URL.createObjectURL');
    }
    const content = (await blob.text()).replace(/^\uFEFF/, '');
    expect(content).toContain('"דוח היסטוריית תרופות"');
    expect(content).toContain('"טווח מתאריך","01/03/2026"');
    expect(content).toContain('"טווח עד תאריך","17/03/2026"');
    expect(content).toContain('"סינון תרופה","אקמול"');
    expect(content).toContain('"17/03/2026","08:15","אקמול","נלקח","ווב"');
  });
});

describe('exportHistoryPdf', () => {
  it('renders summary lines and downloads pdf with range-based filename', () => {
    const anchor = document.createElement('a');
    const clickSpy = vi.spyOn(anchor, 'click').mockImplementation(() => undefined);
    const createObjectURLSpy = vi.fn((_: Blob | MediaSource) => 'blob:pdf-test');
    const revokeObjectURLSpy = vi.fn();

    vi.spyOn(document, 'createElement').mockImplementation((tagName: string) => {
      if (tagName === 'a') return anchor;
      return originalCreateElement(tagName);
    });
    Object.defineProperty(URL, 'createObjectURL', { configurable: true, value: createObjectURLSpy });
    Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: revokeObjectURLSpy });

    exportHistoryPdf(
      [
        makeEvent(),
        makeEvent({
          id: 'event-2',
          localTime: '09:00',
          scheduledEpochMs: new Date(2026, 2, 17, 9, 0, 0, 0).getTime(),
          respondedEpochMs: new Date(2026, 2, 17, 9, 0, 0, 0).getTime(),
        }),
      ],
      [makeNotice(), makeNotice({ id: 'notice-2', medicationName: 'אדוויל' })],
      {
        fromDate: '2026-03-01',
        toDate: '2026-03-17',
        medicationNameFilter: 'אקמול',
      }
    );

    const doc = mockState.instances[0];
    expect(doc.output).toHaveBeenCalledWith('blob');
    expect(clickSpy).toHaveBeenCalledTimes(1);
    expect(anchor.download).toBe('history_report_2026-03-01_2026-03-17.pdf');
    expect(createObjectURLSpy).toHaveBeenCalledTimes(1);
    expect(revokeObjectURLSpy).toHaveBeenCalledWith('blob:pdf-test');

    const textCalls = doc.text.mock.calls.map(([text]) => text as string);
    const textBlock = textCalls.join('\n');
    expect(textBlock).toContain("סה\"כ אירועים: 2");
    expect(textBlock).toContain("סה\"כ אירועי מחיקה מהענן: 2");
    expect(textBlock).toContain('אירועי נטילה');
    expect(textCalls).toContain('אירועי מחיקה מהענן');
    expect(textBlock).toContain('17/03/2026');
    expect(textBlock).toContain('08:15');
    expect(textBlock).toContain('09:00');
  });
});
