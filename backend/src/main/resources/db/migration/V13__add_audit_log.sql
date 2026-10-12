CREATE TABLE IF NOT EXISTS audit_log (
    id UUID PRIMARY KEY,
    actor_user_id UUID NULL REFERENCES users(id) ON DELETE SET NULL,
    actor_username VARCHAR(50) NULL,
    actor_full_name VARCHAR(100) NULL,
    action VARCHAR(80) NOT NULL,
    entity_type VARCHAR(40) NOT NULL,
    entity_id UUID NULL,
    entity_label VARCHAR(255) NULL,
    old_value TEXT NULL,
    new_value TEXT NULL,
    ip_address VARCHAR(64) NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_audit_log_created_at ON audit_log(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_log_entity ON audit_log(entity_type, entity_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_log_actor ON audit_log(actor_user_id, created_at DESC);
