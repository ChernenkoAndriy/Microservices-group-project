import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

// WCAG 2.x contrast of the token pairs used for text, in day and night mode.
const css = readFileSync(resolve(__dirname, 'tokens.css'), 'utf8');

function tokens(block: string): Record<string, string> {
  const out: Record<string, string> = {};
  for (const m of block.matchAll(/--([a-z0-9-]+):\s*(#[0-9a-f]{6})/gi)) out[m[1]] = m[2].toLowerCase();
  return out;
}
const day = tokens(css.slice(css.indexOf(':root {'), css.lastIndexOf("[data-theme='night'] {")));
const night = { ...day, ...tokens(css.slice(css.lastIndexOf("[data-theme='night'] {"))) };

function luminance(hex: string): number {
  const [r, g, b] = [1, 3, 5].map((i) => {
    const c = parseInt(hex.slice(i, i + 2), 16) / 255;
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}
export function contrast(a: string, b: string): number {
  const [l1, l2] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (l1 + 0.05) / (l2 + 0.05);
}

// [foreground, background] pairs for normal-size text (4.5:1).
const SMALL_TEXT: Array<[string, string]> = [
  ['text', 'bg'],
  ['text', 'surface'],
  ['text-2-ink', 'bg'],
  ['text-2-ink', 'surface'],
  ['accent-ink', 'bg'],
  ['accent-ink', 'surface'],
  ['on-accent', 'purple'],
  ['on-accent', 'pink'],
  ['on-accent', 'cyan'],
  ['pink', 'deep'], // "NOW SUFFERING" label
];

describe.each([
  ['day', day],
  ['night', night],
])('%s mode', (_name, t) => {
  it.each(SMALL_TEXT)('%s on %s reaches 4.5:1', (fg, bg) => {
    expect(t[fg], fg).toBeDefined();
    expect(t[bg], bg).toBeDefined();
    expect(contrast(t[fg], t[bg])).toBeGreaterThanOrEqual(4.5);
  });
});

it('keeps the brief palette', () => {
  expect(day).toMatchObject({
    bg: '#fce4f2',
    surface: '#ffffff',
    purple: '#b967ff',
    cyan: '#01cdfe',
    pink: '#ff71ce',
    mint: '#05ffa1',
    text: '#3d2a5c',
    'text-2': '#8c7aa8',
  });
  expect(night).toMatchObject({ bg: '#1a1030', surface: '#2a1b4a', text: '#f5f0ff', 'text-2': '#b5a6d6', 'border-color': '#8f7bb8' });
});
