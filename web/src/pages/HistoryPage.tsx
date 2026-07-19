import { useEffect, useMemo, useState } from 'react';

import { Dialog } from '@/components/Dialog';
import { exportHistoryCsv, exportHistoryPdf } from '@/core/exportReports';
import { toLocalDate } from '@/core/time';
import { deleteDoseEvent, updateDoseEventTime } from '@/data/actions';
import { doseEventEffectiveEpochMs, doseEventEffectiveLocalDate } from '@/domain/doseEventTime';
import { useCloudDeletionNotices, useDoseEvents, useMedications } from '@/hooks/useLocalData';
import { useAppStore } from '@/state/useAppStore';
import { syncEngine } from '@/sync/instance';
import type { ReportMetadata } from '@/core/exportReports';
import type { DoseEventRecord } from '@/domain/models';

interface HistoryTimeGroup {
  startTime: string;
  endTime: string;
  events: DoseEventRecord[];
}

const ONE_DAY_MS = 24 * 60 * 60 * 1000;
const TWO_HOURS_MS = 2 * 60 * 60 * 1000;

function eventTimeEpochMs(event: DoseEventRecord): number {
  return doseEventEffectiveEpochMs(event);
}

function eventDisplayTime(event: DoseEventRecord): string {
  const base = new Date(eventTimeEpochMs(event));
  return `${String(base.getHours()).padStart(2, '0')}:${String(base.getMinutes()).padStart(2, '0')}`;
}

function formatDateChip(localDate: string): string {
  const [year, month, day] = localDate.split('-').map((part) => Number(part));
  const value = new Date(year, month - 1, day);
  return new Intl.DateTimeFormat('he-IL', { day: '2-digit', month: '2-digit' }).format(value);
}

function formatDateHeader(localDate: string): string {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const yesterday = new Date(today.getTime() - ONE_DAY_MS);
  const [year, month, day] = localDate.split('-').map((part) => Number(part));
  const target = new Date(year, month - 1, day);
  target.setHours(0, 0, 0, 0);
  if (target.getTime() === today.getTime()) return '📅 היום';
  if (target.getTime() === yesterday.getTime()) return '📅 אתמול';
  return new Intl.DateTimeFormat('he-IL', { weekday: 'long', day: '2-digit', month: 'long' }).format(target);
}

function formatTimeAgo(event: DoseEventRecord): string {
  const now = Date.now();
  const diffMinutes = Math.floor((now - eventTimeEpochMs(event)) / (60 * 1000));
  const absMinutes = Math.abs(diffMinutes);
  if (absMinutes < 1) return 'ממש עכשיו';
  if (absMinutes >= 60 && absMinutes <= 1439) {
    const hours = Math.floor(absMinutes / 60);
    const minutes = absMinutes % 60;
    const hhmm = `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}`;
    return diffMinutes >= 0 ? `לפני ${hhmm} שעות` : `בעוד ${hhmm} שעות`;
  }
  if (diffMinutes >= 0 && absMinutes >= 1440 && absMinutes <= 2879) return 'לפני יותר מ-24 שעות';
  if (absMinutes < 60) return diffMinutes >= 0 ? `לפני ${absMinutes} דקות` : `בעוד ${absMinutes} דקות`;
  const days = Math.floor(absMinutes / 1440);
  return diffMinutes >= 0 ? `לפני ${days} ימים` : `בעוד ${days} ימים`;
}

function canDeleteEvent(event: DoseEventRecord): boolean {
  return Date.now() - event.updatedAtEpochMs <= TWO_HOURS_MS;
}

function groupEventsByFiveMinutes(events: DoseEventRecord[]): HistoryTimeGroup[] {
  if (events.length === 0) return [];
  const sorted = [...events].sort((a, b) => eventTimeEpochMs(b) - eventTimeEpochMs(a));
  const groups: HistoryTimeGroup[] = [];
  let current: DoseEventRecord[] = [sorted[0]];
  let currentStart = eventTimeEpochMs(sorted[0]);

  for (let index = 1; index < sorted.length; index += 1) {
    const candidate = sorted[index];
    const minuteDiff = Math.floor((currentStart - eventTimeEpochMs(candidate)) / (60 * 1000));
    if (minuteDiff <= 5) {
      current.push(candidate);
      continue;
    }
    groups.push({
      startTime: eventDisplayTime(current[0]),
      endTime: eventDisplayTime(current[current.length - 1]),
      events: current,
    });
    current = [candidate];
    currentStart = eventTimeEpochMs(candidate);
  }

  groups.push({
    startTime: eventDisplayTime(current[0]),
    endTime: eventDisplayTime(current[current.length - 1]),
    events: current,
  });

  return groups;
}

function daysAgoDate(days: number): string {
  const value = new Date();
  value.setDate(value.getDate() - days);
  return toLocalDate(value.getTime());
}

function normalizeDateRange(fromDate: string, toDate: string): { normalizedFrom: string; normalizedTo: string } {
  return fromDate <= toDate
    ? { normalizedFrom: fromDate, normalizedTo: toDate }
    : { normalizedFrom: toDate, normalizedTo: fromDate };
}

type DateRange = 'today' | 'twoDays' | 'week' | 'custom';

export function HistoryPage() {
  const events = useDoseEvents();
  const notices = useCloudDeletionNotices();
  const medications = useMedications();
  const { setMessage } = useAppStore();

  const today = toLocalDate(Date.now());
  const defaultFrom = useMemo(() => daysAgoDate(30), [today]);

  const [dateRange, setDateRange] = useState<DateRange>('twoDays');
  const [customFromDate, setCustomFromDate] = useState(daysAgoDate(7));
  const [customToDate, setCustomToDate] = useState(today);
  const [selectedMedicationId, setSelectedMedicationId] = useState<string>('');
  const [medicationSearchQuery, setMedicationSearchQuery] = useState('');
  const [showReportsDialog, setShowReportsDialog] = useState(false);
  const [reportFromDate, setReportFromDate] = useState(defaultFrom);
  const [reportToDate, setReportToDate] = useState(today);
  const [reportMedicationId, setReportMedicationId] = useState('');
  const [editingEventId, setEditingEventId] = useState<string | null>(null);
  const [editingTimeValue, setEditingTimeValue] = useState('');
  const [deleteTarget, setDeleteTarget] = useState<DoseEventRecord | null>(null);
  const dateRangeOptions: Array<{ value: DateRange; label: string; sortOrder: number }> = [
    { value: 'today', label: 'היום', sortOrder: 1 },
    { value: 'twoDays', label: 'יומיים', sortOrder: 2 },
    { value: 'week', label: 'שבוע', sortOrder: 3 },
    { value: 'custom', label: 'מותאם', sortOrder: 4 }
  ];

  useEffect(() => {
    void syncEngine.syncNow('history_open');
  }, []);

  // Compute actual from/to based on range mode
  const { selectedFromDate, selectedToDate } = useMemo(() => {
    switch (dateRange) {
      case 'today': return { selectedFromDate: today, selectedToDate: today };
      case 'twoDays': return { selectedFromDate: daysAgoDate(1), selectedToDate: today };
      case 'week': return { selectedFromDate: daysAgoDate(7), selectedToDate: today };
      case 'custom': return { selectedFromDate: customFromDate, selectedToDate: customToDate };
    }
  }, [dateRange, today, customFromDate, customToDate]);

  const takenEvents = useMemo(() => {
    return events.filter((event) => event.status === 'TAKEN');
  }, [events]);

  const takenTodayCountByMedicationId = useMemo(() => {
    return takenEvents
      .filter((event) => doseEventEffectiveLocalDate(event) === today)
      .reduce<Record<string, number>>((acc, event) => {
        acc[event.medicationId] = (acc[event.medicationId] ?? 0) + 1;
        return acc;
      }, {});
  }, [takenEvents, today]);

  const asNeededOrOneTimeMedicationIds = useMemo(() => {
    return new Set(medications.filter((item) => item.times.length === 0).map((item) => item.id));
  }, [medications]);

  const dateFilteredEvents = useMemo(() => {
    const [rangeStart, rangeEnd] =
      selectedFromDate <= selectedToDate
        ? [selectedFromDate, selectedToDate]
        : [selectedToDate, selectedFromDate];
    return takenEvents.filter((event) => {
      const eventDate = doseEventEffectiveLocalDate(event);
      return eventDate >= rangeStart && eventDate <= rangeEnd;
    });
  }, [takenEvents, selectedFromDate, selectedToDate]);

  const textFilteredEvents = useMemo(() => {
    const query = medicationSearchQuery.trim().toLowerCase();
    if (!query) return dateFilteredEvents;
    return dateFilteredEvents.filter((event) => event.medicationName.toLowerCase().includes(query));
  }, [dateFilteredEvents, medicationSearchQuery]);

  const medicationFilters = useMemo(() => {
    return textFilteredEvents
      .map((event) => ({ id: event.medicationId, name: event.medicationName }))
      .filter((value, index, array) => array.findIndex((candidate) => candidate.id === value.id) === index)
      .sort((a, b) => a.name.localeCompare(b.name, 'he'));
  }, [textFilteredEvents]);

  const effectiveMedicationId = useMemo(() => {
    if (!selectedMedicationId) return '';
    return medicationFilters.some((item) => item.id === selectedMedicationId) ? selectedMedicationId : '';
  }, [medicationFilters, selectedMedicationId]);

  const filteredEvents = useMemo(() => {
    if (!effectiveMedicationId) return textFilteredEvents;
    return textFilteredEvents.filter((event) => event.medicationId === effectiveMedicationId);
  }, [effectiveMedicationId, textFilteredEvents]);

  const groupedItems = useMemo(() => {
    const byDate = filteredEvents.reduce<Record<string, DoseEventRecord[]>>((acc, event) => {
      const eventDate = doseEventEffectiveLocalDate(event);
      if (!acc[eventDate]) acc[eventDate] = [];
      acc[eventDate].push(event);
      return acc;
    }, {});
    return Object.keys(byDate)
      .sort((a, b) => b.localeCompare(a))
      .map((date) => ({ date, groups: groupEventsByFiveMinutes(byDate[date]) }));
  }, [filteredEvents]);

  function onStartEditing(event: DoseEventRecord) {
    setEditingEventId(event.id);
    setEditingTimeValue(eventDisplayTime(event));
  }

  async function onSaveTime() {
    if (!editingEventId || !editingTimeValue) return;
    await updateDoseEventTime(editingEventId, editingTimeValue);
    setMessage('✅ שעה עודכנה');
    setEditingEventId(null);
  }

  function onCancelEditing() {
    setEditingEventId(null);
    setEditingTimeValue('');
  }

  async function onDelete(eventId: string) {
    await deleteDoseEvent(eventId);
    setMessage('🗑 אירוע נמחק');
    setDeleteTarget(null);
  }

  function handleExport(type: 'csv' | 'pdf') {
    const { normalizedFrom, normalizedTo } = normalizeDateRange(reportFromDate, reportToDate);
    const selectedMedicationLabel =
      reportMedicationId ? medicationFilters.find((medication) => medication.id === reportMedicationId)?.name ?? '' : '';
    const reportEvents = takenEvents.filter(
      (event) => {
        const eventDate = doseEventEffectiveLocalDate(event);
        return (
          eventDate >= normalizedFrom &&
          eventDate <= normalizedTo &&
          (!reportMedicationId || event.medicationId === reportMedicationId)
        );
      }
    );
    const reportNotices = notices.filter((notice) => {
      const noticeDate = toLocalDate(notice.deletedAtEpochMs);
      return noticeDate >= normalizedFrom && noticeDate <= normalizedTo;
    });
    const exportMetadata: ReportMetadata = {
      fromDate: normalizedFrom,
      toDate: normalizedTo,
      medicationNameFilter: selectedMedicationLabel || undefined,
    };
    if (type === 'csv') {
      exportHistoryCsv(reportEvents, reportNotices, exportMetadata);
    } else {
      exportHistoryPdf(reportEvents, reportNotices, exportMetadata);
    }
    setShowReportsDialog(false);
    setMessage(`✅ דוח ${type.toUpperCase()} הורד בהצלחה`);
  }

  return (
    <div className="stack-lg">
      {/* Header */}
      <section className="panel">
        <div className="history-top-row">
          <h1 className="page-title">
            <span aria-hidden="true">📋</span> היסטוריה
            {filteredEvents.length > 0 ? (
              <span className="event-count-badge">{filteredEvents.length}</span>
            ) : null}
          </h1>
          <button type="button" className="secondary" onClick={() => setShowReportsDialog(true)}>
            📄 דוחות
          </button>
        </div>

        {/* Quick date range chips */}
        <div className="date-range-chips">
          {dateRangeOptions
            .slice()
            .sort((a, b) => a.sortOrder - b.sortOrder)
            .map((option) => (
              <button
                key={option.value}
                type="button"
                className={`range-chip ${dateRange === option.value ? 'range-active' : ''}`}
                onClick={() => setDateRange(option.value)}
              >
                {option.label}
              </button>
            ))}
        </div>

        {/* Custom date inputs */}
        {dateRange === 'custom' ? (
          <div className="history-filters-row mt-sm">
            <div className="form-group">
              <label htmlFor="historyFromDate">מתאריך</label>
              <input
                id="historyFromDate"
                type="date"
                value={customFromDate}
                onChange={(event) => setCustomFromDate(event.target.value)}
              />
            </div>
            <div className="form-group">
              <label htmlFor="historyToDate">עד תאריך</label>
              <input id="historyToDate" type="date" value={customToDate} onChange={(event) => setCustomToDate(event.target.value)} />
            </div>
          </div>
        ) : null}

        {/* Medication filter + search */}
        {medicationFilters.length > 1 || medicationSearchQuery.trim() ? (
          <div className="stack mt-sm">
            <div className="form-group">
              <label htmlFor="historyMedicationSearch" className="sr-only">חיפוש לפי תרופה</label>
              <input
                id="historyMedicationSearch"
                type="search"
                placeholder="🔍 חפשו תרופה לפי שם..."
                value={medicationSearchQuery}
                onChange={(event) => setMedicationSearchQuery(event.target.value)}
                className="search-input"
              />
            </div>
            {medicationFilters.length > 1 ? (
              <div className="form-group">
                <select
                  id="historyMedicationFilter"
                  value={effectiveMedicationId}
                  onChange={(event) => setSelectedMedicationId(event.target.value)}
                >
                  <option value="">הכל ({medicationFilters.length} תרופות)</option>
                  {medicationFilters.map((medication) => (
                    <option key={medication.id} value={medication.id}>
                      {medication.name}
                    </option>
                  ))}
                </select>
              </div>
            ) : null}
          </div>
        ) : null}
      </section>

      {/* Cloud Deletion Notices */}
      {notices.length > 0 ? (
        <section className="panel warning-panel" data-testid="cloud-deletion-notices">
          <h2 className="section-title">⚠️ מחיקות מהענן</h2>
          <ul className="plain-list">
            {notices.slice(0, 12).map((notice) => (
              <li key={notice.id}>
                נמחק: {notice.recordTypeLabel} / {notice.medicationName} ({notice.deletedAtLabel})
              </li>
            ))}
          </ul>
        </section>
      ) : null}

      {/* Events */}
      {groupedItems.length === 0 ? (
        <section className="panel">
          <div className="history-empty">
            <span className="empty-icon">📋</span>
            <h3>אין היסטוריה להצגה</h3>
            <p className="muted">
              {dateRange === 'today' ? 'עדיין לא נרשמו נטילות היום' : 'נסו לבחור טווח תאריכים אחר'}
            </p>
          </div>
        </section>
      ) : (
        <div className="stack">
          {groupedItems.map((dateGroup) => (
            <section key={dateGroup.date} className="stack">
              <h3 className="history-date-header">{formatDateHeader(dateGroup.date)}</h3>
              {dateGroup.groups.map((group) => (
                <article key={`${dateGroup.date}-${group.startTime}-${group.endTime}-${group.events[0]?.id ?? 'empty'}`} className="card">
                  <header className="history-group-header">
                    <div>
                      <h3>{group.events.length === 1 ? group.events[0].medicationName : `${group.events.length} תרופות`}</h3>
                    </div>
                    <p className="history-time-chip">{group.startTime === group.endTime ? group.startTime : `${group.startTime} – ${group.endTime}`}</p>
                  </header>
                  <p className="history-ago-chip">⏳ {formatTimeAgo(group.events[0])}</p>

                  {group.events.map((event, index) => {
                    const isAsNeededOrOneTime = asNeededOrOneTimeMedicationIds.has(event.medicationId);
                    const takenTodayCount = takenTodayCountByMedicationId[event.medicationId] ?? 0;
                    const canDelete = canDeleteEvent(event);
                    const isEditing = editingEventId === event.id;

                    return (
                      <div key={event.id} className="history-event-row">
                        {group.events.length > 1 ? <p className="history-index-chip">{index + 1}</p> : null}
                        <div className="history-event-main">
                          {group.events.length > 1 ? <strong>{event.medicationName}</strong> : null}
                          <div className="history-tags">
                            <span className={`history-tag ${isAsNeededOrOneTime ? 'as-needed' : 'fixed'}`}>
                              {isAsNeededOrOneTime ? 'לפי צורך' : 'קבוע'}
                            </span>
                            <span className="history-tag neutral">×{takenTodayCount} היום</span>
                            <span className="history-tag neutral">{formatDateChip(doseEventEffectiveLocalDate(event))}</span>
                          </div>

                          {/* Inline time editing */}
                          {isEditing ? (
                            <div className="inline-edit-row">
                              <input
                                type="time"
                                value={editingTimeValue}
                                onChange={(e) => setEditingTimeValue(e.target.value)}
                                autoFocus
                              />
                              <button type="button" onClick={onSaveTime} className="inline-save-btn">
                                ✓
                              </button>
                              <button type="button" onClick={onCancelEditing} className="ghost inline-cancel-btn">
                                ✕
                              </button>
                            </div>
                          ) : (
                            <div className="event-actions-row">
                              <button type="button" className="ghost event-action-btn" onClick={() => onStartEditing(event)}>
                                ✏️ ערוך שעה
                              </button>
                              {canDelete ? (
                                <button type="button" className="ghost event-action-btn event-action-delete" onClick={() => setDeleteTarget(event)}>
                                  🗑 מחק
                                </button>
                              ) : null}
                            </div>
                          )}
                        </div>
                      </div>
                    );
                  })}
                </article>
              ))}
            </section>
          ))}
        </div>
      )}

      {/* Reports Dialog */}
      <Dialog isOpen={showReportsDialog} onClose={() => setShowReportsDialog(false)} title="📄 יצירת דוח">
        <div className="stack">
          <div className="history-filters-row">
            <div className="form-group">
              <label htmlFor="reportFromDate">מתאריך</label>
              <input id="reportFromDate" type="date" value={reportFromDate} onChange={(event) => setReportFromDate(event.target.value)} />
            </div>
            <div className="form-group">
              <label htmlFor="reportToDate">עד תאריך</label>
              <input id="reportToDate" type="date" value={reportToDate} onChange={(event) => setReportToDate(event.target.value)} />
            </div>
          </div>
          <div className="form-group">
            <label htmlFor="reportMedicationFilter">תרופה</label>
            <select id="reportMedicationFilter" value={reportMedicationId} onChange={(event) => setReportMedicationId(event.target.value)}>
              <option value="">כל התרופות</option>
              {medicationFilters.map((medication) => (
                <option key={medication.id} value={medication.id}>
                  {medication.name}
                </option>
              ))}
            </select>
          </div>
          <div className="button-row">
            <button type="button" onClick={() => handleExport('pdf')} className="btn-flex">
              📄 PDF
            </button>
            <button type="button" className="secondary btn-flex" onClick={() => handleExport('csv')}>
              📊 CSV
            </button>
          </div>
          <button type="button" className="ghost btn-block" onClick={() => setShowReportsDialog(false)}>
            ביטול
          </button>
        </div>
      </Dialog>

      {/* Delete Confirmation Dialog */}
      <Dialog isOpen={Boolean(deleteTarget)} onClose={() => setDeleteTarget(null)} title="⚠️ מחיקת אירוע">
        {deleteTarget ? (
          <div className="stack">
            <p className="section-subtitle">
              למחוק את הנטילה של <strong className="text-strong">{deleteTarget.medicationName}</strong> בשעה{' '}
              <strong className="text-strong">{eventDisplayTime(deleteTarget)}</strong>?
            </p>
            <div className="button-row">
              <button type="button" className="danger btn-flex" onClick={() => onDelete(deleteTarget.id)}>
                🗑 מחק
              </button>
              <button type="button" className="secondary btn-flex" onClick={() => setDeleteTarget(null)}>
                ביטול
              </button>
            </div>
          </div>
        ) : null}
      </Dialog>
    </div>
  );
}
