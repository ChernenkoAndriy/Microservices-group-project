import createClient from 'openapi-fetch';
import type { paths as AuthPaths, components as AuthC } from './generated/auth';
import type { paths as CatalogPaths, components as CatalogC } from './generated/catalog';
import type { paths as LibraryPaths, components as LibraryC } from './generated/library';
import type { paths as StreamingPaths, components as StreamingC } from './generated/streaming';
import type { paths as RecoPaths, components as RecoC } from './generated/recommendation';
import { authFetch } from './http';

// Types and clients generated from api-contracts/ (npm run gen:api). Same origin: /api is proxied to the gateway.
const opts = { baseUrl: typeof location !== 'undefined' ? location.origin : '', fetch: authFetch };
export const authApi = createClient<AuthPaths>(opts);
export const catalogApi = createClient<CatalogPaths>(opts);
export const libraryApi = createClient<LibraryPaths>(opts);
export const streamingApi = createClient<StreamingPaths>(opts);
export const recoApi = createClient<RecoPaths>(opts);

export type Track = CatalogC['schemas']['Track'];
export type Album = CatalogC['schemas']['Album'];
export type AlbumSummary = CatalogC['schemas']['AlbumSummary'];
export type Artist = CatalogC['schemas']['Artist'];
export type Genre = CatalogC['schemas']['Genre'];
export type UserProfile = AuthC['schemas']['UserProfile'];
export type TokenResponse = AuthC['schemas']['TokenResponse'];
export type PlaylistSummary = LibraryC['schemas']['PlaylistSummary'];
export type Playlist = LibraryC['schemas']['Playlist'];
export type StreamToken = StreamingC['schemas']['StreamToken'];
export type RecommendationFeed = RecoC['schemas']['RecommendationFeed'];
export type Chart = RecoC['schemas']['Chart'];
export type PlaySource = RecoC['schemas']['PlaySource'];
