import { Link, useParams } from 'react-router-dom';
import { catalogApi } from '../api/client';
import { ApiError, unwrap } from '../api/http';
import { Cover } from '../components/Cover';
import { Empty, ErrorState, Loading } from '../components/States';
import { TrackList } from '../components/TrackList';
import { formatTime } from '../lib/format';
import { useAsync } from '../lib/useAsync';
import { usePlayer } from '../player/PlayerContext';
import { NotFoundPage } from './NotFoundPage';

export function AlbumPage() {
  const albumId = Number(useParams().id);
  const player = usePlayer();
  const { data, error, loading, reload } = useAsync(
    async () => unwrap(await catalogApi.GET('/api/v1/albums/{albumId}', { params: { path: { albumId } } })),
    [albumId],
  );

  if (!Number.isInteger(albumId) || albumId < 1) return <NotFoundPage />;
  if (loading && !data) return <Loading />;
  if (error instanceof ApiError && error.status === 404) return <NotFoundPage />;
  if (error || !data) return <ErrorState error={error} onRetry={reload} />;

  const total = data.tracks.reduce((s, t) => s + (t.durationSeconds ?? 0), 0);
  return (
    <>
      <div className="page-head">
        <Cover seed={data.id} url={data.coverUrl} label={data.title} />
        <div>
          <div className="page-head__kind">{data.type.toLowerCase()}</div>
          <h1>{data.title}</h1>
          <p>
            <Link to={`/artist/${data.artist.id}`}>{data.artist.name}</Link>
            <span className="muted">
              {' · '}
              {data.releaseDate?.slice(0, 4) ?? 'year unknown'} · {data.tracks.length} track{data.tracks.length === 1 ? '' : 's'} ·{' '}
              <span className="pixel">{formatTime(total)}</span>
            </span>
          </p>
          {data.tracks.length > 0 && (
            <button
              type="button"
              className="btn btn--primary"
              onClick={() => void player.play(data.tracks[0], { queue: data.tracks, source: 'ALBUM' })}
            >
              ▶ Play anyway
            </button>
          )}
        </div>
      </div>
      {data.tracks.length === 0 ? (
        <Empty>No tracks here yet. A rare mercy.</Empty>
      ) : (
        <TrackList tracks={data.tracks} source="ALBUM" numbered="trackNumber" />
      )}
    </>
  );
}
