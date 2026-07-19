import { describe, expect, it } from 'vitest';

import { decryptAesGcm, deriveDataKey, encryptAesGcm } from '@/core/crypto';

describe('Sync crypto primitives', () => {
  it('derives deterministic 32-byte key from sync code', async () => {
    const keyA = await deriveDataKey('SYNC-CODE-12345');
    const keyB = await deriveDataKey('SYNC-CODE-12345');
    const keyC = await deriveDataKey('SYNC-CODE-ABCDE');

    expect(keyA).toHaveLength(32);
    expect(Array.from(keyA)).toEqual(Array.from(keyB));
    expect(Array.from(keyA)).not.toEqual(Array.from(keyC));
  });

  it('encrypt/decrypt roundtrip with aad', async () => {
    const key = await deriveDataKey('SYNC-CODE-12345');
    const plaintext = new TextEncoder().encode('{"name":"אקמול","dose":"1"}');
    const aad = 'dataset|MEDICATION|med-1|100';

    const encrypted = await encryptAesGcm(plaintext, key, aad);
    const decrypted = await decryptAesGcm(encrypted.ciphertext, encrypted.iv, key, aad);

    expect(new TextDecoder().decode(decrypted)).toEqual('{"name":"אקמול","dose":"1"}');
  });
});
