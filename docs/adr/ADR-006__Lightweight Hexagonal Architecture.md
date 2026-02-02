# ADR-005 — Lightweight Hexagonal Architecture (Ports & Adapters)

**Status:** Accepted  
**Date:** 2026-01-31

## Context

As the starter grows, clear boundaries are required to prevent infrastructure leakage
and to keep business/application logic testable and adaptable.

A full-blown hexagonal architecture was considered but deemed too heavy.

## Decision

- Adopt a lightweight hexagonal architecture.
- Use inbound adapters (REST) calling inbound ports (use cases).
- Define outbound ports as interfaces where they clarify boundaries.
- Implement outbound adapters for persistence, auditing, and integrations.
- Avoid unnecessary abstraction and “port explosion”.

## Consequences

- Clear dependency direction (adapters depend inward).
- Improved testability and maintainability.
- Slight increase in structural complexity.

## Alternatives Considered

- Traditional layered architecture.
- Framework-centric design.

Both were rejected due to weaker boundary enforcement.
