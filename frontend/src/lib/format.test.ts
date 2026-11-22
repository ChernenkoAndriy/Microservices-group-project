import { formatTime, initials, pastelGradient } from './format';

describe('formatTime', () => {
  it('formats minutes and hours', () => {
    expect(formatTime(0)).toBe('0:00');
    expect(formatTime(214)).toBe('3:34');
    expect(formatTime(3725)).toBe('1:02:05');
  });
  it('shows dashes for unknown values', () => {
    expect(formatTime(undefined)).toBe('--:--');
    expect(formatTime(Number.NaN)).toBe('--:--');
  });
});

describe('pastelGradient', () => {
  it('is stable per seed', () => {
    expect(pastelGradient(42)).toBe(pastelGradient(42));
    expect(pastelGradient('artist-1')).toMatch(/^linear-gradient\(/);
  });
});

describe('initials', () => {
  it('takes two initials', () => {
    expect(initials('Gary and the Mild Disappointments')).toBe('GA');
    expect(initials(undefined)).toBe('?');
  });
});
