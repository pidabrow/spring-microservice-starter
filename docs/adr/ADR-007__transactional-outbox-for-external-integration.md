# ADR-007 — Transactional Outbox for External Integration

**Status:** Accepted
**Date:** <!-- TODO: fill in — not stated in original ADR -->
**Decision-makers:** <!-- TODO: fill in — not stated in original ADR -->
**Consulted:** <!-- TODO: fill in — not stated in original ADR -->
**Informed:** <!-- TODO: fill in — not stated in original ADR -->
**Related:** ADR-002 (Soft Multi-Tenancy), ADR-006 (Domain Events & Tenant-Aware Auditing)

## Context and Problem Statement

Focus: Reliability, Eventual Consistency, Traceability.

Following ADR-006, we need a mechanism that guarantees "At-Least-Once" delivery of events to external systems (Kafka, SaaS APIs) without the risk of "Dual Write" inconsistencies.

While `AuditLog` (ADR-006) captures **what happened** for historical purposes, we need a dedicated mechanism for **what needs to be sent** to external consumers. A failure in a network call to Kafka must not roll back the business transaction, yet we must guarantee the message won't be lost.

## Decision Drivers

- Guarantee "At-Least-Once" delivery of events to external systems (Kafka, SaaS APIs).
- Avoid "Dual Write" inconsistencies.
- A failure in a network call to an external system must not roll back the business transaction.
- `tenant_id` must be mandatory and propagated to external systems (Soft Multi-tenancy — ADR-002).

## Considered Options

<!-- TODO: fill in — original ADR does not enumerate alternative mechanisms side by side; it only names the risk ("Dual Write" inconsistencies) that the chosen mechanism avoids -->

## Decision Outcome

1. **Mechanism**: Transactional Outbox Pattern. External messages are persisted in a `message_outbox` table within the same ACID transaction as the domain change.
2. **Separation of Intent**:
    - `AuditLog`: Captures Domain Facts (e.g., `USER_EMAIL_CHANGED`).
    - `MessageOutbox`: Captures Integration Intents (e.g., `SEND_WELCOME_EMAIL`).
3. **Technical Flow**:
    - Use Case publishes a `DomainEvent`.
    - An `IntegrationEventListener` (Inbound Port) maps the domain event to one or more outbox records.
    - The `MessageOutbox` record includes `origin_event_type` to maintain a link back to the audit trail.
4. **Reliability & Ordering**:
    - **At-Least-Once Delivery**: Status remains `PENDING` until a Kafka Acknowledgement (ACK) is received.
    - **Partitioning**: The `partition_key` (typically the aggregate's `entityId`) is used as the Kafka Message Key to ensure chronological ordering per entity.
5. **Multi-tenancy**: `tenant_id` is mandatory and propagated via Kafka Headers (Soft Multi-tenancy - ADR-002).

### Data Schema (DDL)

The schema follows standards: UUID v7, DB-driven timestamps, and JSONB for flexible payloads.

```sql
CREATE TABLE message_outbox (
    id UUID PRIMARY KEY,                   -- UUID v7 (Time-ordered)
    tenant_id UUID NOT NULL,               -- Soft Multi-tenancy (ADR-002)
    
    -- Integration Metadata
    message_type VARCHAR(255) NOT NULL,    -- e.g., 'WELCOME_EMAIL_REQUEST'
    origin_event_type VARCHAR(255),        -- Link to Domain Event (traceability)
    destination VARCHAR(255) NOT NULL,     -- Kafka topic or routing hint
    partition_key VARCHAR(255) NOT NULL,   -- Kafka key (e.g., entityId)
    
    -- Payload & Context
    payload JSONB NOT NULL,                -- Message body
    headers JSONB,                         -- Multi-tenancy & Correlation headers
    
    -- State Machine
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    last_error TEXT,
    
    -- DB Driven Timestamps
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE
);

-- Optimization for Polling Relay (ShedLock recommended)
CREATE INDEX idx_message_outbox_polling ON message_outbox (status, created_at) 
WHERE status = 'PENDING';

-- Traceability Index
CREATE INDEX idx_message_outbox_origin ON message_outbox (origin_event_type);

-- Trigger for DB-driven updated_at
```

### Consequences

**Positive**

- Guaranteed message delivery, zero dual-write risk, high traceability.

**Negative**

- Slight eventual consistency delay, additional database storage for the outbox table.

### Confirmation

<!-- TODO: fill in — original ADR does not describe a confirmation/verification mechanism -->

## Pros and Cons of the Options

<!-- TODO: fill in — original ADR does not compare alternative mechanisms -->

## Notes for AI

**Implementation Rules**

- Infrastructure: Use spring-kafka (explicit KafkaTemplate configuration).
- Relay Service: A background process (@Scheduled) with ShedLock polls for PENDING records.
- Batching: Fetch and process records in batches (default 100) to optimize throughput.
- Visibility Buffer: Poll only records where `created_at < NOW() - INTERVAL '1 second'` to ensure transaction visibility.
- Error Handling: Implement exponential backoff or simple retry count. After 5 failed attempts, status moves to FAILED.
- Retention Policy: Records with status SENT older than 7 days should be purged automatically.
- Testing: Must use Testcontainers (PostgreSQL + Redpanda/Kafka) to verify the full integration chain.

## More Information

<!-- TODO: fill in — original ADR does not reference further material -->
