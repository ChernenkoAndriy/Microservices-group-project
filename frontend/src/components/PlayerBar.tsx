import type { CSSProperties } from 'react';
import { Link } from 'react-router-dom';
import { formatTime } from '../lib/format';
import { usePlayer } from '../player/PlayerContext';
import { Cover } from './Cover';
import { LikeButton } from './LikeButton';

const fill = (pct: number) => ({ '--pct': `${pct}%` }) as CSSProperties;

/** The persistent bottom bar; rendered once in the layout, outside the routed pages. */
export function PlayerBar() {
  const p = usePlayer();
  const t = p.current;
  const pct = p.duration > 0 ? Math.min(100, (p.time / p.duration) * 100) : 0;
  return (
    <div className="player" role="region" aria-label="Player" data-testid="player-bar">
      <div className="player__now">
        <div className="player__thumb">
          <Cover seed={t?.album?.id ?? t?.id ?? 0} url={t?.coverUrl} label={t ? '♪' : ''} />
        </div>
        <div className="player__meta">
          <div className="player__title" data-testid="player-title">
            {t ? t.title : 'Nothing. Bliss.'}
          </div>
          <div className="player__artist">
            {t ? <Link to={`/artist/${t.artist.id}`}>{t.artist.name}</Link> : (p.error ?? 'Pick something regrettable')}
          </div>
          {t && p.error && (
            <div className="error-text" role="alert" style={{ fontSize: '0.8rem' }}>
              {p.error}
            </div>
          )}
        </div>
        {t && <LikeButton trackId={t.id} />}
      </div>

      <div className="player__center">
        <div className="player__controls">
          <button type="button" className="btn btn--icon" onClick={p.prev} disabled={!t} aria-label="Previous" title="Previous">
            ⏮
          </button>
          <button
            type="button"
            className="btn btn--icon btn--round btn--primary"
            onClick={p.toggle}
            disabled={!t}
            aria-label={p.playing ? 'Pause' : 'Play anyway'}
            title={p.playing ? 'Pause' : 'Play anyway'}
            data-testid="player-toggle"
          >
            {p.playing ? '❚❚' : '▶'}
          </button>
          <button
            type="button"
            className="btn btn--icon"
            onClick={p.next}
            disabled={!t}
            aria-label="Skip (recommended)"
            title="Skip (recommended)"
          >
            ⏭
          </button>
        </div>
        <div className="player__progress">
          <span className="time" data-testid="time-current">
            {formatTime(p.time)}
          </span>
          <input
            type="range"
            className="range"
            min={0}
            max={p.duration || 0}
            step={0.1}
            value={Math.min(p.time, p.duration || 0)}
            onChange={(e) => p.seek(Number(e.target.value))}
            disabled={!t}
            aria-label="Seek"
            aria-valuetext={`${formatTime(p.time)} of ${formatTime(p.duration)}`}
            style={fill(pct)}
            data-testid="seek"
          />
          <span className="time">{formatTime(p.duration)}</span>
        </div>
      </div>

      <div className="player__volume">
        <span>Bearable</span>
        <input
          type="range"
          className="range range--volume"
          min={0}
          max={1}
          step={0.01}
          value={p.volume}
          onChange={(e) => p.setVolume(Number(e.target.value))}
          aria-label="Volume"
          style={fill(p.volume * 100)}
        />
        <span>Why</span>
      </div>
    </div>
  );
}
