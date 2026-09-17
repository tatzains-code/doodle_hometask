# Mini Doodle — Meeting Scheduling Service

A backend service for booking meetings across one or more participants at a
fixed time. An organizer names some participants and a start/end; the server
checks every participant's availability in one atomic operation and either
books a slot for all of them or fails the whole request. There's no separate
"book a single slot" flow — a 1:1 meeting is simply the case where the
participant list has one entry.

## Running locally

```bash
docker-compose up --build
```

This starts the Spring Boot application and a PostgreSQL database, and runs
Flyway migrations on startup.

The API is available at `http://localhost:8080`.
Swagger/OpenAPI: `http://localhost:8080/swagger-ui.html`.
Metrics: `http://localhost:8080/actuator/prometheus`.

## Authentication (stub)

There is no login flow. Create a user via `POST /users` (`{ name, email }`,
no password), then send that user's id in an `X-User-Id` header on every
subsequent request. This is a deliberate simplification — see
"Design decisions" below.

## API

| Method | Path | Description |
|---|---|---|
| `POST` | `/users` | `{ name, email }`, no password |
| `POST` | `/slots` | Create a slot (owner = current user) |
| `GET` | `/slots` | List my own slots (full detail) — **paginated** |
| `PATCH` | `/slots/{slotId}` | Modify a slot (only while `FREE`) |
| `DELETE` | `/slots/{slotId}` | Delete a slot (only while `FREE`) |
| `GET` | `/availability?ownerId=&from=&to=&duration=` | Another user's availability — restricted view (timing + status only). Defaults to the next 7 days if `from`/`to` are omitted; range is capped. When `duration` is given, adjacent `FREE` slots are merged into contiguous windows before filtering, so several back-to-back short slots can satisfy a longer request. **Paginated.** |
| `POST` | `/meetings` | `{ title, description, start, end, participantIds }` — atomically checks and books every participant, or fails entirely |
| `GET` | `/meetings` | List my meetings (as organizer or participant) — **paginated** |
| `DELETE` | `/meetings/{meetingId}` | Cancel (organizer only) — every participant's slot(s) return to `FREE` |

There is no separate "book a single slot" endpoint — a 1:1 meeting is simply
`POST /meetings` with one entry in `participantIds`.

### Example requests

Create a user:

```bash
curl -X POST http://localhost:8080/users \
  -H "Content-Type: application/json" \
  -d '{"name": "Dana Kim", "email": "dana.kim@example.com"}'
# -> { "id": "b2b1...", "name": "Dana Kim", "email": "dana.kim@example.com" }
```

Create a `FREE` slot (owner = whoever's id is in `X-User-Id`):

```bash
curl -X POST http://localhost:8080/slots \
  -H "Content-Type: application/json" \
  -H "X-User-Id: b2b1..." \
  -d '{"start": "2026-09-18T09:00:00Z", "end": "2026-09-18T09:30:00Z"}'
```

Book a meeting (fails atomically with 409 if any participant isn't free):

```bash
curl -X POST http://localhost:8080/meetings \
  -H "Content-Type: application/json" \
  -H "X-User-Id: b2b1..." \
  -d '{
        "title": "Design sync",
        "description": "Review the API contract",
        "start": "2026-09-18T09:00:00Z",
        "end": "2026-09-18T09:30:00Z",
        "participantIds": ["c3c2...", "d4d3..."]
      }'
```

Check someone else's availability (restricted view, no meeting details):

```bash
curl "http://localhost:8080/availability?ownerId=c3c2...&from=2026-09-18T00:00:00Z&to=2026-09-19T00:00:00Z&duration=30" \
  -H "X-User-Id: b2b1..."
```

### Pagination

`GET /slots`, `GET /availability`, and `GET /meetings` all accept standard
Spring Data paging params — `page`, `size`, `sort` — via `Pageable`, with a
default page size of 50. Each responds with Spring Data Web's
`PagedModel<T>` shape: a `content` array plus a `page` object
(`size`, `totalElements`, `totalPages`, `number`). This matters at the
brief's stated scale ("hundreds of users, thousands of slots") so no list
endpoint can return an unbounded result set. For `GET /availability` with a
`duration` filter, contiguous `FREE` slots are merged before pagination is
applied in-memory over the merged windows; the response shape is unchanged.

### Errors

All errors are returned as `ProblemDetail` (RFC 7807) from a single
`GlobalExceptionHandler` — controllers never build error bodies themselves.

| Code | Title | When | Example detail |
|---|---|---|---|
| 400 | `Invalid request` | Self-only participant list, invalid `start`/`end` range, malformed `X-User-Id` header | "At least one distinct participant is required" |
| 400 | `Validation failed` | Bean Validation failure on the request body (e.g. `@ValidTimeRange` — start/duration not aligned to granularity, or outside min/max duration) | "start: must be aligned to a 15-minute granularity" |
| 400 | `Malformed request` | Request body isn't valid JSON | "Malformed request body" |
| 400 | `Missing parameter` | A required query param is absent (e.g. `ownerId` on `/availability`) | "Required parameter 'ownerId' is not present" |
| 400 | `Invalid parameter` | A query/path param has the wrong type (e.g. non-numeric id) | "Invalid value for parameter: ownerId" |
| 404 | `Resource not found` | Unknown user, slot, or meeting id | "Slot not found: 42" |
| 409 | `Slot not modifiable` | `PATCH`/`DELETE` on a slot that's currently `BUSY` | "Slot 42 is BUSY and cannot be modified" |
| 409 | `Slot unavailable` | A participant has a conflicting slot at booking time, or a concurrent request won the race (DB exclusion constraint or optimistic-lock failure) | "The requested slot(s) are no longer available" |
| 409 | `Email already in use` | `POST /users` with an email that already exists | "Email already in use: a@b.com" |
| 403 | `Not authorized` | Cancelling a meeting as a non-`OWNER` participant | "Only the organizer can cancel this meeting" |
| 500 | `Internal server error` | Anything unhandled — logged server-side, no stack trace leaked to the client | "An unexpected error occurred" |

## Testing

```bash
./mvnw test
```

Tests use Testcontainers (a real `postgres:16-alpine` container via
`AbstractIntegrationTest`), so **Docker must be running** to run the suite.

- `MeetingBookingIntegrationTest` — 1:1 and group booking happy paths,
  partial-conflict rollback (no partial booking), participant validation
  (empty/self-only/unknown), cancellation and organizer-only authorization,
  and a concurrency test where two threads race to book the same
  participant/slot — exactly one request succeeds (201), the other gets 409.
- `SlotControllerIntegrationTest` — header validation, 409 on
  `PATCH`/`DELETE` of a `BUSY` slot, happy-path free-slot modification.
- `AvailabilityIntegrationTest` — happy-path queries, and confirms a
  non-owner never receives meeting details through the restricted view.
- `MeetingRequestValidatorTest` — a pure unit test (Mockito, no Spring
  context or DB) for participant-validation rules.

## Observability

- **Swagger/OpenAPI** at `/swagger-ui.html` — interactive API docs, driven
  by `springdoc-openapi`.
- **Micrometer + Prometheus** at `/actuator/prometheus` — scrapeable
  metrics, plus `/actuator/health` (details hidden from the response body)
  and `/actuator/info`.
- Three custom counters beyond the JVM/HTTP defaults: `meeting.booked`,
  `meeting.cancelled`, and `meeting.booking.conflict` (incremented from
  `MeetingService` and `GlobalExceptionHandler` respectively), giving a
  quick signal on booking throughput and conflict rate without parsing logs.

## Domain model

- **User** — `name`, `email` (unique), no password.
- **TimeSlot** — `owner`, `start`, `end`, `status` (`FREE`/`BUSY`), `meeting`
  (nullable — set when the slot is consumed by a meeting, cleared on
  cancellation), optimistic-locking `version`.
- **Meeting** — `title`, `description`, and its own `start`/`end` (the
  organizer-specified window — not derived from any `TimeSlot`).
- **MeetingParticipant** — `meeting`, `user`, `role` (`OWNER`/`PARTICIPANT`).
  No direct reference to `TimeSlot`; a participant's slot(s) for a given
  meeting are found via `TimeSlot.meeting = X AND TimeSlot.owner = user` —
  a participant can be covered by more than one slot row (see "Design
  decisions").

## Design decisions

- **Configurable slot granularity.** Start times and durations are
  validated against a configurable step (`scheduling.slot.granularity-minutes`,
  default 15) via one `@ValidTimeRange` constraint, not duplicated checks.
  Not a stated requirement — my interpretation of "configurable duration,"
  made an explicit assumption rather than a silent hardcode. Same config
  block also holds min/max duration, booking buffer, and max horizon.

- **Closed-world availability.** Booking `[start, end]` requires existing
  `FREE` slots to fully, contiguously cover it (`MeetingService.isFullyCovered`) —
  a gap, a `BUSY` slot, or simply no data all mean "not available." I
  deliberately chose this over "absence of data = free" so it extends
  cleanly to rule-generated availability later without a schema change.

- **All-or-nothing booking.** `MeetingService.bookMeeting` is one
  `@Transactional` operation: every participant's availability is checked
  before any row is written, and one unavailable participant fails the
  whole request with nothing committed. The coverage check is re-run at
  write time, not trusted from an earlier read, to catch concurrent
  conflicts.

- **`Meeting` stores its own `start`/`end`.** Populated from the
  organizer's request, not derived from `TimeSlot` rows. Earlier designs
  put a `slot_id` FK on `Meeting`/`MeetingParticipant`, but broke once a
  meeting could be covered by several adjacent `FREE` slots per
  participant — an authoritative `start`/`end` plus a nullable
  `TimeSlot.meeting` back-reference sidesteps that.

- **No slot splitting, no atomic reschedule.** Booking consumes `FREE`
  slots whole; rescheduling is cancel-then-rebook. Both would need their
  own multi-row atomic operation and dedicated concurrency tests —
  disproportionate to the time box (see "What I'd add").

- **`/availability?duration=X` merges contiguous `FREE` slots.** Matches
  what booking already allows (see closed-world coverage) — otherwise a
  participant bookable across three adjacent 30-minute slots wouldn't
  surface in a 90-minute search. `AvailabilityService` merges exactly
  back-to-back runs and filters by combined span; the caller never sees
  the underlying slot rows.

- **"Own" vs. "restricted" view split.** `GET /slots` returns the full
  `SlotResponse`; `GET /availability` returns `AvailabilitySlotResponse`
  (timing + status only, no meeting reference). Enforced via a dedicated
  DTO in the service/mapper layer, not left to a shared type that happens
  to omit fields.

- **Optimistic locking + DB exclusion constraint.** `@Version` on
  `TimeSlot` covers row-local races (concurrent `PATCH`/`DELETE`, or two
  bookings racing for the same slot) → 409. The Postgres GiST exclusion
  constraint on `(owner_id, tstzrange(start, end))` is belt-and-braces,
  mattering most for `POST /slots`; booking only transitions existing rows,
  so its role there is secondary. No pessimistic locking (`SELECT ... FOR
  UPDATE`) on `User` rows — not needed, since every participant gets a
  materialized `TimeSlot` row and the exclusion constraint already
  enforces non-overlap at write time.

- **Recurring availability rules are out of scope.** Every `FREE` slot is
  created explicitly via `POST /slots`. A known, considered limitation —
  it's exactly what closed-world availability was chosen to support
  cleanly if a rule engine were added later.

- **Auth is stubbed.** `POST /users` takes `{ name, email }`, no password;
  every other request carries that user's id in `X-User-Id`, resolved by
  `CurrentUserResolver`. A deliberate stand-in for the time box, not a
  design I'd ship — real auth would be OAuth2/JWT.

## What I'd add with more time

- Slot splitting on partial booking, atomic meeting rescheduling.
- Real user registration/authentication (OAuth2/JWT).
- Caching for availability queries (e.g. Redis), with invalidation on slot
  changes.
- Recurring availability rules (e.g. "available weekdays 9–17") — would
  raise the pre-generation vs. on-the-fly trade-off; I'd lean toward
  pre-generation with a rolling horizon job given a read-heavy access
  pattern.
- A DB-level trigger tying `TimeSlot.meeting_id`/`owner_id` to an actual
  `MeetingParticipant` row — today that invariant is only maintained by
  application code, not enforced by the schema.
