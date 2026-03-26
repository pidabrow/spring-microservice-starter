# Architecture Overview

## Purpose

This repository contains an **opinionated, high-performance Spring Boot microservice starter**.
Its goal is to demonstrate architectural excellence using **Modern Java (21+)**, **Strict Immutability**, and **Hexagonal Principles**.

It is intended to be **forked, trimmed, and adapted** while maintaining a zero-compromise approach to code quality.

---

## High-Level Structure

This is a **monorepo** consisting of:

- **platform modules** — reusable building blocks (infrastructure, security, auditing).
- **sample service** — minimal reference implementation of business logic.

Dependency direction is strictly one-way:
`sample service → platform modules`

**Encapsulation Rule**: Implementations within modules (especially adapters) MUST be `package-private`. Public access is strictly reserved for Ports and DTOs to prevent "architectural leaking".

---

## Architectural Style: Strict Hexagonal Architecture

We follow a **Ports & Adapters** pattern with a "Golden Circle" domain.

### Core ideas
- **Inbound adapters** (e.g., REST controllers) map external requests to Use Cases.
- **Inbound ports** (Use Cases) orchestrate business flow, manage transactions, and publish events.
- **Domain Layer**: The heart of the system. It contains pure business logic, is **entirely immutable**, and has **zero dependencies** on Spring or any external library.
- **Outbound ports**: Interfaces defining side effects (persistence, external APIs).
- **Outbound adapters**: Concrete implementations (JPA, Mailer, etc.) that depend inward.

---

## Architectural Principles

- **Immutability by Default**: Domain entities and DTOs MUST use Java Records or final fields.
- **No Persistence Leaking**: JPA entities are implementation details of the persistence adapter and NEVER reach the Domain or Application layers.
- **Explicit over implicit**: We avoid "magic" (like broad AOP or auto-proxying) in favor of readable, traceable code.
- **Boundaries enforced through Code**: We don't just "hope" people follow the rules; we enforce them via ArchUnit.

---

## Domain Model & State Management

The domain is immutable. State transitions do not happen via setters.

Core entities:
- **Tenant**: Root of data isolation (soft multi-tenancy).
- **User**: Identity within a tenant.
- **Notification**: Business entity associated with a user.
- **AuditLog**: Append-only record of changes.

- **AuditLog**
  Append-only audit trail

The simplicity of the domain is intentional.  
Architecture, not business logic, is the focus of this repository.

---

## Soft Multi-Tenancy

- **Logical Isolation**: Every entity (except `Tenant`) contains a `tenant_id`.
- **Security Boundary**: Tenant context is resolved once at the edge (Inbound Adapter) and propagated via a secure, immutable context.
- **No Ad-hoc Filtering**: Isolation is handled at the persistence layer level to prevent developers from "forgetting" a WHERE clause.

### Implementation Components

- **Tenant Entity** (`platform-data`): JPA entity with UUID ID, status enum (ACTIVE/SUSPENDED), and audit fields. Does NOT contain `tenant_id` as it IS the tenant.
- **TenantContext** (`platform-common`): Immutable record representing the current tenant for a request.
- **TenantContextHolder** (`platform-common`): Thread-local holder for tenant context, providing explicit access without hidden static patterns.
- **TenantContextInterceptor** (`platform-web`): Inbound adapter that resolves tenant ID from HTTP header `X-Tenant-Id` and sets the tenant context per request.
- **TenantScopedEntity** (`platform-data`): Base entity class for tenant-scoped entities. Automatically sets `tenant_id` from `TenantContextHolder` in `@PrePersist`.
- **Tenant Filter** (`platform-data`): Hibernate filter automatically enabled via AOP aspect for all `@Transactional` methods, ensuring tenant isolation at the persistence layer without requiring explicit filtering in application or domain layers.

---

## Event-Driven Design

Business actions publish **Application Events** after a successful transaction commit.
- Events are **Java Records**.
- Publishing is intentional and explicit from the Use Case layer.
- Auditing and other cross-cutting concerns react to these events to keep the core logic clean.

---

## Transactional Outbox (ADR-007)

The **Transactional Outbox** pattern guarantees **At-Least-Once Delivery** of events to external systems (Kafka) without the risk of Dual Write inconsistencies.

### How it works

1. A Use Case publishes a `DomainEvent` via `DomainEventPublisher`.
2. An `IntegrationEventListener` (`@EventListener`) captures the event **within the same ACID transaction** and persists a `MessageOutboxEntity` record to the `message_outbox` table.
3. An `OutboxRelayService` (`@Scheduled` + **ShedLock**) polls for `PENDING` records and publishes them to **Kafka** via `KafkaMessagePublisher`.
4. Upon Kafka ACK, the record status is updated to `SENT`.
5. An `OutboxCleanupService` purges `SENT` records older than 7 days.

### Implementation Components

- **MessagePublisher** (`platform-common`): Outbound port interface for message publishing.
- **MessageOutboxEntity** (`platform-infrastructure`): JPA entity with UUID v7 PK, JSONB payload/headers, DB-driven timestamps.
- **IntegrationEventListener** (`platform-infrastructure`): Maps `DomainEvent` → outbox record in the same transaction.
- **OutboxRelayService** (`platform-infrastructure`): Polls PENDING records in batches of 100 with a 1-second visibility buffer. Uses ShedLock for distributed coordination.
- **KafkaMessagePublisher** (`platform-infrastructure`): Publishes to Kafka with `x-tenant-id`, `x-message-type`, and `x-correlation-id` headers.
- **OutboxCleanupService** (`platform-infrastructure`): Daily retention policy job.
- **OutboxConfiguration** (`platform-infrastructure`): Enables scheduling and ShedLock; activated by `outbox.enabled=true`.

### Resilience

- **Exponential Backoff**: Failed sends increment `retry_count`; records are skipped until eligible for retry.
- **Max Retries**: After 5 failed attempts, status moves to `FAILED`.
- **Dynamic Routing**: The `destination` field on each outbox record determines the Kafka topic.

All infrastructure adapter classes are **package-private** (enforced by ArchUnit).

---
## Auditing

Auditing is **event-driven and append-only**:

- audit entries are written only **after transaction commit**
- records capture tenant, actor, request context, and change deltas
- sensitive fields must be masked
- all records track their creation and last modification times. To prevent data manipulation and ensure a reliable audit trail, these timestamps are strictly **database-driven**. This ensures that even direct database modifications are captured, maintaining the integrity of the system's history.

---

## Automated Governance (Architecture-as-Code)

This project uses **ArchUnit** to ensure that:
1. The Domain layer remains "pure" (no imports from `infrastructure` or `spring`).
2. No public setters are added to JPA entities or Domain objects.
3. Adapters stay `package-private`.
4. Dependency directions are never violated.

---

## What This Project Avoids

- **Lombok (where Records suffice)**: We prefer native Java 21 features.
- We do not treat JPA entities as our primary domain model.
- **Excessive Ceremony**: We don't create ports for things that don't need abstraction, but we never skip them where boundaries are required.
- **Hidden Magic**: No persistence callbacks (`@PostPersist`) for business logic.

---

## Documentation Index

- Architecture entry point: `/architecture.md`
- Architectural decisions: `/docs/adr`
- **Integration testing (shared Testcontainers, fixtures):** `/docs/adr/ADR-009__platform-testing-infrastructure.md`
- AI/Cursor coding rules: `/.cursor/rules/*.mdc`