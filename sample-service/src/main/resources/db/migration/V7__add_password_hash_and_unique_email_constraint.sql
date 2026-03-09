-- Add password_hash column to users table
ALTER TABLE users ADD COLUMN password_hash VARCHAR(255);

-- Add unique constraint on (email, tenant_id) to prevent duplicate registrations
-- This is the final source of truth for email uniqueness per tenant
CREATE UNIQUE INDEX idx_users_email_tenant_unique ON users(email, tenant_id);

