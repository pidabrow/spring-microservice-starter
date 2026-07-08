# ADR-006 — Domain Events & Tenant-Aware Auditing (JSON Patch, AFTER_COMMIT)

**Status:** Accepted
**Date:** 2026-02-26
**Decision-makers:** <!-- TODO: fill in — not stated in original ADR -->
**Consulted:** <!-- TODO: fill in — not stated in original ADR -->
**Informed:** <!-- TODO: fill in — not stated in original ADR -->
**Related:** ADR-002 (Soft Multi-Tenancy), ADR-003 (Event-Driven Auditing Architecture), ADR-004 (Application Events vs JPA Interceptors), ADR-005 (Lightweight Hexagonal Architecture)

## Context and Problem Statement

The system requires a reliable, explicit, and decoupled auditing mechanism aligned with ADR-002, ADR-003, ADR-004, and ADR-005.

## Decision Drivers

- Respect tenant boundaries.
- Capture actor identity (SYSTEM vs USER).
- Capture the delta of changes.
- Execute only after successful transaction commit.
- Remain fully decoupled from domain logic.

## Considered Options

- JPA Entity Listeners.
- Hibernate Envers.
- Immediate synchronous audit writes.
- Domain Events + AFTER_COMMIT auditing model.

## Decision Outcome

Chosen option: "Domain Events + AFTER_COMMIT auditing model".

### 1. Domain Events Model

- Introduce `DomainEvent` as a sealed interface in `platform-common`.
- Domain events must be immutable Java Records.
- Domain events must not depend on Spring or JPA.

Each event must expose:

- `UUID entityId()`
- `UUID tenantId()`
- `String entityType()` (explicit, mandatory)
- `Instant occurredAt()`

Example events:

- `EntityCreatedEvent`
- `EntityUpdatedEvent`

For update events:

- `delta` must be stored as a **JSON Patch (RFC 6902)** string.

Events must never expose JPA entities.

### 2. Event Publishing

- `DomainEventPublisher` defined as an outbound port in `platform-common`.
- Spring-based adapter implemented in `platform-web`.
- Events are handled by listeners using:

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
```

Rationale: Audit entries must only be written if the business transaction commits successfully. No "ghost" audit entries are allowed.

### 3. AuditLog Model (Append-Only)

Create `AuditLog` entity in `platform-data`.

Fields:

- `id` (UUID, generated in application)
- `tenantId` (mandatory)
- `entityType`
- `entityId`
- `action`
- `eventClassName`
- `actorType` (SYSTEM / USER)
- `actorId` (nullable UUID)
- `changes` (TEXT or JSONB, storing JSON Patch)
- `created_at` (DB default `CURRENT_TIMESTAMP`)

Rules:

- `AuditLog` is append-only.
- No public setters.
- Provide a static factory method for creation.
- Protected no-args constructor for JPA.
- No `updated_at` required (append-only model).

### 4. Actor Context

Introduce identity boundary:

- `ActorContext` (sealed interface)
    - `SystemActor`
    - `UserActor(UUID userId)`
- `ActorContextHolder` (ThreadLocal)

MVP Actor Resolution:

- `X-Actor-Type: SYSTEM | USER` (missing = SYSTEM)
- `X-Actor-Id: <uuid>` required for USER

### 5. Architectural Constraints

- Domain events must not depend on Spring.
- Domain events must not depend on JPA.
- `AuditLog` must not expose public setters.
- Persistence and listeners reside in outbound adapters.
- Application layer publishes events explicitly.

### Consequences

**Positive**

- Clear separation of concerns.
- Explicit business intent.
- Tenant-aware audit trail.
- Safe execution after commit.
- Meaningful use of Java 21 features (sealed types, records, pattern matching).

**Negative**

- Slight increase in structural complexity.
- In-process event handling may lose audit entries in crash scenarios.

### Confirmation

<!-- TODO: fill in — original ADR does not describe a confirmation/verification mechanism -->

## Pros and Cons of the Options

### JPA Entity Listeners

- Bad, because of implicit behavior (rejected).

### Hibernate Envers

- Bad, because of tight coupling to persistence model (rejected).

### Immediate synchronous audit writes

- Bad, because it violates transactional integrity (rejected).

### Domain Events + AFTER_COMMIT auditing model (chosen)

- Good, because of clear separation of concerns.
- Good, because of explicit business intent.
- Good, because of tenant-aware audit trail.
- Good, because of safe execution after commit.
- Good, because of meaningful use of Java 21 features (sealed types, records, pattern matching).
- Bad, because of a slight increase in structural complexity.
- Bad, because in-process event handling may lose audit entries in crash scenarios.

## Notes for AI

- Header-based actor resolution (`X-Actor-Type` / `X-Actor-Id`) is MVP only; JWT-based authentication will replace it in a future iteration. Headers must never be trusted in production.

## More Information

### Future Consideration — Transactional Outbox

To guarantee at-least-once delivery in case of crashes:

- Persist `DomainEvent`s to an outbox table within the business transaction.
- Process events asynchronously from the outbox.

**Not implemented in this ADR.**
