export function shouldApplyRemote(localUpdatedAtEpochMs: number | null | undefined, remoteUpdatedAtEpochMs: number): boolean {
  const local = localUpdatedAtEpochMs ?? Number.MIN_SAFE_INTEGER;
  return remoteUpdatedAtEpochMs > local;
}
