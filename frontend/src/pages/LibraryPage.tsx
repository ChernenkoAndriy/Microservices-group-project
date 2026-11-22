import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { catalogApi, libraryApi } from '../api/client';
import { unwrap } from '../api/http';
import { useAuth } from '../auth/AuthContext';
import { useLibrary } from '../auth/LibraryContext';
import { Cover } from '../components/Cover';
import { Empty, ErrorState, Loading } from '../components/States';
import { TrackList } from '../components/TrackList';
import { tracksByIds } from '../lib/catalog';
import { useAsync } from '../lib/useAsync';

type Tab = 'likes' | 'artists' | 'playlists';

export function LibraryPage() {
  const { user, checking } = useAuth();
  const [params, setParams] = useSearchParams();
  const tab = (params.get('tab') as Tab) || 'likes';

  if (checking) return <Loading />;
  if (!user) {
    return (
      <div className="state">
        <p>Your library is private. Log in to face it.</p>
        <Link to="/login?next=/library" className="btn btn--primary">
          Log in
        </Link>
      </div>
    );
  }

  return (
    <>
      <h1>Your library</h1>
      <div className="tabs" role="tablist">
        {(
          [
            ['likes', 'Liked songs'],
            ['artists', 'Artists you follow'],
            ['playlists', 'Playlists'],
          ] as const
        ).map(([id, label]) => (
          <button
            key={id}
            role="tab"
            aria-selected={tab === id}
            className={`btn btn--small ${tab === id ? 'btn--primary' : ''}`}
            onClick={() => setParams({ tab: id })}
          >
            {label}
          </button>
        ))}
      </div>
      {tab === 'likes' && <LikedSongs />}
      {tab === 'artists' && <FollowedArtists />}
      {tab === 'playlists' && <Playlists />}
    </>
  );
}

function LikedSongs() {
  const { liked } = useLibrary();
  const { data, error, loading, reload } = useAsync(async () => {
    const likes = unwrap(await libraryApi.GET('/api/v1/library/likes', { params: { query: { size: 100 } } }));
    return tracksByIds(likes.items.map((l) => l.trackId));
  }, []);
  if (loading && !data) return <Loading />;
  if (error) return <ErrorState error={error} onRetry={reload} />;
  // Unliked rows disappear right away; newly liked ones appear on the next visit.
  const tracks = (data ?? []).filter((t) => liked.has(t.id));
  if (tracks.length === 0) return <Empty>No liked songs. Your taste is intact.</Empty>;
  return <TrackList tracks={tracks} source="LIBRARY" />;
}

function FollowedArtists() {
  const { data, error, loading, reload } = useAsync(async () => {
    const follows = unwrap(await libraryApi.GET('/api/v1/library/follows', { params: { query: { size: 100 } } }));
    const artists = await Promise.all(
      follows.items.map((f) =>
        catalogApi
          .GET('/api/v1/artists/{artistId}', { params: { path: { artistId: f.artistId } } })
          .then((r) => r.data)
          .catch(() => undefined),
      ),
    );
    return artists.filter((a) => !!a);
  }, []);
  if (loading && !data) return <Loading />;
  if (error) return <ErrorState error={error} onRetry={reload} />;
  if (!data || data.length === 0) return <Empty>You follow nobody. Very independent.</Empty>;
  return (
    <div className="artist-grid">
      {data.map((a) => (
        <Link key={a!.id} to={`/artist/${a!.id}`} className="card artist-card">
          <Cover seed={`artist-${a!.id}`} url={a!.avatarUrl} label={a!.name} />
          <div style={{ marginTop: 8, fontWeight: 500 }}>{a!.name}</div>
        </Link>
      ))}
    </div>
  );
}

function Playlists() {
  const { playlists, refreshPlaylists } = useLibrary();
  const navigate = useNavigate();
  const [name, setName] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function create(e: FormEvent) {
    e.preventDefault();
    const n = name.trim();
    if (!n) return;
    setBusy(true);
    setError(null);
    try {
      const p = unwrap(await libraryApi.POST('/api/v1/library/playlists', { body: { name: n } }));
      setName('');
      await refreshPlaylists();
      navigate(`/playlist/${p.id}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not create the playlist.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <form className="row" onSubmit={create} style={{ marginBottom: 20 }}>
        <label htmlFor="pl-name" className="sr-only">
          Playlist name
        </label>
        <input
          id="pl-name"
          className="input"
          placeholder="Name your mistake"
          value={name}
          maxLength={100}
          onChange={(e) => setName(e.target.value)}
        />
        <button className="btn btn--primary" disabled={busy || !name.trim()}>
          + New playlist
        </button>
        {error && (
          <span className="error-text" role="alert">
            {error}
          </span>
        )}
      </form>
      {playlists.length === 0 ? (
        <Empty>No playlists. Honestly, good choice.</Empty>
      ) : (
        <div className="album-grid">
          {playlists.map((p) => (
            <Link key={p.id} to={`/playlist/${p.id}`} className="card album-card">
              <Cover seed={`pl-${p.id}`} label={p.name} />
              <div className="album-card__title">{p.name}</div>
              <div className="album-card__sub">
                {p.trackCount} track{p.trackCount === 1 ? '' : 's'}
              </div>
            </Link>
          ))}
        </div>
      )}
    </>
  );
}
