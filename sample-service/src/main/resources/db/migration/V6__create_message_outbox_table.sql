-- Transactional Outbox table (ADR-007)
-- Guarantees At-Least-Once delivery of events to external systems (Kafka)
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

-- Tenant isolation index
CREATE INDEX idx_message_outbox_tenant_id ON message_outbox (tenant_id);

-- Trigger for DB-driven updated_at
CREATE TRIGGER update_message_outbox_updated_at
    BEFORE UPDATE ON message_outbox
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- ShedLock table for distributed lock coordination
CREATE TABLE shedlock (
    name VARCHAR(64) NOT NULL,
    lock_until TIMESTAMP NOT NULL,
    locked_at TIMESTAMP NOT NULL,
    locked_by VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);

