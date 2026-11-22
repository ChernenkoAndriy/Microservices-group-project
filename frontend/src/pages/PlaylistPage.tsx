import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { libraryApi, type Track } from '../api/client';
import { ApiError, unwrap } from '../api/http';
import { useAuth } from '../auth/AuthContext';
import { useLibrary } from '../auth/LibraryContext';
import { Cover } from '../components/Cover';
import { Empty, ErrorState, Loading } from '../components/States';
import { TrackList } from '../components/TrackList';
import { tracksByIds } from '../lib/catalog';
import { useAsync } from '../lib/useAsync';
import { usePlayer } from '../player/PlayerContext';
import { NotFoundPage } from './NotFoundPage';

export function PlaylistPage() {
  const playlistId = Number(useParams().id);
  const { user, checking } = useAuth();
  const { refreshPlaylists } = useLibrary();
  const player = usePlayer();
  const navigate = useNavigate();
  const [editing, setEditing] = useState(false);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [msg, setMsg] = useState<string | null>(null);

  const { data, error, loading, reload } = useAsync(async () => {
    if (!user) return undefined;
    const playlist = unwrap(
      await libraryApi.GET('/api/v1/library/playlists/{playlistId}', { params: { path: { playlistId } } }),
    );
    const ordered = [...playlist.tracks].sort((a, b) => a.position - b.position);
    const tracks = await tracksByIds(ordered.map((t) => t.trackId));
    return { playlist, tracks };
  }, [playlistId, user?.id]);

  if (!Number.isInteger(playlistId) || playlistId < 1) return <NotFoundPage />;
  if (checking || (loading && !data)) return <Loading />;
  if (!user) {
    return (
      <div className="state">
        <p>Log in to see this playlist.</p>
        <Link to={`/login?next=/playlist/${playlistId}`} className="btn btn--primary">
          Log in
        </Link>
      </div>
    );
  }
  if (error instanceof ApiError && error.status === 404) return <NotFoundPage />;
  if (error || !data) return <ErrorState error={error} onRetry={reload} />;

  const { playlist, tracks } = data;

  async function save(e: FormEvent) {
    e.preventDefault();
    try {
      unwrap(
        await libraryApi.PATCH('/api/v1/library/playlists/{playlistId}', {
          params: { path: { playlistId } },
          body: { name: name.trim(), description: description.trim() },
        }),
      );
      setEditing(false);
      setMsg(null);
      reload();
      void refreshPlaylists();
    } catch (err) {
      setMsg(err instanceof Error ? err.message : 'Could not save.');
    }
  }

  async function remove(track: Track) {
    try {
      unwrap(
        await libraryApi.DELETE('/api/v1/library/playlists/{playlistId}/tracks/{trackId}', {
          params: { path: { playlistId, trackId: track.id } },
        }),
      );
      reload();
      void refreshPlaylists();
    } catch (err) {
      setMsg(err instanceof Error ? err.message : 'Could not remove.');
    }
  }

  async function destroy() {
    if (!window.confirm(`Delete “${playlist.name}”? This is the one good decision.`)) return;
    try {
      unwrap(await libraryApi.DELETE('/api/v1/library/playlists/{playlistId}', { params: { path: { playlistId } } }));
      await refreshPlaylists();
      navigate('/library?tab=playlists');
    } catch (err) {
      setMsg(err instanceof Error ? err.message : 'Could not delete.');
    }
  }

  return (
    <>
      <div className="page-head">
        <Cover seed={`pl-${playlist.id}`} label={playlist.name} />
        <div>
          <div className="page-head__kind">playlist</div>
          {editing ? (
            <form className="form" onSubmit={save}>
              <div className="field">
                <label htmlFor="pl-edit-name">Name</label>
                <input
                  id="pl-edit-name"
                  className="input"
                  value={name}
                  maxLength={100}
                  required
                  onChange={(e) => setName(e.target.value)}
                />
              </div>
              <div className="field">
                <label htmlFor="pl-edit-desc">Description</label>
                <textarea
                  id="pl-edit-desc"
                  className="textarea"
                  value={description}
                  maxLength={500}
                  onChange={(e) => setDescription(e.target.value)}
                />
              </div>
              <div className="row">
                <button className="btn btn--primary" disabled={!name.trim()}>
                  Save
                </button>
                <button type="button" className="btn" onClick={() => setEditing(false)}>
                  Cancel
                </button>
              </div>
            </form>
          ) : (
            <>
              <h1>{playlist.name}</h1>
              {playlist.description && <p>{playlist.description}</p>}
              <p className="muted">
                {playlist.trackCount} track{playlist.trackCount === 1 ? '' : 's'}
              </p>
              <div className="row">
                {tracks.length > 0 && (
                  <button
                    type="button"
                    className="btn btn--primary"
                    onClick={() => void player.play(tracks[0], { queue: tracks, source: 'LIBRARY' })}
                  >
                    ▶ Play anyway
                  </button>
                )}
                <button
                  type="button"
                  className="btn"
                  onClick={() => {
                    setName(playlist.name);
                    setDescription(playlist.description ?? '');
                    setEditing(true);
                  }}
                >
                  Edit
                </button>
                <button type="button" className="btn" onClick={destroy}>
                  Delete
                </button>
              </div>
            </>
          )}
          {msg && (
            <p className="error-text" role="alert">
              {msg}
            </p>
          )}
        </div>
      </div>
      {tracks.length === 0 ? (
        <Empty>Your playlist is empty. Honestly, good choice.</Empty>
      ) : (
        <TrackList
          tracks={tracks}
          source="LIBRARY"
          action={(t) => (
            <button
              type="button"
              className="icon-btn"
              title="Remove from playlist"
              aria-label={`Remove ${t.title} from playlist`}
              onClick={() => void remove(t)}
            >
              −
            </button>
          )}
        />
      )}
    </>
  );
}
