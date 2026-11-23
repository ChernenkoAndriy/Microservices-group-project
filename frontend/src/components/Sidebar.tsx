import { NavLink } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { useLibrary } from '../auth/LibraryContext';

export function Sidebar() {
  const { user } = useAuth();
  const { playlists } = useLibrary();
  return (
    <nav className="sidebar" aria-label="Main">
      <div className="nav">
        <NavLink to="/" end>
          <span className="nav__icon" aria-hidden="true">
            ⌂
          </span>{' '}
          Home
        </NavLink>
        <NavLink to="/search">
          <span className="nav__icon" aria-hidden="true">
            ⌕
          </span>{' '}
          Search
        </NavLink>
        <NavLink to="/library">
          <span className="nav__icon" aria-hidden="true">
            ▤
          </span>{' '}
          Your library
        </NavLink>
      </div>
      {user && (
        <div className="sidebar__section">
          <h2 className="sidebar__title">Playlists</h2>
          {playlists.length === 0 ? (
            <p className="muted" style={{ margin: '0 12px', fontSize: '0.875rem' }}>
              None yet. Restraint!
            </p>
          ) : (
            <ul className="playlist-links">
              {playlists.map((p) => (
                <li key={p.id}>
                  <NavLink to={`/playlist/${p.id}`} title={p.name}>
                    {p.name}
                  </NavLink>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </nav>
  );
}
