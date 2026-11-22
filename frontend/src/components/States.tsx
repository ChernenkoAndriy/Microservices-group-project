import type { ReactNode } from 'react';
import { ApiError } from '../api/http';

export function Loading({ text = 'Finding something worse…' }: { text?: string }) {
  return (
    <div className="state" role="status">
      {text}
      <span className="blink">_</span>
    </div>
  );
}

export function Empty({ children }: { children: ReactNode }) {
  return <div className="state">{children}</div>;
}

export function ErrorState({ error, onRetry }: { error?: unknown; onRetry?: () => void }) {
  const detail =
    error instanceof ApiError ? `${error.status}: ${error.message}` : error instanceof Error ? error.message : undefined;
  return (
    <div className="state state--error" role="alert">
      <strong>ERROR 404: TASTE NOT FOUND</strong>
      {detail && <p className="muted">{detail}</p>}
      {onRetry && (
        <button className="btn" onClick={onRetry}>
          Try again (why?)
        </button>
      )}
    </div>
  );
}
