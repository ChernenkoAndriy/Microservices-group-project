/** 214 -> "3:34"; unknown -> "--:--". */
export function formatTime(seconds: number | undefined | null): string {
  if (seconds == null || !Number.isFinite(seconds) || seconds < 0) return '--:--';
  const s = Math.floor(seconds);
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const sec = String(s % 60).padStart(2, '0');
  return h > 0 ? `${h}:${String(m).padStart(2, '0')}:${sec}` : `${m}:${sec}`;
}

const PASTELS = [
  ['#ff71ce', '#b967ff'],
  ['#01cdfe', '#b967ff'],
  ['#05ffa1', '#01cdfe'],
  ['#fff27a', '#ff71ce'],
  ['#ffb3e6', '#a5f3ff'],
  ['#c9b6ff', '#ff9ad5'],
  ['#9effd6', '#ffd1f0'],
  ['#ffcf9e', '#b967ff'],
];

/** A stable pastel gradient for things without a cover image. */
export function pastelGradient(seed: number | string): string {
  const n = typeof seed === 'number' ? seed : [...seed].reduce((a, c) => (a * 31 + c.charCodeAt(0)) >>> 0, 7);
  const [a, b] = PASTELS[Math.abs(n) % PASTELS.length];
  const angle = (Math.abs(n) * 47) % 360;
  return `linear-gradient(${angle}deg, ${a}, ${b})`;
}

export function initials(name: string | undefined): string {
  if (!name) return '?';
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((w) => w[0]!.toUpperCase())
    .join('');
}
