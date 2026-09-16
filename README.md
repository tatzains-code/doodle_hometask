# Mini Doodle — Meeting Scheduling Service

A focused backend service for a 1:1 slot-booking flow: one user exposes their
availability as time slots, another user books one of those slots, turning it
into a meeting.

## Running locally

```bash
docker-compose up --build
```

This starts the Spring Boot application and a PostgreSQL database, and runs
Flyway migrations (including a small set of seed users) on startup.

The API is available at `http://localhost:8080`.
If Swagger/OpenAPI is included: `http://localhost:8080/swagger-ui.html`.

## Authentication (stub)

There is no registration/login flow. Requests are attributed to a user via an
`X-User-Id` header, referencing one of the seed users created by the Flyway
migration. This is a deliberate simplification for the time box — see
"Design decisions" below.

## API

| Method | Path | Description |
|---|---|---|
| `POST` | `/slots` | Create a slot (owner = current user) |
| `GET` | `/slots` | List my own slots (full detail) |
| `PATCH` | `/slots/{slotId}` | Modify a slot (only while `FREE`) |
| `DELETE` | `/slots/{slotId}` | Delete a slot (only while `FREE`) |
| `GET` | `/availability?ownerId=&from=&to=&duration=` | View another user's availability (restricted view — no meeting details). Defaults to the next 7 days if `from`/`to` are omitted; the range is capped to bound query cost. |
| `POST` | `/slots/{slotId}/book` | Book a slot in full (no partial booking) |
| `GET` | `/meetings` | List my meetings (as owner or participant) |
| `DELETE` | `/meetings/{meetingId}` | Cancel a meeting; the underlying slot returns to `FREE` |

Errors are returned as `ProblemDetail` (RFC 7807) via a global exception
handler, with distinct status codes for not-found (404), state conflicts
such as double-booking or modifying a booked slot (409), and invalid input
such as self-booking or bad duration/range (400).

## Domain model

- **User** — seeded, no self-service registration in this version.
- **TimeSlot** — `owner`, `start`, `end`, `duration`, `status` (`FREE`/`BUSY`),
  optimistic-locking version field.
- **Meeting** — `slotId`, `title`, `description`.
- **MeetingParticipant** — `meetingId`, `userId`, `role` (`OWNER` / `INVITEE`).
  Modeled as a separate table even though the current flow is strictly 1:1,
  so extending to group meetings later needs no schema migration — only
  relaxing the application-level constraint on participant count.

## Design decisions

- **Scope: 1:1 booking, not multi-participant meetings.** One user exposes
  availability, another books it. This keeps the core domain logic and
  concurrency handling the priority within the time box. The
  `MeetingParticipant` table is already shaped to support more participants
  without a schema change.

- **Configurable slot duration, aligned to a granularity step.** The
  requirements ask for "configurable duration," so slots are not forced to a
  single fixed length. Both `start` and `duration` are required to align to a
  fixed step (e.g. 15 minutes) — an assumption, not a stated requirement, but
  a reasonable default for a scheduling platform: it avoids fragmented or
  oddly-shaped slots and keeps availability queries predictable. Easy to
  relax if a different granularity (or none) is needed.

- **No overlapping slots per owner.** Enforced at the database level and/or
  in the service layer.

- **Booking reserves a slot in full — no splitting.** If a booking doesn't
  use the whole slot, the remainder isn't automatically turned into a new
  `FREE` slot.

- **Availability filtering, not aggregation.** `/availability?duration=X`
  returns existing slots whose duration is at least `X`; it does not stitch
  together several contiguous smaller slots into a longer virtual one.

- **Rescheduling is not a dedicated endpoint.** It's achieved via cancel +
  re-book using the existing endpoints, rather than an atomic slot-swap.

  These three — slot splitting, aggregating contiguous free slots, and
  atomic rescheduling — are grouped here deliberately: each would require a
  multi-step atomic operation beyond the single-slot optimistic-locking model
  implemented here, with its own concurrency tests. That additional
  transactional complexity felt disproportionate to the time box; each is a
  natural next step.

- **Authorization boundary between "own" and "public" views.** A user's own
  slots are returned with full detail. A slot viewed via `/availability`
  (someone else's calendar) is returned through a restricted projection —
  only timing and free/busy status, never meeting title, description, or
  participants. This is enforced in the service layer, not left to the
  serializer, so an unauthorized field can't leak through a shared DTO.

- **Concurrency.** Booking uses optimistic locking (`@Version`) on
  `TimeSlot`, so two simultaneous booking attempts on the same slot result in
  one success and one conflict (409), never a double booking.

- **Auth is stubbed** via an `X-User-Id` header rather than real
  authentication. In production this would be OAuth2/JWT with a proper
  identity provider.

## AI usage

Claude was used for boilerplate generation (entities, repositories, DTO
mapping, test scaffolding, README drafting) and for talking through
architectural trade-offs (slot-splitting vs. fixed-granularity aggregation,
concurrency model, scope boundaries). The design decisions above, and the
reasoning behind them, are my own — happy to walk through any of them in
detail.

## What I'd add with more time

- Partial booking with slot splitting; aggregating contiguous free slots for
  arbitrary requested durations; atomic meeting rescheduling (see above).
- Real user registration/authentication (OAuth2/JWT).
- Caching for availability queries (e.g. Redis), with invalidation on slot
  changes.
- Recurring availability rules (e.g. "available weekdays 9–17"), which would
  raise the pre-generation vs. on-the-fly generation trade-off — I'd lean
  toward pre-generation with a rolling horizon job, given a read-heavy access
  pattern.
- Metrics (Micrometer/Prometheus) and OpenAPI documentation, if not already
  included above.
