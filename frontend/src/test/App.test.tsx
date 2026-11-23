import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { App } from '../App';
import { AuthProvider } from '../auth/AuthContext';
import { LibraryProvider } from '../auth/LibraryContext';
import { PlayerProvider } from '../player/PlayerContext';

const track = (id: number, title: string, artist: string, album: number) => ({
  id,
  title,
  artist: { id: 100 + album, name: artist },
  album: { id: album, title: `${title} (album)` },
  trackNumber: 1,
  genres: [{ slug: 'muzak', name: 'Muzak' }],
  durationSeconds: 125,
  explicit: false,
  status: 'PUBLISHED',
  createdAt: '2026-11-01T00:00:00Z',
  updatedAt: '2026-11-01T00:00:00Z',
});

const TRACKS = [track(1, 'Lukewarm Forever', 'Gary and the Mild Disappointments', 11), track(2, 'Elevator to Nowhere', 'Muzak Supreme', 12)];

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

beforeEach(() => {
  localStorage.clear();
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: Request | string) => {
      const url = new URL(typeof input === 'string' ? input : input.url, 'http://localhost');
      if (url.pathname === '/api/v1/tracks') {
        return json({ items: TRACKS, page: { number: 0, size: 50, totalElements: 2, totalPages: 1 } });
      }
      if (url.pathname === '/api/v1/charts/top') {
        return json({ period: 'WEEK', generatedAt: '2026-11-22T00:00:00Z', items: [] });
      }
      return json({ title: 'Not Found', status: 404 }, 404);
    }),
  );
});

afterEach(() => vi.unstubAllGlobals());

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <LibraryProvider>
          <PlayerProvider>
            <App />
          </PlayerProvider>
        </LibraryProvider>
      </AuthProvider>
    </MemoryRouter>,
  );
}

it('renders the home page with hero, bargain bin and track list', async () => {
  renderAt('/');
  expect(await screen.findByTestId('hero-title')).toHaveTextContent('Lukewarm Forever');
  expect(screen.getByText('NOW SUFFERING:')).toBeInTheDocument();
  expect(screen.getByText('now_suffering.exe')).toBeInTheDocument();
  expect(screen.getAllByTestId('album-card')).toHaveLength(2);
  const list = screen.getByTestId('tracklist');
  expect(within(list).getAllByTestId('track-row')).toHaveLength(2);
  expect(within(list).getAllByText('2:05')).toHaveLength(2);
  expect(screen.getByText('© 1997 Spotty Corp. All rights regretted.')).toBeInTheDocument();
  expect(screen.getByLabelText('Volume')).toBeInTheDocument();
  expect(screen.getByText('Bearable')).toBeInTheDocument();
  expect(screen.getByText('Why')).toBeInTheDocument();
});

it('shows the 404 copy for unknown routes', async () => {
  renderAt('/nope');
  expect(await screen.findByText('ERROR 404: TASTE NOT FOUND')).toBeInTheDocument();
});

it('asks anonymous users to log in before playing', async () => {
  renderAt('/');
  const play = await screen.findByTestId('hero-play');
  play.click();
  expect(await screen.findByRole('heading', { name: 'Log in' })).toBeInTheDocument();
});
