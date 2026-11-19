# Decisions log

Choices made during implementation that `docs/SPEC.md` does not dictate. Newest first.

| PR | Decision | Why |
| --- | --- | --- |
| 01 | Spring Boot 3.5.0, compiled with `maven.compiler.release=17` | Meets the spec's Java 17+ requirement and still builds on newer JDKs |
| 01 | Plain `mvn` instead of the Maven wrapper | The wrapper couldn't be generated offline; GitHub runners ship Maven. Add the wrapper in a follow-up if wanted |
| 01 | `app.time-zone` property (default Asia/Kolkata) feeds the single `Clock` bean | All time logic uses one injectable clock, as the spec requires |
