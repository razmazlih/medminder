import type { CloudRecordDocument, CloudRecordEnvelope } from '@/domain/models';
import { isFakeCloudEnabled } from '@/firebase/config';
import { FakeCloudAdapter } from '@/sync/fakeCloudAdapter';
import { FirebaseCloudAdapter } from '@/sync/firebaseCloudAdapter';

export interface JoinDatasetResult {
  datasetId: string;
}

export interface CloudAdapter {
  ensureAnonymousUser(): Promise<string>;
  loginWebWithSyncCode(syncCode: string): Promise<JoinDatasetResult>;
  logoutWebSession(datasetId: string): Promise<void>;
  signOut(): Promise<void>;
  pullRecords(datasetId: string, sinceEpochMs: number | null): Promise<CloudRecordEnvelope[]>;
  pushRecord(datasetId: string, documentId: string, record: CloudRecordDocument): Promise<void>;
}

let cloudAdapterSingleton: CloudAdapter | null = null;

export function getCloudAdapter(): CloudAdapter {
  if (cloudAdapterSingleton) {
    return cloudAdapterSingleton;
  }

  cloudAdapterSingleton = isFakeCloudEnabled()
    ? new FakeCloudAdapter()
    : new FirebaseCloudAdapter();

  return cloudAdapterSingleton;
}
