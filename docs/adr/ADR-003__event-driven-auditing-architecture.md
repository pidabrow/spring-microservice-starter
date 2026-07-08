# ADR-003 — Event-Driven Auditing Architecture

**Status:** Accepted
**Date:** 2026-01-31
**Decision-makers:** <!-- TODO: fill in — not stated in original ADR -->
**Consulted:** <!-- TODO: fill in — not stated in original ADR -->
**Informed:** <!-- TODO: fill in — not stated in original ADR -->
**Related:** <!-- TODO: fill in — not stated in original ADR -->

## Context and Problem Statement

Auditing is required for traceability and compliance but must not pollute domain logic.
JPA interceptors and automatic auditing frameworks were evaluated.

## Decision Drivers

- Traceability and compliance requirements.
- Auditing must not pollute domain logic.

## Considered Options

- Hibernate Envers.
- JPA entity listeners.
- Application-level domain events.

## Decision Outcome

Chosen option: "Application-level domain events", because the alternatives (Hibernate Envers, JPA entity listeners) were rejected due to implicit behavior and tight coupling to persistence.

- Use application-level domain events.
- Auditing is implemented via event listeners, not persistence hooks.
- Audit logging is append-only.

### Consequences

**Positive**

- Clear separation between business logic and auditing.
- Auditing logic is extensible and testable.

**Negative**

- Slight increase in conceptual complexity due to event flow.

### Confirmation

<!-- TODO: fill in — original ADR does not describe a confirmation/verification mechanism -->

## Pros and Cons of the Options

### Hibernate Envers

- Bad, because it relies on implicit behavior and tight coupling to persistence (rejected).

### JPA entity listeners

- Bad, because it relies on implicit behavior and tight coupling to persistence (rejected).

### Application-level domain events (chosen)

- Good, because it gives a clear separation between business logic and auditing.
- Good, because auditing logic is extensible and testable.
- Bad, because it introduces a slight increase in conceptual complexity due to event flow.

## Notes for AI

- Audit entries are written only after transaction commit.
- Sensitive fields must be maskable.

## More Information

<!-- TODO: fill in — original ADR does not reference further material -->
