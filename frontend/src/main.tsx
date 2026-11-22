import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { App } from './App';
import { AuthProvider } from './auth/AuthContext';
import { LibraryProvider } from './auth/LibraryContext';
import { PlayerProvider } from './player/PlayerContext';
import './styles/app.css';

// Apply the saved theme before the first paint (ThemeToggle keeps it in sync afterwards).
try {
  const t = localStorage.getItem('spotty.theme');
  document.documentElement.dataset.theme =
    t === 'night' || t === 'day' ? t : window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'night' : 'day';
} catch {
  document.documentElement.dataset.theme = 'day';
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <LibraryProvider>
          <PlayerProvider>
            <App />
          </PlayerProvider>
        </LibraryProvider>
      </AuthProvider>
    </BrowserRouter>
  </StrictMode>,
);
