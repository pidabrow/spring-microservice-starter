# ADR-002 — Soft Multi-Tenancy

**Status:** Accepted
**Date:** 2026-01-31

## Context

The starter is intended to support multi-tenant systems without excessive operational overhead.
Separate databases or schemas per tenant were considered but rejected for this baseline.

## Decision

- Use soft multi-tenancy.
- All entities except `Tenant` include a `tenant_id`.
- `tenant_id` represents a security boundary, not a business relationship.
- Tenant isolation must not rely on joins or ad-hoc filtering.
- Tenant context is resolved explicitly per request.

## Consequences

- Simpler operational model (single database, single schema).
- Requires discipline to prevent accidental cross-tenant data access.
- Enables efficient querying and auditing per tenant.

## Alternatives Considered

- Separate schema per tenant.
- Separate database per tenant.

Both were rejected due to operational complexity and reduced suitability for a starter.
