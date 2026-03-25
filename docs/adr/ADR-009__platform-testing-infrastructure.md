# ADR-009 — Platform Testing Infrastructure (Testcontainers, Fixtures, Multi-Tenancy)

**Status:** Proposed  
**Date:** 2026-03-25  
**Focus:** Test ergonomics, repeatable integration tests, alignment with Soft Multi-Tenancy and Outbox  

**Related:** ADR-002 (Soft Multi-Tenancy), ADR-005 (Lightweight Hexagonal), ADR-007 (Transactional Outbox), `.cursor/rules/40-testing.mdc`

---

## Context

Integration tests in the starter currently duplicate **Testcontainers** setup (PostgreSQL image variants, credentials, `@DynamicPropertySource`) and **tenant bootstrap** patterns (`Tenant.create`, persistence, `TenantContextHolder`) across multiple test classes in `sample-service`.

This duplication:

- Increases maintenance cost when Docker images or datasource properties change.
- Makes it harder for new services (forked from the starter) to adopt the same standards without copy-paste.
- Risks subtle drift (e.g. one test uses `postgres:15-alpine`, another `postgres:15`).

The testing policy requires integration tests for persistence, multi-tenancy, and outbox-related behavior. **ADR-007** already states that the outbox chain should be verified with **Testcontainers (PostgreSQL + Kafka)**.

We need a **single, opinionated place** for shared test infrastructure that does **not** violate hexagonal boundaries: no domain logic from sample services in platform modules; only reusable test setup and helpers.

---

## Why this refactor (value added)

Refactoring integration tests into shared `platform-testing` infrastructure is not an end in itself. The expected benefits:

1. **Single source of truth for test runtime** — One canonical Postgres image, one Kafka broker image, one mapping to Spring properties. Changing versions or broker settings happens in one module, not in every test class.
2. **Faster forks and new services** — A new microservice adds one Gradle dependency and inherits the same stack; authors focus on business assertions, not on Docker wiring.
3. **Less drift and fewer “works on my machine” issues** — Eliminates inconsistent image tags and credentials across tests.
4. **Alignment with ADR-007** — Outbox + Kafka is part of the **baseline** test stack, so full integration tests do not require a one-off Kafka setup in a single class.
5. **Shorter feedback loops in CI** — **One shared container instance** per JVM test run (see Decision §3) reduces startup overhead compared to starting separate Postgres (and Kafka) per test class.
6. **Clearer ownership** — Test infrastructure is a **platform concern**; domain-specific fixtures and assertions remain in each service.

---

## Decision

### 1. New module: `platform-testing`

- Add a **platform module** `platform-testing`, consumed only as **`testImplementation`** / **`testFixtures`** by services.
- **Dependency direction:** `sample-service` (and future services) → `platform-testing`. Platform modules **must not** depend on `sample-service`.
- **Content:** Testcontainers wiring, shared Spring Boot test configuration hooks, **tenant fixtures**, **tenant isolation** helpers, and generic **outbox** helpers where applicable. **No** domain-specific DTOs, entities, or use cases from sample services.

### 2. Gradle layout: `java-test-fixtures`

- Use the **`java-test-fixtures`** plugin so that reusable classes (abstract base tests, fixtures) live in a **`testFixtures`** source set.
- Rationale: Keeps Testcontainers and Spring Test APIs **out of production `main`** and avoids leaking test dependencies into runtime classpaths.

### 3. Shared containers: PostgreSQL + Kafka, single instance per JVM

- **PostgreSQL** and **Kafka** are both **included from the start** in the shared test stack (baseline for integration tests).
- **Lifecycle:** **One instance** of each container (singleton) for the **entire test JVM process** (Gradle `test` task), shared across test classes. Implementation detail: static container fields started once, with `spring.datasource.*` and `spring.kafka.bootstrap-servers` registered via `@DynamicPropertySource` on a small number of abstract base classes (or a single entry point), so that all inheriting tests reuse the same brokers.
- **Dynamic property registration:** Map container endpoints to `spring.datasource.*` and `spring.kafka.bootstrap-servers` (and any other required Kafka client properties for tests).

### 3a. Implications of including Kafka in the baseline

| Area | Implication |
|------|-------------|
| **Docker** | CI and local dev must have Docker available for integration tests (already required for Postgres). Kafka adds a second container image pull and runtime. |
| **Resources** | Higher memory and CPU footprint during `./gradlew test` (Kafka broker + Zookeeper-less or KRaft image per chosen image). |
| **Duration** | First cold start of the Kafka image is slower; **singleton** reuse amortizes this across many test classes. |
| **Stability** | Flakiness risk if tests share topics without isolation (see mitigation below). |
| **Mitigation (tests)** | Use **unique topic names** per test or per class (prefix + UUID), or clear consumer groups / seek strategies as today in `OutboxIntegrationTest`, so parallel or sequential tests do not read each other’s messages. |
| **Gradle** | `platform-testing` test-fixtures depend on `testcontainers-kafka` (and Postgres) explicitly; consumers inherit them transitively. |

### 3b. Canonical Docker images

- **PostgreSQL:** Single canonical tag for all integration tests (e.g. `postgres:15`); migrate away from ad-hoc variants (`postgres:15-alpine`, different DB names) in favor of one definition in `platform-testing`.
- **Kafka (broker):** **`confluentinc/cp-kafka`** — Confluent Platform packaging of Apache Kafka, aligned with common production setups and with the existing outbox integration test pattern.
  - **Baseline tag:** `7.5.0` (must match the shared `DockerImageName` used in `AbstractIntegrationTest` / test-fixtures). Version bumps are done **only** in `platform-testing`, not per test class.
  - **Testcontainers usage:** `new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))` (or equivalent factory in one place).
- **Rationale:** Avoids Redpanda/Kafka-API-compatible brokers for the **default** stack so integration tests exercise a **real Kafka broker** behavior consistent with ADR-007; trade-off is heavier CI cost (accepted in §3a).

### 4. Tenant fixtures (no “context on demand” in scope)

- Provide helpers to **persist** a `Tenant` (from `platform-data`) and return **`UUID tenantId`**, matching patterns already used in integration tests.
- **Out of scope for `platform-testing` v1:** Helpers that wrap `TenantContextHolder` / `CorrelationContextHolder` in a “run with context” API.
- **Rationale:** Existing tests already set and clear context **explicitly** in `@BeforeEach` / `@AfterEach`. That remains **visible and grep-friendly** in each test class. A runner API would save a few lines but adds indirection and makes it harder to see when context leaks (especially with correlation). If repetition becomes painful later, a thin optional helper can be added **without** changing this ADR’s core decision.

### 5. Tenant isolation helpers

- Provide reusable **assertion or helper** patterns (inspired by `TenantIsolationIntegrationTest`) so services can verify persistence-layer **tenant isolation** without duplicating Hibernate filter/session details.

### 6. Outbox testing helpers

- Generic helpers or assertions for **outbox table** state (e.g. `PENDING` rows, headers in JSONB) **where** they do not depend on a specific message type from a single service. Service-specific message types and assertions stay in the service’s tests.

### 7. Refactor of existing tests: equivalence guarantee

- All integration tests in `sample-service` that are migrated to `platform-testing` **must remain behaviorally equivalent** to the pre-refactor state.
- **Definition of done for migration:** For each migrated class, the **same scenarios** are executed with the **same assertions** (HTTP status, DB state, outbox rows, Kafka headers, tenant boundaries). The only intended differences are: shared container wiring, shared tenant fixture calls, and removal of duplicated `@Container` / `@DynamicPropertySource` blocks.
- **No reduction in coverage** as part of this refactor; additions (e.g. stricter shared helpers) are allowed only **on top of** preserved behavior.

### 8. Documentation and governance

- Add a pointer in **`architecture.md`** (Documentation Index / Testing) to this ADR once the module is in place.
- **ArchUnit** (optional follow-up): Rule discouraging ad-hoc `@Container` PostgreSQL/Kafka in service tests in favor of the shared base—only if it does not block legitimate exceptions.

---

## Module and package layout (ASCII)

```
platform-testing/
├── build.gradle.kts          # java-library + java-test-fixtures; testcontainers postgresql + kafka
├── src/
│   main/
│   │   └── java/             # empty or minimal; production code not required for this ADR
│   └── testFixtures/
│       └── java/
│           └── com/pidabrow/starter/testing/
│               ├── AbstractIntegrationTest.java       # @DynamicPropertySource: Postgres + Kafka (singleton)
│               ├── tenant/
│               │   └── TenantTestFixtures.java        # persist Tenant -> UUID tenantId
│               ├── assertions/
│               │   └── TenantIsolationAssertions.java # optional helpers for tenant isolation checks
│               └── outbox/
│                   └── OutboxTestAssertions.java        # generic outbox row/header checks (no domain message types)
```

**Consumer (example):**

```
sample-service/
├── src/test/java/.../integration/
│   └── *.java                 # extends AbstractIntegrationTest; uses TenantTestFixtures; no local @Container
```

---

## Consequences

**Positive**

- One place to update Postgres/Kafka images and Spring properties.
- **Singleton** containers reduce startup cost versus per-class containers.
- Baseline **Kafka** matches ADR-007 outbox verification without bespoke setup per class.
- Explicit **equivalence** rule keeps the refactor safe and reviewable.

**Negative**

- **Extra module** to maintain.
- **Gradle:** `test-fixtures` must be wired so runtime classpaths stay clean.
- **CI:** Slightly heavier integration test runs (Kafka always on).
- Helpers must stay **generic**; domain-specific test data stays in services.

---

## Alternatives Considered

1. **Copy-paste only in each service** — Rejected: duplication already exists and will grow.
2. **Shared test code in `platform-infrastructure` `test` source** — Rejected: not consumable across modules.
3. **Kafka optional per test class** — Rejected for this ADR: baseline includes Kafka for **consistency** with ADR-007; tests that do not need broker interaction still run against the same stack (no second code path for “DB-only” images).
4. **Per-class `@Container` instances** — Rejected in favor of **one instance per JVM** for performance and consistency.
5. **Redpanda or other Kafka-protocol-compatible images** — Rejected for the **default** baseline: we standardize on **`confluentinc/cp-kafka`** for parity with typical Kafka deployments; faster-but-different brokers remain a possible future profile, not the starter default.

---

## Notes

- **Migration:** Refactor `sample-service` integration tests incrementally; after each step, **same assertions** as before (see §7).
- **Status:** Set to **Accepted** once `platform-testing` exists, at least one reference test uses it, and **migration equivalence** has been verified for migrated classes.
