# Decisions log

Choices made during implementation that `docs/SPEC.md` does not dictate. Newest first.

| PR | Decision | Why |
| --- | --- | --- |
| 13 | `PUT /live-state` takes a target status; a maid may only set IDLE, BREAK or OFFLINE. EN_ROUTE / ON_JOB are reserved for the job lifecycle | Matches the spec's endpoint; job statuses must come from real jobs |
| 13 | OFFLINE → IDLE is check-in: it needs a PLANNED shift starting within 30 min (`app.shift.early-check-in-minutes`) and starts that shift. Going OFFLINE ends any checked-in shift | Spec: check-in requires a shift today; maids often arrive a little early |
| 13 | Live-state transitions are a table in `MaidLiveState` (EnumMap of allowed moves) | Keeps rules in the entity and readable at a glance |
| 12 | `POST /maids/{id}/shifts` adds one shift from a JSON body; with `?generateFrom=&to=` (no body) it generates from the template | Matches the spec's single endpoint; Spring routes on the query parameters |
| 12 | A hand-added shift that overlaps another or falls on a leave day is a 409 `SHIFT_CONFLICT` (generation skips instead) | A person adding one shift should be told why it failed; bulk generation should be repeatable |
| 12 | Shift, template and leave endpoints live in one `SupplyController` under `/api/v1/maids/{maidId}` | All are about one maid's working time; keeps `MaidController` to the profile |
| 11 | Leave is whole days (both dates inclusive) and can't start in the past | Matches the spec's `Leave(maidId, from, to)`; part-day sickness is a separate, not-yet-approved "end shift early" feature |
| 11 | Leave cancels only PLANNED shifts; a CHECKED_IN shift is left for the maid to end | She is already working; cancelling would be an illegal transition |
| 11 | Re-allocating jobs on cancelled shifts is not done here | Assignments don't exist yet; PR-47 adds the leave listener that releases and re-assigns jobs |
| 10 | Generating shifts skips template windows that clash with an existing shift (and reports how many) instead of failing | Re-running generation for the same weeks is safe and creates no duplicates; manual shifts win over the template |
| 10 | Generation covers at most 31 days per call, never starts in the past, and runs under the maid's lock | Bounded work per request; two generations for one maid can't create overlapping shifts |
| 10 | A template is stored per maid (`WeeklyShiftTemplate`) and replaced as a whole | Simpler than editing single windows; matches `PUT /shift-template` in the spec |
| 09 | Shift transitions: PLANNED → CHECKED_IN → ENDED; PLANNED → ABSENT or CANCELLED. Only PLANNED and CHECKED_IN add capacity | Matches the spec; a maid who checked in can't be cancelled, she ends her shift instead |
| 09 | Shifts are 2–10 hours and must end on the day they start (so 20:00–01:00 is two shifts) | Spec input rule; keeps a shift's capacity inside one day's buckets |
| 09 | `ShiftChanged` is a plain record (CAPACITY_ADDED / CAPACITY_REMOVED); the service that changes shifts publishes it | The supply package never calls the capacity package; the ledger listens instead |
| 08 | Controllers and DTOs live in `api.<area>` (e.g. `api.maid`); services take command records, not DTOs | Keeps HTTP shapes out of the domain; the spec's `api/` package holds all controllers |
| 08 | Maid ids are `m-` + 8 random hex characters | Short, readable in logs and URLs; collisions are negligible at this scale |
| 08 | Onboarding activates the maid immediately and rejects a phone number already registered (compared on the last 10 digits) | The spec says onboarding saves an ACTIVE maid; one person shouldn't get two profiles |
| 40 | Business errors (`DomainException`) are stored and replayed for the same key; unexpected errors free the key | A declined payment stays declined on retry (use a new key to try again), but a crash or timeout can be retried safely |
| 40 | Added `REQUEST_IN_PROGRESS` (409) to `ErrorCode` | The spec names it for a repeat that arrives while the first request is still running |
| 40 | `IdempotencyStore` is a concrete in-memory component, no interface yet | Only one implementation is planned; extract an interface if a Redis store is added |
| 07 | `Maid` is a class (not a record) because its status changes; every other field is final | Status moves only through `activate()` / `suspend()`, which reject illegal moves |
| 07 | New maids start ONBOARDING; ACTIVE ↔ SUSPENDED after that | Only ACTIVE maids get shifts, appear in search or receive jobs |
| 07 | Phone must be an Indian mobile (`+91` optional, 10 digits starting 6–9); rating 0–5 where 0 = unrated | Single-city India assumption; lets the rating filter treat unrated maids explicitly |
| 07 | `Tier` has no price multiplier yet | Multipliers belong to the rate card (PR-19), not the maid |
| 06 | Travel to a non-neighbour zone is "out of reach" (empty), not a long estimate | Maids only work their zone or a neighbour; an empty result makes that rule explicit for allocation |
| 06 | Neighbour links count from either side (A lists B = B is next to A) | Seed data can't drift into one-way neighbours |
| 06 | Unknown locality → 400 `VALIDATION_FAILED` with a "we don't serve" message | Matches the spec's error table |
| 04 | `Money` is INR-only and never negative; `minus` below zero throws | The spec has one currency; a negative price or refund is always a bug |
| 04 | `RecurrenceRule` rejects a slot that would run past midnight, and both dates are inclusive | Matches the spec's "never crosses midnight" rule; inclusive end date is what customers expect |
| 05 | One `ReentrantLock` per key (map) instead of fixed stripes | Stripes could put two unrelated keys on one lock; one lock per key keeps waits exact. Memory is fine at this scale |
| 05 | Lock order is checked per thread at runtime: a key must sort after the last one held (re-entry allowed) | Turns a possible deadlock into an immediate, testable error |
| 05 | `Repository.getById` throws `NotFoundException` (404) using the repository's entity name | Every service gets the same 404 behaviour without repeating it |
| 03 | `TimeSlot.minutesPerBucket(30)` returns minutes per bucket (ordered map) instead of the plan's `buckets(30)` list | The capacity ledger needs per-bucket minutes, e.g. a 15-min travel buffer takes 15 of the 08:30 bucket |
| 03 | Invalid slots and bucket sizes throw `ValidationException` (400), not `IllegalArgumentException` | Bad times usually come from requests, so clients get a 400 with a message instead of a 500 |
| 02 | `ErrorCode` enum carries its HTTP status; `GlobalExceptionHandler` lives in `api` and is the only place statuses are applied | One table of codes from the spec, no HTTP types in domain exceptions |
| 01 | Spring Boot 3.5.0, compiled with `maven.compiler.release=17` | Meets the spec's Java 17+ requirement and still builds on newer JDKs |
| 01 | Plain `mvn` instead of the Maven wrapper | The wrapper couldn't be generated offline; GitHub runners ship Maven. Add the wrapper in a follow-up if wanted |
| 01 | `app.time-zone` property (default Asia/Kolkata) feeds the single `Clock` bean | All time logic uses one injectable clock, as the spec requires |
