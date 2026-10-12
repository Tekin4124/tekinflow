CREATE TABLE IF NOT EXISTS purchase_request_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID NOT NULL REFERENCES purchase_requests(id) ON DELETE CASCADE,
    item_name VARCHAR(255) NOT NULL,
    quantity NUMERIC(12,3) NOT NULL DEFAULT 1,
    unit VARCHAR(20) NOT NULL DEFAULT 'ADET' CHECK (unit IN ('KG','CM','M','MM','LITRE','M3','ADET')),
    package_count INTEGER,
    dimensions_note VARCHAR(255),
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW()
);

INSERT INTO purchase_request_items (
    request_id,
    item_name,
    quantity,
    unit,
    package_count,
    dimensions_note,
    sort_order,
    created_at
)
SELECT
    pr.id,
    pr.item_to_purchase,
    1,
    'ADET',
    pr.package_count,
    NULLIF(pr.product_dimensions, ''),
    0,
    COALESCE(pr.created_at, NOW())
FROM purchase_requests pr
WHERE pr.item_to_purchase IS NOT NULL
  AND pr.item_to_purchase <> ''
  AND NOT EXISTS (
      SELECT 1 FROM purchase_request_items item WHERE item.request_id = pr.id
  );

CREATE INDEX IF NOT EXISTS idx_request_items_request ON purchase_request_items(request_id, sort_order);
