# Mini Doodle — Meeting Scheduling Service

A focused backend service for a mini meeting-scheduling platform: users
expose their availability as time slots, and an organizer books a meeting
naming one or more participants and a fixed time. The server checks every
participant's availability and books the meeting for everyone atomically,
or fails entirely. A 1:1 meeting is simply the case where the participant
list has size 1 — there is no separate single-slot booking flow.

## Running locally

```bash
docker-compose up --build
```

This starts the Spring Boot application and a PostgreSQL database, and runs
Flyway migrations on startup.

The API is available at `http://localhost:8080`.
Swagger/OpenAPI: `http://localhost:8080/swagger-ui.html`.
Prometheus metrics: `http://localhost:8080/actuator/prometheus`.

## Authentication (stub)

There is no password or login flow. `POST /users` creates a user from
`{ name, email }`. Requests are then attributed to that user via an
`X-User-Id` header. This is a deliberate simplification for the time box —
see "Design decisions" below.

## API

| Method | Path | Description |
|---|---|---|
| `POST` | `/users` | Create a user: `{ name, email }`, no password |
| `POST` | `/slots` | Create a slot (owner = current user) |
| `GET` | `/slots` | List my own slots (full detail) |
| `PATCH` | `/slots/{slotId}` | Modify a slot (only while `FREE`) |
| `DELETE` | `/slots/{slotId}` | Delete a slot (only while `FREE`) |
| `GET` | `/availability?ownerId=&from=&to=&duration=` | View another user's availability (restricted view — no meeting details). Defaults to the next 7 days if `from`/`to` are omitted; the range is capped to bound query cost. |
| `POST` | `/meetings` | `{ title, description, start, end, participantIds }` — atomically checks and books every participant, or fails entirely |
| `GET` | `/meetings` | List my meetings (as organizer or invitee) |
| `DELETE` | `/meetings/{meetingId}` | Cancel a meeting (organizer only); every participant's slot(s) return to `FREE` |

There is no separate `POST /slots/{slotId}/book` endpoint — booking always
goes through `POST /meetings`, even for a single invitee.

Errors are returned as `ProblemDetail` (RFC 7807) via a global exception
handler, with distinct status codes for not-found (404), state conflicts
such as an unavailable participant or modifying a booked slot (409), and
invalid input such as self-only meetings or bad duration/range (400).

## Domain model

- **User** — `name`, `email`. Created via `POST /users`, no password field.
- **TimeSlot** — `owner`, `meeting` (nullable — set while the slot is
  `BUSY` for a booked meeting, cleared back to `null` on cancellation),
  `start`, `end`, `status` (`FREE`/`BUSY`), optimistic-locking `@Version`
  field. A user's own slots can never overlap (enforced by a Postgres
  exclusion constraint, not just the app layer).
- **Meeting** — `title`, `description`, `start`, `end`. `start`/`end` are
  the single source of truth for a meeting's time, populated directly from
  the organizer's request rather than derived from any `TimeSlot`.
- **MeetingParticipant** — one row per participant per meeting: `meeting`,
  `user`, `role` (`OWNER` for the organizer, `INVITEE` for the rest). Kept
  as its own table (not FK columns on `Meeting`) so it supports an
  arbitrary number of participants without a schema change. It holds no
  slot reference itself — the link between a participant and their booked
  slot(s) is `TimeSlot` rows where `meeting = thatMeeting AND owner =
  thatParticipant.user`, which also allows a participant to hold more than
  one slot for the same meeting.

## Design decisions

- **Meetings support an arbitrary list of participants from the start.**
  `POST /meetings` takes `{ title, description, start, end, participantIds }`
  — not a "book this one slot" endpoint bolted onto later. 1:1 is just
  `participantIds.size() == 1`.

- **No search for a common free time across participants.** The organizer
  supplies a fixed `start`/`end`; the server only *checks* whether every
  participant is free then — it doesn't *find* a time that works for
  everyone. Real free-busy matching across several calendars is out of
  scope for the time box.

- **Configurable slot duration, aligned to a granularity step.** Both
  `start` and `duration` are required to align to a fixed step (e.g. 15
  minutes) — an assumption, not a stated requirement, but a reasonable
  default for a scheduling platform: it avoids fragmented or oddly-shaped
  slots and keeps availability queries predictable. Easy to relax if a
  different granularity (or none) is needed.

- **No overlapping slots per owner.** Enforced both by the application and
  by a Postgres `EXCLUDE USING gist` constraint on `(owner_id,
  tstzrange(start, end))`, so cross-request double-booking is impossible at
  the database level regardless of which concurrent transaction the
  conflicting row came from. `@Version` (optimistic locking) covers the
  row-local case — concurrent `PATCH`/`DELETE` of the same slot.

- **Booking a meeting is one atomic, all-or-nothing operation.** For every
  `participantId`, the server checks for a conflicting `BUSY` slot in
  `[start, end]`. If *any* participant is unavailable, the whole request
  fails with 409 and nothing is created or booked for anyone — no partial
  success. On success, each participant gets a `BUSY` `TimeSlot` (new, or
  an existing `FREE` one transitioned, with `meeting` set), a `Meeting`
  row, and one `MeetingParticipant` row.

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
  multi-step atomic operation with its own concurrency tests, disproportionate
  to the time box. Each is a natural next step.

- **Authorization boundary between "own" and "public" views.** A user's own
  slots are returned with full detail. A slot viewed via `/availability`
  (someone else's calendar) is returned through a restricted projection —
  only timing and free/busy status, never meeting title, description, or
  participants. This is enforced in the service layer, not left to the
  serializer, so an unauthorized field can't leak through a shared DTO.

- **Only the organizer can cancel a meeting.** Cancellation is restricted to
  the `OWNER` participant; on cancellation, every `TimeSlot` tied to the
  meeting (`meeting_id = meeting.id`) returns to `FREE` and has `meeting`
  cleared, looked up directly rather than via `MeetingParticipant`.

- **Auth is stubbed** via `POST /users` + an `X-User-Id` header rather than
  real authentication. In production this would be OAuth2/JWT with a proper
  identity provider.

- **No Kafka / event publishing.** Out of scope for this challenge brief,
  even though it's a topic worth discussing in a follow-up interview.

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
- Free-time search across multiple participants' calendars.
- A DB-level check tying `TimeSlot.meeting_id`/`owner_id` to an actual
  `MeetingParticipant` row for that meeting/user (see CLAUDE.md's "Known
  limitations" — currently only enforced in application code).
- Real user registration/authentication (OAuth2/JWT).
- Caching for availability queries (e.g. Redis), with invalidation on slot
  changes.
- Recurring availability rules (e.g. "available weekdays 9–17"), which would
  raise the pre-generation vs. on-the-fly generation trade-off — I'd lean
  toward pre-generation with a rolling horizon job, given a read-heavy access
  pattern.
