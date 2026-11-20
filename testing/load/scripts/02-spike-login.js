// Scenario 2: SPIKE test (open model, ramping arrival rate) - a burst of logins, e.g. after an outage
// or a push notification. Login is the most expensive call of auth-service (BCrypt).
//
// All requests come anonymously from one client IP, so the gateway rate limiter (50 burst, 20/s per IP) has to
// absorb the spike: the excess must get 429 + Retry-After, and auth-service must never answer 5xx or slow down.
// The open model (arrival rate) keeps sending even if responses get slow, so it avoids coordinated omission.
import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { BASE_URL, PASSWORD, phaseOf, phaseThresholds, register, statusClass, summary } from './lib.js';

const USERS = 10;
const rateLimited = new Rate('login_rate_limited');
const serverErrors = new Counter('login_5xx');
const okLatency = new Trend('login_ok_duration', true);
const responses = new Counter('responses');

const STAGES = [
  { duration: '30s', target: 2 },    // baseline, below the measured capacity (~3 logins/s per pod)
  { duration: '10s', target: 60 },   // spike: 30x in 10 s
  { duration: '40s', target: 60 },   // hold the spike
  { duration: '10s', target: 2 },    // drop back
  { duration: '60s', target: 2 },    // recovery: does it come back?
];
const PHASES = ['baseline', 'spike', 'spike', 'recovery', 'recovery'];

export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  setupTimeout: '3m',
  scenarios: {
    login_spike: {
      executor: 'ramping-arrival-rate',
      startRate: 2,
      timeUnit: '1s',
      preAllocatedVUs: 50,
      maxVUs: 300,
      stages: STAGES,
    },
  },
  thresholds: Object.assign({
    login_5xx: ['count==0'],                       // auth-service never breaks
    login_ok_duration: ['p(95)<1500'],             // admitted logins stay fast
    login_rate_limited: ['rate>0'],                // the limiter really kicked in
    'checks{scenario:login_spike}': ['rate>0.99'], // every answer is 200 or a proper 429
    'login_ok_duration{phase:baseline}': ['max>=0'],
    'login_ok_duration{phase:spike}': ['max>=0'],
    'login_ok_duration{phase:recovery}': ['max>=0'],
  }, phaseThresholds(PHASES)),
};

export function setup() {
  const emails = [];
  for (let i = 0; i < USERS; i++) {
    const email = `spike-${__ENV.RUN_ID || Date.now()}-${i}@load.test`;
    register(email, `spike listener ${i} ${__ENV.RUN_ID || ''}`.trim(), 'LISTENER');
    emails.push(email);
  }
  return { emails };
}

export default function (data) {
  const phase = phaseOf(STAGES, PHASES, Date.now() - exec.scenario.startTime);
  const email = data.emails[Math.floor(Math.random() * data.emails.length)];
  const res = http.post(`${BASE_URL}/api/v1/auth/login`, JSON.stringify({ email, password: PASSWORD }),
    { headers: { 'Content-Type': 'application/json' }, tags: { name: 'POST /auth/login', phase },
      // 429 is an expected answer here, not a failed request
      responseCallback: http.expectedStatuses(200, 429) });

  responses.add(1, { phase, cls: statusClass(res.status) });
  rateLimited.add(res.status === 429, { phase });
  if (res.status >= 500) {
    serverErrors.add(1);
  }
  if (res.status === 200) {
    okLatency.add(res.timings.duration, { phase });
  }
  check(res, {
    'login 200 or 429': (r) => r.status === 200 || r.status === 429,
    '200 has a token': (r) => r.status !== 200 || !!r.json('accessToken'),
    '429 has Retry-After': (r) => r.status !== 429 || !!r.headers['Retry-After'],
  });
}

export const handleSummary = summary('02 spike: login burst', PHASES);
