// Scenario 1: LOAD test (closed model, ramping VUs) - expected everyday traffic.
//
// 20 artists use their dashboard at the same time. Every iteration is one "page view" of 5 calls through the
// gateway: profile (auth-service), genres (catalog), own tracks incl. drafts (catalog -> auth /internal/users),
// a track of their own (catalog -> auth) and their public artist page (catalog -> auth).
// Goal: under normal load p95 < 500 ms and < 1 % errors.
import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { BASE_URL, auth, createArtists, summary } from './lib.js';

const ARTISTS = Number(__ENV.ARTISTS || 20);

export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  setupTimeout: '5m',
  scenarios: {
    artist_dashboard: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: ARTISTS }, // ramp up
        { duration: '2m', target: ARTISTS },  // steady state
        { duration: '20s', target: 0 },       // ramp down
      ],
      gracefulRampDown: '10s',
    },
  },
  thresholds: {
    'http_req_duration{scenario:artist_dashboard}': ['p(95)<500', 'p(99)<1000'],
    'http_req_failed{scenario:artist_dashboard}': ['rate<0.01'],
    'checks{scenario:artist_dashboard}': ['rate>0.99'],
    'group_duration{group:::dashboard page}': ['p(95)<2000'],
  },
};

export function setup() {
  return { artists: createArtists(ARTISTS, 3, 'dash') };
}

export default function (data) {
  // One VU = one artist, so each one has its own rate-limit bucket at the gateway (per user id).
  const artist = data.artists[(__VU - 1) % data.artists.length];
  const trackId = artist.trackIds[__ITER % artist.trackIds.length];

  group('dashboard page', () => {
    const me = http.get(`${BASE_URL}/api/v1/users/me`, auth(artist.token, 'GET /users/me'));
    check(me, { 'me 200': (r) => r.status === 200 });

    const genres = http.get(`${BASE_URL}/api/v1/genres`, auth(artist.token, 'GET /genres'));
    check(genres, { 'genres 200': (r) => r.status === 200 });

    const mine = http.get(`${BASE_URL}/api/v1/artists/me/tracks?size=20`, auth(artist.token, 'GET /artists/me/tracks'));
    check(mine, { 'my tracks 200': (r) => r.status === 200 });

    const track = http.get(`${BASE_URL}/api/v1/tracks/${trackId}`, auth(artist.token, 'GET /tracks/{id}'));
    check(track, {
      'track 200': (r) => r.status === 200,
      // The artist name comes from auth-service (/internal/users); "Unknown artist" means the fallback was used.
      'artist name resolved': (r) => r.status === 200 && r.json('artist.name') !== 'Unknown artist',
    });

    const page = http.get(`${BASE_URL}/api/v1/artists/${artist.id}`, auth(artist.token, 'GET /artists/{id}'));
    check(page, { 'artist page 200': (r) => r.status === 200 });
  });

  sleep(1); // think time of a real user
}

export const handleSummary = summary('01 load: artist dashboard');
