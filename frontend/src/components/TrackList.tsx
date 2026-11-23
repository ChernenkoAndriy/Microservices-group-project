import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import type { PlaySource, Track } from '../api/client';
import { formatTime } from '../lib/format';
import { usePlayer } from '../player/PlayerContext';
import { AddToPlaylist } from './AddToPlaylist';
import { Equalizer } from './Equalizer';
import { LikeButton } from './LikeButton';

interface Props {
  tracks: Track[];
  source: PlaySource;
  feedId?: string;
  /** Extra per-row action, e.g. remove from playlist. */
  action?: (track: Track) => ReactNode;
  numbered?: 'index' | 'trackNumber';
}

export function TrackList({ tracks, source, feedId, action, numbered = 'index' }: Props) {
  const player = usePlayer();
  return (
    <ol className="tracklist card" data-testid="tracklist">
      {tracks.map((t, i) => {
        const isCurrent = player.current?.id === t.id;
        const n = numbered === 'trackNumber' ? (t.trackNumber ?? i + 1) : i + 1;
        const start = () => void player.play(t, { queue: tracks, source, feedId });
        return (
          <li
            key={`${t.id}-${i}`}
            className={`track ${isCurrent ? 'track--current' : ''}`}
            data-testid="track-row"
            data-current={isCurrent || undefined}
          >
            <button
              type="button"
              className="track__num"
              onClick={start}
              aria-label={isCurrent && player.playing ? `Pause ${t.title}` : `Play ${t.title}`}
              title={isCurrent && player.playing ? 'Pause' : 'Play anyway'}
            >
              {isCurrent ? (
                <Equalizer paused={!player.playing} />
              ) : (
                <>
                  <span className="track__n">{n}</span>
                  <span className="track__play" aria-hidden="true">
                    ▶
                  </span>
                </>
              )}
            </button>
            <div className="track__main" onDoubleClick={start}>
              <div className="track__title">
                {t.title}
                {t.explicit && (
                  <>
                    {' '}
                    <span className="badge">explicit</span>
                  </>
                )}
              </div>
              <div className="track__artist">
                <Link to={`/artist/${t.artist.id}`}>{t.artist.name}</Link>
                {t.album && (
                  <>
                    {' · '}
                    <Link to={`/album/${t.album.id}`}>{t.album.title}</Link>
                  </>
                )}
              </div>
            </div>
            <div className="track__tools">
              <LikeButton trackId={t.id} />
              <AddToPlaylist trackId={t.id} />
              {action?.(t)}
            </div>
            <span className="track__dur">{formatTime(t.durationSeconds)}</span>
          </li>
        );
      })}
    </ol>
  );
}
