import { useEffect, useRef, useState } from 'react';
import { libraryApi } from '../api/client';
import { unwrap } from '../api/http';
import { useAuth } from '../auth/AuthContext';
import { useLibrary } from '../auth/LibraryContext';

export function AddToPlaylist({ trackId }: { trackId: number }) {
  const { user } = useAuth();
  const { playlists, refreshPlaylists } = useLibrary();
  const [open, setOpen] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const close = (e: MouseEvent) => {
      if (!ref.current?.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', close);
    return () => document.removeEventListener('mousedown', close);
  }, [open]);

  if (!user) return null;
  return (
    <div className="menu" ref={ref}>
      <button
        type="button"
        className="icon-btn"
        title="Add to playlist"
        aria-label="Add to playlist"
        aria-expanded={open}
        onClick={(e) => {
          e.stopPropagation();
          setMsg(null);
          setOpen((o) => !o);
        }}
      >
        +
      </button>
      {open && (
        <div className="menu__panel" role="menu">
          <div className="who">Add to playlist</div>
          {playlists.length === 0 && <div className="muted" style={{ padding: 8 }}>No playlists yet.</div>}
          {playlists.map((p) => (
            <button
              key={p.id}
              role="menuitem"
              className="menu__item"
              onClick={async () => {
                try {
                  unwrap(
                    await libraryApi.PUT('/api/v1/library/playlists/{playlistId}/tracks/{trackId}', {
                      params: { path: { playlistId: p.id, trackId } },
                    }),
                  );
                  setMsg(`Added to ${p.name}. No refunds.`);
                  void refreshPlaylists();
                } catch (e) {
                  setMsg(e instanceof Error ? e.message : 'Could not add.');
                }
              }}
            >
              {p.name}
            </button>
          ))}
          {msg && (
            <div className="muted" role="status" style={{ padding: 8, fontSize: '0.875rem' }}>
              {msg}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
