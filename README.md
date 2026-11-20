# Maid Booking System

Backend for a home-services (maid) booking platform, modelled on how Urban Company and Snabbit operate: customers book **time slots in a zone**, the platform reserves zone capacity, takes payment, and **assigns** a maid.

- **Spec:** [docs/SPEC.md](docs/SPEC.md) — domain model, capacity and allocation design, flows, API, edge cases.
- **Build plan:** [docs/PR_PLAN.md](docs/PR_PLAN.md) — 52 small PRs (~100 lines of production code each) in dependency waves.
- **Decisions log:** [DECISIONS.md](DECISIONS.md) — choices made during implementation that the spec doesn't dictate.

## Tech stack

Java 17, Spring Boot 3, Maven, in-memory repositories behind interfaces, JUnit 5 + AssertJ + Mockito.

## Build and run

```bash
mvn test                 # run the test suite
mvn spring-boot:run      # start on http://localhost:8080
curl localhost:8080/actuator/health
```

Requires JDK 17 or newer and Maven 3.9+.

## Status

Implementation follows [docs/PR_PLAN.md](docs/PR_PLAN.md). Merged so far:

- [x] PR-01 Project skeleton
- [x] PR-02 Error model
- [x] PR-03 TimeSlot
- [x] PR-05 Locks and repository base
- [x] PR-04 Money and RecurrenceRule
- [x] PR-06 Zones
- [x] PR-07 Maid entity
- [x] PR-40 Idempotency store
- [x] PR-08 Maid onboarding API
- [x] PR-09 Shift entity
- [x] PR-10 Shift templates
- [x] PR-11 Leave
- [x] PR-12 Supply endpoints
- [x] PR-13 Live state
- [x] PR-14 Capacity buckets
- [x] PR-15 Capacity ledger
- [x] PR-16 Reserve / commit / release
- [x] PR-17 Holds
- [x] PR-18 Capacity race test
- [x] PR-19 Rate-card pricing
- [x] PR-20 Slot grid
- [x] PR-21 Maid calendar
- [x] PR-22 Filters
- [x] PR-23 Scoring core
- [x] PR-24 More scoring rules
- [x] PR-25 Candidate finder
- [x] PR-26 Allocation service
- [x] PR-27 Allocation race test
- [x] PR-28 Preferred-maid search
- [x] PR-29 Instant dispatch
- [x] PR-30 Job state machine
- [x] PR-31 Booking aggregate
- [x] PR-32 Booking type registry
- [x] PR-33 Create booking
- [x] PR-34 Instant booking
- [x] PR-35 Preferred maid
- [x] PR-36 Recurring booking
- [x] PR-37 Subscription generation
- [ ] PR-38 Payment gateway

The README is completed in PR-52 with design notes, assumptions and a demo script.
