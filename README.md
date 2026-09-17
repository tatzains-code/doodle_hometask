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
| `GET` | `/slots` | List my own slots (full detail) |
| `PATCH` | `/slots/{slotId}` | Modify a slot (only while `FREE`) |
| `DELETE` | `/slots/{slotId}` | Delete a slot (only while `FREE`) |
| `GET` | `/availability?ownerId=&from=&to=&duration=` | Another user's availability — restricted view (timing + status only). Defaults to the next 7 days if `from`/`to` are omitted; range is capped. When `duration` is given, adjacent `FREE` slots are merged into contiguous windows before filtering, so several back-to-back short slots can satisfy a longer request. |
| `POST` | `/meetings` | `{ title, description, start, end, participantIds }` — atomically checks and books every participant, or fails entirely |
| `GET` | `/meetings` | List my meetings (as organizer or participant) |
| `DELETE` | `/meetings/{meetingId}` | Cancel (organizer only) — every participant's slot(s) return to `FREE` |

There is no separate "book a single slot" endpoint — a 1:1 meeting is simply
`POST /meetings` with one entry in `participantIds`.

Errors are returned as `ProblemDetail` (RFC 7807) via a single global
exception handler: 404 for missing resources (including a well-formed but
non-existent user id), 400 for malformed/invalid requests (including a
missing or malformed `X-User-Id` header, and Bean Validation failures),
409 for state conflicts (double-booking, modifying a `BUSY` slot,
optimistic-lock failures), 403 for unauthorized cancellation.

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

- **Configurable slot duration, aligned to a granularity step.**
  Slot start times and durations are validated against a configurable
  granularity (`scheduling.slot.granularity-minutes`, default 15) via a
  single `@ValidTimeRange` bean-validation constraint, rather than
  duplicating the check across `SlotService` and `MeetingService`. This
  granularity assumption isn't a stated requirement of the brief — it's my
  interpretation of "configurable duration" — and I made it an explicit,
  documented assumption rather than a silent hardcoded value. The same
  config block also holds `min-duration-minutes`/`max-duration-minutes`,
  `min-booking-buffer-minutes` (no booking in the near past), and
  `max-horizon-days` (no booking arbitrarily far in the future).

- **Closed-world availability: explicit coverage required, no auto-filling.**
  Booking a participant into `[start, end]` requires their existing `FREE`
  slots to fully and contiguously cover that range — one slot, or several
  adjacent ones (`MeetingService.isFullyCovered`). A gap, a `BUSY` slot
  overlapping the range, or simply no slot at all for part of the range all
  mean "not available" — absence of data is never treated as "free". No new
  slot rows are created during booking; only existing `FREE` slots are
  transitioned to `BUSY`. I started from the more permissive "absence of
  data = free" reading and deliberately revised it: closed-world
  availability is what lets this extend cleanly to rule-generated
  availability later (e.g. a recurring "9–12, 13–14" rule that simply never
  generates a lunch-hour slot) without a schema change — an open-world
  default would silently treat that protected gap as bookable the moment
  such a rule existed.

- **All-or-nothing booking across participants.**
  `MeetingService.bookMeeting` runs as a single `@Transactional` operation:
  every participant's availability is checked before any row is written,
  and if any participant is unavailable the whole request fails and
  nothing commits — no `Meeting`, no `MeetingParticipant`, no slot flipped
  to `BUSY` for anyone. The per-participant coverage check is re-run at
  write time rather than trusted from the earlier read, so a conflicting
  slot committed by a concurrent transaction in between is still caught
  before this one commits.

- **Why `Meeting` stores its own `start`/`end`.**
  `Meeting.start`/`Meeting.end` are populated directly from the organizer's
  request rather than derived by scanning participants' `TimeSlot` rows.
  Earlier iterations of the schema put a `slot_id` reference on `Meeting`,
  then on `MeetingParticipant` — both broke once a meeting could be covered
  by more than one contiguous `FREE` slot per participant (the "several
  adjacent slots" case in closed-world coverage above has no single slot to
  point at). Giving `Meeting` its own authoritative `start`/`end`, and
  linking slots back via the nullable `TimeSlot.meeting` FK (cleared on
  cancellation), sidesteps that entirely.

- **No slot splitting, no atomic reschedule.**
  Booking always consumes existing `FREE` slots whole — it never carves a
  slot into a booked part and a leftover free part; rescheduling is
  cancel-then-rebook through the existing endpoints, not an atomic
  slot-swap. Both share the same root cause: each would need its own
  multi-row atomic operation, with dedicated concurrency tests,
  disproportionate to a 4-hour time box — see "What I'd add" below.

- **`/availability?duration=X` merges contiguous `FREE` slots, matching
  what booking already allows.**
  Booking can satisfy a meeting window from several adjacent `FREE` slots
  (see closed-world coverage above), so an availability search needed the
  same view — otherwise a participant bookable for 90 minutes across three
  30-minute slots would never surface in a search for 90-minute
  availability. `AvailabilityService` loads the owner's `FREE` slots in
  range, merges runs that are exactly back-to-back
  (`slotA.end == slotB.start`, no gap or overlap), and filters by the
  merged run's total span rather than any individual slot's duration. Each
  run is returned as one restricted-projection result with its combined
  `start`/`end` — the caller never sees that it was internally several slot
  rows. This only changes how `/availability` computes windows; the
  booking transaction already handled contiguous coverage separately and
  wasn't touched.

- **Authorization boundary between "own" and "restricted" views.**
  `GET /slots` (own slots) returns the full `SlotResponse`; `GET
  /availability` (someone else's) returns `AvailabilitySlotResponse` — id,
  timing, and `FREE`/`BUSY` status only, with no meeting reference at all.
  This split is enforced in the service/mapper layer via a dedicated DTO,
  not left to a shared response type happening to omit the right fields for
  the right caller.

- **Concurrency: optimistic locking, and a DB-level exclusion constraint
  scoped to slot creation.**
  `@Version` on `TimeSlot` covers row-local races: two concurrent requests
  touching the same slot row (a `PATCH`/`DELETE`, or two `POST /meetings`
  both trying to consume the same `FREE` slot) — the loser gets an
  optimistic-lock failure, mapped to 409. The Postgres GiST exclusion
  constraint on `(owner_id, tstzrange(start, end))` is the belt-and-braces
  layer: it makes a given owner holding two overlapping slot rows
  impossible at the DB level regardless of which transaction the
  conflicting row came from, which matters most for `POST /slots`; since
  booking only ever transitions existing rows rather than inserting new
  overlapping ones, its role during booking itself is secondary to
  `@Version`. I didn't add pessimistic locking (`SELECT ... FOR UPDATE` on
  `User` rows, as seen in at least one other public solution to this same
  challenge) — it isn't needed here, because every participant already gets
  a materialized `TimeSlot` row per booking, and the exclusion constraint
  enforces non-overlap at write time without needing to serialize access to
  the user row itself.

- **Recurring availability rules are out of scope, and closed-world
  booking is what makes that extension safe later.**
  Recurring/rule-based availability (e.g. "available weekdays 9–17") isn't
  implemented — every `FREE` slot is created explicitly via `POST /slots`.
  This is a known limitation I thought through, not an oversight: it's
  exactly what the closed-world availability decision above was chosen to
  support cleanly. If rules were added later, a rule engine would
  materialize `FREE` slots (or the coverage check would consult rules
  directly) without needing to touch the closed-world semantics that
  already treat "no slot" as "not available."

- **Auth is stubbed.**
  There's no login flow: `POST /users` creates a user from `{ name, email }`
  with no password, and every other request carries that user's id in an
  `X-User-Id` header, resolved per-request by `CurrentUserResolver`. This is
  a deliberate stand-in for the time box, not a design I'd ship as-is — real
  auth would be OAuth2/JWT, with the header replaced by a validated bearer
  token and `X-User-Id` removed entirely.

## AI usage

Claude (via Claude Code) was used for boilerplate (DTOs, mappers, the
repetitive `@ExceptionHandler` wiring in `GlobalExceptionHandler`,
Testcontainers setup), drafting integration tests from a description of each
scenario, and as a sounding board for trade-offs — e.g. talking through
open-world vs. closed-world availability semantics before settling on
closed-world. The scope decisions, the domain rules, and the reasoning
written up in this README are my own; a `CLAUDE.md` in this repo records the
constraints I gave the assistant up front, specifically so it would work
within them rather than quietly redesign them.

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
