export function nowEpochMs(): number {
  return Date.now();
}

export function toLocalDate(epochMs: number): string {
  const date = new Date(epochMs);
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export function toLocalTime(epochMs: number): string {
  const date = new Date(epochMs);
  const hours = String(date.getHours()).padStart(2, '0');
  const minutes = String(date.getMinutes()).padStart(2, '0');
  return `${hours}:${minutes}`;
}

export function formatDateTime(epochMs: number): string {
  return new Intl.DateTimeFormat('he-IL', {
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(new Date(epochMs));
}

export function formatDate(epochMs: number): string {
  return new Intl.DateTimeFormat('he-IL', {
    dateStyle: 'medium',
  }).format(new Date(epochMs));
}

export function formatTime(epochMs: number): string {
  return new Intl.DateTimeFormat('he-IL', {
    timeStyle: 'short',
  }).format(new Date(epochMs));
}
