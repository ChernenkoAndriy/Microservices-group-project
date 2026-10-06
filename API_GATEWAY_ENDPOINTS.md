# Spotty: API Gateway endpoint plan

Source: `ARCHITECTURE_PROPOSAL.md` (root), cross-checked against the current controllers in each service (2026-10-05).

## 1. What the system does (business functions)

Spotty is a music streaming platform with three end-to-end scenarios:

1. **Artist publishes a track.** The artist uploads a master audio file plus metadata (title, genre, cover). Catalog validates the metadata and queues processing, the media processor transcodes the audio, `TrackPublishedEvent` is emitted, and Notification emails the artist's subscribers.
2. **Listener likes a track.** The like goes through the gateway. The owning service stores the like and emits `TrackLikedEvent {userId, trackId, timestamp}` (or `TrackUnlikedEvent`). Recommendation consumes it, updates the taste profile and queues a feed recalculation.
3. **Listener gets recommendations.** The recommendation endpoint returns a precomputed list immediately (HTTP 200). It then emits an event so a background consumer records impressions (so the same tracks are not repeated next session) and prepares the next batch.

Roles: **Listener**, **Artist**, **Administrator**.

## 2. Service roles

| Service (port, compose name) | Bounded context | Owns | Emits / consumes |
|---|---|---|---|
| auth-service (8081, `auth-service`) | IAM | User, Credential, Role, RefreshToken. Registration, login, OAuth2/JWT, sessions, roles | emits `UserSnapshot` on profile change |
| track-service (8082, `catalog-service`) | Catalog & Content | Artist, Album, Track, Genre. Metadata, search | emits `TrackPublishedEvent`, `TrackSnapshot`; consumes `AssetReadySnapshot` |
| streaming-service (8083, `streaming-service`) | Media Streaming & Delivery | MediaAsset, TranscodingJob, ChunkManifest. Audio storage (SeaweedFS S3), transcoding, streaming, signed stream/offline tokens | emits `AssetReadySnapshot` |
| recommendation-service (8084, `recommendation-service`) | Recommendation & Analytics | ListeningEvent, UserTasteProfile, RecommendationFeed (ClickHouse). Popularity, personal feeds | consumes like/play/impression events; emits feed-refresh events |
| notification-service (8085, `notification-service`) | Notification | NotificationTemplate, DeliveryJob, NotificationLogEntry. Templating, mailings, send queues | consumes `TrackPublishedEvent` (and year-summary triggers) |
| library-service (8086, `library-service`) | User Library | Like, Follow (later Playlist). The listener's own collection | emits `TrackLikedEvent`/`TrackUnlikedEvent`, `ArtistFollowedEvent`/`ArtistUnfollowedEvent` |
| api-gateway (8080) | none (edge) | Single entry point: routing, JWT validation, role checks, CORS, rate limiting | none |

## 3. Endpoints the gateway should register

Auth column: **public** = no token; **user** = any authenticated role; **artist** / **admin** = that role required. All public paths are versioned as `/api/v1/...` (a breaking change goes to `/api/v2/...` while v1 keeps running), so the gateway can route by path prefix without rewriting. Service-to-service endpoints use `/internal/...`, are not versioned and are never routed.

### 3.1 Identity (→ auth-service)

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/api/v1/auth/register` | public | Register a listener or artist account (exists today) |
| POST | `/api/v1/auth/login` | public | Exchange credentials for access + refresh JWT |
| POST | `/api/v1/auth/refresh` | public (refresh token) | Rotate tokens |
| POST | `/api/v1/auth/logout` | user | Revoke the refresh token / session |
| GET | `/api/v1/users/me` | user | Current user's profile |
| PATCH | `/api/v1/users/me` | user | Update profile (emits `UserSnapshot`) |
| GET | `/api/v1/admin/users` | admin | List or search users |
| PATCH | `/api/v1/admin/users/{id}` | admin | Change role or status (block/unblock) |

Not exposed: service-to-service endpoints live under `/internal/**` (`/internal/auth/token-is-valid`, `/internal/auth/get-user-id-from-token`, and the raw lookup `/internal/users/{email}`). The gateway must not route `/internal/**`. A JWKS / public-key endpoint, if used, is consumed by the gateway internally rather than routed.

### 3.2 Catalog (→ track-service / `catalog-service`)

| Method | Path | Auth | Purpose |
|---|---|---|---|
| GET | `/api/v1/tracks` | public | Search and browse tracks (`q`, `genre`, `artistId`, paging) |
| GET | `/api/v1/tracks/{id}` | public | Track details (exists today) |
| POST | `/api/v1/tracks` | artist | Create track metadata; status becomes "awaiting audio" (Scenario 1; exists today without auth) |
| PATCH | `/api/v1/tracks/{id}` | artist (owner) | Edit metadata |
| DELETE | `/api/v1/tracks/{id}` | artist (owner) / admin | Remove or unpublish a track |
| GET | `/api/v1/artists/{id}` | public | Artist page |
| GET | `/api/v1/artists/{id}/tracks` | public | Artist's published tracks |
| GET | `/api/v1/artists/{id}/albums` | public | Artist's albums |
| GET | `/api/v1/artists/me/tracks` | artist | Artist dashboard: own tracks incl. drafts and processing status |
| GET | `/api/v1/albums/{id}` | public | Album with its tracks |
| POST | `/api/v1/albums` | artist | Create album |
| GET | `/api/v1/genres` | public | Genre list |

### 3.3 User library (→ library-service)

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/api/v1/library/likes/{trackId}` | user | Like / add to favourites (Scenario 2, emits `TrackLikedEvent`) |
| DELETE | `/api/v1/library/likes/{trackId}` | user | Remove like (emits `TrackUnlikedEvent`) |
| GET | `/api/v1/library/likes` | user | My liked / favourite tracks |
| POST | `/api/v1/library/follows/{artistId}` | user | Follow an artist (emits `ArtistFollowedEvent`; Notification keeps its own subscriber list for Scenario 1) |
| DELETE | `/api/v1/library/follows/{artistId}` | user | Unfollow (emits `ArtistUnfollowedEvent`) |
| GET | `/api/v1/library/follows` | user | Artists I follow |

Note: the architecture doc's Scenario 2 names `POST /tracks/{id}/like`; this plan moves it under `/api/v1/library/**` so one prefix routes to one service.

### 3.4 Media & streaming (→ streaming-service)

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/api/v1/media/{trackId}/master` | artist (owner) | Upload master audio, multipart (Scenario 1); replaces today's generic `/api/v1/streaming/upload` |
| POST | `/api/v1/media/{trackId}/cover` | artist (owner) | Upload cover image |
| GET | `/api/v1/media/{trackId}/status` | artist (owner) | Transcoding job status |
| POST | `/api/v1/streaming/{trackId}/token` | user | Issue a short-lived signed token for playback, or `?type=offline` for download |
| GET | `/api/v1/streaming/{trackId}` | signed token | Stream audio with HTTP `Range` support and a `quality` parameter; replaces today's `/api/v1/streaming/{fileName}` |

### 3.5 Recommendations & analytics (→ recommendation-service)

| Method | Path | Auth | Purpose |
|---|---|---|---|
| GET | `/api/v1/recommendations/me` | user | Precomputed personal feed; triggers the async refresh and impression tracking (Scenario 3) |
| POST | `/api/v1/analytics/plays` | user | Report a play (trackId, listened seconds) as a ListeningEvent; userId comes from the JWT, not the body |
| GET | `/api/v1/charts/top` | public | Popular tracks (`period`, `genre`), from popularity counts |

Not exposed: the existing `GET /api/v1/recommendations/users/{userId}/events` (lets anyone read anyone's history; replace with `/me` or keep admin-only). The existing `POST /api/v1/recommendations/events` becomes `/api/v1/analytics/plays`.

### 3.6 Notifications (→ notification-service)

Notification is driven by events, so listeners need very little HTTP surface.

| Method | Path | Auth | Purpose |
|---|---|---|---|
| GET | `/api/v1/notifications/preferences` | user | Email preferences (new releases, year summary) |
| PUT | `/api/v1/notifications/preferences` | user | Opt in / out of mailings |
| GET | `/api/v1/admin/notifications` | admin | Delivery log (NotificationLogEntry), filter by status |
| GET/POST/PUT | `/api/v1/admin/notification-templates/**` | admin | Manage email templates |

Not exposed: the existing `POST /api/v1/notifications` (would let any client send email). Sending happens only from consumed events.

## 4. Route table (gateway config)

Order matters: the more specific `/api/v1/admin/...` routes come before the general ones.

| Route id | Predicate (Path) | Target |
|---|---|---|
| admin-users | `/api/v1/admin/users/**` | `http://auth-service:8081` |
| admin-notifications | `/api/v1/admin/notifications/**`, `/api/v1/admin/notification-templates/**` | `http://notification-service:8085` |
| auth | `/api/v1/auth/**`, `/api/v1/users/**` | `http://auth-service:8081` |
| catalog | `/api/v1/tracks/**`, `/api/v1/artists/**`, `/api/v1/albums/**`, `/api/v1/genres/**` | `http://catalog-service:8082` |
| library | `/api/v1/library/**` | `http://library-service:8086` |
| media | `/api/v1/media/**`, `/api/v1/streaming/**` | `http://streaming-service:8083` |
| recommendations | `/api/v1/recommendations/**`, `/api/v1/analytics/**`, `/api/v1/charts/**` | `http://recommendation-service:8084` |
| notifications | `/api/v1/notifications/**` | `http://notification-service:8085` |

Cross-cutting gateway behaviour:
- Validate the JWT once at the gateway, then forward `X-User-Id` and `X-User-Roles` downstream, and strip any such headers sent by the client.
- Enforce role rules by path (`/api/v1/admin/**` needs admin; artist-only writes listed above).
- CORS for the web client; rate limit `/api/v1/auth/login` and `/api/v1/auth/register`.
- Pass `Range` headers through and disable response buffering for `/api/v1/streaming/**`.
- Do not route `/actuator/**` of downstream services.

## 5. Decisions (2026-10-05)

1. **Likes and artist follows** live in a new **library-service** (port 8086) that owns likes, follows and future playlists. It emits like events to recommendation-service and follow events to notification-service.
2. **Who notifies followers.** notification-service keeps a local subscriber list built from follow events, so it needs no synchronous call when `TrackPublishedEvent` arrives.
3. **Master file storage.** The master file is stored in streaming-service's own storage. The storage engine (SeaweedFS, previously MinIO) is not decided yet; this does not affect gateway routes.
4. **Streaming path.** Audio is streamed through the gateway.
5. **Gateway routing library.** Later stage; the gateway currently has only WebFlux and no routes.
6. **Message broker.** Later stage; all events above assume one.
