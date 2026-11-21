# Testing: contract tests (Pact) and load tests (k6)

The full report with the run data is in [`REPORT.md`](REPORT.md).

## Contract test (Pact JVM 4.7.7)

Consumer-driven contract between two services:

| Role | Service | Test |
|---|---|---|
| consumer | **catalog-service** (`track-service/`), `ArtistDirectory` -> generated `InternalUsersApi` client | `track-service/src/test/java/.../contract/AuthServiceContractTest.java` |
| provider | **auth-service**, `GET /internal/users?ids=` (`InternalAuthController`) | `auth-service/src/test/java/.../contract/AuthServiceProviderContractTest.java` |

The consumer test writes the pact `pacts/catalog-service-auth-service.json`, and the provider replays it against its web layer.

```bash
cd track-service && ./gradlew contractTest   # consumer: writes ../pacts/catalog-service-auth-service.json
cd auth-service  && ./gradlew contractTest   # provider: verifies every interaction of the pact
```

Both tests are tagged `contract`, so they also run in the normal `./gradlew test`.

Demo: a contract change on either side breaks the test, and the script reverts the change afterwards:

```bash
bash testing/contract/demo-breaking-change.sh    # output + logs: testing/contract/results/
```

## Load tests (k6 1.3, in minikube)

| # | Script | Type | Model |
|---|---|---|---|
| 1 | `load/scripts/01-load-artist-dashboard.js` | load | closed, `ramping-vus` 0 -> 20 -> 0 |
| 2 | `load/scripts/02-spike-login.js` | spike | open, `ramping-arrival-rate` 2 -> 60 -> 2 req/s |
| 3 | `load/scripts/03-stress-catalog-auth.js` | stress | open, `ramping-arrival-rate` 10 / 20 / 35 / 50 / 75 / 100 req/s |

Every scenario runs as a Kubernetes Job (`load/k6-job.yaml`) next to the services and calls `http://api-gateway:8080`.

```bash
minikube start --driver=docker --memory=12288 --cpus=4
minikube addons enable metrics-server
docker compose build && for i in auth-service catalog-service api-gateway; do minikube image load $i:latest; done
cp k8s/base/secret.yaml.example k8s/base/secret.yaml && kubectl apply -k k8s
bash testing/load/probe-patch.sh              # probe fix found in load run #1 (see the report)
bash testing/load/run-load-tests.sh           # all scenarios, or pass one name: 02-spike-login
```

Results go to `testing/load/results/<scenario>/`:
- `k6.log`: the full k6 output
- `summary.txt` / `summary.json`: the k6 summary
- `resources.txt`: `kubectl top pods`, sampled every 15 s
