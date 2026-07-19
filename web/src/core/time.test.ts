import { afterEach, describe, expect, it, vi } from 'vitest';

import { toLocalDate, toLocalTime } from '@/core/time';

afterEach(() => {
  vi.restoreAllMocks();
});

describe('time helpers', () => {
  it('formats local date/time without relying on UTC ISO conversion', () => {
    const isoSpy = vi.spyOn(Date.prototype, 'toISOString');
    const epochMs = new Date(2026, 2, 29, 1, 2, 0, 0).getTime();

    expect(toLocalDate(epochMs)).toBe('2026-03-29');
    expect(toLocalTime(epochMs)).toBe('01:02');
    expect(isoSpy).not.toHaveBeenCalled();
  });
});
