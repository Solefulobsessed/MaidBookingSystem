# Decisions log

Choices made during implementation that `docs/SPEC.md` does not dictate. Newest first.

| PR | Decision | Why |
| --- | --- | --- |
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
