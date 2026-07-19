import { describe, expect, it } from 'vitest';

import { shouldApplyRemote } from '@/core/syncConflict';

describe('Sync conflict resolver', () => {
  it('applies remote when remote timestamp is newer', () => {
    expect(shouldApplyRemote(100, 101)).toBe(true);
  });

  it('does not apply remote when remote is older or equal', () => {
    expect(shouldApplyRemote(100, 100)).toBe(false);
    expect(shouldApplyRemote(100, 99)).toBe(false);
  });

  it('applies remote when local timestamp is missing', () => {
    expect(shouldApplyRemote(null, 1)).toBe(true);
    expect(shouldApplyRemote(undefined, 1)).toBe(true);
  });
});
