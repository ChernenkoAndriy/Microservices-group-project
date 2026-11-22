# Manual checks

- [ ] **Catalog legacy test suites hang in the Gradle container.** `InternalTrackControllerTest`, `CatalogGrpcServiceTest`,
  `ArtistDirectoryTest`, `AuthServiceContractTest`, `AuthClientResilienceIntegrationTest` produced no result within 8 min
  (earlier they passed in a targeted rerun). Run `./gradlew test` in `track-service` on your machine and confirm they pass.
  The new `AlbumAndTrackApiIntegrationTest` (6 tests) passes.
