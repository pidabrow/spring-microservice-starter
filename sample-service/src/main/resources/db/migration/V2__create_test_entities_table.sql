CREATE TABLE test_entities (
    id UUID NOT NULL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    name VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);

CREATE INDEX idx_test_entities_tenant_id ON test_entities(tenant_id);

