# Spotty API contracts

OpenAPI 3.0.3 contracts for every service behind the API gateway, written from
[`API_GATEWAY_ENDPOINTS.md`](../API_GATEWAY_ENDPOINTS.md), plus AsyncAPI 3.0 contracts for the Kafka events
from [`ARCHITECTURE_PROPOSAL.md`](../ARCHITECTURE_PROPOSAL.md). They are the source of truth for code generation:
services implement the generated interfaces and DTOs, and clients use generated clients.

## Layout

```
api-contracts/
├── README.md                 this file: shared conventions + codegen setup
├── redocly.yaml              lint config for all contracts
├── common/                   shared building blocks, referenced by every contract
│   ├── schemas.yaml          Id, Email, GenreSlug, PageMetadata, ProblemDetail, FieldError
│   ├── parameters.yaml       page, size
│   ├── responses.yaml        400 / 401 / 403 / 404 / 409 / 422 / 429 error responses
│   └── events.yaml           Kafka headers, eventId, occurredAt, record key
├── auth-service/
│   ├── openapi.yaml          /api/v1/auth/**, /api/v1/users/**, /api/v1/admin/users/**
│   ├── internal-openapi.yaml /internal/users (service-to-service, not routed)
│   └── asyncapi.yaml         sends UserSnapshot
├── catalog-service/
│   ├── openapi.yaml          /api/v1/tracks/**, /api/v1/artists/**, /api/v1/albums/**, /api/v1/genres
│   ├── internal-openapi.yaml /internal/tracks/** (service-to-service, not routed)
│   └── asyncapi.yaml         sends TrackPublishedEvent, TrackSnapshot; receives AssetReadySnapshot
├── library-service/
│   ├── openapi.yaml          /api/v1/library/**
│   └── asyncapi.yaml         sends TrackLiked/UnlikedEvent, ArtistFollowed/UnfollowedEvent
├── streaming-service/
│   ├── openapi.yaml          /api/v1/media/**, /api/v1/streaming/**
│   └── asyncapi.yaml         sends AssetReadySnapshot
├── recommendation-service/
│   ├── openapi.yaml          /api/v1/recommendations/**, /api/v1/analytics/**, /api/v1/charts/**
│   └── asyncapi.yaml         sends and receives RecommendationsServedEvent; receives likes, TrackSnapshot
└── notification-service/
    ├── openapi.yaml          /api/v1/notifications/**, /api/v1/admin/notifications/**, /api/v1/admin/notification-templates/**
    └── asyncapi.yaml         receives TrackPublishedEvent, artist follows, UserSnapshot
```

`catalog-service` is implemented in the `track-service/` directory (its compose and k8s name is `catalog-service`).

Inside each contract the order is always: `info` → `tags` → `paths` (grouped by tag) → `components`
(security schemes, parameters, enums, value types, then request/response DTOs).

## Endpoint index

| Service | Method | Path | Auth | operationId |
|---|---|---|---|---|
| auth | POST | `/api/v1/auth/register` | public | `register` |
| auth | POST | `/api/v1/auth/login` | public | `login` |
| auth | POST | `/api/v1/auth/refresh` | public | `refreshTokens` |
| auth | POST | `/api/v1/auth/logout` | user | `logout` |
| auth | GET | `/api/v1/users/me` | user | `getMyProfile` |
| auth | PATCH | `/api/v1/users/me` | user | `updateMyProfile` |
| auth | GET | `/api/v1/admin/users` | admin | `listUsers` |
| auth | PATCH | `/api/v1/admin/users/{userId}` | admin | `updateUser` |
| catalog | GET | `/api/v1/tracks` | public | `searchTracks` |
| catalog | POST | `/api/v1/tracks` | artist | `createTrack` |
| catalog | GET | `/api/v1/tracks/{trackId}` | public | `getTrack` |
| catalog | PATCH | `/api/v1/tracks/{trackId}` | artist (owner) | `updateTrack` |
| catalog | DELETE | `/api/v1/tracks/{trackId}` | artist (owner) / admin | `deleteTrack` |
| catalog | GET | `/api/v1/artists/me/tracks` | artist | `listMyTracks` |
| catalog | GET | `/api/v1/artists/{artistId}` | public | `getArtist` |
| catalog | GET | `/api/v1/artists/{artistId}/tracks` | public | `listArtistTracks` |
| catalog | GET | `/api/v1/artists/{artistId}/albums` | public | `listArtistAlbums` |
| catalog | POST | `/api/v1/albums` | artist | `createAlbum` |
| catalog | GET | `/api/v1/albums/{albumId}` | public | `getAlbum` |
| catalog | GET | `/api/v1/genres` | public | `listGenres` |
| library | GET | `/api/v1/library/likes` | user | `listMyLikes` |
| library | POST | `/api/v1/library/likes/{trackId}` | user | `likeTrack` |
| library | DELETE | `/api/v1/library/likes/{trackId}` | user | `unlikeTrack` |
| library | GET | `/api/v1/library/follows` | user | `listMyFollows` |
| library | POST | `/api/v1/library/follows/{artistId}` | user | `followArtist` |
| library | DELETE | `/api/v1/library/follows/{artistId}` | user | `unfollowArtist` |
| streaming | POST | `/api/v1/media/{trackId}/master` | artist (owner) | `uploadMaster` |
| streaming | POST | `/api/v1/media/{trackId}/cover` | artist (owner) | `uploadCover` |
| streaming | GET | `/api/v1/media/{trackId}/status` | artist (owner) / admin | `getTranscodingStatus` |
| streaming | POST | `/api/v1/streaming/{trackId}/token` | user | `issueStreamToken` |
| streaming | GET | `/api/v1/streaming/{trackId}` | signed `token` query param | `streamTrack` |
| recommendation | GET | `/api/v1/recommendations/me` | user | `getMyRecommendations` |
| recommendation | POST | `/api/v1/analytics/plays` | user | `reportPlay` |
| recommendation | GET | `/api/v1/charts/top` | public | `getTopChart` |
| notification | GET | `/api/v1/notifications/preferences` | user | `getMyNotificationPreferences` |
| notification | PUT | `/api/v1/notifications/preferences` | user | `updateMyNotificationPreferences` |
| notification | GET | `/api/v1/admin/notifications` | admin | `listNotificationLog` |
| notification | GET | `/api/v1/admin/notification-templates` | admin | `listNotificationTemplates` |
| notification | POST | `/api/v1/admin/notification-templates` | admin | `createNotificationTemplate` |
| notification | GET | `/api/v1/admin/notification-templates/{templateCode}` | admin | `getNotificationTemplate` |
| notification | PUT | `/api/v1/admin/notification-templates/{templateCode}` | admin | `updateNotificationTemplate` |

## Shared conventions

**Identifiers.** All ids are positive `int64` (`Long`), matching the existing JPA entities
(`@GeneratedValue(IDENTITY)`). An artist's `artistId` **equals the user id** of the artist's account in
auth-service (1:1), so no mapping table is needed. recommendation-service currently stores `userId` and
`trackId` as `String`; they become `Long` / ClickHouse `UInt64`.

**Cross-service data.** Only the owning service returns full details. library and recommendation responses
contain `trackId` / `artistId` only. Clients load details with a batch call to the catalog:
`GET /api/v1/tracks?ids=1,2,3` (up to 100 ids).

**Timestamps and dates.** `date-time` values are RFC 3339 in UTC (`2026-10-05T09:14:00Z`), i.e.
`OffsetDateTime` in Java. Calendar dates (`releaseDate`) use `date` (`2026-10-01`, `LocalDate`).
Durations are whole seconds (`int32`).

**Strings.** Every string has a `maxLength`. Names and titles also forbid leading and trailing whitespace
(`pattern: ^\S(?:.*\S)?$`). Enum values are `UPPER_SNAKE_CASE`.

**Optional vs. null.**
- A property in `required` is always present and never `null`.
- A property not in `required` may be **absent**. No property is `nullable`, so servers must not send
  `null`. Configure the service's JSON mapper to omit null properties (`NON_NULL` inclusion).
- Requests reject unknown properties (`additionalProperties: false`). The generated DTOs do not enforce
  this, so enable "fail on unknown properties" in the service's JSON mapper.

**PATCH vs. PUT.** `PATCH` bodies are partial: omitted fields keep their value, and at least one field
is required (`minProperties: 1`; check this in the service, because generated bean validation does not).
A PATCH cannot clear a field. `PUT` bodies are full replacements.

**Paging.** Paged lists take `page` (zero-based, default 0) and `size` (1–100, default 20) and return
`{ "items": [...], "page": { "number", "size", "totalElements", "totalPages" } }`.
Each list has its own named page type (`TrackPage`, `LikePage`, ...) because generators do not handle
generic wrappers well. Small, bounded lists (`/api/v1/genres`, templates) are plain arrays.

**Errors.** Every error body is an RFC 9457 `ProblemDetail` (Spring's `ProblemDetail`), sent as
`application/problem+json`, with two extensions:
- `code`: a stable, machine-readable reason (e.g. `EMAIL_ALREADY_REGISTERED`). The operation
  descriptions list the specific codes.
- `errors[]`: per-field validation failures, sent with 400.

Status codes used:
- `400`: malformed request or failed validation.
- `401`: no valid credentials.
- `403`: wrong role or not the owner.
- `404`: not found or not visible to the caller.
- `409`: conflicts with the current state.
- `422`: the body refers to something unusable, e.g. an unknown genre.
- `429`: rate limited.

**Security.** Contracts describe the public surface at the gateway (`http://localhost:8080`).
- `bearerAuth` (JWT) is the default. Public operations declare `security: []`.
- `GET /api/v1/tracks/{trackId}` accepts both anonymous and authenticated calls.
- `GET /api/v1/streaming/{trackId}` uses the `streamToken` scheme, a signed `token` query parameter, so
  `<audio src>` works without headers.

Roles are listed in each operation's description (`**Role:** ARTIST (owner)`). The gateway validates
the JWT and forwards `X-User-Id` / `X-User-Roles`. Those headers are internal, so they are **not**
parameters in the contracts: read them in the service with a filter or argument resolver.
The stream token is likewise not a generated method parameter; check it in a filter.

**Async side effects.** Operation descriptions name the events they emit (`TrackLikedEvent`,
`UserSnapshot`, ...). The events themselves are described in each service's `asyncapi.yaml`
(see [Events (Kafka)](#events-kafka)).

## Internal APIs and service-to-service calls

- **Where.** Endpoints for other services live under `/internal/**`, in a separate `internal-openapi.yaml`
  per service. The gateway never routes `/internal/**`.
- **Protection.** Every internal call carries the shared secret `INTERNAL_API_TOKEN` in `X-Internal-Token`.
  A filter in the called service rejects other calls with 401 `INVALID_INTERNAL_TOKEN`.
- **Generated code.** The owning service generates controller interfaces from the internal contract. Each
  caller generates a client (`library = "spring-http-interface"`) from the same file, so both sides have their
  own DTO classes and share only the YAML.
- **Calls between services:**

  | Caller → owner | Endpoint | Purpose |
  |---|---|---|
  | catalog → auth | `GET /internal/users?ids=` | artist names and avatars for a page of tracks |
  | library → auth | `GET /internal/users?ids=` | check that an artist exists before a follow |
  | streaming → catalog | `GET /internal/tracks/{trackId}` | check the uploader owns the track; stream only published tracks |
  | library → catalog | `GET /internal/tracks/{trackId}` | check that a track exists before a like |
  | streaming → catalog | `POST /internal/tracks/{trackId}/publish` | **deprecated**: stand-in until catalog consumes `AssetReadySnapshot` |

  Access tokens are never checked through an internal call: the gateway verifies the JWT itself.
- **First pair: catalog-service → auth-service.**
  - `GET /internal/users?ids=` loads artist names and avatars for a whole page of tracks in one call (no N+1).
  - The client (`track-service/.../client/ArtistDirectory`) is guarded by a semaphore bulkhead, a circuit
    breaker and a retry with exponential backoff and jitter (Resilience4j).
  - When auth-service is unavailable, the graceful fallback shows the artist as `Unknown artist` instead of
    failing.
  - Trace context (`traceparent`) and `X-Correlation-Id` are passed on.
- **Idempotency.** `POST /api/v1/auth/register` and `POST /api/v1/tracks` accept `Idempotency-Key`.

## Events (Kafka)

Each service's `asyncapi.yaml` lists the topics it **sends** to (defined there) and **receives** from
(referenced from the producer's file). The producer owns a topic and its message schemas; consumers never
redefine them.

| Topic | Message(s) | Producer | Consumers | Key |
|---|---|---|---|---|
| `auth.user.snapshot` (compacted) | `UserSnapshot` | auth | notification | `userId` |
| `catalog.track.published` | `TrackPublishedEvent` | catalog | notification | `trackId` |
| `catalog.track.snapshot` (compacted) | `TrackSnapshot` | catalog | recommendation | `trackId` |
| `streaming.asset.ready` | `AssetReadySnapshot` | streaming | catalog | `trackId` |
| `library.track-likes` | `TrackLikedEvent`, `TrackUnlikedEvent` | library | recommendation | `userId` |
| `library.artist-follows` | `ArtistFollowedEvent`, `ArtistUnfollowedEvent` | library | notification | `artistId` |
| `recommendation.feed.served` | `RecommendationsServedEvent` | recommendation | recommendation | `userId` |

Conventions:
- **Names.** Topics are `<producer>.<entity>.<event>`. Messages that must stay in order (like and unlike)
  share one topic; the `eventType` header tells them apart.
- **Keys.** The record key is the id named in the table, as a decimal string. Kafka keeps order only per
  key, so changes that must be applied in order share a key.
- **Snapshots** carry the full current state, not a change, on a compacted topic. A new consumer rebuilds its
  local copy by reading the topic from the start.
- **Payload.** JSON. Every message has `eventId` (UUID) and `occurredAt` (UTC), plus the headers
  `eventType`, `X-Correlation-Id` and `traceparent` (`common/events.yaml`).
- **Delivery** is at least once. Consumers are idempotent: they remember processed `eventId`s and skip
  repeats. Each service consumes with its own consumer group, named after the service.
- **Evolution.** Fields are only added, never renamed or removed, and consumers ignore unknown fields.
  A breaking change goes to a new topic (`...v2`).

## Differences from `API_GATEWAY_ENDPOINTS.md`

- Path variables have descriptive names (`{trackId}`, `{userId}`, ...). The URLs are unchanged.
- Added list filters for batch lookups: `GET /api/v1/tracks?ids=`, `GET /api/v1/library/likes?trackIds=`,
  `GET /api/v1/library/follows?artistIds=`.
- `GET /api/v1/recommendations/me` also takes an optional `genre` filter.
- Tracks have 1–3 genres (`genreSlugs`), following `TrackSnapshot.genres` in `ARCHITECTURE_PROPOSAL.md`.
- Not covered yet, because the plan does not list them: password change/reset, email verification,
  album edit/delete, album cover upload, artist profile edit, playlists.

## Linting

```bash
cd api-contracts
npx @redocly/cli lint
```

## Code generation (Spring Boot 4, Java 25)

The contracts were checked with **openapi-generator 7.25.0** (`spring` generator). All six contracts
generate and the result compiles against Spring Boot 4.1.1 / Java 25. On Boot 4, `useSpringBoot4` and
`useJackson3` are **required**; otherwise the DTOs import Jackson 2 classes and fail to compile.

Example for `library-service/build.gradle.kts`:

```kotlin
plugins {
    // ...existing plugins
    id("org.openapi.generator") version "7.25.0"
}

dependencies {
    // ...existing dependencies
    implementation("org.springframework.boot:spring-boot-starter-validation")
}

openApiGenerate {
    generatorName.set("spring")
    inputSpec.set("$rootDir/../api-contracts/library-service/openapi.yaml")
    outputDir.set(layout.buildDirectory.dir("generated/openapi").get().asFile.path)
    apiPackage.set("com.epam.java.specialization.libraryservice.api")
    modelPackage.set("com.epam.java.specialization.libraryservice.api.dto")
    modelNameSuffix.set("Dto")
    typeMappings.set(mapOf("URI" to "String"))
    configOptions.set(
        mapOf(
            "interfaceOnly" to "true",
            "useSpringBoot4" to "true",
            "useJackson3" to "true",
            "useTags" to "true",
            "useBeanValidation" to "true",
            "useSpringBuiltInValidation" to "true",
            "openApiNullable" to "false",
            "skipDefaultInterface" to "true",
            "documentationProvider" to "none",
            "annotationLibrary" to "none",
            "generateJsonIncludeAnnotations" to "false",
            "generateJsonSetterNullsAnnotations" to "false",
        )
    )
}

sourceSets.main { java.srcDir(layout.buildDirectory.dir("generated/openapi/src/main/java")) }
tasks.compileJava { dependsOn(tasks.openApiGenerate) }
```

This produces one interface per tag (e.g. `LikesApi`, `FollowsApi`) for controllers to implement, plus
`*Dto` classes with Bean Validation annotations.

Two options work around generator quirks:
- `typeMappings` "URI" to "String": with `maxLength`, a `format: uri` field would get `@Size` on `java.net.URI`,
  which Hibernate Validator rejects at runtime. URI fields therefore stay `String`. Check the URL format in the
  service where it matters.
- `useSpringBuiltInValidation`: interfaces get no `@Validated`. Spring MVC's built-in method validation then
  checks parameters and raises `HandlerMethodValidationException`. A `@Validated` proxy would raise
  `ConstraintViolationException` instead.

Every service except `api-gateway` is already set up this way. Controllers implement the generated interfaces.
`auth-service` implements every operation and keeps `skipDefaultInterface` at `true`. The other services set it
to `false`, so an operation they don't implement yet answers 501 Not Implemented through the generated default
method. Switch it to `true` once a service implements everything.

**Other generated code** (every service registers these Gradle tasks next to `openApiGenerate`):
- `openApiGenerateInternal`: controller interfaces for the service's own `internal-openapi.yaml` (auth, catalog).
- `openApi...Client`: an HTTP interface client (`library = "spring-http-interface"`) for another service's
  internal API: catalog → auth, library → auth and catalog, streaming → catalog. Package `<service>.client.<target>`.
- `openApiGenerateEvents`: Kafka payload classes. openapi-generator cannot read AsyncAPI, so each service has an
  `event-models.yaml` that only `$ref`s the payload schemas it sends or receives from the `asyncapi.yaml` files,
  and the task generates models only (package `<service>.event`). Producers and listeners are written by hand
  (`KafkaTemplate`, `@KafkaListener`).

Mapping between entities and generated DTOs uses **MapStruct** in every service: mappers are Spring beans
(`-Amapstruct.defaultComponentModel=spring`) and an unmapped target property fails the build
(`-Amapstruct.unmappedTargetPolicy=ERROR`).

**Docker builds:** each service's build context is its own directory, so the Dockerfiles take
`api-contracts` from a second, named build context, `contracts` (`COPY --from=contracts . /api-contracts`).
`docker-compose.yml` passes it through `additional_contexts`. For a plain build, run this from the
service directory:

```bash
docker build --build-context contracts=../api-contracts .
```

Kubernetes manifests use the built images, so they need no change.
