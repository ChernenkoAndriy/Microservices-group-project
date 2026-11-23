import { useSearchParams } from 'react-router-dom';
import { catalogApi } from '../api/client';
import { unwrap } from '../api/http';
import { Empty, ErrorState, Loading } from '../components/States';
import { TrackList } from '../components/TrackList';
import { useAsync } from '../lib/useAsync';

export function SearchPage() {
  const [params, setParams] = useSearchParams();
  const q = params.get('q')?.trim() || undefined;
  const genre = params.get('genre') || undefined;

  const genres = useAsync(async () => unwrap(await catalogApi.GET('/api/v1/genres')), []);
  const results = useAsync(
    async () =>
      unwrap(
        await catalogApi.GET('/api/v1/tracks', {
          params: { query: { q, genre, size: 50, sort: q ? 'RELEVANCE' : 'NEWEST' } },
        }),
      ),
    [q, genre],
  );

  function setGenre(slug: string | undefined) {
    const next = new URLSearchParams(params);
    if (slug) next.set('genre', slug);
    else next.delete('genre');
    setParams(next);
  }

  return (
    <>
      <h1>{q ? `Regrets matching “${q}”` : 'Browse the damage'}</h1>
      {genres.data && genres.data.length > 0 && (
        <div className="chips" style={{ margin: '12px 0 20px' }} aria-label="Genres">
          <button type="button" className={`chip ${!genre ? 'chip--on' : ''}`} onClick={() => setGenre(undefined)} aria-pressed={!genre}>
            All
          </button>
          {genres.data.map((g) => (
            <button
              type="button"
              key={g.slug}
              className={`chip ${genre === g.slug ? 'chip--on' : ''}`}
              onClick={() => setGenre(g.slug)}
              aria-pressed={genre === g.slug}
            >
              {g.name}
            </button>
          ))}
        </div>
      )}
      {results.loading && !results.data ? (
        <Loading />
      ) : results.error ? (
        <ErrorState error={results.error} onRetry={results.reload} />
      ) : results.data && results.data.items.length > 0 ? (
        <>
          <p className="muted">
            {results.data.page.totalElements} result{results.data.page.totalElements === 1 ? '' : 's'}. Each one a choice.
          </p>
          <TrackList tracks={results.data.items} source="SEARCH" />
        </>
      ) : (
        <Empty>Nothing found. Even our catalog has standards.</Empty>
      )}
    </>
  );
}
