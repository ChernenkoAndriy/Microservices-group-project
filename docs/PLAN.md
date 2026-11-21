# Spotty frontend — approved plan

Source: `docs/FRONTEND_BRIEF.md`, `CLAUDE.md` and the Phase-1 answers (implement all missing backend for real,
no teammate exclusions, no migration tool, minikube with 8 GB, Playwright with Chromium only and 1 worker).
Progress details and the scaling log are in `docs/WORK_LOG.md`; decisions in `docs/DECISIONS.md`.

Status values: **done** · **in progress** · **code written, not tested** · **not started**.

| # | Step | Status |
|---|---|---|
| 1 | Branch `feature/frontend-vaporwave` from `grpc-task`; WORK_LOG, DECISIONS, STUBS | done |
| 2 | Cluster: minikube (4 CPU / 8 GB), load images, `kubectl apply -k k8s-local`, scale observability/monitoring to 0 | done |
| 3 | Catalog (B1, B2): albums (`POST /albums`, `GET /albums/{id}`, `GET /artists/{id}/albums`, track ↔ album), outbox + `TrackSnapshot` | in progress (tests running) |
| 4 | Streaming (B3, B4): signed stream tokens (`POST /streaming/{id}/token`), token check and single byte ranges (206/416) on `GET /streaming/{id}` | code written, not tested |
| 5 | Library (B8, B5, B6): Kafka/outbox setup, likes and follows with checks against catalog/auth, like/follow events, k8s env | code written, not tested |
| 6 | Library (B7): playlist contract (`/api/v1/library/playlists/**`) + implementation | code written, not tested |
| 7 | Recommendation (B9, B10): Kafka consumers (likes, track snapshots) into ClickHouse, `GET /charts/top` | not started |
| 8 | Recommendation (B11): `GET /recommendations/me` feed + `RecommendationsServedEvent` impressions | not started |
| 9 | Seed data: ffmpeg-synthesized audio, `spotty-seed` image + k8s Job (8 artists, 8 albums × 2 tracks, demo listener) | not started |
| 10 | Frontend scaffold: Vite + React + TS + Router, openapi-typescript/openapi-fetch, Vite proxy, `.env.example`, fonts, Vitest | not started |
| 11 | Design system: day/night tokens, Card/Button/Window, header art, scanlines, reduced motion, contrast test | not started |
| 12 | App shell + auth: header, sidebar/bottom nav, footer, Login/Register, token refresh, loading/empty/404 copy | not started |
| 13 | Player: persistent `<audio>`, stream tokens, queue, seek, volume, keyboard shortcuts, play reports | not started |
| 14 | Pages: Home (hero, bargain bin, track list + equalizer), Search, Album, Artist (follow), Library, Playlist | not started |
| 15 | Tests: Vitest + Playwright (Chromium, headless, 1 worker) against the real cluster | not started |
| 16 | Container: multi-stage Dockerfile, nginx (SPA fallback, `/api` proxy), `k8s/services/frontend.yaml`, load + verify in cluster | not started |
| 17 | Docs: `docs/FRONTEND.md`, final `STUBS.md` and `DECISIONS.md` | not started |
| O1 | Catalog: `PATCH`/`DELETE /tracks/{id}` + `TrackPublishedEvent` | in progress (done together with step 3) |
| O2 | Streaming: `POST /media/{id}/cover`, catalog picks up `coverUrl` | not started |
| O3 | Notification: preferences GET/PUT, delivery log, template CRUD | not started |
| O4 | Notification: consume `TrackPublishedEvent`, follows, `UserSnapshot`; email followers | not started |
| 18 | Wrap-up: restore replicas, `docs/MANUAL_CHECKS.md`, `STATUS: DONE` in WORK_LOG | not started |
