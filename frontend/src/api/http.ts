import { currentSession, saveSession, type Session } from './tokens';

/** A problem+json body from the backend, or a network failure. */
export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
    public readonly code?: string,
  ) {
    super(message);
  }
}

let refreshing: Promise<Session | null> | null = null;

/** Rotates the tokens once, even when several requests get 401 at the same time. */
export function refreshSession(): Promise<Session | null> {
  const s = currentSession();
  if (!s) return Promise.resolve(null);
  refreshing ??= fetch('/api/v1/auth/refresh', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken: s.refreshToken }),
  })
    .then(async (r) => {
      if (!r.ok) {
        saveSession(null);
        return null;
      }
      const t = (await r.json()) as { accessToken: string; refreshToken: string; expiresIn: number };
      const next = { accessToken: t.accessToken, refreshToken: t.refreshToken, expiresAt: Date.now() + t.expiresIn * 1000 };
      saveSession(next);
      return next;
    })
    .catch(() => null)
    .finally(() => {
      refreshing = null;
    });
  return refreshing;
}

const PUBLIC_AUTH = ['/api/v1/auth/login', '/api/v1/auth/register', '/api/v1/auth/refresh'];

/**
 * fetch used by every API client: adds the bearer token, refreshes it shortly before expiry, and on a 401 refreshes
 * once and repeats the request.
 */
export async function authFetch(input: Request): Promise<Response> {
  const isPublicAuth = PUBLIC_AUTH.some((p) => new URL(input.url, location.origin).pathname === p);
  let session = currentSession();
  if (session && !isPublicAuth && session.expiresAt - Date.now() < 15_000) {
    session = (await refreshSession()) ?? null;
  }
  const retry = input.clone();
  const first = await fetch(withToken(input, isPublicAuth ? null : session));
  if (first.status !== 401 || isPublicAuth || !session) return first;
  const renewed = await refreshSession();
  if (!renewed) return first;
  return fetch(withToken(retry, renewed));
}

function withToken(req: Request, s: Session | null): Request {
  if (!s) return req;
  const headers = new Headers(req.headers);
  headers.set('Authorization', `Bearer ${s.accessToken}`);
  return new Request(req, { headers });
}

/** Unwraps an openapi-fetch result: returns data or throws ApiError with the problem detail. */
export function unwrap<T>(r: { data?: T; error?: unknown; response: Response }): T {
  if (r.error !== undefined || !r.response.ok) {
    const e = (r.error ?? {}) as { detail?: string; title?: string; code?: string };
    throw new ApiError(r.response.status, e.detail ?? e.title ?? r.response.statusText ?? 'Request failed', e.code);
  }
  return r.data as T;
}
