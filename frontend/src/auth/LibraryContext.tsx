import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { libraryApi, type PlaylistSummary } from '../api/client';
import { unwrap } from '../api/http';
import { useAuth } from './AuthContext';

/** The signed-in user's likes, follows and playlists, shared by every page (sidebar, like buttons, follow button). */
interface LibraryValue {
  liked: Set<number>;
  followed: Set<number>;
  playlists: PlaylistSummary[];
  toggleLike: (trackId: number) => Promise<void>;
  toggleFollow: (artistId: number) => Promise<void>;
  refreshPlaylists: () => Promise<void>;
}

const LibraryContext = createContext<LibraryValue | null>(null);

export function LibraryProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const [liked, setLiked] = useState<Set<number>>(new Set());
  const [followed, setFollowed] = useState<Set<number>>(new Set());
  const [playlists, setPlaylists] = useState<PlaylistSummary[]>([]);

  const refreshPlaylists = useCallback(async () => {
    const r = await libraryApi.GET('/api/v1/library/playlists', { params: { query: { size: 100 } } });
    if (r.data) setPlaylists(r.data.items);
  }, []);

  useEffect(() => {
    if (!user) {
      setLiked(new Set());
      setFollowed(new Set());
      setPlaylists([]);
      return;
    }
    libraryApi.GET('/api/v1/library/likes', { params: { query: { size: 100 } } }).then((r) => {
      if (r.data) setLiked(new Set(r.data.items.map((l) => l.trackId)));
    });
    libraryApi.GET('/api/v1/library/follows', { params: { query: { size: 100 } } }).then((r) => {
      if (r.data) setFollowed(new Set(r.data.items.map((f) => f.artistId)));
    });
    void refreshPlaylists();
  }, [user, refreshPlaylists]);

  const toggleLike = useCallback(
    async (trackId: number) => {
      const was = liked.has(trackId);
      const params = { params: { path: { trackId } } };
      setLiked((s) => toggled(s, trackId, !was));
      try {
        if (was) unwrap(await libraryApi.DELETE('/api/v1/library/likes/{trackId}', params));
        else unwrap(await libraryApi.POST('/api/v1/library/likes/{trackId}', params));
      } catch (e) {
        setLiked((s) => toggled(s, trackId, was));
        throw e;
      }
    },
    [liked],
  );

  const toggleFollow = useCallback(
    async (artistId: number) => {
      const was = followed.has(artistId);
      const params = { params: { path: { artistId } } };
      setFollowed((s) => toggled(s, artistId, !was));
      try {
        if (was) unwrap(await libraryApi.DELETE('/api/v1/library/follows/{artistId}', params));
        else unwrap(await libraryApi.POST('/api/v1/library/follows/{artistId}', params));
      } catch (e) {
        setFollowed((s) => toggled(s, artistId, was));
        throw e;
      }
    },
    [followed],
  );

  const value = useMemo(
    () => ({ liked, followed, playlists, toggleLike, toggleFollow, refreshPlaylists }),
    [liked, followed, playlists, toggleLike, toggleFollow, refreshPlaylists],
  );
  return <LibraryContext.Provider value={value}>{children}</LibraryContext.Provider>;
}

function toggled(s: Set<number>, id: number, on: boolean): Set<number> {
  const n = new Set(s);
  if (on) n.add(id);
  else n.delete(id);
  return n;
}

export function useLibrary(): LibraryValue {
  const v = useContext(LibraryContext);
  if (!v) throw new Error('useLibrary outside LibraryProvider');
  return v;
}
