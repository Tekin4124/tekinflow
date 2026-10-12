ALTER TABLE idempotency_keys ADD COLUMN IF NOT EXISTS request_hash VARCHAR(64);
ALTER TABLE idempotency_keys ADD COLUMN IF NOT EXISTS response_json TEXT;
