import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
  type SyntheticEvent,
} from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { recoApi, streamingApi, type PlaySource, type Track } from '../api/client';
import { ApiError, unwrap } from '../api/http';
import { currentSession } from '../api/tokens';

export interface PlayOptions {
  queue?: Track[];
  source?: PlaySource;
  feedId?: string;
}

interface PlayerValue {
  current: Track | null;
  queue: Track[];
  index: number;
  playing: boolean;
  /** Waiting for a stream token or for the first bytes. */
  buffering: boolean;
  time: number;
  duration: number;
  volume: number;
  error: string | null;
  play: (track: Track, opts?: PlayOptions) => Promise<void>;
  toggle: () => void;
  next: () => void;
  prev: () => void;
  seek: (seconds: number) => void;
  seekBy: (delta: number) => void;
  setVolume: (v: number) => void;
}

const PlayerContext = createContext<PlayerValue | null>(null);
const VOLUME_KEY = 'spotty.volume';

function storedVolume(): number {
  try {
    const v = Number(localStorage.getItem(VOLUME_KEY));
    return Number.isFinite(v) && v > 0 && v <= 1 ? v : 0.7;
  } catch {
    return 0.7;
  }
}

/**
 * Owns the one <audio> element of the app. It lives above the routes, so playback continues while navigating.
 * Audio comes from streaming-service: a short-lived signed token per track (POST /streaming/{id}/token), then the
 * browser streams `streamUrl` with Range requests. Plays are reported to recommendation-service when a track ends
 * or is replaced (listened seconds exclude pauses and seeks).
 */
export function PlayerProvider({ children }: { children: ReactNode }) {
  const audioRef = useRef<HTMLAudioElement>(null);
  const navigate = useNavigate();
  const location = useLocation();

  const [queue, setQueue] = useState<Track[]>([]);
  const [index, setIndex] = useState(-1);
  const [playing, setPlaying] = useState(false);
  const [buffering, setBuffering] = useState(false);
  const [time, setTime] = useState(0);
  const [duration, setDuration] = useState(0);
  const [volume, setVolumeState] = useState(storedVolume);
  const [error, setError] = useState<string | null>(null);

  const current = index >= 0 ? (queue[index] ?? null) : null;

  // Listening bookkeeping for play reports.
  const listen = useRef({ trackId: 0, seconds: 0, lastTime: 0, startedAt: '', source: undefined as PlaySource | undefined, feedId: undefined as string | undefined });
  const tokenRetry = useRef(0);
  const loadSeq = useRef(0);

  const report = useCallback(() => {
    const l = listen.current;
    const seconds = Math.round(l.seconds);
    if (l.trackId && seconds >= 1 && currentSession()) {
      void recoApi
        .POST('/api/v1/analytics/plays', {
          body: {
            trackId: l.trackId,
            listenedSeconds: Math.min(seconds, 21600),
            playedAt: l.startedAt,
            ...(l.source ? { source: l.source } : {}),
            ...(l.feedId ? { feedId: l.feedId } : {}),
          },
        })
        .catch(() => undefined);
    }
    listen.current = { ...l, trackId: 0, seconds: 0 };
  }, []);

  const load = useCallback(
    async (track: Track, startAt = 0, autoplay = true) => {
      const audio = audioRef.current;
      if (!audio) return;
      const seq = ++loadSeq.current;
      setError(null);
      setBuffering(true);
      try {
        const token = unwrap(
          await streamingApi.POST('/api/v1/streaming/{trackId}/token', { params: { path: { trackId: track.id } } }),
        );
        if (seq !== loadSeq.current) return;
        audio.src = token.streamUrl;
        if (startAt > 0) audio.currentTime = startAt;
        if (autoplay) await audio.play();
      } catch (e) {
        if (seq !== loadSeq.current) return;
        setBuffering(false);
        setPlaying(false);
        if (e instanceof ApiError && e.status === 401) {
          navigate(`/login?next=${encodeURIComponent(location.pathname)}`);
          setError('Log in to start suffering.');
        } else if (e instanceof DOMException && e.name === 'NotAllowedError') {
          setError('Your browser blocked autoplay. Press play anyway.');
        } else {
          setError(e instanceof Error && e.message ? `Could not play: ${e.message}` : 'Could not play this track.');
        }
      }
    },
    [navigate, location.pathname],
  );

  const startTrack = useCallback(
    (list: Track[], i: number, opts?: PlayOptions) => {
      report();
      const track = list[i];
      if (!track) return;
      listen.current = {
        trackId: track.id,
        seconds: 0,
        lastTime: 0,
        startedAt: new Date().toISOString(),
        source: opts?.source ?? listen.current.source,
        feedId: opts?.feedId,
      };
      tokenRetry.current = 0;
      setQueue(list);
      setIndex(i);
      setTime(0);
      setDuration(track.durationSeconds ?? 0);
      void load(track);
    },
    [load, report],
  );

  const play = useCallback(
    async (track: Track, opts?: PlayOptions) => {
      if (!currentSession()) {
        setError('Log in to start suffering.');
        navigate(`/login?next=${encodeURIComponent(location.pathname)}`);
        return;
      }
      const list = opts?.queue && opts.queue.some((t) => t.id === track.id) ? opts.queue : [track];
      const i = list.findIndex((t) => t.id === track.id);
      if (current && current.id === track.id && audioRef.current?.src) {
        const a = audioRef.current;
        if (a.paused) void a.play().catch(() => undefined);
        else a.pause();
        return;
      }
      startTrack(list, i, opts);
    },
    [current, startTrack, navigate, location.pathname],
  );

  const toggle = useCallback(() => {
    const a = audioRef.current;
    if (!a || !current) return;
    if (!a.src) {
      void load(current);
      return;
    }
    if (a.paused) void a.play().catch(() => undefined);
    else a.pause();
  }, [current, load]);

  const next = useCallback(() => {
    if (index + 1 < queue.length) startTrack(queue, index + 1);
    else if (queue.length > 1) startTrack(queue, 0);
    else if (current) seekTo(0);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [index, queue, startTrack, current]);

  const prev = useCallback(() => {
    const a = audioRef.current;
    if (a && a.currentTime > 3) {
      a.currentTime = 0;
      return;
    }
    if (index > 0) startTrack(queue, index - 1);
    else if (a) a.currentTime = 0;
  }, [index, queue, startTrack]);

  function seekTo(seconds: number) {
    const a = audioRef.current;
    if (!a || !current) return;
    const max = Number.isFinite(a.duration) && a.duration > 0 ? a.duration : duration;
    const t = Math.max(0, Math.min(seconds, max || seconds));
    a.currentTime = t;
    listen.current.lastTime = t;
    setTime(t);
  }

  const seek = useCallback(seekTo, [current, duration]); // eslint-disable-line react-hooks/exhaustive-deps
  const seekBy = useCallback((d: number) => seekTo((audioRef.current?.currentTime ?? 0) + d), [current, duration]); // eslint-disable-line react-hooks/exhaustive-deps

  const setVolume = useCallback((v: number) => {
    const clamped = Math.max(0, Math.min(1, v));
    setVolumeState(clamped);
    try {
      localStorage.setItem(VOLUME_KEY, String(clamped));
    } catch {
      /* ignore */
    }
  }, []);

  useEffect(() => {
    if (audioRef.current) audioRef.current.volume = volume;
  }, [volume]);

  // Space = play/pause, arrows = seek 5 s. Not while typing in a field.
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.defaultPrevented || e.altKey || e.ctrlKey || e.metaKey) return;
      const t = e.target as HTMLElement | null;
      const tag = t?.tagName;
      if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || t?.isContentEditable) return;
      if (e.code === 'Space' || e.key === ' ') {
        if (!current) return;
        e.preventDefault();
        toggle();
      } else if (e.key === 'ArrowLeft') {
        if (!current) return;
        e.preventDefault();
        seekBy(-5);
      } else if (e.key === 'ArrowRight') {
        if (!current) return;
        e.preventDefault();
        seekBy(5);
      }
    }
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [current, toggle, seekBy]);

  // Report the last play when the tab closes.
  useEffect(() => {
    const onHide = () => report();
    window.addEventListener('pagehide', onHide);
    return () => window.removeEventListener('pagehide', onHide);
  }, [report]);

  const handlers = {
    onPlay: () => {
      setPlaying(true);
      setError(null);
    },
    onPlaying: () => setBuffering(false),
    onPause: () => setPlaying(false),
    onWaiting: () => setBuffering(true),
    onCanPlay: () => setBuffering(false),
    onLoadedMetadata: (e: SyntheticEvent<HTMLAudioElement>) => {
      const d = e.currentTarget.duration;
      if (Number.isFinite(d) && d > 0) setDuration(d);
    },
    onTimeUpdate: (e: SyntheticEvent<HTMLAudioElement>) => {
      const t = e.currentTarget.currentTime;
      const l = listen.current;
      const delta = t - l.lastTime;
      if (!e.currentTarget.paused && delta > 0 && delta < 2) l.seconds += delta;
      l.lastTime = t;
      setTime(t);
    },
    onEnded: () => {
      setPlaying(false);
      if (index + 1 < queue.length) startTrack(queue, index + 1);
      else report();
    },
    onError: () => {
      // Most likely the stream token expired (e.g. seeking after a long pause): fetch a new one and resume once.
      const a = audioRef.current;
      if (!current || !a?.src) return;
      if (tokenRetry.current < 1) {
        tokenRetry.current += 1;
        void load(current, a.currentTime || time, playing);
      } else {
        setBuffering(false);
        setPlaying(false);
        setError('ERROR 404: TASTE NOT FOUND (the audio would not load).');
      }
    },
  };

  const value = useMemo<PlayerValue>(
    () => ({ current, queue, index, playing, buffering, time, duration, volume, error, play, toggle, next, prev, seek, seekBy, setVolume }),
    [current, queue, index, playing, buffering, time, duration, volume, error, play, toggle, next, prev, seek, seekBy, setVolume],
  );

  return (
    <PlayerContext.Provider value={value}>
      {children}
      <audio ref={audioRef} preload="metadata" data-testid="audio" {...handlers} />
    </PlayerContext.Provider>
  );
}

export function usePlayer(): PlayerValue {
  const v = useContext(PlayerContext);
  if (!v) throw new Error('usePlayer outside PlayerProvider');
  return v;
}
