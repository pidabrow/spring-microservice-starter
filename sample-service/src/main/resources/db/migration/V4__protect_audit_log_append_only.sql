-- Ensure audit_log is truly append-only at the database level
--
-- This migration enforces that:
-- - UPDATE and DELETE operations on audit_log are rejected via trigger
-- - Direct UPDATE/DELETE from any role will fail, including application role
--
-- Note: Blocking INSERTs at the database level would also block the
-- application from writing audit entries, unless we introduced
-- dedicated roles or a SECURITY DEFINER function with a different
-- contract. That is out of scope for this migration.

CREATE OR REPLACE FUNCTION prevent_audit_log_update_delete()
RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_log is append-only: % not allowed on id=%', TG_OP, OLD.id;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS audit_log_no_update_delete ON audit_log;

CREATE TRIGGER audit_log_no_update_delete
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW
    EXECUTE FUNCTION prevent_audit_log_update_delete();

-- Optionally harden privileges: no generic UPDATE/DELETE on audit_log
REVOKE UPDATE, DELETE ON audit_log FROM PUBLIC;
