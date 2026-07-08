# ADR-005 — Lightweight Hexagonal Architecture (Ports & Adapters)

**Status:** Accepted
**Date:** 2026-01-31
**Decision-makers:** <!-- TODO: fill in — not stated in original ADR -->
**Consulted:** <!-- TODO: fill in — not stated in original ADR -->
**Informed:** <!-- TODO: fill in — not stated in original ADR -->
**Related:** <!-- TODO: fill in — not stated in original ADR -->

## Context and Problem Statement

As the starter grows, clear boundaries are required to prevent infrastructure leakage
and to keep business/application logic testable and adaptable.

A full-blown hexagonal architecture was considered but deemed too heavy.

## Decision Drivers

- Prevent infrastructure leakage.
- Keep business/application logic testable and adaptable.
- Avoid the overhead of a full-blown hexagonal architecture.

## Considered Options

- Traditional layered architecture.
- Framework-centric design.
- Full-blown hexagonal architecture.
- Lightweight hexagonal architecture.

## Decision Outcome

Chosen option: "Lightweight hexagonal architecture", because the full-blown hexagonal architecture was deemed too heavy, and the traditional layered / framework-centric alternatives were rejected due to weaker boundary enforcement.

- Adopt a lightweight hexagonal architecture.
- Use inbound adapters (REST) calling inbound ports (use cases).
- Define outbound ports as interfaces where they clarify boundaries.
- Implement outbound adapters for persistence, auditing, and integrations.

### Consequences

**Positive**

- Clear dependency direction (adapters depend inward).
- Improved testability and maintainability.

**Negative**

- Slight increase in structural complexity.

### Confirmation

<!-- TODO: fill in — original ADR does not describe a confirmation/verification mechanism -->

## Pros and Cons of the Options

### Traditional layered architecture

- Bad, because of weaker boundary enforcement (rejected).

### Framework-centric design

- Bad, because of weaker boundary enforcement (rejected).

### Full-blown hexagonal architecture

- Bad, because it was deemed too heavy (rejected).

### Lightweight hexagonal architecture (chosen)

- Good, because of clear dependency direction (adapters depend inward).
- Good, because of improved testability and maintainability.
- Bad, because of a slight increase in structural complexity.

## Notes for AI

- Avoid unnecessary abstraction and "port explosion".

## More Information

<!-- TODO: fill in — original ADR does not reference further material -->
