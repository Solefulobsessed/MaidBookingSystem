# Maid Booking System

Backend for a home-services (maid) booking platform, modelled on how Urban Company and Snabbit operate: customers book **time slots in a zone**, the platform reserves zone capacity, takes payment, and **assigns** a maid.

- **Spec:** [docs/SPEC.md](docs/SPEC.md) — domain model, capacity and allocation design, flows, API, edge cases.
- **Build plan:** [docs/PR_PLAN.md](docs/PR_PLAN.md) — 52 small PRs (~100 lines of production code each) in dependency waves. All 52 are implemented.
- **Decisions log:** [DECISIONS.md](DECISIONS.md) — every choice made during implementation that the spec doesn't dictate, with the reason.

## Run it

Requires JDK 17+ and Maven 3.9+.

```bash
mvn test                 # ~540 tests: unit, controller slices, race tests, full-app tests
mvn spring-boot:run      # http://localhost:8080, with demo data
```

- **Swagger UI:** <http://localhost:8080/swagger-ui.html> (spec at `/v3/api-docs`)
- **Health:** `GET /actuator/health`
- **Demo data** (profile `demo`, on by default; start with `--spring.profiles.active=prod` for an empty app):

| Zone | Next to | Maids (id, tier, rating, Mon–Sat hours) |
| --- | --- | --- |
| Koramangala `kor-5` (localities: koramangala, st johns road…) | `hsr-2`, `ind-1` | `m-lakshmi` premium 4.8 07–15 · `m-asha` 4.4 07–13 · `m-ravi` 4.1 12–20 · `m-meena` premium 4.9 09–17 |
| HSR Layout `hsr-2` (hsr layout, agara…) | `kor-5` | `m-sunita` 4.3 07–15 · `m-kavya` premium 4.7 08–16 · `m-imran` 3.9 12–20 · `m-geetha` unrated 07–13 |
| Indiranagar `ind-1` (indiranagar, domlur…) | `kor-5` | `m-priya` premium 4.6 07–15 · `m-rekha` 4.2 08–16 · `m-anil` 4.0 12–20 · `m-fatima` premium 4.5 09–17 |

Shifts are generated for the next 14 days, so recurring bookings have capacity for their first window.

## Demo

[`requests.http`](requests.http) walks through the whole product in order (IntelliJ HTTP client):

1. **Discover:** slot grid with OPEN / FEW_LEFT / FULL and prices; preferred-maid search with filters.
2. **Book Lakshmi, pay, cancel:** preferred maid held at booking → UPI payment assigns her → a repeated request is replayed, not re-charged → refund quote → cancel with a full refund, slot open again.
3. **Decline, pay, drop-out:** a declined UPI (402, job still payable) → card payment assigns a maid → she drops the job and it goes straight to another maid.
4. **A weekly series:** 14 days reserved all-or-nothing with a saved UPI method → run the background jobs on demand (`POST /admin/run/{subscriptions|recurring-charge|allocation|hold-sweeper}`) → cancel the series.
5. **Live (shift hours only):** check a maid in → instant availability → instant booking → pay by wallet → on the way → start with the OTP → complete.

Steps 1–4 run as an automated test against the seeded app (`DemoScriptTest`), so the script is known to work.

## Design

```
 SUPPLY                         CAPACITY                              ALLOCATION
 maids, weekly templates  ──►   ledger: zone × service × 30-min  ──►  per-maid calendar of assignments
 shifts, leave, live state      bucket (minutes, × 0.85 sellable)     (travel buffers before and after)
        │  ShiftChanged event          ▲ reserve / commit / release          ▲ best fit + scoring, under
        └──────────────────────────────┘   under the zone lock                 the maid's lock
                                       │                                     │
 CUSTOMER  slots ─► book (reserve every job, all or nothing) ─► pay ─► assign maid (or hold one at booking)
```

- **Customers book slots, the platform assigns maids.** Availability is answered from the capacity ledger, which is fast arithmetic, not a search over every maid's calendar. This is the Urban Company model; it also keeps a slot sellable when one maid drops out, because another maid can take it.
- **Two levels of double-booking protection.** The zone ledger never sells more minutes than the shifts provide (checked under the zone lock), and each maid's calendar never holds two overlapping jobs (re-checked under her lock when she is assigned). Race tests book one slot and one maid from many threads at once; with the locks replaced by no-ops they fail.
- **Lock order is zone → maid → job**, checked at runtime by `InMemoryLockRegistry` (a wrong order throws). Events are published after locks are released. Paying, cancelling and the visit steps use `JobGuard`, a non-blocking per-job guard, so they never wait while holding locks.
- **Three booking types, one template.** `BookingService.create` runs handler → validate → plan → price → reserve every job → hold a maid if needed → save, for every type:
  - **Instant:** the nearest checked-in, idle maid within 15 minutes is held in the same request.
  - **Scheduled:** 2 hours to 30 days ahead; the maid is chosen after payment.
  - **Recurring:** a subscription plus its first 14 days of visits; one visit is added every night, each charged 24 h ahead with the saved method.
- **Preferred-maid mode** fits on top: the customer searches maids who are actually free, picks one, and only she is held; for a series she is a soft preference and visits fall back to the best other maid.
- **Payments are per job and idempotent.** `Idempotency-Key` → stored result; the key is forwarded to the gateway; recurring charges use `jobId:charge`. If a hold lapses while the gateway is charging, the money is refunded straight away.
- **Failure paths keep the customer whole:** unpaid holds expire after 10 minutes; a maid drop-out or leave re-allocates at once; a job still without a maid 60 minutes before the start becomes UNFULFILLED with a 100% refund. Cancelling refunds first and changes nothing if the refund fails.
- **Pricing:** rate card × tier, +20% when a slot is under 20% free, +₹49 for instant; one primary strategy prices quotes and charges, so the quote is what is charged.

## Extension points

Every variation point is an interface plus a registry built from `List<Interface>`, so there is no `switch` on type:

| To add | Write | Registered by |
| --- | --- | --- |
| A search / booking filter | a `MaidFilterFactory` bean | `FilterRegistry` |
| A booking type | a `BookingType` value + a `BookingTypeHandler` bean | `BookingTypeRegistry` |
| A payment method | a `PaymentMethodType` value + a `PaymentProcessor` bean | `PaymentProcessorRegistry` |
| A scoring rule | a `ScoringRule` bean + a weight in `app.allocation.weights` | `MaidScorer` |
| A cancellation policy / pricing / dispatch | a `CancellationPolicy`, `PricingStrategy` decorator or `DispatchStrategy` bean | property or `@Primary` |

**Example: a `language` filter** (`?language=kannada` in maid search, and stored on the booking as a hard requirement), added next to the others in `StandardFilters`:

```java
@Bean
public MaidFilterFactory languageFilter() {
    return factory("language", raw -> maid -> maid.tags().contains("speaks-" + raw.toLowerCase(Locale.ROOT)));
}
```

**Example: a "deep clean" booking type** (always 4 hours, weekends, 2 days' notice):

```java
@Component
public class DeepCleanBookingHandler implements BookingTypeHandler {
    public BookingType type() { return BookingType.DEEP_CLEAN; }
    public void validate(CreateBookingRequest r, LocalDateTime now) { /* weekend, >= 2 days ahead */ }
    public BookingPlan plan(CreateBookingRequest r, LocalDateTime now) {
        return BookingPlan.single(TimeSlot.of(r.start(), Duration.ofHours(4)));
    }
    public AllocationMode allocationMode() { return AllocationMode.ALLOCATE_AFTER_PAYMENT; }
}
```

Nothing else changes: the booking template, payment, cancellation and allocation work as they are.

## Assumptions

- One city in IST. Zones are predefined; a locality maps to exactly one zone; each zone has 1–2 neighbours.
- Maids are contracted supply (Snabbit's model): they choose shifts, the platform assigns jobs, there is no accept/reject step.
- Travel time is a fixed estimate: 15 minutes within a zone, 30 minutes to a neighbour. No maps API.
- Prices come from the platform's rate card × tier, not from individual maids.
- One address per booking; the customer is an opaque `customerId`; no authentication (admin endpoints included).
- Ratings are seed data; there is no review flow. Customer notifications (e.g. a skipped visit) are log lines.
- The utilisation factor (0.85), hold (10 min), allocation cutoff (T−60 min), refund windows, surge and charge timing are configuration properties.

## Known limitations

- Capacity is reserved in the job's zone; when a neighbour-zone maid takes the job, her home zone's ledger isn't charged for it.
- Allocation doesn't match the maid's tier to the booking's tier (a premium booking can be re-allocated to a standard maid after a drop-out).
- State is in memory: a restart loses bookings. Repositories are interfaces, so a database can be swapped in.

## With more time

- Batch allocation that optimises a whole day per zone instead of one job at a time (Urban Company describes bin packing with Branch and Bound).
- Flexible slots: the customer picks a window such as 1–4 pm and the platform picks the exact start.
- Demand forecasting to suggest shifts and to position idle maids, as Snabbit does in its micro-markets.
- Postgres: a conditional `UPDATE` on capacity rows, an exclusion constraint on maid assignments, an outbox for events.
- Real travel times, live GPS, an offer/accept dispatch for gig supply, maid earnings and incentives, ratings, notifications.

## Tech stack

Java 17, Spring Boot 3.5, Maven, springdoc-openapi, in-memory repositories behind interfaces, JUnit 5 + AssertJ + Mockito. CI runs `mvn -B verify` on every push.
