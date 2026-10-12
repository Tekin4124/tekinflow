ALTER TABLE purchase_requests ADD COLUMN IF NOT EXISTS contact_person_name VARCHAR(100);
ALTER TABLE purchase_requests ADD COLUMN IF NOT EXISTS spot_driver_phone VARCHAR(50);
ALTER TABLE purchase_requests ADD COLUMN IF NOT EXISTS delivered_to VARCHAR(200);
ALTER TABLE purchase_requests ADD COLUMN IF NOT EXISTS actual_purchase_date TIMESTAMP;
