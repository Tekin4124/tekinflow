CREATE TABLE IF NOT EXISTS user_preferences (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    key VARCHAR(100) NOT NULL,
    value JSONB NOT NULL DEFAULT '{}'::jsonb,
    updated_at TIMESTAMP DEFAULT NOW(),
    PRIMARY KEY (user_id, key)
);
