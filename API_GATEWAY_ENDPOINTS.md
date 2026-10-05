# Spotty: API Gateway endpoint plan

Source: `ARCHITECTURE_PROPOSAL.md` (root), cross-checked against the current controllers in each service (2026-10-05). Planning only, nothing in the repo was changed.

## 1. What the system does (business functions)

Spotty is a music streaming platform with three end-to-end scenarios:

1. **Artist publishes a track.** The artist uploads a master audio file plus metadata (title, genre, cover). Catalog validates the metadata and queues processing, the media processor transcodes the audio, `TrackPublishedEvent` is emitted, and Notification emails the artist's subscribers.
2. **Listener likes a track.** `POST /tracks/{id}/like` goes through the gateway. The owning service stores the like and emits `TrackLikedEvent {userId, trackId, timestamp}` (or `TrackUnlikedEvent`). Recommendation consumes it, updates the taste profile and queues a feed recalculation.
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
| library-service (8086, new) | User Library | Like, Follow (later Playlist). The listener's own collection | emits `TrackLikedEvent`/`TrackUnlikedEvent`, `ArtistFollowedEvent`/`ArtistUnfollowedEvent` |
| api-gateway (8080) | none (edge) | Single entry point: routing, JWT validation, role checks, CORS, rate limiting | none |

## 3. Endpoints the gateway should register

Auth column: **public** = no token; **user** = any authenticated role; **artist** / **admin** = that role required. All paths keep the existing `/api/...` convention the services already use, so the gateway can route by path prefix without rewriting.

### 3.1 Identity (→ auth-service)

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/api/auth/register` | public | Register a listener or artist account (exists today) |
| POST | `/api/auth/login` | public | Exchange credentials for access + refresh JWT |
| POST | `/api/auth/refresh` | public (refresh token) | Rotate tokens |
| POST | `/api/auth/logout` | user | Revoke the refresh token / session |
| GET | `/api/users/me` | user | Current user's profile |
| PATCH | `/api/users/me` | user | Update profile (emits `UserSnapshot`) |
| GET | `/api/admin/users` | admin | List or search users |
| PATCH | `/api/admin/users/{id}` | admin | Change role or status (block/unblock) |

Not exposed: the existing `GET /api/auth/users/{email}` is a raw user lookup by email and should stay internal. A JWKS / public-key endpoint, if used, is consumed by the gateway internally rather than routed.

### 3.2 Catalog (→ track-service / `catalog-service`)

| Method | Path | Auth | Purpose |
|---|---|---|---|
| GET | `/api/tracks` | public | Search and browse tracks (`q`, `genre`, `artistId`, paging) |
| GET | `/api/tracks/{id}` | public | Track details (exists today) |
| POST | `/api/tracks` | artist | Create track metadata; status becomes "awaiting audio" (Scenario 1, step 1 to 2; exists today without auth) |
| PATCH | `/api/tracks/{id}` | artist (owner) | Edit metadata |
| DELETE | `/api/tracks/{id}` | artist (owner) / admin | Remove or unpublish a track |
| GET | `/api/artists/{id}` | public | Artist page |
| GET | `/api/artists/{id}/tracks` | public | Artist's published tracks |
| GET | `/api/artists/{id}/albums` | public | Artist's albums |
| GET | `/api/artists/me/tracks` | artist | Artist dashboard: own tracks incl. drafts and processing status |
| GET | `/api/albums/{id}` | public | Album with its tracks |
| POST | `/api/albums` | artist | Create album |
| GET | `/api/genres` | public | Genre list |

### 3.2a User library (→ new library-service)

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/api/library/likes/{trackId}` | user | Like / add to favourites (Scenario 2, emits `TrackLikedEvent`) |
| DELETE | `/api/library/likes/{trackId}` | user | Remove like (emits `TrackUnlikedEvent`) |
| GET | `/api/library/likes` | user | My liked / favourite tracks |
| POST | `/api/library/follows/{artistId}` | user | Follow an artist (emits `ArtistFollowedEvent`; Notification keeps its own subscriber list for Scenario 1, step 5) |
| DELETE | `/api/library/follows/{artistId}` | user | Unfollow (emits `ArtistUnfollowedEvent`) |
| GET | `/api/library/follows` | user | Artists I follow |

Note: the doc's Scenario 2 names `POST /tracks/{id}/like`; this plan moves it under `/api/library/**` so one prefix routes to one service.

### 3.3 Media & streaming (→ streaming-service)

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/api/media/{trackId}/master` | artist (owner) | Upload master audio, multipart (Scenario 1, step 1 and 3); replaces today's generic `/api/streaming/upload` |
| POST | `/api/media/{trackId}/cover` | artist (owner) | Upload cover image |
| GET | `/api/media/{trackId}/status` | artist (owner) | Transcoding job status |
| POST | `/api/streaming/{trackId}/token` | user | Issue a short-lived signed token for playback, or `?type=offline` for download |
| GET | `/api/streaming/{trackId}` | signed token | Stream audio with HTTP `Range` support and a `quality` parameter; replaces today's `/api/streaming/{fileName}` |

### 3.4 Recommendations & analytics (→ recommendation-service)

| Method | Path | Auth | Purpose |
|---|---|---|---|
| GET | `/api/recommendations/me` | user | Precomputed personal feed; triggers the async refresh and impression tracking (Scenario 3) |
| POST | `/api/analytics/plays` | user | Report a play (trackId, listened seconds) as a ListeningEvent; userId comes from the JWT, not the body |
| GET | `/api/charts/top` | public | Popular tracks (`period`, `genre`), from popularity counts |

Not exposed: the existing `GET /api/recommendations/users/{userId}/events` (lets anyone read anyone's history; replace with `/me` or keep admin-only). The existing `POST /api/recommendations/events` becomes `/api/analytics/plays`.

### 3.5 Notifications (→ notification-service)

Notification is driven by events, so listeners need very little HTTP surface.

| Method | Path | Auth | Purpose |
|---|---|---|---|
| GET | `/api/notifications/preferences` | user | Email preferences (new releases, year summary) |
| PUT | `/api/notifications/preferences` | user | Opt in / out of mailings |
| GET | `/api/admin/notifications` | admin | Delivery log (NotificationLogEntry), filter by status |
| GET/POST/PUT | `/api/admin/notification-templates/**` | admin | Manage email templates |

Not exposed: the existing `POST /api/notifications` (would let any client send email). Sending happens only from consumed events.

## 4. Route table (what goes in the gateway config)

Order matters: the more specific `/api/admin/...` routes are listed before the general ones.

| Route id | Predicate (Path) | Target |
|---|---|---|
| admin-users | `/api/admin/users/**` | `http://auth-service:8081` |
| admin-notifications | `/api/admin/notifications/**`, `/api/admin/notification-templates/**` | `http://notification-service:8085` |
| auth | `/api/auth/**`, `/api/users/**` | `http://auth-service:8081` |
| catalog | `/api/tracks/**`, `/api/artists/**`, `/api/albums/**`, `/api/genres/**` | `http://catalog-service:8082` |
| library | `/api/library/**` | `http://library-service:8086` |
| media | `/api/media/**`, `/api/streaming/**` | `http://streaming-service:8083` |
| recommendations | `/api/recommendations/**`, `/api/analytics/**`, `/api/charts/**` | `http://recommendation-service:8084` |
| notifications | `/api/notifications/**` | `http://notification-service:8085` |

Cross-cutting gateway behaviour for the next stage:
- Validate the JWT once at the gateway, then forward `X-User-Id` and `X-User-Roles` downstream, and strip any such headers sent by the client.
- Enforce role rules by path (`/api/admin/**` needs admin; artist-only writes listed above).
- CORS for the web client; rate limit `/api/auth/login` and `/api/auth/register`.
- Pass `Range` headers through and disable response buffering for `/api/streaming/**`.
- Do not route `/actuator/**` of downstream services.

## 5. Decisions (updated 2026-10-05 with team answers)

1. **Likes and artist follows (decided 2026-10-05).** They live in a new **library-service** (port 8086) owning likes, follows and future playlists, matching the doc's "user library" context. It emits like events to recommendation-service and follow events to notification-service.
2. **Who notifies followers.** With library-service, notification-service keeps a local subscriber list built from follow events, so it needs no synchronous call when `TrackPublishedEvent` arrives.
3. **Master file storage.** Confirmed: the master file is stored in streaming-service's own storage. The storage engine (SeaweedFS, previously MinIO) is not decided yet; this does not affect the gateway routes.
4. **Streaming path.** Confirmed: audio is streamed through the gateway.
5. **Gateway routing library.** Later stage; the gateway still has only WebFlux and no routes.
6. **Message broker.** Later stage; all events above assume one.
