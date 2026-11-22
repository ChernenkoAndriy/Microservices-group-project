import { catalogApi, type Track } from '../api/client';
import { unwrap } from '../api/http';

/** Loads published tracks by id (batches of 100) and returns them in the order of `ids`. */
export async function tracksByIds(ids: number[]): Promise<Track[]> {
  const unique = [...new Set(ids)];
  const found = new Map<number, Track>();
  for (let i = 0; i < unique.length; i += 100) {
    const chunk = unique.slice(i, i + 100);
    const page = unwrap(
      await catalogApi.GET('/api/v1/tracks', { params: { query: { ids: chunk, size: chunk.length } } }),
    );
    page.items.forEach((t) => found.set(t.id, t));
  }
  return ids.map((id) => found.get(id)).filter((t): t is Track => !!t);
}
