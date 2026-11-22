import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { useLibrary } from '../auth/LibraryContext';

export function LikeButton({ trackId, className = 'icon-btn' }: { trackId: number; className?: string }) {
  const { user } = useAuth();
  const { liked, toggleLike } = useLibrary();
  const navigate = useNavigate();
  const [busy, setBusy] = useState(false);
  const on = liked.has(trackId);
  return (
    <button
      type="button"
      className={`${className} ${on ? 'icon-btn--on' : ''}`}
      title="You sure?"
      aria-label={on ? 'Unlike (you sure?)' : 'Like (you sure?)'}
      aria-pressed={on}
      disabled={busy}
      onClick={async (e) => {
        e.stopPropagation();
        if (!user) {
          navigate('/login');
          return;
        }
        setBusy(true);
        try {
          await toggleLike(trackId);
        } catch {
          /* rolled back in the context */
        } finally {
          setBusy(false);
        }
      }}
    >
      {on ? '♥' : '♡'}
    </button>
  );
}
