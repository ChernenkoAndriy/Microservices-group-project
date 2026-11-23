// Scenario 3: STRESS test (open model, step-wise arrival rate) - where does the chain
// gateway -> catalog-service -> auth-service (GET /internal/users, the contract-tested call) break?
//
// GET /api/v1/artists/{id} and GET /api/v1/tracks/{id} both make catalog-service call auth-service for the
// artist name. The rate goes up in steps; every request is tagged with its step, so the summary shows p95, error
// rate and status classes per step: the step where they jump is the breaking point. We also watch whether
// catalog's resilience (bulkhead 20 / circuit breaker on auth-service) degrades to the "Unknown artist" fallback
// instead of failing. Requests rotate over 30 users, so the per-user gateway rate limit (20/s each) is not the
// bottleneck below 600 req/s.
import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Counter, Rate } from 'k6/metrics';
import { BASE_URL, auth, createArtists, phaseOf, phaseThresholds, statusClass, summary } from './lib.js';

const fallbackRate = new Rate('artist_name_fallback');
const responses = new Counter('responses');

const STAGES = [
  { duration: '15s', target: 10 },  { duration: '45s', target: 10 },   // step 1:  10 req/s
  { duration: '15s', target: 20 },  { duration: '45s', target: 20 },   // step 2:  20 req/s
  { duration: '15s', target: 35 },  { duration: '45s', target: 35 },   // step 3:  35 req/s
  { duration: '15s', target: 50 },  { duration: '45s', target: 50 },   // step 4:  50 req/s
  { duration: '15s', target: 75 },  { duration: '45s', target: 75 },   // step 5:  75 req/s
  { duration: '15s', target: 100 }, { duration: '45s', target: 100 },  // step 6: 100 req/s
  { duration: '20s', target: 0 },
];
// The ramp into a step belongs to that step
const PHASES = ['s1_10rps', 's1_10rps', 's2_20rps', 's2_20rps', 's3_35rps', 's3_35rps', 's4_50rps', 's4_50rps',
  's5_75rps', 's5_75rps', 's6_100rps', 's6_100rps', 'rampdown'];

export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  setupTimeout: '6m',
  scenarios: {
    catalog_stress: {
      executor: 'ramping-arrival-rate',
      startRate: 5,
      timeUnit: '1s',
      preAllocatedVUs: 100,
      maxVUs: 500,
      stages: STAGES,
    },
  },
  thresholds: Object.assign({
    // Deliberately strict: a stress test is expected to cross them; the per-step table shows where.
    'http_req_duration{scenario:catalog_stress}': ['p(95)<800'],
    'http_req_failed{scenario:catalog_stress}': ['rate<0.05'],
    artist_name_fallback: ['rate<0.05'],
  }, phaseThresholds(PHASES)),
};

export function setup() {
  return { artists: createArtists(30, 2, 'stress') };
}

export default function (data) {
  const phase = phaseOf(STAGES, PHASES, Date.now() - exec.scenario.startTime);
  const artist = data.artists[Math.floor(Math.random() * data.artists.length)];
  const caller = data.artists[Math.floor(Math.random() * data.artists.length)];
  const byTrack = Math.random() < 0.5 && artist.trackIds.length > 0;

  const params = byTrack
    ? auth(artist.token, 'GET /tracks/{id}') // owners see their draft tracks
    : auth(caller.token, 'GET /artists/{id}');
  params.tags.phase = phase;
  const res = byTrack
    ? http.get(`${BASE_URL}/api/v1/tracks/${artist.trackIds[0]}`, params)
    : http.get(`${BASE_URL}/api/v1/artists/${artist.id}`, params);

  responses.add(1, { phase, cls: statusClass(res.status) });
  const ok = res.status === 200;
  const name = ok ? (byTrack ? res.json('artist.name') : res.json('name')) : null;
  if (ok) {
    fallbackRate.add(name === 'Unknown artist' || name === null, { phase });
  }
  check(res, {
    'status 200': () => ok,
    'not rate limited': (r) => r.status !== 429,
  });
}

export const handleSummary = summary('03 stress: catalog -> auth chain', PHASES);
