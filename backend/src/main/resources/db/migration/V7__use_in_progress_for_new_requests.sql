ALTER TABLE purchase_requests
    ALTER COLUMN material_status SET DEFAULT 'IN_PROGRESS';

UPDATE purchase_requests
SET material_status = 'IN_PROGRESS',
    updated_at = NOW()
WHERE material_status = 'PURCHASED';
