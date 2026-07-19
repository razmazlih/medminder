import type { CloudDeletionNotice, HiddenIntakeLogRecord, SyncRecordType } from '@/domain/models';
import { formatDateTime } from '@/core/time';

function recordTypeLabel(recordType: SyncRecordType): string {
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

export function parseCloudDeletionNotice(log: HiddenIntakeLogRecord): CloudDeletionNotice | null {
  const details = log.operationDetails;
  if (!details || !details.startsWith('cloud_deleted:')) {
    return null;
  }

  const parts = details.split(':');
  if (parts.length < 4) {
    return null;
  }

  const type = parts[1] as SyncRecordType;
  const recordId = parts[2];
  const deletedAtEpochMs = Number(parts[3]);
  if (!Number.isFinite(deletedAtEpochMs)) {
    return null;
  }

  return {
    id: log.id,
    recordType: type,
    recordTypeLabel: recordTypeLabel(type),
    recordId,
    medicationName: log.medicationName,
    deletedAtEpochMs,
    deletedAtLabel: formatDateTime(deletedAtEpochMs),
  };
}
