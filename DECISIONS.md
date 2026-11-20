# Decisions log

Choices made during implementation that `docs/SPEC.md` does not dictate. Newest first.

| PR | Decision | Why |
| --- | --- | --- |
| 27 | Allocation race tests check outcomes (counts) **and** that no maid's live jobs overlap, including travel; one case proves non-overlapping jobs all still fit | Catches double-booking and also over-eager locking that would wrongly refuse valid jobs |
| 26 | Find and rank without locks, then re-check the chosen maid under **her** lock with the same `CandidateFinder.candidate` rules; on failure try the next | Short locks, no stale decisions; one maid's lock at a time keeps the lock order safe |
| 26 | `allocate` returns the existing live assignment for the same job instead of making a second one | Retries (scheduler, payment replays) can't double-book a job |
| 26 | `AllocationService` takes maid locks only; callers must not hold a job lock when calling it | Lock order zone → maid → job: booking code allocates first, then updates the job |
| 26 | One `AllocationStrategy` bean (best fit) instead of a `allocation.strategy` switch | Only one strategy exists; replacing the bean is the extension point |
| 25 | Candidates come from live shifts in the job's zone and its neighbours (own zone first, then neighbours by id); a maid appears once | Spec: maids work their zone or a neighbour; a fixed order keeps allocation repeatable |
| 25 | Travel is measured from her previous job's zone, else her shift's zone; her shift must cover travel + job; the gap to her next job must cover the travel there | The maid must physically make every job on time |
| 25 | A neighbour-zone maid can fill a job whose capacity was reserved in the job's zone | Spec allows neighbour allocation; capacity stays per zone (a known simplification noted for later work) |
| 25 | `candidate(shift, zone, request)` is public | PR-26 re-checks the chosen maid under her lock with exactly the same rules |
| 24 | Travel score = 1 − travel minutes / 60 (same zone 0.75, neighbour 0.5, out of reach 0), measured from her previous job's zone, else her shift's zone | Shorter trips mean more jobs per day and fewer late arrivals |
| 24 | Fairness score = 1 / (1 + her jobs that day) | Spec's starvation score: spreads work so no maid sits idle while others are overloaded |
| 24 | Rating score = rating / 5; unrated maids get a neutral 0.5 | New maids must still get jobs to earn a rating |
| 24 | Default weights: travel 1.0, fairness 0.5, rating 0.5 (best fit 1.0, preferred 10.0) | Proximity and packing matter most for utilisation; fairness and rating break near-ties. Tune in `application.yml` |
| 23 | `AllocationRequest` and `Candidate` land here (planned for PR-26) | Scoring rules need the job and the candidate's shift, calendar and blocked time; defining them once avoids a throwaway type |
| 23 | Best-fit score = 1 / (1 + idle minutes left around the job / 30): 0 idle → 1.0, 30 min → 0.5 | Smaller leftover gaps pack calendars tighter and keep big gaps free (Urban Company's best fit); bounded 0–1 like every rule |
| 23 | Weights in `app.allocation.weights`: best-fit-gap 1.0, preferred-maid 10.0; a rule without a weight counts 1.0; ties go to the lower maid id | The preferred maid should win whenever she can do the job; deterministic ties make tests and support repeatable |
| 22 | `MaidFilter.matches(Maid)` takes only the maid (no `MatchContext` yet) and has `describe()` | Every spec filter depends on the maid alone; `describe()` lets a booking store its constraints as text |
| 22 | The four standard filters are beans in one `StandardFilters` config; a new filter is a new `MaidFilterFactory` bean anywhere | Adding a filter never edits existing code; the registry collects all factories |
| 22 | Unknown filter keys and bad values are 400s listing the supported keys; duplicate keys stop the app at startup | A typo like `minRatng` must not silently return unfiltered maids |
| 22 | `skills` filters on the maid's tags (all must match); tier and gender are case-insensitive | Services are chosen separately; tags carry finer skills like `deep-cleaning` |
| 21 | `MaidCalendar` is a read-only view built from her live assignments under the maid lock; assignments are stored in `AssignmentRepository` | One source of truth (the repository); the calendar can't drift from it |
| 21 | A job fits if its blocked time (travel + work) overlaps none of hers **and** the gap to her next job covers the travel to it | Inserting a job before another one in a different zone must leave her time to get there |
| 21 | Assignment statuses: HELD (instant job awaiting payment) → ACTIVE → RELEASED | Spec: the instant flow holds a maid before payment and activates on payment |
| 20 | Slot status comes from `CapacityService.headroom` = how many more such jobs fit (smallest over the job's buckets, both pools): 0 FULL, 1 FEW_LEFT, 2+ OPEN | Spec: FEW_LEFT is under two jobs' worth; one read-only number per start time keeps the grid cheap |
| 20 | The grid includes the same-zone travel buffer, so a start exactly at the beginning of every shift (e.g. 07:00 for 07:00 shifts) shows FULL | The maid must travel to her first job too; shifts should start before the first sellable slot |
| 20 | Start times inside the booking lead time (`app.booking.min-lead-minutes`, 120) or ending after midnight are left out of the grid; FULL slots carry no price | Matches the scheduled-booking rules, so every slot shown can actually be booked |
| 20 | Missing or mistyped query parameters now return our `ApiError` 400 | Consistent error shape for all endpoints |
| 19 | Prices come from a platform rate card in `app.pricing` (hourly rate per service × tier multiplier); startup fails if any service or tier is missing or not positive | Matches how Urban Company / Snabbit price; a missing rate is a config bug that should stop the app, not produce ₹0 jobs |
| 19 | Pricing takes a `PriceQuery` (service, tier, job time without travel, zone, instant flag) instead of `BookingType` | Keeps `pricing` independent of `booking`; the surge decorator (PR-49) gets the zone and instant flag it needs |
| 19 | Default rates: cleaning 249, cooking 299, dishwashing 199, laundry 199, babysitting 349 per hour; premium × 1.3 | Placeholder numbers in the range of public Indian home-service prices; change in `application.yml` |
| 18 | Race tests use `@RepeatedTest(20)`, 100 threads released by one latch, and check bucket totals as well as success counts | Races are probabilistic; checking that bucket minutes equal live reservations catches lost updates, not just overbooking |
| 17 | Lapsed holds are released lazily inside `reserve` (for that zone) **and** by `HoldSweeper` every minute | Lazy release means a booking never fails because of a dead hold; the sweeper keeps the slot grid right in quiet zones |
| 17 | Paying (commit) after the hold lapsed → 409 `HOLD_EXPIRED` and the minutes are released, even if the sweeper hasn't run | The slot may already be resold; the customer must book again |
| 17 | `CapacityHoldExpired` is published after the zone lock is released | The booking side (PR-33+) will expire the job; listeners can take any lock |
| 16 | `reserve` checks every bucket in both pools first, then reserves; the 409 lists **every** bucket that lacks room | All or nothing, and the client learns exactly which times are full |
| 16 | A reservation stores the minutes it took per bucket; `release` gives back exactly those | Release can't drift from reserve even if shifts change in between |
| 16 | `holdUntil = null` means a hold with no expiry | Spec: later recurring jobs are held without expiring |
| 16 | Releasing twice or committing a released reservation is an `INVALID_STATE` error, not a silent no-op | A double release would hand out minutes twice; failing loudly protects the ledger |
| 15 | `ShiftService` publishes `ShiftChanged` **after** releasing the maid lock | Listeners (the ledger) take the zone lock, which must come before the maid lock; publishing inside would be a lock-order violation (a test proves it) |
| 15 | The ledger listens synchronously (`@EventListener`) | Capacity is updated before the shift API call returns, so slots are never shown from stale capacity; easy to test |
| 15 | Pools come from the maid's skills at the time of the event | Skills don't change after onboarding yet; if editing skills is added, it must re-publish her future shifts |
| 14 | A bucket's pool is a string: the service name, or `ANY` for the zone-wide pool | One bucket type for both pools; factories `forService` / `forZone` keep callers from typing the string |
| 14 | `free = floor(capacity × utilisation factor) − reserved`; factor 0.85 in `app.capacity.utilisation-factor` (typed `@ConfigurationProperties`) | The spec's safety margin so confirmed jobs can still be packed into maids' calendars |
| 14 | Removing capacity never goes below 0 but may go below what's reserved (free turns negative = full) | Leave must always be accepted; existing jobs are kept and re-allocated |
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
