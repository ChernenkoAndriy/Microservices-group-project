// Shared helpers of the k6 scenarios. All traffic goes through the api-gateway, like a real client.
import http from 'k6/http';
import { check, sleep, fail } from 'k6';

export const BASE_URL = __ENV.BASE_URL || 'http://api-gateway:8080';
export const RUN_ID = __ENV.RUN_ID || `${Date.now()}`;
export const PASSWORD = 'LoadTest-Passw0rd!';

const JSON_HEADERS = { 'Content-Type': 'application/json' };

/** POST with retries on 429, so the setup phase is not broken by the gateway rate limiter (50 burst, 20/s). */
function postWithBackoff(url, body, params) {
  for (let attempt = 0; attempt < 10; attempt++) {
    const res = http.post(url, body, params);
    if (res.status !== 429) {
      return res;
    }
    sleep(Number(res.headers['Retry-After'] || 1));
  }
  fail(`still rate limited: POST ${url}`);
}

export function register(email, displayName, role) {
  const res = postWithBackoff(`${BASE_URL}/api/v1/auth/register`,
    JSON.stringify({ email, password: PASSWORD, displayName, role }),
    { headers: JSON_HEADERS, tags: { name: 'setup register' } });
  if (res.status !== 201 && res.status !== 409) {
    fail(`register ${email}: ${res.status} ${res.body}`);
  }
}

export function login(email) {
  const res = postWithBackoff(`${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ email, password: PASSWORD }),
    { headers: JSON_HEADERS, tags: { name: 'setup login' } });
  if (res.status !== 200) {
    fail(`login ${email}: ${res.status} ${res.body}`);
  }
  return res.json('accessToken');
}

export function auth(token, name) {
  return { headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }, tags: { name } };
}

/** Registers `count` artists, logs them in and creates `tracksPerArtist` draft tracks for each one. */
export function createArtists(count, tracksPerArtist, prefix) {
  const genres = http.get(`${BASE_URL}/api/v1/genres`, { tags: { name: 'setup genres' } }).json();
  const genreSlug = genres.length ? genres[0].slug : 'rock';
  const artists = [];
  for (let i = 0; i < count; i++) {
    const email = `${prefix}-${RUN_ID}-${i}@load.test`;
    register(email, `${prefix} artist ${i} ${RUN_ID}`, 'ARTIST');
    const token = login(email);
    const me = http.get(`${BASE_URL}/api/v1/users/me`, auth(token, 'setup me')).json();
    const trackIds = [];
    for (let t = 0; t < tracksPerArtist; t++) {
      const res = postWithBackoff(`${BASE_URL}/api/v1/tracks`,
        JSON.stringify({ title: `Load track ${i}-${t}`, genreSlugs: [genreSlug] }),
        auth(token, 'setup create track'));
      check(res, { 'setup: track created': (r) => r.status === 201 });
      if (res.status === 201) {
        trackIds.push(res.json('id'));
      }
    }
    artists.push({ id: me.id, email, token, trackIds });
  }
  return artists;
}

/** Plain text + JSON summary; the runner script cuts the JSON block out of the pod log. */
export function summary(name, order = []) {
  return (data) => {
    const m = data.metrics;
    const v = (metric, key) => (m[metric] && m[metric].values[key] !== undefined ? m[metric].values[key] : null);
    const fmt = (x) => (x === null ? '-' : Number(x).toFixed(2));
    const lines = [
      `==== ${name} ====`,
      `requests          : ${v('http_reqs', 'count')} (${fmt(v('http_reqs', 'rate'))}/s)`,
      `iterations        : ${v('iterations', 'count')} (dropped: ${v('dropped_iterations', 'count') || 0})`,
      `failed rate       : ${fmt(100 * (v('http_req_failed', 'rate') || 0))} %`,
      `duration avg/med  : ${fmt(v('http_req_duration', 'avg'))} / ${fmt(v('http_req_duration', 'med'))} ms`,
      `duration p90/p95  : ${fmt(v('http_req_duration', 'p(90)'))} / ${fmt(v('http_req_duration', 'p(95)'))} ms`,
      `duration p99/max  : ${fmt(v('http_req_duration', 'p(99)'))} / ${fmt(v('http_req_duration', 'max'))} ms`,
      `checks passed     : ${fmt(100 * (v('checks', 'rate') || 0))} %`,
      'thresholds:',
    ];
    for (const [metric, def] of Object.entries(m)) {
      if (def.thresholds) {
        for (const [expr, res] of Object.entries(def.thresholds)) {
          if (!expr.endsWith('>=0')) { // ">=0" thresholds only create the per-phase sub-metrics
            lines.push(`  ${res.ok ? 'PASS' : 'FAIL'}  ${metric}: ${expr}`);
          }
        }
      }
    }
    // Per-phase table (scenarios that tag requests with {phase})
    const phases = Object.keys(m).map((k) => (k.match(/^http_req_duration\{phase:([^}]+)\}$/) || [])[1]).filter(Boolean);
    phases.sort((a, b) => order.indexOf(a) - order.indexOf(b));
    if (phases.length) {
      const pad = (x, n) => String(x).padStart(n);
      lines.push('per phase:', `  ${'phase'.padEnd(10)} ${pad('reqs', 6)} ${pad('2xx', 6)} ${pad('429', 6)} ${pad('5xx', 6)}`
        + ` ${pad('err', 5)} ${pad('failed%', 8)} ${pad('med ms', 8)} ${pad('p95 ms', 8)} ${pad('p99 ms', 8)}`);
      for (const p of phases) {
        const d = `http_req_duration{phase:${p}}`;
        const c = (cls) => v(`responses{phase:${p},cls:${cls}}`, 'count') || 0;
        const total = ['2xx', '4xx', '429', '5xx', 'err'].reduce((s, cls) => s + c(cls), 0);
        lines.push(`  ${p.padEnd(10)} ${pad(total, 6)} ${pad(c('2xx'), 6)} ${pad(c('429'), 6)} ${pad(c('5xx'), 6)}`
          + ` ${pad(c('err'), 5)} ${pad(fmt(100 * (v(`http_req_failed{phase:${p}}`, 'rate') || 0)), 8)}`
          + ` ${pad(fmt(v(d, 'med')), 8)} ${pad(fmt(v(d, 'p(95)')), 8)} ${pad(fmt(v(d, 'p(99)')), 8)}`);
      }
    }
    return { stdout: `${lines.join('\n')}\n###K6_SUMMARY_JSON###\n${JSON.stringify(data)}\n###K6_SUMMARY_END###\n` };
  };
}

/**
 * Tags of the current phase of a ramping scenario: `stages` are the executor stages, `names` the label of each.
 * Requests are tagged {phase}, the custom counter `responses` also {cls: 2xx|4xx|429|5xx|err}, and `phaseThresholds` declares sub-metrics per phase
 * so the summary contains p95 / error rate / status classes for every phase (the step where the system breaks).
 */
export function phaseOf(stages, names, elapsedMs) {
  let end = 0;
  for (let i = 0; i < stages.length; i++) {
    end += parseInt(stages[i].duration, 10) * 1000;
    if (elapsedMs < end) {
      return names[i];
    }
  }
  return names[names.length - 1];
}

export function statusClass(status) {
  if (status === 0) return 'err';
  if (status === 429) return '429';
  if (status >= 500) return '5xx';
  if (status >= 400) return '4xx';
  return '2xx';
}

export function phaseThresholds(names) {
  const t = {};
  for (const p of [...new Set(names)]) {
    t[`http_req_duration{phase:${p}}`] = ['max>=0'];
    t[`http_req_failed{phase:${p}}`] = ['rate>=0'];
    for (const c of ['2xx', '429', '5xx', 'err']) {
      t[`responses{phase:${p},cls:${c}}`] = ['count>=0'];
    }
  }
  return t;
}
