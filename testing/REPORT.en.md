# Testing report: contract and load tests

Date of runs: 2026-11-20. How to run everything: [`README.md`](README.md).

| Task | Result |
|---|---|
| Contract test that works with two different services | Pact JVM 4.7.7. Consumer **catalog-service** (track-service) and provider **auth-service**, interaction `GET /internal/users?ids=`: 2/2 consumer tests and 2/2 provider verifications pass |
| A contract change on one side makes the test fail | Shown in both directions: a provider change and a consumer change each FAIL the provider verification (`testing/contract/demo-breaking-change.sh`) |
| 3 load-testing scenarios created and run | k6 1.3.0 in minikube: **load** (artist dashboard), **spike** (login burst), **stress** (catalog -> auth chain) |
| Report with run data and results | This document. Raw data is in `testing/load/results/` and `testing/contract/results/` |

---

## 1. Contract testing (Pact, consumer-driven)

### 1.1 What is tested

catalog-service shows the artist name on tracks and artist pages. It does not store users: it fetches them from
auth-service in one batch call (`ArtistDirectory` -> generated `InternalUsersApi` client -> `GET /internal/users`).
The same call appears in the load tests below.

```
catalog-service (consumer)                 pacts/catalog-service-auth-service.json             auth-service (provider)
AuthServiceContractTest  --writes-->  2 interactions, matchers, provider states  --replayed by-->  AuthServiceProviderContractTest
  real Spring context:                                                                            real web layer:
  AuthClientConfig (RestClient, X-Internal-Token,                                                 InternalTokenFilter, SecurityConfig,
  app JSON mapper with fail-on-unknown-properties),                                               InternalAuthController, GlobalExceptionHandler,
  ArtistDirectory (resilience4j)                                                                  InternalUserMapper, app Jackson config
  -> Pact mock server on :18081                                                                    -> Spring7MockMvcTestTarget, @State stubs the DB
```

Interactions in the pact:

| Interaction | Provider state | Request | Expected response (matchers) |
|---|---|---|---|
| a batch lookup of existing users | `users 7 (ARTIST) and 8 (LISTENER) exist` | `GET /internal/users?ids=7&ids=8`, header `X-Internal-Token` (regex `.+`) | 200, `application/json`, array of 2 objects: `id` number, `email` / `displayName` string, `role` regex `LISTENER\|ARTIST\|ADMIN`, `status` regex `ACTIVE\|BLOCKED`, `createdAt` ISO-8601 regex |
| a batch lookup of an unknown user | `no user with id 999 exists` | `GET /internal/users?ids=999` | 200, `[]` (unknown ids are skipped, not 404) |

The consumer test also asserts what catalog-service does with the answer: artist names resolved, `isArtist(7)` true,
`isArtist(8)` false, an unknown id shown as "Unknown artist", and `available == true` (no fallback). Retries are switched
off in the test, so a mismatch cannot be hidden by resilience4j.

Matchers are used instead of exact values. The provider can return other names, dates or ids, but not other
types, field names or enum values.

### 1.2 Result

| Test | Command | Result |
|---|---|---|
| Consumer (catalog-service) | `cd track-service && ./gradlew contractTest` | 2 tests, 0 failures. Writes `pacts/catalog-service-auth-service.json` |
| Provider (auth-service) | `cd auth-service && ./gradlew contractTest` | 2 interactions verified, 0 failures |

### 1.3 A contract change on one side fails the test

`testing/contract/demo-breaking-change.sh` makes each change, runs the tests and reverts the change.
Output (`testing/contract/results/demo-output.txt`):

```
== 0. Baseline
   consumer (catalog-service) test : PASSED
   provider (auth-service) verify  : PASSED
== 1. Provider renames the JSON field displayName -> display_name (auth-service internal-openapi.yaml)
   provider (auth-service) verify  : FAILED   <- expected FAILED
      | Actual map is missing the following keys: displayName
== 2. Consumer starts to require avatarUrl (catalog-service consumer test)
   consumer (catalog-service) test : PASSED   (the consumer itself is fine)
   provider (auth-service) verify  : FAILED   <- expected FAILED
      | Actual map is missing the following keys: avatarUrl
== 3. Restore and re-verify
   consumer (catalog-service) test : PASSED
   provider (auth-service) verify  : PASSED
```

- **Provider-side change.** auth-service renames the JSON field `displayName` to `display_name`. It is a
  typical snake_case clean-up, and the Java getter stays `getDisplayName()`. auth-service still compiles, and its own unit tests pass because they
  use Java getters. Only the Pact verification catches that catalog-service would stop showing artist names in production.
- **Consumer-side change.** catalog-service starts to expect `avatarUrl` in every user. Its own test is green
  (the mock returns what it was told), but auth-service omits `avatarUrl` for users without an avatar (`non_null`
  inclusion), so the provider verification fails. Without the pact this would surface only after deployment.

In CI the provider verification would run on every auth-service PR, with the pact coming from a Pact Broker
(`can-i-deploy`). Here the pact is shared through the repository folder `pacts/` (`@PactFolder("../pacts")`).

### 1.4 Tool choice

- **Pact JVM 4.7.7.** Its `spring7` module supports Spring Boot 4.1 / Java 25, which this project uses. Pact is the tool
  named in lecture 10 (consumer test `V4Pact` plus provider verification `@Provider` / `@PactFolder`).
- **stubborn-contract** (the continuation of Spring Cloud Contract) was considered. It is only published as
  `0.1.0-SNAPSHOT`, with no release yet, so it was not used.

---

## 2. Load testing (k6)

### 2.1 Environment

| | |
|---|---|
| Host | Windows 11, 4 logical CPUs, 20 GB RAM; Docker Desktop (WSL2 limited to 14 GB in `%USERPROFILE%\.wslconfig`) |
| Cluster | minikube v1.39.0, docker driver, **4 CPU / 12 GB**, containerd, metrics-server |
| Deployed | `kubectl apply -k k8s-local`: 1 replica per service. Each service pod: CPU request 100m / **limit 750m**, memory 384Mi / 768Mi |
| Scaled to 0 during the runs | library, notification, recommendation and streaming services, their databases, grafana, loki, tempo. They are not on the tested paths, and on 4 CPUs they starved the services under test |
| Load generator | `grafana/k6:1.3.0` as a Kubernetes Job in the same cluster (`testing/load/k6-job.yaml`), CPU limit 1. It calls `http://api-gateway:8080` like a real client: JWT checks, rate limiting, circuit breakers and bulkheads all apply |
| Measured | k6 summary (`summary.txt`, `summary.json`), `kubectl top pods` every 15 s (`resources.txt`), grouped WARN/ERROR logs of gateway, catalog and auth and the restart counts (`service-warnings.txt`) |

All absolute numbers come from a laptop cluster: 3 JVMs, PostgreSQL, Kafka and Prometheus share 4 CPUs. Each
service is also capped at 0.75 CPU. Use the numbers to compare scenarios and to find bottlenecks, not as production capacity.

### 2.2 Scenarios

| # | Type | Script | Load model | What it checks | Thresholds |
|---|---|---|---|---|---|
| 1 | **Load** | `01-load-artist-dashboard.js` | closed, `ramping-vus`: 0 -> 20 VUs (30 s), hold 2 min, -> 0 (20 s); 1 s think time | Everyday traffic: 20 artists open their dashboard. One page = 5 calls: `GET /users/me`, `/genres`, `/artists/me/tracks`, `/tracks/{id}`, `/artists/{id}` (3 of them call catalog -> auth `/internal/users`) | p95 < 500 ms, p99 < 1 s, errors < 1 %, checks > 99 %, page p95 < 2 s |
| 2 | **Spike** | `02-spike-login.js` | open, `ramping-arrival-rate`: 2/s (30 s) -> **60/s in 10 s**, hold 40 s -> 2/s, recovery 60 s | A burst of logins (BCrypt) from one client IP. The gateway rate limiter (50 burst, 20/s per IP) should absorb it with 429 + `Retry-After`, auth-service should never return 5xx, and the system should recover | 0 × 5xx, p95 of successful logins < 1.5 s, rate limiter active, every answer is 200 or 429 |
| 3 | **Stress** | `03-stress-catalog-auth.js` | open, `ramping-arrival-rate` in steps: **10 / 20 / 35 / 50 / 75 / 100 req/s**, 15 s ramp + 45 s hold each | Where does gateway -> catalog -> auth (`/internal/users`, the contract-tested call) break? Does it degrade gracefully (catalog falls back to "Unknown artist", gateway bulkhead and circuit breaker) or collapse? 30 users rotate, so the per-user limit is not the bottleneck | p95 < 800 ms, errors < 5 %, fallback < 5 %. Deliberately strict: the per-step table shows where they are crossed |

Scenarios 2 and 3 use the open model (arrival rate). k6 keeps sending at the target rate even when responses get
slow, so the results are not distorted by coordinated omission (lecture 10). `dropped_iterations` counts the
arrivals k6 could not start because all VUs were busy.

### 2.3 Runs

| Run | Time | What ran | Why |
|---|---|---|---|
| #1 | 12:44-12:50 | scenario 1 | first run, original manifests |
| #2 | 13:01-13:16 | scenarios 1, 2, 3 | after the probe fix from run #1 (see 2.4) |
| #3 | 13:18-13:31 | scenarios 2, 3 | per-step and per-phase tagging added to the scripts |
| #4 | 13:33-13:45 | scenarios 1, 2 | runner waits for healthy services + 60 s cool-down; spike baseline still 5/s |
| #5 | 13:55-14:09 | scenarios 2, 3 | final spike with baseline 2/s (under the measured capacity), final stress |

Data: `testing/load/results/run1-before-probe-fix`, `run2-after-probe-fix`, `run3-phased`, `run4-spike-baseline5`.
The latest run of each scenario is in `testing/load/results/<scenario>/`.

### 2.4 Scenario 1: load (artist dashboard)

| Metric | Run #1 (original probes) | **Run #2 (probe fix)** | Run #4 (probe fix, node stall) |
|---|---|---|---|
| Requests / rate | 2856 / 11.6 req/s | **2311 / 9.9 req/s** | 1856 / 8.5 req/s |
| Failed requests | **52.1 %** | **0.00 %** | 0.81 % |
| Checks passed | 44.3 % | **100 %** | 98.0 % |
| Latency med / p95 / p99 | 521 / 2776 / - ms | **987 / 2470 / 3064 ms** | 864 / 4048 / 14067 ms |
| Max | 10052 ms | 7124 ms | 25979 ms |
| Page (5 calls) p95 | > 2 s | 8.8 s | > 2 s |
| Peak CPU gateway / catalog / auth | 603m / 599m / 521m | **636m / 746m / 745m** (limit 750m) | 537m / 494m / 231m |
| Pod restarts during the run | catalog-service x1 | 0 | 0 |
| Thresholds | 0/5 passed | errors and checks PASS; latency FAIL | errors PASS; checks and latency FAIL |

**Finding 1 (run #1): the liveness probe kills a healthy pod under load.** The probes call the full
`/actuator/health` (DB + Kafka health checks) with a 1 s timeout. Under load the CPU-throttled catalog-service answered
the probe too slowly. kubelet restarted it ("failed liveness probe, will be restarted"), and the gateway circuit
breaker `catalog-service` opened: 382 × `CallNotPermittedException`, and 52 % of requests failed.
Fix (`testing/load/probe-patch.sh`): liveness -> `/actuator/health/liveness`, readiness -> `/actuator/health/readiness`
(Spring Boot probe groups, which do not depend on DB/Kafka), timeout 3 s. Run #2 then had **0 errors and 100 % checks**.
Recommendation: put the same change into `k8s/services/*.yaml`.

**Finding 2: latency is limited by the per-pod CPU limit.** In run #2 catalog-service and auth-service run at
745-746m of their 750m limit at only ~10 req/s. Every request is throttled by the CPU quota, so p95 is 2.5 s instead of < 500 ms.
Errors are still 0, so the system is slow but correct. More replicas or a higher CPU limit (HPA on CPU) would address this.
Request-per-request cost is also raised by 100 % trace sampling and JSON logging.

**Run #4: graceful degradation.** Mid-run the whole minikube node froze for about 46 s. Hikari logged
`Thread starvation or clock leap detected (housekeeper delta=46s)` and Kafka connections dropped at the same time. catalog-service's circuit
breaker `auth-service` opened (CLOSED -> OPEN -> HALF_OPEN -> CLOSED in the log). For 65 lookups catalog-service served
"Unknown artist" instead of failing, so the error rate stayed at 0.81 % and only the "artist name resolved" check dropped. This is the
resilience4j fallback working as designed.

### 2.5 Scenario 2: spike (login burst)

Final run (#5), baseline 2/s, spike 60/s:

| Phase | Requests | 200 | 429 | 5xx | Failed % | med | p95 | p99 |
|---|---|---|---|---|---|---|---|---|
| baseline (2/s) | 60 | 60 | 0 | 0 | **0.0 %** | 840 ms | 2742 ms | 3454 ms |
| spike (-> 60/s) | 2487 | 23 | **1493** | **971** | 39.0 % | 256 ms | 5277 ms | 13650 ms |
| recovery (2/s, 70 s) | 428 | 114 | 139 | 175 | 40.9 % | 206 ms | 1626 ms | 4876 ms |
| **total** | 2985 (18.8 req/s) | | | | 38.4 % | 266 ms | 4305 ms | 13393 ms |

224 arrivals dropped. Thresholds: rate limiter active PASS, 0 × 5xx FAIL, p95 of successful logins FAIL, checks 87 % FAIL.
Peak CPU of auth-service 638m (limit 750m). Gateway log: 816 × `Rate limit exceeded for ip`,
804 × `CircuitBreaker 'auth-service' is OPEN`, 503 × `HALF_OPEN`, 183 × I/O errors to auth-service.

**Finding 3: the rate limiter works but is set above what auth-service can handle.**
- A single login on an idle system takes **about 300 ms** (measured with curl in the cluster, through the gateway and directly). That is
  BCrypt strength 10 plus the JWT and the refresh-token write, on a 0.75-CPU pod, which gives a capacity of roughly **3 logins/s per pod**.
- The gateway lets **20/s per IP** through (50 burst). The limiter answered 1493 requests with proper 429 + `Retry-After`,
  but the ~20/s it admitted were still about 6x over auth-service's capacity. Requests queued, and the DB pool ran out:
  `HikariPool-1 - Connection is not available, request timed out after 30024ms (total=10, active=10, waiting=4)`.
  The cause is that `AuthService.login()` is `@Transactional`, so a DB connection is held during the CPU-bound BCrypt check.
  Calls then exceeded the gateway read timeout (10 s) and slow-call threshold (5 s). The gateway circuit breaker opened and returned 503 fallbacks.
- Recovery is slow: after the spike ended, the backlog kept failing 41 % of requests for over a minute.
- In runs #3 and #4 the baseline was 5/s. That is already above capacity, so 34 % (run #3) and 83 % (run #4) of the *baseline*
  failed. This confirms the capacity limit; it is not a spike effect.

Recommendations: a separate, lower limit for `POST /api/v1/auth/login` (about 2-3/s per IP), with the global 20/s kept for
the other routes. Verify the password outside the DB transaction, so the connection is not held while BCrypt runs. Run 2+ auth replicas
with an HPA on CPU. Keep BCrypt strength at 10, but size the pods for it.

### 2.6 Scenario 3: stress (gateway -> catalog -> auth)

Final run (#5) per step (`results/03-stress-catalog-auth/summary.txt`), with run #3 for comparison:

| Step | Run #5 requests | 2xx | 5xx | Failed % | med | p95 | p99 | Run #3 failed % / p95 |
|---|---|---|---|---|---|---|---|---|
| 10 req/s | 539 | 224 | 315 | 58.4 %* | 468 ms | 11947 ms | 19741 ms | **0.0 % / 1083 ms** |
| 20 req/s | 1125 | 1118 | 7 | **0.6 %** | 75 ms | **988 ms** | 1596 ms | 21.3 % / 8267 ms |
| 35 req/s | 1725 | 1291 | 434 | 25.2 % | 102 ms | 7769 ms | 13463 ms | 52.5 % / 7367 ms |
| 50 req/s | 2882 | 2003 | 879 | 30.5 % | 201 ms | 4332 ms | 9595 ms | 70.3 % / 24215 ms |
| 75 req/s | 3449 | 2256 | 1193 | 34.6 % | 1527 ms | 9724 ms | 12564 ms | 86.1 % (gateway restarted) |
| 100 req/s | 4757 | 1928 | 2829 | 59.5 % | 4008 ms | 11161 ms | 13899 ms | 100 % (connection errors) |
| **total** | 15596 (35.3 req/s) | | | 38.6 % | 1327 ms | 9976 ms | 13485 ms | 60.7 % / 18302 ms |

\* Warm-up anomaly: the 10 req/s step ran right after the setup phase (30 registrations and logins plus 60 track creations), and in run #3 the
same step was clean. The 20 req/s step of the same run is clean again.

Dropped arrivals: 2242 in run #5, 9371 in run #3. Peak CPU in run #5: gateway 644m, catalog 546m, auth 332m. Gateway memory
reached 605Mi of its 768Mi limit. No pod restarts in run #5.

**Finding 4: the breaking point of the chain is about 20-35 req/s on this cluster.** Both runs are clean up to 10-20 req/s
(p95 about 1 s). From about 35 req/s the error rate jumps to 25-50 %, and it keeps rising with load. auth-service is not the
bottleneck (332-508m, far from its limit). catalog-service and the gateway are.

**Finding 5: how the system fails.**
- *Run #3: cascading failure.* At 75 req/s the gateway's Tomcat threads were all waiting on catalog-service, so
  the liveness probe itself timed out, even on `/actuator/health/liveness` with 3 s. kubelet killed the gateway (restart #3),
  and for the last steps every request got a connection error (`err`). One slow downstream service took the
  entry point down. Recommendation: serve actuator on a separate management port (`management.server.port`), so probes do not wait in
  the same thread pool as user traffic, and use a longer `failureThreshold` for the gateway's liveness probe.
- *Run #5: graceful degradation.* The gateway survived. Its bulkhead for catalog-service (`max-concurrent-calls: 100`,
  `max-wait-duration: 0`) rejected the excess at once: 2903 × `BulkheadFullException`, and
  2782 × `CircuitBreaker 'catalog-service' is OPEN` turned into fast 503 responses. Only 115 of about 6000 gateway
  errors (about 2 %) were slow I/O errors to catalog-service.
  catalog-service's own fallback for auth-service was hardly needed: `artist_name_fallback` stayed under 5 % (PASS).
- Two catalog-service warnings, `Error while extracting response for type List<UserSummaryDto>`, appeared only at the
  highest load. The response format itself is correct: a real response was checked with curl and matches the
  pact. The most likely cause is the 2 s read timeout of the auth client (`auth-client.read-timeout`) running out while the
  body was read on a CPU-starved pod. It is not a contract problem.

### 2.7 Summary of findings and recommendations

| # | Finding | Evidence | Recommendation |
|---|---|---|---|
| 1 | Liveness/readiness probes on the full `/actuator/health` with a 1 s timeout kill healthy pods under load | run #1: 52 % errors, catalog restart; run #2 after the fix: 0 % | `/actuator/health/liveness` and `/readiness`, timeout 3 s (`probe-patch.sh`); move into `k8s/services/*.yaml` |
| 2 | Latency is limited by the CPU limit of 750m per pod (CFS throttling) | run #2: catalog/auth at 745m at 10 req/s, p95 2.5 s | more replicas / HPA, higher limit; consider < 100 % trace sampling |
| 3 | Login capacity is about 3/s per auth pod, but the gateway admits 20/s per IP. The DB connection is held during BCrypt | spike: 971 × 5xx, Hikari pool exhausted, slow recovery | separate login rate limit; BCrypt outside `@Transactional`; 2+ auth replicas |
| 4 | Breaking point of gateway -> catalog -> auth is about 20-35 req/s on this cluster | stress per-step table | scale catalog-service and the gateway (both CPU-bound) |
| 5 | Actuator probes share the gateway's thread pool, so a slow downstream got the gateway killed (cascade) | run #3: gateway restart, 100 % connection errors | separate management port for probes; the bulkhead + circuit breaker (run #5) are the right defence |
| + | Circuit breakers, bulkheads, the rate limiter and the "Unknown artist" fallback all worked as designed | run #4 (node stall: 0.81 % errors), run #5 (fast 503 instead of a crash) | keep them, and cover them with alerts (circuit breaker state, 429 rate) |
