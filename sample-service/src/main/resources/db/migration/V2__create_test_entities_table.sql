CREATE TABLE test_entities (
    id UUID NOT NULL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    name VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_test_entities_tenant_id ON test_entities(tenant_id);

-- Trigger to automatically update updated_at on test_entities table
CREATE TRIGGER update_test_entities_updated_at
    BEFORE UPDATE ON test_entities
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

