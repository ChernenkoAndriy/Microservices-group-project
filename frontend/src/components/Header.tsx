import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { initials } from '../lib/format';
import { HeaderArt } from './HeaderArt';
import { ThemeToggle } from './ThemeToggle';

export function Header() {
  const navigate = useNavigate();
  const location = useLocation();
  const [params] = useSearchParams();
  const [q, setQ] = useState(location.pathname === '/search' ? (params.get('q') ?? '') : '');

  useEffect(() => {
    if (location.pathname === '/search') setQ(params.get('q') ?? '');
  }, [location.pathname, params]);

  function submit(e: FormEvent) {
    e.preventDefault();
    const term = q.trim();
    navigate(term ? `/search?q=${encodeURIComponent(term)}` : '/search');
  }

  return (
    <header className="header">
      <HeaderArt />
      <div className="header__bar">
        <Link to="/" className="logo" aria-label="Spotty home">
          <span className="logo__name">Spotty</span>
          <span className="logo__jp" lang="ja">
            スポッティ
          </span>
        </Link>
        <form className="search" role="search" onSubmit={submit}>
          <label htmlFor="search" className="sr-only">
            Search
          </label>
          <input
            id="search"
            type="search"
            placeholder="Search for regrets…"
            value={q}
            maxLength={100}
            onChange={(e) => setQ(e.target.value)}
          />
        </form>
        <div className="header__actions">
          <ThemeToggle />
          <AvatarMenu />
        </div>
      </div>
    </header>
  );
}

function AvatarMenu() {
  const { user, logout } = useAuth();
  const [open, setOpen] = useState(false);
  const navigate = useNavigate();
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const close = (e: MouseEvent) => {
      if (!ref.current?.contains(e.target as Node)) setOpen(false);
    };
    const esc = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
    };
    document.addEventListener('mousedown', close);
    document.addEventListener('keydown', esc);
    return () => {
      document.removeEventListener('mousedown', close);
      document.removeEventListener('keydown', esc);
    };
  }, [open]);

  if (!user) {
    return (
      <Link to="/login" className="btn btn--primary" data-testid="login-link">
        Log in
      </Link>
    );
  }
  return (
    <div className="menu" ref={ref}>
      <button
        type="button"
        className="avatar"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label={`Account menu for ${user.displayName}`}
        onClick={() => setOpen((o) => !o)}
        data-testid="avatar"
      >
        {initials(user.displayName)}
      </button>
      {open && (
        <div className="menu__panel" role="menu">
          <div className="who">
            {user.displayName}
            <br />
            <span>{user.role.toLowerCase()}</span>
          </div>
          <Link to="/library" role="menuitem" className="menu__item" onClick={() => setOpen(false)}>
            Your library
          </Link>
          <button
            role="menuitem"
            className="menu__item"
            onClick={async () => {
              setOpen(false);
              await logout();
              navigate('/');
            }}
          >
            Log out (finally)
          </button>
        </div>
      )}
    </div>
  );
}
