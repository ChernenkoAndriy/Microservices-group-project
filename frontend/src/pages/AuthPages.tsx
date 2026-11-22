import { useState, type FormEvent } from 'react';
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom';
import { ApiError } from '../api/http';
import { useAuth } from '../auth/AuthContext';
import { Window } from '../components/Window';

function safeNext(next: string | null): string {
  return next && next.startsWith('/') && !next.startsWith('//') ? next : '/';
}

function message(e: unknown, fallback: string): string {
  if (e instanceof ApiError) {
    if (e.status === 401) return 'Wrong email or password. Or both. Impressive.';
    if (e.status === 409) return 'That email already has an account. Log in instead.';
    if (e.status === 423 || e.status === 403) return 'This account is blocked. We had our reasons.';
    return e.message || fallback;
  }
  return fallback;
}

export function LoginPage() {
  const { login, user } = useAuth();
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const next = safeNext(params.get('next'));
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  if (user) return <Navigate to={next} replace />;

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await login(email.trim(), password);
      navigate(next, { replace: true });
    } catch (err) {
      setError(message(err, 'Could not log in.'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div style={{ maxWidth: 480 }}>
      <Window title="login.exe">
        <h1>Log in</h1>
        <p className="muted">Welcome back. We knew you would.</p>
        <form className="form" onSubmit={submit}>
          <div className="field">
            <label htmlFor="email">Email</label>
            <input id="email" className="input" type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="password">Password</label>
            <input
              id="password"
              className="input"
              type="password"
              autoComplete="current-password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </div>
          {error && (
            <p className="error-text" role="alert">
              {error}
            </p>
          )}
          <div className="row">
            <button className="btn btn--primary" disabled={busy}>
              {busy ? 'Checking…' : 'Log in'}
            </button>
            <Link to={`/register${params.get('next') ? `?next=${encodeURIComponent(next)}` : ''}`}>No account? Make a mistake</Link>
          </div>
        </form>
      </Window>
    </div>
  );
}

export function RegisterPage() {
  const { register, user } = useAuth();
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const next = safeNext(params.get('next'));
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [role, setRole] = useState<'LISTENER' | 'ARTIST'>('LISTENER');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  if (user) return <Navigate to={next} replace />;

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (password.length < 8) {
      setError('The password needs at least 8 characters. Unlike our songs, it should be strong.');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await register(email.trim(), password, displayName.trim(), role);
      navigate(next, { replace: true });
    } catch (err) {
      setError(message(err, 'Could not register.'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div style={{ maxWidth: 480 }}>
      <Window title="register.exe">
        <h1>Create account</h1>
        <p className="muted">Join thousands of people who should know better.</p>
        <form className="form" onSubmit={submit}>
          <div className="field">
            <label htmlFor="name">Display name</label>
            <input id="name" className="input" required maxLength={100} value={displayName} onChange={(e) => setDisplayName(e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="reg-email">Email</label>
            <input id="reg-email" className="input" type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="reg-password">Password (8+ characters)</label>
            <input
              id="reg-password"
              className="input"
              type="password"
              autoComplete="new-password"
              required
              minLength={8}
              maxLength={128}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </div>
          <div className="field">
            <label htmlFor="role">I am a</label>
            <select id="role" className="select" value={role} onChange={(e) => setRole(e.target.value as 'LISTENER' | 'ARTIST')}>
              <option value="LISTENER">Listener (victim)</option>
              <option value="ARTIST">Artist (suspect)</option>
            </select>
          </div>
          {error && (
            <p className="error-text" role="alert">
              {error}
            </p>
          )}
          <div className="row">
            <button className="btn btn--primary" disabled={busy}>
              {busy ? 'Signing you up…' : 'Register'}
            </button>
            <Link to="/login">Already suffering? Log in</Link>
          </div>
        </form>
      </Window>
    </div>
  );
}
