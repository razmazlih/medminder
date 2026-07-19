import { toLocalDate, toLocalTime } from '@/core/time';
import type { DoseEventRecord } from '@/domain/models';

export function doseEventEffectiveEpochMs(event: DoseEventRecord): number {
  return event.respondedEpochMs ?? event.scheduledEpochMs;
}

export function doseEventEffectiveLocalDate(event: DoseEventRecord): string {
  return toLocalDate(doseEventEffectiveEpochMs(event));
}

export function doseEventEffectiveLocalTime(event: DoseEventRecord): string {
  return event.respondedEpochMs != null ? toLocalTime(event.respondedEpochMs) : event.localTime;
}
