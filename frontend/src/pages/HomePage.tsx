import { Link } from 'react-router-dom';
import { catalogApi, recoApi, type PlaySource, type Track } from '../api/client';
import { unwrap } from '../api/http';
import { useAuth } from '../auth/AuthContext';
import { AlbumCard } from '../components/AlbumCard';
import { Cover } from '../components/Cover';
import { Empty, ErrorState, Loading } from '../components/States';
import { TrackList } from '../components/TrackList';
import { Window } from '../components/Window';
import { tracksByIds } from '../lib/catalog';
import { useAsync } from '../lib/useAsync';
import { usePlayer } from '../player/PlayerContext';

interface HomeData {
  newest: Track[];
  songs: Track[];
  source: PlaySource;
  feedId?: string;
  heading: string;
}

/**
 * Track list: the personal feed when signed in, else the weekly chart; the newest tracks when either is empty.
 * Bargain bin: the albums of the newest tracks.
 */
async function loadHome(signedIn: boolean): Promise<HomeData> {
  const newest = unwrap(await catalogApi.GET('/api/v1/tracks', { params: { query: { sort: 'NEWEST', size: 50 } } })).items;
  try {
    if (signedIn) {
      const feed = unwrap(await recoApi.GET('/api/v1/recommendations/me', { params: { query: { limit: 10 } } }));
      const songs = await tracksByIds(feed.items.map((i) => i.trackId));
      if (songs.length) return { newest, songs, source: 'RECOMMENDATIONS', feedId: feed.feedId, heading: 'Songs to cancel plans to' };
    } else {
      const chart = unwrap(await recoApi.GET('/api/v1/charts/top', { params: { query: { period: 'WEEK', limit: 10 } } }));
      const songs = await tracksByIds(chart.items.map((i) => i.trackId));
      if (songs.length) return { newest, songs, source: 'CHART', heading: 'Songs to cancel plans to' };
    }
  } catch {
    /* recommendations are optional: fall back to the newest tracks */
  }
  return { newest, songs: newest.slice(0, 10), source: 'OTHER', heading: 'Songs to cancel plans to' };
}

export function HomePage() {
  const { user, checking } = useAuth();
  const signedIn = !!user;
  const { data, error, loading, reload } = useAsync(() => loadHome(signedIn), [signedIn, checking]);
  const player = usePlayer();

  if (loading && !data) return <Loading />;
  if (error) return <ErrorState error={error} onRetry={reload} />;
  if (!data || data.newest.length === 0) {
    return <Empty>The catalog is empty. Somehow that is still better than what we usually have.</Empty>;
  }

  const albums = distinctAlbums(data.newest).slice(0, 6);
  const heroTrack = player.current ?? data.songs[0] ?? data.newest[0];
  const heroIsCurrent = !!player.current && player.current.id === heroTrack.id;

  return (
    <>
      <Window title="now_suffering.exe" className="hero">
        <div className="hero__body">
          <Cover seed={heroTrack.album?.id ?? heroTrack.id} url={heroTrack.coverUrl} label={heroTrack.album?.title ?? heroTrack.title} />
          <div>
            <span className="label-suffering">NOW SUFFERING:</span>
            <div className="hero__title" data-testid="hero-title">
              {heroTrack.title}
            </div>
            <div className="hero__artist">
              <Link to={`/artist/${heroTrack.artist.id}`}>{heroTrack.artist.name}</Link>
            </div>
            <div className="hero__actions">
              <button
                type="button"
                className="btn btn--primary"
                data-testid="hero-play"
                onClick={() =>
                  heroIsCurrent
                    ? player.toggle()
                    : void player.play(heroTrack, { queue: data.songs, source: data.source, feedId: data.feedId })
                }
              >
                {heroIsCurrent && player.playing ? '❚❚ Pause' : '▶ Play anyway'}
              </button>
              <button
                type="button"
                className="btn"
                title="Skip (recommended)"
                disabled={!player.current}
                onClick={player.next}
              >
                ⏭ Skip (recommended)
              </button>
            </div>
          </div>
        </div>
      </Window>

      <section className="section" aria-labelledby="bargain">
        <div className="section__head">
          <h2 id="bargain">Bargain bin picks</h2>
          <span className="badge">50% off · 0% value</span>
        </div>
        {albums.length === 0 ? (
          <Empty>No albums yet. The bin is empty, like our promises.</Empty>
        ) : (
          <div className="album-grid">
            {albums.map((a) => (
              <AlbumCard key={a.id} id={a.id} title={a.title} artist={a.artist} coverUrl={a.coverUrl} />
            ))}
          </div>
        )}
      </section>

      <section className="section" aria-labelledby="songs">
        <div className="section__head">
          <h2 id="songs">{data.heading}</h2>
          <span className="muted">
            {data.source === 'RECOMMENDATIONS'
              ? 'Picked for you. We are sorry.'
              : data.source === 'CHART'
                ? 'Most played this week. Nobody knows why.'
                : 'Fresh off the press.'}
          </span>
        </div>
        <TrackList tracks={data.songs} source={data.source} feedId={data.feedId} />
      </section>
    </>
  );
}

function distinctAlbums(tracks: Track[]) {
  const seen = new Map<number, { id: number; title: string; artist: string; coverUrl?: string }>();
  for (const t of tracks) {
    if (t.album && !seen.has(t.album.id)) {
      seen.set(t.album.id, { id: t.album.id, title: t.album.title, artist: t.artist.name, coverUrl: t.coverUrl });
    }
  }
  return [...seen.values()];
}
