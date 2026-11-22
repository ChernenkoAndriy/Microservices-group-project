# Work log — Spotty frontend (vaporwave)

## Current state

- **Task:** docs/FRONTEND_BRIEF.md + CLAUDE.md + Phase-1 answers.
- **Branch:** feature/frontend-vaporwave
- **Steps done:** 1–17, O1. Step 18 partly (replicas restored, MANUAL_CHECKS written).
- **Not done:** O3 (notification preferences/log/templates), O4 (notification consumes TrackPublishedEvent/follows, emails followers). O2 (cover upload) done. STATUS: DONE is withheld because of these.
- **Next step:** O3, then O4; then add `STATUS: DONE` here.
- **Pod replica changes not yet restored:** none (`kubectl apply -k k8s-local` run at the end; frontend added, 1 replica). If you scale observability down again, log it below.
- **How to run Gradle:** `docker run --rm -v spotty-gradle:/root/.gradle -v D:/Microservices-group-project:/work -w /work/<service> eclipse-temurin:25-jdk ./gradlew test --no-daemon --console=plain` (PowerShell). Full suites hang in this container; run single classes.

## Summary of this session

- Catalog: albums, track PATCH/DELETE, outbox, TrackSnapshot/TrackPublishedEvent (6 new integration tests pass).
- Streaming: signed stream tokens + single byte ranges (verified 206/416/401 live).
- Library: likes, follows, playlists, outbox events (verified live).
- Recommendation: Kafka consumers, charts, personal feed, impressions (verified live; fixed ClickHouse timestamp binding).
- Seed Job (ffmpeg audio, 8 artists x 8 albums x 2 tracks, demo listener).
- Frontend: Vite/React/TS, generated API types, vaporwave design, persistent player, all pages; Vitest 28 tests, Playwright 9 tests (Chromium, against the cluster) pass; nginx image deployed as svc/frontend (NodePort 30300).
- Known issues: see docs/MANUAL_CHECKS.md.

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
| 2026-11-22 01:10 | grafana, loki, otel-collector, tempo, alertmanager, webhook-receiver, prometheus 1 → 0; ds otel-collector-logs disabled via nodeSelector | yes (kubectl apply -k k8s-local, 2026-11-22) |

## Step log

### Step 1 — branch and docs (2026-11-22)
- Created branch `feature/frontend-vaporwave` from `grpc-task`; added WORK_LOG, DECISIONS, STUBS.

### Step 2 — cluster bring-up (2026-11-22)
- `minikube start --driver=docker --cpus=4 --memory=8192`; loaded the 7 service images; existing
  `k8s/base/secret.yaml` kept; `kubectl apply -k k8s-local`.
- Scaled observability/monitoring to 0 (see scaling log).
