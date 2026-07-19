import { base64ToBytes, bytesToBase64 } from '@/core/base64';

const encoder = new TextEncoder();
const decoder = new TextDecoder();

const HKDF_SALT = encoder.encode('medminder-sync-salt-v1');
const HKDF_INFO = encoder.encode('medminder-sync-data-key');
const WRAP_AAD = encoder.encode('medminder-wrap-data-key-v1');
const DATA_KEY_LENGTH_BYTES = 32;
const GCM_IV_SIZE_BYTES = 12;

export interface EncryptedPayload {
  iv: string;
  ciphertext: string;
}

function toBufferSource(bytes: Uint8Array): BufferSource {
  const normalized = bytes.byteOffset === 0 && bytes.byteLength === bytes.buffer.byteLength ? bytes : bytes.slice();
  return normalized as unknown as BufferSource;
}

export function generateSyncCode(length = 32): string {
  const alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  const random = crypto.getRandomValues(new Uint8Array(length));
  return Array.from(random)
    .map((value) => alphabet[value % alphabet.length])
    .join('');
}

export async function deriveDataKey(syncCode: string): Promise<Uint8Array> {
  const normalized = syncCode.trim();
  if (!normalized) {
    throw new Error('Sync code is required');
  }

  const ikm = await crypto.subtle.importKey('raw', toBufferSource(encoder.encode(normalized)), 'HKDF', false, ['deriveBits']);
  const bits = await crypto.subtle.deriveBits(
    {
      name: 'HKDF',
      hash: 'SHA-256',
      salt: toBufferSource(HKDF_SALT),
      info: toBufferSource(HKDF_INFO),
    },
    ikm,
    DATA_KEY_LENGTH_BYTES * 8
  );
  return new Uint8Array(bits);
}

async function importAesKey(rawKey: Uint8Array): Promise<CryptoKey> {
  return crypto.subtle.importKey('raw', toBufferSource(rawKey), 'AES-GCM', false, ['encrypt', 'decrypt']);
}

export async function encryptAesGcm(plaintext: Uint8Array, rawKey: Uint8Array, aad: string): Promise<EncryptedPayload> {
  const iv = crypto.getRandomValues(new Uint8Array(GCM_IV_SIZE_BYTES));
  const key = await importAesKey(rawKey);
  const ciphertext = await crypto.subtle.encrypt(
    {
      name: 'AES-GCM',
      iv: toBufferSource(iv),
      additionalData: toBufferSource(encoder.encode(aad)),
      tagLength: 128,
    },
    key,
    toBufferSource(plaintext)
  );

  return {
    iv: bytesToBase64(iv),
    ciphertext: bytesToBase64(new Uint8Array(ciphertext)),
  };
}

export async function decryptAesGcm(ciphertextBase64: string, ivBase64: string, rawKey: Uint8Array, aad: string): Promise<Uint8Array> {
  const key = await importAesKey(rawKey);
  const iv = base64ToBytes(ivBase64);
  const ciphertext = base64ToBytes(ciphertextBase64);
  const plaintext = await crypto.subtle.decrypt(
    {
      name: 'AES-GCM',
      iv: toBufferSource(iv),
      additionalData: toBufferSource(encoder.encode(aad)),
      tagLength: 128,
    },
    key,
    toBufferSource(ciphertext)
  );
  return new Uint8Array(plaintext);
}

export async function encryptPayloadJson(payloadJson: string, rawKey: Uint8Array, aad: string): Promise<EncryptedPayload> {
  return encryptAesGcm(encoder.encode(payloadJson), rawKey, aad);
}

export async function decryptPayloadJson(ciphertextBase64: string, ivBase64: string, rawKey: Uint8Array, aad: string): Promise<string> {
  const plaintext = await decryptAesGcm(ciphertextBase64, ivBase64, rawKey, aad);
  return decoder.decode(plaintext);
}

export function generateLocalWrapKeyBase64(): string {
  const keyBytes = crypto.getRandomValues(new Uint8Array(DATA_KEY_LENGTH_BYTES));
  return bytesToBase64(keyBytes);
}

export async function wrapDataKey(dataKey: Uint8Array, wrapKeyBase64: string): Promise<string> {
  const wrapKeyBytes = base64ToBytes(wrapKeyBase64);
  const wrapKey = await importAesKey(wrapKeyBytes);
  const iv = crypto.getRandomValues(new Uint8Array(GCM_IV_SIZE_BYTES));
  const wrapped = await crypto.subtle.encrypt(
    {
      name: 'AES-GCM',
      iv: toBufferSource(iv),
      additionalData: toBufferSource(WRAP_AAD),
      tagLength: 128,
    },
    wrapKey,
    toBufferSource(dataKey)
  );
  return JSON.stringify({
    iv: bytesToBase64(iv),
    data: bytesToBase64(new Uint8Array(wrapped)),
  });
}

export async function unwrapDataKey(wrappedKeyJson: string, wrapKeyBase64: string): Promise<Uint8Array> {
  const wrapped = JSON.parse(wrappedKeyJson) as { iv: string; data: string };
  const wrapKeyBytes = base64ToBytes(wrapKeyBase64);
  const wrapKey = await importAesKey(wrapKeyBytes);
  const iv = base64ToBytes(wrapped.iv);
  const encryptedData = base64ToBytes(wrapped.data);
  const plaintext = await crypto.subtle.decrypt(
    {
      name: 'AES-GCM',
      iv: toBufferSource(iv),
      additionalData: toBufferSource(WRAP_AAD),
      tagLength: 128,
    },
    wrapKey,
    toBufferSource(encryptedData)
  );
  return new Uint8Array(plaintext);
}
