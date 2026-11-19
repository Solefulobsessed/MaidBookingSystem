# PR plan

The spec ([SPEC.md](SPEC.md)) splits into 52 pull requests of about 100 lines of production code each, plus their tests. Every PR compiles, passes `mvn test`, and adds one reviewable concern on top of PRs already merged.

## Ground rules for every PR

- **Size:** about 100 lines of production code, excluding imports and blank lines. Tests are extra and expected. Over 150 lines means split the PR.
- **One concern:** one entity, one service method or one endpoint group. Refactors go in their own PR, never mixed with features.
- **Always green and shippable:** each PR compiles, passes `mvn test`, and leaves the app runnable. No stubs left behind, no commented-out code.
- **Interfaces first:** when a PR introduces an extension point, it ships the interface, the registry and at least one real implementation. Further implementations come in follow-up PRs.
- **Tests in the same PR:** every new class with logic gets a unit test in that PR. Domain and service PRs need no Spring context.
- **Title and branch:** `PR-07: Maid onboarding service and endpoint`, branch `pr-07-maid-onboarding`. The description links the spec section and lists what is deliberately left for later.
- **Reviewer checklist:** naming matches the spec; state changes only through entity methods; `Clock` injected; `BigDecimal` for money; no `switch` on types; the lock order is zone → maid → job.

## Merge waves

A PR's wave is one more than the latest wave among its dependencies, so all PRs in a wave can be built and reviewed in parallel. The critical path is 01 → 05 → 07 → 09 → 10 → 11 → 15 → 16 → 33 → 41 → 44 → 46 → 47 → 52. It runs through the capacity ledger, booking, payment and cancellation; prioritise reviews there.

| Wave | PRs (parallel) | Count |
| --- | --- | --- |
| 1 | 01 | 1 |
| 2 | 02, 03, 05 | 3 |
| 3 | 04, 06, 07, 40 | 4 |
| 4 | 08, 09, 14, 19, 21, 22, 30, 38 | 8 |
| 5 | 10, 13, 23, 25, 31, 39 | 6 |
| 6 | 11, 24, 26, 28, 32, 43 | 6 |
| 7 | 12, 15, 27 | 3 |
| 8 | 16, 50 | 2 |
| 9 | 17, 18, 20, 29, 33, 49 | 6 |
| 10 | 34, 35, 36, 41 | 4 |
| 11 | 37, 44, 48 | 3 |
| 12 | 42, 45, 46 | 3 |
| 13 | 47, 51 | 2 |
| 14 | 52 | 1 |

## PRs 01–18: foundation, supply, capacity

| PR | Title | What's in it | Tests | Depends on | ~Lines |
| --- | --- | --- | --- | --- | --- |
| 01 | Project skeleton | `pom.xml`, `MaidBookingApplication`, `application.yml`, `ClockConfig` (injectable `Clock`), `/actuator/health` | Context loads | — | 60 |
| 02 | Error model | `DomainException` hierarchy (NotFound, Validation, Conflict, InvalidStateTransition), `ApiError` record, `GlobalExceptionHandler` | Each exception → status and body | 01 | 100 |
| 03 | `TimeSlot` | Half-open record, validation, `overlaps`, `contains`, `duration`, `buckets(30)`, `withBufferBefore` | Overlap matrix, bucket splitting | 01 | 80 |
| 04 | `Money`, `RecurrenceRule` | Money (BigDecimal, INR, plus/times/percent); RecurrenceRule with `expand(from, to)` | Rounding; weekdays at 9am, Sundays only, end-date cut | 03 | 110 |
| 05 | Locks and repository base | `LockRegistry` (zone/maid/job striped `ReentrantLock`s, `withLocks` helper enforcing order), generic `InMemoryRepository<T, ID>` | Lock-order guard, CRUD | 01 | 90 |
| 06 | Zones | `Zone`, `ZoneRepository`, `ZoneResolver` (locality → zone), `TravelTimeEstimator` (same 15 min / neighbour 30 min) | Resolve, unknown locality, travel | 05 | 90 |
| 07 | Maid entity | `Maid`, enums `ServiceType`, `Tier`, `Gender`, `MaidStatus`, `MaidRepository` | Invariants (≥ 1 skill) | 05 | 80 |
| 08 | Maid onboarding API | `MaidService.onboard`, `POST /maids`, `GET /maids/{id}`, request DTO + validation | Service + `@WebMvcTest` | 02, 07 | 100 |
| 09 | Shift entity | `Shift`, `ShiftStatus` transitions, `ShiftRepository` (by maid/date, by zone/date), `ShiftChanged` event | Transitions, illegal moves | 03, 06, 07 | 90 |
| 10 | Shift templates | `ShiftTemplate`, `ShiftService.setTemplate`, `generateShifts(from, to)` with overlap check | Generation, overlap rejected | 09 | 100 |
| 11 | Leave | `Leave`, `ShiftService.addLeave` cancels overlapping shifts and publishes `ShiftChanged` | Leave cancels the right shifts | 10 | 80 |
| 12 | Supply endpoints | `PUT /shift-template`, `POST /shifts`, `POST /leaves` | `@WebMvcTest` | 08, 11 | 90 |
| 13 | Live state | `MaidLiveState`, `LiveStatus`, `PUT /maids/{id}/live-state` (check in/out) | Check-in requires a shift today | 08, 09 | 90 |
| 14 | Capacity buckets | `CapacityBucket` (zone, service or ANY, date, bucketStart, capacity, reserved, version), `BucketKey`, repository | `free()` with utilisation factor | 03, 06, 07 | 80 |
| 15 | Capacity ledger | `CapacityLedger` listens to `ShiftChanged`: adds/removes minutes per service pool and once in the ANY pool | Multi-skill counted once in zone pool | 11, 14 | 100 |
| 16 | Reserve / commit / release | `CapacityReservation`, `CapacityService.reserve` (both pools, all buckets, under the zone lock), `commit`, `release` | All-or-nothing; release exactly undoes reserve | 15 | 120 |
| 17 | Holds | Lazy purge of expired HELD reservations inside reserve; `HoldSweeper` `@Scheduled` every minute | Expiry via `MutableClock` | 16 | 60 |
| 18 | Capacity race test | Test-only: 100 threads vs room for 10 jobs, run 20 times | Exactly 10 succeed, never negative | 16 | 0 (+80 test) |

## PRs 19–29: pricing, slots, allocation, search, dispatch

Allocation takes an `AllocationRequest` (zone, service, slot, constraints, preferred maid, excluded maids, reference id), not a `Job`. That lets PRs 21–27 land before the booking PRs and keeps `allocation` independent of `booking`.

| PR | Title | What's in it | Tests | Depends on | ~Lines |
| --- | --- | --- | --- | --- | --- |
| 19 | Rate-card pricing | `RateCard` (service → hourly rate), `PricingStrategy`, `TierPricing` (rate × hours × tier multiplier) | Quotes per tier and duration | 04, 07 | 70 |
| 20 | Slot grid | `SlotService.slots` (07:00–20:00 starts, OPEN / FEW_LEFT / FULL, price), `GET /slots` | Travel buffer uses the previous bucket; FULL when either pool is short | 02, 16, 19 | 110 |
| 21 | Maid calendar | `Assignment`, `AssignmentStatus`, `MaidCalendar` (`TreeMap`; `fits(blocked, nextJobBuffer)`, `add`, `release`), repository | Gaps, back-to-back with buffers | 03, 07 | 110 |
| 22 | Filters | `MaidFilter`, `MaidFilterFactory`, `FilterRegistry`, MinRating, Tier, Gender, Skills/tags factories, `InvalidFilterException` | Each filter; unknown key → 400 | 02, 07 | 110 |
| 23 | Scoring core | `ScoringRule`, `MaidScorer` (weights from config), `ScoredCandidate` with reasons, `BestFitGapRule`, `PreferredMaidRule` | Tightest gap wins; preferred bonus | 21 | 100 |
| 24 | More scoring rules | `TravelDistanceRule`, `FairnessRule` (jobs today), `RatingRule` | Each rule in isolation and combined | 06, 23 | 80 |
| 25 | Candidate finder | `CandidateFinder`: on-shift in zone or neighbour, covering buffer + slot, skill, constraints, not excluded, calendar fits | Excludes leave, wrong zone, short shift | 06, 09, 21, 22 | 100 |
| 26 | Allocation service | `AllocationRequest`, `AllocationStrategy`, `BestFitAllocationStrategy`, `AllocationService.allocate` (re-check under maid lock, next candidate on conflict) | Picks best; never violates constraints | 23, 25 | 110 |
| 27 | Allocation race test | Test-only: two parallel allocations for one maid with overlapping slots | No overlapping assignments | 26 | 0 (+70 test) |
| 28 | Preferred-maid search | `MaidSearchService.search` (finder + quote + sort + page), `GET /maids/search` | Only available maids; filters combined | 19, 22, 25 | 100 |
| 29 | Instant dispatch | `DispatchStrategy`, `NearestIdleDispatchStrategy` (live state, ETA ≤ 15 min, shift room), `GET /slots/instant` | Nearest wins; shift ending excluded; none → alternatives | 13, 16, 24, 25 | 120 |

## PRs 30–42: jobs, booking types, payments

| PR | Title | What's in it | Tests | Depends on | ~Lines |
| --- | --- | --- | --- | --- | --- |
| 30 | Job state machine | `Job`, `JobStatus`, guarded transitions (`confirm`, `assign`, `unassign`, `markUnfulfilled`, `cancel`, `expire`, `enRoute`, `start(otp)`, `complete`) | Every legal and illegal transition | 04 | 120 |
| 31 | Booking aggregate | `Booking`, `BookingType`, derived `BookingStatus`, `BookingRepository`, `JobRepository` | Derived status cases | 30 | 80 |
| 32 | Booking type registry | `BookingTypeHandler`, `BookingPlan`, `AllocationMode`, `BookingTypeRegistry`, `ScheduledBookingHandler` | Validation windows (2 h–30 days) | 31 | 90 |
| 33 | Create booking | `BookingService.create` template (handler → plan → price → reserve all → save; release all on failure), `POST /bookings`, `GET /bookings/{id}`, `GET /customers/{id}/bookings` | Scheduled happy path; capacity conflict leaves nothing reserved | 02, 16, 19, 32 | 120 |
| 34 | Instant booking | `InstantBookingHandler`, DISPATCH_NOW path: reserve, dispatch, held assignment; release both on failure | Assigned at once; NO_MAID_NEARBY releases capacity | 26, 29, 33 | 90 |
| 35 | Preferred maid | Preferred-maid branch in `BookingService` (allocate only that maid; 409 `MAID_UNAVAILABLE`) | Preferred busy → 409, nothing held | 26, 33 | 60 |
| 36 | Recurring booking | `Subscription`, `SubscriptionStatus`, `RecurringBookingHandler` (first 14-day window, all-or-nothing, conflict dates in error) | Weekday plan; one clash fails all | 33 | 110 |
| 37 | Subscription generation | `SubscriptionService.generate` (extend by a day, prefer last maid, SKIPPED_NO_CAPACITY), `@Scheduled` daily | Continuity; skip on no capacity | 26, 36 | 100 |
| 38 | Payment gateway | `PaymentGateway`, `GatewayChargeRequest/RefundRequest`, `GatewayResult`, `MockPaymentGateway` (configurable failures, idempotent on key) | Success, decline, replayed key | 04 | 80 |
| 39 | Payment methods | `Payment`, `PaymentStatus`, `PaymentDetails` (sealed: Card/UPI/Wallet), `PaymentProcessor` + three processors, `PaymentProcessorRegistry` | Per-method validation; refund bookkeeping | 38 | 120 |
| 40 | Idempotency store | `IdempotencyStore` (key → request hash, status, response; IN_PROGRESS via `putIfAbsent`; 24 h TTL) | Replay; different body → 422; parallel same key | 05 | 80 |
| 41 | Pay a job | `PaymentService.pay` (idempotency → hold check → charge → commit capacity → confirm → activate or allocate), `POST /jobs/{id}/payments` | Success assigns; decline keeps payable; expired hold 409 | 26, 33, 39, 40 | 120 |
| 42 | Recurring charges | `RecurringChargeJob` (charge T−24 h with saved method, key `jobId:charge`; failure cancels that job only) | Only due jobs charged; failure isolation | 37, 41 | 80 |

## PRs 43–52: cancellation, recovery, lifecycle, polish

| PR | Title | What's in it | Tests | Depends on | ~Lines |
| --- | --- | --- | --- | --- | --- |
| 43 | Cancellation policy | `CancellationPolicy`, `RefundDecision`, `CancelledBy`, `TimeBasedCancellationPolicy` (thresholds from config), `NoRefundPolicy`, selection by property | Boundaries at 4 h and 1 h; platform-caused = 100%; EN_ROUTE refused | 30, 39 | 90 |
| 44 | Cancel a job | `CancellationService.cancelJob` (refund first, then release assignment + capacity, then `job.cancel`), `GET /cancellation-quote`, `POST /jobs/{id}/cancel` | Slot shows OPEN again; refund failure changes nothing | 41, 43 | 110 |
| 45 | Cancel a series | `cancelBooking` (all future cancellable jobs, subscription → CANCELLED), `POST /bookings/{id}/cancel` | Past jobs untouched; generation stops | 37, 44 | 70 |
| 46 | Allocation retries | `AllocationJob` (`@Scheduled` 5 min, earliest first), cutoff → UNFULFILLED with platform-caused refund | Retry succeeds later; cutoff refunds 100% | 41, 44 | 90 |
| 47 | Drop-outs | `MaidService.dropJob`, `POST /maids/{id}/jobs/{jobId}/drop`, leave listener releasing assignments, immediate re-allocation excluding that maid | Re-assigned to another maid; capacity untouched | 11, 46 | 90 |
| 48 | Job lifecycle | `JobLifecycleService` (`enRoute`, `start(otp)`, `complete` → maid IDLE), endpoints | Wrong OTP rejected; cancel blocked after EN_ROUTE | 13, 41 | 90 |
| 49 | Surge pricing | `SurgePricingDecorator` (uplift under 20% free capacity, instant premium), wired as primary `PricingStrategy` | Uplift applied; quote = charge | 16, 19 | 70 |
| 50 | Seed data | `SeedDataLoader` (3 zones, ~12 maids, templates, 7 days of shifts), profile `demo` | Ledger non-empty after startup | 10, 12 | 90 |
| 51 | OpenAPI and admin | springdoc config, `POST /admin/run/{job}` triggers for allocation, subscriptions, recurring charges, hold sweeper | Swagger loads; triggers run | 17, 37, 42, 46 | 70 |
| 52 | Demo and README | `requests.http` end-to-end script, README (run, design, assumptions, more time), DECISIONS.md | Script runs against seed data | all | docs only |
