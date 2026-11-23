import { useEffect, useState } from 'react';

type Theme = 'day' | 'night';
const KEY = 'spotty.theme';

function initialTheme(): Theme {
  try {
    const t = localStorage.getItem(KEY);
    if (t === 'day' || t === 'night') return t;
  } catch {
    /* ignore */
  }
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'night' : 'day';
}

export function ThemeToggle() {
  const [theme, setTheme] = useState<Theme>(initialTheme);
  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    try {
      localStorage.setItem(KEY, theme);
    } catch {
      /* ignore */
    }
  }, [theme]);
  const night = theme === 'night';
  return (
    <button
      type="button"
      className="btn btn--icon"
      onClick={() => setTheme(night ? 'day' : 'night')}
      aria-label={night ? 'Switch to day mode' : 'Switch to night mode'}
      aria-pressed={night}
      title={night ? 'Day mode (too bright)' : 'Night mode (for 3 a.m. regrets)'}
      data-testid="theme-toggle"
    >
      {night ? '☀' : '☾'}
    </button>
  );
}
