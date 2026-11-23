import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { catalogApi } from '../api/client';
import { ApiError, unwrap } from '../api/http';
import { useAuth } from '../auth/AuthContext';
import { useLibrary } from '../auth/LibraryContext';
import { AlbumCard } from '../components/AlbumCard';
import { Cover } from '../components/Cover';
import { Empty, ErrorState, Loading } from '../components/States';
import { TrackList } from '../components/TrackList';
import { useAsync } from '../lib/useAsync';
import { usePlayer } from '../player/PlayerContext';
import { NotFoundPage } from './NotFoundPage';

export function ArtistPage() {
  const artistId = Number(useParams().id);
  const { user } = useAuth();
  const { followed, toggleFollow } = useLibrary();
  const player = usePlayer();
  const navigate = useNavigate();
  const [busy, setBusy] = useState(false);
  const [followError, setFollowError] = useState<string | null>(null);

  const { data, error, loading, reload } = useAsync(async () => {
    const path = { params: { path: { artistId } } };
    const [artist, tracks, albums] = await Promise.all([
      catalogApi.GET('/api/v1/artists/{artistId}', path).then(unwrap),
      catalogApi.GET('/api/v1/artists/{artistId}/tracks', { params: { path: { artistId }, query: { size: 50 } } }).then(unwrap),
      catalogApi.GET('/api/v1/artists/{artistId}/albums', { params: { path: { artistId }, query: { size: 50 } } }).then(unwrap),
    ]);
    return { artist, tracks: tracks.items, albums: albums.items };
  }, [artistId]);

  if (!Number.isInteger(artistId) || artistId < 1) return <NotFoundPage />;
  if (loading && !data) return <Loading />;
  if (error instanceof ApiError && error.status === 404) return <NotFoundPage />;
  if (error || !data) return <ErrorState error={error} onRetry={reload} />;

  const { artist, tracks, albums } = data;
  const isFollowing = followed.has(artist.id);

  return (
    <>
      <div className="page-head">
        <Cover seed={`artist-${artist.id}`} url={artist.avatarUrl} label={artist.name} className="artist-cover" />
        <div>
          <div className="page-head__kind">artist (allegedly)</div>
          <h1>{artist.name}</h1>
          {artist.bio && <p>{artist.bio}</p>}
          <p className="muted">
            {artist.trackCount} track{artist.trackCount === 1 ? '' : 's'} · {artist.albumCount} album
            {artist.albumCount === 1 ? '' : 's'}
          </p>
          <div className="row">
            {tracks.length > 0 && (
              <button
                type="button"
                className="btn btn--primary"
                onClick={() => void player.play(tracks[0], { queue: tracks, source: 'ARTIST' })}
              >
                ▶ Play anyway
              </button>
            )}
            <button
              type="button"
              className={`btn ${isFollowing ? 'btn--active' : ''}`}
              aria-pressed={isFollowing}
              disabled={busy}
              data-testid="follow-button"
              onClick={async () => {
                if (!user) {
                  navigate('/login');
                  return;
                }
                setBusy(true);
                setFollowError(null);
                try {
                  await toggleFollow(artist.id);
                } catch (e) {
                  setFollowError(e instanceof Error ? e.message : 'Could not follow.');
                } finally {
                  setBusy(false);
                }
              }}
            >
              {isFollowing ? '✓ Following (why?)' : '+ Follow'}
            </button>
          </div>
          {followError && (
            <p className="error-text" role="alert">
              {followError}
            </p>
          )}
        </div>
      </div>

      {albums.length > 0 && (
        <section className="section" aria-labelledby="albums">
          <h2 id="albums">Discography (sorry)</h2>
          <div className="album-grid">
            {albums.map((a) => (
              <AlbumCard
                key={a.id}
                id={a.id}
                title={a.title}
                artist={a.artist.name}
                coverUrl={a.coverUrl}
                sub={`${a.type.toLowerCase()} · ${a.releaseDate?.slice(0, 4) ?? '????'}`}
              />
            ))}
          </div>
        </section>
      )}

      <section className="section" aria-labelledby="tracks">
        <h2 id="tracks">Tracks</h2>
        {tracks.length === 0 ? <Empty>No published tracks. Yet.</Empty> : <TrackList tracks={tracks} source="ARTIST" />}
      </section>
    </>
  );
}
