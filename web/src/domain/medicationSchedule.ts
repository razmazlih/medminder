import type { MedicationRecord } from '@/domain/models';

function toDayIndex(localDate: string): number | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(localDate);
  if (!match) {
    return null;
  }

  const [, yearText, monthText, dayText] = match;
  const year = Number(yearText);
  const month = Number(monthText);
  const day = Number(dayText);

  if (!Number.isInteger(year) || !Number.isInteger(month) || !Number.isInteger(day)) {
    return null;
  }

  return Math.floor(Date.UTC(year, month - 1, day) / (24 * 60 * 60 * 1000));
}

export function isEveryXDaysMedication(medication: MedicationRecord): boolean {
  return medication.scheduleMode === 'EVERY_X_DAYS';
}

export function isMedicationWithinDateRange(medication: MedicationRecord, localDate: string): boolean {
  const targetDay = toDayIndex(localDate);
  const startDay = toDayIndex(medication.startDate);

  if (targetDay == null || startDay == null || targetDay < startDay) {
    return false;
  }

  if (!medication.endDate) {
    return true;
  }

  const endDay = toDayIndex(medication.endDate);
  return endDay != null && targetDay <= endDay;
}

export function isEveryXDaysMedicationDueOnDate(medication: MedicationRecord, localDate: string): boolean {
  if (!isEveryXDaysMedication(medication) || !isMedicationWithinDateRange(medication, localDate)) {
    return false;
  }

  const intervalDays = medication.intervalHours;
  if (intervalDays == null || intervalDays < 1) {
    return false;
  }

  const targetDay = toDayIndex(localDate);
  const startDay = toDayIndex(medication.startDate);
  if (targetDay == null || startDay == null) {
    return false;
  }

  return (targetDay - startDay) % intervalDays === 0;
}

export function shouldShowMedicationOnIntakesDate(medication: MedicationRecord, localDate: string): boolean {
  if (!medication.isActive || medication.deletedAtEpochMs != null) {
    return false;
  }

  if (!isMedicationWithinDateRange(medication, localDate)) {
    return false;
  }

  if (!isEveryXDaysMedication(medication)) {
    return true;
  }

  return isEveryXDaysMedicationDueOnDate(medication, localDate);
}
