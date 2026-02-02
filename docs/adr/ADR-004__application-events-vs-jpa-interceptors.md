# ADR-004 — Application Events vs JPA Interceptors

**Status:** Accepted  
**Date:** 2026-01-31

## Context

Cross-cutting concerns such as auditing and metrics need to react to business actions.
JPA lifecycle callbacks provide hooks but operate at the persistence level.

## Decision

- Publish events explicitly from the application layer.
- Do not rely on JPA entity listeners or persistence callbacks for business events.
- Event names must reflect business intent, not technical actions.

## Consequences

- Business intent is explicit and observable.
- Cross-cutting concerns do not leak into domain or persistence layers.
- Requires discipline to publish events consistently.

## Alternatives Considered

- JPA lifecycle callbacks.
- Generic persistence events.

These were rejected due to implicit behavior and limited expressiveness.
