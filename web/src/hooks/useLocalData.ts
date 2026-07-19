import { useLiveQuery } from 'dexie-react-hooks';

import { getActiveProfileId } from '@/core/profile';
import { db } from '@/db/appDb';
import { defaultSettings } from '@/data/localStore';
import type { CloudDeletionNotice, DoseEventRecord, HiddenIntakeLogRecord, MedicationRecord } from '@/domain/models';

async function safeOrderByDesc<T>(
  readOrdered: () => PromiseLike<T[]>,
  readFallback: () => PromiseLike<T[]>,
  sortValue: (item: T) => number
): Promise<T[]> {
  try {
    return await readOrdered();
  } catch {
    const items = await readFallback();
    return [...items].sort((a, b) => sortValue(b) - sortValue(a));
  }
}

export function useAppSettings() {
  const settings = useLiveQuery(() => db.settings.get('singleton'), [], defaultSettings);
  return settings ?? defaultSettings;
}

export function useMedications(): MedicationRecord[] {
  const medications = useLiveQuery(
    async () => {
      const profileId = await getActiveProfileId();
      const items = await safeOrderByDesc(
        () => db.medications.orderBy('updatedAtEpochMs').reverse().toArray(),
        () => db.medications.toArray(),
        (item) => item.updatedAtEpochMs
      );
      return items.filter((item) => item.ownerProfileId === profileId);
    },
    [],
    []
  );
  return medications ?? [];
}

export function useDoseEvents(): DoseEventRecord[] {
  const events = useLiveQuery(
    async () => {
      const profileId = await getActiveProfileId();
      const items = await safeOrderByDesc(
        () => db.doseEvents.orderBy('updatedAtEpochMs').reverse().toArray(),
        () => db.doseEvents.toArray(),
        (item) => item.updatedAtEpochMs
      );
      return items.filter((item) => item.ownerProfileId === profileId && item.deletedAtEpochMs == null);
    },
    [],
    []
  );
  return events ?? [];
}

export function useHiddenIntakeLogs(): HiddenIntakeLogRecord[] {
  const logs = useLiveQuery(
    async () => {
      const profileId = await getActiveProfileId();
      const items = await safeOrderByDesc(
        () => db.hiddenIntakeLogs.orderBy('createdAtEpochMs').reverse().toArray(),
        () => db.hiddenIntakeLogs.toArray(),
        (item) => item.createdAtEpochMs
      );
      return items.filter((item) => item.ownerProfileId === profileId);
    },
    [],
    []
  );
  return logs ?? [];
}

export function useCloudDeletionNotices(): CloudDeletionNotice[] {
  const notices = useLiveQuery(
    async () => [],
    [],
    []
  );
  return notices ?? [];
}
