import { useMemo, useState } from 'react';

import { toLocalDate } from '@/core/time';
import { createDoseEvent, markFixedDoseTaken } from '@/data/actions';
import { isEveryXDaysMedication, shouldShowMedicationOnIntakesDate } from '@/domain/medicationSchedule';
import type { MedicationRecord } from '@/domain/models';
import { useDoseEvents, useMedications } from '@/hooks/useLocalData';
import { useAppStore } from '@/state/useAppStore';

function isActiveMedication(medication: MedicationRecord): boolean {
  return medication.isActive && medication.deletedAtEpochMs == null;
}

function sortedByName(items: MedicationRecord[]): MedicationRecord[] {
  return [...items].sort((a, b) => a.name.localeCompare(b.name, 'he'));
}

type FixedMedicationByTime = {
  medication: MedicationRecord;
  localTime: string;
  sortOrder: number;
};

export function IntakesPage() {
  const medications = useMedications();
  const events = useDoseEvents();
  const { setMessage } = useAppStore();

  const [takingKey, setTakingKey] = useState<string | null>(null);

  const today = toLocalDate(Date.now());

  const activeMedications = useMemo(
    () => sortedByName(medications.filter(isActiveMedication)),
    [medications]
  );

  const activeEveryXDaysMedications = useMemo(
    () => activeMedications.filter(isEveryXDaysMedication),
    [activeMedications]
  );

  const dueEveryXDaysMedications = useMemo(
    () => activeEveryXDaysMedications.filter((medication) => shouldShowMedicationOnIntakesDate(medication, today)),
    [activeEveryXDaysMedications, today]
  );

  const fixedMedications = useMemo(
    () => activeMedications.filter((medication) => !isEveryXDaysMedication(medication) && medication.times.length > 0),
    [activeMedications]
  );

  const fixedMedicationsByTime = useMemo(() => {
    return fixedMedications
      .flatMap<FixedMedicationByTime>((medication) =>
        medication.times.map((item) => ({
          medication,
          localTime: item.localTime,
          sortOrder: item.sortOrder,
        }))
      )
      .sort((a, b) => {
        if (a.localTime !== b.localTime) return a.localTime.localeCompare(b.localTime, 'he');
        if (a.sortOrder !== b.sortOrder) return a.sortOrder - b.sortOrder;
        return a.medication.name.localeCompare(b.medication.name, 'he');
      });
  }, [fixedMedications]);

  const asNeededMedications = useMemo(
    () => activeMedications.filter((medication) => !isEveryXDaysMedication(medication) && medication.times.length === 0),
    [activeMedications]
  );

  const takenTodayCountByMedicationId = useMemo(() => {
    return events
      .filter((event) => event.status === 'TAKEN' && event.localDate === today)
      .reduce<Record<string, number>>((acc, event) => {
        acc[event.medicationId] = (acc[event.medicationId] ?? 0) + 1;
        return acc;
      }, {});
  }, [events, today]);

  const takenTodayByMedicationAndTime = useMemo(() => {
    return new Set(
      events
        .filter((event) => event.status === 'TAKEN' && event.localDate === today)
        .map((event) => `${event.medicationId}|${event.localTime}`)
    );
  }, [events, today]);

  async function onTakeFixedByTime(medicationId: string, scheduledTime: string) {
    const key = `fixed:${medicationId}:${scheduledTime}`;
    if (takingKey) return;
    setTakingKey(key);
    try {
      await markFixedDoseTaken({
        medicationId,
        localDate: toLocalDate(Date.now()),
        scheduledLocalTime: scheduledTime,
      });
      setMessage('✅ נרשמה נטילה לפי שעה');
    } catch {
      setMessage('⚠️ לא הצלחנו לרשום נטילה');
    } finally {
      setTakingKey(null);
    }
  }

  async function onTakeAsNeededNow(medicationId: string) {
    const key = `as-needed:${medicationId}`;
    if (takingKey) return;
    setTakingKey(key);
    try {
      await createDoseEvent({ medicationId, status: 'TAKEN' });
      setMessage('✅ נרשמה נטילה לפי צורך');
    } catch {
      setMessage('⚠️ לא הצלחנו לרשום נטילה');
    } finally {
      setTakingKey(null);
    }
  }

  async function onTakeEveryXDaysNow(medicationId: string) {
    const key = `every-x-days:${medicationId}`;
    if (takingKey) return;
    setTakingKey(key);
    try {
      await createDoseEvent({ medicationId, status: 'TAKEN' });
      setMessage('✅ נרשמה נטילה להיום');
    } catch {
      setMessage('⚠️ לא הצלחנו לרשום נטילה');
    } finally {
      setTakingKey(null);
    }
  }

  return (
    <div className="stack-lg">
      <section className="panel">
        <h1 className="page-title">
          <span aria-hidden="true">🧾</span> נטילות
        </h1>
        <p className="section-subtitle">רישום ברור ומהיר: תרופות של כל כמה ימים, תרופות קבועות לפי שעות, ותרופות לפי צורך בלחיצה אחת.</p>
      </section>

      {activeEveryXDaysMedications.length > 0 ? (
        <section className="panel">
          <h2 className="section-title">📅 תרופות של כל כמה ימים</h2>
          <p className="section-subtitle">
            התרופות האלו מופיעות כאן רק ביום הנטילה שלהן, ונשארות זמינות לאורך כל היום.
          </p>

          {dueEveryXDaysMedications.length === 0 ? (
            <p className="muted">אין תרופות של כל כמה ימים שצריך לקחת היום.</p>
          ) : (
            <div className="stack">
              {dueEveryXDaysMedications.map((medication) => {
                const takenToday = takenTodayCountByMedicationId[medication.id] ?? 0;
                const takenTodayAlready = takenToday > 0;
                const isLoading = takingKey === `every-x-days:${medication.id}`;
                const usualTime = medication.times[0]?.localTime ?? null;

                return (
                  <article key={medication.id} className="med-card">
                    <div className="med-card-top">
                      <div className="med-card-info">
                        <h3>{medication.name}</h3>
                        <p className="muted med-dose-text">
                          {medication.doseText} {medication.doseUnit}
                        </p>
                      </div>
                      <span className={`stat-chip ${takenTodayAlready ? 'stat-done' : 'stat-pending'}`}>
                        {takenTodayAlready ? '✓ נלקחה היום' : 'היום הוא יום נטילה'}
                      </span>
                    </div>

                    <div className="med-card-stats">
                      <span className="stat-chip">📆 כל {medication.intervalHours ?? '—'} ימים</span>
                      <span className="stat-chip">🗓️ החל מ-{medication.startDate}</span>
                      <span className="stat-chip">{usualTime ? `⏰ שעת יעד: ${usualTime}` : '🕘 זמין כל היום'}</span>
                    </div>

                    <button
                      type="button"
                      className={takenTodayAlready ? 'secondary' : undefined}
                      onClick={() => void onTakeEveryXDaysNow(medication.id)}
                      disabled={Boolean(takingKey) || takenTodayAlready}
                    >
                      {isLoading ? '⏳ רושם...' : takenTodayAlready ? '✓ נרשמה להיום' : '💊 נטילה היום'}
                    </button>
                  </article>
                );
              })}
            </div>
          )}
        </section>
      ) : null}

      <section className="panel">
        <h2 className="section-title">⏰ תרופות קבועות לפי זמנים</h2>
        <p className="section-subtitle">
          לכל תרופה אפשר לרשום נטילה ישירות על השעה הרלוונטית של היום.
        </p>

        {fixedMedicationsByTime.length === 0 ? (
          <p className="muted">אין כרגע תרופות קבועות פעילות.</p>
        ) : (
          <div className="stack">
            {fixedMedicationsByTime.map(({ medication, localTime }, index) => {
              const takenToday = takenTodayCountByMedicationId[medication.id] ?? 0;
              const expectedToday = medication.times.length;
              const key = `${medication.id}|${localTime}`;
              const isTakenForTime = takenTodayByMedicationAndTime.has(key);
              const isLoading = takingKey === `fixed:${medication.id}:${localTime}`;

              return (
                <article key={`${key}:${index}`} className="med-card">
                  <div className="med-card-top">
                    <div className="med-card-info">
                      <h3>{medication.name}</h3>
                      <p className="muted med-dose-text">
                        {medication.doseText} {medication.doseUnit}
                      </p>
                    </div>
                    <span className={`stat-chip ${takenToday >= expectedToday ? 'stat-done' : 'stat-pending'}`}>
                      נטילות היום: {takenToday}/{expectedToday}
                    </span>
                  </div>

                  <button
                    type="button"
                    className={isTakenForTime ? 'secondary' : undefined}
                    onClick={() => void onTakeFixedByTime(medication.id, localTime)}
                    disabled={Boolean(takingKey) || isTakenForTime}
                  >
                    {isLoading ? '⏳' : isTakenForTime ? '✓' : '💊'} {localTime}
                  </button>
                </article>
              );
            })}
          </div>
        )}
      </section>

      <section className="panel">
        <h2 className="section-title">🕘 תרופות לפי צורך</h2>
        <p className="section-subtitle">
          תרופות ללא שעות קבועות - רישום מיידי לפי הצורך.
        </p>

        {asNeededMedications.length === 0 ? (
          <p className="muted">אין כרגע תרופות לפי צורך פעילות.</p>
        ) : (
          <div className="stack">
            {asNeededMedications.map((medication) => {
              const isLoading = takingKey === `as-needed:${medication.id}`;
              return (
                <article key={medication.id} className="med-card">
                  <div className="med-card-top">
                    <div className="med-card-info">
                      <h3>{medication.name}</h3>
                      <p className="muted med-dose-text">
                        {medication.doseText} {medication.doseUnit}
                      </p>
                    </div>
                    <button
                      type="button"
                      onClick={() => void onTakeAsNeededNow(medication.id)}
                      disabled={Boolean(takingKey)}
                    >
                      {isLoading ? '⏳ רושם...' : '💊 נטילה עכשיו'}
                    </button>
                  </div>
                </article>
              );
            })}
          </div>
        )}
      </section>
    </div>
  );
}
