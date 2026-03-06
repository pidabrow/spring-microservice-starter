# ADR-007 — Transactional Outbox for External Integration

**Focus:** Reliability, Eventual Consistency, Traceability  

## Context
Following ADR-006, we need a mechanism that guarantees "At-Least-Once" delivery of events to external systems (Kafka, SaaS APIs) without the risk of "Dual Write" inconsistencies.

While `AuditLog` (ADR-006) captures **what happened** for historical purposes, we need a dedicated mechanism for **what needs to be sent** to external consumers. A failure in a network call to Kafka must not roll back the business transaction, yet we must guarantee the message won't be lost.

## Decision
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

## Data Schema (DDL)
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


**Implementation Rules**
- Infrastructure: Use spring-kafka (explicit KafkaTemplate configuration).

- Relay Service: A background process (@Scheduled) with ShedLock polls for PENDING records.

- Batching: Fetch and process records in batches (default 100) to optimize throughput.

- Error Handling: Implement exponential backoff or simple retry count. After X failed attempts, status moves to FAILED.

- Testing: Must use Testcontainers (PostgreSQL + Redpanda/Kafka) to verify the full integration chain.

**Consequences**

- Positive: Guaranteed message delivery, zero dual-write risk, high traceability.

- Negative: Slight eventual consistency delay, additional database storage for the outbox table.