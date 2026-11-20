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
- [ ] PR-11 Leave

The README is completed in PR-52 with design notes, assumptions and a demo script.
