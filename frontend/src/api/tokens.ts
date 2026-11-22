// Access/refresh tokens, kept in localStorage so a reload keeps the session.
export interface Session {
  accessToken: string;
  refreshToken: string;
  /** epoch ms */
  expiresAt: number;
}

const KEY = 'spotty.session';
type Listener = (s: Session | null) => void;
const listeners = new Set<Listener>();

export function loadSession(): Session | null {
  try {
    const raw = localStorage.getItem(KEY);
    return raw ? (JSON.parse(raw) as Session) : null;
  } catch {
    return null;
  }
}

export function saveSession(s: Session | null): void {
  try {
    if (s) localStorage.setItem(KEY, JSON.stringify(s));
    else localStorage.removeItem(KEY);
  } catch {
    /* storage blocked: session lives in memory only */
  }
  memory = s;
  listeners.forEach((l) => l(s));
}

let memory: Session | null = loadSession();
export const currentSession = (): Session | null => memory;

export function onSessionChange(l: Listener): () => void {
  listeners.add(l);
  return () => listeners.delete(l);
}

/** Decodes the JWT payload (no verification: the gateway verifies). */
export function jwtClaims(token: string): Record<string, unknown> {
  try {
    const part = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    return JSON.parse(decodeURIComponent(escape(atob(part)))) as Record<string, unknown>;
  } catch {
    return {};
  }
}
