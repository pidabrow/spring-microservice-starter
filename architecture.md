# Architecture Overview

## Purpose

This repository contains an **opinionated Spring Boot microservice starter**.
Its goal is to demonstrate architectural decision-making and reusable foundations,
not feature richness.

It is intended to be **forked, trimmed, and adapted**.

---

## High-Level Structure

This is a **monorepo** consisting of:

- **platform modules** — reusable building blocks
- **sample service** — minimal reference implementation

Dependency direction is strictly one-way:

sample service → platform modules

Platform modules must never depend on the sample service.

---

## Architectural Style: Lightweight Hexagonal Architecture

This codebase follows a **lightweight hexagonal architecture (Ports & Adapters)**.
The purpose is to enforce clear boundaries without introducing excessive ceremony.

### Core ideas
- **Inbound adapters** (e.g., REST controllers) call **inbound ports** (use cases).
- **Inbound ports** contain application orchestration and transactions.
- **Outbound ports** define required capabilities (persistence, audit sink, etc.).
- **Outbound adapters** implement those ports (JPA repositories, audit listeners, etc.).
- The domain and application layers must not depend on Spring or infrastructure details.

---

## Architectural Principles

- Opinionated over configurable
- Explicit over implicit
- Simplicity over completeness
- Boundaries enforced through Ports & Adapters
- Architecture documented via ADRs

Significant decisions are documented in `/docs/adr`.

---

## Domain Model

The domain is intentionally small to keep architectural decisions visible.

Core entities:

- **Tenant**
  Root of data isolation (soft multi-tenancy)

- **User**
  Identity within a tenant

- **Notification**
  Business entity associated with a user

- **AuditLog**
  Append-only audit trail

The simplicity of the domain is intentional.  
Architecture, not business logic, is the focus of this repository.

---

## Soft Multi-Tenancy

The system uses **soft multi-tenancy**:

- single database
- single schema
- logical isolation via `tenant_id`

Rules:

- `tenant_id` exists in every entity except `Tenant`
- `tenant_id` represents a **security boundary**
- tenant isolation must NOT rely on joins or ad-hoc filtering

Tenant context is resolved per request and propagated explicitly.

See ADR-002 for details.

---

## Event-Driven Design

Business actions publish **application-level events** from the application layer.

Events are:
- explicit, business-named classes
- published intentionally from use cases (inbound ports)
- not derived from persistence callbacks or generic hooks

Cross-cutting concerns (e.g., auditing) react to events to avoid coupling.

---
ś
## Auditing

Auditing is **event-driven and append-only**:

- audit entries are written only **after transaction commit**
- records capture tenant, actor, request context, and change deltas
- sensitive fields must be masked

See ADR-003 for auditing details.

---

## What This Project Avoids

This project intentionally avoids:

- framework-building and over-abstraction
- excessive configurability
- hidden magic (JPA interceptors, broad AOP)
- non-essential features in the starter baseline
- “hexagonal ceremony” (ports everywhere without value)

---

## Documentation Index

- Architecture entry point: `/architecture.md`
- Architectural decisions: `/docs/adr`
- AI coding rules: `/.cursor/rules/*.mdc`
