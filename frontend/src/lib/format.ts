const MINUTE = 60_000;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;

/** "just now" / "12 min ago" / "3 h ago" / "2 d ago" / absolute date beyond a week. */
export function formatRelativeDate(iso: string, now: Date = new Date()): string {
  const timestamp = Date.parse(iso);
  if (Number.isNaN(timestamp)) {
    return 'unknown date';
  }
  const diff = now.getTime() - timestamp;
  if (diff < 0) {
    return 'just now';
  }
  if (diff < MINUTE) {
    return 'just now';
  }
  if (diff < HOUR) {
    return `${String(Math.floor(diff / MINUTE))} min ago`;
  }
  if (diff < DAY) {
    return `${String(Math.floor(diff / HOUR))} h ago`;
  }
  if (diff < 7 * DAY) {
    return `${String(Math.floor(diff / DAY))} d ago`;
  }
  return new Date(timestamp).toLocaleDateString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  });
}

export function formatBytes(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes < 0) {
    return '—';
  }
  if (bytes < 1024) {
    return `${String(bytes)} B`;
  }
  const kb = bytes / 1024;
  if (kb < 1024) {
    return `${kb.toFixed(0)} KB`;
  }
  return `${(kb / 1024).toFixed(1)} MB`;
}

export function pluralise(count: number, singular: string, plural = `${singular}s`): string {
  return `${String(count)} ${count === 1 ? singular : plural}`;
}

export function truncate(text: string, max: number): string {
  const clean = text.replace(/\s+/g, ' ').trim();
  return clean.length <= max ? clean : `${clean.slice(0, max).trimEnd()}…`;
}
