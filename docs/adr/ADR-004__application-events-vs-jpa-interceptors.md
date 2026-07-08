# ADR-004 — Application Events vs JPA Interceptors

**Status:** Accepted
**Date:** 2026-01-31
**Decision-makers:** <!-- TODO: fill in — not stated in original ADR -->
**Consulted:** <!-- TODO: fill in — not stated in original ADR -->
**Informed:** <!-- TODO: fill in — not stated in original ADR -->
**Related:** <!-- TODO: fill in — not stated in original ADR -->

## Context and Problem Statement

Cross-cutting concerns such as auditing and metrics need to react to business actions.
JPA lifecycle callbacks provide hooks but operate at the persistence level.

## Decision Drivers

- Cross-cutting concerns (auditing, metrics) need to react to business actions.
- JPA lifecycle callbacks operate at the persistence level, coupling business concerns to persistence.

## Considered Options

- JPA lifecycle callbacks.
- Generic persistence events.
- Explicit events published from the application layer.

## Decision Outcome

Chosen option: "Explicit events published from the application layer", because the alternatives (JPA lifecycle callbacks, generic persistence events) were rejected due to implicit behavior and limited expressiveness.

- Publish events explicitly from the application layer.
- Do not rely on JPA entity listeners or persistence callbacks for business events.

### Consequences

**Positive**

- Business intent is explicit and observable.
- Cross-cutting concerns do not leak into domain or persistence layers.

**Negative**

- Requires discipline to publish events consistently.

### Confirmation

<!-- TODO: fill in — original ADR does not describe a confirmation/verification mechanism -->

## Pros and Cons of the Options

### JPA lifecycle callbacks

- Bad, because of implicit behavior and limited expressiveness (rejected).

### Generic persistence events

- Bad, because of implicit behavior and limited expressiveness (rejected).

### Explicit events published from the application layer (chosen)

- Good, because business intent is explicit and observable.
- Good, because cross-cutting concerns do not leak into domain or persistence layers.
- Bad, because it requires discipline to publish events consistently.

## Notes for AI

- Event names must reflect business intent, not technical actions.

## More Information

<!-- TODO: fill in — original ADR does not reference further material -->
