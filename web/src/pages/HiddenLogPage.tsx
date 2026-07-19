import { useMemo, useState } from 'react';

import type { HiddenIntakeLogRecord } from '@/domain/models';
import { useHiddenIntakeLogs } from '@/hooks/useLocalData';

type OperationTypeFilter = HiddenIntakeLogRecord['operationType'] | 'ALL';
type ActionSourceFilter = HiddenIntakeLogRecord['actionSource'] | 'ALL';

function operationLabel(type: HiddenIntakeLogRecord['operationType']): string {
  switch (type) {
    case 'ADD':
      return 'הוספה';
    case 'UPDATE':
      return 'עדכון';
    case 'DELETE':
      return 'מחיקה';
    case 'CHANGE':
      return 'שינוי';
    default:
      return 'פעולה לא מזוהה';
  }
}

function sourceLabel(source: HiddenIntakeLogRecord['actionSource']): string {
  switch (source) {
    case 'APP':
      return 'אפליקציה';
    case 'NOTIFICATION':
      return 'התראה';
    case 'SYSTEM':
      return 'מערכת';
    default:
      return 'מקור לא מזוהה';
  }
}

function statusLabel(status: string): string {
  switch (status) {
    case 'PENDING':
      return 'ממתין';
    case 'MISSED':
      return 'הוחמץ';
    case 'TAKEN':
      return 'נלקח';
    default:
      return 'לא ידוע';
  }
}

function recordTypeLabel(recordType: string): string {
  switch (recordType) {
    case 'MEDICATION':
      return 'תרופה';
    case 'DOSE_EVENT':
      return 'אירוע נטילה';
    case 'HIDDEN_INTAKE_LOG':
      return 'יומן פנימי';
    default:
      return 'רשומה';
  }
}

function translateOperationDetails(details: string | null): string {
  if (!details) {
    return 'ללא פרטים נוספים';
  }

  if (details === 'event_added') {
    return 'נוסף אירוע נטילה';
  }

  if (details === 'fixed_time_event_added') {
    return 'נוסף אירוע נטילה מזמן קבוע';
  }

  if (details === 'as_needed_intake_added') {
    return 'נוספה נטילה לפי צורך';
  }

  if (details === 'one_time_intake_added') {
    return 'נוספה נטילה חד-פעמית';
  }

  if (details === 'event_deleted') {
    return 'אירוע הנטילה נמחק';
  }

  if (details === 'delete_denied:outside_edit_window') {
    return 'המחיקה נחסמה: מחוץ לחלון העריכה';
  }

  const statusMatch = details.match(/^status:([^:]+)->([^:]+)$/);
  if (statusMatch) {
    const fromStatus = statusLabel(statusMatch[1] ?? '');
    const toStatus = statusLabel(statusMatch[2] ?? '');
    return `סטטוס עודכן מ־${fromStatus} ל־${toStatus}`;
  }

  const localTimeMatch = details.match(/^localTime:(\d{1,2}:\d{2})->(\d{1,2}:\d{2})$/);
  if (localTimeMatch) {
    const fromTime = localTimeMatch[1] ?? '';
    const toTime = localTimeMatch[2] ?? '';
    return `שעת הנטילה עודכנה מ־${fromTime} ל־${toTime}`;
  }

  if (details.startsWith('cloud_deleted:')) {
    const parts = details.split(':');
    const recordType = parts[1];
    const deletedAtRaw = parts[3];

    if (recordType) {
      const typeText = recordTypeLabel(recordType);
      const deletedAtEpochMs = Number(deletedAtRaw);

      if (Number.isFinite(deletedAtEpochMs)) {
        const deletedAtText = new Date(deletedAtEpochMs).toLocaleString('he-IL');
        return `נמחק בענן: ${typeText} בתאריך ${deletedAtText}`;
      }

      return `נמחק בענן: ${typeText}`;
    }

    return 'נרשמה מחיקה בענן';
  }

  return 'פרטי הפעולה אינם זמינים להצגה';
}

export function HiddenLogPage() {
  const logs = useHiddenIntakeLogs();
  const [query, setQuery] = useState('');
  const [operationType, setOperationType] = useState<OperationTypeFilter>('ALL');
  const [actionSource, setActionSource] = useState<ActionSourceFilter>('ALL');

  const filtered = useMemo(() => {
    const normalized = query.trim().toLowerCase();
    return logs.filter((log) => {
      if (operationType !== 'ALL' && log.operationType !== operationType) {
        return false;
      }

      if (actionSource !== 'ALL' && log.actionSource !== actionSource) {
        return false;
      }

      if (!normalized) {
        return true;
      }

      const translatedDetails = translateOperationDetails(log.operationDetails);
      return (
        log.medicationName.toLowerCase().includes(normalized)
        || (log.operationDetails ?? '').toLowerCase().includes(normalized)
        || translatedDetails.toLowerCase().includes(normalized)
      );
    });
  }, [actionSource, logs, operationType, query]);

  const summary = useMemo(() => {
    const operationCounts: Record<'ADD' | 'UPDATE' | 'DELETE' | 'CHANGE', number> = {
      ADD: 0,
      UPDATE: 0,
      DELETE: 0,
      CHANGE: 0,
    };
    const sourceCounts: Record<'APP' | 'NOTIFICATION' | 'SYSTEM', number> = {
      APP: 0,
      NOTIFICATION: 0,
      SYSTEM: 0,
    };

    filtered.forEach((log) => {
      operationCounts[log.operationType] += 1;
      sourceCounts[log.actionSource] += 1;
    });

    return {
      total: logs.length,
      filtered: filtered.length,
      operationCounts,
      sourceCounts,
    };
  }, [filtered, logs.length]);

  return (
    <div className="stack-lg">
      <section className="panel">
        <h1 className="page-title">
          <span aria-hidden="true">🔍</span> יומן פנימי
        </h1>

        <div className="form-row hidden-log-filters-row">
          <div className="form-group">
            <label htmlFor="searchLog">חיפוש</label>
            <input
              id="searchLog"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="שם תרופה או פרטי פעולה..."
            />
          </div>
          <div className="form-group">
            <label htmlFor="filterOperation">סוג פעולה</label>
            <select id="filterOperation" value={operationType} onChange={(event) => setOperationType(event.target.value as OperationTypeFilter)}>
              <option value="ALL">הכל</option>
              <option value="ADD">הוספה</option>
              <option value="UPDATE">עדכון</option>
              <option value="DELETE">מחיקה</option>
              <option value="CHANGE">שינוי</option>
            </select>
          </div>
          <div className="form-group">
            <label htmlFor="filterSource">מקור</label>
            <select id="filterSource" value={actionSource} onChange={(event) => setActionSource(event.target.value as ActionSourceFilter)}>
              <option value="ALL">הכל</option>
              <option value="APP">אפליקציה</option>
              <option value="NOTIFICATION">התראה</option>
              <option value="SYSTEM">מערכת</option>
            </select>
          </div>
        </div>

        <div className="hidden-log-summary" aria-live="polite">
          <div className="hidden-log-summary-grid">
            <article className="hidden-log-summary-card">
              <p className="hidden-log-summary-label">סה"כ רשומות</p>
              <p className="hidden-log-summary-value">{summary.total}</p>
            </article>
            <article className="hidden-log-summary-card">
              <p className="hidden-log-summary-label">מוצגות כעת</p>
              <p className="hidden-log-summary-value">{summary.filtered}</p>
            </article>
            <article className="hidden-log-summary-card">
              <p className="hidden-log-summary-label">הוספה / עדכון</p>
              <p className="hidden-log-summary-value">
                {summary.operationCounts.ADD} / {summary.operationCounts.UPDATE}
              </p>
            </article>
            <article className="hidden-log-summary-card">
              <p className="hidden-log-summary-label">מחיקה / שינוי</p>
              <p className="hidden-log-summary-value">
                {summary.operationCounts.DELETE} / {summary.operationCounts.CHANGE}
              </p>
            </article>
          </div>

          <div className="hidden-log-breakdown-row">
            <div className="hidden-log-breakdown-group" aria-label="התפלגות לפי סוג פעולה">
              <span className="meta-chip hidden-log-stat-chip">הוספה: {summary.operationCounts.ADD}</span>
              <span className="meta-chip hidden-log-stat-chip">עדכון: {summary.operationCounts.UPDATE}</span>
              <span className="meta-chip hidden-log-stat-chip">מחיקה: {summary.operationCounts.DELETE}</span>
              <span className="meta-chip hidden-log-stat-chip">שינוי: {summary.operationCounts.CHANGE}</span>
            </div>
            <div className="hidden-log-breakdown-group" aria-label="התפלגות לפי מקור">
              <span className="meta-chip hidden-log-stat-chip">אפליקציה: {summary.sourceCounts.APP}</span>
              <span className="meta-chip hidden-log-stat-chip">התראה: {summary.sourceCounts.NOTIFICATION}</span>
              <span className="meta-chip hidden-log-stat-chip">מערכת: {summary.sourceCounts.SYSTEM}</span>
            </div>
          </div>
        </div>
      </section>

      {filtered.length === 0 ? (
        <section className="panel">
          <div className="history-empty">
            <span className="empty-icon">🔍</span>
            <h3>אין רשומות להצגה</h3>
            <p className="muted">נסו לשנות את הסינון או החיפוש</p>
          </div>
        </section>
      ) : (
        <section className="panel hidden-log-table-panel">
          <div className="hidden-log-table-wrap" role="region" aria-label="טבלת רשומות יומן פנימי" tabIndex={0}>
            <table className="hidden-log-table">
              <caption className="sr-only">רשומות יומן פנימי</caption>
              <thead>
                <tr>
                  <th scope="col">תאריך ושעה</th>
                  <th scope="col">תרופה</th>
                  <th scope="col">סוג פעולה</th>
                  <th scope="col">מקור</th>
                  <th scope="col">פרטים</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((log) => {
                  const detailsText = translateOperationDetails(log.operationDetails);
                  return (
                    <tr key={log.id}>
                      <td className="hidden-log-date-cell">
                        <time dateTime={new Date(log.createdAtEpochMs).toISOString()}>
                          {new Date(log.createdAtEpochMs).toLocaleString('he-IL')}
                        </time>
                      </td>
                      <td>{log.medicationName}</td>
                      <td>
                        <span className={`meta-chip hidden-log-chip hidden-log-chip-${log.operationType.toLowerCase()}`}>
                          {operationLabel(log.operationType)}
                        </span>
                      </td>
                      <td>
                        <span className={`meta-chip hidden-log-chip hidden-log-source-chip-${log.actionSource.toLowerCase()}`}>
                          {sourceLabel(log.actionSource)}
                        </span>
                      </td>
                      <td className="hidden-log-details-cell">{detailsText}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </section>
      )}
    </div>
  );
}
