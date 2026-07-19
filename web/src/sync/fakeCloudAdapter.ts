import { sha256Hex } from '@/core/hash';
import type { CloudRecordDocument, CloudRecordEnvelope, SyncRecordType } from '@/domain/models';
import type { CloudAdapter, JoinDatasetResult } from '@/sync/cloudAdapter';

interface FakeDataset {
  ownerUid: string;
  status: 'ACTIVE' | 'REVOKED';
  webMembers: Set<string>;
  records: Map<string, CloudRecordDocument>;
}

interface FakeCloudDebugApi {
  issueAppSyncCode(syncCode: string): Promise<string>;
  seedTombstone(params: {
    datasetId: string;
    recordType: SyncRecordType;
    recordId: string;
    updatedAt: number;
    deletedAt: number;
  }): void;
  seedTombstoneForActive(params: {
    recordType: SyncRecordType;
    recordId: string;
    updatedAt: number;
    deletedAt: number;
  }): void;
  getActiveDatasetId(): string | null;
  reset(): void;
}

declare global {
  interface Window {
    __fakeCloudDebug?: FakeCloudDebugApi;
  }
}

const datasets = new Map<string, FakeDataset>();
const ownerActiveDataset = new Map<string, string>();
let nextWebUid = 1;

async function datasetIdFromSyncCode(syncCode: string): Promise<string> {
  return (await sha256Hex(`medminder|${syncCode.trim()}`)).slice(0, 40);
}

export class FakeCloudAdapter implements CloudAdapter {
  private uid: string | null = null;
  private activeDatasetId: string | null = null;

  constructor() {
    if (typeof window !== 'undefined') {
      window.__fakeCloudDebug = {
        issueAppSyncCode: async (syncCode: string) => this.issueAppSyncCode(syncCode),
        seedTombstone: ({ datasetId, recordType, recordId, updatedAt, deletedAt }) => {
          const dataset = datasets.get(datasetId);
          if (!dataset) {
            throw new Error('dataset not found');
          }
          dataset.records.set(`${recordType}_${recordId}`, {
            recordType,
            recordId,
            updatedAt,
            deletedAt,
            source: 'web',
            schemaVersion: 1,
            iv: null,
            ciphertext: null,
          });
        },
        seedTombstoneForActive: ({ recordType, recordId, updatedAt, deletedAt }) => {
          const datasetId = this.activeDatasetId;
          if (!datasetId) {
            throw new Error('active dataset not found');
          }
          const dataset = datasets.get(datasetId);
          if (!dataset) {
            throw new Error('dataset not found');
          }
          dataset.records.set(`${recordType}_${recordId}`, {
            recordType,
            recordId,
            updatedAt,
            deletedAt,
            source: 'web',
            schemaVersion: 1,
            iv: null,
            ciphertext: null,
          });
        },
        getActiveDatasetId: () => this.activeDatasetId,
        reset: () => {
          datasets.clear();
          ownerActiveDataset.clear();
          this.uid = null;
          this.activeDatasetId = null;
          nextWebUid = 1;
        },
      };
    }
  }

  private ensureUid(): string {
    if (!this.uid) {
      this.uid = `fake-web-${nextWebUid++}`;
    }
    return this.uid;
  }

  async ensureAnonymousUser(): Promise<string> {
    return this.ensureUid();
  }

  async issueAppSyncCode(syncCode: string, ownerUid = 'fake-app-owner'): Promise<string> {
    const datasetId = await datasetIdFromSyncCode(syncCode);
    const previousDatasetId = ownerActiveDataset.get(ownerUid);

    let dataset = datasets.get(datasetId);
    if (!dataset) {
      dataset = {
        ownerUid,
        status: 'ACTIVE',
        webMembers: new Set<string>(),
        records: new Map<string, CloudRecordDocument>(),
      };
      datasets.set(datasetId, dataset);
    } else if (dataset.ownerUid !== ownerUid) {
      throw new Error('dataset belongs to a different owner');
    } else {
      dataset.status = 'ACTIVE';
    }

    if (previousDatasetId && previousDatasetId !== datasetId) {
      datasets.delete(previousDatasetId);
    }

    ownerActiveDataset.set(ownerUid, datasetId);
    return datasetId;
  }

  async loginWebWithSyncCode(syncCode: string): Promise<JoinDatasetResult> {
    const datasetId = await datasetIdFromSyncCode(syncCode);
    const dataset = datasets.get(datasetId);
    if (!dataset) {
      throw new Error('dataset not found');
    }
    if (dataset.status !== 'ACTIVE') {
      throw new Error('dataset is not active');
    }

    const uid = this.ensureUid();
    dataset.webMembers.add(uid);
    this.activeDatasetId = datasetId;
    return { datasetId };
  }

  async logoutWebSession(datasetId: string): Promise<void> {
    const uid = this.ensureUid();
    const dataset = datasets.get(datasetId);
    dataset?.webMembers.delete(uid);
    if (this.activeDatasetId === datasetId) {
      this.activeDatasetId = null;
    }
  }

  async signOut(): Promise<void> {
    this.uid = null;
    this.activeDatasetId = null;
  }

  async pullRecords(datasetId: string, sinceEpochMs: number | null): Promise<CloudRecordEnvelope[]> {
    const dataset = datasets.get(datasetId);
    if (!dataset) {
      throw new Error('dataset not found');
    }
    const uid = this.ensureUid();
    if (!dataset.webMembers.has(uid)) {
      throw new Error('permission-denied');
    }

    return Array.from(dataset.records.values())
      .filter((record) => (sinceEpochMs == null ? true : record.updatedAt > sinceEpochMs))
      .sort((a, b) => a.updatedAt - b.updatedAt)
      .map((record) => ({
        recordType: record.recordType,
        recordId: record.recordId,
        updatedAt: record.updatedAt,
        deletedAt: record.deletedAt,
        source: record.source,
        iv: record.iv,
        ciphertext: record.ciphertext,
      }));
  }

  async pushRecord(datasetId: string, documentId: string, record: CloudRecordDocument): Promise<void> {
    const dataset = datasets.get(datasetId);
    if (!dataset) {
      throw new Error('dataset not found');
    }
    const uid = this.ensureUid();
    if (!dataset.webMembers.has(uid)) {
      throw new Error('permission-denied');
    }
    dataset.records.set(documentId, record);
  }
}
