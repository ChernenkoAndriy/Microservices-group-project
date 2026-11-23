# catalog-service (`track-service/`)

Tracks, artists and genres. Compose and k8s name: `catalog-service`. REST API on port **8082**
(`api-contracts/catalog-service/openapi.yaml`, `internal-openapi.yaml`).

## gRPC

catalog-service is also a gRPC server, on port **9090** (`GRPC_SERVER_PORT`), for other services only.
streaming-service is its client. The REST API stays for external clients.

- **Contract:** [`api-contracts/catalog-service/grpc/catalog_internal.proto`](../api-contracts/catalog-service/grpc/catalog_internal.proto),
  package `spotty.catalog.v1`, service `CatalogInternalService`. Both services generate their Java code from this
  file at build time (Gradle plugin `com.google.protobuf`, output in `build/generated/sources/proto`, not committed).
- **Server:** `grpc/CatalogGrpcService` (Spring gRPC `@GrpcService`). `CatalogGrpcExceptionHandler` maps errors:
  unknown track → `NOT_FOUND`, invalid id → `INVALID_ARGUMENT`. `GrpcCallLoggingInterceptor` logs every call with
  method, status, duration and attempt number (from the `grpc-previous-rpc-attempts` header that a retrying client sends).
- **Auth:** like `/internal/**`, every call to `CatalogInternalService` needs the service token in the
  `x-internal-token` metadata header (`INTERNAL_API_TOKEN`, `dev-internal-token` locally). Reflection and health
  are open.
- **Reflection** is on by default (`GRPC_REFLECTION_ENABLED=false` turns it off), so grpcurl and Postman can list
  the API without the `.proto` file.

| RPC | Kind | What it does |
|---|---|---|
| `GetTrackInfo` | unary | Owner, status, title, duration and play count of a track in any status |
| `ListArtistTracks` | server streaming | One `TrackInfo` per track of an artist, oldest first |
| `ReportPlays` | client streaming | Adds one play per `PlayEvent` to the tracks' `play_count` (all or nothing), answers with a `PlaySummary` |
| `WatchTrackStatus` | bidirectional streaming | Answers every `WatchTrackStatusRequest` with the track's current status |

### grpcurl examples

With the service running locally (`-plaintext`, no TLS):

```bash
# List services and describe the API (server reflection)
grpcurl -plaintext localhost:9090 list
grpcurl -plaintext localhost:9090 describe spotty.catalog.v1.CatalogInternalService

# Unary
grpcurl -plaintext -H 'x-internal-token: dev-internal-token' \
  -d '{"track_id": 1}' \
  localhost:9090 spotty.catalog.v1.CatalogInternalService/GetTrackInfo

# Server streaming: one JSON object per track
grpcurl -plaintext -H 'x-internal-token: dev-internal-token' \
  -d '{"artist_id": 7}' \
  localhost:9090 spotty.catalog.v1.CatalogInternalService/ListArtistTracks

# Client streaming: several messages in, one summary out
grpcurl -plaintext -H 'x-internal-token: dev-internal-token' \
  -d '{"track_id": 1, "user_id": 42, "listened_seconds": 180, "played_at": "2026-11-11T10:00:00Z"}
      {"track_id": 1, "user_id": 43, "listened_seconds": 95, "played_at": "2026-11-11T10:05:00Z"}
      {"track_id": 2, "user_id": 42, "listened_seconds": 30, "played_at": "2026-11-11T10:06:00Z"}' \
  localhost:9090 spotty.catalog.v1.CatalogInternalService/ReportPlays

# Bidirectional streaming: one status update per request
grpcurl -plaintext -H 'x-internal-token: dev-internal-token' \
  -d '{"track_id": 1} {"track_id": 2}' \
  localhost:9090 spotty.catalog.v1.CatalogInternalService/WatchTrackStatus
```

Without the token every call ends with `UNAUTHENTICATED`. An unknown `track_id` gives `NOT_FOUND`, and `0` or a
missing id gives `INVALID_ARGUMENT`.

In Postman: New → gRPC, URL `localhost:9090`, "Use server reflection", add the metadata `x-internal-token`.

### Client side (streaming-service)

The channel `catalog` is set in `streaming-service/src/main/resources/application.yaml`
(`spring.grpc.client.channel.catalog`):

- target `${CATALOG_GRPC_TARGET:dns:///catalog-service:9090}`
- `round_robin` load balancing
- default deadline 3s
- plaintext
- retries: up to 3 attempts on `UNAVAILABLE` (backoff 100ms × 2, at most 1s) for `GetTrackInfo`,
  `ListArtistTracks` and `WatchTrackStatus`. `ReportPlays` is never retried because it is not idempotent.
