import { signInAnonymously, signOut } from 'firebase/auth';
import {
  collection,
  doc,
  getDocs,
  limit,
  query,
  setDoc,
} from 'firebase/firestore';
import { httpsCallable } from 'firebase/functions';

import type { CloudRecordDocument, CloudRecordEnvelope } from '@/domain/models';
import { cloudRecordDocumentSchema } from '@/domain/schemas';
import { getFirebaseServices } from '@/firebase/client';
import type { CloudAdapter, JoinDatasetResult } from '@/sync/cloudAdapter';

const AUTH_TIMEOUT_MS = 15_000;
const JOIN_TIMEOUT_MS = 20_000;
const LOGOUT_TIMEOUT_MS = 12_000;
const FIRESTORE_TIMEOUT_MS = 20_000;
const PULL_LIMIT = 1000;

function pick(raw: Record<string, unknown>, ...keys: string[]): unknown {
  for (const key of keys) {
    if (raw[key] !== undefined) {
      return raw[key];
    }
  }
  return undefined;
}

function toNumberOrNull(value: unknown): number | null {
  if (value == null || value === '') {
    return null;
  }
  const numeric = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(numeric) ? numeric : null;
}

function normalizeRecordType(value: unknown): CloudRecordEnvelope['recordType'] | null {
  if (value === 'MEDICATION' || value === 'DOSE_EVENT' || value === 'HIDDEN_INTAKE_LOG') {
    return value;
  }
  const normalized = typeof value === 'string' ? value.trim().toUpperCase() : '';
  if (normalized === 'MEDICATION') return 'MEDICATION';
  if (normalized === 'DOSE_EVENT') return 'DOSE_EVENT';
  if (normalized === 'HIDDEN_INTAKE_LOG') return 'HIDDEN_INTAKE_LOG';
  return null;
}

function normalizeEnvelope(raw: Record<string, unknown>): CloudRecordEnvelope | null {
  const payload = (pick(raw, 'payload', 'encryptedPayload') ?? null) as Record<string, unknown> | null;
  const recordType = normalizeRecordType(pick(raw, 'recordType', 'record_type', 'type'));
  const recordId = pick(raw, 'recordId', 'record_id', 'id');
  const updatedAt = toNumberOrNull(
    pick(raw, 'updatedAt', 'updated_at', 'updatedAtEpochMs', 'updated_at_epoch_ms')
  );
  const deletedAt = toNumberOrNull(
    pick(raw, 'deletedAt', 'deleted_at', 'deletedAtEpochMs', 'deleted_at_epoch_ms')
  );
  const sourceRaw = pick(raw, 'source');
  const source = sourceRaw === 'mobile' || sourceRaw === 'web' ? sourceRaw : null;
  const iv = pick(raw, 'iv', 'aesIv', 'aes_iv') ?? payload?.iv ?? payload?.aesIv ?? null;
  const ciphertext = pick(raw, 'ciphertext', 'data') ?? payload?.ciphertext ?? payload?.data ?? null;

  if (!recordType || typeof recordId !== 'string' || recordId.length === 0 || updatedAt == null) {
    return null;
  }

  return {
    recordType,
    recordId,
    updatedAt,
    deletedAt: deletedAt ?? null,
    source,
    iv: typeof iv === 'string' ? iv : null,
    ciphertext: typeof ciphertext === 'string' ? ciphertext : null,
  };
}

async function withTimeout<T>(promise: Promise<T>, timeoutMs: number, label: string): Promise<T> {
  let timeoutId: ReturnType<typeof setTimeout> | null = null;
  const timeoutPromise = new Promise<never>((_, reject) => {
    timeoutId = setTimeout(() => {
      reject(new Error(`${label} timed out after ${timeoutMs}ms`));
    }, timeoutMs);
  });

  try {
    return await Promise.race([promise, timeoutPromise]);
  } finally {
    if (timeoutId) {
      clearTimeout(timeoutId);
    }
  }
}

export class FirebaseCloudAdapter implements CloudAdapter {
  async ensureAnonymousUser(): Promise<string> {
    const { auth } = getFirebaseServices();
    if (auth.currentUser) {
      return auth.currentUser.uid;
    }

    const result = await withTimeout(signInAnonymously(auth), AUTH_TIMEOUT_MS, 'Anonymous sign-in');
    const uid = result.user?.uid;
    if (!uid) {
      throw new Error('Anonymous auth failed');
    }
    return uid;
  }

  async loginWebWithSyncCode(syncCode: string): Promise<JoinDatasetResult> {
    const { functions } = getFirebaseServices();
    const callable = httpsCallable<{ syncCode: string }, { datasetId: string }>(
      functions,
      'loginWebWithSyncCode',
      { timeout: JOIN_TIMEOUT_MS }
    );

    const response = await withTimeout(
      callable({
        syncCode,
      }),
      JOIN_TIMEOUT_MS + 2_000,
      'loginWebWithSyncCode'
    );

    const datasetId = response.data?.datasetId;
    if (!datasetId) {
      throw new Error('loginWebWithSyncCode response missing datasetId');
    }

    return { datasetId };
  }

  async logoutWebSession(datasetId: string): Promise<void> {
    const { functions } = getFirebaseServices();
    const callable = httpsCallable<{ datasetId: string }, { ok: boolean }>(
      functions,
      'logoutWebSession',
      { timeout: LOGOUT_TIMEOUT_MS }
    );
    await withTimeout(callable({ datasetId }), LOGOUT_TIMEOUT_MS + 2_000, 'logoutWebSession');
  }

  async signOut(): Promise<void> {
    const { auth } = getFirebaseServices();
    await withTimeout(signOut(auth), AUTH_TIMEOUT_MS, 'signOut');
  }

  async pullRecords(datasetId: string, sinceEpochMs: number | null): Promise<CloudRecordEnvelope[]> {
    const { firestore } = getFirebaseServices();
    const recordsRef = collection(firestore, 'datasets_v2', datasetId, 'records');
    const q = query(recordsRef, limit(PULL_LIMIT));

    const snapshot = await withTimeout(getDocs(q), FIRESTORE_TIMEOUT_MS, 'pullRecords');

    return snapshot.docs
      .map((document) => normalizeEnvelope(document.data() as Record<string, unknown>))
      .filter((item): item is CloudRecordEnvelope => item != null)
      .filter((item) => (sinceEpochMs == null ? true : item.updatedAt > sinceEpochMs))
      .sort((a, b) => a.updatedAt - b.updatedAt);
  }

  async pushRecord(datasetId: string, documentId: string, record: CloudRecordDocument): Promise<void> {
    const { firestore } = getFirebaseServices();
    const ref = doc(firestore, 'datasets_v2', datasetId, 'records', documentId);
    const validated = cloudRecordDocumentSchema.parse(record);
    await withTimeout(setDoc(ref, validated, { merge: true }), FIRESTORE_TIMEOUT_MS, 'pushRecord');
  }
}
