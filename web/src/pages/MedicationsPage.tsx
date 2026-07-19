import { useEffect, useMemo, useRef, useState } from 'react';
import type { FormEvent } from 'react';

import { Dialog } from '@/components/Dialog';
import { toLocalDate } from '@/core/time';
import { createDoseEvent, saveMedication, setMedicationActive, type MedicationInput } from '@/data/actions';
import type { DoseEventRecord, MedicationRecord } from '@/domain/models';
import { useDoseEvents, useMedications } from '@/hooks/useLocalData';
import { useAppStore } from '@/state/useAppStore';

const weekdayLabels: Array<{ day: number; label: string }> = [
  { day: 1, label: 'ב׳' },
  { day: 2, label: 'ג׳' },
  { day: 3, label: 'ד׳' },
  { day: 4, label: 'ה׳' },
  { day: 5, label: 'ו׳' },
  { day: 6, label: 'ש׳' },
  { day: 7, label: 'א׳' },
];

const baseInput: MedicationInput = {
  name: '',
  doseText: '1',
  doseUnit: 'יחידה',
  foodRelation: 'NONE',
  scheduleMode: 'FIXED_TIMES',
  intervalHours: null,
  windowStartTime: null,
  windowEndTime: null,
  startDate: toLocalDate(Date.now()),
  endDate: null,
  notes: '',
  weekdays: [1, 2, 3, 4, 5, 6, 7],
  times: ['08:00'],
  isActive: true,
};

function inputFromMedication(medication: MedicationRecord): MedicationInput {
  return {
    id: medication.id,
    name: medication.name,
    doseText: medication.doseText,
    doseUnit: medication.doseUnit,
    foodRelation: medication.foodRelation,
    scheduleMode: medication.scheduleMode,
    intervalHours: medication.intervalHours,
    windowStartTime: medication.windowStartTime,
    windowEndTime: medication.windowEndTime,
    startDate: medication.startDate,
    endDate: medication.endDate,
    notes: medication.notes,
    weekdays: medication.weekdays,
    times: medication.times.map((value) => value.localTime),
    isActive: medication.isActive,
  };
}

function scheduleModeLabel(mode: string): string {
  switch (mode) {
    case 'FIXED_TIMES': return 'שעות קבועות';
    case 'EVERY_X_HOURS': return 'כל X שעות';
    case 'EVERY_X_DAYS': return 'כל X ימים';
    default: return mode;
  }
}

function isAsNeededMedication(medication: MedicationRecord): boolean {
  return medication.scheduleMode === 'FIXED_TIMES' && medication.times.length === 0;
}

function isAsNeededInput(input: MedicationInput): boolean {
  return input.scheduleMode === 'FIXED_TIMES' && input.times.length === 0;
}

function medicationScheduleText(medication: MedicationRecord): string {
  if (isAsNeededMedication(medication)) {
    return 'לפי צורך';
  }

  if ((medication.scheduleMode === 'EVERY_X_HOURS' || medication.scheduleMode === 'EVERY_X_DAYS') && medication.intervalHours) {
    return `כל ${medication.intervalHours} ${medication.scheduleMode === 'EVERY_X_HOURS' ? 'שעות' : 'ימים'}`;
  }

  return scheduleModeLabel(medication.scheduleMode);
}

function getLastTakenInfo(events: DoseEventRecord[], medicationId: string): string | null {
  const medEvents = events
    .filter((e) => e.medicationId === medicationId && e.status === 'TAKEN')
    .sort((a, b) => b.updatedAtEpochMs - a.updatedAtEpochMs);

  if (medEvents.length === 0) return null;

  const last = medEvents[0];
  const now = Date.now();
  const diffMinutes = Math.floor((now - last.updatedAtEpochMs) / (60 * 1000));

  if (diffMinutes < 1) return 'ממש עכשיו';
  if (diffMinutes < 60) return `לפני ${diffMinutes} דקות`;
  const hours = Math.floor(diffMinutes / 60);
  if (hours < 24) return `לפני ${hours} שעות`;
  const days = Math.floor(hours / 24);
  return `לפני ${days} ימים`;
}

export function MedicationsPage() {
  const medications = useMedications();
  const events = useDoseEvents();
  const { setMessage } = useAppStore();

  const [input, setInput] = useState<MedicationInput>(baseInput);
  const [isSaving, setIsSaving] = useState(false);
  const [showForm, setShowForm] = useState(false);
  const [expandedCardId, setExpandedCardId] = useState<string | null>(null);
  const [justTakenId, setJustTakenId] = useState<string | null>(null);
  const [confirmDeactivateId, setConfirmDeactivateId] = useState<string | null>(null);
  const [isTakingMedicationId, setIsTakingMedicationId] = useState<string | null>(null);
  const [quickMedicationId, setQuickMedicationId] = useState<string>('');
  const nameInputRef = useRef<HTMLInputElement>(null);

  const today = toLocalDate(Date.now());
  const asNeededInput = isAsNeededInput(input);
  const isEveryXDaysInput = input.scheduleMode === 'EVERY_X_DAYS';

  // Sort: active first, then alphabetically
  const sorted = useMemo(
    () => [...medications].sort((a, b) => {
      if (a.isActive !== b.isActive) return a.isActive ? -1 : 1;
      return a.name.localeCompare(b.name, 'he');
    }),
    [medications]
  );

  const activeCount = useMemo(() => sorted.filter((m) => m.isActive).length, [sorted]);
  const activeMedications = useMemo(() => sorted.filter((m) => m.isActive), [sorted]);
  const quickTakeMedications = useMemo(
    () => activeMedications.filter(isAsNeededMedication),
    [activeMedications]
  );

  // Today's taken count per medication
  const takenTodayMap = useMemo(() => {
    return events
      .filter((event) => event.status === 'TAKEN' && event.localDate === today)
      .reduce<Record<string, number>>((acc, event) => {
        acc[event.medicationId] = (acc[event.medicationId] ?? 0) + 1;
        return acc;
      }, {});
  }, [events, today]);

  // Focus name input when form opens
  useEffect(() => {
    if (showForm && nameInputRef.current) {
      setTimeout(() => nameInputRef.current?.focus(), 100);
    }
  }, [showForm]);

  // Clear "just taken" animation
  useEffect(() => {
    if (!justTakenId) return;
    const timeout = window.setTimeout(() => setJustTakenId(null), 1500);
    return () => window.clearTimeout(timeout);
  }, [justTakenId]);

  useEffect(() => {
    if (quickTakeMedications.length === 0) {
      setQuickMedicationId('');
      return;
    }

    const exists = quickTakeMedications.some((medication) => medication.id === quickMedicationId);
    if (!exists) {
      setQuickMedicationId(quickTakeMedications[0].id);
    }
  }, [quickTakeMedications, quickMedicationId]);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setIsSaving(true);
    try {
      if (!input.name.trim()) {
        setMessage('⚠️ יש להזין שם תרופה');
        return;
      }
      if (input.scheduleMode !== 'FIXED_TIMES' && (input.intervalHours == null || input.intervalHours < 1)) {
        setMessage(`⚠️ יש להגדיר כל כמה ${input.scheduleMode === 'EVERY_X_HOURS' ? 'שעות' : 'ימים'} לקחת את התרופה`);
        return;
      }
      if (isEveryXDaysInput && !input.startDate) {
        setMessage('⚠️ יש לבחור את תאריך הנטילה הראשון');
        return;
      }
      if (isEveryXDaysInput && input.times.length === 0) {
        setMessage('⚠️ יש לבחור שעת נטילה ליום היעד');
        return;
      }
      await saveMedication(input);
      setInput(baseInput);
      setShowForm(false);
      setMessage('✅ התרופה נשמרה בהצלחה');
    } finally {
      setIsSaving(false);
    }
  }

  function onTimesChange(value: string) {
    const times = value
      .split(',')
      .map((item) => item.trim())
      .filter(Boolean);

    setInput((current) => ({
      ...current,
      times,
    }));
  }

  function onScheduleModeChange(scheduleMode: MedicationInput['scheduleMode']) {
    setInput((current) => ({
      ...current,
      scheduleMode,
      intervalHours: scheduleMode === 'FIXED_TIMES' ? null : current.intervalHours,
      times: scheduleMode === 'EVERY_X_DAYS'
        ? (current.times.length > 0 ? current.times.slice(0, 1) : ['08:00'])
        : current.times,
    }));
  }

  function onEveryXDaysTimeChange(value: string) {
    setInput((current) => ({
      ...current,
      times: value ? [value] : [],
    }));
  }

  function onWeekdayToggle(day: number) {
    setInput((current) => {
      const exists = current.weekdays.includes(day);
      return {
        ...current,
        weekdays: exists ? current.weekdays.filter((value) => value !== day) : [...current.weekdays, day],
      };
    });
  }

  async function onToggleActive(medicationId: string, isActive: boolean) {
    if (!isActive) {
      // Deactivating — ask for confirmation
      setConfirmDeactivateId(medicationId);
      return;
    }
    // Reactivating — no confirmation needed
    await setMedicationActive(medicationId, true);
    setMessage('✅ התרופה שוחזרה');
  }

  async function onConfirmDeactivate() {
    if (!confirmDeactivateId) return;
    await setMedicationActive(confirmDeactivateId, false);
    setMessage('⏸ התרופה הושבתה');
    setConfirmDeactivateId(null);
  }

  async function onTakeDoseNow(medicationId: string) {
    if (!medicationId || isTakingMedicationId) return;
    setIsTakingMedicationId(medicationId);
    setJustTakenId(medicationId);
    try {
      await createDoseEvent({ medicationId, status: 'TAKEN' });
      setMessage('✅ נרשמה נטילה');
    } catch {
      setMessage('⚠️ לא הצלחנו לרשום נטילה, נסו שוב');
    } finally {
      setIsTakingMedicationId(null);
    }
  }

  function onEdit(medication: MedicationRecord) {
    setInput(inputFromMedication(medication));
    setShowForm(true);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  function onCancelEdit() {
    setInput(baseInput);
    setShowForm(false);
  }

  function toggleExpand(id: string) {
    setExpandedCardId((current) => (current === id ? null : id));
  }

  return (
    <div className="stack-lg">
      {/* Summary bar */}
      <section className="panel">
        <div className="med-summary-row">
          <div>
            <h1 className="page-title"><span aria-hidden="true">💊</span> התרופות שלי</h1>
            <p className="muted">{activeCount} פעילות מתוך {sorted.length}</p>
          </div>
          <button
            type="button"
            onClick={() => { setShowForm(!showForm); if (showForm) onCancelEdit(); }}
            className={showForm ? 'secondary' : undefined}
          >
            {showForm ? '✕ סגור' : '＋ חדשה'}
          </button>
        </div>
      </section>

      {quickTakeMedications.length > 0 ? (
        <section className="panel">
          <div className="form-row form-row-end">
            <div className="form-group flex-1">
              <label htmlFor="quick-medication">נטילה מהירה (לפי צורך)</label>
              <select
                id="quick-medication"
                value={quickMedicationId}
                onChange={(event) => setQuickMedicationId(event.target.value)}
                disabled={Boolean(isTakingMedicationId)}
              >
                {quickTakeMedications.map((medication) => (
                  <option key={medication.id} value={medication.id}>
                    {medication.name} ({medication.doseText} {medication.doseUnit})
                  </option>
                ))}
              </select>
            </div>
            <button
              type="button"
              onClick={() => void onTakeDoseNow(quickMedicationId)}
              disabled={!quickMedicationId || Boolean(isTakingMedicationId)}
            >
              {isTakingMedicationId ? '⏳ רושם...' : '💊 הוסף נטילה עכשיו'}
            </button>
          </div>
        </section>
      ) : null}

      {/* Add/Edit Form */}
      {showForm ? (
        <section className="panel form-panel">
          <h2 className="form-title">
            <span>{input.id ? '✏️' : '➕'}</span>
            {input.id ? 'עריכת תרופה' : 'תרופה חדשה'}
          </h2>

          <form onSubmit={onSubmit} className="stack">
            <div className="form-group">
              <label htmlFor="name">שם תרופה *</label>
              <input
                ref={nameInputRef}
                id="name"
                value={input.name}
                onChange={(event) => setInput((current) => ({ ...current, name: event.target.value }))}
                placeholder="לדוגמה: אומפרזול"
                required
                autoComplete="off"
              />
            </div>

            <div className="form-row">
              <div className="form-group">
                <label htmlFor="doseText">מינון</label>
                <input
                  id="doseText"
                  value={input.doseText}
                  onChange={(event) => setInput((current) => ({ ...current, doseText: event.target.value }))}
                />
              </div>
              <div className="form-group">
                <label htmlFor="doseUnit">יחידה</label>
                <input
                  id="doseUnit"
                  value={input.doseUnit}
                  onChange={(event) => setInput((current) => ({ ...current, doseUnit: event.target.value }))}
                />
              </div>
            </div>

            <div className="form-row">
              <div className="form-group">
                <label htmlFor="scheduleMode">מצב תזמון</label>
                <select
                  id="scheduleMode"
                  value={input.scheduleMode}
                  onChange={(event) => onScheduleModeChange(event.target.value as MedicationInput['scheduleMode'])}
                  disabled={asNeededInput}
                >
                  <option value="FIXED_TIMES">שעות קבועות</option>
                  <option value="EVERY_X_HOURS">כל X שעות</option>
                  <option value="EVERY_X_DAYS">כל X ימים</option>
                </select>
              </div>
              {input.scheduleMode !== 'FIXED_TIMES' ? (
                <div className="form-group">
                  <label htmlFor="intervalHours">
                    {input.scheduleMode === 'EVERY_X_HOURS' ? 'כל כמה שעות לקחת?' : 'כל כמה ימים לקחת?'}
                  </label>
                  <input
                    id="intervalHours"
                    type="number"
                    min={1}
                    required
                    value={input.intervalHours ?? ''}
                    placeholder="—"
                    onChange={(event) =>
                      setInput((current) => ({
                        ...current,
                        intervalHours: event.target.value ? Number(event.target.value) : null,
                      }))
                    }
                  />
                </div>
              ) : null}
            </div>

            <label className="toggle-row">
              <input
                type="checkbox"
                checked={input.scheduleMode === 'FIXED_TIMES' && input.times.length === 0}
                onChange={(event) => {
                  if (event.target.checked) {
                    setInput((current) => ({
                      ...current,
                      scheduleMode: 'FIXED_TIMES',
                      intervalHours: null,
                      times: [],
                    }));
                    return;
                  }
                  setInput((current) => ({
                    ...current,
                    times: current.times.length > 0 ? current.times : ['08:00'],
                  }));
                }}
              />
              תרופה לפי צורך (ללא זמנים קבועים)
            </label>

            {isEveryXDaysInput ? (
              <>
                <p className="field-help">
                  בחרו את המרווח ואת תאריך הנטילה הראשון. התרופה תופיע בדף הבית רק בימי הנטילה, ותישאר שם כל היום.
                </p>
                <div className="form-row">
                  <div className="form-group">
                    <label htmlFor="startDate">מתי לקחת בפעם הראשונה?</label>
                    <input
                      id="startDate"
                      type="date"
                      value={input.startDate}
                      onChange={(event) => setInput((current) => ({ ...current, startDate: event.target.value }))}
                      required
                    />
                  </div>
                  <div className="form-group">
                    <label htmlFor="every-x-days-time">שעת נטילה ביום היעד</label>
                    <input
                      id="every-x-days-time"
                      type="time"
                      value={input.times[0] ?? ''}
                      onChange={(event) => onEveryXDaysTimeChange(event.target.value)}
                      required
                    />
                    <p className="field-help">השעה מגדירה את זמן היעד. ביום הנטילה התרופה תופיע בדף הבית כל היום.</p>
                  </div>
                </div>
              </>
            ) : (
              <>
                <div className="form-group">
                  <label htmlFor="times">שעות נטילה (מופרדות בפסיק)</label>
                  <input
                    id="times"
                    value={input.times.join(', ')}
                    onChange={(event) => onTimesChange(event.target.value)}
                    placeholder="08:00, 14:00, 20:00"
                    disabled={asNeededInput}
                  />
                </div>

                <fieldset className="weekday-fieldset">
                  <legend>ימי השבוע</legend>
                  <div className="weekday-grid">
                    {weekdayLabels.map((weekday) => (
                      <label key={weekday.day} className={input.weekdays.includes(weekday.day) ? 'weekday-active' : ''}>
                        <input
                          type="checkbox"
                          checked={input.weekdays.includes(weekday.day)}
                          onChange={() => onWeekdayToggle(weekday.day)}
                        />
                        {weekday.label}
                      </label>
                    ))}
                  </div>
                </fieldset>
              </>
            )}

            <div className="form-group">
              <label htmlFor="notes">הערות (אופציונלי)</label>
              <textarea
                id="notes"
                rows={2}
                value={input.notes}
                onChange={(event) => setInput((current) => ({ ...current, notes: event.target.value }))}
                placeholder="הערות נוספות..."
              />
            </div>

            <div className="button-row">
              <button type="submit" disabled={isSaving} data-testid="save-medication" className="btn-flex">
                {isSaving ? '⏳ שומר...' : '💾 שמירה'}
              </button>
              {input.id ? (
                <button type="button" className="secondary" onClick={onCancelEdit}>
                  ✕ ביטול
                </button>
              ) : null}
            </div>
          </form>
        </section>
      ) : null}

      {/* Medication Cards */}
      {sorted.length === 0 ? (
        <section className="panel">
          <div className="history-empty">
            <span className="empty-icon">💊</span>
            <h3>אין תרופות עדיין</h3>
            <p className="muted">לחצו על ״+חדשה״ כדי להוסיף תרופה חדשה</p>
            <button type="button" onClick={() => setShowForm(true)} className="mt-sm">
              ＋ הוספת תרופה ראשונה
            </button>
          </div>
        </section>
      ) : (
        <div className="stack">
          {sorted.map((medication) => {
            const isExpanded = expandedCardId === medication.id;
            const takenToday = takenTodayMap[medication.id] ?? 0;
            const lastTaken = getLastTakenInfo(events, medication.id);
            const wasJustTaken = justTakenId === medication.id;
            const asNeeded = isAsNeededMedication(medication);
            const expectedDoses = medication.times.length;

            return (
              <article
                key={medication.id}
                className={`med-card ${medication.isActive ? '' : 'inactive'} ${wasJustTaken ? 'just-taken' : ''}`}
                data-testid={`medication-card-${medication.id}`}
                data-med-id={medication.id}
              >
                {/* Top row: name + quick-dose button */}
                <div className="med-card-top">
                  <button
                    type="button"
                    className="med-card-info-button"
                    onClick={() => toggleExpand(medication.id)}
                    aria-expanded={isExpanded}
                    aria-controls={`med-actions-${medication.id}`}
                  >
                    <h3>
                      {medication.name}
                      {!medication.isActive ? <span className="inactive-label"> (מושבת)</span> : null}
                    </h3>
                    <p className="muted med-dose-text">
                      {medication.doseText} {medication.doseUnit} · {medicationScheduleText(medication)}
                    </p>
                  </button>

                  {/* Big prominent dose button */}
                  {medication.isActive && asNeeded ? (
                    <button
                      type="button"
                      className={`quick-dose-btn ${wasJustTaken ? 'taken-flash' : ''}`}
                      onClick={() => void onTakeDoseNow(medication.id)}
                      aria-label={`רישום נטילה של ${medication.name}`}
                      disabled={Boolean(isTakingMedicationId)}
                    >
                      {wasJustTaken ? '✓' : '💊'}
                    </button>
                  ) : null}
                </div>

                {/* Stats row */}
                <div className="med-card-stats">
                  <span className={`stat-chip ${!asNeeded && expectedDoses > 0 && takenToday >= expectedDoses ? 'stat-done' : 'stat-pending'}`}>
                    {!asNeeded && expectedDoses > 0 && takenToday >= expectedDoses ? '✓' : '◔'} נטילות היום: {takenToday}{!asNeeded && medication.times.length > 0 ? `/${expectedDoses}` : ''}
                  </span>
                  {lastTaken ? (
                    <span className="stat-chip stat-time">⏱ {lastTaken}</span>
                  ) : null}
                  {!asNeeded && medication.times.length > 0 ? (
                    <span className="stat-chip">⏰ {medication.times.map((item) => item.localTime).join(', ')}</span>
                  ) : null}
                  {asNeeded ? (
                    <span className="stat-chip">🕘 לפי צורך</span>
                  ) : null}
                </div>

                {/* Expandable actions */}
                {isExpanded ? (
                  <div className="med-card-expanded" id={`med-actions-${medication.id}`}>
                    {medication.notes ? (
                      <p className="med-card-notes">📝 {medication.notes}</p>
                    ) : null}
                    <div className="med-card-actions">
                      <button type="button" className="secondary" onClick={() => onEdit(medication)}>
                        ✏️ עריכה
                      </button>
                      <button
                        type="button"
                        className={medication.isActive ? 'ghost' : 'secondary'}
                        onClick={() => onToggleActive(medication.id, !medication.isActive)}
                      >
                        {medication.isActive ? '⏸ השבתה' : '▶ שחזור'}
                      </button>
                    </div>
                  </div>
                ) : (
                  <button
                    type="button"
                    className="expand-hint"
                    onClick={() => toggleExpand(medication.id)}
                    aria-label="הצג פעולות נוספות"
                  >
                    ⋯ פעולות נוספות
                  </button>
                )}
              </article>
            );
          })}
        </div>
      )}

      {/* Deactivation confirmation dialog */}
      <Dialog isOpen={Boolean(confirmDeactivateId)} onClose={() => setConfirmDeactivateId(null)} title="⏸ השבתת תרופה">
        {confirmDeactivateId ? (
          <div className="stack">
            <p className="section-subtitle">
              האם להשבית את{' '}
              <strong className="text-strong">
                {medications.find((m) => m.id === confirmDeactivateId)?.name}
              </strong>
              ? תוכלו לשחזר אותה בכל עת.
            </p>
            <div className="button-row">
              <button type="button" className="secondary btn-flex" onClick={onConfirmDeactivate}>
                ⏸ כן, השבת
              </button>
              <button type="button" className="btn-flex" onClick={() => setConfirmDeactivateId(null)}>
                ביטול
              </button>
            </div>
          </div>
        ) : null}
      </Dialog>

    </div>
  );
}
