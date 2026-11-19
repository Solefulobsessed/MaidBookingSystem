# Instructions for AI coding agents

This repo is built from a written spec, one small PR at a time.

## Source of truth

- `docs/SPEC.md` — the design. Do not deviate from its model: customers book slots in a zone; availability = shifts → capacity ledger (zone × service × 30-min bucket) → per-maid calendar of assignments; the platform assigns maids.
- `docs/PR_PLAN.md` — the order of work. Implement exactly one PR from it at a time, in wave order, unless told otherwise.

## Rules

- Java 17, Spring Boot 3, Maven. Use the spec's package names and interface names.
- About 100 lines of production code per PR (excluding imports and blank lines); split if over 150. Tests are extra and required.
- Program to interfaces. Use registries built from injected `List<Interface>`; no `switch` or `if` chains on booking type, payment method or filter key.
- Entities own their state transitions and throw `InvalidStateTransitionException` on illegal moves. Services never assign status fields directly.
- Inject `java.time.Clock`; never call `LocalDateTime.now()` or `Instant.now()` without it.
- `BigDecimal` for money. Java records for value objects and DTOs.
- Lock order is always zone → maid → job.
- No commented-out code, no TODO stubs left in merged code.

## Workflow per PR

1. Branch `pr-NN-short-name` from `main`.
2. Implement the PR's scope from `docs/PR_PLAN.md` with its tests.
3. Run `mvn test`; fix failures.
4. Record any choice the spec doesn't dictate in `DECISIONS.md`.
5. Open the PR titled `PR-NN: <title>` using the PR template, then stop for review.
