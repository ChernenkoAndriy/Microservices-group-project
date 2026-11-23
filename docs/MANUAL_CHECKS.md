# Manual checks

- [ ] **Catalog legacy test suites hang in the Gradle container.** `InternalTrackControllerTest`, `CatalogGrpcServiceTest`,
  `ArtistDirectoryTest`, `AuthServiceContractTest`, `AuthClientResilienceIntegrationTest` produced no result within 8 min
  (earlier they passed in a targeted rerun). Run `./gradlew test` in `track-service` on your machine and confirm they pass.
  The new `AlbumAndTrackApiIntegrationTest` (6 tests) passes.
- [ ] **Optional steps O2-O4 are implemented** (cover upload, notification preferences/log/templates, follower emails). No SMTP server exists in the cluster, so emails are rendered and logged as SENT but not delivered; set `spring.mail.host` (and `notification.mail.from`) on notification-service to send for real. A failed send is recorded as FAILED once (no `RETRYING` cycle). A duplicate template code returns 409 with the code in the message text, not a separate `code` field. auth-service now publishes `UserSnapshot` (and republishes all users once at startup).
- [ ] **Seed data has two mangled artist names** ("eturn", "uzak Supreme") from an early seed run where ffmpeg consumed the
  script's stdin. Fixed in `seed/seed.sh` (`-nostdin`); existing rows were not deleted (policy). Rename them via `PATCH /users/me`
  as those artists or recreate the DB if you want clean names.
- [ ] Visual look, day and night (http://localhost:3000 after `kubectl port-forward svc/frontend 3000:8080`): pastel palette,
  hard shadows, header sun/grid/bust/palm, scanlines. Playwright screenshots are in `frontend/test-results/`.
- [ ] Audio by ear: log in as `demo@spotty.local` / `Spotty-demo-1997`, press "Play anyway", hear a tone, pause, seek by
  dragging, navigate to Search/Album/Artist and confirm it keeps playing, skip to the next track.
- [ ] Keyboard: Space = play/pause, Left/Right = seek 5 s (not while typing in a field).
- [ ] Mobile layout (browser device toolbar, ~390 px): bottom nav, two-row player bar, no horizontal scroll.
- [ ] Decisions marked **[confirm]** in `docs/DECISIONS.md` (WCAG darker "ink" shades for small text).
- [ ] Recommendation feed: the feed ranking (`FeedBuilder`) is a simple heuristic; confirm it is acceptable. Its unit test
  `FeedBuilderTest` and the service tests for streaming/library were written but Gradle test runs hang here, so only the
  built services were verified live (curl smoke test: token, 206/416 ranges, likes, follows, playlists, play report, chart, feed).
- [ ] Mixed-restore: `kubectl apply -k k8s-local` was run at the end to restore replicas; check `kubectl get pods`.
