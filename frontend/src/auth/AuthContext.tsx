import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { authApi, type UserProfile } from '../api/client';
import { unwrap } from '../api/http';
import { currentSession, jwtClaims, onSessionChange, saveSession, type Session } from '../api/tokens';

interface AuthValue {
  user: UserProfile | null;
  session: Session | null;
  /** True until the stored session has been checked against /users/me. */
  checking: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string, displayName: string, role: 'LISTENER' | 'ARTIST') => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(currentSession());
  const [user, setUser] = useState<UserProfile | null>(null);
  const [checking, setChecking] = useState(!!currentSession());

  useEffect(() => onSessionChange(setSession), []);

  // Re-check only when the signed-in user changes, not on every token rotation.
  const sub = session ? String(jwtClaims(session.accessToken).sub ?? '') : null;

  useEffect(() => {
    if (!sub) {
      setUser(null);
      setChecking(false);
      return;
    }
    let cancelled = false;
    authApi.GET('/api/v1/users/me').then(
      (r) => {
        if (cancelled) return;
        if (r.response.ok && r.data) setUser(r.data);
        else if (r.response.status === 401) saveSession(null);
        setChecking(false);
      },
      () => !cancelled && setChecking(false),
    );
    return () => {
      cancelled = true;
    };
  }, [sub]);

  const login = useCallback(async (email: string, password: string) => {
    const t = unwrap(await authApi.POST('/api/v1/auth/login', { body: { email, password } }));
    saveSession({ accessToken: t.accessToken, refreshToken: t.refreshToken, expiresAt: Date.now() + t.expiresIn * 1000 });
  }, []);

  const register = useCallback(
    async (email: string, password: string, displayName: string, role: 'LISTENER' | 'ARTIST') => {
      unwrap(await authApi.POST('/api/v1/auth/register', { body: { email, password, displayName, role } }));
      await login(email, password);
    },
    [login],
  );

  const logout = useCallback(async () => {
    const s = currentSession();
    if (s) {
      await authApi.POST('/api/v1/auth/logout', { body: { refreshToken: s.refreshToken } }).catch(() => undefined);
    }
    saveSession(null);
    setUser(null);
  }, []);

  const value = useMemo(
    () => ({ user, session, checking, login, register, logout }),
    [user, session, checking, login, register, logout],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthValue {
  const v = useContext(AuthContext);
  if (!v) throw new Error('useAuth outside AuthProvider');
  return v;
}
