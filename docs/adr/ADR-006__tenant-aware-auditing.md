# ADR-006 — Domain Events & Tenant-Aware Auditing (JSON Patch, AFTER_COMMIT)

**Status:** Accepted  
**Date:** 2026-02-26

---

## Context

The system requires a reliable, explicit, and decoupled auditing mechanism aligned with:

- ADR-002 — Soft Multi-Tenancy
- ADR-003 — Event-Driven Auditing Architecture
- ADR-004 — Application Events vs JPA Interceptors
- ADR-005 — Lightweight Hexagonal Architecture

Auditing must:

- Respect tenant boundaries
- Capture actor identity (SYSTEM vs USER)
- Capture the delta of changes
- Execute only after successful transaction commit
- Remain fully decoupled from domain logic

---

## Decision

We adopt a **Domain Events + AFTER_COMMIT auditing model**.

---

## 1. Domain Events Model

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

---

## 2. Event Publishing

- `DomainEventPublisher` defined as an outbound port in `platform-common`.
- Spring-based adapter implemented in `platform-web`.
- Events are handled by listeners using:

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
```

### Rationale

Audit entries must only be written if the business transaction commits successfully.  
No "ghost" audit entries are allowed.

---

## 3. AuditLog Model (Append-Only)

Create `AuditLog` entity in `platform-data`.

### Fields

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

### Rules

- `AuditLog` is append-only.
- No public setters.
- Provide a static factory method for creation.
- Protected no-args constructor for JPA.
- No `updated_at` required (append-only model).

---

## 4. Actor Context

Introduce identity boundary:

- `ActorContext` (sealed interface)
    - `SystemActor`
    - `UserActor(UUID userId)`

- `ActorContextHolder` (ThreadLocal)

### MVP Actor Resolution

- `X-Actor-Type: SYSTEM | USER` (missing = SYSTEM)
- `X-Actor-Id: <uuid>` required for USER

### Important

- Header-based resolution is MVP only.
- JWT-based authentication will replace it in a future iteration.
- Headers must never be trusted in production.

---

## 5. Architectural Constraints

- Domain events must not depend on Spring.
- Domain events must not depend on JPA.
- `AuditLog` must not expose public setters.
- Persistence and listeners reside in outbound adapters.
- Application layer publishes events explicitly.

---

## Consequences

### Positive

- Clear separation of concerns.
- Explicit business intent.
- Tenant-aware audit trail.
- Safe execution after commit.
- Meaningful use of Java 21 features (sealed types, records, pattern matching).

### Trade-offs

- Slight increase in structural complexity.
- In-process event handling may lose audit entries in crash scenarios.

### Future Consideration — Transactional Outbox

To guarantee at-least-once delivery in case of crashes:

- Persist `DomainEvent`s to an outbox table within the business transaction.
- Process events asynchronously from the outbox.

**Not implemented in this ADR.**

---

## Alternatives Considered

- JPA Entity Listeners — rejected (implicit behavior).
- Hibernate Envers — rejected (tight coupling to persistence model).
- Immediate synchronous audit writes — rejected (violates transactional integrity).  