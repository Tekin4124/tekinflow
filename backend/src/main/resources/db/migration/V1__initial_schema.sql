CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(50) UNIQUE NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('PURCHASING','LOGISTICS_OFFICE','DRIVER','ADMIN')),
    license_plate VARCHAR(20),
    is_active BOOLEAN DEFAULT true,
    failed_attempts INTEGER DEFAULT 0,
    locked_until TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS purchase_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_number VARCHAR(20) UNIQUE NOT NULL,
    item_to_purchase VARCHAR(255) NOT NULL,
    company_name VARCHAR(255) NOT NULL,
    company_address TEXT,
    contact_number VARCHAR(50),
    product_dimensions VARCHAR(100),
    product_weight VARCHAR(100),
    package_count INTEGER,
    urgency_level VARCHAR(10) NOT NULL DEFAULT 'NORMAL' CHECK (urgency_level IN ('LOW','NORMAL','HIGH','URGENT')),
    purchase_date DATE,
    notes TEXT,
    license_plate VARCHAR(20),
    spot_driver_name VARCHAR(100),
    spot_driver_identity_number VARCHAR(20),
    spot_driver_company_name VARCHAR(100),
    material_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (material_status IN ('PENDING','IN_PROGRESS','PURCHASED','ON_THE_WAY','DELIVERED','CANCELLED')),
    assigned_driver_id UUID REFERENCES users(id),
    assigned_purchasing_staff_id UUID NOT NULL REFERENCES users(id),
    cancellation_reason TEXT,
    cancelled_by UUID REFERENCES users(id),
    objection_reason TEXT,
    has_objection BOOLEAN DEFAULT false,
    objection_by UUID REFERENCES users(id),
    redirected_to_request_id UUID REFERENCES purchase_requests(id),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message VARCHAR(500) NOT NULL,
    target_user_id UUID NOT NULL REFERENCES users(id),
    request_id UUID REFERENCES purchase_requests(id) ON DELETE CASCADE,
    is_read BOOLEAN DEFAULT false,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS request_seen (
    request_id UUID NOT NULL REFERENCES purchase_requests(id),
    user_id UUID NOT NULL REFERENCES users(id),
    seen_at TIMESTAMP DEFAULT NOW(),
    PRIMARY KEY (request_id, user_id)
);

CREATE TABLE IF NOT EXISTS refresh_tokens (
    token UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    parent_token UUID,
    revoked_at TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS idempotency_keys (
    key UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    request_path VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS request_audit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID NOT NULL REFERENCES purchase_requests(id) ON DELETE CASCADE,
    actor_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    action VARCHAR(50) NOT NULL,
    old_value TEXT,
    new_value TEXT,
    ip_address VARCHAR(64),
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS request_status_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID NOT NULL REFERENCES purchase_requests(id) ON DELETE CASCADE,
    actor_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    old_status VARCHAR(20),
    new_status VARCHAR(20) NOT NULL CHECK (new_status IN ('PENDING','IN_PROGRESS','PURCHASED','ON_THE_WAY','DELIVERED','CANCELLED')),
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE SEQUENCE IF NOT EXISTS request_number_seq START WITH 1 INCREMENT BY 1;
SELECT setval(
    'request_number_seq',
    (SELECT GREATEST(COALESCE(MAX(SUBSTRING(request_number FROM 'TLP-[0-9]{4}-([0-9]+)')::BIGINT), 1), 1) FROM purchase_requests),
    EXISTS(SELECT 1 FROM purchase_requests)
);

ALTER TABLE users ADD COLUMN IF NOT EXISTS failed_attempts INTEGER DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS locked_until TIMESTAMP;
ALTER TABLE purchase_requests ADD COLUMN IF NOT EXISTS package_count INTEGER;
ALTER TABLE purchase_requests ADD COLUMN IF NOT EXISTS spot_driver_name VARCHAR(100);
ALTER TABLE purchase_requests ADD COLUMN IF NOT EXISTS spot_driver_identity_number VARCHAR(20);
ALTER TABLE purchase_requests ADD COLUMN IF NOT EXISTS spot_driver_company_name VARCHAR(100);
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS request_id UUID REFERENCES purchase_requests(id) ON DELETE CASCADE;
ALTER TABLE refresh_tokens ADD COLUMN IF NOT EXISTS parent_token UUID;
ALTER TABLE refresh_tokens ADD COLUMN IF NOT EXISTS revoked_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_requests_status ON purchase_requests(material_status);
CREATE INDEX IF NOT EXISTS idx_requests_driver ON purchase_requests(assigned_driver_id);
CREATE INDEX IF NOT EXISTS idx_requests_staff ON purchase_requests(assigned_purchasing_staff_id);
CREATE INDEX IF NOT EXISTS idx_notifications_user ON notifications(target_user_id, is_read);
CREATE INDEX IF NOT EXISTS idx_requests_updated ON purchase_requests(updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user ON refresh_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_active ON refresh_tokens(user_id, revoked_at);
CREATE INDEX IF NOT EXISTS idx_idempotency_user ON idempotency_keys(user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_request_audit_request ON request_audit(request_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_request_status_history_request ON request_status_history(request_id, created_at DESC);
