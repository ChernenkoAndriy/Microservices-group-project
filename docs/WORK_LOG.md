# Work log — Spotty frontend (vaporwave)

## Current state

- **Task:** `docs/FRONTEND_BRIEF.md` + `CLAUDE.md` + Phase-1 answers (implement all missing backend for real,
  no stubs; minikube 8 GB; all steps 1–18 plus optional O1–O4).
- **Branch:** `feature/frontend-vaporwave` (from `grpc-task`)
- **Steps done:** 1, 2
- **Current step:** 3 — catalog albums + outbox + TrackSnapshot (+ O1 track edit/delete, TrackPublishedEvent).
  Code + test (`AlbumAndTrackApiIntegrationTest`) written, NOT yet compiled/tested/committed.
  To do: run tests, build image, deploy, curl, commit.
- **Step 4 code written (uncommitted, untested):** StreamTokenService, ByteRange, StreamingController
  (token + Range), StreamingException(+Handler), ClockConfig, config, k8s STREAM_TOKEN_SECRET env, tests
  (StreamTokenServiceTest, ByteRangeTest, StreamingControllerTest).
- **Step 5 code mostly written (uncommitted, untested):** library build deps, outbox/tracing/exception/
  CorrelationIdFilter copied from catalog, TrackLike/ArtistFollow + repos, CatalogDirectory clients,
  LibraryEvents, LikeService/FollowService, controllers, ClockConfig, KafkaTopicsConfig, application.yaml.
  To do: k8s env for library (KAFKA, CATALOG/AUTH URL, INTERNAL_API_TOKEN), test properties, tests.
- **Next step:** 4 — streaming tokens + ranges
- **How to run Gradle:** local Gradle sees JDK 11 via JAVA_HOME. Use PowerShell (Git Bash mangles paths):
  `docker run --rm -v spotty-gradle:/root/.gradle -v D:/Microservices-group-project:/work -w /work/<service> eclipse-temurin:25-jdk ./gradlew test --no-daemon --console=plain`
- **Pod replica changes not yet restored:** grafana, loki, otel-collector, tempo, alertmanager,
  webhook-receiver, prometheus → 0 (k8s-local value: 1 each); daemonset otel-collector-logs patched with
  nodeSelector `spotty-disabled=true` (restore: `kubectl apply -k k8s-local`).

## Plan (approved)

Full plan with per-step status: `docs/PLAN.md` (update it whenever a step's status changes).

1. Branch and docs
2. Cluster bring-up (minikube 8 GB, k8s-local, scale down observability/monitoring)
3. Catalog: albums + outbox + TrackSnapshot (B1, B2)
4. Streaming: signed stream tokens + byte ranges (B3, B4)
5. Library: setup, likes, follows + events (B8, B5, B6)
6. Library: playlists (contract + implementation) (B7)
7. Recommendation: Kafka consumers + charts (B9, B10)
8. Recommendation: personal feed + impressions (B11)
9. Seed data Job (synthesized audio)
10. Frontend scaffold
11. Design system
12. App shell + auth pages
13. Player
14. Pages
15. Tests (Vitest + Playwright)
16. Container + k8s manifest
17. Docs
O1–O4. Optional backend gaps (catalog track edit/delete + TrackPublishedEvent, cover upload, notification)
18. Wrap-up (restore replicas, MANUAL_CHECKS, STATUS: DONE)

## Scaling log

| When | Change | Restored |
|---|---|---|
| 2026-11-22 01:10 | grafana, loki, otel-collector, tempo, alertmanager, webhook-receiver, prometheus 1 → 0; ds otel-collector-logs disabled via nodeSelector | no |

## Step log

### Step 1 — branch and docs (2026-11-22)
- Created branch `feature/frontend-vaporwave` from `grpc-task`; added WORK_LOG, DECISIONS, STUBS.

### Step 2 — cluster bring-up (2026-11-22)
- `minikube start --driver=docker --cpus=4 --memory=8192`; loaded the 7 service images; existing
  `k8s/base/secret.yaml` kept; `kubectl apply -k k8s-local`.
- Scaled observability/monitoring to 0 (see scaling log).
