# Backend Architecture

## Layers

- `api`: Spring MVC controllers and DTO error handling. Controllers keep URL contracts stable and delegate to use-case services.
- `auth`, `profile`, `messages`, `social`, `payments`, `events`: domain/service modules.
- `*/persistence`: MyBatis mapper interfaces and row objects. Mapper methods should stay persistence-only and avoid business rules.
- `resources/mapper`: MyBatis SQL XML files.
- `resources/db/migration`: Flyway schema history.

## Event Module Split

`EventController` now delegates to focused event services instead of one large service surface:

- `EventQueryService`: event lists, details, feed, saved/liked/my events and feed preferences.
- `EventCommandService`: create, multipart create, update and delete event commands.
- `EventParticipantService`: join, paid post-payment join, leave, owner approve/remove/block and participant listing.
- `EventMediaService`: media metadata, multipart/raw upload, authenticated content streaming, thumbnail content and deletion.
- `EventMediaStorageService`: upload validation, storage quota checks, S3 object writes, thumbnail generation, content loading and object cleanup.
- `EventDtoMapper`: event/media/participant/feed preference DTO assembly and authenticated media URL shaping.
- `EventRules`: shared join/rating/ended-state business predicates used by services and DTO mapping.
- `EventFeedRequestNormalizer`: feed query validation, cursor encode/decode, scope/sort/limit/tag/attendance filter normalization.
- `EventEngagementService`: like/unlike and event/organizer ratings.
- `EventTicketService`: event ticket product sync and completed-ticket lookup used by paid joins.
- `EventNotificationService`: scheduled event finishing and reminder notification entry points.
- `EventService`: compatibility facade for existing internal callers such as profile/payment code.
- `EventDomainService`: shared domain orchestration kept behind the focused services while preserving existing behavior and API contracts.

## Transaction Boundaries

Write use cases are annotated with `@Transactional` at service boundaries. This covers event create/update/delete, join/leave, feed preferences, likes/ratings, participant moderation, media metadata writes and ticket product sync.

Media upload stores binary objects before metadata insert. On multipart event creation failure, the service deletes the created event and best-effort deletes uploaded objects, preserving the previous cleanup behavior while making the DB boundary explicit.

## Error Handling

`ApiExceptionHandler` centralizes `ResponseStatusException` into the existing `ApiErrorResponse(message, status, error)` format. Do not change this response shape without coordinating frontend changes.

## Running Locally

```bash
export DB_PASSWORD=gik
export AUTH_JWT_SECRET=change-this-local-secret-to-at-least-32-bytes
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

## Tests

```bash
./mvnw test
./mvnw -B verify
```

Current focused tests cover JWT boundaries, password policy, event access/capacity edge cases, event command/media service delegation, media storage limits/filename handling, event DTO/rule behavior, feed cursor/filter normalization and ticket product sync.

## Required Environment

Minimum local variables:

- `DB_URL` (defaults to local PostgreSQL in application config)
- `DB_USERNAME`
- `DB_PASSWORD`
- `AUTH_JWT_SECRET`

Optional integrations include Google/Apple OAuth audiences, payment stub/Stripe config, Expo push config, WebSocket origin allowlist and S3-compatible storage config. See `README.md` for the complete list.
