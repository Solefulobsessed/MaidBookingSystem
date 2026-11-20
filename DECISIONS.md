# Decisions log

Choices made during implementation that `docs/SPEC.md` does not dictate. Newest first.

| PR | Decision | Why |
| --- | --- | --- |
| 37 | `generate()` tops up each ACTIVE subscription to today + 13 days (capped at the series end); on a normal day that is one new day, after a missed run it catches up. PAUSED subscriptions are not extended | Spec: "extends each ACTIVE subscription by one day"; catching up keeps the 14-day promise if a run is missed |
| 37 | Each generated visit is reserved with no expiry and gets a HELD maid: the maid of the series' latest earlier visit, else the customer's preferred maid, as the allocation preference; anyone else if she can't | Spec: continuity via a high preferred-maid score; recurring charges (PR-42) later release the maid and capacity if a charge fails |
| 37 | If allocation finds nobody, the visit is still created without a maid | Capacity is reserved; payment or the allocation retry job assigns someone later |
| 37 | A day without capacity is kept in `Subscription.skippedNoCapacity` and logged as "customer to be notified"; the window still moves on | Spec: SKIPPED_NO_CAPACITY, series not cancelled. There is no notification channel yet, so the log stands in for it |
| 37 | Pricing and reserving one visit moved into `JobReserver`, shared by `BookingService` and `SubscriptionService` | Both create jobs the same way; one place for price, travel buffer, reservation and OTP |
| 37 | The daily run is `SubscriptionGenerationJob` at 00:30 IST (`app.subscription.generation-cron`) | Same pattern as `HoldSweeper`; a quiet hour, after the date changes |
| 36 | The first window is the 14 days from the series' start date; visits less than 2 hours away are skipped; `generatedUntil` = the window's last day (or the series end if sooner) | Spec: "jobs for the first 14 days"; the same 2-hour lead as scheduled bookings |
| 36 | A series starts between today and 30 days ahead and runs 1 week to 6 months (end date ≥ start + 6 days, ≤ start + 6 months); visits follow the duration and half-hour rules | Spec input rules; reuses the scheduled booking window |
| 36 | Only the first visit gets the 10-minute payment hold; later visits are reserved (HELD) with no expiry until they are charged | Spec: the other jobs are kept "without a hold expiry, because the subscription is the commitment". They stay HELD so payment can commit each one as usual |
| 36 | Every visit is tried before failing; if any clash, all reservations are released and 409 `SLOT_UNAVAILABLE` carries `details.conflictDates`. A single-job booking keeps the ledger's own error (bucket times) | Spec: "the response lists the dates that clashed"; one-job bookings already had a precise error |
| 36 | A booking with a subscription never hard-holds the preferred maid; the subscription keeps her as a soft preference | Spec: recurring treats the preferred maid as soft; tied to having a subscription, not to the booking type |
| 36 | Subscription ids are `sub-` + 8 hex; `Subscription` moves ACTIVE ↔ PAUSED and to CANCELLED (once) | `s-` is already used for shifts; transitions per the spec's status list |
| 36 | The saved payment method is not stored on the subscription yet | Payment details arrive with PR-39; recurring charges (PR-42) will add it |
| 35 | A preferred maid is held (HELD assignment) at booking time and only she is tried (`AllocationService.allocateOnly`); payment then activates her like an instant maid | Spec: allocate only that maid in the same request, so the customer knows at once whether she's free |
| 35 | Unknown preferred maid → 404; one who doesn't offer the service or isn't of the booked tier → 400, checked before anything is reserved; not free or out of area → 409 `MAID_UNAVAILABLE` with `details.maidId` | Static facts about the maid are input errors; her calendar and shifts are the "busy" case |
| 35 | Booked tier must equal the preferred maid's tier | Price is per tier, so the amount charged matches what the search quoted for her |
| 35 | Instant bookings reject `preferredMaidId` (400) | Instant sends the nearest idle maid; choosing a maid is the scheduled preferred-maid mode |
| 35 | Recurring bookings will treat the preferred maid as a soft preference; PR-36 decides how that type skips the hard hold | Spec: "Recurring subscriptions treat the preferred maid as a soft preference" |
| 34 | The instant handler plans the slot from the nearest dispatch option (start = now rounded up to 5 min + her ETA); `AllocationService.dispatch` then holds a maid whose option has exactly that slot | One `now` per request makes plan and dispatch agree; a maid who'd start at a different time isn't silently given another slot |
| 34 | `AllocationService` gets the `DispatchStrategy`; dispatch reuses the same lock-and-re-check loop as `allocate` | Two instant bookings racing for one idle maid: the second finds her calendar taken and moves on, as in PR-27 |
| 34 | If no maid can be held after capacity was reserved (she was just taken), all holds and reservations are released and the request fails with 409 `NO_MAID_NEARBY` | Spec: "if allocation fails, release the capacity and return 409"; nothing is left half-booked |
| 34 | `NO_MAID_NEARBY` from planning carries `details.alternatives`: the next three open scheduled slots today or tomorrow (`InstantAvailabilityService.alternatives`, now public) | Spec asks for three alternatives; reusing the `/slots/instant` logic keeps both answers identical |
| 34 | The instant handler resolves the zone and preference filters itself, as `BookingService` does | The handler interface only gets the request; resolving twice is cheap and keeps the interface as the spec defines it |
| 33 | A job reserves its slot plus the same-zone travel buffer (15 min) before it, held for `app.booking.hold-minutes` (10) | Matches the spec's ledger example (a 09:00 job also takes 15 min of the 08:30 bucket) and its 10-minute payment hold |
| 33 | All-or-nothing: if any job's reservation fails, the reservations already made are released and nothing is saved | The booking is never left half-reserved; the customer gets the `SLOT_UNAVAILABLE` conflict as is |
| 33 | Request checks (type rules, locality, preferences) run before any capacity is reserved | A bad request costs nothing and never touches the ledger |
| 33 | Booking ids are `b-` and job ids `j-` + 8 hex characters; each job gets a random 4-digit OTP from `SecureRandom` | Same id style as maids and shifts; the OTP must not be guessable |
| 33 | `PriceQuery.instant` is true when the handler's mode is DISPATCH_NOW | Keeps the template free of booking-type checks; instant pricing follows how the job is served |
| 33 | `amountDueNow` = price of the earliest unpaid job | One job for instant and scheduled; for recurring, the customer pays the first visit at creation (spec) |
| 33 | Responses list each job's start and end separately, plus its OTP for the customer | Easier to read and parse than the spec's `09:00/10:00` slot string; the customer shows the OTP to the maid |
| 32 | `CreateBookingRequest` is a record in the `booking` package; the controller (PR-33) maps its JSON onto it | Handlers live in the domain and must not depend on `api` DTOs; fields a type doesn't use stay null and each handler checks its own |
| 32 | `BookingPlan` holds only the job slots for now; the `Subscription` part is added with recurring bookings (PR-36) | No subscription type exists yet; adding the field then keeps this PR free of placeholders |
| 32 | A booking type with no handler bean is a 400 `VALIDATION_FAILED`; two handlers for one type stop the app at startup | Instant and recurring arrive in later PRs, so asking for them now is a client error, while a duplicate handler is a wiring bug |
| 32 | The 30-day limit is `app.booking.max-advance-days` (default 30), next to the existing 2-hour `min-lead-minutes` | Both ends of the booking window are tunable in one place |
| 31 | Booking status is computed, never stored: open job + something paid → ACTIVE; open jobs, nothing paid → PENDING_PAYMENT; all final → COMPLETED if any visit happened, else CANCELLED | One source of truth (the jobs); a recurring series stays ACTIVE while later visits await their charge |
| 31 | The booking keeps the customer's `MaidFilter` constraints and preferred maid | Every job of the booking, and every re-allocation after a drop-out, must honour the same requirements |
| 30 | Allowed job moves live in one table in `JobStatus`; `Job` methods add the business guard (e.g. `confirm` only from PENDING_PAYMENT, `start` needs the right OTP) | The table can't tell "payment arrived" from "maid dropped out" (both end in CONFIRMED); the per-method guard caught that in tests |
| 30 | An instant job holds its maid while unpaid (`holdMaid`); `confirm` then moves it straight to ASSIGNED | Matches the spec's instant flow: the maid is held with the capacity and activated on payment |
| 30 | The OTP is passed in when the job is created (4 digits), not generated inside `Job` | Keeps the entity deterministic and testable; the booking service will generate it |
| 29 | Instant dispatch picks from maids who are checked in and IDLE in the job's zone or next door, whose travel is within the promise (`app.instant.promise-minutes`, 15); nearest first, then maid id | Snabbit-style dispatch; with 15/30-min travel, only same-zone maids qualify by default |
| 29 | Instant start = now rounded up to 5 min + travel; the maid must still pass the full `CandidateFinder` check | Gives the maid a moment to leave; shift end, calendar and skill rules stay identical to scheduled jobs |
| 29 | `GET /slots/instant` reserves nothing; when nobody can come it returns the next 3 bookable slots (today, then tomorrow) with full date-times | Availability is a quote; booking (PR-34) re-checks and reserves |
| 29 | Duration rule moved to one `JobDurations.validate` used by slots, search and instant | Removes three copies of the same rule |
| 28 | Maid search reuses `CandidateFinder` (same shift, skill, calendar and travel checks as allocation) | A maid shown in search can really be booked; one set of rules, not two |
| 28 | Search returns nothing when the zone has no capacity headroom for the slot, even if a maid looks free | Unassigned confirmed jobs already hold that capacity; showing her would lead to a failed booking |
| 28 | Results sorted by rating (high first), then quoted price (low first), then id; paged (size ≤ 50); any extra query parameter is a filter | Spec ordering; unknown parameters hit the filter registry and become 400s instead of being ignored |
| 28 | `StandardFilters` bean methods are public | Other packages' tests can build the real registry |
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
