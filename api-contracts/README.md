# Spotty API contracts

OpenAPI 3.0.3 contracts for every service behind the API gateway, written from
[`API_GATEWAY_ENDPOINTS.md`](../API_GATEWAY_ENDPOINTS.md). They are the source of truth for code generation:
services implement the generated interfaces and DTOs, and clients use generated clients.

## Layout

```
api-contracts/
├── README.md                 this file: shared conventions + codegen setup
├── redocly.yaml              lint config for all contracts
├── common/                   shared building blocks, referenced by every contract
│   ├── schemas.yaml          Id, Email, GenreSlug, PageMetadata, ProblemDetail, FieldError
│   ├── parameters.yaml       page, size
│   └── responses.yaml        400 / 401 / 403 / 404 / 409 / 422 / 429 error responses
├── auth-service/openapi.yaml            /api/v1/auth/**, /api/v1/users/**, /api/v1/admin/users/**
├── catalog-service/openapi.yaml         /api/tracks/**, /api/artists/**, /api/albums/**, /api/genres
├── library-service/openapi.yaml         /api/library/**
├── streaming-service/openapi.yaml       /api/media/**, /api/streaming/**
├── recommendation-service/openapi.yaml  /api/recommendations/**, /api/analytics/**, /api/charts/**
└── notification-service/openapi.yaml    /api/notifications/**, /api/admin/notifications/**, /api/admin/notification-templates/**
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
| catalog | GET | `/api/tracks` | public | `searchTracks` |
| catalog | POST | `/api/tracks` | artist | `createTrack` |
| catalog | GET | `/api/tracks/{trackId}` | public | `getTrack` |
| catalog | PATCH | `/api/tracks/{trackId}` | artist (owner) | `updateTrack` |
| catalog | DELETE | `/api/tracks/{trackId}` | artist (owner) / admin | `deleteTrack` |
| catalog | GET | `/api/artists/me/tracks` | artist | `listMyTracks` |
| catalog | GET | `/api/artists/{artistId}` | public | `getArtist` |
| catalog | GET | `/api/artists/{artistId}/tracks` | public | `listArtistTracks` |
| catalog | GET | `/api/artists/{artistId}/albums` | public | `listArtistAlbums` |
| catalog | POST | `/api/albums` | artist | `createAlbum` |
| catalog | GET | `/api/albums/{albumId}` | public | `getAlbum` |
| catalog | GET | `/api/genres` | public | `listGenres` |
| library | GET | `/api/library/likes` | user | `listMyLikes` |
| library | POST | `/api/library/likes/{trackId}` | user | `likeTrack` |
| library | DELETE | `/api/library/likes/{trackId}` | user | `unlikeTrack` |
| library | GET | `/api/library/follows` | user | `listMyFollows` |
| library | POST | `/api/library/follows/{artistId}` | user | `followArtist` |
| library | DELETE | `/api/library/follows/{artistId}` | user | `unfollowArtist` |
| streaming | POST | `/api/media/{trackId}/master` | artist (owner) | `uploadMaster` |
| streaming | POST | `/api/media/{trackId}/cover` | artist (owner) | `uploadCover` |
| streaming | GET | `/api/media/{trackId}/status` | artist (owner) / admin | `getTranscodingStatus` |
| streaming | POST | `/api/streaming/{trackId}/token` | user | `issueStreamToken` |
| streaming | GET | `/api/streaming/{trackId}` | signed `token` query param | `streamTrack` |
| recommendation | GET | `/api/recommendations/me` | user | `getMyRecommendations` |
| recommendation | POST | `/api/analytics/plays` | user | `reportPlay` |
| recommendation | GET | `/api/charts/top` | public | `getTopChart` |
| notification | GET | `/api/notifications/preferences` | user | `getMyNotificationPreferences` |
| notification | PUT | `/api/notifications/preferences` | user | `updateMyNotificationPreferences` |
| notification | GET | `/api/admin/notifications` | admin | `listNotificationLog` |
| notification | GET | `/api/admin/notification-templates` | admin | `listNotificationTemplates` |
| notification | POST | `/api/admin/notification-templates` | admin | `createNotificationTemplate` |
| notification | GET | `/api/admin/notification-templates/{templateCode}` | admin | `getNotificationTemplate` |
| notification | PUT | `/api/admin/notification-templates/{templateCode}` | admin | `updateNotificationTemplate` |

## Shared conventions

**Identifiers.** All ids are positive `int64` (`Long`), matching the existing JPA entities
(`@GeneratedValue(IDENTITY)`). An artist's `artistId` **equals the user id** of the artist's account in
auth-service (1:1), so no mapping table is needed. recommendation-service currently stores `userId` and
`trackId` as `String`; they become `Long` / ClickHouse `UInt64`.

**Cross-service data.** Only the owning service returns full details. library and recommendation responses
contain `trackId` / `artistId` only. Clients load details with a batch call to the catalog:
`GET /api/tracks?ids=1,2,3` (up to 100 ids).

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
generic wrappers well. Small, bounded lists (`/api/genres`, templates) are plain arrays.

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
- `GET /api/tracks/{trackId}` accepts both anonymous and authenticated calls.
- `GET /api/streaming/{trackId}` uses the `streamToken` scheme, a signed `token` query parameter, so
  `<audio src>` works without headers.

Roles are listed in each operation's description (`**Role:** ARTIST (owner)`). The gateway validates
the JWT and forwards `X-User-Id` / `X-User-Roles`. Those headers are internal, so they are **not**
parameters in the contracts: read them in the service with a filter or argument resolver.
The stream token is likewise not a generated method parameter; check it in a filter.

**Async side effects.** Operation descriptions name the events they emit (`TrackLikedEvent`,
`UserSnapshot`, ...). Event payloads are not part of these HTTP contracts; they belong in an AsyncAPI
document once the broker is chosen.

## Differences from `API_GATEWAY_ENDPOINTS.md`

- Path variables have descriptive names (`{trackId}`, `{userId}`, ...). The URLs are unchanged.
- Added list filters for batch lookups: `GET /api/tracks?ids=`, `GET /api/library/likes?trackIds=`,
  `GET /api/library/follows?artistIds=`.
- `GET /api/recommendations/me` also takes an optional `genre` filter.
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
    configOptions.set(
        mapOf(
            "interfaceOnly" to "true",
            "useSpringBoot4" to "true",
            "useJackson3" to "true",
            "useTags" to "true",
            "useBeanValidation" to "true",
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

**Docker builds:** each service's Docker build context is its own directory (`./library-service`),
so `../api-contracts` is not visible inside `docker build`. Either change the build context in
`docker-compose.yml` to the repo root (and adjust the `COPY` lines), or copy the contract into the
service before building.
