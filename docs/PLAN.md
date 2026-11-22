# Spotty frontend — approved plan

Source: `docs/FRONTEND_BRIEF.md`, `CLAUDE.md` and the Phase-1 answers (implement all missing backend for real,
no teammate exclusions, no migration tool, minikube with 8 GB, Playwright with Chromium only and 1 worker).
Progress details and the scaling log are in `docs/WORK_LOG.md`; decisions in `docs/DECISIONS.md`.

Status values: **done** · **in progress** · **code written, not tested** · **not started**.

| # | Step | Status |
|---|---|---|
| 1 | Branch `feature/frontend-vaporwave` from `grpc-task`; WORK_LOG, DECISIONS, STUBS | done |
| 2 | Cluster: minikube (4 CPU / 8 GB), load images, `kubectl apply -k k8s-local`, scale observability/monitoring to 0 | done |
| 3 | Catalog (B1, B2): albums (`POST /albums`, `GET /albums/{id}`, `GET /artists/{id}/albums`, track ↔ album), outbox + `TrackSnapshot` | done (new tests pass; legacy suites hang locally, see MANUAL_CHECKS) |
| 4 | Streaming (B3, B4): signed stream tokens (`POST /streaming/{id}/token`), token check and single byte ranges (206/416) on `GET /streaming/{id}` | done (deployed and smoke-tested) |
| 5 | Library (B8, B5, B6): Kafka/outbox setup, likes and follows with checks against catalog/auth, like/follow events, k8s env | done (deployed and smoke-tested) |
| 6 | Library (B7): playlist contract (`/api/v1/library/playlists/**`) + implementation | done (deployed and smoke-tested) |
| 7 | Recommendation (B9, B10): Kafka consumers (likes, track snapshots) into ClickHouse, `GET /charts/top` | done |
| 8 | Recommendation (B11): `GET /recommendations/me` feed + `RecommendationsServedEvent` impressions | done |
| 9 | Seed data: ffmpeg-synthesized audio, `spotty-seed` image + k8s Job (8 artists, 8 albums × 2 tracks, demo listener) | done |
| 10 | Frontend scaffold: Vite + React + TS + Router, openapi-typescript/openapi-fetch, Vite proxy, `.env.example`, fonts, Vitest | done |
| 11 | Design system: day/night tokens, Card/Button/Window, header art, scanlines, reduced motion, contrast test | done |
| 12 | App shell + auth: header, sidebar/bottom nav, footer, Login/Register, token refresh, loading/empty/404 copy | done |
| 13 | Player: persistent `<audio>`, stream tokens, queue, seek, volume, keyboard shortcuts, play reports | done |
| 14 | Pages: Home (hero, bargain bin, track list + equalizer), Search, Album, Artist (follow), Library, Playlist | done |
| 15 | Tests: Vitest + Playwright (Chromium, headless, 1 worker) against the real cluster | done |
| 16 | Container: multi-stage Dockerfile, nginx (SPA fallback, `/api` proxy), `k8s/services/frontend.yaml`, load + verify in cluster | done |
| 17 | Docs: `docs/FRONTEND.md`, final `STUBS.md` and `DECISIONS.md` | done |
| O1 | Catalog: `PATCH`/`DELETE /tracks/{id}` + `TrackPublishedEvent` | done |
| O2 | Streaming: `POST /media/{id}/cover` (+ public `GET`), catalog picks up `coverUrl` | done (deployed, smoke-tested) |
| O3 | Notification: preferences GET/PUT, delivery log, template CRUD | done (tests pass; deployed) |
| O4 | Notification: consume `TrackPublishedEvent`, follows, `UserSnapshot`; email followers | done (tests pass; auth now publishes `UserSnapshot`) |
| 18 | Wrap-up: restore replicas, `docs/MANUAL_CHECKS.md`, `STATUS: DONE` in WORK_LOG | done: replicas restored, MANUAL_CHECKS written, `STATUS: DONE` in WORK_LOG |
