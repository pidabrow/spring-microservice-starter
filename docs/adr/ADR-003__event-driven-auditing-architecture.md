# ADR-003 — Event-Driven Auditing Architecture

**Status:** Accepted  
**Date:** 2026-01-31

## Context

Auditing is required for traceability and compliance but must not pollute domain logic.
JPA interceptors and automatic auditing frameworks were evaluated.

## Decision

- Use application-level domain events.
- Audit logging is append-only.
- Audit entries are written only after transaction commit.
- Auditing is implemented via event listeners, not persistence hooks.
- Sensitive fields must be maskable.

## Consequences

- Clear separation between business logic and auditing.
- Auditing logic is extensible and testable.
- Slight increase in conceptual complexity due to event flow.

## Alternatives Considered

- Hibernate Envers.
- JPA entity listeners.

Both were rejected due to implicit behavior and tight coupling to persistence.
