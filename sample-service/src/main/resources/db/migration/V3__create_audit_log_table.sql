-- Create audit_log table for append-only audit trail
CREATE TABLE audit_log (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    entity_type VARCHAR(255) NOT NULL,
    entity_id UUID NOT NULL,
    action VARCHAR(50) NOT NULL,
    event_class_name VARCHAR(500) NOT NULL,
    actor_type VARCHAR(50) NOT NULL,
    actor_id UUID,
    changes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create index on tenant_id for tenant isolation queries
CREATE INDEX idx_audit_log_tenant_id ON audit_log(tenant_id);

-- Create index on entity_type and entity_id for entity-specific queries
CREATE INDEX idx_audit_log_entity ON audit_log(entity_type, entity_id);

-- Create index on created_at for time-based queries
CREATE INDEX idx_audit_log_created_at ON audit_log(created_at);

